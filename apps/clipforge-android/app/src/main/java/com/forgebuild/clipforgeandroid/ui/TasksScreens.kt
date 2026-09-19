@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.forgebuild.clipforgeandroid.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.clipforgeandroid.data.AgentPromptBuilder
import com.forgebuild.clipforgeandroid.data.Pipeline
import com.forgebuild.clipforgeandroid.data.PlanValidator
import com.forgebuild.clipforgeandroid.data.SuperSeries
import com.forgebuild.clipforgeandroid.data.TaskStatus
import com.forgebuild.clipforgeandroid.data.ZernioPublish
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens

/* ============================================================================
 *  V23 TASKS — active list, completed list (with quick-publish), and the task
 *  detail screen with the rebuilt LIVE LOGGER (v23-R5): every job + every step
 *  of the run with its real GitHub Actions log lines, a designed loading state
 *  while running (wavy progress on the live step, morphing loader while a
 *  runner is queued), card timeline with status-colored nodes, copy-log.
 * ============================================================================ */

/* ------------------------------ task lists ------------------------------ */

@Composable
fun TasksScreen(vm: ClipForgeViewModel, onSelectTask: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.onTasksOpen() }
    val activeTasks by vm.activeTasks.collectAsState()
    val refreshing by vm.tasksRefreshing.collectAsState()
    TaskListScaffold(
        title = "Active tasks",
        emptyTitle = "No active tasks",
        emptyBody = "Start a render from the New Video tab.",
        tasks = activeTasks,
        refreshing = refreshing,
        onRefresh = { vm.refreshTasks() },
        onDelete = { ids -> vm.deleteTasks(ids) },
        onSelectTask = onSelectTask,
        rowActions = null,
    )
}

@Composable
fun CompletedScreen(vm: ClipForgeViewModel, onSelectTask: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.onTasksOpen() }
    val completedTasks by vm.completedTasks.collectAsState()
    val refreshing by vm.tasksRefreshing.collectAsState()
    val settings by vm.settings.collectAsState()
    TaskListScaffold(
        title = "Completed videos",
        emptyTitle = "No completed videos yet",
        emptyBody = "Finished renders land here, ready to play, download and publish.",
        tasks = completedTasks,
        refreshing = refreshing,
        onRefresh = { vm.refreshTasks() },
        onDelete = { ids -> vm.deleteTasks(ids) },
        onSelectTask = onSelectTask,
        rowActions = if (settings.zernioEnabled) { task ->
            // Quick publish straight from the completed list (auto mode).
            { TextActionButton(label = "Publish", onClick = { vm.publishTask(task.jobId, "") }) }
        } else null,
    )
}

@Composable
private fun TaskListScaffold(
    title: String,
    emptyTitle: String,
    emptyBody: String,
    tasks: List<TaskStatus>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: (Set<String>) -> Unit,
    onSelectTask: (String) -> Unit,
    rowActions: ((TaskStatus) -> @Composable () -> Unit)?,
) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedIds.isEmpty()) title else "${selectedIds.size} selected") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ElevationTokens.tonalContainerColor(2)),
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconActionButton(EngineIcons.Cancel, "Delete selected", onClick = { showDeleteDialog = true })
                    } else {
                        IconActionButton(EngineIcons.Restart, "Refresh", onClick = onRefresh, busy = refreshing)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (refreshing) EngineLinearWavyProgress(Modifier.fillMaxWidth())
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                if (tasks.isEmpty() && !refreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CfEmptyState(title = emptyTitle, body = emptyBody)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(SpacingTokens.Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
                    ) {
                        items(tasks, key = { it.jobId }) { task ->
                            val isSelected = selectedIds.contains(task.jobId)
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = ElevationTokens.tonalContainerColor(if (isSelected) 3 else 1),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .combinedClickable(
                                        onClick = {
                                            if (selectedIds.isNotEmpty())
                                                selectedIds = if (isSelected) selectedIds - task.jobId else selectedIds + task.jobId
                                            else onSelectTask(task.jobId)
                                        },
                                        onLongClick = {
                                            selectedIds = if (isSelected) selectedIds - task.jobId else selectedIds + task.jobId
                                        },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(SpacingTokens.Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                                        Text(task.jobId, style = MaterialTheme.typography.titleSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                                            CfChip(label = Pipeline.describe(task.state), color = cfStateColor(task.state))
                                            if (task.seriesEnabled) CfChip(label = "Series · Part ${task.part}", color = MaterialTheme.colorScheme.secondary)
                                        }
                                        if (task.message.isNotBlank()) {
                                            Text(
                                                task.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    rowActions?.let { it(task).invoke() }
                                    if (selectedIds.isNotEmpty()) {
                                        Checkbox(checked = isSelected, onCheckedChange = {
                                            selectedIds = if (isSelected) selectedIds - task.jobId else selectedIds + task.jobId
                                        })
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
            title = { Text("Delete tasks") },
            text = { Text("Delete ${selectedIds.size} task(s) and their files? This cannot be undone.") },
            confirmButton = {
                TextActionButton(label = "Delete", destructive = true, onClick = {
                    onDelete(selectedIds)
                    selectedIds = emptySet()
                    showDeleteDialog = false
                })
            },
            dismissButton = { TextActionButton(label = "Cancel", onClick = { showDeleteDialog = false }) },
        )
    }
}

/* ------------------------------ task detail ----------------------------- */

@Composable
fun TaskDetailScreen(vm: ClipForgeViewModel, jobId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val status by vm.detailStatus.collectAsState()
    val request by vm.detailRequest.collectAsState()
    val logs by vm.detailLogs.collectAsState()
    val upload by vm.upload.collectAsState()
    val downloadState by vm.downloadState.collectAsState()
    val torrentFiles by vm.torrentFiles.collectAsState()
    val torrentSubmitting by vm.torrentSubmitting.collectAsState()
    val plan by vm.detailPlan.collectAsState()
    val superState by vm.detailSuperState.collectAsState()
    val nextPart by vm.nextPart.collectAsState()
    val downloadedVideo by vm.downloadedVideoFor.collectAsState()
    val settings by vm.settings.collectAsState()
    val publish by vm.taskPublish.collectAsState()
    val login by vm.login.collectAsState()
    val playVideo by vm.playVideoUri.collectAsState()

    val isSuperSeriesTask = request?.optJSONObject("series")?.optBoolean("super_series", false) == true

    var rawPlanText by remember { mutableStateOf("") }
    var planErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var scheduleInput by remember { mutableStateOf("") }
    var reschedulePost by remember { mutableStateOf<ZernioPublish.Post?>(null) }
    var rescheduleInput by remember { mutableStateOf("") }
    var cancelPost by remember { mutableStateOf<ZernioPublish.Post?>(null) }

    val planFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                rawPlanText = String(bytes, Charsets.UTF_8)
                planErrors = if (isSuperSeriesTask) SuperSeries.parseAndValidateSuperPlan(rawPlanText).errors
                else PlanValidator.validate(rawPlanText)
                vm.toast(if (planErrors.isEmpty()) "Loaded a valid plan" else "Validation error: ${planErrors.first()}")
            }
        } catch (e: Exception) {
            vm.toast("Failed to read JSON: ${e.message}")
        }
    }

    DisposableEffect(jobId) {
        vm.startPollingTask(jobId)
        vm.refreshNextPart(jobId)
        onDispose { vm.stopPollingTask() }
    }
    LaunchedEffect(jobId, status?.isComplete) {
        if (status?.isComplete == true) vm.loadTaskPublish(jobId)
    }

    playVideo?.let { (uri, title) -> VideoPlayerDialog(uriString = uri, title = title, onDismiss = { vm.dismissVideoPlayer() }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(jobId, style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ElevationTokens.tonalContainerColor(2)),
                navigationIcon = { IconActionButton(EngineIcons.ArrowBack, "Back", onClick = onBack) },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md),
        ) {
            val currentStatus = status
            if (currentStatus == null) {
                CfLoading("Loading task…")
                return@Column
            }

            upload?.let { CfCard(tonalLevel = 2) { CfProgress(label = it.label, fraction = it.fraction) } }

            // ---- Status ----
            CfSection(title = "Status") {
                Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    CfChip(label = Pipeline.describe(currentStatus.state), color = cfStateColor(currentStatus.state))
                    if (isSuperSeriesTask) CfChip(label = "Super Series part", color = MaterialTheme.colorScheme.secondary)
                }
                if (currentStatus.message.isNotBlank()) Text(currentStatus.message, style = MaterialTheme.typography.bodyMedium)
                if (currentStatus.releaseTag.isNotBlank()) {
                    Text("Release: ${currentStatus.releaseTag}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (currentStatus.runUrl.isNotBlank()) {
                    OutlinedActionButton(
                        label = "Open workflow run" + if (currentStatus.runId > 0) " #${currentStatus.runId}" else "",
                        icon = EngineIcons.OpenInNew,
                        onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentStatus.runUrl))) }
                            catch (_: Exception) { vm.toast("No browser available to open the run") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---- Super Series queue (anchor) ----
            superState?.let { sp ->
                val total = sp.optInt("total_parts", 0)
                val spawned = sp.optJSONArray("spawned")?.length() ?: 0
                CfSection(title = "Super Series queue", subtitle = "Parts dispatch automatically in sequence; a failed part halts the chain until it is restarted.") {
                    CfChip(label = "$spawned / $total parts dispatched", color = MaterialTheme.colorScheme.primary)
                }
            }

            // ---- Torrent selection ----
            if (currentStatus.state == "awaiting_torrent_selection") {
                CfSection(title = "Select the video inside the torrent", subtitle = "The torrent holds more than one file — pick the one to process.") {
                    when {
                        torrentSubmitting || upload != null -> CfProgress(label = "Submitting selection…", fraction = null)
                        torrentFiles.isEmpty() -> CfLoading("Waiting for the pipeline to list the torrent contents…")
                        else -> torrentFiles.forEach { opt ->
                            OutlinedActionButton(
                                label = opt.name + if (opt.sizeBytes > 0) "  ·  " + vm.formatBytes(opt.sizeBytes) else "",
                                onClick = { vm.submitTorrentSelection(jobId, opt.index) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // ---- Copy agent prompt ----
            if (currentStatus.state == "awaiting_plan" || currentStatus.isComplete) {
                ActionButton(
                    label = "Copy agent prompt",
                    icon = EngineIcons.Copy,
                    onClick = {
                        val text = AgentPromptBuilder.build(
                            currentStatus, request,
                            login?.owner ?: "motionssalt", login?.repo ?: "clipforge",
                        )
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("ClipForge Agent Prompt", text))
                        vm.toast("Agent prompt copied")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Plan submission ----
            if (currentStatus.state == "awaiting_plan") {
                CfSection(
                    title = if (isSuperSeriesTask) "Submit the Super Series plan" else "Submit production.json",
                    subtitle = if (isSuperSeriesTask)
                        "ONE whole-series document covering every part. Part 1 starts immediately; the rest chain automatically."
                    else "Paste the generated JSON or upload the file.",
                ) {
                    OutlinedActionButton(
                        label = "Upload .json file",
                        onClick = { planFilePicker.launch("application/json") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = rawPlanText,
                        onValueChange = {
                            rawPlanText = it
                            planErrors = if (it.isBlank()) emptyList()
                            else if (isSuperSeriesTask) SuperSeries.parseAndValidateSuperPlan(it).errors
                            else PlanValidator.validate(it)
                        },
                        label = { Text(if (isSuperSeriesTask) "Super-plan JSON" else "production.json") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    if (planErrors.isNotEmpty()) {
                        Text(
                            "Validation errors:\n" + planErrors.joinToString("\n• ", "• "),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    ActionButton(
                        label = if (isSuperSeriesTask) "Submit plan — Part 1 starts now" else "Submit plan & start Stage B",
                        busy = busyOf(vm, "submit_super") || upload != null,
                        enabled = rawPlanText.isNotBlank() && planErrors.isEmpty() && upload == null,
                        onClick = {
                            if (isSuperSeriesTask) vm.submitSuperPlan(jobId, rawPlanText) { rawPlanText = ""; planErrors = emptyList() }
                            else vm.submitProductionPlan(jobId, rawPlanText) { rawPlanText = ""; planErrors = emptyList() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---- Video ready ----
            if (currentStatus.isComplete) {
                CfSection(title = "Video ready") {
                    val alreadyDownloaded = remember(downloadedVideo, jobId) { vm.downloadedVideoFor(jobId) }
                    when {
                        downloadState.isDownloading -> CfProgress(
                            label = "${downloadState.downloadedText} / ${downloadState.totalText} · ${downloadState.speedText}",
                            fraction = downloadState.progress,
                        )
                        alreadyDownloaded != null -> {
                            ActionButton(
                                label = "Play final video",
                                icon = EngineIcons.Play,
                                onClick = { vm.playDownloaded(jobId, alreadyDownloaded.optString("name").ifBlank { "$jobId.mp4" }) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedActionButton(
                                label = "Download again",
                                icon = EngineIcons.Download,
                                onClick = {
                                    val tag = currentStatus.releaseTag.ifBlank { "clipforge-$jobId" }
                                    vm.saveVideoToMovies(tag, "$jobId.mp4", jobId, currentStatus.seriesId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        else -> ActionButton(
                            label = "Download final MP4",
                            icon = EngineIcons.Download,
                            onClick = {
                                val tag = currentStatus.releaseTag.ifBlank { "clipforge-$jobId" }
                                vm.saveVideoToMovies(tag, "$jobId.mp4", jobId, currentStatus.seriesId)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ---- Zernio publish card (completed tasks) ----
            if (currentStatus.isComplete && settings.zernioEnabled) {
                val pub = publish ?: ZernioPublish.TaskPublishState("not_requested")
                CfSection(title = "Publishing", subtitle = ZernioPublish.statusLabel(pub.status)) {
                    CfChip(
                        label = ZernioPublish.statusLabel(pub.status),
                        color = when (pub.status) {
                            "published" -> MaterialTheme.colorScheme.tertiary
                            "failed", "partial" -> MaterialTheme.colorScheme.error
                            "publishing", "scheduled" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                        ActionButton(
                            label = if (pub.status == "published") "Publish again" else "Publish now",
                            busy = busyOf(vm, "publish_task"),
                            onClick = { vm.publishTask(jobId, "publish_now") },
                        )
                        TonalActionButton(
                            label = "Smart schedule",
                            busy = busyOf(vm, "publish_task"),
                            onClick = { vm.publishTask(jobId, "smart_schedule") },
                        )
                        OutlinedActionButton(
                            label = "Schedule for…",
                            busy = busyOf(vm, "publish_task"),
                            onClick = { scheduleInput = ""; showScheduleDialog = true },
                        )
                    }
                    pub.posts.forEach { post ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = ElevationTokens.tonalContainerColor(2),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(SpacingTokens.Spacing.sm), verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        post.platform.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    CfChip(
                                        label = post.status,
                                        color = if (post.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (post.scheduledFor.isNotBlank()) {
                                    Text("Scheduled: ${post.scheduledFor}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (post.message.isNotBlank()) {
                                    Text(post.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                                    if (post.status == "failed") {
                                        TextActionButton(label = "Retry", busy = busyOf(vm, "zernio_post"),
                                            onClick = { vm.zernioPostAction(jobId, "retry", post.postId) })
                                    }
                                    if (post.status != "published" && post.status != "publishing") {
                                        TextActionButton(label = "Publish now", busy = busyOf(vm, "zernio_post"),
                                            onClick = { vm.zernioPostAction(jobId, "publish_now", post.postId) })
                                    }
                                    if (post.status == "scheduled") {
                                        TextActionButton(label = "Reschedule", busy = busyOf(vm, "zernio_post"),
                                            onClick = { rescheduleInput = post.scheduledFor; reschedulePost = post })
                                        TextActionButton(label = "Cancel", destructive = true, busy = busyOf(vm, "zernio_post"),
                                            onClick = { cancelPost = post })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Series continuation (ordinary series only) ----
            (if (isSuperSeriesTask) null else nextPart)?.let { np ->
                CfSection(title = "Series continuation", subtitle = "Series ${np.continuation.seriesId} — Part ${np.continuation.part - 1} finished at ${np.continuation.startSeconds}s.") {
                    if (np.exists) {
                        Text("Part ${np.continuation.part} already exists as task ${np.nextId}", style = MaterialTheme.typography.bodyMedium)
                        OutlinedActionButton(label = "Re-check ${np.nextId}", onClick = { vm.refreshNextPart(jobId) }, modifier = Modifier.fillMaxWidth())
                    } else {
                        ActionButton(
                            label = "Start next part (Part ${np.continuation.part})",
                            icon = EngineIcons.Play,
                            busy = busyOf(vm, "start_next"),
                            onClick = { vm.startNextSeriesPart(jobId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ---- Stage controls ----
            val state = currentStatus.state
            val stageBStarted = Regex("stage b", RegexOption.IGNORE_CASE).containsMatchIn(currentStatus.message)
            val atStageA = state in setOf("queued", "stage_a_running", "awaiting_torrent_selection")
            val failedTask = state == "error" || state == "cancelled"
            val canRestartA = atStageA || failedTask
            val canRestartB = (plan != null || (failedTask && stageBStarted)) && !(isSuperSeriesTask && state == "complete")
            val canCancel = state in setOf("queued", "stage_a_running", "stage_b_queued", "stage_b_running")
            if (canRestartA || canRestartB || canCancel) {
                CfSection(title = "Stage controls") {
                    if (canRestartA) ActionButton(
                        label = "Restart Stage A", icon = EngineIcons.Restart,
                        busy = busyOf(vm, "restart_a"),
                        onClick = { vm.restartStageA(jobId) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (canRestartB) {
                        TonalActionButton(
                            label = "Restart Stage B", icon = EngineIcons.Restart,
                            busy = busyOf(vm, "restart_b"),
                            onClick = { vm.restartStageB(jobId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (plan == null) {
                            Text(
                                "No production.json yet — Stage B restart will ask you to upload one first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (canCancel) OutlinedActionButton(
                        label = "Cancel running stage", icon = EngineIcons.Cancel, destructive = true,
                        busy = busyOf(vm, "cancel_stage"),
                        onClick = { showCancelConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---- LIVE LOGGER (v23-R5) ----
            LoggerCard(vm = vm, logs = logs, state = currentStatus.state)
        }
    }

    // ---- dialogs ----
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel the running stage for task ${jobId.substringAfter("manual-")}?") },
            text = { Text("The running render is stopped and the job moves to cancelled. You can restart it afterwards.") },
            confirmButton = {
                TextActionButton(label = "Yes, cancel", destructive = true, busy = busyOf(vm, "cancel_stage"), onClick = {
                    showCancelConfirm = false
                    vm.cancelRunningStage(jobId)
                })
            },
            dismissButton = { TextActionButton(label = "Back", onClick = { showCancelConfirm = false }) },
        )
    }
    if (showScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("Schedule this video") },
            text = {
                OutlinedTextField(
                    value = scheduleInput, onValueChange = { scheduleInput = it },
                    label = { Text("Local time") },
                    placeholder = { Text("2026-09-20T17:30") },
                    supportingText = { Text("YYYY-MM-DDTHH:MM, in your publishing timezone (Settings → Publishing).") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextActionButton(label = "Schedule", enabled = ZernioPublish.validDateTime(scheduleInput), onClick = {
                    showScheduleDialog = false
                    vm.publishTask(jobId, "manual_schedule", scheduleInput)
                })
            },
            dismissButton = { TextActionButton(label = "Cancel", onClick = { showScheduleDialog = false }) },
        )
    }
    reschedulePost?.let { post ->
        AlertDialog(
            onDismissRequest = { reschedulePost = null },
            title = { Text("Reschedule ${post.platform} post") },
            text = {
                OutlinedTextField(
                    value = rescheduleInput, onValueChange = { rescheduleInput = it },
                    label = { Text("Local time") },
                    placeholder = { Text("2026-09-20T17:30") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextActionButton(label = "Reschedule", enabled = ZernioPublish.validDateTime(rescheduleInput), onClick = {
                    vm.zernioPostAction(jobId, "reschedule", post.postId, "manual_schedule", rescheduleInput)
                    reschedulePost = null
                })
            },
            dismissButton = { TextActionButton(label = "Cancel", onClick = { reschedulePost = null }) },
        )
    }
    cancelPost?.let { post ->
        AlertDialog(
            onDismissRequest = { cancelPost = null },
            title = { Text("Cancel ${post.platform} post?") },
            text = { Text("The scheduled post is cancelled. You can schedule it again afterwards.") },
            confirmButton = {
                TextActionButton(label = "Cancel post", destructive = true, busy = busyOf(vm, "zernio_post"), onClick = {
                    vm.zernioPostAction(jobId, "cancel", post.postId)
                    cancelPost = null
                })
            },
            dismissButton = { TextActionButton(label = "Back", onClick = { cancelPost = null }) },
        )
    }
}

/* ------------------------------ live logger ----------------------------- */

@Composable
private fun LoggerCard(vm: ClipForgeViewModel, logs: List<ClipForgeViewModel.LogStep>, state: String) {
    val context = LocalContext.current
    CfSection(title = "Live execution log") {
        Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
            TextActionButton(label = "Copy log", onClick = {
                val text = logs.joinToString("\n") { step ->
                    "### " + step.name + "\n" + step.details.joinToString("\n") { it.text }
                }
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("ClipForge log", text))
                vm.toast("Log copied")
            })
        }
        when {
            logs.isEmpty() -> CfLoading(
                if (state == "queued" || state.endsWith("queued")) "Waiting for a GitHub runner — steps appear here as soon as the run starts…"
                else "Waiting for the first step…",
            )
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(logs) {
                    if (logs.isNotEmpty()) {
                        val runningIdx = logs.indexOfLast { it.level == ClipForgeViewModel.LogLevel.RUNNING }
                        val target = if (runningIdx >= 0) runningIdx else logs.size - 1
                        listState.animateScrollToItem(target.coerceAtMost(logs.size - 1))
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                ) {
                    items(logs, key = { it.key }) { step ->
                        LogStepRow(step, vm.isStepExpanded(step)) { vm.toggleStepExpanded(step.key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogStepRow(step: ClipForgeViewModel.LogStep, expanded: Boolean, onToggle: () -> Unit) {
    val accent = cfStatusColor(step.level)
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.medium,
        color = ElevationTokens.tonalContainerColor(2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(SpacingTokens.Spacing.sm)) {
            // Status-colored timeline node.
            Box(
                modifier = Modifier
                    .padding(top = SpacingTokens.Spacing.xxs)
                    .size(SpacingTokens.Spacing.sm)
                    .background(accent, CircleShape),
            )
            Spacer(Modifier.width(SpacingTokens.Spacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(step.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = accent)
                    if (step.durationText.isNotBlank()) {
                        Text(step.durationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (step.level == ClipForgeViewModel.LogLevel.RUNNING) {
                    EngineLinearWavyProgress(Modifier.fillMaxWidth())
                }
                if (expanded) {
                    step.details.forEach { d -> CfLogLine(d.text, cfStatusColor(d.level)) }
                }
            }
        }
    }
}
