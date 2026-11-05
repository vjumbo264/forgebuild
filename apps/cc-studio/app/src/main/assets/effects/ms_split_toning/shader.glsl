#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uShadowHue;
uniform float uShadowSat;
uniform float uHighHue;
uniform float uHighSat;
uniform float uBalance;

vec3 hueToRgb(float hue) {
    float h = fract(hue / 360.0) * 6.0;
    float x = 1.0 - abs(mod(h, 2.0) - 1.0);
    vec3 c = vec3(0.0);
    if (h < 1.0) c = vec3(1.0, x, 0.0);
    else if (h < 2.0) c = vec3(x, 1.0, 0.0);
    else if (h < 3.0) c = vec3(0.0, 1.0, x);
    else if (h < 4.0) c = vec3(0.0, x, 1.0);
    else if (h < 5.0) c = vec3(x, 0.0, 1.0);
    else c = vec3(1.0, 0.0, x);
    return c;
}

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);
    float luma = clamp(dot(rgb, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    float pivot = 0.5 + uBalance * 0.25;
    float shadowWeight = clamp((pivot - luma) / max(pivot, 0.0001), 0.0, 1.0);
    float highWeight = clamp((luma - pivot) / max(1.0 - pivot, 0.0001), 0.0, 1.0);
    vec3 sTint = hueToRgb(uShadowHue);
    vec3 hTint = hueToRgb(uHighHue);
    rgb = mix(rgb, rgb * sTint, shadowWeight * uShadowSat);
    rgb = mix(rgb, 1.0 - (1.0 - rgb) * (1.0 - hTint * 0.5), highWeight * uHighSat);
    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0) * c.a, c.a);
}
