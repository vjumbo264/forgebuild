#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
precision mediump sampler2D;
varying vec2 vTex;
uniform sampler2D uTexture;
uniform float uHazeBand;
void main(){
  vec3 s=vec3(0.);float ws=0.;
  for(int j=0;j<25;j++){
    float i=float(j-12); float k=i/4.;float w=exp(-.5*k*k);
    float y=vTex.y+i/12.*3.*uHazeBand;ws+=w;
    if(y<0.||y>1.)continue;
    s+=texture2D(uTexture,vec2(vTex.x,y)).rgb*w;
  }
  gl_FragColor=vec4(s/ws,1.);
}
