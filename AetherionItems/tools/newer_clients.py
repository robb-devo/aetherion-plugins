#!/usr/bin/env python3
"""Make an unzipped served pack safe for clients newer than 1.21.1 (they join through ViaVersion).

    python newer_clients.py <pack_root>

1. The core shaders and the HUD carrier art only work on 1.21.1: newer clients changed the shader format, and
   a shader they cannot compile breaks other rendering (invisible water) and the pack's text. They move into
   the overlay ``v1_21_1`` (pack format 34 only). The base pack keeps a blank carrier font and a clear BLUE
   bar background, so on a newer client the HUD carrier is simply not drawn.
2. 1.21.4 dropped CustomModelData overrides in item models for item definitions (assets/minecraft/items).
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


def write_mcmeta(pack):
    path = os.path.join(pack, 'pack.mcmeta')
    with open(path, encoding='utf-8-sig') as f:
        meta = json.load(f)
    meta['overlays'] = {'entries': [{'formats': FORMATS, 'directory': OVERLAY}]}
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
    write_mcmeta(pack)
    written, skipped = item_definitions(pack)
    print('newer clients: overlay %s, %d item definitions (%d special materials left vanilla)'
          % (OVERLAY, written, skipped))


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
