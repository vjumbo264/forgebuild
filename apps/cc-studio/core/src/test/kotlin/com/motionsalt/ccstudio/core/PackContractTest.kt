package com.motionsalt.ccstudio.core

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

class PackContractTest {
    private fun pack(effects: List<EffectDefinition> = listOf(MatchEffect.definition())): ByteArray =
        ByteArrayOutputStream().also { PackBuilder.buildPack(SuiteInfo(), effects, it) }.toByteArray()

    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                result[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }
        return result
    }

    @Test
    fun parserCompatibleRootIdsAndIdenticalShaderBytes() {
        val e = MatchEffect.definition()
        val b = pack()
        val files = entries(b)
        assertEquals(setOf("suite.json", "effects/colour_match/effect.json", "effects/colour_match/shader.glsl"), files.keys)
        assertEquals("colour_match", JSONObject(String(files.getValue("suite.json"))).getJSONArray("effects").getString(0))
        assertArrayEquals(e.shaderCode.toByteArray(), files.getValue("effects/colour_match/shader.glsl"))
        assertTrue(PackBuilder.validatePack(b.inputStream()).isValid)
    }

    @Test
    fun collisionKeepBothPreservesExistingBytes() {
        val existing = pack()
        val out = ByteArrayOutputStream()
        val ids = PackBuilder.mergePacks(existing.inputStream(), listOf(MatchEffect.definition(name = "Second")), CollisionPolicy.KEEP_BOTH_NEW_ID, out)
        assertEquals(listOf("colour_match_2"), ids)
        val f = entries(out.toByteArray())
        assertArrayEquals(entries(existing).getValue("effects/colour_match/shader.glsl"), f.getValue("effects/colour_match/shader.glsl"))
        assertTrue(PackBuilder.validatePack(out.toByteArray().inputStream()).isValid)
    }

    @Test
    fun replaceAndSkipAreReal() {
        for (policy in listOf(CollisionPolicy.REPLACE, CollisionPolicy.SKIP)) {
            val out = ByteArrayOutputStream()
            PackBuilder.mergePacks(pack().inputStream(), listOf(MatchEffect.definition(name = "Changed")), policy, out)
            val obj = JSONObject(String(entries(out.toByteArray()).getValue("effects/colour_match/effect.json")))
            assertEquals(if (policy == CollisionPolicy.REPLACE) "Changed" else "Colour Match", obj.getString("name"))
        }
    }

    @Test
    fun scalarAndStageControlsRoundTrip() {
        val s = TuneState.defaultState()
        assertEquals("colour_match", s.stack.single().effectId)
        s.stack.single().params["strength"] = .47
        val copy = TuneState.fromJsonString(s.toJsonString())
        assertEquals(.47, (copy.stack.single().params["strength"] as Number).toDouble(), 0.0)
    }

    @Test
    fun shaderContainsOnlyInputSampler() {
        val source = MatchShader.source(ParametricMatch())
        assertEquals(1, Regex("uniform sampler2D").findAll(source).count())
        assertFalse(source.contains("uLut"))
        assertTrue(source.contains("uHueEnabled"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedGraphsNeverSilentlyExportFirstPass() {
        pack(listOf(MatchEffect.definition().copy(passes = listOf("pass1.glsl"))))
    }

    @Test
    fun multiPassEffectsExportAllDeclaredPassShadersAndValidateSuccessfully() {
        val passCode = "precision mediump float; uniform sampler2D uTexture; varying vec2 vTex; void main() { gl_FragColor = texture2D(uTexture, vTex); }"
        val multi = EffectDefinition(
            id = "test_multi",
            name = "Test Multi",
            category = "MOTIONSALT",
            description = "Multi-pass test",
            parameters = listOf(EffectParam("amount", "Amount", ParamType.SCALAR, 15.0, 0f, 100f, 1f)),
            passes = listOf("shader.glsl", "pass_01.glsl"),
            shaderCode = passCode,
            passShaders = mapOf("pass_01.glsl" to passCode)
        )
        val b = pack(listOf(multi))
        val files = entries(b)
        assertTrue(files.containsKey("effects/test_multi/effect.json"))
        assertTrue(files.containsKey("effects/test_multi/shader.glsl"))
        assertTrue(files.containsKey("effects/test_multi/pass_01.glsl"))
        val valResult = PackBuilder.validatePack(b.inputStream())
        assertTrue("Pack validation failed: ${valResult.errors}", valResult.isValid)
    }

    @Test
    fun tunedParameterValuesExportAsDefaultState() {
        val tuned = EffectDefinition(
            id = "sharpen",
            name = "Sharpen",
            category = "MOTIONSALT",
            description = "Sharpen",
            parameters = listOf(EffectParam("amount", "Sharpen Amount", ParamType.SCALAR, 15.0, 0f, 4000f, 1f)),
            shaderCode = "precision mediump float; uniform sampler2D uTexture; uniform float uAmount; varying vec2 vTex; void main() { gl_FragColor = texture2D(uTexture, vTex) * uAmount; }"
        )
        val b = pack(listOf(tuned))
        val files = entries(b)
        val effectJson = JSONObject(String(files.getValue("effects/sharpen/effect.json")))
        val params = effectJson.getJSONArray("parameters")
        val amountParam = (0 until params.length()).map { params.getJSONObject(it) }.first { it.getString("key") == "amount" }
        assertEquals(15.0, amountParam.getDouble("default"), 0.001)
        val enableParam = (0 until params.length()).map { params.getJSONObject(it) }.first { it.getString("key") == "enabled" }
        assertEquals(1.0, enableParam.getDouble("default"), 0.001)
    }

    @Test
    fun disabledEffectExportsWithDisabledDefault() {
        val prep = PackBuilder.prepareEffect(
            EffectDefinition(
                id = "unsharp_mask",
                name = "Unsharp Mask",
                category = "MOTIONSALT",
                description = "Unsharp Mask",
                parameters = listOf(
                    EffectParam("amount", "Amount", ParamType.SCALAR, 75.0, 0f, 500f, 1f),
                    EffectParam("radius", "Radius", ParamType.SCALAR, 2.5, 0.1f, 500f, 0.1f)
                ),
                shaderCode = "precision mediump float; uniform sampler2D uTexture; varying vec2 vTex; void main() { gl_FragColor = texture2D(uTexture, vTex); }"
            ),
            suiteCategory = "MOTIONSALT",
            isEnabled = false
        )
        val b = pack(listOf(prep))
        val files = entries(b)
        val effectJson = JSONObject(String(files.getValue("effects/unsharp_mask/effect.json")))
        val params = effectJson.getJSONArray("parameters")
        val enableParam = (0 until params.length()).map { params.getJSONObject(it) }.first { it.getString("key") == "enabled" }
        assertEquals(0.0, enableParam.getDouble("default"), 0.001)
        val amountParam = (0 until params.length()).map { params.getJSONObject(it) }.first { it.getString("key") == "amount" }
        assertEquals(75.0, amountParam.getDouble("default"), 0.001)
    }

    @Test
    fun counterpartStackExportPreservesAllTunedSettingsAndDisabledComponents() {
        val ccEffect = MatchEffect.definition(
            model = ParametricMatch(),
            name = "Gojo CC",
            id = "look_gojo12345",
            params = mapOf(
                "enabled" to true,
                "strength" to 0.85,
                "matrixEnabled" to false, // disabled sub-parameter
                "curvesEnabled" to true,
                "hueEnabled" to false   // disabled sub-parameter
            ),
            isEnabled = true
        )
        val sharpenEffect = PackBuilder.prepareEffect(
            EffectDefinition(
                id = "sharpen",
                name = "Sharpen",
                category = "MOTIONSALT",
                description = "Sharpen",
                parameters = listOf(EffectParam("amount", "Sharpen Amount", ParamType.SCALAR, 150.0, 0f, 4000f, 1f)),
                shaderCode = "precision mediump float; uniform sampler2D uTexture; uniform float uAmount; varying vec2 vTex; void main() { gl_FragColor = texture2D(uTexture, vTex); }"
            ),
            suiteCategory = "MOTIONSALT",
            isEnabled = true
        )
        val disabledGlow = PackBuilder.prepareEffect(
            EffectDefinition(
                id = "glow",
                name = "Glow",
                category = "MOTIONSALT",
                description = "Glow",
                parameters = listOf(
                    EffectParam("threshold", "Threshold", ParamType.SCALAR, 0.4, 0f, 1f, 0.01f),
                    EffectParam("radius", "Radius", ParamType.SCALAR, 0.8, 0f, 1f, 0.01f)
                ),
                shaderCode = "precision mediump float; uniform sampler2D uTexture; varying vec2 vTex; void main() { gl_FragColor = texture2D(uTexture, vTex); }"
            ),
            suiteCategory = "MOTIONSALT",
            isEnabled = false
        )

        val b = pack(listOf(ccEffect, sharpenEffect, disabledGlow))
        val files = entries(b)

        val suiteJson = JSONObject(String(files.getValue("suite.json")))
        val effectsArr = suiteJson.getJSONArray("effects")
        assertEquals(3, effectsArr.length())
        assertEquals("look_gojo12345", effectsArr.getString(0))
        assertEquals("sharpen", effectsArr.getString(1))
        assertEquals("glow", effectsArr.getString(2))

        // Check CC effect
        val ccJson = JSONObject(String(files.getValue("effects/look_gojo12345/effect.json")))
        val ccParams = ccJson.getJSONArray("parameters")
        val matrixParam = (0 until ccParams.length()).map { ccParams.getJSONObject(it) }.first { it.getString("key") == "matrixEnabled" }
        assertEquals("matrixEnabled must be 0.0 when disabled", 0.0, matrixParam.getDouble("default"), 0.001)
        val curvesParam = (0 until ccParams.length()).map { ccParams.getJSONObject(it) }.first { it.getString("key") == "curvesEnabled" }
        assertEquals("curvesEnabled must be 1.0 when enabled", 1.0, curvesParam.getDouble("default"), 0.001)
        val hueParam = (0 until ccParams.length()).map { ccParams.getJSONObject(it) }.first { it.getString("key") == "hueEnabled" }
        assertEquals("hueEnabled must be 0.0 when disabled", 0.0, hueParam.getDouble("default"), 0.001)
        val strengthParam = (0 until ccParams.length()).map { ccParams.getJSONObject(it) }.first { it.getString("key") == "strength" }
        assertEquals("strength must be 0.85", 0.85, strengthParam.getDouble("default"), 0.001)

        // Check sharpen effect
        val sharpJson = JSONObject(String(files.getValue("effects/sharpen/effect.json")))
        val sharpParams = sharpJson.getJSONArray("parameters")
        val sharpAmt = (0 until sharpParams.length()).map { sharpParams.getJSONObject(it) }.first { it.getString("key") == "amount" }
        assertEquals("sharpen amount must be 150.0", 150.0, sharpAmt.getDouble("default"), 0.001)
        val sharpEnabled = (0 until sharpParams.length()).map { sharpParams.getJSONObject(it) }.first { it.getString("key") == "enabled" }
        assertEquals("sharpen enabled must be 1.0", 1.0, sharpEnabled.getDouble("default"), 0.001)

        // Check disabled glow effect
        val glowJson = JSONObject(String(files.getValue("effects/glow/effect.json")))
        val glowParams = glowJson.getJSONArray("parameters")
        val glowEnabled = (0 until glowParams.length()).map { glowParams.getJSONObject(it) }.first { it.getString("key") == "enabled" }
        assertEquals("glow enabled must be 0.0 because it was disabled in stack", 0.0, glowEnabled.getDouble("default"), 0.001)
        val glowRadius = (0 until glowParams.length()).map { glowParams.getJSONObject(it) }.first { it.getString("key") == "radius" }
        assertEquals("glow radius tuned default preserved", 0.8, glowRadius.getDouble("default"), 0.001)

        val valResult = PackBuilder.validatePack(b.inputStream())
        assertTrue("Pack validation failed: ${valResult.errors}", valResult.isValid)
    }
}
