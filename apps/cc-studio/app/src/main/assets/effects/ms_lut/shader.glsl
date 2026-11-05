#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform sampler2D uTexture;
varying vec2 vTex;
uniform float uIntensity;

void main() {
    vec4 c = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    gl_FragColor = c;
}
