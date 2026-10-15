"""Validate a built pack against the live one.

    python validate.py LIVE.zip GIGA.zip [--jar 1.21.1.jar] [--out report.txt]

Checks: JSON parses, every model / parent / texture reference resolves (pack or vanilla jar), CMD
overrides ascending, PNGs decode, pack_format 34, font JSON intact. Then it resolves every
(material, CMD) the way the 1.21.1 client does (last matching override wins, custom_model_data is
">=") in both packs and lists what each item now shows.
"""
import argparse
import hashlib
import io
import json
import os
import sys
import zipfile

from PIL import Image

JAR = os.path.expandvars(r'%APPDATA%\.minecraft\versions\1.21.1\1.21.1.jar')
MODELS = 'assets/minecraft/models/'
LOCKED = {('netherite_sword', 3301): 'Gravwell Cleaver', ('netherite_sword', 3304): 'Blossom Blade'}


def load(path):
    with zipfile.ZipFile(path) as z:
        return {i.filename: z.read(i) for i in z.infolist() if not i.filename.endswith('/')}


def ref_path(ref, kind):
    ns, _, p = ref.partition(':') if ':' in ref else ('minecraft', '', ref)
    return f'assets/{ns}/{kind}/{p}.' + ('json' if kind == 'models' else 'png')


class Res:
    def __init__(self, files, jar):
        self.f = files
        self.jar = jar

    def has(self, path):
        return path in self.f or (self.jar is not None and path in self.jar)

    def json(self, path):
        data = self.f.get(path)
        if data is None and self.jar is not None:
            data = self.jar.get(path)
        return None if data is None else json.loads(data.decode('utf-8-sig'))

    def textures(self, model_ref, depth=0):
        j = self.json(ref_path(model_ref, 'models'))
        if j is None or depth > 10:
            return {}
        tex = {}
        if 'parent' in j:
            tex.update(self.textures(j['parent'], depth + 1))
        tex.update(j.get('textures', {}))
        return tex

    def resolve(self, mat, cmd):
        """Model the client shows for an item of `mat` with CustomModelData `cmd` (no other predicates)."""
        j = self.json(f'{MODELS}item/{mat}.json')
        if j is None:
            return f'minecraft:item/{mat}'
        hit = None
        for o in j.get('overrides', []):
            p = o['predicate']
            if set(p) == {'custom_model_data'} and cmd >= p['custom_model_data']:
                hit = o['model']
        return hit or f'minecraft:item/{mat}'

    def look(self, model_ref):
        """(model, texture refs, hash of the texture bytes) for comparing what is actually drawn."""
        tex = self.textures(model_ref)
        h = hashlib.sha1()
        names = []
        for k in sorted(tex):
            v = tex[k]
            if v.startswith('#'):
                continue
            names.append(v.split('/')[-1])
            p = ref_path(v, 'textures')
            h.update(self.f.get(p) or (self.jar.get(p) if self.jar and p in self.jar else b'?'))
        return model_ref.split('/')[-1], '+'.join(names), h.hexdigest()[:10]


def check(files, jar, out):
    res = Res(files, jar)
    errs = []
    for n, data in files.items():
        if n.endswith('.json') or n.endswith('.mcmeta'):
            try:
                json.loads(data.decode('utf-8-sig'))
            except Exception as e:
                errs.append(f'BAD JSON {n}: {e}')
        if n.endswith('.png'):
            try:
                Image.open(io.BytesIO(data)).load()
            except Exception as e:
                errs.append(f'BAD PNG {n}: {e}')
    meta = json.loads(files['pack.mcmeta'].decode('utf-8-sig'))
    if meta['pack']['pack_format'] != 34:
        errs.append('pack_format is not 34')
    item_models = [n for n in files if n.startswith(MODELS + 'item/') and n.endswith('.json')]
    for n in item_models:
        j = json.loads(files[n].decode('utf-8-sig'))
        if 'parent' in j and not res.has(ref_path(j['parent'], 'models')) and not j['parent'].startswith(('builtin/', 'minecraft:builtin/')):
            errs.append(f'{n}: parent {j["parent"]} missing')
        for k, v in j.get('textures', {}).items():
            if not v.startswith('#') and not res.has(ref_path(v, 'textures')):
                errs.append(f'{n}: texture {k}={v} missing')
        cmds = []
        for o in j.get('overrides', []):
            if not res.has(ref_path(o['model'], 'models')):
                errs.append(f'{n}: override model {o["model"]} missing')
            if 'custom_model_data' in o['predicate']:
                cmds.append(o['predicate']['custom_model_data'])
        if cmds != sorted(cmds):
            errs.append(f'{n}: CMD overrides not ascending {cmds}')
    out.append(f'files {len(files)}  item models {len(item_models)}  errors {len(errs)}')
    out.extend('  ' + e for e in errs)
    return res, errs


def glyph_advances(files, font_path):
    """{char: (advance, height)} for every bitmap glyph of a font, measured from its PNG the way the
    client does (rightmost non-transparent column + 1, scaled by height / cell height)."""
    out = {}
    data = files.get(font_path)
    if data is None:
        return out
    font = json.loads(data.decode('utf-8-sig'))
    for p in font.get('providers', []):
        if p.get('type') != 'bitmap':
            continue
        tex = files.get(ref_path(p['file'].replace('.png', ''), 'textures'))
        if tex is None:
            continue
        im = Image.open(io.BytesIO(tex)).convert('RGBA')
        rows = p['chars']
        cw, ch = im.width // max(len(r) for r in rows), im.height // len(rows)
        px = im.load()
        for j, row in enumerate(rows):
            for i, c in enumerate(row):
                if c in ('\x00', ' '):
                    continue
                right = -1
                for x in range(cw):
                    if any(px[i * cw + x, j * ch + y][3] for y in range(ch)):
                        right = x
                out[(p['file'], c)] = (right + 1, p.get('height', 8), p.get('ascent'))
    return out


def check_fonts(live_files, giga_files, out, errs):
    for fp in ('assets/minecraft/font/default.json', 'assets/aetherion/font/hud.json'):
        a, b = glyph_advances(live_files, fp), glyph_advances(giga_files, fp)
        bad = [k for k in a if a[k] != b.get(k)]
        out.append(f'  {fp}: {len(a)} bitmap glyphs, advance/height/ascent changed: {len(bad)}')
        for k in bad[:20]:
            out.append(f'    {k[0]} U+{ord(k[1]):04X} {a[k]} -> {b.get(k)}')
        errs.extend(f'glyph contract changed {k}' for k in bad)


def pairs(*packs):
    seen = set()
    for res in packs:
        for n, data in res.f.items():
            if n.startswith(MODELS + 'item/') and n.endswith('.json'):
                j = json.loads(data.decode('utf-8-sig'))
                mat = n.split('/')[-1][:-5]
                for o in j.get('overrides', []):
                    c = o['predicate'].get('custom_model_data')
                    if c is not None and set(o['predicate']) == {'custom_model_data'}:
                        seen.add((mat, c))
    return sorted(seen)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('live')
    ap.add_argument('giga')
    ap.add_argument('--jar', default=JAR)
    ap.add_argument('--out', default=None)
    ap.add_argument('--java', default=None, help='extra "material cmd" lines (Java sites) to resolve')
    a = ap.parse_args()
    jar = load(a.jar) if a.jar and os.path.exists(a.jar) else None
    out = []
    out.append('== GIGA checks')
    live_files, giga_files = load(a.live), load(a.giga)
    giga, errs = check(giga_files, jar, out)
    live = Res(live_files, jar)
    for p in ('assets/minecraft/font/default.json', 'assets/aetherion/font/hud.json'):
        if live_files.get(p) != giga_files.get(p):
            out.append(f'  NOTE font file changed: {p}')
    check_fonts(live_files, giga_files, out, errs)
    extra = []
    if a.java and os.path.exists(a.java):
        for line in open(a.java, encoding='utf-8'):
            parts = line.split()
            if len(parts) >= 2 and parts[1].isdigit():
                extra.append((parts[0].lower(), int(parts[1])))
    out.append('')
    out.append('== What each (material, CMD) shows: live -> GIGA')
    changed = same = 0
    for mat, c in sorted(set(pairs(live, giga)) | set(extra)):
        lm, ln, lh = live.look(live.resolve(mat, c))
        gm, gn, gh = giga.look(giga.resolve(mat, c))
        tag = ''
        if (mat, c) in LOCKED:
            tag = '  LOCKED ' + LOCKED[(mat, c)] + (' ok' if (lm, ln, lh) == (gm, gn, gh) else ' CHANGED!')
            if (lm, ln, lh) != (gm, gn, gh):
                errs.append(f'LOCKED {LOCKED[(mat, c)]} changed')
        if (lm, lh) == (gm, gh):
            same += 1
            if tag:
                out.append(f'  {mat:<22}{c:>6}  {lm}{tag}')
            continue
        changed += 1
        art = 'art' if lm == gm else 'model'
        out.append(f'  {mat:<22}{c:>6}  {lm} ({ln}) -> {gm} ({gn})  [{art}]{tag}')
    out.append(f'unchanged {same}, changed {changed}')
    text = '\n'.join(out)
    if a.out:
        with open(a.out, 'w', encoding='utf-8') as fh:
            fh.write(text + '\n')
    print(text[-6000:] if len(text) > 6000 else text)
    sys.exit(1 if errs else 0)


if __name__ == '__main__':
    main()
