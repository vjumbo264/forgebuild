package com.motionsalt.ccstudio.core

import kotlin.math.*

/**
 * Pure Kotlin/JVM software stack evaluator and pixel-level pipeline.
 * Evaluates the full effect stack (FittedTransform LUT + Tone/Toning/Detail/Look passes)
 * directly on pixel buffers, matching the GLSL ES 1.00 shader math.
 * Used for live Compose viewer updates, offscreen pixel verification tests,
 * and deterministic export proofing.
 */
object StackRenderer {

    data class PixelBuffer(
        val width: Int,
        val height: Int,
        val pixels: IntArray // ARGB_8888 packed
    ) {
        fun copy(): PixelBuffer {
            return PixelBuffer(width, height, pixels.clone())
        }

        fun getR(x: Int, y: Int): Float = ((pixels[y * width + x] ushr 16) and 0xFF) / 255.0f
        fun getG(x: Int, y: Int): Float = ((pixels[y * width + x] ushr 8) and 0xFF) / 255.0f
        fun getB(x: Int, y: Int): Float = (pixels[y * width + x] and 0xFF) / 255.0f
        fun getA(x: Int, y: Int): Float = ((pixels[y * width + x] ushr 24) and 0xFF) / 255.0f

        fun setRgb(x: Int, y: Int, r: Float, g: Float, b: Float, a: Float = 1.0f) {
            val ir = (r.coerceIn(0f, 1f) * 255f).roundToInt()
            val ig = (g.coerceIn(0f, 1f) * 255f).roundToInt()
            val ib = (b.coerceIn(0f, 1f) * 255f).roundToInt()
            val ia = (a.coerceIn(0f, 1f) * 255f).roundToInt()
            pixels[y * width + x] = (ia shl 24) or (ir shl 16) or (ig shl 8) or ib
        }
    }

    /**
     * Render an input buffer through the complete TuneState stack,
     * optionally applying the fitted 3D LUT transform.
     */
    fun render(
        input: PixelBuffer,
        tuneState: TuneState,
        lutTransform: FittedTransform? = null
    ): PixelBuffer {
        var current = input.copy()

        for (item in tuneState.stack) {
            if (!item.enabled) continue

            when (item.effectId) {
                "ms_lut" -> {
                    val intensity = (item.params["intensity"] as? Number)?.toFloat() ?: 1.0f
                    if (intensity > 0.001f && lutTransform != null) {
                        current = applyLut(current, lutTransform, intensity)
                    }
                }
                "ms_brightness_contrast" -> {
                    val brightness = (item.params["brightness"] as? Number)?.toFloat() ?: 0.0f
                    val contrast = (item.params["contrast"] as? Number)?.toFloat() ?: 0.0f
                    current = applyBrightnessContrast(current, brightness, contrast)
                }
                "ms_exposure_gamma" -> {
                    val exposure = (item.params["exposure"] as? Number)?.toFloat() ?: 0.0f
                    val gamma = (item.params["gamma"] as? Number)?.toFloat() ?: 1.0f
                    current = applyExposureGamma(current, exposure, gamma)
                }
                "ms_highlights_shadows" -> {
                    val shadows = (item.params["shadows"] as? Number)?.toFloat() ?: 0.0f
                    val highlights = (item.params["highlights"] as? Number)?.toFloat() ?: 0.0f
                    val whites = (item.params["whites"] as? Number)?.toFloat() ?: 0.0f
                    val blacks = (item.params["blacks"] as? Number)?.toFloat() ?: 0.0f
                    current = applyHighlightsShadows(current, shadows, highlights, whites, blacks)
                }
                "ms_white_balance" -> {
                    val temp = (item.params["temperature"] as? Number)?.toFloat() ?: 0.0f
                    val tint = (item.params["tint"] as? Number)?.toFloat() ?: 0.0f
                    current = applyWhiteBalance(current, temp, tint)
                }
                "ms_hue_saturation" -> {
                    val hue = (item.params["hue"] as? Number)?.toFloat() ?: 0.0f
                    val sat = (item.params["saturation"] as? Number)?.toFloat() ?: 1.0f
                    val vibrance = (item.params["vibrance"] as? Number)?.toFloat() ?: 0.0f
                    current = applyHueSaturation(current, hue, sat, vibrance)
                }
                "ms_color_balance" -> {
                    val sR = (item.params["shadowsR"] as? Number)?.toFloat() ?: 0.0f
                    val sG = (item.params["shadowsG"] as? Number)?.toFloat() ?: 0.0f
                    val sB = (item.params["shadowsB"] as? Number)?.toFloat() ?: 0.0f
                    val hR = (item.params["highlightsR"] as? Number)?.toFloat() ?: 0.0f
                    val hG = (item.params["highlightsG"] as? Number)?.toFloat() ?: 0.0f
                    val hB = (item.params["highlightsB"] as? Number)?.toFloat() ?: 0.0f
                    current = applyColorBalance(current, sR, sG, sB, hR, hG, hB)
                }
                "ms_tritone" -> {
                    val sCol = parseHexColor(item.params["shadowColor"] as? String ?: "#0A1428")
                    val mCol = parseHexColor(item.params["midtoneColor"] as? String ?: "#D48B47")
                    val hCol = parseHexColor(item.params["highlightColor"] as? String ?: "#FFF2D9")
                    val blend = (item.params["blend"] as? Number)?.toFloat() ?: 0.85f
                    current = applyTritone(current, sCol, mCol, hCol, blend)
                }
                "ms_duotone" -> {
                    val sCol = parseHexColor(item.params["shadowColor"] as? String ?: "#001026")
                    val hCol = parseHexColor(item.params["highlightColor"] as? String ?: "#FFE4B5")
                    val blend = (item.params["blend"] as? Number)?.toFloat() ?: 1.0f
                    current = applyDuotone(current, sCol, hCol, blend)
                }
                "ms_gradient_map" -> {
                    val c0 = parseHexColor(item.params["color0"] as? String ?: "#050014")
                    val c1 = parseHexColor(item.params["color1"] as? String ?: "#8B1E3F")
                    val c2 = parseHexColor(item.params["color2"] as? String ?: "#E87A5D")
                    val c3 = parseHexColor(item.params["color3"] as? String ?: "#F4EAD4")
                    val strength = (item.params["mixStrength"] as? Number)?.toFloat() ?: 0.8f
                    current = applyGradientMap(current, c0, c1, c2, c3, strength)
                }
                "ms_split_toning" -> {
                    val sHue = (item.params["shadowHue"] as? Number)?.toFloat() ?: 215f
                    val sSat = (item.params["shadowSat"] as? Number)?.toFloat() ?: 0.35f
                    val hHue = (item.params["highHue"] as? Number)?.toFloat() ?: 35f
                    val hSat = (item.params["highSat"] as? Number)?.toFloat() ?: 0.40f
                    val balance = (item.params["balance"] as? Number)?.toFloat() ?: 0.0f
                    current = applySplitToning(current, sHue, sSat, hHue, hSat, balance)
                }
                "ms_vignette" -> {
                    val radius = (item.params["radius"] as? Number)?.toFloat() ?: 0.8f
                    val softness = (item.params["softness"] as? Number)?.toFloat() ?: 0.5f
                    val amount = (item.params["amount"] as? Number)?.toFloat() ?: 0.5f
                    current = applyVignette(current, radius, softness, amount)
                }
                "ms_sharpen" -> {
                    val amount = (item.params["amount"] as? Number)?.toFloat()
                        ?: (item.params["sharpenAmount"] as? Number)?.toFloat() ?: 0.5f
                    val radius = (item.params["radius"] as? Number)?.toFloat() ?: 1.0f
                    current = applySharpen(current, amount, radius)
                }
                "ms_unsharp_mask" -> {
                    val amount = (item.params["amount"] as? Number)?.toFloat() ?: 100.0f
                    val radius = (item.params["radius"] as? Number)?.toFloat() ?: 1.5f
                    val threshold = (item.params["threshold"] as? Number)?.toFloat() ?: 0.0f
                    current = applyUnsharpMask(current, amount, radius, threshold)
                }
                "ms_levels_curves" -> {
                    val bp = (item.params["blackPoint"] as? Number)?.toFloat() ?: 0.0f
                    val wp = (item.params["whitePoint"] as? Number)?.toFloat() ?: 1.0f
                    val gamma = (item.params["gamma"] as? Number)?.toFloat() ?: 1.0f
                    val curve = (item.params["curveStrength"] as? Number)?.toFloat() ?: 0.0f
                    current = applyLevelsCurves(current, bp, wp, gamma, curve)
                }
                "ms_channel_mixer" -> {
                    val rR = (item.params["redR"] as? Number)?.toFloat() ?: 1.0f
                    val rG = (item.params["redG"] as? Number)?.toFloat() ?: 0.0f
                    val rB = (item.params["redB"] as? Number)?.toFloat() ?: 0.0f
                    val gR = (item.params["greenR"] as? Number)?.toFloat() ?: 0.0f
                    val gG = (item.params["greenG"] as? Number)?.toFloat() ?: 1.0f
                    val gB = (item.params["greenB"] as? Number)?.toFloat() ?: 0.0f
                    val bR = (item.params["blueR"] as? Number)?.toFloat() ?: 0.0f
                    val bG = (item.params["blueG"] as? Number)?.toFloat() ?: 0.0f
                    val bB = (item.params["blueB"] as? Number)?.toFloat() ?: 1.0f
                    val c = (item.params["constant"] as? Number)?.toFloat() ?: 0.0f
                    current = applyChannelMixer(current, rR, rG, rB, gR, gG, gB, bR, bG, bB, c)
                }
                "ms_clarity" -> {
                    val strength = (item.params["strength"] as? Number)?.toFloat() ?: 0.3f
                    val radius = (item.params["radius"] as? Number)?.toFloat() ?: 6.0f
                    current = applyClarity(current, strength, radius)
                }
                "ms_glow" -> {
                    val threshold = (item.params["threshold"] as? Number)?.toFloat() ?: 0.65f
                    val radius = (item.params["radius"] as? Number)?.toFloat() ?: 12.0f
                    val intensity = (item.params["intensity"] as? Number)?.toFloat() ?: 0.8f
                    val tint = parseHexColor(item.params["tint"] as? String ?: "#FFF0D0")
                    current = applyGlow(current, threshold, radius, intensity, tint)
                }
                "ms_haze_flare" -> {
                    val intensity = (item.params["intensity"] as? Number)?.toFloat() ?: 0.4f
                    val color = parseHexColor(item.params["color"] as? String ?: "#FFAA60")
                    val cx = (item.params["centerX"] as? Number)?.toFloat() ?: 0.5f
                    val cy = (item.params["centerY"] as? Number)?.toFloat() ?: 0.35f
                    val spread = (item.params["spread"] as? Number)?.toFloat() ?: 0.6f
                    current = applyHazeFlare(current, intensity, color, cx, cy, spread)
                }
                "ms_grain" -> {
                    val intensity = (item.params["intensity"] as? Number)?.toFloat() ?: 0.15f
                    val size = (item.params["size"] as? Number)?.toFloat() ?: 1.0f
                    val colored = ((item.params["colored"] as? Number)?.toFloat() ?: 0.0f) > 0.5f
                    current = applyGrain(current, intensity, size, colored)
                }
                "ms_bloom_halation" -> {
                    val thresh = (item.params["threshold"] as? Number)?.toFloat() ?: 0.7f
                    val bloomInt = (item.params["bloomIntensity"] as? Number)?.toFloat() ?: 0.5f
                    val halColor = parseHexColor(item.params["halationColor"] as? String ?: "#FF3311")
                    val halInt = (item.params["halationIntensity"] as? Number)?.toFloat() ?: 0.35f
                    current = applyBloomHalation(current, thresh, bloomInt, halColor, halInt)
                }
            }
        }

        return current
    }

    private fun parseHexColor(hex: String): FloatArray {
        val clean = hex.removePrefix("#")
        val num = clean.toLongOrNull(16) ?: 0L
        val r = ((num shr 16) and 0xFF) / 255.0f
        val g = ((num shr 8) and 0xFF) / 255.0f
        val b = (num and 0xFF) / 255.0f
        return floatArrayOf(r, g, b)
    }

    fun applyLut(buf: PixelBuffer, transform: FittedTransform, intensity: Float): PixelBuffer {
        val out = buf.copy()
        val w = buf.width
        val h = buf.height
        for (y in 0 until h) {
            for (x in 0 until w) {
                val r = buf.getR(x, y)
                val g = buf.getG(x, y)
                val b = buf.getB(x, y)
                val a = buf.getA(x, y)

                val eval = transform.evaluate(r, g, b)
                val finalR = r * (1.0f - intensity) + eval.first * intensity
                val finalG = g * (1.0f - intensity) + eval.second * intensity
                val finalB = b * (1.0f - intensity) + eval.third * intensity

                out.setRgb(x, y, finalR, finalG, finalB, a)
            }
        }
        return out
    }

    fun applyBrightnessContrast(buf: PixelBuffer, brightness: Float, contrast: Float): PixelBuffer {
        val out = buf.copy()
        val factor = (1.015f * (contrast + 1.0f)) / max(1.015f - contrast, 0.0001f)
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                var r = buf.getR(x, y) + brightness
                var g = buf.getG(x, y) + brightness
                var b = buf.getB(x, y) + brightness

                r = (r - 0.5f) * factor + 0.5f
                g = (g - 0.5f) * factor + 0.5f
                b = (b - 0.5f) * factor + 0.5f

                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyExposureGamma(buf: PixelBuffer, exposure: Float, gamma: Float): PixelBuffer {
        val out = buf.copy()
        val mult = 2.0f.pow(exposure)
        val invGamma = 1.0f / max(gamma, 0.001f)
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                val r = max(0f, buf.getR(x, y) * mult).pow(invGamma)
                val g = max(0f, buf.getG(x, y) * mult).pow(invGamma)
                val b = max(0f, buf.getB(x, y) * mult).pow(invGamma)
                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyHighlightsShadows(buf: PixelBuffer, shadows: Float, highlights: Float, whites: Float, blacks: Float): PixelBuffer {
        val out = buf.copy()
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                var r = buf.getR(x, y)
                var g = buf.getG(x, y)
                var b = buf.getB(x, y)
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)

                val sMask = 1.0f - smoothstep(0.0f, 0.6f, luma)
                val hMask = smoothstep(0.4f, 1.0f, luma)
                val bMask = 1.0f - smoothstep(0.0f, 0.25f, luma)
                val wMask = smoothstep(0.75f, 1.0f, luma)

                val delta = shadows * 0.3f * sMask + highlights * 0.3f * hMask + blacks * 0.2f * bMask + whites * 0.2f * wMask
                r += delta
                g += delta
                b += delta
                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyWhiteBalance(buf: PixelBuffer, temp: Float, tint: Float): PixelBuffer {
        val out = buf.copy()
        val rGain = 1.0f + temp * 0.4f
        val bGain = 1.0f - temp * 0.4f
        val gGain = 1.0f + tint * 0.4f
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                val r = buf.getR(x, y) * rGain
                val g = buf.getG(x, y) * gGain
                val b = buf.getB(x, y) * bGain
                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyHueSaturation(buf: PixelBuffer, hue: Float, sat: Float, vibrance: Float): PixelBuffer {
        val out = buf.copy()
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                var r = buf.getR(x, y)
                var g = buf.getG(x, y)
                var b = buf.getB(x, y)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b

                // Saturation
                r = luma + (r - luma) * sat
                g = luma + (g - luma) * sat
                b = luma + (b - luma) * sat

                // Vibrance (boosts less-saturated colors)
                val curSat = max(max(r, g), b) - min(min(r, g), b)
                val vibFactor = 1.0f + vibrance * (1.0f - curSat)
                r = luma + (r - luma) * vibFactor
                g = luma + (g - luma) * vibFactor
                b = luma + (b - luma) * vibFactor

                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyColorBalance(buf: PixelBuffer, sR: Float, sG: Float, sB: Float, hR: Float, hG: Float, hB: Float): PixelBuffer {
        val out = buf.copy()
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                var r = buf.getR(x, y)
                var g = buf.getG(x, y)
                var b = buf.getB(x, y)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b

                val sWeight = (1.0f - luma).coerceIn(0f, 1f)
                val hWeight = luma.coerceIn(0f, 1f)

                r += sR * 0.25f * sWeight + hR * 0.25f * hWeight
                g += sG * 0.25f * sWeight + hG * 0.25f * hWeight
                b += sB * 0.25f * sWeight + hB * 0.25f * hWeight

                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyTritone(buf: PixelBuffer, sCol: FloatArray, mCol: FloatArray, hCol: FloatArray, blend: Float): PixelBuffer {
        val out = buf.copy()
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                val r = buf.getR(x, y)
                val g = buf.getG(x, y)
                val b = buf.getB(x, y)
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)

                val triR = if (luma < 0.5f) {
                    val t = luma / 0.5f
                    sCol[0] * (1.0f - t) + mCol[0] * t
                } else {
                    val t = (luma - 0.5f) / 0.5f
                    mCol[0] * (1.0f - t) + hCol[0] * t
                }

                val triG = if (luma < 0.5f) {
                    val t = luma / 0.5f
                    sCol[1] * (1.0f - t) + mCol[1] * t
                } else {
                    val t = (luma - 0.5f) / 0.5f
                    mCol[1] * (1.0f - t) + hCol[1] * t
                }

                val triB = if (luma < 0.5f) {
                    val t = luma / 0.5f
                    sCol[2] * (1.0f - t) + mCol[2] * t
                } else {
                    val t = (luma - 0.5f) / 0.5f
                    mCol[2] * (1.0f - t) + hCol[2] * t
                }

                val finalR = r * (1.0f - blend) + triR * blend
                val finalG = g * (1.0f - blend) + triG * blend
                val finalB = b * (1.0f - blend) + triB * blend

                out.setRgb(x, y, finalR, finalG, finalB, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyDuotone(buf: PixelBuffer, sCol: FloatArray, hCol: FloatArray, blend: Float): PixelBuffer {
        val out = buf.copy()
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                val r = buf.getR(x, y)
                val g = buf.getG(x, y)
                val b = buf.getB(x, y)
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)

                val duoR = sCol[0] * (1.0f - luma) + hCol[0] * luma
                val duoG = sCol[1] * (1.0f - luma) + hCol[1] * luma
                val duoB = sCol[2] * (1.0f - luma) + hCol[2] * luma

                val finalR = r * (1.0f - blend) + duoR * blend
                val finalG = g * (1.0f - blend) + duoG * blend
                val finalB = b * (1.0f - blend) + duoB * blend

                out.setRgb(x, y, finalR, finalG, finalB, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyGradientMap(buf: PixelBuffer, c0: FloatArray, c1: FloatArray, c2: FloatArray, c3: FloatArray, strength: Float): PixelBuffer {
        val out = buf.copy()
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                val r = buf.getR(x, y)
                val g = buf.getG(x, y)
                val b = buf.getB(x, y)
                val l = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)

                val (mR, mG, mB) = when {
                    l < 0.333f -> {
                        val t = l / 0.333f
                        Triple(c0[0] * (1f - t) + c1[0] * t, c0[1] * (1f - t) + c1[1] * t, c0[2] * (1f - t) + c1[2] * t)
                    }
                    l < 0.666f -> {
                        val t = (l - 0.333f) / 0.333f
                        Triple(c1[0] * (1f - t) + c2[0] * t, c1[1] * (1f - t) + c2[1] * t, c1[2] * (1f - t) + c2[2] * t)
                    }
                    else -> {
                        val t = (l - 0.666f) / 0.334f
                        Triple(c2[0] * (1f - t) + c3[0] * t, c2[1] * (1f - t) + c3[1] * t, c2[2] * (1f - t) + c3[2] * t)
                    }
                }

                val finalR = r * (1f - strength) + mR * strength
                val finalG = g * (1f - strength) + mG * strength
                val finalB = b * (1f - strength) + mB * strength

                out.setRgb(x, y, finalR, finalG, finalB, buf.getA(x, y))
            }
        }
        return out
    }

    fun applySplitToning(buf: PixelBuffer, sHue: Float, sSat: Float, hHue: Float, hSat: Float, balance: Float): PixelBuffer {
        val out = buf.copy()
        val sTint = hueToRgb(sHue)
        val hTint = hueToRgb(hHue)
        val pivot = (0.5f + balance * 0.25f).coerceIn(0.1f, 0.9f)

        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                var r = buf.getR(x, y)
                var g = buf.getG(x, y)
                var b = buf.getB(x, y)
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)

                val shadowWeight = ((pivot - luma) / max(pivot, 0.0001f)).coerceIn(0f, 1f)
                val highWeight = ((luma - pivot) / max(1.0f - pivot, 0.0001f)).coerceIn(0f, 1f)

                r = r * (1.0f - shadowWeight * sSat) + (r * sTint[0]) * (shadowWeight * sSat)
                g = g * (1.0f - shadowWeight * sSat) + (g * sTint[1]) * (shadowWeight * sSat)
                b = b * (1.0f - shadowWeight * sSat) + (b * sTint[2]) * (shadowWeight * sSat)

                r = r * (1.0f - highWeight * hSat) + (1.0f - (1.0f - r) * (1.0f - hTint[0] * 0.5f)) * (highWeight * hSat)
                g = g * (1.0f - highWeight * hSat) + (1.0f - (1.0f - g) * (1.0f - hTint[1] * 0.5f)) * (highWeight * hSat)
                b = b * (1.0f - highWeight * hSat) + (1.0f - (1.0f - b) * (1.0f - hTint[2] * 0.5f)) * (highWeight * hSat)

                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyVignette(buf: PixelBuffer, radius: Float, softness: Float, amount: Float): PixelBuffer {
        val out = buf.copy()
        val cx = buf.width * 0.5f
        val cy = buf.height * 0.5f
        val maxDist = sqrt(cx * cx + cy * cy)
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt(dx * dx + dy * dy) / maxDist
                val vig = smoothstep(radius, radius - softness, dist)
                val factor = (1.0f - amount) + vig * amount

                val r = buf.getR(x, y) * factor
                val g = buf.getG(x, y) * factor
                val b = buf.getB(x, y) * factor
                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applySharpen(buf: PixelBuffer, amount: Float, radius: Float = 1.0f): PixelBuffer {
        val out = buf.copy()
        val w = buf.width
        val h = buf.height
        val stepX = max(1, (radius * (w / 1280f).coerceAtLeast(1f)).roundToInt()).coerceIn(1, max(1, w / 4))
        val stepY = max(1, (radius * (h / 720f).coerceAtLeast(1f)).roundToInt()).coerceIn(1, max(1, h / 4))
        for (y in 0 until h) {
            val yN = min(h - 1, y + stepY)
            val yS = max(0, y - stepY)
            for (x in 0 until w) {
                val xE = min(w - 1, x + stepX)
                val xW = max(0, x - stepX)
                val cR = buf.getR(x, y); val cG = buf.getG(x, y); val cB = buf.getB(x, y)
                val nR = buf.getR(x, yN); val nG = buf.getG(x, yN); val nB = buf.getB(x, yN)
                val sR = buf.getR(x, yS); val sG = buf.getG(x, yS); val sB = buf.getB(x, yS)
                val eR = buf.getR(xE, y); val eG = buf.getG(xE, y); val eB = buf.getB(xE, y)
                val wR = buf.getR(xW, y); val wG = buf.getG(xW, y); val wB = buf.getB(xW, y)
                val lapR = 4.0f * cR - (nR + sR + eR + wR)
                val lapG = 4.0f * cG - (nG + sG + eG + wG)
                val lapB = 4.0f * cB - (nB + sB + eB + wB)
                val r = (cR + lapR * amount).coerceIn(0f, 1f)
                val g = (cG + lapG * amount).coerceIn(0f, 1f)
                val b = (cB + lapB * amount).coerceIn(0f, 1f)
                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyUnsharpMask(buf: PixelBuffer, amount: Float, radius: Float, threshold: Float): PixelBuffer {
        val out = buf.copy()
        val w = buf.width
        val h = buf.height
        val stepX = max(1, radius.roundToInt()).coerceIn(1, max(1, w / 4))
        val stepY = max(1, radius.roundToInt()).coerceIn(1, max(1, h / 4))
        val threshNorm = (threshold / 255.0f).coerceIn(0f, 1f)
        val boostMultiplier = (amount / 100.0f)

        for (y in 0 until h) {
            val yU = min(h - 1, y + stepY); val yD = max(0, y - stepY)
            for (x in 0 until w) {
                val xR = min(w - 1, x + stepX); val xL = max(0, x - stepX)
                val origR = buf.getR(x, y); val origG = buf.getG(x, y); val origB = buf.getB(x, y)
                val blurR = origR * 0.25f +
                    (buf.getR(xR, y) + buf.getR(xL, y) + buf.getR(x, yU) + buf.getR(x, yD)) * 0.125f +
                    (buf.getR(xR, yU) + buf.getR(xL, yU) + buf.getR(xR, yD) + buf.getR(xL, yD)) * 0.0625f
                val blurG = origG * 0.25f +
                    (buf.getG(xR, y) + buf.getG(xL, y) + buf.getG(x, yU) + buf.getG(x, yD)) * 0.125f +
                    (buf.getG(xR, yU) + buf.getG(xL, yU) + buf.getG(xR, yD) + buf.getG(xL, yD)) * 0.0625f
                val blurB = origB * 0.25f +
                    (buf.getB(xR, y) + buf.getB(xL, y) + buf.getB(x, yU) + buf.getB(x, yD)) * 0.125f +
                    (buf.getB(xR, yU) + buf.getB(xL, yU) + buf.getB(xR, yD) + buf.getB(xL, yD)) * 0.0625f

                val diffR = origR - blurR
                val diffG = origG - blurG
                val diffB = origB - blurB

                val maskR = if (abs(diffR) >= threshNorm) 1.0f else 0.0f
                val maskG = if (abs(diffG) >= threshNorm) 1.0f else 0.0f
                val maskB = if (abs(diffB) >= threshNorm) 1.0f else 0.0f

                val resR = (origR + maskR * diffR * boostMultiplier).coerceIn(0f, 1f)
                val resG = (origG + maskG * diffG * boostMultiplier).coerceIn(0f, 1f)
                val resB = (origB + maskB * diffB * boostMultiplier).coerceIn(0f, 1f)

                out.setRgb(x, y, resR, resG, resB, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyLevelsCurves(buf: PixelBuffer, blackPoint: Float, whitePoint: Float, gamma: Float, curveStrength: Float): PixelBuffer {
        val out = buf.copy()
        val range = max(whitePoint - blackPoint, 0.001f)
        val invGamma = 1.0f / max(gamma, 0.001f)
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                var r = ((buf.getR(x, y) - blackPoint) / range).coerceIn(0f, 1f).pow(invGamma)
                var g = ((buf.getG(x, y) - blackPoint) / range).coerceIn(0f, 1f).pow(invGamma)
                var b = ((buf.getB(x, y) - blackPoint) / range).coerceIn(0f, 1f).pow(invGamma)
                if (abs(curveStrength) > 0.001f) {
                    val sR = r * r * (3f - 2f * r)
                    val sG = g * g * (3f - 2f * g)
                    val sB = b * b * (3f - 2f * b)
                    r = (r * (1f - curveStrength) + sR * curveStrength).coerceIn(0f, 1f)
                    g = (g * (1f - curveStrength) + sG * curveStrength).coerceIn(0f, 1f)
                    b = (b * (1f - curveStrength) + sB * curveStrength).coerceIn(0f, 1f)
                }
                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyChannelMixer(buf: PixelBuffer, rR: Float, rG: Float, rB: Float, gR: Float, gG: Float, gB: Float, bR: Float, bG: Float, bB: Float, constVal: Float): PixelBuffer {
        val out = buf.copy()
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                val r = buf.getR(x, y); val g = buf.getG(x, y); val b = buf.getB(x, y)
                val outR = (r * rR + g * rG + b * rB + constVal).coerceIn(0f, 1f)
                val outG = (r * gR + g * gG + b * gB + constVal).coerceIn(0f, 1f)
                val outB = (r * bR + g * bG + b * bB + constVal).coerceIn(0f, 1f)
                out.setRgb(x, y, outR, outG, outB, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyClarity(buf: PixelBuffer, strength: Float, radius: Float): PixelBuffer {
        val out = buf.copy()
        val w = buf.width; val h = buf.height
        val step = max(1, radius.roundToInt()).coerceIn(1, max(1, w / 4))
        for (y in 0 until h) {
            val yU = min(h - 1, y + step); val yD = max(0, y - step)
            for (x in 0 until w) {
                val xR = min(w - 1, x + step); val xL = max(0, x - step)
                val r = buf.getR(x, y); val g = buf.getG(x, y); val b = buf.getB(x, y)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val meanLuma = (
                    (0.2126f * buf.getR(xR, y) + 0.7152f * buf.getG(xR, y) + 0.0722f * buf.getB(xR, y)) +
                    (0.2126f * buf.getR(xL, y) + 0.7152f * buf.getG(xL, y) + 0.0722f * buf.getB(xL, y)) +
                    (0.2126f * buf.getR(x, yU) + 0.7152f * buf.getG(x, yU) + 0.0722f * buf.getB(x, yU)) +
                    (0.2126f * buf.getR(x, yD) + 0.7152f * buf.getG(x, yD) + 0.0722f * buf.getB(x, yD))
                ) * 0.25f
                val diff = luma - meanLuma
                val boost = diff * strength
                out.setRgb(x, y, (r + boost).coerceIn(0f, 1f), (g + boost).coerceIn(0f, 1f), (b + boost).coerceIn(0f, 1f), buf.getA(x, y))
            }
        }
        return out
    }

    fun applyGlow(buf: PixelBuffer, threshold: Float, radius: Float, intensity: Float, tint: FloatArray): PixelBuffer {
        val out = buf.copy()
        val w = buf.width; val h = buf.height
        val step = max(1, radius.roundToInt()).coerceIn(1, max(1, w / 4))
        for (y in 0 until h) {
            val yU = min(h - 1, y + step); val yD = max(0, y - step)
            for (x in 0 until w) {
                val xR = min(w - 1, x + step); val xL = max(0, x - step)
                val r = buf.getR(x, y); val g = buf.getG(x, y); val b = buf.getB(x, y)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val bright = max(0f, luma - threshold) / max(1f - threshold, 0.001f)
                val glowR = bright * tint[0] * intensity
                val glowG = bright * tint[1] * intensity
                val glowB = bright * tint[2] * intensity
                out.setRgb(x, y, (r + glowR).coerceIn(0f, 1f), (g + glowG).coerceIn(0f, 1f), (b + glowB).coerceIn(0f, 1f), buf.getA(x, y))
            }
        }
        return out
    }

    fun applyHazeFlare(buf: PixelBuffer, intensity: Float, color: FloatArray, cx: Float, cy: Float, spread: Float): PixelBuffer {
        val out = buf.copy()
        val w = buf.width; val h = buf.height
        val aspect = h.toFloat() / max(w, 1).toFloat()
        for (y in 0 until h) {
            val ny = y.toFloat() / (h - 1f)
            for (x in 0 until w) {
                val nx = x.toFloat() / (w - 1f)
                val dx = (nx - cx)
                val dy = (ny - cy) * aspect
                val dist = sqrt(dx * dx + dy * dy)
                val flare = exp(-dist / max(spread, 0.001f)) * intensity
                val r = (buf.getR(x, y) + color[0] * flare).coerceIn(0f, 1f)
                val g = (buf.getG(x, y) + color[1] * flare).coerceIn(0f, 1f)
                val b = (buf.getB(x, y) + color[2] * flare).coerceIn(0f, 1f)
                out.setRgb(x, y, r, g, b, buf.getA(x, y))
            }
        }
        return out
    }

    fun applyGrain(buf: PixelBuffer, intensity: Float, size: Float, colored: Boolean): PixelBuffer {
        val out = buf.copy()
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                val r = buf.getR(x, y); val g = buf.getG(x, y); val b = buf.getB(x, y)
                val px = (x / max(size, 0.5f)).toInt()
                val py = (y / max(size, 0.5f)).toInt()
                val seed = (px * 374761393 + py * 668265263).toFloat()
                val noise = ((sin(seed) * 43758.5453f) - floor(sin(seed) * 43758.5453f)) - 0.5f
                val delta = noise * intensity
                if (colored) {
                    val seedG = (px * 127161393 + py * 311265263).toFloat()
                    val noiseG = ((sin(seedG) * 43758.5453f) - floor(sin(seedG) * 43758.5453f)) - 0.5f
                    val seedB = (px * 269161393 + py * 183265263).toFloat()
                    val noiseB = ((sin(seedB) * 43758.5453f) - floor(sin(seedB) * 43758.5453f)) - 0.5f
                    out.setRgb(x, y, (r + delta).coerceIn(0f, 1f), (g + noiseG * intensity).coerceIn(0f, 1f), (b + noiseB * intensity).coerceIn(0f, 1f), buf.getA(x, y))
                } else {
                    out.setRgb(x, y, (r + delta).coerceIn(0f, 1f), (g + delta).coerceIn(0f, 1f), (b + delta).coerceIn(0f, 1f), buf.getA(x, y))
                }
            }
        }
        return out
    }

    fun applyBloomHalation(buf: PixelBuffer, threshold: Float, bloomIntensity: Float, halationColor: FloatArray, halationIntensity: Float): PixelBuffer {
        val out = buf.copy()
        for (y in 0 until buf.height) {
            for (x in 0 until buf.width) {
                val r = buf.getR(x, y)
                val g = buf.getG(x, y)
                val b = buf.getB(x, y)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b

                val bloomFactor = ((luma - threshold) / max(1.0f - threshold, 0.0001f)).coerceIn(0f, 1f)
                val bloomR = r * bloomFactor * bloomIntensity + halationColor[0] * bloomFactor * halationIntensity
                val bloomG = g * bloomFactor * bloomIntensity + halationColor[1] * bloomFactor * halationIntensity
                val bloomB = b * bloomFactor * bloomIntensity + halationColor[2] * bloomFactor * halationIntensity

                out.setRgb(x, y, r + bloomR, g + bloomG, b + bloomB, buf.getA(x, y))
            }
        }
        return out
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / max(edge1 - edge0, 0.00001f)).coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }

    private fun hueToRgb(hue: Float): FloatArray {
        var h = (hue / 360.0f)
        h = (h - floor(h)) * 6.0f
        val x = 1.0f - abs((h % 2.0f) - 1.0f)
        return when {
            h < 1.0f -> floatArrayOf(1.0f, x, 0.0f)
            h < 2.0f -> floatArrayOf(x, 1.0f, 0.0f)
            h < 3.0f -> floatArrayOf(0.0f, 1.0f, x)
            h < 4.0f -> floatArrayOf(0.0f, x, 1.0f)
            h < 5.0f -> floatArrayOf(x, 0.0f, 1.0f)
            else -> floatArrayOf(1.0f, 0.0f, x)
        }
    }
}
