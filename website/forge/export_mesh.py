"""Sort baked quads into draw regions and pack them as one gzipped AEM2 file."""
import numpy as np, struct, json, gzip, sys
def export(base, out, meta, region=64, level=9):
    b=open(base+'.quads','rb').read(); nq,nc=struct.unpack('<II',b[:8]); q0=8+16*nc
    dt=np.dtype([('x','<i2'),('y','<i2'),('z','<i2'),('wf','<u2'),('h','<u2'),('c','<u2'),('s01','u1'),('s23','u1'),('b01','u1'),('b23','u1'),('ao','u1'),('ab','u1'),('c3','u1'),('pad','u1')])
    a=np.frombuffer(b,dt,nq,q0).copy()
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
