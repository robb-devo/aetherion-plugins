"""Whisperwood (north-east): the lumber camp, the chop grove, Twig's Forage pad on the eastern cliff."""
from __future__ import annotations

import math
import random

import numpy as np

import structures as S
from common import gy, npc, YAW
from terrain import GX, GZ
from world import World, stairs, slab, log, fence, lantern, barrel, campfire, X0, Z0

CAMP = (90, -62)
PAD_DECK = (132, -100, 147, -86)
PAD = (139, -95, 143, -91)


def plan(P, T):
    P.circle(*CAMP, 9, 73, blend=5)
    P.rect(*PAD_DECK, 73, blend=4)
    road = (("dirt_path", 4), ("coarse_dirt", 1), ("podzol", 1))
    P.path([(96, -22), (95, -36), (92, -50), (90, -55)], width=3, mats=road, step="mud_brick", smooth=5, maxgrade=0.45)
    P.path([(96, -66), (110, -76), (124, -86), (132, -93)], width=3, mats=road, step="mud_brick", smooth=5,
           maxgrade=0.45)
    P.path([(88, -74), (82, -86), (79, -96)], width=2, mats=(("dirt_path", 3), ("podzol", 1)), step="mud_brick",
           smooth=5, maxgrade=0.5)


def camp(W, T, rng):
    cx, cz = CAMP
    y = 73
    # hut
    S.house(W, 79, -72, 86, -66, y, "farm", floors=1, door_side="east", rng=random.Random(4), interior=True)
    # log piles
    for k, (lx, lz) in enumerate(((95, -68), (95, -56), (84, -56))):
        for dy in range(3):
            for dx in range(4 - dy):
                for dz in (0, 1):
                    W.set(lx + dx + dy // 2, y + 1 + dy, lz + dz, log("oak_log" if (k + dx) % 2 else "spruce_log", "x"))
    # chopping block + sawhorse
    W.set(cx + 2, y + 1, cz + 2, log("oak_log"))
    W.set(cx + 2, y + 2, cz + 2, "minecraft:oak_pressure_plate[powered=false]")
    for dz in range(3):
        W.set(cx - 3, y + 1, cz + 3 + dz, fence("spruce") if dz != 1 else log("stripped_oak_log", "z"))
    W.set(cx - 3, y + 2, cz + 4, log("stripped_oak_log", "z"))
    # campfire ring + benches
    W.set(cx, y + 1, cz - 3, campfire(True))
    S.bench(W, cx - 1, y, cz - 6, "south", 3, "spruce")
    S.lamp_post(W, cx + 6, y, cz + 5, "spruce", 3)
    S.wall_sign_at(W, 87, y + 3, -69, "east", ["", "LUMBER CAMP", "ten logs = one", "honest day"])
    npc(W, "lumberjack", cx + 2.5, y + 1, cz + 4.5, YAW["south"], note="Lumberjack/Forager at the camp (chop demo)")
    W.anchor("spawn.whisperwood", cx + 0.5, y + 1, cz + 0.5, yaw=YAW["south"], note="Whisperwood camp (keeps the old id)")
    W.anchor("discover.whisperwood", cx + 0.5, y + 1, cz + 0.5, radius=30)
    # chop grove: plain oaks in a ring around the clearing
    trees = []
    for k in range(26):
        a = k / 26 * 2 * math.pi + rng.random() * 0.2
        r = 12 + rng.random() * 9
        tx, tz = round(cx + math.cos(a) * r), round(cz + math.sin(a) * r)
        if T.occ[tx - X0, tz - Z0] or not T.island[tx - X0, tz - Z0]:
            continue
        ty = gy(T, tx, tz)
        S.oak_tree(W, tx, ty, tz, rng, h=rng.randint(5, 7), rad=2.4)
        trees.append((tx, tz))
        T.occ[tx - X0 - 2:tx - X0 + 3, tz - Z0 - 2:tz - Z0 + 3] = True
    W.anchor("region.chop_grove", cx, y, cz, min=[cx - 23, y - 4, cz - 23], max=[cx + 23, y + 16, cz + 23],
             note=f"Chop grove: {len(trees)} plain oaks for the lumberjack demo — break/regen region")


def forage_pad(W, T, rng):
    x1, z1, x2, z2 = PAD_DECK
    y = 73
    # deck out over the cliff
    for x in range(x2 - 6, x2 + 6):
        for z in range(z1 + 3, z2 - 2):
            if not T.island_all[x - X0, z - Z0] or W.is_air(x, y, z):
                W.set(x, y, z, "spruce_planks" if (x * 3 + z) % 7 else log("stripped_spruce_log", "x"))
    for x in range(x2 - 6, x2 + 6):
        for z in (z1 + 3, z2 - 3):
            W.set(x, y + 1, z, fence("spruce"))
    for z in range(z1 + 3, z2 - 2):
        W.set(x2 + 5, y + 1, z, fence("spruce"))
    for k in range(1, 9):
        W.set(x2 + 5 - k, y - k, z1 + 3, log("spruce_log", "x"))
        W.set(x2 + 5 - k, y - k, z2 - 3, log("spruce_log", "x"))
    px1, pz1, px2, pz2 = PAD
    for x in range(px1, px2 + 1):
        for z in range(pz1, pz2 + 1):
            W.set(x, y, z, "slime_block")
    # leafy arch (Twig's style)
    for z in (pz1 - 1, pz2 + 1):
        for yy in range(y + 1, y + 5):
            W.set(px1 - 1, yy, z, log("oak_log"))
    for z in range(pz1 - 1, pz2 + 2):
        W.set(px1 - 1, y + 5, z, log("oak_log", "z"))
        W.set(px1 - 1, y + 6, z, "minecraft:flowering_azalea_leaves[distance=7,persistent=true,waterlogged=false]")
        W.set(px1 - 2, y + 5, z, "minecraft:azalea_leaves[distance=7,persistent=true,waterlogged=false]")
    W.set(px1 - 1, y + 4, (pz1 + pz2) // 2, lantern(True))
    S.signpost(W, px1 - 3, y, pz2 + 2, ["", "FORAGE ISLE", "--> east", "ask Twig"], rot=12)
    W.anchor("pad.origin_to_forage", (px1 + px2) / 2 + 0.5, y + 1, (pz1 + pz2) / 2 + 0.5,
             min=[px1, y, pz1], max=[px2, y, pz2], target_hint="Forage Isle landing (479.5 74 -240.5) — ENE",
             note="Forage slime pad (open). Launch heading ENE.")
    W.anchor("pad.forage_to_origin.land", px1 - 4.5, y + 1, (pz1 + pz2) / 2 + 0.5, note="forage_to_origin target")
    npc(W, "forage_pad_guide", px1 - 2.5, y + 1, pz1 - 2.5, YAW["east"], note="Twig beside the Forage pad")
    W.anchor("landmark.forage_skyway", (px1 + px2) / 2 + 0.5, y + 1, pz1 - 3.5, note="The Forage Skyway (pad deck)")


def knoll(W, T, rng):
    # Whisperwood glowcap on the knoll
    from d_capital import glowcap
    x, z = 78, -100
    y = gy(T, x, z)
    glowcap(W, x, y, z, h=7)
    W.anchor("waystone.whisperwood", x + 2.5, y + 1, z + 0.5, note="Whisperwood glowcap")


def build(W: World, T, rng):
    camp(W, T, rng)
    forage_pad(W, T, rng)
    knoll(W, T, rng)
