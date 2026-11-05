package com.motionsalt.ccstudio.core

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

class PackContractTest {
    private fun pack(effects:List<EffectDefinition> = listOf(MatchEffect.definition())):ByteArray=ByteArrayOutputStream().also{PackBuilder.buildPack(SuiteInfo(),effects,it)}.toByteArray()
    private fun entries(bytes:ByteArray):Map<String,ByteArray> {val result=linkedMapOf<String,ByteArray>();ZipInputStream(bytes.inputStream()).use{zip->var e=zip.nextEntry;while(e!=null){result[e.name]=zip.readBytes();e=zip.nextEntry}};return result}
    @Test fun parserCompatibleRootIdsAndIdenticalShaderBytes(){val e=MatchEffect.definition();val b=pack();val files=entries(b);assertEquals(setOf("suite.json","effects/colour_match/effect.json","effects/colour_match/shader.glsl"),files.keys);assertEquals("colour_match",JSONObject(String(files.getValue("suite.json"))).getJSONArray("effects").getString(0));assertArrayEquals(e.shaderCode.toByteArray(),files.getValue("effects/colour_match/shader.glsl"));assertTrue(PackBuilder.validatePack(b.inputStream()).isValid)}
    @Test fun collisionKeepBothPreservesExistingBytes(){val existing=pack();val out=ByteArrayOutputStream();val ids=PackBuilder.mergePacks(existing.inputStream(),listOf(MatchEffect.definition(name="Second")),CollisionPolicy.KEEP_BOTH_NEW_ID,out);assertEquals(listOf("colour_match_2"),ids);val f=entries(out.toByteArray());assertArrayEquals(entries(existing).getValue("effects/colour_match/shader.glsl"),f.getValue("effects/colour_match/shader.glsl"));assertTrue(PackBuilder.validatePack(out.toByteArray().inputStream()).isValid)}
    @Test fun replaceAndSkipAreReal(){for(policy in listOf(CollisionPolicy.REPLACE,CollisionPolicy.SKIP)){val out=ByteArrayOutputStream();PackBuilder.mergePacks(pack().inputStream(),listOf(MatchEffect.definition(name="Changed")),policy,out);val obj=JSONObject(String(entries(out.toByteArray()).getValue("effects/colour_match/effect.json")));assertEquals(if(policy==CollisionPolicy.REPLACE)"Changed"else "Colour Match",obj.getString("name"))}}
    @Test fun scalarAndStageControlsRoundTrip(){val s=TuneState.defaultState();assertEquals("colour_match",s.stack.single().effectId);s.stack.single().params["strength"]=.47;val copy=TuneState.fromJsonString(s.toJsonString());assertEquals(.47,(copy.stack.single().params["strength"] as Number).toDouble(),0.0)}
    @Test fun shaderContainsOnlyInputSampler(){val source=MatchShader.source(ParametricMatch());assertEquals(1,Regex("uniform sampler2D").findAll(source).count());assertFalse(source.contains("uLut"));assertTrue(source.contains("uHueEnabled"))}
    @Test(expected=IllegalArgumentException::class)fun unsupportedGraphsNeverSilentlyExportFirstPass(){pack(listOf(MatchEffect.definition().copy(passes=listOf("pass1.glsl"))))}

    @Test fun multiPassEffectsExportAllDeclaredPassShadersAndValidateSuccessfully() {
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

    @Test fun tunedParameterValuesExportAsDefaultState() {
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
}
