package com.motionsalt.ccstudio.core

import java.util.Locale

/** One source generator for preview AND exported shader.glsl. No sampler except the input image. */
object MatchShader {
    fun number(x:Double):String { require(x.isFinite());return String.format(Locale.US,"%.12e",x) }
    fun source(model:ParametricMatch):String=buildString {
        append("""#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
precision highp sampler2D;
uniform sampler2D uTexture;
uniform float uEnabled;
uniform float uStrength;
uniform float uMatrixEnabled;
uniform float uCurvesEnabled;
uniform float uHueEnabled;
varying vec2 vTex;
float lin(float x) { return x<=0.04045 ? x/12.92 : pow((x+0.055)/1.055,2.4); }
float enc(float v) { float x=clamp(v,0.0,1.0); return x<=0.0031308 ? 12.92*x : 1.055*pow(x,1.0/2.4)-0.055; }
float lf(float x) { return x>0.008856 ? pow(x,1.0/3.0) : 7.787*x+16.0/116.0; }
float li(float x) { return x>0.2068966 ? x*x*x : (x-16.0/116.0)/7.787; }
vec3 lab(vec3 rgb) { vec3 p=vec3(lin(rgb.r),lin(rgb.g),lin(rgb.b)); vec3 v=vec3(lf(dot(p,vec3(.412453,.357580,.180423))/.95047),lf(dot(p,vec3(.212671,.715160,.072169))),lf(dot(p,vec3(.019334,.119193,.950227))/1.08883)); return vec3(116.0*v.y-16.0,500.0*(v.x-v.y),200.0*(v.y-v.z));}
vec3 rgb(vec3 l) { float y=(l.x+16.0)/116.0;float x=l.y/500.0+y;float z=max(0.0,y-l.z/200.0); vec3 p=vec3(li(x)*.95047,li(y),li(z)*1.08883); return vec3(enc(dot(p,vec3(3.24048134,-1.53715152,-.49853633))),enc(dot(p,vec3(-.96925495,1.87599,.04155593))),enc(dot(p,vec3(.05564664,-.20404134,1.05731107))));}
""")
        model.curves.forEachIndexed { c,curve ->
            append("float curve$c(float v) {\n")
            for(i in 0 until curve.x.size-1) {
                val last=i==curve.x.size-2
                if(!last)append("if(v<${number(curve.x[i+1])}) ")
                append("{float h=${number(curve.x[i+1]-curve.x[i])};float t=(v-${number(curve.x[i])})/h;float t2=t*t;float t3=t2*t;")
                append("return clamp((2.0*t3-3.0*t2+1.0)*${number(curve.y[i])}+(t3-2.0*t2+t)*h*${number(curve.slopes[i])}+(-2.0*t3+3.0*t2)*${number(curve.y[i+1])}+(t3-t2)*h*${number(curve.slopes[i+1])},0.0,1.0);}\n")
            };append("}\n")
        }
        append("vec3 hueParam(float i) {\n")
        for(i in 0 until 23)append("if(i<${i+.5})return vec3(${model.hue[i].joinToString(","){number(it)}});\n")
        append("return vec3(${model.hue[23].joinToString(","){number(it)}});}\n")
        append("void main(){vec4 src=texture2D(uTexture,clamp(vTex,0.0,1.0));if(uEnabled<=0.5){gl_FragColor=src;return;}vec3 inputRgb=src.rgb/max(src.a,0.0001);vec3 p=vec3(lin(inputRgb.r),lin(inputRgb.g),lin(inputRgb.b));vec3 g=inputRgb;\n")
        append("if(uMatrixEnabled>0.5){g=vec3(")
        append((0..2).joinToString(","){c->"enc(dot(p,vec3(${model.matrix[c].joinToString(","){number(it)}}))+${number(model.offset[c])})"});append(");}\n")
        append("""if(uCurvesEnabled>0.5)g=vec3(curve0(g.r),curve1(g.g),curve2(g.b));if(uHueEnabled>0.5){vec3 l=lab(g);float h=atan(l.z,l.y);float c=length(l.yz);float w=c/(c+10.0);float k=mod(h/6.283185307179586,1.0)*24.0;float i=floor(k);float f=fract(k);vec3 v=mix(hueParam(i),hueParam(mod(i+1.0,24.0)),f);float c2=c*(1.0+v.y*w);float h2=h+v.z*w;g=rgb(vec3(l.x+v.x*w,c2*cos(h2),c2*sin(h2)));}gl_FragColor=vec4(mix(inputRgb,g,clamp(uStrength,0.0,1.0))*src.a,src.a);}""")
    }
}
