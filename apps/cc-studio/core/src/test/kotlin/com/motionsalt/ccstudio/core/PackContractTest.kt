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
}
