#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform vec3 uColor0;
uniform vec3 uColor1;
uniform vec3 uColor2;
uniform vec3 uColor3;
uniform float uMixStrength;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);
    float l = clamp(dot(rgb, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    vec3 mapped;
    if (l < 0.333) {
        mapped = mix(uColor0, uColor1, l / 0.333);
    } else if (l < 0.666) {
        mapped = mix(uColor1, uColor2, (l - 0.333) / 0.333);
    } else {
        mapped = mix(uColor2, uColor3, (l - 0.666) / 0.334);
    }
    vec3 res = mix(rgb, mapped, clamp(uMixStrength, 0.0, 1.0));
    gl_FragColor = vec4(clamp(res, 0.0, 1.0) * c.a, c.a);
}
