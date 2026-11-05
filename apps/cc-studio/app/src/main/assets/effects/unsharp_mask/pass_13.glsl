
        
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    
        uniform sampler2D uTexture;     // full size, blurred along x
        uniform sampler2D uPrev;        // the 1/4 rung, finished
        uniform sampler2D uInput;       // the stack's state as this effect received it
        uniform float uAmount;          // percent, as AE
        uniform float uThreshold;       // 0-255 levels, as AE
        
        uniform vec2 uTexelSize;
        uniform float uRadius;          // px at 1080p = the Gaussian's sigma
        varying vec2 vTex;

        float sigmaHere() { return uRadius * (1.0 / 1080.0) / uTexelSize.y; }
        bool needed(float s) { return true; }
        float handOver(float s) { return smoothstep(4.0, 5.3, s); }

        vec4 gauss(vec2 axis, float s) {
            float sigma = sqrt(max(s * s - 0.0, 0.0));
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
            float h = handOver(s);
            vec4 own = vec4(0.0);
            if (h < 1.0) own = gauss(vec2(0.0, uTexelSize.y), s);
            vec4 far = vec4(0.0);
            if (h > 0.0) far = coarser();
            vec4 blur = mix(own, far, h);

            vec4 base = texture2D(uInput, vTex);
            vec4 diff = base - blur;
            vec4 ad = abs(diff) * 255.0;
            float m = max(max(ad.r, ad.g), max(ad.b, ad.a));
            float gate = clamp(m - uThreshold + 1.0, 0.0, 1.0);
            vec4 o = base + diff * (uAmount / 100.0) * gate;
            
            float outA = clamp(o.a, 0.0, 1.0);
            vec3 straight = clamp(o.rgb / max(o.a, 1e-4), 0.0, 1.0);
            gl_FragColor = o.a > 0.0 ? vec4(straight * outA, outA) : vec4(0.0);
    
        }
    