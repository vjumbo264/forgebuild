package com.forgebuild.taskflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.Task
import com.forgebuild.taskflow.data.TaskType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    vm: TaskViewModel,
    onEditTask: (Long) -> Unit,
    onOpenUnfinished: () -> Unit,
    onOpenCompleted: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateTask: () -> Unit,
    onOpenChat: (startListening: Boolean) -> Unit
) {
    val tasks by vm.tasks.collectAsState()
    val breadcrumbs by vm.breadcrumbs.collectAsState()
    val timeRange by vm.timeRange.collectAsState()
    val allocatedMinutes by vm.allocatedMinutesToday.collectAsState()
    val remainingMinutes by vm.remainingMinutesToday.collectAsState()

    var peekInfo by remember { mutableStateOf<Task?>(null) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.userMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TaskFlow", style = MaterialTheme.typography.titleLarge)
                        if (breadcrumbs.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { vm.navigateToBreadcrumb(-1) }) {
                                    Text("Tasks", style = MaterialTheme.typography.labelMedium)
                                }
                                breadcrumbs.forEachIndexed { i, (_, title) ->
                                    Icon(EngineIcons.KeyboardArrowRight, null, Modifier.alpha(0.6f))
                                    TextButton(onClick = { vm.navigateToBreadcrumb(i) }) {
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (breadcrumbs.isNotEmpty()) {
                        IconButton(onClick = { vm.navigateUp() }) {
                            Icon(EngineIcons.ArrowBack, contentDescription = "Up")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenUnfinished) {
                        Icon(EngineIcons.Alarm, contentDescription = "Unfinished tasks", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = onOpenCompleted) {
                        Icon(EngineIcons.CheckCircle, contentDescription = "Completed tasks")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(EngineIcons.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "+" add-task: opens the FULL creation sheet up front (nothing is created until confirmed there)
                FloatingActionButton(
                    onClick = onCreateTask,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(EngineIcons.Add, contentDescription = "New task")
                }

                // Persistent Voice AI FAB with microphone
                FloatingActionButton(
                    onClick = { onOpenChat(true) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(EngineIcons.Mic, contentDescription = "Talk to AI")
                }

                // AI Chat Assistant FAB
                ExtendedFloatingActionButton(
                    onClick = { onOpenChat(false) },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    icon = { Icon(EngineIcons.SmartToy, contentDescription = null) },
                    text = { Text("TaskFlow AI") }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = SpacingTokens.Spacing.md)
        ) {
            Spacer(Modifier.height(SpacingTokens.Spacing.xs))

            // TIME REMAINING TODAY CARD (prominent indicator)
            TimeRemainingTodayCard(
                allocatedMinutes = allocatedMinutes,
                remainingMinutes = remainingMinutes
            )

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            // TIME RANGE VIEW SWITCHER (Today / Week / Month / Year)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TimeRangeView.entries.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = timeRange == range,
                        onClick = { vm.setTimeRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TimeRangeView.entries.size)
                    ) {
                        Text(range.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }

            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            // Creation happens only through the "+" full creation sheet — no bare quick-add.
            Spacer(Modifier.height(SpacingTokens.Spacing.xs))

            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            EngineIcons.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(SpacingTokens.Spacing.xxxl * 1.5f)
                        )
                        Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                        Text(
                            "No tasks for this view",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Tap + to create a task, or the mic to speak to AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                ) {
                    itemsIndexed(tasks, key = { _, t -> t.id }) { index, task ->
                        val isDragging = dragIndex == index
                        val elev by animateFloatAsState(if (isDragging) 1.04f else 1f, label = "dragScale")
                        TaskRow(
                            task = task,
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                    scaleX = elev
                                    scaleY = elev
                                },
                            onToggle = { vm.toggleComplete(task) },
                            onOpen = { vm.openTask(task.id) },
                            onEdit = { onEditTask(task.id) },
                            onDelete = { vm.delete(task) },
                            onPeekInfo = { peekInfo = task },
                            onDragStart = { dragIndex = index; dragOffset = 0f },
                            onDragBy = { dragOffset += it },
                            onDragEnd = {
                                val itemH = 76 * 3
                                val delta = (dragOffset / itemH).roundToInt()
                                if (delta != 0) vm.moveRelative(task, delta)
                                dragIndex = null
                                dragOffset = 0f
                            }
                        )
                    }
                    item { Spacer(Modifier.height(84.dp)) }
                }
            }
        }
    }

    peekInfo?.let { t ->
        ModalBottomSheet(onDismissRequest = { peekInfo = null }) {
            Column(Modifier.padding(SpacingTokens.Spacing.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(t.title, style = MaterialTheme.typography.titleLarge)
                    Text("${t.durationMinutes}m", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                if (t.info.isNotBlank()) {
                    Text(t.info, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text("No additional info provided for this task.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(SpacingTokens.Spacing.md))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        val id = t.id
                        peekInfo = null
                        onEditTask(id)
                    }) {
                        Text("Edit full task")
                    }
                }
            }
        }
    }
}

/**
 * Prominent time remaining indicator card showing how much time is left today.
 */
@Composable
private fun TimeRemainingTodayCard(
    allocatedMinutes: Long,
    remainingMinutes: Long
) {
    val totalMinutes = (allocatedMinutes + remainingMinutes).coerceAtLeast(1L)
    val progress = (allocatedMinutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(SpacingTokens.Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        EngineIcons.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "TIME REMAINING TODAY",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "${remainingMinutes / 60}h ${remainingMinutes % 60}m unallocated",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${allocatedMinutes / 60}h ${allocatedMinutes % 60}m scheduled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(progress * 100).toInt()}% day booked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Visually distinct task row with 4-type color coding, due-now pinning/emphasis,
 * quick info peek action, and subtask navigation.
 */
@Composable
private fun TaskRow(
    task: Task,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPeekInfo: () -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault()) }

    // Overnight/cross-midnight tasks show their linked span: start → end (+Nd when it
    // spills into later calendar days). Still ONE task — one entry, one completion, one edit.
    val overnightLabel: String? = remember(task.fixedTime, task.durationMinutes) {
        val ft = task.fixedTime ?: return@remember null
        if (!com.forgebuild.taskflow.data.DayAccounting.crossesMidnight(task)) return@remember null
        val end = ft + task.durationMinutes.coerceAtLeast(1L) * 60_000L
        val days = ((com.forgebuild.taskflow.data.DayAccounting.dayStart(end - 1L) -
            com.forgebuild.taskflow.data.DayAccounting.dayStart(ft)) / 86_400_000L).toInt()
        "${timeFmt.format(Date(ft))} → ${timeFmt.format(Date(end))}" + if (days > 0) " +${days}d" else ""
    }

    // Pulsing effect for Due-Now tasks
    val infiniteTransition = rememberInfiniteTransition(label = "dueNowPulse")
    val dueNowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dueNowAlpha"
    )

    // Visual classification based on TaskType:
    // 1. Normal: clean surfaceContainerLow
    // 2. Scheduled (fixed time): secondaryContainer tint + secondary accent bar
    // 3. Recurring with fixed time: tertiaryContainer tint + tertiary accent bar
    // 4. Recurring without fixed time: primaryContainer tint + primary accent bar
    val (cardColor, accentColor, badgeText, badgeIcon) = when {
        task.dueNow -> Quadruple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            "DUE NOW",
            EngineIcons.PriorityHigh
        )
        task.taskType == TaskType.RECURRING_FIXED -> Quadruple(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.tertiary,
            "Recurring • ${task.recurrence.name.lowercase().replaceFirstChar { it.uppercase() }}" +
                (overnightLabel?.let { " · $it" } ?: ""),
            EngineIcons.Repeat
        )
        task.taskType == TaskType.RECURRING_NO_TIME -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.primary,
            "Recurring • ${task.recurrence.name.lowercase().replaceFirstChar { it.uppercase() }}",
            EngineIcons.Repeat
        )
        task.taskType == TaskType.FIXED_TIME -> Quadruple(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.secondary,
            task.fixedTime?.let { dateFmt.format(Date(it)) + (overnightLabel?.let { o -> " → $o" } ?: "") } ?: "Scheduled",
            EngineIcons.Alarm
        )
        else -> Quadruple(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.outlineVariant,
            "Task",
            null
        )
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = cardColor,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SpacingTokens.Spacing.xs, horizontal = SpacingTokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragBy(dragAmount.y)
                            }
                        )
                    }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    EngineIcons.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Left vertical accent stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            // Checkbox
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )

            // Title + Badges
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (task.dueNow) FontWeight.Bold else FontWeight.Normal,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else null
                        ),
                        color = if (task.dueNow) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(Modifier.width(6.dp))

                    // Duration badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Text(
                            text = "${task.durationMinutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                // Schedule / Recurrence / Due-Now badge row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (task.dueNow) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = dueNowAlpha)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(EngineIcons.PriorityHigh, null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("DUE NOW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    } else if (badgeText.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (badgeIcon != null) {
                                Icon(badgeIcon, null, tint = accentColor, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Peek eye action
            if (task.info.isNotBlank()) {
                IconButton(onClick = onPeekInfo, modifier = Modifier.size(36.dp)) {
                    Icon(EngineIcons.Visibility, contentDescription = "View notes", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }

            // Sub-tasks drilldown action
            IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                Icon(EngineIcons.SubdirectoryArrowRight, contentDescription = "Subtasks", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
