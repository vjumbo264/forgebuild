package com.forgebuild.clipforgeandroid.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons
import kotlin.math.PI
import kotlin.math.sin

/**
 * ClipForge shared Material 3 "Material You" motion + loading language.
 * One place for the app's expressive motion so every screen animates the same
 * way: an emphasized ease for cross-fades and the squiggle (wavy) loader that
 * is this UI's signature for indeterminate work. Fully theme-driven — it
 * follows the ForgeBuild dynamic light/dark theme automatically.
 */
object CFMotion {
    /** Emphasized tween used for container/state cross-fades and entrances. */
    fun <T> fadeSpec(durationMs: Int = 260): TweenSpec<T> =
        tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
}

/** Staggered entrance helper: slide up + fade in with a per-index delay. */
@Composable
fun Modifier.staggeredAppear(index: Int, baseDelayMs: Int = 45): Modifier {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * baseDelayMs).toLong())
        animatable.animateTo(1f, CFMotion.fadeSpec(340))
    }
    val a = animatable.value
    return this.graphicsLayer {
        alpha = a
        translationY = (1f - a) * 48f
        scaleX = 0.97f + 0.03f * a
        scaleY = 0.97f + 0.03f * a
    }
}

/**
 * The ClipForge squiggle loader — a hand-drawn wavy indeterminate progress bar
 * (the Material 3 expressive "wavy indicator" look, drawn on Canvas so it works
 * on the pinned Compose BOM). Used everywhere the app waits on the network.
 */
@Composable
fun SquiggleLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    waveLength: Dp = 40.dp,
    strokeWidth: Dp = 5.dp,
    height: Dp = 14.dp
) {
    val transition = rememberInfiniteTransition(label = "squiggle")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing)),
        label = "phase"
    )
    val ampPulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amp"
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val wavePx = with(density) { waveLength.toPx() }
    val strokePx = with(density) { strokeWidth.toPx() }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val cy = size.height / 2f
        drawLine(trackColor, Offset(0f, cy), Offset(size.width, cy), strokeWidth = strokePx, cap = StrokeCap.Round)
        val amp = (size.height / 2f - strokePx).coerceAtLeast(0.5f) * ampPulse
        val path = Path()
        var x = 0f
        path.moveTo(0f, cy + amp * sin(-phase))
        while (x <= size.width) {
            path.lineTo(x, cy + amp * sin((2f * PI.toFloat() * x / wavePx) - phase))
            x += 6f
        }
        path.lineTo(size.width, cy + amp * sin((2f * PI.toFloat() * size.width / wavePx) - phase))
        drawPath(path, color = color, style = Stroke(width = strokePx, cap = StrokeCap.Round))
    }
}

/** Compact rotating-arc loader for buttons, icons and tight rows. */
@Composable
fun SquiggleCircularLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.5.dp
) {
    val transition = rememberInfiniteTransition(label = "squiggleCircular")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)),
        label = "rotation"
    )
    val sweep by transition.animateFloat(
        initialValue = 90f,
        targetValue = 250f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val diameter = size.toPx() - strokeWidth.toPx()
        val topLeft = Offset(strokeWidth.toPx() / 2f, strokeWidth.toPx() / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = stroke)
        drawArc(color, rotation, sweep, false, topLeft, arcSize, style = stroke)
    }
}

/** Friendly expressive empty-state block with a squiggle flourish. */
@Composable
fun CFEEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp
        ) {
            Box(Modifier.padding(20.dp)) {
                icon?.invoke() ?: Icon(
                    EngineIcons.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        SquiggleLoader(
            modifier = Modifier.fillMaxWidth(0.45f),
            waveLength = 24.dp,
            strokeWidth = 4.dp
        )
    }
}

/** Pill status chip with an animated color transition between states. */
@Composable
fun CFStatusChip(label: String, color: Color) {
    val animated by animateColorAsState(color, animationSpec = CFMotion.fadeSpec(), label = "statusColor")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = animated.copy(alpha = 0.16f),
        contentColor = animated
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
