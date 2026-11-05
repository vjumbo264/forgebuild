#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uRedR;
uniform float uRedG;
uniform float uRedB;
uniform float uGreenR;
uniform float uGreenG;
uniform float uGreenB;
uniform float uBlueR;
uniform float uBlueG;
uniform float uBlueB;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);

    vec3 mixed = vec3(
        rgb.r * uRedR + rgb.g * uRedG + rgb.b * uRedB,
        rgb.r * uGreenR + rgb.g * uGreenG + rgb.b * uGreenB,
        rgb.r * uBlueR + rgb.g * uBlueG + rgb.b * uBlueB
    );

    gl_FragColor = vec4(clamp(mixed, 0.0, 1.0) * c.a, c.a);
}
