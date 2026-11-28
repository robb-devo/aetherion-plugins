"""The Old Quarry, cut in benches into the escarpment.

Real stone lives only here (the server lets players break STONE and ores anywhere, and grows them back), so
the quarry is where mining is learnt: fresh grey stone on every bench, a coal seam along the north face, a
little copper, and an iron vein that the first pickaxe cannot bite. A treadwheel crane after the medieval
Tretkran stands at the rim, the masons' lodge (Bauhuette) beside the way in.
"""
import math

import numpy as np

import layout as L
from build import Canvas, Mix, RoofMat, mix_set, stair, slab, lantern_post, disc
from noise import hash01, value

QX, QZ = L.QUARRY
G = L.QUARRY_Y
ROOF = RoofMat("spruce", "dark_oak", 0.25, ridge="dark_oak", seed=701)


def prepare(t):
    import sites
    sites.clear_disc(t, QX, QZ, 24)
    sites.level_box(t, -21, -9, -12, -4, G, 2)           # the lodge stands on the apron south-east of the floor


def build(w, t):
    c = Canvas(w, 0, 0, 0)
    _stone(w, t)
    _seams(c, t)
    _crane(c)
    _lodge(c)
    _clutter(c, t)
    c.mark("grett", -16, G + 1, -10, yaw=45)
    c.mark("quarry_floor", QX, G + 1, QZ)


def _stone(w, t):
    """Every surface of the cut becomes fresh stone, with rubble on the floor."""
    stone = "stone"
    zs, xs = np.nonzero(t.quarry)
    for zi, xi in zip(zs.tolist(), xs.tolist()):
        x, z = xi + L.X0, zi + L.Z0
        h = int(t.H[zi, xi])
        for y in range(h - 3, h + 1):
            w.set(x, y, z, stone)
        if h == G:
            r = float(hash01(x, 0, z, 703))
            patch = float(value(x, z, 3.0, 705))
            if patch > 0.74:
                w.set(x, h, z, "gravel")
            elif r < 0.07:
                w.set(x, h, z, "cobblestone")
    # the walls of the cut next to it: face them in stone too, so no andesite shows inside the bowl
    for zi, xi in zip(zs.tolist(), xs.tolist()):
        for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            a, b = zi + dz, xi + dx
            if 0 <= a < L.SZ and 0 <= b < L.SX and not t.quarry[a, b] and t.land[a, b]:
                hn, hq = int(t.H[a, b]), int(t.H[zi, xi])
                if hn > hq:
                    for y in range(hq + 1, min(hn, hq + 4) + 1):
                        if w.name(b + L.X0, y, a + L.Z0) not in ("air", "grass_block", "dirt"):
                            w.set(b + L.X0, y, a + L.Z0, stone)


def _face_cells(t, level):
    """Columns of the bench that stands directly over the given level (their side faces that level)."""
    out = []
    zs, xs = np.nonzero(t.quarry)
    for zi, xi in zip(zs.tolist(), xs.tolist()):
        if int(t.H[zi, xi]) != level + 3:
            continue
        for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            a, b = zi + dz, xi + dx
            if 0 <= a < L.SZ and 0 <= b < L.SX and t.quarry[a, b] and int(t.H[a, b]) == level:
                out.append((xi + L.X0, zi + L.Z0))
                break
    return out


def _seams(c, t):
    face = _face_cells(t, G)                  # the first bench: its wall rises from the floor
    north = sorted([p for p in face if p[1] < QZ - 3], key=lambda p: p[0])
    west = sorted([p for p in face if p[0] < QX - 5 and p[1] >= QZ - 3], key=lambda p: p[1])
    coal = []
    for i, (x, z) in enumerate(north):
        if 2 <= i < len(north) - 2:
            c.set(x, G + 1, z, "coal_ore")
            if i % 5 != 4:
                c.set(x, G + 2, z, "coal_ore")
            coal.append((x, z))
    for (x, z) in north[-2:]:
        c.set(x, G + 1, z, "copper_ore")
        c.set(x, G + 2, z, "copper_ore" if (x + z) % 2 else "stone")
    iron = west[1:4] if len(west) >= 4 else west
    for (x, z) in iron:
        c.set(x, G + 1, z, "iron_ore")
        c.set(x, G + 2, z, "iron_ore")
        c.set(x, G + 3, z, "iron_ore" if (x + z) % 2 == 0 else "stone")
    if coal:
        mx, mz = coal[len(coal) // 2]
        c.mark("coal_seam", mx, G + 1, mz + 1)
    if iron:
        mx, mz = iron[len(iron) // 2]
        c.mark("iron_vein", mx + 1, G + 1, mz)
    # a second small seam on the second bench for those who climb
    upper = sorted([p for p in _face_cells(t, G + 3) if p[1] < QZ - 6], key=lambda p: p[0])
    for (x, z) in upper[3:7]:
        c.set(x, G + 4, z, "coal_ore")


def _crane(c):
    """Treadwheel crane: a man-high wheel between two trestles, a boom over the floor, a block on the rope."""
    ax, az = -21, -13                        # the wheel's centre column, on the south-east of the floor
    y0 = G + 1
    cy = y0 + 2                              # axle height
    # trestles either side of the wheel
    for z in (az - 1, az + 1):
        for yy in range(y0, cy + 2):
            c.set(ax, yy, z, "stripped_spruce_log[axis=y]")
        c.set(ax - 1, y0, z, stair("spruce", "east"))
        c.set(ax + 1, y0, z, stair("spruce", "west"))
    c.set(ax, cy, az - 1, "stripped_spruce_log[axis=z]")
    c.set(ax, cy, az + 1, "stripped_spruce_log[axis=z]")
    c.set(ax, cy + 2, az, "stripped_spruce_log[axis=z]")
    # the wheel, in the x-y plane between the trestles
    ring = [(-2, -1), (-2, 0), (-2, 1), (2, -1), (2, 0), (2, 1), (-1, -2), (0, -2), (1, -2), (-1, 2), (0, 2), (1, 2)]
    for (dx, dy) in ring:
        c.set(ax + dx, cy + dy, az, "spruce_planks")
    for (dx, dy, st) in ((-1, -1, None), (1, -1, None), (-1, 1, None), (1, 1, None)):
        c.set(ax + dx, cy + dy, az, "spruce_fence")
    c.set(ax, cy, az, "stripped_spruce_log[axis=z]")
    for (dx, dy) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        c.set(ax + dx, cy + dy, az, "spruce_fence")
    # the boom: up and out over the floor to the north-west, a chain and a dressed block
    bx, by = ax, cy + 2
    for k in range(1, 6):
        c.set(ax - k, by + (k + 1) // 2, az, "stripped_spruce_log[axis=x]")
    tipx, tipy = ax - 5, by + 3
    for yy in range(tipy - 1, tipy - 5, -1):
        c.set(tipx, yy, az, "chain[axis=y]")
    c.set(tipx, tipy - 5, az, "smooth_stone")
    c.mark("crane_block", tipx, tipy - 5, az)


def _lodge(c):
    """The masons' lodge: an open shed with a workbench, a stonecutter and dressed blocks."""
    x1, x2, z1, z2 = -20, -13, -8, -5
    y = G + 1
    for (x, z) in ((x1, z1), (x2, z1), (x1, z2), (x2, z2), ((x1 + x2) // 2, z1)):
        for yy in range(y, y + 3):
            c.set(x, yy, z, "stripped_spruce_log[axis=y]")
    for x in range(x1 - 1, x2 + 2):
        c.set(x, y + 3, z1 - 1, stair(ROOF.pick(x, y + 3, z1 - 1), "south"))
        c.set(x, y + 3, z1, "spruce_planks")
        for i, z in enumerate(range(z1 + 1, z2 + 2)):
            c.set(x, y + 3 - (i + 1) // 2, z, slab(ROOF.pick(x, y, z), top=(i % 2 == 0)))
    for x in range(x1 + 1, x2):
        c.set(x, y, z1, "cobblestone_wall" if x % 3 else "stone_brick_wall")
    c.set(x1 + 1, y, z1 + 1, "stonecutter[facing=south]")
    c.set(x1 + 2, y, z1 + 1, "barrel[facing=up]")
    c.set(x2 - 1, y, z1 + 1, "stone_bricks")
    c.set(x2 - 1, y + 1, z1 + 1, "stone_brick_slab[type=bottom]")
    c.set(x2 - 2, y, z1 + 1, "chiseled_stone_bricks")
    c.set(x2 - 1, y, z1 + 2, "stone_brick_stairs[facing=west,half=bottom]")
    c.set((x1 + x2) // 2, y + 2, z1 + 1, "lantern[hanging=true]")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if c.name(x, G, z) in ("grass_block", "dirt"):
                c.set(x, G, z, "gravel" if (x * 3 + z) % 4 else "coarse_dirt")


def _clutter(c, t):
    y = G + 1
    # cut blocks waiting on the floor, a ladder up the first bench, lamps
    for (x, z, b) in ((QX + 3, QZ + 4, "stone_bricks"), (QX + 4, QZ + 4, "stone_bricks"), (QX + 3, QZ + 5, "stone"),
                      (QX - 2, QZ + 5, "cobblestone"), (QX + 5, QZ - 1, "smooth_stone_slab[type=bottom]")):
        if c.is_air(x, y, z) and t.h(x, z) == G:
            c.set(x, y, z, b)
    c.set(QX + 3, y + 1, QZ + 4, "stone_brick_slab[type=bottom]")
    face = _face_cells(t, G)
    east = sorted([p for p in face if p[0] > QX + 4], key=lambda p: p[1])
    if east:
        x, z = east[len(east) // 2]
        if t.h(x - 1, z) == G:
            for yy in range(G + 1, G + 4):
                c.set(x - 1, yy, z, "ladder[facing=west]")
    lantern_post(c, QX + 9, y, QZ + 6, 3, "spruce")
    lantern_post(c, QX - 1, y, QZ + 7, 3, "spruce")
