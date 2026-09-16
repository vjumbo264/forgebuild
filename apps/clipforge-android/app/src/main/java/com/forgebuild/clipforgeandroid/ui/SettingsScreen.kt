package com.forgebuild.clipforgeandroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.platform.LocalContext
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.DiagLog
import com.forgebuild.clipforgeandroid.data.Voices
import com.forgebuild.engine.files.SafeSave
import com.forgebuild.engine.ui.icons.EngineIcons

/** Small inline spinner used inside buttons while an async op runs. */
@Composable
private fun ButtonSpinner() {
    SquiggleCircularLoader(Modifier.size(16.dp), color = LocalContentColor.current)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: ClipForgeViewModel,
    onOpenMusic: () -> Unit
) {
    val login by vm.login.collectAsState()
    val settings by vm.settings.collectAsState()
    val defaultMusic by vm.defaultMusic.collectAsState()
    val settingsLoading by vm.settingsLoading.collectAsState()
    val settingsLoaded by vm.settingsLoaded.collectAsState()
    val busyOps by vm.busyOps.collectAsState()
    val audioState by AudioPreview.state.collectAsState()

    // Session-11 (task-75): SAF export of the diagnostic log. The system picker
    // (ACTION_CREATE_DOCUMENT) lets the operator save it wherever they choose —
    // e.g. Documents/ClipForge — with NO storage permission, instead of an
    // app-private folder they cannot reach. Write goes through the Engine
    // SafeSave helper (never DownloadManager).
    val context = LocalContext.current
    val diagExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            try {
                val text = DiagLog.exportText(context.applicationContext)
                SafeSave.writeBytes(context, uri, text.toByteArray(Charsets.UTF_8))
                vm.toast("Diagnostic log saved (${uri.lastPathSegment ?: "file"})")
            } catch (_: Exception) {
                vm.toast("Could not write diagnostic log")
            }
        }
        // uri == null -> user cancelled the picker; nothing was saved.
    }

    val isOriginal = vm.api?.isOriginalRepo() == true

    // Re-fetch settings from the clone every time the Settings screen becomes active,
    // so watermark / Zernio / narrator / series values from a prior session are visible
    // instead of the default "as if I should set it newly" state.
    LaunchedEffect(Unit) { vm.loadSettings() }

    // Stop any voice preview when leaving the screen (fix #2).
    DisposableEffect(Unit) { onDispose { AudioPreview.stop() } }

    var showDeleteRepoDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showClearZernioDialog by remember { mutableStateOf(false) }

    // Bind the local input state to the LOADED settings values so previously-saved
    // watermark/etc. actually appear in the field once loadSettings() returns.
    // The Zernio API key is a sealed GitHub Actions secret: its value can never be
    // read back, so the input starts empty and only ever holds a NEW key the user types.
    var watermarkInput by remember(settings.watermarkText) { mutableStateOf(settings.watermarkText) }
    var zernioKeyInput by remember { mutableStateOf("") }
    var newsInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
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
            // Explicit loading banner while first-load is in progress, so the screen
            // does not silently render blank/default values as if nothing was saved.
            if (settingsLoading && !settingsLoaded) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SquiggleCircularLoader(Modifier.size(20.dp))
                        Text("Loading your saved settings…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // --- Section 1: GitHub Clone Management ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("GitHub Clone Repository", style = MaterialTheme.typography.titleMedium)

                    login?.let {
                        Text("${it.owner}/${it.repo}", style = MaterialTheme.typography.bodyLarge)
                        Text("Signed in as ${it.login}", style = MaterialTheme.typography.bodySmall)
                        val maskedPat = if (it.pat.length > 8) it.pat.take(4) + "••••••••" + it.pat.takeLast(4) else "••••••••"
                        Text("PAT: $maskedPat", style = MaterialTheme.typography.bodySmall)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text(if (settings.isPrivate) "Private Repository" else "Public Repository") }
                            )
                            val visBusy = busyOps.contains("toggle_visibility")
                            OutlinedButton(
                                onClick = { vm.toggleRepoVisibility() },
                                enabled = !visBusy
                            ) {
                                if (visBusy) { ButtonSpinner(); Spacer(Modifier.width(8.dp)) }
                                Text(if (settings.isPrivate) "Make Public" else "Make Private")
                            }
                        }

                        val syncBusy = busyOps.contains("sync_from_source")
                        Button(
                            onClick = { vm.syncFromSource() },
                            enabled = !syncBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (syncBusy) { ButtonSpinner(); Spacer(Modifier.width(8.dp)) }
                            Text("Sync from motionssalt/clipforge (Source)")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDisconnectDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Disconnect")
                            }
                            OutlinedButton(
                                onClick = { showDeleteRepoDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Delete Clone")
                            }
                        }

                        // Main-Account Gated Features
                        if (isOriginal) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("Owner / Main-Account Controls", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)

                            val pushBusy = busyOps.contains("push_update")
                            Button(
                                onClick = { vm.pushUpdateToClones() },
                                enabled = !pushBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (pushBusy) { ButtonSpinner(); Spacer(Modifier.width(8.dp)) }
                                Text("Push Update to Clones")
                            }

                            OutlinedTextField(
                                value = newsInput,
                                onValueChange = { newsInput = it },
                                label = { Text("Broadcast News to Clones") },
                                placeholder = { Text("e.g. New model update live!") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            val newsBusy = busyOps.contains("push_news")
                            Button(
                                onClick = {
                                    if (newsInput.isNotBlank()) {
                                        vm.pushNews(newsInput)
                                        newsInput = ""
                                    }
                                },
                                enabled = newsInput.isNotBlank() && !newsBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (newsBusy) { ButtonSpinner(); Spacer(Modifier.width(8.dp)) }
                                Text("Broadcast News")
                            }
                        }
                    }
                }
            }

            // --- Section 2: Narrator Voice (TTS) ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Narrator Voice (Edge TTS)", style = MaterialTheme.typography.titleMedium)
                        if (busyOps.contains("save_narrator")) {
                            ButtonSpinner()
                        }
                    }
                    Text("Select the voice used for spoken voiceovers across generated clips.", style = MaterialTheme.typography.bodySmall)

                    // Fix #2 — every voice carries the bot's own preview affordance
                    // (assets/tts-previews/<voiceId>.mp3, same file the bot sends via
                    // getRepositoryFileBytes) so the operator can hear a voice before
                    // choosing it. Streaming + caching go through the shared
                    // AudioPreview pipeline — the sample is never re-synthesized.
                    Voices.ALL.forEach { voice ->
                        val isSelected = settings.narratorVoice == voice.id
                        val previewUrl = vm.audioPreviewUrl("assets/tts-previews/${voice.id}.mp3")
                        val thisActive = audioState.url == previewUrl
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.setNarratorVoice(voice.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { vm.setNarratorVoice(voice.id) }
                            )
                            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                Text(
                                    text = "${voice.label} (${voice.gender})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = voice.style,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.previewVoice(voice.id) }) {
                                when {
                                    thisActive && audioState.isBuffering -> SquiggleCircularLoader(Modifier.size(20.dp))
                                    thisActive && audioState.isPlaying -> Icon(EngineIcons.Pause, "Pause preview")
                                    else -> Icon(Icons.Default.PlayArrow, "Voice preview for ${voice.label}")
                                }
                            }
                        }
                    }
                }
            }

            // --- Section 3: Music Library ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Music Library", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Default Track: ${defaultMusic ?: "None (Silence / Sound effects only)"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = onOpenMusic,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manage Music & Tracks")
                    }
                }
            }

            // --- Section 4: Creator Watermark ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Creator Watermark", style = MaterialTheme.typography.titleMedium)
                    Text("Burn a creator watermark handle or text onto the rendered video.", style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = watermarkInput,
                        onValueChange = { watermarkInput = it },
                        label = { Text("Watermark text / handle") },
                        placeholder = { Text("@your_channel") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val wmBusy = busyOps.contains("save_watermark")
                        Button(
                            onClick = { vm.setWatermark(watermarkInput) },
                            enabled = !wmBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (wmBusy) { ButtonSpinner(); Spacer(Modifier.width(8.dp)) }
                            Text("Save Watermark")
                        }
                        if (settings.watermarkText.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    vm.clearWatermark()
                                    watermarkInput = ""
                                },
                                enabled = !wmBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                    if (settings.watermarkText.isNotBlank()) {
                        Text(
                            "Currently saved: \u201c${settings.watermarkText}\u201d",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- Section 5: Series Mode Default ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Default Series Mode", style = MaterialTheme.typography.titleMedium)
                            Text("Automatically enable Series Mode on new video tasks", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (busyOps.contains("save_series_default")) {
                                ButtonSpinner()
                                Spacer(Modifier.width(8.dp))
                            }
                            Switch(
                                checked = settings.seriesDefault,
                                enabled = !busyOps.contains("save_series_default"),
                                onCheckedChange = { vm.setSeriesDefaultWithDependency(it) }
                            )
                        }
                    }
                }
            }

            // --- Section 5b: Super Series (parallel to, not merged into, Series Mode) ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Super Series", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Whole-series planning: submit ONE super-plan and parts 2..N dispatch automatically on completion. Requires Series Mode — it is forced off when Series Mode is off.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Super Series default", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Stored at branding/super_series_settings.json",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val superBusy = busyOps.contains("save_super")
                        if (superBusy) ButtonSpinner()
                        Switch(
                            checked = settings.superSeriesDefault && settings.seriesDefault,
                            enabled = settings.seriesDefault && !superBusy,
                            onCheckedChange = { vm.setSuperSeriesDefault(it) }
                        )
                    }
                    if (!settings.seriesDefault) {
                        Text(
                            "Turn Series Mode on to enable Super Series.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- Section 6: Zernio Publishing (full card: status, key, save, accounts, refresh) ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Zernio Publishing", style = MaterialTheme.typography.titleMedium)
                            Text("Publish rendered clips directly to social channels", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = settings.zernioEnabled,
                            onCheckedChange = { vm.saveZernioSettings(zernioKeyInput, it) }
                        )
                    }

                    // Status row driven by the SEALED-secret existence check, never a
                    // plaintext value (the key is a GitHub Actions secret and unreadable).
                    val statusText = when {
                        !settings.zernioKeyConfigured -> "No API key saved yet"
                        settings.zernioEnabled -> "Active — publishing enabled"
                        else -> "Key saved — publishing paused"
                    }
                    val statusColor = when {
                        !settings.zernioKeyConfigured -> MaterialTheme.colorScheme.onSurfaceVariant
                        settings.zernioEnabled -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                    AssistChip(onClick = {}, label = { Text(statusText, color = statusColor) })

                    if (settings.zernioKeyConfigured) {
                        Text(
                            "An API key is saved (stored securely as a GitHub Actions secret). Enter a new key below only to replace it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = zernioKeyInput,
                        onValueChange = { zernioKeyInput = it },
                        label = { Text(if (settings.zernioKeyConfigured) "Replace Zernio API Key" else "Zernio API Key") },
                        placeholder = { Text("zn_api_••••••••") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val zSaveBusy = busyOps.contains("save_zernio")
                        Button(
                            onClick = { vm.saveZernioSettings(zernioKeyInput, settings.zernioEnabled) },
                            enabled = !zSaveBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (zSaveBusy) { ButtonSpinner(); Spacer(Modifier.width(8.dp)) }
                            Text("Save")
                        }
                        if (settings.zernioKeyConfigured) {
                            OutlinedButton(
                                onClick = { showClearZernioDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Remove Key")
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // Connected-channels area is ALWAYS shown so the section is never "empty".
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Connected Channels", style = MaterialTheme.typography.titleSmall)
                        val zRefBusy = busyOps.contains("zernio_refresh")
                        TextButton(
                            onClick = { vm.refreshZernioAccounts() },
                            enabled = !zRefBusy
                        ) {
                            if (zRefBusy) { ButtonSpinner(); Spacer(Modifier.width(6.dp)) }
                            Text("Refresh")
                        }
                    }

                    if (settings.zernioAccounts.isEmpty()) {
                        Text(
                            "No connected accounts found. Connect accounts in the Telegram bot or on Zernio, then tap \u2018Refresh\u2019.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        settings.zernioAccounts.forEach { acc ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(acc.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        acc.platform.replaceFirstChar { it.uppercase() } + if (acc.username.isNotBlank()) " · @${acc.username}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val (stateLabel, stateColor) = when {
                                    acc.needsReconnection -> "Reconnect" to MaterialTheme.colorScheme.error
                                    !acc.isActive -> "Inactive" to MaterialTheme.colorScheme.onSurfaceVariant
                                    !acc.enabled -> "Disabled" to MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> "Active" to MaterialTheme.colorScheme.primary
                                }
                                Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = stateColor)
                            }
                        }
                    }
                }
            }
            // --- Section 7: About (fix #7) ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About ClipForge Android", style = MaterialTheme.typography.titleMedium)
                    Text("Version v20", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "A ForgeBuild client for the ClipForge pipeline (motionssalt/clipforge). Drive it from a Shadow Clone of the bot repository: it manages video tasks, production plans, music, narrator voices, series parts and Zernio publishing from your GitHub clone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider()
                    Text("Pipeline: stage-a.yml → stage-b.yml via GitHub Actions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Source: github.com/motionssalt/clipforge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Built with the ForgeBuild Engine (Material 3, dynamic color)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    // Session-10 fix #8: the exact-request diagnostic log (method, path,
                    // HTTP code, response excerpt, Contents-API create/update intent).
                    Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Every failed GitHub request is recorded on-device with its exact method, path and HTTP code. Export it when reporting an issue — the save picker lets you put it in Documents/ClipForge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val name = "clipforge-diagnostic-log-${System.currentTimeMillis() / 1000}.txt"
                                diagExportLauncher.launch(name)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export Diagnostic Log")
                        }
                        OutlinedButton(onClick = { vm.clearDiagLog() }, modifier = Modifier.weight(1f)) {
                            Text("Clear Log")
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialogs
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Repository") },
            text = { Text("Sign out and remove saved PAT credentials from this device?") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectDialog = false
                    vm.signOut()
                }) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteRepoDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteRepoDialog = false },
            title = { Text("Delete Clone Repository") },
            text = { Text("Permanently delete ${login?.slug} from GitHub? This action cannot be undone.") },
            confirmButton = {
                val delBusy = busyOps.contains("delete_clone")
                TextButton(
                    onClick = {
                        showDeleteRepoDialog = false
                        vm.deleteClone {}
                    },
                    enabled = !delBusy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (delBusy) { ButtonSpinner(); Spacer(Modifier.width(6.dp)) }
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRepoDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearZernioDialog) {
        AlertDialog(
            onDismissRequest = { showClearZernioDialog = false },
            title = { Text("Remove Zernio Key") },
            text = { Text("Remove the stored Zernio API key (the GitHub Actions secret) and disable publishing? Connected accounts are kept.") },
            confirmButton = {
                val clearBusy = busyOps.contains("clear_zernio")
                TextButton(
                    onClick = {
                        showClearZernioDialog = false
                        zernioKeyInput = ""
                        vm.clearZernioKey()
                    },
                    enabled = !clearBusy
                ) {
                    if (clearBusy) { ButtonSpinner(); Spacer(Modifier.width(6.dp)) }
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearZernioDialog = false }) { Text("Cancel") }
            }
        )
    }
}
