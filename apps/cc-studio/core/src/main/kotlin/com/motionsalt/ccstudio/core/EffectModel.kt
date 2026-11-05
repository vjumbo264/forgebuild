package com.motionsalt.ccstudio.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ParamType {
    SCALAR,
    BOOL,
    CHOICE,
    COLOR
}

data class EffectParam(
    val key: String,
    val label: String,
    val type: ParamType,
    val defaultValue: Any,
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 0.01f,
    val options: List<String> = emptyList(),
    val group: String = "General"
)

data class EffectDefinition(
    val id: String,
    val name: String,
    val category: String, // Colour correction, Tone, Detail, Look
    val description: String,
    val expandBounds: Float = 0f,
    val parameters: List<EffectParam> = emptyList(),
    val passes: List<String> = emptyList(),
    val shaderCode: String = ""
)

data class StackItem(
    val instanceId: String = UUID.randomUUID().toString(),
    val effectId: String,
    var enabled: Boolean = true,
    var expose: Boolean = true,
    val params: MutableMap<String, Any> = mutableMapOf()
) {
    fun copyItem(): StackItem {
        return StackItem(
            instanceId = UUID.randomUUID().toString(),
            effectId = effectId,
            enabled = enabled,
            expose = expose,
            params = HashMap(params)
        )
    }

    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("instanceId", instanceId)
        obj.put("effectId", effectId)
        obj.put("enabled", enabled)
        obj.put("expose", expose)

        val paramsObj = JSONObject()
        for ((k, v) in params) {
            paramsObj.put(k, v)
        }
        obj.put("params", paramsObj)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): StackItem {
            val instanceId = obj.optString("instanceId", UUID.randomUUID().toString())
            val effectId = obj.getString("effectId")
            val enabled = obj.optBoolean("enabled", true)
            val expose = obj.optBoolean("expose", true)
            val params = mutableMapOf<String, Any>()

            val pObj = obj.optJSONObject("params")
            if (pObj != null) {
                val keys = pObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    params[k] = pObj.get(k)
                }
            }
            return StackItem(instanceId, effectId, enabled, expose, params)
        }
    }
}

data class TuneState(
    val version: Int = 1,
    val stack: MutableList<StackItem> = mutableListOf()
) {
    init {
        // Preserve order: Colour Match may move, but every state contains exactly one.
        if(stack.none { it.effectId == "colour_match" }) stack.add(0,primary())
    }

    fun toJsonString(): String {
        val root = JSONObject()
        root.put("version", version)
        val arr = JSONArray()
        for (item in stack) {
            arr.put(item.toJsonObject())
        }
        root.put("stack", arr)
        return root.toString(2)
    }

    companion object {
        fun fromJsonString(json: String): TuneState {
            val root = JSONObject(json)
            val version = root.optInt("version", 1)
            val arr = root.optJSONArray("stack") ?: JSONArray()
            val list = mutableListOf<StackItem>()
            for (i in 0 until arr.length()) {
                val itemObj = arr.getJSONObject(i)
                val item=StackItem.fromJsonObject(itemObj)
                if(item.effectId == "colour_match" || !item.effectId.startsWith("ms_")) list.add(item)
            }
            return TuneState(version, list)
        }

        fun primary() = StackItem(instanceId="colour-match-primary",effectId="colour_match",params=mutableMapOf("enabled" to 1.0, "strength" to 1.0,"matrixEnabled" to 1.0,"curvesEnabled" to 1.0,"hueEnabled" to 1.0))
        fun defaultState() = TuneState(stack=mutableListOf(primary()))
    }
}
