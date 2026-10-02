"""Renderers: top-down map + vectorised DDA perspective raycaster (for verification and docs)."""
from __future__ import annotations

import math

import numpy as np
from PIL import Image

from world import X0, Y0, Z0, NX, NY, NZ, World

C = {
    "grass_block": (98, 150, 58), "dirt": (134, 96, 67), "coarse_dirt": (119, 85, 59), "podzol": (91, 63, 24),
    "moss_block": (89, 110, 45), "rooted_dirt": (144, 104, 77), "dirt_path": (148, 122, 65), "farmland": (100, 70, 40),
    "stone": (125, 125, 125), "andesite": (136, 136, 137), "tuff": (108, 109, 102), "calcite": (223, 224, 220),
    "cobblestone": (122, 122, 122), "mossy_cobblestone": (100, 118, 90), "dripstone_block": (134, 107, 92),
    "snow_block": (245, 250, 250), "snow": (245, 250, 250), "sand": (219, 207, 163), "gravel": (131, 127, 126),
    "clay": (160, 166, 179), "packed_mud": (142, 106, 79), "mud": (60, 57, 60), "basalt": (80, 80, 86),
    "smooth_basalt": (72, 72, 78), "blackstone": (42, 36, 41), "polished_blackstone": (53, 48, 56),
    "water": (54, 92, 196), "stone_bricks": (122, 121, 122), "mossy_stone_bricks": (115, 121, 105),
    "cracked_stone_bricks": (118, 117, 118), "chiseled_stone_bricks": (119, 118, 119), "polished_andesite": (132, 135, 134),
    "smooth_stone": (159, 159, 159), "oak_planks": (162, 130, 78), "spruce_planks": (114, 84, 48),
    "dark_oak_planks": (66, 43, 20), "birch_planks": (192, 175, 121), "cherry_planks": (226, 178, 172),
    "oak_log": (109, 85, 50), "spruce_log": (58, 37, 16), "birch_log": (216, 215, 210), "dark_oak_log": (60, 46, 26),
    "cherry_log": (54, 33, 44), "stripped_oak_log": (177, 144, 86), "stripped_spruce_log": (115, 89, 52),
    "stripped_dark_oak_log": (96, 76, 49), "oak_leaves": (60, 120, 40), "birch_leaves": (110, 150, 70),
    "spruce_leaves": (50, 90, 55), "cherry_leaves": (232, 170, 200), "azalea_leaves": (90, 130, 50),
    "flowering_azalea_leaves": (120, 130, 80), "dark_oak_leaves": (50, 100, 30), "white_terracotta": (210, 178, 161),
    "terracotta": (152, 94, 68), "red_terracotta": (143, 61, 47), "brown_terracotta": (77, 51, 36),
    "white_wool": (234, 236, 237), "red_wool": (161, 39, 35), "hay_block": (166, 136, 38),
    "deepslate_tiles": (54, 54, 55), "deepslate_bricks": (70, 70, 71), "polished_deepslate": (72, 72, 73),
    "cobbled_deepslate": (77, 77, 80), "deepslate": (80, 80, 82), "bricks": (150, 97, 83),
    "waxed_weathered_cut_copper": (108, 153, 110), "waxed_oxidized_cut_copper": (79, 153, 126),
    "waxed_exposed_cut_copper": (154, 121, 101), "waxed_weathered_copper": (108, 153, 110),
    "sandstone": (216, 203, 155), "smooth_sandstone": (223, 214, 170), "cut_sandstone": (217, 206, 159),
    "chiseled_sandstone": (216, 202, 155), "red_sandstone": (186, 99, 29), "cut_red_sandstone": (189, 101, 31),
    "lantern": (230, 180, 90), "glass": (200, 220, 230), "glass_pane": (200, 220, 230), "iron_bars": (110, 110, 110),
    "bone_block": (229, 225, 207), "netherrack": (110, 50, 50), "magma_block": (140, 60, 30),
    "mud_bricks": (137, 103, 79), "tuff_bricks": (98, 102, 95), "polished_tuff": (97, 104, 99),
    "sea_lantern": (172, 199, 190), "glowstone": (171, 131, 84), "shroomlight": (240, 146, 70),
    "ochre_froglight": (250, 245, 206), "mushroom_stem": (203, 196, 185), "brown_mushroom_block": (149, 111, 81),
    "red_mushroom_block": (200, 46, 45), "slime_block": (111, 192, 91), "crying_obsidian": (32, 10, 60),
    "obsidian": (15, 10, 24), "purpur_block": (169, 125, 169), "amethyst_block": (133, 97, 191),
    "copper_block": (192, 107, 79), "chain": (60, 60, 70), "barrel": (134, 100, 58), "bell": (250, 210, 60),
    "wheat": (180, 160, 60), "carrots": (90, 150, 40), "potatoes": (90, 150, 40), "pumpkin": (198, 118, 24),
    "melon": (111, 145, 30), "composter": (110, 80, 40), "blue_wool": (53, 57, 157), "yellow_wool": (248, 197, 39),
    "light_blue_wool": (58, 175, 217), "cyan_wool": (21, 137, 145), "green_wool": (84, 109, 27),
    "black_wool": (20, 21, 25), "gray_wool": (62, 68, 71), "light_gray_wool": (142, 142, 134),
    "brown_wool": (114, 71, 40), "orange_wool": (240, 118, 19), "purple_wool": (121, 42, 172),
    "lime_wool": (112, 185, 25), "pink_wool": (237, 141, 172), "magenta_wool": (189, 68, 179),
    "quartz_block": (235, 229, 222), "smooth_quartz": (235, 229, 222), "iron_block": (220, 220, 220),
    "gold_block": (246, 208, 61), "anvil": (68, 68, 68), "lectern": (173, 137, 83), "bookshelf": (117, 94, 59),
    "cartography_table": (103, 87, 67), "smithing_table": (57, 58, 70), "blast_furnace": (80, 80, 81),
    "furnace": (110, 110, 110), "grindstone": (140, 140, 140), "crafting_table": (120, 73, 42),
    "scaffolding": (170, 132, 73), "rail": (120, 110, 90), "coal_ore": (105, 105, 105), "iron_ore": (136, 129, 122),
    "copper_ore": (124, 125, 120), "light_gray_concrete": (125, 125, 115), "white_concrete": (207, 213, 214),
    "packed_ice": (141, 180, 250), "blue_ice": (116, 167, 253), "prismarine": (99, 156, 151),
    "dark_prismarine": (51, 91, 75), "nether_bricks": (44, 21, 26), "granite": (149, 103, 85),
    "polished_granite": (154, 106, 89), "mangrove_planks": (117, 54, 48), "pointed_dripstone": (129, 102, 89),
    "end_rod": (220, 220, 220), "chest": (160, 110, 40), "campfire": (90, 70, 50), "smoker": (90, 85, 80),
    "loom": (140, 120, 90), "stonecutter": (120, 120, 120), "cauldron": (60, 60, 60), "coal_block": (20, 20, 20),
    "ladder": (130, 100, 60), "amethyst_cluster": (160, 120, 220), "chiseled_bookshelf": (117, 94, 59),
    "beetroots": (100, 140, 50), "carved_pumpkin": (198, 118, 24), "polished_basalt": (88, 88, 92),
    "gilded_blackstone": (60, 45, 30), "polished_blackstone_bricks": (48, 43, 50), "skeleton_skull": (200, 200, 200),
    "soul_campfire": (80, 120, 140), "brown_mushroom": (150, 110, 80), "red_mushroom": (200, 40, 40),
    "chiseled_polished_blackstone": (53, 48, 56), "waxed_copper_grate": (190, 110, 80), "light_blue_glazed_terracotta": (90, 160, 210),
    "mushroom_stem": (203, 196, 185), "dead_bush": (120, 90, 50), "sweet_berry_bush": (60, 90, 50),
    "lily_pad": (40, 110, 40), "sugar_cane": (140, 190, 90), "water_cauldron": (60, 60, 60), "light": None, "barrier": None, "structure_void": None,
}

TRANSPARENT_HINTS = ("short_grass", "tall_grass", "fern", "large_fern", "poppy", "dandelion", "cornflower",
                     "oxeye_daisy", "azure_bluet", "allium", "lily_of_the_valley", "red_tulip", "orange_tulip",
                     "white_tulip", "pink_tulip", "blue_orchid", "peony", "lilac", "rose_bush", "sunflower",
                     "dead_bush", "pink_petals", "hanging_roots", "torch", "vine", "sweet_berry", "seagrass",
                     "kelp", "lily_pad", "glow_lichen", "cobweb", "light[", "minecraft:light", "button",
                     "pressure_plate", "tripwire", "flower_pot", "potted_", "candle", "carpet", "redstone_wire",
                     "spore_blossom", "moss_carpet", "rail")
THIN_HINTS = ("_fence[", "pane[", "iron_bars", "chain[", "lantern[", "ladder[", "trapdoor[", "_sign[", "scaffolding",
              "lightning_rod", "end_rod", "bell[")


def base_name(state: str) -> str:
    s = state.split("[")[0].replace("minecraft:", "")
    return s


def color_of(state: str):
    if state == "minecraft:air":
        return None
    for t in TRANSPARENT_HINTS:
        if t in state:
            return None
    b = base_name(state)
    if b in C:
        return C[b]
    for suf in ("_stairs", "_slab", "_wall", "_fence", "_trapdoor", "_door", "_pressure_plate", "_button",
                "_fence_gate", "_wall_sign", "_hanging_sign", "_sign", "_carpet"):
        if b.endswith(suf):
            root = b[: -len(suf)]
            for cand in (root, root + "s", root + "_planks", root + "_block", root.replace("brick", "bricks"),
                         root.replace("tile", "tiles"), root + "_bricks"):
                if cand in C:
                    return C[cand]
            if root.endswith("brick") and root + "s" in C:
                return C[root + "s"]
    if b.endswith("_wool") or b.endswith("_concrete") or b.endswith("_terracotta"):
        return (150, 150, 150)
    if "log" in b or "wood" in b:
        return (100, 80, 50)
    if "leaves" in b:
        return (60, 120, 40)
    if "copper" in b:
        return (108, 153, 110)
    return (170, 60, 170)  # unknown = magenta (spot them)


def palette_colors(Wd: World, thin_solid=True):
    cols = np.zeros((len(Wd.pal), 3), dtype=np.float32)
    solid = np.zeros(len(Wd.pal), dtype=bool)
    unknown = []
    for i, s in enumerate(Wd.pal):
        if not thin_solid and any(t in s for t in THIN_HINTS):
            continue
        c = color_of(s)
        if c is None:
            continue
        if c == (170, 60, 170):
            unknown.append(s)
        cols[i] = c
        solid[i] = True
    return cols / 255.0, solid, unknown


def topdown(Wd: World, scale=2, path=None):
    cols, solid, unknown = palette_colors(Wd)
    A = Wd.a
    S = solid[A]  # (NX,NY,NZ) bool
    # highest solid
    anyv = S.any(axis=1)
    hi = NY - 1 - np.argmax(S[:, ::-1, :], axis=1)
    hi = np.where(anyv, hi, 0)
    ids = np.take_along_axis(A, hi[:, None, :], axis=1)[:, 0, :]
    img = cols[ids]
    hy = hi.astype(float)
    gx = np.zeros_like(hy)
    gx[1:-1, :] = hy[2:, :] - hy[:-2, :]
    gz = np.zeros_like(hy)
    gz[:, 1:-1] = hy[:, 2:] - hy[:, :-2]
    shade = np.clip(1.0 + 0.09 * (-gx - gz), 0.55, 1.35)
    shade *= (0.82 + 0.18 * np.clip((hy - 60) / 100, 0, 1))
    img = img * shade[..., None]
    img[~anyv] = (0.07, 0.08, 0.12)
    im = (np.clip(np.transpose(img, (1, 0, 2)), 0, 1) * 255).astype(np.uint8)  # rows=z, cols=x
    I = Image.fromarray(im).resize((NX * scale, NZ * scale), Image.NEAREST)
    if path:
        I.save(path)
    return I, unknown


def raycast(Wd: World, eye, yaw_deg, pitch_deg, fov=70, w=960, h=540, maxd=520, path=None, fog=0.0018,
            sun=(0.45, 0.8, -0.35), cols_solid=None):
    """Minecraft yaw: 0 = +z (south), 90 = -x (west), 180 = -z, -90/270 = +x. Pitch: + looks down."""
    cols, solid, _ = cols_solid if cols_solid is not None else palette_colors(Wd, thin_solid=False)
    A = Wd.a
    yaw = math.radians(yaw_deg)
    pitch = math.radians(pitch_deg)
    fwd = np.array([-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
    right = np.array([-math.cos(yaw), 0.0, -math.sin(yaw)])
    up = np.cross(right, fwd)
    up /= np.linalg.norm(up)
    right = np.cross(fwd, up)
    t = math.tan(math.radians(fov) / 2)
    u = (np.arange(w) + 0.5) / w * 2 - 1
    v = (np.arange(h) + 0.5) / h * 2 - 1
    U, V = np.meshgrid(u * t, -v * t * h / w)
    D = fwd[None, None, :] + U[..., None] * right[None, None, :] + V[..., None] * up[None, None, :]
    D /= np.linalg.norm(D, axis=-1, keepdims=True)
    D = D.reshape(-1, 3)
    N = D.shape[0]
    O = np.array([eye[0] - X0, eye[1] - Y0, eye[2] - Z0], dtype=np.float64)
    pos = np.floor(O).astype(np.int64)[None, :].repeat(N, 0)
    step = np.where(D > 0, 1, -1).astype(np.int64)
    invD = np.where(np.abs(D) < 1e-9, 1e9, 1.0 / np.where(np.abs(D) < 1e-9, 1e-9, D))
    nxt = np.where(D > 0, pos + 1, pos).astype(np.float64)
    tMax = (nxt - O[None, :]) * invD
    tDelta = np.abs(invD)
    out = np.zeros((N, 3), dtype=np.float32)
    done = np.zeros(N, dtype=bool)
    tcur = np.zeros(N)
    face = np.zeros(N, dtype=np.int64)
    active = np.arange(N)
    dims = np.array([NX, NY, NZ])
    sunv = np.array(sun) / np.linalg.norm(sun)
    face_shade = {0: 0.78, 1: 1.0, 2: 0.62}
    for it in range(int(maxd * 1.8)):
        if active.size == 0:
            break
        p = pos[active]
        inside = np.all((p >= 0) & (p < dims), axis=1)
        tc = tcur[active]
        alive = tc < maxd
        # sample
        idx = np.zeros(active.size, dtype=np.int64)
        pi = p[inside]
        idx[inside] = A[pi[:, 0], pi[:, 1], pi[:, 2]]
        hit = inside & solid[idx] & alive
        if hit.any():
            ha = active[hit]
            c = cols[idx[hit]]
            f = face[ha]
            sh = np.where(f == 1, 1.0, np.where(f == 0, 0.8, 0.66))
            # top faces lit by sun; bottom faces (underside) darker
            ddy = D[ha, 1]
            sh = np.where((f == 1) & (ddy > 0), 0.5, sh)
            col = c * sh[:, None]
            dist_ = tcur[ha]
            fogf = 1 - np.exp(-fog * dist_)
            sky = sky_color(D[ha])
            out[ha] = col * (1 - fogf[:, None]) + sky * fogf[:, None]
            done[ha] = True
        # rays that left the box going outward, or too far
        leaving = ~inside & np.any(((p < 0) & (step[active] < 0)) | ((p >= dims) & (step[active] > 0)), axis=1)
        kill = hit | leaving | ~alive
        active = active[~kill]
        if active.size == 0:
            break
        tm = tMax[active]
        ax = np.argmin(tm, axis=1)
        rows = np.arange(active.size)
        tcur[active] = tm[rows, ax]
        pos[active, ax] += step[active, ax]
        tMax[active, ax] += tDelta[active, ax]
        face[active] = ax
    nd = ~done
    out[nd] = sky_color(D[nd])
    img = (np.clip(out.reshape(h, w, 3), 0, 1) * 255).astype(np.uint8)
    I = Image.fromarray(img)
    if path:
        I.save(path)
    return I


def sky_color(D):
    y = np.clip(D[:, 1], -1, 1)
    top = np.array([0.42, 0.62, 0.92])
    hor = np.array([0.80, 0.86, 0.95])
    low = np.array([0.55, 0.62, 0.78])
    a = np.clip(y, 0, 1)[:, None]
    b = np.clip(-y, 0, 1)[:, None]
    return (hor * (1 - a) + top * a) * (1 - b) + low * b
