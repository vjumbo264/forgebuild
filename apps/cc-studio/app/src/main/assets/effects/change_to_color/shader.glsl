
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
    
        vec3 hsl(vec3 c) {
            float hi=max(c.r,max(c.g,c.b)),lo=min(c.r,min(c.g,c.b)),d=hi-lo;
            float l=(hi+lo)*0.5; float s=d<0.00001 ? 0.0 : d/max(1.0-abs(2.0*l-1.0),0.00001);
            float h=0.0;
            if(d>0.00001) {
                if(hi==c.r) h=mod((c.g-c.b)/d,6.0);
                else if(hi==c.g) h=(c.b-c.r)/d+2.0;
                else h=(c.r-c.g)/d+4.0;
            }
            return vec3(fract(h/6.0),s,l);
        }
        vec3 fromHsl(vec3 h) {
            vec3 p=abs(fract(h.x+vec3(0,2.0/3.0,1.0/3.0))*6.0-3.0);
            return h.z+(clamp(p-1.0,0.0,1.0)-0.5)*(1.0-abs(2.0*h.z-1.0))*h.y;
        }
    
        uniform float uFromR,uFromG,uFromB,uToR,uToG,uToB,uChange,uChangeBy;
        uniform float uHueTolerance,uLightnessTolerance,uSaturationTolerance,uSoftness,uViewMatte;
        float eligible(float distance,float tolerance) {
            return 1.0-smoothstep(tolerance,tolerance+max(uSoftness,0.0001),distance);
        }
        void main() {
            vec4 b=texture2D(uTexture,vTex); vec3 rgb=straight(b);
            vec3 src=hsl(vec3(uFromR,uFromG,uFromB)),dst=hsl(vec3(uToR,uToG,uToB)),v=hsl(rgb);
            float hd=abs(v.x-src.x); hd=min(hd,1.0-hd)*2.0;
            float weight=eligible(hd,uHueTolerance)*eligible(abs(v.y-src.y),uSaturationTolerance)*eligible(abs(v.z-src.z),uLightnessTolerance);
            // Hue has no definition for a neutral source: select neutrals by saturation/lightness.
            if(src.y<0.0001) weight=eligible(v.y,uSaturationTolerance)*eligible(abs(v.z-src.z),uLightnessTolerance);
            vec3 target=dst;
            if(uChangeBy>0.5) {
                float dh=mod(dst.x-src.x+0.5,1.0)-0.5;
                target=vec3(fract(v.x+dh),clamp(v.y+dst.y-src.y,0.0,1.0),clamp(v.z+dst.z-src.z,0.0,1.0));
            }
            if(uChange<0.5) target.yz=v.yz;
            else if(uChange<1.5) target.y=v.y;
            else if(uChange<2.5) target.z=v.z;
            vec3 result=uViewMatte>0.5 ? vec3(weight) : mix(rgb,fromHsl(target),weight);
            gl_FragColor=vec4(clamp(result,0.0,1.0)*b.a,b.a);
        }
        