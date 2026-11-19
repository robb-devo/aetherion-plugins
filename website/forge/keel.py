"""Coarsen natural rock and roots well below the surface. Far from the camera they read as rock
either way; at block resolution they cost half the mesh."""
import numpy as np, re
NAT = re.compile(r'^minecraft:(stone|deepslate|tuff|andesite|granite|diorite|dirt|coarse_dirt|rooted_dirt|mud|muddy_mangrove_roots|mangrove_roots|cobbled_deepslate|cobblestone|smooth_basalt|basalt|gravel|dripstone_block|calcite|clay|moss_block|blackstone|sandstone|packed_mud)(\[.*)?$')
PLANT = re.compile(r'^minecraft:(hanging_roots|glow_lichen|vine|cave_vines|cave_vines_plant|spore_blossom|mangrove_propagule|pointed_dripstone)(\[.*)?$')
def coarsen(arr, pal, lut_ids, depth=9, cell=3, keep_fine=None, seed=7):
    """arr: (H,L,W) of raw palette indices. returns new array."""
    H,L,W=arr.shape
    natural=np.zeros(len(pal),bool)
    for i,n in enumerate(pal):
        if NAT.match(n): natural[i]=True
    air=[i for i,n in enumerate(pal) if n.split('[')[0] in ('minecraft:air','minecraft:cave_air','minecraft:void_air')]
    is_air=np.zeros(len(pal),bool); is_air[air]=True
    plant=np.zeros(len(pal),bool)
    for i,n in enumerate(pal):
        if PLANT.match(n): plant[i]=True
    nat=natural[arr]; solid=~is_air[arr]&~plant[arr]
    # depth below the first solid block from above, per column
    top=np.where(solid.any(axis=0), H-1-np.argmax(solid[::-1],axis=0), -1)
    Y=np.arange(H)[:,None,None]
    deep=(Y < (top[None,:,:]-depth)) & nat
    # also: anything natural under an overhang stays deep only if it is not near a built block
    if keep_fine is not None: deep &= ~keep_fine
    H3,L3,W3=H//cell,L//cell,W//cell
    a=deep[:H3*cell,:L3*cell,:W3*cell].reshape(H3,cell,L3,cell,W3,cell)
    frac=a.sum(axis=(1,3,5))
    v=arr[:H3*cell,:L3*cell,:W3*cell].reshape(H3,cell,L3,cell,W3,cell)
    # dominant block of a cell: first deep voxel, skipping thin plants
    flat=a.transpose(0,2,4,1,3,5).reshape(H3,L3,W3,-1)
    vflat=v.transpose(0,2,4,1,3,5).reshape(H3,L3,W3,-1)
    first=flat.argmax(axis=3)
    dom=np.take_along_axis(vflat,first[...,None],axis=3)[...,0]
    fill=frac>=int(0.42*cell**3)
    out=arr.copy()
    # thin plants deep under the island: keep a sparse fringe
    rs=np.random.RandomState(seed)
    pm=plant[arr]; pm&=(Y<(top[None,:,:]-depth))
    if keep_fine is not None: pm&=~keep_fine
    drop=pm&(rs.random_sample(arr.shape)>0.22)
    out[drop]=air[0]
    o=out[:H3*cell,:L3*cell,:W3*cell]
    cellmap=np.repeat(np.repeat(np.repeat(fill,cell,0),cell,1),cell,2)
    dommap=np.repeat(np.repeat(np.repeat(dom,cell,0),cell,1),cell,2)
    dp=deep[:H3*cell,:L3*cell,:W3*cell]; ai=is_air[o]
    # inside filled cells: deep and air voxels become the dominant block (near-surface air next to builds stays air)
    near_built=(solid&~nat)[:H3*cell,:L3*cell,:W3*cell]
    below=(Y[:H3*cell]< (top[None,:L3*cell,:W3*cell]-depth))
    set_mask=cellmap&(dp|(ai&below))
    o[set_mask]=dommap[set_mask]
    # outside filled cells: deep voxels vanish
    o[(~cellmap)&dp]=air[0]
    return out
