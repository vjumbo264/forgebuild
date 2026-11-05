precision mediump float;
varying vec2 vTex;
uniform sampler2D uTexture;
uniform float uAmount;
void main() {
    vec4 c = texture2D(uTexture, vTex);
    float amount = clamp(uAmount, 0.0, 1.0);
    // (1 - straightRGB) * alpha = alpha - premultipliedRGB.
    // No division by tiny alpha: even the faintest edge stays well behaved.
    vec3 negative = clamp(vec3(c.a) - c.rgb, 0.0, c.a);
    gl_FragColor = vec4(mix(c.rgb, negative, amount), c.a);
}