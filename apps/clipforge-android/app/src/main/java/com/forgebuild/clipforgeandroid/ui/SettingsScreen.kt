@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.forgebuild.clipforgeandroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.DiagLog
import com.forgebuild.clipforgeandroid.data.Voices
import com.forgebuild.clipforgeandroid.data.ZernioSettings
import com.forgebuild.engine.files.SafeSave
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens
import kotlinx.coroutines.launch

/* ============================================================================
 *  V23 SETTINGS — every saved setting readable + writable in one fresh screen:
 *  clone management, narrator voice (with previews), music library, watermark,
 *  Series Mode + Super Series defaults, the FULL Zernio publishing surface
 *  (auto-publish, mode, interval, preferred time, timezone, queue depth,
 *  start mode, custom first slot, per-platform target accounts), About +
 *  diagnostics export. All settings keep their existing storage shapes, so
 *  nothing the operator already saved is lost.
 * ============================================================================ */

@Composable
fun SettingsScreen(vm: ClipForgeViewModel, onOpenMusic: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val login by vm.login.collectAsState()
    val settings by vm.settings.collectAsState()
    val defaultMusic by vm.defaultMusic.collectAsState()
    val settingsLoading by vm.settingsLoading.collectAsState()
    val settingsLoaded by vm.settingsLoaded.collectAsState()
    val audioState by AudioPreview.state.collectAsState()

    val diagExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            try {
                SafeSave.writeBytes(context, uri, DiagLog.exportText(context.applicationContext).toByteArray(Charsets.UTF_8))
                vm.toast("Diagnostic log saved (${uri.lastPathSegment ?: "file"})")
            } catch (_: Exception) { vm.toast("Could not write the diagnostic log") }
        }
    }

    val isOriginal = vm.api?.isOriginalRepo() == true

    LaunchedEffect(Unit) { vm.loadSettings() }
    DisposableEffect(Unit) { onDispose { AudioPreview.stop() } }

    var showDeleteRepoDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showClearZernioDialog by remember { mutableStateOf(false) }
    var watermarkInput by remember(settings.watermarkText) { mutableStateOf(settings.watermarkText) }
    var zernioKeyInput by remember { mutableStateOf("") }
    var newsInput by remember { mutableStateOf("") }

    // ---- Full Zernio settings editor state (v23-R6) ----
    var zFull by remember { mutableStateOf<ZernioSettings.Settings?>(null) }
    var zFullError by remember { mutableStateOf<String?>(null) }
    var zSaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { zFull = vm.readZernioFull() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            if (settingsLoading && !settingsLoaded) {
                CfCard(tonalLevel = 2) { CfLoading("Loading your saved settings…") }
            }

            // ---- GitHub clone ----
            CfSection(title = "GitHub clone repository") {
                login?.let { creds ->
                    Text(creds.slug, style = MaterialTheme.typography.titleSmall)
                    Text("Signed in as ${creds.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        CfChip(
                            label = if (settings.isPrivate) "Private" else "Public",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextActionButton(
                            label = if (settings.isPrivate) "Make public" else "Make private",
                            busy = busyOf(vm, "toggle_visibility"),
                            onClick = { vm.toggleRepoVisibility() },
                        )
                    }
                    TonalActionButton(
                        label = "Sync from motionssalt/clipforge",
                        busy = busyOf(vm, "sync_from_source"),
                        onClick = { vm.syncFromSource() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                        OutlinedActionButton(label = "Disconnect", onClick = { showDisconnectDialog = true }, modifier = Modifier.weight(1f))
                        OutlinedActionButton(label = "Delete clone", destructive = true, onClick = { showDeleteRepoDialog = true }, modifier = Modifier.weight(1f))
                    }
                    if (isOriginal) {
                        Text("Owner controls", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        TonalActionButton(
                            label = "Push update to clones",
                            busy = busyOf(vm, "push_update"),
                            onClick = { vm.pushUpdateToClones() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newsInput, onValueChange = { newsInput = it },
                            label = { Text("Broadcast news to clones") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ActionButton(
                            label = "Broadcast news",
                            busy = busyOf(vm, "push_news"),
                            enabled = newsInput.isNotBlank(),
                            onClick = { vm.pushNews(newsInput); newsInput = "" },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ---- Narrator voice ----
            CfSection(title = "Narrator voice (Edge TTS)", subtitle = "Preview a voice before choosing it.") {
                Voices.ALL.forEach { voice ->
                    val isSelected = settings.narratorVoice == voice.id
                    val previewUrl = vm.audioPreviewUrl("assets/tts-previews/${voice.id}.mp3")
                    val thisActive = audioState.url == previewUrl
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busyOf(vm, "save_narrator")) { vm.setNarratorVoice(voice.id) },
                    ) {
                        RadioButton(selected = isSelected, onClick = { vm.setNarratorVoice(voice.id) })
                        Column(Modifier.weight(1f).padding(start = SpacingTokens.Spacing.xs)) {
                            Text("${voice.label} (${voice.gender})", style = MaterialTheme.typography.bodyMedium)
                            Text(voice.style, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconActionButton(
                            icon = if (thisActive && audioState.isPlaying) EngineIcons.Pause else EngineIcons.Play,
                            contentDescription = "Preview ${voice.label}",
                            busy = thisActive && audioState.isBuffering,
                            onClick = { vm.previewVoice(voice.id) },
                        )
                    }
                }
            }

            // ---- Music library ----
            CfSection(title = "Music library", subtitle = "Default track: ${defaultMusic ?: "none (silence)"}") {
                ActionButton(label = "Manage music & tracks", onClick = onOpenMusic, modifier = Modifier.fillMaxWidth())
            }

            // ---- Watermark ----
            CfSection(title = "Creator watermark", subtitle = "Burn a handle onto every rendered video.") {
                OutlinedTextField(
                    value = watermarkInput, onValueChange = { watermarkInput = it },
                    label = { Text("Watermark text / handle") },
                    placeholder = { Text("@your_channel") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    ActionButton(
                        label = "Save watermark",
                        busy = busyOf(vm, "save_watermark"),
                        onClick = { vm.setWatermark(watermarkInput) },
                        modifier = Modifier.weight(1f),
                    )
                    if (settings.watermarkText.isNotBlank()) {
                        OutlinedActionButton(
                            label = "Clear",
                            busy = busyOf(vm, "save_watermark"),
                            onClick = { vm.clearWatermark(); watermarkInput = "" },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---- Series + Super Series defaults ----
            CfSection(title = "Series Mode default", subtitle = "Automatically enable Series Mode on new tasks.") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Series Mode", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.seriesDefault,
                        enabled = !busyOf(vm, "save_series_default"),
                        onCheckedChange = { vm.setSeriesDefaultWithDependency(it) },
                    )
                }
            }
            CfSection(title = "Super Series default", subtitle = "Whole-series planning. Requires Series Mode — it is forced off when Series Mode is off.") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Super Series", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.superSeriesDefault && settings.seriesDefault,
                        enabled = settings.seriesDefault && !busyOf(vm, "save_super"),
                        onCheckedChange = { vm.setSuperSeriesDefault(it) },
                    )
                }
                if (!settings.seriesDefault) {
                    Text("Turn Series Mode on to enable Super Series.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- ZERNIO PUBLISHING (full surface, v23-R6) ----
            CfSection(title = "Zernio publishing", subtitle = "Publish rendered clips to your connected social accounts.") {
                val zStatusText = when {
                    !settings.zernioKeyConfigured -> "No API key saved yet"
                    settings.zernioEnabled -> "Active — publishing enabled"
                    else -> "Key saved — publishing paused"
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    CfChip(
                        label = zStatusText,
                        color = when {
                            !settings.zernioKeyConfigured -> MaterialTheme.colorScheme.onSurfaceVariant
                            settings.zernioEnabled -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.secondary
                        },
                    )
                    Switch(
                        checked = settings.zernioEnabled,
                        enabled = !busyOf(vm, "save_zernio"),
                        onCheckedChange = { vm.saveZernioSettings(zernioKeyInput, it) },
                    )
                }
                OutlinedTextField(
                    value = zernioKeyInput, onValueChange = { zernioKeyInput = it },
                    label = { Text(if (settings.zernioKeyConfigured) "Replace Zernio API key" else "Zernio API key") },
                    placeholder = { Text("zn_api_…") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    ActionButton(
                        label = "Save key",
                        busy = busyOf(vm, "save_zernio"),
                        enabled = zernioKeyInput.isNotBlank(),
                        onClick = { vm.saveZernioSettings(zernioKeyInput, settings.zernioEnabled); zernioKeyInput = "" },
                        modifier = Modifier.weight(1f),
                    )
                    if (settings.zernioKeyConfigured) {
                        OutlinedActionButton(
                            label = "Remove key", destructive = true,
                            onClick = { showClearZernioDialog = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Live summary of the full stored schedule.
                zFull?.let { z ->
                    Text(ZernioSettings.summary(z), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                // Full schedule editor.
                zFull?.let { z ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Automatic publishing", style = MaterialTheme.typography.bodyMedium)
                            Text("Publish each finished video without manual action", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = z.autoPublish, onCheckedChange = { zFull = z.copy(autoPublish = it) })
                    }

                    Text("Automatic mode", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                        FilterChip(
                            selected = z.automaticMode == "publish_now",
                            onClick = { zFull = z.copy(automaticMode = "publish_now") },
                            label = { Text("Publish immediately") },
                        )
                        FilterChip(
                            selected = z.automaticMode == "smart_schedule",
                            onClick = { zFull = z.copy(automaticMode = "smart_schedule") },
                            label = { Text("Smart schedule") },
                        )
                    }

                    if (z.automaticMode == "smart_schedule") {
                        Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                            OutlinedTextField(
                                value = z.smart.intervalHours.toString(),
                                onValueChange = { v -> zFull = z.copy(smart = z.smart.copy(intervalHours = v.filter { c -> c.isDigit() }.toIntOrNull() ?: 0)) },
                                label = { Text("Interval (hours)") },
                                supportingText = { Text("1–8760") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = z.smart.preferredTime,
                                onValueChange = { v -> zFull = z.copy(smart = z.smart.copy(preferredTime = v.take(5))) },
                                label = { Text("Preferred time") },
                                placeholder = { Text("05:00") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = z.smart.queueDepth.toString(),
                            onValueChange = { v -> zFull = z.copy(smart = z.smart.copy(queueDepth = v.filter { c -> c.isDigit() }.toIntOrNull() ?: 0)) },
                            label = { Text("Queue depth") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Searchable picker over the FULL genuine IANA list (java.time
                        // tz database — the same family the pipeline's zoneinfo uses).
                        // Free text is rejected at save time by ZernioSettings.validate.
                        val allZones = remember { ZernioSettings.ianaZones() }
                        var tzQuery by remember { mutableStateOf(z.smart.timezone) }
                        var tzExpanded by remember { mutableStateOf(false) }
                        val tzMatches = remember(tzQuery) {
                            val q = tzQuery.trim().lowercase()
                            if (q.isEmpty() || q == "utc") allZones.take(60)
                            else allZones.filter { it.lowercase().contains(q) }.take(60)
                        }
                        androidx.compose.foundation.layout.Box {
                            OutlinedTextField(
                                value = tzQuery,
                                onValueChange = { v ->
                                    tzQuery = v
                                    tzExpanded = true
                                    zFull = z.copy(smart = z.smart.copy(timezone = v.trim()))
                                },
                                label = { Text("Timezone (IANA)") },
                                placeholder = { Text("Type to search, e.g. Africa/Lagos") },
                                supportingText = {
                                    if (tzQuery.isNotBlank() && !ZernioSettings.isValidIanaZone(tzQuery)) {
                                        Text("Unknown IANA timezone: ${tzQuery.trim()}",
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                isError = tzQuery.isNotBlank() && !ZernioSettings.isValidIanaZone(tzQuery),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            androidx.compose.material3.DropdownMenu(
                                expanded = tzExpanded && tzMatches.isNotEmpty(),
                                onDismissRequest = { tzExpanded = false },
                            ) {
                                tzMatches.forEach { zone ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(zone) },
                                        onClick = {
                                            tzQuery = zone
                                            tzExpanded = false
                                            zFull = z.copy(smart = z.smart.copy(timezone = zone))
                                        },
                                    )
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                            ZernioSettings.COMMON_TIMEZONES.take(8).forEach { zone ->
                                FilterChip(
                                    selected = z.smart.timezone == zone,
                                    onClick = {
                                        tzQuery = zone
                                        zFull = z.copy(smart = z.smart.copy(timezone = zone))
                                    },
                                    label = { Text(zone, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                        Text("Start mode", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                            FilterChip(
                                selected = z.smart.startMode == "next_available",
                                onClick = { zFull = z.copy(smart = z.smart.copy(startMode = "next_available")) },
                                label = { Text("Next available slot") },
                            )
                            FilterChip(
                                selected = z.smart.startMode == "custom",
                                onClick = { zFull = z.copy(smart = z.smart.copy(startMode = "custom")) },
                                label = { Text("Custom first slot") },
                            )
                        }
                        if (z.smart.startMode == "custom") {
                            OutlinedTextField(
                                value = z.smart.customStart,
                                onValueChange = { v -> zFull = z.copy(smart = z.smart.copy(customStart = v.take(16))) },
                                label = { Text("First slot (YYYY-MM-DDTHH:MM)") },
                                placeholder = { Text("2026-09-20T17:30") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Per-platform target accounts.
                    if (settings.zernioAccounts.isNotEmpty()) {
                        Text("Target accounts", style = MaterialTheme.typography.bodyMedium)
                        ZernioSettings.PLATFORMS.forEach { platform ->
                            val accounts = settings.zernioAccounts.filter { it.platform == platform }
                            if (accounts.isNotEmpty()) {
                                Text(platform.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                accounts.forEach { acc ->
                                    val checked = z.targetAccounts[platform]?.contains(acc.id) == true
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = acc.available) {
                                                val cur = z.targetAccounts[platform].orEmpty()
                                                val next = if (checked) cur - acc.id else cur + acc.id
                                                zFull = z.copy(targetAccounts = z.targetAccounts + (platform to next))
                                            },
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            enabled = acc.available,
                                            onCheckedChange = {
                                                val cur = z.targetAccounts[platform].orEmpty()
                                                val next = if (checked) cur - acc.id else cur + acc.id
                                                zFull = z.copy(targetAccounts = z.targetAccounts + (platform to next))
                                            },
                                        )
                                        Column(Modifier.padding(start = SpacingTokens.Spacing.xs)) {
                                            Text(acc.label, style = MaterialTheme.typography.bodyMedium)
                                            if (!acc.available) {
                                                Text(
                                                    if (acc.needsReconnection) "Needs reconnection" else "Unavailable",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    zFullError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    ActionButton(
                        label = "Save publishing settings",
                        busy = zSaving,
                        onClick = {
                            val candidate = zFull ?: return@ActionButton
                            zSaving = true
                            scope.launch {
                                val err = vm.saveZernioFull(candidate)
                                zSaving = false
                                zFullError = err
                                if (err == null) vm.toast("Publishing settings saved")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } ?: CfLoading("Loading publishing settings…")

                // Connected accounts.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Connected channels", style = MaterialTheme.typography.titleSmall)
                    TextActionButton(label = "Refresh", busy = busyOf(vm, "zernio_refresh"), onClick = { vm.refreshZernioAccounts() })
                }
                if (settings.zernioAccounts.isEmpty()) {
                    Text(
                        "No connected accounts found. Connect accounts on Zernio, then tap Refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    settings.zernioAccounts.forEach { acc ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(acc.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    acc.platform.replaceFirstChar { it.uppercase() } + if (acc.username.isNotBlank()) " · @${acc.username}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            CfChip(
                                label = when {
                                    acc.needsReconnection -> "Reconnect"
                                    !acc.isActive -> "Inactive"
                                    !acc.enabled -> "Disabled"
                                    else -> "Active"
                                },
                                color = if (acc.needsReconnection || !acc.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            // ---- About + diagnostics ----
            CfSection(title = "About ClipForge Android") {
                Text("Version v23", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "A ForgeBuild client for the ClipForge pipeline. It drives your GitHub clone: video tasks, production plans, music, narrator voices, series and Zernio publishing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Pipeline: stage-a.yml → stage-b.yml via GitHub Actions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Built with the ForgeBuild Engine (Material 3 Expressive)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Every failed GitHub request is recorded on-device. Export it when reporting an issue — you pick where to save it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    OutlinedActionButton(
                        label = "Export diagnostic log",
                        onClick = { diagExportLauncher.launch("clipforge-diagnostic-log-${System.currentTimeMillis() / 1000}.txt") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedActionButton(label = "Clear", onClick = { vm.clearDiagLog() }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // ---- dialogs ----
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect repository") },
            text = { Text("Sign out and remove the saved credentials from this device?") },
            confirmButton = { TextActionButton(label = "Disconnect", destructive = true, onClick = { showDisconnectDialog = false; vm.signOut() }) },
            dismissButton = { TextActionButton(label = "Cancel", onClick = { showDisconnectDialog = false }) },
        )
    }
    if (showDeleteRepoDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteRepoDialog = false },
            title = { Text("Delete clone repository") },
            text = { Text("Permanently delete ${login?.slug} from GitHub? This cannot be undone.") },
            confirmButton = {
                TextActionButton(label = "Delete permanently", destructive = true, busy = busyOf(vm, "delete_clone"), onClick = {
                    showDeleteRepoDialog = false
                    vm.deleteClone {}
                })
            },
            dismissButton = { TextActionButton(label = "Cancel", onClick = { showDeleteRepoDialog = false }) },
        )
    }
    if (showClearZernioDialog) {
        AlertDialog(
            onDismissRequest = { showClearZernioDialog = false },
            title = { Text("Remove Zernio key") },
            text = { Text("Remove the stored Zernio API key and disable publishing? Connected accounts are kept.") },
            confirmButton = {
                TextActionButton(label = "Remove", destructive = true, busy = busyOf(vm, "clear_zernio"), onClick = {
                    showClearZernioDialog = false
                    zernioKeyInput = ""
                    vm.clearZernioKey()
                })
            },
            dismissButton = { TextActionButton(label = "Cancel", onClick = { showClearZernioDialog = false }) },
        )
    }
}
