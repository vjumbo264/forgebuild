package com.forgebuild.clipforgeandroid.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.AgentPromptBuilder
import com.forgebuild.clipforgeandroid.data.Pipeline
import com.forgebuild.clipforgeandroid.data.PlanValidator
import com.forgebuild.clipforgeandroid.data.TaskStatus
import com.forgebuild.engine.ui.icons.EngineIcons

/* ---------------- Tasks Screen (Active Tasks) ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    vm: ClipForgeViewModel,
    onSelectTask: (String) -> Unit
) {
    // Cache-first open (recovered working architecture): instant cached render, then background refresh.
    LaunchedEffect(Unit) { vm.onTasksOpen() }
    val activeTasks by vm.activeTasks.collectAsState()
    val refreshing by vm.tasksRefreshing.collectAsState()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectedIds.isEmpty()) "Active Tasks" else "${selectedIds.size} Selected")
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete selected")
                        }
                    } else {
                        IconButton(onClick = { vm.refreshTasks() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (activeTasks.isEmpty() && !refreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active tasks. Tap 'New Video' to begin.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeTasks, key = { it.jobId }) { task ->
                        val isSelected = selectedIds.contains(task.jobId)
                        TaskListItem(
                            task = task,
                            isSelected = isSelected,
                            isSelectionMode = selectedIds.isNotEmpty(),
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds = if (isSelected) selectedIds - task.jobId else selectedIds + task.jobId
                                } else {
                                    onSelectTask(task.jobId)
                                }
                            },
                            onLongClick = {
                                selectedIds = if (isSelected) selectedIds - task.jobId else selectedIds + task.jobId
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Tasks") },
            text = { Text("Delete ${selectedIds.size} task(s) and associated files?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTasks(selectedIds)
                    selectedIds = emptySet()
                    showDeleteDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/* ---------------- Completed Screen ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedScreen(
    vm: ClipForgeViewModel,
    onSelectTask: (String) -> Unit
) {
    LaunchedEffect(Unit) { vm.onTasksOpen() }
    val completedTasks by vm.completedTasks.collectAsState()
    val refreshing by vm.tasksRefreshing.collectAsState()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectedIds.isEmpty()) "Completed Videos" else "${selectedIds.size} Selected")
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete selected")
                        }
                    } else {
                        IconButton(onClick = { vm.refreshTasks() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (refreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (completedTasks.isEmpty() && !refreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No completed videos yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(completedTasks, key = { it.jobId }) { task ->
                        val isSelected = selectedIds.contains(task.jobId)
                        TaskListItem(
                            task = task,
                            isSelected = isSelected,
                            isSelectionMode = selectedIds.isNotEmpty(),
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds = if (isSelected) selectedIds - task.jobId else selectedIds + task.jobId
                                } else {
                                    onSelectTask(task.jobId)
                                }
                            },
                            onLongClick = {
                                selectedIds = if (isSelected) selectedIds - task.jobId else selectedIds + task.jobId
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Completed Videos") },
            text = { Text("Delete ${selectedIds.size} completed video(s)?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTasks(selectedIds)
                    selectedIds = emptySet()
                    showDeleteDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskListItem(
    task: TaskStatus,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.jobId, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(Pipeline.describe(task.state)) }
                    )
                    if (task.seriesEnabled) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Series: Part ${task.part}") }
                        )
                    }
                }
                if (task.message.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(task.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            }
        }
    }
}

/* ---------------- Task Detail Screen ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    vm: ClipForgeViewModel,
    jobId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val status by vm.detailStatus.collectAsState()
    val request by vm.detailRequest.collectAsState()
    val logs by vm.detailLogs.collectAsState()
    val upload by vm.upload.collectAsState()
    val downloadState by vm.downloadState.collectAsState()
    val torrentFiles by vm.torrentFiles.collectAsState()
    val torrentSubmitting by vm.torrentSubmitting.collectAsState()
    val plan by vm.detailPlan.collectAsState()
    val busyOps by vm.busyOps.collectAsState()

    var rawPlanText by remember { mutableStateOf("") }
    var planErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    // Fix #4: the cancel confirmation step — the bot always asks before the real
    // cancel (confirmCancelStageB); the destructive action runs only after Yes.
    var showCancelConfirm by remember { mutableStateOf(false) }

    // JSON file picker for production.json
    val planFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                rawPlanText = String(bytes, Charsets.UTF_8)
                planErrors = PlanValidator.validate(rawPlanText)
                if (planErrors.isEmpty()) {
                    vm.toast("Loaded valid production.json")
                } else {
                    vm.toast("Validation error: ${planErrors.first()}")
                }
            }
        } catch (e: Exception) {
            vm.toast("Failed to read JSON: ${e.message}")
        }
    }

    DisposableEffect(jobId) {
        vm.startPollingTask(jobId)
        onDispose { vm.stopPollingTask() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(jobId) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val currentStatus = status
            if (currentStatus == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Loading task details…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            upload?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(it.label, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { it.fraction }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Status Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status", style = MaterialTheme.typography.titleMedium)
                        AssistChip(
                            onClick = {},
                            label = { Text(Pipeline.describe(currentStatus.state)) }
                        )
                    }
                    if (currentStatus.message.isNotBlank()) {
                        Text(currentStatus.message, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (currentStatus.releaseTag.isNotBlank()) {
                        Text("Release Tag: ${currentStatus.releaseTag}", style = MaterialTheme.typography.bodySmall)
                    }
                    // Fix #3: direct link to the GitHub Actions workflow run associated
                    // with the current stage. run.workflow_run_url is already parsed into
                    // TaskStatus (real example: .../actions/runs/34666886936) — purely a
                    // missing UI affordance. Opens in the system browser.
                    if (currentStatus.runUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentStatus.runUrl)))
                                } catch (_: Exception) {
                                    vm.toast("No browser available to open the run")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(EngineIcons.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Workflow Run" + if (currentStatus.runId > 0) " #${currentStatus.runId}" else "")
                        }
                    }
                }
            }

            // Awaiting Torrent Selection — user must pick which video inside
            // the torrent to render (same step the Telegram bot used to ask for).
            if (currentStatus.state == "awaiting_torrent_selection") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Select Video From Torrent", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "The torrent contains more than one file (or the pipeline needs your confirmation). Pick the video to process.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (torrentSubmitting || upload != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Submitting selection…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (torrentFiles.isEmpty()) {
                            Text(
                                "Waiting for the pipeline to list the torrent contents… this page refreshes automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            torrentFiles.forEach { opt ->
                                OutlinedButton(
                                    onClick = { vm.submitTorrentSelection(jobId, opt.index) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            opt.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2
                                        )
                                        if (opt.sizeBytes > 0) {
                                            val mb = opt.sizeBytes / (1024.0 * 1024.0)
                                            Text(
                                                if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // "Copy Agent Prompt" button (available when awaiting_plan or complete)
            if (currentStatus.state == "awaiting_plan" || currentStatus.isComplete) {
                Button(
                    onClick = {
                        val promptText = AgentPromptBuilder.build(currentStatus, request)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ClipForge Agent Prompt", promptText))
                        vm.toast("Agent prompt copied to clipboard!")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(EngineIcons.Copy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Agent Prompt to Clipboard")
                }
            }

            // Awaiting Plan Section
            if (currentStatus.state == "awaiting_plan") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Provide production.json", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Paste the generated JSON plan below or upload it directly from file storage.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { planFilePicker.launch("application/json") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Upload .json File")
                            }
                        }

                        OutlinedTextField(
                            value = rawPlanText,
                            onValueChange = {
                                rawPlanText = it
                                planErrors = if (it.isNotBlank()) PlanValidator.validate(it) else emptyList()
                            },
                            label = { Text("Paste raw production.json") },
                            placeholder = { Text("""{"video_duration_seconds": 120, ...}""") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 320.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )

                        if (planErrors.isNotEmpty()) {
                            Text(
                                text = "Validation Errors:\n" + planErrors.joinToString("\n• ", "• "),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                vm.submitProductionPlan(jobId, rawPlanText) {
                                    rawPlanText = ""
                                    planErrors = emptyList()
                                }
                            },
                            enabled = rawPlanText.isNotBlank() && planErrors.isEmpty() && upload == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Submit Plan & Start Stage B Rendering")
                        }
                    }
                }
            }

            // Completed Section: Save Video
            if (currentStatus.isComplete) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Video Ready", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "The rendered vertical video is ready in the GitHub release.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (downloadState.isDownloading) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { downloadState.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(downloadState.speedText, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val tag = currentStatus.releaseTag.ifBlank { "clipforge-$jobId" }
                                    vm.saveVideoToMovies(tag, "$jobId.mp4")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(EngineIcons.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Save Video to Movies/ClipForge/")
                            }
                        }
                    }
                }
            }

            // ---------------- Stage Controls (fix #4, bot runtime.js taskKeyboard parity) ----------------
            // Restart Stage A — offered whenever the task is at/has failed Stage A
            //   (bot: error/cancelled rows always carry Restart Stage A).
            // Restart Stage B — the operator's rule: available whenever the task has
            //   already produced a production.json (reached/passed Stage A). The bot's
            //   own restartStageB still guards server-side with the exact block message,
            //   reproduced in the ViewModel — the button is hidden when no plan exists.
            // Cancel running stage — bot taskKeyboard shows it for stage_b_queued /
            //   stage_b_running; the app extends the same affordance to a running Stage A
            //   (both cancel branches are implemented in cancelRunningStage).
            val state = currentStatus.state
            val stageBStarted = state in setOf("awaiting_plan", "stage_b_queued", "stage_b_running", "complete") ||
                Regex("stage b", RegexOption.IGNORE_CASE).containsMatchIn(currentStatus.message)
            val canRestartA = state == "error" || state == "cancelled"
            val canRestartB = (state == "error" || state == "cancelled") &&
                (plan != null || stageBStarted)
            val canCancel = state == "stage_a_running" || state == "stage_a_queued" ||
                state == "stage_b_queued" || state == "stage_b_running" || state == "queued"
            if (canRestartA || canCancel) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Stage Controls", style = MaterialTheme.typography.titleMedium)
                        if (canRestartA) {
                            Button(
                                onClick = { vm.restartStageA(jobId) },
                                enabled = !busyOps.contains("restart_a"),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(EngineIcons.Restart, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Restart Stage A")
                            }
                        }
                        if (canRestartB) {
                            Button(
                                onClick = { vm.restartStageB(jobId) },
                                enabled = !busyOps.contains("restart_b"),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(EngineIcons.Restart, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Restart Stage B")
                            }
                            if (plan == null) {
                                Text(
                                    "No production.json found on the clone — restarting Stage B will show the bot's guard message unless a plan is uploaded first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (canCancel) {
                            OutlinedButton(
                                onClick = { showCancelConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(EngineIcons.Cancel, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Cancel Running Stage")
                            }
                        }
                    }
                }
            }

            // Bot confirmCancelStageB, verbatim wording — the real cancel happens
            // only on Yes (both branches: Actions API run cancel / local status write).
            if (showCancelConfirm) {
                AlertDialog(
                    onDismissRequest = { showCancelConfirm = false },
                    title = { Text("Cancel the running stage for task ${jobId.substringAfter("manual-")}?") },
                    text = {
                        Text("The running render is stopped and the job moves to cancelled. You can restart it afterwards.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showCancelConfirm = false
                                vm.cancelRunningStage(jobId)
                            }
                        ) { Text("Yes, cancel") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCancelConfirm = false }) { Text("Back") }
                    }
                )
            }

            // Live Execution Logs — color-coded per step:
            //   pending  = steps not yet run (muted gray)
            //   running  = step in progress (primary)
            //   success  = completed steps (green)
            //   skipped  = skipped / waiting-on-you steps (amber)
            //   failure  = failed steps (red)
            //   cancelled = cancelled steps (outline gray)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Live Execution Logs", style = MaterialTheme.typography.titleMedium)
                    if (logs.isEmpty()) {
                        Text(
                            "Waiting for the first action…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val listState = rememberLazyListState()
                        // Follow the action: scroll to the running step (or the latest line
                        // when nothing is running) whenever the log updates.
                        LaunchedEffect(logs) {
                            if (logs.isNotEmpty()) {
                                val runningIdx = logs.indexOfLast { it.level == ClipForgeViewModel.LogLevel.RUNNING }
                                val target = if (runningIdx >= 0) runningIdx else logs.size - 1
                                listState.animateScrollToItem(target)
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(logs, key = { it.key }) { line ->
                                LogRow(line)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Single color-coded log row for the task detail live log stream. */
@Composable
private fun LogRow(line: ClipForgeViewModel.LogLine) {
    val scheme = MaterialTheme.colorScheme
    // Green/amber are chosen to keep ~4.5:1 contrast on both light and dark surfaces.
    val success = androidx.compose.ui.graphics.Color(0xFF2E7D32)
    val skipped = androidx.compose.ui.graphics.Color(0xFF9A6A00)
    val color = when (line.level) {
        ClipForgeViewModel.LogLevel.SUCCESS -> success
        ClipForgeViewModel.LogLevel.FAILURE -> scheme.error
        ClipForgeViewModel.LogLevel.SKIPPED -> skipped
        ClipForgeViewModel.LogLevel.RUNNING -> scheme.primary
        ClipForgeViewModel.LogLevel.CANCELLED -> scheme.outline
        ClipForgeViewModel.LogLevel.PENDING -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
        ClipForgeViewModel.LogLevel.INFO -> scheme.onSurfaceVariant
    }
    Text(
        text = line.text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color
    )
}
