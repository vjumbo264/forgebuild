package com.motionsalt.ccstudio.core

object MatchEffect {
    fun definition(
        model: ParametricMatch = ParametricMatch(),
        name: String = "Colour Match",
        id: String = "colour_match",
        params: Map<String, Any> = emptyMap()
    ): EffectDefinition {
        val controls = listOf(
            EffectParam("enabled", "Enable Color Correction", ParamType.BOOL, 1.0, 0f, 1f, 1f, group = "General"),
            EffectParam("strength", "Strength", ParamType.SCALAR, 1.0, 0f, 1f, .01f, group = "General"),
            EffectParam("matrixEnabled", "Linear matrix", ParamType.BOOL, 1.0, 0f, 1f, 1f, group = "Components"),
            EffectParam("curvesEnabled", "Tone curves", ParamType.BOOL, 1.0, 0f, 1f, 1f, group = "Components"),
            EffectParam("hueEnabled", "Hue-selective correction", ParamType.BOOL, 1.0, 0f, 1f, 1f, group = "Components")
        ).map { it.copy(defaultValue = params[it.key] ?: it.defaultValue) }
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
