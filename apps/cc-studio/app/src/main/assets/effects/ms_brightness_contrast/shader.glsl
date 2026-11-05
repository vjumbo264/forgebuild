#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uBrightness;
uniform float uContrast;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);

    // Apply brightness
    rgb += vec3(uBrightness);

    // Apply contrast centered on 0.5
    float factor = (1.015 * (uContrast + 1.0)) / max(1.015 - uContrast, 0.0001);
    rgb = (rgb - 0.5) * factor + 0.5;

    rgb = clamp(rgb, 0.0, 1.0);
    gl_FragColor = vec4(rgb * c.a, c.a);
}
