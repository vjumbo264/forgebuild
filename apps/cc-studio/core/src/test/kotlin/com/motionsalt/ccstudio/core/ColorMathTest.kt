package com.motionsalt.ccstudio.core

import org.junit.Assert.*
import org.junit.Test

class ColorMathTest {
    @Test
    fun testIdentityTransformPreservesColors() {
        val curve = FloatArray(256) { it / 255f }
        val matrix = arrayOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f)
        )
        val offset = floatArrayOf(0f, 0f, 0f)
        val gridSize = 17
        val residual = FloatArray(gridSize * gridSize * gridSize * 3)

        val transform = FittedTransform(curve, curve, curve, matrix, offset, gridSize, residual, 1.0f)

        // Evaluate corners and center
        val black = transform.evaluate(0f, 0f, 0f)
        assertEquals(0f, black.first, 1e-4f)
        assertEquals(0f, black.second, 1e-4f)
        assertEquals(0f, black.third, 1e-4f)

        val white = transform.evaluate(1f, 1f, 1f)
        assertEquals(1f, white.first, 1e-4f)
        assertEquals(1f, white.second, 1e-4f)
        assertEquals(1f, white.third, 1e-4f)

        val mid = transform.evaluate(0.5f, 0.5f, 0.5f)
        assertEquals(0.5f, mid.first, 0.01f)
        assertEquals(0.5f, mid.second, 0.01f)
        assertEquals(0.5f, mid.third, 0.01f)
    }

    @Test
    fun testCubeTextFormatting() {
        val curve = FloatArray(256) { it / 255f }
        val matrix = arrayOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f))
        val offset = floatArrayOf(0f, 0f, 0f)
        val transform = FittedTransform(curve, curve, curve, matrix, offset, 17, FloatArray(17 * 17 * 17 * 3), 1f)

        val cube = ColorMath.generateCubeText(transform, 17)
        assertTrue(cube.contains("LUT_3D_SIZE 17"))
        assertTrue(cube.contains("DOMAIN_MIN 0.0 0.0 0.0"))
        assertTrue(cube.contains("DOMAIN_MAX 1.0 1.0 1.0"))
    }

    @Test
    fun testDeltaEComputation() {
        val lab1 = ColorMath.rgbToLab(1f, 0f, 0f)
        val lab2 = ColorMath.rgbToLab(1f, 0f, 0f)
        val diff = ColorMath.deltaE76(lab1, lab2)
        assertEquals(0f, diff, 1e-4f)
    }
}
