#!/usr/bin/env python3
"""Vanilla screens in Aetherion stone: the inventory, the crafting table and every other block screen, recoloured.

    python gen_vanilla_gui.py <vanilla 1.21.1 client jar>

Vanilla draws these screens from six greys. Each grey is swapped for a lavender stone tone, so every slot, arrow
and edge stays exactly where the client expects it. The panel stays light on purpose: the client writes the screen
titles ("Crafting", "Inventory") in its fixed dark grey, which must stay readable. Chest, hopper, dispenser and
shulker screens get the same stone: the themed plugin menus paint their opaque pages over it, and every menu with a
plain text title (and every real chest) reads as Aetherion too.

The green recipe-book button becomes the Aetherion recipe book (menu icon item/gui/recipes.png); clicking it opens
our recipe book (AetherionItems RecipeBookListener). Output goes to AetherionItems/resourcepack/assets/minecraft/
textures/gui, which build_served_pack.py copies into the served pack.
"""
import io
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'assets'))
GUI = os.path.join(PACK, 'minecraft', 'textures', 'gui')
RECIPES_ICON = os.path.join(PACK, 'aetherion', 'textures', 'item', 'gui', 'recipes.png')

CONTAINERS = (
    'inventory', 'crafting_table', 'crafter', 'furnace', 'blast_furnace', 'smoker', 'anvil', 'enchanting_table',
    'brewing_stand', 'grindstone', 'smithing', 'stonecutter', 'loom', 'cartography_table', 'beacon', 'villager',
    'horse', 'generic_54', 'hopper', 'dispenser', 'shulker_box',
)


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


# vanilla grey -> Aetherion stone
STONE = {
    hx('#c6c6c6'): hx('#bab1ca'),  # panel body
    hx('#ffffff'): hx('#efeaf7'),  # bevel light / slot lower-right lip
    hx('#555555'): hx('#685f80'),  # bevel shadow
    hx('#8b8b8b'): hx('#8a809f'),  # slot interior
    hx('#373737'): hx('#433c56'),  # slot upper-left shadow
    hx('#000000'): hx('#1a1623'),  # outline, player preview
}

# Recipe button, 20 x 18: our menu chrome (gen_gui_chrome.py) around the recipe book icon.
OUT = hx('#0a090e')
BEV_HI = hx('#45415a')
BEV_LO = hx('#110f17')
BODY = hx('#211f2a')
ACCENT = hx('#b06cff')


def recolor(im):
    im = im.convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a and (r, g, b) in STONE:
                px[x, y] = STONE[(r, g, b)] + (a,)
    return im


def button(highlighted):
    im = Image.new('RGBA', (20, 18), (0, 0, 0, 0))
    px = im.load()
    rim = ACCENT if highlighted else OUT
    for y in range(18):
        for x in range(20):
            edge = x in (0, 19) or y in (0, 17)
            corner = (x in (0, 19)) and (y in (0, 17))
            if corner:
                continue
            if edge:
                px[x, y] = rim + (255,)
            elif x == 1 or y == 1:
                px[x, y] = BEV_HI + (255,)
            elif x == 18 or y == 16:
                px[x, y] = BEV_LO + (255,)
            else:
                px[x, y] = BODY + (255,)
    icon = Image.open(RECIPES_ICON).convert('RGBA')
    if icon.size != (16, 16):
        icon = icon.resize((16, 16), Image.NEAREST)
    if highlighted:
        lit = icon.copy()
        lp = lit.load()
        for y in range(16):
            for x in range(16):
                r, g, b, a = lp[x, y]
                if a:
                    lp[x, y] = (min(255, r + 28), min(255, g + 28), min(255, b + 28), a)
        icon = lit
    im.alpha_composite(icon.crop((1, 1, 15, 15)), (3, 2))
    return im


def save(im, *parts):
    path = os.path.join(GUI, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    jar = zipfile.ZipFile(sys.argv[1])

    def vanilla(path):
        return Image.open(io.BytesIO(jar.read('assets/minecraft/textures/gui/' + path)))

    for name in CONTAINERS:
        save(recolor(vanilla('container/%s.png' % name)), 'container', name + '.png')
    save(recolor(vanilla('recipe_book.png')), 'recipe_book.png')
    sprites = [n for n in jar.namelist()
               if n.startswith('assets/minecraft/textures/gui/sprites/recipe_book/') and n.endswith('.png')]
    for n in sprites:
        leaf = n.rsplit('/', 1)[1]
        if leaf.startswith('button'):
            continue
        save(recolor(vanilla('sprites/recipe_book/' + leaf)), 'sprites', 'recipe_book', leaf)
        # The client reads a sprite's nine-slice .mcmeta from the pack that serves the png, so it travels along.
        if n + '.mcmeta' in jar.namelist():
            with open(os.path.join(GUI, 'sprites', 'recipe_book', leaf + '.mcmeta'), 'wb') as out:
                out.write(jar.read(n + '.mcmeta'))
    save(button(False), 'sprites', 'recipe_book', 'button.png')
    save(button(True), 'sprites', 'recipe_book', 'button_highlighted.png')
    print('vanilla gui: %d screens, recipe book + %d sprites' % (len(CONTAINERS), len(sprites)))


if __name__ == '__main__':
    main()
