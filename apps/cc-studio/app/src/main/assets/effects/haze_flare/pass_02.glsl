
        
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    
        varying vec2 vTex;
        
        uniform mat3 uLayerToLocal;
        uniform mat3 uLocalToUv;
        vec2 localAt(vec2 uv) {
            vec3 q = uLayerToLocal * vec3(uv, 1.0);
            return q.xy / (abs(q.z) < 0.001 ? 0.001 : q.z);
        }
        vec2 uvAt(vec2 p) {
            vec3 q = uLocalToUv * vec3(p, 1.0);
            if (abs(q.z) < 0.001) return vec2(-2.0);
            return q.xy / q.z;
        }
        bool hasFrame() {
            return abs((uLayerToLocal * vec3(vTex, 1.0)).z) >= 0.001;
        }
    
        
        uniform sampler2D uTexture;
        uniform vec2 uSrcTexelSize;
        vec4 lightAt(vec2 uv) {
            vec2 edge = min(uv, 1.0 - uv) / uSrcTexelSize + 0.5;
            float inside = clamp(edge.x, 0.0, 1.0) * clamp(edge.y, 0.0, 1.0);
            return texture2D(uTexture, uv) * inside;
        }
    
        uniform float uReach;
        uniform float uSoftness;
        void main() {
            if (!hasFrame()) { gl_FragColor = vec4(0.0); return; }
            vec2 p = localAt(vTex);
            float soft = uSoftness * uSoftness;
            vec2 axis = vec2(0.5 * uReach + 0.12 * soft, 0.0);
            vec4 sum = vec4(0.0);
            float weight = 0.0;
            for (int i = -8; i <= 8; i++) {
                float x = float(i) / 8.0;
                float w = exp(-3.0 * x * x);
                sum += lightAt(uvAt(p + axis * (x * 1.0))) * w;
                weight += w;
            }
            gl_FragColor = sum / weight;
        }
        