#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform vec2 uTexelSize;
uniform float uThreshold;
uniform float uBloomIntensity;
uniform vec3 uHalationColor;
uniform float uHalationIntensity;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);
    vec2 off = uTexelSize * 3.5;
    vec3 acc = vec3(0.0);
    acc += texture2D(uTexture, clamp(vTex + vec2(off.x, 0.0), 0.0, 1.0)).rgb;
    acc += texture2D(uTexture, clamp(vTex - vec2(off.x, 0.0), 0.0, 1.0)).rgb;
    acc += texture2D(uTexture, clamp(vTex + vec2(0.0, off.y), 0.0, 1.0)).rgb;
    acc += texture2D(uTexture, clamp(vTex - vec2(0.0, off.y), 0.0, 1.0)).rgb;
    acc *= 0.25;
    float lumaAcc = dot(acc, vec3(0.2126, 0.7152, 0.0722));
    float bloomFactor = clamp((lumaAcc - uThreshold) / max(1.0 - uThreshold, 0.0001), 0.0, 1.0);
    vec3 bloom = acc * bloomFactor * uBloomIntensity;
    vec3 halation = uHalationColor * bloomFactor * uHalationIntensity;
    vec3 res = rgb + bloom + halation;
    gl_FragColor = vec4(clamp(res, 0.0, 1.0) * c.a, c.a);
}
