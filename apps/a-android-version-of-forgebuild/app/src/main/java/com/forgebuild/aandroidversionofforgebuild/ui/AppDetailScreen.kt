package com.forgebuild.aandroidversionofforgebuild.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.aandroidversionofforgebuild.BuildTask
import com.forgebuild.aandroidversionofforgebuild.ContractGenerator
import com.forgebuild.aandroidversionofforgebuild.ForgeApp
import com.forgebuild.aandroidversionofforgebuild.ForgeBuildViewModel
import com.forgebuild.engine.ui.icons.EngineIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    app: ForgeApp,
    viewModel: ForgeBuildViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf(0) }
    val sections = listOf("Tasks", "Releases", "History", "Extend")

    var showDispatchDialog by remember { mutableStateOf(false) }
    var releaseNotesInput by remember { mutableStateOf("") }

    var extendInput by remember { mutableStateOf("") }
    var generatedExtendPrompt by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(EngineIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.copyToClipboard(context, app.slug, "App Slug")
                    }) {
                        Icon(EngineIcons.ContentCopy, contentDescription = "Copy Slug")
                    }
                    IconButton(onClick = {
                        viewModel.openInBrowser(context, "https://github.com/vjumbo264/forgebuild/tree/main/apps/${app.slug}")
                    }) {
                        Icon(EngineIcons.OpenInNew, contentDescription = "GitHub")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // App Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "apps/${app.slug}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val isComplete = app.buildState?.buildComplete == true
                        StatusBadge(
                            label = if (isComplete) "Verified Live" else (app.buildState?.currentTask ?: "Pending"),
                            isDone = isComplete
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val latest = app.latestRelease
                        if (latest != null) {
                            Button(
                                onClick = { viewModel.downloadApk(context, latest) },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(EngineIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download ${latest.version}", maxLines = 1)
                            }
                        }

                        FilledTonalButton(
                            onClick = { showDispatchDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(EngineIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Build CI", maxLines = 1)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab navigation
            PrimaryTabRow(
                selectedTabIndex = selectedSection,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                sections.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedSection == index,
                        onClick = { selectedSection = index },
                        text = { Text(title, fontWeight = if (selectedSection == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section Contents
            when (selectedSection) {
                0 -> TasksSection(tasks = app.buildState?.tasks ?: emptyList())
                1 -> ReleasesSection(
                    releases = app.releases,
                    onDownload = { viewModel.downloadApk(context, it) },
                    onOpenUrl = { viewModel.openInBrowser(context, it) }
                )
                2 -> PromptHistorySection(promptHistory = app.promptHistory ?: "No prompt history found.")
                3 -> ExtendSection(
                    app = app,
                    input = extendInput,
                    onInputChange = { extendInput = it },
                    generatedPrompt = generatedExtendPrompt,
                    onGenerate = {
                        generatedExtendPrompt = ContractGenerator.promptExtend(
                            slug = app.slug,
                            instruction = extendInput,
                            latest = app.latestRelease?.tagName ?: ""
                        )
                    },
                    onCopy = {
                        viewModel.copyToClipboard(context, generatedExtendPrompt, "Extend Prompt")
                    }
                )
            }
        }
    }

    // Trigger Release Workflow Dialog
    if (showDispatchDialog) {
        AlertDialog(
            onDismissRequest = { showDispatchDialog = false },
            title = { Text("Dispatch Release Workflow") },
            text = {
                Column {
                    Text(
                        "This will trigger the release.yml workflow on GitHub Actions for apps/${app.slug}. A new release tag will be auto-incremented.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = releaseNotesInput,
                        onValueChange = { releaseNotesInput = it },
                        label = { Text("Release notes (optional)") },
                        placeholder = { Text("e.g. Initial release") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDispatchDialog = false
                        viewModel.dispatchRelease(app.slug, releaseNotesInput)
                    }
                ) {
                    Text("Dispatch Workflow")
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
fun TasksSection(tasks: List<BuildTask>) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No tasks found in BUILD_STATE.json", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                TaskItemCard(task)
            }
        }
    }
}

@Composable
fun TaskItemCard(task: BuildTask) {
    val isDone = task.status == "done"
    val isInProgress = task.status == "in_progress"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else if (isInProgress) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isDone) EngineIcons.CheckCircle else EngineIcons.Schedule,
                    contentDescription = null,
                    tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )

                Text(
                    text = task.id.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = task.status,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            if (task.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = task.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ReleasesSection(
    releases: List<com.forgebuild.aandroidversionofforgebuild.AppRelease>,
    onDownload: (com.forgebuild.aandroidversionofforgebuild.AppRelease) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    if (releases.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No published releases yet for this app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(releases, key = { it.tagName }) { rel ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rel.tagName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (rel.apkSizeFormatted != null) {
                                Text(
                                    text = rel.apkSizeFormatted,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (rel.body.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = rel.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onDownload(rel) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(EngineIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download APK")
                            }

                            if (rel.manifestUrl != null) {
                                OutlinedButton(
                                    onClick = { onOpenUrl(rel.manifestUrl) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Manifest")
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
fun PromptHistorySection(promptHistory: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PROMPT HISTORY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = promptHistory,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ExtendSection(
    app: ForgeApp,
    input: String,
    onInputChange: (String) -> Unit,
    generatedPrompt: String,
    onGenerate: () -> Unit,
    onCopy: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Extend or Update App",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Describe the new feature, enhancement, or fix. ForgeBuild will generate an append-only contract prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        label = { Text("New feature or fix instruction") },
                        placeholder = { Text("e.g. Add dark mode toggle and export to CSV") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onGenerate,
                        enabled = input.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Generate Extend Contract")
                    }
                }
            }
        }

        if (generatedPrompt.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Generated Prompt Contract",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = onCopy,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(EngineIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = generatedPrompt,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
