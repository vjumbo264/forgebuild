package com.forgebuild.clipforgeandroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.Voices
import com.forgebuild.engine.ui.icons.EngineIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: ClipForgeViewModel,
    onOpenMusic: () -> Unit
) {
    val login by vm.login.collectAsState()
    val settings by vm.settings.collectAsState()
    val defaultMusic by vm.defaultMusic.collectAsState()

    val isOriginal = vm.api?.isOriginalRepo() == true

    var showDeleteRepoDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showClearZernioDialog by remember { mutableStateOf(false) }

    var watermarkInput by remember(settings.watermarkText) { mutableStateOf(settings.watermarkText) }
    var zernioKeyInput by remember(settings.zernioApiKey) { mutableStateOf(settings.zernioApiKey) }
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
                            OutlinedButton(onClick = { vm.toggleRepoVisibility() }) {
                                Text(if (settings.isPrivate) "Make Public" else "Make Private")
                            }
                        }

                        Button(
                            onClick = { vm.syncFromSource() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
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

                            Button(
                                onClick = { vm.pushUpdateToClones() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Push Update to Clones")
                            }

                            OutlinedTextField(
                                value = newsInput,
                                onValueChange = { newsInput = it },
                                label = { Text("Broadcast News to Clones") },
                                placeholder = { Text("e.g. New model update live!") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (newsInput.isNotBlank()) {
                                        vm.pushNews(newsInput)
                                        newsInput = ""
                                    }
                                },
                                enabled = newsInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Broadcast News")
                            }
                        }
                    }
                }
            }

            // --- Section 2: Narrator Voice (TTS) ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Narrator Voice (Edge TTS)", style = MaterialTheme.typography.titleMedium)
                    Text("Select the voice used for spoken voiceovers across generated clips.", style = MaterialTheme.typography.bodySmall)

                    Voices.ALL.forEach { voice ->
                        val isSelected = settings.narratorVoice == voice.id
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
                            Column(Modifier.padding(start = 8.dp)) {
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
                        Button(
                            onClick = { vm.setWatermark(watermarkInput) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Watermark")
                        }
                        if (settings.watermarkText.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    vm.clearWatermark()
                                    watermarkInput = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear")
                            }
                        }
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
                        Switch(
                            checked = settings.seriesDefault,
                            onCheckedChange = { vm.setSeriesDefault(it) }
                        )
                    }
                }
            }

            // --- Section 6: Zernio Publishing ---
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

                    OutlinedTextField(
                        value = zernioKeyInput,
                        onValueChange = { zernioKeyInput = it },
                        label = { Text("Zernio API Key") },
                        placeholder = { Text("zn_api_••••••••") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { vm.saveZernioSettings(zernioKeyInput, settings.zernioEnabled) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Key")
                        }
                        if (settings.zernioApiKey.isNotBlank()) {
                            OutlinedButton(
                                onClick = { showClearZernioDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear Key")
                            }
                        }
                    }

                    if (settings.zernioAccounts.isNotEmpty()) {
                        Text("Connected Channels:", style = MaterialTheme.typography.titleSmall)
                        settings.zernioAccounts.forEach { acc ->
                            Text("• $acc", style = MaterialTheme.typography.bodySmall)
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
                TextButton(
                    onClick = {
                        showDeleteRepoDialog = false
                        vm.deleteClone {}
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Permanently") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRepoDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearZernioDialog) {
        AlertDialog(
            onDismissRequest = { showClearZernioDialog = false },
            title = { Text("Clear Zernio Key") },
            text = { Text("Clear stored Zernio API key and disable publishing?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearZernioDialog = false
                    zernioKeyInput = ""
                    vm.clearZernioKey()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearZernioDialog = false }) { Text("Cancel") }
            }
        )
    }
}
