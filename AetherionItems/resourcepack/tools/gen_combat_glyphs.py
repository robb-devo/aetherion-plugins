"""Generates the combat screen-glyph font (aetherion:combat) for the combat kit.

Each glyph is a 128x128 RGBA PNG shown through the title slot (client draws titles at 4x),
so at height 64 it spans roughly the middle third of the screen. Re-run after edits:

    python AetherionItems/resourcepack/tools/gen_combat_glyphs.py

Writes assets/aetherion/textures/font/combat/*.png and assets/aetherion/font/combat.json.
Chars U+E900..U+E906 are reserved for this font only (see CombatGlyphs.java).
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "aetherion")
TEX = os.path.join(ROOT, "textures", "font", "combat")
S = 128
C = S / 2
random.seed(7)


def canvas():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def glow(img, radius=3, boost=1):
    blurred = img.filter(ImageFilter.GaussianBlur(radius))
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for _ in range(boost):
        out = Image.alpha_composite(out, blurred)
    return Image.alpha_composite(out, img)


def starburst(rays, inner, outer, color, core, width=3):
    img = canvas()
    d = ImageDraw.Draw(img)
    for i in range(rays):
        a = 2 * math.pi * i / rays + random.uniform(-0.08, 0.08)
        r = outer * random.uniform(0.6, 1.0)
        w = width * random.uniform(0.6, 1.3)
        p1 = (C + math.cos(a) * inner, C + math.sin(a) * inner)
        p2 = (C + math.cos(a) * r, C + math.sin(a) * r)
        side = (math.cos(a + math.pi / 2) * w, math.sin(a + math.pi / 2) * w)
        d.polygon([(p1[0] + side[0], p1[1] + side[1]), p2, (p1[0] - side[0], p1[1] - side[1])], fill=color)
    d.ellipse([C - inner, C - inner, C + inner, C + inner], fill=core)
    return img


def parry():
    img = starburst(18, 10, 62, (255, 201, 74, 235), (255, 246, 216, 255), 4)
    d = ImageDraw.Draw(img)
    # crossed blades
    for sgn in (1, -1):
        d.line([(C - 44, C - 44 * sgn), (C + 44, C + 44 * sgn)], fill=(255, 255, 255, 255), width=5)
        d.line([(C - 44, C - 44 * sgn), (C + 44, C + 44 * sgn)], fill=(255, 220, 120, 255), width=2)
    d.ellipse([C - 30, C - 30, C + 30, C + 30], outline=(255, 246, 216, 200), width=2)
    return glow(img, 4, 2)


def guard():
    img = canvas()
    d = ImageDraw.Draw(img)
    for i in range(10):
        a = math.pi * (0.15 + 0.7 * i / 9) + math.pi
        r1, r2 = 26, 26 + random.uniform(10, 26)
        d.line([(C + math.cos(a) * r1, C + 20 + math.sin(a) * r1), (C + math.cos(a) * r2, C + 20 + math.sin(a) * r2)],
               fill=(207, 227, 242, 220), width=2)
    d.arc([C - 30, C - 10, C + 30, C + 50], 200, 340, fill=(230, 240, 250, 255), width=4)
    return glow(img, 2, 1)


def dodge():
    img = canvas()
    d = ImageDraw.Draw(img)
    for i in range(16):
        y = random.uniform(20, 108)
        x1 = random.uniform(0, 40)
        x2 = x1 + random.uniform(50, 88)
        w = random.choice((1, 2, 2, 3))
        col = (159, 227, 255, random.randint(150, 240)) if i % 3 else (255, 255, 255, 230)
        d.line([(x1, y), (x2, y)], fill=col, width=w)
    return glow(img, 2, 1)


def slash(color, hot):
    img = canvas()
    d = ImageDraw.Draw(img)
    pts = []
    for t in range(41):
        f = t / 40
        x = 8 + f * 112
        y = 112 - f * 96 - math.sin(f * math.pi) * 18
        pts.append((x, y))
    for i in range(len(pts) - 1):
        f = i / (len(pts) - 1)
        w = max(1, int(14 * math.sin(f * math.pi)))
        d.line([pts[i], pts[i + 1]], fill=color, width=w)
        d.line([pts[i], pts[i + 1]], fill=hot, width=max(1, w // 3))
    for _ in range(14):
        p = random.choice(pts[8:34])
        y = p[1] + random.uniform(-8, 8)
        d.ellipse([p[0] - 1.5, y - 1.5, p[0] + 1.5, y + 1.5], fill=hot)
    return glow(img, 3, 2)


def winded():
    img = canvas()
    px = img.load()
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - C, y - C) / C
            if r > 0.62:
                a = min(1.0, (r - 0.62) / 0.38)
                px[x, y] = (150, 20, 16, int(200 * a * a))
    return img


def peril():
    img = canvas()
    d = ImageDraw.Draw(img)
    font = None
    for path in ("C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/msyhbd.ttc", "C:/Windows/Fonts/simsun.ttc"):
        if os.path.exists(path):
            font = ImageFont.truetype(path, 92)
            break
    d.ellipse([C - 54, C - 54, C + 54, C + 54], fill=(60, 0, 0, 150), outline=(255, 70, 50, 255), width=4)
    if font is not None:
        d.text((C, C + 2), "危", font=font, anchor="mm", fill=(255, 60, 44, 255), stroke_width=3,
               stroke_fill=(40, 0, 0, 255))
    else:
        d.text((C, C), "!!", anchor="mm", fill=(255, 60, 44, 255))
    return glow(img, 4, 2)


GLYPHS = [
    ("\ue900", "parry", parry),
    ("\ue901", "dodge", dodge),
    ("\ue902", "winded", winded),
    ("\ue903", "peril", peril),
    ("\ue904", "finisher", lambda: slash((255, 154, 60, 240), (255, 240, 200, 255))),
    ("\ue905", "riposte", lambda: slash((229, 139, 255, 240), (255, 230, 255, 255))),
    ("\ue906", "guard", guard),
]


def main():
    os.makedirs(TEX, exist_ok=True)
    providers = []
    for char, name, fn in GLYPHS:
        fn().save(os.path.join(TEX, name + ".png"))
        providers.append({"type": "bitmap", "file": "aetherion:font/combat/%s.png" % name,
                          "ascent": 32, "height": 64, "chars": [char]})
    with open(os.path.join(ROOT, "font", "combat.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, indent=2)
    print("wrote", len(GLYPHS), "glyphs")


if __name__ == "__main__":
    main()
