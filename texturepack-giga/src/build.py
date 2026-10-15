"""Build the GIGA pack: live zip + GIGA art + CMD wiring -> Aetherion_texturepack_GIGA_<date>.zip

    python build.py [--base LIVE.zip] [--out OUT.zip] [--extract DIR]

The live zip is only read. Every CMD/id stays as Java sets it; new entries only fill CMDs Java already
uses but the pack never drew (they showed a neighbour's icon or a vanilla item).
"""
import argparse
import datetime
import io
import json
import os
import sys
import zipfile
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from PIL import Image  # noqa: E402

import registry  # noqa: E402
import wiring  # noqa: E402

BASE = r'C:\Users\Robbi\IdeaProjects\Aetherion_texturepack.zip'
JAR = os.path.expandvars(r'%APPDATA%\.minecraft\versions\1.21.1\1.21.1.jar')
MODELS = 'assets/minecraft/models/item/'
TEX = 'assets/minecraft/textures/item/'
CIT_DIRS = ('assets/minecraft/citresewn/cit/aetherion_items/', 'assets/minecraft/optifine/cit/aetherion_items/')


def png_bytes(img):
    b = io.BytesIO()
    img.save(b, 'PNG', optimize=True)
    return b.getvalue()


def jdump(obj):
    return (json.dumps(obj, indent=2, ensure_ascii=True) + '\n').encode('utf-8')


def short(ref):
    ref = ref.split(':')[-1]
    return ref[5:] if ref.startswith('item/') else ref


class Pack:
    def __init__(self, base_zip, jar):
        self.f = OrderedDict()
        with zipfile.ZipFile(base_zip) as z:
            for info in z.infolist():
                if not info.filename.endswith('/'):
                    self.f[info.filename.replace('\\', '/')] = z.read(info)
        self.jar = zipfile.ZipFile(jar) if jar and os.path.exists(jar) else None
        self.base = dict(self.f)
        self.touched_materials = {}
        self.log = []

    # -------------------------------------------------------------- io
    def json(self, name):
        return json.loads(self.f[name].decode('utf-8-sig'))

    def has(self, name):
        return name in self.f

    def put(self, name, data):
        self.f[name] = data

    def vanilla_json(self, path):
        if not self.jar:
            raise SystemExit('vanilla jar needed for ' + path)
        return json.loads(self.jar.read(path).decode('utf-8'))

    def vanilla_has(self, path):
        if not self.jar:
            return False
        try:
            self.jar.getinfo(path)
            return True
        except KeyError:
            return False

    # -------------------------------------------------------------- models
    def material(self, mat):
        """The base item model for a vanilla material: the pack's, else a fresh copy of vanilla's."""
        if mat not in self.touched_materials:
            name = MODELS + mat + '.json'
            if self.has(name):
                j = self.json(name)
            else:
                j = self.vanilla_json(name)
                self.log.append(f'new base model {mat}.json (from vanilla 1.21.1)')
            j.setdefault('overrides', [])
            before = [o['predicate']['custom_model_data'] for o in j['overrides']
                      if 'custom_model_data' in o['predicate']]
            if before != sorted(before):
                self.log.append(f'{mat}: live CMD overrides were out of order {before} (last match wins) -> sorted')
            self.touched_materials[mat] = {'json': j, 'new': set()}
        return self.touched_materials[mat]

    def child_model(self, model, texture, kind):
        if kind == 'leather':
            body = {'parent': 'minecraft:item/generated',
                    'textures': {'layer0': 'minecraft:item/aetherion_blank', 'layer1': 'minecraft:item/' + texture}}
        elif kind == 'bow':
            body = {'parent': 'minecraft:item/bow', 'textures': {'layer0': 'minecraft:item/' + texture}}
        else:
            parent = {'handheld': 'minecraft:item/handheld', 'generated': 'minecraft:item/generated',
                      'rod': 'minecraft:item/handheld_rod'}[kind]
            body = {'parent': parent, 'textures': {'layer0': 'minecraft:item/' + texture}}
        self.put(MODELS + model + '.json', jdump(body))

    def wire(self, mat, cmd, model, texture=None, kind='generated'):
        texture = texture or model
        m = self.material(mat)
        ov = m['json']['overrides']
        target = 'minecraft:item/' + model
        for o in ov:
            p = o['predicate']
            if p.get('custom_model_data') == cmd and len(p) == 1:
                if short(o['model']) != model:
                    self.log.append(f'replace {mat} {cmd}: {short(o["model"])} -> {model}')
                o['model'] = target
                break
        else:
            ov.append({'predicate': {'custom_model_data': cmd}, 'model': target})
            self.log.append(f'wire {mat} {cmd} -> {model}')
        m['new'].add(cmd)
        if kind != 'keep':
            self.child_model(model, texture, kind)

    def finish_materials(self, trims=()):
        for mat, m in self.touched_materials.items():
            j = m['json']
            ov = j['overrides']
            plain = [o for o in ov if 'custom_model_data' not in o['predicate']]
            cmd = [o for o in ov if 'custom_model_data' in o['predicate']]
            if mat in trims:
                van = self.vanilla_json(MODELS + mat + '.json').get('overrides', [])
                have = {json.dumps(o['predicate'], sort_keys=True) for o in plain}
                add = [o for o in van if 'trim_type' in o['predicate']
                       and json.dumps(o['predicate'], sort_keys=True) not in have]
                if add:
                    self.log.append(f'{mat}: restored {len(add)} vanilla trim overrides')
                plain = add + plain
            values = {o['predicate']['custom_model_data'] for o in cmd}
            for c in sorted(m['new']):
                if c + 1 not in values:
                    cmd.append({'predicate': {'custom_model_data': c + 1}, 'model': f'minecraft:item/{mat}_vanilla'})
                    values.add(c + 1)
                    self.ensure_vanilla(mat)
            cmd.sort(key=lambda o: (o['predicate']['custom_model_data'], len(o['predicate'])))
            j['overrides'] = plain + cmd
            self.put(MODELS + mat + '.json', jdump(j))

    def ensure_vanilla(self, mat):
        name = MODELS + mat + '_vanilla.json'
        if self.has(name):
            return
        v = self.vanilla_json(MODELS + mat + '.json')
        v.pop('overrides', None)
        self.put(name, jdump(v))
        self.log.append(f'new sentinel model {mat}_vanilla.json')

    def leather_layer1(self, model):
        """Move a leather icon to layer1 over a blank layer0 so the dye no longer muddies it."""
        name = MODELS + model + '.json'
        j = self.json(name)
        tex = j.get('textures', {})
        if 'layer1' in tex or 'layer0' not in tex:
            return False
        art = tex['layer0']
        j['textures'] = {'layer0': 'minecraft:item/aetherion_blank', 'layer1': art}
        self.put(name, jdump(j))
        return True

    # -------------------------------------------------------------- output
    def write(self, out):
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            names = list(self.f)
            first = [n for n in ('pack.mcmeta', 'pack.png') if n in self.f]
            for n in first + [n for n in names if n not in first]:
                zi = zipfile.ZipInfo(n, date_time=(2026, 10, 16, 0, 0, 0))
                zi.compress_type = zipfile.ZIP_DEFLATED
                zi.external_attr = 0o644 << 16
                z.writestr(zi, self.f[n])

    def extract(self, root):
        BS = chr(92)
        for n, data in self.f.items():
            p = os.path.abspath(os.path.join(root, n)).replace('/', BS)
            if not p.startswith(BS * 2):
                p = BS * 2 + '?' + BS + p
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, 'wb') as fh:
                fh.write(data)


def fix_live_bases(pack):
    """Live base models for vanilla items: sort CMD overrides (last match wins, so an unsorted list
    shows the wrong icon) and point a base at vanilla's textures when the pack's are missing."""
    for name in list(pack.f):
        if not (name.startswith(MODELS) and name.endswith('.json')) or '/' in name[len(MODELS):]:
            continue
        mat = name[len(MODELS):-5]
        if not pack.vanilla_has(name):
            continue
        j = pack.json(name)
        ov = j.get('overrides', [])
        cmds = [o['predicate']['custom_model_data'] for o in ov if 'custom_model_data' in o['predicate']]
        changed = False
        if cmds != sorted(cmds):
            plain = [o for o in ov if 'custom_model_data' not in o['predicate']]
            cm = sorted((o for o in ov if 'custom_model_data' in o['predicate']),
                        key=lambda o: (o['predicate']['custom_model_data'], len(o['predicate'])))
            j['overrides'] = plain + cm
            pack.log.append(f'{mat}: live CMD overrides were out of order {cmds} (last match wins) -> sorted')
            changed = True
        for k, v in list(j.get('textures', {}).items()):
            ref = v.split(':')[-1]
            path = 'assets/minecraft/textures/' + ref + '.png'
            if not v.startswith('#') and not pack.has(path) and not pack.vanilla_has(path):
                van = pack.vanilla_json(name)
                pack.log.append(f'{mat}.json base texture {v} does not exist in 1.21.1 -> vanilla base restored')
                j['parent'] = van.get('parent', j.get('parent'))
                if 'textures' in van:
                    j['textures'] = van['textures']
                else:
                    j.pop('textures', None)
                changed = True
                break
        if changed:
            pack.put(name, jdump(j))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', default=BASE)
    ap.add_argument('--jar', default=JAR)
    ap.add_argument('--out', default=os.path.join(os.path.dirname(HERE),
                                                  f'Aetherion_texturepack_GIGA_{datetime.date(2026, 10, 16)}.zip'))
    ap.add_argument('--extract', default=None)
    ap.add_argument('--report', default=None)
    a = ap.parse_args()

    pack = Pack(a.base, a.jar)
    icons = registry.all_icons()

    # 1. art
    blank = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    pack.put(TEX + 'aetherion_blank.png', png_bytes(blank))
    rendered = {}
    for name, fn in icons.items():
        if name.startswith('basetest_'):
            continue
        img = fn().render()
        rendered[name] = img
        tex_name = wiring.TEXTURE_FILE.get(name, name)
        pack.put(TEX + tex_name + '.png', png_bytes(img))

    # 2. extra non-item assets (GUI / HUD / bars) produced by the chrome generators
    for path, data in wiring.extra_assets(pack.f['assets/minecraft/font/default.json']):
        pack.put(path, data)

    # 3. CMD wiring
    for mat, cmd, model, kind in wiring.WIRE:
        tex = wiring.TEXTURE_OF.get(model, model)
        pack.wire(mat, cmd, model, tex, kind)

    # 4. leather icons onto layer1 (every custom model the leather base files point at)
    moved = []
    for mat in wiring.LEATHER_MATERIALS:
        for o in pack.material(mat)['json']['overrides']:
            m = short(o['model'])
            if not m.endswith('_vanilla') and pack.has(MODELS + m + '.json') and pack.leather_layer1(m):
                moved.append(m)
    pack.log.append(f'leather icons moved to layer1 (dye no longer tints them): {len(moved)}')

    # 5. material files: sort, sentinels, restore vanilla trim overrides on armour
    pack.finish_materials(trims=wiring.TRIM_MATERIALS)
    for mat, parent in wiring.BASE_PARENT_FIX.items():
        name = MODELS + mat + '.json'
        j = pack.json(name)
        if j.get('parent') != parent:
            pack.log.append(f'{mat}.json parent {j.get("parent")} -> {parent} (vanilla)')
            j['parent'] = parent
            pack.put(name, jdump(j))
    fix_live_bases(pack)

    # 6. CIT copies (charms, crossbow frames, blueprint tools) use the same art
    for cit_name, art in wiring.CIT_SAME_ART.items():
        for d in CIT_DIRS:
            if pack.has(d + cit_name + '.png'):
                pack.put(d + cit_name + '.png', png_bytes(rendered[art]))
    for model, tex in wiring.RETEXTURE.items():
        name = MODELS + model + '.json'
        j = pack.json(name)
        j['textures'] = {k: ('minecraft:item/' + tex if k == 'layer0' else v) for k, v in j['textures'].items()}
        pack.put(name, jdump(j))
        pack.log.append(f'{model}.json now draws its own texture {tex}')

    # 7. every GIGA texture must be reachable from some model (otherwise it is wasted art)
    referenced = set()
    for n, data in pack.f.items():
        if n.startswith(MODELS) and n.endswith('.json'):
            for v in json.loads(data.decode('utf-8-sig')).get('textures', {}).values():
                referenced.add(short(v))
    cit_names = {n.split('/')[-1][:-4] for n in pack.f if '/cit/' in n and n.endswith('.png')}
    orphans = [n for n in rendered if wiring.TEXTURE_FILE.get(n, n) not in referenced and n not in cit_names]
    replaced = [n for n in rendered if (TEX + wiring.TEXTURE_FILE.get(n, n) + '.png') in pack.base]
    pack.log.append(f'GIGA textures: {len(rendered)} ({len(replaced)} replace live art, '
                    f'{len(rendered) - len(replaced)} new), unreferenced: {orphans}')

    pack.write(a.out)
    if a.extract:
        pack.extract(a.extract)
    if a.report:
        with open(a.report, 'w', encoding='utf-8') as fh:
            fh.write('\n'.join(pack.log) + '\n')
    print('wrote', a.out, len(pack.f), 'files')
    print('\n'.join(pack.log[-12:]))


if __name__ == '__main__':
    main()
