#!/usr/bin/env python3
"""Hub props for the sky-isle hub (2026-11-26): item models shown by AetherionPit through item displays.

    python gen_hub_props.py

Writes assets/aetherion/models/item/hub/*.json and assets/aetherion/textures/item/hub/*.png under
AetherionItems/resourcepack, and the CustomModelData entries for minecraft:disc_fragment_5 into gui_icons.json
(build_served_pack.py merges them into the served pack). The codes are mirrored in de.aetherion.pit.hub.HubModels.

Models are drawn in model space (16 px = 1 block) and shown with ItemDisplayTransform.NONE, so the plugin
scales and places them freely. Most faces reuse vanilla block textures; only the scroll paper is painted here.
"""
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'assets', 'aetherion'))
MODELS = os.path.join(PACK, 'models', 'item', 'hub')
TEX = os.path.join(PACK, 'textures', 'item', 'hub')
MATERIAL = 'disc_fragment_5'

CODES = {
    'scroll_paper': 8101,
    'scroll_roller': 8102,
    'lamp_post': 8103,
    'sign_board': 8104,
    'duckling': 8105,
    'crystal': 8106,
    'sign_post': 8107,
    'rune_ring': 8108,
    'scroll_paper_dark': 8109,
    # round 4 (2026-11-27): the Discord owl post, the tinkerer's chest (modpack), the storm orb (Sky Brawl)
    'owl_body': 8110,
    'owl_head': 8111,
    'owl_head_blink': 8112,
    'owl_wing': 8113,
    'envelope': 8114,
    'owl_perch': 8115,
    'chest_base': 8116,
    'chest_lid': 8117,
    'storm_orb': 8118,
}


def face_all(tex, uv=None, cull=False):
    f = {'texture': tex}
    if uv:
        f['uv'] = uv
    return {d: dict(f) for d in ('north', 'south', 'east', 'west', 'up', 'down')}


def _uv(frm, to, face):
    """a uv window inside 0..16 of the face's size, so elements outside the unit cube never sample the atlas"""
    sx, sy, sz = (abs(to[i] - frm[i]) for i in range(3))
    w, h = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy),
            'up': (sx, sz), 'down': (sx, sz)}[face]
    w, h = min(16.0, max(0.25, w)), min(16.0, max(0.25, h))
    u0, v0 = (16 - w) / 2, (16 - h) / 2
    return [round(u0, 3), round(v0, 3), round(u0 + w, 3), round(v0 + h, 3)]


def box(frm, to, tex, rot=None, uv=None, shade=True):
    faces = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        faces[d] = {'texture': tex, 'uv': list(uv) if uv else _uv(frm, to, d)}
    e = {'from': frm, 'to': to, 'faces': faces}
    if rot:
        e['rotation'] = rot
    if not shade:
        e['shade'] = False
    return e


def model(textures, elements, particle=None):
    tx = dict(textures)
    tx['particle'] = particle or next(iter(textures.values()))
    return {'textures': tx, 'elements': elements,
            'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}


# ----------------------------------------------------------------------------- textures

def parchment(path, dark=False, size=64, seed=7):
    rng = random.Random(seed)
    img = Image.new('RGBA', (size, size))
    px = img.load()
    base = (236, 222, 184) if not dark else (58, 44, 66)
    edge = (205, 176, 120) if not dark else (36, 26, 44)
    for y in range(size):
        streak = rng.uniform(-6, 6)
        for x in range(size):
            dx = min(x, size - 1 - x) / (size / 2)
            dy = min(y, size - 1 - y) / (size / 2)
            d = min(dx, dy)
            t = max(0.0, 1.0 - d * 3.2) ** 1.6
            n = rng.uniform(-7, 7) + streak * 0.6
            c = [base[i] * (1 - t) + edge[i] * t + n for i in range(3)]
            px[x, y] = tuple(int(max(0, min(255, v))) for v in c) + (255,)
    ink = (128, 92, 48) if not dark else (176, 140, 230)
    gold = (196, 150, 58) if not dark else (232, 196, 110)
    m = 5
    for x in range(m, size - m):
        for y in (m, size - 1 - m):
            px[x, y] = ink + (255,)
    for y in range(m, size - m):
        for x in (m, size - 1 - m):
            px[x, y] = ink + (255,)
    for (cx, cy) in ((m, m), (size - 1 - m, m), (m, size - 1 - m), (size - 1 - m, size - 1 - m)):
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                if abs(dx) + abs(dy) <= 2:
                    px[cx + dx, cy + dy] = gold + (255,)
    # burnt rim: ragged outermost pixels
    for i in range(size):
        for (x, y) in ((i, 0), (i, size - 1), (0, i), (size - 1, i)):
            if rng.random() < 0.55:
                r, g, b, a = px[x, y]
                px[x, y] = (int(r * 0.62), int(g * 0.55), int(b * 0.45), 255)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ----------------------------------------------------------------------------- models

def scroll_paper(dark=False):
    t = 'aetherion:item/hub/parchment_dark' if dark else 'aetherion:item/hub/parchment'
    e = box([0, 0, 7.9], [16, 16, 8.1], '#p', uv=[0, 0, 16, 16], shade=False)
    return model({'p': t}, [e])


def scroll_roller():
    wood = 'minecraft:block/stripped_dark_oak_log'
    gold = 'minecraft:block/gold_block'
    els = [
        box([-0.5, 7.2, 7.2], [16.5, 8.8, 8.8], '#w', uv=[0, 6, 16, 10]),
        box([-1.8, 6.6, 6.6], [-0.5, 9.4, 9.4], '#g'),
        box([16.5, 6.6, 6.6], [17.8, 9.4, 9.4], '#g'),
        box([-2.6, 7.3, 7.3], [-1.8, 8.7, 8.7], '#g'),
        box([17.8, 7.3, 7.3], [18.6, 8.7, 8.7], '#g'),
    ]
    return model({'w': wood, 'g': gold}, els)


def lamp_post():
    """slim elven lamp post with a swan-neck arm; the lantern itself is a separate glowing block display"""
    pole = 'minecraft:block/stripped_dark_oak_log'
    base = 'minecraft:block/polished_deepslate'
    gold = 'minecraft:block/gold_block'
    leaf = 'minecraft:block/flowering_azalea_leaves'
    els = [
        box([5, 0, 5], [11, 1.5, 11], '#b'),
        box([6, 1.5, 6], [10, 3, 10], '#b'),
        box([6.8, 3, 6.8], [9.2, 4, 9.2], '#g'),
        box([7.3, 4, 7.3], [8.7, 29, 8.7], '#p', uv=[6, 0, 8, 16]),
        box([7, 11, 7], [9, 11.8, 9], '#g'),
        box([7, 21, 7], [9, 21.8, 9], '#g'),
        box([7, 29, 7], [9, 30, 9], '#g'),
        box([7.5, 30, 7.5], [8.5, 31.5, 8.5], '#g'),
        # swan neck: a rising brace and the horizontal arm
        box([8.2, 22.5, 7.55], [9.2, 28.5, 8.45], '#p', rot={'angle': -45, 'axis': 'z', 'origin': [8.7, 25, 8]}),
        box([8.7, 27.6, 7.5], [14.2, 28.6, 8.5], '#p', uv=[0, 7, 16, 9]),
        box([13.6, 26.8, 7.3], [14.8, 29.2, 8.7], '#g'),
        box([13.85, 25.2, 7.85], [14.55, 26.8, 8.15], '#g'),
        # a little leaf tuft where the arm meets the pole
        box([6.6, 26.2, 6.6], [9.4, 27.8, 9.4], '#l', rot={'angle': 22.5, 'axis': 'y', 'origin': [8, 27, 8]}),
    ]
    return model({'p': pole, 'b': base, 'g': gold, 'l': leaf}, els, particle=pole)


def sign_post():
    pole = 'minecraft:block/stripped_spruce_log'
    gold = 'minecraft:block/gold_block'
    els = [
        box([6.5, 0, 6.5], [9.5, 1, 9.5], '#p'),
        box([7.2, 0, 7.2], [8.8, 30, 8.8], '#p', uv=[6, 0, 9, 16]),
        box([7, 30, 7], [9, 31, 9], '#g'),
        box([7.4, 31, 7.4], [8.6, 32, 8.6], '#g'),
    ]
    return model({'p': pole, 'g': gold}, els, particle=pole)


def sign_board():
    """arrow board, 16 px long, pointing towards +x; text displays sit on both faces"""
    wood = 'minecraft:block/spruce_planks'
    dark = 'minecraft:block/dark_oak_planks'
    els = [
        box([0, 0, 7.4], [13, 4.5, 8.6], '#w', uv=[0, 4, 13, 8.5]),
        box([12.2, 0.75, 7.45], [15.2, 3.75, 8.55], '#w', rot={'angle': 45, 'axis': 'z', 'origin': [13.2, 2.25, 8]}),
        box([-0.3, -0.3, 7.3], [13, 0.3, 8.7], '#d'),
        box([-0.3, 4.2, 7.3], [13, 4.8, 8.7], '#d'),
        box([-0.6, -0.3, 7.3], [0, 4.8, 8.7], '#d'),
    ]
    return model({'w': wood, 'd': dark}, els, particle=wood)


def duckling():
    y = 'minecraft:block/yellow_concrete'
    o = 'minecraft:block/orange_concrete'
    k = 'minecraft:block/black_concrete'
    els = [
        box([5, 0, 5], [11, 4, 10.5], '#y'),
        box([10.5, 2.5, 6.5], [12.5, 5, 9], '#y', rot={'angle': 22.5, 'axis': 'z', 'origin': [11, 3, 7.75]}),
        box([4, 4, 5.75], [8.5, 8.5, 9.75], '#y'),
        box([2, 5.2, 6.75], [4, 6.4, 8.75], '#o'),
        box([4.6, 6.6, 5.55], [5.6, 7.6, 5.75], '#k'),
        box([4.6, 6.6, 9.75], [5.6, 7.6, 9.95], '#k'),
        box([6.2, 1.2, 4.6], [9.8, 3.4, 5], '#y'),
        box([6.2, 1.2, 10.5], [9.8, 3.4, 10.9], '#y'),
    ]
    return model({'y': y, 'o': o, 'k': k}, els, particle=y)


def crystal():
    a = 'minecraft:block/amethyst_block'
    c = 'minecraft:block/calcite'
    els = [
        box([6.2, 2, 6.2], [9.8, 13, 9.8], '#a', rot={'angle': 45, 'axis': 'y', 'origin': [8, 8, 8]}),
        box([6.9, 13, 6.9], [9.1, 16, 9.1], '#a', rot={'angle': 45, 'axis': 'y', 'origin': [8, 8, 8]}),
        box([7.4, 0, 7.4], [8.6, 2, 8.6], '#a', rot={'angle': 45, 'axis': 'y', 'origin': [8, 8, 8]}),
        box([9.2, 4, 7.2], [11, 9, 8.8], '#a', rot={'angle': -22.5, 'axis': 'z', 'origin': [9.5, 5, 8]}),
        box([5, 3, 7.2], [6.8, 7, 8.8], '#a', rot={'angle': 22.5, 'axis': 'z', 'origin': [6.5, 4, 8]}),
    ]
    return model({'a': a, 'c': c}, els, particle=a)


def rune_ring():
    """flat ring of 12 small glowing tiles, drawn around the y axis (spun by the plugin)"""
    g = 'minecraft:block/prismarine'
    s = 'minecraft:block/sea_lantern'
    els = []
    import math
    for k in range(12):
        ang = 2 * math.pi * k / 12
        cx, cz = 8 + 7 * math.cos(ang), 8 + 7 * math.sin(ang)
        els.append(box([round(cx - 0.6, 2), 7.6, round(cz - 0.6, 2)], [round(cx + 0.6, 2), 8.4, round(cz + 0.6, 2)],
                       '#s' if k % 3 == 0 else '#g'))
    return model({'g': g, 's': s}, els, particle=g)


# ----------------------------------------------------------------------------- round 4 textures (painted, 16 px)

BLURPLE = (88, 101, 242)
BLURPLE_D = (61, 70, 178)
BLURPLE_DD = (42, 47, 128)
BLURPLE_L = (138, 148, 250)
LAVENDER = (206, 210, 252)


def _img(size=16, fill=(0, 0, 0, 0)):
    img = Image.new('RGBA', (size, size), fill)
    return img, img.load()


def _save(img, name):
    os.makedirs(TEX, exist_ok=True)
    img.save(os.path.join(TEX, name + '.png'))


def owl_feather(seed=21):
    """blurple plumage: scalloped feather rows, darker tips, a few light flecks"""
    rng = random.Random(seed)
    img, px = _img()
    for y in range(16):
        row = y // 3
        for x in range(16):
            off = (row % 2) * 2
            u = (x + off) % 4
            tip = (y % 3 == 2) and u in (1, 2)
            edge = (y % 3 == 2)
            c = BLURPLE_D if edge else BLURPLE
            if tip:
                c = BLURPLE_DD
            if (y % 3 == 0) and u == 1:
                c = BLURPLE_L
            n = rng.randint(-6, 6)
            px[x, y] = tuple(max(0, min(255, v + n)) for v in c) + (255,)
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        px[x, y] = LAVENDER + (255,)
    _save(img, 'owl_feather')


def owl_belly(seed=22):
    """pale chest with small dark chevrons"""
    rng = random.Random(seed)
    img, px = _img()
    for y in range(16):
        for x in range(16):
            n = rng.randint(-5, 5)
            px[x, y] = tuple(max(0, min(255, v + n)) for v in LAVENDER) + (255,)
    for cy in range(2, 16, 4):
        for cx in range((cy // 4) % 2 * 2 + 1, 16, 4):
            for dx, dy in ((-1, 0), (0, 1), (1, 0)):
                x, y = cx + dx, cy + dy
                if 0 <= x < 16 and 0 <= y < 16:
                    px[x, y] = BLURPLE_D + (255,)
    _save(img, 'owl_belly')


def owl_face(blink=False):
    """facial disk with two big golden eyes (or closed lids), a dark rim, beak space in the middle"""
    img, px = _img()
    for y in range(16):
        for x in range(16):
            # heart-shaped disk: two circles side by side
            d = min(((x - 4.5) ** 2 + (y - 7.5) ** 2) ** 0.5, ((x - 11.5) ** 2 + (y - 7.5) ** 2) ** 0.5)
            if d < 4.4:
                c = (244, 244, 255)
            elif d < 5.6:
                c = (190, 196, 248)
            else:
                c = BLURPLE
            px[x, y] = c + (255,)
    for ex in (4.5, 11.5):
        xs = range(int(ex - 3.5), int(ex + 4.5))
        if blink:
            for x in range(int(ex - 2), int(ex + 3)):
                px[x, 8] = (40, 34, 60, 255)
            px[int(ex - 2.5), 7] = (40, 34, 60, 255)
            px[int(ex + 2.5), 7] = (40, 34, 60, 255)
            continue
        for y in range(4, 12):
            for x in xs:
                if not 0 <= x < 16:
                    continue
                d = ((x + 0.5 - (ex + 0.5)) ** 2 + (y + 0.5 - 8.0) ** 2) ** 0.5
                if d < 1.2:
                    px[x, y] = (18, 14, 26, 255)
                elif d < 2.5:
                    px[x, y] = (255, 190, 44, 255)
                elif d < 3.2:
                    px[x, y] = (150, 84, 18, 255)
        px[int(ex) - 1, 6] = (255, 255, 255, 255)
    _save(img, 'owl_face_blink' if blink else 'owl_face')


def owl_misc():
    """left half beak/claw horn, right half the blurple wax and a darker feather for tufts"""
    img, px = _img()
    for y in range(16):
        for x in range(16):
            if x < 8:
                c = (232, 168, 58) if y < 8 else (64, 54, 70)
            else:
                c = BLURPLE_DD if y < 8 else (120, 130, 248)
            px[x, y] = c + (255,)
    _save(img, 'owl_misc')


def envelope_tex():
    img, px = _img()
    for y in range(16):
        for x in range(16):
            c = (246, 236, 212)
            if y in (0, 15) or x in (0, 15):
                c = (214, 196, 158)
            # flap lines from the top corners to the middle
            if abs((y - 1) - abs(x - 7.5) * 0.9) < 0.6 and y < 9:
                c = (200, 180, 140)
            px[x, y] = c + (255,)
    for y in range(7, 12):
        for x in range(6, 10):
            d = ((x - 7.5) ** 2 + (y - 9) ** 2) ** 0.5
            if d < 2.3:
                px[x, y] = (BLURPLE if d < 1.5 else BLURPLE_D) + (255,)
    _save(img, 'envelope')


def chest_tex():
    """warm planks with dark seams, gold bands and corner studs; the lid gets a lock plate"""
    rng = random.Random(31)
    for name, lock in (('chest_wood', False), ('chest_lid', True)):
        img, px = _img()
        for y in range(16):
            for x in range(16):
                plank = y // 4
                base = (150, 96, 52) if plank % 2 == 0 else (138, 86, 46)
                n = rng.randint(-8, 8)
                c = tuple(max(0, min(255, v + n)) for v in base)
                if y % 4 == 3:
                    c = (84, 50, 26)
                if x in (0, 15) or y in (0, 15):
                    c = (212, 164, 64)
                if (x in (1, 14)) and (y in (1, 14)):
                    c = (255, 226, 130)
                px[x, y] = c + (255,)
        if lock:
            for y in range(10, 16):
                for x in range(6, 10):
                    px[x, y] = (226, 182, 80, 255) if (x in (6, 9) or y in (10, 15)) else (196, 150, 58, 255)
            px[7, 12] = px[8, 12] = (40, 26, 16, 255)
            px[7, 13] = (40, 26, 16, 255)
        _save(img, name)


def storm_tex():
    rng = random.Random(41)
    img, px = _img()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            swirl = (x * 3 + y * 5 + int(d * 4)) % 7
            c = (120, 236, 255) if swirl < 2 else ((60, 160, 220) if swirl < 5 else (210, 252, 255))
            n = rng.randint(-10, 10)
            px[x, y] = tuple(max(0, min(255, v + n)) for v in c) + (255,)
    _save(img, 'storm')


# ----------------------------------------------------------------------------- round 4 models

def face_box(frm, to, tex_all, faces_override=None, rot=None):
    """box with per-face textures: faces_override maps face -> (texture, uv or None)"""
    e = box(frm, to, tex_all, rot=rot)
    for face, (tex, uv) in (faces_override or {}).items():
        e['faces'][face] = {'texture': tex, 'uv': list(uv) if uv else _uv(frm, to, face)}
    return e


def owl_body():
    """the body around the model centre; front = south (+z). Feet grip at y=0."""
    F, B, M = '#f', '#b', '#m'
    els = [
        face_box([4, 1, 5], [12, 10, 11], F, {'south': (B, [2, 1, 14, 15])}),
        face_box([4.6, 9.5, 5.4], [11.4, 11.5, 10.6], F),
        # tail fan behind
        face_box([5.5, 0, 3.6], [10.5, 4.5, 5], F, rot={'angle': -22.5, 'axis': 'x', 'origin': [8, 4, 5]}),
        # talons gripping the perch
        face_box([5, 0, 8.6], [7, 1, 11.4], M, {f: (M, [1, 9, 7, 15]) for f in ('north', 'south', 'east', 'west', 'up', 'down')}),
        face_box([9, 0, 8.6], [11, 1, 11.4], M, {f: (M, [1, 9, 7, 15]) for f in ('north', 'south', 'east', 'west', 'up', 'down')}),
    ]
    return model({'f': 'aetherion:item/hub/owl_feather', 'b': 'aetherion:item/hub/owl_belly',
                  'm': 'aetherion:item/hub/owl_misc'}, els, particle='aetherion:item/hub/owl_feather')


def owl_head(blink=False):
    """pivot at the neck = model centre (8, 8, 8); the face looks south (+z)"""
    F, K, M = '#f', '#k', '#m'
    els = [
        face_box([3.5, 8, 4.5], [12.5, 15, 11.5], F, {'south': (K, [0, 1, 16, 15])}),
        # ear tufts
        face_box([3.6, 14.5, 7], [5.6, 17.5, 9], F, {f: ('#m', [9, 1, 15, 7]) for f in ('north', 'south', 'east', 'west', 'up')},
                 rot={'angle': 22.5, 'axis': 'z', 'origin': [4.6, 15, 8]}),
        face_box([10.4, 14.5, 7], [12.4, 17.5, 9], F, {f: ('#m', [9, 1, 15, 7]) for f in ('north', 'south', 'east', 'west', 'up')},
                 rot={'angle': -22.5, 'axis': 'z', 'origin': [11.4, 15, 8]}),
        # beak
        face_box([7.3, 9.6, 11.4], [8.7, 11.4, 12.4], M, {f: (M, [1, 1, 7, 7]) for f in ('north', 'south', 'east', 'west', 'up', 'down')}),
    ]
    face = 'aetherion:item/hub/owl_face_blink' if blink else 'aetherion:item/hub/owl_face'
    return model({'f': 'aetherion:item/hub/owl_feather', 'k': face, 'm': 'aetherion:item/hub/owl_misc'}, els,
                 particle='aetherion:item/hub/owl_feather')


def owl_wing():
    """one wing hanging from the shoulder at the model centre; flapping rotates it about z"""
    els = [
        face_box([7.4, 1.5, 5.4], [8.6, 8.4, 10.6], '#f', {'east': ('#b', [3, 2, 13, 14])}),
        face_box([7.5, 0, 6.2], [8.5, 1.6, 9.6], '#f'),
    ]
    return model({'f': 'aetherion:item/hub/owl_feather', 'b': 'aetherion:item/hub/owl_belly'}, els,
                 particle='aetherion:item/hub/owl_feather')


def envelope():
    e = box([3, 4.5, 7.8], [13, 11.5, 8.2], '#e', uv=[0, 0, 16, 16], shade=False)
    return model({'e': 'aetherion:item/hub/envelope'}, [e])


def owl_perch():
    """falconer's perch: stone foot, dark post, birch cross bar wrapped in rope, a little letter box"""
    st, po, bar, rope, gold = '#s', '#p', '#c', '#r', '#g'
    els = [
        box([3, 0, 3], [13, 2, 13], st),
        box([4.5, 2, 4.5], [11.5, 3, 11.5], st),
        box([7, 3, 7], [9, 26, 9], po, uv=[6, 0, 8, 16]),
        box([6.6, 12, 6.6], [9.4, 13, 9.4], gold),
        box([0, 26, 7.1], [16, 27.8, 8.9], bar, uv=[0, 6, 16, 8]),
        box([3.6, 25.8, 6.9], [5.0, 28.0, 9.1], rope),
        box([11.0, 25.8, 6.9], [12.4, 28.0, 9.1], rope),
        box([6.8, 25.6, 6.8], [9.2, 28.2, 9.2], gold),
        # letter box hanging on the post, letters peeking out
        box([5.2, 15, 9], [10.8, 20, 12.4], po),
        box([5.0, 19.8, 8.8], [11.0, 20.6, 12.6], gold),
        face_box([6.2, 20.6, 10.0], [7.8, 22.6, 10.4], '#e', {f: ('#e', [2, 2, 14, 14]) for f in ('north', 'south')}),
        face_box([8.4, 20.6, 10.6], [9.8, 22.0, 11.0], '#e', {f: ('#e', [2, 2, 14, 14]) for f in ('north', 'south')}),
    ]
    return model({'s': 'minecraft:block/polished_deepslate', 'p': 'minecraft:block/stripped_dark_oak_log',
                  'c': 'minecraft:block/stripped_birch_log', 'r': 'minecraft:block/stripped_bamboo_block',
                  'g': 'minecraft:block/gold_block', 'e': 'aetherion:item/hub/envelope'}, els,
                 particle='minecraft:block/stripped_dark_oak_log')


def chest_base():
    """chest body, open at the top: the treasure inside glows (gold, a gem or two) and shows when the lid lifts"""
    W, T = '#w', '#t'
    body = box([1, 0, 3], [15, 9, 13], W, uv=[0, 3, 16, 16])
    del body['faces']['up']
    els = [
        body,
        box([0.6, 0, 2.6], [1.6, 9.2, 3.6], T),
        box([14.4, 0, 2.6], [15.4, 9.2, 3.6], T),
        box([0.6, 0, 12.4], [1.6, 9.2, 13.4], T),
        box([14.4, 0, 12.4], [15.4, 9.2, 13.4], T),
        box([1.6, 9, 3], [14.4, 9.4, 3.6], T),
        box([1.6, 9, 12.4], [14.4, 9.4, 13], T),
        box([1.2, 7.6, 3.4], [14.8, 8.5, 12.6], '#i'),
        box([3.5, 8.5, 5], [5.3, 10.2, 6.8], '#a', rot={'angle': 22.5, 'axis': 'y', 'origin': [4.4, 9, 5.9]}),
        box([10.2, 8.5, 8.6], [11.8, 9.8, 10.2], '#e', rot={'angle': -22.5, 'axis': 'y', 'origin': [11, 9, 9.4]}),
        box([7, 8.5, 6], [9.4, 9.1, 8.4], T),
    ]
    return model({'w': 'aetherion:item/hub/chest_wood', 't': 'minecraft:block/gold_block',
                  'i': 'minecraft:block/glowstone', 'a': 'minecraft:block/amethyst_block',
                  'e': 'minecraft:block/emerald_block'}, els, particle='aetherion:item/hub/chest_wood')


def chest_lid():
    """the lid hinged at the back edge = model centre (8, 8, 8); closed it covers z 3..13 towards +z"""
    L = '#l'
    els = [
        face_box([1, 8, 8], [15, 12, 18], L, {'south': (L, [0, 4, 16, 16]), 'up': (L, [0, 0, 16, 16])}),
        box([0.6, 7.8, 7.6], [1.6, 12.4, 18.4], '#t'),
        box([14.4, 7.8, 7.6], [15.4, 12.4, 18.4], '#t'),
    ]
    return model({'l': 'aetherion:item/hub/chest_lid', 't': 'minecraft:block/gold_block'}, els,
                 particle='aetherion:item/hub/chest_lid')


def storm_orb():
    s = '#s'
    els = [
        box([4, 4, 4], [12, 12, 12], s, rot={'angle': 45, 'axis': 'y', 'origin': [8, 8, 8]}),
        box([5, 5, 5], [11, 11, 11], s, rot={'angle': 45, 'axis': 'x', 'origin': [8, 8, 8]}),
        box([6.5, 1, 6.5], [9.5, 15, 9.5], '#c', rot={'angle': 45, 'axis': 'y', 'origin': [8, 8, 8]}),
    ]
    return model({'s': 'aetherion:item/hub/storm', 'c': 'minecraft:block/sea_lantern'}, els,
                 particle='aetherion:item/hub/storm')


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEX, exist_ok=True)
    parchment(os.path.join(TEX, 'parchment.png'))
    parchment(os.path.join(TEX, 'parchment_dark.png'), dark=True, seed=11)
    owl_feather()
    owl_belly()
    owl_face()
    owl_face(blink=True)
    owl_misc()
    envelope_tex()
    chest_tex()
    storm_tex()
    models = {
        'scroll_paper': scroll_paper(),
        'scroll_paper_dark': scroll_paper(dark=True),
        'scroll_roller': scroll_roller(),
        'lamp_post': lamp_post(),
        'sign_board': sign_board(),
        'sign_post': sign_post(),
        'duckling': duckling(),
        'crystal': crystal(),
        'rune_ring': rune_ring(),
        'owl_body': owl_body(),
        'owl_head': owl_head(),
        'owl_head_blink': owl_head(blink=True),
        'owl_wing': owl_wing(),
        'envelope': envelope(),
        'owl_perch': owl_perch(),
        'chest_base': chest_base(),
        'chest_lid': chest_lid(),
        'storm_orb': storm_orb(),
    }
    for name, m in models.items():
        with open(os.path.join(MODELS, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(m, f, indent=1)
            f.write('\n')
    icons_path = os.path.join(HERE, 'gui_icons.json')
    with open(icons_path, encoding='utf-8') as f:
        icons = json.load(f)
    icons[MATERIAL] = [[code, 'aetherion:item/hub/' + name] for name, code in sorted(CODES.items(), key=lambda kv: kv[1])]
    with open(icons_path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(icons, f, indent=2, sort_keys=True)
        f.write('\n')
    print('wrote %d models, %d codes on %s' % (len(models), len(CODES), MATERIAL))


if __name__ == '__main__':
    main()
