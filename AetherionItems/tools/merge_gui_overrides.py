#!/usr/bin/env python3
"""Merge the Manager icon CustomModelData overrides (gui_icons.json) into a full pack's item models.

    python merge_gui_overrides.py <pack_root> <vanilla>

<pack_root> is the unzipped *full* texture pack (the served zip, not AetherionItems/resourcepack, which
only carries the HUD and GUI sources). <vanilla> is the 1.21.1 client jar or a folder holding its
assets/minecraft/models/item; a material the pack does not override yet starts from that vanilla model.

The overrides already in a model stay where they are; ours are appended in ascending CMD order. Minecraft
picks the last override whose custom_model_data is <= the item's value, so this is only safe while every
existing CMD on these materials is below ours. The script refuses to write if one is not. Running it again
replaces our entries instead of adding them twice.
"""
import json
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))


def vanilla_model(source, material):
    rel = 'assets/minecraft/models/item/%s.json' % material
    if source.endswith('.jar'):
        with zipfile.ZipFile(source) as jar:
            return json.loads(jar.read(rel))
    for path in (os.path.join(source, rel), os.path.join(source, material + '.json')):
        if os.path.exists(path):
            with open(path, encoding='utf-8') as f:
                return json.load(f)
    raise SystemExit('no vanilla model for %s in %s' % (material, source))


def main(pack, vanilla):
    with open(os.path.join(HERE, 'gui_icons.json'), encoding='utf-8') as f:
        wanted = json.load(f)
    ours = {cmd for entries in wanted.values() for cmd, _ in entries}
    lowest = min(ours)
    for material, entries in sorted(wanted.items()):
        path = os.path.join(pack, 'assets/minecraft/models/item/%s.json' % material)
        if os.path.exists(path):
            with open(path, encoding='utf-8-sig') as f:
                model = json.load(f)
        else:
            model = vanilla_model(vanilla, material)
        kept = [o for o in model.get('overrides', []) if o.get('predicate', {}).get('custom_model_data') not in ours]
        clash = [o['predicate']['custom_model_data'] for o in kept
                 if o.get('predicate', {}).get('custom_model_data', 0) >= lowest]
        if clash:
            raise SystemExit('%s already uses CMD %s, at or above ours (%d)' % (material, clash, lowest))
        added = [{'predicate': {'custom_model_data': cmd}, 'model': ref} for cmd, ref in sorted(entries)]
        model['overrides'] = kept + added
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(model, f, indent=2)
            f.write('\n')
        print('%-32s %d existing + %d gui' % (material, len(kept), len(added)))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
