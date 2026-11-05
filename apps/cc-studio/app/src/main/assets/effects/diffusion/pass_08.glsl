
        
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    
        uniform sampler2D uTexture;
        uniform sampler2D uPrev;
        uniform float uSize;
        uniform vec2 uTexelSize;
        varying vec2 vTex;
        void main() {
            // Both branches overlap; no abrupt switch in the blur radius.
            // The wide chain is used only once the desired blur exceeds its sampling
            // floor. Low-resolution previews stay on the quarter branch longer.
            float sigma = uSize * 0.5 / uTexelSize.y;
            float wide = smoothstep(6.0, 12.0, sigma);
            gl_FragColor = mix(texture2D(uPrev, vTex), texture2D(uTexture, vTex), wide);
        }
    