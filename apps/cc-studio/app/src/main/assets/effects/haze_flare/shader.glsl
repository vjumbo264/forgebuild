
        
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
    
        uniform float uSpillage;
        vec4 sourceAt(vec2 uv) {
            vec4 c = lightAt(uv);
            vec2 p = localAt(uv);
            float edgeDistance = max(0.0, min(min(p.x, 1.0 - p.x), min(p.y, 1.0 - p.y)));
            float spill = clamp(uSpillage, 0.0, 1.0);
            float band = 0.015 + 0.65 * spill;
            float edgeLight = 1.0 - smoothstep(0.0, band, edgeDistance);
            float admission = mix(edgeLight, 1.0, spill * spill * spill * spill);
            // Weight bright pixels without a hard threshold. Alpha has already attenuated
            // c.rgb; use straight RGB only for this brightness measurement, not twice.
            vec3 rgb = c.rgb / max(c.a, 0.001);
            float brightness = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
            return vec4(c.rgb * brightness * admission, 0.0);
        }
        void main() {
            if (!hasFrame()) { gl_FragColor = vec4(0.0); return; }
            vec2 d = 0.5 * uSrcTexelSize;
            gl_FragColor = 0.25 * (
                sourceAt(vTex + vec2(-d.x, -d.y)) + sourceAt(vTex + vec2(d.x, -d.y)) +
                sourceAt(vTex + vec2(-d.x, d.y)) + sourceAt(vTex + vec2(d.x, d.y)));
        }
    