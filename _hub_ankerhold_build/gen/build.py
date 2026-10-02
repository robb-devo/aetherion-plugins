import time, sys, random, pickle
import numpy as np
from world import World
import terrain as TR
import render as R
from plan import Plan
import d_harbour, d_capital, d_ridge, d_whisper, d_fields, d_border, d_mountain, d_sky
import post

MODULES = [d_harbour, d_capital, d_ridge, d_whisper, d_fields, d_border, d_mountain, d_sky]

def road_lamps(W, T, P, spacing=17):
    import math
    import structures as S
    from world import X0, Z0
    n = 0
    for p in P.paths:
        if p['width'] < 2.9:
            continue
        pts = p['P']
        acc = spacing / 2
        for i in range(1, len(pts)):
            x0, z0 = pts[i - 1][0], pts[i - 1][1]
            x1, z1 = pts[i][0], pts[i][1]
            seg = math.hypot(x1 - x0, z1 - z0)
            acc += seg
            if acc < spacing:
                continue
            acc = 0
            dx, dz = (x1 - x0) / (seg or 1), (z1 - z0) / (seg or 1)
            for side in (1, -1):
                lx = round(x1 - dz * (p['width'] / 2 + 1.2) * side)
                lz = round(z1 + dx * (p['width'] / 2 + 1.2) * side)
                ix, iz = lx - X0, lz - Z0
                if not (0 <= ix < T.top.shape[0] and 0 <= iz < T.top.shape[1]) or T.plateau[ix, iz]:
                    continue
                y = int(T.top[ix, iz])
                g = W.getn(lx, y, lz)
                if not any(t in g for t in ('grass_block', 'dirt', 'podzol', 'coarse', 'gravel', 'moss', 'stone')):
                    continue
                if not all(W.is_air(lx, y + k, lz) or 'grass' in W.getn(lx, y + k, lz) for k in range(1, 5)):
                    continue
                if T.pathmat[ix, iz] >= 0:
                    continue
                for k in range(1, 5):
                    W.set(lx, y + k, lz, 0)
                S.lamp_post(W, lx, y, lz, 'spruce', 3)
                T.occ[max(ix - 1, 0):ix + 2, max(iz - 1, 0):iz + 2] = True
                n += 1
                break
    print('  road lamps', n)


def run(modules=MODULES, veg=True):
    t0 = time.time()
    W = World()
    T = TR.build_heights()
    P = Plan(T)
    for m in modules:
        m.plan(P, T)
    TR.underside(T)
    TR.voxelise(W, T)
    P.surface(W)
    TR.stalactites(W, T)
    print('terrain+plan %.1fs' % (time.time() - t0))
    rng = random.Random(1)
    for m in modules:
        t1 = time.time()
        m.build(W, T, rng)
        print('  built', m.__name__, '%.1fs' % (time.time() - t1))
    road_lamps(W, T, P)
    if veg:
        import vegetation
        vegetation.run(W, T)
    post.run(W)
    print('total %.1fs palette %d' % (time.time() - t0, len(W.pal)))
    return W, T, P

def views(W, tag='', which=('top','arrival','aerial')):
    cs = R.palette_colors(W, thin_solid=False)
    if cs[2]: print('UNKNOWN colours:', cs[2][:20])
    if 'top' in which: R.topdown(W, 2, f'out/top{tag}.png')
    if 'arrival' in which: R.raycast(W, (166.5, 66.6, 8.5), 90, 2, fov=100, w=960, h=540, path=f'out/arrival{tag}.png', cols_solid=cs)
    if 'aerial' in which: R.raycast(W, (330, 200, 300), 132, 30, fov=55, w=800, h=450, maxd=900, path=f'out/aerial{tag}.png', cols_solid=cs)

if __name__ == '__main__':
    W, T, P = run()
    views(W)
