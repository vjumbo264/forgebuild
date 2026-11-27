#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
precision mediump sampler2D;
varying vec2 vTex;
uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform float uUsmRadius;
        vec4 blur(vec2 stepVec) {
            // AE's Radius parameter corresponds to Gaussian standard deviation sigma
            float sigma = max(uUsmRadius, 0.05);
            float tapRadius = clamp(ceil(3.0 * sigma), 1.0, 300.0);

            float twoSigma2 = 2.0 * sigma * sigma;

            // Center tap (x = 0)
            vec4 accum = texture2D(uTexture, vTex);
            float weightSum = 1.0;



            // Symmetric 1D Gaussian convolution using bilinear hardware fetch optimization
            // Pairs adjacent taps (2i-1, 2i) into a single bilinear texture lookup with exact analytical weight
            for (int i = 0; i < 150; i++) {
                float x1 = float(i * 2 + 1);
                if (x1 > tapRadius) break;


                float w1 = exp(-(x1 * x1) / twoSigma2);

                if (x1 + 1.0 <= tapRadius) {
                    float x2 = x1 + 1.0;
                    float w2 = exp(-(x2 * x2) / twoSigma2);
                    float wPair = w1 + w2;
                    float offset = (x1 * w1 + x2 * w2) / max(wPair, 1e-10);

                    vec2 uvPos = clamp(vTex + stepVec * offset, 0.0, 1.0);
                    vec2 uvNeg = clamp(vTex - stepVec * offset, 0.0, 1.0);

                    accum += (texture2D(uTexture, uvPos) + texture2D(uTexture, uvNeg)) * wPair;
                    weightSum += 2.0 * wPair;
                } else {
                    vec2 uvPos = clamp(vTex + stepVec * x1, 0.0, 1.0);
                    vec2 uvNeg = clamp(vTex - stepVec * x1, 0.0, 1.0);

                    accum += (texture2D(uTexture, uvPos) + texture2D(uTexture, uvNeg)) * w1;
                    weightSum += 2.0 * w1;
                }
            }

            return accum / weightSum;
        }
void main(){ gl_FragColor=blur(vec2(uTexelSize.x,0.0)); }
