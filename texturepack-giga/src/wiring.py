"""Where GIGA art goes. Every (material, CMD) below is one Java already sets (AetherionItems
CustomItem / AccessoryItems / DungeonWeaponKind / DungeonRelic / DungeonCore); the pack simply never
drew it, so those items showed a neighbour's icon or the vanilla item.

Art that keeps its existing model (same texture name, PNG replaced) needs no entry here:
worldbite, aetherblade, stormcaller_maul, warped_blade, aetherion_void_stick, hunter_crossbow + frames,
worldhide_*, aetherion_*, ironhide_*, charm_combat / _2 / _3.
"""
import os

H, G, B, L = 'handheld', 'generated', 'bow', 'leather'

WIRE = [
    # ---------------------------------------------------------------- P0 boss / showcase uniques
    ('netherite_sword', 4205, 'helios_solstice', H),                 # Solstice (Helios, Mythic)
    ('iron_sword', 3340, 'seraphine_needle', H),                     # Severance, the Saint's Needle
    ('blaze_rod', 2501, 'staff_of_technical_difficulties', H),
    ('bow', 2205, 'hunter_crossbow_loaded', B),                      # Hunter Crossbow is a BOW item
    ('iron_axe', 1019, 'ashen_cleaver', H),
    ('iron_axe', 1020, 'rotten_cleaver', H),
    ('stone_sword', 1021, 'webweave_fang', H),
    # ---------------------------------------------------------------- dungeon weapons (Warden's Prison)
    ('iron_sword', 2811, 'dungeon_sword', H),
    ('iron_sword', 2812, 'dungeon_t2_sword', H),
    ('netherite_sword', 2813, 'dungeon_t3_sword', H),
    ('bow', 2821, 'dungeon_bow', B),
    ('bow', 2822, 'dungeon_t2_bow', B),
    ('bow', 2823, 'dungeon_t3_bow', B),
    ('blaze_rod', 2831, 'dungeon_wand', H),
    ('blaze_rod', 2832, 'dungeon_t2_wand', H),
    ('blaze_rod', 2833, 'dungeon_t3_wand', H),
    ('iron_axe', 2841, 'dungeon_mace', H),
    ('iron_axe', 2842, 'dungeon_t2_mace', H),
    ('netherite_axe', 2843, 'dungeon_t3_mace', H),
    ('blaze_rod', 2851, 'dungeon_staff', H),
    ('blaze_rod', 2852, 'dungeon_t2_staff', H),
    ('blaze_rod', 2853, 'dungeon_t3_staff', H),
    ('ender_eye', 2792, 'dungeon_t2_weapon_relic', G),
    ('nether_star', 2793, 'dungeon_t3_weapon_relic', G),
    ('paper', 2795, 'weapon_schematic', G),
    ('paper', 2796, 'dungeon_weapon_schematic', G),
    ('leather_helmet', 2800, 'dungeon_t2_relic_helmet', L),
    ('leather_chestplate', 2801, 'dungeon_t2_relic_chestplate', L),
    ('leather_leggings', 2802, 'dungeon_t2_relic_leggings', L),
    ('leather_boots', 2803, 'dungeon_t2_relic_boots', L),
    ('leather_helmet', 2810, 'dungeon_t3_relic_helmet', L),
    ('leather_chestplate', 2811, 'dungeon_t3_relic_chestplate', L),
    ('leather_leggings', 2812, 'dungeon_t3_relic_leggings', L),
    ('leather_boots', 2813, 'dungeon_t3_relic_boots', L),
    # ---------------------------------------------------------------- P1 boss armour sets
    ('netherite_helmet', 2341, 'hollow_sun_helmet', G),              # Corona Visor
    ('netherite_chestplate', 2342, 'hollow_sun_chestplate', G),      # Hollow Heart Cuirass
    ('netherite_leggings', 2343, 'hollow_sun_leggings', G),          # Red Giant Greaves
    ('netherite_boots', 2344, 'hollow_sun_boots', G),                # Starfall Sabatons
    ('netherite_helmet', 4201, 'helios_crown', G),                   # Dawnbearer Crown
    ('netherite_chestplate', 4202, 'helios_heartplate', G),
    ('netherite_leggings', 4203, 'helios_orbit_greaves', G),
    ('netherite_boots', 4204, 'helios_dawn_treads', G),
    ('leather_helmet', 3341, 'seraphine_veil', L),                   # Kintsugi Veil
    ('leather_chestplate', 3342, 'seraphine_bodice', L),             # Porcelain Bodice
    ('leather_leggings', 3343, 'seraphine_bell_skirt', L),           # Marionette Bell Skirt
    ('leather_boots', 3344, 'seraphine_pointe_slippers', L),         # Gilt Pointe Slippers
    ('leather_boots', 2204, 'squids_boot', L),
    # ---------------------------------------------------------------- P2 charms (CMD = 3100 + ordinal*3 + tier)
    ('compass', 3104, 'charm_mining', G),
    ('clock', 3105, 'charm_mining_2', G),
    ('recovery_compass', 3106, 'charm_mining_3', G),
    ('oak_sapling', 3107, 'charm_foraging', G),
    ('apple', 3108, 'charm_foraging_2', G),
    ('honeycomb', 3109, 'charm_foraging_3', G),
    ('wheat_seeds', 3110, 'charm_farming', G),
    ('bowl', 3111, 'charm_farming_2', G),
    ('flower_pot', 3112, 'charm_farming_3', G),
    ('nautilus_shell', 3113, 'charm_fishing', G),
    ('heart_of_the_sea', 3114, 'charm_fishing_2', G),
    ('prismarine_crystals', 3115, 'charm_fishing_3', G),
    ('amethyst_shard', 3116, 'charm_utility', G),
    ('ender_eye', 3117, 'charm_utility_2', G),
    ('sunflower', 3118, 'charm_utility_3', G),
    ('gold_nugget', 3200, 'charm_shiny', G),
    ('blast_furnace', 3201, 'charm_forge', G),
    ('clock', 3202, 'charm_estate', G),
    # ---------------------------------------------------------------- P2 boss trinkets + cores
    ('hopper', 2502, 'void_vacuum_charm', G),
    ('magma_cream', 2503, 'thermal_core', G),
    ('netherite_upgrade_smithing_template', 2504, 'pickaxe_core_of_the_burrower', G),
    ('book', 2505, 'insolvent_ledger', G),
    ('book', 3008, 'recipe_book', G),
    ('heart_of_the_sea', 2501, 'dungeon_core_1', G),
    ('ender_eye', 2502, 'dungeon_core_2', G),
    ('nether_star', 2503, 'dungeon_core_3', G),
]

# model name -> texture name, where they differ
TEXTURE_OF = {}
# registry name -> texture file name, where they differ
TEXTURE_FILE = {}

LEATHER_MATERIALS = ('leather_helmet', 'leather_chestplate', 'leather_leggings', 'leather_boots')
TRIM_MATERIALS = ('netherite_helmet', 'netherite_chestplate', 'netherite_leggings', 'netherite_boots',
                  'leather_helmet', 'leather_chestplate', 'leather_leggings', 'leather_boots')
# base models the live pack bent away from vanilla by accident
BASE_PARENT_FIX = {'blaze_rod': 'minecraft:item/handheld'}

# CIT copies (CIT Resewn / OptiFine users) get the same art as everyone else: CIT texture -> GIGA art
CIT_SAME_ART = {n: n for n in ['charm_' + s + t for s in ('combat', 'mining', 'foraging', 'farming', 'fishing', 'utility')
                               for t in ('', '_2', '_3')] + ['charm_shiny', 'charm_forge', 'charm_estate',
                                                             'void_vacuum_charm', 'hunter_crossbow',
                                                             'hunter_crossbow_pulling_0', 'hunter_crossbow_pulling_1',
                                                             'hunter_crossbow_pulling_2', 'hunter_crossbow_arrow',
                                                             'hunter_crossbow_firework']}
CIT_SAME_ART.update({'tool_bounty_hoe': 'bounty_hoe', 'tool_tide_latch': 'tide_latch',
                     'tool_vein_siphon': 'vein_siphon', 'tool_wild_sight': 'wild_sight'})

# live models whose texture is shared with a material icon get their own texture instead
RETEXTURE = {'compressed_gold_sword': 'compressed_gold_sword', 'compacted_emerald_scythe': 'compacted_emerald_scythe'}


CHROME_GENERATORS = ('gen_gui_chrome.py', 'gen_hud.py', 'gen_bars.py')


def extra_assets(default_font_json):
    """(zip path, bytes) for the GUI chrome, HUD and bar art, rendered by the generators in ../chrome.

    gen_bars merges its providers into the default font, so it gets the live default.json to work on;
    only the PNGs are kept: the font JSONs stay byte-identical to live (glyph contracts live in Java)."""
    import subprocess
    import sys
    import tempfile
    chrome = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'chrome')
    out = []
    with tempfile.TemporaryDirectory() as root:
        font_dir = os.path.join(root, 'assets', 'minecraft', 'font')
        os.makedirs(font_dir)
        with open(os.path.join(font_dir, 'default.json'), 'wb') as fh:
            fh.write(default_font_json)
        for gen in CHROME_GENERATORS:
            subprocess.run([sys.executable, '-I', os.path.join(chrome, gen), root], check=True,
                           stdout=subprocess.DEVNULL, cwd=chrome)
        for dp, dn, fn in os.walk(root):
            for f in fn:
                p = os.path.join(dp, f)
                rel = os.path.relpath(p, root).replace(os.sep, '/')
                if rel.startswith('assets/') and rel.endswith('.png'):
                    with open(p, 'rb') as fh:
                        out.append((rel, fh.read()))
    return sorted(out)
