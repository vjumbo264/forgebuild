package com.motionsalt.ccstudio.core

import kotlin.math.*

data class PixelPair(
    val base: Triple<Float, Float, Float>,
    val target: Triple<Float, Float, Float>
)

data class FittedTransform(
    val curveR: FloatArray,
    val curveG: FloatArray,
    val curveB: FloatArray,
    val matrix: Array<FloatArray>,
    val matrixOffset: FloatArray,
    val gridSize: Int,
    val residual: FloatArray,
    val strength: Float
) {
    fun evaluate(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val cr = curveR[((r.coerceIn(0f, 1f) * 255f).roundToInt()).coerceIn(0, 255)]
        val cg = curveG[((g.coerceIn(0f, 1f) * 255f).roundToInt()).coerceIn(0, 255)]
        val cb = curveB[((b.coerceIn(0f, 1f) * 255f).roundToInt()).coerceIn(0, 255)]

        var mr = matrix[0][0] * cr + matrix[0][1] * cg + matrix[0][2] * cb + matrixOffset[0]
        var mg = matrix[1][0] * cr + matrix[1][1] * cg + matrix[1][2] * cb + matrixOffset[1]
        var mb = matrix[2][0] * cr + matrix[2][1] * cg + matrix[2][2] * cb + matrixOffset[2]

        mr = mr.coerceIn(0f, 1f)
        mg = mg.coerceIn(0f, 1f)
        mb = mb.coerceIn(0f, 1f)

        val n = gridSize
        val fx = (mr * (n - 1)).coerceIn(0f, (n - 1).toFloat())
        val fy = (mg * (n - 1)).coerceIn(0f, (n - 1).toFloat())
        val fz = (mb * (n - 1)).coerceIn(0f, (n - 1).toFloat())

        val x0 = floor(fx).toInt().coerceIn(0, n - 1)
        val x1 = min(n - 1, x0 + 1)
        val y0 = floor(fy).toInt().coerceIn(0, n - 1)
        val y1 = min(n - 1, y0 + 1)
        val z0 = floor(fz).toInt().coerceIn(0, n - 1)
        val z1 = min(n - 1, z0 + 1)

        val wx = fx - x0
        val wy = fy - y0
        val wz = fz - z0

        fun getRes(ix: Int, iy: Int, iz: Int): Triple<Float, Float, Float> {
            val idx = (iz * n * n + iy * n + ix) * 3
            return Triple(residual[idx], residual[idx + 1], residual[idx + 2])
        }

        val c000 = getRes(x0, y0, z0)
        val c100 = getRes(x1, y0, z0)
        val c010 = getRes(x0, y1, z0)
        val c110 = getRes(x1, y1, z0)
        val c001 = getRes(x0, y0, z1)
        val c101 = getRes(x1, y0, z1)
        val c011 = getRes(x0, y1, z1)
        val c111 = getRes(x1, y1, z1)

        val c00 = Triple(c000.first * (1f - wx) + c100.first * wx, c000.second * (1f - wx) + c100.second * wx, c000.third * (1f - wx) + c100.third * wx)
        val c10 = Triple(c010.first * (1f - wx) + c110.first * wx, c010.second * (1f - wx) + c110.second * wx, c010.third * (1f - wx) + c110.third * wx)
        val c01 = Triple(c001.first * (1f - wx) + c101.first * wx, c001.second * (1f - wx) + c101.second * wx, c001.third * (1f - wx) + c101.third * wx)
        val c11 = Triple(c011.first * (1f - wx) + c111.first * wx, c011.second * (1f - wx) + c111.second * wx, c011.third * (1f - wx) + c111.third * wx)

        val c0 = Triple(c00.first * (1f - wy) + c10.first * wy, c00.second * (1f - wy) + c10.second * wy, c00.third * (1f - wy) + c10.third * wy)
        val c1 = Triple(c01.first * (1f - wy) + c11.first * wy, c01.second * (1f - wy) + c11.second * wy, c01.third * (1f - wy) + c11.third * wy)

        val res = Triple(c0.first * (1f - wz) + c1.first * wz, c0.second * (1f - wz) + c1.second * wz, c0.third * (1f - wz) + c1.third * wz)

        val outR = (mr + res.first * strength).coerceIn(0f, 1f)
        val outG = (mg + res.second * strength).coerceIn(0f, 1f)
        val outB = (mb + res.third * strength).coerceIn(0f, 1f)

        return Triple(outR, outG, outB)
    }
}

object ColorMath {
    fun srgbToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    fun linearToSrgb(c: Float): Float =
        if (c <= 0.0031308f) c * 12.92f else 1.055f * max(0f, c).pow(1f / 2.4f) - 0.055f

    fun rgbToLab(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val rl = srgbToLinear(r)
        val gl = srgbToLinear(g)
        val bl = srgbToLinear(b)

        val x = (rl * 0.4124564f + gl * 0.3575761f + bl * 0.1804375f) / 0.95047f
        val y = (rl * 0.2126729f + gl * 0.7151522f + bl * 0.0721750f) / 1.00000f
        val z = (rl * 0.0193339f + gl * 0.1191920f + bl * 0.9503041f) / 1.08883f

        fun f(t: Float) = if (t > 0.008856f) t.pow(1f / 3f) else 7.787f * t + 16f / 116f

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        return Triple(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    fun deltaE76(l1: Triple<Float, Float, Float>, l2: Triple<Float, Float, Float>): Float {
        val dl = l1.first - l2.first
        val da = l1.second - l2.second
        val db = l1.third - l2.third
        return sqrt(dl * dl + da * da + db * db)
    }

    fun generateCubeText(transform: FittedTransform, size: Int = 33): String {
        val sb = StringBuilder()
        sb.appendLine("# Created by MOTIONSALT CC Studio (100% Offline)")
        sb.appendLine("TITLE \"MOTIONSALT_Grade\"")
        sb.appendLine("LUT_3D_SIZE $size")
        sb.appendLine("DOMAIN_MIN 0.0 0.0 0.0")
        sb.appendLine("DOMAIN_MAX 1.0 1.0 1.0")
        sb.appendLine()

        for (b in 0 until size) {
            val bVal = b.toFloat() / (size - 1)
            for (g in 0 until size) {
                val gVal = g.toFloat() / (size - 1)
                for (r in 0 until size) {
                    val rVal = r.toFloat() / (size - 1)
                    val out = transform.evaluate(rVal, gVal, bVal)
                    sb.appendLine(String.format(java.util.Locale.US, "%.6f %.6f %.6f", out.first, out.second, out.third))
                }
            }
        }
        return sb.toString()
    }
}
