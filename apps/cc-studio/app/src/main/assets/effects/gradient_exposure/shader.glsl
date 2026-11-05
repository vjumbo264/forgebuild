
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
    
            uniform float uX1;
            uniform float uY1;
            uniform float uX2;
            uniform float uY2;
            void main() {
                vec4 base = texture2D(uTexture, vTex);
                vec3 q = uLayerToLocal * vec3(vTex, 1.0);
                if (abs(q.z) < 0.001 || abs(uStops) < 0.00001) { gl_FragColor = base; return; }
                vec2 metric = vec2(exposureAspect(), 1.0);
                vec2 start = vec2(0.5 + uX1, 0.5 + uY1);
                vec2 axis = (vec2(uX2, uY2) - vec2(uX1, uY1)) * metric;
                float length2 = dot(axis, axis);
                // Coincident handles do not define a direction: pass through safely.
                if (length2 < 0.000001) { gl_FragColor = base; return; }
                float t = dot((q.xy / q.z - start) * metric, axis) / length2;
                gl_FragColor = expose(base, 1.0 - exposureRamp(t));
            }
        