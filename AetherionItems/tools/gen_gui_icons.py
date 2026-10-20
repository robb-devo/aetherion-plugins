#!/usr/bin/env python3
"""Aetherion Manager menu emblems (32x32 item textures) + invisible GUI filler.

    python gen_gui_icons.py <pack_root>        e.g. python gen_gui_icons.py ../resourcepack

Every emblem is a medallion built exactly like the pack's charm items (``charm_*``): a bevelled metal
ring, a dark coloured disc and a small line pictogram in two or three tones. The ring is gold on live
buttons and dull pewter on locked or disabled ones; the disc colour groups the buttons. The Java side
puts these on the menu buttons with CustomModelData (ManagerIcons, GuiIcons); the base material keeps its
vanilla look everywhere else.

Writes:
    assets/aetherion/textures/item/gui/<icon>.png
    assets/aetherion/models/item/gui/<icon>.json
    gui_icons.json (next to this script)   material -> [(cmd, icon)] for the model-override merge
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from px_art import (AMBER, ASH, BONE, CRIMSON, EMBER, EMERALD, GOLD, ROSE, SKY, SLATE, STEEL, TEAL, VIOLET,  # noqa: E402
                    WHITE, Px, hx, ramp)

WOOD = ramp('#3a2010', '#6a3e1e', '#99632d', '#c48850', '#e0b07a', '#f6dcb0')
PEWTER = ramp('#1c1a22', '#3a3742', '#5e5a68', '#86828f', '#b2afba', '#dddbe3')
GREY_DISC = ramp('#121117', '#1e1c24', '#2a2832', '#3a3844', '#56535f', '#76737f')

# The charm medallion without its hanging loop. Ring: a outline, e dark, d shade, g mid, c light,
# b highlight. Disc: f rim, h fill, j shade toward the bottom right. Rows 6..25 of the 32x32 texture.
MEDALLION = [
    '............aaaaaaaa............',
    '..........aabcccccccaa..........',
    '.........abccccccccccca.........',
    '........abcccffffffcggca........',
    '.......abccffhhhhhhffggca.......',
    '.......accfhhhhhhhhhhfgda.......',
    '......abccfhhhhhhhhhhjgdca......',
    '......accfhhhhhhhhhhhhfdda......',
    '......accfhhhhhhhhhhhhjdda......',
    '.....abccfhhhhhhhhhhhhjddca.....',
    '.....acccfhhhhhhhhhhhhjddea.....',
    '......accfhhhhhhhhhhhhjdda......',
    '......accfhhhhhhhhhhhhjdda......',
    '......acccfhhhhhhhhhhjddea......',
    '.......acgfhhhhhhhhhhjdda.......',
    '.......acggfjhhhhhhjjddea.......',
    '........acgggfjjjjjdddea........',
    '.........acddddddddddea.........',
    '..........aacddddddeaa..........',
    '............aaaaaaaa............',
]
MEDALLION_TOP = 6
PICTO_X, PICTO_Y = 11, 11       # 10x10 pictogram box, centred on the disc

# Pictogram colours. Upper case and digits for metals, lower case for materials.
INK = {
    'W': WHITE,
    '1': STEEL[5], '2': STEEL[4], '3': STEEL[3], '4': STEEL[2],
    'G': GOLD[5], 'H': GOLD[4], 'I': GOLD[3], 'J': GOLD[2],
    'n': WOOD[4], 'm': WOOD[3], 'b': WOOD[2],
    'x': BONE[5], 'y': BONE[4], 'z': BONE[3],
    'R': CRIMSON[4], 'E': CRIMSON[3], 'F': CRIMSON[2],
    'g': EMERALD[4], 'h': EMERALD[3], 'i': EMERALD[2],
    'c': SKY[4], 'd': SKY[3], 'D': hx('#14141e'),
    'P': VIOLET[5], 'p': VIOLET[4], 'q': VIOLET[3], 'r': VIOLET[2],
    'A': AMBER[4], 'a': hx('#ffb84a'),
    's': ASH[4], 't': ASH[3], 'u': ASH[2],
    'k': hx('#988c8c'), 'K': hx('#73676a'),
    'v': hx('#b6f0ff'), 'w': hx('#52bede'),
}

P = {
    'manager': ['....HH....',
                '....GH....',
                '....GH....',
                '...GWWH...',
                'HGGWWWWGHI',
                'IHHWWWWIIJ',
                '...HWWJ...',
                '....HJ....',
                '....HJ....',
                '....JJ....'],
    'shop': ['....Pp....',
             '.W.PPpq...',
             '...Ppqq...',
             '..PPpqqr..',
             '..Ppppqr..',
             '..Pppqqr..',
             '..ppqqqr..',
             '...pqqr...',
             '...pqqr...',
             '....qr....'],
    'bazaar': ['....GH....',
               '.GGGGHHHI.',
               '.G..GH..I.',
               'G.G.GH.I.I',
               'GGG.GH.IJJ',
               '....GH....',
               '....GH....',
               '....GH....',
               '..GGGHHI..',
               '.HHHHIIIJ.'],
    'skills': ['.........1',
               '........12',
               '.......12.',
               '......12..',
               '..G..12...',
               '...G12....',
               '...bH.....',
               '..b..H....',
               '.b....I...',
               'H.........'],
    'auction': ['......GGH.',
                '.....G.GHI',
                '....GGGHHI',
                '...GGWGHI.',
                '..GGWGHHI.',
                '.GGGGHHI..',
                'GGGGHHI...',
                '.GHHHI....',
                '..HII.....',
                '..........'],
    'bestiary': ['..xxxxxx..',
                 '.xxxxxxxy.',
                 '.xxxxxxyy.',
                 '.x..xy..y.',
                 '.x..xy..z.',
                 '.xxxx.yyz.',
                 '..xxyyzz..',
                 '..x.yy.z..',
                 '...yyzz...',
                 '..........'],
    'collection': ['..........',
                   '..111.....',
                   '.1.221....',
                   '.....21...',
                   '......21..',
                   '.....b.21.',
                   '....b...1.',
                   '...b......',
                   '..b.......',
                   '.b........'],
    'pets': ['..WW..WW..',
             '..W2..W2..',
             'WW......WW',
             'W2......W2',
             '...WWWW...',
             '..WWWWW2..',
             '.WWWWWW22.',
             '.WWWWW222.',
             '..W22222..',
             '..........'],
    'journal': ['.FRRRRRR..',
                '.FRRRRRRx.',
                '.FRRGHRRx.',
                '.FRGWHIRx.',
                '.FRRHIRRx.',
                '.FRRRRRRx.',
                '.FRRRRRRx.',
                '.FEEEEEEx.',
                '.Fxxxxxxx.',
                '..FFFFFFF.'],
    'recipes': ['.bnnnnnnb.',
                '..xxxxxy..',
                '..xzzzxy..',
                '..xxxxxy..',
                '..xzzxxy..',
                '..xxxxxy..',
                '..xzzzxy..',
                '..xxxxxy..',
                '.bnnnnnnb.',
                '..........'],
    'island': ['....gg....',
               '...gghh...',
               '..ggghhi..',
               '...hhhi...',
               '....nb....',
               '.gggnbhhh.',
               '..mmmmmm..',
               '...mmmb...',
               '....mb....',
               '..........'],
    'guild': ['.bnnnnnnb.',
              '..RRRREE..',
              '..RRGGEE..',
              '..RGWHIE..',
              '..RRHIEE..',
              '..RRRREE..',
              '..RRREEE..',
              '..RR..EE..',
              '..R....E..',
              '..........'],
    'stats': ['..........',
              '.......GH.',
              '.......GI.',
              '....GH.GI.',
              '....GI.GI.',
              '.GH.GI.GI.',
              '.GI.GI.GI.',
              '.GI.GI.GI.',
              'WWWWWWWWW.',
              '..........'],
    'storage': ['..........',
                '..nnnnnn..',
                '.nmmmmmmb.',
                '.nmmmmmmb.',
                '.IIIGHIII.',
                '.nmmIJmmb.',
                '.nmmmmmmb.',
                '.nmmmmmmb.',
                '.bbbbbbbb.',
                '..........'],
    'loadouts': ['.22....33.',
                 '2222..3334',
                 '2222223334',
                 '.22222333.',
                 '.22223334.',
                 '.22223334.',
                 '.22233334.',
                 '.22333344.',
                 '.33334444.',
                 '..........'],
    'spawns': ['...RRRR...',
               '..RRRRRE..',
               '.RR..REEE.',
               '.RR..EEEF.',
               '.RRREEEEF.',
               '..RREEEF..',
               '...REEF...',
               '....EF....',
               '....F.....',
               '..........'],
    'craft': ['..........',
              '.nm.nm.nm.',
              '.mb.mb.mb.',
              '..........',
              '.nm.GH.nm.',
              '.mb.HI.mb.',
              '..........',
              '.nm.nm.nm.',
              '.mb.mb.mb.',
              '..........'],
    'anvil': ['..........',
              '........a.',
              '...a...a..',
              '..........',
              '.kkkkkkkkk',
              '..KKKKKKK.',
              '....KKK...',
              '....KKK...',
              '...KKKKK..',
              '..........'],
    'dev': ['....12....',
            '.1.1122.2.',
            '..111222..',
            '.11....22.',
            '111....223',
            '112....233',
            '.12....33.',
            '..222333..',
            '.2.2233.3.',
            '....33....'],
    'close': ['..........',
              '.WW....WW.',
              '.WWW..WW2.',
              '..WWWW22..',
              '...WW22...',
              '...WW22...',
              '..WW2222..',
              '.WW2..222.',
              '.W2....22.',
              '..........'],
    'locked': ['...ssss...',
               '..s....t..',
               '..s....t..',
               '.sssssttt.',
               '.sss..ttu.',
               '.sss..ttu.',
               '.ssssttuu.',
               '.sstttuuu.',
               '.tttuuuuu.',
               '..........'],
    'next': ['..........',
             '...W......',
             '...WW.....',
             '...WWW....',
             '...WWWW...',
             '...Wggg...',
             '...ggg....',
             '...gg.....',
             '...g......',
             '..........'],
    'later': ['.sssssst..',
              '..s...t...',
              '..s...t...',
              '...s.t....',
              '....st....',
              '....ts....',
              '...t..u...',
              '..t.tu.u..',
              '.ttttuuuu.',
              '..........'],
    'back': ['..........',
             '...G......',
             '..GG......',
             '.GGGGGGH..',
             'GGGGGGGHH.',
             '.HH....HI.',
             '..H....HI.',
             '.......HI.',
             '.....HHI..',
             '..........'],
    'prev': ['..........',
             '...G...G..',
             '..GH..GH..',
             '.GH..GH...',
             'GH..GH....',
             'HI..HI....',
             '.HI..HI...',
             '..HI..HI..',
             '...I...I..',
             '..........'],
    'fish': ['..........',
             '..........',
             '..........',
             '....v...w.',
             '..DwwvDw..',
             '.wwwwww...',
             '..wwww.w..',
             '....w...w.',
             '..........',
             '..........'],
    'axe': ['..........',
            '....11....',
            '...11.1...',
            '..21.1..b.',
            '..211..b..',
            '...1..b...',
            '.....b....',
            '....b.....',
            '...b......',
            '..b.......'],
}


def mirror(rows):
    return [r[::-1] for r in rows]


def dim(rows):
    """Disabled look: every metal tone becomes pewter, keeping the light/shade structure."""
    swap = {'G': 's', 'H': 's', 'I': 't', 'J': 'u', 'W': 's'}
    return [''.join(swap.get(ch, ch) for ch in r) for r in rows]


def emblem(picto, disc, metal=GOLD):
    p = Px(32, 32)
    pal = {'a': metal[0], 'e': metal[1], 'd': metal[2], 'g': metal[3], 'c': metal[4], 'b': metal[5],
           'f': disc[0], 'h': disc[1], 'j': disc[2]}
    p.sprite(MEDALLION, pal, 0, MEDALLION_TOP)
    if len(picto) != 10 or any(len(r) != 10 for r in picto):
        raise SystemExit('pictogram is not 10x10: %r' % (picto,))
    p.sprite(picto, INK, PICTO_X, PICTO_Y)
    return p.image()


def empty():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


# icon -> (CustomModelData, vanilla material the button already uses, pictogram, disc, metal)
ICONS = {
    'manager': (7701, 'nether_star', P['manager'], VIOLET, GOLD),
    'shop': (7702, 'amethyst_shard', P['shop'], TEAL, GOLD),
    'bazaar': (7703, 'chest', P['bazaar'], EMERALD, GOLD),
    'skills': (7704, 'netherite_scrap', P['skills'], CRIMSON, GOLD),
    'auction': (7705, 'gold_block', P['auction'], VIOLET, GOLD),
    'bestiary': (7706, 'bone', P['bestiary'], SLATE, GOLD),
    'collection': (7707, 'iron_pickaxe', P['collection'], SKY, GOLD),
    'pets': (7708, 'lead', P['pets'], ROSE, GOLD),
    'journal': (7709, 'writable_book', P['journal'], TEAL, GOLD),
    'recipes': (7710, 'knowledge_book', P['recipes'], EMBER, GOLD),
    'island': (7711, 'grass_block', P['island'], SKY, GOLD),
    'guild': (7712, 'yellow_banner', P['guild'], AMBER, GOLD),
    'stats': (7713, 'experience_bottle', P['stats'], SLATE, GOLD),
    'storage': (7714, 'ender_chest', P['storage'], VIOLET, GOLD),
    'loadouts': (7715, 'armor_stand', P['loadouts'], CRIMSON, GOLD),
    'spawns': (7716, 'compass', P['spawns'], TEAL, GOLD),
    'craft': (7717, 'crafting_table', P['craft'], SKY, GOLD),
    'anvil': (7718, 'anvil', P['anvil'], EMBER, GOLD),
    'dev': (7719, 'command_block', P['dev'], ROSE, GOLD),
    'close': (7720, 'barrier', P['close'], CRIMSON, GOLD),
    'locked': (7721, 'gray_dye', P['locked'], GREY_DISC, PEWTER),
    'next': (7722, 'lime_dye', P['next'], EMERALD, GOLD),
    'later': (7723, 'light_gray_stained_glass_pane', P['later'], GREY_DISC, PEWTER),
    # menu navigation, shared by every chrome menu (GuiIcons in Java)
    'back': (7724, 'arrow', P['back'], SLATE, GOLD),
    'prev': (7725, 'arrow', P['prev'], SLATE, GOLD),
    'next_page': (7726, 'arrow', mirror(P['prev']), SLATE, GOLD),
    'prev_off': (7727, 'gray_stained_glass_pane', dim(P['prev']), GREY_DISC, PEWTER),
    'next_off': (7728, 'gray_stained_glass_pane', dim(mirror(P['prev'])), GREY_DISC, PEWTER),
    'section_combat': (7729, 'black_stained_glass_pane', P['skills'], CRIMSON, GOLD),
    'section_gathering': (7730, 'black_stained_glass_pane', P['axe'], EMERALD, GOLD),
    'section_fishing': (7731, 'black_stained_glass_pane', P['fish'], SKY, GOLD),
}
# Fillers: the same CMD on every pane colour draws nothing, so the chrome under it shows.
FILLER_CMD = 7700
FILLERS = ('gray_stained_glass_pane', 'black_stained_glass_pane', 'light_gray_stained_glass_pane')


def main(root):
    tex = os.path.join(root, 'assets/aetherion/textures/item/gui')
    mdl = os.path.join(root, 'assets/aetherion/models/item/gui')
    os.makedirs(tex, exist_ok=True)
    os.makedirs(mdl, exist_ok=True)
    overrides = {}
    for name, (cmd, material, picto, disc, metal) in ICONS.items():
        emblem(picto, disc, metal).save(os.path.join(tex, name + '.png'), optimize=True)
        with open(os.path.join(mdl, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'aetherion:item/gui/' + name}}, f,
                      indent=2)
            f.write('\n')
        overrides.setdefault(material, []).append([cmd, 'aetherion:item/gui/' + name])
    empty().save(os.path.join(tex, 'empty.png'), optimize=True)
    with open(os.path.join(mdl, 'empty.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'aetherion:item/gui/empty'}}, f, indent=2)
        f.write('\n')
    for material in FILLERS:
        overrides.setdefault(material, []).append([FILLER_CMD, 'aetherion:item/gui/empty'])
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gui_icons.json'), 'w', encoding='utf-8',
              newline='\n') as f:
        json.dump(overrides, f, indent=2, sort_keys=True)
        f.write('\n')
    print('%d icons + filler written' % len(ICONS))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'out')
