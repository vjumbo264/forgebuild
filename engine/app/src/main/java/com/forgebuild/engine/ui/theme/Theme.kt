package com.forgebuild.engine.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Shared ForgeBuild Material 3 Expressive theme (the DEFAULT Engine theme).
 *
 * Upgraded from plain Material 3 to **Material 3 Expressive**: spring-based
 * expressive motion ([MotionScheme.expressive]) and the expressive shape system
 * ([Shapes] defaults, incl. LargeIncreased / ExtraLargeIncreased /
 * ExtraExtraLarge) are now applied on top of the existing dynamic-color +
 * light/dark behavior. Expressive progress/loading indicators live in
 * [com.forgebuild.engine.ui.components.EngineProgress].
 *
 * Colors: dynamic color (Android 12+) is preserved unchanged. For the
 * non-dynamic path, the expressive light scheme is used; dark mode uses the
 * standard dark scheme (the library intentionally ships no separate
 * "expressiveDark" scheme — expressive-ness for dark comes from motion+shape,
 * matching Google's own MaterialExpressiveTheme sample which toggles
 * expressiveLight + darkTheme).
 *
 * Backward compatibility: generated apps keep calling ForgeBuildTheme exactly
 * as before — same signature, same dynamic-color + dark-mode behavior. The
 * expressive upgrade is purely additive (motion + shape), so existing
 * generated UIs do not visually break.
 *
 * Never a raw unstyled UI, never hardcoded colors.
 */
private val LightColors = lightColorScheme()
private val ExpressiveLightColors = expressiveLightColorScheme()
private val DarkColors = darkColorScheme()

/** Expressive shape system (morphing-capable corner shapes with the increased tiers). */
private val ExpressiveShapes = Shapes()

/** Expressive motion scheme (spring-based spatial + effects specs). */
private val ExpressiveMotion: MotionScheme = MotionScheme.expressive()

@Composable
fun ForgeBuildTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> ExpressiveLightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = ExpressiveMotion,
        shapes = ExpressiveShapes,
        typography = Typography(),
        content = content
    )
}
