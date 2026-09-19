@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.forgebuild.clipforgeandroid.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.SourceClassifier
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens

/* ============================================================================
 *  V23 NEW-TASK WIZARD — rebuilt from scratch.
 *  Paste-anything source auto-detection (magnet checked before the generic URL
 *  fallback, exactly like the backend wizard), torrent upload, duration presets
 *  + custom dialog, background-music pick, Series + Super Series toggles, and a
 *  busy-aware submit that gives immediate visible feedback and cannot
 *  double-fire.
 * ============================================================================ */

@Composable
fun NewTaskWizard(vm: ClipForgeViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val upload by vm.upload.collectAsState()
    val settings by vm.settings.collectAsState()
    val musicTracks by vm.music.collectAsState()
    val defaultMusic by vm.defaultMusic.collectAsState()
    val isOriginal = vm.api?.isOriginalRepo() == true

    var detectedSourceKind by remember { mutableStateOf<String?>(null) }
    var sourceValue by remember { mutableStateOf("") }
    var torrentBytes by remember { mutableStateOf<ByteArray?>(null) }
    var torrentFileName by remember { mutableStateOf("") }

    var focus by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableStateOf(120) }
    var customDurationText by remember { mutableStateOf("") }
    var showCustomDialog by remember { mutableStateOf(false) }
    var isSeries by remember { mutableStateOf(settings.seriesDefault) }
    var seriesId by remember { mutableStateOf("") }
    var isSuperSeries by remember { mutableStateOf(settings.seriesDefault && settings.superSeriesDefault) }
    var selectedMusicPath by remember { mutableStateOf(defaultMusic) }

    LaunchedEffect(Unit) { vm.onNewTaskOpen() }

    val torrentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                if (bytes.size > 1024 * 1024) vm.toast("Torrent file exceeds the 1 MB limit")
                else {
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
        } else if (!isSeries) isSuperSeries = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New video task") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ElevationTokens.tonalContainerColor(2)),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(top = pad.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPaddingForFloatingBar()),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md),
        ) {
            upload?.let { CfCard(tonalLevel = 2) { CfProgress(label = it.label, fraction = it.fraction) } }

            CfSection(
                title = "Video source",
                subtitle = "Paste any link — the type is detected automatically — or upload a .torrent file.",
            ) {
                OutlinedTextField(
                    value = sourceValue,
                    onValueChange = { raw ->
                        sourceValue = raw
                        detectedSourceKind = when (val verdict = SourceClassifier.classify(raw)) {
                            is SourceClassifier.Result.Kind -> verdict.kind
                            is SourceClassifier.Result.Invalid -> null
                        }
                    },
                    label = { Text("Link") },
                    placeholder = { Text("https://… | Google Drive | magnet:?xt=… | t.me/channel/123") },
                    modifier = Modifier.fillMaxWidth(),
                )
                detectedSourceKind?.let { detected ->
                    val gated = detected == "telegram_channel" && !isOriginal
                    CfChip(
                        label = when (detected) {
                            "magnet" -> "Detected: magnet link"
                            "drive" -> "Detected: Google Drive link"
                            "url" -> "Detected: direct URL"
                            "telegram_channel" -> if (gated) "Telegram channel (official repo only)" else "Detected: Telegram channel post"
                            else -> "Detected: $detected"
                        },
                        color = if (gated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
                ) {
                    OutlinedActionButton(
                        label = if (torrentFileName.isNotBlank()) "Change .torrent file" else "Pick .torrent file",
                        onClick = { torrentPicker.launch("application/x-bittorrent") },
                    )
                    if (torrentFileName.isNotBlank()) {
                        Text(
                            "$torrentFileName (${(torrentBytes?.size ?: 0) / 1024} KB)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            CfSection(title = "Production options") {
                OutlinedTextField(
                    value = focus, onValueChange = { focus = it },
                    label = { Text("Focus or theme (optional)") },
                    placeholder = { Text("e.g. best goals, dramatic reveal, funny moments") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Target spoken narration: ${durationSeconds}s", style = MaterialTheme.typography.bodyMedium)
                val presets = listOf(30, 60, 120, 180, 300)
                val customSelected = durationSeconds !in presets
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    presets.forEach { dur ->
                        FilterChip(selected = durationSeconds == dur, onClick = { durationSeconds = dur }, label = { Text("${dur}s") })
                    }
                    FilterChip(
                        selected = customSelected,
                        onClick = {
                            customDurationText = durationSeconds.takeIf { it !in presets }?.toString() ?: ""
                            showCustomDialog = true
                        },
                        label = { Text(if (customSelected) "Custom (${durationSeconds}s)" else "Custom") },
                    )
                }
            }

            CfSection(title = "Background music", subtitle = "The saved default is used unless you pick a track here.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedMusicPath == null, onClick = { selectedMusicPath = null })
                    Text(
                        if (defaultMusic != null) "Saved default ($defaultMusic)" else "No music",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = SpacingTokens.Spacing.xs),
                    )
                }
                if (musicTracks.isEmpty()) {
                    Text(
                        "Library is empty — add tracks under Settings → Music library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                musicTracks.forEach { track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedMusicPath == track.path, onClick = { selectedMusicPath = track.path })
                        Text(track.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = SpacingTokens.Spacing.xs))
                    }
                }
            }

            CfSection(title = "Series", subtitle = "Split the content into sequential parts.") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Series Mode", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isSeries,
                        onCheckedChange = { on ->
                            isSeries = on
                            if (!on) isSuperSeries = false // dependency: Series OFF forces Super OFF
                        },
                    )
                }
                if (isSeries) {
                    OutlinedTextField(
                        value = seriesId, onValueChange = { seriesId = it },
                        label = { Text("Series ID (blank = auto-generate)") },
                        placeholder = { Text("e.g. doc-episodes-01") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Super Series", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "One whole-series plan; parts dispatch automatically in sequence.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = isSuperSeries, onCheckedChange = { isSuperSeries = it })
                    }
                }
            }

            ActionButton(
                label = if (upload != null) "Starting Stage A…" else "Start video analysis (Stage A)",
                icon = EngineIcons.Bolt,
                busy = upload != null,
                onClick = {
                    var submitKind = ""
                    var submitValue = ""
                    if (torrentBytes != null && torrentBytes!!.isNotEmpty()) {
                        submitKind = "torrent_file"
                    } else {
                        when (val verdict = SourceClassifier.classify(sourceValue)) {
                            is SourceClassifier.Result.Invalid -> { vm.toast(verdict.error); return@ActionButton }
                            is SourceClassifier.Result.Kind -> {
                                if (verdict.kind == "telegram_channel" && !isOriginal) {
                                    vm.toast("Telegram channel sources are only available on the official ClipForge repo")
                                    return@ActionButton
                                }
                                submitKind = verdict.kind
                                submitValue = verdict.value
                            }
                        }
                        if (submitValue.isBlank()) { vm.toast("Please enter a valid source URL"); return@ActionButton }
                    }
                    if (durationSeconds !in 1..36000) { vm.toast("Enter a valid duration (1–36000 seconds)"); return@ActionButton }
                    vm.createStageATask(
                        sourceKind = submitKind,
                        sourceValue = submitValue,
                        focus = focus,
                        targetDurationSeconds = durationSeconds,
                        selectedMusicPath = selectedMusicPath,
                        isSeries = isSeries,
                        seriesId = seriesId.ifBlank { null },
                        isSuperSeries = isSuperSeries && isSeries,
                        torrentBytes = torrentBytes,
                        onCreated = { onDone() },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextActionButton(label = "Apply", enabled = valid, onClick = { typed?.let { durationSeconds = it }; showCustomDialog = false })
            },
            dismissButton = { TextActionButton(label = "Cancel", onClick = { showCustomDialog = false }) },
        )
    }
}
