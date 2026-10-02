"""Floor 1 room designs — Warden's Prison. Every room: square, doors centred on all four sides."""
from kit import Room


def floor_plain(room):
    return lambda x, z: room.brick()


def floor_worn(room, patch=0.08):
    def mix(x, z):
        v = room.r.random()
        if v < patch * 0.4:
            return "andesite"
        if v < patch * 0.7:
            return "cobblestone"
        if v < patch:
            return "gravel"
        return room.brick()
    return mix


def floor_inlay(room, ring_mat="polished_andesite", step=6):
    m = room.mid

    def mix(x, z):
        if (x - m) % step == 0 and (z - m) % step == 0:
            return "chiseled_stone_bricks"
        if x == m or z == m:
            return ring_mat
        return room.brick()
    return mix


# ---------------------------------------------------------------- lobby
def gatehouse(seed):
    r = Room("f1_gatehouse", 25, 11, seed)
    r.title = "Gatehouse"
    r.shell(floor_inlay(r))
    S, m = r.S, r.mid
    for cx, cz in ((6, 6), (S - 7, 6), (6, S - 7), (S - 7, S - 7)):
        r.pillar(cx, cz, 1)
        r.hang(cx + 2, cz + 2, 2)
    # benches against west/east walls
    for z in range(3, S - 3):
        if abs(z - m) <= 4:
            continue
        r.stair(1, 1, z, "east", block="spruce_stairs")
        r.stair(S - 2, 1, z, "west", block="spruce_stairs")
    # quartermaster desk (north-east corner) + barrels
    for x in range(S - 7, S - 2):
        r.set(x, 1, 3, "spruce_planks")
        r.slab(x, 2, 3, "bottom", "spruce_slab")
    r.set(S - 5, 2, 3, "lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    for x, z in ((2, 2), (3, 2), (2, 3), (S - 3, S - 3), (S - 4, S - 3), (2, S - 3)):
        r.barrel(x, 1, z)
    r.barrel(2, 2, 2)
    for x in range(4, S - 4, 5):
        r.beam_x(m - 5, r.H - 2, 1, S - 2)
        r.beam_x(m + 5, r.H - 2, 1, S - 2)
    r.hang(m, m, 3)
    for side_z in (1, S - 2):
        for x in (m - 6, m + 6):
            r.wall_torch(x, 3, side_z, "south" if side_z == 1 else "north")
    r.connect()
    r.auto_pads(6)
    r.loot = (m, m - 5)
    return r


# ---------------------------------------------------------------- small (25)
def armory(seed):
    r = Room("f1_armory", 25, 10, seed)
    r.title = "Armory"
    r.shell(floor_worn(r))
    S, m = r.S, r.mid
    # weapon racks: fences with anvils / grindstones between, along two walls
    for side in (0, 2):
        for a in range(3, S - 3):
            if abs(a - m) <= 3:
                continue
            x, z = r._wall_local(side, a, 1)
            kind = (a - 3) % 4
            if kind == 0:
                r.set(x, 1, z, "anvil", {"facing": "east"})
            elif kind == 1:
                r.set(x, 1, z, "spruce_fence")
                r.set(x, 2, z, "spruce_fence")
            elif kind == 2:
                r.set(x, 1, z, "grindstone", {"face": "floor", "facing": "north"})
            else:
                r.barrel(x, 1, z, "north" if side == 2 else "south")
    r.set(m - 6, 1, m, "smithing_table")
    r.set(m + 6, 1, m, "smithing_table")
    r.pillar(m - 6, m - 6, 1)
    r.pillar(m + 6, m + 6, 1)
    r.pillar(m + 6, m - 6, 0)
    r.pillar(m - 6, m + 6, 0)
    r.beam_z(m, r.H - 2, 1, S - 2)
    r.hang(m, m - 4, 2)
    r.hang(m, m + 4, 2)
    r.connect()
    r.auto_pads(10)
    r.auto_loot()
    return r


def interrogation(seed):
    r = Room("f1_interrogation", 25, 10, seed)
    r.title = "Interrogation"
    r.shell(floor_worn(r, 0.12))
    S, m = r.S, r.mid
    r.cell_row(3, 2, 3, width=3, depth=3)
    r.cell_row(1, 2, 3, width=3, depth=3)
    # chair + table off-centre
    cx, cz = m + 4, m + 5
    r.stair(cx, 1, cz, "north", block="spruce_stairs")
    r.set(cx, 1, cz - 1, "spruce_fence")
    r.slab(cx, 2, cz - 1, "top", "spruce_slab")
    r.set(cx, 3, cz - 1, "candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    for x in range(m - 5, m + 6, 5):
        r.hang(x, m, 3)
    for z in (4, S - 5):
        r.beam_x(z, r.H - 2, 1, S - 2)
    r.cobweb(1, r.H - 2, 1)
    r.cobweb(S - 2, r.H - 2, S - 2)
    r.connect()
    r.auto_pads(10)
    r.auto_loot()
    return r


# ---------------------------------------------------------------- medium (31)
def guard_hall(seed):
    r = Room("f1_guard_hall", 31, 11, seed)
    r.title = "Guard Hall"
    r.shell(floor_inlay(r, "andesite", 5))
    S, m = r.S, r.mid
    for cx in (m - 8, m + 8):
        for cz in (6, 12, S - 13, S - 7):
            r.pillar(cx, cz, 1)
    for z in (6, 12, S - 13, S - 7):
        r.beam_x(z, r.H - 2, 1, S - 2)
    for cz in (9, S - 10):
        r.hang(m, cz, 3)
        r.hang(m - 8, cz, 2)
        r.hang(m + 8, cz, 2)
    # tables with barrels along the walls
    for z in range(3, S - 3, 4):
        if abs(z - m) <= 4:
            continue
        r.barrel(2, 1, z, "east")
        r.barrel(S - 3, 1, z, "west")
        r.slab(2, 2, z, "bottom", "spruce_slab")
    for x in range(m - 3, m + 4):
        if x == m:
            continue
        r.set(x, 1, m - 9, "spruce_fence")
        r.slab(x, 2, m - 9, "bottom", "spruce_slab")
    r.connect()
    r.auto_pads(12)
    r.auto_loot()
    return r


def cell_block(seed):
    r = Room("f1_cell_block", 31, 11, seed)
    r.title = "Cell Block"
    r.shell(floor_worn(r))
    S, m = r.S, r.mid
    r.cell_row(3, 1, 4, width=4, depth=5)
    r.cell_row(1, 1, 4, width=4, depth=5)
    # upper catwalk hints (slabs + bars) above the cells
    for z in range(1, S - 1):
        if abs(z - m) <= 3:
            continue
        for x in (6, S - 7):
            r.slab(x, 6, z, "top")
            r.set(x, 7, z, "iron_bars")
    r.beam_z(m, r.H - 2, 1, S - 2)
    for z in range(5, S - 4, 6):
        r.hang(m, z, 3)
    r.connect()
    r.auto_pads(12)
    r.auto_loot()
    return r


def mess_hall(seed):
    r = Room("f1_mess_hall", 31, 11, seed)
    r.title = "Mess Hall"
    r.shell(floor_plain(r))
    S, m = r.S, r.mid
    for tz in (m - 7, m + 7):
        for x in range(5, S - 5):
            if abs(x - m) <= 2:
                continue
            r.set(x, 1, tz, "spruce_fence")
            if x % 6 == 0:
                r.set(x, 2, tz, "spruce_planks")
            else:
                r.slab(x, 2, tz, "bottom", "spruce_slab")
            r.stair(x, 1, tz - 1, "south", block="spruce_stairs")
            r.stair(x, 1, tz + 1, "north", block="spruce_stairs")
    # hearth on the east wall
    for z in range(m - 7, m - 3):
        for y in range(1, r.H - 1):
            r.set(S - 2, y, z, "bricks" if y > 3 else "stone_bricks")
    r.set(S - 3, 1, m - 5, "campfire", {"facing": "west", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    r.set(S - 3, 1, m - 4, "cauldron")
    r.set(S - 3, 1, m - 6, "cauldron")
    for x, z in ((2, 2), (3, 2), (2, 3), (2, S - 3), (S - 3, S - 3)):
        r.barrel(x, 1, z)
    for z in (m - 7, m + 7):
        r.hang(m - 7, z, 3)
        r.hang(m + 7, z, 3)
    r.beam_x(m, r.H - 2, 1, S - 2)
    r.connect()
    r.auto_pads(12)
    r.auto_loot()
    return r


def archive(seed):
    r = Room("f1_warden_archive", 31, 11, seed)
    r.title = "Warden's Archive"
    r.shell(floor_inlay(r, "polished_andesite", 7))
    S, m = r.S, r.mid
    for row in (5, 9, S - 10, S - 6):
        for x in range(4, S - 4):
            if abs(x - m) <= 3:
                continue
            for y in (1, 2):
                r.set(x, y, row, "bookshelf")
            r.slab(x, 3, row, "bottom", "spruce_slab")
    r.set(m - 6, 1, m, "lectern", {"facing": "east", "has_book": "false", "powered": "false"})
    r.set(m + 6, 1, m, "lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    for x, z in ((m - 6, m - 2), (m + 6, m + 2), (m, m - 6), (m, m + 6)):
        r.set(x, 1, z, "candle", {"candles": "4", "lit": "true", "waterlogged": "false"})
    for x in (m - 8, m + 8):
        r.hang(x, m, 3)
    r.cobweb(1, r.H - 2, 1)
    r.cobweb(S - 2, r.H - 2, 1)
    r.connect()
    r.auto_pads(12)
    r.auto_loot()
    return r


def chapel(seed):
    r = Room("f1_ruined_chapel", 31, 12, seed)
    r.title = "Ruined Chapel"
    r.shell(floor_worn(r, 0.15))
    S, m = r.S, r.mid
    # pews facing the altar at +Z end (altar off the door lane)
    for z in range(6, S - 9, 2):
        for x in list(range(4, m - 3)) + list(range(m + 4, S - 4)):
            if r.r.random() < 0.12:
                r.rubble(x, z, 1)
                continue
            r.stair(x, 1, z, "north", block="spruce_stairs")
    for x in range(m - 6, m - 3):
        r.set(x, 1, S - 6, "chiseled_stone_bricks")
        r.set(x, 2, S - 6, "candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    for x in range(m + 4, m + 7):
        r.set(x, 1, S - 6, "chiseled_stone_bricks")
        r.set(x, 2, S - 6, "candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    for cz in (6, S - 7):
        r.pillar(3, cz, 0)
        r.pillar(S - 4, cz, 0)
    # collapsed corner
    for x in range(1, 6):
        for z in range(1, 6 - x):
            r.rubble(x, z, r.r.randint(1, 3))
    r.hang(m, m, 4, "soul_lantern")
    r.connect()
    r.auto_pads(12)
    r.auto_loot()
    return r


# ---------------------------------------------------------------- large (41)
def chain_vault(seed):
    r = Room("f1_chain_vault", 41, 13, seed)
    r.title = "Chain Vault"
    r.shell(floor_inlay(r, "polished_andesite", 8))
    S, m = r.S, r.mid
    for cx in (m - 11, m + 11):
        for cz in (m - 11, m, m + 11):
            if cz == m:
                continue
            r.pillar(cx, cz, 2)
    for cx, cz in ((m, m - 11), (m, m + 11)):
        pass
    # central dais (two steps) with anvils / grindstone
    for x in range(m - 4, m + 5):
        for z in range(m - 4, m + 5):
            edge = max(abs(x - m), abs(z - m))
            if edge == 4:
                r.slab(x, 1, z, "bottom")
            elif edge <= 3:
                r.set(x, 1, z, r.brick())
    r.set(m - 1, 2, m, "anvil", {"facing": "north"})
    r.set(m + 1, 2, m, "grindstone", {"face": "floor", "facing": "east"})
    for x, z in ((m - 3, m - 3), (m + 3, m + 3), (m - 3, m + 3), (m + 3, m - 3)):
        r.set(x, 2, z, "stone_brick_wall")
        r.set(x, 3, z, "lantern", {"hanging": "false", "waterlogged": "false"})
    # chains everywhere
    for x in range(4, S - 4, 5):
        for z in range(4, S - 4, 5):
            if r.near_center(x, z, 5) or r.in_lane(x, z, 6, 3):
                continue
            if r.r.random() < 0.55:
                r.hang(x, z, r.r.randint(3, 6), "lantern" if r.r.random() < 0.4 else None)
    for z in (m - 11, m + 11):
        r.beam_x(z, r.H - 2, 1, S - 2)
    for c in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        for y in range(r.H - 3, r.H - 1):
            r.cobweb(c[0], y, c[1])
    r.connect()
    r.auto_pads(16)
    r.auto_loot()
    return r


def collapsed_wing(seed):
    r = Room("f1_collapsed_wing", 41, 13, seed)
    r.title = "Collapsed Wing"
    r.shell(floor_worn(r, 0.22))
    S, m = r.S, r.mid
    # broken pillar grid
    for cx in range(6, S - 5, 7):
        for cz in range(6, S - 5, 7):
            if r.in_lane(cx, cz, 6, 3) or r.near_center(cx, cz, 4):
                continue
            h = r.r.choice([2, 3, 5, r.H - 2, r.H - 2])
            r.pillar(cx, cz, 1, top=h, cap=h == r.H - 2)
            for _ in range(4):
                rx, rz = cx + r.r.randint(-3, 3), cz + r.r.randint(-3, 3)
                if not r.in_lane(rx, rz) and r.is_air(rx, 1, rz):
                    r.rubble(rx, rz)
    # fallen beams
    for _ in range(4):
        z = r.r.randint(4, S - 5)
        x0 = r.r.randint(3, S - 12)
        for x in range(x0, x0 + 7):
            if r.is_air(x, 1, z) and not r.in_lane(x, z):
                r.set(x, 1, z, "stripped_spruce_log", {"axis": "x"})
    # caved-in corner
    for x in range(S - 9, S - 1):
        for z in range(S - 9, S - 1):
            d = (S - 1 - x) + (S - 1 - z)
            if d < 9:
                r.rubble(x, z, max(1, min(r.H - 2, 9 - d)))
    for x in range(3, S - 3, 6):
        r.cobweb(x, r.H - 2, 1)
        r.cobweb(x, r.H - 2, S - 2)
    r.hang(m, m, 4)
    r.connect()
    r.auto_pads(16)
    r.auto_loot()
    return r


def cistern(seed):
    r = Room("f1_old_cistern", 41, 14, seed)
    r.title = "Old Cistern"

    def floor(x, z):
        v = r.r.random()
        if v < 0.10:
            return "mud_bricks"
        if v < 0.16:
            return "moss_block"
        return r.brick(mossy=0.45, cracked=0.2)
    r.shell(floor)
    S, m = r.S, r.mid
    # 3x3 arch grid
    for cx in (m - 10, m, m + 10):
        for cz in (m - 10, m, m + 10):
            if cx == m and cz == m:
                continue
            r.pillar(cx, cz, 1, top=r.H - 2)
            for d in range(-4, 5):
                for y in (r.H - 3,):
                    if abs(d) > 1:
                        if r.is_air(cx + d, y, cz):
                            r.set(cx + d, y, cz, r.brick(mossy=0.5))
                        if r.is_air(cx, y, cz + d):
                            r.set(cx, y, cz + d, r.brick(mossy=0.5))
    # dripstone + roots from the ceiling
    for x in range(2, S - 2):
        for z in range(2, S - 2):
            if r.is_air(x, r.H - 2, z) and r.r.random() < 0.04:
                r.set(x, r.H - 2, z, "pointed_dripstone", {"thickness": "tip", "vertical_direction": "down", "waterlogged": "false"})
            elif r.is_air(x, r.H - 2, z) and r.r.random() < 0.03:
                r.set(x, r.H - 2, z, "hanging_roots", {"waterlogged": "false"})
            elif r.is_air(x, 1, z) and r.r.random() < 0.05 and not r.in_lane(x, z):
                r.set(x, 1, z, "moss_carpet")
    # central well
    for x in range(m - 2, m + 3):
        for z in range(m - 2, m + 3):
            if max(abs(x - m), abs(z - m)) == 2:
                r.set(x, 1, z, "stone_brick_wall")
            else:
                r.set(x, 0, z, "water", {"level": "0"})
    r.hang(m, m, 4)
    r.connect()
    r.auto_pads(16)
    r.auto_loot()
    return r


def wardens_yard(seed):
    r = Room("f1_execution_yard", 41, 14, seed)
    r.title = "Execution Yard"
    r.shell(floor_inlay(r, "andesite", 10))
    S, m = r.S, r.mid
    # gallows platform (off centre, north half)
    px, pz = m + 8, m - 8
    for x in range(px - 3, px + 4):
        for z in range(pz - 2, pz + 3):
            r.set(x, 1, z, "spruce_planks")
            r.set(x, 2, z, "spruce_planks") if max(abs(x - px), abs(z - pz)) < 2 else None
    for x in (px - 3, px + 3):
        for y in range(2, 8):
            r.set(x, y, pz, "stripped_spruce_log", {"axis": "y"})
    r.beam_x(pz, 8, px - 3, px + 3)
    for x in (px - 1, px + 1):
        for y in range(5, 8):
            r.set(x, y, pz, "chain", {"axis": "y", "waterlogged": "false"})
    for z in range(pz - 2, pz + 3):
        r.stair(px - 4, 1, z, "east")
    # cages
    for cx, cz in ((m - 9, m + 8), (m - 9, m - 8), (m + 8, m + 9)):
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                edge = max(abs(x - cx), abs(z - cz)) == 2
                if edge:
                    for y in range(1, 4):
                        r.set(x, y, z, "iron_bars")
                r.set(x, 4, z, "stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
        r.set(cx, 3, cz, "chain", {"axis": "y", "waterlogged": "false"})
        r.set(cx, 1, cz, "skeleton_skull", {"rotation": str(r.r.randint(0, 15)), "powered": "false"})
    for x in range(4, S - 4, 8):
        for z in (4, S - 5):
            if not r.in_lane(x, z, 6, 3):
                r.hang(x, z, 2)
    r.hang(m, m, 4, "soul_lantern")
    r.connect()
    r.auto_pads(16)
    r.auto_loot()
    return r


# ---------------------------------------------------------------- huge (51)
def great_cross_hall(seed):
    r = Room("f1_great_cross_hall", 51, 15, seed)
    r.title = "Great Cross Hall"
    r.shell(floor_inlay(r, "polished_andesite", 10))
    S, m = r.S, r.mid
    # four massive corner blocks leave a plus-shaped hall + an outer ring aisle
    for sx in (-1, 1):
        for sz in (-1, 1):
            x1, x2 = sorted((m + sx * 7, m + sx * 16))
            z1, z2 = sorted((m + sz * 7, m + sz * 16))
            for x in range(x1, x2 + 1):
                for z in range(z1, z2 + 1):
                    edge = x in (x1, x2) or z in (z1, z2)
                    for y in range(1, r.H - 1):
                        if edge:
                            window = (y in (2, 3)) and ((x - x1) % 3 == 1 or (z - z1) % 3 == 1) and not (x in (x1, x2) and z in (z1, z2))
                            r.set(x, y, z, "iron_bars" if window else r.brick())
                        elif y == 1 and r.r.random() < 0.06:
                            r.set(x, 1, z, "barrel", {"facing": "up", "open": "false"})
            # balcony slab ring on top of each block
            for x in range(x1, x2 + 1):
                for z in range(z1, z2 + 1):
                    r.set(x, r.H - 2, z, r.brick())
    # centre chandelier
    for dx in (-2, 0, 2):
        for dz in (-2, 0, 2):
            r.hang(m + dx, m + dz, 5 if dx == 0 and dz == 0 else 3)
    # aisle lamps
    for a in range(5, S - 5, 6):
        for x, z in ((a, 3), (a, S - 4), (3, a), (S - 4, a)):
            if not r.in_lane(x, z, 6, 3) and r.is_air(x, 1, z):
                r.hang(x, z, 3)
    for c in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        r.cobweb(c[0], r.H - 2, c[1])
    r.connect()
    r.auto_pads(20, 5)
    r.auto_loot()
    return r


def drowned_pit(seed):
    r = Room("f1_sunken_blocks", 51, 15, seed)
    r.title = "Sunken Blocks"
    r.shell(floor_worn(r, 0.18))
    S, m = r.S, r.mid
    # three rows of free-standing cell towers with lanes between
    for cx in (m - 14, m, m + 14):
        for cz in (m - 14, m + 14):
            for x in range(cx - 4, cx + 5):
                for z in range(cz - 3, cz + 4):
                    edge = max(abs(x - cx) / 4, abs(z - cz) / 3) >= 1
                    if not edge:
                        continue
                    front = abs(z - cz) == 3 and abs(x - cx) < 4
                    for y in range(1, 6):
                        r.set(x, y, z, "iron_bars" if front and y <= 3 else r.brick())
                for z in range(cz - 3, cz + 4):
                    r.set(x, 6, z, r.brick())
            r.set(cx, 1, cz, "hay_block", {"axis": "y"})
            r.cobweb(cx + 2, 4, cz)
    for x in range(4, S - 4, 7):
        r.beam_x(m, r.H - 2, 1, S - 2)
        if not r.in_lane(x, m, 6, 3):
            r.hang(x, m, 4)
    for z in (6, S - 7):
        for x in range(3, S - 3, 9):
            r.hang(x, z, 3)
    r.connect()
    r.auto_pads(20, 5)
    r.auto_loot()
    return r


# ---------------------------------------------------------------- boss (45)
def warden_throne(seed):
    r = Room("f1_warden_throne", 45, 17, seed)
    r.title = "Warden's Throne"
    S, m = r.S, r.mid

    def floor(x, z):
        d = ((x - m) ** 2 + (z - m) ** 2) ** 0.5
        if abs(d - 16) < 0.7:
            return "polished_andesite"
        if abs(d - 9) < 0.6:
            return "chiseled_stone_bricks"
        if d < 3:
            return "polished_andesite"
        return r.brick(mossy=0.2, cracked=0.35)
    r.shell(floor)
    # ring of 12 pillars at radius 18 (outside the fight circle)
    import math
    for i in range(12):
        a = i * math.pi / 6 + math.pi / 12
        cx = round(m + 18 * math.cos(a))
        cz = round(m + 18 * math.sin(a))
        if r.in_lane(cx, cz, 7, 4):
            continue
        r.pillar(cx, cz, 1)
        r.hang(cx, cz, 0)
    # the throne sits in the east alcove (off every door lane)
    tx = S - 4
    for z in range(m - 4, m + 5):
        if abs(z - m) <= 3:
            continue
    for x in range(S - 7, S - 1):
        for z in range(m + 5, m + 12):
            r.set(x, 1, z, r.brick())
            if x >= S - 4:
                r.set(x, 2, z, r.brick())
    r.stair(S - 5, 2, m + 8, "west", block="stone_brick_stairs")
    r.set(S - 4, 3, m + 8, "chiseled_stone_bricks")
    r.set(S - 4, 4, m + 8, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
    # chains + soul lanterns over the arena
    for i in range(8):
        a = i * math.pi / 4
        x = round(m + 11 * math.cos(a))
        z = round(m + 11 * math.sin(a))
        r.hang(x, z, 6, "soul_lantern")
    r.hang(m, m, 3, "soul_lantern")
    # cells in the four corners behind the ring
    for (x0, z0) in ((1, 1), (S - 7, 1), (1, S - 7), (S - 7, S - 7)):
        for x in range(x0, x0 + 6):
            for z in range(z0, z0 + 6):
                if x in (x0 + 5,) and x0 == 1 or x == x0 and x0 != 1:
                    for y in range(1, 5):
                        r.set(x, y, z, "iron_bars")
                if r.r.random() < 0.15 and r.is_air(x, 1, z):
                    r.cobweb(x, 1, z)
    r.connect()
    r.auto_pads(8, 6)
    r.loot = (m + 2, m)
    return r


# Pool definition: (builder, size-class, weight)
DESIGNS = [
    (gatehouse, "LOBBY", 1),
    (armory, "SMALL", 3),
    (interrogation, "SMALL", 3),
    (guard_hall, "MEDIUM", 3),
    (cell_block, "MEDIUM", 3),
    (mess_hall, "MEDIUM", 3),
    (archive, "MEDIUM", 2),
    (chapel, "MEDIUM", 2),
    (chain_vault, "LARGE", 2),
    (collapsed_wing, "LARGE", 2),
    (cistern, "LARGE", 2),
    (wardens_yard, "LARGE", 2),
    (great_cross_hall, "HUGE", 1),
    (drowned_pit, "HUGE", 1),
    (warden_throne, "BOSS", 1),
]
