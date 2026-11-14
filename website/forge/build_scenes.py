#!/usr/bin/env python3
"""Bakes the website's 3D scenes from the game's own builds.

  city    the walled city past the fog (schematic, own build)
  isles   the four starter islands players get (Guilds templates, own builds)
  prison  floor 1 of the Warden's Prison, cut open (Dungeons structures, own builds)
  island  the main island, straight from the server's world folder (region files)

Usage: MC_ASSETS=<node_modules with minecraft-assets + minecraft-data> build_scenes.py <repo-root> <exile.schem> <out-dir> [views.bin]
       SCENES=island ISLAND=<world>/region ISLAND_BOX=x0,z0,x1,z1 [ISLAND_Y0=..] [ISLAND_FINE="x,z,r;x,z,r"] [ISLAND_LOD=2] build_scenes.py ...
Only averaged colours leave this script; no game textures ship with the site.
"""
import numpy as np, json, subprocess, sys, os, time
HERE=os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0,HERE)
import schem, tables, keel, nbtstruct, export_mesh, anvil
WORK=os.environ.get('FORGE_WORK','/tmp/forge-work'); os.makedirs(WORK,exist_ok=True)
BAKE=os.path.join(WORK,'bake')
def compile_baker():
    subprocess.run(['gcc','-O3','-march=native','-fopenmp','-o',BAKE,os.path.join(HERE,'bake.c'),'-lm','-w'],check=True)

def bake(name,arr,pal,out,views=None,drop=2,sea=False):
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
    export_mesh.export(base,out,json.load(open(base+'.meta.json')))

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

def soften(arr,pal,fine,cell=2):
    """Far from the camera a block is smaller than a pixel. Outside `fine` (a (L,W) mask of columns that
    keep every block) the grid is redrawn in cells of `cell` blocks: a cell that is at least half full
    becomes its topmost block, anything thinner becomes air. The greedy mesher then needs a fraction of
    the faces for the same silhouette."""
    H,L,W=arr.shape
    air=np.array([n.split('[')[0] in anvil.AIR for n in pal])
    H2,L2,W2=H//cell,L//cell,W//cell
    v=arr[:H2*cell,:L2*cell,:W2*cell].reshape(H2,cell,L2,cell,W2,cell).transpose(0,2,4,1,3,5)[:,:,:,::-1].reshape(H2,L2,W2,-1)
    solid=~air[v]
    top=np.take_along_axis(v,solid.argmax(axis=3)[...,None],axis=3)[...,0]
    full=solid.sum(axis=3)*2>=cell**3
    coarse=np.where(full,top,0).astype(arr.dtype)
    big=np.repeat(np.repeat(np.repeat(coarse,cell,0),cell,1),cell,2)
    out=arr.copy()
    far=~fine[:L2*cell,:W2*cell]
    o=out[:H2*cell,:L2*cell,:W2*cell]
    o[:,far]=big[:,far]
    return out

def scene_island(folder,out,box,y0=None,fine=None,cell=2):
    """The main island from the world's region files. `box` is x0,z0,x1,z1 in world blocks; `fine` is a
    list of (x,z,radius) around the places the camera comes close to (None: every block everywhere)."""
    t=time.time()
    arr,pal,info=anvil.load(folder,*box,y0=y0)
    arr,info=anvil.trim(arr,pal,info)
    H,L,W=arr.shape; ox,oy,oz=info['offset']
    print('island',W,H,L,'at',info['offset'],'palette',len(pal),'data version',info['version'],'%.0fs'%(time.time()-t))
    if fine is not None:
        zz,xx=np.mgrid[0:L,0:W]
        mask=np.zeros((L,W),bool)
        for x,z,r in fine: mask|=(xx+ox-x)**2+(zz+oz-z)**2<=r*r
        arr=soften(arr,pal,mask,cell)
        print('island: every block kept on %.0f%% of the ground'%(100*mask.mean()))
    bake('island',arr,pal,out,drop=2,sea=True)
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
        man['island']=scene_island(os.environ['ISLAND'],os.path.join(outdir,'island.wasm'),box,y0,fine,int(os.environ.get('ISLAND_LOD','2')))
    json.dump(man,open(mf,'w'),indent=1)
