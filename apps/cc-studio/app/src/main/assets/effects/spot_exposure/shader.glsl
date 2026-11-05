
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform sampler2D uTexture;
        uniform mat3 uLayerToLocal;
        uniform mat3 uLocalToUv;
        uniform vec2 uSrcTexelSize;
        uniform float uStops;
        uniform float uFalloff;
        varying vec2 vTex;

        // Fraction of the transition at which its strength has reached one half.
        float exposureRamp(float t) {
            if (t <= 0.0) return 0.0;
            if (t >= 1.0) return 1.0;
            float mid = smoothstep(0.0, 1.0, clamp(uFalloff, 0.01, 0.99));
            float exponent = log(0.5) / log(mid);
            return pow(smoothstep(0.0, 1.0, t), exponent);
        }
        vec2 exposureUv(vec2 p) {
            vec3 q = uLocalToUv * vec3(p, 1.0);
            return q.xy / (abs(q.z) < 0.001 ? 1.0 : q.z);
        }
        // Measure the axes at the frame centre, in target pixels: a radius is a
        // fraction of frame height, and Aspect=1 gives a circle on an unrotated frame.
        float exposureAspect() {
            vec2 c = exposureUv(vec2(0.5));
            vec2 px = max(uSrcTexelSize, vec2(0.00001));
            float x = length((exposureUv(vec2(0.51, 0.5)) - c) / px);
            float y = length((exposureUv(vec2(0.5, 0.51)) - c) / px);
            return clamp(x / max(y, 0.001), 0.01, 100.0);
        }
        vec4 expose(vec4 base, float mask) {
            // Multiplying premultiplied RGB is equivalent to unpremultiply, expose,
            // clamp and premultiply. Never create coverage or colour in transparent pixels.
            float gain = exp2(uStops * clamp(mask, 0.0, 1.0));
            return vec4(clamp(base.rgb * gain, vec3(0.0), vec3(base.a)), base.a);
        }
    
            uniform float uCenterX;
            uniform float uCenterY;
            uniform float uRadius;
            uniform float uAspect;
            uniform float uSpread;
            uniform float uInvert;
            void main() {
                vec4 base = texture2D(uTexture, vTex);
                vec3 q = uLayerToLocal * vec3(vTex, 1.0);
                if (abs(q.z) < 0.001 || abs(uStops) < 0.00001) { gl_FragColor = base; return; }
                vec2 p = q.xy / q.z - vec2(0.5 + uCenterX, 0.5 + uCenterY);
                p.x *= exposureAspect() / max(uAspect, 0.1);
                float distance = length(p) / max(uRadius, 0.005);
                float spread = clamp(uSpread, 0.0, 1.0);
                float mask = spread >= 0.9999 ? 1.0 - step(1.0, distance)
                    : 1.0 - exposureRamp((distance - spread) / max(1.0 - spread, 0.0001));
                if (uInvert >= 0.5) mask = 1.0 - mask;
                gl_FragColor = expose(base, mask);
            }
        