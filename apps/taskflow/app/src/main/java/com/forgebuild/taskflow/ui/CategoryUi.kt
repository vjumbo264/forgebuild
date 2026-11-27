@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.forgebuild.taskflow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import com.forgebuild.engine.ui.theme.SpacingTokens
import com.forgebuild.taskflow.data.Task
import com.forgebuild.taskflow.data.TaskCategory

data class CategoryVisualStyle(
    val accentColor: Color,
    val badgeContainerColor: Color,
    val badgeContentColor: Color,
    val cardContainerColor: Color,
    val icon: ImageVector,
    val quadrantShortTag: String
)

@Composable
@ReadOnlyComposable
fun categoryVisualStyle(category: TaskCategory): CategoryVisualStyle {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return when (category) {
        TaskCategory.URGENT_IMPORTANT -> CategoryVisualStyle(
            accentColor = cs.error,
            badgeContainerColor = cs.errorContainer,
            badgeContentColor = cs.onErrorContainer,
            cardContainerColor = cs.errorContainer.copy(alpha = if (dark) 0.16f else 0.24f),
            icon = EngineIcons.PriorityHigh,
            quadrantShortTag = "Q1 · Urgent & Important"
        )
        TaskCategory.NOT_URGENT_IMPORTANT -> CategoryVisualStyle(
            accentColor = cs.primary,
            badgeContainerColor = cs.primaryContainer,
            badgeContentColor = cs.onPrimaryContainer,
            cardContainerColor = cs.primaryContainer.copy(alpha = if (dark) 0.16f else 0.24f),
            icon = EngineIcons.CalendarToday,
            quadrantShortTag = "Q2 · Important, Not Urgent"
        )
        TaskCategory.URGENT_NOT_IMPORTANT -> CategoryVisualStyle(
            accentColor = cs.tertiary,
            badgeContainerColor = cs.tertiaryContainer,
            badgeContentColor = cs.onTertiaryContainer,
            cardContainerColor = cs.tertiaryContainer.copy(alpha = if (dark) 0.16f else 0.24f),
            icon = EngineIcons.Alarm,
            quadrantShortTag = "Q3 · Urgent, Unimportant"
        )
        TaskCategory.NOT_URGENT_NOT_IMPORTANT -> CategoryVisualStyle(
            accentColor = cs.secondary,
            badgeContainerColor = cs.secondaryContainer,
            badgeContentColor = cs.onSecondaryContainer,
            cardContainerColor = cs.surfaceContainerLow,
            icon = EngineIcons.Info,
            quadrantShortTag = "Q4 · Neither Urgent Nor Important"
        )
    }
}

/**
 * 2x2 selectable Eisenhower Matrix category picker used on Creation & Edit screens.
 * Uses full labels with clean wrapping so text never clips on narrow viewports.
 */
@Composable
fun CategorySelector(
    selected: TaskCategory,
    onSelect: (TaskCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
    ) {
        Text("Eisenhower category", style = MaterialTheme.typography.titleSmall)
        val rows = listOf(
            TaskCategory.URGENT_IMPORTANT to TaskCategory.NOT_URGENT_IMPORTANT,
            TaskCategory.URGENT_NOT_IMPORTANT to TaskCategory.NOT_URGENT_NOT_IMPORTANT
        )
        rows.forEach { (left, right) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)
            ) {
                CategoryChoiceCard(
                    category = left,
                    isSelected = selected == left,
                    onClick = { onSelect(left) },
                    modifier = Modifier.weight(1f)
                )
                CategoryChoiceCard(
                    category = right,
                    isSelected = selected == right,
                    onClick = { onSelect(right) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CategoryChoiceCard(
    category: TaskCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = categoryVisualStyle(category)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) style.badgeContainerColor else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isSelected) BorderStroke(1.5.dp, style.accentColor) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(SpacingTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = if (isSelected) style.badgeContentColor else style.accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = category.actionHint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) style.badgeContentColor else style.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = category.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) style.badgeContentColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Bottom sheet to reclassify a task into one of the four Eisenhower categories. */
@Composable
fun MoveCategorySheet(
    task: Task,
    onMove: (TaskCategory) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingTokens.Spacing.lg)
                .padding(bottom = SpacingTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)
        ) {
            Text("Move to category", style = MaterialTheme.typography.titleLarge)
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(SpacingTokens.Spacing.xxs))
            TaskCategory.entries.forEach { cat ->
                val style = categoryVisualStyle(cat)
                val isCurrent = task.category == cat
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (isCurrent) style.badgeContainerColor else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (isCurrent) BorderStroke(1.5.dp, style.accentColor) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onMove(cat)
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpacingTokens.Spacing.md, vertical = SpacingTokens.Spacing.sm),
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
                        Spacer(Modifier.width(SpacingTokens.Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCurrent) style.badgeContentColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${cat.actionHint} · ${if (cat.isUrgent) "Urgent" else "Not urgent"} · ${if (cat.isImportant) "Important" else "Unimportant"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) style.badgeContentColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                imageVector = EngineIcons.Check,
                                contentDescription = "Current category",
                                tint = style.badgeContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
