#!/usr/bin/env python3
"""Make an unzipped served pack safe for clients newer than 1.21.1 (they join through ViaVersion).

    python newer_clients.py <pack_root>

1. The core shaders and the HUD carrier art are written for 1.21.1: newer clients changed the shader format, and
   a shader they cannot compile breaks other rendering (invisible water) and the pack's text. They move into
   the overlay ``v1_21_1`` (pack format 34 only). The base pack keeps a blank carrier font and a clear BLUE
   bar background, so a client no overlay below covers simply does not draw the HUD carrier.
2. Newer clients get the HUD through one overlay per text-shader format (HUD_VARIANTS): the vanilla text shader
   of that format (vanilla_text/, copied from the client jars) plus the HUD block of the 1.21.1 shader
   (between ``aetherion-hud:begin`` and ``aetherion-hud:end``, written by gen_carrier.py) and the real carrier
   font. Their boss bar background stays clear, so the carrier's own bar is invisible there too.
3. 1.21.4 dropped CustomModelData overrides in item models for item definitions (assets/minecraft/items).
   Each overridden material gets a definition built from its overrides; 1.21.1 ignores that folder.
   Materials whose vanilla definition does more than show one model (bow, compass, chest, ...) are skipped.
"""
import json
import os
import shutil
import sys

from PIL import Image

OVERLAY = 'v1_21_1'
FORMATS = [34, 34]
VANILLA_TEXT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'vanilla_text')
# overlay, pack formats it covers, vanilla shader (vanilla_text/<name>.vsh), core shader name, how ScreenSize
# reaches the shader. 1.21.2-1.21.4 still read core shader JSONs (1.21.5 ignores them and may leave ScreenSize
# at 0: the HUD block then draws at GUI scale). 1.21.6+ keep ScreenSize in the Globals uniform block.
HUD_VARIANTS = (
    ('ae_hud_42', (42, 55), '1.21.2', 'rendertype_text', 'uniform'),
    ('ae_hud_63', (63, 64), '1.21.6', 'rendertype_text', 'moj_import'),
    ('ae_hud_69', (69, 75), '1.21.9', 'rendertype_text', 'moj_import'),
    ('ae_hud_84', (84, 84), '26.1', 'rendertype_text', 'moj_import'),
    ('ae_hud_88', (88, 88), '26.2', 'text', 'moj_import'),
    ('ae_hud_97', (97, 97), '26.3', 'text', 'include'),
)
HUD_BEGIN, HUD_END = '// aetherion-hud:begin', '// aetherion-hud:end'
ONLY_1211 = (
    'assets/minecraft/shaders/core',
    'assets/aetherion/font/carrier.json',
    'assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png',
)
CARRIER_FONT = 'assets/aetherion/font/carrier.json'
CARRIER_BAR = 'assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png'
# Vanilla 1.21.4+ definitions of these are more than one model (use animation, needle, special renderer).
SKIP_PREDICATES = {'pull', 'pulling', 'charged', 'firework', 'angle', 'time', 'cast', 'blocking', 'throwing'}
SKIP_PARENTS = ('builtin/entity', 'minecraft:builtin/entity', 'item/chest', 'minecraft:item/template_banner',
                'item/template_banner')
LEATHER = ('leather_helmet', 'leather_chestplate', 'leather_leggings', 'leather_boots', 'leather_horse_armor')
LEATHER_TINT = [{'type': 'minecraft:dye', 'default': -6265536}]


def ns(model):
    return model if ':' in model else 'minecraft:' + model


def split_overlay(pack):
    for rel in ONLY_1211:
        src = os.path.join(pack, rel)
        if not os.path.exists(src):
            continue
        dst = os.path.join(pack, OVERLAY, rel)
        if os.path.isdir(dst):
            shutil.rmtree(dst)
        elif os.path.exists(dst):
            os.remove(dst)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.move(src, dst)
    over_font = os.path.join(pack, OVERLAY, CARRIER_FONT)
    if os.path.exists(over_font):
        with open(over_font, encoding='utf-8') as f:
            font = json.load(f)
        blank = {}
        for p in font.get('providers', []):
            if p.get('type') == 'space':
                blank.update(p.get('advances', {}))
            for row in p.get('chars', []):
                for ch in row:
                    if ch != '\u0000':
                        blank.setdefault(ch, 0)
        path = os.path.join(pack, CARRIER_FONT)
        with open(path, 'w', encoding='utf-8') as f:
            json.dump({'providers': [{'type': 'space', 'advances': blank}]}, f, indent=1)
    over_bar = os.path.join(pack, OVERLAY, CARRIER_BAR)
    if os.path.exists(over_bar):
        with Image.open(over_bar) as img:
            size = img.size
        Image.new('RGBA', size, (0, 0, 0, 0)).save(os.path.join(pack, CARRIER_BAR))


def once(text, old, new):
    if text.count(old) != 1:
        raise SystemExit('newer clients: expected exactly one %r in a vanilla text shader' % old)
    return text.replace(old, new)


def hud_shader(template, block, screen):
    """The vanilla text shader ``template`` drawing the HUD carrier like the 1.21.1 shader does."""
    s = template
    if screen == 'uniform':
        s = once(s, 'uniform int FogShape;\n', 'uniform int FogShape;\nuniform vec2 ScreenSize;\n')
    else:
        kw = '#include' if screen == 'include' else '#moj_import'
        line = kw + ' <minecraft:projection.glsl>\n'
        s = once(s, line, line + kw + ' <minecraft:globals.glsl>\n')
    s = once(s, '\nvoid main() {\n', '\n// Aetherion HUD carrier (AetherionItems/tools/gen_carrier.py + newer_clients.py).\n'
             + block + '\n\nvoid main() {\n')
    s = once(s, '    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n',
             '    vec3 pos = Position;\n'
             '    vec4 col = Color;\n'
             '    bool drop = aetherion_hud(pos, col);\n'
             '    gl_Position = drop ? vec4(2.0, 2.0, 2.0, 1.0) : ProjMat * ModelViewMat * vec4(pos, 1.0);\n')
    if 'vertexColor = Color' not in s:
        raise SystemExit('newer clients: no vertexColor = Color in a vanilla text shader')
    return s.replace('vertexColor = Color', 'vertexColor = col')


def hud_overlays(pack):
    """One overlay per newer text-shader format: HUD shader + the real carrier font."""
    src = os.path.join(pack, OVERLAY, 'assets/minecraft/shaders/core/rendertype_text.vsh')
    font = os.path.join(pack, OVERLAY, CARRIER_FONT)
    if not (os.path.exists(src) and os.path.exists(font)):
        return []
    with open(src, encoding='utf-8') as f:
        text = f.read()
    if HUD_BEGIN not in text or HUD_END not in text:
        raise SystemExit('newer clients: the 1.21.1 text shader has no aetherion-hud block (run gen_carrier.py)')
    block = text[text.index(HUD_BEGIN):text.index(HUD_END) + len(HUD_END)]
    entries = []
    for directory, (lo, hi), vanilla, name, screen in HUD_VARIANTS:
        root = os.path.join(pack, directory)
        shutil.rmtree(root, ignore_errors=True)
        core = os.path.join(root, 'assets/minecraft/shaders/core')
        os.makedirs(core)
        with open(os.path.join(VANILLA_TEXT, vanilla + '.vsh'), encoding='utf-8') as f:
            template = f.read()
        with open(os.path.join(core, name + '.vsh'), 'w', encoding='utf-8', newline='\n') as f:
            f.write(hud_shader(template, block, screen))
        json_src = os.path.join(VANILLA_TEXT, vanilla + '.json')
        if os.path.exists(json_src):
            with open(json_src, encoding='utf-8') as f:
                program = json.load(f)
            program['uniforms'].append({'name': 'ScreenSize', 'type': 'float', 'count': 2, 'values': [1.0, 1.0]})
            with open(os.path.join(core, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
                json.dump(program, f, indent=4)
        dst = os.path.join(root, CARRIER_FONT)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(font, dst)
        entries.append({'formats': [lo, hi], 'min_format': lo, 'max_format': hi, 'directory': directory})
    return entries


def write_mcmeta(pack, hud_entries):
    path = os.path.join(pack, 'pack.mcmeta')
    with open(path, encoding='utf-8-sig') as f:
        meta = json.load(f)
    # Packs that still support format 34 must give each overlay ``formats``; clients from 1.21.9 on also read
    # min_format / max_format.
    first = {'formats': FORMATS, 'min_format': FORMATS[0], 'max_format': FORMATS[1], 'directory': OVERLAY}
    meta['overlays'] = {'entries': [first] + hud_entries}
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(meta, f, indent=2)


def item_definitions(pack):
    models = os.path.join(pack, 'assets/minecraft/models/item')
    out_dir = os.path.join(pack, 'assets/minecraft/items')
    written = skipped = 0
    for name in sorted(os.listdir(models)):
        if not name.endswith('.json'):
            continue
        material = name[:-5]
        with open(os.path.join(models, name), encoding='utf-8-sig') as f:
            model = json.load(f)
        cmds = {}
        special = model.get('parent') in SKIP_PARENTS
        for o in model.get('overrides', []):
            pred = o.get('predicate', {})
            if SKIP_PREDICATES & set(pred):
                special = True
            if 'custom_model_data' in pred and set(pred) <= {'custom_model_data', 'trim_type'} \
                    and 'trim_type' not in pred:
                cmds[pred['custom_model_data']] = o['model']
        if not cmds:
            continue
        if special:
            skipped += 1
            continue
        tints = LEATHER_TINT if material in LEATHER else None

        def leaf(ref):
            node = {'type': 'minecraft:model', 'model': ns(ref)}
            if tints:
                node['tints'] = tints
            return node

        definition = {'model': {
            'type': 'minecraft:range_dispatch',
            'property': 'minecraft:custom_model_data',
            'index': 0,
            'fallback': leaf('item/' + material),
            'entries': [{'threshold': float(c), 'model': leaf(m)} for c, m in sorted(cmds.items())],
        }}
        os.makedirs(out_dir, exist_ok=True)
        with open(os.path.join(out_dir, name), 'w', encoding='utf-8') as f:
            json.dump(definition, f, indent=1)
        written += 1
    return written, skipped


def main(pack):
    split_overlay(pack)
    hud = hud_overlays(pack)
    write_mcmeta(pack, hud)
    written, skipped = item_definitions(pack)
    print('newer clients: overlay %s, %d HUD overlays (%s), %d item definitions (%d special materials left vanilla)'
          % (OVERLAY, len(hud), ', '.join(e['directory'] for e in hud), written, skipped))


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
