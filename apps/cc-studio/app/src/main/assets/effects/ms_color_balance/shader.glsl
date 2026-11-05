#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uShadowsR;
uniform float uShadowsG;
uniform float uShadowsB;
uniform float uMidtonesR;
uniform float uMidtonesG;
uniform float uMidtonesB;
uniform float uHighlightsR;
uniform float uHighlightsG;
uniform float uHighlightsB;
uniform float uPreserveLuma;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));

    // Weights across luminance spectrum
    float wShadow = clamp(1.0 - luma * 2.0, 0.0, 1.0);
    float wHighlight = clamp(luma * 2.0 - 1.0, 0.0, 1.0);
    float wMid = clamp(1.0 - abs(luma - 0.5) * 2.0, 0.0, 1.0);

    vec3 offset = vec3(uShadowsR, uShadowsG, uShadowsB) * wShadow * 0.35 +
                  vec3(uMidtonesR, uMidtonesG, uMidtonesB) * wMid * 0.35 +
                  vec3(uHighlightsR, uHighlightsG, uHighlightsB) * wHighlight * 0.35;

    vec3 outRgb = clamp(rgb + offset, 0.0, 1.0);

    if (uPreserveLuma > 0.5) {
        float newLuma = dot(outRgb, vec3(0.2126, 0.7152, 0.0722));
        outRgb *= luma / max(newLuma, 0.0001);
    }

    gl_FragColor = vec4(clamp(outRgb, 0.0, 1.0) * c.a, c.a);
}
