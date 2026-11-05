
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
    
            uniform float uM11,uM12,uM13,uM21,uM22,uM23,uM31,uM32,uM33,uDivider,uAbsolute,uBlend;
            vec3 tap(vec2 p) {
                // Hold source edges. Transparent source pixels contribute no hidden RGB.
                return straight(texture2D(uTexture,clamp(p,0.5*uSrcTexelSize,1.0-0.5*uSrcTexelSize)));
            }
            void main() {
                vec4 base=texture2D(uTexture,vTex);
                if(uBlend>=1.0 || abs(uDivider)<0.0001) { gl_FragColor=base;return; }
                vec2 d=1.0/max(uCompSize,vec2(1.0));
                vec3 result=tap(vTex+vec2(-d.x,d.y))*uM11+tap(vTex+vec2(0.0,d.y))*uM12+tap(vTex+d)*uM13+
                    tap(vTex+vec2(-d.x,0.0))*uM21+straight(base)*uM22+tap(vTex+vec2(d.x,0.0))*uM23+
                    tap(vTex-d)*uM31+tap(vTex+vec2(0.0,-d.y))*uM32+tap(vTex+vec2(d.x,-d.y))*uM33;
                result/=uDivider;
                if(uAbsolute>0.5) result=abs(result);
                result=mix(result,straight(base),clamp(uBlend,0.0,1.0));
                gl_FragColor=vec4(clamp(result,0.0,1.0)*base.a,base.a);
            }
        