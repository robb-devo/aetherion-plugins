"""Block looks for the preview renderer: textures per face (resolved from the client jar's blockstates and
models), tints, and a box shape per block state. Nothing here is used to build the world."""
import io
import json
import zipfile

import numpy as np
from PIL import Image

from world import base_name, props_of

GRASS_TINT = (0x83, 0xBB, 0x6D)      # meadow
FOLIAGE_TINT = (0x63, 0xA9, 0x48)    # meadow
BIRCH_TINT = (0x80, 0xA7, 0x55)
SPRUCE_TINT = (0x61, 0x99, 0x61)
WATER_TINT = (0x45, 0xAD, 0xF2)      # lukewarm ocean
LILY_TINT = (0x20, 0x80, 0x30)

F_CUTOUT = 1
F_TRANSLUCENT = 2
F_EMISSIVE = 4
F_WATER = 8
F_NOSHADOW = 16

FULL = [(0, 0, 0, 1, 1, 1)]


class Jar:
    def __init__(self, path):
        self.z = zipfile.ZipFile(path)
        self.names = set(self.z.namelist())
        self.tex_cache = {}
        self.model_cache = {}

    def json(self, path):
        if path not in self.names:
            return None
        return json.loads(self.z.read(path).decode("utf-8"))

    def model(self, ref):
        ref = ref.replace("minecraft:", "")
        if ref in self.model_cache:
            return self.model_cache[ref]
        data = self.json("assets/minecraft/models/%s.json" % ref) or {}
        tex = {}
        parent = data.get("parent")
        if parent:
            tex.update(self.model(parent))
        tex.update(data.get("textures", {}))
        self.model_cache[ref] = tex
        return tex

    def textures_for(self, name):
        """dict of texture keys -> texture path (block/xyz) for a block name."""
        bs = self.json("assets/minecraft/blockstates/%s.json" % name)
        model = None
        if bs:
            if "variants" in bs:
                keys = list(bs["variants"])
                if keys and all(k.startswith("age=") for k in keys):
                    keys.sort(key=lambda k: int(k.split("=")[1]))        # crops: show them ripe
                    v = bs["variants"][keys[-1]]
                else:
                    v = next(iter(bs["variants"].values()))
                v = v[0] if isinstance(v, list) else v
                model = v.get("model")
            elif "multipart" in bs:
                a = bs["multipart"][0]["apply"]
                a = a[0] if isinstance(a, list) else a
                model = a.get("model")
        if not model:
            return {}
        tex = dict(self.model(model))
        out = {}
        for k, v in tex.items():
            seen = 0
            while isinstance(v, str) and v.startswith("#") and seen < 8:
                v = tex.get(v[1:])
                seen += 1
            if isinstance(v, str):
                out[k] = v.replace("minecraft:", "")
        return out

    def image(self, ref):
        if ref in self.tex_cache:
            return self.tex_cache[ref]
        if ref.startswith("flat/"):
            rgb = FLAT_LOOK[ref[5:]]
            arr = np.zeros((16, 16, 4), np.uint8)
            arr[..., 0], arr[..., 1], arr[..., 2], arr[..., 3] = rgb[0], rgb[1], rgb[2], 255
            arr[::5, :, :3] = (arr[::5, :, :3] * 0.82).astype(np.uint8)
            self.tex_cache[ref] = arr
            return arr
        path = "assets/minecraft/textures/%s.png" % ref
        arr = None
        if path in self.names:
            im = Image.open(io.BytesIO(self.z.read(path))).convert("RGBA")
            a = np.asarray(im, dtype=np.uint8)
            if a.shape[0] >= 16 and a.shape[1] >= 16:
                arr = a[:16, :16].copy()
        self.tex_cache[ref] = arr
        return arr


FLAT_LOOK = {
    "lantern": (255, 196, 96), "soul_lantern": (96, 220, 230), "torch": (255, 214, 120),
    "wall_torch": (255, 214, 120), "end_rod": (245, 240, 225), "lightning_rod": (196, 110, 78),
    "chain": (62, 66, 78), "candle": (232, 220, 190), "bell": (236, 190, 62), "iron_bars": (120, 122, 126),
}


def pick(tex, *keys):
    for k in keys:
        if k in tex:
            return tex[k]
    return None


def faces(jar, name):
    """(top, side, bottom) texture refs for a block name, or None."""
    if name in ("water",):
        return ("block/water_still",) * 3
    if name == "lava":
        return ("block/lava_still",) * 3
    if name.endswith("_wall_sign") or name.endswith("_sign") or name.endswith("_hanging_sign"):
        wood = name.split("_wall")[0].split("_hanging")[0].replace("_sign", "")
        return ("block/%s_planks" % wood,) * 3
    if name.endswith("_banner"):
        colour = name.replace("_wall_banner", "").replace("_banner", "")
        return ("block/%s_wool" % colour,) * 3
    if name.endswith("_bed"):
        return ("block/%s_wool" % name.replace("_bed", ""),) * 3
    if name in ("chest", "trapped_chest", "ender_chest"):
        return ("block/oak_planks",) * 3
    if name == "bell":
        return ("block/gold_block",) * 3
    if name == "light" or name == "barrier" or name == "structure_void":
        return None
    if name in FLAT_LOOK:
        return ("flat/" + name,) * 3
    tex = jar.textures_for(name)
    if not tex:
        return None
    top = pick(tex, "top", "end", "up", "all", "texture", "cross", "plant", "particle")
    side = pick(tex, "side", "all", "texture", "cross", "plant", "wall", "pane", "front", "crop", "stem", "fire",
                "torch", "rail", "bars", "edge", "lantern", "particle", "top")
    bottom = pick(tex, "bottom", "end", "down", "all", "texture", "particle") or side
    if name == "grass_block":
        return ("block/grass_block_top", "block/grass_block_side", "block/dirt")
    if name in ("dirt_path",):
        return ("block/dirt_path_top", "block/dirt_path_side", "block/dirt")
    if name == "farmland":
        return ("block/farmland_moist", "block/dirt", "block/dirt")
    if name.endswith("_door"):
        return (pick(tex, "top"), pick(tex, "bottom"), pick(tex, "bottom"))
    if top is None and side is None:
        return None
    return (top or side, side or top, bottom or side or top)


def tint_for(name):
    """(top tint, side tint) as rgb tuples or None."""
    if name == "grass_block":
        return GRASS_TINT, None
    if name in ("short_grass", "tall_grass", "fern", "large_fern", "sugar_cane", "potted_fern"):
        return GRASS_TINT, GRASS_TINT
    if name == "birch_leaves":
        return BIRCH_TINT, BIRCH_TINT
    if name == "spruce_leaves":
        return SPRUCE_TINT, SPRUCE_TINT
    if name in ("oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "vine"):
        return FOLIAGE_TINT, FOLIAGE_TINT
    if name == "lily_pad":
        return LILY_TINT, LILY_TINT
    if name == "water":
        return WATER_TINT, WATER_TINT
    return None, None


CROSS = {
    "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "seagrass", "tall_seagrass", "kelp",
    "kelp_plant", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
    "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "sunflower", "lilac",
    "rose_bush", "peony", "torchflower", "brown_mushroom", "red_mushroom", "sugar_cane", "sweet_berry_bush",
    "wheat", "carrots", "potatoes", "beetroots", "cobweb", "hanging_roots", "bamboo", "cave_vines",
    "cave_vines_plant", "mangrove_propagule", "nether_wart",
}
EMISSIVE = {"lantern", "soul_lantern", "torch", "wall_torch", "glowstone", "sea_lantern", "shroomlight",
            "campfire", "soul_campfire", "fire", "lava", "jack_o_lantern", "end_rod", "magma_block",
            "redstone_lamp", "ochre_froglight", "pearlescent_froglight", "verdant_froglight", "candle"}
TRANSLUCENT = {"water", "ice", "glass", "tinted_glass", "slime_block", "honey_block"}


def _facing_box(facing, depth, y0=0.0, y1=1.0):
    """A slab of `depth` on the side the block faces away from (attached to the opposite wall)."""
    if facing == "north":
        return (0, y0, 1 - depth, 1, y1, 1)
    if facing == "south":
        return (0, y0, 0, 1, y1, depth)
    if facing == "west":
        return (1 - depth, y0, 0, 1, y1, 1)
    return (0, y0, 0, depth, y1, 1)


def shape(name, p):
    """(boxes, flags). Boxes in block-local 0..1 coordinates."""
    flags = 0
    if name in EMISSIVE and p.get("lit", "true") != "false":
        flags |= F_EMISSIVE
    if name in TRANSLUCENT or name.endswith("_stained_glass"):
        flags |= F_TRANSLUCENT
    if name == "water":
        return FULL, flags | F_WATER | F_NOSHADOW
    if name.endswith("_leaves"):
        return FULL, flags | F_CUTOUT
    if name in CROSS or name.endswith("_sapling") or name.endswith("_coral") or name.endswith("_tulip"):
        h = 1.0
        return [(0.47, 0, 0.05, 0.53, h, 0.95), (0.05, 0, 0.47, 0.95, h, 0.53)], flags | F_CUTOUT | F_NOSHADOW
    if name.startswith("potted_") or name == "flower_pot":
        return [(0.31, 0, 0.31, 0.69, 0.375, 0.69)], flags
    if name.endswith("_slab"):
        t = p.get("type", "bottom")
        if t == "double":
            return FULL, flags
        return ([(0, 0.5, 0, 1, 1, 1)] if t == "top" else [(0, 0, 0, 1, 0.5, 1)]), flags
    if name.endswith("_stairs"):
        top = p.get("half", "bottom") == "top"
        base = (0, 0.5, 0, 1, 1, 1) if top else (0, 0, 0, 1, 0.5, 1)
        y0, y1 = (0, 0.5) if top else (0.5, 1)
        f = p.get("facing", "north")
        sh = p.get("shape", "straight")
        half = {"north": (0, y0, 0, 1, y1, 0.5), "south": (0, y0, 0.5, 1, y1, 1),
                "west": (0, y0, 0, 0.5, y1, 1), "east": (0.5, y0, 0, 1, y1, 1)}
        from world import CCW, CW
        boxes = [base]
        if sh == "straight":
            boxes.append(half[f])
        elif sh.startswith("inner"):
            side = CCW[f] if sh.endswith("left") else CW[f]
            boxes.append(half[f])
            a, b = half[side], half[f]
            boxes.append((max(a[0], 0), y0, max(a[2], 0), a[3], y1, a[5]))
        else:  # outer: quarter
            side = CCW[f] if sh.endswith("left") else CW[f]
            a, b = half[f], half[side]
            boxes.append((max(a[0], b[0]), y0, max(a[2], b[2]), min(a[3], b[3]), y1, min(a[5], b[5])))
        return boxes, flags
    if name.endswith("_fence") or name.endswith("_wall") or name.endswith("_pane") or name == "iron_bars":
        wall = name.endswith("_wall")
        thin = name.endswith("_pane") or name == "iron_bars"
        w = 0.25 if wall else (0.0625 if thin else 0.125)
        post = 0.25 if wall else (0.0625 if thin else 0.125)
        height = 1.0
        arm_lo, arm_hi = (0.0, 0.875) if wall else ((0.0, 1.0) if thin else (0.375, 0.9375))
        boxes = []
        if not wall or p.get("up", "true") == "true":
            boxes.append((0.5 - post, 0, 0.5 - post, 0.5 + post, height, 0.5 + post))
        for d, box in (("north", (0.5 - w, arm_lo, 0, 0.5 + w, arm_hi, 0.5)),
                       ("south", (0.5 - w, arm_lo, 0.5, 0.5 + w, arm_hi, 1)),
                       ("west", (0, arm_lo, 0.5 - w, 0.5, arm_hi, 0.5 + w)),
                       ("east", (0.5, arm_lo, 0.5 - w, 1, arm_hi, 0.5 + w))):
            v = p.get(d, "false")
            if v in ("true", "low", "tall"):
                if wall and v == "tall":
                    box = (box[0], 0.0, box[2], box[3], 1.0, box[5])
                boxes.append(box)
        if not boxes:
            boxes.append((0.5 - post, 0, 0.5 - post, 0.5 + post, height, 0.5 + post))
        fl = flags | (F_CUTOUT if thin and name != "iron_bars" and "glass" in name else 0)
        if "glass" in name:
            fl |= F_TRANSLUCENT
        return boxes, fl
    if name.endswith("_fence_gate"):
        f = p.get("facing", "north")
        if f in ("north", "south"):
            return [(0, 0.3, 0.44, 1, 1, 0.56)], flags
        return [(0.44, 0.3, 0, 0.56, 1, 1)], flags
    if name.endswith("_trapdoor"):
        if p.get("open", "false") == "true":
            return [_facing_box(p.get("facing", "north"), 0.1875)], flags | F_CUTOUT
        if p.get("half", "bottom") == "top":
            return [(0, 0.8125, 0, 1, 1, 1)], flags | F_CUTOUT
        return [(0, 0, 0, 1, 0.1875, 1)], flags | F_CUTOUT
    if name.endswith("_door"):
        f = p.get("facing", "north")
        if p.get("open", "false") == "true":
            hinge = p.get("hinge", "left")
            from world import CCW, CW
            f = CW[f] if hinge == "left" else CCW[f]
        return [_facing_box(f, 0.1875)], flags | F_CUTOUT
    if name.endswith("_carpet") or name == "snow" or name == "pink_petals" or name == "lily_pad":
        h = 0.0625 if name != "snow" else int(p.get("layers", "1")) / 8.0
        fl = flags | (F_CUTOUT if name in ("pink_petals", "lily_pad") else 0)
        return [(0, 0, 0, 1, h, 1)], fl
    if name in ("dirt_path", "farmland"):
        return [(0, 0, 0, 1, 0.9375, 1)], flags
    if name in ("lantern", "soul_lantern"):
        if p.get("hanging", "false") == "true":
            return [(0.31, 0.06, 0.31, 0.69, 0.56, 0.69), (0.44, 0.56, 0.44, 0.56, 1, 0.56)], flags | F_NOSHADOW
        return [(0.31, 0, 0.31, 0.69, 0.5, 0.69)], flags | F_NOSHADOW
    if name in ("torch", "soul_torch", "candle"):
        return [(0.44, 0, 0.44, 0.56, 0.62, 0.56)], flags | F_NOSHADOW
    if name in ("wall_torch", "soul_wall_torch"):
        b = _facing_box(p.get("facing", "north"), 0.3, 0.2, 0.8)
        return [(max(b[0], 0.35) if b[3] - b[0] > 0.5 else b[0], b[1], max(b[2], 0.35) if b[5] - b[2] > 0.5 else b[2],
                 min(b[3], 0.65) if b[3] - b[0] > 0.5 else b[3], b[4], min(b[5], 0.65) if b[5] - b[2] > 0.5 else b[5])], \
            flags | F_NOSHADOW
    if name == "chain" or name == "end_rod" or name == "lightning_rod":
        a = p.get("axis") or {"up": "y", "down": "y", "north": "z", "south": "z", "west": "x", "east": "x"}.get(
            p.get("facing", "up"), "y")
        if a == "y":
            return [(0.42, 0, 0.42, 0.58, 1, 0.58)], flags | F_NOSHADOW
        if a == "x":
            return [(0, 0.42, 0.42, 1, 0.58, 0.58)], flags | F_NOSHADOW
        return [(0.42, 0.42, 0, 0.58, 0.58, 1)], flags | F_NOSHADOW
    if name == "ladder" or name == "vine" or name == "glow_lichen" or name.endswith("_wall_sign") \
            or name.endswith("_wall_banner"):
        if name == "vine" or name == "glow_lichen":
            boxes = []
            for d in ("north", "south", "west", "east"):
                if p.get(d) == "true":
                    boxes.append(_facing_box({"north": "south", "south": "north", "west": "east", "east": "west"}[d],
                                             0.0625))
            if p.get("up") == "true":
                boxes.append((0, 0.9375, 0, 1, 1, 1))
            return (boxes or [(0, 0, 0, 1, 0.0625, 1)]), flags | F_CUTOUT | F_NOSHADOW
        y0, y1 = (0.28, 0.78) if name.endswith("_wall_sign") else (0, 1)
        if name.endswith("_wall_banner"):
            y0, y1 = -0.9, 0.8
        return [_facing_box(p.get("facing", "north"), 0.125, max(y0, 0), y1)], flags | (F_CUTOUT if name == "ladder" else 0)
    if name.endswith("_sign") or name.endswith("_banner"):
        return [(0.47, 0, 0.47, 0.53, 0.55, 0.53), (0.1, 0.55, 0.45, 0.9, 1, 0.55)], flags
    if name in ("campfire", "soul_campfire"):
        return [(0, 0, 0, 1, 0.44, 1)], flags | F_CUTOUT
    if name.endswith("_bed"):
        return [(0, 0.19, 0, 1, 0.56, 1)], flags
    if name in ("chest", "trapped_chest", "ender_chest"):
        return [(0.0625, 0, 0.0625, 0.9375, 0.875, 0.9375)], flags
    if name in ("anvil", "chipped_anvil", "damaged_anvil"):
        if p.get("facing", "north") in ("north", "south"):
            return [(0.125, 0, 0.125, 0.875, 0.25, 0.875), (0.31, 0.25, 0.25, 0.69, 0.62, 0.75),
                    (0.19, 0.62, 0, 0.81, 1, 1)], flags
        return [(0.125, 0, 0.125, 0.875, 0.25, 0.875), (0.25, 0.25, 0.31, 0.75, 0.62, 0.69),
                (0, 0.62, 0.19, 1, 1, 0.81)], flags
    if name in ("cauldron", "water_cauldron", "composter", "hopper"):
        return [(0, 0, 0, 1, 0.2, 1), (0, 0, 0, 0.125, 1, 1), (0.875, 0, 0, 1, 1, 1), (0, 0, 0, 1, 1, 0.125),
                (0, 0, 0.875, 1, 1, 1)], flags
    if name == "lectern":
        return [(0, 0, 0, 1, 0.125, 1), (0.25, 0.125, 0.25, 0.75, 0.875, 0.75), (0, 0.75, 0, 1, 1, 1)], flags
    if name == "grindstone":
        return [(0.25, 0.25, 0.125, 0.75, 1, 0.875), (0.125, 0, 0.375, 0.875, 0.5, 0.625)], flags
    if name == "bell":
        return [(0.25, 0.25, 0.25, 0.75, 0.81, 0.75), (0.44, 0.81, 0.44, 0.56, 1, 0.56)], flags
    if name in ("stonecutter", "enchanting_table"):
        return [(0, 0, 0, 1, 0.5625 if name == "stonecutter" else 0.75, 1)], flags
    if name in ("skeleton_skull", "player_head"):
        return [(0.25, 0, 0.25, 0.75, 0.5, 0.75)], flags
    if name == "pointed_dripstone":
        return [(0.3, 0, 0.3, 0.7, 1, 0.7)], flags
    if name == "sea_pickle":
        return [(0.3, 0, 0.3, 0.7, 0.4, 0.7)], flags
    if name in ("azalea", "flowering_azalea"):
        return [(0, 0.5, 0, 1, 1, 1), (0.375, 0, 0.375, 0.625, 0.5, 0.625)], flags | F_CUTOUT
    if name in ("big_dripleaf",):
        return [(0, 0.69, 0, 1, 0.94, 1), (0.44, 0, 0.44, 0.56, 0.69, 0.56)], flags | F_CUTOUT
    if name in ("small_dripleaf", "big_dripleaf_stem", "spore_blossom", "amethyst_cluster", "rail", "lever",
                "tripwire", "redstone_wire", "turtle_egg", "decorated_pot", "brewing_stand", "cake", "cocoa",
                "scaffolding", "cactus", "conduit", "daylight_detector"):
        return [(0.2, 0, 0.2, 0.8, 0.8, 0.8)], flags | F_CUTOUT
    if name in ("glass", "tinted_glass") or name.endswith("_stained_glass"):
        return FULL, flags | F_CUTOUT
    if name in ("cobweb",):
        return FULL, flags | F_CUTOUT
    return FULL, flags


def build_tables(jar_path, palette):
    """Returns (atlas uint8 [n,16,16,4], table list of dict per palette id)."""
    jar = Jar(jar_path)
    atlas = []
    atlas_index = {}

    def tex_id(ref):
        if ref is None:
            return -1
        if ref in atlas_index:
            return atlas_index[ref]
        img = jar.image(ref)
        if img is None:
            atlas_index[ref] = -1
            return -1
        atlas_index[ref] = len(atlas)
        atlas.append(img)
        return atlas_index[ref]

    table = []
    missing = set()
    for state in palette:
        name = base_name(state)
        p = props_of(state)
        if name in ("air", "cave_air", "void_air", "light", "barrier", "structure_void"):
            table.append(None)
            continue
        fc = faces(jar, name)
        if fc is None:
            missing.add(name)
            table.append(None)
            continue
        boxes, flags = shape(name, p)
        ttop, tside = tint_for(name)
        ids = [tex_id(f) for f in fc]
        if ids[1] < 0 and ids[0] < 0:
            missing.add(name)
            table.append(None)
            continue
        if ids[0] < 0:
            ids[0] = ids[1]
        if ids[1] < 0:
            ids[1] = ids[0]
        if ids[2] < 0:
            ids[2] = ids[1]
        axis = p.get("axis")
        table.append({"tex": ids, "boxes": boxes, "flags": flags, "ttop": ttop, "tside": tside,
                      "axis": {"x": 1, "z": 2}.get(axis, 0) if name.endswith(("_log", "_wood", "_stem", "hay_block"))
                      or name in ("hay_block", "bone_block", "basalt", "polished_basalt") else 0})
    return np.array(atlas, dtype=np.uint8) if atlas else np.zeros((1, 16, 16, 4), np.uint8), table, sorted(missing)
