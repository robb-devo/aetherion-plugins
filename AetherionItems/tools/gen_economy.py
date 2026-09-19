# Generates Aetherion economy.yml values from the documented curve.
# Run: python tools/gen_economy.py

from __future__ import annotations

import math
from pathlib import Path

STACK = 128
COMPRESS = 1.4
COMPACT = 1.5
REFINE = 4.0

MARGIN = {1: 1.20, 2: 1.30, 3: 1.45, 4: 1.65, 5: 1.90}
CHARM = {1: 1.25, 2: 1.45, 3: 1.70}
BOOSTER = 1.35
MATERIAL = 1.55


def r(x: float) -> int:
    return max(1, int(round(x)))


def compressed(unit: int) -> int:
    raw = max(1, unit) * STACK
    return max(raw + 1, r(raw * COMPRESS))


def compacted(comp: int) -> int:
    packed = max(1, comp) * STACK
    return max(packed + 1, r(packed * COMPACT))


def refined(compd: int) -> int:
    return max(compd + 1, r(compd * REFINE))


def craft(s: int, m: float) -> int:
    return max(1, r(s * m))


# Skyblock-ish raw units
VANILLA = {
    "COBBLESTONE": 1,
    "DIRT": 1,
    "NETHERRACK": 1,
    "GRAVEL": 1,
    "SAND": 1,
    "OAK_LOG": 1,
    "BIRCH_LOG": 1,
    "SPRUCE_LOG": 1,
    "OAK_PLANKS": 1,
    "STICK": 1,
    "COAL": 2,
    "RAW_IRON": 2,
    "IRON_INGOT": 3,
    "RAW_GOLD": 3,
    "GOLD_INGOT": 4,
    "GOLD_NUGGET": 1,
    "RAW_COPPER": 1,
    "COPPER_INGOT": 1,
    "REDSTONE": 1,
    "LAPIS_LAZULI": 2,
    "DIAMOND": 8,
    "EMERALD": 6,
    "ANCIENT_DEBRIS": 10,
    "NETHERITE_SCRAP": 12,
    "NETHERITE_INGOT": 25,
    "NETHERITE_BLOCK": 225,
    "WHEAT": 1,
    "CARROT": 1,
    "POTATO": 1,
    "SUGAR_CANE": 1,
    "NETHER_WART": 2,
    "HAY_BLOCK": 9,
    "BREAD": 1,
    "ARROW": 1,
    "BONE": 1,
    "ROTTEN_FLESH": 1,
    "ENDER_PEARL": 4,
    "GUNPOWDER": 2,
    "STRING": 1,
    "LEATHER": 2,
    "FEATHER": 1,
    "COD": 1,
    "SALMON": 2,
    "PUFFERFISH": 3,
    "PRISMARINE_SHARD": 2,
    "AMETHYST_CLUSTER": 5,
    "GLOWSTONE": 2,
    "IRON_BOOTS": 12,
    "IRON_BLOCK": 27,
    "GOLD_BLOCK": 36,
    "DIAMOND_BLOCK": 72,
    "EMERALD_BLOCK": 54,
    "COAL_BLOCK": 18,
    "REDSTONE_BLOCK": 9,
    "LAPIS_BLOCK": 18,
    "COPPER_BLOCK": 9,
}

# CompressedResource key -> input material name used for unit lookup
RESOURCES = {
    "cobblestone": "COBBLESTONE",
    "coal": "COAL",
    "raw_iron": "IRON_INGOT",
    "raw_gold": "GOLD_INGOT",
    "raw_copper": "COPPER_INGOT",
    "redstone": "REDSTONE",
    "lapis": "LAPIS_LAZULI",
    "diamond": "DIAMOND",
    "emerald": "EMERALD",
    "oak_log": "OAK_LOG",
    "leather": "LEATHER",
    "bone": "BONE",
    "string": "STRING",
    "wheat": "WHEAT",
    "carrot": "CARROT",
    "potato": "POTATO",
    "gunpowder": "GUNPOWDER",
    "feather": "FEATHER",
    "rotten_flesh": "ROTTEN_FLESH",
    "cod": "COD",
    "salmon": "SALMON",
    "pufferfish": "PUFFERFISH",
    "birch_log": "BIRCH_LOG",
    "spruce_log": "SPRUCE_LOG",
    "sugar_cane": "SUGAR_CANE",
    "nether_wart": "NETHER_WART",
    "prismarine_shard": "PRISMARINE_SHARD",
}

REFINEABLE = {"wheat", "carrot", "potato"}

res_comp: dict[str, int] = {}
res_compd: dict[str, int] = {}
res_ref: dict[str, int] = {}

for key, mat in RESOURCES.items():
    unit = VANILLA[mat]
    c = compressed(unit)
    d = compacted(c)
    res_comp[key] = c
    res_compd[key] = d
    if key in REFINEABLE:
        res_ref[key] = refined(d)


def V(mat: str) -> int:
    return VANILLA[mat]


def C(key: str) -> int:
    return res_comp[key]


def D(key: str) -> int:
    return res_compd[key]


def R(key: str) -> int:
    return res_ref[key]


items: dict[str, int] = {}

# Resources
for key in RESOURCES:
    items[f"compressed_{key}"] = C(key)
    items[f"compacted_{key}"] = D(key)
    if key in REFINEABLE:
        items[f"refined_{key}"] = R(key)
    items[f"quarry_{key}"] = 450

items["quarry_core_shard"] = 180
items["quarry_core"] = 1400
items["quarry_compressor"] = 450
items["quarry_compactor"] = 1400

# --- Skill ladders (approximate shapes from RecipeRegistry) ---
# Combat armor T1: helm5, chest8, legs7, boots4 cobble
combat_t1 = {
    "combat_helmet": 5,
    "combat_chestplate": 8,
    "combat_leggings": 7,
    "combat_boots": 4,
}
for k, n in combat_t1.items():
    items[k] = craft(n * V("COBBLESTONE"), MARGIN[1])

# Combat T2: wrap 8 iron + T1
for piece in combat_t1:
    items[f"{piece}_2"] = craft(items[piece] + 8 * V("IRON_INGOT"), MARGIN[2])

# Combat T3: 4 bone C + 4 rotten C + T2
for piece in combat_t1:
    items[f"{piece}_3"] = craft(
        items[f"{piece}_2"] + 4 * C("bone") + 4 * C("rotten_flesh"), MARGIN[3]
    )

# Combat T4: 4 gold D + 4 bone D + T3
for piece in combat_t1:
    items[f"{piece}_4"] = craft(
        items[f"{piece}_3"] + 4 * D("raw_gold") + 4 * D("bone"), MARGIN[4]
    )

# Combat T5: 4 diamond D + 4 emerald D + T4
for piece in combat_t1:
    items[f"{piece}_5"] = craft(
        items[f"{piece}_4"] + 4 * D("diamond") + 4 * D("emerald"), MARGIN[5]
    )

# Combat sword T1: 2 cobble + stick
items["combat_sword"] = craft(2 * V("COBBLESTONE") + V("STICK"), MARGIN[1])
items["combat_sword_2"] = craft(items["combat_sword"] + 8 * V("IRON_INGOT"), MARGIN[2])
items["combat_sword_3"] = craft(
    items["combat_sword_2"] + 4 * C("bone") + 4 * C("rotten_flesh"), MARGIN[3]
)
items["combat_sword_4"] = craft(
    items["combat_sword_3"] + 4 * D("raw_gold") + 4 * D("bone"), MARGIN[4]
)
items["combat_sword_5"] = craft(
    items["combat_sword_4"] + 4 * D("diamond") + 4 * D("emerald"), MARGIN[5]
)

# Mining armor T1 copper shapes (same counts as combat)
mining_t1 = {
    "mining_helmet": 5,
    "mining_chestplate": 8,
    "mining_leggings": 7,
    "mining_boots": 4,
}
for k, n in mining_t1.items():
    items[k] = craft(n * V("COPPER_INGOT"), MARGIN[1])
for piece in mining_t1:
    items[f"{piece}_2"] = craft(items[piece] + 8 * V("IRON_INGOT"), MARGIN[2])
# T3: iron C + coal C mixed
for piece in mining_t1:
    items[f"{piece}_3"] = craft(
        items[f"{piece}_2"] + 4 * C("raw_iron") + 4 * C("coal"), MARGIN[3]
    )
# T4: copper D + coal D
for piece in mining_t1:
    items[f"{piece}_4"] = craft(
        items[f"{piece}_3"] + 4 * D("raw_copper") + 4 * D("coal"), MARGIN[4]
    )
# T5: diamond + redstone dual
for piece in mining_t1:
    items[f"{piece}_5"] = craft(
        items[f"{piece}_4"] + 4 * D("diamond") + 4 * D("redstone"), MARGIN[5]
    )

# simple pick + mining pick ladder
items["simple_pickaxe"] = craft(3 * V("COBBLESTONE") + 2 * V("STICK"), MARGIN[1])
# mining_pickaxe T1 often coal wrap on simple — treat as simple + coal surround
items["mining_pickaxe"] = craft(items["simple_pickaxe"] + 8 * V("COAL"), MARGIN[1])
items["mining_pickaxe_2"] = craft(
    items["mining_pickaxe"] + 4 * V("IRON_INGOT") + 4 * V("COAL"), MARGIN[2]
)
items["mining_pickaxe_3"] = craft(
    items["mining_pickaxe_2"] + 4 * C("raw_copper") + 4 * C("raw_iron"), MARGIN[3]
)
items["mining_pickaxe_4"] = craft(
    items["mining_pickaxe_3"] + 4 * D("raw_copper") + 4 * D("coal"), MARGIN[4]
)
items["mining_pickaxe_5"] = craft(
    items["mining_pickaxe_4"] + D("diamond") + 3 * D("redstone"), MARGIN[5]
)
items["vein_siphon"] = craft(
    items["mining_pickaxe"] + 4 * V("IRON_INGOT") + 4 * V("REDSTONE"), MARGIN[3]
)

# Farming
farm_t1 = {
    "farming_helmet": 5,
    "farming_chestplate": 8,
    "farming_leggings": 7,
    "farming_boots": 4,
}
for k, n in farm_t1.items():
    items[k] = craft(n * V("WHEAT"), MARGIN[1])
items["farming_hoe"] = craft(2 * V("WHEAT") + 2 * V("STICK"), MARGIN[1])
for piece in list(farm_t1) + ["farming_hoe"]:
    wrap = V("CARROT")
    items[f"{piece}_2"] = craft(items[piece] + 8 * wrap, MARGIN[2])
for piece in farm_t1:
    items[f"{piece}_3"] = craft(items[f"{piece}_2"] + 8 * C("potato"), MARGIN[3])
items["farming_hoe_3"] = craft(items["farming_hoe_2"] + 8 * C("wheat"), MARGIN[3])
for piece in farm_t1:
    items[f"{piece}_4"] = craft(
        items[f"{piece}_3"] + 4 * D("sugar_cane") + 4 * D("carrot"), MARGIN[4]
    )
items["farming_hoe_4"] = craft(
    items["farming_hoe_3"] + 4 * D("sugar_cane") + 4 * D("wheat"), MARGIN[4]
)
for piece in farm_t1:
    items[f"{piece}_5"] = craft(
        items[f"{piece}_4"] + 4 * D("nether_wart") + 4 * D("potato"), MARGIN[5]
    )
items["farming_hoe_5"] = craft(
    items["farming_hoe_4"] + 4 * D("nether_wart") + 4 * D("wheat"), MARGIN[5]
)

# Foraging
forage_t1 = {
    "foraging_helmet": 5,
    "foraging_chestplate": 8,
    "foraging_leggings": 7,
    "foraging_boots": 4,
}
for k, n in forage_t1.items():
    items[k] = craft(n * V("SPRUCE_LOG"), MARGIN[1])
items["foraging_axe"] = craft(3 * V("SPRUCE_LOG") + 2 * V("STICK"), MARGIN[1])
for piece in list(forage_t1) + ["foraging_axe"]:
    items[f"{piece}_2"] = craft(items[piece] + 8 * V("BIRCH_LOG"), MARGIN[2])
for piece in list(forage_t1) + ["foraging_axe"]:
    items[f"{piece}_3"] = craft(items[f"{piece}_2"] + 8 * C("oak_log"), MARGIN[3])
for piece in forage_t1:
    items[f"{piece}_4"] = craft(
        items[f"{piece}_3"] + 4 * D("spruce_log") + 4 * D("birch_log"), MARGIN[4]
    )
items["foraging_axe_4"] = craft(
    items["foraging_axe_3"] + 4 * D("spruce_log") + 4 * D("birch_log"), MARGIN[4]
)
for piece in forage_t1:
    items[f"{piece}_5"] = craft(
        items[f"{piece}_4"] + 4 * D("oak_log") + 4 * D("birch_log"), MARGIN[5]
    )
items["foraging_axe_5"] = craft(
    items["foraging_axe_4"] + 4 * D("oak_log") + 4 * D("birch_log"), MARGIN[5]
)

# Fishing
fish_t1 = {
    "fishing_helmet": 5,
    "fishing_chestplate": 8,
    "fishing_leggings": 7,
    "fishing_boots": 4,
}
for k, n in fish_t1.items():
    items[k] = craft(n * V("COD"), MARGIN[1])
items["fishing_rod"] = craft(2 * V("COD") + 2 * V("STICK") + V("STRING"), MARGIN[1])
for piece in list(fish_t1) + ["fishing_rod"]:
    items[f"{piece}_2"] = craft(items[piece] + 8 * V("SALMON"), MARGIN[2])
for piece in list(fish_t1) + ["fishing_rod"]:
    items[f"{piece}_3"] = craft(items[f"{piece}_2"] + 8 * C("pufferfish"), MARGIN[3])
for piece in fish_t1:
    items[f"{piece}_4"] = craft(
        items[f"{piece}_3"] + 4 * D("prismarine_shard") + 4 * D("salmon"), MARGIN[4]
    )
items["fishing_rod_4"] = craft(
    items["fishing_rod_3"] + 4 * D("prismarine_shard") + 4 * D("salmon"), MARGIN[4]
)
for piece in fish_t1:
    items[f"{piece}_5"] = craft(
        items[f"{piece}_4"] + 4 * D("prismarine_shard") + 4 * D("pufferfish"), MARGIN[5]
    )
items["fishing_rod_5"] = craft(
    items["fishing_rod_4"] + 4 * D("prismarine_shard") + 4 * D("pufferfish"), MARGIN[5]
)

# Charms T1: 2x2 vanilla domain
charm_t1_mats = {
    "charm_combat": V("BONE") * 4,
    "charm_mining": V("COAL") * 4,
    "charm_foraging": V("OAK_LOG") * 4,
    "charm_farming": V("WHEAT") * 4,
    "charm_fishing": V("COD") * 4,
    "charm_utility": V("IRON_INGOT") * 4,
}
for k, s in charm_t1_mats.items():
    items[k] = craft(s, CHARM[1])
charm_t2 = {
    "charm_combat_2": ("charm_combat", "bone"),
    "charm_mining_2": ("charm_mining", "coal"),
    "charm_foraging_2": ("charm_foraging", "oak_log"),
    "charm_farming_2": ("charm_farming", "wheat"),
    "charm_fishing_2": ("charm_fishing", "cod"),
    "charm_utility_2": ("charm_utility", "raw_iron"),
}
for k, (prev, mat) in charm_t2.items():
    items[k] = craft(items[prev] + 8 * C(mat), CHARM[2])
charm_t3 = {
    "charm_combat_3": ("charm_combat_2", "bone"),
    "charm_mining_3": ("charm_mining_2", "coal"),
    "charm_foraging_3": ("charm_foraging_2", "oak_log"),
    "charm_farming_3": ("charm_farming_2", "wheat"),
    "charm_fishing_3": ("charm_fishing_2", "cod"),
    "charm_utility_3": ("charm_utility_2", "raw_iron"),
}
for k, (prev, mat) in charm_t3.items():
    items[k] = craft(items[prev] + 8 * D(mat), CHARM[3])

# Special charms
items["charm_shiny"] = craft(4 * C("diamond") + 4 * C("emerald"), CHARM[2])
items["charm_forge"] = craft(4 * D("raw_iron") + 4 * C("coal"), CHARM[3])
items["charm_estate"] = craft(
    V("LEATHER") + V("BONE") + V("GOLD_INGOT") + 8, CHARM[1]
)

# Catcher set T1–3
catcher_t1 = {
    "catcher_helmet": 5,
    "catcher_chestplate": 8,
    "catcher_leggings": 7,
    "catcher_boots": 4,
}
for k, n in catcher_t1.items():
    items[k] = craft(n * V("STRING") + n * V("IRON_INGOT") // 2, MARGIN[1])
items["catcher_gaff"] = craft(3 * V("IRON_INGOT") + 2 * V("STICK") + V("STRING"), MARGIN[1])
for piece in list(catcher_t1) + ["catcher_gaff"]:
    items[f"{piece}_2"] = craft(items[piece] + 8 * V("FEATHER"), MARGIN[2])
for piece in list(catcher_t1) + ["catcher_gaff"]:
    items[f"{piece}_3"] = craft(items[f"{piece}_2"] + 8 * D("feather"), MARGIN[4])

# Boosters: 9 blocks (carrot = 9×32)
boosters = {
    "coal_booster": ("COAL_BLOCK", 9),
    "iron_booster": ("IRON_BLOCK", 9),
    "gold_booster": ("GOLD_BLOCK", 9),
    "diamond_booster": ("DIAMOND_BLOCK", 9),
    "emerald_booster": ("EMERALD_BLOCK", 9),
    "redstone_booster": ("REDSTONE_BLOCK", 9),
    "lapis_booster": ("LAPIS_BLOCK", 9),
    "glowstone_booster": ("GLOWSTONE", 9),
    "wheat_booster": ("HAY_BLOCK", 9),
    "oak_booster": ("OAK_LOG", 9),
    "birch_booster": ("BIRCH_LOG", 9),
}
for k, (mat, n) in boosters.items():
    items[k] = craft(n * V(mat), BOOSTER)
items["carrot_booster"] = craft(9 * 32 * V("CARROT"), BOOSTER)

# Material progression
items["compressed_oak_chestplate"] = craft(8 * C("oak_log") + V("LEATHER"), MATERIAL)
items["compacted_timber_axe"] = craft(3 * D("oak_log") + 2 * V("STICK"), MATERIAL)
items["compressed_stone_pickaxe"] = craft(3 * C("cobblestone") + 2 * V("STICK"), MATERIAL)
items["compacted_cobble_hammer"] = craft(2 * D("cobblestone") + 2 * V("STICK"), MATERIAL)
items["compressed_coal_ring"] = craft(8 * C("coal") + V("GOLD_NUGGET"), MATERIAL)
items["copper_sword"] = craft(2 * C("raw_copper") + V("STICK"), MATERIAL)
items["compressed_gold_sword"] = craft(2 * C("raw_gold") + V("STICK"), MATERIAL)
items["compacted_midas_dagger"] = craft(
    3 * D("raw_gold") + items["compressed_gold_sword"] + V("STICK"), MATERIAL
)
items["redstone_infused_boots"] = craft(6 * D("redstone") + V("IRON_BOOTS"), MATERIAL)
items["lapis_pendant"] = craft(8 * C("lapis") + V("AMETHYST_CLUSTER"), MATERIAL)
items["compacted_diamond_chestplate"] = craft(4 * D("diamond"), MATERIAL)
items["compacted_diamond_sword"] = craft(
    3 * D("diamond") + V("NETHERITE_SCRAP") + V("STICK"), MATERIAL
)
items["emerald_crown"] = craft(5 * D("emerald") + 2 * V("GOLD_INGOT"), MATERIAL)
items["compacted_emerald_scythe"] = craft(
    2 * D("emerald") + D("diamond") + V("STICK"), MATERIAL
)
items["compacted_iron_pickaxe"] = craft(
    items["compressed_stone_pickaxe"] + 3 * C("raw_iron") + V("STICK"), MATERIAL
)
items["compacted_diamond_pickaxe"] = craft(
    items["compacted_iron_pickaxe"] + 2 * D("diamond") + V("STICK"), MATERIAL
)

# Blueprint stones
items["blueprint_upgrade_stone_2"] = craft(8 * D("raw_iron") + C("diamond"), MATERIAL)
items["blueprint_upgrade_stone_3"] = craft(
    6 * D("diamond") + V("NETHERITE_SCRAP") + 2 * D("raw_gold"), 1.65
)
items["blueprint_upgrade_stone_4"] = craft(
    6 * D("diamond") + 2 * V("ANCIENT_DEBRIS") + V("NETHERITE_BLOCK"), 1.80
)

# Starters / mid weapons
items["simple_longbow"] = craft(3 * V("STICK") + 3 * V("STRING"), MARGIN[1])
items["simple_shortbow"] = craft(2 * V("STICK") + 2 * V("STRING"), MARGIN[1])
items["wooden_rod"] = craft(2 * V("STICK") + 2 * V("STRING"), MARGIN[1])
items["splinter_glaive"] = 18_000
items["ashen_cleaver"] = 45_000
items["bone_knife"] = craft(2 * C("bone") + V("STICK"), MARGIN[2])
items["venom_dagger"] = craft(2 * C("string") + C("gunpowder") + V("STICK"), MARGIN[2])
items["iron_longbow"] = craft(3 * V("IRON_INGOT") + 3 * V("STRING"), MARGIN[2])
items["reinforced_shortbow"] = craft(2 * C("raw_iron") + 2 * V("STRING"), MARGIN[3])
items["copper_rod"] = craft(2 * C("raw_copper") + 2 * V("STRING"), MARGIN[2])
items["frost_shard"] = craft(4 * C("prismarine_shard") + C("cod"), MARGIN[2])
items["mender_staff"] = craft(2 * C("wheat") + C("carrot") + V("STICK"), MARGIN[3])
items["ember_rod"] = craft(3 * C("coal") + 2 * V("STICK"), MARGIN[3])
items["reinforced_pickaxe"] = craft(3 * C("raw_iron") + C("coal") + 2 * V("STICK"), MARGIN[3])
items["obsidian_maul"] = craft(3 * D("cobblestone") + 2 * V("STICK"), MARGIN[4])

# Side armor sets
for prefix, mat in [
    ("rotten", "rotten_flesh"),
    ("bone", "bone"),
    ("webweave", "string"),
    ("ironhide", "leather"),
    ("healer", "wheat"),
]:
    items[f"{prefix}_helmet"] = craft(5 * C(mat), MARGIN[2])
    items[f"{prefix}_chestplate"] = craft(8 * C(mat), MARGIN[2])
    items[f"{prefix}_leggings"] = craft(7 * C(mat), MARGIN[2])
    items[f"{prefix}_boots"] = craft(4 * C(mat), MARGIN[2])

# Diving set — fishing loot / craft mid
for piece, n in [("helmet", 5), ("chestplate", 8), ("leggings", 7), ("boots", 4)]:
    items[f"diving_{piece}"] = craft(n * C("prismarine_shard"), MARGIN[3])

# Sacks
items["mining_sack"] = craft(8 * C("leather") + C("coal"), MARGIN[2])
items["farming_sack"] = craft(8 * C("leather") + C("wheat"), MARGIN[2])
items["foraging_sack"] = craft(8 * C("leather") + C("oak_log"), MARGIN[2])
items["fishing_sack"] = craft(8 * C("leather") + C("cod"), MARGIN[2])
items["combat_sack"] = craft(8 * C("leather") + C("bone"), MARGIN[2])

# Drops / dungeon / boss / god (hand bands)
drops = {
    "dungeon_core": 350_000,
    "dungeon_core_2": 750_000,
    "dungeon_core_3": 1_500_000,
    "dungeon_vestige_helmet": 55_000,
    "dungeon_vestige_chestplate": 65_000,
    "dungeon_vestige_leggings": 60_000,
    "dungeon_vestige_boots": 50_000,
    "dungeon_tank_helmet": 70_000,
    "dungeon_tank_chestplate": 85_000,
    "dungeon_tank_leggings": 78_000,
    "dungeon_tank_boots": 65_000,
    "dungeon_assassin_helmet": 70_000,
    "dungeon_assassin_chestplate": 85_000,
    "dungeon_assassin_leggings": 78_000,
    "dungeon_assassin_boots": 65_000,
    "dungeon_soldier_helmet": 70_000,
    "dungeon_soldier_chestplate": 85_000,
    "dungeon_soldier_leggings": 78_000,
    "dungeon_soldier_boots": 65_000,
    "dungeon_healer_helmet": 70_000,
    "dungeon_healer_chestplate": 85_000,
    "dungeon_healer_leggings": 78_000,
    "dungeon_healer_boots": 65_000,
    "dungeon_shaman_helmet": 70_000,
    "dungeon_shaman_chestplate": 85_000,
    "dungeon_shaman_leggings": 78_000,
    "dungeon_shaman_boots": 65_000,
    "dungeon_relic_t2_helmet": 140_000,
    "dungeon_relic_t2_chestplate": 165_000,
    "dungeon_relic_t2_leggings": 150_000,
    "dungeon_relic_t2_boots": 130_000,
    "dungeon_relic_t2_weapon": 175_000,
    "dungeon_relic_t3_helmet": 280_000,
    "dungeon_relic_t3_chestplate": 330_000,
    "dungeon_relic_t3_leggings": 300_000,
    "dungeon_relic_t3_boots": 260_000,
    "dungeon_relic_t3_weapon": 350_000,
    "dungeon_t2_sword": 220_000,
    "dungeon_t2_bow": 220_000,
    "dungeon_t2_wand": 220_000,
    "dungeon_t3_sword": 440_000,
    "dungeon_t3_bow": 440_000,
    "dungeon_t3_wand": 440_000,
    "aetherblade": 4_500_000,
    "bridged_axe": 2_400_000,
    "warped_blade": 1_800_000,
    "gravwell_cleaver": 2_400_000,
    "staff_of_technical_difficulties": 3_200_000,
    "void_vacuum_charm": 2_600_000,
    "thermal_core": 2_800_000,
    "pickaxe_core_of_the_burrower": 2_700_000,
    "insolvent_ledger": 2_900_000,
    "squids_boot": 1_600_000,
    "skuldugery_shortbow": 2_800_000,
    "hollow_longbow": 2_600_000,
    "aetherion_void_stick": 6_000_000,
    # Aetherion set slightly above strong T5 craft
    "aetherion_helmet": 3_200_000,
    "aetherion_chestplate": 4_000_000,
    "aetherion_leggings": 3_600_000,
    "aetherion_boots": 2_800_000,
    "god_pickaxe": 8_000_000,
    "god_axe": 8_000_000,
    "god_sword": 8_000_000,
    "god_helmet": 8_000_000,
    "god_chestplate": 8_000_000,
    "god_leggings": 8_000_000,
    "god_boots": 8_000_000,
    "god2_pickaxe": 15_000_000,
    "god2_axe": 15_000_000,
    "god2_sword": 15_000_000,
    "god2_helmet": 15_000_000,
    "god2_chestplate": 15_000_000,
    "god2_leggings": 15_000_000,
    "god2_boots": 15_000_000,
}
items.update(drops)

# Print sample key values
print("=== RESOURCES ===")
for k in ["cobblestone", "coal", "raw_iron", "raw_gold", "diamond", "emerald", "wheat", "bone"]:
    print(f"{k}: unit={VANILLA[RESOURCES[k]]} C={C(k)} D={D(k)}" + (f" R={R(k)}" if k in REFINEABLE else ""))

print("\n=== COMBAT HELMET LADDER ===")
for t in ["", "_2", "_3", "_4", "_5"]:
    print(f"combat_helmet{t}: {items[f'combat_helmet{t}']:,}")

print("\n=== FARM HELMET LADDER ===")
for t in ["", "_2", "_3", "_4", "_5"]:
    print(f"farming_helmet{t}: {items[f'farming_helmet{t}']:,}")

print("\n=== MATERIAL ===")
for k in [
    "compressed_oak_chestplate",
    "compacted_midas_dagger",
    "compacted_diamond_chestplate",
    "emerald_crown",
    "compacted_emerald_scythe",
]:
    print(f"{k}: {items[k]:,}")

print(f"\nTotal item entries: {len(items)}")

# Emit yml
out = Path(r"C:\Users\Robbi\IdeaProjects\AetherionItems\src\main\resources\economy.yml")

def section(title: str, keys: list[str]) -> str:
    lines = [f"  # --- {title} ---"]
    for k in keys:
        if k in items:
            lines.append(f"  {k}: {items[k]}")
    return "\n".join(lines) + "\n"

vanilla_lines = "\n".join(f"  {k}: {v}" for k, v in VANILLA.items())

resource_keys = []
for key in RESOURCES:
    resource_keys.append(f"compressed_{key}")
    resource_keys.append(f"compacted_{key}")
    if key in REFINEABLE:
        resource_keys.append(f"refined_{key}")
    resource_keys.append(f"quarry_{key}")
resource_keys += ["quarry_core_shard", "quarry_core", "quarry_compressor", "quarry_compactor"]

crafted_keys = sorted(k for k in items if k not in resource_keys and not k.startswith("dungeon_") and k not in drops)
# Actually split properly
drop_keys = list(drops.keys())
crafted_keys = sorted(k for k in items if k not in resource_keys and k not in drop_keys)

yml = f"""# Aetherion listed coin values (Skyblock-feel).
# Formula (see EconomyCurve.java):
#   compressed = round(unit × 128 × 1.4)
#   compacted  = round(compressed × 128 × 1.5)
#   refined    = round(compacted × 4)
#   crafted    = round(ingredientSum × craftMargin)
# Margins: T1 1.20, T2 1.30, T3 1.45, T4 1.65, T5 1.90
#          charms 1.25/1.45/1.70, boosters 1.35, material 1.55
# Channels: Trader 100% resources, Gear buyback 32%, Silas ×8, Bazaar 50–300%.

vanilla:
{vanilla_lines}

# Flat merge target — also loaded from resources/crafted/drops below.
items: {{}}

resources:
{chr(10).join(f'  {k}: {items[k]}' for k in resource_keys)}

crafted:
{chr(10).join(f'  {k}: {items[k]}' for k in crafted_keys)}

drops:
{chr(10).join(f'  {k}: {items[k]}' for k in drop_keys)}
"""

out.write_text(yml, encoding="utf-8")
print(f"Wrote {out}")
