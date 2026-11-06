export const SOLID_VS = `#version 300 es
precision highp float; precision highp int;
layout(location=0) in ivec3 aPos;
layout(location=1) in uint aColor;
layout(location=2) in uvec4 aInfo;  // faceAo, skyBlk, sunAB, sunC
uniform mat4 uVP; uniform vec3 uOrigin; uniform highp sampler2D uPal; uniform float uIntro; uniform vec3 uCam; uniform vec3 uCenter;
uniform vec3 uSunW, uSunCol, uAmbUp, uAmbDn, uBlock, uSunDirV; uniform float uTime;
out vec3 vW; out float vAO; out vec3 vCol; out vec3 vL; flat out vec3 vN; flat out vec4 vM; flat out vec3 vSunTint;
const vec3 NRM[8]=vec3[8](vec3(0,-1,0),vec3(0,1,0),vec3(0,0,-1),vec3(0,0,1),vec3(-1,0,0),vec3(1,0,0),vec3(.707,0,-.707),vec3(.707,0,.707));
float h21(vec2 p){p=fract(p*vec2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);}
void main(){
  vec3 p=vec3(aPos)/16.0+uOrigin;
  uint face=aInfo.x&7u; uint ao=(aInfo.x>>3u)&3u;
  int ci=int(aColor);
  vec4 c=texelFetch(uPal,ivec2(ci&255,(ci>>8)*2),0);
  vec4 m=texelFetch(uPal,ivec2(ci&255,(ci>>8)*2+1),0);
  vCol=c.rgb*c.rgb*(c.rgb*.305+.695); // fast sRGB to linear
  vN=NRM[face]; vAO=float(ao)/3.0;
  float sky=float(aInfo.y>>4u)/15.0;
  float blk=float(aInfo.y&15u)/15.0;
  vec3 sun=vec3(float(aInfo.z>>4u),float(aInfo.z&15u),float(aInfo.w>>4u))/15.0;
  vec3 amb=mix(uAmbDn,uAmbUp,vN.y*.5+.5);
  amb*=1.0+dot(vN,uSunDirV)*.28;
  float skyL=.15+.85*pow(sky,.9);
  vec3 L=amb*skyL+uSunCol*dot(sun,uSunW);
  L+=uBlock*blk*blk*blk;
  vL=L;
  vM=vec4(floor(m.r*255.0/16.0),mod(m.r*255.0,16.0)/15.0,m.g*255.0,0.0);
  vSunTint=uSunCol;
  if(uIntro<1.0){ // chunks rise out of the fog, from the middle outward
    vec2 col=floor(p.xz/16.0); float s=h21(col); float d=length(p.xz-uCenter.xz)/230.0;
    float k=clamp((uIntro*1.6-d*.72-s*.22)/.5,0.,1.); k=1.0-(1.0-k)*(1.0-k)*(1.0-k);
    p.y-=(1.0-k)*(150.0+s*90.0);
  }
  // leaves and grass breathe a little
  float pat=vM.x;
  if(pat==6.0||face>5u){ float sw=sin(uTime*1.1+p.x*.31+p.z*.27+p.y*.2)*.035; p.x+=sw; p.z+=sw*.7; }
  vW=p; gl_Position=uVP*vec4(p,1.0);
}`

export const SOLID_FS = `#version 300 es
precision highp float; precision highp int;
in vec3 vW; in float vAO; in vec3 vCol; in vec3 vL; flat in vec3 vN; flat in vec4 vM; flat in vec3 vSunTint;
uniform vec3 uCam; uniform sampler2D uPat; uniform sampler2D uNoise; uniform vec3 uSunDir, uHaze, uAmbUp; uniform float uEmis, uFog, uMist, uMistY, uTime, uExposure, uFogBoost;
out vec4 o;
float h31(vec3 p){p=fract(p*.1031);p+=dot(p,p.zyx+31.32);return fract((p.x+p.y)*p.z);}
vec3 aces(vec3 x){return clamp((x*(2.51*x+.03))/(x*(2.43*x+.59)+.14),0.,1.);}
void main(){
  vec3 d=vW-uCam; float dist=length(d); vec3 V=-d/dist;
  vec2 uv=abs(vN.y)>.5?vW.xz:(abs(vN.x)>.5?vW.zy:vW.xy);
  vec3 bp=floor(vW-vN*.02);
  float hb=h31(bp);
  float pat=vM.x, con=vM.y, flags=vM.z;
  vec2 tile=vec2(mod(pat,4.0),floor(pat/4.0));
  float near=1.0-smoothstep(18.0,150.0,dist);
  float g=0.0;
  if(near>0.0){ g=(texture(uPat,(tile+fract(uv))/4.0).r*2.0-1.0)*(.1+con*1.5)*near; }
  vec3 alb=vCol*(1.0+g)*(1.0+(hb-.5)*.15);
  float ao=mix(.5,1.0,vAO);
  vec3 col=alb*vL*ao;
  float isGlow=mod(flags,2.0), isGlass=mod(floor(flags/2.0),2.0), isWater=mod(floor(flags/4.0),2.0);
  // metal and glass catch the sun as the camera moves: each block a slightly different mirror
  if(pat==8.0||isGlass>.5||isWater>.5){
    vec3 n=normalize(vN+(vec3(h31(bp+1.3),h31(bp+2.7),h31(bp+4.1))-.5)*(isGlass>.5?.12:.28));
    float s=pow(max(dot(reflect(-V,n),uSunDir),0.),isGlass>.5?64.:28.);
    float fr=pow(1.0-max(dot(V,vN),0.),3.0);
    col+=vSunTint*s*(isGlass>.5?1.1:.9)+uAmbUp*fr*.35;
  }
  if(isGlow>.5) col=mix(col,alb*(1.1+uEmis*2.6),.55+.45*uEmis);
  if(isGlass>.5){ // a window: dark by day; at dusk the lights come on, one by one
    float on=step(hb,uEmis*.86);
    vec3 warm=mix(vec3(1.0,.62,.28),vec3(1.0,.8,.5),h31(bp*1.7));
    col=mix(col*.55+uAmbUp*.09,warm*(1.15+hb*.8),on);
  }
  col=aces(col*uExposure);
  col=pow(col,vec3(.4545));
  float f=1.0-exp(-pow(dist*(uFog+uFogBoost),1.7));
  // mist pooling in the lower air
  float m=texture(uNoise,vW.xz*.0023+uTime*vec2(.0035,.0018)).r;
  float band=smoothstep(uMistY+85.0,uMistY-10.0,vW.y);
  f=max(f,clamp(band*(m*1.4-.25)*uMist,0.,.92));
  col=mix(col,uHaze,f*.4);
  float a=1.0-f*f*(3.0-2.0*f);
  o=vec4(col*a,a);
}`

export const CLOUD_VS = `#version 300 es
precision highp float;
layout(location=0) in vec2 aP;
uniform mat4 uVP; uniform vec3 uCam; uniform float uY, uR;
out vec3 vW;
void main(){ vec3 p=vec3(uCam.x+aP.x*uR,uY,uCam.z+aP.y*uR); vW=p; gl_Position=uVP*vec4(p,1.0); }`

export const CLOUD_FS = `#version 300 es
precision highp float;
in vec3 vW;
uniform vec3 uCam, uLit, uDark, uHaze, uSunDir; uniform sampler2D uNoise; uniform float uTime, uCover, uR, uAlpha, uScale;
out vec4 o;
void main(){
  vec2 p=vW.xz*uScale;
  vec2 w=uTime*vec2(.0016,.0009);
  float a=texture(uNoise,p+w).r, b=texture(uNoise,p*3.3-w*1.7).g;
  float n=a*.74+b*.26;
  float cov=smoothstep(uCover-.16,uCover+.2,n);
  vec2 s=uSunDir.xz*.012;
  float a2=texture(uNoise,p+w+s).r, b2=texture(uNoise,p*3.3-w*1.7+s*3.3).g;
  float lit=clamp(.62+(n-(a2*.74+b2*.26))*3.6+(n-.5)*.5,0.,1.);
  vec3 col=mix(uDark,uLit,lit);
  float dist=length(vW-uCam);
  float hz=smoothstep(uR*.28,uR*.96,dist);
  col=mix(col,uHaze,hz*.72);
  float al=cov*(1.0-hz)*uAlpha;
  al=max(al,0.);
  o=vec4(col*al,al);
}`

export const GLOW_VS = `#version 300 es
precision highp float;
layout(location=0) in vec4 aP; // xyz, level
uniform mat4 uVP; uniform vec3 uOrigin; uniform float uPx, uEmis, uTime, uIntro;
out float vA;
void main(){
  vec3 p=aP.xyz+uOrigin; vec4 c=uVP*vec4(p,1.0);
  float lvl=aP.w/15.0;
  float fl=.86+.14*sin(uTime*(2.0+fract(p.x*.37)*2.0)+p.y*3.1+p.z*1.7);
  float size=(2.0+lvl*5.5)*uPx/max(c.w,1.0);
  gl_PointSize=clamp(size,1.5,uPx*.09);
  vA=uEmis*lvl*fl*smoothstep(1.0,.82,uIntro==1.0?0.0:1.0-uIntro)*smoothstep(1400.0,120.0,c.w)*.92;
  gl_Position=c;
}`

export const GLOW_FS = `#version 300 es
precision highp float;
in float vA; uniform vec3 uTint; out vec4 o;
void main(){
  vec2 q=gl_PointCoord*2.0-1.0; float r=dot(q,q); if(r>1.0)discard;
  float g=(1.0-r); g=g*g*g;
  o=vec4(uTint*g*vA,0.0);
}`
