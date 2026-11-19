"""Vanilla structure .nbt -> schem-like (arr, palette)."""
import numpy as np
from schem import read_nbt
def load(path):
    name,root=read_nbt(path)
    sx,sy,sz=[int(v) for v in root['size']]
    pal=[]
    for p in root['palette']:
        n=p['Name']; pr=p.get('Properties') or {}
        pal.append(n+('['+','.join(f'{k}={v}' for k,v in sorted(pr.items()))+']' if pr else ''))
    if 'minecraft:air' not in pal: pal.append('minecraft:air')
    air=pal.index('minecraft:air')
    arr=np.full((sy,sz,sx),air,dtype=np.int32)
    for b in root['blocks']:
        x,y,z=[int(v) for v in b['pos']]
        arr[y,z,x]=int(b['state'])
    return arr,pal,dict(W=sx,H=sy,L=sz)
