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
import androidx.compose.foundation.layout.height
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.forgebuild.engine.ui.components.EngineCircularWavyProgress
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
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer


/** A document with a top-level parts[] array is a Super Series plan regardless of
 *  how the task's super_series flag was recorded — validation and submission must
 *  follow the document's actual shape, never the flag alone. (Operator fix:
 *  previous versions ran the single-part production.json validator on a valid
 *  super-plan and rejected it with "'cuts' must contain at least one cut.") */
private fun looksLikeSuperPlan(text: String): Boolean = try {
    org.json.JSONObject(text).optJSONArray("parts") != null
} catch (_: Exception) { false }

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
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            if (refreshing) EngineLinearWavyProgress(Modifier.fillMaxWidth())
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                if (tasks.isEmpty() && !refreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CfEmptyState(title = emptyTitle, body = emptyBody)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPaddingForFloatingBar(),
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
    val publishError by vm.publishError.collectAsState()
    publishError?.let { PublishErrorDialog(err = it, onDismiss = { vm.clearPublishError() }) }
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
    val promptDuration = request?.optJSONObject("options")?.optInt("target_duration_seconds", -1) ?: -1
    val promptReady = promptDuration in 1..36000

    var rawPlanText by remember { mutableStateOf("") }
    var planErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var reschedulePost by remember { mutableStateOf<ZernioPublish.Post?>(null) }
    var rescheduleInput by remember { mutableStateOf("") }
    var cancelPost by remember { mutableStateOf<ZernioPublish.Post?>(null) }

    val planFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                rawPlanText = String(bytes, Charsets.UTF_8)
                planErrors = if (isSuperSeriesTask || looksLikeSuperPlan(rawPlanText)) SuperSeries.parseAndValidateSuperPlan(rawPlanText).errors
                else PlanValidator.validate(rawPlanText)
                vm.toast(if (planErrors.isEmpty()) "Loaded a valid plan" else "Validation error: ${planErrors.first()}")
            }
        } catch (e: Exception) {
            vm.toast("Failed to read JSON: ${e.message}")
        }
    }

    // Round-12 fix 2: swap in this task's last-known content SYNCHRONOUSLY during
    // composition (before the first frame is drawn) — reopening an already-loaded
    // task shows it instantly with no loading flash; the poll below then merges in
    // whatever is new (updated status, new log lines) in place.
    val hadCachedDetail = remember(jobId) { vm.prepareTaskDetail(jobId) }

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
                // Reachable only on the very first open of a task never loaded before
                // (no in-memory or on-disk cache) — a REOPEN always has
                // prepareTaskDetail()'s synchronous restore above, so no flash.
                CfLoading(if (hadCachedDetail) "Restoring task…" else "Loading task…")
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
                    label = if (promptReady) "Copy agent prompt" else "Loading exact task duration…",
                    icon = EngineIcons.Copy,
                    enabled = promptReady,
                    onClick = {
                        val text = AgentPromptBuilder.build(
                            currentStatus, request,
                            login?.owner ?: "motionssalt", login?.repo ?: "clipforge",
                        )
                        if (text == null) {
                            vm.toast("The saved task duration is not available yet. Wait for task details to refresh and try again.")
                        } else {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("ClipForge Agent Prompt", text))
                            vm.toast("Agent prompt copied with ${promptDuration}s target")
                        }
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
                            else if (isSuperSeriesTask || looksLikeSuperPlan(it)) SuperSeries.parseAndValidateSuperPlan(it).errors
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
                            if (isSuperSeriesTask || looksLikeSuperPlan(rawPlanText)) vm.submitSuperPlan(jobId, rawPlanText) { rawPlanText = ""; planErrors = emptyList() }
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
                    // Finished jobs: immediate publish only — scheduling settings
                    // (smart schedule / manual schedule) are intentionally not shown.
                    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                        ActionButton(
                            label = if (pub.status == "published") "Publish again" else "Publish now",
                            busy = busyOf(vm, "publish_task"),
                            onClick = { vm.publishTask(jobId, "publish_now") },
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
private fun PublishErrorDialog(err: ClipForgeViewModel.ZernioDispatchError, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish dispatch failed") },
        text = {
            Column {
                Text(
                    "GitHub rejected the request for ${err.action}. Full error:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(err.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Copy error",
                onClick = { clipboard.setText(AnnotatedString(err.message)) }
            )
        },
        dismissButton = { TextActionButton(label = "Close", onClick = onDismiss) }
    )
}

@Composable
private fun LoggerCard(vm: ClipForgeViewModel, logs: List<ClipForgeViewModel.LogStep>, state: String) {
    val context = LocalContext.current
    val rawLog by vm.detailRawLog.collectAsState()
    var viewMode by remember { mutableStateOf(0) } // 0 = Step View, 1 = Raw Terminal

    val totalSteps = logs.size
    val completedSteps = logs.count { it.level == ClipForgeViewModel.LogLevel.SUCCESS }
    val runningStep = logs.firstOrNull { it.level == ClipForgeViewModel.LogLevel.RUNNING }
    val failedStep = logs.firstOrNull { it.level == ClipForgeViewModel.LogLevel.FAILURE }
    // Issue 3: rolling window — the active step plus the three preceding ones.
    // Full per-step detail lives in the Raw Console view; the step view renders
    // plain non-interactive rows only.
    val activeIdx = logs.indexOfFirst { it.level == ClipForgeViewModel.LogLevel.RUNNING }
        .let { if (it >= 0) it else logs.lastIndex }
    val windowStart = (activeIdx - 3).coerceAtLeast(0)
    val visibleSteps = if (logs.isEmpty()) logs else logs.subList(windowStart, (activeIdx + 1).coerceAtMost(logs.size))

    CfSection(
        title = "Pipeline Execution Log",
        subtitle = when {
            runningStep != null -> "Running step: ${runningStep.name}"
            failedStep != null -> "Failed at step: ${failedStep.name}"
            completedSteps == totalSteps && totalSteps > 0 -> "All $totalSteps steps completed"
            else -> "Real-time runner logs from GitHub Actions"
        }
    ) {
        // Top Toolbar: Mode Switcher + Copy (per-step expand removed — issue 3)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                FilterChip(
                    selected = viewMode == 0,
                    onClick = { viewMode = 0 },
                    label = { Text("Steps (${logs.size})", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    shape = CircleShape,
                )
                FilterChip(
                    selected = viewMode == 1,
                    onClick = { viewMode = 1 },
                    label = { Text("Raw Console", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    shape = CircleShape,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)) {
                if (viewMode == 0 && logs.isNotEmpty()) {
                }
                TextActionButton(
                    label = "Copy",
                    onClick = {
                        val textToCopy = if (viewMode == 1 && rawLog.isNotBlank()) {
                            rawLog
                        } else {
                            logs.joinToString("\n\n") { step ->
                                "### [${step.level}] ${step.name} (${step.durationText})\n" +
                                        step.details.joinToString("\n") { it.text }
                            }
                        }
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("ClipForge Log", textToCopy))
                        vm.toast("Log copied to clipboard")
                    }
                )
            }
        }

        // Live execution status bar
        if (runningStep != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(SpacingTokens.Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                    ) {
                        EngineCircularWavyProgress(Modifier.size(16.dp))
                        Text(
                            "In progress: ${runningStep.name}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (runningStep.durationText.isNotBlank()) {
                            Text(
                                runningStep.durationText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    EngineLinearWavyProgress(Modifier.fillMaxWidth())
                }
            }
        }

        when {
            logs.isEmpty() -> {
                CfLoading(
                    if (state == "queued" || state.endsWith("queued"))
                        "Waiting for a GitHub runner — steps appear here as soon as the run starts…"
                    else "Waiting for pipeline logs…",
                )
            }
            viewMode == 1 -> {
                // Raw Console Terminal View
                RawConsoleTerminal(rawText = rawLog.ifBlank {
                    logs.joinToString("\n") { step ->
                        "==> ${step.name}\n" + step.details.joinToString("\n") { it.text }
                    }
                })
            }
            else -> {
                // Structured Step-by-Step View
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs),
                ) {
                    if (totalSteps > 0) {
                        Text(
                            text = "Step ${activeIdx + 1} of $totalSteps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    visibleSteps.forEach { step ->
                        LogStepRow(step = step)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogStepRow(step: ClipForgeViewModel.LogStep) {
    val accent = cfStatusColor(step.level)
    val active = step.level == ClipForgeViewModel.LogLevel.RUNNING

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = ElevationTokens.tonalContainerColor(if (active) 2 else 1),
        border = BorderStroke(
            1.dp,
            if (active) accent.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SpacingTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)
            ) {
                // Status icon node
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (step.level) {
                        ClipForgeViewModel.LogLevel.RUNNING -> {
                            EngineCircularWavyProgress(Modifier.size(18.dp))
                        }
                        ClipForgeViewModel.LogLevel.SUCCESS -> {
                            Icon(
                                imageVector = EngineIcons.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        ClipForgeViewModel.LogLevel.FAILURE -> {
                            Icon(
                                imageVector = EngineIcons.Cancel,
                                contentDescription = "Failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                        }
                    }
                }

                Text(
                    text = step.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (step.level == ClipForgeViewModel.LogLevel.FAILURE) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )

                if (step.durationText.isNotBlank()) {
                    Text(
                        text = step.durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            }

            if (step.level == ClipForgeViewModel.LogLevel.RUNNING) {
                EngineLinearWavyProgress(Modifier.fillMaxWidth())
            }

        }
    }
}

@Composable

@Composable
private fun RawConsoleTerminal(rawText: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF141416),
        border = BorderStroke(1.dp, Color(0xFF26262B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = rawText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                ),
                color = Color(0xFFDCDCE5)
            )
        }
    }
}
