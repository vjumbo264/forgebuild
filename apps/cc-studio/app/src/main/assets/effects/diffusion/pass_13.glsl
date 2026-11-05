
        
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
    
        uniform sampler2D uPrev;
        uniform vec2 uTexelSize;
        varying vec2 vTex;
        void main() {
            vec2 o = uTexelSize * 0.25;
            gl_FragColor = (texture2D(uPrev, vTex + vec2(-o.x,-o.y))
                + texture2D(uPrev, vTex + vec2(o.x,-o.y))
                + texture2D(uPrev, vTex + vec2(-o.x,o.y))
                + texture2D(uPrev, vTex + vec2(o.x,o.y))) * 0.25;
        }
    