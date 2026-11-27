#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
precision mediump sampler2D;
varying vec2 vTex;
uniform sampler2D uTexture;
uniform float uHazeThreshold;
uniform vec2 uTexelSize;
vec3 straight(vec4 p){ return p.rgb / max(p.a,0.0001); }
vec3 toLin(vec3 c){return mix(c/12.92,pow((c+.055)/1.055,vec3(2.4)),step(.04045,c));}
vec3 toSrgb(vec3 c){c=max(c,0.);return mix(c*12.92,1.055*pow(c,vec3(1./2.4))-.055,step(.0031308,c));}
void main(){
  float uThr=0.04+0.91*uHazeThreshold;
  float uKnee=0.2*uThr;
  vec3 acc=vec3(0.);
  for(int j=0;j<4;j++)for(int i=0;i<4;i++){
    vec2 off=((vec2(float(i),float(j))+.5)/4.-.5)*uTexelSize;
    vec3 lin=toLin(straight(texture2D(uTexture,vTex+off)));
    float lum=max(lin.r,max(lin.g,lin.b));
    float m=smoothstep(uThr-uKnee,uThr+uKnee,lum);
    acc+=lin*m;
  }
  gl_FragColor=vec4(acc/16.0,1.0);
}
