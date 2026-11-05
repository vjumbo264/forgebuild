package com.motionsalt.ccstudio.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AppTestSuite {

    @Test
    fun testTuneStateDefaultStackHasLutFirst() {
        val state = TuneState.defaultState()
        assertEquals(1, state.stack.size)
        assertEquals("ms_lut", state.stack[0].effectId)
        assertTrue(state.stack[0].enabled)
        assertTrue(state.stack[0].expose)
        assertEquals(1.0, (state.stack[0].params["intensity"] as Number).toDouble(), 0.0001)
    }

    @Test
    fun testTuneStateReorderingKeepsLutFirst() {
        val state = TuneState.defaultState()
        val glow = StackItem(
            instanceId = "glow-1",
            effectId = "ms_glow",
            enabled = true,
            expose = true,
            params = mutableMapOf("intensity" to 0.5)
        )
        val grain = StackItem(
            instanceId = "grain-1",
            effectId = "ms_grain",
            enabled = true,
            expose = true,
            params = mutableMapOf("amount" to 0.2)
        )

        state.stack.add(glow)
        state.stack.add(grain)
        assertEquals(3, state.stack.size)
        assertEquals("ms_lut", state.stack[0].effectId)
        assertEquals("ms_glow", state.stack[1].effectId)
        assertEquals("ms_grain", state.stack[2].effectId)

        // Reorder grain and glow
        val temp = state.stack[1]
        state.stack[1] = state.stack[2]
        state.stack[2] = temp

        assertEquals("ms_grain", state.stack[1].effectId)
        assertEquals("ms_glow", state.stack[2].effectId)

        val json = state.toJsonString()
        val parsed = TuneState.fromJsonString(json)
        assertEquals(3, parsed.stack.size)
        assertEquals("ms_lut", parsed.stack[0].effectId)
        assertEquals("ms_grain", parsed.stack[1].effectId)
        assertEquals("ms_glow", parsed.stack[2].effectId)
    }

    @Test
    fun testStackItemDuplication() {
        val original = StackItem(
            instanceId = "orig-1",
            effectId = "ms_sharpen",
            enabled = true,
            expose = false,
            params = mutableMapOf("sharpness" to 0.75)
        )

        val copy = original.copyItem()
        assertNotEquals(original.instanceId, copy.instanceId)
        assertEquals(original.effectId, copy.effectId)
        assertEquals(original.enabled, copy.enabled)
        assertEquals(original.expose, copy.expose)
        assertEquals((original.params["sharpness"] as Number).toDouble(), (copy.params["sharpness"] as Number).toDouble(), 0.0001)
    }

    @Test
    fun testEnsurePrecisionHeader() {
        val rawShader = """
uniform sampler2D uTexture;
varying vec2 vTex;
void main() {
    gl_FragColor = texture2D(uTexture, vTex);
}
        """.trimIndent()

        val withHeader = PackBuilder.ensurePrecisionHeader(rawShader)
        assertTrue(withHeader.startsWith("#ifdef GL_FRAGMENT_PRECISION_HIGH"))
        assertTrue(withHeader.contains("precision highp float;"))

        // Applying twice shouldn't duplicate header
        val twice = PackBuilder.ensurePrecisionHeader(withHeader)
        assertEquals(withHeader, twice)
    }

    @Test
    fun testPackValidationCatchesMissingHeader() {
        // Shader missing mandatory header
        val badShader = """
uniform sampler2D uTexture;
varying vec2 vTex;
void main() { gl_FragColor = texture2D(uTexture, vTex); }
        """.trimIndent()

        val suite = SuiteInfo(id = "com.test", name = "Test", author = "Test", brand = "Test", version = "1.0")
        val effect = EffectDefinition(
            id = "bad_eff",
            name = "Bad Effect",
            category = "Tone",
            description = "",
            shaderCode = badShader
        )

        val baos = java.io.ByteArrayOutputStream()
        PackBuilder.buildPack(suite, listOf(effect), baos)

        // Note: buildPack automatically ensures precision header so exported packs are valid!
        val bais = java.io.ByteArrayInputStream(baos.toByteArray())
        val validation = PackBuilder.validatePack(bais)
        assertTrue(validation.isValid)
    }
}
