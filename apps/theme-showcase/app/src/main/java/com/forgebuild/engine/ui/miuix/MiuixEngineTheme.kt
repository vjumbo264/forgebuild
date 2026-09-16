package com.forgebuild.engine.ui.miuix

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * ForgeBuild Engine Miuix base theme (opt-in, selectable via
 * [com.forgebuild.engine.ui.theme.EngineTheme.MIUIX]).
 *
 * Wraps the open-source Miuix-for-Compose library
 * (`top.yukonga.miuix.kmp:miuix-ui`, the compose-miuix-ui/miuix project,
 * Apache-2.0). Provides Miuix colors + text styles (Miuix's own typography
 * system) for the whole hierarchy. Light/dark follow the system by default,
 * matching the Engine's other themes.
 *
 * Shape/corner-radius: Miuix does not use a Material-style Shapes object;
 * its corner treatment (squircle) is built into each component
 * (Button/Card/etc. take a `cornerRadius`). So "shape coverage" for Miuix is
 * expressed per-component in [com.forgebuild.engine.ui.miuix] wrappers, not as
 * a theme token. See BUILD_STATE.json research notes.
 *
 * Dynamic color (Monet) is available via Miuix's ThemeController; the Engine
 * keeps the simple colors-based theme here for parity with Material/Miuix
 * defaults, and dark mode + system light/dark are fully preserved. A generated
 * app that wants Monet can wrap with a ThemeController instead — this base
 * theme is the safe default.
 */
@Composable
fun MiuixEngineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkColorScheme() else lightColorScheme()
    MiuixTheme(colors = colors, content = content)
}
