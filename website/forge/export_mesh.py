"""Sort baked quads into draw regions and pack them as one gzipped AEM2 file."""
import numpy as np, struct, json, gzip, sys
AXU=np.array([0,2,1,0,2,1]); AXV=np.array([2,0,0,1,1,2]); AXN=np.array([1,1,2,2,0,0])

def _nib(v,hi): return (v>>4) if hi else (v&15)

def merge_level(a, may, size):
    """Four quads of one face, each `size` (1/16 block) square, same colour, tiling an aligned square twice as
    wide, become one quad. Its corners keep the light and shade of the four outer corners, so the surface
    looks the same from afar, at a quarter of the cost. Only quads where `may` holds take part."""
    face=(a['wf']>>13).astype(np.int64); w=(a['wf']&0x1fff).astype(np.int64); h=a['h'].astype(np.int64)
    idx=np.flatnonzero(may&(face<6)&(w==size)&(h==size))
    if not len(idx): return a,may,0
    P=np.stack([a['x'][idx],a['y'][idx],a['z'][idx]],1).astype(np.int64); f=face[idx]; r=np.arange(len(idx))
    pu=P[r,AXU[f]]; pv=P[r,AXV[f]]; pn=P[r,AXN[f]]; S=size*2
    cu=np.floor_divide(pu,S); cv=np.floor_divide(pv,S); pos=((pu-cu*S)//size)+2*((pv-cv*S)//size)
    key=np.stack([f,pn,cu,cv,a['c'][idx].astype(np.int64)],1)
    _,inv,cnt=np.unique(key,axis=0,return_inverse=True,return_counts=True); inv=inv.ravel()
    full=cnt[inv]==4
    if not full.any(): return a,may,0
    gid=inv[full]; G,gi=np.unique(gid,return_inverse=True)
    M=np.empty((len(G),4),np.int64); M[gi,pos[full]]=idx[full]
    m0,m1,m2,m3=M[:,0],M[:,1],M[:,3],M[:,2]  # corners 0..3 sit at (0,0) (1,0) (1,1) (0,1)
    q=a[m0].copy()
    q['wf']=(S|(face[m0]<<13)).astype(np.uint16); q['h']=S
    for lo,hi in (('s01','s23'),('b01','b23')):
        q[lo]=(_nib(a[lo][m0],1)<<4)|_nib(a[lo][m1],0); q[hi]=(_nib(a[hi][m2],1)<<4)|_nib(a[hi][m3],0)
    q['ao']=(a['ao'][m0]&3)|(a['ao'][m1]&12)|(a['ao'][m2]&48)|(a['ao'][m3]&192)
    ab=np.stack([a['ab'][M[:,k]] for k in range(4)],1).astype(np.int64); c3=np.stack([a['c3'][M[:,k]] for k in range(4)],1).astype(np.int64)
    q['ab']=(((ab>>4).mean(1).round().astype(np.int64)<<4)|((ab&15).mean(1).round().astype(np.int64))).astype(np.uint8)
    q['c3']=(((c3>>4).mean(1).round().astype(np.int64))<<4).astype(np.uint8)
    gone=np.zeros(len(a),bool); gone[M.ravel()]=True
    return np.concatenate([a[~gone],q]),np.concatenate([may[~gone],np.ones(len(q),bool)]),len(q)

def export(base, out, meta, region=64, level=9, merge=None):
    """`merge(x, z)`: blocks (mesh coordinates) where neighbouring quads may be merged, up to 8 x 8 blocks."""
    b=open(base+'.quads','rb').read(); nq,nc=struct.unpack('<II',b[:8]); q0=8+16*nc
    dt=np.dtype([('x','<i2'),('y','<i2'),('z','<i2'),('wf','<u2'),('h','<u2'),('c','<u2'),('s01','u1'),('s23','u1'),('b01','u1'),('b23','u1'),('ao','u1'),('ab','u1'),('c3','u1'),('pad','u1')])
    a=np.frombuffer(b,dt,nq,q0).copy()
    if merge is not None:
        may=merge(a['x'].astype(np.int64)//16,a['z'].astype(np.int64)//16)
        for size in (16,32,64):
            a,may,n=merge_level(a,may,size)
            print('merged %d quads into %d-block squares'%(n*4,size//8))
        nq=len(a)
    face=a['wf']>>13
    rx=(a['x']//16)//region; ry=(a['y']//16)//region; rz=(a['z']//16)//region
    plant=(face>=6).astype(np.int64)
    key=(ry.astype(np.int64)*1000000+rz.astype(np.int64)*1000+rx.astype(np.int64))
    order=np.lexsort((face,a['c'],plant,key))
    a=a[order]; key=key[order]; plant=plant[order]; face=face[order]
    w=(a['wf']&0x1fff).astype(float)/16; h=a['h'].astype(float)/16
    px=a['x'].astype(float)/16;py=a['y'].astype(float)/16;pz=a['z'].astype(float)/16
    uniq,starts=np.unique(key,return_index=True); ends=np.append(starts[1:],nq)
    regs=[]
    for s,e in zip(starts,ends):
        solid=int((plant[s:e]==0).sum()); pl=int(e-s-solid)
        # region bounds from quad origins, generous by the largest quad
        c=[(px[s:e].min()+px[s:e].max())/2,(py[s:e].min()+py[s:e].max())/2,(pz[s:e].min()+pz[s:e].max())/2]
        r=float(np.sqrt(((px[s:e]-c[0])**2+(py[s:e]-c[1])**2+(pz[s:e]-c[2])**2).max()))+24
        regs.append((int(s),solid,pl,*c,r))
    colors=meta['colors']; W,H,L=meta['size']
    lights=np.fromfile(base+'.quads.lights',dtype='<i2').reshape(-1,4)
    # order within file: header
    hdr=struct.pack('<IIIII3f',0x324d4541,nq,len(regs),len(colors),len(lights),W,H,L)
    rb=b''.join(struct.pack('<III4f',*r) for r in regs)
    cb=b''.join(struct.pack('<BBBBBBBB',c[0],c[1],c[2],(c[3]<<4)|c[4],c[5],0,0,0) for c in colors)
    lb=b''.join(struct.pack('<hhhBB',int(l[0]),int(l[1]),int(l[2]),int(l[3]),0) for l in lights)
    soa=b''.join(a[n].tobytes() for n in a.dtype.names if n!='pad')
    raw=hdr+rb+cb+lb+soa
    gz=gzip.compress(raw,level)
    open(out,'wb').write(gz)
    print(out,'quads',nq,'regions',len(regs),'lights',len(lights),'raw',len(raw),'gz',len(gz))
if __name__=='__main__':
    base=sys.argv[1]; out=sys.argv[2]
    export(base,out,json.load(open(base+'.meta.json')))
