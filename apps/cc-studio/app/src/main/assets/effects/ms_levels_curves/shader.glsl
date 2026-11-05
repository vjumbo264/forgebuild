#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uBlackPoint;
uniform float uWhitePoint;
uniform float uCurveStrength;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    vec3 rgb = c.rgb / max(c.a, 0.0001);

    // Levels remapping
    rgb = clamp((rgb - uBlackPoint) / max(uWhitePoint - uBlackPoint, 0.0001), 0.0, 1.0);

    // Smooth cubic Hermite S-curve
    vec3 scurve = rgb * rgb * (3.0 - 2.0 * rgb);
    if (uCurveStrength > 0.0) {
        rgb = mix(rgb, scurve, uCurveStrength);
    } else {
        vec3 invScurve = sqrt(max(vec3(0.0), rgb));
        rgb = mix(rgb, invScurve, -uCurveStrength);
    }

    gl_FragColor = vec4(rgb * c.a, c.a);
}
