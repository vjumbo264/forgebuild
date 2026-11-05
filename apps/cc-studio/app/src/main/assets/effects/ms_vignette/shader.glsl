#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform vec2 uTexelSize;
uniform float uAmount;
uniform float uMidpoint;
uniform float uFeather;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);

    float aspect = uTexelSize.y / max(uTexelSize.x, 0.0001);
    vec2 p = vTex - vec2(0.5);
    p.x *= aspect;

    float dist = length(p);
    float vig = smoothstep(uMidpoint, uMidpoint + max(uFeather, 0.0001), dist);
    rgb *= (1.0 - vig * uAmount);

    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0) * c.a, c.a);
}
