package com.motionsalt.ccstudio.core

import java.util.Locale

/**
 * Builds a single, unified Keyflaw EffectDefinition containing the complete
 * color correction look (Colour Match + Deep Glow + Sharpen + other stacked effects)
 * organized into clean collapsible parameter groups under ONE single effect.
 */
object UnifiedLookBuilder {

    private fun num(x: Double): String {
        require(x.isFinite())
        return String.format(Locale.US, "%.12e", x)
    }

    fun build(
        lookId: String,
        lookName: String,
        category: String = "MOTIONSALT",
        model: ParametricMatch,
        stackItems: List<StackItem>
    ): EffectDefinition {
        val params = mutableListOf<EffectParam>()

        // 1. Color Correction Group (Primary Grade)
        val cmItem = stackItems.firstOrNull { it.effectId == "colour_match" }
        val ccEnabledVal = (cmItem?.params?.get("enabled") as? Number)?.toDouble() ?: 1.0
        val strengthVal = (cmItem?.params?.get("strength") as? Number)?.toDouble() ?: 1.0
        val matrixEnabledVal = (cmItem?.params?.get("matrixEnabled") as? Number)?.toDouble() ?: 1.0
        val curvesEnabledVal = (cmItem?.params?.get("curvesEnabled") as? Number)?.toDouble() ?: 1.0
        val hueEnabledVal = (cmItem?.params?.get("hueEnabled") as? Number)?.toDouble() ?: 1.0

        params.add(EffectParam("ccEnabled", "Enable Grade", ParamType.BOOL, ccEnabledVal, 0f, 1f, 1f, group = "Color Correction"))
        params.add(EffectParam("strength", "Grade Strength", ParamType.SCALAR, strengthVal, 0f, 2f, 0.01f, group = "Color Correction"))
        params.add(EffectParam("matrixEnabled", "Enable Linear Matrix", ParamType.BOOL, matrixEnabledVal, 0f, 1f, 1f, group = "Color Correction"))
        params.add(EffectParam("curvesEnabled", "Enable Tone Curves", ParamType.BOOL, curvesEnabledVal, 0f, 1f, 1f, group = "Color Correction"))
        params.add(EffectParam("hueEnabled", "Enable Hue Selective", ParamType.BOOL, hueEnabledVal, 0f, 1f, 1f, group = "Color Correction"))

        // 2. Glow Group (Deep Glow)
        val glowItem = stackItems.firstOrNull { it.effectId == "glow" }
        val hasGlow = glowItem != null
        if (hasGlow) {
            val gEnabled = if (glowItem.enabled) ((glowItem.params["enabled"] as? Number)?.toDouble() ?: 1.0) else 0.0
            val gThreshold = (glowItem.params["threshold"] as? Number)?.toDouble() ?: 0.4
            val gSoftness = (glowItem.params["softness"] as? Number)?.toDouble() ?: 0.4
            val gRadius = (glowItem.params["radius"] as? Number)?.toDouble() ?: 0.6
            val gFalloff = (glowItem.params["falloff"] as? Number)?.toDouble() ?: 0.15
            val gExposure = (glowItem.params["exposure"] as? Number)?.toDouble() ?: 1.4
            val gTintR = (glowItem.params["tintR"] as? Number)?.toDouble() ?: 1.0
            val gTintG = (glowItem.params["tintG"] as? Number)?.toDouble() ?: 1.0
            val gTintB = (glowItem.params["tintB"] as? Number)?.toDouble() ?: 1.0
            val gTintEnabled = (glowItem.params["tintEnabled"] as? Number)?.toDouble() ?: 0.0
            val gTintAmount = (glowItem.params["tintAmount"] as? Number)?.toDouble() ?: 1.0
            val gChromatic = (glowItem.params["chromatic"] as? Number)?.toDouble() ?: 0.0
            val gAddMode = (glowItem.params["addMode"] as? Number)?.toDouble() ?: 0.0
            val gGlowOnly = (glowItem.params["glowOnly"] as? Number)?.toDouble() ?: 0.0

            params.add(EffectParam("glowEnabled", "Enable Glow", ParamType.BOOL, gEnabled, 0f, 1f, 1f, group = "Glow"))
            params.add(EffectParam("glowThreshold", "Threshold", ParamType.SCALAR, gThreshold, 0f, 1f, 0.01f, group = "Glow"))
            params.add(EffectParam("glowSoftness", "Softness Knee", ParamType.SCALAR, gSoftness, 0f, 1f, 0.01f, group = "Glow"))
            params.add(EffectParam("glowRadius", "Glow Radius", ParamType.SCALAR, gRadius, 0f, 1f, 0.01f, group = "Glow"))
            params.add(EffectParam("glowFalloff", "Glow Falloff", ParamType.SCALAR, gFalloff, 0f, 1f, 0.01f, group = "Glow"))
            params.add(EffectParam("glowExposure", "Glow Exposure", ParamType.SCALAR, gExposure, 0f, 8f, 0.05f, group = "Glow"))
            params.add(EffectParam("glowTintR", "Tint R", ParamType.SCALAR, gTintR, 0f, 1f, 0.00392f, group = "Glow"))
            params.add(EffectParam("glowTintG", "Tint G", ParamType.SCALAR, gTintG, 0f, 1f, 0.00392f, group = "Glow"))
            params.add(EffectParam("glowTintB", "Tint B", ParamType.SCALAR, gTintB, 0f, 1f, 0.00392f, group = "Glow"))
            params.add(EffectParam("glowTintEnabled", "Enable Tint", ParamType.BOOL, gTintEnabled, 0f, 1f, 1f, group = "Glow"))
            params.add(EffectParam("glowTintAmount", "Tint Amount", ParamType.SCALAR, gTintAmount, 0f, 1f, 0.01f, group = "Glow"))
            params.add(EffectParam("glowChromatic", "Chromatic Aberration", ParamType.SCALAR, gChromatic, 0f, 1f, 0.01f, group = "Glow"))
            params.add(EffectParam("glowAddMode", "Add Mode", ParamType.BOOL, gAddMode, 0f, 1f, 1f, group = "Glow"))
            params.add(EffectParam("glowGlowOnly", "Glow Only", ParamType.BOOL, gGlowOnly, 0f, 1f, 1f, group = "Glow"))
        }

        // 3. Sharpen / Unsharp Mask Group
        val sharpItem = stackItems.firstOrNull { it.effectId == "sharpen" || it.effectId == "unsharp_mask" }
        val hasSharpen = sharpItem != null
        if (hasSharpen) {
            val sEnabled = if (sharpItem.enabled) ((sharpItem.params["enabled"] as? Number)?.toDouble() ?: 1.0) else 0.0
            val sAmount = (sharpItem.params["amount"] as? Number)?.toDouble() ?: 25.0
            val sRadius = (sharpItem.params["radius"] as? Number)?.toDouble() ?: 1.0
            val sThreshold = (sharpItem.params["threshold"] as? Number)?.toDouble() ?: 0.0

            params.add(EffectParam("sharpenEnabled", "Enable Sharpen", ParamType.BOOL, sEnabled, 0f, 1f, 1f, group = "Sharpen"))
            params.add(EffectParam("sharpenAmount", "Sharpen Amount", ParamType.SCALAR, sAmount, 0f, 500f, 1f, group = "Sharpen"))
            params.add(EffectParam("sharpenRadius", "Sharpen Radius", ParamType.SCALAR, sRadius, 0.1f, 10f, 0.1f, group = "Sharpen"))
            params.add(EffectParam("sharpenThreshold", "Sharpen Threshold", ParamType.SCALAR, sThreshold, 0f, 255f, 1f, group = "Sharpen"))
        }

        // 4. Vignette Group
        val vigItem = stackItems.firstOrNull { it.effectId == "vignette" }
        val hasVig = vigItem != null
        if (hasVig) {
            val vEnabled = if (vigItem.enabled) ((vigItem.params["enabled"] as? Number)?.toDouble() ?: 1.0) else 0.0
            val vAmount = (vigItem.params["amount"] as? Number)?.toDouble() ?: 0.45
            val vSize = (vigItem.params["size"] as? Number)?.toDouble() ?: 0.6
            val vSoftness = (vigItem.params["softness"] as? Number)?.toDouble() ?: 0.5

            params.add(EffectParam("vignetteEnabled", "Enable Vignette", ParamType.BOOL, vEnabled, 0f, 1f, 1f, group = "Vignette"))
            params.add(EffectParam("vignetteAmount", "Vignette Amount", ParamType.SCALAR, vAmount, 0f, 1f, 0.01f, group = "Vignette"))
            params.add(EffectParam("vignetteSize", "Vignette Size", ParamType.SCALAR, vSize, 0f, 1.5f, 0.01f, group = "Vignette"))
            params.add(EffectParam("vignetteSoftness", "Vignette Softness", ParamType.SCALAR, vSoftness, 0.01f, 1.5f, 0.01f, group = "Vignette"))
        }

        val shaderCode = generateShader(model, hasGlow, hasSharpen, hasVig)

        return EffectDefinition(
            id = lookId,
            name = lookName,
            category = category,
            description = "Unified color correction grade: Color grade, deep glow, and sharpness in one master effect.",
            expandBounds = if (hasGlow) 0.3f else 0.0f,
            parameters = params,
            shaderCode = shaderCode
        )
    }

    private fun generateShader(
        model: ParametricMatch,
        hasGlow: Boolean,
        hasSharpen: Boolean,
        hasVig: Boolean
    ): String = buildString {
        append("""#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
precision highp sampler2D;

varying vec2 vTex;
uniform sampler2D uTexture;
uniform vec2 uTexelSize;

// Color Correction Group
uniform float uCcEnabled;
uniform float uStrength;
uniform float uMatrixEnabled;
uniform float uCurvesEnabled;
uniform float uHueEnabled;

""")

        if (hasGlow) {
            append("""// Glow Group
uniform float uGlowEnabled;
uniform float uGlowThreshold;
uniform float uGlowSoftness;
uniform float uGlowRadius;
uniform float uGlowFalloff;
uniform float uGlowExposure;
uniform float uGlowTintR;
uniform float uGlowTintG;
uniform float uGlowTintB;
uniform float uGlowTintEnabled;
uniform float uGlowTintAmount;
uniform float uGlowChromatic;
uniform float uGlowAddMode;
uniform float uGlowGlowOnly;

""")
        }

        if (hasSharpen) {
            append("""// Sharpen Group
uniform float uSharpenEnabled;
uniform float uSharpenAmount;
uniform float uSharpenRadius;
uniform float uSharpenThreshold;

""")
        }

        if (hasVig) {
            append("""// Vignette Group
uniform float uVignetteEnabled;
uniform float uVignetteAmount;
uniform float uVignetteSize;
uniform float uVignetteSoftness;

""")
        }

        append("""float lin(float x) { return x <= 0.04045 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4); }
float enc(float v) { float x = clamp(v, 0.0, 1.0); return x <= 0.0031308 ? 12.92 * x : 1.055 * pow(x, 1.0 / 2.4) - 0.055; }
float lf(float x) { return x > 0.008856 ? pow(x, 1.0 / 3.0) : 7.787 * x + 16.0 / 116.0; }
float li(float x) { return x > 0.2068966 ? x * x * x : (x - 16.0 / 116.0) / 7.787; }
vec3 lab(vec3 rgb) {
    vec3 p = vec3(lin(rgb.r), lin(rgb.g), lin(rgb.b));
    vec3 v = vec3(lf(dot(p, vec3(.412453, .357580, .180423)) / .95047),
                  lf(dot(p, vec3(.212671, .715160, .072169))),
                  lf(dot(p, vec3(.019334, .119193, .950227)) / 1.08883));
    return vec3(116.0 * v.y - 16.0, 500.0 * (v.x - v.y), 200.0 * (v.y - v.z));
}
vec3 rgb(vec3 l) {
    float y = (l.x + 16.0) / 116.0;
    float x = l.y / 500.0 + y;
    float z = max(0.0, y - l.z / 200.0);
    vec3 p = vec3(li(x) * .95047, li(y), li(z) * 1.08883);
    return vec3(enc(dot(p, vec3(3.24048134, -1.53715152, -.49853633))),
                enc(dot(p, vec3(-.96925495, 1.87599, .04155593))),
                enc(dot(p, vec3(.05564664, -.20404134, 1.05731107))));
}
""")

        // Tone Curves
        model.curves.forEachIndexed { c, curve ->
            append("float curve$c(float v) {\n")
            for (i in 0 until curve.x.size - 1) {
                val last = i == curve.x.size - 2
                if (!last) append("if(v<${num(curve.x[i + 1])}) ")
                append("{float h=${num(curve.x[i + 1] - curve.x[i])};float t=(v-${num(curve.x[i])})/h;float t2=t*t;float t3=t2*t;")
                append("return clamp((2.0*t3-3.0*t2+1.0)*${num(curve.y[i])}+(t3-2.0*t2+t)*h*${num(curve.slopes[i])}+(-2.0*t3+3.0*t2)*${num(curve.y[i + 1])}+(t3-t2)*h*${num(curve.slopes[i + 1])},0.0,1.0);}\n")
            }
            append("}\n")
        }

        // Hue Table
        append("vec3 hueParam(float i) {\n")
        for (i in 0 until 23) {
            append("if(i<${i + .5})return vec3(${model.hue[i].joinToString(",") { num(it) }});\n")
        }
        append("return vec3(${model.hue[23].joinToString(",") { num(it) }});}\n")

        if (hasGlow) {
            append("""
float glowLuma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 extractGlowLight(vec4 c, float th, float softKnee, float expo) {
    vec3 rgbVal = c.rgb / max(c.a, 0.0001);
    float y = glowLuma(rgbVal);
    float knee = max(softKnee * (1.0 - th), 0.001);
    float soft = clamp((y - th + knee) / (2.0 * knee), 0.0, 1.0);
    float weight = (y > (th + knee)) ? clamp((y - th) / max(1.0 - th, 0.0001), 0.0, 1.0) : soft * soft * (3.0 - 2.0 * soft);
    return rgbVal * weight * expo;
}
const int GLOW_TAPS = 32;
""")
        }

        append("""
void main() {
    vec4 src = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec4 current = src;

    // --- STAGE 1: Color Correction ---
    if (uCcEnabled > 0.5) {
        vec3 inputRgb = current.rgb / max(current.a, 0.0001);
        vec3 p = vec3(lin(inputRgb.r), lin(inputRgb.g), lin(inputRgb.b));
        vec3 g = inputRgb;
        if (uMatrixEnabled > 0.5) {
            g = vec3(
""")
        append((0..2).joinToString(",") { c ->
            "enc(dot(p, vec3(${model.matrix[c].joinToString(",") { num(it) }})) + ${num(model.offset[c])})"
        })
        append(""");
        }
        if (uCurvesEnabled > 0.5) {
            g = vec3(curve0(g.r), curve1(g.g), curve2(g.b));
        }
        if (uHueEnabled > 0.5) {
            vec3 l = lab(g);
            float h = atan(l.z, l.y);
            float c = length(l.yz);
            float w = c / (c + 10.0);
            float k = mod(h / 6.283185307179586, 1.0) * 24.0;
            float i = floor(k);
            float f = fract(k);
            vec3 v = mix(hueParam(i), hueParam(mod(i + 1.0, 24.0)), f);
            float c2 = c * (1.0 + v.y * w);
            float h2 = h + v.z * w;
            g = rgb(vec3(l.x + v.x * w, c2 * cos(h2), c2 * sin(h2)));
        }
        vec3 mixedGrade = mix(inputRgb, g, clamp(uStrength, 0.0, 2.0));
        current = vec4(clamp(mixedGrade, 0.0, 1.0) * current.a, current.a);
    }
""")

        if (hasGlow) {
            append("""
    // --- STAGE 2: Deep Glow (Vogel golden-angle spiral, continuous falloff) ---
    if (uGlowEnabled > 0.5 && uGlowExposure > 0.0) {
        vec3 lightSum = vec3(0.0);
        float totalWeight = 0.0;
        float maxDist = max(uGlowRadius, 0.01) * 32.0;
        float aspect = uTexelSize.y / max(uTexelSize.x, 0.0001);

        for (int i = 0; i < GLOW_TAPS; i++) {
            float fi = float(i);
            float rNorm = sqrt((fi + 0.5) / float(GLOW_TAPS));
            float rDist = rNorm * maxDist;
            float w = exp(-rNorm * (3.5 / max(uGlowFalloff + 0.05, 0.05)));
            float angle = fi * 2.39996323;
            vec2 dir = vec2(cos(angle) / aspect, sin(angle)) * uTexelSize * rDist;
            float spread = uGlowChromatic * 0.4;
            vec2 dirR = dir * (1.0 + spread);
            vec2 dirG = dir;
            vec2 dirB = dir * max(0.0, 1.0 - spread);
            vec4 sR = texture2D(uTexture, clamp(vTex + dirR, 0.0, 1.0));
            vec4 sG = texture2D(uTexture, clamp(vTex + dirG, 0.0, 1.0));
            vec4 sB = texture2D(uTexture, clamp(vTex + dirB, 0.0, 1.0));
            vec3 lR = extractGlowLight(sR, uGlowThreshold, uGlowSoftness, uGlowExposure);
            vec3 lG = extractGlowLight(sG, uGlowThreshold, uGlowSoftness, uGlowExposure);
            vec3 lB = extractGlowLight(sB, uGlowThreshold, uGlowSoftness, uGlowExposure);
            lightSum += vec3(lR.r, lG.g, lB.b) * w;
            totalWeight += w;
        }
        vec3 glow = lightSum / max(totalWeight, 0.0001);
        if (uGlowTintEnabled > 0.5) {
            vec3 tint = vec3(uGlowTintR, uGlowTintG, uGlowTintB);
            glow = mix(glow, glow * tint, clamp(uGlowTintAmount, 0.0, 1.0));
        }
        if (uGlowGlowOnly > 0.5) {
            float alpha = clamp(max(glow.r, max(glow.g, glow.b)), 0.0, 1.0);
            current = vec4(clamp(glow, 0.0, 1.0) * current.a, alpha * current.a);
        } else {
            vec3 baseRgb = current.rgb / max(current.a, 0.0001);
            vec3 outRgb;
            if (uGlowAddMode > 0.5) {
                outRgb = baseRgb + glow;
            } else {
                vec3 halo = vec3(1.0) - exp(-max(glow, vec3(0.0)));
                outRgb = baseRgb + (1.0 - baseRgb) * halo;
            }
            current = vec4(clamp(outRgb, 0.0, 1.0) * current.a, current.a);
        }
    }
""")
        }

        if (hasSharpen) {
            append("""
    // --- STAGE 3: Sharpen / Unsharp Mask ---
    if (uSharpenEnabled > 0.5 && uSharpenAmount > 0.0) {
        vec2 d = uTexelSize * max(uSharpenRadius, 1.0);
        vec4 edges = texture2D(uTexture, clamp(vTex + vec2( d.x, 0.0), 0.0, 1.0))
                   + texture2D(uTexture, clamp(vTex + vec2(-d.x, 0.0), 0.0, 1.0))
                   + texture2D(uTexture, clamp(vTex + vec2(0.0,  d.y), 0.0, 1.0))
                   + texture2D(uTexture, clamp(vTex + vec2(0.0, -d.y), 0.0, 1.0));
        vec4 corners = texture2D(uTexture, clamp(vTex + d, 0.0, 1.0))
                     + texture2D(uTexture, clamp(vTex - d, 0.0, 1.0))
                     + texture2D(uTexture, clamp(vTex + vec2( d.x, -d.y), 0.0, 1.0))
                     + texture2D(uTexture, clamp(vTex + vec2(-d.x,  d.y), 0.0, 1.0));
        vec4 detail = (20.0 * current - 4.0 * edges - corners) / 6.0;
        vec4 ad = abs(detail) * 255.0;
        float m = max(max(ad.r, ad.g), max(ad.b, ad.a));
        float gate = clamp(m - uSharpenThreshold + 1.0, 0.0, 1.0);
        vec4 o = current + detail * (uSharpenAmount / 100.0) * gate;
        float outA = clamp(o.a, 0.0, 1.0);
        vec3 straight = clamp(o.rgb / max(o.a, 0.0001), 0.0, 1.0);
        current = o.a > 0.0 ? vec4(straight * outA, outA) : vec4(0.0);
    }
""")
        }

        if (hasVig) {
            append("""
    // --- STAGE 4: Vignette ---
    if (uVignetteEnabled > 0.5 && uVignetteAmount > 0.0) {
        float aspect = uTexelSize.y / max(uTexelSize.x, 0.0001);
        vec2 coord = (vTex - 0.5) * vec2(1.0 / aspect, 1.0);
        float d = length(coord) * 2.0;
        float vig = smoothstep(uVignetteSize, uVignetteSize + max(uVignetteSoftness, 0.01), d);
        vec3 rgbVig = current.rgb / max(current.a, 0.0001);
        rgbVig = mix(rgbVig, rgbVig * (1.0 - uVignetteAmount), clamp(vig, 0.0, 1.0));
        current = vec4(clamp(rgbVig, 0.0, 1.0) * current.a, current.a);
    }
""")
        }

        append("""
    gl_FragColor = current;
}
""")
    }
}
