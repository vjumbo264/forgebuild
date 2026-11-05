package com.motionsalt.ccstudio.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CoreTestSuite {

    @Test
    fun testColorMathConversions() {
        // sRGB to linear and back
        for (v in listOf(0f, 0.01f, 0.04f, 0.18f, 0.5f, 0.8f, 1.0f)) {
            val lin = ColorMath.srgbToLinear(v)
            val srgb = ColorMath.linearToSrgb(lin)
            assertEquals(v, srgb, 1e-3f)
        }

        // Lab conversion
        val whiteLab = ColorMath.rgbToLab(1f, 1f, 1f)
        assertEquals(100f, whiteLab.first, 1.0f)

        val blackLab = ColorMath.rgbToLab(0f, 0f, 0f)
        assertEquals(0f, blackLab.first, 0.1f)
    }

    @Test
    fun testHald8BmpGeneration() {
        val identity = FittedTransform(
            FloatArray(256) { it / 255f },
            FloatArray(256) { it / 255f },
            FloatArray(256) { it / 255f },
            arrayOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f)),
            floatArrayOf(0f, 0f, 0f),
            17,
            FloatArray(17 * 17 * 17 * 3),
            1.0f
        )
        val bmpBytes = ColorMath.generateHald8Bmp(identity)
        assertTrue(bmpBytes.size > 54)
        assertEquals('B'.code.toByte(), bmpBytes[0])
        assertEquals('M'.code.toByte(), bmpBytes[1])
    }

    @Test
    fun testLutSolverWithSyntheticPairs() {
        val pairs = SyntheticGenerator.generateSyntheticPixelPairs(500)
        var lastStep = ""
        val result = LutSolver.fit(pairs, gridSize = 17) { progress ->
            lastStep = progress.stepName
            assertTrue(progress.progressFraction in 0f..1f)
        }

        assertEquals("Build LUT/Hald", lastStep)
        assertNotNull(result.transform)
        assertTrue(result.sampleCount > 0)
        assertTrue(result.meanDeltaE >= 0f)
        assertTrue(result.p95DeltaE >= result.meanDeltaE)

        // Evaluate transform on a sample
        val out = result.transform.evaluate(0.5f, 0.5f, 0.5f)
        assertTrue(out.first in 0f..1f)
        assertTrue(out.second in 0f..1f)
        assertTrue(out.third in 0f..1f)
    }

    @Test
    fun testTuneStateSerialization() {
        val state = TuneState.defaultState()
        assertEquals(1, state.stack.size)
        assertEquals("ms_lut", state.stack[0].effectId)

        // Add glow effect
        state.stack.add(
            StackItem(
                instanceId = "glow-1",
                effectId = "ms_glow",
                enabled = true,
                expose = true,
                params = mutableMapOf("radius" to 0.4, "intensity" to 0.8)
            )
        )

        val json = state.toJsonString()
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("ms_lut"))
        assertTrue(json.contains("ms_glow"))

        // Deserialize back
        val restored = TuneState.fromJsonString(json)
        assertEquals(2, restored.stack.size)
        assertEquals("ms_lut", restored.stack[0].effectId)
        assertEquals("ms_glow", restored.stack[1].effectId)
        assertEquals(0.8, (restored.stack[1].params["intensity"] as Number).toDouble(), 0.0001)
    }

    @Test
    fun testPackBuilderAndValidator() {
        val suite = SuiteInfo(
            id = "com.motionsalt.testsuite",
            name = "Test Pack",
            author = "MOTIONSALT",
            brand = "MOTIONSALT",
            version = "1.0.0",
            description = "Test Suite",
            category = "MOTIONSALT"
        )

        val lutEffect = EffectDefinition(
            id = "ms_lut",
            name = "3D LUT Grade",
            category = "Colour correction",
            description = "Applies 3D LUT transform",
            parameters = listOf(
                EffectParam("intensity", "Intensity", ParamType.SCALAR, 1.0f, 0f, 1f, 0.01f)
            ),
            shaderCode = """
uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uIntensity;
void main() {
    vec4 c = texture2D(uTexture, vTex);
    gl_FragColor = c;
}
            """.trimIndent()
        )

        val baos = ByteArrayOutputStream()
        PackBuilder.buildPack(suite, listOf(lutEffect), baos)
        val packBytes = baos.toByteArray()
        assertTrue(packBytes.isNotEmpty())

        // Validate
        val bais = ByteArrayInputStream(packBytes)
        val validation = PackBuilder.validatePack(bais)
        assertTrue(validation.errors.joinToString(", "), validation.isValid)
        assertEquals("com.motionsalt.testsuite", validation.suiteInfo?.id)
        assertTrue(validation.effectIds.contains("ms_lut"))
    }

    @Test
    fun testSyntheticChartBmp() {
        val baseBmp = SyntheticGenerator.generateChartBmp(graded = false)
        val targetBmp = SyntheticGenerator.generateChartBmp(graded = true)

        assertTrue(baseBmp.size > 54)
        assertTrue(targetBmp.size > 54)
        assertEquals(baseBmp.size, targetBmp.size)
    }
}
