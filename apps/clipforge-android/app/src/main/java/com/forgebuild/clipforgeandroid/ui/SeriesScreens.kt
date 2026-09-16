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
import androidx.compose.ui.unit.dp
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.Pipeline
import com.forgebuild.clipforgeandroid.data.SeriesLogic
import com.forgebuild.clipforgeandroid.data.TaskStatus
import com.forgebuild.engine.ui.icons.EngineIcons

/* ---------------- Series Screen (All Series Groups) ---------------- */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SeriesScreen(
    vm: ClipForgeViewModel,
    onSelectSeries: (String) -> Unit,
    onSelectTask: (String) -> Unit
) {
    LaunchedEffect(Unit) { vm.onTasksOpen(); vm.refreshSuperQueues() }
    val tasks by vm.tasks.collectAsState()
    val refreshing by vm.tasksRefreshing.collectAsState()
    val superQueues by vm.superQueues.collectAsState()
    // Session-13 fix #3: hold-to-delete a whole series (destructive -> confirm first).
    var seriesPendingDelete by remember { mutableStateOf<String?>(null) }

            // Session-13 fix #3: destructive delete confirmation (native AlertDialog,
            // matching this app's existing delete-confirmation pattern).
            seriesPendingDelete?.let { sid ->
                AlertDialog(
                    onDismissRequest = { seriesPendingDelete = null },
                    title = { Text("Delete Series") },
                    text = { Text("Delete the entire series \"$sid\" and every part/job belonging to it? This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            seriesPendingDelete = null
                            vm.deleteSeries(sid)
                        }) { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { seriesPendingDelete = null }) { Text("Cancel") }
                    }
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
                actions = {
                    // Session-11 (task-74): spinning corner refresh — same state as swipe.
                    IconButton(onClick = { vm.refreshTasks() }) {
                        if (refreshing) {
                            SquiggleCircularLoader(Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (refreshing) {
                SquiggleLoader(Modifier.fillMaxWidth())
            }

            // Session-11 (task-74): swipe-down-to-refresh wraps the whole list area.
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { vm.refreshTasks() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (seriesGroups.isEmpty() && !refreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CFEEmptyState(title = "No series yet", body = "Start a Series Video from the 'New Video' tab.")
                    }
                } else {
                    LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(seriesGroups.entries.toList(), key = { _, e -> e.key }) { index, (seriesId, partTasks) ->
                        val sortedParts = partTasks.sortedBy { it.part }
                        val latestPart = sortedParts.lastOrNull()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .staggeredAppear(index)
                                .combinedClickable(
                                    onClick = { onSelectSeries(seriesId) },
                                    onLongClick = { seriesPendingDelete = seriesId }
                                )
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(seriesId, style = MaterialTheme.typography.titleMedium)

                                // Super Series: badge + queue/halt state. A halted chain names the part.
                                superQueues[seriesId]?.let { q ->
                                    AssistChip(
                                        onClick = { onSelectSeries(seriesId) },
                                        label = {
                                            Text(
                                                "Super Series — " + q.label,
                                                color = if (q.halted) MaterialTheme.colorScheme.error else LocalContentColor.current
                                            )
                                        }
                                    )
                                }
                                Text(
                                    "${sortedParts.size} part(s) created",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                latestPart?.let {
                                    Text(
                                        "Latest: Part ${it.part} — ${Pipeline.describe(it.state)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                // Session-11 (task-76): the overview shows EVERY part as its
                                // own row — “Part 1”, “Part 2”, “Part 3”… — instead of one
                                // bare topic folder. Tapping a row opens that part's task.
                                sortedParts.forEach { part ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectTask(part.jobId) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "Part ${part.part}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.width(64.dp)
                                        )
                                        Text(
                                            Pipeline.describe(part.state),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (part.state == "error" || part.state == "cancelled")
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // Session-11 (task-74): close PullToRefreshBox wrapper
                }
            }
        }
    }
}


/* ---------------- Series Detail Screen (Ordered Parts) ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    vm: ClipForgeViewModel,
    seriesId: String,
    onBack: () -> Unit,
    onSelectTask: (String) -> Unit
) {
    LaunchedEffect(Unit) { vm.onTasksOpen() }
    val tasks by vm.tasks.collectAsState()
    // Fix #5 — videos downloaded for ANY part of this series are findable from here.
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(EngineIcons.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Sequential Series Parts",
                style = MaterialTheme.typography.titleSmall
            )

            if (downloads.isNotEmpty()) {
                Text("Downloaded videos", style = MaterialTheme.typography.titleSmall)
                downloads.forEach { rec ->
                    OutlinedButton(
                        onClick = { vm.playVideoFromRegistry(rec) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Play ${rec.optString("name")} (${rec.optString("jobId")})")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(seriesParts, key = { it.jobId }) { part ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTask(part.jobId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Part ${part.part} (${part.jobId})", style = MaterialTheme.typography.titleSmall)
                                AssistChip(
                                    onClick = { onSelectTask(part.jobId) },
                                    label = { Text(Pipeline.describe(part.state)) }
                                )
                                if (part.message.isNotBlank()) {
                                    Text(part.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
