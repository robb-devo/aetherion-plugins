#!/usr/bin/env python3
"""Draws the sea chart of the main island: original ink on paper, nothing of the source image survives
but the shape of the coast, the lakes and the roads. Text is added by the page, so it can be translated.

usage: chart.py <map.jpg> <out.webp>
"""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage as ndi
from skimage.morphology import skeletonize

src, out = sys.argv[1], sys.argv[2]
S = 1600  # output size
PAD = 0.09  # margin of sea and paper around the island

img = Image.open(src).convert('RGB')
W, H = img.size
med = img.filter(ImageFilter.MedianFilter(9))
a = np.asarray(med, float) / 255
r, g, b = a[..., 0], a[..., 1], a[..., 2]
lum = a @ np.array([0.3, 0.59, 0.11])
sat = (a.max(-1) - a.min(-1)) / np.maximum(a.max(-1), 1e-3)

# land: everything brighter than the dark background and its dim tinted circles
land = lum > 0.17
land = ndi.binary_closing(land, iterations=6)
land = ndi.binary_opening(land, iterations=4)
lab, n = ndi.label(land)
sizes = ndi.sum(land, lab, range(1, n + 1))
land = lab == (1 + int(np.argmax(sizes)))
land = ndi.binary_fill_holes(land)
land = ndi.gaussian_filter(land.astype(float), 5) > 0.5

# fresh water and the bay: clearly blue
water = (b > r * 1.5) & (b > g * 1.22) & (lum > 0.14)
water = ndi.binary_opening(water, iterations=2)
water = ndi.binary_closing(water, iterations=4)
lab, n = ndi.label(water)
keep = np.isin(lab, [i + 1 for i, s in enumerate(ndi.sum(water, lab, range(1, n + 1))) if s > 240])
water = ndi.gaussian_filter(keep.astype(float), 3.0) > 0.5

# roads: low saturation, middling brightness; only their centre lines survive, spurs pruned
road = (sat < 0.2) & (lum > 0.3) & (lum < 0.62) & land & ~water
road = ndi.binary_opening(road, iterations=1)
lab, n = ndi.label(road)
keep = np.isin(lab, [i + 1 for i, s in enumerate(ndi.sum(road, lab, range(1, n + 1))) if s > 400])
road = skeletonize(ndi.gaussian_filter(keep.astype(float), 2.2) > 0.5)
for _ in range(14):  # eat dead ends a few pixels at a time, then keep long strands
    nb = ndi.convolve(road.astype(int), np.ones((3, 3), int), mode='constant') - road
    road = road & (nb > 1)
lab, n = ndi.label(road, structure=np.ones((3, 3)))
keep = np.isin(lab, [i + 1 for i, s in enumerate(ndi.sum(road, lab, range(1, n + 1))) if s > 30])
road = keep

# forest: green wins
forest = (g > r * 1.22) & (g > b * 1.3) & land & ~water
forest = ndi.gaussian_filter(forest.astype(float), 6)


def up(mask, s=S):
    """Scale a mask to chart space, inside the frame, softly."""
    inner = int(s * (1 - PAD * 2))
    m = Image.fromarray((np.clip(mask, 0, 1) * 255).astype('uint8')).resize((inner, inner), Image.BICUBIC)
    c = Image.new('L', (s, s), 0)
    o = (s - inner) // 2
    c.paste(m, (o, o))
    return np.asarray(c, float) / 255


rng = np.random.default_rng(1147)
PAPER = np.array([246, 235, 208], float)
PAPER2 = np.array([232, 214, 176], float)
SEA = np.array([206, 214, 200], float)
INK = np.array([24, 32, 66], float)
WATER_IN = np.array([168, 194, 196], float)

L = up(land.astype(float)) > 0.5
Wt = up(water.astype(float)) > 0.5
R = up(ndi.gaussian_filter(road.astype(float), 0.8) * 2.2)
F = up(forest)


def smooth_noise(size, sigma, seed):
    n = np.random.default_rng(seed).random((size, size))
    n = ndi.gaussian_filter(n, sigma, mode='wrap')
    return (n - n.min()) / (n.max() - n.min())


# paper: fibres and stains, darker toward the edge
blot = smooth_noise(S, 90, 3) * 0.6 + smooth_noise(S, 26, 4) * 0.3 + smooth_noise(S, 5, 5) * 0.1
yy, xx = np.mgrid[0:S, 0:S] / (S - 1) - 0.5
vign = np.clip((np.hypot(xx, yy) - 0.34) / 0.42, 0, 1) ** 1.6
paper = PAPER[None, None] * (1 - blot[..., None] * 0.55) + PAPER2[None, None] * (blot[..., None] * 0.55)
paper = paper * (1 - vign[..., None] * 0.2) + np.array([150, 110, 60])[None, None] * vign[..., None] * 0.2
fibre = rng.normal(0, 2.4, (S, S))
paper += fibre[..., None]

canvas = paper.copy()


def ink(mask, alpha, col=INK):
    global canvas
    m = np.clip(mask * alpha, 0, 1)[..., None]
    canvas = canvas * (1 - m) + col[None, None] * m


# sea: a pale wash that deepens away from land, with the old chartmakers' coast lines
d_sea = ndi.distance_transform_edt(~L)
m0 = (1 - np.exp(-d_sea / 70)) * (~L)
canvas = canvas * (1 - (m0 * 0.62)[..., None]) + SEA[None, None] * (m0 * 0.62)[..., None]
for dist, w, al in [(5, 1.5, 0.5), (11, 1.3, 0.34), (19, 1.2, 0.22), (30, 1.1, 0.13), (45, 1.0, 0.07)]:
    ink(np.clip(1 - np.abs(d_sea - dist) / w, 0, 1) * (~L), al)

# interior water
d_in = ndi.distance_transform_edt(Wt)
wash = Wt.astype(float)
canvas = canvas * (1 - (wash * 0.82)[..., None]) + WATER_IN[None, None] * (wash * 0.82)[..., None]
for dist, w, al in [(4, 1.1, 0.34), (9, 1.0, 0.18)]:
    ink(np.clip(1 - np.abs(d_in - dist) / w, 0, 1) * Wt, al)

# land: slightly warmer, with a shade thrown east of the coast
d_land = ndi.distance_transform_edt(L & ~Wt)
ink(np.clip(1 - d_land / 22, 0, 1) * (L & ~Wt) * 0.5, 0.18, np.array([140, 96, 40], float))

# the coast and shores, hand-drawn: line weight wobbles
wob = smooth_noise(S, 8, 9)
edge = np.clip(1.5 + wob * 1.0 - d_sea, 0, 1) * (~L) + np.clip(1.2 + wob * 0.8 - d_land, 0, 1) * L * (~Wt)
ink(edge, 0.92)
ink(np.clip(1.0 + wob * 0.7 - ndi.distance_transform_edt(~Wt), 0, 1) * (~Wt), 0.8)

# roads, a warm brown thread with a pen's breaks in it
Rm = (R > 0.5) & L & (~Wt)
dr = ndi.distance_transform_edt(~Rm)
dash = smooth_noise(S, 3, 12) > 0.34
ink(np.clip(0.95 - dr, 0, 1) * dash, 0.7, np.array([104, 62, 24], float))

# forests: little ink trees, sown on a jittered lattice so they never line up
trees = Image.new('L', (S, S), 0)
td = ImageDraw.Draw(trees)
step = 15
for gy in range(0, S, step):
    for gx in range(0, S, step):
        x = gx + rng.integers(-5, 6)
        y = gy + rng.integers(-5, 6)
        if not (0 <= x < S and 0 <= y < S) or not L[y, x] or Wt[y, x] or R[y, x] > 0.3:
            continue
        p = F[y, x]
        if rng.random() > (p * 2.3 - 0.35):
            continue
        s = rng.integers(3, 6)
        td.arc((x - s, y - s - 1, x + s, y + s - 1), 170, 370, fill=210, width=1)
        td.line((x, y + s - 1, x, y + s + 2), fill=170, width=1)
ink(np.asarray(trees, float) / 255, 0.62, np.array([30, 62, 50], float))


def blocks_to_px(x, z):
    # measured against the source map's own grid labels
    px = (54 + (x + 500) * 1.0322) / 1060
    py = (42 + (z + 600) * 1.045) / 1060
    inner = S * (1 - PAD * 2)
    o = S * PAD
    return o + px * inner, o + py * inner


# towns: the small blocks of houses a chartmaker scatters instead of drawing streets
town = Image.new('L', (S, S), 0)
twd = ImageDraw.Draw(town)
TOWNS = [(0, -55, 92, 150), (290, -395, 46, 60), (-160, 20, 22, 14), (-235, 170, 30, 14), (-167, 210, 12, 7), (349, -326, 14, 8), (292, -25, 12, 7), (410, -400, 10, 5)]
for cx, cz, rad, count in TOWNS:
    for _ in range(count):
        ang = rng.random() * 6.283
        rr = rad * np.sqrt(rng.random())
        x, y = blocks_to_px(cx + np.cos(ang) * rr, cz + np.sin(ang) * rr)
        ix, iy = int(x), int(y)
        if not (0 <= ix < S and 0 <= iy < S) or not L[iy, ix] or Wt[iy, ix]:
            continue
        w = rng.integers(3, 6)
        h = rng.integers(2, 5)
        twd.rectangle((x - w, y - h, x + w, y + h), fill=210, outline=255)
ink(np.asarray(town, float) / 255, 0.62, np.array([70, 40, 30], float))

# farm fields: little ruled plots
farm = Image.new('L', (S, S), 0)
fmd = ImageDraw.Draw(farm)
for cx, cz, rad, count in [(-230, 150, 80, 26), (-60, -540, 60, 8)]:
    for _ in range(count):
        ang = rng.random() * 6.283
        rr = rad * np.sqrt(rng.random())
        x, y = blocks_to_px(cx + np.cos(ang) * rr, cz + np.sin(ang) * rr)
        ix, iy = int(x), int(y)
        if not (0 <= ix < S and 0 <= iy < S) or not L[iy, ix] or Wt[iy, ix]:
            continue
        w = rng.integers(9, 16)
        h = rng.integers(7, 12)
        fmd.rectangle((x - w, y - h, x + w, y + h), outline=170)
        for k in range(int(y - h) + 3, int(y + h), 3):
            fmd.line((x - w + 1, k, x + w - 1, k), fill=90)
ink(np.asarray(farm, float) / 255, 0.7, np.array([70, 80, 30], float))

# the wastes in the south-east: stipple and a few bones of hatching
waste = np.zeros((S, S))
cx, cy = blocks_to_px(280, 205)
rad_px = 165 * S * (1 - PAD * 2) * 1.03 / 1060
for _ in range(2200):
    ang = rng.random() * 6.283
    rr = rad_px * np.sqrt(rng.random())
    ix, iy = int(cx + np.cos(ang) * rr), int(cy + np.sin(ang) * rr)
    if 0 <= ix < S and 0 <= iy < S and L[iy, ix] and not Wt[iy, ix]:
        waste[iy, ix] = 1
ink(ndi.gaussian_filter(waste, 0.6) * 2.2, 0.72, np.array([90, 40, 30], float))

# two shapes everyone knows the island by: the ring and the scar
sym = Image.new('L', (S, S), 0)
syd = ImageDraw.Draw(sym)
x, y = blocks_to_px(-354, -124)
syd.ellipse((x - 20, y - 16, x + 20, y + 16), outline=255, width=2)
syd.ellipse((x - 12, y - 9, x + 12, y + 9), outline=180, width=1)
x, y = blocks_to_px(150, 125)
pts = [(x + (1.8 + t * 0.34) * np.cos(t), y + (1.8 + t * 0.34) * np.sin(t) * 0.8) for t in np.linspace(0, 16, 80)]
syd.line(pts, fill=230, width=2)
ink(np.asarray(sym, float) / 255, 0.82, np.array([70, 40, 30], float))


# relief: hachured peaks where the island really rises
hill = Image.new('L', (S, S), 0)
hd = ImageDraw.Draw(hill)
RANGES = [(-30, -300, 115, 34, 13), (80, -335, 55, 12, 10), (-150, -285, 50, 12, 10), (125, -300, 42, 14, 9), (-430, 60, 95, 30, 11), (-407, 27, 40, 7, 14), (192, -176, 16, 3, 10), (-6, 230, 20, 5, 9)]
for cx, cz, rad, count, size in RANGES:
    for _ in range(count):
        ang = rng.random() * 6.283
        rr = rad * np.sqrt(rng.random())
        x, y = blocks_to_px(cx + np.cos(ang) * rr, cz + np.sin(ang) * rr)
        ix, iy = int(x), int(y)
        if not (0 <= ix < S and 0 <= iy < S) or not L[iy, ix] or Wt[iy, ix]:
            continue
        s = size * (0.8 + rng.random() * 0.6)
        hd.line((x - s, y + s * 0.7, x, y - s * 0.7, x + s, y + s * 0.7), fill=230, width=2)
        for k in range(1, 4):
            t = k / 4
            hd.line((x + s * t * 0.8, y - s * 0.7 + s * 1.4 * t, x + s * t * 0.8 + 1.5, y + s * 0.7), fill=140, width=1)
ink(np.asarray(hill, float) / 255, 0.78)

# rhumb lines from a rose in the south-east sea, and a graticule tick along the frame
over = Image.new('L', (S, S), 0)
od = ImageDraw.Draw(over)
RX, RY = S * 0.895, S * 0.895
for k in range(16):
    ang = k * 3.14159 / 8
    od.line((RX, RY, RX + np.cos(ang) * S * 2, RY + np.sin(ang) * S * 2), fill=26, width=1)
m = np.asarray(over, float) / 255 * (~L)
ink(m, 1.0)

rose = Image.new('L', (S, S), 0)
rd = ImageDraw.Draw(rose)
for k in range(8):
    ang = k * 3.14159 / 4 - 1.5708
    big = k % 2 == 0
    rad = 74 if big else 44
    wid = 12 if big else 8
    tip = (RX + np.cos(ang) * rad, RY + np.sin(ang) * rad)
    l = (RX + np.cos(ang + 1.5708) * wid, RY + np.sin(ang + 1.5708) * wid)
    rg = (RX + np.cos(ang - 1.5708) * wid, RY + np.sin(ang - 1.5708) * wid)
    rd.polygon([tip, l, (RX, RY)], fill=255)
    rd.line([tip, rg, (RX, RY), tip], fill=255, width=1)
rd.ellipse((RX - 84, RY - 84, RX + 84, RY + 84), outline=170, width=1)
rd.ellipse((RX - 90, RY - 90, RX + 90, RY + 90), outline=110, width=1)
ink(np.asarray(rose, float) / 255, 0.92)

# frame: double rule, chequered like a real chart's scale
fr = Image.new('L', (S, S), 0)
fd = ImageDraw.Draw(fr)
o = int(S * 0.014)
i = int(S * 0.03)
fd.rectangle((o, o, S - o, S - o), outline=255, width=3)
fd.rectangle((i, i, S - i, S - i), outline=255, width=1)
n = 32
seg = (S - 2 * i) / n
for t in range(n):
    if t % 2 == 0:
        a0 = i + t * seg
        a1 = a0 + seg
        for box in [(a0, o + 2, a1, i), (a0, S - i, a1, S - o - 2), (o + 2, a0, i, a1), (S - i, a0, S - o - 2, a1)]:
            fd.rectangle(box, fill=255)
ink(np.asarray(fr, float) / 255, 0.88)

canvas = np.clip(canvas, 0, 255).astype('uint8')
im = Image.fromarray(canvas)
im.save(out, 'WEBP', quality=80, method=6)
small = im.resize((1000, 1000), Image.LANCZOS)
small.save(out.replace('.webp', '-1000.webp'), 'WEBP', quality=78, method=6)
print('saved', out)
