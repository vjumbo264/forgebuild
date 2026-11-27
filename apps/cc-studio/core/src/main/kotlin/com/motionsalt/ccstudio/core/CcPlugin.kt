package com.motionsalt.ccstudio.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One API 1 effect per saved grade. Pass descriptions and bytes also serve as the preview graph. */
object CcPlugin {
    data class Param(val id: String, val label: String, val group: String, val min: Double, val max: Double,
                     val neutral: Double, val initial: Double, val scale: Int = 1, val decimals: Int = 0,
                     val type: String = "scalar", val choices: List<String> = emptyList()) {
        fun json(): JSONObject = JSONObject().put("id",id).put("label",label).put("group",group).put("type",type).apply {
            if (type == "scalar") put("default",neutral).put("min",min).put("max",max).put("displayScale",scale).put("decimals",decimals)
            else put("default",neutral.toInt())
            if (choices.isNotEmpty()) put("options",JSONArray(choices))
        }
    }
    private fun toggle(id: String, group: String, initial: Double = 0.0) = Param(id, "Enabled", group, 0.0, 1.0, 0.0, initial, type="bool")
    private const val MATCH = "Colour Match"
    private const val CLARITY = "Clarity"
    private const val STRUCT = "Structure"
    private const val USM = "AE Unsharp Mask"
    private const val HAZE = "Haze Flare"
    val params = listOf(
        toggle("matchOn", MATCH, 1.0), Param("matchStrength", "Strength", MATCH, 0.0, 1.0, 0.0, 1.0, 100),
        toggle("clarityOn", CLARITY), Param("clarity", "Amount", CLARITY, -100.0, 100.0, 0.0, 0.0),
        toggle("structureOn", STRUCT), Param("structure", "Amount", STRUCT, -100.0, 100.0, 0.0, 0.0),
        toggle("usmOn", USM), Param("usmAmount", "Amount", USM, 0.0, 500.0, 0.0, 50.0),
        Param("usmRadius", "Radius", USM, .1, 100.0, 1.0, 1.0, decimals=1),
        Param("usmThreshold", "Threshold", USM, 0.0, 255.0, 0.0, 0.0),
        toggle("hazeOn", HAZE), Param("hazeIntensity", "Intensity", HAZE, 0.0, 1.0, 0.0, .4, 100),
        Param("hazeThreshold", "Threshold", HAZE, 0.0, 1.0, .5, .5, 100),
        Param("hazeOpacity", "Opacity", HAZE, 0.0, 1.0, .4, .4, 100),
        Param("hazeSpread", "Spread", HAZE, .1, .6, .35, .35, 100),
        Param("hazeBand", "Band", HAZE, .005, .1, .03, .03, 100, 1),
        Param("hazeSide", "Side", HAZE, 0.0, .5, .4, .4, 100),
        Param("hazeCenter", "Center", HAZE, 0.0, 1.0, 0.0, 0.0, 100),
        Param("hazeGain", "Gain", HAZE, 1.0, 15.0, 3.5, 3.5, decimals=1),
        Param("hazeMode", "Blend mode", HAZE, 0.0, 2.0, 0.0, 0.0, type="choice", choices=listOf("Lighten","Additive","Screen"))
    )
    val initial: Map<String, Double> = params.associate { it.id to it.initial }
    data class Pass(val source: String, val scale: Double = 1.0, val reference: Int? = null,
                    val gate: String? = null, val floatTarget: Boolean = false) {
        fun json(index: Int) = JSONObject().put("shader", "pass$index.frag").put("scale", scale).apply {
            reference?.let { put("prevPass",it) }; gate?.let { put("requiredParam",it).put("requiredMinimum",.5) }
            if (floatTarget) put("floatTarget",true)
        }
    }
    private fun shader(name: String) = checkNotNull(javaClass.getResourceAsStream("/cc-studio/$name.frag")).use { it.readBytes().toString(Charsets.UTF_8) }
    fun passes(model: ParametricMatch): List<Pass> = listOf(
        Pass(MatchShader.source(model, plugin=true)),
        Pass(shader("detail")),
        Pass(shader("usm_horizontal"), gate="usmOn", floatTarget=true),
        Pass(shader("usm_composite"), reference=1),
        Pass(shader("haze_extract"), .25, gate="hazeOn", floatTarget=true),
        Pass(shader("haze_horizontal"), .25, gate="hazeOn", floatTarget=true),
        Pass(shader("haze_vertical"), .25, gate="hazeOn", floatTarget=true),
        Pass(shader("haze_composite"), reference=3)
    )
    data class Look(val id: String, val name: String, val model: ParametricMatch, val values: Map<String, Double> = emptyMap())
    private fun values(map: Map<String, Double>) = JSONObject().apply {
        for ((id,value) in map) {
            val param = params.firstOrNull { it.id == id } ?: error("Unknown CC parameter $id")
            require(value.isFinite() && value in param.min..param.max && (param.type == "scalar" || value == value.toInt().toDouble())) { "Invalid CC value $id=$value" }
            if (param.type == "scalar") put(id,value) else put(id,value.toInt())
        }
    }
    fun entries(looks: List<Look>, versionCode: Int): Map<String, ByteArray> {
        require(versionCode > 0 && looks.isNotEmpty() && looks.size <= 7)
        require(looks.map { it.id }.toSet().size == looks.size && looks.all { it.id.matches(Regex("[a-z0-9_-]{1,32}")) && it.name.isNotBlank() })
        val manifest = JSONObject().put("format",1).put("api",1).put("id","motionsalt.cc").put("name","MOTIONSALT")
            .put("version","1.0.$versionCode").put("versionCode",versionCode).put("author","MOTIONSALT")
            .put("description","Colour match and four finishing controls in one effect per look.")
            .put("license","All rights reserved").put("effects",JSONArray(looks.map { it.id }))
        return linkedMapOf("manifest.json" to manifest.toString(2).toByteArray()).apply {
            for (look in looks) {
                val prefix = "effects/${look.id}/"
                val passes = passes(look.model)
                val presets = listOf(
                    Triple("victor_sharpen","Victor sharpen",mapOf("usmOn" to 1.0,"usmAmount" to 32.0,"usmRadius" to 22.1,"usmThreshold" to 0.0)),
                    Triple("soft_haze","Soft haze",mapOf("hazeOn" to 1.0,"hazeIntensity" to .4,"hazeOpacity" to .4,"hazeMode" to 2.0))
                ).map { (id,label,p) -> JSONObject().put("id",id).put("label",label).put("values",values(p)) }
                val effect = JSONObject().put("id",look.id).put("name",look.name).put("category","Warna")
                    .put("params",JSONArray(params.map { it.json() })).put("passes",JSONArray(passes.mapIndexed { i,p -> p.json(i) }))
                    .put("newInstanceValues",values(initial + look.values)).put("presets",JSONArray(presets))
                    .put("thumbnailValues",values(mapOf("matchOn" to 1.0,"matchStrength" to 1.0))).put("thumbnailStyle","split")
                put(prefix+"effect.json",effect.toString(2).toByteArray())
                for ((i,pass) in passes.withIndex()) {
                    val bytes = pass.source.toByteArray()
                    require(bytes.size <= 32768) { "Pass $i exceeds the API 1 shader limit: ${bytes.size} bytes" }
                    put(prefix+"pass$i.frag",bytes)
                }
            }
        }
    }
    fun zip(entries: Map<String, ByteArray>): ByteArray {
        require(entries.size <= 64 && entries.values.sumOf { it.size.toLong() } <= 8*1024*1024)
        return ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip -> for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name).apply { time = 0L }); zip.write(data); zip.closeEntry()
            } }
        }.toByteArray().also { require(it.size <= 2*1024*1024) }
    }
}
