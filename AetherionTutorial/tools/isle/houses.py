"""Timber-framed houses (Fachwerk): stone plinth, posts and rails of stripped spruce, plaster infill, a jettied
upper floor on brackets, a steep roof with overhang, shutters, a chimney that smokes.

Local frame: x runs along the front (0..w-1), z into the depth (0 = back, d-1 = front wall), the front faces +z.
y = 0 is the first course above the ground; the floor replaces the ground block at y = -1.

No leaves, azalea or vines anywhere on a house: next to a log the Foraging plugin would read the frame as a tree.
"""
from build import Mix, RoofMat, gable_roof, mix_set, mixed, stair, slab
from noise import hash01

PLINTH = Mix([("cobblestone", 4), ("andesite", 2.5), ("stone_bricks", 2), ("mossy_cobblestone", 1.2),
              ("cracked_stone_bricks", 0.6)], scale=2.4, seed=401)
# plaster is one material per house (a patchwork of whites reads as dirt, not as weathering)
PLASTER = Mix([("mushroom_stem", 1)], seed=403)
PLASTER_WARM = Mix([("white_terracotta", 1)], seed=405)
PLASTER_OCHRE = Mix([("smooth_sandstone", 1)], seed=407)

ROOFS = {
    "shingle": RoofMat("dark_oak", "spruce", 0.24, ridge="dark_oak", seed=411),
    "slate": RoofMat("deepslate_tile", "deepslate_brick", 0.18, ridge="deepslate_tile", seed=413),
    "tile": RoofMat("brick", None, 0.0, ridge="brick", seed=415),
    "spruce": RoofMat("spruce", "dark_oak", 0.25, ridge="spruce", seed=417),
}


def posts_along(n):
    """Indices of the posts along a wall of n blocks: both ends, and bays of two to three blocks between."""
    if n <= 5:
        return {0, n - 1}
    m = max(1, int(round((n - 1) / 3.5)))
    return {int(round(k * (n - 1) / m)) for k in range(m + 1)}


class House:
    def __init__(self, c, w, d, floors=2, jetty=True, ridge="x", roof="shingle", timber="stripped_spruce",
                 plaster=PLASTER, door=None, chimney=None, wood="spruce", seed=0, gable_window=True,
                 back_door=False, stone_ground="low", fit_out=True, floor=None):
        self.c = c
        self.w, self.d = w, d
        self.floors = floors
        self.jetty = jetty and floors == 2
        self.ridge = ridge
        self.roof = ROOFS[roof] if isinstance(roof, str) else roof
        self.timber = timber
        self.plaster = plaster
        self.wood = wood
        self.door = (w // 2) if door is None else door
        self.chimney = chimney
        self.seed = seed
        self.gable_window = gable_window
        self.back_door = back_door
        self.stone_ground = stone_ground
        self.fit_out = fit_out
        self.floor = floor
        self.zf = d - 1                       # front wall of the ground floor
        self.zu = d if self.jetty else d - 1  # front wall of the upper floor
        self.eave_y = 8 if floors == 2 else 4

    # ------------------------------------------------------------------ pieces
    def log(self, x, y, z, axis):
        self.c.set(x, y, z, "%s_log[axis=%s]" % (self.timber, axis))

    def infill(self, x, y, z):
        mix_set(self.c, x, y, z, self.plaster)

    def wall(self, cells, y0, y1, face, windows_y, window_ok=True, stone_to=None):
        """One wall. `cells` is the ordered list of (x, z) along it; face is its outward direction.
        Rows up to `stone_to` (inclusive) are masonry instead of frame."""
        c = self.c
        n = len(cells)
        posts = posts_along(n)
        out = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[face]
        bays = []
        last = None
        for i in sorted(posts):
            if last is not None and i - last > 1:
                bays.append((last + 1, i - 1))
            last = i
        for i, (x, z) in enumerate(cells):
            for y in range(y0, y1 + 1):
                if stone_to is not None and y <= stone_to:
                    mix_set(c, x, y, z, PLINTH)
                elif i in posts:
                    self.log(x, y, z, "y")
                else:
                    self.infill(x, y, z)
        if window_ok:
            for k, (a, b) in enumerate(bays):
                width = b - a + 1
                if width >= 4:
                    span = [a + width // 2 - 1, a + width // 2]
                else:
                    span = [a + width // 2]
                for i in span:
                    x, z = cells[i]
                    c.set(x, windows_y, z, "glass_pane")
                # shutters on the ground floor only, and not on every window
                if windows_y <= 2 and (self.seed + k + n) % 3 != 0:
                    for i in (span[0] - 1, span[-1] + 1):
                        if 0 <= i < n and i not in posts and a <= i <= b:
                            x, z = cells[i]
                            c.set(x + out[0], windows_y, z + out[1],
                                  "%s_trapdoor[facing=%s,open=true,half=bottom]" % (self.wood, face))
        return posts, bays

    # ------------------------------------------------------------------ build
    def build(self):
        c, w, d = self.c, self.w, self.d
        zf, zu = self.zf, self.zu
        # floor and a clean box of air
        for x in range(0, w):
            for z in range(0, d):
                if self.floor is None:
                    c.set(x, -1, z, "%s_planks" % self.wood)
                else:
                    mix_set(c, x, -1, z, self.floor)
                c.column_down(x, -2, z, "cobblestone", 8)
        for x in range(-1, w + 1):
            for z in range(-1, d + 2):
                for y in range(0, self.eave_y + max(w, d)):
                    if c.name(x, y, z) != "air":
                        c.set(x, y, z, "air")
        # plinth course
        ring = [(x, 0) for x in range(w)] + [(x, zf) for x in range(w)] + [(0, z) for z in range(d)] + \
               [(w - 1, z) for z in range(d)]
        for (x, z) in ring:
            mix_set(c, x, 0, z, PLINTH)
        # ground floor walls
        front = [(x, zf) for x in range(w)]
        back = [(x, 0) for x in range(w)]
        left = [(0, z) for z in range(d)]
        right = [(w - 1, z) for z in range(d)]
        st = 1 if self.stone_ground == "low" else (3 if self.stone_ground == "full" else None)
        self.wall(front, 1, 3, "south", 2, stone_to=st)
        self.wall(back, 1, 3, "north", 2, stone_to=st)
        self.wall(left, 1, 3, "west", 2, stone_to=st)
        self.wall(right, 1, 3, "east", 2, stone_to=st)
        if st == 3:
            # masonry corners get dressed quoins
            for (x, z) in ((0, 0), (w - 1, 0), (0, zf), (w - 1, zf)):
                for y in range(0, 4):
                    c.set(x, y, z, "stone_bricks" if y % 2 == 0 else "andesite")
        # the door: two high through plinth and frame, a step outside
        dx = self.door
        for y in (0, 1):
            c.set(dx, y, zf, "air")
        c.set(dx, 0, zf, "%s_door[facing=north,half=lower,hinge=left,open=false]" % self.wood)
        c.set(dx, 1, zf, "%s_door[facing=north,half=upper,hinge=left,open=false]" % self.wood)
        c.set(dx, 2, zf, "%s_log[axis=x]" % self.timber)
        for x in (dx - 1, dx + 1):
            if 0 < x < w - 1 and c.name(x, 2, zf) != "glass_pane":
                for y in (1, 2):
                    c.set(x, y, zf, "%s_log[axis=y]" % self.timber)
        if self.back_door:
            bx = w - 2 if dx < w // 2 else 1
            for y in (0, 1):
                c.set(bx, y, 0, "air")
            c.set(bx, 0, 0, "%s_door[facing=south,half=lower,hinge=left,open=false]" % self.wood)
            c.set(bx, 1, 0, "%s_door[facing=south,half=upper,hinge=left,open=false]" % self.wood)
        if self.floors == 1:
            # wall plate
            for x in range(w):
                self.log(x, 4, 0, "x")
                self.log(x, 4, zf, "x")
            top = self._roof(0, 0, w - 1, zf, 4)
            if self.fit_out:
                self._inside_one()
            self._chimney(top)
            return top
        # the beam course the upper floor stands on (jettied to the front)
        for x in range(w):
            self.log(x, 4, 0, "x")
            self.log(x, 4, zu, "x")
        for z in range(0, zu + 1):
            self.log(0, 4, z, "z")
            self.log(w - 1, 4, z, "z")
        if self.jetty:
            for x in sorted(posts_along(w)):
                c.set(x, 3, zu, stair(self.wood, "north", top=True))
            for x in range(1, w - 1):
                if c.name(x, 3, zu) == "air" and x not in (dx,):
                    pass
        # upper floor
        for x in range(1, w - 1):
            for z in range(1, zu):
                c.set(x, 4, z, "%s_planks" % self.wood)
        ufront = [(x, zu) for x in range(w)]
        uleft = [(0, z) for z in range(zu + 1)]
        uright = [(w - 1, z) for z in range(zu + 1)]
        self.wall(ufront, 5, 7, "south", 6)
        self.wall(back, 5, 7, "north", 6)
        self.wall(uleft, 5, 7, "west", 6)
        self.wall(uright, 5, 7, "east", 6)
        top = self._roof(0, 0, w - 1, zu, 8)
        if self.fit_out:
            self._inside_two()
        self._chimney(top)
        return top

    def _roof(self, x1, z1, x2, z2, y):
        c = self.c

        def gable(x, yy, z):
            # studs every other block, plaster between, a small window in the middle
            if self.ridge == "x":
                pos, lo, hi = z, z1, z2
            else:
                pos, lo, hi = x, x1, x2
            mid = (lo + hi) / 2.0
            if self.gable_window and abs(pos - mid) < 0.6 and yy == y + 1:
                return "glass_pane"
            if yy == y:
                return "%s_log[axis=%s]" % (self.timber, "z" if self.ridge == "x" else "x")
            if abs(pos - mid) > 0.6 and int(round(abs(pos - mid))) % 3 == 2:
                return "%s_log[axis=y]" % self.timber
            wx, wy, wz = c.at(x, yy, z)
            return self.plaster.at(wx, wy, wz)

        return gable_roof(c, x1, z1, x2, z2, y, self.roof, ridge_axis=self.ridge, overhang=1, gable_fill=gable)

    def _chimney(self, top):
        if not self.chimney:
            return
        c, w, d = self.c, self.w, self.d
        side, pos = self.chimney
        # an outside stack against a side wall
        x = -1 if side == "left" else w
        z = pos
        brick = Mix([("bricks", 5), ("granite", 1.2), ("mud_bricks", 1.5)], scale=2.0, seed=421)
        height = top + 2
        for y in range(-1, height + 1):
            mix_set(c, x, y, z, brick)
        c.column_down(x, -2, z, "cobblestone", 8)
        # hearth inside: an opening with a fire
        ix = 0 if side == "left" else w - 1
        c.set(ix, 0, z, "campfire[lit=true]")
        c.set(ix, 1, z, "air")
        mix_set(c, ix, 2, z, brick)
        # the cap: a fire inside a crown of trapdoors, so only the smoke shows
        c.set(x, height + 1, z, "campfire[lit=true]")
        for (dx, dz, f) in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
            if c.name(x + dx, height + 1, z + dz) == "air":
                c.set(x + dx, height + 1, z + dz, "dark_oak_trapdoor[facing=%s,open=true,half=bottom]" % f)

    # ------------------------------------------------------------------ interiors
    def _lamp(self, x, y, z):
        self.c.set(x, y, z, "lantern[hanging=true]")

    def _inside_one(self):
        c, w, d = self.c, self.w, self.d
        if w >= 5 and d >= 5:
            c.set(1, 0, 1, "barrel[facing=up]")
            c.set(w - 2, 0, 1, "crafting_table")
            c.set(1, 0, d - 2, "chest[facing=east,type=single]")
            self._lamp(w // 2, 3, d // 2)

    def _inside_two(self):
        c, w, d = self.c, self.w, self.d
        zf = self.zf
        # ground floor: table, bench, barrels, a ladder up in the back corner
        lx, lz = w - 2, 1
        for y in range(0, 5):
            c.set(lx, y, lz, "ladder[facing=south]")
        c.set(lx, 4, lz, "ladder[facing=south]")
        if w >= 6 and d >= 6:
            tx, tz = 2, d // 2
            c.set(tx, 0, tz, "%s_fence" % self.wood)
            c.set(tx, 1, tz, "%s_pressure_plate" % self.wood)
            c.set(tx - 1, 0, tz, stair(self.wood, "west"))
            c.set(tx + 1, 0, tz, stair(self.wood, "east"))
            c.set(1, 0, 1, "barrel[facing=up]")
            c.set(1, 1, 1, "barrel[facing=up]")
            c.set(2, 0, 1, "barrel[facing=south]")
        self._lamp(w // 2, 3, d // 2)
        # upper floor: a bed, a chest, a shelf
        if w >= 6:
            c.set(1, 5, 2, "red_bed[facing=north,part=foot]")
            c.set(1, 5, 1, "red_bed[facing=north,part=head]")
            c.set(2, 5, 1, "chest[facing=south,type=single]")
        c.set(1, 5, self.zu - 1, "bookshelf")
        self._lamp(w // 2, 7, d // 2)


def lean_to(c, house, side, z1, z2, depth=3, kind="stable", roof=("spruce", "dark_oak")):
    """A shed leaning against a side wall (local z1..z2), under a shallow roof of slabs that falls away from the
    house by half a block per row. kind: "stable" (hay, a trough, a rail), "wood" (firewood) or "store" (barrels).
    Use depth 3 (or 1): the posts then stand under a lower-half slab and meet it."""
    sgn = -1 if side == "left" else 1
    wall_x = -1 if side == "left" else house.w
    fence = "%s_fence" % house.wood

    def lx(k):                                   # k = 1 next to the wall .. depth at the posts
        return wall_x + sgn * (k - 1)

    def level(k):                                # the slab of row k: its y, and whether it is the upper half
        return 3 - k // 2, (k % 2 == 0)

    for k in range(1, depth + 1):
        y, top = level(k)
        for z in range(z1 - 1, z2 + 2):
            wx, wy, wz = c.at(lx(k), y, z)
            m = roof[1] if hash01(wx, wy, wz, 441) < 0.22 else roof[0]
            c.set(lx(k), y, z, slab(m, top=top))
    yo, _ = level(depth)
    posts = [z1, z2] + ([(z1 + z2) // 2] if z2 - z1 >= 7 else [])
    for z in posts:
        for y in range(0, yo):
            c.set(lx(depth), y, z, fence)
    for k in range(1, depth + 1):
        for z in range(z1, z2 + 1):
            wx, wy, wz = c.at(lx(k), -1, z)
            r = hash01(wx, wy, wz, 443)
            c.set(lx(k), -1, z, "coarse_dirt" if r < 0.5 else ("gravel" if r < 0.7 else "dirt_path"))
    mid = (z1 + z2) // 2
    if kind == "stable":
        c.set(lx(1), 0, z1, "hay_block[axis=y]")
        c.set(lx(1), 1, z1, "hay_block[axis=x]")
        c.set(lx(2), 0, z1, "hay_block[axis=z]")
        c.set(lx(1), 0, z2, "water_cauldron[level=3]")
        for z in range(z1 + 1, z2):              # a rail along the open side, a gap to lead a horse in
            if z not in (mid, mid + 1):
                c.set(lx(depth), 0, z, fence)
        c.set(lx(1), 0, mid, fence)              # the partition between the two stands
        c.set(lx(2), 0, mid, fence)
        c.set(lx(1), 2, mid + 1, "lantern[hanging=true]")
    elif kind == "wood":
        for z in range(z1, z2 + 1):
            for k in range(1, depth):
                c.set(lx(k), 0, z, "spruce_log[axis=x]")
                if (z + k) % 3:
                    c.set(lx(k), 1, z, "spruce_log[axis=x]")
    else:
        for i, z in enumerate(range(z1, z2 + 1)):
            c.set(lx(1), 0, z, "barrel[facing=up]" if i % 3 else "composter")
            if i % 2 == 0 and i % 3:
                c.set(lx(1), 1, z, "barrel[facing=up]")


def dress_front(c, w, d, house, seed=0, flowers=("potted_red_tulip", "potted_cornflower", "potted_oxeye_daisy",
                                                  "potted_allium", "potted_dandelion")):
    """A lantern by the door, flower pots on little shelves under the ground-floor windows."""
    zf = house.zf
    out = zf + 1
    dx = house.door
    lamp_x = dx + 1 if dx + 1 < w - 1 else dx - 1
    if house.jetty:
        c.set(lamp_x, 3, out, "lantern[hanging=true]")
    else:
        c.set(lamp_x, 2, out, "%s_fence" % house.wood)
        c.set(lamp_x, 1, out, "lantern[hanging=true]")
    for x in range(1, w - 1):
        if c.name(x, 2, zf) == "glass_pane" and c.name(x, 1, out) == "air":
            c.set(x, 0, out, "%s_trapdoor[facing=south,open=false,half=top]" % house.wood)
            c.set(x, 1, out, flowers[int(hash01(x, seed, w, 431) * 97) % len(flowers)])
