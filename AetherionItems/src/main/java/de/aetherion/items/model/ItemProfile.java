package de.aetherion.items.model;

import java.util.EnumSet;

public enum ItemProfile {

    /*
     * =========================================================
     * BEGINNER
     * =========================================================
     */

    BEGINNER_PICKAXE(
            "beginner_pickaxe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),


    /*
     * =========================================================
     * SIMPLE TOOLS
     * =========================================================
     */

    SIMPLE_PICKAXE(
            "simple_pickaxe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    SIMPLE_AXE(
            "simple_axe",
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    SIMPLE_HOE(
            "simple_hoe",
            ItemCapability.FORTUNE,
            ItemCapability.HARVEST_SPREAD
    ),


    /*
     * =========================================================
     * SIMPLE WEAPON
     * =========================================================
     */

    SIMPLE_SWORD(
            "simple_sword",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    SIMPLE_LONGBOW(
            "simple_longbow",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    SIMPLE_SHORTBOW(
            "simple_shortbow",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    WOODEN_ROD(
            "wooden_rod",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    BONE_KNIFE(
            "bone_knife",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    VENOM_DAGGER(
            "venom_dagger",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    IRON_LONGBOW(
            "iron_longbow",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    REINFORCED_SHORTBOW(
            "reinforced_shortbow",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    COPPER_ROD(
            "copper_rod",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    FROST_SHARD(
            "frost_shard",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),


    /*
     * =========================================================
     * SIMPLE ARMOR
     * =========================================================
     */

    SIMPLE_HELMET(
            "simple_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    SIMPLE_CHESTPLATE(
            "simple_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    SIMPLE_LEGGINGS(
            "simple_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    SIMPLE_BOOTS(
            "simple_boots",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPEED
    ),

    ROTTEN_HELMET(
            "rotten_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.UNDEAD_DAMAGE,
            ItemCapability.UNDEAD_RESIST
    ),

    ROTTEN_CHESTPLATE(
            "rotten_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.UNDEAD_DAMAGE,
            ItemCapability.UNDEAD_RESIST
    ),

    ROTTEN_LEGGINGS(
            "rotten_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.UNDEAD_DAMAGE,
            ItemCapability.UNDEAD_RESIST
    ),

    ROTTEN_BOOTS(
            "rotten_boots",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.UNDEAD_DAMAGE,
            ItemCapability.UNDEAD_RESIST
    ),

    BONE_HELMET(
            "bone_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.CRIT_CHANCE
    ),

    BONE_CHESTPLATE(
            "bone_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.CRIT_CHANCE
    ),

    BONE_LEGGINGS(
            "bone_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.CRIT_CHANCE
    ),

    BONE_BOOTS(
            "bone_boots",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.SPEED
    ),

    WEBWEAVE_HELMET(
            "webweave_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPEED
    ),

    WEBWEAVE_CHESTPLATE(
            "webweave_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPEED
    ),

    WEBWEAVE_LEGGINGS(
            "webweave_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPEED
    ),

    WEBWEAVE_BOOTS(
            "webweave_boots",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPEED
    ),


    /*
     * =========================================================
     * COMBAT SET
     * =========================================================
     *
     * Combat:
     * - Damage
     * - Attack Spread
     *
     * Combat Armor:
     * - Defense
     * - Health
     * - Attack Spread
     */

    COMBAT_HELMET(
            "combat_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.DAMAGE
    ),

    COMBAT_CHESTPLATE(
            "combat_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    COMBAT_LEGGINGS(
            "combat_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.DAMAGE
    ),

    COMBAT_BOOTS(
            "combat_boots",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.DAMAGE,
            ItemCapability.SPEED
    ),

    COMBAT_HELMET_2(
            "combat_helmet_2",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.DAMAGE
    ),

    COMBAT_CHESTPLATE_2(
            "combat_chestplate_2",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    COMBAT_LEGGINGS_2(
            "combat_leggings_2",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.DAMAGE
    ),

    COMBAT_BOOTS_2(
            "combat_boots_2",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.DAMAGE,
            ItemCapability.SPEED
    ),

    COMBAT_SWORD(
            "combat_sword",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),


    /*
     * =========================================================
     * MINING SET
     * =========================================================
     *
     * Mining:
     * - Mining Power
     * - Fortune
     * - Emerald Spread
     *
     * Mining Armor:
     * - Defense
     * - Mining Power
     * - Fortune
     * - Emerald Spread
     */

    MINING_HELMET(
            "mining_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    MINING_CHESTPLATE(
            "mining_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    MINING_LEGGINGS(
            "mining_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    MINING_BOOTS(
            "mining_boots",
            ItemCapability.DEFENSE,
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD,
            ItemCapability.SPEED
    ),

    MINING_PICKAXE(
            "mining_pickaxe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    VEIN_SIPHON(
            "vein_siphon",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    CANOPY_CLEAVER(
            "canopy_cleaver",
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    BOUNTY_HOE(
            "bounty_hoe",
            ItemCapability.FORTUNE,
            ItemCapability.HARVEST_SPREAD
    ),

    WILD_SIGHT(
            "wild_sight",
            ItemCapability.PET_CATCH_RATE
    ),

    TIDE_LATCH(
            "tide_latch",
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH
    ),

    FARMING_HELMET(
            "farming_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.HARVEST_SPREAD
    ),

    FARMING_CHESTPLATE(
            "farming_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.HARVEST_SPREAD
    ),

    FARMING_LEGGINGS(
            "farming_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.HARVEST_SPREAD
    ),

    FARMING_BOOTS(
            "farming_boots",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.HARVEST_SPREAD,
            ItemCapability.SPEED
    ),

    FARMING_HOE(
            "farming_hoe",
            ItemCapability.FORTUNE,
            ItemCapability.HARVEST_SPREAD
    ),

    FORAGING_HELMET(
            "foraging_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    FORAGING_CHESTPLATE(
            "foraging_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    FORAGING_LEGGINGS(
            "foraging_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    FORAGING_BOOTS(
            "foraging_boots",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD,
            ItemCapability.SPEED
    ),

    FORAGING_AXE(
            "foraging_axe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    FISHING_HELMET(
            "fishing_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH
    ),

    FISHING_CHESTPLATE(
            "fishing_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH
    ),

    FISHING_LEGGINGS(
            "fishing_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH
    ),

    FISHING_BOOTS(
            "fishing_boots",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH,
            ItemCapability.SPEED
    ),

    FISHING_ROD(
            "fishing_rod",
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH
    ),

    DIVING_HELMET(
            "diving_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH
    ),

    DIVING_CHESTPLATE(
            "diving_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH
    ),

    DIVING_LEGGINGS(
            "diving_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH
    ),

    DIVING_BOOTS(
            "diving_boots",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH,
            ItemCapability.SPEED
    ),

    CHARM_COMBAT(
            "charm_combat",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    CHARM_MINING(
            "charm_mining",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    CHARM_FORAGING(
            "charm_foraging",
            ItemCapability.FORTUNE,
            ItemCapability.SPEED,
            ItemCapability.SPREAD
    ),

    CHARM_FARMING(
            "charm_farming",
            ItemCapability.FORTUNE,
            ItemCapability.HARVEST_SPREAD
    ),

    CHARM_FISHING(
            "charm_fishing",
            ItemCapability.FORTUNE,
            ItemCapability.FISHING_SPEED,
            ItemCapability.FISHING_CATCH
    ),

    CHARM_UTILITY(
            "charm_utility",
            ItemCapability.SPEED,
            ItemCapability.PET_CATCH_RATE,
            ItemCapability.HEALTH
    ),

    CHARM_SHINY(
            "charm_shiny",
            ItemCapability.PET_CATCH_RATE
    ),

    CHARM_FORGE(
            "charm_forge"
    ),

    CHARM_ESTATE(
            "charm_estate"
    ),


    /*
     * =========================================================
     * GOD ITEMS
     * =========================================================
     *
     * Test-Items.
     *
     * Alle Capabilities sind erlaubt.
     */

    GOD_PICKAXE(
            "god_pickaxe",
            ItemCapability.values()
    ),

    GOD_AXE(
            "god_axe",
            ItemCapability.values()
    ),

    GOD_SWORD(
            "god_sword",
            ItemCapability.values()
    ),

    GOD_HELMET(
            "god_helmet",
            ItemCapability.values()
    ),

    GOD_CHESTPLATE(
            "god_chestplate",
            ItemCapability.values()
    ),

    GOD_LEGGINGS(
            "god_leggings",
            ItemCapability.values()
    ),

    GOD_BOOTS(
            "god_boots",
            ItemCapability.values()
    ),


    /*
     * =========================================================
     * GOD KIT 2
     * =========================================================
     *
     * Bewusst unterschiedliche Capabilities.
     *
     * Diese Items besitzen zunächst 0 Stats.
     *
     * Das Set dient als kompletter System-Stresstest
     * für:
     *
     * - ActiveEquipmentStats
     * - Damage
     * - Defense
     * - Health
     * - Mining Power
     * - Fortune
     * - Emerald Spread
     * - Redstone Attack Spread
     * - Booster-Kompatibilität
     */


    /*
     * =========================================================
     * GOD2 PICKAXE
     * =========================================================
     *
     * Mining:
     * - Mining Power
     * - Fortune
     * - Emerald Spread
     */

    GOD2_PICKAXE(
            "god2_pickaxe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),


    /*
     * =========================================================
     * GOD2 AXE
     * =========================================================
     *
     * Mining:
     * - Mining Power
     * - Fortune
     * - Emerald Spread
     */

    GOD2_AXE(
            "god2_axe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),


    /*
     * =========================================================
     * GOD2 SWORD
     * =========================================================
     *
     * Combat:
     * - Damage
     * - Redstone Attack Spread
     */

    GOD2_SWORD(
            "god2_sword",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),


    /*
     * =========================================================
     * GOD2 HELMET
     * =========================================================
     *
     * Armor:
     * - Defense
     * - Health
     *
     * Special:
     * - Emerald Spread
     */

    GOD2_HELMET(
            "god2_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPREAD
    ),


    /*
     * =========================================================
     * GOD2 CHESTPLATE
     * =========================================================
     *
     * Armor:
     * - Defense
     * - Health
     *
     * Special:
     * - Redstone Attack Spread
     */

    GOD2_CHESTPLATE(
            "god2_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),


    /*
     * =========================================================
     * GOD2 LEGGINGS
     * =========================================================
     *
     * Armor:
     * - Defense
     * - Health
     *
     * Special:
     * - Lapis Health
     */

    GOD2_LEGGINGS(
            "god2_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),


    /*
     * =========================================================
     * GOD2 BOOTS
     * =========================================================
     *
     * Armor:
     * - Defense
     * - Health
     *
     * Special:
     * - Emerald Spread
     * - Redstone Attack Spread
     * - Lapis Health
     */

    GOD2_BOOTS(
            "god2_boots",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPREAD,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.SPEED
    ),


    /*
     * =========================================================
     * CATCHER SET
     * =========================================================
     *
     * Pet-Catch-Rüstung. Nur Catch Rate als Special,
     * Speed zusätzlich auf den Boots.
     */

    CATCHER_HELMET(
            "catcher_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.PET_CATCH_RATE
    ),

    CATCHER_CHESTPLATE(
            "catcher_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.PET_CATCH_RATE
    ),

    CATCHER_LEGGINGS(
            "catcher_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.PET_CATCH_RATE
    ),

    CATCHER_BOOTS(
            "catcher_boots",
            ItemCapability.DEFENSE,
            ItemCapability.PET_CATCH_RATE,
            ItemCapability.SPEED
    ),


    /*
     * =========================================================
     * SHORTBOWS
     * =========================================================
     *
     * Ranged weapons that fire without arrows.
     * Hold right-click; interval is stored on the item PDC.
     */

    SHORTBOW(
            "shortbow",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    SKULDUGERY_SHORTBOW(
            "skuldugery_shortbow",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    HOLLOW_LONGBOW(
            "hollow_longbow",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    AETHERBLADE(
            "aetherblade",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    BRIDGED_AXE(
            "bridged_axe",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    WARPED_BLADE(
            "warped_blade",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    STAFF_OF_TECHNICAL_DIFFICULTIES(
            "staff_of_technical_difficulties",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    VOID_VACUUM_CHARM(
            "void_vacuum_charm",
            ItemCapability.SPEED
    ),

    THERMAL_CORE(
            "thermal_core",
            ItemCapability.DAMAGE
    ),

    PICKAXE_CORE_OF_THE_BURROWER(
            "pickaxe_core_of_the_burrower",
            ItemCapability.FORTUNE,
            ItemCapability.MINING_POWER,
            ItemCapability.DEFENSE
    ),

    INSOLVENT_LEDGER(
            "insolvent_ledger",
            ItemCapability.DAMAGE
    ),

    DUNGEON_CORE(
            "dungeon_core"
    ),

    DUNGEON_VESTIGE(
            "dungeon_vestige",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    DUNGEON_RELIC(
            "dungeon_relic",
            ItemCapability.DAMAGE,
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    DUNGEON_SWORD(
            "dungeon_sword",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    DUNGEON_BOW(
            "dungeon_bow",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE,
            ItemCapability.SPEED
    ),

    DUNGEON_WAND(
            "dungeon_wand",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    DUNGEON_MACE(
            "dungeon_mace",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.DEFENSE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    DUNGEON_STAFF(
            "dungeon_staff",
            ItemCapability.DAMAGE,
            ItemCapability.HEALTH,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    WEAPON_SCHEMATIC(
            "weapon_schematic"
    ),

    DUNGEON_WEAPON_SCHEMATIC(
            "dungeon_weapon_schematic"
    ),

    ROTTEN_CLEAVER(
            "rotten_cleaver",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    WEBWEAVE_FANG(
            "webweave_fang",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE,
            ItemCapability.SPEED
    ),

    CATCHER_GAFF(
            "catcher_gaff",
            ItemCapability.PET_CATCH_RATE,
            ItemCapability.SPEED
    ),

    SPLINTER_GLAIVE(
            "splinter_glaive",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    ASHEN_CLEAVER(
            "ashen_cleaver",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    DUNGEON_TANK(
            "dungeon_tank",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.UNDEAD_RESIST
    ),

    DUNGEON_ASSASSIN(
            "dungeon_assassin",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE,
            ItemCapability.SPEED
    ),

    DUNGEON_SOLDIER(
            "dungeon_soldier",
            ItemCapability.DAMAGE,
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.SPEED
    ),

    DUNGEON_HEALER(
            "dungeon_healer",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPEED
    ),

    DUNGEON_SHAMAN(
            "dungeon_shaman",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.SPEED
    ),

    SQUIDS_BOOT(
            "squids_boot",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPEED
    ),

    AETHERION_HELMET(
            "aetherion_helmet",
            ItemCapability.DAMAGE,
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE,
            ItemCapability.SPEED
    ),

    AETHERION_CHESTPLATE(
            "aetherion_chestplate",
            ItemCapability.DAMAGE,
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE,
            ItemCapability.SPEED
    ),

    AETHERION_LEGGINGS(
            "aetherion_leggings",
            ItemCapability.DAMAGE,
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE,
            ItemCapability.SPEED
    ),

    AETHERION_BOOTS(
            "aetherion_boots",
            ItemCapability.DAMAGE,
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE,
            ItemCapability.SPEED
    ),

    AETHERION_VOID_STICK(
            "aetherion_void_stick",
            ItemCapability.DAMAGE
    ),


    /*
     * =========================================================
     * IRONHIDE SET (Tank)
     * =========================================================
     */

    IRONHIDE_HELMET(
            "ironhide_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    IRONHIDE_CHESTPLATE(
            "ironhide_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    IRONHIDE_LEGGINGS(
            "ironhide_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    IRONHIDE_BOOTS(
            "ironhide_boots",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),


    /*
     * =========================================================
     * HEALER SET (Support)
     * =========================================================
     */

    HEALER_HELMET(
            "healer_helmet",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    HEALER_CHESTPLATE(
            "healer_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    HEALER_LEGGINGS(
            "healer_leggings",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),

    HEALER_BOOTS(
            "healer_boots",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH
    ),


    /*
     * =========================================================
     * NEW WEAPONS & TOOLS
     * =========================================================
     */

    MENDER_STAFF(
            "mender_staff",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    EMBER_ROD(
            "ember_rod",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    REINFORCED_PICKAXE(
            "reinforced_pickaxe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    OBSIDIAN_MAUL(
            "obsidian_maul",
            ItemCapability.DAMAGE,
            ItemCapability.DEFENSE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),


    /*
     * =========================================================
     * MATERIAL PROGRESSION
     * =========================================================
     */

    COMPRESSED_OAK_CHESTPLATE(
            "compressed_oak_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.FORTUNE,
            ItemCapability.HEALTH
    ),

    COMPACTED_TIMBER_AXE(
            "compacted_timber_axe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPEED
    ),

    COMPRESSED_STONE_PICKAXE(
            "compressed_stone_pickaxe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    COMPACTED_COBBLE_HAMMER(
            "compacted_cobble_hammer",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    COMPRESSED_COAL_RING(
            "compressed_coal_ring",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPEED
    ),

    COPPER_SWORD(
            "copper_sword",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    COMPRESSED_GOLD_SWORD(
            "compressed_gold_sword",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    COMPACTED_MIDAS_DAGGER(
            "compacted_midas_dagger",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    REDSTONE_INFUSED_BOOTS(
            "redstone_infused_boots",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPEED
    ),

    LAPIS_PENDANT(
            "lapis_pendant",
            ItemCapability.HEALTH,
            ItemCapability.DEFENSE,
            ItemCapability.SPEED
    ),

    COMPACTED_DIAMOND_CHESTPLATE(
            "compacted_diamond_chestplate",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.ATTACK_SPREAD
    ),

    COMPACTED_DIAMOND_SWORD(
            "compacted_diamond_sword",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    EMERALD_CROWN(
            "emerald_crown",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.PET_CATCH_RATE,
            ItemCapability.FORTUNE
    ),

    COMPACTED_EMERALD_SCYTHE(
            "compacted_emerald_scythe",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE,
            ItemCapability.SPEED,
            ItemCapability.FORTUNE
    ),

    COMPACTED_IRON_PICKAXE(
            "compacted_iron_pickaxe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    COMPACTED_DIAMOND_PICKAXE(
            "compacted_diamond_pickaxe",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),

    VOIDED_455(
            "voided_455",
            ItemCapability.MINING_POWER,
            ItemCapability.FORTUNE,
            ItemCapability.SPREAD
    ),


    /*
     * =========================================================
     * UNKNOWN
     * =========================================================
     */

    /*
     * =========================================================
     * TEST ARENA GEAR (sandbox prototypes)
     * =========================================================
     */

    ECHO_BLADE(
            "echo_blade",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    PARITY_GAUNTLETS(
            "parity_gauntlets",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    RULEBREAKER_CHARM(
            "rulebreaker_charm",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    METRONOME_BOW(
            "metronome_bow",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    SOFTLOCK_PLATE(
            "softlock_plate",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.DAMAGE
    ),

    NULLSTEP_BOOTS(
            "nullstep_boots",
            ItemCapability.DEFENSE,
            ItemCapability.HEALTH,
            ItemCapability.SPEED
    ),

    BROKER_CONTRACT(
            "broker_contract",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE
    ),

    GRAVWELL_CLEAVER(
            "gravwell_cleaver",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    ASHEN_KATANA(
            "ashen_katana",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    STORMCALLER_MAUL(
            "stormcaller_maul",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    RESONANCE_SCYTHE(
            "resonance_scythe",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    JUDGMENT_STAFF(
            "judgment_staff",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    DASH_DAGGER(
            "dash_dagger",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    CYCLONE_ROD(
            "cyclone_rod",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    PRISM_STAFF(
            "prism_staff",
            ItemCapability.DAMAGE,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    CASCADE_SHORTBOW(
            "cascade_shortbow",
            ItemCapability.DAMAGE,
            ItemCapability.ATTACK_SPREAD,
            ItemCapability.CRIT_CHANCE,
            ItemCapability.CRIT_DAMAGE
    ),

    UNKNOWN(
            "",
            new ItemCapability[0]
    );


    /*
     * =========================================================
     * DATA
     * =========================================================
     */

    private final String itemId;

    private final EnumSet<ItemCapability> capabilities;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    ItemProfile(
            String itemId,
            ItemCapability... capabilities
    ) {

        this.itemId = itemId;


        if (capabilities.length == 0) {

            this.capabilities =
                    EnumSet.noneOf(
                            ItemCapability.class
                    );

            return;
        }


        this.capabilities =
                EnumSet.of(
                        capabilities[0],
                        capabilities
                );
    }


    /*
     * =========================================================
     * FIND PROFILE
     *
     * Exact id first, then strip a trailing tier suffix.
     *
     * combat_helmet_5 -> combat_helmet
     * mining_pickaxe_3 -> mining_pickaxe
     *
     * That keeps higher tiers on the same armor/tool logic
     * without duplicating every profile.
     * =========================================================
     */

    public static ItemProfile fromItemId(
            String itemId
    ) {

        if (itemId == null || itemId.isBlank()) {
            return UNKNOWN;
        }

        for (ItemProfile profile : values()) {
            if (profile.itemId.equalsIgnoreCase(itemId)) {
                return profile;
            }
        }

        String lower = itemId.toLowerCase();
        if (lower.startsWith("dungeon_relic_")) {
            return DUNGEON_RELIC;
        }
        String stripped = de.aetherion.items.dungeon.DungeonGearTier.strip(lower);
        if (stripped.startsWith("dungeon_vestige_")) {
            return DUNGEON_VESTIGE;
        }
        if (stripped.startsWith("dungeon_tank_")) {
            return DUNGEON_TANK;
        }
        if (stripped.startsWith("dungeon_assassin_")) {
            return DUNGEON_ASSASSIN;
        }
        if (stripped.startsWith("dungeon_soldier_")) {
            return DUNGEON_SOLDIER;
        }
        if (stripped.startsWith("dungeon_healer_")) {
            return DUNGEON_HEALER;
        }
        if (stripped.startsWith("dungeon_shaman_")) {
            return DUNGEON_SHAMAN;
        }
        if (lower.endsWith("_sword") && lower.startsWith("dungeon_t")) {
            return DUNGEON_SWORD;
        }
        if (lower.endsWith("_bow") && lower.startsWith("dungeon_t")) {
            return DUNGEON_BOW;
        }
        if (lower.endsWith("_wand") && lower.startsWith("dungeon_t")) {
            return DUNGEON_WAND;
        }
        if (lower.endsWith("_mace") && lower.startsWith("dungeon_t")) {
            return DUNGEON_MACE;
        }
        if (lower.endsWith("_staff") && lower.startsWith("dungeon_t")) {
            return DUNGEON_STAFF;
        }

        String baseId = itemId.replaceAll("_\\d+$", "");

        if (!baseId.equalsIgnoreCase(itemId)) {
            for (ItemProfile profile : values()) {
                if (profile.itemId.equalsIgnoreCase(baseId)) {
                    return profile;
                }
            }
        }

        return UNKNOWN;
    }

    public String getItemId() {
        return itemId;
    }


    /*
     * =========================================================
     * CAPABILITY CHECK
     * =========================================================
     */

    public boolean hasCapability(
            ItemCapability capability
    ) {

        return capabilities.contains(
                capability
        );
    }


    /*
     * =========================================================
     * SPECIAL BOOSTER CHECK
     * =========================================================
     *
     * Die Kompatibilität wird ausschließlich über
     * die vorhandenen Capabilities bestimmt.
     *
     * Emerald
     * -> SPREAD
     *
     * Redstone
     * -> ATTACK_SPREAD
     *
     * Lapis
     * -> HEALTH
     *
     */

    public boolean allowsSpecialBooster(
            BoosterType boosterType
    ) {
        if (boosterType == null) {
            return false;
        }

        ItemCapability capability = boosterType.getSpecialCapability();
        return capability != null && hasCapability(capability);
    }

    public boolean allowsCoreBoosters() {
        return hasCapability(ItemCapability.MINING_POWER)
                || hasCapability(ItemCapability.FORTUNE)
                || hasCapability(ItemCapability.DAMAGE)
                || hasCapability(ItemCapability.DEFENSE);
    }
}