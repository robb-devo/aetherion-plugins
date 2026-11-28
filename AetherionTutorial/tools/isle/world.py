"""Voxel world container for the Morningbell generator.

Blocks live in one numpy array [y][z][x] of ids into a growing palette of block-state strings. Everything is
addressed in world coordinates. `finalize()` computes the connection states vanilla would compute on a block
update (fences, walls, panes, stair corners), because chunks written straight into region files
never get those updates. `save()` writes region files; `dump()` writes the raw array for the preview renderer.
"""
import json
import os

import numpy as np

import anvil
import nbt

DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
CCW = {"north": "west", "west": "south", "south": "east", "east": "north"}
CW = {v: k for k, v in CCW.items()}

BLOCK_ENTITY = {
    "chest": "chest", "trapped_chest": "trapped_chest", "barrel": "barrel", "campfire": "campfire",
    "soul_campfire": "campfire", "lectern": "lectern", "bell": "bell", "furnace": "furnace",
    "blast_furnace": "blast_furnace", "smoker": "smoker", "hopper": "hopper", "brewing_stand": "brewing_stand",
    "beehive": "beehive", "bee_nest": "beehive", "jukebox": "jukebox", "enchanting_table": "enchanting_table",
    "chiseled_bookshelf": "chiseled_bookshelf", "decorated_pot": "decorated_pot", "ender_chest": "ender_chest",
}

_NONFULL_EXACT = {
    "air", "cave_air", "void_air", "water", "lava", "light", "barrier", "structure_void", "iron_bars", "chain",
    "lantern", "soul_lantern", "torch", "wall_torch", "soul_torch", "soul_wall_torch", "ladder", "vine",
    "lily_pad", "snow", "bell", "grindstone", "anvil", "chipped_anvil", "damaged_anvil", "cauldron",
    "water_cauldron", "hopper", "lectern", "chest", "trapped_chest", "ender_chest", "brewing_stand",
    "enchanting_table", "dirt_path", "farmland", "soul_sand", "mud", "composter", "pointed_dripstone", "cobweb",
    "scaffolding", "bamboo", "sugar_cane", "cactus", "flower_pot", "end_rod", "lightning_rod", "decorated_pot",
    "glow_lichen", "pink_petals", "spore_blossom", "hanging_roots", "big_dripleaf", "big_dripleaf_stem",
    "small_dripleaf", "azalea", "flowering_azalea", "cocoa", "sweet_berry_bush", "wheat", "carrots", "potatoes",
    "beetroots", "melon_stem", "pumpkin_stem", "nether_wart", "fire", "tripwire", "tripwire_hook", "lever",
    "stonecutter", "campfire", "soul_campfire", "short_grass", "tall_grass", "fern", "large_fern", "dead_bush",
    "seagrass", "tall_seagrass", "kelp", "kelp_plant", "sea_pickle", "dandelion", "poppy", "blue_orchid",
    "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy",
    "cornflower", "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony", "torchflower",
    "brown_mushroom", "red_mushroom", "moss_carpet", "candle", "cake", "conduit", "turtle_egg", "sniffer_egg",
    "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud", "rail",
    "powered_rail", "redstone_wire", "repeater", "comparator", "daylight_detector", "skeleton_skull",
    "player_head", "dragon_head", "mangrove_propagule", "mangrove_roots", "frogspawn", "honey_block",
    "slime_block", "cave_vines", "cave_vines_plant", "weeping_vines", "twisting_vines", "chorus_plant",
    "heavy_core", "dragon_egg", "piston_head", "moving_piston",
}
_NONFULL_SUFFIX = (
    "_slab", "_stairs", "_fence", "_fence_gate", "_door", "_trapdoor", "_pane", "_sign", "_banner", "_carpet",
    "_button", "_pressure_plate", "_bed", "_sapling", "_candle", "_coral", "_coral_fan", "_wall_fan", "_head",
    "_skull", "_leaves", "_hanging_sign", "_torch", "_rail", "_egg",
)


def base_name(state):
    return state.split("[", 1)[0].replace("minecraft:", "")


def props_of(state):
    if "[" not in state:
        return {}
    return dict(p.split("=", 1) for p in state.split("[", 1)[1].rstrip("]").split(",") if p)


def with_props(state, **kw):
    name = state.split("[", 1)[0]
    p = props_of(state)
    p.update({k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in kw.items()})
    return canon(name, p)


def canon(name, props=None):
    if ":" not in name:
        name = "minecraft:" + name
    if not props:
        return name
    return name + "[" + ",".join("%s=%s" % (k, props[k]) for k in sorted(props)) + "]"


def norm(state):
    if "[" not in state:
        return state if ":" in state else "minecraft:" + state
    return canon(state.split("[", 1)[0], props_of(state))


def is_wall(name):
    return name.endswith("_wall")


def is_fence(name):
    return name.endswith("_fence")


def is_pane(name):
    return name.endswith("_pane") or name == "iron_bars"


def is_stairs(name):
    return name.endswith("_stairs")


def is_full(name):
    """Full cube with sturdy faces (what fences, walls and panes attach to)."""
    if name in _NONFULL_EXACT or name.startswith("potted_"):
        return False
    if is_wall(name):
        return False
    return not name.endswith(_NONFULL_SUFFIX)


WATERLOGGABLE_SUFFIX = ("_slab", "_stairs", "_fence", "_wall", "_trapdoor", "_pane", "_sign", "_fence_gate")
WATERLOGGABLE_EXACT = {"iron_bars", "chain", "lantern", "ladder", "campfire", "chest", "barrel_never", "sea_pickle",
                       "lightning_rod", "scaffolding", "pointed_dripstone", "big_dripleaf", "candle"}


class World:
    def __init__(self, x0, z0, sx, sz, y0, sy):
        assert x0 % 16 == 0 and z0 % 16 == 0 and sx % 16 == 0 and sz % 16 == 0 and y0 % 8 == 0
        self.x0, self.z0, self.y0 = x0, z0, y0
        self.sx, self.sz, self.sy = sx, sz, sy
        self.blocks = np.zeros((sy, sz, sx), dtype=np.uint16)
        self.palette = ["minecraft:air"]
        self.index = {"minecraft:air": 0}
        self.raw_index = {"air": 0, "minecraft:air": 0}
        self.bents = {}
        self.biome_names = ["minecraft:plains"]
        self.biome = np.zeros((sz // 4, sx // 4), dtype=np.uint8)
        self.marks = {}
        self._wet_ids = set()         # still water and waterlogged blocks: what a block set here stands in
        self._logged_of = {}
        self._wet_ids.add(self.id("minecraft:water"))

    # ------------------------------------------------------------------ palette
    def id(self, state):
        i = self.raw_index.get(state)
        if i is not None:
            return i
        n = norm(state)
        i = self.index.get(n)
        if i is None:
            i = len(self.palette)
            self.palette.append(n)
            self.index[n] = i
        self.raw_index[state] = i
        return i

    def ids(self, states):
        return np.array([self.id(s) for s in states], dtype=np.uint16)

    def name_ids(self, pred):
        """Palette ids whose base name satisfies pred (evaluate after building)."""
        return np.array([i for i, s in enumerate(self.palette) if pred(base_name(s))], dtype=np.uint16)

    # ------------------------------------------------------------------ access
    def inside(self, x, y, z):
        return (self.x0 <= x < self.x0 + self.sx and self.z0 <= z < self.z0 + self.sz
                and self.y0 <= y < self.y0 + self.sy)

    def set(self, x, y, z, state):
        """Place a block. A waterloggable block set into still water keeps that water (as in the game)."""
        if self.x0 <= x < self.x0 + self.sx and self.z0 <= z < self.z0 + self.sz \
                and self.y0 <= y < self.y0 + self.sy:
            i = self.id(state)
            if self.blocks[y - self.y0, z - self.z0, x - self.x0] in self._wet_ids:
                i = self._logged(i)
            self.blocks[y - self.y0, z - self.z0, x - self.x0] = i

    def _logged(self, i):
        """The waterlogged twin of palette entry i (or i itself if the block cannot hold water)."""
        j = self._logged_of.get(i)
        if j is None:
            st = self.palette[i]
            n = base_name(st)
            if (n.endswith(WATERLOGGABLE_SUFFIX) or n in WATERLOGGABLE_EXACT) and "waterlogged" not in props_of(st):
                j = self.id(with_props(st, waterlogged=True))
                self._wet_ids.add(j)
            else:
                j = i
            self._logged_of[i] = j
        return j

    def set_if_air(self, x, y, z, state):
        if self.inside(x, y, z) and self.blocks[y - self.y0, z - self.z0, x - self.x0] == 0:
            self.blocks[y - self.y0, z - self.z0, x - self.x0] = self.id(state)
            return True
        return False

    def get_id(self, x, y, z):
        if self.inside(x, y, z):
            return int(self.blocks[y - self.y0, z - self.z0, x - self.x0])
        return 0

    def get(self, x, y, z):
        return self.palette[self.get_id(x, y, z)]

    def name(self, x, y, z):
        return base_name(self.get(x, y, z))

    def is_air(self, x, y, z):
        return self.get_id(x, y, z) == 0

    def fill(self, x1, y1, z1, x2, y2, z2, state):
        x1, x2 = sorted((x1, x2))
        y1, y2 = sorted((y1, y2))
        z1, z2 = sorted((z1, z2))
        xa, xb = max(x1, self.x0), min(x2, self.x0 + self.sx - 1)
        ya, yb = max(y1, self.y0), min(y2, self.y0 + self.sy - 1)
        za, zb = max(z1, self.z0), min(z2, self.z0 + self.sz - 1)
        if xa > xb or ya > yb or za > zb:
            return
        self.blocks[ya - self.y0:yb - self.y0 + 1, za - self.z0:zb - self.z0 + 1,
                    xa - self.x0:xb - self.x0 + 1] = self.id(state)

    def top(self, x, z, skip=None):
        """Highest non-air y in the column (ignoring ids in `skip`), or y0 - 1."""
        if not (self.x0 <= x < self.x0 + self.sx and self.z0 <= z < self.z0 + self.sz):
            return self.y0 - 1
        col = self.blocks[:, z - self.z0, x - self.x0]
        nz = np.nonzero(col if skip is None else ~np.isin(col, skip) & (col != 0))[0]
        return self.y0 + int(nz[-1]) if nz.size else self.y0 - 1

    def block_entity(self, x, y, z, tags):
        """Extra NBT for the block entity at this spot (dict of name -> nbt Tag)."""
        self.bents[(x, y, z)] = tags

    def mark(self, key, x, y, z, **extra):
        """A named point for the plugin (written to isle.json)."""
        d = {"x": x, "y": y, "z": z}
        d.update(extra)
        self.marks[key] = d

    def set_biome(self, name, mask=None):
        if name not in self.biome_names:
            self.biome_names.append(name)
        i = self.biome_names.index(name)
        if mask is None:
            self.biome[:] = i
        else:
            self.biome[mask] = i
        return i

    # ------------------------------------------------------------------ neighbour logic
    def _coords(self, pred):
        ids = self.name_ids(pred)
        if ids.size == 0:
            return []
        ys, zs, xs = np.nonzero(np.isin(self.blocks, ids))
        return list(zip((xs + self.x0).tolist(), (ys + self.y0).tolist(), (zs + self.z0).tolist()))

    def _sturdy(self, name):
        return is_full(name)

    def _fence_connects(self, x, y, z, d, wooden):
        dx, dz = DIRS[d]
        st = self.get(x + dx, y, z + dz)
        n = base_name(st)
        if is_fence(n):
            return (n != "nether_brick_fence") == wooden
        if n.endswith("_fence_gate"):
            f = props_of(st).get("facing", "north")
            return (f in ("north", "south")) != (d in ("north", "south"))
        return self._sturdy(n)

    def _thin_connects(self, x, y, z, d):
        dx, dz = DIRS[d]
        st = self.get(x + dx, y, z + dz)
        n = base_name(st)
        if is_wall(n) or is_pane(n):
            return True
        if n.endswith("_fence_gate"):
            f = props_of(st).get("facing", "north")
            return (f in ("north", "south")) != (d in ("north", "south"))
        return self._sturdy(n)

    def _stair_shape(self, x, y, z, st):
        p = props_of(st)
        facing, half = p.get("facing", "north"), p.get("half", "bottom")

        def stair_at(d):
            dx, dz = DIRS[d]
            s = self.get(x + dx, y, z + dz)
            if is_stairs(base_name(s)):
                q = props_of(s)
                return q.get("facing", "north"), q.get("half", "bottom")
            return None

        def can_take(d):
            s = stair_at(d)
            return s is None or s[0] != facing or s[1] != half

        front = stair_at(facing)
        if front and front[1] == half:
            d = front[0]
            if (d in ("north", "south")) != (facing in ("north", "south")) and can_take(OPP[d]):
                return "outer_left" if d == CCW[facing] else "outer_right"
        back = stair_at(OPP[facing])
        if back and back[1] == half:
            d = back[0]
            if (d in ("north", "south")) != (facing in ("north", "south")) and can_take(d):
                return "inner_left" if d == CCW[facing] else "inner_right"
        return "straight"

    def finalize(self):
        """Connection states. Call once, after everything is placed. (Waterlogging happens in set().)"""
        # stairs (shape must be computed from the pre-pass states: collect, then write)
        updates = []
        for (x, y, z) in self._coords(is_stairs):
            st = self.get(x, y, z)
            shape = self._stair_shape(x, y, z, st)
            if props_of(st).get("shape", "straight") != shape:
                updates.append((x, y, z, with_props(st, shape=shape)))
        for x, y, z, st in updates:
            self.set(x, y, z, st)
        # fences
        for (x, y, z) in self._coords(is_fence):
            st = self.get(x, y, z)
            wooden = base_name(st) != "nether_brick_fence"
            kw = {d: self._fence_connects(x, y, z, d, wooden) for d in DIRS}
            self.set(x, y, z, with_props(st, **kw))
        # panes / bars
        for (x, y, z) in self._coords(is_pane):
            st = self.get(x, y, z)
            kw = {d: self._thin_connects(x, y, z, d) for d in DIRS}
            self.set(x, y, z, with_props(st, **kw))
        # walls: sides first, then posts (bottom-up so a post above is known... posts depend on the wall above)
        walls = sorted(self._coords(is_wall), key=lambda c: -c[1])
        for (x, y, z) in walls:
            st = self.get(x, y, z)
            above = self.get(x, y + 1, z)
            an = base_name(above)
            ap = props_of(above)
            sides = {}
            for d in DIRS:
                if not self._thin_connects(x, y, z, d):
                    sides[d] = "none"
                    continue
                tall = is_full(an) or (is_wall(an) and ap.get(d, "none") != "none") \
                    or (an.endswith("_slab") and ap.get("type") != "top") \
                    or (is_stairs(an) and ap.get("half") != "top")
                sides[d] = "tall" if tall else "low"
            n, s, e, w = (sides[k] != "none" for k in ("north", "south", "east", "west"))
            if is_wall(an) and ap.get("up", "true") == "true":
                up = True
            elif (not n and not s and not e and not w) or n != s or e != w:
                up = True
            elif (sides["north"] == "tall" and sides["south"] == "tall") or \
                    (sides["east"] == "tall" and sides["west"] == "tall"):
                up = False
            else:
                up = is_full(an) or an in ("lantern", "soul_lantern", "torch", "end_rod", "candle", "flower_pot") \
                    or an.startswith("potted_") or an.endswith(("_sign", "_banner", "_pressure_plate", "_fence")) \
                    or (an == "chain" and ap.get("axis", "y") == "y")
            self.set(x, y, z, with_props(st, up=up, **sides))
        # fence gates in walls
        for (x, y, z) in self._coords(lambda n: n.endswith("_fence_gate")):
            st = self.get(x, y, z)
            f = props_of(st).get("facing", "north")
            side = ("west", "east") if f in ("north", "south") else ("north", "south")
            in_wall = any(is_wall(self.name(x + DIRS[d][0], y, z + DIRS[d][1])) for d in side)
            self.set(x, y, z, with_props(st, in_wall=in_wall))

    # ------------------------------------------------------------------ output
    def _be_tag(self, x, y, z, be_id, extra):
        d = {"id": nbt.String("minecraft:" + be_id), "x": nbt.Int(x), "y": nbt.Int(y), "z": nbt.Int(z),
             "keepPacked": nbt.Byte(0)}
        if extra:
            d.update(extra)
        return nbt.Compound(d)

    def save(self, region_dir, ocean_radius_chunks=24, sea_level=62, floor_y=44,
             floor_state="minecraft:sand", fill_state="minecraft:tuff", ocean_biome="minecraft:lukewarm_ocean"):
        """Writes the built area plus a flat ocean around it out to `ocean_radius_chunks`."""
        writer = anvil.RegionWriter(region_dir)
        if ocean_biome not in self.biome_names:
            self.biome_names.append(ocean_biome)
        ob = self.biome_names.index(ocean_biome)
        # block entities by chunk
        be_ids = {}
        for i, s in enumerate(self.palette):
            n = base_name(s)
            be = BLOCK_ENTITY.get(n)
            if be is None and n.endswith("_sign"):
                be = "hanging_sign" if n.endswith("_hanging_sign") else "sign"
            if be is None and n.endswith("_banner"):
                be = "banner"
            if be is None and n.endswith("_bed"):
                be = "bed"
            if be is None and (n.endswith("_head") or n.endswith("_skull")):
                be = "skull"
            if be:
                be_ids[i] = be
        by_chunk = {}
        if be_ids:
            ys, zs, xs = np.nonzero(np.isin(self.blocks, np.array(list(be_ids), dtype=np.uint16)))
            for xi, yi, zi in zip(xs.tolist(), ys.tolist(), zs.tolist()):
                x, y, z = xi + self.x0, yi + self.y0, zi + self.z0
                be = be_ids[int(self.blocks[yi, zi, xi])]
                by_chunk.setdefault((x >> 4, z >> 4), []).append(
                    self._be_tag(x, y, z, be, self.bents.get((x, y, z))))
        cx0, cz0 = self.x0 >> 4, self.z0 >> 4
        ncx, ncz = self.sx >> 4, self.sz >> 4
        sec0 = self.y0 >> 4
        nsec = (self.sy + (self.y0 & 15) + 15) >> 4
        pad_lo = self.y0 & 15
        air_only = anvil.section_tag(0, None, self.palette, 0, self.biome_names)
        for cz in range(cz0, cz0 + ncz):
            for cx in range(cx0, cx0 + ncx):
                lx, lz = (cx - cx0) * 16, (cz - cz0) * 16
                column = self.blocks[:, lz:lz + 16, lx:lx + 16]
                bcol = self.biome[lz // 4:lz // 4 + 4, lx // 4:lx // 4 + 4]
                uniform = int(bcol[0, 0]) if (bcol == bcol[0, 0]).all() else None
                sections = []
                for sy in range(anvil.MIN_SECTION, anvil.MAX_SECTION + 1):
                    k = sy - sec0
                    states = None
                    if 0 <= k < nsec:
                        lo = k * 16 - pad_lo
                        hi = lo + 16
                        part = np.zeros((16, 16, 16), dtype=np.uint16)
                        a, b = max(lo, 0), min(hi, self.sy)
                        if a < b:
                            part[a - lo:b - lo] = column[a:b]
                        if part.any():
                            states = part
                    biomes = uniform if uniform is not None else np.broadcast_to(bcol, (4, 4, 4))
                    tag = anvil.section_tag(sy, states, self.palette, biomes, self.biome_names)
                    sections.append(tag)
                writer.put(cx, cz, anvil.chunk_bytes(cx, cz, sections, by_chunk.get((cx, cz), [])))
        # flat ocean ring
        ocean = np.zeros((16 * (anvil.MAX_SECTION - anvil.MIN_SECTION + 1), 16, 16), dtype=np.uint16)
        base = anvil.MIN_SECTION * 16
        ocean[self.y0 - base:floor_y - 3 - base] = self.id(fill_state)
        ocean[floor_y - 3 - base:floor_y + 1 - base] = self.id(floor_state)
        ocean[floor_y + 1 - base:sea_level + 1 - base] = self.id("minecraft:water")
        ocean_sections = []
        for sy in range(anvil.MIN_SECTION, anvil.MAX_SECTION + 1):
            part = ocean[(sy - anvil.MIN_SECTION) * 16:(sy - anvil.MIN_SECTION + 1) * 16]
            ocean_sections.append(anvil.section_tag(sy, part if part.any() else None, self.palette, ob,
                                                    self.biome_names))
        r = ocean_radius_chunks
        for cz in range(-r, r):
            for cx in range(-r, r):
                if cx0 <= cx < cx0 + ncx and cz0 <= cz < cz0 + ncz:
                    continue
                writer.put(cx, cz, anvil.chunk_bytes(cx, cz, ocean_sections, []))
        writer.flush()

    def dump(self, folder):
        """Raw array + palette for the renderer and for later tools."""
        os.makedirs(folder, exist_ok=True)
        self.blocks.tofile(os.path.join(folder, "blocks.u16"))
        with open(os.path.join(folder, "meta.json"), "w", encoding="utf-8") as f:
            json.dump({"x0": self.x0, "y0": self.y0, "z0": self.z0, "sx": self.sx, "sy": self.sy, "sz": self.sz,
                       "palette": self.palette, "marks": self.marks}, f)
