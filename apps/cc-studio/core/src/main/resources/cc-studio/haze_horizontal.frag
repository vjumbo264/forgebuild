#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
precision mediump sampler2D;
varying vec2 vTex;
uniform sampler2D uTexture;
uniform float uHazeSpread;
void main(){
  const int N=64;float acc=0.;vec3 s=vec3(0.);float ws=0.;
  for(int j=0;j<129;j++){
    float t=float(j-64)/float(N);float w=exp(-3.*abs(t));
    float x=vTex.x+t*uHazeSpread;ws+=w;
    if(x<0.||x>1.)continue;
    s+=texture2D(uTexture,vec2(x,vTex.y)).rgb*w;
  }
  gl_FragColor=vec4(s/ws,1.);
}
