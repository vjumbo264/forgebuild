#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
precision mediump sampler2D;
varying vec2 vTex;
uniform sampler2D uTexture;
uniform sampler2D uPrev;
uniform float uHazeOn;
uniform float uHazeMode;
uniform float uHazeIntensity;
uniform float uHazeOpacity;
uniform float uHazeSide;
uniform float uHazeCenter;
uniform float uHazeGain;
vec3 straight(vec4 p){ return p.rgb / max(p.a,0.0001); }
vec3 toLin(vec3 c){return mix(c/12.92,pow((c+.055)/1.055,vec3(2.4)),step(.04045,c));}
vec3 toSrgb(vec3 c){c=max(c,0.);return mix(c*12.92,1.055*pow(c,vec3(1./2.4))-.055,step(.0031308,c));}
vec3 soft(vec3 x){return 1.-exp(-x);}
vec3 side(vec2 p){return (p.x>=0.&&p.x<=1.)?texture2D(uTexture,p).rgb:vec3(0.);}
vec3 flareAt(vec2 q){
  vec3 r=side(vec2(q.x-uHazeSide,q.y));          /* right copy: 0 deg, shifted right */
  vec3 l=side(vec2(1.-q.x-uHazeSide,q.y));       /* left copy: mirrored, shifted left */
  vec3 c=texture2D(uTexture,q).rgb*uHazeCenter;
  return soft((l+r+c)*uHazeGain);               /* bound energy, clamp only at output */
}
vec3 blendF(vec3 s,vec3 f,float w){
  if(uHazeMode<0.5)return mix(s,max(s,f),w);      /* Lighten */
  if(uHazeMode<1.5)return s+f*w;                  /* Additive */
  return mix(s,1.-(1.-s)*(1.-f),w);          /* Screen */
}
void main(){
  vec4 base=texture2D(uPrev,vTex);
  float w=uHazeIntensity*uHazeOpacity;
  if(uHazeOn<0.5 || w<=0.0){ gl_FragColor=base; return; }
  vec3 res=blendF(toLin(straight(base)),flareAt(vTex),w);
  gl_FragColor=vec4(clamp(toSrgb(min(res,vec3(1.0))),0.0,1.0)*base.a,base.a);
}
