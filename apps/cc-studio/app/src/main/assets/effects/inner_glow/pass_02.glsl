
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        varying vec2 vTex;
        uniform sampler2D uTexture;
        uniform vec2 uTexelSize,uSrcTexelSize;
        uniform float uSize;
        float alphaAt(vec2 p) {
            if(p.x < 0.0 || p.y < 0.0 || p.x > 1.0 || p.y > 1.0) return 0.0;
            return texture2D(uTexture,p).a;
        }
    
        void main() {
            float aspect = uTexelSize.y / uTexelSize.x;
            vec2 axis = vec2(1.0/aspect,0.0);
            float radius = max(uSize,0.002);
            float a = 0.0, weight = 0.0;
            for(int i=-16;i<=16;i++) {
                float t = float(i)/16.0;
                float w = exp(-4.5*t*t);
                a += alphaAt(vTex + axis*t*radius)*w;
                weight += w;
            }
            gl_FragColor = vec4(0.0,0.0,0.0,a/weight);
        }
    