"""Before/after sheet: what an item showed with the live pack vs the GIGA pack (resolved like the client).

    python before_after.py LIVE.zip GIGA.zip out.png
"""
import io
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

from validate import Res, load, ref_path, JAR  # noqa: E402
from gui_preview import compose  # noqa: E402

HEROES = [
    ('Worldbite (Mythic)', 'netherite_sword', 4105),
    ('Solstice (Mythic, Helios)', 'netherite_sword', 4205),
    ('Severance (Legendary)', 'iron_sword', 3340),
    ('Aetherblade (Mythic)', 'netherite_sword', 2202),
    ('Stormcaller Maul', 'mace', 3302),
    ('Vanguard Edge III', 'netherite_sword', 2813),
    ('Hollow Heart Cuirass', 'netherite_chestplate', 2342),
    ('Dawnbearer Crown', 'netherite_helmet', 4201),
    ('Kintsugi Veil', 'leather_helmet', 3341),
    ('Mythic Combat Chest', 'netherite_chestplate', 2172),
    ('Grove Charm (T2)', 'apple', 3108),
    ('Dungeon Core III', 'nether_star', 2503),
]


def icon(res, mat, cmd):
    model = res.resolve(mat, cmd)
    tex = res.textures(model)
    layers = [tex[k] for k in ('layer0', 'layer1') if k in tex]
    out = None
    for ref in layers:
        p = ref_path(ref, 'textures')
        data = res.f.get(p) or (res.jar.get(p) if res.jar else None)
        if data is None:
            continue
        im = Image.open(io.BytesIO(data)).convert('RGBA')
        if im.height > im.width:
            im = im.crop((0, 0, im.width, im.width))
        if 'aetherion_blank' in ref:
            continue
        im = im.resize((96, 96), Image.NEAREST)
        out = im if out is None else Image.alpha_composite(out, im)
    if out is None:
        out = Image.new('RGBA', (96, 96), (0, 0, 0, 0))
    return out, model.split('/')[-1]


def slot(im, s=96):
    tile = Image.new('RGBA', (s + 8, s + 8), (139, 139, 139, 255))
    d = ImageDraw.Draw(tile)
    d.line((0, 0, s + 7, 0), fill=(55, 55, 55), width=4)
    d.line((0, 0, 0, s + 7), fill=(55, 55, 55), width=4)
    d.line((0, s + 7, s + 7, s + 7), fill=(255, 255, 255), width=4)
    d.line((s + 7, 0, s + 7, s + 7), fill=(255, 255, 255), width=4)
    tile.alpha_composite(im, (4, 4))
    return tile


def main():
    live_zip, giga_zip, out = sys.argv[1], sys.argv[2], sys.argv[3]
    jar = load(JAR) if os.path.exists(JAR) else None
    lf, gf = load(live_zip), load(giga_zip)
    live, giga = Res(lf, jar), Res(gf, jar)
    font = ImageFont.load_default()
    cols = 4
    cw, ch = 2 * 104 + 30, 104 + 34
    rows = (len(HEROES) + cols - 1) // cols
    gui_h = 300
    W = cols * cw + 20
    H = 40 + rows * ch + gui_h + 20
    sheet = Image.new('RGBA', (W, H), (20, 18, 26, 255))
    d = ImageDraw.Draw(sheet)
    d.text((12, 10), 'Aetherion GIGA pack - before (live) / after (GIGA). Wrong-icon "before" = what the client really drew.',
           fill=(235, 228, 255), font=font)
    for i, (label, mat, cmd) in enumerate(HEROES):
        x, y = 10 + (i % cols) * cw, 34 + (i // cols) * ch
        a, am = icon(live, mat, cmd)
        b, bm = icon(giga, mat, cmd)
        sheet.alpha_composite(slot(a), (x, y))
        sheet.alpha_composite(slot(b), (x + 104 + 14, y))
        d.text((x + 106, y + 46), '>', fill=(255, 220, 120), font=font)
        d.text((x, y + 108), label, fill=(240, 232, 255), font=font)
        d.text((x, y + 120), f'{mat} {cmd}: {am} -> {bm}'[:44], fill=(160, 150, 185), font=font)
    # Manager chrome strip
    gy = 34 + rows * ch + 6
    items = {}
    names = [h[1:] for h in HEROES]
    for k, (mat, cmd) in enumerate(names[:9]):
        items[('c', 9 + k)] = icon(giga, mat, cmd)[0].resize((32, 32), Image.NEAREST)
    lgui = Image.open(io.BytesIO(lf['assets/minecraft/textures/font/gui_aetherion_manager.png'])).convert('RGBA')
    ggui = Image.open(io.BytesIO(gf['assets/minecraft/textures/font/gui_aetherion_manager.png'])).convert('RGBA')
    la = compose(lgui, 6, items, scale=2).crop((0, 0, 412, gui_h - 10))
    gb = compose(ggui, 6, items, scale=2).crop((0, 0, 412, gui_h - 10))
    sheet.alpha_composite(la, (10, gy))
    sheet.alpha_composite(gb, (10 + 412 + 30, gy))
    d.text((10 + 412 + 8, gy + 120), '>', fill=(255, 220, 120), font=font)
    sheet.save(out)
    print(out)


if __name__ == '__main__':
    main()
