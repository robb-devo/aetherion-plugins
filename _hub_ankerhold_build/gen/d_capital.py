"""The Capital: walled terrace at y 78 — Ledger's Court, the Tallybell, Registry, Lucky Knot, Archive, Cherry Garden."""
from __future__ import annotations

import math
import random

import numpy as np
from scipy import ndimage

import structures as S
from common import gy, npc, YAW
from terrain import GX, GZ, PLATEAU_Y, poly_mask, PLATEAU
from world import (World, stairs, slab, log, fence, lantern, chain, bell, barrel, trapdoor, ladder, pane, wall,
                   leaves, door, X0, Z0, NX, NZ, WATER, FALLING_WATER, campfire)

Y = PLATEAU_Y            # ground block
F = PLATEAU_Y + 1        # feet

PLAZA = (-26, -16, 10, 14)
FOUNTAIN = (-8, -1)
TOWER = (12, -29, 21, -20)
COUNTING = (-42, -12, -29, 6)
REGISTRY = (26, -17, 40, -6)
TAVERN = (-27, 20, -13, 31)
ARCHIVE = (-54, -42, -37, -31)
GARDEN = (-66, -27, -44, -5)
GATES = {
    "harbour_steps": (44, 1, 50, 7),
    "south_gate": (22, 44, 28, 50),
    "west_gate": (-72, -2, -66, 4),
    "north_stair": (-14, -50, -6, -45),
}

STREETS = [
    # (points, width)
    ([(-10, -15), (-10, -47)], 9),               # North Avenue -> Grand Stair
    ([(10, 4), (47, 4)], 7),                    # East Avenue -> Harbour Steps
    ([(6, 14), (14, 30), (25, 44), (25, 48)], 5),  # South Street -> South Gate
    ([(-26, 1), (-70, 1)], 7),                  # West Street -> West Gate
    ([(-14, -30), (-36, -30), (-36, -24)], 3),   # lane to Archive
    ([(-18, 14), (-20, 36), (-30, 44)], 3),      # lane south-west
    ([(-48, 4), (-50, 22), (-46, 40)], 3),       # lane west
    ([(30, 4), (30, -2)], 3),                   # Registry forecourt
    ([(-2, -16), (6, -24), (8, -34)], 3),        # lane north-east
    ([(22, -10), (24, -18)], 2),
]
RESERVED = [PLAZA, TOWER, COUNTING, REGISTRY, TAVERN, ARCHIVE, GARDEN, (11, 9, 42, 14), (8, 13, 18, 22)]


def plan(P, T):
    # paths on the plateau keep the plateau height
    mats_main = (("stone_bricks", 4), ("polished_andesite", 2), ("cracked_stone_bricks", 1), ("andesite", 1))
    mats_lane = (("cobblestone", 3), ("stone_bricks", 1), ("mossy_cobblestone", 1), ("gravel", 1))
    for pts, w in STREETS:
        P.path([(x, z, Y) for x, z in pts], width=w, mats=mats_main if w >= 5 else mats_lane, step=None,
               smooth=1, set_height=False)
    P.occupy_rect(*PLAZA[:2], *PLAZA[2:])
    for r in RESERVED:
        P.occupy_rect(r[0], r[1], r[2], r[3])
    # Harbour Steps: down from the plateau east edge to the town street
    P.path([(42, 4, Y), (47, 4, Y), (60, 4, None)], width=5,
           mats=(("stone_bricks", 3), ("polished_andesite", 1)), step="stairs:stone_brick", smooth=2, maxgrade=1.0)
    # South gate stairs
    P.path([(25, 44, Y), (25, 49, Y), (27, 60, None), (34, 66, None)], width=5,
           mats=(("stone_bricks", 3), ("cobblestone", 2)), step="stairs:stone_brick", smooth=2, maxgrade=1.0)
    # West gate road
    P.path([(-62, 1, Y), (-70, 1, Y), (-80, 3, None)], width=5,
           mats=(("stone_bricks", 2), ("cobblestone", 2), ("gravel", 1)), step="cobblestone", smooth=3,
           maxgrade=0.6)


def street_mask(T):
    return T.pathmat >= 0


def retaining_walls(W: World, T):
    pl = T.plateau
    d_out = ndimage.distance_transform_edt(~pl)
    ring = (d_out > 0) & (d_out <= 2.3) & T.island_all
    ring1 = (d_out > 0) & (d_out <= 1.0) & T.island_all
    rng = np.random.default_rng(5)
    gate_cells = np.zeros_like(pl)
    for (x1, z1, x2, z2) in GATES.values():
        gate_cells |= (GX >= x1) & (GX <= x2) & (GZ >= z1) & (GZ <= z2)
    mats = [W.bid("stone_bricks")] * 6 + [W.bid("mossy_stone_bricks"), W.bid("cracked_stone_bricks")]
    for ix, iz in np.argwhere(ring):
        x, z = ix + X0, iz + Z0
        if gate_cells[ix, iz]:
            continue
        yo = int(T.top[ix, iz])
        lo = min(yo, Y) - 1
        hi = max(yo, Y)
        for yy in range(lo, hi + 1):
            W.set(x, yy, z, mats[rng.integers(0, len(mats))])
        # clear anything above (terrain) when outside is higher than the parapet line
        if yo <= Y:
            if ring1[ix, iz]:
                W.set(x, Y + 1, z, "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
            else:
                W.set(x, Y + 1, z, 0)
        else:
            for yy in range(hi + 1, hi + 4):
                if W.getn(x, yy, z) in ("minecraft:short_grass", "minecraft:air"):
                    W.set(x, yy, z, 0)
    # buttresses on tall stretches (outer side)
    tall = ring1 & ((Y - T.top) >= 7)
    pts = np.argwhere(tall)
    for k, (ix, iz) in enumerate(pts):
        x, z = ix + X0, iz + Z0
        if (x * 3 + z * 7) % 13 != 0 or gate_cells[ix, iz]:
            continue
        # outward direction = away from plateau
        gx = d_out[min(ix + 1, NX - 1), iz] - d_out[max(ix - 1, 0), iz]
        gz = d_out[ix, min(iz + 1, NZ - 1)] - d_out[ix, max(iz - 1, 0)]
        n = math.hypot(gx, gz) or 1
        ux, uz = gx / n, gz / n
        yo = int(T.top[ix, iz])
        for s in range(1, 4):
            bx, bz = round(x + ux * s), round(z + uz * s)
            ytop = Y - 1 - s * 2
            for yy in range(int(gy(T, bx, bz)), ytop + 1):
                W.set(bx, yy, bz, "stone_bricks")
            W.set(bx, ytop + 1, bz, stairs("stone_brick", "north" if uz > 0.5 else "south" if uz < -0.5 else
                                         "west" if ux > 0 else "east"))


def paint_plaza(W, T):
    x1, z1, x2, z2 = PLAZA
    cx, cz = FOUNTAIN
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            d = math.hypot(x - cx, z - cz)
            if edge:
                b = "stone_bricks"
            elif abs(d - 9) < 0.6 or abs(d - 12) < 0.6:
                b = "chiseled_stone_bricks" if (x + z) % 5 == 0 else "smooth_stone"
            elif (x - cx) == 0 or (z - cz) == 0:
                b = "smooth_stone"
            else:
                b = "polished_andesite" if (x // 2 + z // 2) % 2 else "andesite"
            W.set(x, Y, z, b)
            for yy in range(Y + 1, Y + 3):
                if "grass" in W.getn(x, yy, z) or "flower" in W.getn(x, yy, z):
                    W.set(x, yy, z, 0)


def fountain(W, cx, cz):
    for dx in range(-7, 8):
        for dz in range(-7, 8):
            d = math.hypot(dx, dz)
            x, z = cx + dx, cz + dz
            if d <= 5.3:
                W.set(x, Y - 1, z, "stone_bricks")
                W.set(x, Y, z, WATER)
            elif d <= 6.4:
                W.set(x, Y, z, "stone_bricks")
                W.set(x, Y + 1, z, slab("smooth_stone", "bottom"))
    # pedestal + upper bowl
    for yy in range(Y - 1, Y + 4):
        W.set(cx, yy, cz, "chiseled_stone_bricks" if yy == Y + 3 else "stone_bricks")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                W.set(cx + dx, Y + 4, cz + dz, "stone_brick_stairs[facing=%s,half=top,shape=straight,waterlogged=false]" %
                      ({(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}.get((dx, dz), "north")))
    W.set(cx, Y + 4, cz, WATER)
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        for yy in range(Y + 1, Y + 5):
            W.set(cx + dx, yy, cz + dz, FALLING_WATER)
    # the anchor statue on top
    for yy in range(Y + 5, Y + 10):
        W.set(cx, yy, cz, "polished_blackstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    W.set(cx - 1, Y + 9, cz, "polished_blackstone_stairs[facing=east,half=top,shape=straight,waterlogged=false]")
    W.set(cx + 1, Y + 9, cz, "polished_blackstone_stairs[facing=west,half=top,shape=straight,waterlogged=false]")
    W.set(cx, Y + 10, cz, "chain[axis=y,waterlogged=false]")
    W.set(cx - 2, Y + 5, cz, "polished_blackstone_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]")
    W.set(cx + 2, Y + 5, cz, "polished_blackstone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]")
    W.set(cx - 1, Y + 5, cz, "polished_blackstone_slab[type=bottom,waterlogged=false]")
    W.set(cx + 1, Y + 5, cz, "polished_blackstone_slab[type=bottom,waterlogged=false]")
    W.anchor("fountain.ledgers_court", cx + 0.5, Y + 1, cz + 0.5, radius=5.3,
             note="Wishing fountain (sneak+right-click water). Anchor statue on top.")


def tallybell(W, T):
    x1, z1, x2, z2 = TOWER
    top_shaft = Y + 34
    S.foundation(W, x1, z1, x2, z2, Y, "stone_bricks")
    for yy in range(Y, top_shaft + 1):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                edge = x in (x1, x2) or z in (z1, z2)
                corner = x in (x1, x2) and z in (z1, z2)
                if yy == Y:
                    W.set(x, yy, z, "polished_andesite")
                elif edge:
                    if corner:
                        W.set(x, yy, z, "polished_andesite" if yy % 6 else "chiseled_stone_bricks")
                    else:
                        W.set(x, yy, z, "stone_bricks" if (yy - Y) % 9 else "polished_andesite")
                else:
                    W.set(x, yy, z, 0)
    # windows (slits) every 6
    for yy in range(Y + 6, top_shaft - 2, 6):
        for x in ((x1 + x2) // 2, (x1 + x2) // 2 + 1):
            W.set(x, yy, z1, pane())
            W.set(x, yy + 1, z1, pane())
            W.set(x, yy, z2, pane())
            W.set(x, yy + 1, z2, pane())
        for z in ((z1 + z2) // 2, (z1 + z2) // 2 + 1):
            W.set(x1, yy, z, pane())
            W.set(x1, yy + 1, z, pane())
            W.set(x2, yy, z, pane())
            W.set(x2, yy + 1, z, pane())
    # door (south)
    dx = (x1 + x2) // 2
    for k in (1, 2, 3):
        W.set(dx, Y + k, z2, 0)
        W.set(dx + 1, Y + k, z2, 0)
    W.set(dx, Y + 4, z2, stairs("stone_brick", "east", "top"))
    W.set(dx + 1, Y + 4, z2, stairs("stone_brick", "west", "top"))
    # square spiral stair inside (inner box x1+1..x2-1, z1+1..z2-1)
    ring = []
    ix1, ix2, iz1, iz2 = x1 + 1, x2 - 1, z1 + 1, z2 - 1
    for x in range(ix1, ix2 + 1):
        ring.append((x, iz2))
    for z in range(iz2 - 1, iz1 - 1, -1):
        ring.append((ix2, z))
    for x in range(ix2 - 1, ix1 - 1, -1):
        ring.append((x, iz1))
    for z in range(iz1 + 1, iz2):
        ring.append((ix1, z))
    # start at the door side and climb clockwise
    y = Y
    i = ring.index((dx + 1, iz2)) if (dx + 1, iz2) in ring else 0
    steps = 0
    trail = []
    while y < top_shaft - 1:
        cx, cz = ring[i % len(ring)]
        nx_, nz_ = ring[(i + 1) % len(ring)]
        fac = "east" if nx_ > cx else "west" if nx_ < cx else "south" if nz_ > cz else "north"
        corner = (cx in (ix1, ix2)) and (cz in (iz1, iz2))
        trail.append((cx, cz))
        if corner:
            W.set(cx, y, cz, "stone_bricks")
        else:
            y += 1
            W.set(cx, y, cz, stairs("stone_brick", fac))
            W.set(cx, y - 1, cz, "stone_bricks") if y - 1 > Y else None
        for k in range(1, 4):
            if W.getn(cx, y + k, cz).startswith("minecraft:stone_brick_stairs") is False:
                W.set(cx, y + k, cz, 0)
        i += 1
        steps += 1
        if steps > 400:
            break
    # belfry floor + arches
    bf = top_shaft
    W.fill(x1, bf, z1, x2, bf, z2, "polished_andesite")
    # stairwell opening over the last steps + the arrival cell
    for (sx_, sz_) in trail[-6:] + [ring[i % len(ring)]]:
        W.set(sx_, bf, sz_, 0)
    for yy in range(bf + 1, bf + 8):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                edge = x in (x1, x2) or z in (z1, z2)
                corner = x in (x1, x2) and z in (z1, z2)
                pillar = corner or (x in (x1, x2) and z in (z1 + 3, z2 - 3)) or (z in (z1, z2) and x in (x1 + 3, x2 - 3))
                if pillar:
                    W.set(x, yy, z, "polished_andesite")
                elif edge and yy == bf + 1:
                    W.set(x, yy, z, "stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
                elif edge and yy >= bf + 6:
                    W.set(x, yy, z, "stone_bricks")
                elif edge and yy == bf + 5:
                    W.set(x, yy, z, stairs("stone_brick", "south" if z == z1 else "north" if z == z2 else
                                           "east" if x == x1 else "west", "top"))
    # the Tallybell
    bcx, bcz = (x1 + x2) // 2, (z1 + z2) // 2
    W.fill(x1 + 1, bf + 7, z1 + 1, x2 - 1, bf + 7, z2 - 1, "stone_bricks")
    W.set(bcx, bf + 6, bcz, "minecraft:bell[attachment=ceiling,facing=north,powered=false]")
    W.set(bcx + 1, bf + 6, bcz, chain("y"))
    W.set(bcx, bf + 5, bcz + 2, lantern(False)) if False else None
    # copper spire
    sy = bf + 8
    k = 0
    hw = (x2 - x1) / 2 + 0.6
    mx, mz = (x1 + x2) / 2, (z1 + z2) / 2
    while hw > 0.4:
        for x in range(x1 - 1, x2 + 2):
            for z in range(z1 - 1, z2 + 2):
                if abs(x - mx) <= hw and abs(z - mz) <= hw:
                    W.set(x, sy + k, z, "waxed_weathered_cut_copper")
        k += 1
        hw -= 0.42 if k > 2 else 0.9
    for kk in range(4):
        W.set(int(mx), sy + k + kk, int(mz), "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]" if kk == 3 else
              "waxed_weathered_cut_copper")
    W.anchor("bell.tallybell", bcx + 0.5, bf + 6, bcz + 0.5, note="Tallybell — ring it with an arrow, or climb up")
    W.anchor("vista.tallybell", bcx + 0.5, bf + 1, z2 - 1.5, note="Belfry gallery (vista)")
    W.anchor("landmark.tallybell", dx + 1.0, F, z2 + 1.5, note="The Tallybell (capital landmark)")
    return bf


def counting_house(W, T):
    x1, z1, x2, z2 = COUNTING
    info = S.house(W, x1, z1, x2, z2, Y, "capital_copper", floors=2, door_side="east", roof_axis="z", oh=1,
                   interior=False, storey=5, door_open=True)
    # arcade porch on the east face
    px = x2 + 1
    for z in range(z1, z2 + 1):
        W.set(px + 1, Y, z, "polished_andesite")
        if (z - z1) % 3 == 0:
            for yy in range(Y + 1, Y + 5):
                W.set(px + 1, yy, z, "stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
        W.set(px + 1, Y + 5, z, slab("stone_brick", "bottom"))
        W.set(px, Y + 5, z, slab("stone_brick", "bottom"))
    # Ledger's desk on the porch: lectern + ledger barrels
    dzc = (z1 + z2) // 2
    W.set(px, Y + 1, dzc - 2, "minecraft:lectern[facing=east,has_book=false,powered=false]")
    W.set(px, Y + 1, dzc - 1, barrel("up"))
    W.set(px, Y + 1, dzc - 3, barrel("up"))
    W.set(px, Y + 2, dzc - 1, "minecraft:candle[candles=3,lit=true,waterlogged=false]")
    S.wall_sign_at(W, x2 + 1, Y + 4, dzc + 2, "east", ["", "COUNTING HOUSE", "Miss Ledger", "skills · manager"],
                   mat="dark_oak") if False else None
    W.set(px, Y + 4, dzc, lantern(True))
    # interior: desk rows + shelves
    for z in range(z1 + 2, z2 - 1, 3):
        for x in range(x1 + 2, x2 - 3, 2):
            W.set(x, Y + 1, z, "minecraft:spruce_slab[type=top,waterlogged=false]")
    for z in range(z1 + 1, z2):
        W.set(x1 + 1, Y + 1, z, "bookshelf")
        W.set(x1 + 1, Y + 2, z, "bookshelf")
    npc(W, "ledger", px + 2.5, F, dzc - 1.5, YAW["east"], note="Miss Ledger at her porch desk, facing Ledger's Court")
    W.anchor("landmark.ledgers_court", px + 4.5, F, dzc + 0.5, note="Ledger's Court (capital plaza)")


def registry(W, T):
    x1, z1, x2, z2 = REGISTRY
    S.house(W, x1, z1, x2, z2, Y, "capital_copper", floors=2, door_side="south", roof_axis="x", interior=False)
    # guild banners (wool drapes) on the south facade
    for x, col in ((x1 + 2, "blue_wool"), (x1 + 6, "red_wool"), (x2 - 5, "yellow_wool"), (x2 - 2, "green_wool")):
        for yy in range(Y + 6, Y + 9):
            W.set(x, yy, z2 + 1, col)
        W.set(x, Y + 9, z2 + 1, log("stripped_dark_oak_log", "x"))
    # balcony toward the harbour (east)
    for z in range(z1 + 2, z2 - 1):
        W.set(x2 + 1, Y + 5, z, slab("dark_oak", "top"))
        W.set(x2 + 2, Y + 5, z, slab("dark_oak", "top"))
        W.set(x2 + 2, Y + 6, z, fence("dark_oak"))
    W.set(x2, Y + 6, (z1 + z2) // 2, 0)
    W.set(x2, Y + 7, (z1 + z2) // 2, 0)
    npc(W, "isle_clerk", (x1 + x2) / 2 + 0.5, F, z2 + 2.5, YAW["south"],
        note="Deed at the Harbour Registry (personal island deeds, guild charter)")
    W.anchor("landmark.registry", (x1 + x2) / 2 + 0.5, F, z2 + 3.5, note="Harbour Registry — deeds & guild charters")


def tavern(W, T):
    x1, z1, x2, z2 = TAVERN
    S.house(W, x1, z1, x2, z2, Y, "capital_warm", floors=2, door_side="north", roof_axis="x", chimney=True,
            interior=False)
    # tables inside
    for x in range(x1 + 2, x2 - 1, 3):
        for z in range(z1 + 3, z2 - 1, 3):
            W.set(x, Y + 1, z, fence("spruce"))
            W.set(x, Y + 2, z, "minecraft:spruce_pressure_plate[powered=false]")
            W.set(x + 1, Y + 1, z, stairs("spruce", "west"))
    for x in range(x1 + 1, x2):
        W.set(x, Y + 1, z2 - 1, barrel("north"))
    W.set((x1 + x2) // 2, Y + 4, z1 - 1, "minecraft:dark_oak_wall_sign[facing=north,waterlogged=false]")
    W.sign_text((x1 + x2) // 2, Y + 4, z1 - 1, ["", "THE LUCKY KNOT", "house wins", ""])
    npc(W, "vince", (x1 + x2) / 2 - 2.5, F, z1 - 1.5, YAW["north"], note="Lucky Vince outside the Lucky Knot")
    W.anchor("emitter.coins", (x1 + x2) / 2 + 0.5, F, z1 + 2.5, note="coins emitter (Vince's tables)")


def liquidator(W, T):
    x, z = 14, 17
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            W.set(x + dx, Y, z + dz, "polished_andesite" if abs(dx) < 2 and abs(dz) < 2 else "amethyst_block")
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for yy in range(Y + 1, Y + 4):
            W.set(x + dx, yy, z + dz, "stripped_dark_oak_log[axis=y]")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            W.set(x + dx, Y + 4, z + dz, "purple_wool" if (dx + dz) % 2 else "magenta_wool")
    W.set(x, Y + 1, z + 1, "amethyst_block")
    W.set(x, Y + 2, z + 1, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    W.set(x - 1, Y + 1, z + 1, barrel("up"))
    W.set(x + 1, Y + 1, z + 1, barrel("up"))
    npc(W, "liquidator", x + 0.5, F, z - 0.5, YAW["north"], note="Crystal Liquidator's kiosk by the plaza")


def archive(W, T):
    x1, z1, x2, z2 = ARCHIVE
    S.house(W, x1, z1, x2, z2, Y, "capital_copper", floors=2, door_side="south", roof_axis="x", interior=False)
    for x in range(x1 + 1, x2):
        for z in (z1 + 1, z2 - 1):
            if x != (x1 + x2) // 2 + 1:
                W.set(x, Y + 1, z, "bookshelf")
                W.set(x, Y + 2, z, "chiseled_bookshelf[facing=north,slot_0_occupied=false,slot_1_occupied=false,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]" if z == z1 + 1 else "bookshelf")
    W.set((x1 + x2) // 2, Y + 1, (z1 + z2) // 2, "minecraft:lectern[facing=south,has_book=false,powered=false]")
    W.anchor("landmark.archive", (x1 + x2) / 2 + 1.5, F, z2 + 1.5, note="The Archive (codex/collections vibe; future hook)")


def garden(W, T, rng):
    x1, z1, x2, z2 = GARDEN
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if not T.plateau[x - X0, z - Z0]:
                continue
            W.set(x, Y, z, "grass_block[snowy=false]")
    # gravel walk ring
    cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            d = math.hypot((x - cx) / 1.3, z - cz)
            if abs(d - 6.5) < 0.8 and T.plateau[x - X0, z - Z0]:
                W.set(x, Y, z, "gravel" if (x + z) % 3 else "coarse_dirt")
    # cherries
    for (tx, tz) in ((cx - 7, cz - 7), (cx + 7, cz - 6), (cx - 8, cz + 6), (cx + 7, cz + 7), (cx - 1, cz - 10)):
        if T.plateau[tx - X0, tz - Z0]:
            S.cherry_tree(W, tx, Y, tz, rng)
    # pink petals scatter
    for _ in range(80):
        x, z = rng.randint(x1, x2), rng.randint(z1, z2)
        if T.plateau[x - X0, z - Z0] and W.getn(x, Y, z).startswith("minecraft:grass_block") and W.is_air(x, Y + 1, z):
            W.set(x, Y + 1, z, f"minecraft:pink_petals[facing=north,flower_amount={rng.randint(1, 4)}]")
    # glowcap waystone (capital) in the middle
    glowcap(W, cx, Y, cz, h=6)
    S.bench(W, cx - 2, Y, cz + 4, "north", 3)
    S.bench(W, cx - 2, Y, cz - 4, "south", 3)
    npc(W, "orla_vane", cx + 3.5, F, cz + 0.5, YAW["east"], role="origin:orla",
        note="Origin board (journal / tour / bells / glowcaps folded in)")
    W.anchor("waystone.capital", cx + 1.5, F, cz + 1.5, note="Capital glowcap (Origin-light waystone)")


def glowcap(W, x, y, z, h=6, rng=None):
    """Small giant-mushroom waystone: stem + glowing cap."""
    for yy in range(y + 1, y + h):
        W.set(x, yy, z, "mushroom_stem[down=true,east=true,north=true,south=true,up=true,west=true]")
    cy = y + h
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            d = math.hypot(dx, dz)
            if d <= 3.2:
                W.set(x + dx, cy, z + dz, "brown_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=true]"
                      if d > 1.2 else "shroomlight")
            if d <= 2.2:
                W.set(x + dx, cy + 1, z + dz, "brown_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=true]")
            if 2.2 < d <= 3.2:
                W.set(x + dx, cy - 1, z + dz, "shroomlight" if (dx + dz) % 2 else 0)
    W.set(x, cy - 1, z, "ochre_froglight[axis=y]")


def market_stalls(W, T, rng):
    cols = ["red_wool", "yellow_wool", "blue_wool", "green_wool", "orange_wool", "cyan_wool"]
    for i, x in enumerate(range(14, 42, 7)):
        z1, z2 = 9, 12
        c = cols[i % len(cols)]
        for (px, pz) in ((x, z1), (x + 4, z1), (x, z2), (x + 4, z2)):
            for yy in range(Y + 1, Y + 4):
                W.set(px, yy, pz, fence("spruce"))
        for px in range(x - 1, x + 6):
            for pz in range(z1, z2 + 1):
                W.set(px, Y + 4, pz, c if (px + pz) % 2 else "white_wool")
        for px in range(x + 1, x + 4):
            W.set(px, Y + 1, z1, barrel("up") if px % 2 else "minecraft:spruce_planks")
        if i % 2 == 0:
            W.set(x + 2, Y + 2, z1, "minecraft:pumpkin" if i % 4 == 0 else "minecraft:melon")


def gates(W, T):
    # Harbour Steps arch at the east edge
    for (x, z1, z2) in ((45, 1, 7),):
        for z in (z1, z2):
            for yy in range(Y + 1, Y + 6):
                W.set(x, yy, z, "stone_bricks")
        for z in range(z1, z2 + 1):
            W.set(x, Y + 6, z, "stone_bricks")
            W.set(x, Y + 7, z, "stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]" if z % 2 else 0)
        W.set(x, Y + 5, z1 + 1, stairs("stone_brick", "south", "top"))
        W.set(x, Y + 5, z2 - 1, stairs("stone_brick", "north", "top"))
        W.set(x, Y + 5, (z1 + z2) // 2, lantern(True))
        W.set(x, Y + 5, (z1 + z2) // 2 - 1, slab("stone_brick", "top"))
        W.set(x, Y + 5, (z1 + z2) // 2 + 1, slab("stone_brick", "top"))
    W.anchor("gate.harbour_steps", 47.5, F, 4.5, note="Harbour Steps (capital east gate)")
    # south gate posts
    for (x, z) in ((22, 46), (28, 46)):
        for yy in range(Y + 1, Y + 5):
            W.set(x, yy, z, "stone_bricks")
        W.set(x, Y + 5, z, lantern(False))
    W.anchor("gate.south", 25.5, F, 46.5, note="South Gate (road from the mine / Temper)")
    # west gate posts
    for (x, z) in ((-68, -3), (-68, 5)):
        for yy in range(Y + 1, Y + 5):
            W.set(x, yy, z, "stone_bricks")
        W.set(x, Y + 5, z, lantern(False))
    W.anchor("gate.west", -68.5, F, 1.5, note="West Gate (fields / Vex)")


def pack_houses(W, T, rng):
    allowed = T.plateau.copy()
    allowed &= ndimage.distance_transform_edt(T.plateau) > 2.5
    st = T.pathmat >= 0
    st_d = ndimage.distance_transform_edt(~st)
    allowed &= st_d > 1.0
    res = np.zeros_like(allowed)
    for (x1, z1, x2, z2) in RESERVED + list(GATES.values()):
        res |= (GX >= x1 - 1) & (GX <= x2 + 1) & (GZ >= z1 - 1) & (GZ <= z2 + 1)
    allowed &= ~res
    placed = []
    styles = ["capital", "capital", "capital_brick", "capital_warm", "capital", "capital_brick"]
    taken = np.zeros_like(allowed)
    rng2 = np.random.default_rng(17)
    cand = np.argwhere(allowed)
    # street-front first, then the rest (courtyards stay where nothing fits)
    order = np.argsort(st_d[cand[:, 0], cand[:, 1]] + rng2.random(len(cand)) * 2.0)
    cand = cand[order]
    for ix, iz in cand:
        if taken[ix, iz]:
            continue
        ok = False
        for _ in range(6):
            w = int(rng2.integers(7, 14))
            d = int(rng2.integers(7, 12))
            for (ox, oz) in ((0, 0), (-w + 1, 0), (0, -d + 1), (-w + 1, -d + 1)):
                x1, z1 = ix + ox, iz + oz
                x2, z2 = x1 + w - 1, z1 + d - 1
                if x1 < 2 or z1 < 2 or x2 >= NX - 2 or z2 >= NZ - 2:
                    continue
                if not allowed[x1:x2 + 1, z1:z2 + 1].all():
                    continue
                if taken[x1 - 1:x2 + 2, z1 - 1:z2 + 2].any():
                    continue
                taken[x1:x2 + 1, z1:z2 + 1] = True
                placed.append((int(x1 + X0), int(z1 + Z0), int(x2 + X0), int(z2 + Z0)))
                ok = True
                break
            if ok:
                break
    out = []
    for (x1, z1, x2, z2) in placed:
        def sd(xa, za, xb, zb):
            xa, xb = max(xa - X0, 0), min(xb - X0, NX - 1)
            za, zb = max(za - Z0, 0), min(zb - Z0, NZ - 1)
            return st_d[xa:xb + 1, za:zb + 1].min()
        sides = {"north": sd(x1, z1 - 2, x2, z1 - 2), "south": sd(x1, z2 + 2, x2, z2 + 2),
                 "west": sd(x1 - 2, z1, x1 - 2, z2), "east": sd(x2 + 2, z1, x2 + 2, z2)}
        side = min(sides, key=sides.get)
        fl = 2 if rng.random() < 0.6 else 1
        if (x2 - x1) >= 9 and rng.random() < 0.35:
            fl = 3
        style = styles[(x1 * 7 + z1) % len(styles)]
        S.house(W, x1, z1, x2, z2, Y, style, floors=fl, door_side=side, rng=random.Random(x1 * 13 + z1),
                chimney=rng.random() < 0.35)
        out.append((x1, z1, x2, z2, side))
    T.capital_houses = out
    return out


def bastions(W, T):
    for (cx, cz) in ((-66, -31), (27, -43), (48, 18), (39, 43), (-59, 37), (-45, -45)):
        yb = min(gy(T, cx + dx, cz + dz) for dx in (-5, 0, 5) for dz in (-5, 0, 5))
        S.cylinder(W, cx, cz, 4.5, yb - 2, Y + 7, "stone_bricks")
        S.cylinder(W, cx, cz, 3.5, Y + 1, Y + 7, 0)
        S.cylinder(W, cx, cz, 4.5, Y, Y, "stone_bricks")
        S.cylinder(W, cx, cz, 3.5, Y, Y, "polished_andesite")
        for a in range(0, 360, 45):
            x = cx + round(4.5 * math.cos(math.radians(a)))
            z = cz + round(4.5 * math.sin(math.radians(a)))
            W.set(x, Y + 3, z, pane())
            W.set(x, Y + 4, z, pane())
        S.cylinder(W, cx, cz, 5.2, Y + 8, Y + 8, "stone_bricks")
        S.cone_roof(W, cx, cz, 5.2, Y + 9, "deepslate_tiles", slope=0.75, tip="minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
        # doorway from the plateau side
        dx, dz = (-cx) / (math.hypot(cx, cz) or 1), (-cz) / (math.hypot(cx, cz) or 1)
        ddx, ddz = round(cx + dx * 4.5), round(cz + dz * 4.5)
        W.set(ddx, Y + 1, ddz, 0)
        W.set(ddx, Y + 2, ddz, 0)
        W.set(cx, Y + 6, cz, lantern(True))


def build(W: World, T, rng):
    retaining_walls(W, T)
    paint_plaza(W, T)
    fountain(W, *FOUNTAIN)
    T.bells = getattr(T, "bells", {})
    tallybell(W, T)
    counting_house(W, T)
    registry(W, T)
    tavern(W, T)
    liquidator(W, T)
    archive(W, T)
    garden(W, T, rng)
    market_stalls(W, T, rng)
    gates(W, T)
    bastions(W, T)
    pack_houses(W, T, rng)
    # lamps along streets
    for pts, w in STREETS[:4]:
        for (a, b) in zip(pts[:-1], pts[1:]):
            L = math.dist(a, b)
            n = int(L // 10)
            for k in range(1, n + 1):
                t = k / (n + 1)
                x = round(a[0] + (b[0] - a[0]) * t)
                z = round(a[1] + (b[1] - a[1]) * t)
                # offset to the street edge
                ox, oz = (0, w // 2 + 1) if abs(b[0] - a[0]) > abs(b[1] - a[1]) else (w // 2 + 1, 0)
                for sgn in (1, -1):
                    lx, lz = x + ox * sgn, z + oz * sgn
                    if W.is_air(lx, F, lz) and T.plateau[lx - X0, lz - Z0]:
                        S.stone_lamp(W, lx, Y, lz)
                        break
    W.anchor("spawn.capital", -2.5, F, 6.5, yaw=YAW["west"], note="Capital camp: Ledger's Court south side")
    W.anchor("discover.capital", -8.5, F, -0.5, radius=36, note="walk-in discover centre")
