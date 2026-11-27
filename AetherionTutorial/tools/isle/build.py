"""Building kit: a rotatable canvas over the world, primitives, roofs, timber framing and small props.

Local frame of a Canvas: +x to the right, +z towards the viewer ("front" is +z = south when unrotated), y up.
`rot` turns the whole thing clockwise in quarter turns (seen from above); block states turn with it.
"""
import math

from noise import hash01, value
from world import props_of, canon

HORIZ = ["north", "east", "south", "west"]


def turn_dir(d, r):
    if d in HORIZ:
        return HORIZ[(HORIZ.index(d) + r) % 4]
    return d


def turn_state(state, r, mirror=False):
    """Rotate (and optionally mirror in x) a block state string."""
    if "[" not in state or (r % 4 == 0 and not mirror):
        return state
    name = state.split("[", 1)[0]
    p = props_of(state)
    q = dict(p)
    if mirror:
        if p.get("facing") in ("east", "west"):
            q["facing"] = "west" if p["facing"] == "east" else "east"
        if "hinge" in p:
            q["hinge"] = "left" if p["hinge"] == "right" else "right"
        if "rotation" in p:
            q["rotation"] = str((16 - int(p["rotation"])) % 16)
        e, w_ = p.get("east"), p.get("west")
        if e is not None or w_ is not None:
            if w_ is not None:
                q["east"] = w_
            else:
                q.pop("east", None)
            if e is not None:
                q["west"] = e
            else:
                q.pop("west", None)
        p = dict(q)
    r %= 4
    if r:
        if p.get("facing") in HORIZ:
            q["facing"] = turn_dir(p["facing"], r)
        if p.get("axis") in ("x", "z") and r % 2:
            q["axis"] = "z" if p["axis"] == "x" else "x"
        if "rotation" in p:
            q["rotation"] = str((int(p["rotation"]) + 4 * r) % 16)
        sides = {d: p[d] for d in HORIZ if d in p}
        if sides:
            for d in HORIZ:
                q.pop(d, None)
            for d, v in sides.items():
                q[turn_dir(d, r)] = v
    return canon(name, q)


class Canvas:
    def __init__(self, w, ox, oy, oz, rot=0, mirror=False):
        self.w = w
        self.ox, self.oy, self.oz = ox, oy, oz
        self.rot = rot % 4
        self.mirror = mirror

    def at(self, x, y, z):
        if self.mirror:
            x = -x
        r = self.rot
        if r == 1:
            x, z = -z, x
        elif r == 2:
            x, z = -x, -z
        elif r == 3:
            x, z = z, -x
        return self.ox + x, self.oy + y, self.oz + z

    def sub(self, x, y, z, rot=0):
        """A canvas whose origin is this canvas's local point (same orientation plus `rot`)."""
        wx, wy, wz = self.at(x, y, z)
        return Canvas(self.w, wx, wy, wz, self.rot + rot, self.mirror)

    def st(self, state):
        return turn_state(state, self.rot, self.mirror)

    def set(self, x, y, z, state):
        wx, wy, wz = self.at(x, y, z)
        self.w.set(wx, wy, wz, self.st(state))

    def set_if_air(self, x, y, z, state):
        wx, wy, wz = self.at(x, y, z)
        return self.w.set_if_air(wx, wy, wz, self.st(state))

    def soft(self, x, y, z, state):
        """Place only into air or plants (never into a wall)."""
        wx, wy, wz = self.at(x, y, z)
        n = self.w.name(wx, wy, wz)
        if n == "air" or n in SOFT:
            self.w.set(wx, wy, wz, self.st(state))
            return True
        return False

    def get(self, x, y, z):
        wx, wy, wz = self.at(x, y, z)
        return self.w.get(wx, wy, wz)

    def name(self, x, y, z):
        wx, wy, wz = self.at(x, y, z)
        return self.w.name(wx, wy, wz)

    def is_air(self, x, y, z):
        wx, wy, wz = self.at(x, y, z)
        return self.w.is_air(wx, wy, wz)

    def fill(self, x1, y1, z1, x2, y2, z2, state):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, state)

    def fill_fn(self, x1, y1, z1, x2, y2, z2, fn):
        """fn(x, y, z) -> state or None"""
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    s = fn(x, y, z)
                    if s:
                        self.set(x, y, z, s)

    def clear(self, x1, y1, z1, x2, y2, z2):
        self.fill(x1, y1, z1, x2, y2, z2, "air")

    def line(self, x1, y1, z1, x2, y2, z2, state):
        n = max(abs(x2 - x1), abs(y2 - y1), abs(z2 - z1), 1)
        for i in range(n + 1):
            t = i / n
            self.set(round(x1 + (x2 - x1) * t), round(y1 + (y2 - y1) * t), round(z1 + (z2 - z1) * t), state)

    def column_down(self, x, y, z, state, limit=24):
        """Fill downwards from y until something solid (foundations on a slope)."""
        for yy in range(y, y - limit, -1):
            n = self.name(x, yy, z)
            if n != "air" and n not in SOFT and n != "water":
                break
            self.set(x, yy, z, state)

    def block_entity(self, x, y, z, tags):
        wx, wy, wz = self.at(x, y, z)
        self.w.block_entity(wx, wy, wz, tags)

    def mark(self, key, x, y, z, **extra):
        wx, wy, wz = self.at(x, y, z)
        if "facing" in extra:
            extra["facing"] = turn_dir(extra["facing"], self.rot)
        self.w.mark(key, wx, wy, wz, **extra)

    def world_xyz(self, x, y, z):
        return self.at(x, y, z)


SOFT = {"short_grass", "tall_grass", "fern", "large_fern", "dandelion", "poppy", "cornflower", "oxeye_daisy",
        "azure_bluet", "allium", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "lily_of_the_valley",
        "blue_orchid", "dead_bush", "brown_mushroom", "red_mushroom", "sweet_berry_bush", "moss_carpet",
        "pink_petals", "oak_leaves", "birch_leaves", "spruce_leaves", "azalea_leaves", "flowering_azalea_leaves",
        "lilac", "peony", "rose_bush", "sunflower", "sugar_cane", "seagrass", "tall_seagrass"}


# ----------------------------------------------------------------------------------------------------- mixing
class Mix:
    """A weathered material: coherent patches of a few blocks instead of salt-and-pepper noise."""

    def __init__(self, pairs, scale=3.0, seed=0, speck=0.0, speck_block=None):
        total = float(sum(wt for _, wt in pairs))
        acc = 0.0
        self.steps = []
        for block, wt in pairs:
            acc += wt / total
            self.steps.append((acc, block))
        self.scale = scale
        self.seed = seed
        self.speck = speck
        self.speck_block = speck_block

    def at(self, x, y, z):
        if self.speck and float(hash01(x, y, z, self.seed + 5)) < self.speck:
            return self.speck_block
        v = float(value(x + y * 0.37, z + y * 0.61, self.scale, self.seed))
        v = min(max((v - 0.18) / 0.64, 0.0), 0.9999)
        for edge, block in self.steps:
            if v < edge:
                return block
        return self.steps[-1][1]


def mixed(c, x1, y1, z1, x2, y2, z2, mix):
    """Fill a box of a Canvas with a Mix (noise is taken in world space, so walls line up)."""
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                wx, wy, wz = c.at(x, y, z)
                c.w.set(wx, wy, wz, c.st(mix.at(wx, wy, wz)))


def mix_set(c, x, y, z, mix):
    wx, wy, wz = c.at(x, y, z)
    c.w.set(wx, wy, wz, c.st(mix.at(wx, wy, wz)))


# ----------------------------------------------------------------------------------------------------- roofs
def stair(mat, facing, top=False):
    return "%s_stairs[facing=%s,half=%s]" % (mat, facing, "top" if top else "bottom")


def slab(mat, top=False):
    return "%s_slab[type=%s]" % (mat, "top" if top else "bottom")


class RoofMat:
    """Roof covering: a main material with a share of a second one in patches (weathered shingles)."""

    def __init__(self, main, second=None, share=0.25, ridge=None, seed=0):
        self.main, self.second, self.share = main, second, share
        self.ridge = ridge or main
        self.seed = seed

    def pick(self, wx, wy, wz):
        if self.second and float(value(wx * 1.0 + wy * 0.5, wz * 1.0 - wy * 0.5, 2.6, self.seed)) < self.share:
            return self.second
        return self.main


def gable_roof(c, x1, z1, x2, z2, y, mat, ridge_axis="x", overhang=1, steep=False, gable_fill=None):
    """Gable roof over the footprint x1..x2, z1..z2. `y` is the course that sits on the long walls.

    ridge_axis "x": the ridge runs along x and the slopes fall to -z and +z. `steep`: two blocks up per
    block in (a full block under every stair). gable_fill(x, y, z) -> state closes the two end triangles.
    Returns the y of the ridge course.
    """
    if ridge_axis == "z":
        fill = None
        if gable_fill:
            fill = lambda x, yy, z: _swap_state(gable_fill(z, yy, x))
        return gable_roof(_Swap(c), z1, x1, z2, x2, y, mat, "x", overhang, steep, fill)
    xa, xb = x1 - overhang, x2 + overhang
    rise = 2 if steep else 1
    span = z2 - z1 + 1
    top_y = y
    i = -1 if overhang else 0
    while True:
        zf, zb = z1 + i, z2 - i
        if zf > zb:
            break
        cy = y + i * rise
        for x in range(xa, xb + 1):
            wx, wy, wz = c.at(x, cy, zf)
            m = mat.pick(wx, wy, wz)
            if zf == zb:
                c.set(x, cy, zf, slab(mat.ridge))
                if steep:
                    c.set(x, cy - 1, zf, _full(m))
            else:
                c.set(x, cy, zf, stair(m, "south"))
                wx, wy, wz = c.at(x, cy, zb)
                m2 = mat.pick(wx, wy, wz)
                c.set(x, cy, zb, stair(m2, "north"))
                if steep and i >= 0:
                    c.set(x, cy - 1, zf, _full(m))
                    c.set(x, cy - 1, zb, _full(m2))
        if gable_fill and i >= 0:
            for gx in (x1, x2):
                for gz in range(zf + 1, zb):
                    for gy in range(cy - rise + 1, cy + 1):
                        st = gable_fill(gx, gy, gz)
                        if st:
                            c.set(gx, gy, gz, st)
        top_y = cy
        i += 1
    return top_y


class _Swap:
    """Canvas adapter that swaps x and z (so a roof written for an x ridge serves a z ridge)."""

    def __init__(self, c):
        self.c = c
        self.w = c.w

    def set(self, x, y, z, s):
        self.c.set(z, y, x, _swap_state(s))

    def at(self, x, y, z):
        return self.c.at(z, y, x)

    def st(self, s):
        return self.c.st(_swap_state(s))

    def name(self, x, y, z):
        return self.c.name(z, y, x)


def _swap_state(s):
    """Mirror a state across the x=z diagonal (north<->west, south<->east, axis x<->z)."""
    if not s or "[" not in s:
        return s
    name = s.split("[", 1)[0]
    p = props_of(s)
    m = {"north": "west", "west": "north", "south": "east", "east": "south"}
    if p.get("facing") in m:
        p["facing"] = m[p["facing"]]
    if p.get("axis") in ("x", "z"):
        p["axis"] = "z" if p["axis"] == "x" else "x"
    return canon(name, p)


FULL_OF = {"spruce": "spruce_planks", "dark_oak": "dark_oak_planks", "oak": "oak_planks", "birch": "birch_planks",
           "deepslate_tile": "deepslate_tiles", "deepslate_brick": "deepslate_bricks", "brick": "bricks",
           "mud_brick": "mud_bricks", "cobblestone": "cobblestone", "stone_brick": "stone_bricks",
           "mossy_cobblestone": "mossy_cobblestone", "andesite": "andesite", "tuff_brick": "tuff_bricks",
           "polished_andesite": "polished_andesite", "mossy_stone_brick": "mossy_stone_bricks",
           "blackstone": "blackstone", "polished_blackstone_brick": "polished_blackstone_bricks",
           "cut_copper": "cut_copper", "exposed_cut_copper": "exposed_cut_copper",
           "weathered_cut_copper": "weathered_cut_copper", "oxidized_cut_copper": "oxidized_cut_copper",
           "sandstone": "sandstone", "acacia": "acacia_planks", "jungle": "jungle_planks",
           "mangrove": "mangrove_planks", "cherry": "cherry_planks", "bamboo": "bamboo_planks"}


def _full(mat):
    return FULL_OF.get(mat, mat)


def pyramid_roof(c, x1, z1, x2, z2, y, mat, overhang=1, steep=False):
    """Hipped / pyramid roof. Returns the y of the top course."""
    xa, xb, za, zb = x1 - overhang, x2 + overhang, z1 - overhang, z2 + overhang
    yy = y
    while xa <= xb and za <= zb:
        for x in range(xa, xb + 1):
            for z in (za, zb):
                wx, wy, wz = c.at(x, yy, z)
                m = mat.pick(wx, wy, wz)
                if xa == xb and za == zb:
                    c.set(x, yy, z, slab(mat.ridge))
                elif za == zb:
                    c.set(x, yy, z, slab(mat.ridge))
                else:
                    c.set(x, yy, z, stair(m, "south" if z == za else "north"))
                    if steep:
                        c.set(x, yy - 1, z, _full(m))
        for z in range(za + 1, zb):
            for x in (xa, xb):
                wx, wy, wz = c.at(x, yy, z)
                m = mat.pick(wx, wy, wz)
                if xa == xb:
                    c.set(x, yy, z, slab(mat.ridge))
                else:
                    c.set(x, yy, z, stair(m, "east" if x == xa else "west"))
                    if steep:
                        c.set(x, yy - 1, z, _full(m))
        xa += 1
        xb -= 1
        za += 1
        zb -= 1
        yy += 2 if steep else 1
    return yy - (2 if steep else 1)


# ----------------------------------------------------------------------------------------------------- props
def lantern_post(c, x, y, z, height=3, wood="spruce", arm=None):
    """Slim lamp post on the ground block y-1: fence post, optional arm, hanging lantern."""
    for i in range(height):
        c.set(x, y + i, z, "%s_fence" % wood)
    if arm:
        dx, dz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[arm]
        c.set(x, y + height, z, "%s_fence" % wood)
        c.set(x + dx, y + height, z + dz, "%s_fence" % wood)
        c.set(x + dx, y + height - 1, z + dz, "lantern[hanging=true]")
    else:
        c.set(x, y + height, z, "lantern[hanging=false]")


def barrel(c, x, y, z, facing="up"):
    c.set(x, y, z, "barrel[facing=%s]" % facing)


def crate_pile(c, x, y, z, seed=0):
    """A few barrels and a chest-like stack, 2x2 footprint."""
    picks = ["barrel[facing=up]", "barrel[facing=up]", "barrel[facing=east]", "composter", "barrel[facing=south]"]
    k = int(hash01(x, y, z, seed) * 97)
    c.set(x, y, z, picks[k % 5])
    c.set(x + 1, y, z, picks[(k + 1) % 5])
    c.set(x, y, z + 1, picks[(k + 3) % 5])
    if k % 3:
        c.set(x, y + 1, z, "barrel[facing=up]")
    if k % 2:
        c.set(x + 1, y, z + 1, "hay_block[axis=y]")


def log_pile(c, x, y, z, length=4, layers=2, axis="x", wood="oak"):
    """Stacked logs lying along `axis` (a woodcutter's stack). No leaves: cannot be felled."""
    for layer in range(layers):
        width = layers - layer
        for i in range(length):
            for k in range(width):
                off = k + layer * 0  # pyramid: shift by half a log is impossible, so stack flush
                if axis == "x":
                    c.set(x + i, y + layer, z + off, "%s_log[axis=x]" % wood)
                else:
                    c.set(x + off, y + layer, z + i, "%s_log[axis=z]" % wood)


def bench(c, x, y, z, facing="south", wood="spruce", length=2):
    """Stair-block bench facing `facing`, with trapdoor arm rests."""
    back = {"south": "north", "north": "south", "east": "west", "west": "east"}[facing]
    along = (1, 0) if facing in ("south", "north") else (0, 1)
    for i in range(length):
        c.set(x + along[0] * i, y, z + along[1] * i, "%s_stairs[facing=%s,half=bottom]" % (wood, back))


def sign(c, x, y, z, lines, facing="south", wood="spruce", wall=True, glowing=False, colour="black"):
    """A sign with up to four lines of text (front side)."""
    import nbt
    if wall:
        c.set(x, y, z, "%s_wall_sign[facing=%s]" % (wood, facing))
    else:
        rot = {"south": 0, "west": 4, "north": 8, "east": 12}[facing]
        c.set(x, y, z, "%s_sign[rotation=%d]" % (wood, rot))
    msgs = [nbt.String('{"text":"%s"}' % (ln.replace('"', '\\"'))) for ln in (list(lines) + ["", "", "", ""])[:4]]
    blank = [nbt.String('{"text":""}')] * 4
    front = nbt.Compound({"messages": nbt.List(8, msgs), "color": nbt.String(colour),
                          "has_glowing_text": nbt.Byte(1 if glowing else 0)})
    backt = nbt.Compound({"messages": nbt.List(8, blank), "color": nbt.String("black"),
                          "has_glowing_text": nbt.Byte(0)})
    c.block_entity(x, y, z, {"front_text": front, "back_text": backt, "is_waxed": nbt.Byte(1)})


def disc(cx, cz, r):
    """Integer cells of a filled disc."""
    out = []
    ri = int(math.ceil(r))
    for dx in range(-ri, ri + 1):
        for dz in range(-ri, ri + 1):
            if dx * dx + dz * dz <= r * r + 0.01:
                out.append((cx + dx, cz + dz))
    return out


def round_cells(r):
    """Cells (dx, dz) of a block circle round the origin: r = 2.5 is five across, 3.5 seven, 4.5 nine."""
    out = set()
    ri = int(math.ceil(r))
    for dx in range(-ri, ri + 1):
        for dz in range(-ri, ri + 1):
            if dx * dx + dz * dz <= r * r:
                out.add((dx, dz))
    return out


def shell(cells):
    """The outline of a set of cells: those with a neighbour (edge or corner) outside. Walls built on it close."""
    return {p for p in cells
            if any((p[0] + a, p[1] + b) not in cells for a in (-1, 0, 1) for b in (-1, 0, 1))}
