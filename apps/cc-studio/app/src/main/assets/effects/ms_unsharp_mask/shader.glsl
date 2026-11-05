#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform vec2 uTexelSize;
uniform float uAmount;
uniform float uRadius;
uniform float uThreshold;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 orig = c.rgb / max(c.a, 0.0001);

    // 9-tap Gaussian approximation for blurred low-pass base
    vec2 off = uTexelSize * uRadius;
    vec3 blur = orig * 0.25;
    blur += texture2D(uTexture, clamp(vTex + vec2(off.x, 0.0), 0.0, 1.0)).rgb * 0.125;
    blur += texture2D(uTexture, clamp(vTex - vec2(off.x, 0.0), 0.0, 1.0)).rgb * 0.125;
    blur += texture2D(uTexture, clamp(vTex + vec2(0.0, off.y), 0.0, 1.0)).rgb * 0.125;
    blur += texture2D(uTexture, clamp(vTex - vec2(0.0, off.y), 0.0, 1.0)).rgb * 0.125;
    blur += texture2D(uTexture, clamp(vTex + off, 0.0, 1.0)).rgb * 0.0625;
    blur += texture2D(uTexture, clamp(vTex - off, 0.0, 1.0)).rgb * 0.0625;
    blur += texture2D(uTexture, clamp(vTex + vec2(off.x, -off.y), 0.0, 1.0)).rgb * 0.0625;
    blur += texture2D(uTexture, clamp(vTex + vec2(-off.x, off.y), 0.0, 1.0)).rgb * 0.0625;

    vec3 diff = orig - blur;
    float thresh = uThreshold / 255.0;

    // Apply only where difference exceeds threshold
    vec3 boost = step(vec3(thresh), abs(diff)) * diff * (uAmount / 100.0);
    vec3 result = clamp(orig + boost, 0.0, 1.0);

    gl_FragColor = vec4(result * c.a, c.a);
}
