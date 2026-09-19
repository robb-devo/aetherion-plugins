package de.aetherion.items.core;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.model.ItemCapability;

import org.bukkit.NamespacedKey;

/**
 * Single place for all Aetherion PDC keys.
 *
 * New stats: add the key here, then wire it in {@link de.aetherion.items.manager.ItemManager}.
 */
public final class ItemKeys {

    private ItemKeys() {
    }

    public static NamespacedKey manager() {
        return key("manager");
    }

    /** GUI-only display stacks — never show sell/AH value lore. */
    public static NamespacedKey guiDisplay() {
        return key("gui_display");
    }

    public static NamespacedKey animalAnchor() {
        return key("animal_anchor");
    }

    public static NamespacedKey animalZone() {
        return key("animal_zone");
    }

    public static NamespacedKey mobAnchor() {
        return key("mob_anchor");
    }

    public static NamespacedKey borderlandsAnchor() {
        return key("borderlands_anchor");
    }

    public static NamespacedKey borderlandsRadius() {
        return key("borderlands_radius");
    }

    public static NamespacedKey eldervaleMobAnchor() {
        return key("eldervale_mob_anchor");
    }

    public static NamespacedKey petHabitatAnchor() {
        return key("pet_habitat_anchor");
    }

    public static NamespacedKey petHabitatId() {
        return key("pet_habitat_id");
    }

    public static NamespacedKey petHabitatRadius() {
        return key("pet_habitat_radius");
    }

    public static NamespacedKey petHabitatZone() {
        return key("pet_habitat_zone");
    }

    /** Consumable spirit vial for the Borderlands boss altar. */
    public static NamespacedKey borderlandsSpirit() {
        return key("borderlands_spirit");
    }

    public static NamespacedKey borderlandsSpiritBoss() {
        return key("borderlands_spirit_boss");
    }

    /** Crypt / Colosseum T2 spirit vial (not usable on the Borderlands altar). */
    public static NamespacedKey cryptSpirit() {
        return key("crypt_spirit");
    }

    public static NamespacedKey mobZone() {
        return key("mob_zone");
    }

    public static NamespacedKey npcRemover() {
        return key("npc_remover");
    }

    public static NamespacedKey traderAnchor() {
        return key("trader_anchor");
    }

    public static NamespacedKey traderNpc() {
        return key("trader_npc");
    }

    public static NamespacedKey gearTraderAnchor() {
        return key("gear_trader_anchor");
    }

    public static NamespacedKey fenceAnchor() {
        return key("fence_anchor");
    }

    public static NamespacedKey fenceShop() {
        return key("fence_shop");
    }

    public static NamespacedKey liquidatorAnchor() {
        return key("liquidator_anchor");
    }

    public static NamespacedKey gearShop() {
        return key("gear_shop");
    }

    public static NamespacedKey fishShop() {
        return key("fish_shop");
    }

    public static NamespacedKey bazaarAnchor() {
        return key("bazaar_anchor");
    }

    public static NamespacedKey bazaarNpc() {
        return key("bazaar_npc");
    }

    public static NamespacedKey auctionAnchor() {
        return key("auction_anchor");
    }

    public static NamespacedKey auctionNpc() {
        return key("auction_npc");
    }

    public static NamespacedKey casinoAnchor() {
        return key("casino_anchor");
    }

    public static NamespacedKey casinoNpc() {
        return key("casino_npc");
    }

    public static NamespacedKey slotCabinetAnchor() {
        return key("slot_cabinet_anchor");
    }

    public static NamespacedKey slotCabinet() {
        return key("slot_cabinet");
    }

    public static NamespacedKey slotCabinetFacing() {
        return key("slot_cabinet_facing");
    }

    public static NamespacedKey rouletteCabinetAnchor() {
        return key("roulette_cabinet_anchor");
    }

    public static NamespacedKey rouletteCabinet() {
        return key("roulette_cabinet");
    }

    public static NamespacedKey rouletteCabinetFacing() {
        return key("roulette_cabinet_facing");
    }

    public static NamespacedKey millstoneAnchor() {
        return key("millstone_anchor");
    }

    public static NamespacedKey millstone() {
        return key("millstone");
    }

    public static NamespacedKey millstoneFacing() {
        return key("millstone_facing");
    }

    public static NamespacedKey millstoneArm() {
        return key("millstone_arm");
    }

    public static NamespacedKey millstoneV2Anchor() {
        return key("millstone_v2_anchor");
    }

    public static NamespacedKey millstoneSail() {
        return key("millstone_sail");
    }

    public static NamespacedKey casinoFirework() {
        return key("casino_firework");
    }

    public static NamespacedKey areaTool() {
        return key("area_tool");
    }

    public static NamespacedKey areaId() {
        return key("area_id");
    }

    public static NamespacedKey worldMapTool() {
        return key("world_map_tool");
    }

    public static NamespacedKey worldMapDisplay() {
        return key("world_map_display");
    }

    public static NamespacedKey areaZone() {
        return key("area_zone");
    }

    public static NamespacedKey zoneSpawn() {
        return key("zone_spawn");
    }

    public static NamespacedKey wildlifeTier() {
        return key("wildlife_tier");
    }

    public static NamespacedKey wildlifeLabel() {
        return key("wildlife_label");
    }

    public static NamespacedKey wildlifeTitle() {
        return key("wildlife_title");
    }

    public static NamespacedKey cryptHoloTool() {
        return key("crypt_holo_tool");
    }

    public static NamespacedKey cryptHoloDisplay() {
        return key("crypt_holo_display");
    }

    public static NamespacedKey buildingBannerTool() {
        return key("building_banner_tool");
    }

    public static NamespacedKey buildingBannerDisplay() {
        return key("building_banner_display");
    }

    public static NamespacedKey buildingBannerKind() {
        return key("building_banner_kind");
    }

    public static NamespacedKey buildingBannerPanel() {
        return key("building_banner_panel");
    }

    public static NamespacedKey loreCache() {
        return key("lore_cache");
    }

    public static NamespacedKey listingId() {
        return key("listing_id");
    }

    public static NamespacedKey devAction() {
        return key("dev_action");
    }

    public static NamespacedKey critHit() {
        return key("crit_hit");
    }

    public static NamespacedKey starterRev() {
        return key("starter_rev");
    }

    public static NamespacedKey fishingRev() {
        return key("fishing_rev");
    }

    public static NamespacedKey bossGearRev() {
        return key("boss_gear_rev");
    }

    public static NamespacedKey netherMined() {
        return key("nether_mined");
    }

    public static NamespacedKey item() {
        return AetherKeys.ITEM_ID;
    }

    public static NamespacedKey rarity() {
        return key("rarity");
    }

    public static NamespacedKey boosterType() {
        return key("booster_type");
    }

    public static NamespacedKey sackId() {
        return key("sack_id");
    }

    public static NamespacedKey sackType() {
        return key("sack_type");
    }

    public static NamespacedKey miningPower() {
        return key("mining_power");
    }

    public static NamespacedKey fortune() {
        return key("fortune");
    }

    public static NamespacedKey damage() {
        return key("damage");
    }

    public static NamespacedKey defense() {
        return key("defense");
    }

    public static NamespacedKey health() {
        return key("health");
    }

    public static NamespacedKey spread() {
        return key("spread");
    }

    public static NamespacedKey harvestSpread() {
        return key("harvest_spread");
    }

    public static NamespacedKey fishingSpeed() {
        return key("fishing_speed");
    }

    public static NamespacedKey fishingCatch() {
        return key("fishing_catch");
    }

    public static NamespacedKey fishingRodLevel() {
        return key("fishing_rod_level");
    }

    public static NamespacedKey fishingRodXp() {
        return key("fishing_rod_xp");
    }

    public static NamespacedKey farmingHoeLevel() {
        return key("farming_hoe_level");
    }

    public static NamespacedKey farmingHoeXp() {
        return key("farming_hoe_xp");
    }

    public static NamespacedKey catcherRev() {
        return key("catcher_rev");
    }

    public static NamespacedKey namedGearRev() {
        return key("named_gear_rev");
    }

    public static NamespacedKey catcherGaffLevel() {
        return key("catcher_gaff_level");
    }

    public static NamespacedKey catcherGaffXp() {
        return key("catcher_gaff_xp");
    }

    public static NamespacedKey foragingAxeLevel() {
        return key("foraging_axe_level");
    }

    public static NamespacedKey foragingAxeXp() {
        return key("foraging_axe_xp");
    }

    public static NamespacedKey attackSpread() {
        return key("attack_spread");
    }

    public static NamespacedKey speed() {
        return key("speed");
    }

    public static NamespacedKey catchRate() {
        return key("catch_rate");
    }

    public static NamespacedKey critChance() {
        return key("crit_chance");
    }

    public static NamespacedKey critDamage() {
        return key("crit_damage");
    }

    public static NamespacedKey shortbow() {
        return key("shortbow");
    }

    public static NamespacedKey shortbowInterval() {
        return key("shortbow_interval");
    }

    public static NamespacedKey shortbowPierce() {
        return key("shortbow_pierce");
    }

    public static NamespacedKey shortbowEnderman() {
        return key("shortbow_enderman");
    }

    public static NamespacedKey longbow() {
        return key("longbow");
    }

    public static NamespacedKey longbowCharge() {
        return key("longbow_charge");
    }

    public static NamespacedKey needsAmmo() {
        return key("needs_ammo");
    }

    /** Visual-only FallingBlock debris from sandbox prototype abilities. */
    public static NamespacedKey testDebris() {
        return key("test_debris");
    }

    public static NamespacedKey poisonTicks() {
        return key("poison_ticks");
    }

    public static NamespacedKey wand() {
        return key("wand");
    }

    public static NamespacedKey wandType() {
        return key("wand_type");
    }

    public static NamespacedKey wandRange() {
        return key("wand_range");
    }

    public static NamespacedKey wandCooldown() {
        return key("wand_cooldown");
    }

    public static NamespacedKey wandBlast() {
        return key("wand_blast");
    }

    public static NamespacedKey wandChain() {
        return key("wand_chain");
    }

    public static NamespacedKey wandSlowTicks() {
        return key("wand_slow_ticks");
    }

    public static NamespacedKey crossbow() {
        return key("crossbow");
    }

    public static NamespacedKey undeadDamage() {
        return key("undead_damage");
    }

    public static NamespacedKey undeadResist() {
        return key("undead_resist");
    }

    public static NamespacedKey warpedTier() {
        return key("warped_tier");
    }

    public static NamespacedKey dungeonCoreTier() {
        return key("dungeon_core_tier");
    }

    public static NamespacedKey dungeonGearLevel() {
        return key("dungeon_gear_level");
    }

    public static NamespacedKey dungeonGearXp() {
        return key("dungeon_gear_xp");
    }

    public static NamespacedKey dungeonSchematicFloor() {
        return key("dungeon_schematic_floor");
    }

    public static NamespacedKey dungeonNative() {
        return key("dungeon_native");
    }

    public static NamespacedKey lifesteal() {
        return key("lifesteal");
    }

    public static NamespacedKey thrownAxe() {
        return key("thrown_axe");
    }

    public static NamespacedKey trueDamage() {
        return AetherKeys.TRUE_DAMAGE;
    }

    public static NamespacedKey coalBoosters() {
        return key("coal_boosters");
    }

    public static NamespacedKey ironBoosters() {
        return key("iron_boosters");
    }

    public static NamespacedKey goldBoosters() {
        return key("gold_boosters");
    }

    public static NamespacedKey diamondBoosters() {
        return key("diamond_boosters");
    }

    public static NamespacedKey emeraldBoosters() {
        return key("emerald_boosters");
    }

    public static NamespacedKey redstoneBoosters() {
        return key("redstone_boosters");
    }

    public static NamespacedKey lapisBoosters() {
        return key("lapis_boosters");
    }

    public static NamespacedKey glowstoneBoosters() {
        return key("glowstone_boosters");
    }

    public static NamespacedKey wheatBoosters() {
        return key("wheat_boosters");
    }

    public static NamespacedKey carrotBoosters() {
        return key("carrot_boosters");
    }

    public static NamespacedKey oakBoosters() {
        return key("oak_boosters");
    }

    public static NamespacedKey birchBoosters() {
        return key("birch_boosters");
    }

    public static NamespacedKey statRev() {
        return key("stat_rev");
    }

    public static NamespacedKey charmSuppressedUntil() {
        return AetherKeys.CHARM_SUPPRESS;
    }

    /** Vein Siphon upgrade tier stub (UI later). */
    public static NamespacedKey siphonTier() {
        return key("siphon_tier");
    }

    /** Vein Siphon parts stub (UI later). */
    public static NamespacedKey siphonParts() {
        return key("siphon_parts");
    }

    /** Blueprint tool upgrade tier (1–4). */
    public static NamespacedKey blueprintTier() {
        return key("blueprint_tier");
    }

    /** Upgrade stone target tier (2–4). */
    public static NamespacedKey blueprintUpgradeStone() {
        return key("blueprint_upgrade_stone");
    }

    public static NamespacedKey blueprintStatRev() {
        return key("blueprint_stat_rev");
    }

    /** Eldervale forge hammer / display props. */
    public static NamespacedKey blueprintForgeProp() {
        return key("blueprint_forge_prop");
    }

    public static NamespacedKey forCapability(ItemCapability capability) {
        return switch (capability) {
            case MINING_POWER -> miningPower();
            case FORTUNE -> fortune();
            case DAMAGE -> damage();
            case DEFENSE -> defense();
            case HEALTH -> health();
            case SPREAD -> spread();
            case ATTACK_SPREAD -> attackSpread();
            case SPEED -> speed();
            case PET_CATCH_RATE -> catchRate();
            case CRIT_CHANCE -> critChance();
            case CRIT_DAMAGE -> critDamage();
            case UNDEAD_DAMAGE -> undeadDamage();
            case UNDEAD_RESIST -> undeadResist();
            case HARVEST_SPREAD -> harvestSpread();
            case FISHING_SPEED -> fishingSpeed();
            case FISHING_CATCH -> fishingCatch();
        };
    }

    public static NamespacedKey key(String name) {
        return new NamespacedKey(AetherionItems.NAMESPACE, name);
    }
}
