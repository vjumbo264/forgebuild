
        
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    
        uniform sampler2D uTexture;     // this rung, blurred along x
        uniform sampler2D uPrev;        // the next coarser rung, finished
        
        uniform vec2 uTexelSize;
        uniform float uRadius;          // px at 1080p = the Gaussian's sigma
        varying vec2 vTex;

        float sigmaHere() { return uRadius * (1.0 / 1080.0) / uTexelSize.y; }
        bool needed(float s) { return s * 4.0 > 4.0; }
        float handOver(float s) { return smoothstep(4.0, 5.3, s); }

        vec4 gauss(vec2 axis, float s) {
            float sigma = sqrt(max(s * s - 0.58, 0.0));
            vec4 c = texture2D(uTexture, vTex);
            if (sigma < 0.05) return c;
            float sp = max(1.0, 3.0 * sigma / 16.0);
            float k = -0.5 / (sigma * sigma);
            vec4 sum = c;
            float wsum = 1.0;
            for (int i = 0; i < 8; i++) {
                float a = (2.0 * float(i) + 1.0) * sp;
                float b = a + sp;
                float wa = exp(k * a * a);
                float wb = exp(k * b * b);
                float w = wa + wb;
                float o = w > 1e-6 ? (a * wa + b * wb) / w : a;
                vec2 off = axis * o;
                sum += (texture2D(uTexture, vTex + off) + texture2D(uTexture, vTex - off)) * w;
                wsum += 2.0 * w;
            }
            return sum / wsum;
        }
        
        
        vec4 coarser() {
            vec2 t = uTexelSize * 4.0;
            vec4 s = texture2D(uPrev, vTex + vec2(-t.x,  t.y))
                   + texture2D(uPrev, vTex + vec2( 0.0,  t.y)) * 2.0
                   + texture2D(uPrev, vTex + vec2( t.x,  t.y))
                   + texture2D(uPrev, vTex + vec2(-t.x,  0.0)) * 2.0
                   + texture2D(uPrev, vTex)                    * 4.0
                   + texture2D(uPrev, vTex + vec2( t.x,  0.0)) * 2.0
                   + texture2D(uPrev, vTex + vec2(-t.x, -t.y))
                   + texture2D(uPrev, vTex + vec2( 0.0, -t.y)) * 2.0
                   + texture2D(uPrev, vTex + vec2( t.x, -t.y));
            return s * 0.0625;
        }
    
        void main() {
            float s = sigmaHere();
            if (!needed(s)) { gl_FragColor = vec4(0.0); return; }
            float h = handOver(s);
            vec4 own = vec4(0.0);
            if (h < 1.0) own = gauss(vec2(0.0, uTexelSize.y), s);
            vec4 far = vec4(0.0);
            if (h > 0.0) far = coarser();
            gl_FragColor = mix(own, far, h);
        }
        