package com.forgebuild.clipforgeandroid.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.MusicTrack
import com.forgebuild.engine.ui.icons.EngineIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(
    vm: ClipForgeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val musicList by vm.music.collectAsState()
    val refreshing by vm.musicRefreshing.collectAsState()
    val defaultPath by vm.defaultMusic.collectAsState()
    val upload by vm.upload.collectAsState()
    val audioState by AudioPreview.state.collectAsState()
    val login by vm.login.collectAsState()

    var selectedTracks by remember { mutableStateOf(setOf<MusicTrack>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val trackPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "audio_track.m4a"
                vm.uploadMusic(fileName, bytes)
            }
        } catch (e: Exception) {
            vm.toast("Failed to read audio file: ${e.message}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AudioPreview.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectedTracks.isEmpty()) "Music Library" else "${selectedTracks.size} Selected")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(EngineIcons.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (selectedTracks.isNotEmpty()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete selected")
                        }
                    } else {
                        IconButton(onClick = { vm.refreshMusic() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            if (refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            upload?.let {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(it.label, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { it.fraction }, modifier = Modifier.fillMaxWidth())
                }
            }

            // Upload button
            PaddingValues(16.dp).let {
                Button(
                    onClick = { trackPicker.launch("audio/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Upload New Audio Track")
                }
            }

            if (musicList.isEmpty() && !refreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tracks found in audio-library/.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(musicList, key = { it.path }) { track ->
                        val isSelected = selectedTracks.contains(track)
                        val isDefault = defaultPath == track.path
                        val previewUrl = vm.audioPreviewUrl(track.path)
                        val isThisTrackActive = audioState.url == previewUrl

                        Card(
                            colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else CardDefaults.cardColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectedTracks.isNotEmpty()) {
                                            selectedTracks = if (isSelected) selectedTracks - track else selectedTracks + track
                                        }
                                    },
                                    onLongClick = {
                                        selectedTracks = if (isSelected) selectedTracks - track else selectedTracks + track
                                    }
                                )
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(track.name, style = MaterialTheme.typography.titleSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("${track.size / 1024} KB", style = MaterialTheme.typography.bodySmall)
                                            if (isDefault) {
                                                Text("• Default Track", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }

                                    // Play / Pause button
                                    IconButton(
                                        onClick = {
                                            val pat = login?.pat ?: return@IconButton
                                            AudioPreview.toggle(context, previewUrl, pat)
                                        }
                                    ) {
                                        if (isThisTrackActive && audioState.isPlaying) {
                                            Icon(EngineIcons.Pause, "Pause")
                                        } else {
                                            Icon(EngineIcons.Play, "Play")
                                        }
                                    }

                                    // Set default track button
                                    TextButton(
                                        onClick = {
                                            vm.setDefaultMusic(if (isDefault) null else track.path)
                                        }
                                    ) {
                                        Text(if (isDefault) "Unset" else "Set Default")
                                    }

                                    if (selectedTracks.isNotEmpty()) {
                                        Checkbox(checked = isSelected, onCheckedChange = {
                                            selectedTracks = if (isSelected) selectedTracks - track else selectedTracks + track
                                        })
                                    }
                                }

                                // Audio playback progress indicator
                                if (isThisTrackActive && (audioState.isPlaying || audioState.isPaused)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        LinearProgressIndicator(
                                            progress = {
                                                if (audioState.durationMs > 0)
                                                    (audioState.positionMs.toFloat() / audioState.durationMs).coerceIn(0f, 1f)
                                                else 0f
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${audioState.positionMs / 1000}s / ${audioState.durationMs / 1000}s",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            if (audioState.isPaused) {
                                                Text("Paused", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Tracks") },
            text = { Text("Delete ${selectedTracks.size} audio track(s) from the repository?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMusic(selectedTracks)
                    selectedTracks = emptySet()
                    showDeleteDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
