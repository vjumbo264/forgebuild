package com.forgebuild.engine.ui.glass

import com.kyant.backdrop.isRuntimeShaderSupported

/**
 * Liquid Glass capability gate (the step-1 min-API / AGSL strategy).
 *
 * The true Liquid Glass refraction + blur uses an AGSL [android.graphics.RuntimeShader],
 * which exists only on **API 33+** (Android 13). The `io.github.kyant0:backdrop`
 * library exposes that exact capability via `isRuntimeShaderSupported()` — so the
 * Engine defers to the library's own runtime check instead of hard-coding an SDK
 * integer. (On Android this is `Build.VERSION.SDK_INT >= 33`.)
 *
 * Contract for the whole theme:
 *  - [isSupported] == true  -> components render the real glass effect
 *    (vibrancy + blur + lens/refraction through [com.kyant.backdrop.drawBackdrop]).
 *  - [isSupported] == false -> every Liquid component renders the SAME layout /
 *    shape / typography on a graceful "frosted-lite" fallback surface
 *    (semi-translucent solid + highlight + inner shadow). NEVER a crash, never a
 *    no-op blank. See [LiquidGlassFallback].
 *
 * App-level pickers can use [isSupported] to hide/disable the Liquid Glass entry
 * on unsupported devices, but the theme itself stays safe to select at any API.
 */
object LiquidGlassSupport {

    /** True when the device can render the real AGSL refraction/blur glass effect. */
    val isSupported: Boolean
        get() = isRuntimeShaderSupported()
}
