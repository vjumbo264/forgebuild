#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform vec2 uTexelSize;
uniform float uStrength;
uniform float uRadius;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 orig = c.rgb / max(c.a, 0.0001);

    vec2 off = uTexelSize * uRadius;
    vec3 lowpass = orig * 0.36;
    lowpass += texture2D(uTexture, clamp(vTex + vec2(off.x, 0.0), 0.0, 1.0)).rgb * 0.16;
    lowpass += texture2D(uTexture, clamp(vTex - vec2(off.x, 0.0), 0.0, 1.0)).rgb * 0.16;
    lowpass += texture2D(uTexture, clamp(vTex + vec2(0.0, off.y), 0.0, 1.0)).rgb * 0.16;
    lowpass += texture2D(uTexture, clamp(vTex - vec2(0.0, off.y), 0.0, 1.0)).rgb * 0.16;

    vec3 highpass = orig - lowpass;
    vec3 enhanced = orig + highpass * uStrength;

    gl_FragColor = vec4(clamp(enhanced, 0.0, 1.0) * c.a, c.a);
}
