package com.forgebuild.clipforgeandroid.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.SourceClassifier
import com.forgebuild.engine.ui.icons.EngineIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewTaskWizard(vm: ClipForgeViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val upload by vm.upload.collectAsState()
    val settings by vm.settings.collectAsState()
    val musicTracks by vm.music.collectAsState()
    val defaultMusic by vm.defaultMusic.collectAsState()

    val isOriginal = vm.api?.isOriginalRepo() == true

    // Fix #8 — bot parity (wizard.js): the operator never picks a source type; the
    // pasted text is classified directly (magnet checked BEFORE the generic URL
    // fallback — the session-08 ordering fix must not regress). Only a .torrent
    // upload is still an explicit action (there is nothing to classify in text).
    var detectedSourceKind by remember { mutableStateOf<String?>(null) }
    var sourceValue by remember { mutableStateOf("") }
    var torrentBytes by remember { mutableStateOf<ByteArray?>(null) }
    var torrentFileName by remember { mutableStateOf("") }

    // Fix #1 — load the music library AND the current default track the moment this
    // screen opens; never rely on the Music settings screen having been visited.
    LaunchedEffect(Unit) { vm.onNewTaskOpen() }

    var focus by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableStateOf(120) }
    var customDurationText by remember { mutableStateOf("") }
    var isSeries by remember { mutableStateOf(settings.seriesDefault) }
    var seriesId by remember { mutableStateOf("") }
    // Super Series is ON TOP of Series Mode — only meaningful when Series Mode is
    // on, never on while Series Mode is off (backend wizard.js dependency).
    var isSuperSeries by remember { mutableStateOf(settings.seriesDefault && settings.superSeriesDefault) }
    var selectedMusicPath by remember { mutableStateOf(defaultMusic) }

    val torrentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                if (bytes.size > 1024 * 1024) {
                    vm.toast("Torrent file exceeds 1 MB limit")
                } else {
                    torrentBytes = bytes
                    torrentFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "source.torrent"
                    vm.toast("Loaded $torrentFileName (${bytes.size / 1024} KB)")
                }
            }
        } catch (e: Exception) {
            vm.toast("Failed to read torrent file: ${e.message}")
        }
    }

    LaunchedEffect(settings.seriesDefault, settings.superSeriesDefault) {
        if (seriesId.isBlank()) {
            isSeries = settings.seriesDefault
            isSuperSeries = settings.seriesDefault && settings.superSeriesDefault
        } else if (!isSeries) {
            isSuperSeries = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Video Task") }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            upload?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(it.label, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { it.fraction }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // --- Source Selection ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Video Source", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = sourceValue,
                        onValueChange = { raw ->
                            sourceValue = raw
                            // Live classification exactly like the bot's wizard step 1
                            // (classifySourceText). MAGNET is checked BEFORE the generic
                            // URL fallback — SourceClassifier preserves the bot's exact
                            // ordering, so a magnet URI can never degrade into kind:url.
                            detectedSourceKind = when (val verdict = SourceClassifier.classify(raw)) {
                                is SourceClassifier.Result.Kind -> verdict.kind
                                is SourceClassifier.Result.Invalid -> null
                            }
                        },
                        label = { Text("Paste a link — the app detects the type automatically") },
                        placeholder = { Text("https://… | Google Drive | magnet:?xt=… | t.me/channel/123") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val detected = detectedSourceKind
                    if (detected != null) {
                        val gated = detected == "telegram_channel" && !isOriginal
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    when (detected) {
                                        "magnet" -> "Detected: Magnet link"
                                        "drive" -> "Detected: Google Drive link"
                                        "url" -> "Detected: Direct URL"
                                        "telegram_channel" -> if (gated) "Telegram channel (official repo only)" else "Detected: Telegram channel post"
                                        else -> "Detected: $detected"
                                    }
                                )
                            }
                        )
                    }

                    Text("— or upload a torrent file instead —", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { torrentPicker.launch("application/x-bittorrent") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (torrentFileName.isNotBlank()) "Change .torrent file" else "Pick .torrent file")
                    }
                    if (torrentFileName.isNotBlank()) {
                        Text("Selected: $torrentFileName (${(torrentBytes?.size ?: 0) / 1024} KB)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // --- Options: Focus & Target Duration ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Production Options", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = focus,
                        onValueChange = { focus = it },
                        label = { Text("Focus or Theme (Optional)") },
                        placeholder = { Text("e.g. Best goals, dramatic reveal, funny moments") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Target Spoken Narration Duration: ${durationSeconds}s", style = MaterialTheme.typography.bodyMedium)
                    // Presets match the Telegram bot (TARGET_DURATIONS = 30/60/120/180/300).
                    // Custom opens a real input dialog (the previous chip toggled state but
                    // never surfaced a usable field, so a custom value could not be entered).
                    val presets = listOf(30, 60, 120, 180, 300)
                    val customSelected = durationSeconds !in presets
                    var showCustomDialog by remember { mutableStateOf(false) }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { dur ->
                            FilterChip(
                                selected = durationSeconds == dur,
                                onClick = { durationSeconds = dur },
                                label = { Text("${dur}s") }
                            )
                        }
                        FilterChip(
                            selected = customSelected,
                            onClick = {
                                // Prefill the dialog with the current custom value (if any).
                                customDurationText = durationSeconds.takeIf { it !in presets }?.toString() ?: ""
                                showCustomDialog = true
                            },
                            label = { Text(if (customSelected) "Custom (${durationSeconds}s)" else "Custom") }
                        )
                    }

                    if (showCustomDialog) {
                        val typed = customDurationText.toIntOrNull()
                        val valid = typed != null && typed in 1..36000
                        AlertDialog(
                            onDismissRequest = { showCustomDialog = false },
                            title = { Text("Custom duration") },
                            text = {
                                OutlinedTextField(
                                    value = customDurationText,
                                    onValueChange = { customDurationText = it.filter { c -> c.isDigit() }.take(5) },
                                    label = { Text("Duration (seconds)") },
                                    placeholder = { Text("e.g. 45") },
                                    supportingText = { Text("Target spoken narration length, 1–36000 seconds") },
                                    isError = customDurationText.isNotEmpty() && !valid,
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        typed?.let { durationSeconds = it }
                                        showCustomDialog = false
                                    },
                                    enabled = valid
                                ) { Text("Apply") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }

            // --- Music Selection ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Background Music", style = MaterialTheme.typography.titleMedium)

                    // Bot wizard parity: default/none resolves to the Settings default
                    // track at render time when one is configured, silence otherwise.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedMusicPath == null,
                            onClick = { selectedMusicPath = null }
                        )
                        Text(
                            if (defaultMusic != null) "Use saved default ($defaultMusic)" else "No music",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    if (musicTracks.isEmpty()) {
                        Text(
                            "Library is empty — add tracks in Settings → Music library",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    musicTracks.forEach { track ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedMusicPath == track.path,
                                onClick = { selectedMusicPath = track.path }
                            )
                            Text(track.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            // --- Series Mode ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Series Mode", style = MaterialTheme.typography.titleMedium)
                            Text("Split content into sequential multi-part series", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = isSeries,
                            onCheckedChange = {
                                isSeries = it
                                // dependency: Series Mode OFF forces Super Series OFF
                                if (!it) isSuperSeries = false
                            }
                        )
                    }

                    if (isSeries) {
                        OutlinedTextField(
                            value = seriesId,
                            onValueChange = { seriesId = it },
                            label = { Text("Series ID (leave blank to auto-generate)") },
                            placeholder = { Text("e.g. doc-episodes-01") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Super Series toggle — only present while Series Mode is ON.
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Super Series", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Submit ONE whole-series super-plan; parts 2..N dispatch automatically on completion",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(checked = isSuperSeries, onCheckedChange = { isSuperSeries = it })
                        }
                    }
                }
            }

            // --- Submit Button ---
            Button(
                onClick = {
                    // Fix #8 — bot wizard parity: a picked .torrent file IS the source
                    // (torrent_file); otherwise classify the pasted text directly with
                    // the ported classifier. No source-type selector exists any more.
                    var submitKind = ""
                    var submitValue = ""
                    if (torrentBytes != null && torrentBytes!!.isNotEmpty()) {
                        submitKind = "torrent_file"
                    } else {
                        when (val verdict = SourceClassifier.classify(sourceValue)) {
                            is SourceClassifier.Result.Invalid -> {
                                vm.toast(verdict.error)
                                return@Button
                            }
                            is SourceClassifier.Result.Kind -> {
                                if (verdict.kind == "telegram_channel" && !isOriginal) {
                                    vm.toast("Telegram channel sources are only available on the official ClipForge repo")
                                    return@Button
                                }
                                submitKind = verdict.kind
                                submitValue = verdict.value
                            }
                        }
                        if (submitValue.isBlank()) {
                            vm.toast("Please enter a valid source URL")
                            return@Button
                        }
                    }

                    if (durationSeconds !in 1..36000) {
                        vm.toast("Please enter a valid duration (1–36000 seconds)")
                        return@Button
                    }
                    vm.createStageATask(
                        sourceKind = submitKind,
                        sourceValue = submitValue,
                        focus = focus,
                        targetDurationSeconds = durationSeconds,
                        selectedMusicPath = selectedMusicPath,
                        isSeries = isSeries,
                        seriesId = if (seriesId.isNotBlank()) seriesId else null,
                        isSuperSeries = isSuperSeries && isSeries,
                        torrentBytes = torrentBytes,
                        onCreated = {
                            onDone()
                        }
                    )
                },
                enabled = upload == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Session-10 fix #5: the button ITSELF enters an obvious loading state
                // the instant a stage starts — the operator never has to scroll to
                // find the progress bar to confirm something happened.
                if (upload != null) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    Text("Starting Stage A…")
                } else {
                    Text("Start Video Analysis (Stage A)")
                }
            }
        }
    }
}
