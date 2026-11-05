#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform vec2 uTexelSize;
uniform float uIntensity;
uniform float uSize;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));

    vec2 grainCoord = floor(vTex / (uTexelSize * max(uSize, 0.5)));
    float noise = hash(grainCoord) * 2.0 - 1.0;

    // Weight grain mostly to midtones (real film grain response)
    float lumaMask = 1.0 - abs(luma - 0.5) * 1.6;
    lumaMask = clamp(lumaMask, 0.1, 1.0);

    rgb += vec3(noise * uIntensity * lumaMask);
    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0) * c.a, c.a);
}
