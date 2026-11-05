
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTexture;
            uniform float uSilverRetention;
            uniform float uExposureCompensation;
            varying vec2 vTex;
            void main() {
                vec4 source = texture2D(uTexture, vTex);
                float amount = clamp(uSilverRetention, 0.0, 1.0);
                float stops = clamp(uExposureCompensation, -4.0, 4.0);
                if (amount == 0.0 && stops == 0.0) {
                    gl_FragColor = source;
                    return;
                }
                vec3 rgb = clamp(source.rgb / max(source.a, 1e-4), 0.0, 1.0);
                float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                vec3 muted = mix(rgb, vec3(luma), amount * 0.7);
                // f(x)=x+k*x*(1-x)*(2*x-1), k<=0.9. Its derivative stays
                // between 0.1 and 1.45: no reversed tones, clipped contrast edges
                // or amplification of one-LSB neutral noise into colour blotches.
                vec3 film = muted + (0.9 * amount) * muted * (1.0 - muted) * (2.0 * muted - 1.0);
                vec3 result = clamp(film * exp2(stops), 0.0, 1.0);
                gl_FragColor = vec4(result * source.a, source.a);
            }
        