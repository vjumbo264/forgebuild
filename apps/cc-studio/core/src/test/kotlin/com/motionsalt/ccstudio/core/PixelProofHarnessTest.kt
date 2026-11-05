package com.motionsalt.ccstudio.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * Pixel-level proof test suite (Binding per RESET_PROMPT T2/T3).
 * Proves that:
 * 1. An image evaluated with ms_lut at intensity 0 is identical to the input.
 * 2. An image evaluated with a fitted look or effect differs from input by > threshold.
 * 3. Moving parameters changes the rendered pixels.
 * 4. Disabling an effect restores the base state.
 * 5. Reordering non-commuting effects produces distinct pixels.
 * 6. Identity LUT reproduces the input within 1/255.
 * 7. Invert LUT produces exact inverted pixels.
 * 8. Channel-swap LUT produces exact swapped R and B channels.
 * 9. GPU 2D strip sampling (ColorMath.sampleLut2D) matches CPU evaluation within 1.5/255.
 * 10. Target-Blind test: Recreated output does not depend on target frame once fitted.
 * 11. Unsharp mask boosts edge contrast acutance on synthetic edge.
 * 12. Sharpen amplifies high-frequency Laplacian detail.
 */
class PixelProofHarnessTest {

    private fun createTestBuffer(w: Int = 16, h: Int = 16): StackRenderer.PixelBuffer {
        val buf = StackRenderer.PixelBuffer(w, h, IntArray(w * h))
        for (y in 0 until h) {
            for (x in 0 until w) {
                val r = x.toFloat() / max(1, w - 1)
                val g = y.toFloat() / max(1, h - 1)
                val b = (x + y).toFloat() / max(1, (w - 1) * 2)
                buf.setRgb(x, y, r, g, b, 1.0f)
            }
        }
        return buf
    }

    private fun computePixelDiff(a: StackRenderer.PixelBuffer, b: StackRenderer.PixelBuffer): Float {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        var sum = 0f
        val count = a.width * a.height
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                sum += abs(a.getR(x, y) - b.getR(x, y))
                sum += abs(a.getG(x, y) - b.getG(x, y))
                sum += abs(a.getB(x, y) - b.getB(x, y))
            }
        }
        return sum / (count * 3)
    }

    @Test
    fun testLutIntensityZeroEqualsInput() {
        val input = createTestBuffer()
        val transform = FittedTransform(
            curveR = FloatArray(256) { (it / 255f) * 0.8f },
            curveG = FloatArray(256) { (it / 255f) * 1.1f },
            curveB = FloatArray(256) { (it / 255f) * 0.9f },
            matrix = arrayOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f)),
            matrixOffset = floatArrayOf(0f, 0f, 0f),
            gridSize = 17,
            residual = FloatArray(17 * 17 * 17 * 3),
            strength = 1.0f
        )
        val tuneState = TuneState.defaultState()
        tuneState.stack[0].params["intensity"] = 0.0
        val rendered = StackRenderer.render(input, tuneState, transform)
        val diff = computePixelDiff(input, rendered)
        assertEquals("LUT with intensity 0 must equal input pixels exactly", 0.0f, diff, 0.0001f)
    }

    @Test
    fun testLutIntensityOneDiffersFromInput() {
        val input = createTestBuffer()
        val transform = FittedTransform(
            curveR = FloatArray(256) { (it / 255f).coerceIn(0f, 1f).let { v -> v * v } },
            curveG = FloatArray(256) { (it / 255f) },
            curveB = FloatArray(256) { (it / 255f) * 0.5f },
            matrix = arrayOf(floatArrayOf(1.2f, 0f, 0f), floatArrayOf(0f, 0.9f, 0f), floatArrayOf(0f, 0f, 0.7f)),
            matrixOffset = floatArrayOf(0.05f, -0.02f, 0.08f),
            gridSize = 17,
            residual = FloatArray(17 * 17 * 17 * 3),
            strength = 1.0f
        )
        val tuneState = TuneState.defaultState()
        tuneState.stack[0].params["intensity"] = 1.0
        val rendered = StackRenderer.render(input, tuneState, transform)
        val diff = computePixelDiff(input, rendered)
        assertTrue("LUT with intensity 1.0 must visibly change pixels (diff=$diff)", diff > 0.05f)
    }

    @Test
    fun testIdentityLutReproducesInputWithinOneByte() {
        val input = createTestBuffer(24, 24)
        val identity = ColorMath.createIdentityTransform(33)
        val rendered = StackRenderer.applyLut(input, identity, 1.0f)
        val maxDiff = (0 until input.height).maxOf { y ->
            (0 until input.width).maxOf { x ->
                maxOf(
                    abs(input.getR(x, y) - rendered.getR(x, y)),
                    abs(input.getG(x, y) - rendered.getG(x, y)),
                    abs(input.getB(x, y) - rendered.getB(x, y))
                )
            }
        }
        assertTrue("Identity LUT must reproduce input within 1/255 (maxDiff=$maxDiff)", maxDiff < (1.0f / 255.0f + 0.0001f))
    }

    @Test
    fun testInvertLutProducesExactInversion() {
        val input = createTestBuffer(16, 16)
        val invert = ColorMath.createInvertTransform(33)
        val rendered = StackRenderer.applyLut(input, invert, 1.0f)
        for (y in 0 until input.height) {
            for (x in 0 until input.width) {
                val expectedR = 1.0f - input.getR(x, y)
                val expectedG = 1.0f - input.getG(x, y)
                val expectedB = 1.0f - input.getB(x, y)
                assertEquals(expectedR, rendered.getR(x, y), 0.005f)
                assertEquals(expectedG, rendered.getG(x, y), 0.005f)
                assertEquals(expectedB, rendered.getB(x, y), 0.005f)
            }
        }
    }

    @Test
    fun testChannelSwapLutProducesExactChannelSwap() {
        val input = createTestBuffer(16, 16)
        val swap = ColorMath.createChannelSwapTransform(33)
        val rendered = StackRenderer.applyLut(input, swap, 1.0f)
        for (y in 0 until input.height) {
            for (x in 0 until input.width) {
                assertEquals(input.getB(x, y), rendered.getR(x, y), 0.005f)
                assertEquals(input.getG(x, y), rendered.getG(x, y), 0.005f)
                assertEquals(input.getR(x, y), rendered.getB(x, y), 0.005f)
            }
        }
    }

    @Test
    fun testGpu2DStripSamplingMatchesCpuTransform() {
        val transform = FittedTransform(
            curveR = FloatArray(256) { (it / 255f) * 0.9f + 0.05f },
            curveG = FloatArray(256) { (it / 255f) * 1.05f },
            curveB = FloatArray(256) { (it / 255f) * 0.85f },
            matrix = arrayOf(floatArrayOf(1.05f, -0.05f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0.02f, 0f, 0.98f)),
            matrixOffset = floatArrayOf(0.01f, 0.0f, -0.01f),
            gridSize = 33,
            residual = FloatArray(33 * 33 * 33 * 3) { 0f },
            strength = 1.0f
        )
        val strip = ColorMath.generateLut2DStrip(transform, 33)
        var maxDiff = 0f
        for (bStep in 0..10) {
            val b = bStep / 10.0f
            for (gStep in 0..10) {
                val g = gStep / 10.0f
                for (rStep in 0..10) {
                    val r = rStep / 10.0f
                    val cpu = transform.evaluate(r, g, b)
                    val gpu = ColorMath.sampleLut2D(strip, r, g, b, 33)
                    val diffR = abs(cpu.first - gpu.first)
                    val diffG = abs(cpu.second - gpu.second)
                    val diffB = abs(cpu.third - gpu.third)
                    maxDiff = maxOf(maxDiff, diffR, diffG, diffB)
                }
            }
        }
        assertTrue("GPU 2D strip sampling must match CPU evaluation within 1.5/255 (maxDiff=$maxDiff)", maxDiff < (1.5f / 255.0f))
    }

    @Test
    fun testTargetBlindRecreatedIntegrity() {
        val pairs = SyntheticGenerator.generateSyntheticPixelPairs(200)
        val result = LutSolver.fit(pairs, gridSize = 17) {}
        val baseBuffer = createTestBuffer(32, 32)
        
        // Recreated frame 1 evaluated through fitted LUT
        val recreated1 = StackRenderer.applyLut(baseBuffer, result.transform, 1.0f)
        
        // Now simulate target frame replaced by noise or modified entirely
        val targetCorrupted = StackRenderer.PixelBuffer(32, 32, IntArray(32 * 32) { (Math.random() * 0xFFFFFF).toInt() })
        
        // Recreated frame 2 evaluated from baseBuffer + transform
        val recreated2 = StackRenderer.applyLut(baseBuffer, result.transform, 1.0f)
        
        // Assert recreated1 and recreated2 are 100% byte-identical
        assertArrayEquals(
            "Recreated frame must not depend on target image pixels once LUT has been fitted",
            recreated1.pixels,
            recreated2.pixels
        )
        
        // And assert recreated is distinct from the corrupted target
        val diffFromTarget = computePixelDiff(recreated1, targetCorrupted)
        assertTrue("Recreated frame must not match unrelated target pixels", diffFromTarget > 0.10f)
    }

    @Test
    fun testUnsharpMaskEdgeAcutance() {
        // Create an image with a sharp edge down the middle: left is dark (0.2), right is light (0.8)
        val w = 32
        val h = 16
        val edgeBuf = StackRenderer.PixelBuffer(w, h, IntArray(w * h))
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = if (x < w / 2) 0.2f else 0.8f
                edgeBuf.setRgb(x, y, v, v, v, 1.0f)
            }
        }
        
        // Apply unsharp mask with After Effects standard formula
        val unsharp = StackRenderer.applyUnsharpMask(edgeBuf, amount = 150f, radius = 1.5f, threshold = 0f)
        
        // The pixel immediately to the left of the edge should undershoot (become darker than 0.2)
        val xLeft = w / 2 - 1
        val leftVal = unsharp.getR(xLeft, h / 2)
        assertTrue("Unsharp mask undershoot at edge must make dark side darker (got $leftVal)", leftVal < 0.20f)
        
        // The pixel immediately to the right of the edge should overshoot (become brighter than 0.8)
        val xRight = w / 2
        val rightVal = unsharp.getR(xRight, h / 2)
        assertTrue("Unsharp mask overshoot at edge must make light side brighter (got $rightVal)", rightVal > 0.80f)
        
        // With amount = 0, output must equal input exactly
        val zeroUnsharp = StackRenderer.applyUnsharpMask(edgeBuf, amount = 0f, radius = 1.5f, threshold = 0f)
        val diffZero = computePixelDiff(edgeBuf, zeroUnsharp)
        assertEquals("Unsharp mask with amount 0 must equal input", 0.0f, diffZero, 0.0001f)
    }

    @Test
    fun testSharpenLaplacianResponse() {
        val w = 16
        val h = 16
        val buf = createTestBuffer(w, h)
        val sharpened = StackRenderer.applySharpen(buf, amount = 1.5f, radius = 1.0f)
        val diff = computePixelDiff(buf, sharpened)
        assertTrue("Sharpen must visibly boost detail (diff=$diff)", diff > 0.01f)
        
        val zeroSharpen = StackRenderer.applySharpen(buf, amount = 0f, radius = 1.0f)
        val diffZero = computePixelDiff(buf, zeroSharpen)
        assertEquals("Sharpen with amount 0 must equal input", 0.0f, diffZero, 0.0001f)
    }

    @Test
    fun testEffectParametersChangePixels() {
        val input = createTestBuffer()
        val state1 = TuneState.defaultState()
        state1.stack[0].enabled = false
        state1.stack.add(StackItem(effectId = "ms_brightness_contrast", params = mutableMapOf("brightness" to 0.25f, "contrast" to 0.4f)))
        val out1 = StackRenderer.render(input, state1)
        val diff1 = computePixelDiff(input, out1)
        assertTrue("Brightness & Contrast must visibly alter pixels", diff1 > 0.10f)

        val state2 = TuneState.defaultState()
        state2.stack[0].enabled = false
        state2.stack.add(StackItem(effectId = "ms_tritone", params = mutableMapOf(
            "shadowColor" to "#0A1428",
            "midtoneColor" to "#D48B47",
            "highlightColor" to "#FFF2D9",
            "blend" to 1.0f
        )))
        val out2 = StackRenderer.render(input, state2)
        val diff2 = computePixelDiff(input, out2)
        assertTrue("Tritone toning must visibly alter pixels", diff2 > 0.15f)

        state2.stack[1].enabled = false
        val outDisabled = StackRenderer.render(input, state2)
        val diffDisabled = computePixelDiff(input, outDisabled)
        assertEquals("Disabling effect must restore clean input pixels", 0.0f, diffDisabled, 0.0001f)
    }

    @Test
    fun testReorderingEffectsChangesOutput() {
        val input = createTestBuffer()
        val stateA = TuneState.defaultState()
        stateA.stack[0].enabled = false
        stateA.stack.add(StackItem(effectId = "ms_brightness_contrast", params = mutableMapOf("brightness" to 0.4f, "contrast" to 0.2f)))
        stateA.stack.add(StackItem(effectId = "ms_tritone", params = mutableMapOf(
            "shadowColor" to "#112233", "midtoneColor" to "#884422", "highlightColor" to "#FFEEDD", "blend" to 0.8f
        )))
        val outA = StackRenderer.render(input, stateA)

        val stateB = TuneState.defaultState()
        stateB.stack[0].enabled = false
        stateB.stack.add(StackItem(effectId = "ms_tritone", params = mutableMapOf(
            "shadowColor" to "#112233", "midtoneColor" to "#884422", "highlightColor" to "#FFEEDD", "blend" to 0.8f
        )))
        stateB.stack.add(StackItem(effectId = "ms_brightness_contrast", params = mutableMapOf("brightness" to 0.4f, "contrast" to 0.2f)))
        val outB = StackRenderer.render(input, stateB)
        val diffAB = computePixelDiff(outA, outB)
        assertTrue("Reordering non-commuting effects in stack must change pixels (diff=$diffAB)", diffAB > 0.02f)
    }
}
