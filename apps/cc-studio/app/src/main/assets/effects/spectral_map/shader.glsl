
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
    
            uniform float uOffset,uRepeat,uLuma,uSmoothing,uAlpha;
            uniform float uColor1R,uColor1G,uColor1B,uColor2R,uColor2G,uColor2B;
            uniform float uColor3R,uColor3G,uColor3B,uColor4R,uColor4G,uColor4B;
            uniform float uColor5R,uColor5G,uColor5B,uColor6R,uColor6G,uColor6B;
            vec3 spectrum(float p) {
                vec3 a,b;float t=p*5.0;
                if(t<1.0) {a=vec3(uColor1R,uColor1G,uColor1B);b=vec3(uColor2R,uColor2G,uColor2B);}
                else if(t<2.0) {a=vec3(uColor2R,uColor2G,uColor2B);b=vec3(uColor3R,uColor3G,uColor3B);t-=1.0;}
                else if(t<3.0) {a=vec3(uColor3R,uColor3G,uColor3B);b=vec3(uColor4R,uColor4G,uColor4B);t-=2.0;}
                else if(t<4.0) {a=vec3(uColor4R,uColor4G,uColor4B);b=vec3(uColor5R,uColor5G,uColor5B);t-=3.0;}
                else {a=vec3(uColor5R,uColor5G,uColor5B);b=vec3(uColor6R,uColor6G,uColor6B);t-=4.0;}
                t=clamp(t,0.0,1.0);
                return mix(a,b,mix(t,smoothstep(0.0,1.0,t),clamp(uSmoothing,0.0,1.0)));
            }
            void main() {
                vec4 base=texture2D(uTexture,vTex);
                if(uAlpha<=0.0 || base.a<=0.0) {gl_FragColor=base;return;}
                vec3 rgb=straight(base);
                float brightness=clamp(luma(clamp(rgb,0.0,1.0))*max(uLuma,0.0),0.0,1.0);
                float position=brightness*clamp(uRepeat,0.1,20.0)+fract(uOffset);
                float wrapped=fract(position);
                // A positive exact endpoint uses Color 6, not a sudden jump to Color 1.
                if(position>0.0 && wrapped<0.00001) wrapped=1.0;
                vec3 mapped=spectrum(wrapped);
                gl_FragColor=vec4(clamp(mix(rgb,mapped,clamp(uAlpha,0.0,1.0)),0.0,1.0)*base.a,base.a);
            }
        