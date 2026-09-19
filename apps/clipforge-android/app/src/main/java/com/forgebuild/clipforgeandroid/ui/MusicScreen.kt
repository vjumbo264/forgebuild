@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.forgebuild.clipforgeandroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.MusicTrack
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens

/* ============================================================================
 *  V23 MUSIC LIBRARY — rebuilt from scratch on the expressive stack.
 *  Upload, streamed cached previews, default-track assignment, multi-select
 *  delete — all through busy-aware action buttons.
 * ============================================================================ */

@Composable
fun MusicScreen(vm: ClipForgeViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val musicList by vm.music.collectAsState()
    val refreshing by vm.musicRefreshing.collectAsState()
    val defaultPath by vm.defaultMusic.collectAsState()
    val upload by vm.upload.collectAsState()
    val audioState by AudioPreview.state.collectAsState()
    val login by vm.login.collectAsState()

    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.onMusicOpen() }
    DisposableEffect(Unit) { onDispose { AudioPreview.stop() } }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedPaths.isEmpty()) "Music library" else "${selectedPaths.size} selected") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ElevationTokens.tonalContainerColor(2)),
                navigationIcon = { IconActionButton(EngineIcons.ArrowBack, "Back", onClick = onBack) },
                actions = {
                    if (selectedPaths.isNotEmpty()) {
                        IconActionButton(EngineIcons.Cancel, "Delete selected", onClick = { showDeleteDialog = true })
                    } else {
                        IconActionButton(EngineIcons.Restart, "Refresh", onClick = { vm.refreshMusic() }, busy = refreshing)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (refreshing) com.forgebuild.engine.ui.components.EngineLinearWavyProgress(Modifier.fillMaxWidth())

            upload?.let {
                Box(Modifier.padding(SpacingTokens.Spacing.md)) { CfProgress(label = it.label, fraction = it.fraction) }
            }

            ActionButton(
                label = "Upload new audio track",
                icon = EngineIcons.Add,
                onClick = { trackPicker.launch("audio/*") },
                busy = upload != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.Spacing.md, vertical = SpacingTokens.Spacing.xs),
            )

            if (musicList.isEmpty() && !refreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CfEmptyState(title = "No tracks yet", body = "Upload an audio track to use as background music in your renders.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(SpacingTokens.Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
                ) {
                    items(musicList, key = { it.path }) { track: MusicTrack ->
                        val isSelected = selectedPaths.contains(track.path)
                        val isDefault = defaultPath == track.path
                        val previewUrl = vm.audioPreviewUrl(track.path)
                        val isThisActive = audioState.url == previewUrl

                        CfCard(
                            tonalLevel = if (isSelected) 3 else 1,
                            onClick = {
                                if (selectedPaths.isNotEmpty())
                                    selectedPaths = if (isSelected) selectedPaths - track.path else selectedPaths + track.path
                            },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(track.name, style = MaterialTheme.typography.titleSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                                            Text(
                                                "${track.size / 1024} KB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (isDefault) {
                                                Text("Default track", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                    IconActionButton(
                                        icon = if (isThisActive && audioState.isPlaying) EngineIcons.Pause else EngineIcons.Play,
                                        contentDescription = if (isThisActive && audioState.isPlaying) "Pause" else "Play preview",
                                        busy = isThisActive && audioState.isBuffering,
                                        onClick = {
                                            val pat = login?.pat ?: return@IconActionButton
                                            AudioPreview.toggle(context, previewUrl, pat)
                                        },
                                    )
                                    TextActionButton(
                                        label = if (isDefault) "Unset" else "Set default",
                                        onClick = { vm.setDefaultMusic(if (isDefault) null else track.path) },
                                    )
                                    if (selectedPaths.isNotEmpty()) {
                                        Checkbox(checked = isSelected, onCheckedChange = {
                                            selectedPaths = if (isSelected) selectedPaths - track.path else selectedPaths + track.path
                                        })
                                    }
                                }
                                if (isThisActive && (audioState.isPlaying || audioState.isPaused)) {
                                    CfProgress(
                                        label = "${audioState.positionMs / 1000}s / ${audioState.durationMs / 1000}s" +
                                            if (audioState.isPaused) " — paused" else "",
                                        fraction = if (audioState.durationMs > 0)
                                            (audioState.positionMs.toFloat() / audioState.durationMs).coerceIn(0f, 1f) else 0f,
                                    )
                                }
                                if (selectedPaths.isEmpty()) {
                                    TextActionButton(
                                        label = "Select",
                                        onClick = { selectedPaths = selectedPaths + track.path },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        val doomed = musicList.filter { selectedPaths.contains(it.path) }.toSet()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete tracks") },
            text = { Text("Delete ${doomed.size} audio track(s) from the repository?") },
            confirmButton = {
                TextActionButton(label = "Delete", destructive = true, onClick = {
                    vm.deleteMusic(doomed)
                    selectedPaths = emptySet()
                    showDeleteDialog = false
                })
            },
            dismissButton = { TextActionButton(label = "Cancel", onClick = { showDeleteDialog = false }) },
        )
    }
}
