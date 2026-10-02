"""Ore Ridge (south): coal cut, Shaft No. 1 (tunnel through the ridge), Surveyor's Ledge + Mining pad, Temper's smithy,
Ridge Lookout."""
from __future__ import annotations

import math
import random

import numpy as np

import structures as S
from common import gy, npc, YAW
from terrain import GX, GZ, smoothstep
from world import (World, stairs, slab, log, fence, lantern, chain, barrel, trapdoor, pane, campfire, X0, Z0, NX, NZ)

QUARRY = (80, 84, 99, 98)
MINE_X = (38, 42)
MINE_FLOOR = 72
MINE_Z = (93, 168)
LEDGE = (30, 158, 50, 171)
PAD = (40, 174, 44, 176)
TEMPER = (31, 53, 41, 61)


def plan(P, T):
    # mine apron
    P.rect(30, 82, 46, 93, MINE_FLOOR, blend=4)
    # Temper's smithy pad
    P.rect(TEMPER[0] - 1, TEMPER[1] - 1, TEMPER[2] + 1, TEMPER[3] + 1, 71, blend=3)
    # Surveyor's ledge
    P.rect(*LEDGE, MINE_FLOOR, blend=3)
    # quarry terraces cut into the NE flank
    x1, z1, x2, z2 = QUARRY
    m = (GX >= x1) & (GX <= x2) & (GZ >= z1) & (GZ <= z2)
    terr = 71 + 4 * np.floor((GZ - z1) / 4.0)
    T.h = np.where(m, np.minimum(T.h, terr), T.h)
    T.occ |= m
    T.quarry = m
    # roads
    road = (("dirt_path", 4), ("coarse_dirt", 1), ("gravel", 1))
    P.path([(106, 36), (106, 50), (98, 66), (92, 78), (90, 82)], width=3, mats=road, step="mud_brick",
           smooth=6, maxgrade=0.45)
    P.path([(84, 82), (66, 86), (50, 88), (42, 89)], width=3, mats=road, step="mud_brick", smooth=6, maxgrade=0.45)
    P.path([(37, 84), (34, 74), (31, 66), (28, 61)], width=3, mats=road, step="mud_brick", smooth=5, maxgrade=0.45)
    P.path([(62, 22), (64, 40), (70, 60), (86, 76)], width=3, mats=road, step="mud_brick", smooth=6, maxgrade=0.45)
    # lookout climb (cut into the west shoulder)
    P.path([(42, 92, MINE_FLOOR), (54, 97), (64, 103), (70, 110), (64, 116), (59, 116, None)], width=2,
           mats=(("coarse_dirt", 2), ("cobblestone", 2), ("gravel", 1)), step="stairs:cobblestone", smooth=3,
           maxgrade=0.85)
    P.circle(58, 117, 3.5, int(round(T.h[58 - X0, 117 - Z0])), blend=2)


def mine(W: World, T, rng):
    xa, xb = MINE_X
    za, zb = MINE_Z
    # tunnel through the ridge
    for z in range(za, zb + 1):
        for x in range(xa, xb + 1):
            W.set(x, MINE_FLOOR, z, "gravel" if (x + z) % 4 else "cobblestone")
            for yy in range(MINE_FLOOR + 1, MINE_FLOOR + 5):
                W.set(x, yy, z, 0)
        # supports every 5
        if (z - za) % 5 == 0:
            for yy in range(MINE_FLOOR + 1, MINE_FLOOR + 5):
                W.set(xa, yy, z, log("spruce_log"))
                W.set(xb, yy, z, log("spruce_log"))
            for x in range(xa, xb + 1):
                W.set(x, MINE_FLOOR + 5, z, log("stripped_spruce_log", "x"))
            if (z - za) % 10 == 0:
                W.set((xa + xb) // 2, MINE_FLOOR + 4, z, lantern(True))
        # walls / ceiling: make sure they are rock (open-air stretches stay open)
        for yy in range(MINE_FLOOR + 1, MINE_FLOOR + 6):
            for x in (xa - 1, xb + 1):
                cur = W.getn(x, yy, z)
                if cur != "minecraft:air" and "leaves" not in cur and "grass" not in cur:
                    W.set(x, yy, z, "stone" if rng.random() < 0.7 else "andesite")
        # rails
        W.set((xa + xb) // 2, MINE_FLOOR + 1, z, "minecraft:rail[shape=north_south,waterlogged=false]")
    # first-shift seam: ores in the first 24 blocks of wall
    for z in range(za + 2, za + 26):
        for yy in range(MINE_FLOOR + 1, MINE_FLOOR + 5):
            for x in (xa - 1, xb + 1):
                if W.getn(x, yy, z) in ("minecraft:stone", "minecraft:andesite") and rng.random() < 0.42:
                    W.set(x, yy, z, rng.choice(["coal_ore", "coal_ore", "iron_ore", "copper_ore", "coal_ore"]))
    W.anchor("region.mine_first_shift", (xa + xb) / 2, MINE_FLOOR + 1, za + 14, min=[xa - 1, MINE_FLOOR + 1, za + 2],
             max=[xb + 1, MINE_FLOOR + 4, za + 25], note="Shaft No. 1 ore walls (Foreman's first shift) — break/regen region")
    # mine mouth portal at the north end
    z = za
    for yy in range(MINE_FLOOR + 1, MINE_FLOOR + 7):
        W.set(xa - 2, yy, z, log("dark_oak_log"))
        W.set(xb + 2, yy, z, log("dark_oak_log"))
    for x in range(xa - 3, xb + 4):
        W.set(x, MINE_FLOOR + 7, z, log("stripped_dark_oak_log", "x"))
        W.set(x, MINE_FLOOR + 8, z, slab("dark_oak", "bottom"))
    for x in range(xa - 1, xb + 2):
        W.set(x, MINE_FLOOR + 6, z, log("stripped_dark_oak_log", "x"))
    # fill the face around the portal with rock so it reads as a hillside entrance
    for x in range(xa - 5, xb + 6):
        for yy in range(MINE_FLOOR + 1, MINE_FLOOR + 10):
            if W.is_air(x, yy, z + 1) and not (xa <= x <= xb and yy <= MINE_FLOOR + 4):
                W.set(x, yy, z + 1, "stone" if (x + yy) % 3 else "cobblestone")
    S.wall_sign_at(W, (xa + xb) // 2, MINE_FLOOR + 6, z - 1, "north", ["", "SHAFT No. 1", "mind your head", ""])
    W.set(xa - 3, MINE_FLOOR + 5, z - 1, lantern(True)) if False else None
    S.lamp_post(W, xa - 3, MINE_FLOOR, z - 2, "spruce", 3)
    S.lamp_post(W, xb + 3, MINE_FLOOR, z - 2, "spruce", 3)
    # ore carts + heap on the apron
    for (x, zz) in ((31, 86), (32, 86), (43, 85)):
        W.set(x, MINE_FLOOR + 1, zz, "minecraft:rail[shape=east_west,waterlogged=false]")
    S.crates(W, 44, MINE_FLOOR, 88, n=3, rng=rng)
    for (x, zz) in ((30, 89), (31, 90), (30, 90)):
        W.set(x, MINE_FLOOR + 1, zz, "coal_block" if (x + zz) % 2 else "gravel")
    mx = (xa + xb) / 2 + 0.5
    npc(W, "foreman", mx - 3, MINE_FLOOR + 1, za - 3.5, YAW["north"], note="Shaft Foreman at the mine mouth")
    W.anchor("spawn.mines", mx, MINE_FLOOR + 1, za - 9.5, yaw=YAW["south"], note="Mines camp (mine apron)")
    W.anchor("landmark.shaft_no1", mx, MINE_FLOOR + 1, za + 1.5, note="Shaft No. 1 — runs right through the ridge")
    # headframe over the hill behind the portal
    hx, hz = int(mx), za + 8
    hy = gy(T, hx, hz)
    for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        for yy in range(gy(T, hx + dx, hz + dz) + 1, hy + 14):
            W.set(hx + dx, yy, hz + dz, log("dark_oak_log"))
    for yy in (hy + 6, hy + 13):
        for k in range(-3, 4):
            W.set(hx + k, yy, hz - 3, log("stripped_dark_oak_log", "x"))
            W.set(hx + k, yy, hz + 3, log("stripped_dark_oak_log", "x"))
            W.set(hx - 3, yy, hz + k, log("stripped_dark_oak_log", "z"))
            W.set(hx + 3, yy, hz + k, log("stripped_dark_oak_log", "z"))
    # wheel
    wy = hy + 17
    for a in range(0, 360, 15):
        x = hx + round(4 * math.cos(math.radians(a)))
        y = wy + round(4 * math.sin(math.radians(a)))
        W.set(x, y, hz, "stripped_spruce_log[axis=z]")
    for k in range(-3, 4):
        W.set(hx + k, wy, hz, log("spruce_log", "x"))
        W.set(hx, wy + k, hz, log("spruce_log"))
    for yy in range(hy + 14, wy - 4):
        W.set(hx, yy, hz, log("spruce_log"))
    for yy in range(hy + 1, wy - 3):
        W.set(hx + 1, yy, hz, chain("y"))


def surveyor_ledge(W, T, rng):
    x1, z1, x2, z2 = LEDGE
    y = MINE_FLOOR
    # pad deck projecting over the south edge
    px1, pz1, px2, pz2 = PAD
    for x in range(px1 - 2, px2 + 3):
        for z in range(z2 - 1, pz2 + 4):
            if W.is_air(x, y, z) or not T.island_all[x - X0, z - Z0]:
                W.set(x, y, z, "spruce_planks" if (x + z) % 5 else "stripped_spruce_log[axis=z]")
            edge = x in (px1 - 2, px2 + 2) or z == pz2 + 3
            if edge:
                W.set(x, y + 1, z, fence("spruce"))
    for x in range(px1, px2 + 1):
        for z in range(pz1, pz2 + 1):
            W.set(x, y, z, "slime_block")
    for x in (px1 - 2, px2 + 2):
        for k in range(1, 8):
            W.set(x, y - k, pz2 + 3 - k, log("spruce_log", "z"))
    # an arch over the pad (sealed until the blueprint is stamped)
    for x in (px1 - 1, px2 + 1):
        for yy in range(y + 1, y + 6):
            W.set(x, yy, pz1 - 1, "stone_bricks" if yy < y + 5 else "chiseled_stone_bricks")
    for x in range(px1 - 1, px2 + 2):
        W.set(x, y + 6, pz1 - 1, "stone_bricks")
    W.set((px1 + px2) // 2, y + 5, pz1 - 1, lantern(True))
    W.anchor("pad.origin_to_mining", (px1 + px2) / 2 + 0.5, y + 1, (pz1 + pz2) / 2 + 0.5,
             min=[px1, y, pz1], max=[px2, y, pz2], target_hint="Mining Eldervale landing (53.5 91 482.5) — due south",
             note="Mining slime pad (stamped-blueprint gate). Launch heading +Z.")
    W.anchor("pad.mining_to_origin.land", 40.5, y + 1, 165.5, note="mining_to_origin target (Surveyor's Ledge)")
    # Surveyor's hut
    info = S.house(W, 28, 157, 35, 164, y, "mine", floors=1, door_side="east", rng=random.Random(9), interior=False)
    W.set(34, y + 1, 159, "minecraft:cartography_table")
    W.set(34, y + 1, 162, "minecraft:lectern[facing=west,has_book=false,powered=false]")
    S.wall_sign_at(W, 36, y + 3, 158, "east", ["", "SURVEYOR", "blueprints stamped", "here"])
    npc(W, "surveyor", 36.5, y + 1, 162.5, YAW["east"], note="Surveyor — stamps the blueprint that unseals the Mining pad")
    # tripod theodolite prop
    W.set(46, y + 1, 162, fence("spruce"))
    W.set(46, y + 2, 162, "minecraft:spyglass" if False else "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    W.anchor("landmark.surveyors_ledge", 42.5, y + 1, 164.5, note="Surveyor's Ledge — Mining Eldervale on the southern horizon")


def quarry(W, T, rng):
    x1, z1, x2, z2 = QUARRY
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            y = gy(T, x, z)
            W.set(x, y, z, "gravel" if rng.random() < 0.5 else "stone")
            # riser face behind (south) exposes coal
            for yy in range(y + 1, y + 6):
                b = W.getn(x, yy, z + 1)
                if b in ("minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:dirt",
                         "minecraft:dripstone_block", "minecraft:grass_block[snowy=false]", "minecraft:coarse_dirt"):
                    W.set(x, yy, z + 1, "coal_ore" if rng.random() < 0.38 else "stone")
    # timber crane + carts
    W.set(x1 + 2, gy(T, x1 + 2, z1 + 1) + 1, z1 + 1, barrel("up"))
    S.crates(W, x2 - 2, gy(T, x2 - 2, z1 + 1), z1 + 1, n=3, rng=rng)
    W.anchor("region.ore_ridge_coal", (x1 + x2) / 2, 74, (z1 + z2) / 2, min=[x1, 70, z1], max=[x2, 95, z2 + 1],
             note="Coal cut (Quartermaster's forge_coal errand) — break/regen region")
    W.anchor("spawn.ore_ridge", (x1 + x2) / 2 + 0.5, gy(T, (x1 + x2) // 2, z1 - 3) + 1, z1 - 3.5, yaw=YAW["south"],
             note="Ore Ridge camp (coal cut)")
    W.anchor("discover.ore_ridge", (x1 + x2) / 2 + 0.5, 74, z1 + 4.5, radius=30)


def temper(W, T, rng):
    x1, z1, x2, z2 = TEMPER
    y = 71
    S.foundation(W, x1, z1, x2, z2, y, "cobbled_deepslate")
    W.fill(x1, y, z1, x2, y, z2, "stone_bricks")
    # open smithy: back wall east, roof on posts
    for z in range(z1, z2 + 1):
        for yy in range(y + 1, y + 5):
            W.set(x2, yy, z, "cobblestone" if (z + yy) % 3 else "stone_bricks")
    for (x, z) in ((x1, z1), (x1, z2), (x1 + 5, z1), (x1 + 5, z2)):
        for yy in range(y + 1, y + 5):
            W.set(x, yy, z, log("dark_oak_log"))
    S.gable_roof(W, x1, z1, x2, z2, y + 5, "deepslate_brick", "deepslate_bricks", "cobblestone", axis="x", oh=1)
    for x in range(x1, x2 + 1):
        for z in (z1, z2):
            W.set(x, y + 4, z, log("stripped_dark_oak_log", "x"))
    W.set(x2 - 1, y + 1, z1 + 2, "minecraft:blast_furnace[facing=west,lit=true]")
    W.set(x2 - 1, y + 1, z1 + 3, "minecraft:magma_block")
    W.set(x2 - 1, y + 1, z1 + 4, "minecraft:blast_furnace[facing=west,lit=true]")
    for yy in range(y + 2, y + 11):
        W.set(x2, yy, z1 + 3, "bricks")
    W.set(x2, y + 11, z1 + 3, campfire(True))
    W.set(x1 + 3, y + 1, z1 + 3, "minecraft:anvil[facing=north]")
    W.set(x1 + 3, y + 1, z1 + 6, "minecraft:smithing_table")
    W.set(x2 - 1, y + 1, z2 - 1, barrel("up"))
    # the booster socket bench (amethyst + copper)
    W.set(x1 + 6, y + 1, z1 + 5, "amethyst_block")
    W.set(x1 + 6, y + 2, z1 + 5, "minecraft:amethyst_cluster[facing=up,waterlogged=false]")
    W.set(x1 + 7, y + 1, z1 + 5, "minecraft:waxed_cut_copper")
    W.set((x1 + x2) // 2, y + 4, (z1 + z2) // 2, lantern(True))
    S.wall_sign_at(W, x1, y + 3, (z1 + z2) // 2, "west", ["", "TEMPER", "edges & boosters", ""])
    npc(W, "booster_tutor", x1 - 1.5, y + 1, (z1 + z2) / 2 + 0.5, YAW["west"],
        note="Temper's smithy at the South Gate stair foot ('on the road to Capital')")
    W.anchor("emitter.temper_forge", x2 - 0.5, y + 2, z1 + 3.5, note="forge emitter")


def lookout(W, T, rng):
    x, z = 58, 117
    y = gy(T, x, z)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            W.set(x + dx, y + 1, z + dz, "cobblestone" if (dx or dz) else "mossy_cobblestone")
    W.set(x, y + 2, z, "cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    W.set(x, y + 3, z, lantern(False))
    S.bench(W, x - 1, y, z + 3, "north", 3)
    W.anchor("vista.ridge_lookout", x + 0.5, y + 1, z + 2.5, note="Ridge Lookout (vista): harbour, capital, Mining Eldervale")


def build(W: World, T, rng):
    mine(W, T, rng)
    surveyor_ledge(W, T, rng)
    quarry(W, T, rng)
    temper(W, T, rng)
    lookout(W, T, rng)
