#!/usr/bin/env python3
"""Bakes the website's 3D scenes from the game's own builds.

  city    the walled city past the fog (schematic, own build)
  isles   the four starter islands players get (Guilds templates, own builds)
  prison  floor 1 of the Warden's Prison, cut open (Dungeons structures, own builds)
  island  the main island, straight from the server's world folder (region files)

Usage: MC_ASSETS=<node_modules with minecraft-assets + minecraft-data> build_scenes.py <repo-root> <exile.schem> <out-dir> [views.bin]
       SCENES=island ISLAND=<world>/region ISLAND_BOX=x0,z0,x1,z1 [ISLAND_Y0=..] [ISLAND_FINE="x,z,r;x,z,r"] [ISLAND_MID="x,z,r;..."]
         [ISLAND_LOD=2] [ISLAND_VIEWS=<file>] build_scenes.py ...
Only averaged colours leave this script; no game textures ship with the site.
"""
import numpy as np, json, subprocess, sys, os, time, struct
HERE=os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0,HERE)
import schem, tables, keel, nbtstruct, export_mesh, anvil
WORK=os.environ.get('FORGE_WORK','/tmp/forge-work'); os.makedirs(WORK,exist_ok=True)
BAKE=os.path.join(WORK,'bake')
def compile_baker():
    subprocess.run(['gcc','-O3','-march=native','-fopenmp','-o',BAKE,os.path.join(HERE,'bake.c'),'-lm','-w'],check=True)

def lamp_list(arr,pal,glow):
    """Every lamp of the full-detail grid that touches open air, in the baker's own format (x, y, z, level), so
    lanterns keep their glow where the grid is later drawn in coarse cells."""
    emit=np.zeros(len(pal),np.int16)
    for i,st in enumerate(pal):
        if not glow[i]: continue
        name=st.replace('minecraft:','').split('[')[0]
        props=dict(kv.split('=') for kv in st[st.index('[')+1:-1].split(',')) if '[' in st else {}
        emit[i]=tables.emission(name,props)
    y,z,x=np.nonzero(emit[arr]>=8)
    air=np.array([n.split('[')[0] in anvil.AIR for n in pal])
    H,L,W=arr.shape; seen=np.zeros(len(x),bool)
    for dy,dz,dx in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
        yy,zz,xx=y+dy,z+dz,x+dx; ok=(yy>=0)&(yy<H)&(zz>=0)&(zz<L)&(xx>=0)&(xx<W)
        o=np.zeros(len(x),bool); o[ok]=air[arr[yy[ok],zz[ok],xx[ok]]]; seen|=o|~ok
    out=np.stack([x,y,z,emit[arr[y,z,x]]],1)[seen].astype('<i2')
    print('island: %d lamps'%len(out))
    return out

def bake(name,arr,pal,out,views=None,drop=2,sea=False,merge=None,lamps=None):
    H,L,W=arr.shape
    base=os.path.join(WORK,name)
    colors,unknown,lut=tables.build(pal,base+'.states',sea=sea)
    if unknown: print(name,'unknown blocks',sorted(unknown.items(),key=lambda x:-x[1])[:12])
    g=lut[arr].astype(np.uint16); g.tofile(base+'.grid')
    json.dump({'colors':colors.list,'size':[W,H,L]},open(base+'.meta.json','w'))
    t=time.time()
    cmd=[BAKE,base+'.grid',str(W),str(H),str(L),base+'.states',base+'.quads','0',str(drop)]
    if views: cmd.append(views)
    r=subprocess.run(cmd,capture_output=True,text=True)
    print(name,r.stderr.strip().split('\n')[-10:][0:2],'%.1fs'%(time.time()-t))
    if r.returncode: print(r.stderr); raise SystemExit(1)
    if lamps is not None: lamps.tofile(base+'.quads.lights')
    export_mesh.export(base,out,json.load(open(base+'.meta.json')),merge=merge)

def scene_city(path,out,views=None):
    arr,pal,info=schem.load(path)
    off=info['offset']; ox,oy,oz=int(off[0]),int(off[1]),int(off[2])
    air=pal.index('minecraft:air')
    H,L,W=arr.shape
    Y,Z,X=np.ogrid[:H,:L,:W]; wy=Y+oy; wz=Z+oz; wx=X+ox
    # story spoilers stay off the public site: nothing below the keel, not the islet under the garden
    mask=(wy<14)|((((wx+132)**2+(wz-112)**2)<40**2)&(wy<76))
    arr=arr.copy(); arr[mask]=air
    kf=np.zeros(arr.shape,bool); kf[:,:,278:]=True
    arr=keel.coarsen(arr,pal,None,depth=9,cell=3,keep_fine=kf)
    bake('city',arr,pal,out,views)
    return dict(size=[W,H,L])

def scene_isles(repo,out):
    T=os.path.join(repo,'AetherionGuilds/src/main/resources/templates')
    names=['starter_grove','starter_tide','starter_quarry','starter_guild']
    parts=[schem.load(os.path.join(T,n+'.schem')) for n in names]
    # One palette. Each island stands alone in its own slot of two draw regions (export_mesh cuts
    # the mesh into 64-block cells), far enough from the next that neither shades the other: the
    # site places every island on its own (src/world/route.js), so no cell may hold parts of two.
    pal=['minecraft:air']; idx={'minecraft:air':0}
    slot=128; gap=44; W=slot*len(parts); H=max(p[0].shape[0] for p in parts)+8; L=max(p[0].shape[1] for p in parts)+gap*2
    g=np.zeros((H,L,W),np.int32); layout={}
    for k,(n,(a,p,info)) in enumerate(zip(names,parts)):
        m=np.zeros(len(p),np.int32)
        for i,s in enumerate(p):
            if s not in idx: idx[s]=len(pal); pal.append(s)
            m[i]=idx[s]
        h,l,w=a.shape; x=k*slot+(slot-w)//2; z=(L-l)//2; y=(H-h)//2
        g[y:y+h,z:z+l,x:x+w]=m[a]
        layout[n]=dict(c=[x+w/2,y+h/2,z+l/2],size=[w,h,l],slot=[k*slot,(k+1)*slot])
    bake('isles',g,pal,out,drop=1)
    return dict(size=[W,H,L],layout=layout)

def scene_prison(repo,out):
    D=os.path.join(repo,'AetherionDungeons/src/main/resources/structures/floor1')
    rooms=[('f1_panopticon',0),('f1_warden_throne',0),('f1_chain_vault',0),('f1_execution_yard',0),('f1_old_cistern',0),('f1_listening_hall',0)]
    parts=[(n,)+nbtstruct.load(os.path.join(D,n+'.nbt')) for n,_ in rooms]
    pal=['minecraft:air']; idx={'minecraft:air':0}
    gap=30
    # two rows, stepping down, so a camera above sees one after the other
    cells=[(0,0),(1,0),(2,0),(0,1),(1,1),(2,1)]
    mw=max(p[1].shape[2] for p in parts); ml=max(p[1].shape[1] for p in parts); mh=max(p[1].shape[0] for p in parts)
    W=(mw+gap)*3+gap; L=(ml+gap)*2+gap; H=mh+70+8
    g=np.zeros((H,L,W),np.int32); layout={}
    for (n,a,p,info),(cx,cz) in zip(parts,cells):
        m=np.zeros(len(p),np.int32)
        for i,s in enumerate(p):
            if s not in idx: idx[s]=len(pal); pal.append(s)
            m[i]=idx[s]
        h,l,w=a.shape
        # cut it open: no roof, and the two walls that face the camera come off
        cut=a.copy(); air=p.index('minecraft:air')
        roof=max(4,h-3); cut[roof:,:,:]=air
        cut[2:,l-2:,:]=air; cut[2:,:,w-2:]=air
        x=gap+cx*(mw+gap)+(mw-w)//2; z=gap+cz*(ml+gap)+(ml-l)//2; y=4+(2-cx)*22+(1-cz)*12
        g[y:y+h,z:z+l,x:x+w]=m[cut]
        layout[n]=dict(c=[x+w/2,y+6,z+l/2],size=[w,h,l])
    bake('prison',g,pal,out,drop=1)
    return dict(size=[W,H,L],layout=layout)

def classify(pal):
    """Per palette entry: does it fill its cell (cubes, leaves, glass, sea), is it bulky but no cube (stairs,
    slabs: drawn as a cube when far), or thin (fences, panes, plants, torches: gone when far), and does it glow."""
    tmp=os.path.join(WORK,'classify.states')
    _,_,lut=tables.build(pal,tmp,sea=True)
    raw=open(tmp,'rb').read(); n=struct.unpack('<i',raw[:4])[0]; o=4; kind=[]; glows=[]
    for _ in range(n):
        flags,emit,nb,ch,cc=struct.unpack('<BBBBH',raw[o:o+6]); o+=6; vol=0
        for _ in range(nb):
            b=struct.unpack('<6b',raw[o:o+6]); o+=18
            vol+=max(0,b[3]-b[0])*max(0,b[4]-b[1])*max(0,b[5]-b[2])
        if flags&tables.FL_INVIS or nb==0: k=0
        elif flags&tables.FL_FULL or flags&tables.FL_LEAF or (nb==1 and vol>=4096): k=1
        elif vol>=0.45*4096: k=2
        else: k=0
        kind.append(k); glows.append(emit>0)
    return np.array(kind)[lut],np.array(glows)[lut]

def soften(arr,pal,fine,cell=2,kind=None,glow=None):
    """Far from the camera a block is smaller than a pixel. Outside `fine` (a (L,W) mask of columns that
    keep every block) the grid is redrawn in cells of `cell` blocks: a cell that is at least half full
    becomes its topmost block, anything thinner becomes air. The greedy mesher then needs a fraction of
    the faces for the same silhouette. With `kind` (classify), thin things do not count, stairs and slabs
    become whole cubes (appended to `pal` as `full:` states), and a lamp is never what a cell turns into
    while there is anything else in it."""
    H,L,W=arr.shape
    if kind is None:
        kind=np.array([0 if n.split('[')[0] in anvil.AIR else 1 for n in pal]); glow=np.zeros(len(pal),bool)
    H2,L2,W2=H//cell,L//cell,W//cell
    v=arr[:H2*cell,:L2*cell,:W2*cell].reshape(H2,cell,L2,cell,W2,cell).transpose(0,2,4,1,3,5)[:,:,:,::-1].reshape(H2,L2,W2,-1)
    solid=kind[v]>0
    plain=solid&~glow[v]
    pick=np.where(plain.any(axis=3),plain.argmax(axis=3),solid.argmax(axis=3))
    top=np.take_along_axis(v,pick[...,None],axis=3)[...,0]
    del plain,pick
    full=solid.sum(axis=3)*2>=cell**3
    del solid,v
    bulky=np.flatnonzero(kind==2)
    if len(bulky):
        remap=np.arange(len(pal))
        for i in bulky:
            remap[i]=len(pal); pal.append('full:'+pal[i])
        top=remap[top]
    coarse=np.where(full,top,0).astype(arr.dtype)
    big=np.repeat(np.repeat(np.repeat(coarse,cell,0),cell,1),cell,2)
    out=arr.copy()
    far=~fine[:L2*cell,:W2*cell]
    o=out[:H2*cell,:L2*cell,:W2*cell]
    o[:,far]=big[:,far]
    return out

def scene_island(folder,out,box,y0=None,fine=None,cell=2,views=None,mid=None):
    """The main island from the world's region files. `box` is x0,z0,x1,z1 in world blocks; `fine` is a
    list of (x,z,radius) around the places the camera comes close to (None: every block everywhere).
    `mid` lists wider circles: between them and `fine` the grid is drawn in `cell`-block cells, everywhere
    else in cells twice that size (the far coasts, only ever seen from a distance).
    `views` is a file of camera positions in world blocks, one `x y z` per line, sampled along the route
    (forge/route_views.mjs): faces none of them can see are left out."""
    t=time.time()
    arr,pal,info=anvil.load(folder,*box,y0=y0)
    arr,info=anvil.trim(arr,pal,info)
    H,L,W=arr.shape; ox,oy,oz=info['offset']
    print('island',W,H,L,'at',info['offset'],'palette',len(pal),'data version',info['version'],'%.0fs'%(time.time()-t))
    lamps=None; kind=glow=None
    if fine is not None or mid is not None:
        kind,glow=classify(pal)
        lamps=lamp_list(arr,pal,glow)
    if mid is not None:
        zz,xx=np.mgrid[0:L,0:W]
        near=np.zeros((L,W),bool)
        for x,z,r in list(mid)+list(fine or []): near|=(xx+ox-x)**2+(zz+oz-z)**2<=r*r
        n0=len(pal)
        arr=soften(arr,pal,near,cell*2,kind,glow)
        kind=np.append(kind,np.ones(len(pal)-n0,int)); glow=np.append(glow,np.zeros(len(pal)-n0,bool))
        print('island: %.0f%% of the ground in %d-block cells'%(100*(1-near.mean()),cell*2))
    if fine is not None:
        zz,xx=np.mgrid[0:L,0:W]
        mask=np.zeros((L,W),bool)
        for x,z,r in fine: mask|=(xx+ox-x)**2+(zz+oz-z)**2<=r*r
        arr=soften(arr,pal,mask,cell,kind,glow)
        print('island: every block kept on %.0f%% of the ground'%(100*mask.mean()))
    grid_views=None
    if views:
        pts=np.loadtxt(views,ndmin=2)[:,:3]-np.array([ox,oy,oz],float)
        grid_views=os.path.join(WORK,'island.views')
        np.savetxt(grid_views,pts,fmt='%.1f')
        print('island: %d camera positions'%len(pts))
    # outside the fine circles the grid is already drawn in cells: neighbouring quads there may merge
    merge=None
    if fine is not None:
        def merge(x,z):
            keep=np.zeros(x.shape,bool)
            for cx,cz,r in fine: keep|=(x+ox-cx)**2+(z+oz-cz)**2<=(r+2)**2
            return ~keep
    bake('island',arr,pal,out,views=grid_views,drop=2,sea=True,merge=merge,lamps=lamps)
    # `offset` is the world position of the mesh's corner: SETS.island.origin in src/world/route.js
    return dict(size=[W,H,L],offset=[ox,oy,oz])

if __name__=='__main__':
    repo,exile,outdir=sys.argv[1:4]; views=sys.argv[4] if len(sys.argv)>4 else None
    which=os.environ.get('SCENES','city,isles,prison').split(',')
    compile_baker(); os.makedirs(outdir,exist_ok=True); man={}
    mf=os.path.join(outdir,'scenes.json')
    if os.path.exists(mf): man=json.load(open(mf))
    if 'isles' in which: man['isles']=scene_isles(repo,os.path.join(outdir,'isles.wasm'))
    if 'prison' in which: man['prison']=scene_prison(repo,os.path.join(outdir,'prison.wasm'))
    if 'city' in which: man['city']=scene_city(exile,os.path.join(outdir,'city.wasm'),views)
    if 'island' in which:
        box=[int(v) for v in os.environ['ISLAND_BOX'].split(',')]
        fine=[tuple(float(v) for v in c.split(',')) for c in os.environ['ISLAND_FINE'].split(';')] if os.environ.get('ISLAND_FINE') else None
        y0=int(os.environ['ISLAND_Y0']) if os.environ.get('ISLAND_Y0') else None
        mid=[tuple(float(v) for v in c.split(',')) for c in os.environ['ISLAND_MID'].split(';')] if os.environ.get('ISLAND_MID') else None
        man['island']=scene_island(os.environ['ISLAND'],os.path.join(outdir,'island.wasm'),box,y0,fine,int(os.environ.get('ISLAND_LOD','2')),os.environ.get('ISLAND_VIEWS'),mid)
    json.dump(man,open(mf,'w'),indent=1)
