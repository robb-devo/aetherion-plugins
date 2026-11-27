#!/usr/bin/env python3
"""Renders inventory titles the way the 1.21.1 client lays them out, for checking menu art without a game client.

    python render_title.py <samples.txt> <out_dir> [vanilla 1.21.1 client jar]

samples.txt: one title per line, ``<name> <rows> <layout>\\t<adventure JSON>`` (ManagerChromeTest.samples writes it
with -Djournal.samples=<file>); <layout> is one word per row joined by '/', letters as in JournalPage.Leaf.layout,
and only places a stand-in item where a real menu would show one. Fonts come from AetherionItems/resourcepack plus
the vanilla jar; minecraft:default gets the kit reference the served pack adds (build_served_pack.py).

Layout rules mirrored from the client: a bitmap glyph's advance is its last non-empty column + 1 (scaled to the
provider's height) + 1; its top sits at title y + 7 - ascent, the title at GUI (8, 6); container titles have no
shadow. Items are drawn above the title, so the stand-ins go on last.
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.normpath(os.path.join(HERE, '..', 'resourcepack'))
DEFAULT_JAR = os.path.expandvars(r'%APPDATA%\ModrinthApp\meta\versions\1.21.1-0.19.5\1.21.1-0.19.5.jar')
NAMED = {
    'black': 0x000000, 'dark_blue': 0x0000AA, 'dark_green': 0x00AA00, 'dark_aqua': 0x00AAAA, 'dark_red': 0xAA0000,
    'dark_purple': 0xAA00AA, 'gold': 0xFFAA00, 'gray': 0xAAAAAA, 'dark_gray': 0x555555, 'blue': 0x5555FF,
    'green': 0x55FF55, 'aqua': 0x55FFFF, 'red': 0xFF5555, 'light_purple': 0xFF55FF, 'yellow': 0xFFFF55,
    'white': 0xFFFFFF,
}
MARGIN = 24


class Fonts:
    def __init__(self, jar_path):
        self.jar = zipfile.ZipFile(jar_path)
        self.cache = {}
        self.textures = {}

    def _read(self, rel):
        path = os.path.join(PACK, rel)
        if os.path.exists(path):
            with open(path, 'rb') as f:
                return f.read()
        return self.jar.read(rel)

    def providers(self, font_id):
        if font_id in self.cache:
            return self.cache[font_id]
        ns, path = font_id.split(':', 1) if ':' in font_id else ('minecraft', font_id)
        if font_id.endswith('include/unifont'):
            self.cache[font_id] = []
            return []
        data = json.loads(self._read('assets/%s/font/%s.json' % (ns, path)))
        out = []
        for p in data['providers']:
            if p['type'] == 'reference':
                out.extend(self.providers(p['id']))
            else:
                out.append(p)
        if font_id == 'minecraft:default':
            out.extend(self.providers('aetherion:kit'))
        self.cache[font_id] = out
        return out

    def texture(self, file_id):
        if file_id not in self.textures:
            ns, path = file_id.split(':', 1) if ':' in file_id else ('minecraft', file_id)
            self.textures[file_id] = Image.open(io.BytesIO(self._read('assets/%s/textures/%s' % (ns, path)))) \
                .convert('RGBA')
        return self.textures[file_id]

    def glyph(self, font_id, ch):
        """(advance, image or None, ascent) for one character."""
        for p in self.providers(font_id):
            if p['type'] == 'space':
                if ch in p['advances']:
                    return p['advances'][ch], None, 0
            elif p['type'] == 'bitmap':
                rows = p['chars']
                for j, row in enumerate(rows):
                    i = row.find(ch)
                    if i < 0:
                        continue
                    tex = self.texture(p['file'])
                    cw, chh = tex.width // len(row), tex.height // len(rows)
                    cell = tex.crop((i * cw, j * chh, (i + 1) * cw, (j + 1) * chh))
                    height = p.get('height', 8)
                    scale = height / chh
                    bbox = cell.getchannel('A').getbbox()
                    width = 0 if bbox is None else bbox[2]
                    advance = int(0.5 + width * scale) + 1
                    img = cell if scale == 1 else cell.resize((max(1, round(cw * scale)), height), Image.NEAREST)
                    return advance, img, p.get('ascent', 7)
        return 6, None, 0


def flatten(node, inherited=None):
    """Adventure JSON → [(text, colour, font)]."""
    style = dict(inherited or {'color': 0xFFFFFF, 'font': 'minecraft:default'})
    if isinstance(node, str):
        return [(node, style['color'], style['font'])]
    if isinstance(node, list):
        out = []
        for n in node:
            out.extend(flatten(n, style))
        return out
    if 'color' in node:
        c = node['color']
        style['color'] = int(c[1:], 16) if c.startswith('#') else NAMED.get(c, 0xFFFFFF)
    if 'font' in node:
        style['font'] = node['font']
    out = [(node.get('text', ''), style['color'], style['font'])]
    for child in node.get('extra', []):
        out.extend(flatten(child, style))
    return out


def tint(img, colour):
    r, g, b = (colour >> 16) & 255, (colour >> 8) & 255, colour & 255
    if (r, g, b) == (255, 255, 255):
        return img
    px = img.copy()
    data = [(p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, p[3]) for p in px.getdata()]
    px.putdata(data)
    return px


def render(fonts, rows, layout, title_json, scale=3):
    h = 114 + 18 * rows
    im = Image.new('RGBA', (176 + 2 * MARGIN, h + 2 * MARGIN), (30, 31, 34, 255))
    x = 8
    for text, colour, font in flatten(json.loads(title_json)):
        for ch in text:
            advance, glyph, ascent = fonts.glyph(font, ch)
            if glyph is not None:
                top = 6 + 7 - ascent
                im.alpha_composite(tint(glyph, colour), (x + MARGIN, top + MARGIN))
            x += advance
    # stand-in items where the layout puts something (the client draws items over the title)
    for r, word in enumerate(layout.split('/')[:rows]):
        for c, letter in enumerate(word[:9]):
            if letter in 'PSB':
                ix, iy = 8 + 18 * c + MARGIN, 18 + 18 * r + MARGIN
                for yy in range(4, 12):
                    for xx in range(4, 12):
                        if abs(xx - 7.5) + abs(yy - 7.5) <= 4.5:
                            im.putpixel((ix + xx, iy + yy), (120, 200, 230, 255) if letter == 'P'
                                        else (230, 170, 90, 255))
    return im.resize((im.width * scale, im.height * scale), Image.NEAREST)


def main(samples, out_dir, jar):
    fonts = Fonts(jar)
    os.makedirs(out_dir, exist_ok=True)
    names = []
    with open(samples, encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if not line.strip():
                continue
            head, title = line.split('\t', 1)
            parts = head.split(' ')
            name, rows = parts[0], int(parts[1])
            layout = parts[2] if len(parts) > 2 else ''
            render(fonts, rows, layout, title).save(os.path.join(out_dir, name + '.png'))
            names.append(name)
    print('rendered %d titles into %s' % (len(names), out_dir))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else DEFAULT_JAR)
