package com.forgebuild.engine.ui.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * The "frosted-lite" Liquid Glass surface used when
 * [LiquidGlassSupport.isSupported] is false (API < 33 / no AGSL RuntimeShader).
 *
 * It reproduces the *read* of glass — a semi-translucent surface with a soft
 * top highlight and an inner edge — without any refraction/blur shader, so the
 * below-API-33 experience is graceful and consistent rather than broken.
 * Same shape and sizing contract as the real glass components; only the
 * optical effect is simplified.
 *
 * Used automatically by every component in this package when unsupported; a
 * generated app can also call it directly for custom glass-styled surfaces.
 */
object LiquidGlassFallback {

    /** Translucent base tint for the fallback surface. */
    @Composable
    fun surfaceColor(): Color =
        if (isSystemInDarkTheme()) Color(0xFF1C1C1E).copy(alpha = 0.72f)
        else Color(0xFFFAFAFA).copy(alpha = 0.72f)

    /**
     * Draws the frosted-lite surface behind the content: translucent base +
     * vertical highlight gradient + a 1px inner top edge light.
     */
    fun Modifier.frostedSurface(
        shape: Shape,
        baseColor: Color,
        highlight: Color = Color.White.copy(alpha = 0.14f)
    ): Modifier =
        this
            .clip(shape)
            .drawBehind {
                drawRect(baseColor)
                // Soft top-down highlight -> reads as a lit glass surface.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(highlight, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.6f
                    )
                )
                // Inner top edge light (fake refraction rim).
                drawLine(
                    color = Color.White.copy(alpha = 0.22f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
}
