@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.forgebuild.clipforgeandroid.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.MusicTrack
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens

/** Music library (v22 task-123 rebuild): wavy engine progress everywhere, the
 *  shared busy-aware buttons for every action, tonal cards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(
    vm: ClipForgeViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val musicList by vm.music.collectAsState()
    val refreshing by vm.musicRefreshing.collectAsState()
    val defaultPath by vm.defaultMusic.collectAsState()
    val upload by vm.upload.collectAsState()
    val audioState by AudioPreview.state.collectAsState()
    val login by vm.login.collectAsState()
    LaunchedEffect(Unit) { vm.onMusicOpen() }

    var selectedTracks by remember { mutableStateOf(setOf<MusicTrack>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val trackPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
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

    DisposableEffect(Unit) { onDispose { AudioPreview.stop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTracks.isEmpty()) "Music Library" else "${selectedTracks.size} Selected") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ElevationTokens.tonalContainerColor(2)),
                navigationIcon = {
                    IconActionButton(EngineIcons.ArrowBack, "Back", onClick = onBack)
                },
                actions = {
                    if (selectedTracks.isNotEmpty()) {
                        IconActionButton(Icons.Default.Delete, "Delete selected", onClick = { showDeleteDialog = true })
                    } else {
                        IconActionButton(Icons.Default.Refresh, "Refresh", onClick = { vm.refreshMusic() }, busy = refreshing)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (refreshing) {
                EngineLinearWavyProgress(Modifier.fillMaxWidth())
            }

            upload?.let {
                Column(
                    Modifier.padding(SpacingTokens.Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs),
                ) {
                    Text(it.label, style = MaterialTheme.typography.bodySmall)
                    EngineLinearWavyProgress(progress = { it.fraction }, modifier = Modifier.fillMaxWidth())
                }
            }

            ActionButton(
                label = "Upload New Audio Track",
                onClick = { trackPicker.launch("audio/*") },
                busy = upload != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.Spacing.md, vertical = SpacingTokens.Spacing.xs),
            )

            if (musicList.isEmpty() && !refreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EngineEmptyState(
                        title = "No tracks yet",
                        body = "Upload an audio track to use as background music in your renders.",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(SpacingTokens.Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                ) {
                    items(musicList, key = { it.path }) { track ->
                        val isSelected = selectedTracks.contains(track)
                        val isDefault = defaultPath == track.path
                        val previewUrl = vm.audioPreviewUrl(track.path)
                        val isThisTrackActive = audioState.url == previewUrl

                        Card(
                            colors = if (isSelected)
                                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else CardDefaults.cardColors(containerColor = ElevationTokens.tonalContainerColor(1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .combinedClickable(
                                    onClick = {
                                        if (selectedTracks.isNotEmpty()) {
                                            selectedTracks = if (isSelected) selectedTracks - track else selectedTracks + track
                                        }
                                    },
                                    onLongClick = {
                                        selectedTracks = if (isSelected) selectedTracks - track else selectedTracks + track
                                    },
                                ),
                        ) {
                            Column(
                                Modifier.padding(SpacingTokens.Spacing.md - SpacingTokens.Spacing.xxs),
                                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(track.name, style = MaterialTheme.typography.titleSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                                            Text(
                                                "${track.size / 1024} KB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (isDefault) {
                                                Text(
                                                    "• Default Track",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }

                                    IconActionButton(
                                        icon = if (isThisTrackActive && audioState.isPlaying) EngineIcons.Pause else EngineIcons.Play,
                                        contentDescription = if (isThisTrackActive && audioState.isPlaying) "Pause" else "Play",
                                        busy = isThisTrackActive && audioState.isBuffering,
                                        onClick = {
                                            val pat = login?.pat ?: return@IconActionButton
                                            AudioPreview.toggle(context, previewUrl, pat)
                                        },
                                    )

                                    TextActionButton(
                                        label = if (isDefault) "Unset" else "Set Default",
                                        onClick = { vm.setDefaultMusic(if (isDefault) null else track.path) },
                                    )

                                    if (selectedTracks.isNotEmpty()) {
                                        Checkbox(checked = isSelected, onCheckedChange = {
                                            selectedTracks = if (isSelected) selectedTracks - track else selectedTracks + track
                                        })
                                    }
                                }

                                if (isThisTrackActive && (audioState.isPlaying || audioState.isPaused)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)) {
                                        EngineLinearWavyProgress(
                                            progress = {
                                                if (audioState.durationMs > 0)
                                                    (audioState.positionMs.toFloat() / audioState.durationMs).coerceIn(0f, 1f)
                                                else 0f
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                "${audioState.positionMs / 1000}s / ${audioState.durationMs / 1000}s",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (audioState.isPaused) {
                                                Text(
                                                    "Paused",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
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
                TextActionButton(label = "Delete", destructive = true, onClick = {
                    vm.deleteMusic(selectedTracks)
                    selectedTracks = emptySet()
                    showDeleteDialog = false
                })
            },
            dismissButton = {
                TextActionButton(label = "Cancel", onClick = { showDeleteDialog = false })
            },
        )
    }
}
