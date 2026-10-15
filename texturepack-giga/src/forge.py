"""Aetherion GIGA pixel forge.

Region maps (which material sits on which pixel) go in; shaded, outlined pixel art comes out.
Light comes from the top-left. Every icon in the GIGA pass goes through this one renderer, so
weapons, armour and charms share one hand: same ramps, same bevel, same outline, same glints.
"""
import math
from PIL import Image

# ----------------------------------------------------------------------------- colour


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _gam(c):
    c = 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055
    return c * 255.0


def _cbrt(v):
    return math.copysign(abs(v) ** (1.0 / 3.0), v)


def oklab(rgb):
    r, g, b = (_lin(v) for v in rgb)
    l = _cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    m = _cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    s = _cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)


def _lab_lin(L, a, b):
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)


def oklch(rgb):
    L, a, b = oklab(rgb)
    return L, math.hypot(a, b), math.degrees(math.atan2(b, a)) % 360


def from_oklch(L, C, h):
    """OKLCH -> sRGB, pulling chroma in until the colour fits the gamut."""
    L = min(max(L, 0.0), 1.0)
    for _ in range(40):
        a, b = C * math.cos(math.radians(h)), C * math.sin(math.radians(h))
        lin = _lab_lin(L, a, b)
        if all(-1e-4 <= v <= 1.0001 for v in lin):
            return tuple(int(round(min(255, max(0, _gam(min(max(v, 0.0), 1.0)))))) for v in lin)
        C *= 0.9
    lin = _lab_lin(L, 0, 0)
    return tuple(int(round(min(255, max(0, _gam(min(max(v, 0.0), 1.0)))))) for v in lin)


def _lerp_h(h0, h1, t):
    d = ((h1 - h0 + 180) % 360) - 180
    return (h0 + d * t) % 360


def ramp(base, n=7, at=None, lo=0.19, hi=0.97, shadow_h=292.0, light_h=None, ss=0.32, ls=0.35,
         c_lo=1.0, c_hi=0.4):
    """Hue-shifted ramp, darkest first. `base` sits at index `at`; shadows drift to `shadow_h`
    (Aetherion violet), lights drift to `light_h` (None = keep hue) and lose chroma toward white."""
    if isinstance(base, str):
        base = hx(base)
    L0, C0, h0 = oklch(base)
    if at is None:
        at = int(round((L0 - lo) / max(1e-6, hi - lo) * (n - 1)))
        at = min(max(at, 1), n - 2)
    out = []
    for i in range(n):
        if i == at:
            out.append(tuple(base))
            continue
        if i < at:
            t = (at - i) / at
            L = L0 + (lo - L0) * t
            h = _lerp_h(h0, shadow_h, ss * t)
            C = C0 * (1 + (c_lo - 1) * t)
        else:
            t = (i - at) / (n - 1 - at)
            L = L0 + (hi - L0) * t
            h = _lerp_h(h0, light_h, ls * t) if light_h is not None else h0
            C = C0 * (1 + (c_hi - 1) * t)
        out.append(from_oklch(L, C, h))
    return out


def lighten(c, t):
    return mix(c, (255, 255, 255), t)


def darken(c, t):
    return mix(c, (0, 0, 0), t)


# Aetherion charcoal-violet: the outline every material leans into.
INK = hx('#120d19')

# ----------------------------------------------------------------------------- materials


class Mat:
    """A material: ramp + base index + shading model.

    model: 'bevel' (edge light/shadow + optional form gradient), 'flat' (base + marks only),
           'gem' (faceted), 'sphere' (round lambert).
    z:     stacking height; a higher-z neighbour on the light side casts a contact shadow,
           a lower-z neighbour counts as an open edge (so parts separate cleanly).
    """

    def __init__(self, ramp_, base=None, model='bevel', hi=(0, 1, 2), lo=(0, 1, 1), form=0.0, z=0,
                 outline=None, out_lit=None, cast=1, spec=True, ink=0.55):
        self.r = list(ramp_)
        self.base = base if base is not None else len(self.r) // 2
        self.model = model
        self.hi, self.lo = hi, lo
        self.form = form
        self.z = z
        self.cast = cast
        self.spec = spec
        self.outline = outline if outline is not None else mix(self.r[0], INK, ink)
        self.out_lit = out_lit if out_lit is not None else mix(self.outline, self.r[min(1, len(self.r) - 1)], 0.38)

    def col(self, i):
        return self.r[min(max(int(i), 0), len(self.r) - 1)]

    def top(self):
        return len(self.r) - 1


# ----------------------------------------------------------------------------- icon


class Icon:
    def __init__(self, mats, w=32, h=32):
        self.w, self.h = w, h
        self.mats = mats
        self.key = [[None] * w for _ in range(h)]
        self.adj = [[0] * w for _ in range(h)]
        self.fix = {}
        self.fx = []
        self.noline = set()       # keys that never get an outline
        self.lineless_px = set()  # empty pixels that must stay empty (negative space kept open)

    # --------------------------------------------------------------- painting
    def inb(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def set(self, x, y, key, adj=0):
        if self.inb(x, y):
            self.key[y][x] = key
            self.adj[y][x] = adj

    def bump(self, x, y, d):
        if self.inb(x, y) and self.key[y][x] is not None:
            self.adj[y][x] += d

    def clear(self, x, y):
        if self.inb(x, y):
            self.key[y][x] = None
            self.adj[y][x] = 0

    def get(self, x, y):
        return self.key[y][x] if self.inb(x, y) else None

    def _apply(self, x, y, v):
        if v == 'X':
            self.clear(x, y)
        elif isinstance(v, tuple) and v[0] == '+':
            self.bump(x, y, v[1])
        elif isinstance(v, tuple):
            self.set(x, y, v[0], v[1])
        else:
            self.set(x, y, v)

    def stamp(self, rows, x0, y0, legend, transparent='. '):
        """Paint ASCII rows. legend[ch] = key | (key, adj) | ('+', d) bump | 'X' clear."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in transparent:
                    continue
                self._apply(x0 + i, y0 + j, legend[ch])

    @staticmethod
    def diag_xy(tip, b, a):
        """Straight frame (a across, b along from the tip) -> screen; blade points top-right."""
        tx, ty = tip
        u0, v0 = tx + ty, tx - ty
        u = u0 + a
        v = v0 - 2 * b - (a & 1)
        return (u + v) // 2, (u - v) // 2

    def diag(self, rows, legend, tip, center, transparent='. '):
        """Paint a vertical sprite (row 0 = tip, column `center` = blade axis) onto the
        45-degree diagonal so it runs from the bottom-left up to `tip` at the top-right."""
        for b, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in transparent:
                    continue
                x, y = self.diag_xy(tip, b, i - center)
                self._apply(x, y, legend[ch])

    def line(self, x0, y0, x1, y1, key, adj=0):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, key, adj)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def disc(self, cx, cy, r, key, adj=0):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.set(x, y, key, adj)

    def spark(self, x, y, core, arm, size=1, tip=None):
        """Four-point glint drawn over everything (light needs no outline)."""
        self.fx.append((x, y, core))
        for k in range(1, size + 1):
            c = arm if (k < size or tip is None) else tip
            for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
                self.fx.append((x + dx, y + dy, c))

    def mirror_x(self):
        self.key = [row[::-1] for row in self.key]
        self.adj = [row[::-1] for row in self.adj]

    def dump(self):
        """Key map as text (for checking exact pixels): key char, '.' empty."""
        return '\n'.join(''.join((k[0] if k else '.') for k in row) for row in self.key)

    def blade(self, axis, v0, vtip, ulo, uhi, key, k=2, adj=0):
        """45-degree blade in screen space: u = x + y in [ulo, uhi], v = x - y from v0 up to a tip
        that loses k v-steps per u-line away from `axis`."""
        for y in range(self.h):
            for x in range(self.w):
                u, v = x + y, x - y
                if ulo <= u <= uhi and v0 <= v <= vtip - k * abs(u - axis):
                    self.set(x, y, key, adj)

    # --------------------------------------------------------------- rendering
    def _components(self):
        comp = {}
        seen = set()
        for y in range(self.h):
            for x in range(self.w):
                k = self.key[y][x]
                if k is None or (x, y) in seen:
                    continue
                stack, pts = [(x, y)], []
                seen.add((x, y))
                while stack:
                    px, py = stack.pop()
                    pts.append((px, py))
                    for nx, ny in ((px + 1, py), (px - 1, py), (px, py + 1), (px, py - 1)):
                        if self.inb(nx, ny) and (nx, ny) not in seen and self.key[ny][nx] == k:
                            seen.add((nx, ny))
                            stack.append((nx, ny))
                for p in pts:
                    comp[p] = pts
        return comp

    def _open(self, m, x, y):
        k = self.get(x, y)
        if k is None:
            return True
        return self.mats[k].z < m.z

    def shade_index(self, x, y, comp, stats):
        k = self.key[y][x]
        m = self.mats[k]
        i = m.base
        x0, y0, x1, y1, cx, cy, n = stats[id(comp[(x, y)])]
        if m.model == 'bevel':
            el = self._open(m, x, y - 1) + self._open(m, x - 1, y)
            ed = self._open(m, x, y + 1) + self._open(m, x + 1, y)
            i += m.hi[el] - m.lo[ed]
            if m.form:
                rx, ry = max(1.0, (x1 - x0) / 2), max(1.0, (y1 - y0) / 2)
                f = ((x - cx) / rx + (y - cy) / ry) / 2
                i -= int(round(m.form * f))
        elif m.model == 'gem':
            rx, ry = max(1.0, (x1 - x0 + 1) / 2), max(1.0, (y1 - y0 + 1) / 2)
            f = ((x - cx) / rx + (y - cy) / ry)
            i -= int(round(f * 1.25))
            ed = self._open(m, x, y + 1) + self._open(m, x + 1, y)
            if ed == 2:
                i -= 1
        elif m.model == 'sphere':
            r = max(1.0, max(x1 - x0 + 1, y1 - y0 + 1) / 2)
            dx, dy = (x - cx) / r, (y - cy) / r
            dz = math.sqrt(max(0.0, 1 - min(1.0, dx * dx + dy * dy)))
            L = (-0.55, -0.62, 0.56)
            lam = max(0.0, dx * L[0] + dy * L[1] + dz * L[2])
            i = int(round(lam * (len(m.r) - 1) * 1.05)) - 1 + (m.base - len(m.r) // 2)
        for nx, ny in ((x, y - 1), (x - 1, y)):
            k2 = self.get(nx, ny)
            if k2 is not None and k2 != k and self.mats[k2].z > m.z:
                i -= m.cast
                break
        return i + self.adj[y][x]

    def render(self):
        comp = self._components()
        stats = {}
        for pts in {id(v): v for v in comp.values()}.values():
            xs = [p[0] for p in pts]
            ys = [p[1] for p in pts]
            stats[id(pts)] = (min(xs), min(ys), max(xs), max(ys), sum(xs) / len(xs), sum(ys) / len(ys), len(pts))
        img = Image.new('RGBA', (self.w, self.h), (0, 0, 0, 0))
        px = img.load()
        for y in range(self.h):
            for x in range(self.w):
                k = self.key[y][x]
                if k is None:
                    continue
                px[x, y] = self.mats[k].col(self.shade_index(x, y, comp, stats)) + (255,)
        done = set()
        for (x, y), pts in comp.items():
            if id(pts) in done:
                continue
            done.add(id(pts))
            m = self.mats[self.key[y][x]]
            if m.model in ('gem', 'sphere') and m.spec and len(pts) >= 4:
                x0, y0, x1, y1, cx, cy, n = stats[id(pts)]
                tx, ty = cx - (x1 - x0 + 1) * 0.22, cy - (y1 - y0 + 1) * 0.22
                best = min(pts, key=lambda p: (p[0] - tx) ** 2 + (p[1] - ty) ** 2)
                px[best[0], best[1]] = lighten(m.col(m.top()), 0.35) + (255,)
        for (x, y), c in self.fix.items():
            if self.inb(x, y):
                px[x, y] = tuple(c[:3]) + ((c[3],) if len(c) > 3 else (255,))
        out = {}
        for y in range(self.h):
            for x in range(self.w):
                if self.key[y][x] is not None or (x, y) in self.lineless_px:
                    continue
                best, lit = None, False
                for nx, ny, from_lit in ((x + 1, y, True), (x, y + 1, True), (x - 1, y, False), (x, y - 1, False)):
                    k = self.get(nx, ny)
                    if k is None or k in self.noline:
                        continue
                    m = self.mats[k]
                    if best is None or m.z > best.z:
                        best, lit = m, from_lit
                    elif m.z == best.z and from_lit:
                        lit = True
                if best is not None:
                    out[(x, y)] = best.out_lit if (lit and not self._shadow_side(x, y)) else best.outline
        for (x, y), c in out.items():
            px[x, y] = c + (255,)
        for x, y, c in self.fx:
            if self.inb(x, y):
                px[x, y] = tuple(c[:3]) + ((c[3],) if len(c) > 3 else (255,))
        return img

    def _shadow_side(self, x, y):
        """True when the shape lies up/left of this empty pixel (bottom/right rim = shadow)."""
        up = self.get(x, y - 1) is not None
        left = self.get(x - 1, y) is not None
        down = self.get(x, y + 1) is not None
        right = self.get(x + 1, y) is not None
        return (up or left) and not (down or right)


# ----------------------------------------------------------------------------- previews


def checker(w, h, a=(52, 50, 62), b=(60, 58, 72), s=8):
    im = Image.new('RGBA', (w, h), a + (255,))
    p = im.load()
    for y in range(h):
        for x in range(w):
            if ((x // s) + (y // s)) % 2:
                p[x, y] = b + (255,)
    return im


def slot_preview(icon_img, scale):
    """The icon in a vanilla inventory slot at a GUI scale (nearest sampling, like the client)."""
    s = 16 * scale
    im = Image.new('RGBA', (s + 2 * scale, s + 2 * scale), (139, 139, 139, 255))
    p = im.load()
    for i in range(im.width):
        for k in range(scale):
            p[i, k] = (55, 55, 55, 255)
            p[i, im.height - 1 - k] = (255, 255, 255, 255)
    for j in range(im.height):
        for k in range(scale):
            p[k, j] = (55, 55, 55, 255)
            p[im.width - 1 - k, j] = (255, 255, 255, 255)
    im.alpha_composite(icon_img.resize((s, s), Image.NEAREST), (scale, scale))
    return im


def hotbar_preview(icon_img, scale):
    s = 16 * scale
    im = Image.new('RGBA', (s + 6 * scale, s + 6 * scale), (36, 36, 36, 255))
    im.alpha_composite(icon_img.resize((s, s), Image.NEAREST), (3 * scale, 3 * scale))
    return im


def sheet(entries, path, zoom=8, cols=4, title=None):
    """entries: [(label, PIL image)] -> zoomed art + slot previews (GUI 2 and 3) + hotbar (GUI 2)."""
    from PIL import ImageDraw, ImageFont
    font = ImageFont.load_default()
    cell_w = 32 * zoom + 16 + 64
    cell_h = 32 * zoom + 24
    rows = (len(entries) + cols - 1) // cols
    top = 22 if title else 0
    W, H = cols * cell_w, rows * cell_h + top
    out = Image.new('RGBA', (W, H), (22, 20, 28, 255))
    d = ImageDraw.Draw(out)
    if title:
        d.text((8, 5), title, fill=(230, 222, 255), font=font)
    for n, (label, im) in enumerate(entries):
        x0 = (n % cols) * cell_w + 8
        y0 = (n // cols) * cell_h + top + 4
        out.alpha_composite(checker(32 * zoom, 32 * zoom), (x0, y0))
        out.alpha_composite(im.resize((im.width * zoom, im.height * zoom), Image.NEAREST), (x0, y0))
        sx = x0 + 32 * zoom + 6
        s2 = slot_preview(im, 2)
        out.alpha_composite(s2, (sx, y0))
        s3 = slot_preview(im, 3)
        out.alpha_composite(s3, (sx, y0 + s2.height + 6))
        h2 = hotbar_preview(im, 2)
        out.alpha_composite(h2, (sx, y0 + s2.height + s3.height + 12))
        d.text((x0, y0 + 32 * zoom + 3), label, fill=(225, 218, 240), font=font)
    out.save(path)
    return path
