
        
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    
        uniform sampler2D uInput;
        uniform vec2 uSrcTexelSize;
        uniform vec2 uTexelSize;
        uniform float uHighlightBias;
        uniform float uHighlightsOnly;
        varying vec2 vTex;
        
        float highlightMask(vec3 rgb) {
            float brightness = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
            float threshold = 0.65 + clamp(uHighlightBias, -1.0, 1.0) * 0.3;
            return smoothstep(threshold - 0.15, threshold + 0.15, brightness);
        }
    
        vec4 source(vec2 uv) {
            vec4 c = texture2D(uInput, uv);
            
            vec3 rgb = clamp(c.rgb / max(c.a, 1e-4), 0.0, 1.0);
            c *= mix(1.0, highlightMask(rgb), clamp(uHighlightsOnly, 0.0, 1.0));
            
            return c;
        }
        void main() {
            // This pass always reads the FULL effect input, even after the soft chain.
            // Destination is half size, so half a full-size texel is a quarter target texel.
            vec2 o = uTexelSize * 0.25;
            gl_FragColor = (source(vTex + vec2(-o.x,-o.y))
                + source(vTex + vec2(o.x,-o.y))
                + source(vTex + vec2(-o.x,o.y))
                + source(vTex + vec2(o.x,o.y))) * 0.25;
        }
    