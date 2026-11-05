
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        varying vec2 vTex;
        uniform sampler2D uTexture;
        uniform vec2 uTexelSize, uSrcTexelSize, uCompSize;
        vec4 readUv(vec2 p) {
            if (p.x<0.0 || p.y<0.0 || p.x>1.0 || p.y>1.0) return vec4(0.0);
            return texture2D(uTexture,clamp(p,0.5*uSrcTexelSize,1.0-0.5*uSrcTexelSize));
        }
        vec3 straight(vec4 c) { return c.a>0.00001 ? c.rgb/c.a : vec3(0.0); }
        float luma(vec3 c) { return dot(c,vec3(0.2126,0.7152,0.0722)); }
        float hash(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
        float noise(vec2 p) {
            vec2 i=floor(p),f=fract(p); f=f*f*(3.0-2.0*f);
            return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),
                       mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);
        }
    
            uniform float uRedPhase,uGreenPhase,uBluePhase,uOverflow;
            float channel(float value,float phase) {
                float angle=mod(phase,720.0);
                if(angle==0.0) return value;
                if(uOverflow<0.5) {
                    float turn=mod(angle,360.0);
                    if(turn==0.0) return value;
                    return fract(value+turn/360.0);
                }
                if(uOverflow<1.5) return 1.0-abs(mod(value+angle/360.0,2.0)-1.0);
                // Smooth reflection starting from each input value, with a 720-degree period.
                return 0.5+0.5*sin(asin(clamp(value*2.0-1.0,-1.0,1.0))+angle*0.00872664626);
            }
            void main() {
                vec4 base=texture2D(uTexture,vTex);vec3 rgb=straight(base);
                rgb=vec3(channel(rgb.r,uRedPhase),channel(rgb.g,uGreenPhase),channel(rgb.b,uBluePhase));
                gl_FragColor=vec4(clamp(rgb,0.0,1.0)*base.a,base.a);
            }
        