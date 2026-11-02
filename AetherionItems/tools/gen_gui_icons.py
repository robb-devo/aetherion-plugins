#!/usr/bin/env python3
"""Aetherion Manager and menu icons (16x16 item textures) + the invisible GUI filler.

    python gen_gui_icons.py <pack_root> [preview.png]     e.g. python gen_gui_icons.py ../resourcepack

One object per button, drawn at the chest-menu chrome's own pixel size (16 texels on a 16 px slot), lit
from the top left with a dark outline tinted toward the object's colour. Each tab's colour is the accent of
the menu it opens (THEMES in gen_gui_chrome.py), so the icon, the menu frame and the menu title agree.

Lockable Manager tabs get three more looks of the same object, so a player sees what a tab will be before
it opens: ``<tab>_locked`` (stone grey, a small padlock), ``<tab>_next`` (full colour, a green arrow badge)
and ``<tab>_later`` (a dark silhouette). The Java side (ManagerIcons, GuiIcons) puts them on with
CustomModelData; the base material keeps its vanilla look everywhere else.

Writes:
    assets/aetherion/textures/item/gui/<icon>.png
    assets/aetherion/models/item/gui/<icon>.json
    gui_icons.json (next to this script)   material -> [(cmd, icon)] for merge_gui_overrides.py
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from px_art import (AMBER, BONE, CRIMSON, EMBER, GOLD, STEEL, Px, edge, grow, hx, mix, ramp,  # noqa: E402
                    shift, shrink)

N = 16
OUT = hx('#0a090e')
WOOD = ramp('#2a160a', '#5a3418', '#8a5428', '#b47a3e', '#d8a464', '#f2d2a0')
PAPER = ramp('#2a2018', '#6a5640', '#a88c68', '#d2bc94', '#ecdcb8', '#fff6e0')
FLAME = ramp('#3a0e04', '#8a2a08', '#d2541a', '#f28a2a', '#ffc04a', '#fff2a8')
LEAF = ramp('#0c2410', '#1d4a20', '#2f7a30', '#4fa84a', '#86d468', '#d2f6a8')
GRASS = ramp('#0e2410', '#1f4a1c', '#3a7a2a', '#5aa83c', '#8ad45a', '#ccf29a')
DIRT = ramp('#1c120c', '#3e2a1c', '#5e4028', '#7e5836', '#a07850', '#c49c74')
STONE = ramp('#121118', '#2a2834', '#454252', '#64606f', '#8a8696', '#b4b0c0')


def accent(hexc):
    """Outline, dark, shade, mid, light, highlight around a menu accent colour."""
    c = hx(hexc)
    return (mix(c, OUT, 0.80), mix(c, (0, 0, 0), 0.55), mix(c, (0, 0, 0), 0.30), c,
            mix(c, (255, 255, 255), 0.32), mix(c, (255, 255, 255), 0.64))


# Menu accents (gen_gui_chrome.THEMES) and a few of our own for tabs without a menu frame.
ACC = {
    'manager': accent('#9d86cf'), 'shop': accent('#5cc2d6'), 'bazaar': accent('#d6a24c'),
    'auction': accent('#e2c766'), 'skills': accent('#7ea4e8'), 'recipes': accent('#c99560'),
    'bestiary': accent('#cc5c66'), 'collection': accent('#62c08a'), 'journal': accent('#c9ad7e'),
    'stats': accent('#52baa9'), 'storage': accent('#d48a52'), 'loadouts': accent('#93a7bf'),
    'spawns': accent('#a9c46c'), 'pets': accent('#e083b3'), 'guild': accent('#e0b24c'),
    'island': accent('#6cc070'), 'dev': accent('#cc5c66'), 'nav': accent('#b8a8e0'),
}


# ----------------------------------------------------------------------------- drawing kit

class Icon(Px):
    def __init__(self):
        super().__init__(N, N)

    def body(self, mask, rmp, soft=False):
        """Flat shading lit from the top left; the outline comes later, once, round everything."""
        self.shade(mask, rmp, soft=soft)

    def finish(self, tint):
        """Outline round every solid texel, tinted toward ``tint`` (an accent ramp's outline colour)."""
        solid = self.solid()
        ring = grow(solid) & ~solid
        self.put(ring, mix(tint, OUT, 0.35))
        return self


def poly(p, pts):
    return p.g.poly([(x, y) for x, y in pts])


def rect(p, x0, y0, x1, y1):
    """Texels x0..x1, y0..y1 inclusive."""
    return p.g.rect(x0, y0, x1 + 1, y1 + 1)


def line(p, x0, y0, x1, y1, thick=1):
    return p.g.line(x0, y0, x1, y1, thick)


def disc(p, cx, cy, r):
    return p.g.disc(cx, cy, r)


# ----------------------------------------------------------------------------- the objects

def i_shop():
    p, a = Icon(), ACC['shop']
    big = poly(p, [(8, 0.5), (11.5, 4), (11.5, 11), (8, 15), (4.5, 11), (4.5, 4)])
    p.put(big, a[3])
    p.put(big & (p.g.x < 8), a[4])
    p.put(big & (p.g.x < 6.5) & (p.g.y < 11), a[5])
    p.put(big & (p.g.x > 9.5), a[2])
    p.put(big & (p.g.y > 11.5) & (p.g.x > 8), a[1])
    left = poly(p, [(3, 6.5), (5, 8.5), (5, 13), (3, 15), (1, 13), (1, 8.5)]) & ~big
    p.put(left, a[3])
    p.put(left & (p.g.x < 3), a[4])
    right = poly(p, [(13, 5.5), (15, 7.5), (15, 12), (13, 14), (11, 12), (11, 7.5)]) & ~big
    p.put(right, a[2])
    p.put(right & (p.g.x < 13), a[3])
    p.at(7, 3, (255, 255, 255))
    p.at(7, 4, a[5])
    return p.finish(a[0])


def i_bazaar():
    p, a = Icon(), ACC['bazaar']
    # striped awning with a scalloped lip, two posts, a counter with goods
    awn = rect(p, 1, 2, 14, 5)
    for x in range(1, 15):
        col = a[4] if (x // 2) % 2 == 0 else PAPER[4]
        p.put(awn & (p.g.x >= x) & (p.g.x < x + 1), col)
    p.put(rect(p, 1, 2, 14, 2), a[5])
    for x in range(1, 15, 3):
        p.put(rect(p, x, 6, x + 1, 6), a[2])
    p.put(rect(p, 2, 7, 2, 14) | rect(p, 13, 7, 13, 14), WOOD[3])
    counter = rect(p, 1, 10, 14, 13)
    p.body(counter, WOOD)
    p.put(rect(p, 1, 10, 14, 10), WOOD[5])
    # goods on the counter: a gold coin stack and a green bundle
    p.put(rect(p, 4, 8, 6, 9), GOLD[4])
    p.at(4, 8, GOLD[5])
    p.put(rect(p, 9, 8, 11, 9), hx('#6cc070'))
    p.at(9, 8, hx('#a8e89a'))
    return p.finish(a[0])


def i_skills():
    p, a = Icon(), ACC['skills']
    bolt = poly(p, [(9.5, 0.5), (2.5, 9.5), (7, 9.5), (4.5, 15.5), (13.5, 5.5), (9, 5.5), (12.5, 0.5)])
    p.put(bolt, a[3])
    p.put(bolt & edge(bolt, -1, 0), a[5])
    p.put(bolt & edge(bolt, 1, 0), a[2])
    p.put(bolt & (p.g.y < 6) & ~edge(bolt, 1, 0), a[4])
    # sparks
    for x, y in ((2, 3), (13, 11), (14, 2)):
        p.at(x, y, a[5])
    return p.finish(a[0])


def i_auction():
    p, a = Icon(), ACC['auction']
    handle = line(p, 7, 7, 14, 14, 2)
    p.put(handle, WOOD[3])
    p.put(line(p, 7, 7, 14, 14), WOOD[4])
    head = poly(p, [(1.5, 5), (6, 0.5), (10.5, 5), (6, 9.5)])
    p.put(head, a[3])
    p.put(head & (p.g.x + p.g.y < 9), a[4])
    p.put(head & (p.g.x + p.g.y > 13), a[2])
    band = poly(p, [(3.5, 3), (4, 2.5), (8.5, 7), (8, 7.5)])
    p.put(band & head, a[1])
    p.at(5, 2, a[5])
    block = rect(p, 1, 12, 7, 14)
    p.body(block, WOOD)
    p.put(rect(p, 1, 12, 7, 12), a[4])
    return p.finish(a[0])


def i_bestiary():
    p, a = Icon(), ACC['bestiary']
    skull = disc(p, 8, 7.5, 5.6) | rect(p, 4, 9, 11, 12)
    p.body(skull, BONE)
    # horns
    for sx in (-1, 1):
        cx = 8 + sx * 5.5
        horn = poly(p, [(cx - sx * 1.5, 4), (cx + sx * 2.5, 0.5), (cx + sx * 2.5, 2.5), (cx + sx * 0.5, 6.5)])
        p.put(horn & ~skull, a[3])
        p.put(horn & ~skull & (p.g.y < 3), a[4])
    # eyes glow, nose, teeth
    for ex in (5, 9):
        p.put(rect(p, ex, 7, ex + 1, 8), a[1])
        p.at(ex, 7, a[4])
    p.at(7, 10, BONE[1])
    p.at(8, 10, BONE[1])
    jaw = rect(p, 5, 13, 10, 14)
    p.body(jaw, BONE, soft=True)
    for x in (6, 8):
        p.at(x, 13, BONE[1])
    p.put(rect(p, 4, 12, 11, 12), BONE[2])
    return p.finish(a[0])


def i_collection():
    p, a = Icon(), ACC['collection']
    handle = line(p, 4, 13, 11, 6, 2)
    p.put(handle, WOOD[3])
    p.put(line(p, 4, 13, 11, 6), WOOD[4])
    head = (disc(p, 9.5, 9.5, 8.8) & ~disc(p, 9.5, 9.5, 6.6)) & (p.g.y < 8) & (p.g.x > 4)
    head |= rect(p, 9, 4, 11, 6)
    p.body(head, STEEL)
    p.put(head & (p.g.y < 3), STEEL[5])
    # a leaf: everything you gather counts
    leaf = poly(p, [(0.5, 15.5), (1, 11), (4, 9), (5.5, 10), (5, 13.5)])
    p.put(leaf, a[3])
    p.put(leaf & (p.g.x + 0.6 * p.g.y < 9.5), a[4])
    p.put(line(p, 1, 15, 4, 11), a[1])
    return p.finish(a[0])


def i_pets():
    p, a = Icon(), ACC['pets']
    pad = disc(p, 8, 11, 4.2) & (p.g.y > 7.5)
    pad |= disc(p, 6, 12.5, 2.6) | disc(p, 10, 12.5, 2.6)
    p.body(pad, a)
    for cx, cy, r in ((3, 7, 1.9), (6.2, 3.8, 2.0), (9.8, 3.8, 2.0), (13, 7, 1.9)):
        toe = disc(p, cx, cy, r)
        p.body(toe, a, soft=True)
    p.at(7, 9, a[5])
    return p.finish(a[0])


def i_journal():
    p, a = Icon(), ACC['journal']
    pages = rect(p, 4, 2, 13, 13)
    p.put(pages, PAPER[4])
    for y in range(3, 13, 2):
        p.put(rect(p, 12, y, 13, y), PAPER[3])
    cover = rect(p, 2, 1, 12, 13)
    leather = ramp('#1e0e0c', '#4a2018', '#6e3222', '#8e4630', '#b06244', '#d48a66')
    p.body(cover, leather)
    p.put(rect(p, 3, 1, 3, 13), leather[1])            # spine fold
    # skull seal: a boss journal
    p.put(rect(p, 6, 5, 9, 7), BONE[4])
    p.put(rect(p, 7, 8, 8, 8), BONE[3])
    p.at(6, 6, leather[1])
    p.at(9, 6, leather[1])
    p.at(6, 5, BONE[5])
    # ribbon
    p.put(rect(p, 10, 13, 10, 15), a[4])
    p.at(10, 15, a[2])
    return p.finish(leather[0])


def i_recipes():
    p, a = Icon(), ACC['recipes']
    sheet = rect(p, 3, 3, 12, 12)
    p.body(sheet, PAPER, soft=True)
    for y in (5, 7, 9):
        p.put(rect(p, 5, y, 10 - (y == 9) * 2, y), PAPER[1])
    p.at(5, 11, a[2])
    p.at(6, 11, a[3])
    for y0, rmp in ((1, a), (12, a)):
        roll = rect(p, 2, y0, 13, y0 + 2)
        p.body(roll, rmp)
        p.put(rect(p, 1, y0 + 1, 1, y0 + 1) | rect(p, 14, y0 + 1, 14, y0 + 1), WOOD[2])
    return p.finish(a[0])


def i_island():
    p, a = Icon(), ACC['island']
    under = poly(p, [(1, 9), (15, 9), (11, 13), (8, 15.5), (5, 13)])
    p.body(under, DIRT)
    p.put(under & (p.g.y > 12.5), STONE[2])
    top = rect(p, 1, 8, 14, 9)
    p.put(top, GRASS[4])
    p.put(rect(p, 1, 8, 14, 8), GRASS[5])
    p.put(rect(p, 1, 10, 14, 10) & under, GRASS[2])
    trunk = rect(p, 9, 5, 10, 7)
    p.put(trunk, WOOD[2])
    crown = disc(p, 9.6, 3.6, 3.4)
    p.body(crown, LEAF)
    p.at(4, 7, hx('#f2e6ff'))
    p.at(3, 7, hx('#c8b8ea'))
    return p.finish(a[0])


def i_guild():
    p, a = Icon(), ACC['guild']
    p.put(rect(p, 2, 1, 2, 15), WOOD[3])
    p.put(rect(p, 2, 1, 2, 15) & (p.g.y < 3), WOOD[5])
    p.put(rect(p, 1, 1, 13, 1), WOOD[4])
    cloth = poly(p, [(3, 2), (13, 2), (13, 13), (10.5, 10.5), (8, 13), (5.5, 10.5), (3, 13)])
    p.put(cloth, a[3])
    p.put(cloth & (p.g.x < 5), a[4])
    p.put(cloth & (p.g.x > 11.5), a[2])
    p.put(cloth & (p.g.y > 10) & (p.g.x > 6) & (p.g.x < 10), a[2])
    # star emblem
    star = ['..#..', '.###.', '#####', '.###.', '.#.#.']
    for y, row in enumerate(star):
        for x, ch in enumerate(row):
            if ch == '#':
                p.at(6 + x, 4 + y, PAPER[5] if y < 2 else PAPER[4])
    return p.finish(a[0])


def i_stats():
    p, a = Icon(), ACC['stats']
    p.put(rect(p, 1, 14, 14, 14), STONE[4])
    p.put(rect(p, 1, 2, 1, 14), STONE[4])
    for x0, top in ((3, 10), (6, 7), (9, 9), (12, 4)):
        bar = rect(p, x0, top, x0 + 1, 13)
        p.put(bar, a[3])
        p.put(rect(p, x0, top, x0, 13), a[4])
        p.put(rect(p, x0, top, x0 + 1, top), a[5])
    return p.finish(a[0])


def i_storage():
    p, a = Icon(), ACC['storage']
    lid = rect(p, 1, 3, 14, 7)
    p.body(lid, WOOD)
    p.put(rect(p, 2, 3, 13, 3), WOOD[5])
    box = rect(p, 1, 8, 14, 14)
    p.body(box, WOOD)
    p.put(rect(p, 1, 8, 14, 8), WOOD[1])
    for x in (3, 12):
        p.put(rect(p, x, 3, x, 14), a[2])
    lock = rect(p, 6, 6, 9, 10)
    p.body(lock, GOLD)
    p.put(rect(p, 7, 8, 8, 9), GOLD[0])
    return p.finish(a[0])


def i_loadouts():
    p, a = Icon(), ACC['loadouts']
    plate = poly(p, [(1, 3), (5, 1), (6.5, 3), (9.5, 3), (11, 1), (15, 3), (13, 7), (12, 7),
                     (12, 15), (4, 15), (4, 7), (3, 7)])
    p.body(plate, STEEL)
    p.put(plate & (np.abs(p.g.x - 8) < 0.6) & (p.g.y > 4), STEEL[2])
    collar = rect(p, 6, 3, 9, 4)
    p.put(collar, a[2])
    p.put(rect(p, 5, 13, 10, 14) & plate, a[3])
    p.put(rect(p, 5, 13, 10, 13) & plate, a[4])
    return p.finish(STEEL[0])


def i_spawns():
    p, a = Icon(), ACC['spawns']
    logs = line(p, 2, 14, 13, 10, 2) | line(p, 2, 10, 13, 14, 2)
    p.put(logs, WOOD[3])
    p.put(line(p, 2, 10, 13, 14), WOOD[4])
    flame = poly(p, [(8, 0.5), (11.5, 6), (11.5, 9), (9.5, 11.5), (6.5, 11.5), (4.5, 9), (4.5, 6), (6.5, 4)])
    p.put(flame, FLAME[3])
    inner = poly(p, [(8, 4), (10, 7.5), (10, 9.5), (8.5, 11), (7.5, 11), (6, 9.5), (6, 8)])
    p.put(inner, FLAME[4])
    p.put(poly(p, [(8, 7), (9, 9.5), (8, 11), (7, 9.5)]), FLAME[5])
    p.put(flame & edge(flame, 1, 0), FLAME[2])
    # camp stones in the tab colour
    for x in (1, 14):
        p.put(rect(p, x, 12, x, 13), a[3])
    return p.finish(FLAME[0])


def i_craft():
    p, a = Icon(), ACC['recipes']
    bench = rect(p, 1, 8, 10, 14)
    p.body(bench, WOOD)
    for x in (4, 7):
        p.put(rect(p, x, 8, x, 14), WOOD[1])
    for y in (10, 12):
        p.put(rect(p, 1, y, 10, y), WOOD[1])
    p.put(rect(p, 1, 8, 10, 8), WOOD[5])
    handle = line(p, 7, 9, 13, 3, 2)
    p.put(handle, WOOD[2])
    head = poly(p, [(10, 0.5), (15.5, 6), (13.5, 8), (8, 2.5)])
    p.body(head, STEEL)
    return p.finish(WOOD[0])


def i_anvil():
    p, a = Icon(), ACC['loadouts']
    top = poly(p, [(0.5, 4), (4, 4), (4, 3), (15.5, 3), (15.5, 7), (11, 7), (4, 7), (1, 5.5)])
    p.body(top, STEEL)
    p.put(rect(p, 4, 3, 15, 3), STEEL[5])
    waist = rect(p, 6, 8, 11, 10)
    p.body(waist, STONE)
    base = rect(p, 3, 11, 14, 14)
    p.body(base, STONE)
    p.put(rect(p, 3, 11, 14, 11), STONE[4])
    return p.finish(STEEL[0])


def i_dev():
    p, a = Icon(), ACC['dev']
    g = p.g
    ang = g.angle(7.5, 7.5)
    d = g.dist(7.5, 7.5)
    teeth = np.mod(ang / (2 * math.pi) * 8 + 0.25, 1.0) < 0.5
    gear = ((d < 5.5) | ((d < 7.4) & teeth)) & (d > 2.2)
    p.body(gear, STEEL)
    p.put(disc(p, 7.5, 7.5, 2.2) & ~disc(p, 7.5, 7.5, 1.2), a[3])
    return p.finish(STEEL[0])


def i_close():
    p, a = Icon(), CRIMSON
    x = line(p, 3, 3, 12, 12, 2) | line(p, 12, 3, 3, 12, 2)
    p.put(x, a[3])
    p.put(line(p, 3, 3, 12, 12), a[4])
    p.put(line(p, 12, 3, 3, 12) & (p.g.y < 7), a[4])
    return p.finish(a[0])


def i_manager():
    p, a = Icon(), ACC['manager']
    g = p.g
    ang = g.angle(7.5, 7.5)
    d = g.dist(7.5, 7.5)
    k = np.abs(np.mod(ang / (2 * math.pi) * 8, 1.0) - 0.5) * 2      # 1 on the 8 rays, 0 between
    star = d < 2.8 + 4.6 * np.power(k, 2.2)
    big = d < 2.8 + 4.8 * np.power(np.abs(np.mod(ang / (2 * math.pi) * 4, 1.0) - 0.5) * 2, 3.0)
    p.put(star, a[3])
    p.put(big, GOLD[3])
    p.put(big & (g.x + g.y < 15), GOLD[4])
    p.put(big & (np.abs(g.x - 7.5) < 0.6), GOLD[5])
    p.put(disc(p, 7.5, 7.5, 1.6), (255, 255, 255))
    return p.finish(a[0])


def i_lock():
    p = Icon()
    shackle = (disc(p, 7.5, 6, 4.0) & ~disc(p, 7.5, 6, 2.2)) & (p.g.y < 7.5)
    p.put(shackle, STEEL[4])
    p.put(shackle & (p.g.x > 8), STEEL[2])
    body = rect(p, 3, 7, 12, 14)
    p.body(body, GOLD)
    p.put(rect(p, 7, 9, 8, 12), GOLD[0])
    return p.finish(GOLD[0])


def i_next():
    p, a = Icon(), accent('#62d977')
    arrow = poly(p, [(8, 1), (14.5, 7.5), (10.5, 7.5), (10.5, 14.5), (5.5, 14.5), (5.5, 7.5), (1.5, 7.5)])
    p.body(arrow, a)
    return p.finish(a[0])


def i_later():
    p = Icon()
    glass = poly(p, [(3, 2), (13, 2), (8.5, 8), (13, 14), (3, 14), (7.5, 8)])
    p.put(glass, hx('#d8e4f0'))
    p.put(glass & (p.g.y > 10.5), AMBER[4])
    p.put(glass & (p.g.y < 4.5) & (p.g.y > 2.5), AMBER[4])
    p.put(rect(p, 2, 1, 13, 2) | rect(p, 2, 14, 13, 15), WOOD[3])
    return p.finish(WOOD[0])


def i_back():
    p, a = Icon(), ACC['nav']
    g = p.g
    d = g.dist(9, 9)
    hook = (d < 5.6) & (d > 2.8) & ((g.y < 9) | (g.x > 9))
    p.put(hook, a[4])
    head = poly(p, [(0.5, 9), (6, 4.5), (6, 13.5)])
    p.put(head & (g.x < 6.5), a[4])
    p.put(edge(hook | head, 0, -1) & (hook | head), a[5])
    return p.finish(a[0])


def i_prev(off=False):
    p = Icon()
    a = ACC['nav'] if not off else STONE
    for dx in (0, 5):
        chev = poly(p, [(6.5 + dx, 2), (9 + dx, 2), (4.5 + dx, 8), (9 + dx, 14), (6.5 + dx, 14), (2 + dx, 8)])
        p.put(chev, a[4] if dx == 0 else a[3])
    p.put(edge(p.solid(), 0, -1) & p.solid(), a[5])
    return p.finish(a[0])


def i_sword():
    p, a = Icon(), CRIMSON
    blade = line(p, 3, 12, 13, 2, 2)
    p.put(blade, STEEL[4])
    p.put(line(p, 3, 12, 13, 2), STEEL[5])
    guard = line(p, 2, 9, 6, 13, 2)
    p.put(guard, GOLD[3])
    p.put(line(p, 1, 14, 3, 12, 2), WOOD[3])
    return p.finish(STEEL[0])


def i_axe():
    p, a = Icon(), ACC['collection']
    p.put(line(p, 3, 14, 11, 3, 2), WOOD[3])
    head = poly(p, [(8, 1), (14.5, 3), (14.5, 9), (11, 7.5), (9.5, 5)])
    p.body(head, STEEL)
    return p.finish(STEEL[0])


def i_fish():
    p, a = Icon(), ACC['shop']
    body = p.g.ellipse(7, 8, 5.8, 3.6)
    tail = poly(p, [(11.5, 8), (15.5, 4), (15.5, 12)])
    p.body(body, a)
    p.put(tail, a[2])
    p.put(rect(p, 3, 7, 3, 7), OUT)
    p.put(body & (p.g.y > 9.5), PAPER[4])
    return p.finish(a[0])


def empty():
    return Image.new('RGBA', (N, N), (0, 0, 0, 0))


# ----------------------------------------------------------------------------- tab variants

def badge(im, rows, pal, x0, y0):
    im = im.copy()
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                c = pal[ch]
                px[x0 + x, y0 + y] = c + (255,)
    return im


LOCK_BADGE = ['.ooo..', 'o...o.', 'o...o.', 'ooooo.', 'oGGGo.', 'oGkGo.', 'ooooo.']
NEXT_BADGE = ['..o...', '.oGo..', 'oGGGo.', '.oGo..', '.oGo..', '.ooo..']


def locked(im):
    a = np.asarray(im).astype(float)
    grey = a[..., :3] @ np.array([0.30, 0.55, 0.15])
    tone = mix(STONE[3], STONE[4], 0.5)
    for i in range(3):
        a[..., i] = np.where(a[..., 3] > 0, grey * 0.42 + tone[i] * 0.30, a[..., i])
    out = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA')
    return badge(out, LOCK_BADGE, {'o': OUT, 'G': GOLD[4], 'k': GOLD[1]}, 10, 9)


def next_up(im):
    out = im.copy()
    return badge(out, NEXT_BADGE, {'o': OUT, 'G': hx('#62d977')}, 10, 10)


def later(im):
    a = np.asarray(im).copy()
    solid = a[..., 3] > 0
    inner = shrink(solid)
    a[solid, :3] = hx('#1c1a25')
    a[inner, :3] = hx('#48445a')
    a[solid, 3] = 255
    return Image.fromarray(a, 'RGBA')


# ----------------------------------------------------------------------------- catalogue

# icon -> (CustomModelData, vanilla material the button uses, drawing)
ICONS = {
    'manager': (7701, 'nether_star', i_manager),
    'shop': (7702, 'amethyst_shard', i_shop),
    'bazaar': (7703, 'chest', i_bazaar),
    'skills': (7704, 'netherite_scrap', i_skills),
    'auction': (7705, 'gold_block', i_auction),
    'bestiary': (7706, 'bone', i_bestiary),
    'collection': (7707, 'iron_pickaxe', i_collection),
    'pets': (7708, 'lead', i_pets),
    'journal': (7709, 'writable_book', i_journal),
    'recipes': (7710, 'knowledge_book', i_recipes),
    'island': (7711, 'grass_block', i_island),
    'guild': (7712, 'yellow_banner', i_guild),
    'stats': (7713, 'experience_bottle', i_stats),
    'storage': (7714, 'ender_chest', i_storage),
    'loadouts': (7715, 'armor_stand', i_loadouts),
    'spawns': (7716, 'compass', i_spawns),
    'craft': (7717, 'crafting_table', i_craft),
    'anvil': (7718, 'anvil', i_anvil),
    'dev': (7719, 'command_block', i_dev),
    'close': (7720, 'barrier', i_close),
    'locked': (7721, 'gray_dye', i_lock),
    'next': (7722, 'lime_dye', i_next),
    'later': (7723, 'light_gray_stained_glass_pane', i_later),
    # menu navigation, shared by every chrome menu (GuiIcons in Java)
    'back': (7724, 'arrow', i_back),
    'prev': (7725, 'arrow', i_prev),
    'next_page': (7726, 'arrow', lambda: i_prev().image().transpose(Image.FLIP_LEFT_RIGHT)),
    'prev_off': (7727, 'gray_stained_glass_pane', lambda: i_prev(True)),
    'next_off': (7728, 'gray_stained_glass_pane', lambda: i_prev(True).image().transpose(Image.FLIP_LEFT_RIGHT)),
    'section_combat': (7729, 'black_stained_glass_pane', i_sword),
    'section_gathering': (7730, 'black_stained_glass_pane', i_axe),
    'section_fishing': (7731, 'black_stained_glass_pane', i_fish),
}

# Lockable Manager tabs, in ManagerIcons.TABS order: locked on grey dye, next on lime dye, later on a
# light-grey pane, each at base + index.
TABS = ('bazaar', 'skills', 'auction', 'bestiary', 'collection', 'pets', 'journal', 'recipes', 'island', 'guild',
        'spawns', 'craft', 'anvil')
VARIANTS = (('locked', 7740, 'gray_dye', locked), ('next', 7760, 'lime_dye', next_up),
            ('later', 7780, 'light_gray_stained_glass_pane', later))

# Fillers: the same CMD on every pane colour draws nothing, so the chrome under it shows (ChromePage hides any
# stained pane with a blank name, whatever colour the old menu framed itself in).
FILLER_CMD = 7700
FILLERS = tuple('%s_stained_glass_pane' % c for c in (
    'gray', 'black', 'light_gray', 'white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'cyan',
    'purple', 'blue', 'brown', 'green', 'red'))


def render(fn):
    out = fn()
    return out if isinstance(out, Image.Image) else out.image()


def main(root, preview=None):
    tex = os.path.join(root, 'assets/aetherion/textures/item/gui')
    mdl = os.path.join(root, 'assets/aetherion/models/item/gui')
    os.makedirs(tex, exist_ok=True)
    os.makedirs(mdl, exist_ok=True)
    overrides = {}
    images = {}

    def write(name, im, cmd, material):
        im.save(os.path.join(tex, name + '.png'), optimize=True)
        with open(os.path.join(mdl, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'aetherion:item/gui/' + name}}, f,
                      indent=2)
            f.write('\n')
        overrides.setdefault(material, []).append([cmd, 'aetherion:item/gui/' + name])
        images[name] = im

    for name, (cmd, material, fn) in ICONS.items():
        write(name, render(fn), cmd, material)
    for i, tab in enumerate(TABS):
        for suffix, base, material, look in VARIANTS:
            write('%s_%s' % (tab, suffix), look(images[tab]), base + i, material)
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
    if preview:
        sheet(images, preview)
    print('%d icons + filler written' % len(images))


def sheet(images, path, scale=4):
    """Every icon on a chest-menu slot well, for checking the art."""
    names = list(images)
    cols = 10
    cell = 18 * scale
    im = Image.new('RGBA', (cols * cell, ((len(names) + cols - 1) // cols) * cell), (33, 31, 42, 255))
    for k, name in enumerate(names):
        x, y = (k % cols) * cell, (k // cols) * cell
        well = Image.new('RGBA', (cell - 2 * scale, cell - 2 * scale), (44, 42, 55, 255))
        im.alpha_composite(well, (x + scale, y + scale))
        icon = images[name].resize((16 * scale, 16 * scale), Image.NEAREST)
        im.alpha_composite(icon, (x + scale, y + scale))
    im.save(path)


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'out', sys.argv[2] if len(sys.argv) > 2 else None)
