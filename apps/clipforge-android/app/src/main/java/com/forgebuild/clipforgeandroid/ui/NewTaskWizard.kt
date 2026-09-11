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
import com.forgebuild.engine.ui.icons.EngineIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskWizard(vm: ClipForgeViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val upload by vm.upload.collectAsState()
    val settings by vm.settings.collectAsState()
    val musicTracks by vm.music.collectAsState()
    val defaultMusic by vm.defaultMusic.collectAsState()

    val isOriginal = vm.api?.isOriginalRepo() == true

    var sourceKind by remember { mutableStateOf("url") }
    var sourceValue by remember { mutableStateOf("") }
    var torrentBytes by remember { mutableStateOf<ByteArray?>(null) }
    var torrentFileName by remember { mutableStateOf("") }

    var focus by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableStateOf(120) }
    var isSeries by remember { mutableStateOf(settings.seriesDefault) }
    var seriesId by remember { mutableStateOf("") }
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

    LaunchedEffect(settings.seriesDefault) {
        if (seriesId.isBlank()) {
            isSeries = settings.seriesDefault
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

                    // Source kind selection
                    Column {
                        listOf(
                            "url" to "Direct URL (HTTP/HTTPS)",
                            "drive" to "Google Drive Link",
                            "magnet" to "Magnet Link",
                            "torrent_file" to "Torrent File (.torrent, ≤1MB)"
                        ).plus(
                            if (isOriginal) listOf("telegram_channel" to "Telegram Channel Post (Official Repo only)")
                            else emptyList()
                        ).forEach { (kind, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = sourceKind == kind,
                                    onClick = { sourceKind = kind }
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }

                    if (sourceKind == "torrent_file") {
                        Button(
                            onClick = { torrentPicker.launch("application/x-bittorrent") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (torrentFileName.isNotBlank()) "Change .torrent file" else "Pick .torrent file")
                        }
                        if (torrentFileName.isNotBlank()) {
                            Text("Selected: $torrentFileName (${(torrentBytes?.size ?: 0) / 1024} KB)", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        OutlinedTextField(
                            value = sourceValue,
                            onValueChange = { sourceValue = it },
                            label = {
                                Text(when (sourceKind) {
                                    "url" -> "Direct Video URL"
                                    "drive" -> "Google Drive Share URL"
                                    "magnet" -> "Magnet URI"
                                    "telegram_channel" -> "t.me/channel/post_id"
                                    else -> "Source"
                                })
                            },
                            placeholder = {
                                Text(when (sourceKind) {
                                    "url" -> "https://example.com/video.mp4"
                                    "drive" -> "https://drive.google.com/file/d/..."
                                    "magnet" -> "magnet:?xt=urn:btih:..."
                                    "telegram_channel" -> "https://t.me/mychannel/123"
                                    else -> ""
                                })
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(60, 90, 120, 180).forEach { dur ->
                            FilterChip(
                                selected = durationSeconds == dur,
                                onClick = { durationSeconds = dur },
                                label = { Text("${dur}s") }
                            )
                        }
                    }
                }
            }

            // --- Music Selection ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Background Music", style = MaterialTheme.typography.titleMedium)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedMusicPath == null,
                            onClick = { selectedMusicPath = null }
                        )
                        Text("Default / None", modifier = Modifier.padding(start = 8.dp))
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
                            onCheckedChange = { isSeries = it }
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
                    }
                }
            }

            // --- Submit Button ---
            Button(
                onClick = {
                    if (sourceKind == "torrent_file" && (torrentBytes == null || torrentBytes!!.isEmpty())) {
                        vm.toast("Please select a .torrent file")
                        return@Button
                    }
                    if (sourceKind != "torrent_file" && sourceValue.isBlank()) {
                        vm.toast("Please enter a valid source URL")
                        return@Button
                    }

                    vm.createStageATask(
                        sourceKind = sourceKind,
                        sourceValue = sourceValue,
                        focus = focus,
                        targetDurationSeconds = durationSeconds,
                        selectedMusicPath = selectedMusicPath,
                        isSeries = isSeries,
                        seriesId = if (seriesId.isNotBlank()) seriesId else null,
                        torrentBytes = torrentBytes,
                        onCreated = {
                            onDone()
                        }
                    )
                },
                enabled = upload == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Video Analysis (Stage A)")
            }
        }
    }
}
