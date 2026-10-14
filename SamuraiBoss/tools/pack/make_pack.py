#!/usr/bin/env python3
"""
Erzeugt das optionale Resource Pack für SamuraiBoss (Minecraft 1.21.1, pack_format 34).

Inhalt:
  * assets/minecraft/models/item/pink_dye.json  – Pinke Farbe sieht unverändert aus, bekommt nur
    "overrides" für custom_model_data 7310001–7310014 (so funktioniert CMD in 1.21.1)
  * assets/samurai/models/item/*.json           – Katana, Geister-Katana, Saya, Griff, Blüten, Schnitt-Sicheln,
                                                   Siegel, roter Mond, Wappen
  * assets/samurai/textures/...                 – alle Texturen (hier prozedural erzeugt)
  * assets/samurai/font/kanji.json              – Pinsel-Kanji (aus "Yuji Syuku", SIL OFL) + Bruchstück-Glyphen
  * assets/samurai/sounds.json + sounds/*.ogg   – synthetisierte Klänge (Ziehen, Klick, Tempelglocke, Taiko …)
  * assets/samurai/lang/*.json                  – Untertitel

Aufruf: make_pack.py <ausgabeordner> <pfad/zu/YujiSyuku-Regular.ttf>
"""
import json
import math
import os
import random
import shutil
import subprocess
import sys
import tempfile
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = sys.argv[1]
FONT = sys.argv[2]
NS = "samurai"
SR = 44100
rnd = random.Random(1000)


def path(*p):
    full = os.path.join(OUT, *p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


def write_json(obj, *p):
    with open(path(*p), "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def tex(name):
    return f"{NS}:item/{name}"


# =====================================================================================
# Texturen
# =====================================================================================

def save(img, *p):
    img.save(path(*p))


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def katana_texture(ghost=False):
    """64x64-Atlas (1 UV-Einheit = 4 px).
    A (0..8,0..4)  Klingenflanke mit Hamon     B (8..16,0..4) Klingenrücken/-schneide
    C (0..8,4..8)  Tsuka-Ito (Rautenmuster)     D (8..12,4..8) Gold    E (12..16,4..8) Eisen
    F (0..4,8..12) Kissaki-Flanke (Spitze)      G (4..8,8..12) Hamon-Kante hell
    """
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    if ghost:
        steel_top, steel_bot, hamon, edge = (255, 150, 200, 210), (255, 190, 225, 220), (255, 245, 250, 235), (255, 255, 255, 240)
        ito_a, ito_b = (190, 60, 140, 200), (255, 200, 230, 220)
        gold, iron = (255, 210, 235, 230), (220, 120, 190, 220)
    else:
        steel_top, steel_bot, hamon, edge = (96, 104, 118, 255), (176, 184, 196, 255), (236, 240, 245, 255), (255, 255, 255, 255)
        ito_a, ito_b = (22, 18, 24, 255), (232, 226, 214, 255)
        gold, iron = (214, 168, 64, 255), (52, 50, 56, 255)
    # A: Klingenflanke (oben = Rücken, unten = Schneide), welliger Hamon
    for x in range(0, 32):
        wave_y = 10.3 + 1.2 * math.sin(x * 0.55) + 0.6 * math.sin(x * 1.7 + 1)
        for y in range(0, 16):
            if y < wave_y:
                c = lerp(steel_top, steel_bot, y / 16)
            else:
                c = lerp(hamon, edge, (y - wave_y) / max(1, 16 - wave_y))
            # Nioi: feiner heller Saum an der Hamon-Grenze
            if abs(y - wave_y) < 0.8:
                c = lerp(c, edge, 0.6)
            px[x, y] = c
    # B: Rücken/Schneide
    for x in range(32, 64):
        for y in range(0, 16):
            px[x, y] = lerp(steel_bot, edge, y / 16)
    # C: Tsuka-Ito (schwarzes Seidenband, weiße Rochenhaut in Rauten)
    for x in range(0, 32):
        for y in range(16, 32):
            u, v = x % 8, (y - 16) % 8
            diamond = abs(u - 3.5) + abs(v - 3.5) < 2.6
            px[x, y] = ito_b if diamond else ito_a
    # D: Gold
    for x in range(32, 48):
        for y in range(16, 32):
            k = 0.85 + 0.15 * math.sin(x * 0.9 + y * 0.4)
            px[x, y] = (min(255, int(gold[0] * k)), min(255, int(gold[1] * k)), min(255, int(gold[2] * k)), gold[3])
    # E: Eisen (leicht gehämmert)
    for x in range(48, 64):
        for y in range(16, 32):
            n = rnd.randint(-10, 10)
            px[x, y] = (max(0, iron[0] + n), max(0, iron[1] + n), max(0, iron[2] + n), iron[3])
    # F: Kissaki – Flanke mit schräger Spitze (oben rechts abgeschnitten)
    for x in range(0, 16):
        for y in range(32, 48):
            ly = y - 32
            if ly < (x / 16.0) * 13:
                continue  # transparent: die Spitze steigt zur Schneide hin an
            wave_y = 10.5
            c = lerp(steel_top, steel_bot, ly / 16) if ly < wave_y else lerp(hamon, edge, (ly - wave_y) / 5.5)
            px[x, y] = c
    # G: helle Kante
    for x in range(16, 32):
        for y in range(32, 48):
            px[x, y] = edge
    return img


def saya_texture():
    """32x32: schwarzer Lack mit Blütenblättern (0..8 UV) und Gold (8..16)."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 31, 63], fill=(14, 10, 16, 255))
    # Lackglanz
    for y in range(64):
        for x in range(32):
            if (x + y // 3) % 11 == 0:
                img.putpixel((x, y), (40, 30, 44, 255))
    # verstreute Kirschblüten
    for i in range(7):
        cx, cy = rnd.randint(4, 27), rnd.randint(4, 59)
        for k in range(5):
            a = k * 2 * math.pi / 5 + rnd.random()
            d.ellipse([cx + math.cos(a) * 2 - 1.5, cy + math.sin(a) * 2 - 1.5, cx + math.cos(a) * 2 + 1.5, cy + math.sin(a) * 2 + 1.5],
                      fill=(255, 150, 190, 255))
        d.point((cx, cy), fill=(255, 220, 120, 255))
    d.rectangle([32, 0, 63, 63], fill=(214, 168, 64, 255))
    return img


def petal_texture(white=False):
    """Kirschblütenblatt: oval, zur Basis schmal, mit der typischen Kerbe an der Spitze."""
    n = 64
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()
    base = (255, 236, 244) if white else (255, 158, 196)
    deep = (255, 196, 220) if white else (236, 98, 150)
    for y in range(n):
        for x in range(n):
            u = (x - n / 2) / (n * 0.30)          # -1..1 quer
            v = (y - n * 0.08) / (n * 0.86)       # 0 (Spitze) .. 1 (Basis)
            if v < 0 or v > 1:
                continue
            half = math.sin(math.pi * min(1.0, v * 1.05)) ** 0.65 * (1 - 0.35 * v)
            notch = v < 0.16 and abs(u) < 0.22 * (1 - v / 0.16)
            if abs(u) > half or notch:
                continue
            t = v ** 1.4
            c = lerp(base, deep, t * 0.8)
            vein = abs(u) < 0.04 and v > 0.25
            if vein:
                c = lerp(c, deep, 0.5)
            edge = max(0.0, min(1.0, (half - abs(u)) * 6))
            px[x, y] = (c[0], c[1], c[2], int(255 * edge))
    return img


def crescent_texture(glow):
    """Sichel in der Draufsicht: Bogen von +X (rechts) über +Z (unten) nach -X (links)."""
    n = 256
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()
    cx = cy = n / 2
    R = n * 0.47
    for y in range(n):
        for x in range(n):
            dx, dy = x - cx, y - cy
            if dy < -n * 0.02:
                continue
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)  # 0 = rechts, pi/2 = unten
            prof = max(0.0, math.sin(ang))  # dick in der Mitte, spitz an den Enden
            thick = n * 0.11 * prof ** 0.8 + 1
            inner = R - thick
            if r > R + 3 or r < inner - 3:
                continue
            # Kern hell, Rand farbig, weiche Kanten
            t = (r - inner) / max(1, (R - inner))
            core = 1 - abs(t - 0.62) * 2.2
            a = max(0.0, min(1.0, (R + 3 - r) / 3)) * max(0.0, min(1.0, (r - inner + 3) / 3))
            c = lerp(glow, (255, 255, 255), max(0, min(1, core)))
            px[x, y] = (c[0], c[1], c[2], int(255 * a * (0.55 + 0.45 * prof)))
    return img.filter(ImageFilter.GaussianBlur(0.8))


def seal_texture(font_path, ring_text, outer=False):
    n = 256
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pink, white = (255, 143, 179, 255), (255, 245, 250, 255)
    c = n / 2
    if outer:
        for r, w in [(n * 0.485, 3), (n * 0.455, 1)]:
            d.ellipse([c - r, c - r, c + r, c + r], outline=pink, width=w)
        for k in range(36):
            a = k * math.pi * 2 / 36
            r0, r1 = n * 0.455, n * (0.485 if k % 3 == 0 else 0.47)
            d.line([(c + math.cos(a) * r0, c + math.sin(a) * r0), (c + math.cos(a) * r1, c + math.sin(a) * r1)], fill=white, width=2)
        return img.filter(ImageFilter.GaussianBlur(0.5))
    for r, w in [(n * 0.48, 4), (n * 0.40, 2), (n * 0.25, 3)]:
        d.ellipse([c - r, c - r, c + r, c + r], outline=pink if w > 2 else white, width=w)
    # Kanji-Ring zwischen 0.40 und 0.48
    f = ImageFont.truetype(font_path, int(n * 0.085))
    for i, ch in enumerate(ring_text):
        a = i * 2 * math.pi / len(ring_text) - math.pi / 2
        gx, gy = c + math.cos(a) * n * 0.44, c + math.sin(a) * n * 0.44
        glyph = Image.new("RGBA", (int(n * 0.1), int(n * 0.1)), (0, 0, 0, 0))
        ImageDraw.Draw(glyph).text((glyph.width / 2, glyph.height / 2), ch, font=f, fill=white, anchor="mm")
        glyph = glyph.rotate(-math.degrees(a) - 90, resample=Image.BICUBIC)
        img.alpha_composite(glyph, (int(gx - glyph.width / 2), int(gy - glyph.height / 2)))
    # Pentagramm der Klingen
    pts = [(c + math.cos(-math.pi / 2 + k * 4 * math.pi / 5) * n * 0.39, c + math.sin(-math.pi / 2 + k * 4 * math.pi / 5) * n * 0.39) for k in range(6)]
    d.line(pts, fill=pink, width=2)
    # Kirschblüte in der Mitte
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        px_, py_ = c + math.cos(a) * n * 0.11, c + math.sin(a) * n * 0.11
        d.ellipse([px_ - n * 0.085, py_ - n * 0.085, px_ + n * 0.085, py_ + n * 0.085], outline=white, width=2)
    d.ellipse([c - n * 0.03, c - n * 0.03, c + n * 0.03, c + n * 0.03], fill=(255, 210, 120, 255))
    # weicher Schein hinter den Linien
    glow = img.filter(ImageFilter.GaussianBlur(4))
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.alpha_composite(glow)
    out.alpha_composite(img.filter(ImageFilter.GaussianBlur(0.5)))
    return out


def moon_texture():
    n = 256
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()
    c = n / 2
    R = n * 0.36
    craters = [(rnd.uniform(-0.6, 0.6), rnd.uniform(-0.6, 0.6), rnd.uniform(0.05, 0.16)) for _ in range(14)]
    for y in range(n):
        for x in range(n):
            dx, dy = (x - c) / R, (y - c) / R
            r = math.hypot(dx, dy)
            if r <= 1.0:
                shade = 0.75 + 0.25 * (1 - r ** 2) ** 0.5
                col = [200 * shade, 22 * shade, 44 * shade]
                for (cx_, cy_, cr) in craters:
                    if math.hypot(dx - cx_, dy - cy_) < cr:
                        col = [v * 0.82 for v in col]
                px[x, y] = (int(col[0]), int(col[1]), int(col[2]), 255)
            else:
                # Halo
                a = max(0.0, 1 - (r - 1) / 0.38)
                if a > 0:
                    px[x, y] = (255, 90, 120, int(150 * a ** 2))
    return img


def mon_texture(font_path, kanji=None, color=(214, 168, 64, 255)):
    n = 128
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = n / 2
    if kanji:
        d.ellipse([4, 4, n - 4, n - 4], outline=color, width=6)
        f = ImageFont.truetype(font_path, int(n * 0.62))
        d.text((c, c + 2), kanji, font=f, fill=color, anchor="mm")
    else:
        for k in range(5):
            a = -math.pi / 2 + k * 2 * math.pi / 5
            px_, py_ = c + math.cos(a) * n * 0.22, c + math.sin(a) * n * 0.22
            d.ellipse([px_ - n * 0.2, py_ - n * 0.2, px_ + n * 0.2, py_ + n * 0.2], fill=color)
        d.ellipse([c - n * 0.08, c - n * 0.08, c + n * 0.08, c + n * 0.08], fill=(120, 30, 40, 255))
    return img


def menpo_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([8, 10, 56, 60], radius=12, fill=(176, 24, 36, 255))
    d.rectangle([14, 30, 50, 34], fill=(40, 10, 14, 255))
    for x in range(16, 50, 6):
        d.rectangle([x, 40, x + 3, 44], fill=(240, 240, 240, 255))
    d.line([(10, 20), (22, 26)], fill=(255, 255, 255, 255), width=2)
    d.line([(54, 20), (42, 26)], fill=(255, 255, 255, 255), width=2)
    return img


# =====================================================================================
# Modelle
# =====================================================================================

FIXED = {"fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
         "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
         "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}}


def box(frm, to, faces, rotation=None, name=None):
    e = {"from": frm, "to": to, "faces": faces}
    if rotation:
        e["rotation"] = rotation
    if name:
        e["name"] = name
    return e


def all_faces(uv, texture="#t"):
    return {f: {"uv": uv, "texture": texture} for f in ["north", "south", "east", "west", "up", "down"]}


def katana_model(texname):
    """Katana entlang +Z (Ursprung = Griffmitte). Maße wie die Vanilla-Variante im Bossmodell."""
    A = [0, 0, 8, 4]       # Flanke
    B = [8, 0, 16, 4]      # Rücken/Schneide
    C = [0, 4, 8, 8]       # Ito
    D = [8, 4, 12, 8]      # Gold
    E = [12, 4, 16, 8]     # Eisen
    F = [0, 8, 4, 12]      # Kissaki
    els = []
    els.append(box([7.5, 7.5, 4.9], [8.5, 8.5, 5.4], all_faces(D), name="kashira"))
    els.append(box([7.55, 7.55, 5.4], [8.45, 8.45, 11.0], all_faces(C), name="tsuka"))
    els.append(box([6.6, 6.6, 11.0], [9.4, 9.4, 11.4], all_faces(E), name="tsuba"))
    els.append(box([6.8, 6.8, 11.06], [9.2, 9.2, 11.34], all_faces(D), {"angle": 45, "axis": "z", "origin": [8, 8, 11.2]}, "tsuba_gold"))
    els.append(box([7.65, 7.5, 11.4], [8.35, 8.45, 12.0], all_faces(D), name="habaki"))
    # Klinge in drei Abschnitten, leicht ansteigend (Sori – die Krümmung)
    segs = [(12.0, 16.6, 0.0), (16.6, 21.0, 0.12), (21.0, 24.2, 0.3)]
    for i, (z0, z1, dy) in enumerate(segs):
        faces = {
            "east": {"uv": A, "texture": "#t"}, "west": {"uv": A, "texture": "#t"},
            "up": {"uv": B, "texture": "#t"}, "down": {"uv": [8, 3.5, 16, 4], "texture": "#t"},
            "north": {"uv": B, "texture": "#t"}, "south": {"uv": B, "texture": "#t"},
        }
        els.append(box([7.82, 7.5 + dy, z0], [8.18, 8.5 + dy, z1], faces, name=f"blade{i}"))
    tip = {"east": {"uv": F, "texture": "#t"}, "west": {"uv": [4, 8, 0, 12], "texture": "#t"},
           "up": {"uv": B, "texture": "#t"}, "down": {"uv": [8, 3.5, 16, 4], "texture": "#t"},
           "north": {"uv": B, "texture": "#t"}, "south": {"uv": [8, 3.5, 9, 4], "texture": "#t"}}
    els.append(box([7.82, 7.8, 24.2], [8.18, 8.8, 25.6], tip, name="kissaki"))
    return {"credit": "SamuraiBoss", "textures": {"t": tex(texname), "particle": tex(texname)},
            "elements": els, "display": FIXED}


def saya_model():
    L = [0, 0, 8, 16]
    G = [8, 0, 16, 16]
    els = [
        box([7.5, 7.35, -7.2], [8.5, 8.65, 8.0], all_faces(L), name="saya"),
        box([7.4, 7.25, 7.4], [8.6, 8.75, 8.0], all_faces(G), name="koiguchi"),
        box([7.4, 7.25, -7.6], [8.6, 8.75, -6.9], all_faces(G), name="kojiri"),
        box([8.5, 7.7, 4.6], [8.7, 8.3, 5.4], all_faces(G), name="kurikata"),
    ]
    return {"textures": {"t": tex("saya"), "particle": tex("saya")}, "elements": els, "display": FIXED}


def hilt_model():
    C = [0, 4, 8, 8]
    D = [8, 4, 12, 8]
    E = [12, 4, 16, 8]
    els = [
        box([6.6, 6.6, 4.8], [9.4, 9.4, 5.2], all_faces(E), name="tsuba"),
        box([6.8, 6.8, 4.86], [9.2, 9.2, 5.14], all_faces(D), {"angle": 45, "axis": "z", "origin": [8, 8, 5.0]}, "tsuba_gold"),
        box([7.55, 7.55, 5.2], [8.45, 8.45, 10.8], all_faces(C), name="tsuka"),
        box([7.5, 7.5, 10.8], [8.5, 8.5, 11.3], all_faces(D), name="kashira"),
    ]
    return {"textures": {"t": tex("katana"), "particle": tex("katana")}, "elements": els, "display": FIXED}


def plane_xy(texname, frm=(0, 0), to=(16, 16)):
    """Doppelseitige Fläche in der XY-Ebene (zeigt nach ±Z)."""
    faces = {"north": {"uv": [16, 0, 0, 16], "texture": "#t"}, "south": {"uv": [0, 0, 16, 16], "texture": "#t"}}
    return {"textures": {"t": tex(texname), "particle": tex(texname)}, "gui_light": "front",
            "elements": [box([frm[0], frm[1], 8], [to[0], to[1], 8], faces)], "display": FIXED}


def plane_xz(texname):
    """Doppelseitige liegende Fläche (XZ-Ebene). Unterseite mit gespiegelter UV, damit beide Seiten gleich ausgerichtet sind."""
    faces = {"up": {"uv": [0, 0, 16, 16], "texture": "#t"}, "down": {"uv": [0, 16, 16, 0], "texture": "#t"}}
    return {"textures": {"t": tex(texname), "particle": tex(texname)},
            "elements": [box([0, 8, 0], [16, 8, 16], faces)], "display": FIXED}


# =====================================================================================
# Schrift: Pinsel-Kanji + Bruchstücke
# =====================================================================================

MAIN_KANJI = "静嵐散華斬影無一閃剣伏侍桜勝死千本刃雨天降五芒星陣空蝉燕返風桜木斎聖道場花夢春鞘"
FRAGMENT_KANJI = "剣嵐散華伏侍斬静閃桜勝死影無"   # muss zu KanjiFx.FRAGMENT_KANJI passen
FRAGMENTS = 6
CELL = 128
PER_ROW = 16


def glyph_image(font, ch):
    img = Image.new("L", (CELL, CELL), 0)
    d = ImageDraw.Draw(img)
    d.text((CELL / 2, CELL / 2 + 2), ch, font=font, fill=255, anchor="mm")
    return img


def build_font():
    font = ImageFont.truetype(FONT, int(CELL * 0.9))
    chars = []
    for ch in MAIN_KANJI:
        if ch not in chars:
            chars.append(ch)
    rows = [chars[i:i + PER_ROW] for i in range(0, len(chars), PER_ROW)]
    atlas = Image.new("RGBA", (CELL * PER_ROW, CELL * len(rows)), (0, 0, 0, 0))
    for r, row in enumerate(rows):
        for c, ch in enumerate(row):
            g = glyph_image(font, ch)
            white = Image.new("RGBA", (CELL, CELL), (255, 255, 255, 0))
            white.putalpha(g)
            atlas.alpha_composite(white, (c * CELL, r * CELL))
    save(atlas, "assets", NS, "textures", "font", "kanji.png")
    row_strings = []
    for row in rows:
        s = "".join(row)
        s += "\u0000" * (PER_ROW - len(row))
        row_strings.append(s)

    # Bruchstücke: jedes Zeichen in 6 Scherben (Voronoi), mit feinen Rissen
    frag_rows = []
    shard_atlas = Image.new("RGBA", (CELL * 8, CELL * len(FRAGMENT_KANJI)), (0, 0, 0, 0))
    for idx, ch in enumerate(FRAGMENT_KANJI):
        g = np.array(glyph_image(font, ch), dtype=np.float32) / 255.0
        # gleichmäßig verteilte Bruchzentren (Kreis um die Mitte + Zufall), damit jede Scherbe Substanz hat
        a0 = rnd.uniform(0, math.tau)
        seeds = [(64 + math.cos(a0 + k * math.tau / FRAGMENTS) * rnd.uniform(26, 38),
                  64 + math.sin(a0 + k * math.tau / FRAGMENTS) * rnd.uniform(26, 38)) for k in range(FRAGMENTS)]
        yy, xx = np.mgrid[0:CELL, 0:CELL]
        dist = np.stack([np.hypot(xx - sx, yy - sy) for sx, sy in seeds])
        owner = np.argmin(dist, axis=0)
        srt = np.sort(dist, axis=0)
        crack = (srt[1] - srt[0]) < 2.2  # Rissbreite
        row_chars = ""
        for i in range(8):
            cp = 0xE100 + idx * 8 + i
            if i < FRAGMENTS:
                mask = (owner == i) & (~crack)
                a = (g * mask * 255).astype(np.uint8)
                cell = Image.new("RGBA", (CELL, CELL), (255, 255, 255, 0))
                cell.putalpha(Image.fromarray(a, "L"))
                shard_atlas.alpha_composite(cell, (i * CELL, idx * CELL))
                row_chars += chr(cp)
            else:
                row_chars += "\u0000"
        frag_rows.append(row_chars)
    save(shard_atlas, "assets", NS, "textures", "font", "kanji_shards.png")

    font_json = {"providers": [
        {"type": "bitmap", "file": f"{NS}:font/kanji.png", "height": 16, "ascent": 14, "chars": row_strings},
        {"type": "bitmap", "file": f"{NS}:font/kanji_shards.png", "height": 16, "ascent": 14, "chars": frag_rows},
    ]}
    write_json(font_json, "assets", NS, "font", "kanji.json")
    return len(chars)


# =====================================================================================
# Klänge (Synthese → OGG Vorbis)
# =====================================================================================

def env_adsr(n, a, d, s_level, r):
    e = np.ones(n)
    ia, idd, ir = int(a * SR), int(d * SR), int(r * SR)
    if ia > 0:
        e[:ia] = np.linspace(0, 1, ia)
    if idd > 0:
        e[ia:ia + idd] = np.linspace(1, s_level, len(e[ia:ia + idd]))
    e[ia + idd:] = s_level
    if ir > 0:
        e[-ir:] *= np.linspace(1, 0, ir)
    return e


def noise(n, seed=0):
    return np.random.default_rng(seed).uniform(-1, 1, n)


def bandpass(x, f_lo, f_hi):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X[(f < f_lo) | (f > f_hi)] = 0
    return np.fft.irfft(X, len(x))


def sweep_filter(x, f0, f1, width=0.25):
    """Grober zeitvarianter Bandpass: in Blöcken filtern."""
    out = np.zeros_like(x)
    blocks = 24
    L = len(x) // blocks
    for b in range(blocks):
        t = b / (blocks - 1)
        fc = f0 * (f1 / f0) ** t
        seg = x[b * L:(b + 1) * L if b < blocks - 1 else len(x)]
        out[b * L:b * L + len(seg)] = bandpass(seg, fc * (1 - width), fc * (1 + width))
    return out


def tone(freq, dur, decay, phase=0.0):
    t = np.arange(int(dur * SR)) / SR
    return np.sin(2 * np.pi * freq * t + phase) * np.exp(-t / decay)


def norm(x, peak=0.9):
    m = np.max(np.abs(x)) or 1
    return x / m * peak


def to_ogg(x, name):
    x = norm(x)
    tmp = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
    tmp.close()
    with wave.open(tmp.name, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    out = path("assets", NS, "sounds", name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "5", out], check=True)
    os.unlink(tmp.name)


def synth_sounds():
    # Katana ziehen: metallisches "Shiiing" (aufsteigendes Schaben + Nachklingen)
    n = int(0.9 * SR)
    scrape = sweep_filter(noise(n, 1), 1800, 7000, 0.18) * env_adsr(n, 0.02, 0.3, 0.25, 0.4)
    ring = sum(tone(f, 0.9, d, rnd.random()) * a for f, d, a in [(2310, 0.5, 0.5), (3470, 0.4, 0.4), (5220, 0.3, 0.35), (7910, 0.2, 0.2)])
    ring = np.concatenate([np.zeros(int(0.18 * SR)), ring[:n - int(0.18 * SR)]])
    to_ogg(scrape * 0.9 + ring * 0.6, "katana/draw")

    # Klick beim Einrasten (Habaki in den Koiguchi)
    n = int(0.25 * SR)
    t = np.arange(n) / SR
    click = noise(n, 2) * np.exp(-t / 0.004) * 0.8
    click += np.sin(2 * np.pi * 1850 * t) * np.exp(-t / 0.03) * 0.7
    click += np.sin(2 * np.pi * 3720 * t) * np.exp(-t / 0.02) * 0.4
    click += np.sin(2 * np.pi * 310 * t) * np.exp(-t / 0.025) * 0.5
    to_ogg(click, "katana/click")

    # Bonshō – tiefe Tempelglocke mit inharmonischen Teiltönen und Schwebung
    dur = 7.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    f0 = 98.0
    bell = np.zeros(n)
    for ratio, amp, dec in [(0.5, 0.6, 5.5), (1.0, 1.0, 4.5), (1.183, 0.45, 3.5), (1.506, 0.35, 3.0), (2.0, 0.5, 2.8),
                            (2.74, 0.3, 2.0), (3.0, 0.25, 1.6), (4.07, 0.2, 1.1), (5.43, 0.12, 0.8)]:
        f = f0 * ratio
        beat = 1 + 0.25 * np.sin(2 * np.pi * (0.7 + ratio * 0.15) * t)
        bell += amp * np.sin(2 * np.pi * f * t) * np.exp(-t / dec) * beat
    strike = noise(n, 3) * np.exp(-t / 0.015) * 0.4 + np.sin(2 * np.pi * 70 * t) * np.exp(-t / 0.08) * 0.6
    to_ogg(bell + strike, "bell")

    # Taiko (auf F#4 gestimmt ist egal – Trommel), tiefer Schlag mit Fell-Obertönen
    n = int(1.1 * SR)
    t = np.arange(n) / SR
    f = 62 + 40 * np.exp(-t / 0.05)
    phase = 2 * np.pi * np.cumsum(f) / SR
    taiko = np.sin(phase) * np.exp(-t / 0.35)
    taiko += np.sin(2 * np.pi * 168 * t) * np.exp(-t / 0.12) * 0.35
    taiko += bandpass(noise(n, 4), 200, 2500) * np.exp(-t / 0.02) * 0.5
    to_ogg(taiko, "taiko")

    # Schnitt-Whoosh
    n = int(0.45 * SR)
    t = np.arange(n) / SR
    whoosh = sweep_filter(noise(n, 5), 5000, 700, 0.35) * np.exp(-((t - 0.09) / 0.07) ** 2)
    shk = bandpass(noise(n, 6), 3000, 9000) * np.exp(-t / 0.012) * 0.6
    to_ogg(whoosh + shk, "slash")

    # Zeitstillstand: rückwärts anschwellendes Becken + tiefer Bordun + dumpfer Schlag
    dur = 2.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    swell = bandpass(noise(n, 7), 2000, 12000) * (t / dur) ** 3 * (t < 1.4)
    drone = np.sin(2 * np.pi * 55 * t) * np.minimum(1, t / 0.6) * np.exp(-np.maximum(0, t - 1.4) / 0.5) * 0.6
    thud_t = np.maximum(0, t - 1.4)
    thud = np.sin(2 * np.pi * (45 + 30 * np.exp(-thud_t / 0.05)) * thud_t) * np.exp(-thud_t / 0.4) * (t >= 1.4)
    to_ogg(swell * 0.7 + drone + thud, "timestop")

    # Zerspringen (Glas/Kristall)
    n = int(1.2 * SR)
    t = np.arange(n) / SR
    sh = bandpass(noise(n, 8), 2500, 11000) * np.exp(-t / 0.12) * 0.7
    rng = np.random.default_rng(9)
    for _ in range(40):
        st = int(rng.uniform(0, 0.35) * SR)
        f = rng.uniform(3000, 9000)
        L = n - st
        tt = np.arange(L) / SR
        sh[st:] += np.sin(2 * np.pi * f * tt) * np.exp(-tt / rng.uniform(0.02, 0.15)) * rng.uniform(0.05, 0.2)
    to_ogg(sh, "shatter")

    # Windböe
    n = int(2.4 * SR)
    t = np.arange(n) / SR
    w = np.zeros(n)
    base = noise(n, 10)
    for k, (lo, hi) in enumerate([(250, 700), (500, 1300), (900, 2200)]):
        w += bandpass(base, lo, hi) * (0.5 + 0.5 * np.sin(2 * np.pi * (0.4 + k * 0.23) * t + k))
    w *= np.sin(np.pi * t / t[-1]) ** 1.5
    to_ogg(w, "wind")

    # Koto (Karplus-Strong) auf F#4 = 369.99 Hz
    f0 = 369.99
    n = int(1.8 * SR)
    N = int(SR / f0)
    buf = np.random.default_rng(11).uniform(-1, 1, N)
    out = np.zeros(n)
    for i in range(n):
        out[i] = buf[i % N]
        buf[i % N] = 0.996 * 0.5 * (buf[i % N] + buf[(i + 1) % N])
    t = np.arange(n) / SR
    out += np.sin(2 * np.pi * f0 * 2 * t) * np.exp(-t / 0.15) * 0.15  # heller Anschlag
    to_ogg(out * np.exp(-t / 0.9), "koto")

    # Shakuhachi (Bambusflöte) auf F#4 – Grundton, wenige Obertöne, viel Atem
    n = int(1.3 * SR)
    t = np.arange(n) / SR
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.2 * t) * np.minimum(1, t / 0.4)
    ph = 2 * np.pi * np.cumsum(f0 * vib) / SR
    fl = np.sin(ph) + 0.18 * np.sin(2 * ph) + 0.08 * np.sin(3 * ph)
    breath = bandpass(noise(n, 12), 600, 4500) * 0.35
    env = env_adsr(n, 0.09, 0.15, 0.8, 0.35)
    to_ogg((fl + breath) * env, "shakuhachi")


def sounds_json():
    def ev(name, files, subtitle=True, dist=None):
        sounds = []
        for f in files:
            s = {"name": f"{NS}:{f}"}
            if dist:
                s["attenuation_distance"] = dist
            sounds.append(s)
        d = {"sounds": sounds}
        if subtitle:
            d["subtitle"] = f"subtitles.{NS}.{name}"
        return d

    data = {
        "katana.draw": ev("katana.draw", ["katana/draw"], dist=24),
        "katana.click": ev("katana.click", ["katana/click"], dist=24),
        "bell": ev("bell", ["bell"], dist=96),
        "taiko": ev("taiko", ["taiko"], dist=48),
        "slash": ev("slash", ["slash"], dist=24),
        "timestop": ev("timestop", ["timestop"], dist=64),
        "shatter": ev("shatter", ["shatter"], dist=32),
        "wind": ev("wind", ["wind"], dist=32),
        "koto": ev("koto", ["koto"], subtitle=False),
        "shakuhachi": ev("shakuhachi", ["shakuhachi"], subtitle=False),
    }
    write_json(data, "assets", NS, "sounds.json")
    de = {"subtitles.samurai.katana.draw": "Katana wird gezogen", "subtitles.samurai.katana.click": "Klinge rastet ein",
          "subtitles.samurai.bell": "Tempelglocke", "subtitles.samurai.taiko": "Taiko-Trommel",
          "subtitles.samurai.slash": "Schwerthieb", "subtitles.samurai.timestop": "Die Zeit steht still",
          "subtitles.samurai.shatter": "Etwas zerspringt", "subtitles.samurai.wind": "Sturmböe"}
    en = {"subtitles.samurai.katana.draw": "Katana drawn", "subtitles.samurai.katana.click": "Blade clicks home",
          "subtitles.samurai.bell": "Temple bell", "subtitles.samurai.taiko": "Taiko drum",
          "subtitles.samurai.slash": "Sword slash", "subtitles.samurai.timestop": "Time stands still",
          "subtitles.samurai.shatter": "Something shatters", "subtitles.samurai.wind": "Gust of wind"}
    write_json(de, "assets", NS, "lang", "de_de.json")
    write_json(en, "assets", NS, "lang", "en_us.json")


# =====================================================================================
# Zusammenbauen
# =====================================================================================

MODEL_DATA = [  # (custom_model_data, Modellname) – muss zur config.yml passen
    (7310001, "katana"), (7310002, "katana_ghost"), (7310003, "saya"), (7310004, "petal"),
    (7310005, "petal_white"), (7310006, "slash"), (7310007, "slash_pink"), (7310008, "seal"),
    (7310009, "seal_ring"), (7310010, "mon"), (7310011, "crest"), (7310012, "menpo"),
    (7310013, "moon"), (7310014, "hilt"),
]


def main():
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    # pack.mcmeta
    write_json({"pack": {"pack_format": 34,
                         "description": "§d桜木 刃斎 §7– SamuraiBoss (Katana, Blüten, Pinsel-Kanji, Klänge)"}}, "pack.mcmeta")
    # Symbolbild
    icon = Image.new("RGBA", (128, 128), (24, 12, 24, 255))
    d = ImageDraw.Draw(icon)
    for r in range(64, 0, -4):
        a = int(90 * (1 - r / 64))
        d.ellipse([64 - r, 64 - r, 64 + r, 64 + r], fill=(180 + a // 2, 40 + a // 3, 90 + a // 3, 255))
    f = ImageFont.truetype(FONT, 96)
    d.text((64, 68), "桜", font=f, fill=(255, 236, 244, 255), anchor="mm")
    icon.save(path("pack.png"))

    # Texturen
    save(katana_texture(False), "assets", NS, "textures", "item", "katana.png")
    save(katana_texture(True), "assets", NS, "textures", "item", "katana_ghost.png")
    save(saya_texture(), "assets", NS, "textures", "item", "saya.png")
    save(petal_texture(False), "assets", NS, "textures", "item", "petal.png")
    save(petal_texture(True), "assets", NS, "textures", "item", "petal_white.png")
    save(crescent_texture((150, 70, 220)), "assets", NS, "textures", "item", "slash.png")
    save(crescent_texture((255, 120, 170)), "assets", NS, "textures", "item", "slash_pink.png")
    save(seal_texture(FONT, "剣桜散華閃静嵐斬千本刃無"), "assets", NS, "textures", "item", "seal.png")
    save(seal_texture(FONT, "", outer=True), "assets", NS, "textures", "item", "seal_ring.png")
    save(moon_texture(), "assets", NS, "textures", "item", "moon.png")
    save(mon_texture(FONT), "assets", NS, "textures", "item", "mon.png")
    save(mon_texture(FONT, "桜", (255, 236, 244, 255)), "assets", NS, "textures", "item", "crest.png")
    save(menpo_texture(), "assets", NS, "textures", "item", "menpo.png")

    # Modelle
    write_json(katana_model("katana"), "assets", NS, "models", "item", "katana.json")
    write_json(katana_model("katana_ghost"), "assets", NS, "models", "item", "katana_ghost.json")
    write_json(saya_model(), "assets", NS, "models", "item", "saya.json")
    write_json(hilt_model(), "assets", NS, "models", "item", "hilt.json")
    write_json(plane_xy("petal", (3, 3), (13, 13)), "assets", NS, "models", "item", "petal.json")
    write_json(plane_xy("petal_white", (3, 3), (13, 13)), "assets", NS, "models", "item", "petal_white.json")
    write_json(plane_xz("slash"), "assets", NS, "models", "item", "slash.json")
    write_json(plane_xz("slash_pink"), "assets", NS, "models", "item", "slash_pink.json")
    write_json(plane_xy("seal"), "assets", NS, "models", "item", "seal.json")
    write_json(plane_xy("seal_ring"), "assets", NS, "models", "item", "seal_ring.json")
    write_json(plane_xy("moon"), "assets", NS, "models", "item", "moon.json")
    write_json(plane_xy("mon"), "assets", NS, "models", "item", "mon.json")
    write_json(plane_xy("crest"), "assets", NS, "models", "item", "crest.json")
    write_json(plane_xy("menpo"), "assets", NS, "models", "item", "menpo.json")

    # Overrides auf Pinker Farbe (Vanilla-Aussehen bleibt identisch)
    pink = {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/pink_dye"},
            "overrides": [{"predicate": {"custom_model_data": cmd}, "model": f"{NS}:item/{name}"}
                          for cmd, name in sorted(MODEL_DATA)]}
    write_json(pink, "assets", "minecraft", "models", "item", "pink_dye.json")

    n = build_font()
    synth_sounds()
    sounds_json()

    with open(path("CREDITS.txt"), "w", encoding="utf-8") as f:
        f.write("SamuraiBoss Resource Pack\n\n"
                "Pinsel-Kanji gerendert aus der Schrift „Yuji Syuku“ von Kinuta Font Factory,\n"
                "lizenziert unter der SIL Open Font License 1.1 (siehe OFL.txt).\n"
                "Alle übrigen Texturen, Modelle und Klänge wurden für dieses Pack erzeugt.\n")
    ofl = os.path.join(os.path.dirname(FONT), "OFL.txt")
    if os.path.exists(ofl):
        shutil.copy(ofl, path("OFL.txt"))
    print(f"Resource Pack erzeugt: {OUT} ({n} Kanji, {len(FRAGMENT_KANJI) * FRAGMENTS} Bruchstücke, {len(MODEL_DATA)} Modelle)")


if __name__ == "__main__":
    main()
