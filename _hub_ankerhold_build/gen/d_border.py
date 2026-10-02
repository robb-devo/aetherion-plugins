"""The Borderlands (west): Vex's Wall and gate, the Fallen Anchor, the Scar, the Rite Circle, the Colosseum fragment."""
from __future__ import annotations

import math
import random

import numpy as np
from scipy import ndimage

import structures as S
from common import gy, npc, YAW
from terrain import GX, GZ, WALL_LINE
from world import (World, stairs, slab, log, fence, lantern, chain, barrel, campfire, X0, Z0, NX, NZ, Y0)

GATE = (-88, -14)
RITE = (-122, 4)
CROWN = np.array([-152.0, 65.0, -24.0])
SCAR = [(-146, -36), (-136, -44), (-124, -46), (-112, -56)]
COLO = (-162, -143)


def plan(P, T):
    P.rect(GATE[0] - 9, GATE[1] - 6, GATE[0] + 8, GATE[1] + 6, int(round(T.h[GATE[0] - X0, GATE[1] - Z0])), blend=4)
    P.circle(*RITE, 9, int(round(T.h[RITE[0] - X0, RITE[1] - Z0])), blend=4)
    road = (("coarse_dirt", 3), ("gravel", 2), ("dirt_path", 1))
    P.path([(-80, 3), (-82, -8), (-92, -14), (-104, -14), (-118, -8)], width=3, mats=road, step=None, smooth=5)
    P.path([(-104, -16), (-118, -40), (-128, -64), (-130, -88), (-130, -100)], width=3, mats=road, step=None,
           smooth=5)
    P.path([(-118, -40), (-136, -48)], width=2, mats=road, step=None, smooth=4)
    # colosseum fragment flat top
    P.circle(*COLO, 20, 73, blend=2)


def palisade(W, T, rng):
    gx, gz = GATE
    cells = []
    for (a, b) in zip(WALL_LINE[:-1], WALL_LINE[1:]):
        L = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
        for i in range(L + 1):
            x = round(a[0] + (b[0] - a[0]) * i / L)
            z = round(a[1] + (b[1] - a[1]) * i / L)
            cells.append((x, z))
    seen = set()
    for (x, z) in cells:
        if (x, z) in seen:
            continue
        seen.add((x, z))
        if abs(x - gx) <= 3 and abs(z - gz) <= 3:
            continue
        if not T.island[x - X0, z - Z0]:
            continue
        y = gy(T, x, z)
        if y > 96:
            continue
        hgt = 5 + ((x * 7 + z * 3) % 3)
        for yy in range(y + 1, y + hgt):
            W.set(x, yy, z, log("spruce_log" if (x + z) % 5 else "stripped_spruce_log"))
        W.set(x, y + hgt, z, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
        # thicken: second row of logs on the inside where needed (diagonal gaps)
        for (dx, dz) in ((1, 0), (0, 1)):
            if (x + dx, z + dz) not in seen and (x + dx - 1, z + dz + 1) in seen:
                pass
    # gatehouse: two towers flanking a 5-wide gate (gate runs E-W)
    y = gy(T, gx, gz)
    for tz in (gz - 5, gz + 5):
        for x in range(gx - 2, gx + 3):
            for z in range(tz - 2, tz + 3):
                for yy in range(y - 2, y + 11):
                    edge = x in (gx - 2, gx + 2) or z in (tz - 2, tz + 2)
                    W.set(x, yy, z, ("cobblestone" if (x + yy) % 4 else "mossy_cobblestone") if edge or yy <= y else 0)
        for x in range(gx - 3, gx + 4):
            for z in range(tz - 3, tz + 4):
                if x in (gx - 3, gx + 3) or z in (tz - 3, tz + 3):
                    W.set(x, y + 11, z, "cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
                else:
                    W.set(x, y + 11, z, "spruce_planks")
        W.set(gx + 2, y + 1, tz, 0)
        W.set(gx + 2, y + 2, tz, 0)
        W.set(gx, y + 6, tz + (2 if tz < gz else -2), "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
    # lintel + warning bell
    for z in range(gz - 3, gz + 4):
        W.set(gx, y + 9, z, log("spruce_log", "z"))
        W.set(gx, y + 10, z, "spruce_planks")
    W.set(gx, y + 8, gz, "minecraft:bell[attachment=ceiling,facing=east,powered=false]")
    for z in (gz - 2, gz + 2):
        for yy in range(y + 6, y + 9):
            W.set(gx + 1, yy, z, "red_wool")
    # spikes / barricade outside
    for z in (gz - 7, gz + 7):
        W.set(gx + 2, y + 1, z, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
    W.anchor("bell.vex_gate", gx + 0.5, y + 8, gz + 0.5, note="Vex's warning bell over the gate")
    W.anchor("landmark.vexs_gate", gx + 3.5, y + 1, gz + 0.5, note="Vex's Gate")
    npc(W, "vex", gx + 4.5, y + 1, gz - 1.5, YAW["east"], note="Sergeant Vex outside his gate (danger edge)")
    W.anchor("spawn.borderlands", gx - 10.5, gy(T, gx - 10, gz) + 1, gz + 0.5, yaw=YAW["west"],
             note="Borderlands camp (just inside Vex's gate)")
    W.anchor("discover.borderlands", gx - 12.5, y + 1, gz + 0.5, radius=30)


def link_chain(W, pts, link_len=6, link_w=3.4, mat="deepslate_tiles", mat2="polished_deepslate", start_plane=0):
    """Giant chain along a polyline of 3D points (alternating link planes)."""
    pts = [np.array(p, dtype=float) for p in pts]
    # resample
    seq = []
    for a, b in zip(pts[:-1], pts[1:]):
        L = np.linalg.norm(b - a)
        n = max(1, int(L / (link_len - 1.6)))
        for k in range(n):
            seq.append(a + (b - a) * (k / n))
    seq.append(pts[-1])
    for i in range(len(seq) - 1):
        c = (seq[i] + seq[i + 1]) / 2
        u = seq[i + 1] - seq[i]
        u /= (np.linalg.norm(u) or 1)
        ref = np.array([0, 1.0, 0]) if abs(u[1]) < 0.9 else np.array([1.0, 0, 0])
        v1 = np.cross(u, ref)
        v1 /= np.linalg.norm(v1)
        v2 = np.cross(u, v1)
        v = v1 if (i + start_plane) % 2 == 0 else v2
        for ph in np.linspace(0, 2 * math.pi, 40, endpoint=False):
            p = c + u * (link_len / 2) * math.cos(ph) + v * (link_w / 2) * math.sin(ph)
            for d in ((0, 0, 0),):
                W.set(round(p[0]), round(p[1]), round(p[2]), mat if (i % 3) else mat2)


def anchor(W, T, rng):
    """Colossal anchor stuck in the west rim. Stock, arms and ring lie in the N-S plane so the classic
    silhouette reads from the capital and harbour (viewers look west)."""
    C = CROWN
    d = np.array([-math.sin(math.radians(8)), 1.0, -math.tan(math.radians(20))])
    d /= np.linalg.norm(d)
    zax = np.array([0, 0, 1.0])
    side = zax - np.dot(zax, d) * d
    side /= np.linalg.norm(side)
    mats = ["polished_blackstone", "polished_blackstone", "blackstone", "polished_basalt[axis=y]",
            "polished_blackstone", "gilded_blackstone"]
    mats_w = [0.35, 0.25, 0.2, 0.15, 0.04, 0.01]

    def pick():
        r = rng.random()
        acc = 0
        for m, w in zip(mats, mats_w):
            acc += w
            if r <= acc:
                return m
        return mats[0]

    def blob_line(p0, p1, r0, r1, xr=1.0):
        L = np.linalg.norm(p1 - p0)
        n = max(2, int(L * 2))
        for k in range(n + 1):
            t = k / n
            p = p0 + (p1 - p0) * t
            r = r0 + (r1 - r0) * t
            ri = int(math.ceil(r))
            for dx in range(-ri, ri + 1):
                for dy in range(-ri, ri + 1):
                    for dz in range(-ri, ri + 1):
                        if (dx / xr) ** 2 + dy * dy + dz * dz <= r * r + 0.2:
                            W.set(round(p[0] + dx), round(p[1] + dy), round(p[2] + dz), pick())

    L = 62
    top = C + d * L
    blob_line(C, top, 2.7, 2.1)
    # stock (cross-bar) near the top, in the N-S plane
    sp = C + d * (L - 9)
    blob_line(sp - side * 14, sp + side * 14, 1.6, 1.6)
    for s in (-1, 1):
        blob_line(sp + side * 14 * s, sp + side * 15.5 * s, 2.3, 2.3)
    # ring in the same plane
    rc = top + d * 5.5
    for ph in np.linspace(0, 2 * math.pi, 100, endpoint=False):
        p = rc + d * 5.5 * math.cos(ph) + side * 5.5 * math.sin(ph)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if abs(dx) + abs(dy) <= 1:
                    W.set(round(p[0] + dx), round(p[1] + dy), round(p[2]), pick())
    # arms + flukes (south arm in the air, north arm half-buried)
    R = 18
    for k in (-1, 1):
        prev = C
        for th in np.linspace(0, math.radians(74), 20)[1:]:
            p = C + R * (side * k * math.sin(th) + d * (1 - math.cos(th)))
            blob_line(prev, p, 2.6 - th * 0.8, 2.4 - th * 0.8, xr=1.2)
            prev = p
        tip = prev
        back = C + R * (side * k * math.sin(math.radians(60)) + d * (1 - math.cos(math.radians(60))))
        fwd = tip - back
        fwd /= np.linalg.norm(fwd)
        perp = np.cross(fwd, np.array([1.0, 0, 0]))
        perp /= np.linalg.norm(perp)
        for a in np.linspace(-1, 1, 17):
            for bb in np.linspace(0, 1, 14):
                w = (1 - bb) * 5.0
                p = tip - fwd * bb * 8 + perp * a * w
                for dx in (-1, 0, 1):
                    W.set(round(p[0] + dx), round(p[1]), round(p[2]), pick())
    # crater around the crown
    cx, cz = int(C[0]), int(C[2])
    for dx in range(-13, 14):
        for dz in range(-13, 14):
            r = math.hypot(dx, dz)
            ix, iz = cx + dx - X0, cz + dz - Z0
            if r > 13 or not T.island[ix, iz]:
                continue
            y = gy(T, cx + dx, cz + dz)
            if r < 10 and rng.random() < 0.65:
                W.set(cx + dx, y, cz + dz, rng.choice(["gravel", "basalt[axis=y]", "blackstone", "coarse_dirt", "tuff",
                                                       "smooth_basalt"]))
            if 8 < r < 13 and rng.random() < 0.15:
                W.set_if_air(cx + dx, y + 1, cz + dz, rng.choice(["cobbled_deepslate", "basalt[axis=y]", "tuff"]))
    # the snapped chain: from the ring it sags to the ground and trails west over the rim into the void
    gy1 = gy(T, cx - 4, cz + 10)
    ex = cx - 4
    while ex > -191 and T.island[ex - X0, cz + 12 - Z0]:
        ex -= 1
    pts = [rc + d * 4.5, rc + d * 2 + np.array([-3.0, -16, 6]), np.array([cx + 2, gy1 + 14, cz + 12.0]),
           np.array([cx - 3, gy1 + 2.5, cz + 13.0]), np.array([ex + 2, gy1 + 2.0, cz + 13.0]),
           np.array([ex - 3, gy1 - 6, cz + 13.0]), np.array([ex - 4, gy1 - 52, cz + 13.0])]
    link_chain(W, pts, link_len=7, link_w=4.2)
    W.anchor("landmark.fallen_anchor", float(C[0] + 16), gy(T, int(C[0] + 16), int(C[2])) + 1, float(C[2]),
             note="The Fallen Anchor — Ankerhold's torn third mooring (ring top ~y %d)" % int(rc[1] + 6))
    T.anchor_top = (float(rc[0]), float(rc[1] + 6), float(rc[2]))


def ruins(W, T, rng):
    """Burnt west-quarter houses: broken walls, charred beams, no roofs."""
    for (x1, z1, w, d) in ((-112, -66, 9, 7), (-104, 6, 8, 7), (-142, 4, 9, 8), (-120, -86, 7, 7),
                           (-100, -40, 7, 6), (-136, 22, 8, 6)):
        ix, iz = x1 - X0, z1 - Z0
        if not T.island[ix, iz]:
            continue
        y = gy(T, x1 + w // 2, z1 + d // 2)
        S.foundation(W, x1, z1, x1 + w, z1 + d, y, "cobblestone", "mossy_cobblestone", rng=rng)
        for x in range(x1, x1 + w + 1):
            for z in range(z1, z1 + d + 1):
                edge = x in (x1, x1 + w) or z in (z1, z1 + d)
                if not edge:
                    if rng.random() < 0.25:
                        W.set_if_air(x, y + 1, z, rng.choice(["gravel", "coarse_dirt", "stripped_dark_oak_log[axis=x]"]))
                    continue
                hgt = int(rng.random() ** 1.6 * 5)
                for yy in range(y + 1, y + 1 + hgt):
                    W.set(x, yy, z, rng.choice(["cobblestone", "mossy_cobblestone", "cobblestone", "blackstone"]))
        # charred beams
        for _ in range(2):
            bz = z1 + rng.randint(1, d - 1)
            yb = y + rng.randint(2, 4)
            for x in range(x1, x1 + w + 1):
                if rng.random() < 0.8:
                    W.set(x, yb + (x - x1) // 4, bz, "stripped_dark_oak_log[axis=x]")
        W.set_if_air(x1 + w // 2, y + 1, z1 + d // 2, campfire(False))
    W.anchor("landmark.burnt_quarter", -110.5, gy(T, -110, -40) + 1, -40.5,
             note="The burnt west quarter (houses that stood where the anchor came down)")


def scar(W, T, rng):
    pts = np.array(SCAR, dtype=float)
    for (a, b) in zip(pts[:-1], pts[1:]):
        L = np.linalg.norm(b - a)
        n = int(L * 2)
        for k in range(n + 1):
            t = k / n
            p = a + (b - a) * t
            gt = (np.linalg.norm(p - pts[0]) / np.linalg.norm(pts[-1] - pts[0]))
            w = 0.8 + 2.4 * math.sin(math.pi * min(1, gt))
            ri = int(math.ceil(w)) + 1
            for dx in range(-ri, ri + 1):
                for dz in range(-ri, ri + 1):
                    x, z = int(round(p[0] + dx)), int(round(p[1] + dz))
                    r = math.hypot(p[0] - x, p[1] - z)
                    if r <= w:
                        ix, iz = x - X0, z - Z0
                        # rock bridges: in the middle, and wherever a road crosses
                        if abs(gt - 0.55) < 0.03 or T.pathmat[ix, iz] >= 0:
                            continue
                        top = int(T.top[ix, iz])
                        bot = int(T.bot[ix, iz]) - 30
                        W.fill(x, bot, z, x, top + 4, z, 0)
                    elif r <= w + 1.3:
                        y = gy(T, x, z)
                        W.set(x, y, z, rng.choice(["basalt[axis=y]", "blackstone", "smooth_basalt", "tuff"]))
    W.anchor("landmark.the_scar", -131.5, gy(T, -131, -27) + 1, -26.5, note="The Scar — a crack straight through the island")


def rite_circle(W, T, rng):
    cx, cz = RITE
    y = gy(T, cx, cz)
    for dx in range(-8, 9):
        for dz in range(-8, 9):
            r = math.hypot(dx, dz)
            if r <= 8.5:
                W.set(cx + dx, y, cz + dz, "gravel" if r > 6 else ("coarse_dirt" if (dx + dz) % 3 else "packed_mud"))
    for k in range(7):
        a = k / 7 * 2 * math.pi + 0.3
        x, z = cx + round(math.cos(a) * 7), cz + round(math.sin(a) * 7)
        h = 3 + (k * 5) % 4
        for yy in range(y + 1, y + 1 + h):
            W.set(x, yy, z, "polished_basalt[axis=y]" if k % 2 else "bone_block[axis=y]")
        if k % 2 == 0:
            W.set(x, y + 1 + h, z, "minecraft:soul_lantern[hanging=false,waterlogged=false]")
    W.fill(cx - 1, y + 1, cz - 1, cx + 1, y + 1, cz + 1, "polished_blackstone_bricks")
    W.set(cx, y + 2, cz, "minecraft:skeleton_skull[powered=false,rotation=4]")
    for (dx, dz) in ((-3, 0), (3, 0)):
        W.set(cx + dx, y + 1, cz + dz, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    npc(W, "rite_keeper", cx + 0.5, y + 1, cz + 9.5, YAW["north"], note="Rite Warden at the Rite Circle")
    W.anchor("landmark.rite_circle", cx + 0.5, y + 1, cz + 5.5, note="The Rite Circle")


def wastes(W, T, rng):
    # dead trees, bone heaps, soul fires, wrecked cart across the Borderlands
    cells = np.argwhere((T.dmap == 7) & T.island & ~T.occ & (T.edge > 4))
    rng2 = np.random.default_rng(4)
    pick = cells[rng2.random(len(cells)) < 0.006]
    for ix, iz in pick:
        x, z = ix + X0, iz + Z0
        y = gy(T, x, z)
        r = rng.random()
        if r < 0.55:
            S.dead_tree(W, x, y, z, rng)
        elif r < 0.85:
            for _ in range(rng.randint(2, 5)):
                W.set_if_air(x + rng.randint(-1, 1), y + 1, z + rng.randint(-1, 1),
                             rng.choice(["bone_block[axis=y]", "bone_block[axis=x]", "gravel"]))
        else:
            W.set_if_air(x, y + 1, z, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    for ix, iz in cells[rng2.random(len(cells)) < 0.02]:
        x, z = ix + X0, iz + Z0
        y = gy(T, x, z)
        W.set_if_air(x, y + 1, z, rng.choice(["minecraft:dead_bush", "minecraft:dead_bush", "minecraft:brown_mushroom"]))


def colosseum(W, T, rng):
    cx, cz = COLO
    y = 73
    for dx in range(-21, 22):
        for dz in range(-21, 22):
            r = math.hypot(dx, dz)
            x, z = cx + dx, cz + dz
            if r <= 12.5:
                W.set(x, y, z, "sand" if (dx * dz) % 7 else "smooth_sandstone")
                for yy in range(y + 1, y + 14):
                    W.set(x, yy, z, 0)
            elif r <= 17.5:
                tier = int((r - 12.5) / 1.25)
                for yy in range(y, y + 1 + tier):
                    W.set(x, yy, z, "smooth_sandstone" if yy < y + tier else "cut_sandstone")
                for yy in range(y + 1 + tier, y + 14):
                    W.set(x, yy, z, 0)
            elif r <= 19.6:
                for yy in range(y - 2, y + 12):
                    ang = math.degrees(math.atan2(dz, dx)) % 360
                    arch = (ang % 30) < 9 and y + 1 <= yy <= y + 4 and r > 18.4
                    W.set(x, yy, z, 0 if arch else ("sandstone" if (yy - y) % 4 else "cut_sandstone"))
                if (int(math.degrees(math.atan2(dz, dx)) % 360) // 6) % 2 == 0:
                    W.set(x, y + 12, z, "sandstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    # entrance toward the bridge (east): cut through seats
    for dx in range(12, 21):
        for dz in range(-2, 3):
            for yy in range(y + 1, y + 6):
                W.set(cx + dx, yy, cz + dz, 0)
            W.set(cx + dx, y, cz + dz, "cut_sandstone")
    for dz in (-3, 3):
        for yy in range(y + 1, y + 8):
            W.set(cx + 19, yy, cz + dz, "chiseled_sandstone")
    for dz in range(-3, 4):
        W.set(cx + 19, y + 8, cz + dz, "cut_sandstone")
    W.set(cx + 19, y + 7, cz, lantern(True))
    # banners (wool) on the wall
    for a in range(15, 360, 60):
        x = cx + round(19.6 * math.cos(math.radians(a)))
        z = cz + round(19.6 * math.sin(math.radians(a)))
        for yy in range(y + 7, y + 11):
            W.set(x, yy, z, "red_wool" if a % 120 == 15 else "yellow_wool")
    npc(W, "arena_proctor", cx + 22.5, y + 1, cz + 0.5, YAW["east"], note="Proctor at the Colosseum gate")
    W.anchor("spawn.colosseum", cx + 25.5, y + 1, cz + 0.5, yaw=YAW["west"], note="Colosseum camp")
    W.anchor("landmark.colosseum", cx + 0.5, y + 1, cz + 0.5, note="The Colosseum (floating fragment)")
    W.anchor("emitter.colosseum", cx + 0.5, y + 4, cz + 0.5, note="arena crowd emitter")


def bridge_to_colosseum(W, T, rng):
    from d_harbour import rope_bridge
    main = T.island & ~T.colo
    colo = T.colo
    # nearest pair of edge cells
    cm = np.argwhere(colo & (ndimage.binary_dilation(~colo, iterations=1)))
    mm = np.argwhere(main & (ndimage.binary_dilation(~main, iterations=1)) & (GX < -110) & (GZ < -80))
    best = None
    for (ax, az) in mm[::2]:
        d = np.hypot(cm[:, 0] - ax, cm[:, 1] - az)
        j = int(np.argmin(d))
        if best is None or d[j] < best[0]:
            best = (d[j], (ax, az), tuple(cm[j]))
    _, (ax, az), (bx, bz) = best
    a = (int(ax + X0), int(gy(T, ax + X0, az + Z0)), int(az + Z0))
    b = (int(bx + X0), 73, int(bz + Z0))
    # make it axis-friendly: go along the longer axis
    rope_bridge(W, (a[0], a[1], a[2]), (b[0], 73, b[2]), width=5, sag=2.0)
    W.anchor("landmark.colosseum_bridge", (a[0] + b[0]) / 2, 74, (a[2] + b[2]) / 2,
             note="Rope bridge from the Borderlands to the Colosseum fragment")
    T.colo_bridge = (a, b)


def build(W: World, T, rng):
    palisade(W, T, rng)
    scar(W, T, rng)
    anchor(W, T, rng)
    ruins(W, T, rng)
    rite_circle(W, T, rng)
    wastes(W, T, rng)
    colosseum(W, T, rng)
    bridge_to_colosseum(W, T, rng)
