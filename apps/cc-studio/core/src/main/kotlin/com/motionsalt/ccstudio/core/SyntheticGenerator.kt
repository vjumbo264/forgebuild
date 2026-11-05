package com.motionsalt.ccstudio.core

import java.io.ByteArrayOutputStream
import kotlin.math.*

object SyntheticGenerator {

    /**
     * Generates a 24-color synthetic test chart (Base) and a styled/graded version (Target).
     */
    fun generateSyntheticPixelPairs(count: Int = 1200): List<PixelPair> {
        val pairs = mutableListOf<PixelPair>()
        val rand = java.util.Random(42)

        for (i in 0 until count) {
            val r = rand.nextFloat()
            val g = rand.nextFloat()
            val b = rand.nextFloat()

            // Graded style: cinematic warm tone, boosted shadows, teal highlights
            val tr = (r * 1.15f + 0.04f).coerceIn(0f, 1f)
            val tg = (g * 0.98f + 0.01f).coerceIn(0f, 1f)
            val tb = (b * 0.85f - 0.02f).coerceIn(0f, 1f)

            pairs.add(PixelPair(Triple(r, g, b), Triple(tr, tg, tb)))
        }
        return pairs
    }

    /**
     * Generates a simple 24-patch synthetic color chart as raw uncompressed 24-bit BMP image bytes.
     * Dimensions: 240x160.
     */
    fun generateChartBmp(graded: Boolean = false): ByteArray {
        val width = 240
        val height = 160
        val rgbData = ByteArray(width * height * 3)

        // 4 rows x 6 columns
        val rows = 4
        val cols = 6
        val cellW = width / cols
        val cellH = height / rows

        val baseColors = arrayOf(
            Triple(0.45f, 0.32f, 0.24f), Triple(0.76f, 0.58f, 0.50f), Triple(0.38f, 0.48f, 0.61f), Triple(0.35f, 0.42f, 0.26f), Triple(0.51f, 0.50f, 0.69f), Triple(0.39f, 0.74f, 0.66f),
            Triple(0.84f, 0.48f, 0.18f), Triple(0.31f, 0.36f, 0.65f), Triple(0.76f, 0.33f, 0.39f), Triple(0.36f, 0.22f, 0.41f), Triple(0.62f, 0.74f, 0.28f), Triple(0.88f, 0.63f, 0.18f),
            Triple(0.19f, 0.24f, 0.59f), Triple(0.28f, 0.58f, 0.30f), Triple(0.69f, 0.20f, 0.23f), Triple(0.93f, 0.78f, 0.18f), Triple(0.73f, 0.33f, 0.57f), Triple(0.18f, 0.53f, 0.65f),
            Triple(0.96f, 0.96f, 0.96f), Triple(0.79f, 0.79f, 0.79f), Triple(0.63f, 0.63f, 0.63f), Triple(0.47f, 0.47f, 0.47f), Triple(0.31f, 0.31f, 0.31f), Triple(0.12f, 0.12f, 0.12f)
        )

        for (y in 0 until height) {
            val rIdx = (y / cellH).coerceIn(0, rows - 1)
            for (x in 0 until width) {
                val cIdx = (x / cellW).coerceIn(0, cols - 1)
                val patchIndex = rIdx * cols + cIdx
                val base = baseColors[patchIndex]

                val finalR: Float
                val finalG: Float
                val finalB: Float
                if (graded) {
                    finalR = (base.first * 1.15f + 0.04f).coerceIn(0f, 1f)
                    finalG = (base.second * 0.98f + 0.01f).coerceIn(0f, 1f)
                    finalB = (base.third * 0.85f - 0.02f).coerceIn(0f, 1f)
                } else {
                    finalR = base.first
                    finalG = base.second
                    finalB = base.third
                }

                val offset = (y * width + x) * 3
                rgbData[offset] = (finalR * 255f).roundToInt().toByte()
                rgbData[offset + 1] = (finalG * 255f).roundToInt().toByte()
                rgbData[offset + 2] = (finalB * 255f).roundToInt().toByte()
            }
        }

        return createBmp(width, height, rgbData)
    }

    private fun createBmp(width: Int, height: Int, rgbData: ByteArray): ByteArray {
        val rowSize = ((width * 3 + 3) / 4) * 4
        val imageSize = rowSize * height
        val fileSize = 54 + imageSize

        val header = ByteArray(54)
        header[0] = 'B'.code.toByte()
        header[1] = 'M'.code.toByte()
        writeInt(header, 2, fileSize)
        writeInt(header, 10, 54)
        writeInt(header, 14, 40)
        writeInt(header, 18, width)
        writeInt(header, 22, height)
        writeShort(header, 26, 1)
        writeShort(header, 28, 24)
        writeInt(header, 34, imageSize)

        val out = ByteArray(fileSize)
        System.arraycopy(header, 0, out, 0, 54)

        var destOffset = 54
        for (y in height - 1 downTo 0) {
            val srcRow = y * width * 3
            for (x in 0 until width) {
                val srcIdx = srcRow + x * 3
                val r = rgbData[srcIdx]
                val g = rgbData[srcIdx + 1]
                val b = rgbData[srcIdx + 2]
                out[destOffset++] = b
                out[destOffset++] = g
                out[destOffset++] = r
            }
            while (destOffset % 4 != 2) {
                destOffset++
            }
        }
        return out
    }

    private fun writeInt(arr: ByteArray, offset: Int, value: Int) {
        arr[offset] = (value and 0xFF).toByte()
        arr[offset + 1] = ((value shr 8) and 0xFF).toByte()
        arr[offset + 2] = ((value shr 16) and 0xFF).toByte()
        arr[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShort(arr: ByteArray, offset: Int, value: Int) {
        arr[offset] = (value and 0xFF).toByte()
        arr[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
