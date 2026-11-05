
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
            vec2 d = uSrcTexelSize * 0.5;
            float a = (alphaAt(vTex + vec2(-d.x,-d.y)) + alphaAt(vTex + vec2(d.x,-d.y)) +
                       alphaAt(vTex + vec2(-d.x,d.y)) + alphaAt(vTex + vec2(d.x,d.y))) * 0.25;
            gl_FragColor = vec4(0.0,0.0,0.0,a);
        }
    