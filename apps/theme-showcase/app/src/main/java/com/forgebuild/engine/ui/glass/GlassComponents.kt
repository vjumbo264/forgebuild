package com.forgebuild.engine.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.glass.LiquidGlassFallback.frostedSurface
import com.forgebuild.engine.ui.glass.util.GlassInteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import androidx.compose.foundation.shape.CircleShape

/**
 * ForgeBuild Engine Liquid Glass component set (opt-in, used under
 * [com.forgebuild.engine.ui.theme.EngineTheme.LIQUID_GLASS]).
 *
 * `io.github.kyant0:backdrop` ships the backdrop/effect engine only — no
 * components — so these are the Engine's own components, adapted from the
 * library's reference examples (`catalog/components/LiquidButton`,
 * `LiquidToggle`, `LiquidSlider`, `LiquidBottomTabs`) and extended to full
 * parity with the Engine's Material/Miuix surface:
 *  - buttons  -> [GlassButton] / [GlassSurfaceButton]
 *  - cards    -> [GlassCard]
 *  - switches -> [GlassSwitch]
 *  - dialogs  -> [GlassDialog]
 *  - nav      -> [GlassBottomNav] (+ [GlassBottomNavItem])
 *  - slider   -> [GlassSlider]
 *
 * Every component takes the shared [Backdrop] from
 * [rememberLiquidGlassBackdrop] and refracts/blurs whatever was drawn behind
 * it via [liquidBackdropSource]. When [LiquidGlassSupport.isSupported] is
 * false (API < 33 / no AGSL RuntimeShader) each component automatically
 * renders the same layout on the [LiquidGlassFallback] frosted-lite surface —
 * never a crash, never a no-op.
 *
 * Icons: minimal single-weight vector icons (reuse
 * [com.forgebuild.engine.ui.icons.EngineIcons]); no emoji.
 */

// ---------------------------------------------------------------------------
// Button
// ---------------------------------------------------------------------------

/**
 * Liquid glass capsule button (adapted from the reference `LiquidButton`).
 * [content] is a row scope so icon + text compose naturally.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) {
    if (!LiquidGlassSupport.isSupported) {
        GlassSurfaceButton(onClick = onClick, modifier = modifier, content = content)
        return
    }

    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        GlassInteractiveHighlight(animationScope = animationScope)
    }

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (isInteractive) Modifier
                    .then(interactiveHighlight.modifier)
                    .then(interactiveHighlight.gestureModifier)
                else Modifier
            )
            .height(48f.dp)
            .padding(horizontal = 16f.dp),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Frosted-lite capsule button (used as the below-API-33 fallback for [GlassButton]). */
@Composable
fun GlassSurfaceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .frostedSurface(CircleShape, LiquidGlassFallback.surfaceColor())
            .clickable(role = Role.Button, onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ---------------------------------------------------------------------------
// Card
// ---------------------------------------------------------------------------

/**
 * Liquid glass card. [cornerRadius] drives the glass shape (the reference
 * components use `Capsule()`; the card uses a rounded-rect morph shape).
 */
@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    if (!LiquidGlassSupport.isSupported) {
        Column(
            modifier
                .frostedSurface(shape, LiquidGlassFallback.surfaceColor())
                .padding(16.dp),
            content = content
        )
        return
    }

    Column(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = 8f.dp, color = Color.Black.copy(alpha = 0.08f)) },
                innerShadow = { InnerShadow(radius = 4f.dp, alpha = 0.5f) },
                onDrawSurface = {
                    if (surfaceColor.isSpecified) drawRect(surfaceColor)
                }
            )
            .padding(16.dp),
        content = content
    )
}

// ---------------------------------------------------------------------------
// Switch
// ---------------------------------------------------------------------------

/**
 * Liquid glass switch (adapted from the reference `LiquidToggle`, simplified
 * to tap-to-toggle for the Engine's shared API — the reference's drag physics
 * are intentionally not ported to keep the component surface API-stable).
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isLight = !isSystemInDarkTheme()
    val accent = if (isLight) Color(0xFF34C759) else Color(0xFF30D158)
    val track = if (isLight) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)
    val thumbDiameter = 24.dp
    val trackWidth = 52.dp

    if (!LiquidGlassSupport.isSupported) {
        Box(
            modifier
                .semantics { role = Role.Switch }
                .clip(CircleShape)
                .drawBehind { drawRect(if (checked) accent else track) }
                .size(trackWidth, 28.dp)
                .clickable(enabled = enabled) { onCheckedChange(!checked) },
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .padding(2.dp)
                    .size(thumbDiameter)
                    .clip(CircleShape)
                    .drawBehind { drawRect(Color.White) }
            )
        }
        return
    }

    Box(
        modifier
            .semantics { role = Role.Switch }
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track
        Box(
            Modifier
                .clip(CircleShape)
                .drawBehind { drawRect(if (checked) accent else track) }
                .size(trackWidth, 28.dp)
        )
        // Thumb (real glass refraction of whatever is behind)
        Box(
            Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(2.dp)
                .size(thumbDiameter)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        blur(4f.dp.toPx())
                        lens(5f.dp.toPx(), 10f.dp.toPx())
                    },
                    highlight = { Highlight.Ambient },
                    shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.9f)) }
                )
        )
    }
}

// ---------------------------------------------------------------------------
// Slider
// ---------------------------------------------------------------------------

/**
 * Liquid glass slider (adapted from the reference `LiquidSlider`, simplified
 * to a standard value slider for the Engine's shared API).
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    val colors = LiquidGlassTheme.colors
    val fraction = ((value - valueRange.start) /
            (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    if (!LiquidGlassSupport.isSupported) {
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange
        )
        return
    }

    Box(modifier.height(32.dp), contentAlignment = Alignment.CenterStart) {
        // Track
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(colors.contentDim.copy(alpha = 0.25f))
                    drawRect(
                        colors.accent,
                        size = size.copy(width = size.width * fraction)
                    )
                }
        )
        // Thumb (glass lens over the track)
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(28.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            blur(4f.dp.toPx())
                            lens(8f.dp.toPx(), 16f.dp.toPx())
                        },
                        highlight = { Highlight.Ambient },
                        shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.1f)) },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.85f)) }
                    )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Dialog
// ---------------------------------------------------------------------------

/**
 * Liquid glass dialog: a centered glass card surfaced above a dimming scrim.
 * (The reference library has no dialog component; this is the Engine's own,
 * built on the same `drawBackdrop` effect stack so it matches the theme.)
 */
@Composable
fun GlassDialog(
    show: Boolean,
    onDismissRequest: (() -> Unit)?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!show) return
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.4f)) }
            .clickable(enabled = onDismissRequest != null) { onDismissRequest?.invoke() }
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        val glassModifier =
            if (LiquidGlassSupport.isSupported) {
                modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow(radius = 24f.dp, color = Color.Black.copy(alpha = 0.2f)) },
                    innerShadow = { InnerShadow(radius = 8f.dp, alpha = 0.5f) }
                )
            } else {
                modifier.frostedSurface(shape, LiquidGlassFallback.surfaceColor())
            }
        Column(
            glassModifier
                .clickable(enabled = false) {} // swallow scrim taps on the card
                .padding(24.dp),
            content = content
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom navigation
// ---------------------------------------------------------------------------

/**
 * Liquid glass bottom navigation bar (adapted from the reference
 * `LiquidBottomTabs`, simplified: no drag-to-switch physics — items are
 * tapped). Fill with [GlassBottomNavItem]s.
 */
@Composable
fun GlassBottomNav(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    if (!LiquidGlassSupport.isSupported) {
        Row(
            modifier
                .frostedSurface(RoundedCornerShape(28.dp), LiquidGlassFallback.surfaceColor())
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        return
    }

    val container = LiquidGlassTheme.colors.container
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = 12f.dp, color = Color.Black.copy(alpha = 0.12f)) },
                onDrawSurface = { drawRect(container) }
            )
            .height(64.dp)
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** A single bottom-nav item for [GlassBottomNav]. */
@Composable
fun RowScope.GlassBottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LiquidGlassTheme.colors
    Box(
        modifier
            .weight(1f)
            .height(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) drawRect(colors.accent.copy(alpha = 0.18f))
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}
