@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.forgebuild.clipforgeandroid.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/* ============================================================================
 *  v22 task-123/124 shared UI
 *
 *  Every legacy hand-rolled helper that used to live in ui/Motion.kt is now a
 *  THIN WRAPPER over the REAL M3 Expressive components — the app's screens
 *  compile against the same call sites, but there is no hand-drawn canvas,
 *  no invented spring, no fake wavy loader anywhere.
 * ============================================================================ */

// -------- Legacy motion-compat aliases (routes to real expressive components) --------

/** Legacy alias for the old hand-drawn wavy loader → official LinearWavyProgressIndicator. */
@Composable
fun SquiggleLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    waveLength: Dp = 40.dp,
    strokeWidth: Dp = 5.dp,
    height: Dp = 14.dp,
) {
    EngineLinearWavyProgress(modifier = modifier.fillMaxWidth().height(height))
}

/** Legacy alias for the old rotating-arc loader → official LoadingIndicator. */
@Composable
fun SquiggleCircularLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.5.dp,
) {
    EngineLoadingIndicator(modifier = modifier.size(size))
}

/** Legacy empty state → real MaterialShapes decorative cookie + engine wavy indicator. */
@Composable
fun CFEEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpacingTokens.Spacing.lg, vertical = SpacingTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
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
            icon?.invoke()
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        EngineLinearWavyProgress(modifier = Modifier.fillMaxWidth(0.45f))
    }
}

/** Legacy status chip → tonal Surface with the M3 large shape + animated colour. */
@Composable
fun CFStatusChip(label: String, color: Color) {
    val animated by animateColorAsState(color, animationSpec = tween(260, easing = FastOutSlowInEasing), label = "statusColor")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = animated.copy(alpha = 0.16f),
        contentColor = animated,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = SpacingTokens.Spacing.sm, vertical = SpacingTokens.Spacing.xxs + 2.dp),
        )
    }
}

/** Legacy motion object — kept for source compat; forwards to expressive spring physics. */
object CFMotion {
    fun <T> fadeSpec(durationMs: Int = 260): TweenSpec<T> =
        tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
}

/**
 * Legacy staggered-appear modifier — kept for source compat but returns the
 * modifier unchanged: LazyColumn/LazyRow now use the built-in `animateItem()`
 * with the expressive MotionScheme spring specs (v22 task-123 rebuild).
 */
@Composable
fun Modifier.staggeredAppear(index: Int, baseDelayMs: Int = 45): Modifier = this

// -------- v22 shared busy-aware action buttons (task-124) --------

/**
 * v22 task-124 — the ONE shared busy-aware action-button family. Every action
 * that used to be a raw Button/TextButton/IconButton in the 76 baseline call
 * sites now goes through these. On tap the control disables itself within one
 * frame, swaps to the official morphing LoadingIndicator (the label stays),
 * and ignores further taps until the ViewModel clears the op's busy key
 * (withBusy re-entrancy guard). No screen may hand-roll a button+spinner again.
 */
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

/** Tonal section card — the app's one card shape. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ElevationTokens.tonalContainerColor(1)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(SpacingTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.sm),
        ) {
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

/** Rebuild-clean expressive empty state (used by new v22 screens). */
@Composable
fun EngineEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(SpacingTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(SpacingTokens.Spacing.xxxl + SpacingTokens.Spacing.xl)
                .background(
                    color = ElevationTokens.tonalContainerColor(3),
                    shape = MaterialShapes.Cookie12Sided.toShape(),
                )
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * v22 task-127 — the deterministic-music abort dialog. When the app cannot
 * confirm the operator's chosen background music (read/parse failure after
 * retries at dispatch time), dispatch is ABORTED and this card offers the only
 * two honest paths: Retry, or an explicit operator choice to continue without
 * music. Nothing is silently rendered without music.
 */
@Composable
fun MusicConfirmDialog(vm: ClipForgeViewModel) {
    val confirm by vm.musicConfirm.collectAsState()
    confirm?.let { mc ->
        AlertDialog(
            onDismissRequest = { vm.dismissMusicConfirm() },
            title = { Text("Couldn't confirm your background music") },
            text = {
                Text(
                    mc.reason + "\n\nNothing was dispatched — the video will NOT silently render without music.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                ActionButton(label = "Retry", onClick = {
                    vm.dismissMusicConfirm()
                    mc.retry()
                })
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.Spacing.xs)) {
                    TextActionButton(label = "Continue without music", onClick = {
                        vm.dismissMusicConfirm()
                        mc.continueWithoutMusic()
                    })
                    TextActionButton(label = "Cancel", onClick = { vm.dismissMusicConfirm() })
                }
            },
        )
    }
}
