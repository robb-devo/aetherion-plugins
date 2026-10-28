#!/usr/bin/env python3
"""Hub lobby glyphs: font aetherion:hub, drawn by AetherionPit's holograms, plates, title and chat card.

    python gen_hub_glyphs.py [<launcher aetherion-icon.png>]

Writes assets/aetherion/font/hub.json and assets/aetherion/textures/font/hub/*.png under AetherionItems/resourcepack.
The emblem is the launcher's app icon (launcheraetherion/public/aetherion-icon.png) scaled to 64 px; without the
argument the committed emblem.png is kept. The station icons reuse the menu icons (item/gui/*.png, gen_gui_icons.py)
so the hub reads like the menus; only the Discord bubble is drawn here. Codepoints are mirrored in
de.aetherion.pit.hub.HubGlyphs.
"""
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'assets', 'aetherion'))
FONT_DIR = os.path.join(PACK, 'textures', 'font', 'hub')

# Discord-blurple speech bubble, 16 x 16.
BUBBLE = [
    '................',
    '..############..',
    '.##############.',
    '################',
    '################',
    '####..####..####',
    '####..####..####',
    '################',
    '################',
    '.##############.',
    '..############..',
    '....####........',
    '...###..........',
    '..##............',
    '................',
    '................',
]
BLURPLE = (88, 101, 242, 255)
EDGE = (60, 69, 180, 255)

# (char, texture, ascent, height). Line glyphs sit like a capital letter; the board emblem hangs four lines deep.
GLYPHS = (
    ('', 'aetherion:font/hub/emblem.png', 7, 32),
    ('', 'aetherion:font/hub/emblem.png', 7, 8),
    ('', 'aetherion:item/gui/island.png', 7, 8),
    ('', 'aetherion:item/gui/section_combat.png', 7, 8),
    ('', 'aetherion:item/gui/craft.png', 7, 8),
    ('', 'aetherion:font/hub/discord.png', 7, 8),
    ('', 'aetherion:item/gui/shop.png', 7, 8),
)


def emblem(src):
    im = Image.open(src).convert('RGBA')
    box = im.split()[3].getbbox() or (0, 0, im.width, im.height)
    im = im.crop(box).resize((64, 64), Image.LANCZOS)
    im.save(os.path.join(FONT_DIR, 'emblem.png'))


def bubble():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(BUBBLE):
        for x, c in enumerate(row):
            if c != '#':
                continue
            edge = any(not (0 <= x + dx < 16 and 0 <= y + dy < 16) or BUBBLE[y + dy][x + dx] != '#'
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            im.putpixel((x, y), EDGE if edge else BLURPLE)
    im.save(os.path.join(FONT_DIR, 'discord.png'))


def main():
    os.makedirs(FONT_DIR, exist_ok=True)
    if len(sys.argv) > 1:
        emblem(sys.argv[1])
    if not os.path.isfile(os.path.join(FONT_DIR, 'emblem.png')):
        sys.exit('emblem.png missing: pass the launcher aetherion-icon.png once')
    bubble()
    providers = [{'type': 'bitmap', 'file': f, 'ascent': a, 'height': h, 'chars': [c]} for c, f, a, h in GLYPHS]
    with open(os.path.join(PACK, 'font', 'hub.json'), 'w', encoding='utf-8', newline='\n') as out:
        json.dump({'providers': providers}, out, indent=2)
        out.write('\n')
    print('hub font: %d glyphs' % len(GLYPHS))


if __name__ == '__main__':
    main()
