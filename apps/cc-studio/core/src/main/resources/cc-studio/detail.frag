#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
precision mediump sampler2D;
varying vec2 vTex;
uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform float uClarityOn;
uniform float uStructureOn;
uniform float uClarity;
uniform float uStructure;
vec3 straight(vec4 p){ return p.rgb / max(p.a,0.0001); }
        vec3 srgbToLinear(vec3 c) {
            return pow(max(c, vec3(0.0)), vec3(2.2));
        }

        vec3 linearToSrgb(vec3 c) {
            return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
        }

        float getLuminance(vec3 lin) {
            return dot(lin, vec3(0.2126, 0.7152, 0.0722));
        }

        // Interleaved Gradient Noise to eliminate concentric sampling rings
        float ign(vec2 pixel) {
            return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
        }

        void main() {
            float clarity = uClarityOn >= 0.5 ? uClarity : 0.0;
            float structure = uStructureOn >= 0.5 ? uStructure : 0.0;
            vec4 src = texture2D(uTexture, vTex);

            if (abs(clarity) < 0.01 && abs(structure) < 0.01) {
                gl_FragColor = src;
                return;
            }

            vec2 texel = uTexelSize;
            vec3 linCenter = srgbToLinear(straight(src));
            float lumCenter = getLuminance(linCenter);
            float percCenter = sqrt(clamp(lumCenter, 0.0, 1.0));

            float fineSum = 0.0;
            float fineWeight = 0.0;
            float coarseSum = 0.0;
            float coarseWeight = 0.0;

            const float STRUCT_RADIUS = 4.5;
            const float CLARITY_RADIUS = 32.0;
            const float SIGMA_FINE = 0.14;
            const float SIGMA_COARSE = 0.25;
            float sigmaFine2 = 2.0 * SIGMA_FINE * SIGMA_FINE;
            float sigmaCoarse2 = 2.0 * SIGMA_COARSE * SIGMA_COARSE;

            float rotAngle = ign(gl_FragCoord.xy) * 6.2831853;
            float cA = cos(rotAngle);
            float sA = sin(rotAngle);
            mat2 rotMat = mat2(cA, -sA, sA, cA);

            const int TAPS = 32;
            for (int i = 0; i < 32; i++) {
                float fi = float(i) + 0.5;
                float rNorm = sqrt(fi / float(TAPS));
                float theta = fi * 2.39996323; // Golden angle
                vec2 dir = rotMat * vec2(cos(theta), sin(theta)) * rNorm;
                float wSpatial = exp(-2.1 * rNorm * rNorm);

                // Fine Bilateral Base (Structure micro-texture)
                vec2 uvFine = clamp(vTex + dir * STRUCT_RADIUS * texel, 0.0, 1.0);
                float percFine = sqrt(clamp(getLuminance(srgbToLinear(straight(texture2D(uTexture, uvFine)))), 0.0, 1.0));
                float dFine = percFine - percCenter;
                float wFine = wSpatial * exp(-(dFine * dFine) / sigmaFine2);
                fineSum += percFine * wFine;
                fineWeight += wFine;

                // Coarse Bilateral Base (Clarity macro local contrast)
                vec2 uvCoarse = clamp(vTex + dir * CLARITY_RADIUS * texel, 0.0, 1.0);
                float percCoarse = sqrt(clamp(getLuminance(srgbToLinear(straight(texture2D(uTexture, uvCoarse)))), 0.0, 1.0));
                float dCoarse = percCoarse - percCenter;
                float wCoarse = wSpatial * exp(-(dCoarse * dCoarse) / sigmaCoarse2);
                coarseSum += percCoarse * wCoarse;
                coarseWeight += wCoarse;
            }

            float baseFine = fineSum / max(fineWeight, 1e-5);
            float baseCoarse = coarseSum / max(coarseWeight, 1e-5);

            // Orthogonal frequency detail bands
            float structBand = percCenter - baseFine;
            float clarityBand = baseFine - baseCoarse;

            // Soft-knee compression prevents harsh edge halo clipping
            structBand = structBand / (1.0 + abs(structBand) * 3.0);
            clarityBand = clarityBand / (1.0 + abs(clarityBand) * 1.8);

            // Midtone bell-curve weighting for Clarity (protects shadows & specular highlights)
            float midtoneWeight = pow(clamp(4.0 * percCenter * (1.0 - percCenter), 0.0, 1.0), 0.85)
                                * (1.0 - smoothstep(0.72, 1.0, percCenter));

            float structDelta = structBand * (structure / 100.0) * 1.75;
            float clarityDelta = clarityBand * (clarity / 100.0) * 1.50 * midtoneWeight;

            float newPerc = clamp(percCenter + structDelta + clarityDelta, 0.0, 1.0);
            float newLum = newPerc * newPerc;

            vec3 outLinear = linCenter * (newLum / max(lumCenter, 1e-5));
            vec3 outSrgb = linearToSrgb(outLinear);

            gl_FragColor = vec4(clamp(outSrgb, 0.0, 1.0) * src.a, src.a);
        }
