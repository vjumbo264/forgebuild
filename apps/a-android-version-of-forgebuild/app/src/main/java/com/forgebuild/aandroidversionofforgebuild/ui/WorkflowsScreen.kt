package com.forgebuild.aandroidversionofforgebuild.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.aandroidversionofforgebuild.ForgeApp
import com.forgebuild.aandroidversionofforgebuild.ForgeBuildViewModel
import com.forgebuild.aandroidversionofforgebuild.WorkflowRun
import com.forgebuild.engine.ui.icons.EngineIcons

@Composable
fun WorkflowsScreen(
    viewModel: ForgeBuildViewModel,
    apps: List<ForgeApp>,
    workflowRuns: List<WorkflowRun>,
    isLoading: Boolean,
    isDispatching: Boolean
) {
    val context = LocalContext.current

    var showDispatchDialog by remember { mutableStateOf(false) }
    var selectedSlug by remember { mutableStateOf(apps.firstOrNull()?.slug ?: "") }
    var releaseNotes by remember { mutableStateOf("") }
    var expandedDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Dispatch Card Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CI / Release Workflows",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "GitHub Actions: release.yml",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        if (apps.isNotEmpty() && selectedSlug.isEmpty()) {
                            selectedSlug = apps.first().slug
                        }
                        showDispatchDialog = true
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(EngineIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dispatch")
                }
            }
        }

        if (isLoading && workflowRuns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (workflowRuns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No recent workflow runs found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(workflowRuns, key = { it.id }) { run ->
                    WorkflowRunCard(run = run, onOpen = { viewModel.openInBrowser(context, run.htmlUrl) })
                }
            }
        }
    }

    if (showDispatchDialog) {
        AlertDialog(
            onDismissRequest = { showDispatchDialog = false },
            title = { Text("Dispatch Release Workflow") },
            text = {
                Column {
                    Text(
                        "Select app to build on GitHub Actions with release.yml:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Target App",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        selectedSlug.ifEmpty { "Select an app" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(
                                    EngineIcons.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            apps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app.slug) },
                                    onClick = {
                                        selectedSlug = app.slug
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = releaseNotes,
                        onValueChange = { releaseNotes = it },
                        label = { Text("Release notes (optional)") },
                        placeholder = { Text("e.g. Bug fixes and performance updates") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDispatchDialog = false
                        viewModel.dispatchRelease(selectedSlug, releaseNotes)
                    },
                    enabled = selectedSlug.isNotBlank() && !isDispatching
                ) {
                    Text(if (isDispatching) "Dispatching..." else "Trigger Build")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDispatchDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WorkflowRunCard(run: WorkflowRun, onOpen: () -> Unit) {
    val isSuccess = run.conclusion == "success"
    val isFailure = run.conclusion == "failure"
    val isInProgress = run.status == "in_progress" || run.status == "queued"

    val statusColor = when {
        isSuccess -> MaterialTheme.colorScheme.primary
        isFailure -> MaterialTheme.colorScheme.error
        isInProgress -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when {
        isSuccess -> "Success"
        isFailure -> "Failed"
        run.status == "queued" -> "Queued"
        run.status == "in_progress" -> "Building..."
        else -> run.conclusion ?: run.status
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSuccess) EngineIcons.CheckCircle else if (isFailure) EngineIcons.Settings else EngineIcons.Schedule,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )

                    Text(
                        text = "Run #${run.runNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = run.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (run.headCommitMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = run.headCommitMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Event: ${run.event}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                OutlinedButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(EngineIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Logs", fontSize = 12.sp)
                }
            }
        }
    }
}
