@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)
package com.forgebuild.clipforgeandroid.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.engine.ui.components.EngineCircularWavyProgress
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.components.ExpressiveButton
import com.forgebuild.engine.ui.components.ExpressiveButtonLoader
import com.forgebuild.engine.ui.components.ExpressiveTonalButton
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.SpacingTokens

/**
 * Material 3 Expressive tactile bounce: on press, scales with physics spring
 * to provide unmistakable Google-grade tactile touch response.
 */
@Composable
fun Modifier.expressiveBounce(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "expressiveBounce",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Standard content padding for scrollable screens so content scrolls seamlessly
 * behind the floating navigation bar and the bottom-most item has comfortable clearance.
 */
@Composable
fun contentPaddingForFloatingBar(extraBottom: Dp = 0.dp): PaddingValues {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(
        start = SpacingTokens.Spacing.md,
        end = SpacingTokens.Spacing.md,
        top = SpacingTokens.Spacing.md,
        bottom = 88.dp + navBottom + extraBottom,
    )
}

/* ---------------------------------------------------------------------------
 * SEMANTIC STATUS COLORS
 * ------------------------------------------------------------------------- */
@Composable
fun cfStatusColor(status: String): Color = when (status.lowercase()) {
    "complete" -> MaterialTheme.colorScheme.tertiary
    "error", "cancelled" -> MaterialTheme.colorScheme.error
    "stage_a_running", "stage_b_running", "running" -> MaterialTheme.colorScheme.primary
    "awaiting_plan", "awaiting_torrent_selection" -> MaterialTheme.colorScheme.secondary
    "queued", "stage_b_queued" -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun cfStatusColor(level: ClipForgeViewModel.LogLevel): Color = when (level) {
    ClipForgeViewModel.LogLevel.SUCCESS -> MaterialTheme.colorScheme.tertiary
    ClipForgeViewModel.LogLevel.FAILURE -> MaterialTheme.colorScheme.error
    ClipForgeViewModel.LogLevel.RUNNING -> MaterialTheme.colorScheme.primary
    ClipForgeViewModel.LogLevel.PENDING -> MaterialTheme.colorScheme.outline
    ClipForgeViewModel.LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun cfStateColor(state: String): Color = cfStatusColor(state)

/** Status chip using Material 3 Expressive pill shapes and soft tonal containers. */
@Composable
fun CfChip(
    label: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val animated by animateColorAsState(
        targetValue = color,
        label = "cfChipColor",
    )
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = animated.copy(alpha = 0.14f),
        contentColor = animated,
        border = BorderStroke(1.dp, animated.copy(alpha = 0.3f)),
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = SpacingTokens.Spacing.sm,
                vertical = SpacingTokens.Spacing.xxs,
            ),
        )
    }
}

/**
 * Standard Card in ClipForge: high-quality container with M3 Expressive shape,
 * subtle border outline, and tactile spring squish on tap when interactive.
 */
@Composable
fun CfCard(
    modifier: Modifier = Modifier,
    tonalLevel: Int = 1,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = ElevationTokens.tonalContainerColor(tonalLevel),
    )
    val border = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        androidx.compose.material3.Card(
            onClick = onClick,
            colors = colors,
            shape = MaterialTheme.shapes.large,
            border = border,
            interactionSource = interactionSource,
            modifier = modifier
                .fillMaxWidth()
                .expressiveBounce(interactionSource, pressedScale = 0.98f),
        ) {
            Box(Modifier.padding(SpacingTokens.Spacing.md)) { content() }
        }
    } else {
        androidx.compose.material3.Card(
            colors = colors,
            shape = MaterialTheme.shapes.large,
            border = border,
            modifier = modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(SpacingTokens.Spacing.md)) { content() }
        }
    }
}

/** Screen section card with an expressive title, subtitle, and action slot. */
@Composable
fun CfSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    CfCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/** Designed empty state — decorative official MaterialShapes cookie + typography. */
@Composable
fun CfEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SpacingTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(SpacingTokens.Spacing.xxxl + SpacingTokens.Spacing.xl)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialShapes.Cookie12Sided.toShape(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = com.forgebuild.engine.ui.icons.EngineIcons.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Designed loading block — official morphing LoadingIndicator or wavy spinner + caption. */
@Composable
fun CfLoading(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(SpacingTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md),
    ) {
        EngineCircularWavyProgress(Modifier.size(44.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Full-width wavy linear progress row with a caption. */
@Composable
fun CfProgress(label: String, fraction: Float?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xxs)) {
        if (fraction != null) EngineLinearWavyProgress(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        else EngineLinearWavyProgress(Modifier.fillMaxWidth())
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ---------------------------------------------------------------------------
 * THE shared busy-aware action-button family with expressive spring bounce.
 *
 * Every button in the app is one of these. On tap the control scales down
 * with spring physics, disables within one frame, and swaps to the official
 * morphing LoadingIndicator (label stays); the ViewModel's withBusy(key)
 * re-entrancy guard makes a second tap a no-op — a button can never double-fire.
 * ------------------------------------------------------------------------- */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    busy: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    ExpressiveButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        modifier = modifier.expressiveBounce(interactionSource, enabled = enabled && !busy),
    ) {
        if (busy) {
            ExpressiveButtonLoader(color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(SpacingTokens.Spacing.xs))
        }
        icon?.let {
            Icon(it, contentDescription = null)
            Spacer(Modifier.width(SpacingTokens.Spacing.xs))
        }
        Text(label)
    }
}

@Composable
fun TonalActionButton(
    label: String,
    onClick: () -> Unit,
    busy: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    ExpressiveTonalButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        modifier = modifier.expressiveBounce(interactionSource, enabled = enabled && !busy),
    ) {
        if (busy) {
            ExpressiveButtonLoader(color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(SpacingTokens.Spacing.xs))
        }
        icon?.let {
            Icon(it, contentDescription = null)
            Spacer(Modifier.width(SpacingTokens.Spacing.xs))
        }
        Text(label)
    }
}

@Composable
fun OutlinedActionButton(
    label: String,
    onClick: () -> Unit,
    busy: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        interactionSource = interactionSource,
        shapes = ButtonDefaults.shapes(),
        colors = if (destructive) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        else ButtonDefaults.outlinedButtonColors(),
        modifier = modifier.expressiveBounce(interactionSource, enabled = enabled && !busy),
    ) {
        if (busy) {
            ExpressiveButtonLoader(color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(SpacingTokens.Spacing.xs))
        }
        icon?.let {
            Icon(it, contentDescription = null)
            Spacer(Modifier.width(SpacingTokens.Spacing.xs))
        }
        Text(label)
    }
}

@Composable
fun TextActionButton(
    label: String,
    onClick: () -> Unit,
    busy: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        interactionSource = interactionSource,
        shapes = ButtonDefaults.shapes(),
        colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        else ButtonDefaults.textButtonColors(),
        modifier = modifier.expressiveBounce(interactionSource, enabled = enabled && !busy),
    ) {
        if (busy) {
            ExpressiveButtonLoader(color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(SpacingTokens.Spacing.xs))
        }
        Text(label)
    }
}

@Composable
fun IconActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    busy: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        interactionSource = interactionSource,
        modifier = modifier.expressiveBounce(interactionSource, enabled = enabled && !busy),
    ) {
        if (busy) ExpressiveButtonLoader(color = LocalContentColor.current)
        else Icon(icon, contentDescription = contentDescription)
    }
}

/** Convenience: read the busy state of a ViewModel op key. */
@Composable
fun busyOf(vm: ClipForgeViewModel, key: String): Boolean {
    val ops by vm.busyOps.collectAsState()
    return ops.contains(key)
}

/** Monospaced log line in a semantic color. */
@Composable
fun CfLogLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
    )
}

/** One-row dialog action strip used by every confirm dialog. */
@Composable
fun CfConfirmRow(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmDestructive: Boolean = false,
    confirmBusy: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
        TextActionButton(label = "Cancel", onClick = onDismiss)
        TextActionButton(
            label = confirmLabel,
            onClick = onConfirm,
            destructive = confirmDestructive,
            busy = confirmBusy,
        )
    }
}
