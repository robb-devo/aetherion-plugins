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


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEX, exist_ok=True)
    parchment(os.path.join(TEX, 'parchment.png'))
    parchment(os.path.join(TEX, 'parchment_dark.png'), dark=True, seed=11)
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
