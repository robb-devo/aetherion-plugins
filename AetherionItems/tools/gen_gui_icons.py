#!/usr/bin/env python3
"""Aetherion Manager menu icons (32x32 item textures) + invisible GUI filler.

    python gen_gui_icons.py <pack_root>        e.g. python gen_gui_icons.py ../resourcepack

Each icon is drawn at 4x (128 px) with anti-aliased shapes and box-filtered down to 32x32, the size of
the pack's item art. Light comes from the top left; every icon has a one-texel dark outline so it reads
on the dark Manager wells. The Java side puts these on the menu buttons with CustomModelData (see
ManagerIcons); the base material keeps its vanilla look everywhere else.

Writes:
    assets/aetherion/textures/item/gui/<icon>.png
    assets/aetherion/models/item/gui/<icon>.json
    gui_icons.json (next to this script)   material -> [(cmd, icon)] for the model-override merge
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hd_art import Canvas, band, fill, hx, lerp, linear, sd_box, sd_circle, sd_polygon, sd_segment  # noqa: E402

SS = 4            # supersampling
N = 32 * SS
OUTLINE = 1.0 * SS
INK = hx('#120e18')
WHITE = (255, 255, 255)


def finish(cv):
    im = cv.image()
    return im.resize((32, 32), Image.BOX)


def body(cv, sd, top, bottom, x0=0, y0=0, x1=0, y1=N, outline=OUTLINE):
    """Outlined shape with a top-left lit gradient."""
    cv.paint(fill(sd - outline, 1.5), INK)
    cv.paint(fill(sd, 1.5), lerp(top, bottom, linear(cv, x0, y0, x1, y1)))


def shine(cv, sd, amount=0.35, inset=3.0 * SS):
    """Soft highlight on the upper-left inside edge of a shape."""
    inner = fill(sd + inset, 1.5)
    edge = fill(sd, 1.5) * (1.0 - inner)
    upper = np.clip(1.0 - (cv.x + cv.y) / N, 0.0, 1.0)
    cv.paint(edge * upper, WHITE, amount)


def star_pts(cx, cy, r_out, r_in, points=5, rot=-90.0):
    pts = []
    for k in range(points * 2):
        r = r_out if k % 2 == 0 else r_in
        a = math.radians(rot + k * 180.0 / points)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def S(*v):
    """Icon coordinates are written on the 32 px grid; scale them to the supersampled canvas."""
    return [x * SS for x in v] if len(v) > 1 else v[0] * SS


# ----------------------------------------------------------------------------- icons

def manager():
    cv = Canvas(N, N)
    ring = sd_circle(cv, S(16), S(16), S(13.5))
    body(cv, ring, hx('#ffe9a8'), hx('#9a6a1c'), 0, 0, N, N)
    cv.paint(fill(sd_circle(cv, S(16), S(16), S(10.5)), 1.5), lerp(hx('#3a2a5c'), hx('#140f22'), linear(cv, 0, S(6), 0, S(26))))
    star = sd_polygon(cv, star_pts(S(16), S(16), S(9.5), S(2.6), 4, -90))
    cv.paint(fill(star - SS, 1.5), INK)
    cv.paint(fill(star, 1.5), lerp(hx('#f3e6ff'), hx('#a77bea'), linear(cv, S(8), S(8), S(24), S(24))))
    cv.paint(fill(sd_circle(cv, S(16), S(16), S(2.2)), 1.5), WHITE)
    shine(cv, ring)
    return finish(cv)


def shop():
    cv = Canvas(N, N)
    bag = np.minimum(sd_circle(cv, S(16), S(20), S(10)), sd_box(cv, S(9), S(10), S(23), S(20), S(3)))
    body(cv, bag, hx('#b48ae8'), hx('#4b2a86'), 0, S(8), 0, S(30))
    tie = sd_box(cv, S(10.5), S(9), S(21.5), S(12), S(1.2))
    cv.paint(fill(tie - SS, 1.5), INK)
    cv.paint(fill(tie, 1.5), hx('#f5c542'))
    gem = sd_polygon(cv, [(S(16), S(1.5)), (S(21), S(6)), (S(16), S(10.5)), (S(11), S(6))])
    body(cv, gem, hx('#e8fbff'), hx('#3fa6d9'), S(11), S(2), S(21), S(10))
    rune = sd_polygon(cv, star_pts(S(16), S(20.5), S(4.5), S(1.6), 4, -90))
    cv.paint(fill(rune, 1.5), hx('#9ee6ff'), 0.9)
    shine(cv, bag)
    return finish(cv)


def bazaar():
    cv = Canvas(N, N)
    posts = np.minimum(sd_box(cv, S(5), S(12), S(7.5), S(29)), sd_box(cv, S(24.5), S(12), S(27), S(29)))
    body(cv, posts, hx('#c79a5e'), hx('#6b4520'))
    counter = sd_box(cv, S(4), S(21), S(28), S(28), S(1))
    body(cv, counter, hx('#d9a46a'), hx('#7a4a22'), 0, S(21), 0, S(28))
    awning = sd_polygon(cv, [(S(2), S(12)), (S(6), S(4)), (S(26), S(4)), (S(30), S(12))])
    cv.paint(fill(awning - OUTLINE, 1.5), INK)
    stripes = (np.floor((cv.x - S(2)) / S(4)) % 2 == 0)
    cv.paint(fill(awning, 1.5), np.where(stripes[..., None], np.array(hx('#f2c94c'), float), np.array(hx('#e0703a'), float)))
    for k in range(7):
        x = S(4 + 4 * k)
        cv.paint(fill(sd_circle(cv, x, S(12.5), S(2.0)), 1.5) * (cv.y > S(12)), hx('#e0703a') if k % 2 else hx('#f2c94c'))
    for x, c in ((S(10), hx('#62d977')), (S(16), hx('#ff5a4a')), (S(22), hx('#9ee6ff'))):
        g = sd_circle(cv, x, S(19.5), S(2.4))
        cv.paint(fill(g - SS * 0.8, 1.5), INK)
        cv.paint(fill(g, 1.5), c)
    return finish(cv)


def skills():
    cv = Canvas(N, N)
    hexa = sd_polygon(cv, [(S(16 + 13 * math.cos(math.radians(a))), S(16 + 13 * math.sin(math.radians(a))))
                           for a in range(-90, 270, 60)])
    body(cv, hexa, hx('#c6cdff'), hx('#3d4594'), 0, 0, N, N)
    cv.paint(fill(hexa + S(2.5), 1.5), lerp(hx('#2a2f6a'), hx('#151838'), linear(cv, 0, S(5), 0, S(27))))
    arrow = sd_polygon(cv, [(S(16), S(6.5)), (S(23), S(14)), (S(19), S(14)), (S(19), S(24)), (S(13), S(24)),
                            (S(13), S(14)), (S(9), S(14))])
    body(cv, arrow, hx('#ffffff'), hx('#8f9cff'), 0, S(6), 0, S(24), outline=SS * 0.8)
    shine(cv, hexa, 0.3)
    return finish(cv)


def auction():
    cv = Canvas(N, N)
    handle = sd_segment(cv, S(8), S(27), S(19), S(14))
    cv.paint(band(handle, S(2.6), 1.5), INK)
    cv.paint(band(handle, S(1.7), 1.5), lerp(hx('#c79a5e'), hx('#6b4520'), linear(cv, S(8), S(14), S(19), S(27))))
    head = sd_polygon(cv, [(S(13), S(9)), (S(21), S(1.5)), (S(29), S(9.5)), (S(21), S(17))])
    body(cv, head, hx('#fff0b0'), hx('#b0781c'), S(13), S(2), S(29), S(17))
    for a, b in (((S(14.5), S(10.5)), (S(22.5), S(2.8))), ((S(19.5), S(15.5)), (S(27.5), S(8)))):
        cv.paint(band(sd_segment(cv, a[0], a[1], b[0], b[1]), S(0.6), 1.5), hx('#7a4f12'), 0.8)
    base = sd_box(cv, S(3), S(26), S(17), S(30), S(1))
    body(cv, base, hx('#e7c27a'), hx('#7a4f12'), 0, S(26), 0, S(30))
    shine(cv, head)
    return finish(cv)


def bestiary():
    cv = Canvas(N, N)
    horns = np.minimum(sd_polygon(cv, [(S(8), S(12)), (S(2), S(2)), (S(12.5), S(8))]),
                       sd_polygon(cv, [(S(24), S(12)), (S(30), S(2)), (S(19.5), S(8))]))
    body(cv, horns, hx('#f5e6c8'), hx('#8e7a58'), 0, 0, 0, S(12))
    skull = np.minimum(sd_circle(cv, S(16), S(14), S(10)), sd_box(cv, S(10), S(18), S(22), S(29), S(2.5)))
    body(cv, skull, hx('#ffb3a6'), hx('#9c1f19'), 0, S(4), 0, S(29))
    for ex in (S(12), S(20)):
        cv.paint(fill(sd_circle(cv, ex, S(15), S(3.0)), 1.5), INK)
        cv.paint(fill(sd_circle(cv, ex, S(15), S(1.2)), 1.5), hx('#ffd36b'))
    for tx in (S(13), S(16), S(19)):
        cv.paint(fill(sd_box(cv, tx - S(0.9), S(23), tx + S(0.9), S(28)), 1.5), INK, 0.85)
    shine(cv, skull)
    return finish(cv)


def collection():
    cv = Canvas(N, N)
    gem = sd_polygon(cv, [(S(9), S(6)), (S(23), S(6)), (S(29), S(13)), (S(16), S(29)), (S(3), S(13))])
    body(cv, gem, hx('#c8ffd8'), hx('#1e8a4a'), S(4), S(6), S(26), S(28))
    cv.paint(fill(sd_polygon(cv, [(S(9), S(6)), (S(16), S(13)), (S(3), S(13))]), 1.5), WHITE, 0.35)
    cv.paint(fill(sd_polygon(cv, [(S(16), S(13)), (S(29), S(13)), (S(16), S(29))]), 1.5), hx('#0e5a2e'), 0.35)
    for a, b in (((S(3), S(13)), (S(29), S(13))), ((S(16), S(13)), (S(16), S(29))), ((S(9), S(6)), (S(16), S(13))),
                 ((S(23), S(6)), (S(16), S(13)))):
        cv.paint(band(sd_segment(cv, a[0], a[1], b[0], b[1]), S(0.4), 1.5) * fill(gem, 1.5), WHITE, 0.4)
    sp = sd_polygon(cv, star_pts(S(26), S(4.5), S(4), S(1.1), 4, -90))
    cv.paint(fill(sp, 1.5), WHITE)
    return finish(cv)


def pets():
    cv = Canvas(N, N)
    pad = np.minimum(sd_circle(cv, S(16), S(21), S(7.5)), sd_box(cv, S(9.5), S(19), S(22.5), S(28), S(4)))
    toes = [sd_circle(cv, S(6.5), S(13.5), S(3.6)), sd_circle(cv, S(12.5), S(7.5), S(3.8)),
            sd_circle(cv, S(19.5), S(7.5), S(3.8)), sd_circle(cv, S(25.5), S(13.5), S(3.6))]
    for t in toes:
        body(cv, t, hx('#ffd0e0'), hx('#c2457a'), 0, S(4), 0, S(18))
    body(cv, pad, hx('#ffc2d6'), hx('#a8325f'), 0, S(14), 0, S(29))
    cv.paint(fill(sd_circle(cv, S(13), S(19), S(2)), 1.5), WHITE, 0.55)
    return finish(cv)


def journal():
    cv = Canvas(N, N)
    cover = sd_box(cv, S(5), S(3), S(26), S(29), S(2))
    body(cv, cover, hx('#a8683a'), hx('#4a2610'), 0, 0, N, N)
    pages = sd_box(cv, S(25), S(5), S(28), S(28), S(0.8))
    cv.paint(fill(pages - SS * 0.8, 1.5), INK)
    cv.paint(fill(pages, 1.5), hx('#f2e6c8'))
    cv.paint(fill(sd_box(cv, S(7), S(3), S(9), S(29)), 1.5), hx('#3a1c0a'), 0.7)
    emb = sd_polygon(cv, star_pts(S(16.5), S(14), S(5.5), S(2.2), 4, -90))
    cv.paint(fill(emb - SS * 0.7, 1.5), hx('#3a1c0a'))
    cv.paint(fill(emb, 1.5), hx('#f2c94c'))
    mark = sd_polygon(cv, [(S(19), S(24)), (S(23), S(24)), (S(23), S(31)), (S(21), S(29)), (S(19), S(31))])
    cv.paint(fill(mark - SS * 0.8, 1.5), INK)
    cv.paint(fill(mark, 1.5), hx('#d0343c'))
    shine(cv, cover, 0.25)
    return finish(cv)


def recipes():
    cv = Canvas(N, N)
    sheet = sd_box(cv, S(6), S(5), S(24), S(27), S(1))
    body(cv, sheet, hx('#fbf0d2'), hx('#c9a76a'), 0, 0, N, N)
    for y in (S(10), S(14), S(18)):
        cv.paint(band(sd_segment(cv, S(9), y, S(20), y), S(0.5), 1.5), hx('#8a6a3a'), 0.7)
    for y in (S(4), S(27)):
        roll = sd_box(cv, S(4), y - S(2), S(26), y + S(2), S(2))
        body(cv, roll, hx('#e2c48a'), hx('#8a5a22'), 0, y - S(2), 0, y + S(2))
    quill = sd_segment(cv, S(15), S(25), S(29), S(9))
    cv.paint(band(quill, S(2.4), 1.5), INK)
    cv.paint(band(quill, S(1.5), 1.5), lerp(WHITE, hx('#d9a441'), linear(cv, S(29), S(9), S(15), S(25))))
    return finish(cv)


def island():
    cv = Canvas(N, N)
    rock = sd_polygon(cv, [(S(3), S(17)), (S(29), S(17)), (S(23), S(24)), (S(16), S(30)), (S(10), S(24))])
    body(cv, rock, hx('#a58a6a'), hx('#4a3424'), 0, S(17), 0, S(30))
    grass = sd_box(cv, S(3), S(15), S(29), S(19), S(2))
    body(cv, grass, hx('#9be36a'), hx('#3f8a2a'), 0, S(15), 0, S(19))
    trunk = sd_box(cv, S(14.5), S(9), S(17.5), S(16))
    body(cv, trunk, hx('#a8683a'), hx('#5a3418'))
    crown = np.minimum(np.minimum(sd_circle(cv, S(16), S(7.5), S(5.5)), sd_circle(cv, S(11.5), S(10), S(4))),
                       sd_circle(cv, S(20.5), S(10), S(4)))
    body(cv, crown, hx('#8ff07a'), hx('#2a7a32'), 0, S(2), 0, S(14))
    cv.paint(fill(sd_circle(cv, S(14), S(5.5), S(1.8)), 1.5), WHITE, 0.45)
    return finish(cv)


def guild():
    cv = Canvas(N, N)
    pole = sd_box(cv, S(5), S(2), S(7.5), S(30))
    body(cv, pole, hx('#e7c27a'), hx('#7a4f12'))
    flag = sd_polygon(cv, [(S(7), S(4)), (S(28), S(4)), (S(28), S(22)), (S(17.5), S(18)), (S(7), S(22))])
    body(cv, flag, hx('#7fb2ff'), hx('#23418a'), 0, S(4), 0, S(22))
    crest = sd_polygon(cv, [(S(12.5), S(7)), (S(22.5), S(7)), (S(22.5), S(12)), (S(17.5), S(16)), (S(12.5), S(12))])
    cv.paint(fill(crest - SS * 0.8, 1.5), INK)
    cv.paint(fill(crest, 1.5), lerp(hx('#ffe9a8'), hx('#d9a441'), linear(cv, 0, S(7), 0, S(16))))
    cv.paint(fill(sd_circle(cv, S(6.2), S(2), S(2.2)), 1.5), hx('#f5c542'))
    return finish(cv)


def stats():
    cv = Canvas(N, N)
    plate = sd_box(cv, S(3), S(4), S(29), S(28), S(3))
    body(cv, plate, hx('#2f5c58'), hx('#11201f'), 0, 0, N, N)
    for x, h, c in ((S(7), S(8), hx('#62d977')), (S(13), S(13), hx('#52baa9')), (S(19), S(17), hx('#7fd3ff'))):
        bar = sd_box(cv, x, S(24) - h, x + S(4), S(24), S(0.8))
        cv.paint(fill(bar - SS * 0.8, 1.5), INK)
        cv.paint(fill(bar, 1.5), lerp(lerp(c, WHITE, 0.4), c, linear(cv, 0, S(24) - h, 0, S(24))))
    cv.paint(band(sd_segment(cv, S(6), S(25.5), S(26), S(25.5)), S(0.6), 1.5), hx('#9fe3d8'), 0.8)
    arrow = sd_polygon(cv, [(S(27), S(6)), (S(27), S(12)), (S(21), S(6))])
    cv.paint(fill(arrow, 1.5), hx('#f2c94c'))
    shine(cv, plate, 0.25)
    return finish(cv)


def storage():
    cv = Canvas(N, N)
    box = sd_box(cv, S(3), S(10), S(29), S(28), S(2))
    body(cv, box, hx('#d98a4a'), hx('#6b3416'), 0, S(10), 0, S(28))
    lid = sd_box(cv, S(2.5), S(5), S(29.5), S(13), S(3))
    body(cv, lid, hx('#f0a866'), hx('#8a4a1e'), 0, S(5), 0, S(13))
    for x in (S(8), S(24)):
        cv.paint(fill(sd_box(cv, x - S(1), S(5), x + S(1), S(28)), 1.5), hx('#3a1c0a'), 0.6)
    lock = sd_box(cv, S(13.5), S(10), S(18.5), S(16), S(1))
    cv.paint(fill(lock - SS * 0.8, 1.5), INK)
    cv.paint(fill(lock, 1.5), hx('#f5c542'))
    shine(cv, lid, 0.3)
    return finish(cv)


def loadouts():
    cv = Canvas(N, N)
    plate = sd_polygon(cv, [(S(8), S(3)), (S(12), S(3)), (S(16), S(7)), (S(20), S(3)), (S(24), S(3)), (S(30), S(9)),
                            (S(26), S(14)), (S(25), S(28)), (S(7), S(28)), (S(6), S(14)), (S(2), S(9))])
    body(cv, plate, hx('#e6edf5'), hx('#56677a'), 0, 0, N, N)
    cv.paint(band(sd_segment(cv, S(16), S(8), S(16), S(27)), S(0.6), 1.5), hx('#3a4656'), 0.7)
    cv.paint(fill(sd_polygon(cv, [(S(10), S(16)), (S(22), S(16)), (S(21), S(19)), (S(11), S(19))]), 1.5), hx('#7fd3ff'), 0.8)
    shine(cv, plate)
    return finish(cv)


def spawns():
    cv = Canvas(N, N)
    stone = sd_polygon(cv, [(S(9), S(29)), (S(10), S(9)), (S(16), S(2)), (S(22), S(9)), (S(23), S(29))])
    body(cv, stone, hx('#b8c4b0'), hx('#4a5446'), 0, 0, N, N)
    rune = sd_polygon(cv, star_pts(S(16), S(15), S(5), S(1.8), 4, -90))
    glow = np.clip(1.0 - np.hypot(cv.x - S(16), cv.y - S(15)) / S(8), 0.0, 1.0)
    cv.paint(glow ** 2 * fill(stone, 1.5), hx('#62d977'), 0.6)
    cv.paint(fill(rune, 1.5), hx('#c8ffd2'))
    base = sd_box(cv, S(5), S(26), S(27), S(30), S(1))
    body(cv, base, hx('#8f9a88'), hx('#3a4236'), 0, S(26), 0, S(30))
    shine(cv, stone, 0.3)
    return finish(cv)


def craft():
    cv = Canvas(N, N)
    top = sd_box(cv, S(3), S(8), S(29), S(28), S(2))
    body(cv, top, hx('#d9a46a'), hx('#6b4520'), 0, S(8), 0, S(28))
    for i in range(3):
        for j in range(3):
            x, y = S(7 + 6.5 * i), S(11 + 5.5 * j)
            cv.paint(fill(sd_box(cv, x, y, x + S(5), y + S(4.2), S(0.6)), 1.5), hx('#4a2c12'), 0.65)
    hammer = sd_segment(cv, S(19), S(18), S(29), S(28))
    cv.paint(band(hammer, S(2.2), 1.5), INK)
    cv.paint(band(hammer, S(1.4), 1.5), hx('#c79a5e'))
    head = sd_polygon(cv, [(S(15), S(4)), (S(21), S(10)), (S(18), S(13)), (S(12), S(7))])
    body(cv, head, hx('#eef3f8'), hx('#6a7a8a'), S(12), S(4), S(21), S(13))
    return finish(cv)


def anvil():
    cv = Canvas(N, N)
    top = sd_polygon(cv, [(S(2), S(8)), (S(24), S(8)), (S(30), S(11)), (S(24), S(15)), (S(6), S(15))])
    body(cv, top, hx('#c8d0da'), hx('#4a525e'), 0, S(8), 0, S(15))
    waist = sd_box(cv, S(11), S(14), S(21), S(22))
    body(cv, waist, hx('#9aa4b0'), hx('#3a4250'))
    foot = sd_box(cv, S(6), S(21), S(26), S(28), S(1.5))
    body(cv, foot, hx('#a8b2be'), hx('#3a4250'), 0, S(21), 0, S(28))
    spark = sd_polygon(cv, star_pts(S(22), S(4.5), S(3.6), S(1.0), 4, -90))
    cv.paint(fill(spark, 1.5), hx('#ffd36b'))
    shine(cv, top, 0.4)
    return finish(cv)


def dev():
    cv = Canvas(N, N)
    teeth = []
    for k in range(16):
        r = S(13.5) if k % 2 == 0 else S(10.5)
        a = math.radians(k * 22.5)
        teeth.append((S(16) + r * math.cos(a), S(16) + r * math.sin(a)))
    gear = sd_polygon(cv, teeth)
    body(cv, gear, hx('#ff9b86'), hx('#8a1e16'), 0, 0, N, N)
    cv.erase(fill(sd_circle(cv, S(16), S(16), S(4.2)), 1.5))
    cv.paint(band(sd_circle(cv, S(16), S(16), S(4.6)), S(0.8), 1.5), INK)
    shine(cv, gear)
    return finish(cv)


def close():
    cv = Canvas(N, N)
    disc = sd_circle(cv, S(16), S(16), S(13))
    body(cv, disc, hx('#ff8a7a'), hx('#8a1e16'), 0, 0, N, N)
    x = np.minimum(sd_segment(cv, S(11), S(11), S(21), S(21)), sd_segment(cv, S(21), S(11), S(11), S(21)))
    cv.paint(band(x, S(2.8), 1.5), hx('#4a0a06'), 0.7)
    cv.paint(band(x, S(1.8), 1.5), WHITE)
    shine(cv, disc, 0.4)
    return finish(cv)


def _padlock(shackle, body_top, body_bot, glow=None):
    cv = Canvas(N, N)
    if glow is not None:
        g = np.clip(1.0 - np.hypot(cv.x - S(16), cv.y - S(18)) / S(15), 0.0, 1.0)
        cv.paint(g ** 1.5, glow, 0.55)
    arc = band(sd_circle(cv, S(16), S(12), S(6.5)), S(2.6), 1.5) * (cv.y < S(14))
    legs = np.maximum(band(sd_segment(cv, S(9.5), S(12), S(9.5), S(16)), S(2.6), 1.5),
                      band(sd_segment(cv, S(22.5), S(12), S(22.5), S(16)), S(2.6), 1.5))
    cv.paint(np.maximum(arc, legs), INK)
    arc_i = band(sd_circle(cv, S(16), S(12), S(6.5)), S(1.6), 1.5) * (cv.y < S(14))
    legs_i = np.maximum(band(sd_segment(cv, S(9.5), S(12), S(9.5), S(16)), S(1.6), 1.5),
                        band(sd_segment(cv, S(22.5), S(12), S(22.5), S(16)), S(1.6), 1.5))
    cv.paint(np.maximum(arc_i, legs_i), shackle)
    lock = sd_box(cv, S(6), S(15), S(26), S(29), S(2.5))
    body(cv, lock, body_top, body_bot, 0, S(15), 0, S(29))
    cv.paint(fill(sd_circle(cv, S(16), S(21), S(2.2)), 1.5), INK)
    cv.paint(fill(sd_box(cv, S(15.2), S(21), S(16.8), S(25.5)), 1.5), INK)
    shine(cv, lock, 0.3)
    return finish(cv)


def locked():
    return _padlock(hx('#9a97a6'), hx('#7d7a8c'), hx('#3a3846'))


def next_unlock():
    return _padlock(hx('#e8ffd0'), hx('#9be36a'), hx('#2f8a40'), glow=hx('#b4ff7a'))


def later():
    cv = Canvas(N, N)
    lock = sd_box(cv, S(6), S(15), S(26), S(29), S(2.5))
    cv.paint(fill(lock, 1.5), hx('#4a4756'), 0.55)
    cv.paint(band(sd_circle(cv, S(16), S(12), S(6.5)), S(1.6), 1.5) * (cv.y < S(15)), hx('#4a4756'), 0.55)
    return finish(cv)


def _nav_disc(top, bottom):
    cv = Canvas(N, N)
    disc = sd_circle(cv, S(16), S(16), S(13))
    body(cv, disc, top, bottom, 0, 0, N, N)
    return cv, disc


def _glyph(cv, sd, colour, edge=hx('#2a1a04')):
    """A white-ish stroke shape with a dark rim, so it reads on any disc colour."""
    cv.paint(fill(sd - S(1.0), 1.5), edge, 0.7)
    cv.paint(fill(sd, 1.5), colour)


def _chevron(cv, direction):
    tip, back_x = (S(11.5), S(19.5)) if direction < 0 else (S(20.5), S(12.5))
    return np.minimum(sd_segment(cv, back_x, S(9.5), tip, S(16)), sd_segment(cv, tip, S(16), back_x, S(22.5))) - S(1.9)


def back():
    cv, disc = _nav_disc(hx('#ffe08a'), hx('#a8671a'))
    shaft = sd_segment(cv, S(13), S(16), S(22.5), S(16)) - S(1.9)
    head = sd_polygon(cv, [(S(8), S(16)), (S(15), S(9.5)), (S(15), S(22.5))])
    _glyph(cv, np.minimum(shaft, head), WHITE)
    shine(cv, disc, 0.4)
    return finish(cv)


def prev_page():
    cv, disc = _nav_disc(hx('#ffe08a'), hx('#a8671a'))
    _glyph(cv, _chevron(cv, -1), WHITE)
    shine(cv, disc, 0.4)
    return finish(cv)


def next_page():
    cv, disc = _nav_disc(hx('#ffe08a'), hx('#a8671a'))
    _glyph(cv, _chevron(cv, 1), WHITE)
    shine(cv, disc, 0.4)
    return finish(cv)


def _page_off(direction):
    cv, disc = _nav_disc(hx('#6e6a7c'), hx('#2c2a36'))
    _glyph(cv, _chevron(cv, direction), hx('#9a96a8'), edge=hx('#1a1822'))
    return finish(cv)


def prev_off():
    return _page_off(-1)


def next_off():
    return _page_off(1)


def _section(accent):
    """Row-label badge for list pages: dark plaque, accent rim, symbol on top."""
    cv = Canvas(N, N)
    plaque = sd_box(cv, S(3), S(5), S(29), S(27), S(4))
    cv.paint(fill(plaque - SS, 1.5), INK)
    cv.paint(fill(plaque, 1.5), lerp(hx('#2c2638'), hx('#16121e'), linear(cv, 0, S(5), 0, S(27))))
    cv.paint(band(plaque + S(1.2), S(0.6), 1.5), accent, 0.85)
    return cv


def _stroke(cv, sd, half, colour):
    cv.paint(band(sd, half + SS, 1.5), INK)
    cv.paint(band(sd, half, 1.5), colour)


def section_combat():
    cv = _section(hx('#ff6b5e'))
    _stroke(cv, sd_segment(cv, S(11), S(21), S(22.5), S(9.5)), S(1.6), hx('#eef3f8'))
    _stroke(cv, sd_segment(cv, S(9), S(17.5), S(14.5), S(23)), S(1.2), hx('#ffcf5a'))
    _stroke(cv, sd_segment(cv, S(8.5), S(23.5), S(11), S(21)), S(1.2), hx('#8a5a2a'))
    return finish(cv)


def section_gathering():
    cv = _section(hx('#7ee08a'))
    _stroke(cv, sd_segment(cv, S(9), S(23.5), S(20), S(12.5)), S(1.2), hx('#c79a5e'))
    arc = sd_circle(cv, S(17.5), S(17.5), S(9))
    head = band(arc, S(1.6), 1.5) * fill(sd_segment(cv, S(11), S(8.5), S(26.5), S(14)) - S(4.5), 1.5)
    cv.paint(np.clip(head * 0 + band(arc, S(2.6), 1.5) * fill(sd_segment(cv, S(11), S(8.5), S(26.5), S(14)) - S(4.5), 1.5), 0, 1), INK)
    cv.paint(head, hx('#dfe8ef'))
    return finish(cv)


def section_fishing():
    cv = _section(hx('#6fd3e8'))
    shank = sd_segment(cv, S(18), S(8), S(18), S(18))
    bend = sd_circle(cv, S(14), S(18), S(4)) * 1.0
    lower = (cv.y > S(18)).astype(float)
    hook = np.minimum(shank, np.where(lower > 0, np.abs(bend), 1e9))
    cv.paint(band(hook, S(1.4) + SS, 1.5), INK)
    cv.paint(band(hook, S(1.4), 1.5), hx('#dfe8ef'))
    _stroke(cv, sd_segment(cv, S(10), S(18), S(10), S(15.5)), S(1.1), hx('#dfe8ef'))
    cv.paint(fill(sd_circle(cv, S(18), S(8), S(1.8)), 1.5), hx('#6fd3e8'))
    return finish(cv)


def empty():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


# icon -> (CustomModelData, vanilla material the button already uses)
ICONS = {
    'manager': (7701, 'nether_star', manager),
    'shop': (7702, 'amethyst_shard', shop),
    'bazaar': (7703, 'chest', bazaar),
    'skills': (7704, 'netherite_scrap', skills),
    'auction': (7705, 'gold_block', auction),
    'bestiary': (7706, 'bone', bestiary),
    'collection': (7707, 'iron_pickaxe', collection),
    'pets': (7708, 'lead', pets),
    'journal': (7709, 'writable_book', journal),
    'recipes': (7710, 'knowledge_book', recipes),
    'island': (7711, 'grass_block', island),
    'guild': (7712, 'yellow_banner', guild),
    'stats': (7713, 'experience_bottle', stats),
    'storage': (7714, 'ender_chest', storage),
    'loadouts': (7715, 'armor_stand', loadouts),
    'spawns': (7716, 'compass', spawns),
    'craft': (7717, 'crafting_table', craft),
    'anvil': (7718, 'anvil', anvil),
    'dev': (7719, 'command_block', dev),
    'close': (7720, 'barrier', close),
    'locked': (7721, 'gray_dye', locked),
    'next': (7722, 'lime_dye', next_unlock),
    'later': (7723, 'light_gray_stained_glass_pane', later),
    # menu navigation, shared by every chrome menu (GuiIcons in Java)
    'back': (7724, 'arrow', back),
    'prev': (7725, 'arrow', prev_page),
    'next_page': (7726, 'arrow', next_page),
    'prev_off': (7727, 'gray_stained_glass_pane', prev_off),
    'next_off': (7728, 'gray_stained_glass_pane', next_off),
    'section_combat': (7729, 'black_stained_glass_pane', section_combat),
    'section_gathering': (7730, 'black_stained_glass_pane', section_gathering),
    'section_fishing': (7731, 'black_stained_glass_pane', section_fishing),
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
    for name, (cmd, material, fn) in ICONS.items():
        fn().save(os.path.join(tex, name + '.png'), optimize=True)
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
