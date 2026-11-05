
        
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
    
        uniform sampler2D uInput;
        uniform float uStrength;
        uniform float uExposure;
        uniform float uReflectionExposure;
        uniform float uReflection;
        uniform float uReflectionAxis;
        uniform float uMatteBoxSize;
        uniform float uMatteBoxShade;
        uniform float uTintR;
        uniform float uTintG;
        uniform float uTintB;
        uniform float uPreserveAlpha;
        void main() {
            vec4 base = texture2D(uInput, vTex);
            if (uStrength <= 0.0 || !hasFrame()) { gl_FragColor = base; return; }
            vec2 p = localAt(vTex);
            vec3 direct = lightAt(vTex).rgb * exp2(uExposure);
            vec3 reflected = vec3(0.0);
            if (uReflection >= 0.5) {
                // A Haze/Flare reflection has one mirror axis.  Axis 0 mirrors Y, sending
                // light between the top and bottom halves; axis 1 mirrors X, sending it
                // between the left and right halves.  Mirroring both coordinates at once
                // produces a diagonal/opposite-corner ghost, which is not either optical
                // layout and was the old implementation's accidental behaviour.
                vec2 reflectionPoint = p;
                if (uReflectionAxis >= 0.5) reflectionPoint.x = 1.0 - reflectionPoint.x;
                else reflectionPoint.y = 1.0 - reflectionPoint.y;
                reflected = lightAt(uvAt(reflectionPoint)).rgb * exp2(uReflectionExposure);
                reflected *= vec3(uTintR, uTintG, uTintB);
            }
            // A virtual matte box shades only the added light, never the source picture.
            float edge = max(abs(2.0 * p.x - 1.0), abs(2.0 * p.y - 1.0));
            float aperture = max(uMatteBoxSize, 0.001);
            float blocked = smoothstep(0.65 * aperture, aperture, edge);
            float transmission = 1.0 - clamp(uMatteBoxShade, 0.0, 1.0) * blocked;
            vec3 energy = max(vec3(0.0), (direct + reflected) * uStrength * transmission);
            // Smooth rolloff avoids clipping a flat plateau into the middle of the haze.
            vec3 light = 1.0 - exp(-energy);
            float coverage = max(light.r, max(light.g, light.b));
            if (coverage <= 0.0) { gl_FragColor = base; return; }
            float keepAlpha = step(0.5, uPreserveAlpha);
            float cover = mix(1.0, base.a, keepAlpha);
            vec3 rgb = base.rgb + (vec3(cover) - base.rgb) * light;
            float alpha = base.a + coverage * (1.0 - base.a) * (1.0 - keepAlpha);
            // Sub-LSB, deterministic dither on the added light only. Fade it away both
            // with Strength and the haze tail, keeping zero/black and AA edges clean.
            vec3 hash = fract(vec3(gl_FragCoord.xyx) * 0.1031);
            hash += dot(hash, hash.yzx + 33.33);
            float d = (fract((hash.x + hash.y) * hash.z) - 0.5) / 255.0;
            rgb += d * min(1.0, coverage * 64.0) * cover;
            gl_FragColor = vec4(clamp(rgb, 0.0, alpha), alpha);
        }
    