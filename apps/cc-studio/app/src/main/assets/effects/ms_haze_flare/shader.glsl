#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform vec2 uTexelSize;
uniform float uIntensity;
uniform vec3 uColor;
uniform float uCenterX;
uniform float uCenterY;
uniform float uSpread;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);

    float aspect = uTexelSize.y / max(uTexelSize.x, 0.0001);
    vec2 p = vTex - vec2(uCenterX, uCenterY);
    p.x *= aspect;

    float dist = length(p);
    float haze = exp(-dist / max(uSpread, 0.0001));

    vec3 result = rgb + uColor * haze * uIntensity;
    gl_FragColor = vec4(clamp(result, 0.0, 1.0) * c.a, c.a);
}
