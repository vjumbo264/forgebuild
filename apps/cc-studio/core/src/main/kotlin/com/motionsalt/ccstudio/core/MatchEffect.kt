package com.motionsalt.ccstudio.core

object MatchEffect {
    fun definition(model:ParametricMatch=ParametricMatch(),name:String="Colour Match",id:String="colour_match",params:Map<String,Any> = emptyMap()):EffectDefinition {
        val controls=listOf(
            EffectParam("strength","Strength",ParamType.SCALAR,1.0,0f,1f,.01f),
            EffectParam("matrixEnabled","Linear matrix",ParamType.BOOL,1.0),
            EffectParam("curvesEnabled","Tone curves",ParamType.BOOL,1.0),
            EffectParam("hueEnabled","Hue-selective correction",ParamType.BOOL,1.0)
        ).map{it.copy(defaultValue=params[it.key]?:it.defaultValue)}
        return EffectDefinition(id,name,"MOTIONSALT","Direct colour match: linear matrix, monotone PCHIP, periodic D65 Lab hue correction.",parameters=controls,shaderCode=MatchShader.source(model))
    }
}
