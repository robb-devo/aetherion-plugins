"""Where everything on Morningbell stands. One place, so terrain, builds, paths and the plugin agree.

x grows east, z grows south (Minecraft). Heights are the y of the ground block; feet stand one above.
"""

X0, Z0, SX, SZ = -160, -160, 320, 320
Y0, SY = 24, 136
SEA = 62            # highest water block
SEED = 1127

# The coast, clockwise from the west cliffs. Smoothed and roughened by terrain.py.
COAST = [
    (-93, -38), (-90, -60), (-78, -78), (-56, -87), (-36, -82),      # bell headland: sea cliffs
    (-24, -72), (-10, -80), (8, -91), (30, -93), (50, -86),          # north cove, wooded north shore
    (66, -72), (82, -56), (98, -46), (114, -38), (128, -30),         # north-east, the lighthouse point
    (133, -21), (126, -12), (108, -8), (93, -3), (88, 8), (91, 24), (96, 40), (90, 58),   # east bight, tower coast
    (92, 72), (99, 86), (95, 102), (78, 110), (60, 106), (48, 96),   # the tidal flat (sand spit)
    (44, 86), (36, 80), (26, 79), (14, 81), (2, 88),                 # the harbour bay
    (-20, 93), (-44, 89), (-64, 77), (-79, 58), (-87, 36),           # south-west downs
    (-91, 14), (-84, -4), (-90, -20),                                # west bite
]

# ground heights of the sites (the y of the block you stand on)
BELL = (-58, -50)
BELL_Y = 101
BURR = (24, -62)
BURR_Y = 80
QUARRY = (-27, -19)
QUARRY_Y = 70
FORGE = (21, 14)
FORGE_Y = 68
VILLAGE = (36, 46)
VILLAGE_Y = 66
YARD = (74, 26)
YARD_Y = 75
LIGHT = (119, -22)
LIGHT_Y = 67
JETTY = (26, 80)        # where the pier leaves the shore; it runs south
JETTY_Y = 63
FLAT = (72, 92)         # the tidal flat (boss)
FLAT_Y = 62

# the stream: control points (x, z, water level), smoothed into a curve. The fall is between FALL_INDEX and +1.
STREAM = [
    (-25, -57, 79), (-11, -52, 79), (1, -47, 79), (5, -39, 78), (2, -30, 78),
    (3, -20, 70), (8, -9, 69), (4, 1, 69), (13, 6, 68), (14, 12, 68), (14, 23, 68), (14, 29, 67), (16, 35, 66),
    (12, 43, 66), (14, 50, 66), (17, 57, 65), (20, 64, 64), (16, 73, 63), (12, 83, 62),
]
FALL_INDEX = 4          # STREAM[4] is the lip, STREAM[5] the plunge pool

# the escarpment: north of this line the island is a plateau about 8 blocks higher (z of the edge at a given x)
SCARP_Z = -29
SCARP_RISE = 8.0

# Ways. kind: trail (earth, 3 wide), road (cobble, 3 wide), lane (earth, 2 wide). Points are centre lines.
PATHS = [
    ("bell_down", "trail", [(-28, -50), (-23, -51), (-17, -54), (-10, -58), (-1, -61), (9, -62)]),
    ("to_falls", "trail", [(16, -52), (13, -46), (10, -41), (9, -38)]),
    ("falls_west", "trail", [(1, -37), (-3, -36), (-7, -34), (-8, -33)]),
    ("to_quarry", "trail", [(-8, -23), (-11, -20), (-14, -18), (-17, -17)]),
    ("to_forge", "trail", [(-15, -14), (-10, -7), (-5, 2), (-1, 11), (3, 18), (8, 22)]),
    ("forge_village", "road", [(18, 22), (24, 26), (29, 33), (33, 40)]),
    ("village_harbour", "road", [(33, 54), (31, 62), (28, 70), (26, 77)]),
    ("to_yard", "trail", [(43, 42), (51, 36), (59, 31), (66, 28)]),
    ("to_flat", "trail", [(77, 33), (82, 44), (85, 56), (84, 68), (80, 77), (75, 82)]),
    ("shore", "lane", [(60, 84), (52, 83), (44, 81), (36, 78), (29, 77)]),
    ("to_light", "lane", [(60, 28), (66, 12), (78, 0), (92, -9), (106, -17), (114, -21)]),
    ("village_west", "lane", [(28, 47), (23, 47), (17, 47)]),
    ("to_farms", "lane", [(9, 47), (-4, 47), (-18, 46), (-28, 49), (-30, 54)]),
    ("to_mill", "lane", [(-29, 49), (-35, 45), (-39, 41)]),
]

# The Bell Steps: a straight stone flight from the summit down east to the plateau (see sites.py).
STEPS_Z = -50
STEPS_X_TOP = -50       # first stair block (the summit pad ends at x = -51)
STEPS_FLIGHTS = 3       # flights of seven steps with a two-block landing between them

# The ways the lessons follow, in order. They get lantern posts; side ways (lighthouse, farm, mill) do not.
ROUTE = ["bell_down", "to_falls", "falls_west", "to_quarry", "to_forge", "forge_village", "to_yard", "to_flat",
         "village_harbour", "shore"]

# The lesson trail: one line from the Landing to the ferry, in the order the lessons are walked. Names are ways
# from PATHS (walked start to end, or end to start with a leading '-'), pairs are single points in between (the
# places where a lesson happens, bridges, stairs). The plugin draws a glowing trail along it and points its arrow
# along it, so nobody has to read a word to find the next teacher.
TRAIL = [
    (-50, -50), (-46, -50), (-29, -50),                 # the Landing, down the Bell Steps
    "bell_down",
    (15, -61), (21, -61), (22, -57),                    # Burr's camp: Burr, his workbench
    "to_falls",
    (6, -38), (4, -38),                                 # the bridge above the fall
    "falls_west",
    (-9, -33), (-9, -24),                               # the stair down the escarpment
    "to_quarry",
    (-17, -13),                                         # Grett, at the mouth of the quarry
    "to_forge",
    (11, 22), (14, 22),                                 # the forge bridge
    "forge_village",
    (35, 43), (39, 43),                                 # across the village square
    "to_yard",
    (70, 28), (74, 30),                                 # the Old Watch: the yard gate, the ring
    "to_flat",
    (74, 85), (72, 91), (66, 88),                       # the Flat: the gate, the bell, the west gate
    "shore",
    (26, 77), (26, 84), (26, 91),                       # the harbour, the pier, the gangway
]

# The island's own biome (a data pack inside the world folder): its colours are the mood of the place. One
# biome for land and sea, so the stream and the bay share one clear water. No rain, no natural spawns.
BIOME = "morningbell:isle"
BIOME_LOOK = {
    "sky_color": 0x8CC4F0,          # a pale early sky
    "fog_color": 0xE8E4D8,          # warm haze at the horizon
    "water_color": 0x58BDE6,        # clear, a little green
    "water_fog_color": 0x2A7C9C,
    "grass_color": 0x8CC65E,        # fresh
    "foliage_color": 0x74B552,
}
