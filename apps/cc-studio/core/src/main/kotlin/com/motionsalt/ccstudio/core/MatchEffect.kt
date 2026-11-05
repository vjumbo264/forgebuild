package com.motionsalt.ccstudio.core

object MatchEffect {
    private fun toDouble(v: Any?, fallback: Double): Double {
        if (v == null) return fallback
        if (v is Boolean) return if (v) 1.0 else 0.0
        if (v is Number) return v.toDouble()
        val s = v.toString().trim().lowercase()
        if (s == "true" || s == "1" || s == "1.0" || s == "yes" || s == "on") return 1.0
        if (s == "false" || s == "0" || s == "0.0" || s == "no" || s == "off") return 0.0
        return s.toDoubleOrNull() ?: fallback
    }

    fun definition(
        model: ParametricMatch = ParametricMatch(),
        name: String = "Colour Match",
        id: String = "colour_match",
        params: Map<String, Any> = emptyMap(),
        isEnabled: Boolean = true
    ): EffectDefinition {
        val overallEnabled = if (isEnabled) toDouble(params["enabled"], 1.0) else 0.0
        val controls = listOf(
            EffectParam("enabled", "Enable Color Correction", ParamType.BOOL, overallEnabled, 0f, 1f, 1f, group = "General"),
            EffectParam("strength", "Strength", ParamType.SCALAR, toDouble(params["strength"], 1.0), 0f, 1f, .01f, group = "General"),
            EffectParam("matrixEnabled", "Linear matrix", ParamType.BOOL, toDouble(params["matrixEnabled"], 1.0), 0f, 1f, 1f, group = "Components"),
            EffectParam("curvesEnabled", "Tone curves", ParamType.BOOL, toDouble(params["curvesEnabled"], 1.0), 0f, 1f, 1f, group = "Components"),
            EffectParam("hueEnabled", "Hue-selective correction", ParamType.BOOL, toDouble(params["hueEnabled"], 1.0), 0f, 1f, 1f, group = "Components")
        )
        return EffectDefinition(
            id = id,
            name = name,
            category = "MOTIONSALT",
            description = "Direct colour match: linear matrix, monotone PCHIP, periodic D65 Lab hue correction.",
            parameters = controls,
            shaderCode = MatchShader.source(model)
        )
    }
}
