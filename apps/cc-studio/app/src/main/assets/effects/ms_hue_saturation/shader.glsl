#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uHueShift;
uniform float uSaturation;
uniform float uVibrance;

vec3 rgbToHsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsvToRgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);

    vec3 hsv = rgbToHsv(rgb);

    // Hue shift
    hsv.x = fract(hsv.x + uHueShift / 360.0);

    // Standard saturation
    hsv.y = clamp(hsv.y * uSaturation, 0.0, 1.0);

    // Smart vibrance: boosts lower saturated pixels more than already saturated ones
    float satDelta = (1.0 - hsv.y) * uVibrance * 0.7;
    hsv.y = clamp(hsv.y + satDelta, 0.0, 1.0);

    vec3 outRgb = hsvToRgb(hsv);
    gl_FragColor = vec4(outRgb * c.a, c.a);
}
