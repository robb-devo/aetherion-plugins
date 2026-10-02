"""Anker Harbour: lagoon, quays, the long pier, pier head over the Spill, the sky-sloop, lighthouse, town."""
from __future__ import annotations

import math
import random

import numpy as np

import structures as S
from common import gy, npc, YAW
from terrain import GX, GZ
from world import (World, WATER_Y, stairs, slab, log, fence, lantern, chain, bell, barrel, trapdoor, ladder, pane, campfire,
                   leaves, door, X0, Z0, NX, NZ, FALLING_WATER, WATER)

QUAY_Y = 64
DECK_Y = 64

# (key, x1, z1, x2, z2, style, floors, door, extra)
TOWN = [
    ("tavern", 54, 13, 66, 25, "harbour_warm", 2, "east", dict(chimney=True)),
    ("house_w1", 55, -7, 63, 1, "harbour", 2, "east", {}),
    ("house_w2", 55, 29, 63, 36, "harbour_blue", 1, "east", {}),
    ("house_w3", 47, -26, 55, -18, "harbour", 2, "east", {}),
    ("house_w4", 59, -24, 67, -16, "harbour_blue", 1, "east", dict(chimney=True)),
    ("house_w5", 46, 39, 54, 47, "harbour_warm", 1, "north", {}),
    ("house_w6", 46, -44, 56, -36, "harbour_warm", 2, "south", {}),
    ("warehouse", 72, -36, 88, -26, "harbour_blue", 2, "south", {}),
    ("house_n1", 92, -40, 100, -32, "harbour", 2, "south", {}),
    ("house_n2", 103, -44, 111, -36, "harbour_warm", 1, "south", dict(chimney=True)),
    ("workshop", 72, 38, 84, 47, "harbour_warm", 1, "north", dict(chimney=True)),
    ("house_s1", 108, 37, 116, 45, "harbour", 2, "north", {}),
    ("house_s2", 120, 35, 128, 42, "harbour_blue", 1, "north", {}),
    ("house_s3", 62, 50, 70, 58, "harbour", 1, "north", {}),
]
FORGE = (90, 38, 104, 48)  # Quartermaster's forge (open front, north)
SLIPS = (118, -42, 141, -21)


def plan(P, T):
    T.town_pads = {}
    for key, x1, z1, x2, z2, style, fl, dr, ex in TOWN + [("forge", *FORGE, "forge", 1, "north", {})]:
        ix1, ix2, iz1, iz2 = x1 - X0, x2 - X0, z1 - Z0, z2 - Z0
        h = np.nanmean(T.h[ix1:ix2 + 1, iz1:iz2 + 1])
        y = int(round(max(h, QUAY_Y + 1)))
        P.rect(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y, blend=4)
        T.town_pads[key] = y
    # main street from west quay up to the Harbour Steps foot
    P.path([(70, 8), (62, 8), (56, 4)], width=5, mats=(("cobblestone", 3), ("stone_bricks", 2), ("andesite", 1)),
           step="cobblestone", smooth=3, maxgrade=0.4)
    # town lanes
    P.path([(66, -12), (58, -10), (52, -12), (50, -30)], width=3,
           mats=(("cobblestone", 3), ("gravel", 1), ("mossy_cobblestone", 1)), step="cobblestone", smooth=3)
    P.path([(70, 28), (60, 27), (52, 30), (50, 37)], width=3,
           mats=(("cobblestone", 3), ("gravel", 1), ("andesite", 1)), step="cobblestone", smooth=3)
    P.path([(86, -24), (88, -16)], width=3, mats=(("cobblestone", 2), ("gravel", 1)), step="cobblestone", smooth=2)
    # lighthouse rock pad
    # nothing: natural


def paint_quay(W: World, T):
    rng = np.random.default_rng(11)
    cells = np.argwhere(T.quay)
    mats = [W.bid("stone_bricks"), W.bid("stone_bricks"), W.bid("cracked_stone_bricks"),
            W.bid("mossy_stone_bricks"), W.bid("polished_andesite")]
    for ix, iz in cells:
        x, z = ix + X0, iz + Z0
        y = T.top[ix, iz]
        if y != QUAY_Y:
            continue
        W.set(x, y, z, mats[rng.integers(0, len(mats))])
        for yy in range(y - 3, y):
            W.set(x, yy, z, "stone_bricks")
        if W.getn(x, y + 1, z) != "minecraft:air" and "water" not in W.getn(x, y + 1, z):
            W.set(x, y + 1, z, 0)
    # lagoon-side wall faces + bollards
    k = 0
    for ix, iz in cells:
        x, z = ix + X0, iz + Z0
        nearw = any(T.water[ix + dx, iz + dz] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                    if 0 <= ix + dx < NX and 0 <= iz + dz < NZ)
        if nearw:
            k += 1
            if (x * 7 + z * 13) % 11 == 0:
                W.set(x, QUAY_Y + 1, z, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
            elif (x * 5 + z * 3) % 17 == 0:
                S.lamp_post(W, x, QUAY_Y, z, h=3)


def pier_segment(W, x1, x2, z1, z2, y, piling_floor=None, rails=True, lamps=True, mat="spruce"):
    """Wooden deck between x1..x2 / z1..z2 (inclusive) at y. Rails on the long edges."""
    along_x = (x2 - x1) >= (z2 - z1)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = (z in (z1, z2)) if along_x else (x in (x1, x2))
            if edge:
                W.set(x, y, z, log(f"stripped_{mat}_log", "x" if along_x else "z"))
            else:
                W.set(x, y, z, f"minecraft:{mat}_planks" if (x + z) % 7 else f"minecraft:{mat}_slab[type=top,waterlogged=false]")
            # clear above
            W.set(x, y + 1, z, 0)
    # pilings + rails
    if along_x:
        for x in range(x1, x2 + 1):
            for z in (z1, z2):
                if (x - x1) % 6 == 0:
                    if piling_floor is not None:
                        for yy in range(piling_floor, y):
                            W.set(x, yy, z, log(f"{mat}_log"))
                    if lamps and (x - x1) % 12 == 0:
                        S.lamp_post(W, x, y, z, mat, h=3)
                    elif rails:
                        W.set(x, y + 1, z, S.fence(mat))
                elif rails:
                    W.set(x, y + 1, z, S.fence(mat))
    else:
        for z in range(z1, z2 + 1):
            for x in (x1, x2):
                if (z - z1) % 6 == 0:
                    if piling_floor is not None:
                        for yy in range(piling_floor, y):
                            W.set(x, yy, z, log(f"{mat}_log"))
                    if lamps and (z - z1) % 12 == 0:
                        S.lamp_post(W, x, y, z, mat, h=3)
                    elif rails:
                        W.set(x, y + 1, z, S.fence(mat))
                elif rails:
                    W.set(x, y + 1, z, S.fence(mat))


def under_truss(W, x1, x2, z1, z2, y, root_x):
    """Diagonal timber braces under a cantilevered deck, rooted in the cliff at root_x."""
    for z in (z1, z2):
        for x in range(x1, x2 + 1):
            d = x - root_x
            if d < 0:
                continue
            # brace from cliff (deep) to deck
            yy = y - 1 - max(0, (x2 - x) // 3)
            if (x - x1) % 6 == 0:
                for k in range(1, 7):
                    W.set(x - k, y - k, z, log("spruce_log", "x"))
    for x in range(x1, x2 + 1):
        W.set(x, y - 1, (z1 + z2) // 2, log("stripped_spruce_log", "x"))


def ship(W: World, x0, z0, y_deck, length=31, beam=9, rng=None):
    """Sky-sloop: bow toward +x. x0 = stern x, z0 = centreline z. Deck at y_deck."""
    rng = rng or random.Random(5)
    hull = "dark_oak_planks"
    stripe = "spruce_planks"
    half = beam / 2.0
    keel = y_deck - 6
    for i in range(length):
        x = x0 + i
        t = i / (length - 1)
        # plan-view half width: narrow stern, full mid, pointed bow
        if t < 0.15:
            w = half * (0.72 + 0.28 * t / 0.15)
        elif t > 0.68:
            w = half * max(0.0, 1 - ((t - 0.68) / 0.32) ** 1.6)
        else:
            w = half
        for y in range(keel, y_deck + 1):
            depth = (y_deck - y) / (y_deck - keel)
            ww = w * (1 - depth ** 1.8 * 0.85)
            if t > 0.68:
                ww *= (1 - depth * 0.25)
            wi = int(math.floor(ww + 0.35))
            if wi < 0:
                continue
            for dz in range(-wi, wi + 1):
                z = z0 + dz
                edge = abs(dz) == wi or y == keel
                if y == y_deck:
                    W.set(x, y, z, "oak_planks" if not abs(dz) == wi else log("stripped_dark_oak_log", "x"))
                elif edge:
                    W.set(x, y, z, stripe if (y_deck - y) == 2 else hull)
                else:
                    W.set(x, y, z, 0 if y > keel + 1 else hull)
            # gunwale rail
            if y == y_deck:
                for dz in (-wi, wi):
                    W.set(x, y + 1, z0 + dz, S.fence("dark_oak") if 2 < i < length - 3 else "dark_oak_planks")
        # keel fin
        W.set(x, keel - 1, z0, log("dark_oak_log", "x"))
    # stern castle
    for x in range(x0, x0 + 6):
        for dz in range(-3, 4):
            W.set(x, y_deck + 1, z0 + dz, "dark_oak_planks" if abs(dz) == 3 or x == x0 else 0)
            W.set(x, y_deck + 2, z0 + dz, "dark_oak_planks" if abs(dz) == 3 or x == x0 else 0)
            W.set(x, y_deck + 3, z0 + dz, "spruce_planks")
            W.set(x, y_deck + 4, z0 + dz, S.fence("dark_oak") if abs(dz) == 3 or x == x0 else 0)
    for dz in (-1, 0, 1):
        W.set(x0 + 5, y_deck + 1, z0 + dz, 0)
        W.set(x0 + 5, y_deck + 2, z0 + dz, 0)
    W.set(x0 + 5, y_deck + 3, z0 - 2, lantern(True)) if False else None
    W.set(x0 + 2, y_deck + 2, z0, lantern(True))
    # stair to the castle roof
    W.set(x0 + 6, y_deck + 1, z0 + 2, stairs("dark_oak", "west"))
    W.set(x0 + 5, y_deck + 2, z0 + 2, 0)
    # helm
    W.set(x0 + 2, y_deck + 4, z0, "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]")
    # masts + sails
    for mx, mh, sw in ((x0 + 17, 28, 4), (x0 + 9, 22, 3)):
        for y in range(y_deck + 1, y_deck + mh):
            W.set(mx, y, z0, log("spruce_log"))
        for yard_y, span in ((y_deck + mh - 3, sw), (y_deck + mh - 12, sw + 1)):
            for dz in range(-span - 1, span + 2):
                W.set(mx, yard_y, z0 + dz, log("stripped_spruce_log", "z"))
            # sail hangs below yard, bellied toward +x
            for k in range(1, 9):
                for dz in range(-span, span + 1):
                    belly = 1 if (2 <= k <= 6 and abs(dz) < span) else 0
                    W.set(mx + belly, yard_y - k, z0 + dz, "white_wool" if (k + dz) % 9 else "light_gray_wool")
        W.set(mx, y_deck + mh, z0, lantern(False))
        # crow's nest
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx or dz:
                    W.set(mx + dx, y_deck + mh - 7, z0 + dz, slab("spruce", "top"))
                    W.set(mx + dx, y_deck + mh - 6, z0 + dz, S.fence("spruce"))
        # pennant
        for k in range(3):
            W.set(mx - 1 - k, y_deck + mh - 1, z0, "red_wool" if k < 2 else 0)
    # bowsprit
    bx = x0 + length - 1
    for k in range(1, 6):
        W.set(bx + k - 2, y_deck + 1 + k // 2, z0, log("spruce_log", "x"))
    # lanterns + cargo
    for x in (x0 + 8, x0 + 20):
        W.set(x, y_deck + 1, z0 - 3, barrel("up"))
        W.set(x + 1, y_deck + 1, z0 - 3, barrel("up"))
    W.set(x0 + 22, y_deck + 1, z0 + 2, "minecraft:hay_block[axis=y]")
    W.set(x0 + 13, y_deck + 3, z0, lantern(True)) if False else None
    for x in (x0 + 7, x0 + 15, x0 + 24):
        W.set(x, y_deck + 1, z0 + 3, lantern(False))
    # name board
    W.sign_text(x0, y_deck + 2, z0, ["", "THE LONG HAUL", "Ankerhold", ""]) if False else None
    return dict(stern=x0, bow=x0 + length - 1, z=z0)


def rowboat(W, x, z, y, axis="x", mat="spruce"):
    L = 6
    for i in range(L):
        for dz in (-1, 0, 1):
            xx, zz = (x + i, z + dz) if axis == "x" else (x + dz, z + i)
            end = i in (0, L - 1)
            if end and dz != 0:
                continue
            if dz == 0 and not end:
                W.set(xx, y, zz, f"minecraft:{mat}_slab[type=bottom,waterlogged=true]")
            else:
                W.set(xx, y, zz, f"minecraft:{mat}_planks")
            if not end and dz != 0:
                W.set(xx, y + 1, zz, trapdoor(mat, ("north" if dz > 0 else "south") if axis == "x" else
                                              ("west" if dz > 0 else "east"), open_=True))


def lighthouse(W, T, cx, cz):
    y0 = max(gy(T, cx + dx, cz + dz) for dx in (-3, 0, 3) for dz in (-3, 0, 3))
    R = 4
    H = 32
    S.cylinder(W, cx, cz, R + 1.5, y0 - 3, y0, "stone_bricks")
    for k in range(H):
        y = y0 + 1 + k
        band = "red_terracotta" if (k // 5) % 2 == 1 else "white_terracotta"
        if k < 3:
            band = "stone_bricks"
        S.cylinder(W, cx, cz, R, y, y, band, hollow=True, thick=1.0)
        S.cylinder(W, cx, cz, R - 1, y, y, 0)
    # windows
    for k in (8, 16, 24):
        for dx, dz in ((R, 0), (-R, 0), (0, R), (0, -R)):
            W.set(cx + dx, y0 + 1 + k, cz + dz, pane())
            W.set(cx + dx, y0 + 2 + k, cz + dz, pane())
    # door west + ladder inside on east wall
    W.set(cx - R, y0 + 1, cz, 0)
    W.set(cx - R, y0 + 2, cz, 0)
    for k in range(1, H + 1):
        W.set(cx + R - 1, y0 + k, cz, ladder("west"))
    gal = y0 + H + 1
    S.cylinder(W, cx, cz, R + 2, gal, gal, "stone_bricks")
    S.cylinder(W, cx, cz, R + 2, gal + 1, gal + 1, S.fence("dark_oak"), hollow=True, thick=1)
    W.set(cx + R - 1, gal, cz, 0)  # ladder hole
    W.set(cx + R - 1, gal, cz, ladder("west"))
    # lantern room
    for y in range(gal + 1, gal + 5):
        S.cylinder(W, cx, cz, 2.6, y, y, "glass", hollow=True, thick=1)
    W.set(cx, gal + 1, cz, "polished_blackstone")
    W.set(cx, gal + 2, cz, "sea_lantern")
    W.set(cx, gal + 3, cz, "glowstone")
    S.cone_roof(W, cx, cz, 3.6, gal + 5, "dark_oak_planks", slope=1.0, tip=lantern(False))
    W.anchor("vista.lighthouse", cx + 0.5, gal + 1, cz + R + 1.5, note="Lighthouse gallery (vista)")
    W.anchor("emitter.lighthouse", cx + 0.5, gal + 3, cz + 0.5, note="rotating beam emitter")
    return y0, gal


def rope_bridge(W, a, b, width=3, sag=2.0, mat="spruce"):
    (x1, y1, z1), (x2, y2, z2) = a, b
    L = max(abs(x2 - x1), abs(z2 - z1))
    along_x = abs(x2 - x1) >= abs(z2 - z1)
    sag = min(sag, L * 0.08)
    prev_y = None
    for i in range(L + 1):
        t = i / L
        x = round(x1 + (x2 - x1) * t)
        z = round(z1 + (z2 - z1) * t)
        y = round(y1 + (y2 - y1) * t - sag * math.sin(math.pi * t))
        if prev_y is not None:
            y = max(min(y, prev_y + 1), prev_y - 1)
        prev_y = y
        for w in range(-(width // 2), width // 2 + 1):
            xx, zz = (x, z + w) if along_x else (x + w, z)
            W.set(xx, y, zz, f"minecraft:{mat}_slab[type=top,waterlogged=false]" if abs(w) < width // 2 else
                  log(f"stripped_{mat}_log", "x" if along_x else "z"))
            if abs(w) == width // 2:
                W.set(xx, y + 1, zz, S.fence(mat) if i % 4 else chain("y"))
                if i % 4 == 0:
                    W.set(xx, y + 2, zz, S.fence(mat))


def bell_frame(W, x, y, z, axis="x", mat="spruce"):
    """Two posts + beam, bell hanging under the beam. y = ground."""
    if axis == "x":
        for yy in range(y + 1, y + 5):
            W.set(x - 1, yy, z, log(f"{mat}_log"))
            W.set(x + 1, yy, z, log(f"{mat}_log"))
        W.set(x, y + 4, z, log(f"{mat}_log", "x"))
        W.set(x - 1, y + 5, z, slab(mat))
        W.set(x, y + 5, z, slab(mat))
        W.set(x + 1, y + 5, z, slab(mat))
        W.set(x, y + 3, z, "minecraft:bell[attachment=ceiling,facing=north,powered=false]")
    else:
        for yy in range(y + 1, y + 5):
            W.set(x, yy, z - 1, log(f"{mat}_log"))
            W.set(x, yy, z + 1, log(f"{mat}_log"))
        W.set(x, y + 4, z, log(f"{mat}_log", "z"))
        W.set(x, y + 5, z - 1, slab(mat))
        W.set(x, y + 5, z, slab(mat))
        W.set(x, y + 5, z + 1, slab(mat))
        W.set(x, y + 3, z, "minecraft:bell[attachment=ceiling,facing=east,powered=false]")
    return (x, y + 3, z)


def crane(W, x, y, z, facing="south", h=9):
    for yy in range(y + 1, y + h):
        W.set(x, yy, z, log("spruce_log"))
    dx, dz = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[facing]
    for k in range(1, 6):
        W.set(x + dx * k, y + h - 1, z + dz * k, log("stripped_spruce_log", "x" if dx else "z"))
    W.set(x - dx, y + h - 1, z - dz, log("stripped_spruce_log", "x" if dx else "z"))
    W.set(x - dx, y + h - 2, z - dz, "minecraft:cobblestone")  # counterweight
    for k in range(1, 4):
        W.set(x + dx * 5, y + h - 1 - k, z + dz * 5, chain("y"))
    W.set(x + dx * 5, y + h - 5, z + dz * 5, barrel("up"))
    # brace
    W.set(x + dx, y + h - 2, z + dz, stairs("spruce", {"south": "north", "north": "south", "east": "west", "west": "east"}[facing], "top"))


def guild_slips(W, T):
    x1, z1, x2, z2 = SLIPS
    # two stone slipways descending into the lagoon + an unfinished hull skeleton
    for sx in (122, 134):
        for z in range(-42, -15):
            y = QUAY_Y - max(0, (z + 30) // 3) if z > -30 else QUAY_Y
            for x in range(sx - 2, sx + 3):
                W.set(x, y, z, "stone_bricks" if x in (sx - 2, sx + 2) else "smooth_stone")
                for yy in range(y + 1, y + 8):
                    if W.getn(x, yy, z) != "minecraft:air" and "water" not in W.getn(x, yy, z):
                        W.set(x, yy, z, 0)
    # hull ribs on the west slip (future guild ship)
    sx = 122
    for i, z in enumerate(range(-40, -26, 2)):
        r = 3 if 1 < i < 6 else 2
        for a in range(0, 181, 12):
            ang = math.radians(a)
            xx = sx + round(math.cos(ang) * r)
            yy = QUAY_Y + 1 + round(math.sin(ang) * 3.5) - 0
            W.set(xx, yy + 1 - (4 if a == 90 else 0) * 0, z, log("stripped_oak_log", "z"))
        W.set(sx, QUAY_Y + 1, z, log("oak_log", "z"))
    for z in range(-41, -25):
        W.set(sx, QUAY_Y + 1, z, log("oak_log", "z"))
    # scaffolding + a sign that says: reserved
    for yy in range(QUAY_Y + 1, QUAY_Y + 7):
        W.set(sx - 4, yy, -34, "minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]")
        W.set(sx + 4, yy, -34, "minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]")
    S.signpost(W, sx + 6, QUAY_Y, -42, ["Slip No. 1", "RESERVED", "for the first", "guild to sail"], rot=0)
    W.anchor("hook.guild_slips", 128.5, QUAY_Y + 1, -30.5, note="Unfinished guild slips (future guild harbour hook)")


def build(W: World, T, rng):
    paint_quay(W, T)
    # -------------------------------------------------------------- main pier across the lagoon
    floor = WATER_Y - 6
    pier_segment(W, 68, 131, 6, 10, DECK_Y, piling_floor=floor)
    # pier head over the mouth / the Spill
    for x in range(132, 154):
        for z in range(-4, 21):
            W.set(x, DECK_Y, z, "spruce_planks" if (x * 3 + z) % 9 else "stripped_spruce_log[axis=z]")
            for yy in range(DECK_Y + 1, DECK_Y + 4):
                if W.getn(x, yy, z) != "minecraft:air":
                    W.set(x, yy, z, 0)
    for x in range(132, 154, 5):
        for z in (-4, 8, 20):
            for yy in range(WATER_Y - 4, DECK_Y):
                W.set(x, yy, z, log("spruce_log"))
    for x in range(132, 154):
        for z in (-4, 20):
            if x % 5:
                W.set(x, DECK_Y + 1, z, S.fence("spruce"))
    for z in range(-4, 21):
        if z not in range(5, 12):
            W.set(153, DECK_Y + 1, z, S.fence("spruce"))
    S.lamp_post(W, 132, DECK_Y, -4, h=3)
    S.lamp_post(W, 132, DECK_Y, 20, h=3)
    S.lamp_post(W, 153, DECK_Y, -4, h=4)
    S.lamp_post(W, 153, DECK_Y, 20, h=4)
    # Egon's kit shed (north side of the pier head)
    sh = S.house(W, 134, -3, 140, 1, DECK_Y, "harbour", floors=1, door_side="south", oh=1, interior=False,
                 storey=4)
    S.crates(W, 141, DECK_Y, -2, n=4, rng=rng)
    for x in range(135, 140):
        W.set(x, DECK_Y + 1, -2, barrel("up") if x % 2 else "minecraft:chest[facing=south,type=single,waterlogged=false]")
    S.wall_sign_at(W, 137, DECK_Y + 3, 2, "south", ["", "EGON'S", "Kits & Sense", ""])
    # Fish stall (south side)
    for x in range(134, 140):
        for z in (15, 19):
            pass
    for (x, z) in ((134, 15), (139, 15), (134, 19), (139, 19)):
        for yy in range(DECK_Y + 1, DECK_Y + 4):
            W.set(x, yy, z, log("spruce_log"))
    for x in range(133, 141):
        for z in range(14, 21):
            W.set(x, DECK_Y + 4, z, "minecraft:blue_wool" if (x + z) % 2 else "minecraft:white_wool")
    for x in range(135, 139):
        W.set(x, DECK_Y + 1, 15, barrel("up"))
    W.set(136, DECK_Y + 2, 15, "minecraft:cod" if False else "minecraft:spruce_slab[type=bottom,waterlogged=false]")
    S.wall_sign_at(W, 136, DECK_Y + 3, 14, "north", ["", "FRESH COD", "(mostly)", ""])
    # cargo crane at the pier head east edge
    crane(W, 150, DECK_Y, -3, facing="north", h=10)
    # spill: falling water curtain beyond the lip
    lip = max(x for x in range(140, 175) if T.island_all[x - X0, 8 - Z0]) if True else 158
    for z in range(-1, 18):
        xl = max([x for x in range(130, 175) if T.island_all[x - X0, z - Z0]] or [lip])
        if not T.water[xl - X0, z - Z0]:
            continue
        for yy in range(WATER_Y, 28, -1):
            if W.is_air(xl + 1, yy, z):
                W.set(xl + 1, yy, z, FALLING_WATER)
    T.spill_x = lip
    W.anchor("landmark.the_spill", 150.5, DECK_Y + 1, 8.5, note="Pier head over the Spill (lagoon waterfall)")
    # -------------------------------------------------------------- east pier over the void + the sky-sloop
    pier_segment(W, 154, 186, 6, 10, DECK_Y, piling_floor=None)
    for x in range(154, 187, 4):
        for k in range(1, 9):
            if x - k < 150:
                break
        W.set(x, DECK_Y - 1, 8, log("stripped_spruce_log", "x"))
    # cantilever braces down into the cliff
    for z in (6, 10):
        for k in range(0, 14):
            W.set(158 - k // 2 + k // 2, DECK_Y - 1 - k, z, 0) if False else None
        for k in range(1, 12):
            W.set(157 + k, DECK_Y - 1 - (12 - k) // 2, z, log("spruce_log", "x"))
    # mooring posts
    for x in (186,):
        for yy in range(DECK_Y + 1, DECK_Y + 5):
            W.set(x, yy, 11, log("spruce_log"))
        W.set(x, DECK_Y + 5, 11, lantern(False))
    shp = ship(W, 158, 16, DECK_Y, length=31, beam=9)
    # gangway
    for x in (167, 168, 169):
        W.set(x, DECK_Y, 11, "spruce_planks")
        W.set(x, DECK_Y + 1, 11, 0)
        W.set(x, DECK_Y, 12, "oak_planks")
        W.set(x, DECK_Y + 1, 12, 0)
    W.anchor("spawn.harbour", 166.5, DECK_Y + 1, 8.5, yaw=YAW["west"],
             note="Arrival: east pier beside the sky-sloop's gangway; also world spawn")
    W.anchor("ship.sky_sloop", 173.5, DECK_Y + 1, 16.5, note="Moored sky-sloop (decor; first-join fantasy)")
    # -------------------------------------------------------------- Tackle's fishing pier (north quay)
    pier_segment(W, 110, 114, -21, -6, DECK_Y, piling_floor=floor, lamps=False)
    S.lamp_post(W, 110, DECK_Y, -6, h=3)
    W.set(114, DECK_Y + 1, -7, barrel("up"))
    W.set(113, DECK_Y + 1, -21, barrel("up"))
    for x in (111, 112, 113):
        W.set(x, DECK_Y + 1, -6, 0)
    npc(W, "fisher", 112.5, DECK_Y + 1, -6.5, YAW["south"], note="Tackle — fishes off the end of the north pier")
    # -------------------------------------------------------------- town
    for key, x1, z1, x2, z2, style, fl, dr, ex in TOWN:
        y = T.town_pads[key]
        info = S.house(W, x1, z1, x2, z2, y, style, floors=fl, door_side=dr, rng=random.Random(x1 * 7 + z1),
                       chimney=ex.get("chimney", False))
        T.__dict__.setdefault("doors", {})[key] = info["door_out"]
    # tavern
    ty = T.town_pads["tavern"]
    S.wall_sign_at(W, 67, ty + 4, 19, "east", ["", "THE SALT", "BARREL", ""])
    for z in range(15, 24):
        W.set(68, ty + 4, z, stairs("spruce", "west", "top")) if False else None
    for z in (16, 22):
        S.lamp_post(W, 68, ty, z, h=2)
    for z in range(17, 22):
        W.set(68, ty + 1, z, barrel("east") if z % 2 else "minecraft:spruce_planks")
    npc(W, "bar_whisper", 69.5, ty + 1, 19.5, YAW["east"], note="Bar Whisper at the Salt Barrel's quay counter")
    # warehouse + merchant
    wy = T.town_pads["warehouse"]
    crane(W, 89, wy, -24, facing="south", h=9)
    S.crates(W, 76, wy, -23, n=5, rng=rng)
    npc(W, "merchant", 80.5, wy + 1, -23.5, YAW["south"], note="Merchant at the warehouse")
    S.wall_sign_at(W, 80, wy + 4, -25, "south", ["", "TRADE HOUSE", "buy · sell", ""])
    # workshop + craftsman
    ky = T.town_pads["workshop"]
    npc(W, "craftsman", 78.5, ky + 1, 36.5, YAW["north"], note="Craftsman at the workshop door")
    W.set(80, ky + 1, 36, "minecraft:crafting_table")
    W.set(76, ky + 1, 36, "minecraft:stonecutter[facing=north]")
    # forge (Quartermaster): open-front stone smithy
    fy = T.town_pads["forge"]
    x1, z1, x2, z2 = FORGE
    S.foundation(W, x1, z1, x2, z2, fy, "cobbled_deepslate", "cobblestone")
    W.fill(x1, fy, z1, x2, fy, z2, "stone_bricks")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            back = z == z2 or x in (x1, x2)
            for yy in range(fy + 1, fy + 6):
                if back and not (x in (x1, x2) and z == z1 and False):
                    W.set(x, yy, z, "cobblestone" if (x + yy) % 4 else "stone_bricks")
                elif not back:
                    W.set(x, yy, z, 0)
    for x in range(x1, x2 + 1, 4):
        for yy in range(fy + 1, fy + 6):
            W.set(x, yy, z1, log("dark_oak_log"))
    W.set(x2, fy + 1, z1, log("dark_oak_log"))
    for x in range(x1, x2 + 1):
        W.set(x, fy + 5, z1, log("stripped_dark_oak_log", "x"))
    S.gable_roof(W, x1, z1, x2, z2, fy + 6, "deepslate_brick", "deepslate_bricks", "cobblestone", axis="x", oh=1)
    # hearth
    for dx in range(3):
        W.set(x1 + 2 + dx, fy + 1, z2 - 1, "minecraft:blast_furnace[facing=north,lit=true]")
    W.set(x1 + 6, fy + 1, z2 - 1, "minecraft:magma_block")
    W.set(x1 + 7, fy + 1, z2 - 1, "minecraft:magma_block")
    for yy in range(fy + 2, fy + 12):
        W.set(x1 + 6, yy, z2, "bricks")
        W.set(x1 + 7, yy, z2, "bricks")
    W.set(x1 + 6, fy + 12, z2, campfire(True))
    W.set(x1 + 9, fy + 1, z1 + 4, "minecraft:anvil[facing=north]")
    W.set(x1 + 11, fy + 1, z1 + 4, "minecraft:grindstone[face=floor,facing=north]")
    W.set(x1 + 12, fy + 1, z2 - 1, "minecraft:smithing_table")
    W.set(x1 + 3, fy + 1, z1 + 3, "minecraft:cauldron")
    for z in range(z1 + 1, z2):
        W.set(x2 - 1, fy + 1, z, barrel("west") if z % 2 else "minecraft:coal_block")
    W.set((x1 + x2) // 2, fy + 4, (z1 + z2) // 2, lantern(True))
    S.wall_sign_at(W, x1 + 7, fy + 4, z1 - 1, "north", ["", "QUARTERMASTER", "coal in · steel out", ""])
    npc(W, "quartermaster", x1 + 7.5, fy + 1, z1 - 1.5, YAW["north"], note="Quartermaster at the forge front")
    W.anchor("emitter.qm_forge", x1 + 6.5, fy + 2, z2 - 0.5, note="forge smoke/clank emitter")
    # -------------------------------------------------------------- pier head NPCs
    npc(W, "egon", 141.5, DECK_Y + 1, 3.5, YAW["east"], note="Egon greets arrivals at the pier head (green glow)")
    npc(W, "fishmonger", 141.5, DECK_Y + 1, 13.5, YAW["east"], note="Fishmonger's stall beside Egon (they bicker)")
    npc(W, "dockhand", 100.5, DECK_Y + 1, 8.5, YAW["west"], note="flavour: dockhand on the long pier")
    # -------------------------------------------------------------- lighthouse + rope bridge
    lx, lz = 168, -42
    y0, gal = lighthouse(W, T, lx, lz)
    a_x = 150
    while not T.island[a_x - X0, -41 - Z0] and a_x > 140:
        a_x -= 1
    ya = gy(T, a_x, -41)
    rope_bridge(W, (a_x - 1, ya, -41), (lx - 5, y0, -41), width=3, sag=1.5)
    W.anchor("landmark.lighthouse", lx + 0.5, y0 + 1, lz + 0.5, note="Seawatch Light (rope bridge from the north horn)")
    # -------------------------------------------------------------- harbour bell (south horn)
    bx, bz = 146, 30
    while not T.island[bx - X0, bz - Z0]:
        bx -= 1
    bx -= 3
    by = gy(T, bx, bz)
    W.fill(bx - 2, by, bz - 2, bx + 2, by, bz + 2, "stone_bricks")
    T.bells = getattr(T, "bells", {})
    T.bells["harbour_bell"] = bell_frame(W, bx, by, bz, axis="z")
    hb = T.bells["harbour_bell"]
    W.anchor("bell.harbour", hb[0] + 0.5, hb[1], hb[2] + 0.5, note="Harbour Bell on the south horn")
    # -------------------------------------------------------------- guild slips
    guild_slips(W, T)
    # -------------------------------------------------------------- boats in the lagoon
    rowboat(W, 92, -8, WATER_Y, "x")
    rowboat(W, 120, 22, WATER_Y, "x")
    rowboat(W, 86, 18, WATER_Y, "z")
    # moored fishing sloop at the south quay (small)
    # -------------------------------------------------------------- quay props
    for (x, z) in ((74, -14), (76, 24), (102, -22), (104, 32), (128, -18), (128, 30), (90, -21)):
        y = gy(T, x, z)
        S.crates(W, x, y, z, n=3, rng=rng)
    # fish racks
    for x in range(118, 124):
        W.set(x, QUAY_Y + 1, 33, S.fence("spruce") if x in (118, 123) else 0)
        W.set(x, QUAY_Y + 2, 33, S.fence("spruce") if x in (118, 123) else "minecraft:spruce_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]")
    from d_capital import glowcap
    gx_, gz_ = 70, -12
    glowcap(W, gx_, gy(T, gx_, gz_), gz_, h=5)
    W.anchor("waystone.harbour", gx_ + 2.5, gy(T, gx_ + 2, gz_) + 1, gz_ + 0.5, note="Harbour glowcap sprout (Origin-light)")
