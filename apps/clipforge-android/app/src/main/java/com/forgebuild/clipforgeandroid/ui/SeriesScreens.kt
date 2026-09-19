@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.forgebuild.clipforgeandroid.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.Pipeline
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens

/* ---------------- Series Screen (All Series Groups) — v22 rebuild ---------------- */
@OptIn(ExperimentalFoundationApi::class)
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
            title = { Text("Delete Series") },
            text = { Text("Delete the entire series \"$sid\" and every part/job belonging to it? This cannot be undone.") },
            confirmButton = {
                TextActionButton(label = "Delete Permanently", destructive = true, onClick = {
                    seriesPendingDelete = null
                    vm.deleteSeries(sid)
                })
            },
            dismissButton = {
                TextActionButton(label = "Cancel", onClick = { seriesPendingDelete = null })
            },
        )
    }

    val seriesGroups = remember(tasks) {
        tasks.filter { it.seriesEnabled && it.seriesId.isNotBlank() }
            .groupBy { it.seriesId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Series") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ElevationTokens.tonalContainerColor(2)),
                actions = {
                    IconActionButton(Icons.Default.Refresh, "Refresh", onClick = { vm.refreshTasks() }, busy = refreshing)
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (refreshing) {
                EngineLinearWavyProgress(Modifier.fillMaxWidth())
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { vm.refreshTasks() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (seriesGroups.isEmpty() && !refreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EngineEmptyState(
                            title = "No series yet",
                            body = "Start a Series Video from the 'New Video' tab.",
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(SpacingTokens.Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
                    ) {
                        itemsIndexed(seriesGroups.entries.toList(), key = { _, e -> e.key }) { _, (seriesId, partTasks) ->
                            val sortedParts = partTasks.sortedBy { it.part }
                            val latestPart = sortedParts.lastOrNull()
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ElevationTokens.tonalContainerColor(1)),
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
                                        AssistChip(
                                            onClick = { onSelectSeries(seriesId) },
                                            label = {
                                                Text(
                                                    "Super Series — " + q.label,
                                                    color = if (q.halted) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                                )
                                            },
                                        )
                                    }
                                    Text(
                                        "${sortedParts.size} part(s) created",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    latestPart?.let {
                                        Text(
                                            "Latest: Part ${it.part} — ${Pipeline.describe(it.state)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    sortedParts.forEach { part ->
                                        ListItem(
                                            headlineContent = { Text("Part ${part.part}", style = MaterialTheme.typography.bodyMedium) },
                                            supportingContent = {
                                                Text(
                                                    Pipeline.describe(part.state),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (part.state == "error" || part.state == "cancelled")
                                                        MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                            modifier = Modifier.clickable { onSelectTask(part.jobId) },
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

/* ---------------- Series Detail Screen (Ordered Parts) — v22 rebuild ---------------- */
@Composable
fun SeriesDetailScreen(
    vm: ClipForgeViewModel,
    seriesId: String,
    onBack: () -> Unit,
    onSelectTask: (String) -> Unit,
) {
    LaunchedEffect(Unit) { vm.onTasksOpen() }
    val tasks by vm.tasks.collectAsState()
    val downloads = remember(seriesId) { vm.seriesDownloads(seriesId) }
    val playVideo by vm.playVideoUri.collectAsState()
    playVideo?.let { (uri, title) ->
        VideoPlayerDialog(uriString = uri, title = title, onDismiss = { vm.dismissVideoPlayer() })
    }
    val seriesParts = remember(tasks, seriesId) {
        tasks.filter { it.seriesEnabled && it.seriesId == seriesId }
            .sortedBy { it.part }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seriesId) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ElevationTokens.tonalContainerColor(2)),
                navigationIcon = {
                    IconActionButton(EngineIcons.ArrowBack, "Back", onClick = onBack)
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
        ) {
            Text("Sequential Series Parts", style = MaterialTheme.typography.titleSmall)

            if (downloads.isNotEmpty()) {
                Text("Downloaded videos", style = MaterialTheme.typography.titleSmall)
                downloads.forEach { rec ->
                    OutlinedActionButton(
                        label = "Play ${rec.optString("name")} (${rec.optString("jobId")})",
                        onClick = { vm.playVideoFromRegistry(rec) },
                        icon = EngineIcons.Play,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
            ) {
                items(seriesParts, key = { it.jobId }) { part ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevationTokens.tonalContainerColor(1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .clickable { onSelectTask(part.jobId) },
                    ) {
                        Row(
                            modifier = Modifier.padding(SpacingTokens.Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)) {
                                Text("Part ${part.part} (${part.jobId})", style = MaterialTheme.typography.titleSmall)
                                AssistChip(
                                    onClick = { onSelectTask(part.jobId) },
                                    label = { Text(Pipeline.describe(part.state)) },
                                )
                                if (part.message.isNotBlank()) {
                                    Text(
                                        part.message,
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
