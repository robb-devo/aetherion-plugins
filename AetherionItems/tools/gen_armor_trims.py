"""Worn armor that matches the icons: one custom armor trim per armor set, painted over the whole piece.

The server is 1.21.1, so custom equipment models are not available; a trim is. A trim pattern texture is drawn on
top of the base armor with the same UVs, and pixels outside the trim palette keep their colour, so a fully painted
trim texture is the set's own armor. This script reads every custom armor piece from the served pack (material,
piece, custom model data -> icon), takes each set's colours from its chestplate icon, repaints the vanilla armor
layer of the set's material in those colours (shading kept), adds the set's accent (chest gem, belt buckle, visor
line) and writes:

* resourcepack: assets/aetherion/textures/trims/models/armor/<set>_aether.png and <set>_leggings_aether.png
  (1.21.1 clients), the same art under trims/entity/humanoid[_leggings]/<set>_aether.png (1.21.2+ clients via
  ViaVersion), and assets/minecraft/atlases/armor_trims.json listing them (atlas sources add up across packs).
* datapack/aetherion-armor: the trim material aetherion:aether and one trim pattern per set. Template item and
  ingredient are barriers, so no smithing recipe can apply them; only the Items plugin does (ArmorLooks).
* src/main/resources/armor-looks.yml: MATERIAL_PIECE -> custom model data -> set, read by ArmorLooks.

Usage: python gen_armor_trims.py <served pack zip> <vanilla 1.21.1 jar>
Never remove a pattern once it is live: worn items carry the trim and would lose it.
"""
import colorsys
import io
import json
import os
import re
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ITEMS = os.path.normpath(os.path.join(HERE, '..'))
PACK = os.path.join(ITEMS, 'resourcepack')
DATAPACK = os.path.join(ITEMS, 'datapack', 'aetherion-armor')
TABLE = os.path.join(ITEMS, 'src', 'main', 'resources', 'armor-looks.yml')

MATERIALS = ('leather', 'chainmail', 'iron', 'golden', 'diamond', 'netherite')
PIECES = ('helmet', 'chestplate', 'leggings', 'boots')
LAYER = {'leather': 'leather', 'chainmail': 'chainmail', 'iron': 'iron', 'golden': 'gold', 'diamond': 'diamond',
         'netherite': 'netherite'}
SPECIAL = {'seraphine_': 'seraphine', 'helios_': 'helios'}
SUFFIX = 'aether'


def set_of(model):
    for prefix, key in SPECIAL.items():
        if model.startswith(prefix):
            return key
    m = re.match(r'^(.*?)_(helmet|chestplate|leggings|boots)(_(\d))?$', model)
    if not m:
        return None
    return m.group(1) + ('_' + m.group(4) if m.group(4) else '')


def pieces(pack):
    """(material, piece, cmd, set, icon texture) for every custom armor piece."""
    names = set(pack.namelist())
    out = []
    for mat in MATERIALS:
        for piece in PIECES:
            f = 'assets/minecraft/models/item/%s_%s.json' % (mat, piece)
            if f not in names:
                continue
            for o in json.loads(pack.read(f)).get('overrides', []):
                cmd = o.get('predicate', {}).get('custom_model_data')
                model = o['model'].split('/')[-1]
                if cmd is None or model.endswith('_vanilla') or 'trim' in model:
                    continue
                key = set_of(model)
                if not key:
                    continue
                mf = 'assets/minecraft/models/item/%s.json' % model
                tex = json.loads(pack.read(mf)).get('textures', {}) if mf in names else {}
                layer = tex.get('layer1') or tex.get('layer0')
                if tex.get('layer0', '').endswith('aetherion_blank') and tex.get('layer1'):
                    layer = tex['layer1']
                out.append((mat, piece, cmd, key, layer))
    return out


def icon(pack, texture):
    ns, path = texture.split(':') if ':' in texture else ('minecraft', texture)
    return Image.open(io.BytesIO(pack.read('assets/%s/textures/%s.png' % (ns, path)))).convert('RGBA')


def lum(c):
    return (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255.0


def palette(img):
    """Four tones dark -> light and an accent, from the icon's opaque pixels."""
    px = [p[:3] for p in img.getdata() if p[3] > 160]
    px = [p for p in px if lum(p) > 0.06]                    # drop the black outline
    px.sort(key=lum)
    n = len(px)

    def band(q):
        lo, hi = int(max(0, (q - 0.08) * n)), int(min(n, (q + 0.08) * n)) or 1
        sel = px[lo:hi] or px
        return tuple(round(sum(c[i] for c in sel) / len(sel)) for i in range(3))

    ramp = [band(q) for q in (0.12, 0.38, 0.64, 0.88)]

    def sat(c):
        return colorsys.rgb_to_hsv(*(v / 255.0 for v in c))[1]

    vivid = sorted((c for c in px if 0.3 < lum(c) < 0.9), key=sat, reverse=True)[:max(1, n // 10)]
    accent = tuple(round(sum(c[i] for c in vivid) / len(vivid)) for i in range(3)) if vivid else ramp[3]
    if sat(accent) < 0.22:
        accent = ramp[3]
    return ramp, accent


def ramp_at(ramp, t):
    t = max(0.0, min(1.0, t)) * (len(ramp) - 1)
    i = min(len(ramp) - 2, int(t))
    f = t - i
    return tuple(round(ramp[i][k] * (1 - f) + ramp[i + 1][k] * f) for k in range(3))


def mix(a, b, t):
    return tuple(round(a[k] * (1 - t) + b[k] * t) for k in range(3))


def repaint(base, ramp, accent, layer):
    src = base.convert('RGBA')
    ls = [lum(p) for p in src.getdata() if p[3] > 0]
    lo, hi = min(ls), max(ls)
    out = Image.new('RGBA', src.size, (0, 0, 0, 0))
    w, h = src.size
    for y in range(h):
        for x in range(w):
            p = src.getpixel((x, y))
            if p[3] == 0:
                continue
            t = (lum(p) - lo) / max(1e-6, hi - lo)
            out.putpixel((x, y), ramp_at(ramp, 0.08 + t * 0.9) + (255,))

    def put(x, y, c):
        if 0 <= x < w and 0 <= y < h and out.getpixel((x, y))[3]:
            out.putpixel((x, y), c + (255,))

    glow = mix(accent, (255, 255, 255), 0.35)
    dark = mix(ramp[0], (0, 0, 0), 0.35)
    if layer == 1:
        # chest gem with a lit corner, front of the body
        for (x, y) in ((23, 23), (24, 23), (23, 24), (24, 24)):
            put(x, y, accent)
        put(23, 23, glow)
        # rims of the shoulders and the collar in the accent
        for x in range(20, 28):
            put(x, 20, mix(accent, ramp[3], 0.5))
        for x in range(44, 48):
            put(x, 20, mix(accent, ramp[3], 0.5))
        # helmet: a dark visor slit under an accent brow, front of the head
        for x in range(9, 15):
            put(x, 12, dark)
            put(x, 10, mix(accent, ramp[2], 0.4))
        # boots: accent cuff at the top of the boot (legs front, lower part)
        for x in range(4, 8):
            put(x, 27, mix(accent, ramp[2], 0.5))
    else:
        # leggings: a belt around the waist with a buckle at the front
        for x in range(16, 40):
            put(x, 21, dark)
        for (x, y) in ((23, 21), (24, 21)):
            put(x, y, accent)
        # knee plates on the leg fronts
        for x in range(4, 8):
            put(x, 26, mix(accent, ramp[2], 0.45))
    return out


def main(served, jar_path):
    pack = zipfile.ZipFile(served)
    jar = zipfile.ZipFile(jar_path)
    rows = pieces(pack)
    sets = {}
    for mat, piece, cmd, key, layer in rows:
        sets.setdefault(key, {'mat': mat, 'icon': None})
        if piece == 'chestplate':
            sets[key]['mat'] = mat
            sets[key]['icon'] = layer
    for key, s in sets.items():
        if s['icon'] is None:
            s['icon'] = next(l for m, p, c, k, l in rows if k == key)

    tex_old = os.path.join(PACK, 'assets/aetherion/textures/trims/models/armor')
    tex_new = os.path.join(PACK, 'assets/aetherion/textures/trims/entity')
    for d in (tex_old, os.path.join(tex_new, 'humanoid'), os.path.join(tex_new, 'humanoid_leggings'),
              os.path.join(DATAPACK, 'data/aetherion/trim_pattern'), os.path.join(DATAPACK, 'data/aetherion/trim_material')):
        os.makedirs(d, exist_ok=True)

    sources = []
    for key in sorted(sets):
        s = sets[key]
        ramp, accent = palette(icon(pack, s['icon']))
        layer = LAYER[s['mat']]
        for n, name in ((1, key), (2, key + '_leggings')):
            base = Image.open(io.BytesIO(jar.read('assets/minecraft/textures/models/armor/%s_layer_%d.png' % (layer, n))))
            art = repaint(base, ramp, accent, n)
            art.save(os.path.join(tex_old, '%s_%s.png' % (name, SUFFIX)), optimize=True)
            sources.append('aetherion:trims/models/armor/%s_%s' % (name, SUFFIX))
        new_dir = {1: 'humanoid', 2: 'humanoid_leggings'}
        for n in (1, 2):
            src = os.path.join(tex_old, '%s_%s.png' % (key if n == 1 else key + '_leggings', SUFFIX))
            Image.open(src).save(os.path.join(tex_new, new_dir[n], '%s_%s.png' % (key, SUFFIX)), optimize=True)
            sources.append('aetherion:trims/entity/%s/%s_%s' % (new_dir[n], key, SUFFIX))
        with open(os.path.join(DATAPACK, 'data/aetherion/trim_pattern/%s.json' % key), 'w', encoding='utf-8') as f:
            json.dump({'asset_id': 'aetherion:' + key, 'decal': False,
                       'description': {'text': key.replace('_', ' ').title()},
                       'template_item': 'minecraft:barrier'}, f, indent=2)

    with open(os.path.join(DATAPACK, 'data/aetherion/trim_material/%s.json' % SUFFIX), 'w', encoding='utf-8') as f:
        # item_model_index below every vanilla threshold (0.1): no trim icon override ever matches, icons stay ours
        json.dump({'asset_name': SUFFIX, 'description': {'text': 'Aether', 'color': '#B294E6'},
                   'ingredient': 'minecraft:barrier', 'item_model_index': 0.05}, f, indent=2)
    with open(os.path.join(DATAPACK, 'pack.mcmeta'), 'w', encoding='utf-8') as f:
        json.dump({'pack': {'pack_format': 48, 'description': 'Aetherion worn armor (trim patterns)'}}, f, indent=2)

    atlas_dir = os.path.join(PACK, 'assets/minecraft/atlases')
    os.makedirs(atlas_dir, exist_ok=True)
    with open(os.path.join(atlas_dir, 'armor_trims.json'), 'w', encoding='utf-8') as f:
        json.dump({'sources': [{'type': 'single', 'resource': r} for r in sources]}, f, indent=1)

    with open(TABLE, 'w', encoding='utf-8') as f:
        f.write('# Generated by tools/gen_armor_trims.py: <MATERIAL>: { <custom model data>: <set> }\n')
        by = {}
        for mat, piece, cmd, key, _ in rows:
            by.setdefault('%s_%s' % (mat.upper().replace('GOLDEN', 'GOLDEN'), piece.upper()), []).append((cmd, key))
        for material in sorted(by):
            f.write('%s:\n' % material)
            for cmd, key in sorted(by[material]):
                f.write('  %d: %s\n' % (cmd, key))
    print('%d sets, %d pieces' % (len(sets), len(rows)))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
