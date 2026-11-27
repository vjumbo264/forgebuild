package com.motionsalt.ccstudio.core

import kotlin.math.*

/** Full-resolution premultiplied RGBA pixels in GL row order (bottom first). */
class CcFrame(val width: Int, val height: Int, val rgba: FloatArray) {
    init { require(width > 0 && height > 0 && rgba.size == width * height * 4) }

    internal fun sample(u: Double, v: Double, out: DoubleArray) {
        val x = (u * width - .5).coerceIn(0.0, width - 1.0)
        val y = (v * height - .5).coerceIn(0.0, height - 1.0)
        val x0 = x.toInt(); val y0 = y.toInt()
        val x1 = min(x0 + 1, width - 1); val y1 = min(y0 + 1, height - 1)
        val fx = x - x0; val fy = y - y0
        for (c in 0..3) {
            val a = rgba[(y0 * width + x0) * 4 + c] * (1 - fx) + rgba[(y0 * width + x1) * 4 + c] * fx
            val b = rgba[(y1 * width + x0) * 4 + c] * (1 - fx) + rgba[(y1 * width + x1) * 4 + c] * fx
            out[c] = a * (1 - fy) + b * fy
        }
    }
}

data class CcHaze(
    val intensity: Double = .40, val threshold: Double = .50, val opacity: Double = .40,
    val spread: Double = .35, val band: Double = .03, val side: Double = .40,
    val center: Double = 0.0, val gain: Double = 3.5, val mode: Int = 0
)

/** CPU reference, independent of GLSL. Pass targets here retain float precision. */
object CcFinishing {
    private fun smooth(t: Double): Double = t.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
    private fun fract(t: Float) = t - floor(t)
    private inline fun render(w: Int, h: Int, draw: (Int, Int, Double, Double, FloatArray, Int) -> Unit): CcFrame {
        val out = FloatArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) draw(x, y, (x + .5) / w, (y + .5) / h, out, (y * w + x) * 4)
        return CcFrame(w, h, out)
    }

    fun detail(src: CcFrame, clarity: Double, structure: Double): CcFrame {
        if (abs(clarity) < .01 && abs(structure) < .01) return src
        val p = DoubleArray(4)
        val luma = doubleArrayOf(.2126, .7152, .0722)
        fun lum() = (0..2).sumOf { luma[it] * (p[it] / max(p[3], .0001)).pow(2.2) }
        fun perceptual(u: Double, v: Double): Double { src.sample(u, v, p); return sqrt(lum().coerceIn(0.0, 1.0)) }
        val radius = DoubleArray(32) { sqrt((it + .5) / 32) }
        val xTap = DoubleArray(32) { radius[it] * cos((it + .5) * 2.39996323) }
        val yTap = DoubleArray(32) { radius[it] * sin((it + .5) * 2.39996323) }
        val weight = DoubleArray(32) { exp(-2.1 * radius[it] * radius[it]) }
        return render(src.width, src.height) { x, y, u, v, out, at ->
            val center = perceptual(u, v)
            val lum = lum()
            val rot = fract(52.9829189f * fract((x + .5f) * .06711056f + (y + .5f) * .00583715f)) * 6.2831853
            val c = cos(rot); val s = sin(rot)
            var fine = 0.0; var fw = 0.0; var coarse = 0.0; var cw = 0.0
            for (i in 0 until 32) {
                val dx = (c * xTap[i] + s * yTap[i]) / src.width
                val dy = (-s * xTap[i] + c * yTap[i]) / src.height
                val f = perceptual(u + 4.5 * dx, v + 4.5 * dy)
                val k = perceptual(u + 32 * dx, v + 32 * dy)
                val wf = weight[i] * exp(-(f - center).pow(2) / (2 * .14 * .14))
                val wk = weight[i] * exp(-(k - center).pow(2) / (2 * .25 * .25))
                fine += f * wf; fw += wf; coarse += k * wk; cw += wk
            }
            val fineBase = fine / max(fw, 1e-5)
            val structBand = center - fineBase; val clarityBand = fineBase - coarse / max(cw, 1e-5)
            val mid = (4 * center * (1 - center)).coerceIn(0.0, 1.0).pow(.85) * (1 - smooth((center - .72) / .28))
            val tuned = (center + .0175 * structure * structBand / (1 + 3 * abs(structBand)) +
                .015 * clarity * mid * clarityBand / (1 + 1.8 * abs(clarityBand))).coerceIn(0.0, 1.0)
            // Multiplying straight linear RGB by a shared factor is equivalent to applying its gamma-encoded factor.
            val gain = (tuned * tuned / max(lum, 1e-5)).pow(1 / 2.2)
            val alpha = src.rgba[at + 3]
            for (i in 0..2) out[at + i] = (src.rgba[at + i] / max(alpha, .0001f) * gain).coerceIn(0.0, 1.0).toFloat() * alpha
            out[at + 3] = alpha
        }
    }

    fun unsharp(src: CcFrame, amount: Double, radius: Double, threshold: Double): CcFrame {
        if (amount <= 0.0) return src
        val r = ceil(radius * 3).toInt().coerceIn(1, 300)
        val w = DoubleArray(r * 2 + 1) { exp(-(it - r).toDouble().pow(2) / (2 * radius * radius)) }
        val norm = w.sum()
        val p = DoubleArray(4)
        fun blur(frame: CcFrame, horizontal: Boolean) = render(frame.width, frame.height) { _, _, u, v, out, at ->
            for (i in -r..r) {
                frame.sample(u + (if (horizontal) i.toDouble() / frame.width else 0.0),
                    v + (if (horizontal) 0.0 else i.toDouble() / frame.height), p)
                for (c in 0..3) out[at + c] += (w[i+r] / norm * p[c]).toFloat()
            }
        }
        val blurred = blur(blur(src, true), false)
        return render(src.width, src.height) { _, _, _, _, out, at ->
            val a = src.rgba[at + 3]; val b = blurred.rgba[at + 3]
            for (i in 0..2) {
                val base = src.rgba[at+i] / max(a, .0001f)
                val diff = base - blurred.rgba[at+i] / max(b, .0001f)
                val soft = sign(diff) * max(0.0, abs(diff) - threshold / 255)
                out[at+i] = (base + amount / 100 * soft).coerceIn(0.0, 1.0).toFloat() * a
            }
            out[at+3] = a
        }
    }

    fun haze(src: CcFrame, settings: CcHaze): CcFrame {
        val w = settings.intensity * settings.opacity
        if (w <= 0.0) return src
        val lowW = max(1, (src.width * .25).roundToInt()); val lowH = max(1, (src.height * .25).roundToInt())
        val threshold = .04 + .91 * settings.threshold
        val p = DoubleArray(4)
        val highlight = render(lowW, lowH) { _, _, u, v, out, at ->
            for (j in 0..3) for (i in 0..3) {
                src.sample(u + ((i + .5) / 4 - .5) / lowW, v + ((j + .5) / 4 - .5) / lowH, p)
                for (c in 0..2) p[c] = MatchMath.linear(p[c] / max(p[3], .0001))
                val m = smooth((max(p[0], max(p[1], p[2])) - .8 * threshold) / (.4 * threshold))
                for (c in 0..2) out[at+c] += (p[c] * m / 16).toFloat()
            }
            out[at+3] = 1f
        }
        fun streak(frame: CcFrame, vertical: Boolean): CcFrame {
            val r = if (vertical) 12 else 64
            val weights = DoubleArray(2*r+1) { val d = it-r; if (vertical) exp(-.5 * (d / 4.0).pow(2)) else exp(-3 * abs(d / 64.0)) }
            val norm = weights.sum()
            return render(lowW, lowH) { _, _, u, v, out, at ->
                for (i in -r..r) {
                    val x = u + if (vertical) 0.0 else i / 64.0 * settings.spread
                    val y = v + if (vertical) i / 12.0 * 3 * settings.band else 0.0
                    if (x in 0.0..1.0 && y in 0.0..1.0) {
                        frame.sample(x, y, p)
                        for (c in 0..2) out[at+c] += (weights[i+r] / norm * p[c]).toFloat()
                    }
                }
                out[at+3] = 1f
            }
        }
        val band = streak(streak(highlight, false), true)
        val flare = DoubleArray(3)
        return render(src.width, src.height) { _, _, u, v, out, at ->
            band.sample(u, v, p)
            for (i in 0..2) flare[i] = settings.center * p[i]
            for (x in doubleArrayOf(u - settings.side, 1 - u - settings.side)) if (x in 0.0..1.0) {
                band.sample(x, v, p)
                for (i in 0..2) flare[i] += p[i]
            }
            val a = src.rgba[at + 3]
            for (i in 0..2) {
                val f = 1 - exp(-flare[i] * settings.gain)
                val b = MatchMath.linear(src.rgba[at+i].toDouble() / max(a.toDouble(), .0001))
                val result = when (settings.mode) { 0 -> b + w * (max(b, f) - b); 1 -> b + w * f; else -> b + w * (1-b) * f }
                out[at+i] = MatchMath.encoded(result).toFloat() * a
            }
            out[at+3] = a
        }
    }
}
