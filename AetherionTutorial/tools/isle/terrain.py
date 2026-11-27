"""Morningbell's ground: coastline, heights, cliffs, the stream with its fall, the quarry cut, sea bed.

Rule from the server side: STONE, DEEPSLATE and ores are minable everywhere (AetherionMining), so natural rock
here is andesite / tuff / diorite / calcite / cobblestone. Real stone appears only in the quarry.
"""
from collections import deque

import numpy as np

import layout as L
from noise import fbm, value, hash01, smoothstep as ss


def chamfer(mask, limit=160):
    """Approximate distance (blocks) from every True cell to the nearest False cell."""
    big = 1e6
    d = np.where(mask, big, 0.0)
    for _ in range(limit):
        p = np.pad(d, 1, constant_values=big)
        n = np.minimum.reduce([
            p[1:-1, 1:-1],
            p[:-2, 1:-1] + 1, p[2:, 1:-1] + 1, p[1:-1, :-2] + 1, p[1:-1, 2:] + 1,
            p[:-2, :-2] + 1.4142, p[:-2, 2:] + 1.4142, p[2:, :-2] + 1.4142, p[2:, 2:] + 1.4142,
        ])
        if np.array_equal(n, d):
            break
        d = n
    return d


def seg_dist(X, Z, ax, az, bx, bz):
    """Distance to segment a-b and the parameter t (0..1) of the nearest point."""
    dx, dz = bx - ax, bz - az
    ll = dx * dx + dz * dz
    if ll < 1e-9:
        return np.hypot(X - ax, Z - az), np.zeros(np.shape(X))
    t = np.clip(((X - ax) * dx + (Z - az) * dz) / ll, 0.0, 1.0)
    px, pz = ax + t * dx, az + t * dz
    return np.hypot(X - px, Z - pz), t


def chaikin(points, rounds=3, closed=True):
    pts = [tuple(map(float, p)) for p in points]
    for _ in range(rounds):
        out = []
        n = len(pts)
        rng = range(n) if closed else range(n - 1)
        if not closed:
            out.append(pts[0])
        for i in rng:
            a, b = pts[i], pts[(i + 1) % n]
            out.append(tuple(a[k] * 0.75 + b[k] * 0.25 for k in range(len(a))))
            out.append(tuple(a[k] * 0.25 + b[k] * 0.75 for k in range(len(a))))
        if not closed:
            out.append(pts[-1])
        pts = out
    return pts


def polygon_field(X, Z, pts):
    """Signed distance to a closed polygon: positive inside."""
    dist = np.full(X.shape, 1e9)
    inside = np.zeros(X.shape, bool)
    n = len(pts)
    for i in range(n):
        ax, az = pts[i]
        bx, bz = pts[(i + 1) % n]
        d, _ = seg_dist(X, Z, ax, az, bx, bz)
        dist = np.minimum(dist, d)
        if abs(bz - az) > 1e-9:
            cross = ((az > Z) != (bz > Z)) & (X < (bx - ax) * (Z - az) / (bz - az) + ax)
            inside ^= cross
    return np.where(inside, dist, -dist)


class Terrain:
    def __init__(self):
        xs = np.arange(L.X0, L.X0 + L.SX)
        zs = np.arange(L.Z0, L.Z0 + L.SZ)
        self.X, self.Z = np.meshgrid(xs, zs)
        self.H = None           # int top block per column
        self.land = None
        self.din = None
        self.dout = None
        self.water = None       # water level per column (int) or -1
        self.stream = None      # bool: stream / pool water columns
        self.stream_dist = None
        self.stream_pts = None  # sampled centre line [(x, z, level)]
        self.quarry = None      # bool: inside the quarry cut
        self.slope = None
        self.keep_clear = None  # bool: no trees / scatter here (set by sites and paths)

    def h(self, x, z):
        return int(self.H[z - L.Z0, x - L.X0])

    def inb(self, x, z):
        return L.X0 <= x < L.X0 + L.SX and L.Z0 <= z < L.Z0 + L.SZ

    def is_land(self, x, z):
        return self.inb(x, z) and bool(self.land[z - L.Z0, x - L.X0]) and not bool(self.stream[z - L.Z0, x - L.X0])


def stream_curve():
    """Centre line of the stream as dense points; the fall stays a clean break."""
    pts = L.STREAM
    upper = chaikin(pts[:L.FALL_INDEX + 1], 2, closed=False)
    lower = chaikin(pts[L.FALL_INDEX + 1:], 2, closed=False)
    return upper, lower


def shape():
    t = Terrain()
    X, Z = t.X, t.Z
    seed = L.SEED

    # ---------------------------------------------------------------- coastline
    f = polygon_field(X, Z, chaikin(L.COAST, 3))
    f = f + fbm(X, Z, 38, 4, seed) * 4.0 + fbm(X, Z, 13, 3, seed + 7) * 1.6
    land = _main_body(f > 0)
    din = chamfer(land)
    dout = chamfer(~land)
    t.land, t.din, t.dout = land, din, dout

    # ---------------------------------------------------------------- heights
    h = L.SEA + 1.0 + 3.0 * ss(din, 0, 10) + 3.0 * ss(din, 8, 42)
    h = h + fbm(X, Z, 36, 4, seed + 3) * 1.6 * ss(din, 3, 18)

    def hill(cx, cz, amp, sx, sz):
        return amp * np.exp(-(((X - cx) / sx) ** 2 + ((Z - cz) / sz) ** 2))

    # the bell hill: it stays high towards the sea (west, north) and eases down into the island (east, south)
    bx, bz = L.BELL
    sxw = np.where(X < bx, 66.0, 30.0)
    szn = np.where(Z < bz, 66.0, 30.0)
    bell = 25.5 * np.exp(-(((X - bx) / sxw) ** 2 + ((Z - bz) / szn) ** 2))
    h = h + bell
    h = h + hill(bx, bz, 5.5, 12, 12)                      # the summit knoll the belfry stands on
    h = h + hill(-44, -22, 6.0, 20, 16)                    # south-east shoulder above the quarry
    h = h + hill(26, -66, 3.6, 34, 20)                     # a rise in the north wood (Burr's clearing)
    h = h + hill(L.YARD[0], L.YARD[1], 6.5, 17, 15)        # the watchtower knoll
    h = h + hill(-48, 50, 3.2, 30, 24)                     # south-west downs
    h = h + hill(60, -44, 2.5, 22, 18)                     # north-east wood

    # the escarpment: the north of the island is a plateau; a cliff line runs east-west across the middle
    scarp_z = L.SCARP_Z + 7.0 * np.sin(X / 34.0 + 0.4) + fbm(X, Z, 15, 2, seed + 61) * 2.6
    upper = ss(scarp_z - Z, -0.9, 0.9)
    fade = ss(X, -78, -44) * (1 - ss(X, 66, 100))
    h = h + L.SCARP_RISE * upper * fade
    t.upper = (upper * fade) > 0.5
    scarp_band = (np.abs(scarp_z - Z) < 3.5) & (fade > 0.3)

    # sea cliffs: where the hill is still high at the coast it ends in a wall with a rocky foot
    cl = np.clip((bell - 7.0) / 6.5, 0, 1)
    nz = fbm(X, Z, 11, 2, seed + 13)
    face = 1 - ss(din, 3.5, 10.0)
    foot = 1 - ss(din, 1.3 + nz * 1.0, 4.4 + nz * 1.4)
    h = np.where(land, h - cl * face * (2.5 + 3.5 * (nz * 0.5 + 0.5)), h)
    h = np.where(land, h * (1 - cl * foot) + (L.SEA + 1.4 + nz * 1.2) * cl * foot, h)
    # low rocky cliffs along the north shore and round the lighthouse point (3 to 6 blocks)
    north = np.clip((-Z - 62) / 14.0, 0, 1) * (1 - cl)
    point = np.exp(-(((X - 114) / 22.0) ** 2 + ((Z + 22) / 16.0) ** 2))
    low = np.clip(north + point * 0.9, 0, 1)
    h = np.where(land, h + low * (2.6 + nz * 1.4) * ss(din, 1.5, 4.0), h)

    # the tidal flat stays at sea level; the spit that leads to it is low sand
    fx, fz = L.FLAT
    fm = 1 - ss(np.hypot((X - fx) / 1.35, Z - fz), 15, 23)
    h = h * (1 - fm) + (L.SEA + 0.2) * fm

    # break up the contour rings a little
    h = h + fbm(X, Z, 6.5, 2, seed + 17) * 0.55 * ss(din, 4, 9)

    # ---------------------------------------------------------------- site pads (irregular, soft edged)
    pads = np.zeros(X.shape)

    def pad(cx, cz, y, r, blend=8.0, wob=2.0, sxs=1.0):
        nonlocal h, pads
        d = np.hypot((X - cx) / sxs, Z - cz) + fbm(X, Z, 12, 2, seed + int(cx) * 3 + int(cz)) * wob
        m = 1 - ss(d, r, r + blend)
        h = h * (1 - m) + y * m
        pads = np.maximum(pads, m)

    pad(L.BELL[0] + 3, L.BELL[1], L.BELL_Y, 9, 6, 1.0, 1.25)
    pad(L.BURR[0], L.BURR[1], L.BURR_Y, 14, 10, 2.5, 1.2)
    pad(L.FORGE[0] + 2, L.FORGE[1] + 4, L.FORGE_Y, 11, 8)
    pad(L.VILLAGE[0], L.VILLAGE[1], L.VILLAGE_Y, 19, 12, 3.0, 1.25)
    pad(L.YARD[0], L.YARD[1], L.YARD_Y, 11, 12, 2.0)
    pad(L.LIGHT[0], L.LIGHT[1], L.LIGHT_Y, 6, 6)
    pad(L.JETTY[0] + 2, L.JETTY[1] - 7, L.JETTY_Y + 1, 9, 8, 2.0, 1.6)

    # ---------------------------------------------------------------- sea bed
    bed = L.SEA - 1.0 - 17.0 * ss(dout, 0, 42) + fbm(X, Z, 21, 3, seed + 11) * 1.2 * ss(dout, 4, 20)
    bed = np.minimum(bed, L.SEA - 1.0)
    # the harbour bay and the water round the flat stay shallow and bright
    shallow = np.exp(-(((X - 34) / 46.0) ** 2 + ((Z - 100) / 26.0) ** 2))
    bed = bed + shallow * 9.0 * ss(dout, 3, 30)
    bed = np.minimum(bed, L.SEA - 1.0)
    edge = np.minimum.reduce([X - L.X0, L.X0 + L.SX - 1 - X, Z - L.Z0, L.Z0 + L.SZ - 1 - Z])
    e = ss(edge, 0, 18)
    bed = bed * e + 44.0 * (1 - e)
    h = np.where(land, h, bed)

    t.pre = h.copy()
    # ---------------------------------------------------------------- the stream
    upper, lower = stream_curve()
    stream_d = np.full(X.shape, 1e6)
    level = np.zeros(X.shape)
    centre = []
    for curve in (upper, lower):
        for i in range(len(curve) - 1):
            (ax, az, al), (bx_, bz_, bl) = curve[i], curve[i + 1]
            d, tt = seg_dist(X, Z, ax, az, bx_, bz_)
            lv = np.round(al + (bl - al) * tt)
            closer = d < stream_d
            level = np.where(closer, lv, level)
            stream_d = np.where(closer, d, stream_d)
            centre.append((ax, az, al))
        centre.append(curve[-1])
    t.stream_pts = centre
    wob = fbm(X, Z, 9, 2, seed + 21) * 0.35
    width = 1.35 + wob + ss(Z, 40, 80) * 0.7             # a little wider towards the mouth
    lip = L.STREAM[L.FALL_INDEX]
    px, pz, pl = L.STREAM[L.FALL_INDEX + 1]
    pool_d = np.hypot(X - (px + 0.5), Z - (pz + 1.5))
    sx_, sz_, sl_ = L.STREAM[0]
    pond_d = np.hypot((X - sx_) / 1.25, Z - sz_)
    in_pool = pool_d < 5.4 + wob * 2.5
    in_pond = pond_d < 4.2 + wob * 2.5
    level = np.where(in_pool, pl, level)
    level = np.where(in_pond & ~in_pool, sl_, level)
    stream_d = np.where(in_pool, np.minimum(stream_d, np.maximum(pool_d - 4.0, 0)), stream_d)
    stream_d = np.where(in_pond, np.minimum(stream_d, np.maximum(pond_d - 3.0, 0)), stream_d)
    wet = (stream_d < width) | in_pool | in_pond
    wet &= land | (dout < 3)
    # the fall: everything upstream of the lip stays at the lip's level, the cliff under it is a wall
    above_fall = (Z < lip[1] + 1.5) & (level > pl + 0.5)
    # banks: a small valley down to the water, a low levee where the ground is lower than the water
    bank = level + 0.3 + np.maximum(stream_d - width, 0) * 0.8
    near = (stream_d < 12) & land
    # do not let the upper valley eat into the ground below the fall, or the lower one into the ground above
    upper_side = Z < lip[1] + 0.5
    carve = near & ((level > pl + 0.5) == upper_side)
    h = np.where(carve, np.minimum(h, bank), h)
    h = np.where(near & ~wet & (stream_d < width + 1.7) & ((level > pl + 0.5) == upper_side),
                 np.maximum(h, level + 0.35), h)
    depth = np.where(in_pool, 2.0 + (pool_d < 3.0), 1.0)
    h = np.where(wet, level - depth, h)
    del above_fall
    # the fall itself: three columns wide, the stream runs to the edge, the pool reaches the foot of the wall
    lx, lz = int(lip[0]), int(lip[1])
    fall_z = lz + 3
    notch = (np.abs(X - lx) <= 1)
    up = notch & (Z <= fall_z - 1) & (Z >= lz - 1)
    down = notch & (Z >= fall_z) & (Z <= fall_z + 3)
    level = np.where(up, lip[2], level)
    level = np.where(down, pl, level)
    wet = wet | up | down
    h = np.where(up, lip[2] - 1.0, h)
    h = np.where(down, pl - 2.0, h)
    side = (np.abs(X - lx) == 2) & (Z <= fall_z - 1) & (Z >= lz - 1)
    h = np.where(side & ~wet, np.maximum(h, lip[2] + 0.4), h)
    t.fall = (lx, fall_z, int(lip[2]), int(pl))

    # ---------------------------------------------------------------- the quarry cut
    qx, qz = L.QUARRY
    # the floor opens to the south-east; benches climb into the hill to the north-west
    ang = np.arctan2(Z - qz, X - qx)
    qd = np.hypot((X - qx) / 1.2, (Z - qz)) + np.sin(ang * 3.0 + 0.6) * 1.0
    floor_r = 7.5
    step = np.floor(np.maximum(qd - floor_r, 0) / 3.0)
    qh = L.QUARRY_Y + 3.0 * step
    inq = (qd < floor_r + 3.0 * 4) & (h > qh + 0.5) & land
    h = np.where(inq, qh, h)
    apron = (qd < floor_r + 0.5) & ~inq & land
    h = np.where(apron, h * 0.25 + L.QUARRY_Y * 0.75, h)
    t.quarry = inq | apron

    # ---------------------------------------------------------------- settle
    hs = h.copy()
    keep = wet | t.quarry | ~land | (pads > 0.97) | scarp_band
    p = np.pad(hs, 1, mode="edge")
    blur = (p[1:-1, 1:-1] * 4 + p[:-2, 1:-1] + p[2:, 1:-1] + p[1:-1, :-2] + p[1:-1, 2:]) / 8.0
    hs = np.where(keep, hs, blur)
    H = np.floor(hs + 0.5).astype(np.int32)
    H = np.where(land & ~wet, np.maximum(H, L.SEA), H)       # dry land never dips under the sea
    H = np.minimum(H, L.Y0 + L.SY - 40)
    H = _dress_cliffs(H, land, wet | t.quarry | (pads > 0.9), seed)
    t.H = H
    t.stream = wet
    t.stream_dist = stream_d
    t.water = np.where(wet, level, -1).astype(np.int32)
    t.keep_clear = np.zeros(X.shape, bool)
    t.path = np.zeros(X.shape, np.uint8)      # 0 none, 1 trail, 2 road, 3 plaza (set by paths.py)
    t.pads = pads
    _slope(t)
    return t


def seal_water(t):
    """Call after the sites have shaped their ground. Makes the water a fixed point of the game's own rules,
    so nothing starts to run when a block next to it changes one day:

    * banks: every dry column beside water stands at least as high as that water;
    * steps: where the stream drops a level, a lower column that touches two or more higher ones becomes a
      rock (a sill; two sources side by side would breed a third), one that touches exactly one carries the
      thin film that runs over the edge. The game would place exactly that film itself.
    """
    H, land, wet = t.H, t.land, t.stream
    sz, sx = H.shape
    W = np.where(wet, t.water, np.where(~land, L.SEA, -1)).astype(np.int32)
    raised = 0
    zs, xs = np.nonzero(W >= 0)
    near = ((1, 0), (-1, 0), (0, 1), (0, -1))
    for zi, xi in zip(zs.tolist(), xs.tolist()):
        lv = int(W[zi, xi])
        for dz, dx in near:
            a, b = zi + dz, xi + dx
            if 0 <= a < sz and 0 <= b < sx and W[a, b] < 0 and H[a, b] < lv:
                H[a, b] = lv
                raised += 1
    t.sills, t.films = [], []
    for zi, xi in zip(zs.tolist(), xs.tolist()):
        lv = int(W[zi, xi])
        ups = []
        for dz, dx in near:
            a, b = zi + dz, xi + dx
            if 0 <= a < sz and 0 <= b < sx and W[a, b] > lv:
                ups.append(int(W[a, b]))
        if not ups or max(ups) - lv > 1:          # the fall is dressed on its own
            continue
        x, z = xi + L.X0, zi + L.Z0
        if len(ups) >= 2 or float(hash01(x, 7, z, L.SEED + 151)) < 0.22:
            t.sills.append((x, z, lv, max(ups)))
        else:
            t.films.append((x, z, max(ups)))
    _slope(t)
    return raised


def dress_stream(w, t):
    """The sills and films that seal_water worked out, as blocks. Call after paint."""
    rocks = ("mossy_cobblestone", "mossy_cobblestone", "andesite", "cobblestone", "mossy_cobblestone", "tuff")
    for (x, z, low, up) in t.sills:
        h = int(t.H[z - L.Z0, x - L.X0])
        for y in range(h + 1, up + 1):
            w.set(x, y, z, rocks[int(hash01(x, y, z, L.SEED + 153) * 97) % len(rocks)])
    for (x, z, up) in t.films:
        w.set(x, up, z, "water[level=1]")


def paint(w, t):
    _slope(t)
    _paint(w, t)
    w.set_biome(L.BIOME)


def _dress_cliffs(H, land, protect, seed):
    """Breaks up cliff lines: a ragged crown (blocks missing from the edge) and fallen rock at the foot."""
    xs = np.arange(L.X0, L.X0 + L.SX)
    zs = np.arange(L.Z0, L.Z0 + L.SZ)
    X, Z = np.meshgrid(xs, zs)
    p = np.pad(H, 2, mode="edge")
    c = p[2:-2, 2:-2]
    lows, highs = [], []
    for dz in range(-2, 3):
        for dx in range(-2, 3):
            if dx == 0 and dz == 0 or abs(dx) + abs(dz) > 3:
                continue
            n = p[2 + dz:2 + dz + H.shape[0], 2 + dx:2 + dx + H.shape[1]]
            lows.append(n)
            highs.append(n)
    lowest = np.minimum.reduce(lows)
    highest = np.maximum.reduce(highs)
    n1 = value(X, Z, 3.2, seed + 141)
    n2 = hash01(X, 1, Z, seed + 143)
    crown = land & ~protect & (c - lowest >= 5)
    foot = land & ~protect & (highest - c >= 5)
    out = H.copy()
    out = np.where(crown & (n1 > 0.58), out - 1 - (n2 < 0.35), out)
    out = np.where(foot & (n1 < 0.45), out + 1 + (n2 < 0.3), out)
    return out


def _slope(t):
    p = np.pad(t.H, 1, mode="edge")
    c = p[1:-1, 1:-1]
    t.slope = np.maximum.reduce([np.abs(c - p[:-2, 1:-1]), np.abs(c - p[2:, 1:-1]),
                                 np.abs(c - p[1:-1, :-2]), np.abs(c - p[1:-1, 2:])])
    # drop: how far this column stands above its lowest neighbour (cliff tops and faces)
    t.drop = np.maximum.reduce([c - p[:-2, 1:-1], c - p[2:, 1:-1], c - p[1:-1, :-2], c - p[1:-1, 2:]])


def _main_body(mask):
    """Keeps the connected land that contains the island centre; fills enclosed water."""
    sz, sx = mask.shape
    seen = np.zeros(mask.shape, bool)
    cz, cx = -L.Z0, -L.X0
    q = deque([(cz, cx)])
    seen[cz, cx] = True
    while q:
        z, x = q.popleft()
        for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            a, b = z + dz, x + dx
            if 0 <= a < sz and 0 <= b < sx and not seen[a, b] and mask[a, b]:
                seen[a, b] = True
                q.append((a, b))
    body = seen
    sea = np.zeros(mask.shape, bool)
    q = deque()
    for z in range(sz):
        for x in (0, sx - 1):
            if not body[z, x] and not sea[z, x]:
                sea[z, x] = True
                q.append((z, x))
    for x in range(sx):
        for z in (0, sz - 1):
            if not body[z, x] and not sea[z, x]:
                sea[z, x] = True
                q.append((z, x))
    while q:
        z, x = q.popleft()
        for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            a, b = z + dz, x + dx
            if 0 <= a < sz and 0 <= b < sx and not sea[a, b] and not body[a, b]:
                sea[a, b] = True
                q.append((a, b))
    return ~sea


def rock_ids(w, X, Y, Z):
    """Layered natural rock for coordinate arrays: tuff at the wet foot, andesite with pale bands, cobble patches."""
    seed = L.SEED
    warp = fbm(X, Z, 23, 3, seed + 31) * 3.0 + value(X + Y * 0.9, Z - Y * 0.6, 8.0, seed + 37) * 2.2
    band = Y + warp
    n = hash01(X, Y, Z, seed + 41)
    big = value(X + Y * 0.6, Z + Y * 0.4, 6.0, seed + 43)
    ids = np.full(np.shape(band), w.id("andesite"), dtype=np.uint16)
    ids = np.where(band < L.SEA + 3.2, w.id("tuff"), ids)
    ids = np.where((band < L.SEA + 2.6) & (n < 0.32), w.id("mossy_cobblestone"), ids)
    ids = np.where((band >= L.SEA + 3.2) & (band < L.SEA + 5.5) & (n < 0.4), w.id("tuff"), ids)
    for lo, hi, block in ((L.SEA + 10, L.SEA + 11.7, "diorite"), (L.SEA + 17.5, L.SEA + 18.5, "calcite"),
                          (L.SEA + 24.5, L.SEA + 26.4, "diorite"), (L.SEA + 31, L.SEA + 31.9, "calcite")):
        ids = np.where((band >= lo) & (band < hi), w.id(block), ids)
    ids = np.where((big > 0.74) & (band >= L.SEA + 3.2), w.id("cobblestone"), ids)
    ids = np.where((big < 0.15) & (band >= L.SEA + 5.5), w.id("tuff"), ids)
    return ids


def _paint(w, t):
    X, Z, H = t.X, t.Z, t.H
    seed = L.SEED
    sy = L.SY
    ys = (np.arange(sy) + L.Y0)[:, None, None]
    Hb = H[None, :, :]
    blocks = np.where(ys <= Hb, w.id("andesite"), 0).astype(np.uint16)

    # the visible skin (top 10 blocks of every column) gets the layered rock
    skin = (ys <= Hb) & (ys > Hb - 10)
    yi, zi, xi = np.nonzero(skin)
    blocks[yi, zi, xi] = rock_ids(w, xi + L.X0, yi + L.Y0, zi + L.Z0)

    land, din = t.land, t.din
    n1 = fbm(X, Z, 13, 3, seed + 51)
    n2 = hash01(X, 0, Z, seed + 53)
    steep = t.slope >= 2
    fx, fz = L.FLAT
    on_flat = land & (np.hypot((X - fx) / 1.35, Z - fz) < 24) & (H <= L.SEA + 1)
    beach = (land & (H <= L.SEA + 2) & (din < 7.5 + n1 * 3.0) & ~steep) | on_flat
    shore_mix = land & ~beach & (H <= L.SEA + 3) & (din < 11 + n1 * 3) & ~steep
    under = ~land

    grass = w.id("grass_block")
    dirt = w.id("dirt")
    sand = w.id("sand")
    gravel = w.id("gravel")
    coarse = w.id("coarse_dirt")
    clay = w.id("clay")

    top = np.full(H.shape, grass, dtype=np.uint16)
    sub = np.full(H.shape, dirt, dtype=np.uint16)
    soil = np.full(H.shape, 3, dtype=np.int32)

    top = np.where(shore_mix & (n2 < 0.40), coarse, top)
    top = np.where(shore_mix & (n2 >= 0.40) & (n2 < 0.62), sand, top)
    top = np.where(beach, sand, top)
    sub = np.where(beach, sand, sub)
    top = np.where(beach & (n1 > 0.45) & (n2 < 0.45), gravel, top)
    top = np.where(under, sand, top)
    sub = np.where(under, sand, sub)
    top = np.where(under & (n1 < -0.28), gravel, top)
    top = np.where(under & (n1 > 0.5) & (n2 < 0.5), clay, top)
    top = np.where(t.stream, np.where(n2 < 0.55, gravel, np.where(n2 < 0.82, sand, clay)), top)
    sub = np.where(t.stream, gravel, sub)
    # cliffs keep bare rock; thin soil on the gentler steps
    rocky = land & steep & ~beach
    soil = np.where(rocky, 0, soil)
    thin = land & ~rocky & (t.slope == 1) & (n2 < 0.2)
    soil = np.where(thin, 1, soil)
    soil = np.where(t.quarry, 0, soil)
    t.rocky = rocky

    for k in range(1, 4):
        m = (soil >= k) | ((under | t.stream | beach) & (k <= 3))
        yy = H - k
        zz, xx = np.nonzero(m & (yy >= L.Y0))
        blocks[yy[zz, xx] - L.Y0, zz, xx] = sub[zz, xx]
    zz, xx = np.nonzero((soil >= 1) | under | t.stream | beach)
    blocks[H[zz, xx] - L.Y0, zz, xx] = top[zz, xx]

    water = w.id("water")
    sea = (~land) | (land & (H < L.SEA) & ~t.stream)
    wl = np.where(t.stream, t.water, np.where(sea, L.SEA, -1))
    blocks = np.where((ys > Hb) & (ys <= wl[None, :, :]), water, blocks)
    w.blocks[:] = blocks
    # the fall: falling water down the wall, from the lip to the pool
    fx, fz, top_l, pool_l = t.fall
    for x in range(fx - 1, fx + 2):
        for y in range(pool_l + 1, top_l):
            w.set(x, y, fz, "water[level=8]")
        w.set(x, top_l, fz, "water[level=1]")
    t.top_kind = np.where(beach, 1, np.where(rocky, 2, np.where(under, 3, 0)))  # 0 grass 1 beach 2 rock 3 sea
