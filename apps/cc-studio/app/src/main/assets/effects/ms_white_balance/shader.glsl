#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uTemperature;
uniform float uTint;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);

    // Temperature shift (red/blue inverse)
    rgb.r += uTemperature * 0.18;
    rgb.b -= uTemperature * 0.18;

    // Tint shift (green vs magenta)
    rgb.g -= uTint * 0.15;
    rgb.r += uTint * 0.08;
    rgb.b += uTint * 0.08;

    rgb = clamp(rgb, 0.0, 1.0);
    gl_FragColor = vec4(rgb * c.a, c.a);
}
