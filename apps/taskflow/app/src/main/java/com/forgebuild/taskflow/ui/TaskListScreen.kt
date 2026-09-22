package com.forgebuild.taskflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.Recurrence
import com.forgebuild.taskflow.data.Task
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Main task list: priority-sorted, due-now pinned + highlighted, drag-reorder, FABs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(vm: TaskViewModel, onEditTask: (Long) -> Unit, onOpenSettings: () -> Unit, onOpenChat: () -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val breadcrumbs by vm.breadcrumbs.collectAsState()
    var quickAdd by remember { mutableStateOf("") }
    var peekInfo by remember { mutableStateOf<Task?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // drag state: index being dragged + accumulated pixel offset
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Scaffold(
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
                                        Text(title, style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(EngineIcons.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer))
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md)) {
                LargeFloatingActionButton(onClick = onOpenChat,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(EngineIcons.Mic, contentDescription = "Talk to AI")
                }
            }
        }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Quick-add field
            OutlinedTextField(
                value = quickAdd, onValueChange = { quickAdd = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = SpacingTokens.Spacing.md),
                placeholder = { Text("Add a task…") },
                trailingIcon = {
                    IconButton(onClick = {
                        vm.addTask(quickAdd, null, Recurrence.NONE, 0, "")
                        quickAdd = ""
                    }) { Icon(EngineIcons.Add, contentDescription = "Add") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    vm.addTask(quickAdd, null, Recurrence.NONE, 0, "")
                    quickAdd = ""
                }),
                singleLine = true, shape = MaterialTheme.shapes.large)
            Spacer(Modifier.height(SpacingTokens.Spacing.sm))

            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(EngineIcons.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.height(SpacingTokens.Spacing.xxxl))
                        Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Add a task above, or tap the mic and just say it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(tasks, key = { _, t -> t.id }) { index, task ->
                        val isDragging = dragIndex == index
                        val elev by animateFloatAsState(if (isDragging) 1.06f else 1f, label = "dragScale")
                        TaskRow(
                            task = task,
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                    scaleX = elev; scaleY = elev
                                },
                            onToggle = { vm.toggleComplete(task) },
                            onOpen = { vm.openTask(task.id) },
                            onEdit = { onEditTask(task.id) },
                            onDelete = { vm.delete(task) },
                            onPeekInfo = { peekInfo = task },
                            onDragStart = { dragIndex = index; dragOffset = 0f },
                            onDragBy = { dragOffset += it },
                            onDragEnd = {
                                val itemH = 72 * 3 // approx row height in px incl. spacing
                                val delta = (dragOffset / itemH).roundToInt()
                                if (delta != 0) vm.moveRelative(task, delta)
                                dragIndex = null; dragOffset = 0f
                            })
                    }
                }
            }
        }
    }

    peekInfo?.let { t ->
        ModalBottomSheet(onDismissRequest = { peekInfo = null }) {
            Column(Modifier.padding(SpacingTokens.Spacing.lg)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                Text(t.info, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(SpacingTokens.Spacing.sm))
                Text("Read-only quick view — edit this on the task's edit screen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(SpacingTokens.Spacing.lg))
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task, modifier: Modifier = Modifier,
    onToggle: () -> Unit, onOpen: () -> Unit, onEdit: () -> Unit,
    onDelete: () -> Unit, onPeekInfo: () -> Unit,
    onDragStart: () -> Unit, onDragBy: (Float) -> Unit, onDragEnd: () -> Unit,
) {
    val container by animateColorAsState(
        if (task.dueNow) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceContainerLow, label = "dueNowColor")
    Surface(modifier = modifier.fillMaxWidth().padding(
            horizontal = SpacingTokens.Spacing.md, vertical = SpacingTokens.Spacing.xxs),
        shape = MaterialTheme.shapes.medium, color = container) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(SpacingTokens.Spacing.xs)) {
            Icon(
                EngineIcons.DragHandle, contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.pointerInput(task.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, amount -> change.consume(); onDragBy(amount.y) })
                })
            IconButton(onClick = onToggle) {
                Icon(if (task.completed) EngineIcons.CheckCircle else EngineIcons.RadioButtonUnchecked,
                    contentDescription = if (task.completed) "Mark incomplete" else "Mark complete",
                    tint = if (task.completed) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.outline)
            }
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (task.dueNow) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.dueNow) {
                        Icon(EngineIcons.PriorityHigh, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text("DUE NOW", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                    }
                    task.fixedTime?.let {
                        Icon(EngineIcons.Alarm, null, Modifier.height(SpacingTokens.Spacing.md),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(it)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                    }
                    if (task.recurrence != Recurrence.NONE || task.isRecurringTemplate) {
                        Icon(EngineIcons.Repeat, null, Modifier.height(SpacingTokens.Spacing.md),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(SpacingTokens.Spacing.xs))
                    }
                }
            }
            if (task.info.isNotBlank()) {
                IconButton(onClick = onPeekInfo) {
                    Icon(EngineIcons.Visibility, contentDescription = "View info",
                        tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            IconButton(onClick = onOpen) {
                Icon(EngineIcons.SubdirectoryArrowRight, contentDescription = "Sub-tasks",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) {
                Icon(EngineIcons.KeyboardArrowRight, contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(EngineIcons.Delete, contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
