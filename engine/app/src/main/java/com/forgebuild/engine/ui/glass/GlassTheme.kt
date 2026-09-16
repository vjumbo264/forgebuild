package com.forgebuild.engine.ui.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * ForgeBuild Engine Liquid Glass theme (opt-in, selectable via
 * [com.forgebuild.engine.ui.theme.EngineTheme.LIQUID_GLASS]).
 *
 * Built on `io.github.kyant0:backdrop` (Kyant0/AndroidLiquidGlass, Apache-2.0).
 * The library is a backdrop/effect engine only — it ships no components — so
 * the Engine's component set lives in [GlassComponents.kt], adapted from the
 * library's own reference examples (catalog/components/LiquidButton,
 * LiquidToggle, LiquidSlider, LiquidBottomTabs).
 *
 * How it fits together:
 *  1. Wrap the app in [LiquidGlassTheme].
 *  2. Put scrollable/background content behind glass with
 *     [Modifier.liquidBackdropSource] on the background container, passing the
 *     [Backdrop] from [rememberLiquidGlassBackdrop].
 *  3. Render glass components with that same [Backdrop] — they refract/blur
 *     whatever was drawn into the backdrop layer.
 *
 * Capability: below API 33 (no AGSL RuntimeShader) every component degrades to
 * the frosted-lite surface — see [LiquidGlassSupport] / [LiquidGlassFallback].
 *
 * Colors: glass is mostly backdrop-driven, so the palette is intentionally
 * small (accent + container + content). Light/dark follow the system. Dynamic
 * color is unaffected: it belongs to the Material theme path; a generated app
 * can feed its own accent here if it wants Monet-tinted glass.
 */
@Immutable
class LiquidGlassColors(
    val isDark: Boolean,
    val accent: Color,
    val container: Color,
    val content: Color,
    val contentDim: Color
)

private val LocalLiquidGlassColors = compositionLocalOf<LiquidGlassColors?> { null }

object LiquidGlassTheme {
    /** Current glass palette, or null when not inside [LiquidGlassTheme]. */
    val colors: LiquidGlassColors
        @Composable get() = LocalLiquidGlassColors.current
            ?: defaultLiquidGlassColors(isSystemInDarkTheme())
}

@Composable
fun defaultLiquidGlassColors(darkTheme: Boolean): LiquidGlassColors =
    if (darkTheme) LiquidGlassColors(
        isDark = true,
        accent = Color(0xFF0A84FF),
        container = Color(0xFF121212).copy(alpha = 0.40f),
        content = Color(0xFFF2F2F7),
        contentDim = Color(0xFFEBEBF5).copy(alpha = 0.6f)
    ) else LiquidGlassColors(
        isDark = false,
        accent = Color(0xFF007AFF),
        container = Color(0xFFFAFAFA).copy(alpha = 0.40f),
        content = Color(0xFF1C1C1E),
        contentDim = Color(0xFF3C3C43).copy(alpha = 0.6f)
    )

/** Root Liquid Glass theme wrapper for a generated app. */
@Composable
fun LiquidGlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    val base = defaultLiquidGlassColors(darkTheme)
    val colors = if (accent != null) LiquidGlassColors(
        isDark = base.isDark,
        accent = accent,
        container = base.container,
        content = base.content,
        contentDim = base.contentDim
    ) else base
    CompositionLocalProvider(LocalLiquidGlassColors provides colors) {
        content()
    }
}

/**
 * The shared backdrop a screen's glass components draw from. Typed as
 * [LayerBackdrop] (the concrete type [rememberLayerBackdrop] returns and
 * [layerBackdrop] requires); it IS-A [Backdrop], so every `Glass*` component
 * taking the broader [Backdrop] still accepts it.
 */
@Composable
fun rememberLiquidGlassBackdrop(): LayerBackdrop = rememberLayerBackdrop()

/**
 * Marks this modifier's layer as the glass SOURCE (background content the
 * glass refracts/blurs). Put it on the scrolling content / background behind
 * the glass components.
 */
fun Modifier.liquidBackdropSource(backdrop: LayerBackdrop): Modifier =
    this.layerBackdrop(backdrop)
