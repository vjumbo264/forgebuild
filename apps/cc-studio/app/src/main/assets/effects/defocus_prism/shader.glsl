
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
    
        uniform float uWidth,uHeight,uShape,uRoundness,uRotate,uBokeh,uLensNoise,uSeed,uGamma,uBoost,uThreshold,uBrightness,uChroma,uRing,uBlend,uEdge;
        vec4 advancedImage(vec2 p) {
            vec4 base=readUv(p);if(uWidth<=0.0||uBlend<=0.0||udead(p))return base;
            vec2 q=ulocal(p),size=usize();vec3 sum=vec3(0),norm=vec3(0);float alpha=0.0,alphaNorm=0.0;
            for(int i=0;i<32;i++) {
                float t=(float(i)+0.5)/32.0,theta=float(i)*2.39996323+radians(uRotate);
                float sides=uShape+2.0,polygon=1.0;
                if(uShape>0.5){float sector=6.2831853/sides;polygon=cos(3.14159265/sides)/cos(mod(theta+sector*0.5,sector)-sector*0.5);
                    polygon=mix(polygon,1.0,max(uRoundness,0.0));polygon*=1.0+min(uRoundness,0.0)*0.4*cos(theta*sides);}
                float r=mix(sqrt(t),mix(1.0-uRing,1.0,t),uBokeh)*polygon;
                vec2 offset=vec2(cos(theta),sin(theta)*uHeight)*r*uWidth/size;
                float weight=mix(1.0,uhash(vec2(float(i),uSeed))*2.0,uLensNoise);
                vec4 red=utap(q+offset*(1.0+uChroma),uEdge),green=utap(q+offset,uEdge),blue=utap(q+offset*max(0.0,1.0-uChroma),uEdge);
                vec3 c=vec3(red.r,green.g,blue.b),coverage=vec3(red.a,green.a,blue.a);
                vec3 straightColor=c/max(coverage,vec3(0.00001));
                float highlight=max(uluma(straightColor)-uThreshold,0.0)*uBoost;
                sum+=pow(max(straightColor*(1.0+highlight),vec3(0)),vec3(uGamma))*coverage*weight;
                norm+=coverage*weight;alpha+=max(coverage.r,max(coverage.g,coverage.b))*weight;alphaNorm+=weight;
            }
            float opacity=alpha/max(alphaNorm,0.001);
            vec3 color=pow(sum/max(norm,vec3(0.00001)),vec3(1.0/max(uGamma,0.1)))*opacity*uBrightness;
            return mix(base,uvalid(vec4(color,opacity)),uBlend);
        }
    
void main(){gl_FragColor=advancedImage(vTex);}