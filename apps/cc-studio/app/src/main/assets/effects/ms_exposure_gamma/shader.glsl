#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uExposure;
uniform float uGamma;
uniform float uOffset;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);

    // Photographic exposure EV multiplier
    rgb *= pow(2.0, uExposure);
    rgb += vec3(uOffset);
    rgb = max(vec3(0.0), rgb);

    // Gamma power curve
    rgb = pow(rgb, vec3(1.0 / max(uGamma, 0.0001)));

    rgb = clamp(rgb, 0.0, 1.0);
    gl_FragColor = vec4(rgb * c.a, c.a);
}
