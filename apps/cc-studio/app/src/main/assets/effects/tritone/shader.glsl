
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTexture;
            uniform float uShadowR;
            uniform float uShadowG;
            uniform float uShadowB;
            uniform float uMidtoneR;
            uniform float uMidtoneG;
            uniform float uMidtoneB;
            uniform float uHighlightR;
            uniform float uHighlightG;
            uniform float uHighlightB;
            uniform float uBlendOriginal;
            varying vec2 vTex;
            void main() {
                vec4 source = texture2D(uTexture, vTex);
                float blend = clamp(uBlendOriginal, 0.0, 1.0);
                if (blend == 1.0) {
                    gl_FragColor = source;
                    return;
                }
                vec3 rgb = clamp(source.rgb / max(source.a, 1e-4), 0.0, 1.0);
                float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                vec3 shadows = clamp(vec3(uShadowR,uShadowG,uShadowB), 0.0, 1.0);
                vec3 midtones = clamp(vec3(uMidtoneR,uMidtoneG,uMidtoneB), 0.0, 1.0);
                vec3 highlights = clamp(vec3(uHighlightR,uHighlightG,uHighlightB), 0.0, 1.0);
                // The two interpolation slopes meet at zero at the middle anchor:
                // no threshold edge, abrupt colour switch, or hue/chroma division.
                vec3 mapped = luma < 0.5
                    ? mix(shadows, midtones, smoothstep(0.0, 0.5, luma))
                    : mix(midtones, highlights, smoothstep(0.5, 1.0, luma));
                gl_FragColor = vec4(mix(mapped * source.a, source.rgb, blend), source.a);
            }
        