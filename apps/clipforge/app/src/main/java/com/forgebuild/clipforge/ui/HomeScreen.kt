package com.forgebuild.clipforge.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forgebuild.clipforge.data.Job
import com.forgebuild.engine.ui.icons.EngineIcons

/** Home — connection status, active tasks (/tasks), finished tasks (/done), releases. */
@Composable
fun HomeScreen(vm: AppViewModel, onNewJob: () -> Unit) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshJobs() }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewJob) {
                Icon(EngineIcons.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New clip")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(EngineIcons.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Connected clone", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.connection?.fullName ?: "—", style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { vm.refreshJobs() }) {
                            Icon(EngineIcons.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EngineIcons.Tasks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Active tasks", style = MaterialTheme.typography.titleMedium)
                }
            }
            val active = state.jobs.filter { it.isActive() }
            if (active.isEmpty()) {
                item {
                    Text("No active tasks — tap New clip to start one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(active) { job -> JobCard(job, onCancel = { vm.cancelJob(job) }) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(EngineIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Finished", style = MaterialTheme.typography.titleMedium)
                }
            }
            val done = state.jobs.filter { !it.isActive() }
            if (done.isEmpty()) {
                item {
                    Text("Nothing finished yet.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(done) { job -> JobCard(job, onCancel = null) }

            if (state.releases.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(EngineIcons.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Released clips", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(state.releases) { (tag, url) ->
                    Card(onClick = {
                        if (url.isNotBlank()) ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(EngineIcons.PlayArrow, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(tag, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JobCard(job: Job, onCancel: (() -> Unit)?) {
    val (icon, tint) = when (job.state) {
        "complete" -> EngineIcons.CheckCircle to MaterialTheme.colorScheme.primary
        "error" -> EngineIcons.Error to MaterialTheme.colorScheme.error
        "cancelled" -> EngineIcons.Error to MaterialTheme.colorScheme.onSurfaceVariant
        else -> EngineIcons.Movie to MaterialTheme.colorScheme.tertiary
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.title, style = MaterialTheme.typography.titleSmall)
                Text(job.state.replace('_', ' '), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                job.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
