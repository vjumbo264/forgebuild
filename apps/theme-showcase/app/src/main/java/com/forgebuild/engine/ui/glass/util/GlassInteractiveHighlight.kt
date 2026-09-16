package com.forgebuild.engine.ui.glass.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Press-feedback highlight for glass components, adapted from the
 * AndroidLiquidGlass reference `catalog/utils/InteractiveHighlight.kt`
 * (Kyant0/AndroidLiquidGlass, Apache-2.0).
 *
 * Simplification vs. the reference: the Engine version tracks press state via
 * tap gestures and centers the highlight at the press point, without the
 * reference's drag-follow physics (the Engine's draggable components — toggle,
 * slider, tabs — carry their own drag animations). The AGSL radial-highlight
 * shader is kept verbatim, guarded by [isRuntimeShaderSupported]; on older
 * devices a plain additive tint is drawn instead.
 */
@Stable
class GlassInteractiveHighlight(
    private val animationScope: CoroutineScope
) {
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    val pressProgress: Float get() = pressProgressAnimation.value

    private val shader =
        if (isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                if (shader != null) {
                    drawRect(
                        Color.White.copy(0.08f * progress),
                        blendMode = BlendMode.Plus
                    )
                    shader.apply {
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.15f * progress))
                        setFloatUniform("radius", size.minDimension * 1.5f)
                        val pos = positionAnimation.value
                        setFloatUniform(
                            "position",
                            pos.x.fastCoerceIn(0f, size.width),
                            pos.y.fastCoerceIn(0f, size.height)
                        )
                    }
                    drawRect(
                        ShaderBrush(shader.asComposeShader()),
                        blendMode = BlendMode.Plus
                    )
                } else {
                    drawRect(
                        Color.White.copy(0.25f * progress),
                        blendMode = BlendMode.Plus
                    )
                }
            }
            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            detectTapGestures(
                onPress = { down ->
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, spring(0.5f, 300f, 0.001f)) }
                        launch { positionAnimation.snapTo(Offset(down.x, down.y)) }
                    }
                    tryAwaitRelease()
                    animationScope.launch {
                        pressProgressAnimation.animateTo(0f, spring(0.5f, 300f, 0.001f))
                    }
                }
            )
        }
}
