"""Aetherion prop toolkit: block registry, voxel builder, vanilla neighbour logic, Sponge v3 export.

Everything is authored in a sparse voxel dict. Blocks are validated against the vanilla 1.21.1
registry (blocks.json from the data generator) and written with their full property set, so the
.schem pastes byte-identical to what the author intended (FAWE pastes states as-is, without
neighbour updates, which is why connections / stair shapes are resolved here).
"""
from __future__ import annotations

import gzip
import json
import math
import os
import random
import time

import nbtlib
from nbtlib import tag as T

HERE = os.path.dirname(os.path.abspath(__file__))
DATA_VERSION_1_21_1 = 3955

# ---------------------------------------------------------------------------------------------
# registry
# ---------------------------------------------------------------------------------------------

_REG = None


def registry():
    global _REG
    if _REG is None:
        path = os.environ.get("AE_BLOCKS_JSON", os.path.join(HERE, "blocks_1_21_1.json"))
        with open(path, "r", encoding="utf-8") as fh:
            raw = json.load(fh)
        reg = {}
        for name, info in raw.items():
            props = info.get("properties", {})
            if "default" in info:  # slim format shipped with the pack
                default = info["default"]
            else:  # full data-generator report (reports/blocks.json)
                default = next((st.get("properties", {}) for st in info["states"] if st.get("default")), {})
            reg[name] = (props, default)
        _REG = reg
    return _REG


def parse_state(s: str):
    s = s.strip()
    if "[" in s:
        name, rest = s.split("[", 1)
        rest = rest.rstrip("]")
        props = {}
        if rest:
            for kv in rest.split(","):
                k, v = kv.split("=")
                props[k.strip()] = v.strip()
    else:
        name, props = s, {}
    if ":" not in name:
        name = "minecraft:" + name
    return name, props


def fmt_state(name: str, props: dict) -> str:
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]"


def normalize(s: str) -> str:
    """Validate against the registry and return the canonical full-property state string."""
    name, props = parse_state(s)
    reg = registry()
    if name not in reg:
        raise ValueError(f"unknown block: {name}")
    allowed, default = reg[name]
    out = dict(default)
    for k, v in props.items():
        if k not in allowed:
            raise ValueError(f"{name}: unknown property {k} (allowed {list(allowed)})")
        if v not in allowed[k]:
            raise ValueError(f"{name}: bad value {k}={v} (allowed {allowed[k]})")
        out[k] = v
    if name.endswith("_leaves"):
        out["persistent"] = "true"  # pasted foliage must never decay
    return fmt_state(name, out)


def bname(state: str) -> str:
    return state.split("[", 1)[0]


def props_of(state: str) -> dict:
    return parse_state(state)[1]


def with_props(state: str, **kw) -> str:
    name, props = parse_state(state)
    props.update({k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in kw.items()})
    return fmt_state(name, props)


# ---------------------------------------------------------------------------------------------
# directions / rotation
# ---------------------------------------------------------------------------------------------

DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
        "up": (0, 1, 0), "down": (0, -1, 0)}
HORIZ = ["north", "east", "south", "west"]  # clockwise seen from above
OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}


def cw(d, k=1):
    if d not in HORIZ:
        return d
    return HORIZ[(HORIZ.index(d) + k) % 4]


def ccw(d, k=1):
    return cw(d, -k)


_RAIL_CW = {
    "north_south": "east_west", "east_west": "north_south",
    "ascending_north": "ascending_east", "ascending_east": "ascending_south",
    "ascending_south": "ascending_west", "ascending_west": "ascending_north",
    "north_east": "south_east", "south_east": "south_west",
    "south_west": "north_west", "north_west": "north_east",
}


def rotate_state(state: str, k: int) -> str:
    """Rotate a block state clockwise (seen from above) by k*90 degrees."""
    k %= 4
    if k == 0:
        return state
    name, p = parse_state(state)
    q = dict(p)
    if "facing" in p:
        q["facing"] = cw(p["facing"], k)
    if "axis" in p and k % 2 == 1 and p["axis"] in ("x", "z"):
        q["axis"] = "z" if p["axis"] == "x" else "x"
    if "rotation" in p:
        q["rotation"] = str((int(p["rotation"]) + 4 * k) % 16)
    if all(d in p for d in HORIZ):
        for d in HORIZ:
            q[cw(d, k)] = p[d]
    if "shape" in p and p["shape"] in _RAIL_CW:
        s = p["shape"]
        for _ in range(k):
            s = _RAIL_CW[s]
        q["shape"] = s
    return fmt_state(name, q)


def rotate_pos(x, z, k, w, l):
    """Rotate (x,z) inside a w×l footprint clockwise by k*90; returns new (x,z)."""
    k %= 4
    for _ in range(k):
        x, z = (l - 1 - z), x
        w, l = l, w
    return x, z


# ---------------------------------------------------------------------------------------------
# shape classes for neighbour logic
# ---------------------------------------------------------------------------------------------

_NON_FULL_TOKENS = (
    "stairs", "slab", "fence", "wall", "pane", "iron_bars", "trapdoor", "door", "button",
    "pressure_plate", "sign", "torch", "lantern", "carpet", "chain", "rod", "pot", "campfire",
    "rail", "ladder", "vine", "lichen", "sapling", "flower", "grass", "fern", "bush", "roots",
    "sprouts", "fungus", "mushroom", "petals", "dripleaf", "coral", "kelp", "seagrass", "pickle",
    "lily", "candle", "banner", "head", "skull", "bed", "chest", "anvil", "grindstone", "lectern",
    "cauldron", "hopper", "brewing", "bell", "scaffolding", "cake", "snow", "path", "farmland",
    "leaves", "glass", "cobweb", "composter", "stonecutter", "enchanting", "end_portal_frame",
    "azalea", "propagule", "moss_carpet", "hanging", "amethyst_bud", "cluster", "dripstone",
    "tulip", "orchid", "allium", "bluet", "daisy", "poppy", "dandelion", "cornflower", "lily_of",
    "rose", "lilac", "peony", "sunflower", "torchflower", "pitcher", "wheat", "carrots", "potatoes",
    "beetroots", "stem", "berry", "cocoa", "bamboo", "sugar_cane", "cactus", "water", "lava",
    "air", "frogspawn", "egg", "conduit", "daylight", "comparator", "repeater", "redstone_wire",
    "tripwire", "lever", "piston", "decorated_pot", "heavy_core", "vault", "trial_spawner",
    "spawner", "sculk_vein", "sculk_sensor", "shrieker", "portal", "fire", "light", "structure_void",
    "barrier", "sniffer", "_fan", "glow_lichen", "big_dripleaf", "spore_blossom", "moss_carpet",
)
_FULL_EXCEPT = {"minecraft:mushroom_stem", "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block",
                "minecraft:bamboo_block", "minecraft:stripped_bamboo_block", "minecraft:bamboo_planks",
                "minecraft:bamboo_mosaic", "minecraft:dripstone_block", "minecraft:glass",
                "minecraft:tinted_glass", "minecraft:dirt_path", "minecraft:sniffer_egg",
                "minecraft:mud_bricks", "minecraft:packed_mud", "minecraft:moss_block",
                "minecraft:dead_brain_coral_block", "minecraft:dead_tube_coral_block",
                "minecraft:dead_horn_coral_block", "minecraft:dead_bubble_coral_block",
                "minecraft:dead_fire_coral_block", "minecraft:tube_coral_block", "minecraft:horn_coral_block",
                "minecraft:brain_coral_block", "minecraft:bubble_coral_block", "minecraft:fire_coral_block",
                "minecraft:red_sandstone", "minecraft:sandstone", "minecraft:grass_block",
                "minecraft:mushroom_block", "minecraft:hay_block", "minecraft:rooted_dirt"}
_CONNECT_EXCEPTIONS_TOKENS = ("leaves", "pumpkin", "melon", "shulker_box", "barrier")
_STAINED_GLASS_FULL = ("stained_glass",)


def is_full_cube(state: str) -> bool:
    n = bname(state)
    if n in _FULL_EXCEPT:
        return True
    short = n.split(":", 1)[1]
    if short.endswith("stained_glass"):
        return True
    if short.endswith("_slab"):
        return props_of(state).get("type") == "double"
    if short in ("dirt_path", "farmland"):
        return False
    for t in _NON_FULL_TOKENS:
        if t in short:
            return False
    return True


def is_fence(state):
    n = bname(state)
    return n.endswith("_fence")


def is_gate(state):
    return bname(state).endswith("_fence_gate")


def is_wall(state):
    p = props_of(state)
    return "up" in p and p.get("north") in ("none", "low", "tall")


def is_pane(state):
    n = bname(state)
    return n.endswith("_pane") or n == "minecraft:iron_bars"


def is_stairs(state):
    return bname(state).endswith("_stairs")


def sturdy_side(state, face: str) -> bool:
    """Is the given face (direction from this block outward) a full sturdy face?"""
    if state is None:
        return False
    n = bname(state)
    for t in _CONNECT_EXCEPTIONS_TOKENS:
        if t in n:
            return False
    if is_full_cube(state):
        return True
    p = props_of(state)
    if is_stairs(state):
        if face == p["facing"]:
            return True
        if face == "up" and p["half"] == "top":
            return True
        if face == "down" and p["half"] == "bottom":
            return True
        return False
    if n.endswith("_slab"):
        if face == "down" and p.get("type") == "bottom":
            return True
        if face == "up" and p.get("type") == "top":
            return True
    return False


def bottom_full(state):
    return state is not None and sturdy_side(state, "down")


_WALL_POST_OVERRIDE_TOKENS = ("torch", "sign", "banner", "pressure_plate", "tripwire", "lantern", "chain")


# ---------------------------------------------------------------------------------------------
# builder
# ---------------------------------------------------------------------------------------------

class Build:
    def __init__(self, name: str, seed: int = 1):
        self.name = name
        self.blocks: dict[tuple[int, int, int], str] = {}
        self.block_entities: dict[tuple[int, int, int], dict] = {}
        self.rng = random.Random(seed)
        self.anchor = (0, 1, 0)
        self.meta: dict = {}

    # --- basic ----------------------------------------------------------------------------
    def set(self, x, y, z, state, be=None):
        if state is None:
            return
        if state in ("air", "minecraft:air"):
            self.blocks.pop((x, y, z), None)
            self.block_entities.pop((x, y, z), None)
            return
        st = normalize(state)
        self.blocks[(x, y, z)] = st
        if be is not None:
            self.block_entities[(x, y, z)] = be
        else:
            self.block_entities.pop((x, y, z), None)

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    def clear(self, x, y, z):
        self.set(x, y, z, "air")

    def has(self, x, y, z):
        return (x, y, z) in self.blocks

    def fill(self, x1, y1, z1, x2, y2, z2, state):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, self._pick(state))

    def fill_if_empty(self, x1, y1, z1, x2, y2, z2, state):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    if not self.has(x, y, z):
                        self.set(x, y, z, self._pick(state))

    def walls(self, x1, y1, z1, x2, y2, z2, state):
        xa, xb = sorted((x1, x2))
        za, zb = sorted((z1, z2))
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for x in range(xa, xb + 1):
                self.set(x, y, za, self._pick(state))
                self.set(x, y, zb, self._pick(state))
            for z in range(za, zb + 1):
                self.set(xa, y, z, self._pick(state))
                self.set(xb, y, z, self._pick(state))

    def replace(self, frm, to, box=None):
        for pos, st in list(self.blocks.items()):
            if box and not _in_box(pos, box):
                continue
            if bname(st) == ("minecraft:" + frm if ":" not in frm else frm):
                self.set(*pos, self._pick(to))

    def _pick(self, state):
        """state may be a string, or a list of (state, weight) for textured mixes."""
        if isinstance(state, (list, tuple)):
            tot = sum(w for _, w in state)
            r = self.rng.random() * tot
            for s, w in state:
                r -= w
                if r <= 0:
                    return s
            return state[-1][0]
        if callable(state):
            return state(self.rng)
        return state

    # --- compound helpers -----------------------------------------------------------------
    def door(self, x, y, z, wood, facing, hinge="left", open_=False):
        base = f"{wood}_door[facing={facing},hinge={hinge},open={str(open_).lower()}"
        self.set(x, y, z, base + ",half=lower]")
        self.set(x, y + 1, z, base + ",half=upper]")

    def tall(self, x, y, z, plant):
        self.set(x, y, z, f"{plant}[half=lower]")
        self.set(x, y + 1, z, f"{plant}[half=upper]")

    def bed(self, x, y, z, color, facing):
        """(x,y,z) = foot; head is one step toward facing."""
        dx, _, dz = DIRS[facing]
        be = {"id": "minecraft:bed"}
        self.set(x, y, z, f"{color}_bed[facing={facing},part=foot]", be=dict(be))
        self.set(x + dx, y, z + dz, f"{color}_bed[facing={facing},part=head]", be=dict(be))

    def sign(self, x, y, z, state, lines=("", "", "", ""), color="black", glow=False):
        msgs = [json.dumps({"text": t}) if t else '""' for t in (list(lines) + ["", "", "", ""])[:4]]
        blank = ['""'] * 4
        be = {
            "id": "minecraft:hanging_sign" if "hanging_sign" in state else "minecraft:sign",
            "front_text": {"messages": msgs, "color": color, "has_glowing_text": glow},
            "back_text": {"messages": blank, "color": "black", "has_glowing_text": False},
            "is_waxed": True,
        }
        self.set(x, y, z, state, be=be)

    def container(self, x, y, z, state):
        n = bname(normalize(state)).split(":")[1]
        bid = {"chest": "chest", "trapped_chest": "trapped_chest", "barrel": "barrel"}.get(n, n)
        self.set(x, y, z, state, be={"id": "minecraft:" + bid, "Items": []})

    def pot(self, x, y, z, facing="north", sherds=None):
        sherds = sherds or ["minecraft:brick"] * 4
        self.set(x, y, z, f"decorated_pot[facing={facing}]",
                 be={"id": "minecraft:decorated_pot", "sherds": sherds})

    def campfire(self, x, y, z, lit=True, facing="north", soul=False):
        n = "soul_campfire" if soul else "campfire"
        self.set(x, y, z, f"{n}[lit={str(lit).lower()},facing={facing}]",
                 be={"id": "minecraft:campfire", "Items": []})

    def bell(self, x, y, z, facing, attachment):
        self.set(x, y, z, f"bell[facing={facing},attachment={attachment}]", be={"id": "minecraft:bell"})

    def banner(self, x, y, z, state):
        self.set(x, y, z, state, be={"id": "minecraft:banner", "Patterns": []})

    def lectern(self, x, y, z, facing):
        self.set(x, y, z, f"lectern[facing={facing}]", be={"id": "minecraft:lectern"})

    def line(self, p1, p2, state):
        (x1, y1, z1), (x2, y2, z2) = p1, p2
        n = max(abs(x2 - x1), abs(y2 - y1), abs(z2 - z1))
        for i in range(n + 1):
            t = i / n if n else 0
            self.set(round(x1 + (x2 - x1) * t), round(y1 + (y2 - y1) * t), round(z1 + (z2 - z1) * t),
                     self._pick(state))

    def vine_on(self, x, y, z, faces, kind="vine"):
        """faces: directions where the supporting block is."""
        props = ",".join(f"{d}={'true' if d in faces else 'false'}" for d in ("north", "east", "south", "west"))
        if kind == "vine":
            self.set(x, y, z, f"vine[{props},up={'true' if 'up' in faces else 'false'}]")
        else:
            self.set(x, y, z, f"{kind}[{props},up={'true' if 'up' in faces else 'false'},down={'true' if 'down' in faces else 'false'}]")

    def stamp(self, other: "Build", ox, oy, oz, rot=0):
        """Copy another build into this one (rot clockwise k*90 around its own min corner)."""
        if not other.blocks:
            return
        xs = [p[0] for p in other.blocks]
        zs = [p[2] for p in other.blocks]
        mx, mz = min(xs), min(zs)
        w, l = max(xs) - mx + 1, max(zs) - mz + 1
        for (x, y, z), st in other.blocks.items():
            rx, rz = rotate_pos(x - mx, z - mz, rot, w, l)
            be = other.block_entities.get((x, y, z))
            self.blocks[(ox + rx, oy + y, oz + rz)] = rotate_state(st, rot)
            if be:
                self.block_entities[(ox + rx, oy + y, oz + rz)] = dict(be)

    # --- neighbour resolution -------------------------------------------------------------
    def resolve(self, stairs=True):
        """Bake vanilla connection states (fences, walls, panes/bars, stair corners)."""
        b = self.blocks
        new = {}
        for pos, st in b.items():
            x, y, z = pos
            if is_fence(st) or is_pane(st):
                q = {}
                for d in HORIZ:
                    dx, _, dz = DIRS[d]
                    nb = b.get((x + dx, y, z + dz))
                    q[d] = "true" if self._connects(st, nb, d) else "false"
                new[pos] = with_props(st, **q)
            elif is_wall(st):
                above = b.get((x, y + 1, z))
                q = {}
                for d in HORIZ:
                    dx, _, dz = DIRS[d]
                    nb = b.get((x + dx, y, z + dz))
                    if self._connects(st, nb, d):
                        q[d] = "tall" if (above is not None and bottom_full(above)) or \
                            (above is not None and is_wall(above) and props_of(above).get(d, "none") != "none") else "low"
                    else:
                        q[d] = "none"
                q["up"] = "true" if self._wall_post(q, above) else "false"
                new[pos] = with_props(st, **q)
        b.update(new)
        # walls above other walls influence "tall"; second pass for stacked walls
        new = {}
        for pos, st in b.items():
            if is_wall(st):
                x, y, z = pos
                above = b.get((x, y + 1, z))
                if above is not None and is_wall(above):
                    p = props_of(st)
                    pa = props_of(above)
                    q = {d: ("tall" if p[d] != "none" and pa[d] != "none" else p[d]) for d in HORIZ}
                    q["up"] = "true" if self._wall_post(q, above) else "false"
                    new[pos] = with_props(st, **q)
        b.update(new)
        if stairs:
            new = {}
            for pos, st in b.items():
                if is_stairs(st):
                    new[pos] = with_props(st, shape=self._stair_shape(pos, st))
            b.update(new)

    def _connects(self, st, nb, d):
        if nb is None:
            return False
        if is_fence(st):
            nether = bname(st) == "minecraft:nether_brick_fence"
            if is_fence(nb):
                return (bname(nb) == "minecraft:nether_brick_fence") == nether
            if is_gate(nb):
                return props_of(nb)["facing"] in (cw(d), ccw(d))
            return sturdy_side(nb, OPP[d])
        if is_pane(st):
            if is_pane(nb) or is_wall(nb):
                return True
            return sturdy_side(nb, OPP[d])
        if is_wall(st):
            if is_wall(nb) or is_pane(nb):
                return True
            if is_gate(nb):
                return props_of(nb)["facing"] in (cw(d), ccw(d))
            return sturdy_side(nb, OPP[d])
        return False

    @staticmethod
    def _wall_post(q, above):
        if above is not None and is_wall(above) and props_of(above).get("up") == "true":
            return True
        n, e, s, w = (q[d] for d in ("north", "east", "south", "west"))
        all_none = n == s == e == w == "none"
        if all_none or ((n == "none") != (s == "none")) or ((w == "none") != (e == "none")):
            return True
        if (n == "tall" and s == "tall") or (e == "tall" and w == "tall"):
            return False
        if above is None:
            return False
        short = bname(above)
        if any(t in short for t in _WALL_POST_OVERRIDE_TOKENS):
            return True
        return bottom_full(above)

    def _stair_shape(self, pos, st):
        x, y, z = pos
        p = props_of(st)
        f, half = p["facing"], p["half"]

        def at(d):
            dx, _, dz = DIRS[d]
            return self.blocks.get((x + dx, y, z + dz))

        def can_take(d):
            o = at(d)
            return not (o is not None and is_stairs(o) and props_of(o)["facing"] == f and props_of(o)["half"] == half)

        back = at(f)
        if back is not None and is_stairs(back) and props_of(back)["half"] == half:
            d1 = props_of(back)["facing"]
            if _axis(d1) != _axis(f) and can_take(OPP[d1]):
                return "outer_left" if d1 == ccw(f) else "outer_right"
        front = at(OPP[f])
        if front is not None and is_stairs(front) and props_of(front)["half"] == half:
            d2 = props_of(front)["facing"]
            if _axis(d2) != _axis(f) and can_take(d2):
                return "inner_left" if d2 == ccw(f) else "inner_right"
        return "straight"

    # --- info ------------------------------------------------------------------------------
    def bounds(self):
        xs = [p[0] for p in self.blocks]
        ys = [p[1] for p in self.blocks]
        zs = [p[2] for p in self.blocks]
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))

    def size(self):
        (a, b, c), (d, e, f) = self.bounds()
        return d - a + 1, e - b + 1, f - c + 1

    # --- export ----------------------------------------------------------------------------
    def save_schem(self, path):
        return write_schem(self, path)


def _axis(d):
    return "x" if d in ("east", "west") else "z"


def _in_box(pos, box):
    (x1, y1, z1), (x2, y2, z2) = box
    x, y, z = pos
    return min(x1, x2) <= x <= max(x1, x2) and min(y1, y2) <= y <= max(y1, y2) and min(z1, z2) <= z <= max(z1, z2)


# ---------------------------------------------------------------------------------------------
# Sponge schematic v3 writer (layout mirrors FAWE 2.12 output)
# ---------------------------------------------------------------------------------------------

def _varint(v):
    out = bytearray()
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            out.append(b | 0x80)
        else:
            out.append(b)
            return out


def _to_nbt(v):
    if isinstance(v, bool):
        return T.Byte(1 if v else 0)
    if isinstance(v, int):
        return T.Int(v)
    if isinstance(v, float):
        return T.Double(v)
    if isinstance(v, str):
        return T.String(v)
    if isinstance(v, dict):
        return T.Compound({k: _to_nbt(x) for k, x in v.items()})
    if isinstance(v, list):
        if not v:
            return T.List[T.Compound]([])
        items = [_to_nbt(x) for x in v]
        return T.List[type(items[0])](items)
    raise TypeError(type(v))


def write_schem(b: Build, path):
    (mnx, mny, mnz), (mxx, mxy, mxz) = b.bounds()
    W, H, L = mxx - mnx + 1, mxy - mny + 1, mxz - mnz + 1
    air = "minecraft:air"
    palette = {air: 0}
    data = bytearray()
    for y in range(H):
        for z in range(L):
            for x in range(W):
                st = b.blocks.get((x + mnx, y + mny, z + mnz), air)
                idx = palette.get(st)
                if idx is None:
                    idx = len(palette)
                    palette[st] = idx
                data += _varint(idx)
    ax, ay, az = b.anchor
    offset = (mnx - ax, mny - ay, mnz - az)
    bes = []
    for (x, y, z), be in sorted(b.block_entities.items()):
        if (x, y, z) not in b.blocks:
            continue
        rx, ry, rz = x - mnx, y - mny, z - mnz
        d = dict(be)
        d.update({"x": rx, "y": ry, "z": rz})
        bes.append(T.Compound({
            "Id": T.String(be["id"]),
            "Pos": T.IntArray([rx, ry, rz]),
            "Data": _to_nbt(d),
        }))
    schem = T.Compound({
        "Version": T.Int(3),
        "DataVersion": T.Int(DATA_VERSION_1_21_1),
        "Metadata": T.Compound({
            "Date": T.Long(int(time.time() * 1000)),
            "Name": T.String(b.name),
            "Author": T.String("Aetherion props"),
            "WorldEdit": T.Compound({
                "Version": T.String("7.3.9"),
                "EditingPlatform": T.String("enginehub:bukkit"),
                "Origin": T.IntArray([0, 0, 0]),
            }),
        }),
        "Width": T.Short(W),
        "Height": T.Short(H),
        "Length": T.Short(L),
        "Offset": T.IntArray(list(offset)),
        "Blocks": T.Compound({
            "Palette": T.Compound({k: T.Int(v) for k, v in palette.items()}),
            "Data": T.ByteArray([x - 256 if x > 127 else x for x in data]),
            "BlockEntities": T.List[T.Compound](bes),
        }),
    })
    f = nbtlib.File({"Schematic": schem}, root_name="")
    f.save(path, gzipped=True)
    return (W, H, L), offset, len(palette)
