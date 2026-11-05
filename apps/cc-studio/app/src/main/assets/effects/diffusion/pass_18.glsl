
        
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    
        uniform sampler2D uTexture; // highlight blur
        uniform sampler2D uPrev;    // image blur
        uniform sampler2D uInput;   // input of this effect, not the entire stack's original
        uniform float uSize;
        uniform float uGrade;
        uniform float uGlow;
        uniform float uHighlightsOnly;
        uniform float uHighlightBias;
        uniform float uExposureCompensation;
        uniform float uColorR;
        uniform float uColorG;
        uniform float uColorB;
        varying vec2 vTex;
        
        float highlightMask(vec3 rgb) {
            float brightness = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
            float threshold = 0.65 + clamp(uHighlightBias, -1.0, 1.0) * 0.3;
            return smoothstep(threshold - 0.15, threshold + 0.15, brightness);
        }
    
        void main() {
            vec4 base = texture2D(uInput, vTex);
            vec4 soft = texture2D(uPrev, vTex);
            vec4 light = texture2D(uTexture, vTex);
            // Exact passthrough at zero, with a smooth fade through the reduced-size floor.
            float spread = smoothstep(0.0, 0.004, clamp(uSize, 0.0, 0.12));
            vec3 rgb = clamp(base.rgb / max(base.a, 1e-4), 0.0, 1.0);
            float mask = mix(1.0, highlightMask(rgb), clamp(uHighlightsOnly, 0.0, 1.0));
            float grade = clamp(uGrade, 0.0, 1.0) * mask * spread;
            vec4 body = mix(base, soft, grade);
            vec3 tint = clamp(vec3(uColorR,uColorG,uColorB), 0.0, 1.0);
            // Smooth screen-like rolloff instead of clipping a summed halo to white.
            vec3 halo = vec3(1.0) - exp(-max(light.rgb, vec3(0.0)) * tint
                * clamp(uGlow, 0.0, 2.0) * spread * 2.0);
            float coverage = max(halo.r, max(halo.g, halo.b));
            float alpha = body.a + coverage * (1.0 - body.a);
            vec3 result = body.rgb + halo * (1.0 - body.rgb);
            result = min(result * exp2(clamp(uExposureCompensation, -4.0, 4.0)), vec3(alpha));
            gl_FragColor = vec4(max(result, vec3(0.0)), alpha);
        }
    