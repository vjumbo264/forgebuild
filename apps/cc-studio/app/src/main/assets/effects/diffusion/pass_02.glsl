
        
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    
        uniform sampler2D uTexture;
        uniform vec2 uTexelSize;
        uniform float uSize;
        varying vec2 vTex;
        void main() {
            // Size is a fraction of frame height (diameter), independent of preview size.
            float radius = min(uSize * 0.5, 12.0 * uTexelSize.y);
            float sigma = max(radius / uTexelSize.y, 0.35);
            // Dense taps at ordinary sizes; wider support remains bounded on 4K frames.
            float spacing = max(1.0, sigma / 8.0);
            vec2 stepUv = vec2(uTexelSize.x, 0.0) * spacing;
            vec4 sum = vec4(0.0);
            float total = 0.0;
            for (int i = -24; i <= 24; i++) {
                float x = float(i) * spacing / sigma;
                float w = exp(-0.5 * x * x);
                sum += texture2D(uTexture, vTex + float(i) * stepUv) * w;
                total += w;
            }
            gl_FragColor = sum / max(total, 1e-4);
        }
    