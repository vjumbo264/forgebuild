
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
    
                uniform sampler2D uInput;
                uniform float uOpacity,uFalloff,uColorR,uColorG,uColorB,uBlendMode;
                void main() {
                    vec4 base = texture2D(uInput,vTex);
                    if(uSize <= 0.0 || uOpacity <= 0.0 || base.a <= 0.00001) {
                        gl_FragColor = base; return;
                    }
                    float edge = clamp((base.a - texture2D(uTexture,vTex).a)/base.a,0.0,1.0);
                    float amount = pow(max(edge,0.00001),uFalloff)*step(0.00001,edge)*
                                   uOpacity*min(uSize/0.002,1.0);
                    vec3 rgb = base.rgb/base.a;
                    vec3 color = vec3(uColorR,uColorG,uColorB);
                    vec3 lit = uBlendMode > 1.5 ? color : uBlendMode > 0.5 ?
                               min(rgb + color,vec3(1.0)) : 1.0-(1.0-rgb)*(1.0-color);
                    gl_FragColor = vec4(mix(rgb,lit,amount)*base.a,base.a);
                }
            