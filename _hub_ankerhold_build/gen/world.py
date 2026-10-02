"""Voxel world buffer + block-state helpers for the Ankerhold hub generator.

Coordinates are WORLD coordinates (x east, y up, z south). The buffer covers a fixed box.
"""
from __future__ import annotations

import numpy as np

X0, X1 = -192, 191
Z0, Z1 = -192, 191
Y0, Y1 = -48, 179
NX, NY, NZ = X1 - X0 + 1, Y1 - Y0 + 1, Z1 - Z0 + 1
WATER_Y = 63  # top water block of the lagoon

AIR = "minecraft:air"


class World:
    def __init__(self):
        self.a = np.zeros((NX, NY, NZ), dtype=np.uint16)
        self.pal: list[str] = [AIR]
        self.idx: dict[str, int] = {AIR: 0}
        self.anchors: dict = {}
        self.signs: dict = {}      # (x,y,z) -> list of 4 lines
        self.notes: list = []

    # ---------------------------------------------------------------- palette
    def bid(self, state: str) -> int:
        if not state.startswith("minecraft:"):
            state = "minecraft:" + state
        i = self.idx.get(state)
        if i is None:
            i = len(self.pal)
            self.pal.append(state)
            self.idx[state] = i
        return i

    def name(self, i: int) -> str:
        return self.pal[i]

    # ---------------------------------------------------------------- access
    @staticmethod
    def inb(x, y, z):
        return X0 <= x <= X1 and Y0 <= y <= Y1 and Z0 <= z <= Z1

    def set(self, x, y, z, state):
        x, y, z = int(x), int(y), int(z)
        if not self.inb(x, y, z):
            return
        self.a[x - X0, y - Y0, z - Z0] = state if isinstance(state, (int, np.integer)) else self.bid(state)

    def set_if_air(self, x, y, z, state):
        x, y, z = int(x), int(y), int(z)
        if not self.inb(x, y, z):
            return
        if self.a[x - X0, y - Y0, z - Z0] == 0:
            self.a[x - X0, y - Y0, z - Z0] = self.bid(state) if isinstance(state, str) else state

    def get(self, x, y, z) -> int:
        x, y, z = int(x), int(y), int(z)
        if not self.inb(x, y, z):
            return 0
        return int(self.a[x - X0, y - Y0, z - Z0])

    def getn(self, x, y, z) -> str:
        return self.pal[self.get(x, y, z)]

    def is_air(self, x, y, z):
        return self.get(x, y, z) == 0

    def fill(self, x1, y1, z1, x2, y2, z2, state):
        xa, xb = sorted((int(x1), int(x2)))
        ya, yb = sorted((int(y1), int(y2)))
        za, zb = sorted((int(z1), int(z2)))
        xa, xb = max(xa, X0), min(xb, X1)
        ya, yb = max(ya, Y0), min(yb, Y1)
        za, zb = max(za, Z0), min(zb, Z1)
        if xa > xb or ya > yb or za > zb:
            return
        v = state if isinstance(state, (int, np.integer)) else self.bid(state)
        self.a[xa - X0:xb - X0 + 1, ya - Y0:yb - Y0 + 1, za - Z0:zb - Z0 + 1] = v

    def column(self, x, z, y1, y2, state):
        self.fill(x, y1, z, x, y2, z, state)

    def top_solid(self, x, z, ymax=Y1, ignore=None) -> int:
        """Highest non-air y at (x,z) (<= ymax). Returns Y0-1 if none."""
        if not (X0 <= x <= X1 and Z0 <= z <= Z1):
            return Y0 - 1
        col = self.a[x - X0, : ymax - Y0 + 1, z - Z0]
        nz = np.nonzero(col)[0]
        if ignore:
            ign = {self.bid(s) for s in ignore}
            nz = [i for i in nz if col[i] not in ign]
            if not nz:
                return Y0 - 1
            return int(nz[-1]) + Y0
        if len(nz) == 0:
            return Y0 - 1
        return int(nz[-1]) + Y0

    def anchor(self, key, x, y, z, yaw=None, **extra):
        d = {"x": round(float(x), 1), "y": round(float(y), 1), "z": round(float(z), 1)}
        if yaw is not None:
            d["yaw"] = float(yaw)
        d.update(extra)
        self.anchors[key] = d

    def sign_text(self, x, y, z, lines):
        self.signs[(int(x), int(y), int(z))] = list(lines) + [""] * (4 - len(lines))


# ------------------------------------------------------------------ block-state helpers
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
DIRV = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def stairs(mat, facing, half="bottom", shape="straight"):
    return f"minecraft:{mat}_stairs[facing={facing},half={half},shape={shape},waterlogged=false]"


def slab(mat, typ="bottom"):
    return f"minecraft:{mat}_slab[type={typ},waterlogged=false]"


def log(mat, axis="y"):
    return f"minecraft:{mat}[axis={axis}]"


def leaves(mat):
    return f"minecraft:{mat}_leaves[distance=7,persistent=true,waterlogged=false]"


def lantern(hanging=False, soul=False):
    n = "soul_lantern" if soul else "lantern"
    return f"minecraft:{n}[hanging={'true' if hanging else 'false'},waterlogged=false]"


def trapdoor(mat, facing, half="bottom", open_=False):
    return (f"minecraft:{mat}_trapdoor[facing={facing},half={half},open={'true' if open_ else 'false'},"
            f"powered=false,waterlogged=false]")


def fence(mat):
    return f"minecraft:{mat}_fence[east=false,north=false,south=false,waterlogged=false,west=false]"


def wall(mat):
    return f"minecraft:{mat}_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"


def pane(mat="glass_pane"):
    return f"minecraft:{mat}[east=false,north=false,south=false,waterlogged=false,west=false]"


def bars():
    return "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]"


def chain(axis="y"):
    return f"minecraft:chain[axis={axis},waterlogged=false]"


def door(mat, facing, half, hinge="left"):
    return f"minecraft:{mat}_door[facing={facing},half={half},hinge={hinge},open=false,powered=false]"


def bell(facing="north", attachment="ceiling"):
    return f"minecraft:bell[attachment={attachment},facing={facing},powered=false]"


def barrel(facing="up"):
    return f"minecraft:barrel[facing={facing},open=false]"


def campfire(lit=True, facing="north"):
    return f"minecraft:campfire[facing={facing},lit={'true' if lit else 'false'},signal_fire=false,waterlogged=false]"


def double_plant(name):
    return (f"minecraft:{name}[half=lower]", f"minecraft:{name}[half=upper]")


def ladder(facing):
    return f"minecraft:ladder[facing={facing},waterlogged=false]"


def wall_sign(mat, facing):
    return f"minecraft:{mat}_wall_sign[facing={facing},waterlogged=false]"


def hanging_sign(mat, rotation=0, attached=False):
    return f"minecraft:{mat}_hanging_sign[attached={'true' if attached else 'false'},rotation={rotation},waterlogged=false]"


def water(level=0):
    return f"minecraft:water[level={level}]"


GRASS = "minecraft:grass_block[snowy=false]"
WATER = "minecraft:water[level=0]"
FALLING_WATER = "minecraft:water[level=8]"
