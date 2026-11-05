#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform vec2 uTexelSize;
uniform float uThreshold;
uniform float uRadius;
uniform float uIntensity;
uniform vec3 uTint;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);

    // Multi-tap bloom accumulator
    vec2 step = uTexelSize * uRadius;
    vec3 bloom = vec3(0.0);
    float totalWeight = 0.0;

    for (int i = -3; i <= 3; i++) {
        for (int j = -3; j <= 3; j++) {
            float dist = length(vec2(float(i), float(j)));
            float w = exp(-0.5 * dist * dist);
            vec2 tapUv = clamp(vTex + vec2(float(i), float(j)) * step * 0.4, 0.0, 1.0);
            vec3 tapCol = texture2D(uTexture, tapUv).rgb;
            float tapLuma = dot(tapCol, vec3(0.2126, 0.7152, 0.0722));
            float bright = max(0.0, tapLuma - uThreshold) / max(1.0 - uThreshold, 0.0001);
            bloom += tapCol * bright * w;
            totalWeight += w;
        }
    }
    bloom /= max(totalWeight, 0.0001);

    // Additive screen blend tinted by uTint
    vec3 result = rgb + bloom * uIntensity * uTint;
    gl_FragColor = vec4(clamp(result, 0.0, 1.0) * c.a, c.a);
}
