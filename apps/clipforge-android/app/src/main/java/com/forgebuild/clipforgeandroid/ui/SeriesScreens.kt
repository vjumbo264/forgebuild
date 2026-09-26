@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.forgebuild.clipforgeandroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.Pipeline
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens

/* ============================================================================
 *  V23 SERIES — overview groups (with Super Series queue/halt badges and
 *  hold-to-delete) and the per-series ordered parts screen. Rebuilt fresh.
 * ============================================================================ */

@Composable
fun SeriesScreen(
    vm: ClipForgeViewModel,
    onSelectSeries: (String) -> Unit,
    onSelectTask: (String) -> Unit,
) {
    LaunchedEffect(Unit) { vm.onTasksOpen(); vm.refreshSuperQueues() }
    val tasks by vm.tasks.collectAsState()
    val refreshing by vm.tasksRefreshing.collectAsState()
    val superQueues by vm.superQueues.collectAsState()
    var seriesPendingDelete by remember { mutableStateOf<String?>(null) }

    seriesPendingDelete?.let { sid ->
        AlertDialog(
            onDismissRequest = { seriesPendingDelete = null },
            title = { Text("Delete series") },
            text = { Text("Delete the entire series \"$sid\" and every part belonging to it? This cannot be undone.") },
            confirmButton = {
                TextActionButton(label = "Delete permanently", destructive = true, onClick = {
                    seriesPendingDelete = null
                    vm.deleteSeries(sid)
                })
            },
            dismissButton = { TextActionButton(label = "Cancel", onClick = { seriesPendingDelete = null }) },
        )
    }

    val seriesGroups = remember(tasks) {
        tasks.filter { it.seriesEnabled && it.seriesId.isNotBlank() }.groupBy { it.seriesId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Series") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ElevationTokens.tonalContainerColor(2)),
                actions = { IconActionButton(EngineIcons.Restart, "Refresh", onClick = { vm.refreshTasks() }, busy = refreshing) },
            )
        },
    ) { pad ->
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            if (refreshing) EngineLinearWavyProgress(Modifier.fillMaxWidth())
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refreshTasks() }, modifier = Modifier.fillMaxSize()) {
                if (seriesGroups.isEmpty() && !refreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CfEmptyState(title = "No series yet", body = "Start a series from the New Video tab.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPaddingForFloatingBar(),
                        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
                    ) {
                        items(seriesGroups.entries.toList(), key = { it.key }) { (seriesId, partTasks) ->
                            val sortedParts = partTasks.sortedBy { it.part }
                            val latestPart = sortedParts.lastOrNull()
                            androidx.compose.material3.Surface(
                                shape = MaterialTheme.shapes.large,
                                color = ElevationTokens.tonalContainerColor(1),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .combinedClickable(
                                        onClick = { onSelectSeries(seriesId) },
                                        onLongClick = { seriesPendingDelete = seriesId },
                                    ),
                            ) {
                                Column(
                                    Modifier.padding(SpacingTokens.Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                                ) {
                                    Text(seriesId, style = MaterialTheme.typography.titleMedium)
                                    superQueues[seriesId]?.let { q ->
                                        CfChip(
                                            label = "Super Series — " + q.label,
                                            color = if (q.halted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                                        CfChip(label = "${sortedParts.size} part(s)", color = MaterialTheme.colorScheme.secondary)
                                        latestPart?.let {
                                            CfChip(label = "Latest: Part ${it.part} — ${Pipeline.describe(it.state)}", color = cfStateColor(it.state))
                                        }
                                    }
                                    sortedParts.forEach { part ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = SpacingTokens.Spacing.xxs),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(if (part.jobId == superQueues[seriesId]?.anchorJobId) "Super Series plan" else "Part ${part.part}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            TextActionButton(
                                                label = Pipeline.describe(part.state),
                                                onClick = { onSelectTask(part.jobId) },
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

@Composable
fun SeriesDetailScreen(
    vm: ClipForgeViewModel,
    seriesId: String,
    onBack: () -> Unit,
    onSelectTask: (String) -> Unit,
) {
    LaunchedEffect(Unit) { vm.onTasksOpen(); vm.loadSuperQueue(seriesId) }
    val tasks by vm.tasks.collectAsState()
    val superQueue by vm.superQueue.collectAsState()
    val playVideo by vm.playVideoUri.collectAsState()
    playVideo?.let { (uri, title) -> VideoPlayerDialog(uriString = uri, title = title, onDismiss = { vm.dismissVideoPlayer() }) }

    val downloads = remember(seriesId) { vm.seriesDownloads(seriesId) }
    val seriesParts = remember(tasks, seriesId) {
        tasks.filter { it.seriesEnabled && it.seriesId == seriesId }
            .sortedWith(compareBy({ if (it.jobId == superQueue?.anchorJobId) 0 else 1 }, { it.part }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seriesId, style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ElevationTokens.tonalContainerColor(2)),
                navigationIcon = { IconActionButton(EngineIcons.ArrowBack, "Back", onClick = onBack) },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = contentPaddingForFloatingBar(),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
        ) {
            superQueue?.let { q ->
                item {
                    CfSection(title = "Super Series", subtitle = "Parts dispatch automatically; a halted chain resumes by restarting the failed part.") {
                        CfChip(label = q.label, color = if (q.halted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (downloads.isNotEmpty()) {
                item {
                    CfSection(title = "Downloaded on this device") {
                        downloads.forEach { rec ->
                            OutlinedActionButton(
                                label = "Play ${rec.optString("name")}",
                                icon = EngineIcons.Play,
                                onClick = { vm.playVideoFromRegistry(rec) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            item { Text("Parts", style = MaterialTheme.typography.titleSmall) }
            items(seriesParts, key = { it.jobId }) { part ->
                CfCard(onClick = { onSelectTask(part.jobId) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                        Text(if (part.jobId == superQueue?.anchorJobId) "Super Series plan" else "Part ${part.part}", style = MaterialTheme.typography.titleSmall)
                        CfChip(label = Pipeline.describe(part.state), color = cfStateColor(part.state))
                        if (part.message.isNotBlank()) {
                            Text(part.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
