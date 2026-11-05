package com.motionsalt.ccstudio.core

import kotlin.math.*

/** D65 / 2-degree CIELAB from straight, encoded sRGB; errors use CIEDE2000 (kL=kC=kH=1). */
object ColourMetrics {
    data class Lab(val l: Double, val a: Double, val b: Double)
    data class Summary(val mean: Double, val p95: Double, val count: Int)

    fun lab(r: Double, g: Double, b: Double): Lab {
        fun linear(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        fun f(v: Double) = if (v > 216.0 / 24389.0) Math.cbrt(v) else (24389.0 / 27.0 * v + 16.0) / 116.0
        val rr = linear(r); val gg = linear(g); val bb = linear(b)
        val x = f((0.4124564 * rr + 0.3575761 * gg + 0.1804375 * bb) / 0.95047)
        val y = f(0.2126729 * rr + 0.7151522 * gg + 0.0721750 * bb)
        val z = f((0.0193339 * rr + 0.1191920 * gg + 0.9503041 * bb) / 1.08883)
        return Lab(116.0 * y - 16.0, 500.0 * (x - y), 200.0 * (y - z))
    }

    fun lab(argb: Int) = lab(((argb ushr 16) and 255) / 255.0, ((argb ushr 8) and 255) / 255.0, (argb and 255) / 255.0)

    fun deltaE2000(first: Lab, second: Lab): Double {
        val c1 = hypot(first.a, first.b); val c2 = hypot(second.a, second.b)
        val cMean = (c1 + c2) / 2.0
        val c7 = cMean.pow(7)
        val g = 0.5 * (1.0 - sqrt(c7 / (c7 + 25.0.pow(7))))
        val a1 = (1.0 + g) * first.a; val a2 = (1.0 + g) * second.a
        val cp1 = hypot(a1, first.b); val cp2 = hypot(a2, second.b)
        fun hue(a: Double, b: Double): Double {
            if (a == 0.0 && b == 0.0) return 0.0
            return (atan2(b, a) * 180.0 / PI + 360.0) % 360.0
        }
        val h1 = hue(a1, first.b); val h2 = hue(a2, second.b)
        val dl = second.l - first.l; val dc = cp2 - cp1
        val dh = when {
            cp1 * cp2 < 1e-24 -> 0.0
            abs(h2 - h1) <= 180.0 + 1e-12 -> h2 - h1
            h2 <= h1 -> h2 - h1 + 360.0
            else -> h2 - h1 - 360.0
        }
        val dH = 2.0 * sqrt(cp1 * cp2) * sin(dh * PI / 360.0)
        val lm = (first.l + second.l) / 2.0
        val cm = (cp1 + cp2) / 2.0
        val hm = when {
            cp1 * cp2 < 1e-24 -> h1 + h2
            abs(h1 - h2) <= 180.0 + 1e-12 -> (h1 + h2) / 2.0
            h1 + h2 < 360.0 -> (h1 + h2 + 360.0) / 2.0
            else -> (h1 + h2 - 360.0) / 2.0
        }
        fun cosd(v: Double) = cos(v * PI / 180.0)
        val t = 1.0 - 0.17 * cosd(hm - 30.0) + 0.24 * cosd(2.0 * hm) + 0.32 * cosd(3.0 * hm + 6.0) - 0.20 * cosd(4.0 * hm - 63.0)
        val sl = 1.0 + 0.015 * (lm - 50.0).pow(2) / sqrt(20.0 + (lm - 50.0).pow(2))
        val sc = 1.0 + 0.045 * cm
        val sh = 1.0 + 0.015 * cm * t
        val rc = 2.0 * sqrt(cm.pow(7) / (cm.pow(7) + 25.0.pow(7)))
        val rotation = -rc * sin((60.0 * exp(-((hm - 275.0) / 25.0).pow(2))) * PI / 180.0)
        val ll = dl / sl; val cc = dc / sc; val hh = dH / sh
        return sqrt(max(0.0, ll * ll + cc * cc + hh * hh + rotation * cc * hh))
    }

    /** Linear-interpolated percentile at (n-1)*.95, matching numpy.percentile's default. */
    fun summarize(errors: DoubleArray): Summary {
        require(errors.isNotEmpty()) { "No valid pixels; cannot report match quality" }
        require(errors.all { it.isFinite() && it >= 0.0 }) { "Invalid colour error" }
        val sorted = errors.sortedArray()
        val p = (sorted.size - 1) * 0.95
        val lo = floor(p).toInt(); val hi = ceil(p).toInt()
        return Summary(sorted.average(), sorted[lo] + (sorted[hi] - sorted[lo]) * (p - lo), sorted.size)
    }

    /** Labels deliberately never imply a measured maximum, or treat all errors as accurate. */
    fun qualityLabel(mean: Double): String = when {
        !mean.isFinite() || mean < 0.0 -> "Invalid measurement"
        mean < 6.0 -> "Close match"
        mean <= 12.0 -> "Rough match"
        else -> "Poor match"
    }

    fun meetsGojoGate(identity: Summary, matched: Summary, blurred: Summary): Boolean =
        identity.mean > 0.0 && matched.mean <= 5.4 && matched.p95 <= 12.5 &&
            matched.mean <= identity.mean * 0.55 && blurred.mean <= 4.3
}
