"""Mount Skyreach (north): Grand Stair, the Mountain Gate + Threshold hall (dungeon departure), the updraft vent,
Skyreach Terrace, the summit path, the summit (vista, Stellan, Summit Glide ring), the goat path."""
from __future__ import annotations

import math
import random

import numpy as np

import structures as S
from common import gy, npc, YAW
from terrain import GX, GZ, PLATEAU_Y
from world import (World, stairs, slab, log, fence, lantern, chain, barrel, campfire, X0, Z0, NX, NZ)

GATE_Y = 88
PLATFORM = (-30, -71, 10, -58)
HALL = (-16, -98, -4, -72)
VENT = (4, -67)
TERRACE = (-26, -88)
TERRACE_Y = 0  # set in plan from the spur top
SUMMIT = (-50, -118)


def plan(P, T):
    P.rect(*PLATFORM, GATE_Y, blend=3)
    P.path([(-10, -44, PLATEAU_Y), (-10, -47, PLATEAU_Y), (-10, -57, GATE_Y), (-10, -59, GATE_Y)], width=9,
           mats=(("stone_bricks", 4), ("polished_andesite", 1)), step="stairs:stone_brick", smooth=1, maxgrade=1.0)
    # terrace on the south spur (top flattened a little)
    global TERRACE_Y
    TERRACE_Y = int(round(np.nanmax(T.h[TERRACE[0] - 4 - X0:TERRACE[0] + 5 - X0, TERRACE[1] - 4 - Z0:TERRACE[1] + 5 - Z0]))) - 3
    T.terrace_y = TERRACE_Y
    P.circle(*TERRACE, 6, TERRACE_Y, blend=3)
    # summit path (switchbacks) and summit pad
    sy = int(round(np.nanmax(T.h[SUMMIT[0] - 3 - X0:SUMMIT[0] + 4 - X0, SUMMIT[1] - 3 - Z0:SUMMIT[1] + 4 - Z0]))) - 1
    T.summit_y = sy
    P.path([(-26, -94, TERRACE_Y), (-30, -104), (-40, -100), (-44, -108), (-47, -113, sy)], width=2.5,
           mats=(("andesite", 2), ("stone", 2), ("cobblestone", 1)), step="stairs:andesite", smooth=2, maxgrade=0.9)
    P.circle(*SUMMIT, 4, sy, blend=2)
    # goat path from Whisperwood's west edge up the east flank (explorers only)
    P.path([(42, -112), (26, -118), (10, -112), (-4, -100), (-14, -92), (-20, -89, TERRACE_Y)], width=1.6,
           mats=(("coarse_dirt", 2), ("gravel", 1), ("stone", 1)), step="stairs:cobblestone", smooth=2, maxgrade=0.9)


def gate(W, T, rng):
    # pave the gate platform
    px1, pz1, px2, pz2 = PLATFORM
    for x in range(px1, px2 + 1):
        for z in range(pz1, pz2 + 1):
            if gy(T, x, z) == GATE_Y:
                edge = x in (px1, px2) or z in (pz1, pz2)
                W.set(x, GATE_Y, z, "stone_bricks" if edge or (x + z) % 7 == 0 else
                      ("polished_andesite" if (x // 3 + z // 3) % 2 else "andesite"))
                if W.getn(x, GATE_Y + 1, z) in ("minecraft:short_grass",):
                    W.set(x, GATE_Y + 1, z, 0)
    for x in (px1 + 2, px2 - 2):
        S.stone_lamp(W, x, GATE_Y, pz2 - 1)
    x1, z1, x2, z2 = HALL
    y = GATE_Y
    # hall: vaulted tunnel into the mountain
    for z in range(z1, z2 + 1):
        for x in range(x1 - 1, x2 + 2):
            for yy in range(y, y + 13):
                inner = x1 <= x <= x2
                dx = abs(x - (x1 + x2) / 2)
                vault = y + 8 + int(math.sqrt(max(0, 36 - dx * dx)) * 0.6)
                if not inner:
                    if yy <= vault + 1:
                        W.set(x, yy, z, "stone_bricks" if (yy + z) % 5 else "cracked_stone_bricks")
                    continue
                if yy == y:
                    W.set(x, yy, z, "polished_andesite" if (x + z) % 2 else "stone_bricks")
                elif yy <= vault:
                    W.set(x, yy, z, 0)
                elif yy == vault + 1:
                    W.set(x, yy, z, "stone_bricks")
        if (z - z1) % 6 == 0:
            for yy in range(y + 1, y + 8):
                W.set(x1, yy, z, "polished_andesite")
                W.set(x2, yy, z, "polished_andesite")
            W.set(x1 + 1, y + 6, z, lantern(True)) if False else None
            W.set((x1 + x2) // 2, y + 10, z, chain("y"))
            W.set((x1 + x2) // 2, y + 9, z, lantern(True))
    # facade on the mountain face at z2+1
    fz = z2 + 1
    for x in range(x1 - 6, x2 + 7):
        for yy in range(y, y + 18):
            inner = x1 <= x <= x2 and yy <= y + 9
            if inner:
                W.set(x, yy, fz, 0)
                continue
            edge_col = x in (x1 - 1, x2 + 1, x1 - 6, x2 + 6)
            W.set(x, yy, fz, "polished_andesite" if edge_col else ("chiseled_stone_bricks" if yy == y + 10
                                                                    else "stone_bricks"))
    for x in range(x1, x2 + 1):
        W.set(x, y + 10, fz, "chiseled_stone_bricks")
        W.set(x, y + 9, fz, stairs("stone_brick", "south", "top") if x in (x1, x2) else 0)
    # anchor emblem over the arch
    cx = (x1 + x2) // 2
    for yy in range(y + 11, y + 16):
        W.set(cx, yy, fz - 0, "polished_blackstone")
    W.set(cx - 1, y + 15, fz, "polished_blackstone")
    W.set(cx + 1, y + 15, fz, "polished_blackstone")
    W.set(cx - 2, y + 12, fz, "polished_blackstone")
    W.set(cx + 2, y + 12, fz, "polished_blackstone")
    W.set(cx - 1, y + 11, fz, "polished_blackstone")
    W.set(cx + 1, y + 11, fz, "polished_blackstone")
    W.set(cx, y + 16, fz, "polished_blackstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    # braziers
    for x in (x1 - 3, x2 + 3):
        W.set(x, y + 1, fz - 1 + 2, "polished_andesite")
        W.set(x, y + 2, fz + 1, campfire(True))
    # the Threshold: dungeon departure frame at the far end
    pz = z1 + 2
    for x in range(cx - 4, cx + 5):
        for yy in range(y + 1, y + 10):
            edge = x in (cx - 4, cx + 4) or yy == y + 9
            if edge:
                W.set(x, yy, pz, "crying_obsidian" if (x + yy) % 3 == 0 else "polished_blackstone_bricks")
            else:
                W.set(x, yy, pz, 0)
    for x in range(cx - 3, cx + 4):
        W.set(x, y, pz, "polished_blackstone_bricks")
        W.set(x, y, pz + 1, "polished_blackstone_bricks")
    for x in (cx - 5, cx + 5):
        W.set(x, y + 1, pz + 1, "minecraft:soul_lantern[hanging=false,waterlogged=false]")
    W.fill(cx - 3, y + 1, pz - 1, cx + 3, y + 8, pz - 1, "minecraft:polished_blackstone_bricks")
    W.anchor("portal.dungeon_hub", cx + 0.5, y + 1, pz + 1.5, radius=3.5,
             note="Dungeon departure (AetherionDungeons hub-portal centre, r≈3.5) -> mmo-d")
    W.anchor("portal.dungeon_return", cx + 0.5, y + 1, z2 - 2.5, yaw=YAW["south"], note="return landing in the hall")
    npc(W, "dungeon_gate", cx + 3.5, y + 1, pz + 4.5, YAW["south"], note="Threshold, keeper of the dungeon frame")
    W.anchor("landmark.mountain_gate", cx + 0.5, y + 1, fz + 3.5, note="The Mountain Gate (Grand Stair top)")
    T.bells = getattr(T, "bells", {})


def vent(W, T, rng):
    vx, vz = VENT
    y = GATE_Y
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            W.set(vx + dx, y, vz + dz, "polished_andesite" if max(abs(dx), abs(dz)) == 2 else "minecraft:waxed_copper_grate")
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        W.set(vx + dx, y + 1, vz + dz, "stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
        W.set(vx + dx, y + 2, vz + dz, lantern(False))
    # clear the column above
    for yy in range(y + 1, TERRACE_Y + 12):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if W.getn(vx + dx, yy, vz + dz) != "minecraft:air" and yy > y + 2:
                    W.set(vx + dx, yy, vz + dz, 0)
    W.anchor("updraft.skyreach.floor", vx + 0.5, y + 1, vz + 0.5, note="Skyreach Updraft floor (copper grate vent)")
    W.anchor("updraft.skyreach.top", TERRACE[0] + 0.5, TERRACE_Y + 1, TERRACE[1] + 3.5,
             note="Skyreach Updraft top (terrace); glide corridor NW from (4.5, %d, -66.5)" % (TERRACE_Y + 8))


def terrace(W, T, rng):
    cx, cz = TERRACE
    y = TERRACE_Y
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            r = math.hypot(dx, dz)
            if r <= 6.2:
                W.set(cx + dx, y, cz + dz, "stone_bricks" if r > 4.5 else "polished_andesite")
                for yy in range(y + 1, y + 6):
                    W.set(cx + dx, yy, cz + dz, 0)
            if 5.6 < r <= 6.4 and dz > -3:
                W.set(cx + dx, y + 1, cz + dz, "stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    for dz in range(2, 7):
        W.set(cx, y + 1, cz + dz, 0)  # opening toward the glide-in side
    S.stone_lamp(W, cx - 4, y, cz - 3)
    S.stone_lamp(W, cx + 4, y, cz - 3)
    npc(W, "cobb_kettleby", cx + 2.5, y + 1, cz - 2.5, YAW["south"], role="origin:cobb",
        note="Origin skyway board (or move to the gate platform)")
    W.anchor("landmark.skyreach_terrace", cx + 0.5, y + 1, cz + 0.5, note="Skyreach Terrace (updraft landing)")


def summit(W, T, rng):
    cx, cz = SUMMIT
    y = T.summit_y
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            r = math.hypot(dx, dz)
            if r <= 4.3:
                W.set(cx + dx, y, cz + dz, "snow_block" if r > 2.5 else "stone_bricks")
                for yy in range(y + 1, y + 6):
                    W.set(cx + dx, yy, cz + dz, 0)
    # cairn + banner mast
    W.set(cx, y + 1, cz - 2, "cobblestone")
    W.set(cx, y + 2, cz - 2, "mossy_cobblestone")
    for yy in range(y + 3, y + 10):
        W.set(cx, yy, cz - 2, fence("spruce"))
    for yy in range(y + 6, y + 9):
        W.set(cx + 1, yy, cz - 2, "light_blue_wool")
        W.set(cx + 2, yy, cz - 2, "white_wool" if yy != y + 7 else "light_blue_wool")
    W.set(cx, y + 10, cz - 2, lantern(False))
    S.bench(W, cx - 1, y, cz + 1, "south", 3)
    npc(W, "stellan_voss", cx - 2.5, y + 1, cz - 1.5, YAW["south"], role="origin:stellan",
        note="Origin summit stargazer (vistas, wishes)")
    W.anchor("vista.skyreach_summit", cx + 0.5, y + 1, cz + 0.5, note="Skyreach Summit (vista)")
    W.anchor("spawn.summit", cx + 0.5, y + 1, cz + 2.5, yaw=YAW["south"], note="Summit camp (Origin)")
    # glide ring at the south lip
    gx, gz = cx + 1, cz + 4
    W.anchor("glide.summit.ring", gx + 0.5, y + 1, gz + 0.5, radius=1.4,
             note="Summit Glide rift -> Ledger's Court; path over the Grand Stair")
    for a in range(0, 360, 30):
        x = gx + round(1.6 * math.cos(math.radians(a)))
        z = gz + round(1.6 * math.sin(math.radians(a)))
        W.set(x, y, z, "light_blue_glazed_terracotta[facing=north]" if a % 60 == 0 else "calcite")


def build(W: World, T, rng):
    gate(W, T, rng)
    vent(W, T, rng)
    terrace(W, T, rng)
    summit(W, T, rng)
    W.anchor("discover.summit", SUMMIT[0] + 0.5, T.summit_y + 1, SUMMIT[1] + 0.5, radius=18)
