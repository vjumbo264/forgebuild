package com.motionsalt.ccstudio.data

import android.content.Context
import com.motionsalt.ccstudio.core.EffectDefinition
import com.motionsalt.ccstudio.core.EffectParam
import com.motionsalt.ccstudio.core.ParamType
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class EffectRepository(private val context: Context) {

    private val cachedEffects = mutableListOf<EffectDefinition>()

    fun getAvailableEffects(): List<EffectDefinition> {
        if (cachedEffects.isNotEmpty()) return cachedEffects

        try {
            val effectDirs = context.assets.list("effects") ?: emptyArray()
            for (dir in effectDirs) {
                val jsonPath = "effects/$dir/effect.json"
                val glslPath = "effects/$dir/shader.glsl"

                try {
                    val jsonStr = context.assets.open(jsonPath).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    val glslStr = context.assets.open(glslPath).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

                    val obj = JSONObject(jsonStr)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val category = mapCategory(obj.optString("category", "Colour correction"))
                    val description = obj.optString("description", "")
                    val expandBounds = obj.optDouble("expandBounds", 0.0).toFloat()

                    val paramsList = mutableListOf<EffectParam>()
                    val paramsArr = obj.optJSONArray("parameters")
                    if (paramsArr != null) {
                        for (i in 0 until paramsArr.length()) {
                            val pObj = paramsArr.getJSONObject(i)
                            val key = pObj.getString("key")
                            val label = pObj.optString("label", key)
                            val typeStr = pObj.optString("type", "SCALAR")
                            val type = try { ParamType.valueOf(typeStr) } catch (e: Exception) { ParamType.SCALAR }
                            val defaultVal = pObj.opt("default") ?: 0.0
                            val min = pObj.optDouble("min", 0.0).toFloat()
                            val max = pObj.optDouble("max", 1.0).toFloat()
                            val step = pObj.optDouble("step", 0.01).toFloat()
                            val group = pObj.optString("group", "General")

                            val optionsList = mutableListOf<String>()
                            val optArr = pObj.optJSONArray("options")
                            if (optArr != null) {
                                for (o in 0 until optArr.length()) optionsList.add(optArr.getString(o))
                            }

                            paramsList.add(
                                EffectParam(
                                    key = key,
                                    label = label,
                                    type = type,
                                    defaultValue = defaultVal,
                                    min = min,
                                    max = max,
                                    step = step,
                                    options = optionsList,
                                    group = group
                                )
                            )
                        }
                    }

                    cachedEffects.add(
                        EffectDefinition(
                            id = id,
                            name = name,
                            category = category,
                            description = description,
                            expandBounds = expandBounds,
                            parameters = paramsList,
                            shaderCode = glslStr
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort by category and name, with ms_lut first
        cachedEffects.sortBy { eff ->
            if (eff.id == "ms_lut") "000" else "${eff.category}_${eff.name}"
        }
        return cachedEffects
    }

        private fun mapCategory(raw: String): String {
        return when (raw.lowercase().trim()) {
            "warna", "colour", "color", "colour correction" -> "Colour correction"
            "tone" -> "Tone"
            "toning" -> "Toning"
            "detail", "detail & clarity" -> "Detail"
            "look", "cinematic", "stylize" -> "Look"
            else -> raw
        }
    }
}
}
