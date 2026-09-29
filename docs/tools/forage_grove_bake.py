#!/usr/bin/env python3
"""
Foraging Eldervale — offline map tools for Foraging-Eldervale-Island.schem (Sponge v3).

Everything the plugin knows about *where* things are on the island comes from here, so the server never
has to scan blocks at runtime:

  bake      → forage-districts.bin  (8×8×8 district grid, gzip, ~7 KB; goes in AetherionForaging resources)
  anchors   → standing spots near given world points (landmarks / cast)
  updrafts  → clear vertical shafts between low and high tiers (updraft candidates)
  render    → top-down PNG + district map PNGs for eyeballing

World mapping (paste origin 677 90 -116, rotate-y 180, schem Offset -391 -123 -137):
    world = (1068 - x, y - 33, 21 - z)          for schem-local (x, y, z)
If the isle is ever pasted somewhere else with the same rotation, the plugin shifts the grid itself.
Another rotation needs a re-bake: pass --paste and --rotate.

Needs: numpy (and Pillow for `render`).  Example:
    python forage_grove_bake.py bake --schem Foraging-Eldervale-Island.schem --out forage-districts.bin
"""
import argparse
import gzip
import heapq
import io
import struct
import sys

import numpy as np

# ---------------------------------------------------------------- NBT / Sponge


def _read(f, t):
    if t == 1: return struct.unpack('>b', f.read(1))[0]
    if t == 2: return struct.unpack('>h', f.read(2))[0]
    if t == 3: return struct.unpack('>i', f.read(4))[0]
    if t == 4: return struct.unpack('>q', f.read(8))[0]
    if t == 5: return struct.unpack('>f', f.read(4))[0]
    if t == 6: return struct.unpack('>d', f.read(8))[0]
    if t == 7: return f.read(struct.unpack('>i', f.read(4))[0])
    if t == 8: return f.read(struct.unpack('>H', f.read(2))[0]).decode('utf-8', 'replace')
    if t == 9:
        et = f.read(1)[0]
        return [_read(f, et) for _ in range(struct.unpack('>i', f.read(4))[0])]
    if t == 10:
        d = {}
        while True:
            tt = f.read(1)[0]
            if tt == 0:
                return d
            name = f.read(struct.unpack('>H', f.read(2))[0]).decode('utf-8', 'replace')
            d[name] = _read(f, tt)
    if t == 11:
        n = struct.unpack('>i', f.read(4))[0]
        return list(struct.unpack('>%di' % n, f.read(4 * n)))
    if t == 12:
        n = struct.unpack('>i', f.read(4))[0]
        return list(struct.unpack('>%dq' % n, f.read(8 * n)))
    raise ValueError(t)


def _varints(b):
    out = np.empty(len(b), dtype=np.int32)
    i = n = 0
    size = len(b)
    while i < size:
        v = s = 0
        while True:
            x = b[i]
            i += 1
            v |= (x & 0x7f) << s
            if not x & 0x80:
                break
            s += 7
        out[n] = v
        n += 1
    return out[:n]


def load_schem(path):
    f = io.BytesIO(gzip.open(path).read())
    t = f.read(1)[0]
    f.read(struct.unpack('>H', f.read(2))[0])
    root = _read(f, t)
    r = root.get('Schematic', root)
    W, H, L = r['Width'], r['Height'], r['Length']
    blocks = r.get('Blocks', r)
    pal = {v: k for k, v in (blocks.get('Palette') or r['Palette']).items()}
    data = blocks.get('Data') if 'Blocks' in r else r['BlockData']
    ids = _varints(data).reshape(H, L, W)  # [y][z][x]
    return ids, pal, r.get('Offset', [0, 0, 0])


# ---------------------------------------------------------------- mapping

class Mapping:
    def __init__(self, offset, paste=(677, 90, -116), rotate=180):
        self.ox, self.oy, self.oz = offset
        self.px, self.py, self.pz = paste
        self.rot = rotate % 360

    def to_world(self, x, y, z):
        lx, ly, lz = x + self.ox, y + self.oy, z + self.oz
        if self.rot == 0:
            wx, wz = lx, lz
        elif self.rot == 180:
            wx, wz = -lx, -lz
        elif self.rot == 90:
            wx, wz = -lz, lx
        else:
            wx, wz = lz, -lx
        return self.px + wx, self.py + ly, self.pz + wz


# ---------------------------------------------------------------- districts

# 1 frostpine 2 blossom 3 sunscar 4 elderwood 5 gloamwood 6 brinefall 7 crown — Grove.code in the plugin
FAMILIES = [('dark_oak', 5), ('spruce', 1), ('cherry', 2), ('acacia', 3), ('birch', 4), ('jungle', 7), ('oak', 4)]


def district_of(name):
    n = name.split('[')[0].replace('minecraft:', '')
    if not (n.endswith('_log') or n.endswith('_wood') or n.endswith('_leaves') or n in ('bamboo', 'mangrove_roots', 'muddy_mangrove_roots')):
        return 0, 0
    weight = 3 if (n.endswith('_log') or n.endswith('_wood')) else 1
    if n == 'bamboo':
        return 7, 1
    if 'mangrove' in n:
        return 6, weight
    for fam, code in FAMILIES:
        if n.startswith(fam) or n.startswith('stripped_' + fam):
            return code, weight
    return 0, 0


def bake(ids, pal, mapping, out, x0=504, y0=48, z0=-496, nx=71, ny=32, nz=65, cell=8):
    H, L, W = ids.shape
    maxid = max(pal) + 1
    dl = np.zeros(maxid, dtype=np.int8)
    wl = np.zeros(maxid, dtype=np.int8)
    air = np.zeros(maxid, dtype=bool)
    for pid, n in pal.items():
        dl[pid], wl[pid] = district_of(n)
        air[pid] = n.split('[')[0] in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')
    D = dl[ids]
    Wt = wl[ids]
    SOL = ~air[ids]
    lz, lx = np.indices((L, W))
    wxs = np.empty((L, W), dtype=np.int64)
    wzs = np.empty((L, W), dtype=np.int64)
    for z in range(L):
        for x in range(W):
            wx, _, wz = mapping.to_world(x, 0, z)
            wxs[z, x] = wx
            wzs[z, x] = wz
    cxs = (wxs - x0) // cell
    czs = (wzs - z0) // cell
    okxz = (cxs >= 0) & (cxs < nx) & (czs >= 0) & (czs < nz)
    score = np.zeros((8, ny, nz, nx), dtype=np.int32)
    solcell = np.zeros((ny, nz, nx), dtype=bool)
    for y in range(H):
        wy = mapping.to_world(0, y, 0)[1]
        cy = (wy - y0) // cell
        if not (0 <= cy < ny):
            continue
        sl = SOL[y] & okxz
        zz, xx = np.nonzero(sl)
        solcell[cy, czs[zz, xx], cxs[zz, xx]] = True
        for d in range(1, 8):
            m = (D[y] == d) & okxz
            zz, xx = np.nonzero(m)
            if len(zz):
                np.add.at(score[d], (cy, czs[zz, xx], cxs[zz, xx]), Wt[y][zz, xx])
    tot = score.sum(axis=0)
    grid = np.where(tot >= 6, score.argmax(axis=0), 0).astype(np.int8)
    near = solcell.copy()
    for k in (1, 2, 3):
        near[k:] |= solcell[:-k]
    dist = np.full(grid.shape, 10 ** 9, dtype=np.int64)
    lab = grid.copy()
    pq = []
    for (y, z, x) in zip(*np.nonzero(grid > 0)):
        dist[y, z, x] = 0
        heapq.heappush(pq, (0, y, z, x))
    while pq:
        d0, y, z, x = heapq.heappop(pq)
        if d0 > dist[y, z, x]:
            continue
        for dy, dz, dx, c in ((1, 0, 0, 2), (-1, 0, 0, 2), (0, 1, 0, 1), (0, -1, 0, 1), (0, 0, 1, 1), (0, 0, -1, 1)):
            yy, zz2, xx2 = y + dy, z + dz, x + dx
            if 0 <= yy < ny and 0 <= zz2 < nz and 0 <= xx2 < nx and near[yy, zz2, xx2]:
                nd = d0 + c
                if nd < dist[yy, zz2, xx2] and nd <= 12:
                    dist[yy, zz2, xx2] = nd
                    lab[yy, zz2, xx2] = lab[y, z, x]
                    heapq.heappush(pq, (nd, yy, zz2, xx2))
    lab[~near & (grid == 0)] = 0

    def box3(a):
        p = np.pad(a, 1)
        s = np.zeros(a.shape, dtype=np.int16)
        for dy in range(3):
            for dz in range(3):
                for dx in range(3):
                    s += p[dy:dy + a.shape[0], dz:dz + a.shape[1], dx:dx + a.shape[2]]
        return s

    for _ in range(2):
        cnt = np.zeros((8,) + lab.shape, dtype=np.int16)
        for d in range(1, 8):
            cnt[d] = box3((lab == d).astype(np.int16))
        best = cnt.argmax(axis=0)
        bestn = cnt.max(axis=0)
        mine = np.take_along_axis(cnt, lab[None].astype(np.int64), axis=0)[0]
        flip = (lab > 0) & (bestn >= mine + 4)
        lab = lab.copy()
        lab[flip] = best[flip]
    header = (b'AEFD' + struct.pack('>B', 1) + struct.pack('>iii', x0, y0, z0) + struct.pack('>BB', cell, cell)
              + struct.pack('>HHH', nx, ny, nz) + struct.pack('>iiiH', mapping.px, mapping.py, mapping.pz, mapping.rot))
    with gzip.open(out, 'wb', compresslevel=9) as f:
        f.write(header + lab.astype(np.uint8).tobytes(order='C'))
    print('baked', out, 'cells per district', [int((lab == d).sum()) for d in range(1, 8)])
    return lab


# ---------------------------------------------------------------- standing spots / updrafts

PASSABLE = ('air', 'cave_air', 'void_air', 'light', 'short_grass', 'tall_grass', 'fern', 'large_fern', 'snow',
            'pink_petals', 'dead_bush', 'sweet_berry_bush', 'vine', 'glow_lichen', 'moss_carpet', 'cobweb', 'ladder',
            'torch', 'wall_torch')


def passable_floor(pal, ids):
    maxid = max(pal) + 1
    floor = np.zeros(maxid, dtype=bool)
    passable = np.zeros(maxid, dtype=bool)
    for pid, n in pal.items():
        b = n.split('[')[0].replace('minecraft:', '')
        passable[pid] = (b in PASSABLE or b.endswith('_carpet') or b.endswith('_sapling') or 'flower' in b
                         or b.endswith('_button') or b.endswith('_pressure_plate'))
        floor[pid] = (not passable[pid] and b not in ('water', 'lava', 'bubble_column', 'barrier', 'bamboo', 'chain',
                                                      'lantern', 'campfire', 'scaffolding', 'iron_bars')
                      and not b.endswith('_leaves') and not b.endswith('_fence') and not b.endswith('_wall')
                      and not b.endswith('_pane') and not b.endswith('_trapdoor'))
    return floor[ids], passable[ids]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('command', choices=['bake', 'anchors', 'updrafts', 'render'])
    ap.add_argument('--schem', required=True)
    ap.add_argument('--out', default='forage-districts.bin')
    ap.add_argument('--paste', default='677,90,-116')
    ap.add_argument('--rotate', type=int, default=180)
    ap.add_argument('--points', default='', help='anchors: "x,y,z;x,y,z" world points')
    args = ap.parse_args()
    ids, pal, offset = load_schem(args.schem)
    mapping = Mapping(offset, tuple(int(v) for v in args.paste.split(',')), args.rotate)
    if args.command == 'bake':
        bake(ids, pal, mapping, args.out)
        return
    if mapping.rot != 180:
        sys.exit('anchors/updrafts/render assume rotate-y 180 (world = 1068-x, y-33, 21-z); re-derive for others.')
    F, P = passable_floor(pal, ids)
    H, L, W = ids.shape
    S = np.zeros((H, L, W), dtype=bool)
    S[1:H - 2] = F[0:H - 3] & P[1:H - 2] & P[2:H - 1]
    if args.command == 'anchors':
        for raw in filter(None, args.points.split(';')):
            x, y, z = (int(v) for v in raw.split(','))
            lx, ly, lz = 1068 - x, y + 33, 21 - z
            best = None
            for dy in range(-12, 13):
                for dz in range(-10, 11):
                    for dx in range(-10, 11):
                        X, Y, Z = lx + dx, ly + dy, lz + dz
                        if 0 <= X < W and 0 <= Z < L and 0 < Y < H - 2 and S[Y, Z, X]:
                            d = dx * dx + dz * dz + 4 * dy * dy
                            if best is None or d < best[0]:
                                best = (d, 1068 - X, Y - 33, 21 - Z)
            print(raw, '->', best)
        return
    if args.command == 'updrafts':
        free = np.zeros((H, L, W), dtype=np.int16)
        for y in range(H - 2, -1, -1):
            free[y] = np.where(P[y], free[y + 1] + 1, 0)
        lows = np.argwhere(S[80 + 33:130 + 33])
        lows[:, 0] += 80 + 33
        found = []
        for ly, lz, lx in lows[::3]:
            fc = free[ly, lz, lx]
            if fc < 25:
                continue
            reach = ly + fc - 3
            for dz in range(-7, 8):
                for dx in range(-7, 8):
                    X, Z = lx + dx, lz + dz
                    if not (0 <= X < W and 0 <= Z < L):
                        continue
                    col = np.nonzero(S[ly + 25:reach + 1, Z, X])[0]
                    if len(col):
                        ty = ly + 25 + col[-1]
                        found.append((ty - ly, (1068 - lx, ly - 33, 21 - lz), (1068 - X, ty - 33, 21 - Z)))
        found.sort(reverse=True)
        for h, a, b in found[:40]:
            print(h, a, '->', b)
        return
    from PIL import Image
    solid = ~P
    top = np.where(solid.any(axis=0), H - 1 - np.argmax(solid[::-1], axis=0), 0)
    img = (np.clip(top / H, 0, 1) * 255).astype(np.uint8)[::-1, ::-1]
    Image.fromarray(img).save('forage_heightmap.png')
    print('wrote forage_heightmap.png')


if __name__ == '__main__':
    main()
