
        
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    
        varying vec2 vTex;
        
        uniform sampler2D uTexture;
        uniform vec2 uSrcTexelSize;
        vec4 lightAt(vec2 uv) {
            vec2 edge = min(uv, 1.0 - uv) / uSrcTexelSize + 0.5;
            float inside = clamp(edge.x, 0.0, 1.0) * clamp(edge.y, 0.0, 1.0);
            return texture2D(uTexture, uv) * inside;
        }
    
        void main() {
            vec2 d = 0.5 * uSrcTexelSize;
            gl_FragColor = 0.25 * (
                lightAt(vTex + vec2(-d.x, -d.y)) + lightAt(vTex + vec2(d.x, -d.y)) +
                lightAt(vTex + vec2(-d.x, d.y)) + lightAt(vTex + vec2(d.x, d.y)));
        }
    