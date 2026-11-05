#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform vec3 uShadowColor;
uniform vec3 uHighlightColor;
uniform float uBlend;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 duo = mix(uShadowColor, uHighlightColor, clamp(luma, 0.0, 1.0));
    vec3 res = mix(rgb, duo, clamp(uBlend, 0.0, 1.0));
    gl_FragColor = vec4(clamp(res, 0.0, 1.0) * c.a, c.a);
}
