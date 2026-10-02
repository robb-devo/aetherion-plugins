"""Isometric preview renderer driven by the vanilla 1.21.1 blockstate/model JSON + textures.

Orthographic 2:1 dimetric camera looking from south-east-above: visible faces are up / south / east.
Every unique block state is rendered once into a sprite (parallel projection => position-invariant),
then sprites are painted back-to-front.
"""
from __future__ import annotations

import json
import math
import os
import functools

from PIL import Image, ImageDraw, ImageFont

from .core import parse_state, rotate_state, rotate_pos

MC = os.environ.get("AE_MC_ASSETS", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mc", "assets", "minecraft"))

GRASS = (0x91, 0xBD, 0x59)
FOLIAGE = (0x77, 0xAB, 0x2F)
TINTS = {
    "birch_leaves": (0x80, 0xA7, 0x55),
    "spruce_leaves": (0x61, 0x99, 0x61),
    "lily_pad": (0x20, 0x80, 0x30),
    "water": (0x3F, 0x76, 0xE4),
    "water_cauldron": (0x3F, 0x76, 0xE4),
    "redstone_wire": (0xC0, 0x10, 0x10),
    "pumpkin_stem": (0x9A, 0xB0, 0x30), "melon_stem": (0x9A, 0xB0, 0x30),
}
GRASS_TINTED = ("grass_block", "short_grass", "tall_grass", "fern", "large_fern", "sugar_cane", "potted_fern")
FOLIAGE_TINTED = ("oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "vine")


def tint_for(name):
    short = name.split(":")[1]
    if short in TINTS:
        return TINTS[short]
    if short in GRASS_TINTED:
        return GRASS
    if short in FOLIAGE_TINTED:
        return FOLIAGE
    return (255, 255, 255)


@functools.lru_cache(maxsize=None)
def _json(kind, name):
    name = name.replace("minecraft:", "")
    p = os.path.join(MC, kind, name + ".json")
    if not os.path.exists(p):
        return None
    with open(p, "r", encoding="utf-8") as fh:
        return json.load(fh)


@functools.lru_cache(maxsize=None)
def texture(ref):
    ref = ref.replace("minecraft:", "")
    p = os.path.join(MC, "textures", ref + ".png")
    if not os.path.exists(p):
        img = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
        return img
    img = Image.open(p).convert("RGBA")
    w, h = img.size
    if h > w:  # animated strip: first frame
        img = img.crop((0, 0, w, w))
    return img


@functools.lru_cache(maxsize=None)
def model(name):
    """Return (textures dict, elements list) with parent chain merged."""
    m = _json("models", name)
    if m is None:
        return {}, None
    textures, elements = {}, None
    if "parent" in m and not m["parent"].startswith("builtin/"):
        pt, pe = model(m["parent"])
        textures.update(pt)
        elements = pe
    textures.update(m.get("textures", {}))
    if "elements" in m:
        elements = m["elements"]
    return textures, elements


def _resolve_tex(textures, ref, depth=0):
    while ref.startswith("#") and depth < 10:
        ref = textures.get(ref[1:], "missing")
        depth += 1
    return ref


def _match(cond, props):
    if "OR" in cond:
        return any(_match(c, props) for c in cond["OR"])
    if "AND" in cond:
        return all(_match(c, props) for c in cond["AND"])
    for k, v in cond.items():
        if props.get(k) not in str(v).split("|"):
            return False
    return True


def models_for_state(state):
    name, props = parse_state(state)
    bs = _json("blockstates", name)
    if bs is None:
        return None
    out = []
    if "variants" in bs:
        for key, val in bs["variants"].items():
            ok = True
            if key:
                for kv in key.split(","):
                    k, v = kv.split("=")
                    if props.get(k) != v:
                        ok = False
                        break
            if ok:
                out.append(val[0] if isinstance(val, list) else val)
                break
    else:
        for part in bs.get("multipart", []):
            if "when" not in part or _match(part["when"], props):
                a = part["apply"]
                out.append(a[0] if isinstance(a, list) else a)
    return out


FACE_CORNERS = {
    # tl, tr, br, bl as seen from outside, in element coords (f=from, t=to)
    "north": lambda f, t: [(t[0], t[1], f[2]), (f[0], t[1], f[2]), (f[0], f[1], f[2]), (t[0], f[1], f[2])],
    "south": lambda f, t: [(f[0], t[1], t[2]), (t[0], t[1], t[2]), (t[0], f[1], t[2]), (f[0], f[1], t[2])],
    "east": lambda f, t: [(t[0], t[1], t[2]), (t[0], t[1], f[2]), (t[0], f[1], f[2]), (t[0], f[1], t[2])],
    "west": lambda f, t: [(f[0], t[1], f[2]), (f[0], t[1], t[2]), (f[0], f[1], t[2]), (f[0], f[1], f[2])],
    "up": lambda f, t: [(f[0], t[1], f[2]), (t[0], t[1], f[2]), (t[0], t[1], t[2]), (f[0], t[1], t[2])],
    "down": lambda f, t: [(f[0], f[1], t[2]), (t[0], f[1], t[2]), (t[0], f[1], f[2]), (f[0], f[1], f[2])],
}


def default_uv(face, f, t):
    return {
        "down": [f[0], 16 - t[2], t[0], 16 - f[2]],
        "up": [f[0], f[2], t[0], t[2]],
        "north": [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
        "south": [f[0], 16 - t[1], t[0], 16 - f[1]],
        "west": [f[2], 16 - t[1], t[2], 16 - f[1]],
        "east": [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
    }[face]


def _rot_elem(p, rot):
    if not rot:
        return p
    ox, oy, oz = rot.get("origin", [8, 8, 8])
    a = math.radians(rot["angle"])
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    ax = rot["axis"]
    sc = 1 / math.cos(a) if rot.get("rescale") else 1.0
    if ax == "x":
        y, z = y * c - z * s, y * s + z * c
        y, z = y * sc, z * sc
    elif ax == "y":
        x, z = x * c + z * s, -x * s + z * c
        x, z = x * sc, z * sc
    else:
        x, y = x * c - y * s, x * s + y * c
        x, y = x * sc, y * sc
    return (x + ox, y + oy, z + oz)


def _rot_model_fixed(p, rx, ry):
    x, y, z = p
    for _ in range((rx // 90) % 4):
        y, z = z, 16 - y
    for _ in range((ry // 90) % 4):
        x, z = 16 - z, x
    return (x, y, z)


FALLBACK = {
    # builtin-entity blocks: list of (from, to, texture)
    "chest": [((1, 0, 1), (15, 14, 15), "block/oak_planks"), ((7, 7, 15), (9, 11, 16), "block/iron_block")],
    "trapped_chest": [((1, 0, 1), (15, 14, 15), "block/oak_planks")],
    "decorated_pot": [((1, 0, 1), (15, 13, 15), "block/terracotta"), ((4, 13, 4), (12, 16, 12), "block/terracotta")],
    "bell": [((5, 4, 5), (11, 11, 11), "block/gold_block"), ((4, 4, 4), (12, 5, 12), "block/gold_block")],
}


def fallback_elems(name, props):
    short = name.split(":")[1]
    if short in FALLBACK:
        return FALLBACK[short]
    wood = short.split("_")[0]
    if short.endswith("_wall_hanging_sign"):
        w = short.replace("_wall_hanging_sign", "")
        return [((1, 0, 7), (15, 10, 9), f"block/stripped_{w}_log" if w not in ("bamboo", "crimson", "warped") else f"block/{w}_planks"),
                ((0, 14, 6), (16, 16, 10), f"block/{w}_planks")]
    if short.endswith("_hanging_sign"):
        w = short.replace("_hanging_sign", "")
        return [((1, 0, 7), (15, 10, 9), f"block/stripped_{w}_log" if w not in ("bamboo", "crimson", "warped") else f"block/{w}_planks"),
                ((3, 10, 7.5), (4, 16, 8.5), "block/chain"), ((12, 10, 7.5), (13, 16, 8.5), "block/chain")]
    if short.endswith("_wall_sign"):
        w = short.replace("_wall_sign", "")
        return [((0, 4.5, 0), (16, 12.5, 2), f"block/{w}_planks")]
    if short.endswith("_sign"):
        w = short.replace("_sign", "")
        return [((7.25, 0, 7.25), (8.75, 9, 8.75), f"block/stripped_{w}_log" if w not in ("bamboo", "crimson", "warped") else f"block/{w}_planks"),
                ((0, 9, 7), (16, 17, 9), f"block/{w}_planks")]
    if short.endswith("_wall_banner"):
        c = short.replace("_wall_banner", "")
        return [((1, -14, 0), (15, 16, 1), f"block/{c}_wool"), ((0, 14, 0), (16, 16, 2), "block/oak_planks")]
    if short.endswith("_banner"):
        c = short.replace("_banner", "")
        return [((7, 0, 7), (9, 30, 9), "block/oak_planks"), ((1, 2, 6), (15, 30, 7), f"block/{c}_wool"),
                ((0, 28, 6), (16, 30, 8), "block/oak_planks")]
    if short.endswith("_bed"):
        c = short.replace("_bed", "")
        return [((0, 3, 0), (16, 9, 16), f"block/{c}_wool")]
    if short in ("water", "lava"):
        return [((0, 0, 0), (16, 14, 16), f"block/{short}_still")]
    if short.endswith("_skull") or short.endswith("_head"):
        return [((4, 0, 4), (12, 8, 12), "block/bone_block_side")]
    return None


def _needs_facing_rot(name):
    s = name.split(":")[1]
    return s in ("chest", "trapped_chest", "decorated_pot", "bell") or s.endswith("_wall_sign") or \
        s.endswith("_wall_banner") or s.endswith("_bed") or s.endswith("_wall_hanging_sign") or \
        s.endswith("_hanging_sign")


def quads_for_state(state):
    """List of (corners3d in block units, texture img, uv corners, shade, tint, alpha)."""
    name, props = parse_state(state)
    short = name.split(":")[1]
    quads = []
    refs = models_for_state(state)
    use_fallback = False
    if refs is None or all(model(r["model"])[1] is None for r in refs):
        use_fallback = True
    if not use_fallback:
        for r in refs:
            textures, elements = model(r["model"])
            if not elements:
                continue
            rx, ry = r.get("x", 0), r.get("y", 0)
            for el in elements:
                f, t = el["from"], el["to"]
                shade = el.get("shade", True)
                for face, fd in el.get("faces", {}).items():
                    corners = FACE_CORNERS[face](f, t)
                    corners = [_rot_model_fixed(_rot_elem(c, el.get("rotation")), rx, ry) for c in corners]
                    uv = fd.get("uv") or default_uv(face, f, t)
                    u0, v0, u1, v1 = uv
                    uvc = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
                    k = (fd.get("rotation", 0) // 90) % 4
                    uvc = [uvc[(i - k) % 4] for i in range(4)]
                    tex = texture(_resolve_tex(textures, fd.get("texture", "#missing")))
                    tint = tint_for(name) if "tintindex" in fd else (255, 255, 255)
                    quads.append(([(c[0] / 16, c[1] / 16, c[2] / 16) for c in corners], tex, uvc, shade, tint, 255))
    else:
        els = fallback_elems(name, props)
        if not els:
            return quads
        yrot = 0
        if _needs_facing_rot(name) and "facing" in props:
            yrot = {"south": 0, "west": 90, "north": 180, "east": 270}[props["facing"]]
            if short.endswith("_wall_sign") or short.endswith("_wall_banner"):
                yrot = {"south": 0, "west": 90, "north": 180, "east": 270}[props["facing"]]
        rot_deg = 0.0
        if "rotation" in props and not _needs_facing_rot(name):
            rot_deg = int(props["rotation"]) * 22.5
        alpha = 180 if short == "water" else 255
        tint = tint_for(name)
        for (f, t, tref) in els:
            for face in ("north", "south", "east", "west", "up", "down"):
                corners = FACE_CORNERS[face](f, t)
                if rot_deg:
                    corners = [_rot_elem(c, {"origin": [8, 8, 8], "axis": "y", "angle": -rot_deg}) for c in corners]
                corners = [_rot_model_fixed(c, 0, yrot) for c in corners]
                uv = default_uv(face, [max(0, min(16, v)) for v in f], [max(0, min(16, v)) for v in t])
                u0, v0, u1, v1 = uv
                uvc = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
                quads.append(([(c[0] / 16, c[1] / 16, c[2] / 16) for c in corners], texture(tref), uvc, True, tint, alpha))
    return quads


class Renderer:
    def __init__(self, u=16):
        self.u = u
        self.h = u * math.sqrt(1.5)
        self.cache = {}

    def proj(self, x, y, z):
        return ((x - z) * self.u, (x + z) * self.u / 2 - y * self.h)

    def depth(self, x, y, z):
        return (x + z) * (self.h / self.u) + y

    def sprite(self, state):
        if state in self.cache:
            return self.cache[state]
        U = self.u
        H = self.h
        W = int(4 * U) + 4
        Hh = int(3 * H + 3 * U) + 4
        ox, oy = W // 2, int(2 * H) + 2  # screen offset of block origin corner (0,0,0)
        img = Image.new("RGBA", (W, Hh), (0, 0, 0, 0))
        view = (self.h / self.u, 1.0, self.h / self.u)
        items = []
        for corners, tex, uvc, shade, tint, alpha in quads_for_state(state):
            a, b, c = corners[0], corners[1], corners[3]
            e1 = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
            e2 = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
            # normal (outward) = e2 x e1 for tl,tr,bl ordering seen from outside
            n = (e2[1] * e1[2] - e2[2] * e1[1], e2[2] * e1[0] - e2[0] * e1[2], e2[0] * e1[1] - e2[1] * e1[0])
            ln = math.sqrt(n[0] ** 2 + n[1] ** 2 + n[2] ** 2)
            if ln < 1e-9:
                continue
            n = (n[0] / ln, n[1] / ln, n[2] / ln)
            if n[0] * view[0] + n[1] * view[1] + n[2] * view[2] <= 1e-6:
                continue
            if shade:
                light = 0.5 + 0.5 * max(0, n[1]) + 0.3 * abs(n[2]) + 0.1 * abs(n[0]) if n[1] >= 0 else 0.5
                light = min(1.0, (1.0 * max(0, n[1]) + 0.8 * abs(n[2]) + 0.6 * abs(n[0])) /
                            max(1e-6, max(0, n[1]) + abs(n[2]) + abs(n[0])))
            else:
                light = 1.0
            cx = sum(p[0] for p in corners) / 4
            cy = sum(p[1] for p in corners) / 4
            cz = sum(p[2] for p in corners) / 4
            items.append((self.depth(cx, cy, cz), corners, tex, uvc, light, tint, alpha))
        items.sort(key=lambda it: it[0])
        for _, corners, tex, uvc, light, tint, alpha in items:
            pts = [self.proj(*p) for p in corners]
            pts = [(px + ox, py + oy) for px, py in pts]
            self._draw_quad(img, pts, tex, uvc, light, tint, alpha)
        self.cache[state] = (img, ox, oy)
        return self.cache[state]

    def _draw_quad(self, img, pts, tex, uvc, light, tint, alpha):
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        x0, y0 = int(math.floor(min(xs))), int(math.floor(min(ys)))
        x1, y1 = int(math.ceil(max(xs))), int(math.ceil(max(ys)))
        w, h = x1 - x0 + 1, y1 - y0 + 1
        if w <= 1 or h <= 1:
            return
        tw, th = tex.size
        sc = tw / 16.0
        # affine: screen(p) = P0 + s*(P1-P0) + t*(P3-P0); tex = T0 + s*(T1-T0) + t*(T3-T0)
        P0, P1, P3 = pts[0], pts[1], pts[3]
        T0, T1, T3 = uvc[0], uvc[1], uvc[3]
        ax, ay = P1[0] - P0[0], P1[1] - P0[1]
        bx, by = P3[0] - P0[0], P3[1] - P0[1]
        det = ax * by - bx * ay
        if abs(det) < 1e-6:
            return
        # inverse: s = ( by*(X-P0x) - bx*(Y-P0y))/det ; t = (-ay*(X-P0x) + ax*(Y-P0y))/det
        tux, tuy = (T1[0] - T0[0]) * sc, (T1[1] - T0[1]) * sc
        tvx, tvy = (T3[0] - T0[0]) * sc, (T3[1] - T0[1]) * sc
        # tex_u = T0u*sc + s*tux + t*tvx  ; tex_v = T0v*sc + s*tuy + t*tvy
        # X = x0 + ox_ (output pixel centre)
        sa, sb = by / det, -bx / det
        ta, tb = -ay / det, ax / det
        sc0 = -(sa * (P0[0] - x0) + sb * (P0[1] - y0))
        tc0 = -(ta * (P0[0] - x0) + tb * (P0[1] - y0))
        a = sa * tux + ta * tvx
        b = sb * tux + tb * tvx
        c = T0[0] * sc + sc0 * tux + tc0 * tvx
        d = sa * tuy + ta * tvy
        e = sb * tuy + tb * tvy
        f = T0[1] * sc + sc0 * tuy + tc0 * tvy
        # sample at pixel centres
        c += 0.5 * a + 0.5 * b
        f += 0.5 * d + 0.5 * e
        umin = min(t[0] for t in uvc) * sc
        umax = max(t[0] for t in uvc) * sc
        vmin = min(t[1] for t in uvc) * sc
        vmax = max(t[1] for t in uvc) * sc
        region = tex
        warped = region.transform((w, h), Image.AFFINE, (a, b, c, d, e, f), resample=Image.NEAREST)
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).polygon([(p[0] - x0, p[1] - y0) for p in pts], fill=255, outline=255)
        r, g, bb, al = warped.split()
        mul = (light * tint[0] / 255, light * tint[1] / 255, light * tint[2] / 255)
        r = r.point(lambda v, m=mul[0]: int(v * m))
        g = g.point(lambda v, m=mul[1]: int(v * m))
        bb = bb.point(lambda v, m=mul[2]: int(v * m))
        from PIL import ImageChops
        al = ImageChops.multiply(al, mask)
        if alpha < 255:
            al = al.point(lambda v, k=alpha: v * k // 255)
        al = al.point(lambda v: 255 if v >= 128 else (v if v > 40 else 0))
        warped = Image.merge("RGBA", (r, g, bb, al))
        img.alpha_composite(warped, (x0, y0))

    def render(self, blocks, bg=(236, 238, 232), title=None, pad=24, rot=0, cutaway_y=None):
        """blocks: dict pos->state. rot rotates the scene clockwise k*90 (to see other faces)."""
        if rot % 4:
            xs = [p[0] for p in blocks]
            zs = [p[2] for p in blocks]
            mx, mz = min(xs), min(zs)
            w, l = max(xs) - mx + 1, max(zs) - mz + 1
            nb = {}
            for (x, y, z), st in blocks.items():
                rx, rz = rotate_pos(x - mx, z - mz, rot, w, l)
                nb[(rx, y, rz)] = rotate_state(st, rot)
            blocks = nb
        if cutaway_y is not None:
            blocks = {p: s for p, s in blocks.items() if p[1] <= cutaway_y}
        items = []
        minx = miny = 1e9
        maxx = maxy = -1e9
        for (x, y, z), st in blocks.items():
            spr, ox, oy = self.sprite(st)
            sx, sy = self.proj(x, y, z)
            px, py = int(round(sx)) - ox, int(round(sy)) - oy
            items.append((self.depth(x + .5, y + .5, z + .5), px, py, spr))
            bb = spr.getbbox()
            if bb:
                minx = min(minx, px + bb[0]); maxx = max(maxx, px + bb[2])
                miny = min(miny, py + bb[1]); maxy = max(maxy, py + bb[3])
        items.sort(key=lambda it: it[0])
        top = 30 if title else 0
        W = int(maxx - minx) + 2 * pad
        H = int(maxy - miny) + 2 * pad + top
        canvas = Image.new("RGBA", (W, H), bg + (255,))
        for _, px, py, spr in items:
            canvas.alpha_composite(spr, (int(px - minx + pad), int(py - miny + pad + top)))
        if title:
            d = ImageDraw.Draw(canvas)
            try:
                font = ImageFont.truetype("arialbd.ttf", 16)
            except Exception:
                font = ImageFont.load_default()
            d.text((pad, 8), title, fill=(40, 44, 40, 255), font=font)
        return canvas.convert("RGB")


def unresolved_states(states):
    bad = []
    for st in states:
        name = parse_state(st)[0]
        refs = models_for_state(st)
        if refs is None:
            if fallback_elems(name, parse_state(st)[1]) is None and not name.endswith("air"):
                bad.append(st)
        elif not refs and fallback_elems(name, parse_state(st)[1]) is None:
            bad.append(st)
    return bad
