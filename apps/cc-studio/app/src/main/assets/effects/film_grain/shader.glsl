
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
    
        uniform float uIntensity,uSize,uSoftness,uAspect,uRedIntensity,uGreenIntensity,uBlueIntensity;
        uniform float uSaturation,uShadows,uMidtones,uHighlights,uAnimationSpeed,uSeed,uTime;
        float grain(vec2 p) {
            float hard=hash(floor(p))-0.5;
            return mix(hard,noise(p)-0.5,uSoftness);
        }
        void main() {
            vec4 b=texture2D(uTexture,vTex); vec3 rgb=straight(b);
            vec2 p=vTex*uCompSize/max(uSize,0.1)/vec2(max(uAspect,0.1),1.0);
            p+=vec2(floor(uTime*uAnimationSpeed)*47.0+uSeed*113.0);
            vec3 n=vec3(grain(p),grain(p+vec2(71,19)),grain(p+vec2(139,37)));
            n=mix(vec3(n.r),n,uSaturation)*vec3(uRedIntensity,uGreenIntensity,uBlueIntensity);
            float lum=clamp(luma(rgb),0.0,1.0);
            float tonal=mix(uShadows,uMidtones,smoothstep(0.0,0.5,lum));
            tonal=mix(tonal,uHighlights,smoothstep(0.5,1.0,lum));
            gl_FragColor=vec4(clamp(rgb+n*uIntensity*0.12*tonal,0.0,1.0)*b.a,b.a);
        }
        