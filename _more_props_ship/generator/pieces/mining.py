"""Mining pieces: mine mouth in a rock outcrop, headframe over a shaft collar."""
from aeprops.core import Build, bname
from aeprops.style import (STONE_BRICK, COBBLE, DARK_ROCK, PATH, PAVERS, SOIL, SOIL_BLOCKS, soil_patch,
                           plant_on, hang_lantern, pebbles, post, beam, corbel, brace, strut_down, Noise2,
                           leaf_clump, small_spruce, GROUND_PLANTS, FLOWERS)
from . import piece, center_anchor

ORES = [("coal_ore", 3), ("iron_ore", 2), ("copper_ore", 2), ("deepslate_iron_ore", 1), ("deepslate_coal_ore", 1)]


# ---------------------------------------------------------------------------------------------
@piece("ae_mine_mouth", "mining (island edges, next to the ore pads)",
       "Timbered adit cut into a mossy tuff/deepslate outcrop: portal with header beam and corbels, hanging sign, "
       "lagged tunnel with a lit bend, rails out to a buffer and ore tub, crates and a lamp post in the yard.",
       "Free-standing outcrop - paste on flat-ish ground (the tunnel sits above ground level, nothing needs carving). "
       "15x13 rock + 5-deep yard to the south.", group="mid")
def mine_mouth():
    b = Build("ae_mine_mouth", seed=81)
    rng = b.rng
    nz = Noise2(81)
    H = {}
    for x in range(-8, 9):
        for z in range(-13, 1):
            h = 10 - 0.2 * x * x - 0.14 * (z + 4) ** 2 + 1.8 * nz.fbm(x * 0.28, z * 0.28)
            if z == 0:
                h -= 0.6
            if h >= 0.8:
                H[(x, z)] = int(h)
    for (x, z), h in H.items():
        for y in range(0, h + 1):
            b.set(x, y, z, b._pick(DARK_ROCK))
        # ore seams on exposed faces
    for (x, z), h in H.items():
        for y in range(1, h):
            exposed = any(H.get((x + dx, z + dz), -1) < y for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if exposed and rng.random() < 0.08:
                b.set(x, y, z, b._pick(ORES))
    # top dressing: soil where the slope is gentle, moss/cobble elsewhere
    for (x, z), h in H.items():
        nbh = [H.get((x + dx, z + dz), -5) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        gentle = min(nbh) >= h - 1
        if gentle and h >= 2:
            b.set(x, h, z, b._pick(SOIL))
            r = rng.random()
            if r < 0.40:
                p = b._pick(GROUND_PLANTS)
                if p != "air":
                    b.set(x, h + 1, z, p)
            elif r < 0.46:
                b.set(x, h + 1, z, b._pick(FLOWERS))
        elif rng.random() < 0.35:
            b.set(x, h, z, b._pick([("mossy_cobblestone", 2), ("moss_block", 1)]))
    # tunnel: straight x=-1..1 from z=0 to -7, bend west x=-6..-2 at z=-7..-5
    clear = [(x, z) for x in range(-1, 2) for z in range(-7, 1)] + \
            [(x, z) for x in range(-6, -1) for z in range(-7, -4)]
    for (x, z) in clear:
        for y in range(1, 4):
            b.clear(x, y, z)
        b.set(x, 0, z, b._pick([("gravel", 3), ("cobblestone", 2), ("tuff", 2), ("coarse_dirt", 1)]))
        b.set(x, 4, z, "spruce_planks")  # lagging ceiling
        if not b.has(x, 5, z):
            b.set(x, 5, z, b._pick(DARK_ROCK))
    # walls (make sure rock encloses the tunnel)
    for (x, z) in clear:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, z + dz)
            if p in clear or (p[1] >= 1):
                continue
            for y in range(0, 5):
                if not b.has(p[0], y, p[1]):
                    b.set(p[0], y, p[1], b._pick(DARK_ROCK))
    # timber sets
    for z in (0, -2, -4, -6):
        for x in (-2, 2):
            post(b, x, 1, 3, z, "stripped_spruce_log[axis=y]")
        beam(b, (-2, 4, z), (2, 4, z))
    for x in (-3, -5):
        for z in (-8, -4):
            post(b, x, 1, 3, z, "stripped_spruce_log[axis=y]")
        beam(b, (x, 4, -8), (x, 4, -4))
    hang_lantern(b, 0, 4, -4, chain=0)
    hang_lantern(b, -4, 4, -6, chain=0)
    b.set(-6, 1, -7, "barrel[facing=up]", be={"id": "minecraft:barrel", "Items": []})
    b.set(-6, 1, -6, "composter[level=0]")
    b.set(-1, 3, -7, "pointed_dripstone[vertical_direction=down,thickness=tip]") if b.has(-1, 4, -7) else None
    # rails
    for z in range(-5, 4):
        b.set(0, 1, z, "rail[shape=north_south]")
    b.set(0, 1, -6, "rail[shape=south_west]")
    for x in range(-5, 0):
        b.set(x, 1, -6, "rail[shape=east_west]")
    # portal
    for x in (-3, 3):
        b.set(x, 0, 1, "cobblestone")
        post(b, x, 1, 4, 1, "stripped_spruce_log[axis=y]")
    beam(b, (-4, 5, 1), (4, 5, 1))
    for x in range(-4, 5):
        b.set(x, 6, 1, "cobbled_deepslate_stairs[facing=north]")
        b.set(x, 5, 0, b.get(x, 5, 0) or "stripped_spruce_wood[axis=x]")
    corbel(b, -2, 4, 1, "west")
    corbel(b, 2, 4, 1, "east")
    b.sign(0, 4, 1, "spruce_hanging_sign[rotation=0,attached=false]", ["", "SHAFT", "~ III ~", ""])
    hang_lantern(b, -4, 5, 1, chain=0)
    hang_lantern(b, 4, 5, 1, chain=0)
    # vines + lichen on the face
    for (x, y) in ((-5, 4), (-5, 3), (5, 3), (-4, 6), (3, 7), (-2, 7)):
        if b.has(x, y, 0) and not b.has(x, y, 1):
            b.vine_on(x, y, 1, ["north"])
    for (x, y) in ((4, 2), (-5, 1), (5, 1)):
        if b.has(x, y, 0) and not b.has(x, y, 1):
            b.set(x, y, 1, "glow_lichen[north=true,east=false,south=false,west=false,up=false,down=false]")
    # yard
    for x in range(-5, 6):
        for z in range(1, 6):
            if abs(x) + max(0, z - 3) * 2 <= 6 and not b.has(x, 0, z):
                b.set(x, 0, z, b._pick(PATH) if abs(x) <= 2 else b._pick(SOIL))
    b.set(0, 1, 4, "stripped_spruce_log[axis=x]")
    b.set(-1, 1, 4, "cobblestone_wall"); b.set(1, 1, 4, "cobblestone_wall")
    b.set(0, 1, 3, "cauldron")
    b.set(0, 1, 2, "rail[shape=north_south]")
    for (x, z) in ((-4, 2), (-4, 3), (-3, 3)):
        b.container(x, 1, z, "barrel[facing=up]") if (x + z) % 2 else b.set(x, 1, z, "composter[level=0]")
    b.set(-4, 2, 2, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    b.set(-3, 2, 3, "lantern")
    # small ore pile
    for (x, z, s) in ((3, 2, "coal_ore"), (4, 2, "cobblestone"), (4, 3, "raw_iron_block"), (3, 3, "tuff_slab[type=bottom]"),
                      (5, 2, "cobblestone_slab[type=bottom]"), (4, 1, "cobblestone_stairs[facing=north]")):
        b.set(x, 1, z, s)
    b.set(4, 2, 2, "iron_ore")
    # tree up top for silhouette
    top = max(h for (x, z), h in H.items() if (x, z) == (3, -8)) if (3, -8) in H else 6
    small_spruce(b, 3, top + 1, -8, h=7)
    b.set(3, top, -8, "coarse_dirt")
    leaf_clump(b, -3, H.get((-3, -9), 6) + 1, -9, 1, 0, 1, mix=[("azalea_leaves", 3), ("flowering_azalea_leaves", 1)])
    for (x, z) in ((2, 4), (-2, 5), (5, 4), (-5, 4)):
        b.set(x, 0, z, "gravel")
    pebbles(b, [(2, 4), (-2, 5), (5, 4), (-5, 4)])
    b.anchor = (0, 1, -5)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_mine_headframe", "mining (quarry floors, beside a pad, over a pit)",
       "Timber headframe over a raised shaft collar: twin legs with backstays, iron-and-spruce sheave wheel, "
       "hoist chain into the shaft, half-hatched opening, top platform with ladder, rail spur and tub.",
       "Collar is above ground, the shaft floor is a dark block at ground level (reads as a drop). "
       "For a real shaft, dig a 3x3 hole under the collar before pasting.", group="landmark")
def mine_headframe():
    b = Build("ae_mine_headframe", seed=91)
    # collar
    for x in range(-2, 3):
        for z in range(-2, 3):
            edge = max(abs(x), abs(z)) == 2
            b.set(x, -1, z, "cobblestone")
            if edge:
                b.set(x, 0, z, b._pick(COBBLE))
                b.set(x, 1, z, b._pick(COBBLE))
                b.set(x, 2, z, b._pick(STONE_BRICK))
            else:
                b.set(x, 0, z, "black_concrete")
    for x in range(-3, 4):
        for z in range(-3, 4):
            if max(abs(x), abs(z)) == 3:
                b.set(x, 0, z, b._pick(PAVERS))
    for x in (-1, 0, 1):
        b.set(x, 2, -1, "spruce_trapdoor[facing=north,half=top,open=false]")
    b.set(-1, 1, -1, "cobweb")
    for y in (1, 2):
        b.set(1, y, 0, "ladder[facing=west]")
    # tapered A-frame legs (x = -3 / 3): back leg z -3 -> -1, front leg z 2 -> 0, smoothed with stairs
    for x in (-3, 3):
        for (z0, zs, face_up, face_dn) in ((-3, 1, "south", "north"), (2, -1, "north", "south")):
            b.set(x, 0, z0, "polished_andesite")
            for seg, (ya, yb) in enumerate(((1, 4), (5, 8), (9, 12))):
                z = z0 + zs * seg
                post(b, x, ya, yb, z, "stripped_spruce_log[axis=y]")
                if seg > 0:
                    b.set(x, ya, z - zs, f"spruce_stairs[facing={face_up},half=bottom]")
                    b.set(x, ya - 1, z, f"spruce_stairs[facing={face_dn},half=top]")
        beam(b, (x, 6, -1), (x, 6, 0))
        brace(b, (x, 1, -7), "south", 4)
        b.set(x, 0, -7, "polished_andesite")
    for (z, y) in ((-3, 3), (2, 3), (-2, 7), (1, 7)):
        beam(b, (-2, y, z), (2, y, z))
    # platform
    for x in range(-3, 4):
        for z in range(-2, 2):
            if (x, z) == (0, 1):
                continue
            b.set(x, 13, z, "stripped_spruce_wood[axis=x]" if z in (-2, 1) else "spruce_planks")
    for x in range(-3, 4):
        for z in (-2, 1):
            b.set(x, 14, z, "spruce_fence")
    for z in (-1, 0):
        for x in (-3, 3):
            b.set(x, 14, z, "spruce_fence")
    for x in (-3, 3):
        for z in (-2, 1):
            b.set(x, 14, z, "stripped_spruce_log[axis=y]")
    b.set(0, 14, 1, "air")
    # sheave wheel (plane x=0), hub at (0,17,-2), radius 3
    for x in (-1, 1):
        post(b, x, 14, 16, -2, "stripped_spruce_log[axis=y]")
    b.fill(-1, 17, -2, 1, 17, -2, "stripped_spruce_log[axis=x]")
    cz, cy = -2, 17
    for dy in (-1, 0, 1):
        for dz in (-3, 3):
            b.set(0, cy + dy, cz + dz, "stripped_dark_oak_log[axis=y]")
    for dz in (-1, 0, 1):
        b.set(0, cy + 3, cz + dz, "stripped_dark_oak_log[axis=z]")
        b.set(0, cy - 3, cz + dz, "stripped_dark_oak_log[axis=z]")
    b.set(0, cy + 2, cz + 2, "dark_oak_stairs[facing=north,half=bottom]")
    b.set(0, cy + 2, cz - 2, "dark_oak_stairs[facing=south,half=bottom]")
    b.set(0, cy - 2, cz + 2, "dark_oak_stairs[facing=north,half=top]")
    b.set(0, cy - 2, cz - 2, "dark_oak_stairs[facing=south,half=top]")
    for dy in (1, 2):
        b.set(0, cy + dy, cz, "chain[axis=y]")
        b.set(0, cy - dy, cz, "chain[axis=y]")
    for dz in (1, 2):
        b.set(0, cy, cz + dz, "chain[axis=z]")
        b.set(0, cy, cz - dz, "chain[axis=z]")
    # hoist chain down the shaft to a kibble
    for y in range(3, 17):
        if not b.has(0, y, 1):
            b.set(0, y, 1, "chain[axis=y]")
    b.set(0, 2, 1, "chain[axis=y]")
    b.set(0, 1, 1, "cauldron")
    # lanterns
    for (x, z) in ((-2, 0), (2, 0)):
        b.set(x, 12, z, "lantern[hanging=true]")
    for (x, z) in ((-2, 2), (2, 2)):
        b.set(x, 3, z, "lantern")
    b.sign(1, 2, 3, "spruce_wall_sign[facing=south]", ["", "SHAFT II", "keep clear", ""])
    # winch drum behind
    for x in (-2, 2):
        b.set(x, 0, -5, "cobblestone")
        b.set(x, 1, -5, "spruce_fence")
    b.fill(-1, 1, -5, 1, 1, -5, "stripped_spruce_log[axis=x]")
    b.set(3, 1, -5, "spruce_fence"); b.set(3, 2, -5, "spruce_fence")
    # rail spur + tub
    b.set(0, 1, 2, "spruce_fence_gate[facing=south,open=false]")
    b.set(0, 2, 2, "air")
    for z in range(3, 7):
        b.set(0, 0, z, "gravel")
        b.set(0, 1, z, "rail[shape=north_south]")
    b.set(0, 0, 7, "gravel")
    b.set(0, 1, 7, "stripped_spruce_log[axis=x]")
    b.set(0, 1, 5, "cauldron")
    b.container(2, 1, 4, "barrel[facing=up]")
    b.set(3, 1, 4, "composter[level=0]")
    b.set(-2, 1, 4, "iron_ore"); b.set(-3, 1, 4, "cobblestone_slab[type=bottom]"); b.set(-2, 1, 5, "coal_ore")
    b.set(-2, 2, 4, "cobblestone_slab[type=bottom]")
    soil_patch(b, 0, -1, 6, 6, y=0, plants=0.35, flowers=0.04)
    center_anchor(b)
    return b
