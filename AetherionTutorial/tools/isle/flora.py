"""Trees and plants.

Every tree is one the Foraging plugin can fell cleanly. What that plugin does (ForagingListener): it gathers the
logs that touch each other (faces, same-level corners, corners one up; at most 96, within 8 across and 28 up of
the log that was hit), then the canopy that touches those logs and whatever canopy hangs on to it face to face
(within 5 across and 7 up of a log, at most 384 blocks). So a crown must be one face-connected mass that
touches its own wood: `Forest.settle` removes whatever is not.

It also takes "logs" generously: stripped logs and mushroom stems count. A building is safe only because no
canopy block touches its timber or plaster: `trim_canopy_off_buildings` enforces that after planting.
"""
import math

import numpy as np

import layout as L
from noise import Rng, fbm, hash01, value

DIRS8 = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]
FACES = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


class Forest:
    def __init__(self, w, t):
        self.w = w
        self.t = t
        self.tree_logs = set()
        self.trees = []        # (x, y, z, kind)
        self.tree_leaves = set()
        self._logs = []        # the tree being grown
        self._leaves = []

    # ---------------------------------------------------------------------------------- pieces
    def log(self, x, y, z, wood, axis="y"):
        self.w.set(x, y, z, "%s_log[axis=%s]" % (wood, axis))
        self.tree_logs.add((x, y, z))
        self._logs.append((x, y, z))

    def wood(self, x, y, z, wood):
        self.w.set(x, y, z, "%s_wood[axis=y]" % wood)
        self.tree_logs.add((x, y, z))
        self._logs.append((x, y, z))

    def leaf(self, x, y, z, leaves):
        if self.w.is_air(x, y, z):
            kind = leaves(x, y, z) if callable(leaves) else leaves
            self.w.set(x, y, z, "%s[persistent=true,distance=1]" % kind)
            self._leaves.append((x, y, z))

    @staticmethod
    def oak_leaf(x, y, z):
        """Oak crowns carry patches of a second green, and now and then blossom."""
        v = float(value(x + y * 0.7, z - y * 0.45, 3.4, 301))
        if v > 0.80:
            return "flowering_azalea_leaves" if float(hash01(x, y, z, 303)) < 0.22 else "azalea_leaves"
        return "oak_leaves"

    def blob(self, cx, cy, cz, rx, ry, rz, leaves, rng, ragged=0.38, bottom_cut=None, reach=4):
        """A ragged leaf ellipsoid round a point; (cx, cy, cz) should be a log or right beside one."""
        for dx in range(-int(rx) - 1, int(rx) + 2):
            for dy in range(-int(ry) - 1, int(ry) + 2):
                for dz in range(-int(rz) - 1, int(rz) + 2):
                    if max(abs(dx), abs(dz)) > reach or abs(dy) > reach:
                        continue
                    if bottom_cut is not None and dy < bottom_cut:
                        continue
                    d = (dx / rx) ** 2 + (dy / ry) ** 2 + (dz / rz) ** 2
                    edge = 1.0 - ragged * float(hash01(cx + dx, cy + dy, cz + dz, 7))
                    if d <= edge:
                        self.leaf(cx + dx, cy + dy, cz + dz, leaves)

    def trunk(self, x, y, z, h, wood, rng, kink=0.4):
        """h logs upward from y + 1; it may step aside once (two logs side by side there). Returns its top."""
        tx, tz = x, z
        at = rng.int(2, max(2, h - 2)) if (h >= 5 and rng.chance(kink)) else -1
        for i in range(1, h + 1):
            if i == at:
                dx, dz = rng.pick([(1, 0), (-1, 0), (0, 1), (0, -1)])
                self.log(tx, y + i, tz, wood)
                tx, tz = tx + dx, tz + dz
            self.log(tx, y + i, tz, wood)
        return tx, y + h, tz

    def branch(self, x, y, z, dx, dz, length, wood, rise=1):
        """A limb: steps out and up, each log touching the last (the plugin links corners upward)."""
        cx, cy, cz = x, y, z
        for i in range(length):
            cx += dx
            cz += dz
            if i % 2 == 0 or rise > 1:
                cy += 1
            axis = "x" if dx and not dz else ("z" if dz and not dx else "y")
            self.log(cx, cy, cz, wood, axis)
        return cx, cy, cz

    def settle(self, x, y, z, kind):
        """Ends a tree: drops every leaf that does not hang, face to face, on a leaf that touches its wood."""
        logs = set(self._logs)
        leaves = set(self._leaves)
        seen = set()
        stack = []
        for (lx, ly, lz) in logs:
            for a in (-1, 0, 1):
                for b in (-1, 0, 1):
                    for c in (-1, 0, 1):
                        p = (lx + a, ly + b, lz + c)
                        if p in leaves and p not in seen:
                            seen.add(p)
                            stack.append(p)
        while stack:
            px, py, pz = stack.pop()
            for (a, b, c) in FACES:
                p = (px + a, py + b, pz + c)
                if p in leaves and p not in seen:
                    seen.add(p)
                    stack.append(p)
        for p in leaves - seen:
            self.w.set(p[0], p[1], p[2], "air")
        self.tree_leaves |= seen
        self.trees.append((x, y, z, kind))
        self._logs, self._leaves = [], []

    # ---------------------------------------------------------------------------------- species
    def oak(self, x, y, z, rng, big=False):
        h = rng.int(7, 9) if big else rng.int(5, 7)
        tx, top, tz = self.trunk(x, y, z, h, "oak", rng, kink=0.3 if big else 0.15)
        if big:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if rng.chance(0.6):
                    self.wood(x + dx, y + 1, z + dz, "oak")
        crowns = [(tx, top, tz, 3.1 if big else 2.7)]
        start = rng.int(0, 7)
        limbs = rng.int(3, 4) if big else rng.int(2, 3)
        for k in range(limbs):
            dx, dz = DIRS8[(start + k * 3) % 8]
            hb = top - rng.int(1, 3 if big else 2)
            bx, by, bz = self.branch(tx, hb, tz, dx, dz, rng.int(2, 3) if big else rng.int(1, 2), "oak")
            crowns.append((bx, by, bz, rng.range(2.2, 2.8)))
        for i, (cx, cy, cz, r) in enumerate(crowns):
            lift = 1 if i == 0 else 0
            self.blob(cx, cy + lift, cz, r, r * (0.82 if i == 0 else 0.72), r, self.oak_leaf, rng,
                      bottom_cut=-1 if i == 0 else -1)
        self.settle(x, y, z, "oak")

    def birch(self, x, y, z, rng):
        h = rng.int(8, 11)
        tx, top, tz = self.trunk(x, y, z, h, "birch", rng, kink=0.12)
        self.blob(tx, top, tz, 2.2, 2.6, 2.2, "birch_leaves", rng, ragged=0.45, bottom_cut=-2)
        # light tufts down the upper third, each on a short twig
        start = rng.int(0, 3)
        for k in range(rng.int(2, 3)):
            dx, dz = [(1, 0), (0, 1), (-1, 0), (0, -1)][(start + k) % 4]
            hb = top - 2 - k * rng.int(1, 2)
            if hb <= y + 3:
                break
            bx, by, bz = self.branch(tx, hb, tz, dx, dz, 1, "birch")
            self.blob(bx, by, bz, 1.8, 1.5, 1.8, "birch_leaves", rng, ragged=0.5, bottom_cut=-1)
        self.settle(x, y, z, "birch")

    def spruce(self, x, y, z, rng, tall=False):
        h = rng.int(11, 14) if tall else rng.int(8, 10)
        for i in range(1, h + 1):
            self.log(x, y + i, z, "spruce")
        top = y + h
        self.leaf(x, top + 1, z, "spruce_leaves")
        self.leaf(x, top + 2, z, "spruce_leaves")
        # skirts: each a wide ring with a narrower one above it, widening towards the ground
        yy = top
        skirt = 0
        bare = 3 if tall else 2
        while yy > y + bare:
            wide = (1, 2, 2, 3)[min(skirt, 3)]
            for r, level in ((max(wide - 1, 1), yy), (wide, yy - 1)):
                if level <= y + bare:
                    continue
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if not (dx or dz):
                            continue
                        d = math.hypot(dx, dz)
                        if d > r + 0.35:
                            continue
                        if d > r - 0.5 and float(hash01(x + dx, level, z + dz, 11)) < 0.2:
                            continue
                        self.leaf(x + dx, level, z + dz, "spruce_leaves")
            yy -= 3 if wide >= 3 else 2
            skirt += 1
        self.settle(x, y, z, "spruce")

    def old_oak(self, x, y, z, rng):
        """The village oak: a thick trunk and a broad crown, still inside the felling limits."""
        h = 7
        for dx in (0, 1):
            for dz in (0, 1):
                for i in range(1, h + 1 - (dx + dz)):
                    self.log(x + dx, y + i, z + dz, "oak")
        for dx, dz in ((-1, 0), (2, 1), (0, -1), (1, 2), (-1, 1), (2, 0)):
            self.wood(x + dx, y + 1, z + dz, "oak")
        top = y + h
        centres = [(x, top, z)]
        for dx, dz, ln in ((1, 0, 4), (-1, 0, 3), (0, 1, 4), (0, -1, 3), (1, 1, 3), (-1, -1, 3), (1, -1, 2), (-1, 1, 2)):
            bx, by, bz = self.branch(x + (1 if dx > 0 else 0), top - 2, z + (1 if dz > 0 else 0), dx, dz, ln, "oak")
            centres.append((bx, by, bz))
        for i, (cx, cy, cz) in enumerate(centres):
            self.blob(cx, cy + 1, cz, 3.0, 2.4, 3.0, self.oak_leaf, rng, bottom_cut=-1)
        self.settle(x, y, z, "old_oak")

    def bush(self, x, y, z, rng, kind=None):
        """Low shrub: leaves only (no log, so nothing to fell and nothing to mistake for a tree)."""
        leaves = kind or rng.weighted([("oak_leaves", 5), ("azalea_leaves", 3), ("flowering_azalea_leaves", 1.2),
                                       ("birch_leaves", 1)])
        self.leaf(x, y + 1, z, leaves)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if rng.chance(0.45) and self.t.inb(x + dx, z + dz) and self.t.h(x + dx, z + dz) == y:
                self.leaf(x + dx, y + 1, z + dz, leaves)
        if rng.chance(0.3):
            self.leaf(x, y + 2, z, leaves)
        self._leaves = []

    def deadfall(self, x, y, z, rng):
        """A trunk that came down: a log on the ground with moss on it. No leaves, so it cannot be felled."""
        dx, dz = rng.pick([(1, 0), (0, 1)])
        n = rng.int(3, 5)
        wood = rng.pick(["oak", "oak", "birch", "spruce"])
        for i in range(n):
            px, pz = x + dx * i, z + dz * i
            if not self.t.inb(px, pz) or not self.t.is_land(px, pz) or self.t.h(px, pz) != y:
                break
            if not self.w.is_air(px, y + 1, pz) or self.t.path[pz - L.Z0, px - L.X0]:
                break
            self.w.set(px, y + 1, pz, "%s_log[axis=%s]" % (wood, "x" if dx else "z"))
            r = rng.next()
            if r < 0.3:
                self.w.set(px, y + 2, pz, "moss_carpet")
            elif r < 0.4:
                self.w.set(px, y + 2, pz, "brown_mushroom")

    def stump(self, x, y, z, rng):
        self.w.set(x, y + 1, z, "%s_log[axis=y]" % rng.pick(["oak", "oak", "spruce"]))
        if rng.chance(0.4):
            self.w.set(x, y + 2, z, "moss_carpet")


def density(t, x, z):
    """How wooded a spot wants to be, 0..1, and which trees grow there."""
    X = np.array([x])
    Z = np.array([z])
    n = float(fbm(X, Z, 28, 3, L.SEED + 81)[0]) * 0.5 + 0.5
    zi, xi = z - L.Z0, x - L.X0
    upper = bool(t.upper[zi, xi])
    din = float(t.din[zi, xi])
    bell = math.exp(-(((x - L.BELL[0]) / 30.0) ** 2 + ((z - L.BELL[1]) / 28.0) ** 2))
    if bell > 0.35:
        return 0.06 * n, ("spruce", "spruce", "birch")           # the hill stays open
    if upper:
        d = 0.55 + 0.4 * n
        if z < -70:
            return d, ("spruce", "spruce", "oak", "birch")
        return d, ("oak", "oak", "birch", "birch", "oak", "spruce")
    # lower island: mostly meadow with copses
    copse = n > 0.62
    if x < -20 and z > 20:
        return (0.5 if copse else 0.05), ("oak", "birch")         # the downs
    if x > 50 and z < 10:
        return (0.6 if copse else 0.16), ("oak", "birch", "spruce")
    return (0.42 if copse else 0.07), ("oak", "oak", "birch")


def plant_trees(w, t, forest):
    rng = Rng(L.SEED + 5)
    cell = 5
    H = t.H
    for gz in range(L.Z0, L.Z0 + L.SZ, cell):
        for gx in range(L.X0, L.X0 + L.SX, cell):
            x = gx + rng.int(0, cell - 1)
            z = gz + rng.int(0, cell - 1)
            roll = rng.next()
            pick = rng.next()
            if not t.inb(x, z) or not t.is_land(x, z):
                continue
            zi, xi = z - L.Z0, x - L.X0
            if t.keep_clear[zi, xi] or t.path[zi, xi] or t.quarry[zi, xi]:
                continue
            if t.slope[zi, xi] > 1 or t.din[zi, xi] < 6 or int(t.top_kind[zi, xi]) != 0:
                continue
            if t.stream_dist[zi, xi] < 3.2:
                continue
            d, kinds = density(t, x, z)
            if roll > d:
                continue
            y = int(H[zi, xi])
            if w.name(x, y, z) != "grass_block":
                continue
            kind = kinds[int(pick * len(kinds)) % len(kinds)]
            sub = Rng(x * 7349 + z * 9151 + 3)
            if d > 0.3 and sub.chance(0.07):
                if sub.chance(0.6):
                    forest.deadfall(x, y, z, sub)
                else:
                    forest.stump(x, y, z, sub)
                continue
            if kind == "oak":
                forest.oak(x, y, z, sub, big=sub.chance(0.3))
            elif kind == "birch":
                forest.birch(x, y, z, sub)
            else:
                forest.spruce(x, y, z, sub, tall=sub.chance(0.3))
            w.set(x, y, z, "dirt")


def ground_cover(w, t, forest):
    """Flowers in drifts, sparse grass, ferns under trees, bushes at the wood's edge, reeds and lilies."""
    rng = Rng(L.SEED + 9)
    H = t.H
    seed = L.SEED
    flowers_meadow = ["dandelion", "poppy", "oxeye_daisy", "cornflower", "azure_bluet"]
    for zi in range(L.SZ):
        for xi in range(L.SX):
            x, z = xi + L.X0, zi + L.Z0
            if not t.land[zi, xi] or t.stream[zi, xi] or t.path[zi, xi] or t.quarry[zi, xi]:
                continue
            y = int(H[zi, xi])
            if not w.is_air(x, y + 1, z):
                continue
            ground = w.name(x, y, z)
            r = float(hash01(x, 3, z, seed + 91))
            if ground == "grass_block":
                drift = float(value(x, z, 9.0, seed + 93))
                kind = int(value(x, z, 23.0, seed + 95) * len(flowers_meadow)) % len(flowers_meadow)
                wooded = bool(t.upper[zi, xi]) and not t.keep_clear[zi, xi]
                shade = not w.is_air(x, y + 4, z) or not w.is_air(x, y + 6, z)
                if t.keep_clear[zi, xi] and r > 0.03:
                    continue
                floor = float(value(x, z, 6.0, seed + 101))
                if shade:
                    # under the trees: moss and old leaves in patches, ferns, a mushroom now and then
                    if floor > 0.66:
                        w.set(x, y, z, "moss_block" if floor > 0.74 else "podzol")
                    elif floor < 0.2:
                        w.set(x, y, z, "coarse_dirt" if r < 0.5 else "rooted_dirt")
                    if r < 0.11:
                        w.set(x, y + 1, z, "fern")
                    elif r < 0.125:
                        w.set(x, y + 1, z, "brown_mushroom" if r < 0.119 else "red_mushroom")
                    elif r < 0.15 and floor > 0.7:
                        w.set(x, y + 1, z, "moss_carpet")
                    continue
                if wooded and floor > 0.8:
                    w.set(x, y, z, "moss_block")
                if drift > 0.74 and r < 0.34:
                    w.set(x, y + 1, z, flowers_meadow[kind])
                elif r < 0.085:
                    w.set(x, y + 1, z, "short_grass")
                elif r < 0.10 and wooded:
                    w.set(x, y + 1, z, "fern")
                elif r < 0.106 and not t.keep_clear[zi, xi] and t.slope[zi, xi] <= 1:
                    forest.bush(x, y, z, Rng(x * 31 + z * 17))
            elif ground == "sand" and t.din[zi, xi] > 4 and r < 0.012 and y > L.SEA:
                w.set(x, y + 1, z, "dead_bush")
    # reeds along the lower stream and lilies on the pool
    for zi in range(1, L.SZ - 1):
        for xi in range(1, L.SX - 1):
            if not t.stream[zi, xi]:
                continue
            x, z = xi + L.X0, zi + L.Z0
            lvl = int(t.water[zi, xi])
            r = float(hash01(x, 5, z, seed + 97))
            px, pz, _ = L.STREAM[L.FALL_INDEX + 1]
            if math.hypot(x - px, z - pz) < 6 and r < 0.10 and w.is_air(x, lvl + 1, z):
                w.set(x, lvl + 1, z, "lily_pad")
    for zi in range(1, L.SZ - 1):
        for xi in range(1, L.SX - 1):
            x, z = xi + L.X0, zi + L.Z0
            if t.stream[zi, xi] or not t.land[zi, xi] or t.path[zi, xi]:
                continue
            if not (t.stream[zi - 1, xi] or t.stream[zi + 1, xi] or t.stream[zi, xi - 1] or t.stream[zi, xi + 1]):
                continue
            y = int(H[zi, xi])
            r = float(hash01(x, 6, z, seed + 99))
            if r < 0.10 and w.name(x, y, z) in ("grass_block", "dirt", "sand", "coarse_dirt") and w.is_air(x, y + 1, z) \
                    and not t.keep_clear[zi, xi] and z > -20:
                for k in range(1, 2 + int(r * 30) % 3):
                    w.set(x, y + k, z, "sugar_cane")


def boulders(w, t):
    """Loose rocks on slopes, at cliff feet and along the shore."""
    rng = Rng(L.SEED + 13)
    H = t.H
    stones = ["andesite", "cobblestone", "mossy_cobblestone", "tuff", "andesite"]
    for _ in range(520):
        x = rng.int(L.X0 + 8, L.X0 + L.SX - 9)
        z = rng.int(L.Z0 + 8, L.Z0 + L.SZ - 9)
        zi, xi = z - L.Z0, x - L.X0
        size = rng.int(1, 3)
        kind = rng.int(0, 4)
        if t.path[zi, xi] or t.quarry[zi, xi] or t.stream[zi, xi] or t.keep_clear[zi, xi]:
            continue
        coast = (t.land[zi, xi] and t.din[zi, xi] < 5) or (not t.land[zi, xi] and t.dout[zi, xi] < 7)
        slope = t.land[zi, xi] and t.slope[zi, xi] >= 2
        if not (coast or slope or rng.chance(0.06)):
            continue
        if not t.land[zi, xi] and not rng.chance(0.5):
            continue
        y = int(H[zi, xi])
        for dx in range(-size, size + 1):
            for dz in range(-size, size + 1):
                for dy in range(0, size + 1):
                    if dx * dx + dz * dz + dy * dy * 1.6 <= size * size * 0.8 + 0.4:
                        if not t.inb(x + dx, z + dz):
                            continue
                        yy = int(H[z + dz - L.Z0, x + dx - L.X0]) + 1 + dy
                        if abs(yy - (y + 1 + dy)) > 2:
                            continue
                        b = stones[(kind + dx + dz + dy) % 5]
                        if yy <= L.SEA + 1 and b in ("andesite", "cobblestone"):
                            b = "mossy_cobblestone" if (dx + dz) % 2 else "tuff"
                        if w.name(x + dx, yy, z + dz) in ("air", "water", "short_grass", "fern"):
                            w.set(x + dx, yy, z + dz, b)


def sea_life(w, t):
    """Seagrass in the shallows."""
    seed = L.SEED
    H = t.H
    for zi in range(L.SZ):
        for xi in range(L.SX):
            if t.land[zi, xi]:
                continue
            x, z = xi + L.X0, zi + L.Z0
            y = int(H[zi, xi])
            if y < L.SEA - 9 or y >= L.SEA:
                continue
            patch = float(value(x, z, 8.0, seed + 111))
            r = float(hash01(x, 9, z, seed + 113))
            if patch > 0.62 and r < 0.3 and w.name(x, y, z) in ("sand", "gravel") and w.name(x, y + 1, z) == "water":
                if y + 2 <= L.SEA and r < 0.07 and w.name(x, y + 2, z) == "water":
                    w.set(x, y + 1, z, "tall_seagrass[half=lower]")
                    w.set(x, y + 2, z, "tall_seagrass[half=upper]")
                else:
                    w.set(x, y + 1, z, "seagrass")


WOODS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo")


def _woodwork(n):
    """What the Foraging plugin clears together with a tree it touches (its isTreeDecor)."""
    if n == "ladder" or n.endswith("_sign"):
        return True
    if not n.startswith(WOODS):
        return False
    return n.endswith(("_fence", "_fence_gate", "_slab", "_stairs", "_trapdoor", "_button", "_pressure_plate"))


def trim_canopy_off_buildings(w, forest):
    """Keeps trees and buildings apart for the Foraging plugin (see the module note). Returns how many leaves
    were cut.

    1. No canopy block may touch (edge or corner) a log that is not a tree's own: stripped timber and mushroom
       stem plaster count as logs there, and a touching leaf would make the house a tree.
    2. No tree leaf may touch (face) woodwork: fences, stairs, slabs, trapdoors, ladders, signs. The plugin
       clears such "tree decor" with the tree, and a lamp post or a roof edge must not vanish with an oak.
    """
    logs = w.name_ids(lambda n: n.endswith(("_log", "_wood", "_stem", "_hyphae")))
    canopy = set(int(i) for i in w.name_ids(
        lambda n: n.endswith("_leaves") or n in ("azalea", "flowering_azalea", "vine", "glow_lichen",
                                                 "mangrove_roots") or n.endswith("_sapling")))
    ys, zs, xs = np.nonzero(np.isin(w.blocks, logs))
    cut = 0
    for xi, yi, zi in zip(xs.tolist(), ys.tolist(), zs.tolist()):
        x, y, z = xi + w.x0, yi + w.y0, zi + w.z0
        if (x, y, z) in forest.tree_logs:
            continue
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if w.get_id(x + dx, y + dy, z + dz) in canopy:
                        w.set(x + dx, y + dy, z + dz, "air")
                        forest.tree_leaves.discard((x + dx, y + dy, z + dz))
                        cut += 1
    wood = set(int(i) for i in w.name_ids(_woodwork))
    for (x, y, z) in list(forest.tree_leaves):
        if w.get_id(x, y, z) not in canopy:
            continue
        for (a, b, c) in FACES:
            if w.get_id(x + a, y + b, z + c) in wood:
                w.set(x, y, z, "air")
                forest.tree_leaves.discard((x, y, z))
                cut += 1
                break
    return cut


def build(w, t):
    forest = Forest(w, t)
    plant_trees(w, t, forest)
    boulders(w, t)
    ground_cover(w, t, forest)
    sea_life(w, t)
    return forest
