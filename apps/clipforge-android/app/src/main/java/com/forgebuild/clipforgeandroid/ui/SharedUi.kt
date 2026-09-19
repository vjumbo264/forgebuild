@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.forgebuild.clipforgeandroid.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forgebuild.clipforgeandroid.ClipForgeViewModel
import com.forgebuild.engine.ui.components.EngineLinearWavyProgress
import com.forgebuild.engine.ui.components.EngineLoadingIndicator
import com.forgebuild.engine.ui.components.ExpressiveButton
import com.forgebuild.engine.ui.components.ExpressiveButtonLoader
import com.forgebuild.engine.ui.components.ExpressiveTonalButton
import com.forgebuild.engine.ui.theme.ElevationTokens
import com.forgebuild.engine.ui.theme.MotionTokens
import com.forgebuild.engine.ui.theme.SpacingTokens

/* ============================================================================
 *  V23 SHARED UI — built from scratch on the engine's real M3 Expressive stack.
 *
 *  Nothing here is carried over from the old screens: every action control in
 *  the app is one of the busy-aware ActionButton variants below, every wait is
 *  an official wavy/morphing loader, every surface is tonal, every spacing and
 *  motion value comes from the engine tokens. No hex colors, no ad-hoc dp
 *  radii, no per-screen type sizes, no hand-rolled loaders.
 * ============================================================================ */

/** Animated status color — semantic log/status levels mapped onto scheme roles. */
@Composable
fun cfStatusColor(level: ClipForgeViewModel.LogLevel): Color {
    val scheme = MaterialTheme.colorScheme
    return when (level) {
        ClipForgeViewModel.LogLevel.SUCCESS -> scheme.tertiary
        ClipForgeViewModel.LogLevel.FAILURE -> scheme.error
        ClipForgeViewModel.LogLevel.SKIPPED -> scheme.secondary
        ClipForgeViewModel.LogLevel.RUNNING -> scheme.primary
        ClipForgeViewModel.LogLevel.CANCELLED -> scheme.outline
        ClipForgeViewModel.LogLevel.PENDING -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
        ClipForgeViewModel.LogLevel.INFO -> scheme.onSurfaceVariant
    }
}

/** Pipeline-state label color (same semantic mapping as the logger). */
@Composable
fun cfStateColor(state: String): Color {
    val scheme = MaterialTheme.colorScheme
    return when (state) {
        "complete" -> scheme.tertiary
        "error" -> scheme.error
        "cancelled" -> scheme.outline
        "awaiting_torrent_selection", "awaiting_plan" -> scheme.secondary
        "queued" -> scheme.onSurfaceVariant
        else -> scheme.primary // stage_a_running / stage_b_running / live states
    }
}

/** Animated tonal status chip — colors crossfade via the expressive effects spec. */
@Composable
fun CfChip(label: String, color: Color) {
    val animated by animateColorAsState(
        targetValue = color,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chipColor",
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = animated.copy(alpha = 0.16f),
        contentColor = animated,
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

/** The one card the app uses: tonal level-1 surface, official shape tier. */
@Composable
fun CfCard(
    modifier: Modifier = Modifier,
    tonalLevel: Int = 1,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = androidx.compose.material3.CardDefaults.cardColors(
        containerColor = ElevationTokens.tonalContainerColor(tonalLevel),
    )
    if (onClick != null) {
        androidx.compose.material3.Card(
            onClick = onClick,
            colors = colors,
            modifier = modifier.fillMaxWidth(),
        ) { Box(Modifier.padding(SpacingTokens.Spacing.md)) { content() } }
    } else {
        androidx.compose.material3.Card(
            colors = colors,
            modifier = modifier.fillMaxWidth(),
        ) { Box(Modifier.padding(SpacingTokens.Spacing.md)) { content() } }
    }
}

/** Screen section card with a title (titleMedium) and optional subtitle. */
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

/** Designed empty state — decorative official MaterialShapes cookie + text. */
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
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Designed loading block — official morphing LoadingIndicator + caption. */
@Composable
fun CfLoading(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(SpacingTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md),
    ) {
        EngineLoadingIndicator()
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
 * THE shared busy-aware action-button family (v23-R4).
 *
 * Every button in the app is one of these. On tap the control disables within
 * one frame and swaps to the official morphing LoadingIndicator (label stays);
 * the ViewModel's withBusy(key) re-entrancy guard makes a second tap a no-op
 * while an operation is in flight — a button can never double-fire.
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
    ExpressiveButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        modifier = modifier,
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
    ExpressiveTonalButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        modifier = modifier,
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
    OutlinedButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        shapes = ButtonDefaults.shapes(),
        colors = if (destructive) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        else ButtonDefaults.outlinedButtonColors(),
        modifier = modifier,
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
    TextButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        shapes = ButtonDefaults.shapes(),
        colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        else ButtonDefaults.textButtonColors(),
        modifier = modifier,
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
    IconButton(
        onClick = { if (!busy) onClick() },
        enabled = enabled && !busy,
        modifier = modifier,
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
