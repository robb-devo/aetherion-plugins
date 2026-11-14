// bake.c: voxel schematic -> merged, light-baked quads. Own code for the Aetherion website.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <math.h>
#include <omp.h>
#define FL_FULL 1
#define FL_PASS 2
#define FL_RAY 4
#define FL_LEAF 8
#define FL_INVIS 16
typedef struct { int8_t lo[3], hi[3]; uint16_t fc[6]; } Box;
typedef struct { uint8_t flags, emit, nb, crossH; uint16_t crossC; Box *b; } State;
typedef struct { int16_t x,y,z; uint16_t wf,h,color; uint8_t sky01,sky23,blk01,blk23,ao,sunAB,sunC,pad; } Quad;
static int W,H,L,ns; static uint16_t *g; static State *S; static uint8_t *ext,*lit;
static inline size_t I(int x,int y,int z){return ((size_t)y*L+z)*W+x;}
static inline int inb(int x,int y,int z){return x>=0&&y>=0&&z>=0&&x<W&&y<H&&z<L;}
static inline uint8_t fl(int x,int y,int z){ if(!inb(x,y,z))return FL_PASS|FL_INVIS; return S[g[I(x,y,z)]].flags;}
static inline int isExt(int x,int y,int z){ if(!inb(x,y,z))return 1; return ext[I(x,y,z)];}
static const int DIR[6][3]={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
// U,V axes with U x V = normal
static const int AXU[6]={0,2,1,0,2,1}; // down:x up:z north:y south:x west:z east:y
static const int AXV[6]={2,0,0,1,1,2}; // down:z up:x north:x south:y west:y east:z
static const int AXN[6]={1,1,2,2,0,0};

static void flood(void){
  size_t n=(size_t)W*H*L; ext=calloc(n,1); int32_t *st=malloc(sizeof(int32_t)*n); size_t sp=0;
  #define PUSH(x,y,z) do{ if(inb(x,y,z)){size_t k=I(x,y,z); if(!ext[k]&&(S[g[k]].flags&FL_PASS)){ext[k]=1;st[sp++]=(int32_t)k;}} }while(0)
  for(int y=0;y<H;y++)for(int z=0;z<L;z++){PUSH(0,y,z);PUSH(W-1,y,z);}
  for(int y=0;y<H;y++)for(int x=0;x<W;x++){PUSH(x,y,0);PUSH(x,y,L-1);}
  for(int z=0;z<L;z++)for(int x=0;x<W;x++){PUSH(x,0,z);PUSH(x,H-1,z);}
  while(sp){ size_t k=(size_t)st[--sp]; int x=k%W; size_t r=k/W; int z=r%L; int y=r/L;
    PUSH(x+1,y,z);PUSH(x-1,y,z);PUSH(x,y+1,z);PUSH(x,y-1,z);PUSH(x,y,z+1);PUSH(x,y,z-1); }
  free(st);
}
static void blocklight(void){
  size_t n=(size_t)W*H*L; lit=calloc(n,1); int32_t *q=malloc(sizeof(int32_t)*n*2); size_t a=0,b=0;
  for(size_t k=0;k<n;k++){int e=S[g[k]].emit; if(e){lit[k]=e;q[b++]=(int32_t)k;}}
  while(a<b){ size_t k=(size_t)q[a++]; int l=lit[k]; if(l<=1)continue; int x=k%W; size_t r=k/W; int z=r%L; int y=r/L;
    for(int d=0;d<6;d++){int X=x+DIR[d][0],Y=y+DIR[d][1],Z=z+DIR[d][2]; if(!inb(X,Y,Z))continue; size_t m=I(X,Y,Z);
      if((S[g[m]].flags&FL_FULL))continue; if(lit[m]<l-1){lit[m]=l-1; if(b<n*2)q[b++]=(int32_t)m;}}
  }
  free(q);
}
static uint8_t *svg; // sky visibility per open cell
// ---- rays
static inline uint32_t rng(uint32_t *s){uint32_t x=*s; x^=x<<13;x^=x>>17;x^=x<<5;*s=x;return x;}
static inline float rf(uint32_t *s){return (rng(s)&0xffffff)/16777216.0f;}
static int rayEscapes(float ox,float oy,float oz,float dx,float dy,float dz,int maxSteps,uint32_t *seed){
  int x=(int)floorf(ox),y=(int)floorf(oy),z=(int)floorf(oz);
  int sx=dx>0?1:-1,sy=dy>0?1:-1,sz=dz>0?1:-1;
  float tdx=dx!=0?fabsf(1/dx):1e9f,tdy=dy!=0?fabsf(1/dy):1e9f,tdz=dz!=0?fabsf(1/dz):1e9f;
  float tx=dx!=0?((sx>0?(x+1-ox):(ox-x))*tdx):1e9f, ty=dy!=0?((sy>0?(y+1-oy):(oy-y))*tdy):1e9f, tz=dz!=0?((sz>0?(z+1-oz):(oz-z))*tdz):1e9f;
  for(int i=0;i<maxSteps;i++){
    if(tx<ty&&tx<tz){x+=sx;tx+=tdx;} else if(ty<tz){y+=sy;ty+=tdy;} else {z+=sz;tz+=tdz;}
    if(x<0||y<0||z<0||x>=W||y>=H||z>=L) return 1;
    uint8_t f=S[g[I(x,y,z)]].flags;
    if(f&FL_RAY){ if(f&FL_LEAF){ if(rf(seed)<0.72f) return 0; } else return 0; }
  }
  return 1;
}
#define NHEMI 32
#define NSPH 96
static float HX[NHEMI],HY[NHEMI],HZ[NHEMI],SX[NSPH],SY[NSPH],SZ[NSPH];
static void initDirs(void){ // spherical fibonacci over the full sphere
  for(int i=0;i<NHEMI;i++){ float z=1-2*(i+0.5f)/NHEMI; float r=sqrtf(1-z*z); float a=i*2.39996323f; HX[i]=r*cosf(a);HY[i]=z;HZ[i]=r*sinf(a);}
  for(int i=0;i<NSPH;i++){ float z=1-2*(i+0.5f)/NSPH; float r=sqrtf(1-z*z); float a=i*2.39996323f; SX[i]=r*cosf(a);SY[i]=z;SZ[i]=r*sinf(a);}
}
static float SUN[3][3];
static void norm(float*v){float l=sqrtf(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);v[0]/=l;v[1]/=l;v[2]/=l;}
typedef struct {uint8_t sky,sunAB,sunC,open;} LightV;
static LightV shade(float px,float py,float pz,int face,uint32_t *seed,int nearLit){
  const int*n=DIR[face]; float ox=px+n[0]*0.02f,oy=py+n[1]*0.02f,oz=pz+n[2]*0.02f;
  float acc=0,tw=0; int open=0;
  for(int i=0;i<NHEMI;i++){ float c=HX[i]*n[0]+HY[i]*n[1]+HZ[i]*n[2]; if(c<=0.02f)continue;
    // sky above is brighter than the cloud sea below
    float w=c*(HY[i]>0?1.0f:0.32f);
    tw+=c; if(rayEscapes(ox,oy,oz,HX[i],HY[i],HZ[i],56,seed)){acc+=w;open++;}
  }
  LightV L; int sk=(int)(255*fminf(1.f,(tw>0?acc/tw:0)*1.25f)); L.sky=sk; L.open=open;
  int sv[3];
  for(int s=0;s<3;s++){ float c=SUN[s][0]*n[0]+SUN[s][1]*n[1]+SUN[s][2]*n[2]; if(c<=0.0f){sv[s]=0;continue;}
    int hit=0; const int R=5; static const float JX[5]={0,.035f,-.035f,.02f,-.02f},JY[5]={0,.02f,.02f,-.035f,-.035f},JZ[5]={0,-.02f,.02f,.03f,-.03f};
    for(int r=0;r<R;r++){ if(rayEscapes(ox,oy,oz,SUN[s][0]+JX[r],SUN[s][1]+JY[r],SUN[s][2]+JZ[r],420,seed))hit++; }
    sv[s]=(int)(15.0f*hit/R*fminf(1.f,c*1.6f)+0.5f); sv[s]&=~1; if(sv[s]>15)sv[s]=15;
  }
  L.sunAB=(sv[0]<<4)|sv[1]; L.sunC=(sv[2]<<4);
  return L;
}
static inline int solidAO(int x,int y,int z){ if(!inb(x,y,z))return 0; uint8_t f=S[g[I(x,y,z)]].flags; return (f&FL_FULL)?1:0; }
static inline int vAO(int a,int b,int c){ return (a&&b)?0:3-(a+b+c); }

// smooth light: average the open cells around a face corner (Minecraft's own trick, with baked sky)
static inline void cornerLight(int nx,int ny,int nz,const int du[3],const int dv[3],int su,int sv,int *sky,int *blk){
  int ss=0,bb=0,cnt=0; int ax=nx+su*du[0],ay=ny+su*du[1],az=nz+su*du[2]; int bx=nx+sv*dv[0],by=ny+sv*dv[1],bz=nz+sv*dv[2];
  int ca=solidAO(ax,ay,az),cb=solidAO(bx,by,bz);
  #define ADDC(X,Y,Z) do{ if(inb(X,Y,Z)){ size_t k=I(X,Y,Z); if(!(S[g[k]].flags&FL_FULL)){ss+=svg[k];bb+=lit[k];cnt++;} } else {ss+=255;cnt++;} }while(0)
  ADDC(nx,ny,nz); if(!ca)ADDC(ax,ay,az); if(!cb)ADDC(bx,by,bz); if(!ca||!cb)ADDC(ax+sv*dv[0],ay+sv*dv[1],az+sv*dv[2]);
  if(cnt==0){*sky=0;*blk=0;return;}
  *sky=(ss/cnt)>>4; *blk=(bb/cnt);
}
typedef struct {uint16_t color;uint8_t ao,sky01,sky23,blk01,blk23,sunAB,sunC;uint8_t used;} Cell;
static inline int flatAO(uint8_t a){return a==0xFF||a==0xAA||a==0x55||a==0x00;}
static inline int flatCell(const Cell*c){ return flatAO(c->ao)&&c->sky01==c->sky23&&(c->sky01>>4)==(c->sky01&15)&&c->blk01==c->blk23&&(c->blk01>>4)==(c->blk01&15); }
typedef struct {Quad*q;size_t n,cap;} Buf;
static void push(Buf*b,Quad q){ if(b->n==b->cap){b->cap=b->cap?b->cap*2:4096;b->q=realloc(b->q,sizeof(Quad)*b->cap);} b->q[b->n++]=q; }
static void emitRect(Buf*b,int face,int cx,int cy,int cz,int layer,int u0,int v0,int du,int dv,Cell*c){
  int p[3]; int au=AXU[face],av=AXV[face],an=AXN[face]; int base[3]={cx,cy,cz};
  p[an]=(base[an]+layer)*16+(DIR[face][an]>0?16:0); p[au]=(base[au]+u0)*16; p[av]=(base[av]+v0)*16;
  Quad q={(int16_t)p[0],(int16_t)p[1],(int16_t)p[2],(uint16_t)((du*16)|(face<<13)),(uint16_t)(dv*16),c->color,c->sky01,c->sky23,c->blk01,c->blk23,c->ao,c->sunAB,c->sunC,0};
  push(b,q);
}
// ---- view culling: the camera only ever goes along one route, so faces it can never see need not exist
static uint8_t *vmask; static float *VW; static int NV=0;
static int clearRay(float ox,float oy,float oz,float tx,float ty,float tz){
  float dx=tx-ox,dy=ty-oy,dz=tz-oz; float len=sqrtf(dx*dx+dy*dy+dz*dz); if(len<1e-3f)return 1; dx/=len;dy/=len;dz/=len;
  int x=(int)floorf(ox),y=(int)floorf(oy),z=(int)floorf(oz);
  int sx=dx>0?1:-1,sy=dy>0?1:-1,sz=dz>0?1:-1;
  float tdx=dx!=0?fabsf(1/dx):1e9f,tdy=dy!=0?fabsf(1/dy):1e9f,tdz=dz!=0?fabsf(1/dz):1e9f;
  float mx=dx!=0?((sx>0?(x+1-ox):(ox-x))*tdx):1e9f, my=dy!=0?((sy>0?(y+1-oy):(oy-y))*tdy):1e9f, mz=dz!=0?((sz>0?(z+1-oz):(oz-z))*tdz):1e9f;
  for(;;){ float t;
    if(mx<my&&mx<mz){x+=sx;t=mx;mx+=tdx;} else if(my<mz){y+=sy;t=my;my+=tdy;} else {z+=sz;t=mz;mz+=tdz;}
    if(t>=len) return 1;
    if(x<0||y<0||z<0||x>=W||y>=H||z>=L) return 1;
    if(S[g[I(x,y,z)]].flags&FL_FULL) return 0;
  }
}
static inline int anyView(float px,float py,float pz,const int*nd,int *last){
  for(int i=0;i<NV;i++){ int v=(*last+i)%NV; float *V=&VW[v*3];
    if(nd&&((V[0]-px)*nd[0]+(V[1]-py)*nd[1]+(V[2]-pz)*nd[2])<=0.0f)continue;
    if(clearRay(px,py,pz,V[0],V[1],V[2])){*last=v;return 1;} }
  return 0;
}
static void computeVis(int ymin){
  size_t n=(size_t)W*H*L; vmask=calloc(n,1); size_t seenf=0,tested=0;
  #pragma omp parallel for schedule(dynamic,1) reduction(+:seenf,tested)
  for(int y=ymin;y<H;y++){ int last=0; for(int z=0;z<L;z++)for(int x=0;x<W;x++){ size_t k=I(x,y,z); State*s=&S[g[k]];
      if(s->flags&FL_INVIS&&!s->crossH)continue; if(!s->nb&&!s->crossH)continue;
      int nearExt=ext[k]; for(int d=0;d<6&&!nearExt;d++) nearExt=isExt(x+DIR[d][0],y+DIR[d][1],z+DIR[d][2]);
      if(!nearExt)continue;
      uint8_t m=0;
      int full=(s->nb==1&&s->b[0].lo[0]==0&&s->b[0].lo[1]==0&&s->b[0].lo[2]==0&&s->b[0].hi[0]==16&&s->b[0].hi[1]==16&&s->b[0].hi[2]==16);
      if(full){
        for(int d=0;d<6;d++){ if(fl(x+DIR[d][0],y+DIR[d][1],z+DIR[d][2])&FL_FULL)continue; tested++;
          int au=AXU[d],av=AXV[d]; float c[3]={x+.5f+DIR[d][0]*.52f,y+.5f+DIR[d][1]*.52f,z+.5f+DIR[d][2]*.52f};
          static const float SA[5]={0,-.42f,.42f,.42f,-.42f},SB[5]={0,-.42f,-.42f,.42f,.42f}; int vis=0;
          for(int q=0;q<5&&!vis;q++){ float p[3]={c[0],c[1],c[2]}; p[au]+=SA[q]; p[av]+=SB[q]; vis=anyView(p[0],p[1],p[2],DIR[d],&last); }
          if(vis){m|=(uint8_t)(1<<d);seenf++;}
        }
      } else {
        tested++;
        static const float OX[5]={.5f,.2f,.8f,.5f,.5f},OY[5]={.5f,.5f,.5f,.15f,.85f},OZ[5]={.5f,.5f,.5f,.2f,.8f}; int vis=0;
        for(int q=0;q<5&&!vis;q++) vis=anyView(x+OX[q],y+OY[q],z+OZ[q],NULL,&last);
        if(vis){m|=64;seenf++;}
      }
      vmask[k]=m;
    }
  }
  fprintf(stderr,"view cull: %d views, %zu of %zu faces seen\n",NV,seenf,tested);
  // a face next to a visible face stays too, so the steps between sampled viewpoints can't open a crack
  uint8_t *t2=malloc(n);
  for(int it=0;it<2;it++){ memcpy(t2,vmask,n);
    #pragma omp parallel for schedule(dynamic,4)
    for(int y=ymin;y<H;y++)for(int z=0;z<L;z++)for(int x=0;x<W;x++){ uint8_t m=t2[I(x,y,z)]; if(!m)continue;
      for(int d=0;d<6;d++) if(m&(1<<d)){ int au=AXU[d],av=AXV[d];
        for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++){ int p[3]={x,y,z}; p[au]+=a; p[av]+=b; if(inb(p[0],p[1],p[2])){
          #pragma omp atomic
          vmask[I(p[0],p[1],p[2])]|=(uint8_t)(1<<d); } } }
      if(m&64) for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++)for(int c=-1;c<=1;c++){ if(inb(x+a,y+b,z+c)){
          #pragma omp atomic
          vmask[I(x+a,y+b,z+c)]|=64; } }
    }
  }
  free(t2);
}

int main(int argc,char**argv){
  if(argc<8){fprintf(stderr,"usage: bake grid W H L states out ymin [sun...]\n");return 1;}
  W=atoi(argv[2]);H=atoi(argv[3]);L=atoi(argv[4]); int ymin=atoi(argv[7]); float dropOpen=argc>8?atof(argv[8]):1;
  size_t n=(size_t)W*H*L; g=malloc(n*2); FILE*f=fopen(argv[1],"rb"); fread(g,2,n,f); fclose(f);
  f=fopen(argv[5],"rb"); fread(&ns,4,1,f); S=calloc(ns,sizeof(State));
  for(int i=0;i<ns;i++){ uint8_t h[4]; uint16_t cc; fread(h,1,4,f); fread(&cc,2,1,f); S[i].flags=h[0];S[i].emit=h[1];S[i].nb=h[2];S[i].crossH=h[3];S[i].crossC=cc; S[i].b=malloc(sizeof(Box)*(h[2]+1));
    for(int j=0;j<h[2];j++){ fread(S[i].b[j].lo,1,3,f); fread(S[i].b[j].hi,1,3,f); fread(S[i].b[j].fc,2,6,f);} }
  fclose(f);
  float suns[3][3]={{0.85f,0.28f,-0.42f},{-0.80f,0.30f,-0.18f},{0.25f,0.92f,0.30f}}; for(int s=0;s<3;s++){memcpy(SUN[s],suns[s],12);norm(SUN[s]);}
  initDirs(); flood(); blocklight(); size_t n2=n; (void)n2;
  if(argc>9&&argv[9][0]){ FILE*vf=fopen(argv[9],"r"); if(vf){ VW=malloc(sizeof(float)*3*20000); while(NV<20000&&fscanf(vf,"%f %f %f",&VW[NV*3],&VW[NV*3+1],&VW[NV*3+2])==3)NV++; fclose(vf); computeVis(ymin); } }
  svg=calloc(n,1);
  { size_t need=0;
    #pragma omp parallel for schedule(dynamic,64) reduction(+:need)
    for(int y=0;y<H;y++){ uint32_t seed=0x12345u^(uint32_t)(y*2654435761u)|1u; for(int z=0;z<L;z++)for(int x=0;x<W;x++){ size_t k=I(x,y,z); if(S[g[k]].flags&FL_FULL)continue; int nb=0;
        for(int d=0;d<6&&!nb;d++){int X=x+DIR[d][0],Y=y+DIR[d][1],Z=z+DIR[d][2]; if(inb(X,Y,Z)&&!(S[g[I(X,Y,Z)]].flags&FL_INVIS))nb=1;}
        if(!nb){ for(int a=-1;a<=1&&!nb;a++)for(int b=-1;b<=1&&!nb;b++)for(int c=-1;c<=1&&!nb;c++){int X=x+a,Y=y+b,Z=z+c; if(inb(X,Y,Z)&&!(S[g[I(X,Y,Z)]].flags&FL_INVIS))nb=1;} }
        if(!nb){svg[k]=255;continue;}
        need++;
        float ox=x+.5f,oy=y+.5f,oz=z+.5f,acc=0,tw=0;
        for(int i=0;i<NSPH;i++){ float up=SY[i]; float w=up>0?(.55f+up*.9f):(.32f+up*.14f); tw+=w; if(rayEscapes(ox,oy,oz,SX[i],SY[i],SZ[i],64,&seed))acc+=w; }
        int v=(int)(255*fminf(1.f,acc/tw*1.35f)); svg[k]=v; } }
    fprintf(stderr,"sky cells %zu\n",need); }
  size_t extc=0; for(size_t k=0;k<n;k++)extc+=ext[k]; fprintf(stderr,"ext cells %zu of %zu\n",extc,n);
  int CX=(W+15)/16,CY=(H+15)/16,CZ=(L+15)/16; int nc=CX*CY*CZ; Buf *bufs=calloc(nc,sizeof(Buf));
  size_t faces=0,dropped=0,culled=0;
  #pragma omp parallel for schedule(dynamic,8) reduction(+:faces,dropped,culled)
  for(int ci=0;ci<nc;ci++){
    int ccy=ci/(CX*CZ); int r=ci%(CX*CZ); int ccz=r/CX; int ccx=r%CX; int cx=ccx*16,cy=ccy*16,cz=ccz*16; if(cy+16<=ymin)continue;
    Cell *mask=calloc(6*16*16*16,sizeof(Cell)); uint32_t seed=0x9E3779B9u^(uint32_t)(ci*2654435761u)|1u; Buf*B=&bufs[ci]; int any=0;
    for(int y=cy;y<cy+16&&y<H;y++){ if(y<ymin)continue; for(int z=cz;z<cz+16&&z<L;z++)for(int x=cx;x<cx+16&&x<W;x++){
      State*s=&S[g[I(x,y,z)]]; if(s->flags&FL_INVIS&&!s->crossH)continue; if(!s->nb&&!s->crossH)continue;
      int selfExt=ext[I(x,y,z)];
      int nearExt=selfExt; for(int d=0;d<6&&!nearExt;d++) nearExt=isExt(x+DIR[d][0],y+DIR[d][1],z+DIR[d][2]);
      if(!nearExt)continue;
      int full=(s->nb==1&&s->b[0].lo[0]==0&&s->b[0].lo[1]==0&&s->b[0].lo[2]==0&&s->b[0].hi[0]==16&&s->b[0].hi[1]==16&&s->b[0].hi[2]==16);
      if(full){
        for(int d=0;d<6;d++){ int nx=x+DIR[d][0],ny=y+DIR[d][1],nz=z+DIR[d][2]; if(s->b[0].fc[d]==0xffff)continue; if(!isExt(nx,ny,nz))continue;
          if(inb(nx,ny,nz)&&g[I(nx,ny,nz)]==g[I(x,y,z)]&&!(s->flags&FL_FULL))continue;
          if(NV&&!(vmask[I(x,y,z)]&(1<<d))){culled++;continue;}
          float px=x+.5f+DIR[d][0]*.5f,py=y+.5f+DIR[d][1]*.5f,pz=z+.5f+DIR[d][2]*.5f;
          int nl=inb(nx,ny,nz)?lit[I(nx,ny,nz)]:0; LightV lv=shade(px,py,pz,d,&seed,nl); faces++;
          if(lv.open<dropOpen){dropped++;continue;}
          int au=AXU[d],av=AXV[d]; int du[3]={0,0,0},dv[3]={0,0,0}; du[au]=1;dv[av]=1;
          int a=solidAO(nx-du[0],ny-du[1],nz-du[2]),b=solidAO(nx+du[0],ny+du[1],nz+du[2]),c=solidAO(nx-dv[0],ny-dv[1],nz-dv[2]),e=solidAO(nx+dv[0],ny+dv[1],nz+dv[2]);
          int c00=solidAO(nx-du[0]-dv[0],ny-du[1]-dv[1],nz-du[2]-dv[2]),c10=solidAO(nx+du[0]-dv[0],ny+du[1]-dv[1],nz+du[2]-dv[2]),c11=solidAO(nx+du[0]+dv[0],ny+du[1]+dv[1],nz+du[2]+dv[2]),c01=solidAO(nx-du[0]+dv[0],ny-du[1]+dv[1],nz-du[2]+dv[2]);
          int a0=vAO(a,c,c00),a1=vAO(b,c,c10),a2=vAO(b,e,c11),a3=vAO(a,e,c01);
          int sk[4],bl[4]; static const int SU[4]={-1,1,1,-1},SV[4]={-1,-1,1,1}; for(int q=0;q<4;q++)cornerLight(nx,ny,nz,du,dv,SU[q],SV[q],&sk[q],&bl[q]);
          int pos[3]={x-cx,y-cy,z-cz}; int layer=pos[AXN[d]],u=pos[au],v=pos[av];
          Cell*m=&mask[(((d*16+layer)*16+v)*16)+u]; m->color=s->b[0].fc[d]; m->ao=a0|(a1<<2)|(a2<<4)|(a3<<6); m->sky01=(sk[0]<<4)|sk[1]; m->sky23=(sk[2]<<4)|sk[3]; m->blk01=(bl[0]<<4)|bl[1]; m->blk23=(bl[2]<<4)|bl[3]; m->sunAB=lv.sunAB; m->sunC=lv.sunC; m->used=1; any=1;
        }
      } else {
        // partial geometry: one quad per box face
        if(NV&&!(vmask[I(x,y,z)]&64)){culled++;continue;}
        for(int j=0;j<s->nb;j++){ Box*bx=&s->b[j];
          for(int d=0;d<6;d++){ if(bx->fc[d]==0xffff)continue;
            int an=AXN[d],au=AXU[d],av=AXV[d]; int thick=(bx->hi[an]-bx->lo[an]);
            int planeOn=(DIR[d][an]>0)?(bx->hi[an]>=16):(bx->lo[an]<=0);
            int nx=x+DIR[d][0],ny=y+DIR[d][1],nz=z+DIR[d][2];
            if(planeOn){ uint8_t nf=fl(nx,ny,nz); if(nf&FL_FULL)continue; }
            if(thick==0&&DIR[d][an]<0&&0)continue;
            int su=bx->hi[au]-bx->lo[au],sv=bx->hi[av]-bx->lo[av]; if(su<=0||sv<=0)continue;
            int base[3]={x*16,y*16,z*16}; int p[3];
            p[an]=base[an]+((DIR[d][an]>0)?bx->hi[an]:bx->lo[an]); p[au]=base[au]+bx->lo[au]; p[av]=base[av]+bx->lo[av];
            float px=(p[0]+(au==0?su/2.f:0)+(av==0?sv/2.f:0))/16.f,py=(p[1]+(au==1?su/2.f:0)+(av==1?sv/2.f:0))/16.f,pz=(p[2]+(au==2?su/2.f:0)+(av==2?sv/2.f:0))/16.f;
            int nl=planeOn?(inb(nx,ny,nz)?lit[I(nx,ny,nz)]:0):lit[I(x,y,z)]; if(nl<lit[I(x,y,z)])nl=lit[I(x,y,z)];
            LightV lv=shade(px,py,pz,d,&seed,nl); faces++; if(lv.open<dropOpen){dropped++;continue;}
            int dU[3]={0,0,0},dV[3]={0,0,0}; dU[au]=1;dV[av]=1; int fx=planeOn?nx:x,fy=planeOn?ny:y,fz=planeOn?nz:z; int sk[4],bl[4]; static const int SUc[4]={-1,1,1,-1},SVc[4]={-1,-1,1,1};
            for(int q2=0;q2<4;q2++)cornerLight(fx,fy,fz,dU,dV,SUc[q2],SVc[q2],&sk[q2],&bl[q2]);
            Quad q={(int16_t)p[0],(int16_t)p[1],(int16_t)p[2],(uint16_t)(su|(d<<13)),(uint16_t)sv,bx->fc[d],(uint8_t)((sk[0]<<4)|sk[1]),(uint8_t)((sk[2]<<4)|sk[3]),(uint8_t)((bl[0]<<4)|bl[1]),(uint8_t)((bl[2]<<4)|bl[3]),0xFF,lv.sunAB,lv.sunC,0}; push(B,q);
          }
        }
        if(s->crossH){ float px=x+.5f,py=y+.5f,pz=z+.5f; LightV lv=shade(px,py,pz,1,&seed,lit[I(x,y,z)]); faces++;
          if(lv.open>=dropOpen){ int hh=s->crossH;
            int sq=svg[I(x,y,z)]>>4, bq=lit[I(x,y,z)]; uint8_t s2=(sq<<4)|sq,b2=(bq<<4)|bq;
            Quad a={(int16_t)(x*16+2),(int16_t)(y*16),(int16_t)(z*16+2),(uint16_t)(12|(6<<13)),(uint16_t)hh,s->crossC,s2,s2,b2,b2,0xFF,lv.sunAB,lv.sunC,0}; push(B,a);
            Quad c={(int16_t)(x*16+2),(int16_t)(y*16),(int16_t)(z*16+14),(uint16_t)(12|(7<<13)),(uint16_t)hh,s->crossC,s2,s2,b2,b2,0xFF,lv.sunAB,lv.sunC,0}; push(B,c);
          } else dropped++; }
      }
    }}
    if(any){ // greedy merge per face per layer
      for(int d=0;d<6;d++)for(int layer=0;layer<16;layer++){ Cell*m=&mask[(d*16+layer)*16*16];
        for(int v=0;v<16;v++)for(int u=0;u<16;){ Cell*c=&m[v*16+u]; if(!c->used){u++;continue;}
          int du=1; while(u+du<16){Cell*o=&m[v*16+u+du]; if(!o->used||o->color!=c->color||o->sky01!=c->sky01||o->sky23!=c->sky23||o->blk01!=c->blk01||o->blk23!=c->blk23||o->sunAB!=c->sunAB||o->sunC!=c->sunC||o->ao!=c->ao||!flatCell(c))break; du++;}
          int dv=1,ok=1; while(ok&&v+dv<16){ for(int k=0;k<du;k++){Cell*o=&m[(v+dv)*16+u+k]; if(!o->used||o->color!=c->color||o->sky01!=c->sky01||o->sky23!=c->sky23||o->blk01!=c->blk01||o->blk23!=c->blk23||o->sunAB!=c->sunAB||o->sunC!=c->sunC||o->ao!=c->ao||!flatCell(c)){ok=0;break;}} if(ok)dv++; }
          Cell tmp=*c; emitRect(B,d,cx,cy,cz,layer,u,v,du,dv,&tmp);
          for(int a=0;a<dv;a++)for(int k=0;k<du;k++)m[(v+a)*16+u+k].used=0; u+=du;
        }
      }
    }
    free(mask);
  }
  size_t total=0; for(int i=0;i<nc;i++)total+=bufs[i].n; fprintf(stderr,"faces %zu dropped %zu culled %zu quads %zu\n",faces,dropped,culled,total);
  { size_t band[8]={0}; for(int i=0;i<nc;i++){int cy=i/(CX*CZ); int b=cy*16/50; if(b>7)b=7; band[b]+=bufs[i].n;} for(int b=0;b<8;b++)fprintf(stderr,"y%3d-%3d: %zu\n",b*50,b*50+49,band[b]); }
  // output: u32 nquads, u32 nchunks, then chunk table [cx,cy,cz(u16) offset(u32) count(u32)], quads
  { char lp[512]; snprintf(lp,512,"%s.lights",argv[6]); FILE*lf=fopen(lp,"wb"); for(int y=ymin;y<H;y++)for(int z=0;z<L;z++)for(int x=0;x<W;x++){ int e=S[g[I(x,y,z)]].emit; if(e<8)continue; int nearExt=ext[I(x,y,z)]; for(int d=0;d<6&&!nearExt;d++)nearExt=isExt(x+DIR[d][0],y+DIR[d][1],z+DIR[d][2]); if(!nearExt)continue; int16_t r[4]={(int16_t)x,(int16_t)y,(int16_t)z,(int16_t)e}; fwrite(r,2,4,lf);} fclose(lf);}
  f=fopen(argv[6],"wb"); uint32_t tq=total,tc=0; for(int i=0;i<nc;i++)if(bufs[i].n)tc++; fwrite(&tq,4,1,f);fwrite(&tc,4,1,f); uint32_t off=0;
  for(int i=0;i<nc;i++)if(bufs[i].n){ int ccy=i/(CX*CZ); int r=i%(CX*CZ); int ccz=r/CX; int ccx=r%CX; uint16_t c[4]={ccx,ccy,ccz,0}; fwrite(c,2,4,f); uint32_t cnt=bufs[i].n; fwrite(&off,4,1,f); fwrite(&cnt,4,1,f); off+=cnt; }
  for(int i=0;i<nc;i++)if(bufs[i].n)fwrite(bufs[i].q,sizeof(Quad),bufs[i].n,f);
  fclose(f); return 0;
}
