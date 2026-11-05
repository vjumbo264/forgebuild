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

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 center = c.rgb / max(c.a, 0.0001);

    vec2 step = uTexelSize * uRadius;

    vec3 n = texture2D(uTexture, clamp(vTex + vec2(0.0, step.y), 0.0, 1.0)).rgb;
    vec3 s = texture2D(uTexture, clamp(vTex - vec2(0.0, step.y), 0.0, 1.0)).rgb;
    vec3 e = texture2D(uTexture, clamp(vTex + vec2(step.x, 0.0), 0.0, 1.0)).rgb;
    vec3 w = texture2D(uTexture, clamp(vTex - vec2(step.x, 0.0), 0.0, 1.0)).rgb;

    vec3 laplacian = 4.0 * center - (n + s + e + w);
    vec3 sharp = center + laplacian * uAmount;

    gl_FragColor = vec4(clamp(sharp, 0.0, 1.0) * c.a, c.a);
}
