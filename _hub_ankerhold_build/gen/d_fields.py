"""Clucksworth Fields (south-west): farmhouse, red barn, crop strips, windmill hill, Lark's menagerie,
the Field Chapel bell, Harrow + the Farm Isle portal on the SW headland."""
from __future__ import annotations

import math
import random

import numpy as np

import structures as S
from common import gy, npc, YAW
from terrain import GX, GZ
from world import (World, stairs, slab, log, fence, lantern, barrel, campfire, trapdoor, pane, chain, X0, Z0, WATER,
                   GRASS)

FARMHOUSE = (-92, 56, -81, 65)
BARN = (-116, 66, -101, 78)
CROPS = (-128, 88, -90, 120)
MILL = (-112, 56)
MENAGERIE = (-66, 90, -44, 110)
CHAPEL = (-46, 120, -38, 132)
PORTAL = (-136, 140)


def plan(P, T):
    P.rect(FARMHOUSE[0] - 1, FARMHOUSE[1] - 1, FARMHOUSE[2] + 1, FARMHOUSE[3] + 1, 75, blend=4)
    P.rect(BARN[0] - 1, BARN[1] - 1, BARN[2] + 1, BARN[3] + 1, 75, blend=4)
    P.rect(*CROPS, 74, blend=5)
    P.circle(*MILL, 6, int(round(T.h[MILL[0] - X0, MILL[1] - Z0])), blend=3)
    P.rect(*MENAGERIE, 74, blend=4)
    P.rect(CHAPEL[0] - 1, CHAPEL[1] - 1, CHAPEL[2] + 1, CHAPEL[3] + 1, 74, blend=4)
    P.circle(*PORTAL, 6, 74, blend=4)
    extras_plan(P, T)
    road = (("dirt_path", 5), ("coarse_dirt", 1), ("gravel", 1))
    P.path([(-80, 3), (-86, 18), (-94, 34), (-90, 50), (-86, 54)], width=3, mats=road, step="mud_brick", smooth=5)
    P.path([(-86, 66), (-92, 84), (-108, 86), (-124, 124), (-132, 136)], width=3, mats=road, step="mud_brick",
           smooth=5)
    P.path([(-82, 66), (-70, 84), (-60, 88)], width=3, mats=road, step="mud_brick", smooth=5)
    P.path([(-52, 110), (-46, 118)], width=3, mats=road, step="mud_brick", smooth=4)
    P.path([(-38, 124), (-24, 120), (-14, 120)], width=2, mats=road, step="mud_brick", smooth=4)
    P.path([(0, 60), (-6, 90), (-12, 114)], width=2, mats=road, step="mud_brick", smooth=5)
    P.path([(-94, 52), (-104, 56)], width=2, mats=road, step="mud_brick", smooth=3)
    P.path([(-100, 70), (-96, 66), (-90, 66)], width=3, mats=road, step="mud_brick", smooth=3)


def farmhouse(W, T, rng):
    x1, z1, x2, z2 = FARMHOUSE
    S.house(W, x1, z1, x2, z2, 75, "farm", floors=2, door_side="south", chimney=True, rng=random.Random(2))
    # porch
    for x in range(x1, x2 + 1):
        W.set(x, 75, z2 + 1, "spruce_planks")
        W.set(x, 75, z2 + 2, slab("spruce", "top"))
        if x in (x1, x2, (x1 + x2) // 2 - 2, (x1 + x2) // 2 + 3):
            for yy in range(76, 79):
                W.set(x, yy, z2 + 2, fence("spruce"))
        W.set(x, 79, z2 + 2, slab("spruce", "bottom"))
        W.set(x, 79, z2 + 1, slab("spruce", "bottom"))
    W.set(x1 + 1, 76, z2 + 1, "minecraft:composter[level=0]")
    W.set(x2 - 1, 76, z2 + 1, "minecraft:hay_block[axis=y]")
    npc(W, "farmer", (x1 + x2) / 2 + 0.5, 76, z2 + 3.5, YAW["south"], note="Farmer on the farmhouse porch")
    W.anchor("spawn.farm", (x1 + x2) / 2 + 0.5, 76, z2 + 7.5, yaw=YAW["south"], note="Farm camp (farmhouse yard)")
    W.anchor("discover.farm", (x1 + x2) / 2 + 0.5, 76, z2 + 10.5, radius=28)


def barn(W, T, rng):
    x1, z1, x2, z2 = BARN
    y = 75
    S.foundation(W, x1, z1, x2, z2, y, "cobblestone")
    W.fill(x1, y, z1, x2, y, z2, "spruce_planks")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if x in (x1, x2) or z in (z1, z2):
                for yy in range(y + 1, y + 7):
                    corner = x in (x1, x2) and z in (z1, z2)
                    W.set(x, yy, z, log("dark_oak_log") if corner or (x - x1) % 5 == 0 and z in (z1, z2) else
                          "red_terracotta")
            else:
                for yy in range(y + 1, y + 7):
                    W.set(x, yy, z, 0)
    for x in range(x1, x2 + 1):
        W.set(x, y + 7, z1, log("stripped_dark_oak_log", "x"))
        W.set(x, y + 7, z2, log("stripped_dark_oak_log", "x"))
    for z in range(z1, z2 + 1):
        W.set(x1, y + 7, z, log("stripped_dark_oak_log", "z"))
        W.set(x2, y + 7, z, log("stripped_dark_oak_log", "z"))
    S.gable_roof(W, x1, z1, x2, z2, y + 8, "dark_oak", "dark_oak_planks", "red_terracotta", axis="z", oh=1)
    # big doors (open) east
    for z in range((z1 + z2) // 2 - 1, (z1 + z2) // 2 + 2):
        for yy in range(y + 1, y + 5):
            W.set(x2, yy, z, 0)
    for z in range(z1 + 1, z2):
        W.set(x1 + 1, y + 1, z, "minecraft:hay_block[axis=y]")
        if z % 2:
            W.set(x1 + 1, y + 2, z, "minecraft:hay_block[axis=x]")
    W.set((x1 + x2) // 2, y + 5, (z1 + z2) // 2, lantern(True))
    W.anchor("landmark.red_barn", x2 + 2.5, y + 1, (z1 + z2) / 2 + 0.5, note="Clucksworth red barn")


def crops(W, T, rng):
    x1, z1, x2, z2 = CROPS
    y = 74
    kinds = ["wheat[age=7]", "carrots[age=7]", "potatoes[age=7]", "wheat[age=7]", "beetroots[age=3]"]
    for x in range(x1, x2 + 1):
        strip = (x - x1) // 9
        for z in range(z1, z2 + 1):
            if (x - x1) % 9 == 4:
                W.set(x, y, z, WATER)
                continue
            if z in (z1, z2) or (z - z1) % 17 == 0:
                W.set(x, y, z, "dirt_path")
                W.set(x, y + 1, z, 0)
                continue
            W.set(x, y, z, "farmland[moisture=7]")
            W.set(x, y + 1, z, "minecraft:" + kinds[strip % len(kinds)])
    # scarecrow
    sx, sz = (x1 + x2) // 2, (z1 + z2) // 2 + 2
    W.set(sx, y + 1, sz, fence("spruce"))
    W.set(sx, y + 2, sz, fence("spruce"))
    W.set(sx, y + 3, sz, "minecraft:carved_pumpkin[facing=north]")
    W.set(sx - 1, y + 2, sz, "minecraft:hay_block[axis=x]") if False else None
    W.anchor("region.farm_fields", (x1 + x2) / 2, y + 1, (z1 + z2) / 2, min=[x1, y, z1], max=[x2, y + 1, z2],
             note="Crop strips (Farmer's farm_hand) — harvest/regen region")


def windmill(W, T, rng):
    cx, cz = MILL
    y = gy(T, cx, cz)
    H = 16
    S.cylinder(W, cx, cz, 4.4, y - 3, y + 6, "cobblestone")
    S.cylinder(W, cx, cz, 3.4, y + 1, y + 6, 0)
    S.cylinder(W, cx, cz, 3.9, y + 7, y + H, "white_terracotta", hollow=True, thick=1.0)
    S.cylinder(W, cx, cz, 2.9, y + 7, y + H, 0)
    S.cylinder(W, cx, cz, 4.4, y + 7, y + 7, "spruce_planks", hollow=True, thick=1)
    S.cone_roof(W, cx, cz, 4.6, y + H + 1, "spruce_planks", slope=0.8)
    for k in (4, 10, 14):
        W.set(cx + 4, y + k, cz, pane()) if k < 7 else W.set(cx + 3, y + k, cz, pane())
    W.set(cx, y + 1, cz + 4, 0)
    W.set(cx, y + 2, cz + 4, 0)
    # sails on the south face
    hy = y + H - 2
    hz = cz + 5
    W.set(cx, hy, cz + 4, log("stripped_spruce_log", "z"))
    W.set(cx, hy, hz, log("spruce_log", "z"))
    for ang in (35, 125, 215, 305):
        a = math.radians(ang)
        for r in range(1, 12):
            x = cx + round(math.cos(a) * r)
            yy = hy + round(math.sin(a) * r)
            W.set(x, yy, hz, log("stripped_spruce_log", "z"))
            if r > 3:
                # sail cloth beside the arm
                px = cx + round(math.cos(a) * r - math.sin(a) * 1.4)
                py = hy + round(math.sin(a) * r + math.cos(a) * 1.4)
                W.set(px, py, hz, "white_wool")
                px = cx + round(math.cos(a) * r - math.sin(a) * 2.4)
                py = hy + round(math.sin(a) * r + math.cos(a) * 2.4)
                W.set(px, py, hz, "white_wool")
    W.anchor("landmark.windmill", cx + 0.5, y + 1, cz + 6.5, note="Clucksworth windmill (hill landmark)")
    from d_capital import glowcap
    glowcap(W, cx + 9, gy(T, cx + 9, cz - 4), cz - 4, h=6)
    W.anchor("waystone.fields", cx + 11.5, gy(T, cx + 11, cz - 4) + 1, cz - 4.5, note="Fields glowcap")


def menagerie(W, T, rng):
    x1, z1, x2, z2 = MENAGERIE
    y = 74
    # three paddocks
    for (a1, b1, a2, b2) in ((x1, z1, x1 + 9, z1 + 9), (x1 + 11, z1, x2, z1 + 9), (x1, z1 + 11, x1 + 9, z2)):
        for x in range(a1, a2 + 1):
            for z in range(b1, b2 + 1):
                if x in (a1, a2) or z in (b1, b2):
                    W.set(x, y + 1, z, fence("oak"))
        W.set((a1 + a2) // 2, y + 1, b1, "minecraft:oak_fence_gate[facing=north,in_wall=false,open=false,powered=false]")
        W.set(a1 + 2, y + 1, b1 + 2, "minecraft:hay_block[axis=y]")
        W.set(a2 - 2, y + 1, b2 - 2, "minecraft:composter[level=0]") if False else None
    # aviary dome (iron bars) in the 4th corner
    cx, cz = x1 + 16, z1 + 16
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            for dy in range(0, 6):
                d = math.sqrt(dx * dx + dz * dz + (dy * 1.1) ** 2)
                if 3.6 <= d <= 4.5:
                    W.set(cx + dx, y + 1 + dy, cz + dz, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]")
    W.set(cx, y + 1, cz, log("oak_log"))
    W.set(cx, y + 2, cz, log("oak_log"))
    W.set(cx, y + 3, cz, "minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]")
    W.set(cx, y + 1, cz - 4, 0)
    W.set(cx, y + 2, cz - 4, 0)
    S.signpost(W, x1 - 2, y, z1 - 2, ["", "LARK'S", "MENAGERIE", "pets welcome"], rot=8)
    npc(W, "lark", x1 + 10.5, y + 1, z1 - 2.5, YAW["north"], note="Lark at her menagerie gate (pocket_zoo)")
    W.anchor("landmark.menagerie", x1 + 10.5, y + 1, z1 + 10.5, note="Lark's Menagerie (pets hint)")


def chapel(W, T, rng):
    x1, z1, x2, z2 = CHAPEL
    y = 74
    S.house(W, x1, z1, x2, z2, y, "capital", floors=1, door_side="north", roof_axis="z", interior=False, storey=6)
    # little belfry over the door
    bx, bz = (x1 + x2) // 2, z1
    for yy in range(y + 7, y + 13):
        W.set(bx - 1, yy, bz, "stone_bricks")
        W.set(bx + 1, yy, bz, "stone_bricks")
    W.set(bx, y + 12, bz, "stone_bricks")
    W.set(bx, y + 13, bz, slab("deepslate_tile", "bottom"))
    W.set(bx - 1, y + 13, bz, stairs("deepslate_tile", "east"))
    W.set(bx + 1, y + 13, bz, stairs("deepslate_tile", "west"))
    W.set(bx, y + 11, bz, "minecraft:bell[attachment=ceiling,facing=north,powered=false]")
    for yy in range(y + 7, y + 11):
        W.set(bx, yy, bz, 0) if yy > y + 8 else None
    for z in range(z1 + 2, z2 - 1, 2):
        for x in (x1 + 2, x2 - 2):
            W.set(x, y + 1, z, stairs("spruce", "north"))
    W.set(bx, y + 1, z2 - 1, "minecraft:lectern[facing=north,has_book=false,powered=false]")
    W.anchor("bell.field_chapel", bx + 0.5, y + 11, bz + 0.5, note="Field Chapel bell (over the south bay)")


def portal(W, T, rng):
    cx, cz = PORTAL
    y = 74
    # stone ring + hay-and-oak arch facing SW (toward the Farm Isle)
    S.disc(W, cx, cz, 5.5, y, "mossy_cobblestone")
    S.disc(W, cx, cz, 4, y, "coarse_dirt")
    # arch along the NW-SE diagonal approximated on the z axis
    for dz in range(-3, 4):
        W.set(cx - 3, y + 1, cz + dz, 0)
    for yy in range(y + 1, y + 7):
        W.set(cx, yy, cz - 3, log("oak_log"))
        W.set(cx, yy, cz + 3, log("oak_log"))
    for dz in range(-3, 4):
        W.set(cx, y + 7, cz + dz, log("oak_log", "z"))
    for dz in range(-2, 3):
        W.set(cx, y + 6, cz + dz, "minecraft:hay_block[axis=z]" if abs(dz) == 2 else 0)
    for (dz, yy) in ((-4, y + 6), (4, y + 6), (-4, y + 7), (4, y + 7), (0, y + 8), (-2, y + 8), (2, y + 8)):
        W.set(cx, yy, cz + dz, "minecraft:flowering_azalea_leaves[distance=7,persistent=true,waterlogged=false]")
    W.set(cx, y + 5, cz, lantern(True))
    W.anchor("portal.farm_isle", cx + 0.5, y + 1, cz + 0.5, min=[cx, y + 1, cz - 2], max=[cx, y + 5, cz + 2],
             note="Farm Isle portal frame (re-point FarmPortal* region here; Farming 10 gate stays in Farming)")
    npc(W, "farm_isle_guide", cx + 3.5, y + 1, cz - 4.5, YAW["west"], note="Harrow beside the Farm Isle portal")
    W.anchor("landmark.farm_isle_gate", cx + 0.5, y + 1, cz + 0.5, note="Harrow's Gate — Farm Isle to the south-west")


ORCHARD = (-74, 44, -52, 74)
POND = (-148, 72)
PASTURE = (-86, 128, -62, 150)


def extras_plan(P, T):
    P.rect(*ORCHARD, 75, blend=4)
    P.rect(*PASTURE, 74, blend=4)


def orchard(W, T, rng):
    x1, z1, x2, z2 = ORCHARD
    for x in range(x1 + 2, x2 - 1, 5):
        for z in range(z1 + 2, z2 - 1, 5):
            y = gy(T, x, z)
            S.oak_tree(W, x, y, z, rng, h=3, rad=2.0, leaf="flowering_azalea" if (x + z) % 3 == 0 else "oak")
    for x in range(x1, x2 + 1):
        for z in (z1, z2):
            if x % 3:
                W.set(x, gy(T, x, z) + 1, z, "minecraft:azalea_leaves[distance=7,persistent=true,waterlogged=false]")
    W.anchor("landmark.orchard", (x1 + x2) / 2, 76, (z1 + z2) / 2, note="Orchard (blossoming azalea + oak)")


def pond(W, T, rng):
    cx, cz = POND
    if not T.island[cx - X0, cz - Z0]:
        return
    y = gy(T, cx, cz)
    for dx in range(-7, 8):
        for dz in range(-6, 7):
            r = math.hypot(dx / 1.2, dz)
            if r <= 5.2:
                W.set(cx + dx, y, cz + dz, WATER)
                W.set(cx + dx, y - 1, cz + dz, WATER if r < 3.5 else "clay")
                W.set(cx + dx, y - 2, cz + dz, "clay")
                for yy in range(y + 1, y + 3):
                    W.set(cx + dx, yy, cz + dz, 0)
                if rng.random() < 0.12 and r < 4.5:
                    W.set(cx + dx, y + 1, cz + dz, "minecraft:lily_pad")
            elif r <= 6.4 and rng.random() < 0.35:
                for k in range(1, rng.randint(2, 4)):
                    W.set(cx + dx, y + k, cz + dz, "minecraft:sugar_cane[age=0]")


def pasture(W, T, rng):
    x1, z1, x2, z2 = PASTURE
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if x in (x1, x2) or z in (z1, z2):
                W.set(x, gy(T, x, z) + 1, z, fence("spruce"))
    W.set((x1 + x2) // 2, gy(T, (x1 + x2) // 2, z1) + 1, z1,
          "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=false,powered=false]")
    for (x, z) in ((x1 + 4, z1 + 4), (x1 + 5, z1 + 4), (x1 + 4, z1 + 5), (x2 - 5, z2 - 4)):
        W.set(x, gy(T, x, z) + 1, z, "minecraft:hay_block[axis=y]")
    W.set(x2 - 3, gy(T, x2 - 3, z1 + 3) + 1, z1 + 3, "minecraft:water_cauldron[level=3]")


def haystacks(W, T, rng):
    for (x, z) in ((-98, 82), (-96, 84), (-120, 64), (-121, 65), (-95, 70)):
        y = gy(T, x, z)
        W.set(x, y + 1, z, "minecraft:hay_block[axis=y]")
        if rng.random() < 0.5:
            W.set(x, y + 2, z, "minecraft:hay_block[axis=x]")


SHRINE = (-12, 122)


def wayside(W, T, rng):
    """Wayside shrine over the south bay: a small roofed bell + bench, wildflower meadow around it."""
    cx, cz = SHRINE
    y = gy(T, cx, cz)
    W.fill(cx - 2, y, cz - 2, cx + 2, y, cz + 2, "mossy_cobblestone")
    from d_harbour import bell_frame
    bell_frame(W, cx, y, cz, axis="x", mat="spruce")
    W.anchor("bell.wayside", cx + 0.5, y + 3, cz + 0.5, note="Wayside Bell (south bay shrine)")
    S.bench(W, cx - 1, y, cz - 3, "north", 3)
    W.anchor("landmark.wayside_shrine", cx + 0.5, y + 1, cz - 5.5, note="Wayside Shrine over the south bay")
    flowers = ["poppy", "cornflower", "oxeye_daisy", "azure_bluet", "allium", "dandelion"]
    for _ in range(420):
        a = rng.random() * 2 * math.pi
        r = rng.random() ** 0.6 * 22
        x, z = round(cx + math.cos(a) * r), round(cz - 14 + math.sin(a) * r * 0.8)
        if not T.island[x - X0, z - Z0] or T.occ[x - X0, z - Z0]:
            continue
        yy = gy(T, x, z)
        if W.getn(x, yy, z).startswith("minecraft:grass_block") and W.is_air(x, yy + 1, z):
            if rng.random() < 0.15:
                W.set(x, yy + 1, z, f"minecraft:pink_petals[facing=north,flower_amount={rng.randint(1, 4)}]")
            else:
                W.set(x, yy + 1, z, "minecraft:" + rng.choice(flowers))


def build(W: World, T, rng):
    wayside(W, T, rng)
    orchard(W, T, rng)
    pond(W, T, rng)
    pasture(W, T, rng)
    haystacks(W, T, rng)
    farmhouse(W, T, rng)
    barn(W, T, rng)
    crops(W, T, rng)
    windmill(W, T, rng)
    menagerie(W, T, rng)
    chapel(W, T, rng)
    portal(W, T, rng)
