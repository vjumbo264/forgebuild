#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec2 vTex;
uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform float uAmount;
uniform float uRadius;
uniform float uThreshold;

void main() {
    float aspect = uTexelSize.y / max(uTexelSize.x, 0.0001);
    vec2 d = uTexelSize * max(uRadius, 1.0);
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec4 edges = texture2D(uTexture, clamp(vTex + vec2( d.x, 0.0), 0.0, 1.0))
               + texture2D(uTexture, clamp(vTex + vec2(-d.x, 0.0), 0.0, 1.0))
               + texture2D(uTexture, clamp(vTex + vec2(0.0,  d.y), 0.0, 1.0))
               + texture2D(uTexture, clamp(vTex + vec2(0.0, -d.y), 0.0, 1.0));
    vec4 corners = texture2D(uTexture, clamp(vTex + d, 0.0, 1.0))
                 + texture2D(uTexture, clamp(vTex - d, 0.0, 1.0))
                 + texture2D(uTexture, clamp(vTex + vec2( d.x, -d.y), 0.0, 1.0))
                 + texture2D(uTexture, clamp(vTex + vec2(-d.x,  d.y), 0.0, 1.0));
    vec4 detail = (20.0 * c - 4.0 * edges - corners) / 6.0;

    vec4 diff = detail;
    vec4 ad = abs(diff) * 255.0;
    float m = max(max(ad.r, ad.g), max(ad.b, ad.a));
    float gate = clamp(m - uThreshold + 1.0, 0.0, 1.0);
    vec4 o = c + diff * (uAmount / 100.0) * gate;

    float outA = clamp(o.a, 0.0, 1.0);
    vec3 straight = clamp(o.rgb / max(o.a, 1e-4), 0.0, 1.0);
    gl_FragColor = o.a > 0.0 ? vec4(straight * outA, outA) : vec4(0.0);
}
