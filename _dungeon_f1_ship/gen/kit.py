"""Tiny structure-NBT kit for Aetherion Floor 1 (prison palette).

Coordinates: x = east, z = south, y = up. Rooms are square (S x S), floor at y=0.
Door sockets sit on the centre of every side; a 5-wide lane is kept clear so the
runtime builder can punch any side.
"""
import random
import nbtlib
from nbtlib import Compound, List, Int, String

DATA_VERSION = 3955  # 1.21.1

AIRISH = {"air", "cave_air", "light", "torch", "wall_torch", "cobweb", "lantern", "soul_lantern",
          "chain", "candle", "stone_button", "spruce_button", "moss_carpet", "iron_chain",
          "hanging_roots", "pointed_dripstone", "red_carpet", "gray_carpet", "brown_carpet",
          "skeleton_skull", "spruce_pressure_plate", "tripwire_hook", "ladder", "vine"}

CONNECTABLE = {"iron_bars", "stone_brick_wall", "mossy_stone_brick_wall", "cobblestone_wall",
               "spruce_fence", "dark_oak_fence"}


class Room:
    def __init__(self, name, size, height, seed):
        self.name = name
        self.S = size
        self.H = height
        self.mid = (size - 1) // 2
        self.r = random.Random(seed)
        self.b = {}
        self.pads = []
        self.loot = None
        self.title = name

    # ------------------------------------------------------------ basics
    def set(self, x, y, z, name, props=None):
        if 0 <= x < self.S and 0 <= z < self.S and 0 <= y < self.H:
            self.b[(x, y, z)] = (name, dict(props or {}))

    def get(self, x, y, z):
        return self.b.get((x, y, z), ("air", {}))[0]

    def is_air(self, x, y, z):
        return self.get(x, y, z) in AIRISH

    def brick(self, mossy=0.28, cracked=0.28):
        v = self.r.random()
        if v < mossy:
            return "mossy_stone_bricks"
        if v < mossy + cracked:
            return "cracked_stone_bricks"
        return "stone_bricks"

    def fill(self, x1, y1, z1, x2, y2, z2, name, props=None):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, props)

    def fill_brick(self, x1, y1, z1, x2, y2, z2, **kw):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, self.brick(**kw))

    def in_lane(self, x, z, depth=5, half=3):
        m, S = self.mid, self.S
        if abs(x - m) <= half and (z < depth or z > S - 1 - depth):
            return True
        if abs(z - m) <= half and (x < depth or x > S - 1 - depth):
            return True
        return False

    def near_center(self, x, z, r=3):
        return abs(x - self.mid) <= r and abs(z - self.mid) <= r

    # ------------------------------------------------------------ shell
    def shell(self, floor_mix=None, ceiling=True):
        S, H = self.S, self.H
        for x in range(S):
            for z in range(S):
                self.set(x, 0, z, floor_mix(x, z) if floor_mix else self.brick())
                for y in range(1, H - 1):
                    edge = x in (0, S - 1) or z in (0, S - 1)
                    self.set(x, y, z, self.brick() if edge else "air")
                if ceiling:
                    self.set(x, H - 1, z, self.brick(mossy=0.2, cracked=0.2))
        # door arches on every side (the builder opens the ones it links)
        m = self.mid
        for side in range(4):
            for d in range(-3, 4):
                for y in range(1, 6):
                    x, z = self._side_xyz(side, d)
                    if abs(d) == 3 or y == 5:
                        self.set(x, y, z, "chiseled_stone_bricks" if (y == 5 and d == 0) else "stone_bricks")

    def _side_xyz(self, side, d):
        m, S = self.mid, self.S
        if side == 0:
            return m + d, 0
        if side == 1:
            return S - 1, m + d
        if side == 2:
            return m + d, S - 1
        return 0, m + d

    # ------------------------------------------------------------ props
    def pillar(self, cx, cz, r=1, top=None, cap=True):
        top = top if top is not None else self.H - 2
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                for y in range(1, top + 1):
                    self.set(x, y, z, self.brick())
        if cap and r >= 1:
            for x in range(cx - r - 1, cx + r + 2):
                for z in range(cz - r - 1, cz + r + 2):
                    if max(abs(x - cx), abs(z - cz)) == r + 1 and self.is_air(x, top, z):
                        facing = self._face_away(x, z, cx, cz)
                        self.set(x, top, z, "stone_brick_stairs",
                                 {"facing": facing, "half": "top", "shape": "straight", "waterlogged": "false"})

    @staticmethod
    def _face_away(x, z, cx, cz):
        dx, dz = x - cx, z - cz
        if abs(dx) >= abs(dz):
            return "west" if dx > 0 else "east"
        return "north" if dz > 0 else "south"

    def beam_x(self, z, y, x1, x2):
        for x in range(x1, x2 + 1):
            if self.is_air(x, y, z):
                self.set(x, y, z, "stripped_spruce_log", {"axis": "x"})

    def beam_z(self, x, y, z1, z2):
        for z in range(z1, z2 + 1):
            if self.is_air(x, y, z):
                self.set(x, y, z, "stripped_spruce_log", {"axis": "z"})

    def hang(self, x, z, drop, lamp="lantern"):
        top = self.H - 2
        for y in range(top, max(1, top - drop), -1):
            if not self.is_air(x, y, z):
                return
            self.set(x, y, z, "chain", {"axis": "y", "waterlogged": "false"})
        y = max(2, top - drop)
        if self.is_air(x, y, z) and lamp:
            self.set(x, y, z, lamp, {"hanging": "true", "waterlogged": "false"})

    def wall_torch(self, x, y, z, facing):
        self.set(x, y, z, "wall_torch", {"facing": facing})

    def barrel(self, x, y, z, facing="up"):
        self.set(x, y, z, "barrel", {"facing": facing, "open": "false"})

    def cobweb(self, x, y, z):
        if self.is_air(x, y, z):
            self.set(x, y, z, "cobweb")

    def stair(self, x, y, z, facing, half="bottom", block="stone_brick_stairs"):
        self.set(x, y, z, block, {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"})

    def slab(self, x, y, z, kind="bottom", block="stone_brick_slab"):
        self.set(x, y, z, block, {"type": kind, "waterlogged": "false"})

    def rubble(self, x, z, h=None):
        h = h if h is not None else self.r.randint(1, 2)
        for y in range(1, h + 1):
            self.set(x, y, z, self.r.choice(["cobblestone", "mossy_cobblestone", "gravel", "cracked_stone_bricks", "andesite"]))
        if self.is_air(x, h + 1, z) and self.r.random() < 0.5:
            self.slab(x, h + 1, z, "bottom", self.r.choice(["stone_brick_slab", "cobblestone_slab", "mossy_stone_brick_slab"]))

    def cell_row(self, axis_side, start, count, width=5, depth=4, door_gap=True):
        """Prison cells against one wall. axis_side: 0=minZ wall,1=maxX,2=maxZ,3=minX."""
        S = self.S
        for c in range(count):
            a0 = start + c * (width + 1)
            for a in range(a0, a0 + width + 1):
                for d in range(1, depth + 1):
                    x, z = self._wall_local(axis_side, a, d)
                    if self.in_lane(x, z, depth=depth + 2):
                        continue
                    divider = (a == a0 or a == a0 + width)
                    front = d == depth
                    for y in range(1, 5):
                        if divider and not front:
                            self.set(x, y, z, self.brick())
                        elif front:
                            gap = door_gap and a == a0 + width // 2 and y <= 2 and self.r.random() < 0.45
                            if divider:
                                self.set(x, y, z, self.brick())
                            elif not gap:
                                self.set(x, y, z, "iron_bars")
                    if not divider and not front:
                        roll = self.r.random()
                        if d == 1 and roll < 0.25:
                            self.set(x, 1, z, "hay_block", {"axis": "y"})
                        elif roll < 0.12:
                            self.cobweb(x, 3, z)
                        elif roll < 0.18:
                            self.set(x, 1, z, "skeleton_skull", {"rotation": str(self.r.randint(0, 15)), "powered": "false"})
                    if not divider and front is False and d == 1 and a == a0 + 1:
                        self.set(x, 4, z, "chain", {"axis": "y", "waterlogged": "false"})
            # lintel over the row
            for a in range(a0, a0 + width + 1):
                x, z = self._wall_local(axis_side, a, depth)
                if not self.in_lane(x, z, depth=depth + 2):
                    self.set(x, 5, z, self.brick())

    def _wall_local(self, side, a, d):
        S = self.S
        if side == 0:
            return a, d
        if side == 1:
            return S - 1 - d, a
        if side == 2:
            return a, S - 1 - d
        return d, a

    # ------------------------------------------------------------ finish
    def connect(self):
        dirs = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
        for (x, y, z), (name, props) in list(self.b.items()):
            if name not in CONNECTABLE:
                continue
            new = dict(props)
            for key, (dx, dz) in dirs.items():
                other = self.get(x + dx, y, z + dz)
                solid = other not in AIRISH and other not in {"lantern", "chain"} and not other.endswith("_stairs") \
                    and not other.endswith("_slab") and other not in {"hay_block", "barrel", "skeleton_skull"}
                link = other in CONNECTABLE or solid
                if name.endswith("_wall"):
                    new[key] = "low" if link else "none"
                else:
                    new[key] = "true" if link else "false"
            new["waterlogged"] = "false"
            if name.endswith("_wall"):
                straight = (new["north"] != "none" and new["south"] != "none" and new["east"] == "none" and new["west"] == "none") or \
                           (new["east"] != "none" and new["west"] != "none" and new["north"] == "none" and new["south"] == "none")
                new["up"] = "false" if straight else "true"
            self.b[(x, y, z)] = (name, new)

    def walkable(self, x, z):
        return (not self.is_air(x, 0, z)) and self.get(x, 0, z) not in {"water", "lava"} \
            and self.is_air(x, 1, z) and self.is_air(x, 2, z) \
            and self.get(x, 1, z) not in {"cobweb", "skeleton_skull", "lantern", "chain"}

    def auto_pads(self, want=14, min_gap=4):
        S = self.S
        cells = [(x, z) for x in range(2, S - 2) for z in range(2, S - 2)
                 if self.walkable(x, z) and not self.in_lane(x, z, depth=4, half=2)
                 and all(self.walkable(x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1))]
        self.r.shuffle(cells)
        pads = []
        for c in cells:
            if all(abs(c[0] - p[0]) + abs(c[1] - p[1]) >= min_gap for p in pads):
                pads.append(c)
            if len(pads) >= want:
                break
        self.pads = pads
        return pads

    def auto_loot(self):
        """Loot cache spot: off the door cross, 5x5 clear, as close to the centre as possible."""
        S, m = self.S, self.mid
        best = None
        for x in range(3, S - 3):
            for z in range(3, S - 3):
                if abs(x - m) <= 2 or abs(z - m) <= 2:
                    continue
                if self.in_lane(x, z, depth=8, half=3):
                    continue
                if not all(self.walkable(x + dx, z + dz) for dx in (-2, -1, 0, 1, 2) for dz in (-2, -1, 0, 1, 2)):
                    continue
                score = abs(x - m) + abs(z - m)
                if best is None or score < best[0]:
                    best = (score, x, z)
        for off_axis in (2, 1, 0):
            if best is not None:
                break
            for x in range(2, S - 2):
                for z in range(2, S - 2):
                    if self.in_lane(x, z, depth=6, half=3) or (abs(x - m) <= 1 and abs(z - m) <= 1):
                        continue
                    if abs(x - m) < off_axis or abs(z - m) < off_axis:
                        continue
                    if not all(self.walkable(x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
                        continue
                    score = abs(x - m) + abs(z - m)
                    if best is None or score < best[0]:
                        best = (score, x, z)
        if best is None:
            best = (0, m, m + 3)
        self.loot = (best[1], best[2])
        return self.loot

    def to_nbt(self, path):
        palette, index, blocks = [], {}, []
        for (x, y, z), (name, props) in sorted(self.b.items()):
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": String("minecraft:" + name)}
                if props:
                    entry["Properties"] = Compound({k: String(v) for k, v in props.items()})
                palette.append(Compound(entry))
            blocks.append(Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[key])}))
        # explicit air for untouched interior so the paste clears the void box too
        root = Compound({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(self.S), Int(self.H), Int(self.S)]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](blocks),
            "entities": List[Compound]([]),
        })
        nbtlib.File(root, root_name="").save(path, gzipped=True)
        return len(blocks), len(palette)
