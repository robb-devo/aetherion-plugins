"""Armour piece bases (front view, 32x32, symmetric about x = 15.5).

Keys every base uses (a set supplies Mats for them):
    O plate     T trim      I interior (face hole, neck, gaps)   A accent    G gem    R glow
Sets decorate on top. Pieces are drawn as geometry so a set reads as one family.
"""
import math

CX = 15.5


def rect(ic, x0, y0, x1, y1, key, adj=0):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            ic.set(x, y, key, adj)


def ell(ic, cx, cy, rx, ry, key, adj=0, pred=None):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0 and (pred is None or pred(x, y)):
                ic.set(x, y, key, adj)


def sym(fn):
    """Call fn(x) for a left-side x and its mirror."""
    def both(*a, **k):
        fn(*a, **k)
    return both


def mirror_pts(pts):
    out = list(pts)
    for x, y in pts:
        out.append((int(2 * CX - x), y))
    return out


def put_pts(ic, pts, key, adj=0, mirror=True):
    for x, y in (mirror_pts(pts) if mirror else pts):
        ic.set(x, y, key, adj)


def recolor(ic, frm, to, pred, adj=None):
    for y in range(ic.h):
        for x in range(ic.w):
            if ic.key[y][x] == frm and pred(x, y):
                ic.set(x, y, to, ic.adj[y][x] if adj is None else adj)


# --------------------------------------------------------------------------- helmet
def helmet(ic, face='T', dome_ry=11.0, cheeks=True):
    """Dome + cheek guards + brow band. face: 'T' (T-visor), 'slit', 'open', 'closed'."""
    ell(ic, CX, 15.0, 11.5, dome_ry, 'O', pred=lambda x, y: y <= 15)
    if cheeks:
        for y in range(15, 26):
            inset = max(0, (y - 19)) * 0.6
            x0 = int(round(4 + inset))
            rect(ic, x0, y, 13, y, 'O')
            rect(ic, 18, y, int(round(27 - inset)), y, 'O')
    rect(ic, 4, 13, 27, 14, 'T')
    if face == 'T':
        rect(ic, 10, 15, 21, 18, 'I')
        rect(ic, 13, 19, 18, 24, 'I')
        rect(ic, 15, 15, 16, 21, 'O')
    elif face == 'slit':
        rect(ic, 8, 16, 23, 17, 'I')
        rect(ic, 14, 18, 17, 25, 'O')
        rect(ic, 13, 19, 18, 25, 'O')
        for x in range(13, 19):
            ic.set(x, 21, 'I')
            ic.set(x, 23, 'I')
    elif face == 'open':
        ell(ic, CX, 20.5, 6.0, 6.2, 'I', pred=lambda x, y: y >= 15)
    elif face == 'closed':
        rect(ic, 13, 15, 18, 25, 'O')
        rect(ic, 8, 17, 23, 17, 'I')
    return ic


# --------------------------------------------------------------------------- chestplate
def chestplate(ic, pauldrons=True, belt=True, neck=True):
    def torso_half(y):
        if y < 6:
            return None
        if y <= 12:
            return 13.6
        t = (y - 12) / 16.0
        return 12.2 - 3.6 * t
    for y in range(6, 29):
        h = torso_half(y)
        if h is None:
            continue
        rect(ic, int(math.ceil(CX - h)), y, int(math.floor(CX + h)), y, 'O')
    if neck:
        ell(ic, CX, 5.0, 5.2, 4.6, 'I', pred=lambda x, y: y >= 5)
        for x in range(9, 23):
            if ic.get(x, 5) == 'O' or ic.get(x, 6) in ('O', None):
                pass
        ell(ic, CX, 5.0, 6.4, 5.6, 'T', pred=lambda x, y: y >= 5 and ic.get(x, y) == 'O')
    if pauldrons:
        for cx in (5.0, 26.0):
            ell(ic, cx, 9.0, 4.6, 4.4, 'T', pred=lambda x, y: y <= 12)
            ell(ic, cx, 9.4, 3.6, 3.4, 'A', pred=lambda x, y: y <= 12)
    # arm gaps under the pauldrons
    for y in range(13, 29):
        h = torso_half(y)
        for x in range(0, 32):
            if abs(x - CX) > h and ic.get(x, y) == 'O':
                ic.clear(x, y)
    if belt:
        rect(ic, 8, 24, 23, 25, 'T')
    return ic


# --------------------------------------------------------------------------- leggings
def leggings(ic, knees=True, waist=True):
    rect(ic, 6, 4, 25, 8, 'O')
    if waist:
        rect(ic, 6, 4, 25, 5, 'T')
    for y in range(9, 29):
        t = (y - 9) / 19.0
        outer = 6 + 1.0 * t
        inner = 14 - 0.4 * t if y >= 12 else 15.5
        rect(ic, int(round(outer)), y, int(math.floor(inner)), y, 'O')
        rect(ic, int(math.ceil(2 * CX - inner)), y, int(round(2 * CX - outer)), y, 'O')
    if knees:
        for cx in (10.5, 20.5):
            ell(ic, cx, 18.0, 3.6, 2.6, 'T')
            ell(ic, cx, 18.0, 2.2, 1.4, 'A')
    rect(ic, 7, 27, 14, 28, 'T')
    rect(ic, 17, 27, 24, 28, 'T')
    return ic


# --------------------------------------------------------------------------- boots
def boots(ic, cuff=True, toe=True):
    """Two boots, toes pointing outward."""
    for side in (-1, 1):
        def X(x):
            return x if side < 0 else int(2 * CX - x)
        # shaft
        for y in range(9, 24):
            for x in range(8, 14):
                ic.set(X(x), y, 'O')
        # foot
        for y in range(22, 28):
            x0 = 2 if y >= 24 else (4 if y == 23 else 6)
            for x in range(x0, 14):
                ic.set(X(x), y, 'O')
        # sole
        for x in range(2, 14):
            ic.set(X(x), 28, 'T')
        if cuff:
            for y in (9, 10):
                for x in range(7, 15):
                    ic.set(X(x), y, 'T')
        if toe:
            for y in range(24, 28):
                for x in range(2, 5):
                    ic.set(X(x), y, 'A')
    return ic
