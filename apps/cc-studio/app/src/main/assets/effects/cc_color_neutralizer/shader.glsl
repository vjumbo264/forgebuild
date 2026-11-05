
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
    
        uniform float uShadowsR,uShadowsG,uShadowsB,uMidtonesR,uMidtonesG,uMidtonesB;
        uniform float uHighlightsR,uHighlightsG,uHighlightsB,uPinning,uBlend,uView,uBlackPoint,uWhitePoint;
        float lightness(vec3 c) { return (max(c.r,max(c.g,c.b))+min(c.r,min(c.g,c.b)))*0.5; }
        vec3 correct(vec3 c) {
            float originalLightness=lightness(c);
            float t=clamp((originalLightness-uBlackPoint)/max(uWhitePoint-uBlackPoint,0.0001),0.0,1.0);
            float low=1.0-smoothstep(0.0,0.5,t),high=smoothstep(0.5,1.0,t);
            vec3 change=vec3(uShadowsR,uShadowsG,uShadowsB)*low+
                vec3(uMidtonesR,uMidtonesG,uMidtonesB)*(1.0-low-high)+
                vec3(uHighlightsR,uHighlightsG,uHighlightsB)*high;
            change*=0.6666666667;
            float pin=clamp(uPinning,0.0,1.0);
            if(pin>0.0) change*=pow(max(4.0*t*(1.0-t),0.0),pin*8.0);
            vec3 adjusted=c+change;
            adjusted+=vec3(originalLightness-lightness(adjusted));
            return mix(adjusted,c,clamp(uBlend,0.0,1.0));
        }
        void main() {
            vec4 base=texture2D(uTexture,vTex);
            if(uView<0.5) {
                gl_FragColor=vec4(clamp(correct(straight(base)),0.0,1.0)*base.a,base.a);return;
            }
            // Diagnostic image: reference ramp, corrected ramps, then RGB curves.
            float y=1.0-vTex.y,x=vTex.x;
            if(y<0.08) { gl_FragColor=vec4(vec3(x),1.0);return; }
            vec3 shifted=correct(vec3(x));
            if(y<0.5) {
                vec3 background=y<0.22?vec3(0.0):y<0.36?vec3(1.0):vec3(0.5);
                float band=mod((y-0.08)/0.14,1.0);
                gl_FragColor=vec4(band<0.65?clamp(shifted,0.0,1.0):background,1.0);return;
            }
            float graphY=1.0-(y-0.5)*2.0;
            vec3 target=uView>1.5?floor(shifted*16.0+0.5)/16.0:shifted;
            vec3 stroke=vec3(1.0)-smoothstep(vec3(0.0),vec3(max(0.008,3.0*uTexelSize.y)),abs(target-graphY));
            vec3 grid=vec3(0.06+0.06*step(0.94,fract(x*10.0)));
            gl_FragColor=vec4(max(grid,stroke),1.0);
        }
    