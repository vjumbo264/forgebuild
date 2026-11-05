#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uIntensity;
uniform float uWarmth;
uniform float uHighlightCool;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);

    // Dynamic S-curve & tonal contrast adjustment
    vec3 graded = pow(max(rgb, vec3(0.0)), vec3(0.92, 0.95, 1.05));
    graded = mix(graded, graded * graded * (3.0 - 2.0 * graded), 0.35);

    // Color balance offsets
    graded.r += (1.0 - rgb.r) * uWarmth * 0.12;
    graded.b += rgb.b * uHighlightCool * 0.12;
    graded = clamp(graded, 0.0, 1.0);

    vec3 res = mix(rgb, graded, uIntensity);
    gl_FragColor = vec4(res * c.a, c.a);
}
