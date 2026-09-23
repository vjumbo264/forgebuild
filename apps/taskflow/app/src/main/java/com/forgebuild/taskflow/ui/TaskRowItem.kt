@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.forgebuild.taskflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.MotionTokens
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.DayAccounting
import com.forgebuild.taskflow.data.Task
import com.forgebuild.taskflow.data.TaskType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small rounded duration chip shared by rows and detail sheets. */
@Composable
fun DurationPill(minutes: Long) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = "${minutes}m",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(
                horizontal = SpacingTokens.Spacing.xs,
                vertical = SpacingTokens.Spacing.xxs / 2
            )
        )
    }
}

/**
 * Rebuilt expressive task row — full-bleed container on the largeIncreased shape tier,
 * a coloured type-dot, expressive press/drag motion via the global MotionScheme, and a
 * per-row overflow menu instead of a row of trailing icon buttons.
 */
@Composable
fun TaskRowItem(
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
    var rowMenuOpen by remember { mutableStateOf(false) }

    // Overnight/cross-midnight tasks show their linked span; still ONE task.
    val overnightLabel: String? = remember(task.fixedTime, task.durationMinutes) {
        val ft = task.fixedTime ?: return@remember null
        if (!DayAccounting.crossesMidnight(task)) return@remember null
        val end = ft + task.durationMinutes.coerceAtLeast(1L) * 60_000L
        val days = ((DayAccounting.dayStart(end - 1L) - DayAccounting.dayStart(ft)) / 86_400_000L).toInt()
        "${timeFmt.format(Date(ft))} → ${timeFmt.format(Date(end))}" + if (days > 0) " +${days}d" else ""
    }

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

    // 4-type colour language: dot + container tint per type; due-now overrides all.
    val typeColor = when (task.taskType) {
        TaskType.RECURRING_FIXED -> MaterialTheme.colorScheme.tertiary
        TaskType.RECURRING_NO_TIME -> MaterialTheme.colorScheme.primary
        TaskType.FIXED_TIME -> MaterialTheme.colorScheme.secondary
        TaskType.NORMAL -> MaterialTheme.colorScheme.outline
    }
    val targetContainer = when {
        task.dueNow -> MaterialTheme.colorScheme.errorContainer
        task.taskType == TaskType.RECURRING_FIXED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.40f)
        task.taskType == TaskType.RECURRING_NO_TIME -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        task.taskType == TaskType.FIXED_TIME -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    // Colour changes ride the official expressive effects spring, not a static swap.
    @Suppress("UNCHECKED_CAST")
    val effectsSpec = MotionTokens.defaultEffects as
        androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.graphics.Color>
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = effectsSpec,
        label = "rowContainer"
    )
    val badgeText = when (task.taskType) {
        TaskType.RECURRING_FIXED ->
            "Recurring · ${task.recurrence.name.lowercase().replaceFirstChar { it.uppercase() }}" +
                (overnightLabel?.let { " · $it" } ?: "")
        TaskType.RECURRING_NO_TIME ->
            "Recurring · ${task.recurrence.name.lowercase().replaceFirstChar { it.uppercase() }}"
        TaskType.FIXED_TIME ->
            task.fixedTime?.let { dateFmt.format(Date(it)) + (overnightLabel?.let { o -> " → $o" } ?: "") } ?: "Scheduled"
        TaskType.NORMAL -> ""
    }
    val badgeIcon = when (task.taskType) {
        TaskType.RECURRING_FIXED, TaskType.RECURRING_NO_TIME -> EngineIcons.Repeat
        TaskType.FIXED_TIME -> EngineIcons.Alarm
        TaskType.NORMAL -> null
    }

    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = containerColor,
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
            // Drag handle (long-press to reorder by fractional rank).
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

            // Type dot (replaces the old accent stripe).
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (task.dueNow) MaterialTheme.colorScheme.error else typeColor)
            )

            Checkbox(
                checked = task.completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )

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
                        color = if (task.dueNow) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    DurationPill(minutes = task.durationMinutes)
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (task.dueNow) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.error.copy(alpha = dueNowAlpha)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    EngineIcons.PriorityHigh, null,
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    "DUE NOW",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    } else if (badgeText.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (badgeIcon != null) {
                                Icon(badgeIcon, null, tint = typeColor, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Single overflow menu carries the secondary actions.
            Box {
                IconButton(onClick = { rowMenuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        EngineIcons.MoreVert,
                        contentDescription = "Task actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = rowMenuOpen, onDismissRequest = { rowMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Subtasks") },
                        leadingIcon = { Icon(EngineIcons.SubdirectoryArrowRight, null) },
                        onClick = { rowMenuOpen = false; onOpen() }
                    )
                    if (task.info.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("View notes") },
                            leadingIcon = { Icon(EngineIcons.Visibility, null) },
                            onClick = { rowMenuOpen = false; onPeekInfo() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(EngineIcons.Settings, null) },
                        onClick = { rowMenuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(EngineIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { rowMenuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}
