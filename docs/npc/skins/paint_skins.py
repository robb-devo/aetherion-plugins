#!/usr/bin/env python3
"""
Aetherion NPC skin painter.

Every NPC skin in AetherionQuests/src/main/resources/skins/cast/ is produced by this
script from the specs below. No third-party textures, no player accounts, no
creator skins: each PNG is original pixel art composed from simple layers
(skin tone, face, hair, hat, torso, sleeves, legs, boots, props).

Run:  python3 paint_skins.py <out_dir> [--preview preview.png]
Needs Pillow. Deterministic (seeded per NPC id), so re-running gives identical files.
"""
import random
import sys
import zlib

from PIL import Image

# ---------------------------------------------------------------------------
# UV layout (64x64 modern skin). Box origin (u, v), size (w, h, d).
# ---------------------------------------------------------------------------
BOXES = {
    # part: (base_uv, overlay_uv, w, h, d)
    "head": ((0, 0), (32, 0), 8, 8, 8),
    "body": ((16, 16), (16, 32), 8, 12, 4),
    "rarm": ((40, 16), (40, 32), 4, 12, 4),
    "larm": ((32, 48), (48, 48), 4, 12, 4),
    "rleg": ((0, 16), (0, 32), 4, 12, 4),
    "lleg": ((16, 48), (0, 48), 4, 12, 4),
}
FACES = ("top", "bottom", "right", "front", "left", "back")
SHADE = {"top": 1.08, "bottom": 0.72, "front": 1.0, "left": 0.9, "right": 0.9, "back": 0.84}


def face_rects(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def shade(c, f):
    if c is None:
        return None
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), c[3])


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (a[3],)


class Painter:
    def __init__(self, npc_id, slim=False):
        self.im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rng = random.Random(zlib.crc32(npc_id.encode()))
        self.slim = slim

    def dims(self, part):
        base, over, w, h, d = BOXES[part]
        if self.slim and part in ("rarm", "larm"):
            w = 3
        return base, over, w, h, d

    def jitter(self, c, amt=5):
        if c is None:
            return None
        j = self.rng.randint(-amt, amt)
        return (max(0, min(255, c[0] + j)), max(0, min(255, c[1] + j)),
                max(0, min(255, c[2] + j)), c[3])

    def paint(self, part, fn, overlay=False, faces=FACES, noise=5, shading=True):
        """fn(face, x, y, w, h) -> rgba or None (skip)."""
        base, over, w, h, d = self.dims(part)
        u, v = over if overlay else base
        for face, (fx, fy, fw, fh) in face_rects(u, v, w, h, d).items():
            if face not in faces:
                continue
            for y in range(fh):
                for x in range(fw):
                    c = fn(face, x, y, fw, fh)
                    if c is None:
                        continue
                    if shading:
                        f = SHADE[face]
                        if face not in ("top", "bottom") and fh > 4:
                            # soft vertical light: brighter at the top of a limb
                            f *= 1.04 - 0.08 * (y / (fh - 1))
                        c = shade(c, f)
                    self.im.putpixel((fx + x, fy + y), self.jitter(c, noise))

    def put(self, part, face, x, y, c, overlay=False):
        base, over, w, h, d = self.dims(part)
        u, v = over if overlay else base
        fx, fy, fw, fh = face_rects(u, v, w, h, d)[face]
        if 0 <= x < fw and 0 <= y < fh and c is not None:
            self.im.putpixel((fx + x, fy + y), c)


# ---------------------------------------------------------------------------
# Palettes
# ---------------------------------------------------------------------------
SKIN = {
    "pale": hexc("f1d2bd"), "light": hexc("e7b996"), "warm": hexc("d49a73"),
    "tan": hexc("b97c55"), "brown": hexc("8d5a3c"), "deep": hexc("5e3a26"),
}
EYES = {
    "brown": hexc("4a2d1a"), "blue": hexc("3a6fb0"), "green": hexc("3f7a45"),
    "grey": hexc("6b7178"), "amber": hexc("b07a22"), "dark": hexc("26201c"),
    "violet": hexc("7a4ab8"), "glow": hexc("7dffb0"),
}
WHITE_EYE = hexc("f4f1ea")


# ---------------------------------------------------------------------------
# Layers
# ---------------------------------------------------------------------------
def layer_skin(p, tone):
    for part in BOXES:
        p.paint(part, lambda f, x, y, w, h: tone, noise=3)


def layer_face(p, s):
    tone = SKIN[s["skin"]]
    eye = EYES[s.get("eyes", "brown")]
    hair = hexc(s["hair"]) if s.get("hair") else None
    brow = shade(hair, 0.8) if hair else shade(tone, 0.6)
    if s.get("brow"):
        brow = hexc(s["brow"])
    # cheeks / nose / mouth
    blush = mix(tone, hexc("d8645a"), 0.28)
    if s.get("rosy"):
        p.put("head", "front", 1, 5, blush)
        p.put("head", "front", 6, 5, blush)
    p.put("head", "front", 3, 5, shade(tone, 0.88))
    p.put("head", "front", 4, 5, shade(tone, 0.84))
    mouth = s.get("mouth", "flat")
    lip = mix(shade(tone, 0.7), hexc("8a3b36"), 0.35)
    if mouth == "flat":
        p.put("head", "front", 3, 6, lip)
        p.put("head", "front", 4, 6, lip)
    elif mouth == "smirk":
        p.put("head", "front", 3, 6, lip)
        p.put("head", "front", 4, 6, lip)
        p.put("head", "front", 5, 6, shade(tone, 0.8))
    elif mouth == "grin":
        for x in (2, 3, 4, 5):
            p.put("head", "front", x, 6, hexc("f0ece2") if x in (3, 4) else lip)
    elif mouth == "gold":
        p.put("head", "front", 3, 6, lip)
        p.put("head", "front", 4, 6, hexc("f2c230"))
        p.put("head", "front", 5, 6, lip)
    # eyes: outer white, inner iris; optional sleepy lid
    ey = s.get("eye_row", 4)
    lx, rx = (1, 2), (5, 6)
    if s.get("eyes_style") == "narrow":
        p.put("head", "front", 2, ey, eye)
        p.put("head", "front", 5, ey, eye)
        p.put("head", "front", 1, ey, shade(tone, 0.85))
        p.put("head", "front", 6, ey, shade(tone, 0.85))
    else:
        p.put("head", "front", lx[0], ey, WHITE_EYE)
        p.put("head", "front", lx[1], ey, eye)
        p.put("head", "front", rx[0], ey, eye)
        p.put("head", "front", rx[1], ey, WHITE_EYE)
    if s.get("brows", True):
        for x in (1, 2, 5, 6):
            p.put("head", "front", x, ey - 1, brow)
        if s.get("frown"):
            p.put("head", "front", 2, ey - 1, shade(brow, 0.8))
            p.put("head", "front", 5, ey - 1, shade(brow, 0.8))
            p.put("head", "front", 1, ey - 1, tone)
            p.put("head", "front", 6, ey - 1, tone)
    if s.get("scar"):
        sc = mix(tone, hexc("a44c4a"), 0.5)
        for (x, y) in ((6, 2), (6, 3), (5, 5)):
            p.put("head", "front", x, y, sc)
    if s.get("freckles"):
        fr = shade(tone, 0.78)
        for (x, y) in ((1, 5), (2, 6), (6, 5), (5, 6)):
            p.put("head", "front", x, y, fr)
    if s.get("soot"):
        so = mix(tone, hexc("2b2622"), 0.45)
        for (x, y) in ((1, 6), (6, 2), (0, 5)):
            p.put("head", "front", x, y, so)
    if s.get("wrinkles"):
        wr = shade(tone, 0.82)
        p.put("head", "front", 0, ey, wr)
        p.put("head", "front", 7, ey, wr)
    # beard / moustache / stubble
    beard = s.get("beard")
    if beard:
        bc = hexc(s.get("beard_color", s.get("hair", "3a2a20")))
        if beard == "full":
            for y in (5, 6, 7):
                for x in range(8):
                    if y == 5 and x in (3, 4):
                        continue
                    p.put("head", "front", x, y, p.jitter(bc if y > 5 or x in (0, 1, 2, 5, 6, 7) else bc, 6))
            p.put("head", "front", 3, 6, shade(bc, 0.55))
            p.put("head", "front", 4, 6, shade(bc, 0.55))
            p.paint("head", lambda f, x, y, w, h: bc if y >= 5 and (x >= 5 if f == "right" else x <= 2) else None,
                    faces=("right", "left"))
            p.paint("head", lambda f, x, y, w, h: bc if y >= 6 else None, faces=("bottom",))
        elif beard == "moustache":
            for x in (2, 3, 4, 5):
                p.put("head", "front", x, 5, bc)
            p.put("head", "front", 1, 6, shade(bc, 0.9))
            p.put("head", "front", 6, 6, shade(bc, 0.9))
        elif beard == "goatee":
            for x in (3, 4):
                p.put("head", "front", x, 7, bc)
            p.put("head", "front", 2, 5, bc)
            p.put("head", "front", 5, 5, bc)
        elif beard == "stubble":
            st = mix(tone, bc, 0.35)
            for y in (6, 7):
                for x in range(1, 7):
                    if not (y == 6 and x in (3, 4)):
                        p.put("head", "front", x, y, st)
        elif beard == "thin_moustache":
            p.put("head", "front", 2, 5, bc)
            p.put("head", "front", 5, 5, bc)
            p.put("head", "front", 3, 5, shade(bc, 1.1))
            p.put("head", "front", 4, 5, shade(bc, 1.1))


def layer_hair(p, s):
    if not s.get("hair"):
        return
    hc = hexc(s["hair"])
    style = s.get("hair_style", "short")
    dark = shade(hc, 0.8)

    def hair_fn(face, x, y, w, h):
        if face == "top":
            if style == "bald":
                return None
            return hc if (x + y) % 5 else dark
        if face == "bottom":
            return None
        if style == "bald":
            # fringe of hair on sides/back only, low
            if face in ("left", "right", "back") and 2 <= y <= 4:
                return hc
            return None
        if face == "front":
            fr = s.get("fringe", 1)
            if y < fr:
                return hc if (x % 3) else dark
            if style in ("long", "bun", "braid") and y < 7 and x in (0, 7):
                return hc
            return None
        if face in ("left", "right"):
            limit = {"short": 3, "messy": 4, "long": 8, "bun": 5, "braid": 6, "crop": 2}.get(style, 3)
            # sideburns near the face edge
            edge = x >= w - 2 if face == "right" else x <= 1
            if y < limit or (style == "short" and edge and y < 5):
                return hc if (x + y) % 4 else dark
            return None
        if face == "back":
            limit = {"short": 5, "messy": 6, "long": 8, "bun": 7, "braid": 8, "crop": 3}.get(style, 5)
            if y < limit:
                return hc if (x + 2 * y) % 5 else dark
            return None
        return None

    p.paint("head", hair_fn, noise=6)
    if style == "messy":
        p.paint("head", lambda f, x, y, w, h: (hc if (x * 7 + y * 3) % 4 == 0 else None)
                if (f == "top" or (f != "bottom" and y < 2)) else None, overlay=True, noise=8)
    if style == "bun":
        # bun on the back of the hat layer
        p.paint("head", lambda f, x, y, w, h: (shade(hc, 1.05) if 2 <= x <= 5 and 1 <= y <= 3 else None)
                if f == "back" else None, overlay=True)
    if style == "braid":
        p.paint("body", lambda f, x, y, w, h: (hc if x in (3, 4) and y < 7 else None) if f == "back" else None,
                overlay=True)
    if style == "ponytail":
        p.paint("head", lambda f, x, y, w, h: (hc if x in (3, 4) and y >= 4 else None) if f == "back" else None,
                overlay=True)


def layer_hat(p, s):
    hat = s.get("hat")
    if not hat:
        return
    kind = hat["kind"]
    c = hexc(hat["color"])
    band = hexc(hat.get("band", "000000")) if hat.get("band") else shade(c, 0.7)
    rows = hat.get("rows", 2)

    def cap(face, x, y, w, h):
        if face == "top":
            return c
        if face == "bottom":
            return None
        if y < rows:
            return c if y < rows - 1 else band
        if face == "front" and kind in ("cap", "flatcap", "visor") and y == rows:
            return shade(c, 0.75)  # brim shadow line
        return None

    def wide(face, x, y, w, h):
        # wide brim approximated: whole top + two rows of sides + band
        if face == "top":
            return c if (x + y) % 3 else shade(c, 0.9)
        if face == "bottom":
            return shade(c, 0.6) if (x in (0, 7) or y in (0, 7)) else None
        if y == 0:
            return c
        if y == 1:
            return band
        if y == 2:
            return shade(c, 0.95)
        return None

    def hood(face, x, y, w, h):
        if face == "top":
            return c
        if face == "bottom":
            return None
        if face == "front":
            if y < 2 or x in (0, 7):
                return c if y < 7 else shade(c, 0.8)
            return None
        return c if (x + y) % 6 else shade(c, 0.85)

    def hardhat(face, x, y, w, h):
        if face == "top":
            return c if (x + y) % 4 else shade(c, 1.1)
        if face == "bottom":
            return None
        if y == 0:
            return shade(c, 1.1)
        if y == 1:
            return c
        if y == 2:
            return shade(c, 0.7)
        return None

    def sou(face, x, y, w, h):  # sou'wester: long back flap
        if face == "top":
            return c
        if face == "bottom":
            return None
        if face == "back":
            return c if y < 7 else shade(c, 0.8)
        if face in ("left", "right"):
            return c if y < 3 or (y < 6 and (x >= 3 if face == "left" else x <= 4)) else None
        return c if y < 2 else None

    def bandana(face, x, y, w, h):
        if face == "top":
            return c if (x + y) % 2 else shade(c, 0.85)
        if face == "bottom":
            return None
        if y < 2:
            return c if (x + y) % 2 else hexc(hat.get("dot", "f2f2f2"))
        if face == "back" and y < 4 and x in (3, 4):
            return c
        return None

    def tophat(face, x, y, w, h):
        if face == "top":
            return c
        if face == "bottom":
            return None
        if y < 2:
            return c
        if y == 2:
            return band
        return None

    def beanie(face, x, y, w, h):
        if face == "top":
            return c if (x // 2 + y) % 2 else shade(c, 0.85)
        if face == "bottom":
            return None
        if y < 2:
            return c if x % 2 else shade(c, 0.88)
        if y == 2:
            return shade(c, 0.75)
        return None

    def headscarf(face, x, y, w, h):
        if face == "top":
            return c
        if face == "bottom":
            return None
        if face == "front":
            return c if y < 2 else None
        return c if y < 4 else (c if face == "back" and y < 6 else None)

    def visor(face, x, y, w, h):
        if face == "front" and y in (1, 2):
            return c
        if face in ("left", "right") and y == 1:
            return band
        if face == "back" and y == 1:
            return band
        return None

    def turban(face, x, y, w, h):
        if face == "top":
            return c if (x + y) % 3 else shade(c, 0.85)
        if face == "bottom":
            return None
        if y < 3:
            return c if (x + y) % 3 else shade(c, 0.8)
        if face == "front" and y == 3 and x in (3, 4):
            return hexc(hat.get("gem", "e0c040"))
        return None

    fn = {"cap": cap, "flatcap": cap, "officer": cap, "wide": wide, "hood": hood, "hardhat": hardhat,
          "sou": sou, "bandana": bandana, "tophat": tophat, "fedora": tophat, "beanie": beanie,
          "headscarf": headscarf, "visor": visor, "turban": turban}[kind]
    p.paint("head", fn, overlay=True, noise=5)
    if kind == "hardhat" and hat.get("lamp"):
        p.put("head", "front", 3, 1, hexc("fff4b0"), overlay=True)
        p.put("head", "front", 4, 1, hexc("ffe070"), overlay=True)
    if kind == "officer":
        p.put("head", "front", 3, 0, hexc(hat.get("badge", "f0c040")), overlay=True)
        p.put("head", "front", 4, 0, hexc(hat.get("badge", "f0c040")), overlay=True)
    if hat.get("feather"):
        fc = hexc(hat["feather"])
        for (x, y) in ((6, 0), (7, 0), (7, 1)):
            p.put("head", "left", x - 4, y, fc, overlay=True)
    if hat.get("goggles"):
        gc = hexc(hat["goggles"])
        for x in range(8):
            p.put("head", "front", x, rows + 1 if kind != "none" else 2, hexc("3a3028"), overlay=True)
        for x in (1, 2, 5, 6):
            p.put("head", "front", x, rows + 1, gc, overlay=True)


def layer_accessories_head(p, s):
    acc = s.get("face_acc")
    if acc == "glasses":
        fr = hexc(s.get("glasses_color", "2a2a2a"))
        ey = s.get("eye_row", 4)
        for x in (0, 3, 4, 7):
            p.put("head", "front", x, ey, fr, overlay=True)
        for x in (1, 2, 5, 6):
            p.put("head", "front", x, ey - 1, fr, overlay=True)
    elif acc == "monocle":
        fr = hexc(s.get("glasses_color", "d8b040"))
        ey = s.get("eye_row", 4)
        lens = hexc(s.get("lens", "b8e0ff"), 150)
        p.put("head", "front", 5, ey, lens, overlay=True)
        p.put("head", "front", 4, ey, fr, overlay=True)
        p.put("head", "front", 5, ey - 1, fr, overlay=True)
        p.put("head", "front", 5, ey + 1, fr, overlay=True)
        p.put("head", "front", 6, ey + 1, fr, overlay=True)
    elif acc == "eyepatch":
        ey = s.get("eye_row", 4)
        p.put("head", "front", 5, ey, hexc("1c1a18"), overlay=True)
        p.put("head", "front", 6, ey, hexc("1c1a18"), overlay=True)
        for x in range(8):
            if x not in (5, 6):
                p.put("head", "front", x, ey - 2 + (1 if x > 5 else 0), None)
    if s.get("earring"):
        p.put("head", "right", 5, 5, hexc(s["earring"]), overlay=True)
    if s.get("pencil_ear"):
        p.put("head", "left", 1, 3, hexc("f2c230"), overlay=True)
        p.put("head", "left", 2, 3, hexc("f2c230"), overlay=True)
        p.put("head", "left", 3, 3, hexc("e8b0a0"), overlay=True)
    if s.get("pipe"):
        p.put("head", "front", 5, 6, hexc("5a3a22"), overlay=True)
        p.put("head", "front", 6, 6, hexc("5a3a22"), overlay=True)
        p.put("head", "front", 6, 5, hexc("3a2616"), overlay=True)
    if s.get("straw"):
        p.put("head", "front", 5, 6, hexc("e8d070"), overlay=True)
        p.put("head", "front", 6, 7, hexc("e8d070"), overlay=True)


def layer_torso(p, s):
    t = s["torso"]
    shirt = hexc(t["shirt"])
    over = t.get("over")  # apron | vest | coat | overalls | robe | none
    oc = hexc(t["over_color"]) if t.get("over_color") else None
    pattern = t.get("pattern")

    def shirt_fn(face, x, y, w, h):
        c = shirt
        if pattern == "check":
            alt = hexc(t.get("pattern_color", "1d1d1d"))
            if (x // 2 + y // 2) % 2 == 0:
                c = alt
            elif (x + y) % 2 == 0:
                c = mix(shirt, alt, 0.25)
        elif pattern == "stripes":
            if y % 2 == 0:
                c = hexc(t.get("pattern_color", "f2f2f2"))
        elif pattern == "vstripes":
            if x % 3 == 0:
                c = hexc(t.get("pattern_color", "f2f2f2"))
        elif pattern == "pinstripe":
            if x % 2 == 0 and face in ("front", "back"):
                c = mix(shirt, hexc(t.get("pattern_color", "c8c8c8")), 0.35)
        if face == "front" and t.get("collar") and y == 0 and x in (2, 3, 4, 5):
            c = hexc(t["collar"])
        return c

    p.paint("body", shirt_fn)
    # sleeves on arms
    sleeve = hexc(t.get("sleeve", t["shirt"]))
    sleeve_len = t.get("sleeve_len", 12)  # rows of sleeve from shoulder
    glove = hexc(t["gloves"]) if t.get("gloves") else None
    cuff = hexc(t["cuff"]) if t.get("cuff") else None

    def arm_fn(face, x, y, w, h):
        if face == "top":
            return sleeve
        if face == "bottom":
            return glove
        if glove and y >= 9:
            return glove
        if cuff and y == sleeve_len - 1 and sleeve_len < 12:
            return cuff
        if y < sleeve_len:
            c = sleeve
            if pattern == "check" and t.get("check_sleeves", True):
                alt = hexc(t.get("pattern_color", "1d1d1d"))
                if (x // 2 + y // 2) % 2 == 0:
                    c = alt
            if pattern == "stripes" and t.get("stripe_sleeves") and y % 2 == 0:
                c = hexc(t.get("pattern_color", "f2f2f2"))
            return c
        return None

    for arm in ("rarm", "larm"):
        p.paint(arm, arm_fn)

    # overlays on the body
    if over == "apron" and oc:
        top = t.get("apron_top", 4)

        def apron(face, x, y, w, h):
            if face == "front":
                if y >= top:
                    return oc if y < h - 1 else shade(oc, 0.8)
                if x in (1, 6):
                    return shade(oc, 0.85)  # straps
                if y == top - 1 and 1 <= x <= 6:
                    return oc
            if face == "back" and x in (1, 6) and y < top + 1:
                return shade(oc, 0.8)
            if face in ("left", "right") and y == top + 1:
                return shade(oc, 0.7)  # tie string
            return None

        p.paint("body", apron, overlay=True)
        if t.get("apron_pocket"):
            pc = shade(oc, 0.78)
            for x in range(2, 6):
                p.put("body", "front", x, top + 3, pc, overlay=True)
    elif over == "vest" and oc:
        def vest(face, x, y, w, h):
            if face == "front":
                if x in (0, 1, 2, 5, 6, 7) and y < 9:
                    return oc if y < 8 else shade(oc, 0.8)
                return None
            if face in ("left", "right", "back") and y < 9:
                return oc
            if face == "top":
                return oc
            return None
        p.paint("body", vest, overlay=True)
    elif over == "coat" and oc:
        def coat(face, x, y, w, h):
            if face == "front":
                if x in (0, 1, 2, 5, 6, 7):
                    if t.get("lapel") and y < 4 and x in (2, 5):
                        return shade(oc, 0.75)
                    return oc
                return None
            if face == "top":
                return oc
            if face in ("left", "right", "back"):
                return oc
            return None
        p.paint("body", coat, overlay=True)
        # coat sleeves over arms
        if t.get("coat_sleeves", True):
            def csleeve(face, x, y, w, h):
                if face == "bottom":
                    return None
                if face == "top":
                    return oc
                lim = t.get("coat_sleeve_len", 11)
                if y < lim:
                    return oc if y < lim - 1 else hexc(t.get("coat_cuff", t["over_color"]))
                return None
            for arm in ("rarm", "larm"):
                p.paint(arm, csleeve, overlay=True)
        if t.get("coat_tail"):
            for leg in ("rleg", "lleg"):
                p.paint(leg, lambda f, x, y, w, h: (oc if y < t["coat_tail"] else None)
                        if f in ("left", "right", "back", "front") and not (f == "front" and (
                            (leg == "rleg" and x == w - 1) or (leg == "lleg" and x == 0))) else None,
                        overlay=True)
    elif over == "overalls" and oc:
        def bib(face, x, y, w, h):
            if face == "front":
                if y >= 5:
                    return oc
                if x in (1, 6):
                    return shade(oc, 0.9)
                if y == 4 and 2 <= x <= 5:
                    return oc
            if face == "back" and x in (1, 6):
                return shade(oc, 0.85)
            if face in ("left", "right") and y >= 6:
                return oc
            if face == "back" and y >= 6:
                return oc
            return None
        p.paint("body", bib, overlay=True)
        p.put("body", "front", 1, 4, hexc("d8c060"), overlay=True)
        p.put("body", "front", 6, 4, hexc("d8c060"), overlay=True)
    elif over == "robe" and oc:
        def robe(face, x, y, w, h):
            if face == "bottom":
                return None
            if face == "front" and t.get("robe_open") and x in (3, 4) and y < 6:
                return None
            return oc if (x + y) % 7 else shade(oc, 0.88)
        p.paint("body", robe, overlay=True)
        for arm in ("rarm", "larm"):
            p.paint(arm, lambda f, x, y, w, h: None if f == "bottom" else (oc if y < 11 else shade(oc, 0.8)),
                    overlay=True)
        for leg in ("rleg", "lleg"):
            p.paint(leg, lambda f, x, y, w, h: None if f in ("bottom", "top") else (
                oc if y < 10 else shade(oc, 0.75)), overlay=True)

    # details
    if t.get("suspenders"):
        sc = hexc(t["suspenders"])
        for y in range(0, 8):
            p.put("body", "front", 1, y, sc)
            p.put("body", "front", 6, y, sc)
            p.put("body", "back", 2, y, sc)
            p.put("body", "back", 5, y, sc)
    if t.get("tie"):
        tc = hexc(t["tie"])
        for y in range(0, 7):
            p.put("body", "front", 3, y, tc)
            p.put("body", "front", 4, y, tc if y < 6 else None)
        p.put("body", "front", 3, 0, shade(tc, 0.8))
        p.put("body", "front", 4, 0, shade(tc, 0.8))
    if t.get("bowtie"):
        bc = hexc(t["bowtie"])
        for x in (2, 3, 4, 5):
            p.put("body", "front", x, 0, bc if x in (2, 5) else shade(bc, 0.8), overlay=True)
    if t.get("scarf"):
        sc = hexc(t["scarf"])
        p.paint("body", lambda f, x, y, w, h: (sc if y < 2 else None) if f in ("front", "back", "left", "right") else
                (sc if f == "top" else None), overlay=True)
        for y in range(2, 6):
            p.put("body", "front", 5, y, shade(sc, 0.9), overlay=True)
    if t.get("buttons"):
        bc = hexc(t["buttons"])
        for y in (2, 5, 8):
            p.put("body", "front", 3 if over != "coat" else 2, y, bc, overlay=True)
            if over == "coat":
                p.put("body", "front", 5, y, bc, overlay=True)
    if t.get("belt"):
        bl = hexc(t["belt"])
        row = t.get("belt_row", 8)
        p.paint("body", lambda f, x, y, w, h: bl if y == row and f != "top" and f != "bottom" else None,
                overlay=t.get("belt_overlay", False))
        buckle = hexc(t.get("buckle", "d8c060"))
        p.put("body", "front", 3, row, buckle, overlay=t.get("belt_overlay", False))
        p.put("body", "front", 4, row, buckle, overlay=t.get("belt_overlay", False))
    if t.get("sash"):
        sh = hexc(t["sash"])
        for i in range(8):
            y = i + 1
            p.put("body", "front", 7 - i, min(y, 11), sh, overlay=True)
    if t.get("strap"):  # diagonal bag strap
        st = hexc(t["strap"])
        for i in range(10):
            p.put("body", "front", i * 8 // 10, i + 1, st, overlay=True)
    if t.get("badge"):
        p.put("body", "front", 5, 2, hexc(t["badge"]), overlay=True)
    if t.get("epaulettes"):
        ep = hexc(t["epaulettes"])
        p.paint("rarm", lambda f, x, y, w, h: ep if (f == "top" or (y == 0 and f != "bottom")) else None, overlay=True)
        p.paint("larm", lambda f, x, y, w, h: ep if (f == "top" or (y == 0 and f != "bottom")) else None, overlay=True)
    if t.get("crystals"):
        cr = hexc(t["crystals"])
        crl = shade(cr, 1.3)
        for arm in ("rarm", "larm"):
            p.paint(arm, lambda f, x, y, w, h: (crl if (x + y) % 2 else cr) if (f == "top" or (y < 2 and f in ("front", "back"))) and (x + y) % 3 != 1 else None,
                    overlay=True)
    if t.get("necklace"):
        nc = hexc(t["necklace"])
        for (x, y) in ((1, 0), (2, 1), (3, 2), (4, 2), (5, 1), (6, 0)):
            p.put("body", "front", x, y, nc, overlay=True)
        p.put("body", "front", 3, 3, hexc(t.get("pendant", "e8e0c8")), overlay=True)
    if t.get("stains"):
        stc = hexc(t["stains"])
        for (x, y) in t.get("stain_px", ((2, 6), (5, 9), (6, 4))):
            p.put("body", "front", x, y, stc, overlay=True)
    if t.get("bracers"):
        br = hexc(t["bracers"])
        for arm in ("rarm", "larm"):
            p.paint(arm, lambda f, x, y, w, h: (br if 7 <= y <= 9 else None) if f not in ("top", "bottom") else None,
                    overlay=True)


def layer_legs(p, s):
    l = s["legs"]
    pants = hexc(l["pants"])
    boots = hexc(l["boots"]) if l.get("boots") else None
    boot_h = l.get("boot_h", 3)
    patch = hexc(l["patch"]) if l.get("patch") else None

    def leg(face, x, y, w, h):
        if face == "bottom":
            return shade(boots, 0.7) if boots else shade(pants, 0.7)
        if face == "top":
            return pants
        if boots and y >= h - boot_h:
            return boots if y < h - 1 else shade(boots, 0.75)
        if patch and face == "front" and y in (6, 7) and x in (1, 2):
            return patch
        if l.get("stripe") and face in ("left", "right") and x == 1:
            return hexc(l["stripe"])
        return pants

    for part in ("rleg", "lleg"):
        p.paint(part, leg)
    if l.get("boot_cuff") and boots:
        bc = hexc(l["boot_cuff"])
        for part in ("rleg", "lleg"):
            p.paint(part, lambda f, x, y, w, h: bc if y == 12 - boot_h and f not in ("top", "bottom") else None,
                    overlay=True)
    if l.get("dress"):
        dc = hexc(l["dress"])
        for part in ("rleg", "lleg"):
            p.paint(part, lambda f, x, y, w, h: None if f in ("top", "bottom") else (dc if y < l.get("dress_len", 7) else None),
                    overlay=True)


def paint_npc(npc_id, spec):
    p = Painter(npc_id, slim=spec.get("slim", False))
    tone = SKIN[spec["skin"]]
    layer_skin(p, tone)
    layer_torso(p, spec)
    layer_legs(p, spec)
    layer_hair(p, spec)
    layer_face(p, spec)
    layer_hat(p, spec)
    layer_accessories_head(p, spec)
    return p.im


# ---------------------------------------------------------------------------
# Cast — the placed Aetherion NPCs (+ generic presets for the /npc editor)
# ---------------------------------------------------------------------------
CAST = {
    # Harbour spine
    "egon": dict(skin="warm", eyes="grey", hair="8a8a88", hair_style="short", rosy=True, wrinkles=True,
                 beard="full", beard_color="9a9894", mouth="flat",
                 hat=dict(kind="flatcap", color="2f5a60", rows=2),
                 torso=dict(shirt="e8e0cc", sleeve="e8e0cc", sleeve_len=6, over="apron", over_color="7a4e2c",
                            apron_top=3, apron_pocket=True, cuff="cfc6b0", belt="3a2616", belt_row=7,
                            belt_overlay=True),
                 legs=dict(pants="3d4650", boots="4a3020", boot_h=3)),
    "lumberjack": dict(skin="light", eyes="green", hair="6b3e1f", hair_style="short", beard="full",
                       beard_color="6b3e1f", mouth="grin",
                       hat=dict(kind="beanie", color="4f6b34", rows=2),
                       torso=dict(shirt="a8322a", pattern="check", pattern_color="1e1a18", sleeve="a8322a",
                                  sleeve_len=8, suspenders="4a3020", gloves="8a6a44"),
                       legs=dict(pants="4b5a6e", boots="5a3a22", boot_h=4, boot_cuff="7a5a3a", patch="3f4c5c")),
    "quartermaster": dict(skin="pale", eyes="blue", hair="b8b8b4", hair_style="crop", beard="moustache",
                          beard_color="c8c8c4", face_acc="glasses", glasses_color="3a3228",
                          torso=dict(shirt="e8e8e0", over="coat", over_color="263e66", lapel=True, buttons="d8b040",
                                     collar="f2f2f2", tie="7a2830", coat_cuff="d8b040"),
                          legs=dict(pants="2a2e38", boots="1f1a16", boot_h=2)),
    "craftsman": dict(skin="tan", eyes="dark", hair="2a221c", hair_style="bald", beard="stubble",
                      beard_color="2a221c", soot=True, mouth="smirk",
                      torso=dict(shirt="6e6e70", sleeve="6e6e70", sleeve_len=4, over="apron", over_color="3a2c24",
                                 apron_top=2, bracers="5a4030", stains="1e1c1a"),
                      legs=dict(pants="3a3632", boots="2a1e16", boot_h=3)),
    "foreman": dict(skin="warm", eyes="brown", hair="3a2a20", hair_style="short", beard="moustache",
                    beard_color="4a3424", soot=True, pipe=True, frown=True,
                    hat=dict(kind="hardhat", color="e8b82a", rows=3, lamp=True),
                    torso=dict(shirt="6a6a6a", sleeve="6a6a6a", sleeve_len=6, over="vest", over_color="e0762a",
                               gloves="7a5a3a", belt="2a2016", belt_row=9, stains="3a3430"),
                    legs=dict(pants="4a4a52", boots="3a2a1c", boot_h=4, patch="5a5a62")),
    "booster_tutor": dict(skin="light", eyes="amber", hair="c86a2a", hair_style="messy", freckles=True,
                          mouth="grin",
                          hat=dict(kind="visor", color="3a3028", band="3a3028", rows=2, goggles="7ad0e0"),
                          torso=dict(shirt="d88a30", sleeve="d88a30", sleeve_len=7, over="apron",
                                     over_color="5a4032", apron_top=4, stains="2a2622", gloves="4a3a2e"),
                          legs=dict(pants="3e3a36", boots="4a2e1e", boot_h=3)),
    "ledger": dict(slim=True, skin="pale", eyes="violet", hair="2c1e2a", hair_style="bun", fringe=1,
                   face_acc="glasses", glasses_color="7a5a2a", pencil_ear=True, mouth="flat", brow="231820",
                   torso=dict(shirt="f2f0ea", over="coat", over_color="5c2a40", lapel=True, collar="ffffff",
                              coat_cuff="f2f0ea", buttons="d8c080", badge="d8c080"),
                   legs=dict(pants="2a2228", boots="1e1618", boot_h=2, dress="3a2230", dress_len=6)),
    "farmer": dict(skin="tan", eyes="blue", hair="c8a060", hair_style="short", beard="stubble",
                   beard_color="a88040", rosy=True, straw=True,
                   hat=dict(kind="wide", color="e0c070", band="8a3a2a", rows=2),
                   torso=dict(shirt="c05a3a", pattern="check", pattern_color="e8d8b0", sleeve="c05a3a",
                              sleeve_len=6, over="overalls", over_color="3e5f8a", gloves="a8844e"),
                   legs=dict(pants="3e5f8a", boots="5a3a22", boot_h=3, patch="5a7aa0")),
    "lark": dict(skin="light", eyes="green", hair="d8b070", hair_style="messy", freckles=True, mouth="grin",
                 hat=dict(kind="none", color="000000") if False else None,
                 torso=dict(shirt="e8e0c8", sleeve="e8e0c8", sleeve_len=5, over="vest", over_color="3f7f7a",
                            strap="6a4a2a", buttons="d8c060"),
                 legs=dict(pants="8a7a5a", boots="5a4030", boot_h=4, boot_cuff="7a5a40")),
    "fisher": dict(skin="warm", eyes="blue", hair="d8d8d0", hair_style="short", beard="full",
                   beard_color="e0e0d8", rosy=True, wrinkles=True,
                   hat=dict(kind="sou", color="f0c020", rows=2),
                   torso=dict(shirt="f0c020", sleeve="f0c020", buttons="2a2a2a",
                              pattern=None, belt="7a6a20", belt_row=8),
                   legs=dict(pants="f0c020", boots="2e3e4a", boot_h=6)),
    "fishmonger": dict(skin="brown", eyes="dark", hair="1e1612", hair_style="crop", beard="goatee",
                       beard_color="1e1612", mouth="grin",
                       hat=dict(kind="bandana", color="c8322a", rows=2, dot="f2f2f2"),
                       torso=dict(shirt="f2f2f2", sleeve="f2f2f2", sleeve_len=4, over="apron", over_color="2a5a8a",
                                  apron_top=3, gloves="e0e0e8", stains="8ab0b8"),
                       legs=dict(pants="2a3040", boots="1e2a32", boot_h=5)),
    "vex": dict(skin="warm", eyes="grey", hair="1c1a18", hair_style="crop", scar=True, frown=True,
                eyes_style="narrow", mouth="flat",
                hat=dict(kind="officer", color="7a1e22", band="1c1a18", rows=2, badge="e8c040"),
                torso=dict(shirt="7a1e22", over="coat", over_color="8a2226", buttons="e8c040", belt="1c1a18",
                           belt_row=8, belt_overlay=True, epaulettes="e8c040", coat_cuff="e8c040", gloves="1c1a18"),
                legs=dict(pants="1e1e24", boots="121214", boot_h=5, stripe="8a2226")),
    "rite_keeper": dict(skin="pale", eyes="amber", hair="e8e8e8", hair_style="long", mouth="flat",
                        hat=dict(kind="hood", color="4a1a1e", rows=2),
                        torso=dict(shirt="2a1a1c", over="robe", over_color="5a1e24", necklace="d8d0b8",
                                   pendant="c83a3a", belt="8a7050", belt_row=7, belt_overlay=True),
                        legs=dict(pants="2a1a1c", boots="1a1214", boot_h=2)),
    "arena_proctor": dict(skin="pale", eyes="grey", hair="2a2622", hair_style="short", fringe=1,
                          beard="thin_moustache", beard_color="2a2622", eyes_style="narrow",
                          torso=dict(shirt="f0ece2", over="vest", over_color="5a5e66", bowtie="8a2a2a",
                                     buttons="d8c080", sleeve="f0ece2"),
                          legs=dict(pants="3a3e46", boots="1a1816", boot_h=2, stripe="2a2e36")),
    "farm_isle_guide": dict(skin="light", eyes="brown", hair="7a4a24", hair_style="braid", freckles=True,
                            straw=True, mouth="smirk",
                            hat=dict(kind="flatcap", color="6a4a2a", rows=2),
                            torso=dict(shirt="e8dcc0", sleeve="e8dcc0", sleeve_len=5, over="overalls",
                                       over_color="4f6f3a", gloves="8a6a44"),
                            legs=dict(pants="4f6f3a", boots="4a3020", boot_h=3)),
    "surveyor": dict(skin="tan", eyes="blue", hair="8a6a44", hair_style="short", beard="stubble",
                     beard_color="6a4a2a", face_acc="monocle", lens="c0e8ff",
                     hat=dict(kind="wide", color="c8b080", band="6a4a2a", rows=2),
                     torso=dict(shirt="c8b890", sleeve="c8b890", sleeve_len=6, strap="5a3a22", buttons="6a5030",
                                belt="5a3a22", belt_row=8),
                     legs=dict(pants="8a7a58", boots="5a3a22", boot_h=5, boot_cuff="7a5a3a")),
    "vince": dict(skin="warm", eyes="dark", hair="121010", hair_style="short", fringe=1, beard="thin_moustache",
                  beard_color="121010", mouth="gold", earring="f2c230",
                  hat=dict(kind="fedora", color="1a1618", band="a8242a", rows=2),
                  torso=dict(shirt="f2eee6", over="coat", over_color="221c24", pattern=None, lapel=True,
                             tie="a8242a", coat_cuff="221c24"),
                  legs=dict(pants="221c24", boots="0e0c0c", boot_h=2)),
    "bar_whisper": dict(skin="deep", eyes="glow", hair="e8f0f0", hair_style="long", beard="goatee",
                        beard_color="e8f0f0",
                        torso=dict(shirt="1e3a52", over="robe", over_color="2a5a7a", sash="6ae070",
                                   necklace="6ae070", pendant="a8ffb8", robe_open=True),
                        legs=dict(pants="1e3a52", boots="16202a", boot_h=2)),
    "eldervale_welcome": dict(skin="light", eyes="green", hair="9a4a2a", hair_style="braid", rosy=True,
                              mouth="smirk",
                              torso=dict(shirt="d8d0c0", over="coat", over_color="2e6a70", scarf="c8b890",
                                         coat_cuff="e8e0d0", belt="4a3020", belt_row=8, belt_overlay=True),
                              legs=dict(pants="4a4640", boots="5a3a22", boot_h=4, boot_cuff="e8e0d0")),
    "eldervale_upgrade": dict(skin="deep", eyes="amber", hair="1a1210", hair_style="bald", beard="full",
                              beard_color="1a1210", soot=True, frown=True,
                              hat=dict(kind="visor", color="3a2e24", band="3a2e24", rows=2, goggles="f0a030"),
                              torso=dict(shirt="5a5452", sleeve="5a5452", sleeve_len=2, over="apron",
                                         over_color="4a3020", apron_top=2, gloves="2a1e16", bracers="6a4a30"),
                              legs=dict(pants="36302c", boots="1e1612", boot_h=4)),
    "merchant": dict(skin="brown", eyes="amber", hair="1c1410", hair_style="short", beard="full",
                     beard_color="1c1410", earring="f2c230", mouth="grin",
                     hat=dict(kind="turban", color="1f7a4a", rows=3, gem="e8c040"),
                     torso=dict(shirt="e8dcc0", over="vest", over_color="5a2a6a", sash="e8c040",
                                buttons="e8c040", sleeve="e8dcc0"),
                     legs=dict(pants="3a2a4a", boots="6a4020", boot_h=3, boot_cuff="e8c040")),
    "isle_clerk": dict(skin="light", eyes="brown", hair="5a3a22", hair_style="short", fringe=1,
                       face_acc="glasses", glasses_color="2a2a2a", mouth="flat",
                       hat=dict(kind="visor", color="3a8a4a", band="2a2a2a", rows=2),
                       torso=dict(shirt="e8ecf0", sleeve="e8ecf0", tie="2e5a3a", cuff="9a2a2a",
                                  sleeve_len=12, belt="2a2016", belt_row=9),
                       legs=dict(pants="3a3e4a", boots="1e1a16", boot_h=2)),
    "forage_pad_guide": dict(skin="light", eyes="green", hair="8a5a2a", hair_style="messy", freckles=True,
                             mouth="grin",
                             hat=dict(kind="hood", color="4f7a3a", rows=2),
                             torso=dict(shirt="6a8a4a", over="coat", over_color="4f7a3a", coat_sleeves=True,
                                        belt="5a3a22", belt_row=8, belt_overlay=True, strap="7a5a3a"),
                             legs=dict(pants="5a4630", boots="3a2a1c", boot_h=4)),
    "liquidator": dict(skin="pale", eyes="violet", hair="eceaf2", hair_style="short", fringe=1,
                       face_acc="monocle", glasses_color="c8a0e8", lens="b070f0", mouth="smirk",
                       torso=dict(shirt="1a1420", over="coat", over_color="46245e", lapel=True, buttons="c8a0e8",
                                  crystals="9a5ae0", gloves="1a1420", coat_cuff="c8a0e8", coat_tail=4),
                       legs=dict(pants="2a1a36", boots="140e1a", boot_h=3)),
    "canopy_clerk": dict(skin="tan", eyes="brown", hair="4a3020", hair_style="short", face_acc="glasses",
                         glasses_color="4a3a20", pencil_ear=True, mouth="flat",
                         torso=dict(shirt="e0dcc8", sleeve="e0dcc8", over="vest", over_color="4a6a34",
                                    buttons="c8a060", strap="6a4a2a"),
                         legs=dict(pants="6a4a2e", boots="3a2a1c", boot_h=3)),
    "root_cellar": dict(skin="warm", eyes="brown", hair="8a5030", hair_style="bun", rosy=True, mouth="grin",
                        hat=dict(kind="headscarf", color="e0b030", rows=2),
                        torso=dict(shirt="a86a4a", sleeve="a86a4a", sleeve_len=5, over="apron",
                                   over_color="f0ece0", apron_top=3, stains="e8e2d0", apron_pocket=True),
                        legs=dict(pants="6a4a3a", boots="4a3020", boot_h=2, dress="7a5a44", dress_len=8)),

    # Idea NPCs (not placed — /questnpc extras hands out anchors)
    "town_crier": dict(skin="warm", eyes="blue", hair="c8c8c0", hair_style="short", beard="moustache",
                       beard_color="d0d0c8", rosy=True, mouth="grin",
                       hat=dict(kind="tophat", color="2a2440", band="c8a040", rows=3, feather="c83a3a"),
                       torso=dict(shirt="f0ece2", over="coat", over_color="7a2430", lapel=True, buttons="e8c040",
                                  coat_cuff="e8c040", sash="e8c040", coat_tail=5),
                       legs=dict(pants="2a2440", boots="1a1414", boot_h=4, boot_cuff="c8a040")),
    "street_sweeper": dict(skin="tan", eyes="green", hair="5a4030", hair_style="messy", beard="stubble",
                           beard_color="4a3424", mouth="smirk",
                           hat=dict(kind="flatcap", color="4a4a52", rows=2),
                           torso=dict(shirt="7a8a6a", sleeve="7a8a6a", sleeve_len=7, over="apron",
                                      over_color="8a7a5a", apron_top=5, gloves="6a5a44", stains="a89a78",
                                      scarf="c86a2a"),
                           legs=dict(pants="4a4a52", boots="3a2a1c", boot_h=3, patch="5a5a62")),
    "lamp_lighter": dict(skin="light", eyes="amber", hair="2a2a30", hair_style="ponytail", mouth="flat",
                         eyes_style="narrow",
                         hat=dict(kind="cap", color="1e2a3a", rows=2),
                         torso=dict(shirt="d8c890", sleeve="d8c890", over="coat", over_color="1e2a3a",
                                    buttons="f0c040", coat_cuff="f0c040", belt="3a2a1a", belt_row=8,
                                    belt_overlay=True, strap="6a4a2a"),
                         legs=dict(pants="2a2a30", boots="1e1a16", boot_h=4)),

    # Generic presets for moderator-made NPCs (/npc editor)
    "preset_worker": dict(skin="tan", eyes="brown", hair="3a2a1e", hair_style="short", beard="stubble",
                          beard_color="3a2a1e",
                          hat=dict(kind="cap", color="7a5a2a", rows=2),
                          torso=dict(shirt="8a6a4a", sleeve="8a6a4a", sleeve_len=6, suspenders="3a2a1e",
                                     gloves="6a4a2e"),
                          legs=dict(pants="4a4640", boots="3a2a1c", boot_h=3)),
    "preset_sailor": dict(skin="warm", eyes="blue", hair="2a1e14", hair_style="short", beard="full",
                          beard_color="2a1e14",
                          hat=dict(kind="beanie", color="1e3a5a", rows=2),
                          torso=dict(shirt="f2f2f2", pattern="stripes", pattern_color="1e3a6a", sleeve="f2f2f2",
                                     stripe_sleeves=True, sleeve_len=8),
                          legs=dict(pants="1e2a3e", boots="1a1a1e", boot_h=3)),
    "preset_farmer": dict(skin="light", eyes="brown", hair="a87a40", hair_style="messy", rosy=True,
                          hat=dict(kind="wide", color="d8b860", band="4a6a2a", rows=2),
                          torso=dict(shirt="6a8a4a", sleeve="6a8a4a", sleeve_len=5, over="overalls",
                                     over_color="6a4a2e"),
                          legs=dict(pants="6a4a2e", boots="4a3020", boot_h=3)),
    "preset_guard": dict(skin="brown", eyes="dark", hair="1a1410", hair_style="crop", frown=True,
                         hat=dict(kind="officer", color="2a3a5a", band="1a1a1e", rows=2, badge="c0c0c8"),
                         torso=dict(shirt="2a3a5a", over="coat", over_color="2e4266", buttons="c0c0c8",
                                    belt="1a1a1e", belt_row=8, belt_overlay=True, gloves="1a1a1e"),
                         legs=dict(pants="1e2230", boots="121214", boot_h=4)),
    "preset_scholar": dict(skin="pale", eyes="grey", hair="a0a0a0", hair_style="short", beard="goatee",
                           beard_color="a0a0a0", face_acc="glasses",
                           torso=dict(shirt="e8e4d8", over="coat", over_color="4a3a5a", lapel=True,
                                      coat_cuff="e8e4d8", buttons="c8b070"),
                           legs=dict(pants="3a3440", boots="1e1a1e", boot_h=2)),
    "preset_mystic": dict(slim=True, skin="deep", eyes="violet", hair="e8e0f0", hair_style="long",
                          hat=dict(kind="hood", color="2a1a3e", rows=2),
                          torso=dict(shirt="1e1428", over="robe", over_color="3a2456", necklace="c8a0e8",
                                     pendant="9a5ae0"),
                          legs=dict(pants="1e1428", boots="120c18", boot_h=2)),
    "preset_scout": dict(skin="tan", eyes="green", hair="5a3a1e", hair_style="messy",
                         hat=dict(kind="bandana", color="4a6a3a", rows=2, dot="6a8a4a"),
                         torso=dict(shirt="6a5a3a", sleeve="6a5a3a", sleeve_len=9, strap="3a2a1a",
                                    belt="3a2a1a", belt_row=8, bracers="4a3a2a"),
                         legs=dict(pants="4a4a3a", boots="3a2a1c", boot_h=5)),
    "preset_rogue": dict(skin="light", eyes="amber", hair="1a1a1a", hair_style="short", fringe=2,
                         face_acc="eyepatch", beard="stubble", beard_color="1a1a1a", mouth="smirk",
                         hat=dict(kind="hood", color="2a2a30", rows=2),
                         torso=dict(shirt="3a2a2a", over="vest", over_color="1e1e24", belt="5a3a22",
                                    belt_row=8, strap="5a3a22", sleeve="3a2a2a"),
                         legs=dict(pants="2a2a30", boots="1a1414", boot_h=5)),
    "preset_miner": dict(skin="warm", eyes="brown", hair="2a1e14", hair_style="short", beard="full",
                         beard_color="2a1e14", soot=True,
                         hat=dict(kind="hardhat", color="c89a2a", rows=3, lamp=True),
                         torso=dict(shirt="5a5a5e", sleeve="5a5a5e", sleeve_len=7, suspenders="3a2a1a",
                                    gloves="6a4a2e", stains="2a2624"),
                         legs=dict(pants="3e3e46", boots="2a2016", boot_h=4)),
    "preset_townsfolk": dict(slim=True, skin="light", eyes="blue", hair="c8903a", hair_style="ponytail",
                             rosy=True, mouth="grin",
                             torso=dict(shirt="7a9ac8", sleeve="7a9ac8", sleeve_len=5, over="apron",
                                        over_color="f2eee4", apron_top=5),
                             legs=dict(pants="4a4a6a", boots="4a3020", boot_h=2, dress="5a6a9a", dress_len=8)),
}


def front_preview(skin, slim=False, scale=6):
    """Flat front + back render with overlays for review."""
    def crop(x, y, w, h):
        return skin.crop((x, y, x + w, y + h))
    aw = 3 if slim else 4
    canvas = Image.new("RGBA", (2 * (8 + 2 * aw) + 6, 34), (54, 58, 64, 255))
    for i, side in enumerate(("front", "back")):
        ox = i * (8 + 2 * aw + 6)
        def face(part, overlay=False):
            base, over, w, h, d = BOXES[part]
            if part in ("rarm", "larm"):
                w = aw
            u, v = over if overlay else base
            return face_rects(u, v, w, h, d)[side]
        parts = [("head", aw, 1), ("body", aw, 9), ("rarm", 0, 9), ("larm", aw + 8, 9),
                 ("rleg", aw, 21), ("lleg", aw + 4, 21)]
        if side == "back":
            parts = [("head", aw, 1), ("body", aw, 9), ("larm", 0, 9), ("rarm", aw + 8, 9),
                     ("lleg", aw, 21), ("rleg", aw + 4, 21)]
        for part, px, py in parts:
            for ov in (False, True):
                r = crop(*face(part, ov))
                canvas.alpha_composite(r, (ox + px, py))
    return canvas.resize((canvas.width * scale, canvas.height * scale), Image.NEAREST)


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    preview = None
    if "--preview" in sys.argv:
        preview = sys.argv[sys.argv.index("--preview") + 1]
    import os
    os.makedirs(out_dir, exist_ok=True)
    tiles = []
    for npc_id, spec in CAST.items():
        spec = {k: v for k, v in spec.items() if v is not None}
        img = paint_npc(npc_id, spec)
        img.save(os.path.join(out_dir, npc_id + ".png"))
        tiles.append((npc_id, front_preview(img, spec.get("slim", False), scale=5)))
    if preview:
        cols = 6
        tw = max(t.width for _, t in tiles)
        th = max(t.height for _, t in tiles) + 14
        rows = (len(tiles) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * tw, rows * th), (30, 32, 36, 255))
        from PIL import ImageDraw
        d = ImageDraw.Draw(sheet)
        for i, (name, t) in enumerate(tiles):
            x, y = (i % cols) * tw, (i // cols) * th
            sheet.alpha_composite(t, (x, y))
            d.text((x + 4, y + t.height + 1), name, fill=(230, 230, 230, 255))
        sheet.save(preview)
    print("painted", len(tiles), "skins ->", out_dir)


if __name__ == "__main__":
    main()
