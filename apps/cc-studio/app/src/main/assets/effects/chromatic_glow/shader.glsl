
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
    
        uniform mat3 uLayerToLocal, uLocalToUv;
        bool outside(vec2 p) { return p.x<0.0 || p.y<0.0 || p.x>1.0 || p.y>1.0; }
        vec2 local(vec2 p) { vec3 q=uLayerToLocal*vec3(p,1); return q.xy/max(abs(q.z),0.00001)*sign(q.z); }
        vec2 uv(vec2 p) { vec3 q=uLocalToUv*vec3(p,1); return q.xy/max(abs(q.z),0.00001)*sign(q.z); }
        vec2 localSize() {
            return max(vec2(length((uv(vec2(1,0))-uv(vec2(0)))*uCompSize),
                            length((uv(vec2(0,1))-uv(vec2(0)))*uCompSize)),vec2(0.001));
        }
    
        vec2 ulocal(vec2 p) {vec3 q=uLayerToLocal*vec3(p,1.0);return q.xy/max(abs(q.z),0.00001)*sign(q.z);}
        vec2 uuv(vec2 p) {vec3 q=uLocalToUv*vec3(p,1.0);return q.xy/max(abs(q.z),0.00001)*sign(q.z);}
        vec2 usize() {vec2 o=uuv(vec2(0));return max(vec2(
            length((uuv(vec2(1,0))-o)*uCompSize),length((uuv(vec2(0,1))-o)*uCompSize)),vec2(0.001));}
        bool udead(vec2 p) {vec3 q=uLayerToLocal*vec3(p,1);return abs(q.z)<0.001;}
        float uhash(vec2 p) {return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        float unoise(vec2 p) {vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(
            mix(uhash(i),uhash(i+vec2(1,0)),f.x),mix(uhash(i+vec2(0,1)),uhash(i+vec2(1,1)),f.x),f.y);}
        vec3 ustraight(vec4 c) {return c.a>0.00001 ? c.rgb/c.a : vec3(0);}
        float uluma(vec3 c) {return dot(c,vec3(0.2126,0.7152,0.0722));}
        mat2 urotate(float a) {float c=cos(a),s=sin(a);return mat2(c,s,-s,c);}
        vec4 uvalid(vec4 c) {c.a=clamp(c.a,0.0,1.0);c.rgb=clamp(c.rgb,vec3(0),vec3(c.a));return c;}
        vec2 uwrap(vec2 p,float mode) {
            if(mode>2.5) return clamp(p,vec2(0.00001),vec2(0.99999));
            if(mode>1.5) return 1.0-abs(mod(p,2.0)-1.0);
            if(mode>0.5) return fract(p);return p;
        }
        vec4 utap(vec2 p,float mode) {return readUv(uuv(uwrap(p,mode)));}
    
        uniform float uThreshold,uBrightness,uSourceOpacity,uFromAlpha,uPreserveAlpha,uOnly,uBlend;
        vec3 ulight(vec4 c) {
            float signal=uFromAlpha>0.5?c.a:uluma(ustraight(c));
            float weight=max(0.0,signal-uThreshold)/max(1.0-uThreshold,0.001);
            return (uFromAlpha>0.5?vec3(c.a):c.rgb)*weight;
        }
        vec4 ucomposite(vec4 base,vec3 light) {
            light=1.0-exp(-max(light,vec3(0))*uBrightness);
            vec4 source=base*uSourceOpacity;
            if(uPreserveAlpha>0.5)light*=base.a;
            float coverage=max(light.r,max(light.g,light.b));
            float alpha=source.a+coverage*(1.0-source.a);
            if(uPreserveAlpha>0.5)alpha=base.a;
            vec4 result=vec4(source.rgb+(1.0-source.rgb)*light,alpha);
            if(uOnly>0.5)result=vec4(light,max(light.r,max(light.g,light.b)));
            return mix(base,uvalid(result),uBlend);
        }
    
        uniform float uRadius,uSpread,uRed,uGreen,uBlue,uTintR,uTintG,uTintB;
        vec4 advancedImage(vec2 p) {
            vec4 base=readUv(p);if(uBrightness<=0.0||uBlend<=0.0)return base;
            vec3 light=vec3(0);float norm=0.0;
            for(int ring=0;ring<3;ring++){float radius=(float(ring)+0.5)/3.0,weight=exp(-radius*radius*2.0);
                for(int i=0;i<8;i++){float theta=float(i)*0.78539816;vec2 dir=vec2(cos(theta),sin(theta))*radius*uRadius/uCompSize;
                    vec3 r=ulight(readUv(p+dir*max(0.0,uRed+uSpread)));
                    vec3 c=ulight(readUv(p+dir*uGreen));
                    vec3 b=ulight(readUv(p+dir*max(0.0,uBlue-uSpread)));
                    light+=vec3(r.r,c.g,b.b)*weight;norm+=weight;
                }
            }
            light=light/max(norm,0.001)*vec3(uTintR,uTintG,uTintB)*4.0;
            return ucomposite(base,light);
        }
    
void main(){gl_FragColor=advancedImage(vTex);}