"""Minimal Sponge schematic reader (v2/v3) -> numpy (y,z,x) palette-index array."""
import gzip, struct, io
import numpy as np

def _read_payload(f,t):
    if t==1: return struct.unpack('>b',f.read(1))[0]
    if t==2: return struct.unpack('>h',f.read(2))[0]
    if t==3: return struct.unpack('>i',f.read(4))[0]
    if t==4: return struct.unpack('>q',f.read(8))[0]
    if t==5: return struct.unpack('>f',f.read(4))[0]
    if t==6: return struct.unpack('>d',f.read(8))[0]
    if t==7:
        n=struct.unpack('>i',f.read(4))[0]; return f.read(n)
    if t==8:
        n=struct.unpack('>H',f.read(2))[0]; return f.read(n).decode('utf-8','replace')
    if t==9:
        et,n=struct.unpack('>bi',f.read(5)); return [_read_payload(f,et) for _ in range(n)]
    if t==10:
        d={}
        while True:
            tt=f.read(1)[0]
            if tt==0: return d
            nl=struct.unpack('>H',f.read(2))[0]; k=f.read(nl).decode('utf-8','replace')
            d[k]=_read_payload(f,tt)
    if t==11:
        n=struct.unpack('>i',f.read(4))[0]; return np.frombuffer(f.read(4*n),dtype='>i4')
    if t==12:
        n=struct.unpack('>i',f.read(4))[0]; return np.frombuffer(f.read(8*n),dtype='>i8')
    raise ValueError(t)

def read_nbt(path):
    raw=open(path,'rb').read()
    if raw[:2]==b'\x1f\x8b': raw=gzip.decompress(raw)
    f=io.BytesIO(raw); t=f.read(1)[0]
    nl=struct.unpack('>H',f.read(2))[0]; name=f.read(nl).decode()
    return name,_read_payload(f,t)

def decode_varints(b,count):
    a=np.frombuffer(b,dtype=np.uint8)
    if (a<128).all():
        return a.astype(np.int32)[:count]
    # vectorised varint decode (values < 2^21)
    cont=a>=128
    ends=np.flatnonzero(~cont)
    starts=np.concatenate(([0],ends[:-1]+1))
    out=np.zeros(len(ends),dtype=np.int32)
    lens=ends-starts+1
    out=(a[starts]&0x7f).astype(np.int32)
    m=lens>=2; out[m]|=((a[starts[m]+1]&0x7f).astype(np.int32))<<7
    m=lens>=3; out[m]|=((a[starts[m]+2]&0x7f).astype(np.int32))<<14
    m=lens>=4; out[m]|=((a[starts[m]+3]&0x7f).astype(np.int32))<<21
    return out[:count]

def load(path):
    name,root=read_nbt(path)
    s=root.get('Schematic',root)
    W,H,L=s['Width']&0xffff,s['Height']&0xffff,s['Length']&0xffff
    if 'Blocks' in s:   # v3
        bl=s['Blocks']; pal=bl['Palette']; data=bl['Data']
    else:
        pal=s['Palette']; data=s['BlockData']
    inv=[None]*(max(pal.values())+1)
    for k,v in pal.items(): inv[v]=k
    arr=decode_varints(data,W*H*L).reshape(H,L,W)
    return arr,inv,dict(W=W,H=H,L=L,version=s.get('Version'),offset=s.get('Offset'),meta=s.get('Metadata'))

if __name__=='__main__':
    import sys,collections
    arr,inv,info=load(sys.argv[1])
    print(info['W'],info['H'],info['L'],'palette',len(inv),'version',info['version'],'offset',info['offset'])
    air=[i for i,n in enumerate(inv) if n.split('[')[0] in('minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:structure_void','minecraft:barrier','minecraft:light')]
    nonair=(~np.isin(arr,air)).sum()
    print('nonair',nonair)
    c=np.bincount(arr.ravel(),minlength=len(inv))
    agg=collections.Counter()
    for i,n in enumerate(c): agg[inv[i].split('[')[0]]+=int(n)
    for k,v in agg.most_common(40): print(v,k)
    print('unique names',len(agg))
