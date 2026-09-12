package com.forgebuild.clipforgeandroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    vm: ClipForgeViewModel,
    onSelectSeries: (String) -> Unit
) {
    LaunchedEffect(Unit) { vm.onTasksOpen() }
    val tasks by vm.tasks.collectAsState()
    val refreshing by vm.tasksRefreshing.collectAsState()

    val seriesGroups = remember(tasks) {
        tasks.filter { it.seriesEnabled && it.seriesId.isNotBlank() }
            .groupBy { it.seriesId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Series") },
                actions = {
                    IconButton(onClick = { vm.refreshTasks() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (seriesGroups.isEmpty() && !refreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No series created yet. Start a Series Video from 'New Video'.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(seriesGroups.entries.toList(), key = { it.key }) { (seriesId, partTasks) ->
                        val sortedParts = partTasks.sortedBy { it.part }
                        val latestPart = sortedParts.lastOrNull()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSeries(seriesId) }
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(seriesId, style = MaterialTheme.typography.titleMedium)
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
                            }
                        }
                    }
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
