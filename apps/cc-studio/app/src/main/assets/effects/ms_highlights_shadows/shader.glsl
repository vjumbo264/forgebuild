#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uShadows;
uniform float uHighlights;
uniform float uWhites;
uniform float uBlacks;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);
    float luma = clamp(dot(rgb, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    float sMask = 1.0 - smoothstep(0.0, 0.6, luma);
    float hMask = smoothstep(0.4, 1.0, luma);
    float bMask = 1.0 - smoothstep(0.0, 0.25, luma);
    float wMask = smoothstep(0.75, 1.0, luma);
    rgb += vec3(uShadows * 0.3 * sMask);
    rgb += vec3(uHighlights * 0.3 * hMask);
    rgb += vec3(uBlacks * 0.2 * bMask);
    rgb += vec3(uWhites * 0.2 * wMask);
    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0) * c.a, c.a);
}
