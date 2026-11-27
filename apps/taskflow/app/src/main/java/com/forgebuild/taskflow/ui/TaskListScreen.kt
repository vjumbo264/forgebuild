@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
package com.forgebuild.taskflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.forgebuild.engine.ui.components.EngineCircularWavyProgress
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.Task
import com.forgebuild.taskflow.data.TaskCategory
import com.forgebuild.taskflow.data.TimerEngine
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * TaskFlow Home — Responsive four-category Eisenhower Matrix layout inside Material 3 Expressive:
 *  - Wide viewports (≥ 600dp): 2x2 Eisenhower Matrix grid with all four categories side by side.
 *  - Narrow/mobile viewports (< 600dp): Clean stacked & collapsible category cards so titles and
 *    task rows wrap cleanly without clipping or horizontal overflow.
 *  - Every category displays title, count, completion/edit/delete/move actions, quick-add scoped
 *    to that category, and a clear empty state.
 */
@Composable
fun TaskListScreen(
    vm: TaskViewModel,
    onEditTask: (Long) -> Unit,
    onOpenUnfinished: () -> Unit,
    onOpenCompleted: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateTask: (TaskCategory) -> Unit,
    onOpenChat: (startListening: Boolean) -> Unit
) {
    val tasks by vm.tasks.collectAsState()
    val groupedTasks by vm.groupedTasks.collectAsState()
    val categoryCounts by vm.categoryCounts.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val breadcrumbs by vm.breadcrumbs.collectAsState()
    val timeRange by vm.timeRange.collectAsState()
    val allocatedMinutes by vm.allocatedMinutesToday.collectAsState()
    val remainingMinutes by vm.remainingMinutesToday.collectAsState()
    val usedDayMinutes by vm.usedDayMinutes.collectAsState()

    var peekInfo by remember { mutableStateOf<Task?>(null) }
    var moveCategoryTarget by remember { mutableStateOf<Task?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var extendTarget by remember { mutableStateOf<Task?>(null) }
    var dingedIds by remember { mutableStateOf(setOf<Long>()) }

    // Collapsible state for narrow/mobile view (all 4 quadrants expanded by default)
    val expandedMap = remember {
        mutableStateMapOf(
            TaskCategory.URGENT_IMPORTANT to true,
            TaskCategory.NOT_URGENT_IMPORTANT to true,
            TaskCategory.URGENT_NOT_IMPORTANT to true,
            TaskCategory.NOT_URGENT_NOT_IMPORTANT to true
        )
    }
    // Inline quick-add bar visibility per quadrant
    val quickAddOpenMap = remember { mutableStateMapOf<TaskCategory, Boolean>() }

    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            nowTick = System.currentTimeMillis()
        }
    }

    LaunchedEffect(tasks, nowTick) {
        tasks.firstOrNull {
            TimerEngine.stateOf(it, nowTick) == TimerEngine.TimerState.FINISHED && it.id !in dingedIds
        }?.let { t ->
            dingedIds = dingedIds + t.id
            com.forgebuild.taskflow.notify.TimerSounds.playCompletion()
        }
        dingedIds = dingedIds.filter { id ->
            tasks.firstOrNull { it.id == id }?.let {
                TimerEngine.stateOf(it, nowTick) == TimerEngine.TimerState.FINISHED
            } == true
        }.toSet()
    }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.userMessage.collectLatest { msg -> snackbarHostState.showSnackbar(msg) }
    }

    var draggingTaskId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text("TaskFlow", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = breadcrumbs.lastOrNull()?.second ?: "Eisenhower Matrix",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(EngineIcons.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Unfinished") },
                                leadingIcon = { Icon(EngineIcons.Alarm, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; onOpenUnfinished() }
                            )
                            DropdownMenuItem(
                                text = { Text("Completed") },
                                leadingIcon = { Icon(EngineIcons.CheckCircle, null) },
                                onClick = { menuOpen = false; onOpenCompleted() }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(EngineIcons.Settings, null) },
                                onClick = { menuOpen = false; onOpenSettings() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)
            ) {
                SmallFloatingActionButton(
                    onClick = { onOpenChat(true) },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) { Icon(EngineIcons.Mic, contentDescription = "Voice note to AI") }
                SmallFloatingActionButton(
                    onClick = { onOpenChat(false) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) { Icon(EngineIcons.SmartToy, contentDescription = "TaskFlow AI chat") }
                ExtendedFloatingActionButton(
                    onClick = { onCreateTask(TaskCategory.DEFAULT) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(EngineIcons.Add, contentDescription = null) },
                    text = { Text("New task") }
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val isWide = maxWidth >= 600.dp
            val hMargin = SpacingTokens.contentMargin(maxWidth)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = hMargin,
                    end = hMargin,
                    top = SpacingTokens.Spacing.sm,
                    bottom = 140.dp
                ),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)
            ) {
                if (breadcrumbs.isNotEmpty()) {
                    item(key = "breadcrumbs") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.navigateToBreadcrumb(-1) }) {
                                Text("Tasks", style = MaterialTheme.typography.labelLarge)
                            }
                            breadcrumbs.forEachIndexed { i, (_, title) ->
                                Icon(
                                    EngineIcons.KeyboardArrowRight, null,
                                    Modifier
                                        .alpha(0.6f)
                                        .size(16.dp)
                                )
                                TextButton(onClick = { vm.navigateToBreadcrumb(i) }) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "today_hero") {
                    TodayHero(
                        allocatedMinutes = allocatedMinutes,
                        remainingMinutes = remainingMinutes,
                        usedDayMinutes = usedDayMinutes
                    )
                }

                item(key = "time_range_and_search") {
                    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            TimeRangeView.entries.forEachIndexed { index, range ->
                                SegmentedButton(
                                    selected = timeRange == range,
                                    onClick = { vm.setTimeRange(range) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = TimeRangeView.entries.size
                                    )
                                ) {
                                    Text(
                                        text = range.name.lowercase().replaceFirstChar { it.uppercase() },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { vm.setSearchQuery(it) },
                            placeholder = {
                                Text(
                                    "Search tasks across all 4 categories…",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = { Icon(EngineIcons.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { vm.setSearchQuery("") }) {
                                        Icon(EngineIcons.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (isWide) {
                    val matrixRows = listOf(
                        "matrix_row_1" to (TaskCategory.URGENT_IMPORTANT to TaskCategory.NOT_URGENT_IMPORTANT),
                        "matrix_row_2" to (TaskCategory.URGENT_NOT_IMPORTANT to TaskCategory.NOT_URGENT_NOT_IMPORTANT)
                    )
                    items(matrixRows, key = { it.first }) { (_, pair) ->
                        val (leftCat, rightCat) = pair
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md),
                            verticalAlignment = Alignment.Top
                        ) {
                            QuadrantCard(
                                category = leftCat,
                                tasks = groupedTasks[leftCat].orEmpty(),
                                count = categoryCounts[leftCat] ?: 0,
                                isCompactGrid = true,
                                collapsible = false,
                                expanded = true,
                                onToggleExpand = {},
                                quickAddOpen = quickAddOpenMap[leftCat] == true,
                                onToggleQuickAdd = {
                                    quickAddOpenMap[leftCat] = !(quickAddOpenMap[leftCat] ?: false)
                                },
                                hasSearchQuery = searchQuery.isNotBlank(),
                                nowTick = nowTick,
                                draggingTaskId = draggingTaskId,
                                dragOffset = dragOffset,
                                onQuickAddSubmit = { title ->
                                    vm.addTask(title = title, durationMinutes = 30L, category = leftCat)
                                },
                                onOpenFullCreate = { onCreateTask(leftCat) },
                                onToggleTask = { vm.toggleComplete(it) },
                                onOpenSubtasks = { vm.openTask(it.id) },
                                onEditTask = { onEditTask(it.id) },
                                onDeleteTask = { vm.delete(it) },
                                onMoveCategory = { moveCategoryTarget = it },
                                onPeekInfo = { peekInfo = it },
                                onTimerToggle = { vm.timerToggle(it) },
                                onTimerExtend = { extendTarget = it },
                                onTimerComplete = { vm.timerComplete(it) },
                                onDragStart = { t -> draggingTaskId = t.id; dragOffset = 0f },
                                onDragBy = { dragOffset += it },
                                onDragEnd = { t ->
                                    val itemH = 76 * 3
                                    val delta = (dragOffset / itemH).roundToInt()
                                    if (delta != 0) vm.moveRelative(t, delta)
                                    draggingTaskId = null
                                    dragOffset = 0f
                                },
                                modifier = Modifier.weight(1f)
                            )

                            QuadrantCard(
                                category = rightCat,
                                tasks = groupedTasks[rightCat].orEmpty(),
                                count = categoryCounts[rightCat] ?: 0,
                                isCompactGrid = true,
                                collapsible = false,
                                expanded = true,
                                onToggleExpand = {},
                                quickAddOpen = quickAddOpenMap[rightCat] == true,
                                onToggleQuickAdd = {
                                    quickAddOpenMap[rightCat] = !(quickAddOpenMap[rightCat] ?: false)
                                },
                                hasSearchQuery = searchQuery.isNotBlank(),
                                nowTick = nowTick,
                                draggingTaskId = draggingTaskId,
                                dragOffset = dragOffset,
                                onQuickAddSubmit = { title ->
                                    vm.addTask(title = title, durationMinutes = 30L, category = rightCat)
                                },
                                onOpenFullCreate = { onCreateTask(rightCat) },
                                onToggleTask = { vm.toggleComplete(it) },
                                onOpenSubtasks = { vm.openTask(it.id) },
                                onEditTask = { onEditTask(it.id) },
                                onDeleteTask = { vm.delete(it) },
                                onMoveCategory = { moveCategoryTarget = it },
                                onPeekInfo = { peekInfo = it },
                                onTimerToggle = { vm.timerToggle(it) },
                                onTimerExtend = { extendTarget = it },
                                onTimerComplete = { vm.timerComplete(it) },
                                onDragStart = { t -> draggingTaskId = t.id; dragOffset = 0f },
                                onDragBy = { dragOffset += it },
                                onDragEnd = { t ->
                                    val itemH = 76 * 3
                                    val delta = (dragOffset / itemH).roundToInt()
                                    if (delta != 0) vm.moveRelative(t, delta)
                                    draggingTaskId = null
                                    dragOffset = 0f
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    items(TaskCategory.entries, key = { "cat_${it.id}" }) { cat ->
                        QuadrantCard(
                            category = cat,
                            tasks = groupedTasks[cat].orEmpty(),
                            count = categoryCounts[cat] ?: 0,
                            isCompactGrid = false,
                            collapsible = true,
                            expanded = expandedMap[cat] != false,
                            onToggleExpand = {
                                expandedMap[cat] = !(expandedMap[cat] != false)
                            },
                            quickAddOpen = quickAddOpenMap[cat] == true,
                            onToggleQuickAdd = {
                                expandedMap[cat] = true
                                quickAddOpenMap[cat] = !(quickAddOpenMap[cat] ?: false)
                            },
                            hasSearchQuery = searchQuery.isNotBlank(),
                            nowTick = nowTick,
                            draggingTaskId = draggingTaskId,
                            dragOffset = dragOffset,
                            onQuickAddSubmit = { title ->
                                vm.addTask(title = title, durationMinutes = 30L, category = cat)
                            },
                            onOpenFullCreate = { onCreateTask(cat) },
                            onToggleTask = { vm.toggleComplete(it) },
                            onOpenSubtasks = { vm.openTask(it.id) },
                            onEditTask = { onEditTask(it.id) },
                            onDeleteTask = { vm.delete(it) },
                            onMoveCategory = { moveCategoryTarget = it },
                            onPeekInfo = { peekInfo = it },
                            onTimerToggle = { vm.timerToggle(it) },
                            onTimerExtend = { extendTarget = it },
                            onTimerComplete = { vm.timerComplete(it) },
                            onDragStart = { t -> draggingTaskId = t.id; dragOffset = 0f },
                            onDragBy = { dragOffset += it },
                            onDragEnd = { t ->
                                val itemH = 76 * 3
                                val delta = (dragOffset / itemH).roundToInt()
                                if (delta != 0) vm.moveRelative(t, delta)
                                draggingTaskId = null
                                dragOffset = 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    moveCategoryTarget?.let { target ->
        MoveCategorySheet(
            task = target,
            onMove = { newCategory -> vm.moveTaskCategory(target, newCategory) },
            onDismiss = { moveCategoryTarget = null }
        )
    }

    extendTarget?.let { t ->
        var hoursTxt by remember(t.id) { mutableStateOf("") }
        var minsTxt by remember(t.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { extendTarget = null },
            title = {
                Column {
                    Text(
                        if (TimerEngine.stateOf(t, nowTick) == TimerEngine.TimerState.FINISHED) "Time's up"
                        else "Extend timer",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "Add more time — same units used across the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)) {
                        OutlinedTextField(
                            value = hoursTxt, onValueChange = { hoursTxt = it.filter(Char::isDigit).take(2) },
                            label = { Text("Hours") }, singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minsTxt, onValueChange = { minsTxt = it.filter(Char::isDigit).take(3) },
                            label = { Text("Minutes") }, singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                        listOf(5, 10, 15, 30).forEach { m ->
                            FilterChip(
                                selected = false,
                                onClick = { vm.timerExtend(t, m.toLong()); extendTarget = null },
                                label = { Text("+${m}m") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val total = (hoursTxt.toLongOrNull() ?: 0L) * 60L + (minsTxt.toLongOrNull() ?: 0L)
                    if (total > 0L) vm.timerExtend(t, total)
                    extendTarget = null
                }) { Text("Add time") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.timerComplete(t)
                    extendTarget = null
                }) { Text("Mark complete", color = MaterialTheme.colorScheme.primary) }
            }
        )
    }

    peekInfo?.let { t ->
        ModalBottomSheet(onDismissRequest = { peekInfo = null }) {
            Column(Modifier.padding(SpacingTokens.Spacing.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        t.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    DurationPill(minutes = t.durationMinutes)
                }
                Spacer(Modifier.height(SpacingTokens.Spacing.xs))
                Text(
                    text = t.category.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = categoryVisualStyle(t.category).accentColor
                )
                Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                if (t.info.isNotBlank()) {
                    Text(t.info, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        "No additional info provided for this task.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.height(SpacingTokens.Spacing.md))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        val id = t.id
                        peekInfo = null
                        onEditTask(id)
                    }) { Text("Edit full task") }
                }
            }
        }
    }
}

@Composable
private fun QuadrantCard(
    category: TaskCategory,
    tasks: List<Task>,
    count: Int,
    isCompactGrid: Boolean,
    collapsible: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    quickAddOpen: Boolean,
    onToggleQuickAdd: () -> Unit,
    hasSearchQuery: Boolean,
    nowTick: Long,
    draggingTaskId: Long?,
    dragOffset: Float,
    onQuickAddSubmit: (String) -> Unit,
    onOpenFullCreate: () -> Unit,
    onToggleTask: (Task) -> Unit,
    onOpenSubtasks: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onMoveCategory: (Task) -> Unit,
    onPeekInfo: (Task) -> Unit,
    onTimerToggle: (Task) -> Unit,
    onTimerExtend: (Task) -> Unit,
    onTimerComplete: (Task) -> Unit,
    onDragStart: (Task) -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = categoryVisualStyle(category)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "quadrantChevron"
    )
    var quickTitle by remember(category) { mutableStateOf("") }
    val uniqueTasks = remember(tasks) { tasks.distinctBy { it.id } }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = style.cardContainerColor,
        border = BorderStroke(1.dp, style.accentColor.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (collapsible) Modifier.clickable(onClick = onToggleExpand)
                        else Modifier
                    )
                    .padding(horizontal = SpacingTokens.Spacing.xxs, vertical = SpacingTokens.Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = style.badgeContainerColor
                ) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        tint = style.badgeContentColor,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }

                Spacer(Modifier.width(SpacingTokens.Spacing.xs))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = category.actionHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = style.accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(SpacingTokens.Spacing.xs))

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = style.badgeContainerColor
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = style.badgeContentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                IconButton(
                    onClick = onToggleQuickAdd,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (quickAddOpen) EngineIcons.Close else EngineIcons.Add,
                        contentDescription = "Quick add to ${category.label}",
                        tint = style.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (collapsible) {
                    Icon(
                        imageVector = EngineIcons.KeyboardArrowRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }

            AnimatedVisibility(visible = expanded || !collapsible) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                ) {
                    AnimatedVisibility(visible = quickAddOpen) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
                            ) {
                                OutlinedTextField(
                                    value = quickTitle,
                                    onValueChange = { quickTitle = it },
                                    placeholder = {
                                        Text(
                                            "Quick add to ${category.actionHint.lowercase()}…",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (quickTitle.isNotBlank()) {
                                            onQuickAddSubmit(quickTitle.trim())
                                            quickTitle = ""
                                        }
                                    }),
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        if (quickTitle.isNotBlank()) {
                                            onQuickAddSubmit(quickTitle.trim())
                                            quickTitle = ""
                                        }
                                    },
                                    enabled = quickTitle.isNotBlank(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text("Add")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = onOpenFullCreate,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Schedule, duration & options…",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    if (uniqueTasks.isEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(SpacingTokens.Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)
                            ) {
                                Text(
                                    text = if (hasSearchQuery) "No matching tasks in ${category.label}"
                                    else "No tasks in ${category.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!hasSearchQuery) {
                                    TextButton(
                                        onClick = onOpenFullCreate,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            EngineIcons.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Add task",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        HorizontalDivider(color = style.accentColor.copy(alpha = 0.14f))
                        uniqueTasks.forEach { task ->
                            key(task.id) {
                                val isDragging = draggingTaskId == task.id
                                TaskRowItem(
                                    task = task,
                                    compact = isCompactGrid,
                                    nowTick = nowTick,
                                    onTimerToggle = { onTimerToggle(task) },
                                    onTimerExtend = { onTimerExtend(task) },
                                    onTimerComplete = { onTimerComplete(task) },
                                    modifier = Modifier
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (isDragging) dragOffset else 0f
                                            val s = if (isDragging) 1.03f else 1f
                                            scaleX = s
                                            scaleY = s
                                        },
                                    onToggle = { onToggleTask(task) },
                                    onOpen = { onOpenSubtasks(task) },
                                    onEdit = { onEditTask(task) },
                                    onDelete = { onDeleteTask(task) },
                                    onMoveCategory = { onMoveCategory(task) },
                                    onPeekInfo = { onPeekInfo(task) },
                                    onDragStart = { onDragStart(task) },
                                    onDragBy = onDragBy,
                                    onDragEnd = { onDragEnd(task) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayHero(allocatedMinutes: Long, remainingMinutes: Long, usedDayMinutes: Long) {
    val total = (allocatedMinutes + remainingMinutes).coerceAtLeast(1L)
    val booked = (allocatedMinutes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val dayUsed = (usedDayMinutes.toFloat() / 1440f).coerceIn(0f, 1f)

    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(SpacingTokens.Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    EngineCircularWavyProgress(
                        progress = { booked },
                        modifier = Modifier.size(84.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(booked * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "booked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(SpacingTokens.Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${remainingMinutes / 60}h ${remainingMinutes % 60}m left",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${allocatedMinutes / 60}h ${allocatedMinutes % 60}m scheduled across your tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(SpacingTokens.Spacing.sm))
            EngineLinearWavyProgress(
                progress = { dayUsed },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
            Text(
                "Day used: ${usedDayMinutes / 60}h ${usedDayMinutes % 60}m of 24h (elapsed + booked)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
