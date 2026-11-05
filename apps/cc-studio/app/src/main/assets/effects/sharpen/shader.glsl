
        
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    
        uniform sampler2D uTexture;
        uniform vec2 uTexelSize;
        uniform float uAmount;
        varying vec2 vTex;
        void main() {
            float aspect = uTexelSize.y / uTexelSize.x;   // width / height
            // One pixel at 1080p, square on any aspect — and never under one texel of this
            // target: in a reduced preview the 1080p pixel lands inside a texel, and taps
            // that read the same bilinear sample as the centre would sharpen nothing.
            vec2 d = max(vec2(1.0 / aspect, 1.0) * (1.0 / 1080.0), uTexelSize);
            vec4 c = texture2D(uTexture, vTex);
            vec4 edges = texture2D(uTexture, vTex + vec2( d.x, 0.0))
                       + texture2D(uTexture, vTex + vec2(-d.x, 0.0))
                       + texture2D(uTexture, vTex + vec2(0.0,  d.y))
                       + texture2D(uTexture, vTex + vec2(0.0, -d.y));
            vec4 corners = texture2D(uTexture, vTex + d)
                         + texture2D(uTexture, vTex - d)
                         + texture2D(uTexture, vTex + vec2( d.x, -d.y))
                         + texture2D(uTexture, vTex + vec2(-d.x,  d.y));
            vec4 detail = (20.0 * c - 4.0 * edges - corners) / 6.0;
            vec4 o = c + detail * (uAmount / 100.0);
            
            float outA = clamp(o.a, 0.0, 1.0);
            vec3 straight = clamp(o.rgb / max(o.a, 1e-4), 0.0, 1.0);
            gl_FragColor = o.a > 0.0 ? vec4(straight * outA, outA) : vec4(0.0);
    
        }
    