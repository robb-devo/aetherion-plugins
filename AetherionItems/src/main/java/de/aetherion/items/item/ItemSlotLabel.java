package de.aetherion.items.item;

import de.aetherion.items.dungeon.DungeonCalling;
import de.aetherion.items.dungeon.DungeonPiece;
import de.aetherion.items.dungeon.DungeonWeaponKind;

import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.Map;

/**
 * The type line under the item name ({@code Mining Pickaxe}, {@code Core Booster}, …).
 * Short, precise, the real slot. See docs/ITEM_TOOLTIP_SPEC.md.
 */
public final class ItemSlotLabel {

    /** One-offs whose id says nothing useful about the slot. */
    private static final Map<String, String> OVERRIDES = Map.ofEntries(
            Map.entry("beginner_pickaxe", "Starter Pickaxe"),
            Map.entry("aetherblade", "Sword"),
            Map.entry("gravwell_cleaver", "Sword"),
            Map.entry("ashen_katana", "Katana"),
            Map.entry("ashen_cleaver", "Axe"),
            Map.entry("rotten_cleaver", "Axe"),
            Map.entry("bridged_axe", "Axe"),
            Map.entry("warped_blade", "Sword"),
            Map.entry("worldbite", "Sword"),
            Map.entry("helios_solstice", "Sword"),
            Map.entry("seraphine_needle", "Sword"),
            Map.entry("webweave_fang", "Dagger"),
            Map.entry("bone_knife", "Dagger"),
            Map.entry("venom_dagger", "Dagger"),
            Map.entry("dash_dagger", "Dagger"),
            Map.entry("splinter_glaive", "Glaive"),
            Map.entry("echo_blade", "Sword"),
            Map.entry("obsidian_maul", "Maul"),
            Map.entry("meteor_mace", "Mace"),
            Map.entry("stormcaller_maul", "Mace"),
            Map.entry("parity_gauntlets", "Gauntlets"),
            Map.entry("world_splitter", "Axe"),
            Map.entry("aetherion_void_stick", "Wand"),
            Map.entry("wooden_rod", "Wand"),
            Map.entry("copper_rod", "Wand"),
            Map.entry("ember_rod", "Wand"),
            Map.entry("frost_shard", "Wand"),
            Map.entry("cataclysm_rod", "Wand"),
            Map.entry("cyclone_rod", "Wand"),
            Map.entry("terminus", "Wand"),
            Map.entry("mender_staff", "Staff"),
            Map.entry("judgment_staff", "Staff"),
            Map.entry("prism_staff", "Staff"),
            Map.entry("staff_of_technical_difficulties", "Staff"),
            Map.entry("resonance_scythe", "Scythe"),
            Map.entry("skyreaver_spools", "Crossbow"),
            Map.entry("metronome_bow", "Bow"),
            Map.entry("hunter_crossbow", "Bow"),
            Map.entry("deepsong_conch", "Relic"),
            Map.entry("vesper_bell", "Relic"),
            Map.entry("rune_sigil", "Relic"),
            Map.entry("broker_contract", "Contract"),
            Map.entry("rulebreaker_charm", "Charm · Off-hand"),
            Map.entry("void_vacuum_charm", "Charm · Off-hand"),
            Map.entry("insolvent_ledger", "Relic · Off-hand"),
            Map.entry("thermal_core", "Relic · Off-hand"),
            Map.entry("pickaxe_core_of_the_burrower", "Relic · Off-hand"),
            Map.entry("squids_boot", "Boots"),
            Map.entry("nullstep_boots", "Boots"),
            Map.entry("softlock_plate", "Chestplate"),
            Map.entry("grappling_hook", "Traversal Tool"),
            Map.entry("portal_gun", "Traversal Tool"),
            Map.entry("aetherion_summons", "Summon"),
            Map.entry("spectacle_star", "Boss Trophy"),
            Map.entry("recipe_book", "Guide"),
            Map.entry("dragon_ascension_vial", "Ritual Item"),
            Map.entry("aether_blood_vial", "Consumable"),
            Map.entry("lesser_aether_blood_vial", "Consumable"),
            Map.entry("wild_sight", "Pet Lens"),
            Map.entry("tide_latch", "Fishing Rod"),
            Map.entry("vein_siphon", "Mining Pickaxe"),
            Map.entry("canopy_cleaver", "Foraging Axe"),
            Map.entry("bounty_hoe", "Farming Hoe"),
            Map.entry("voided_455", "Mining Pickaxe"),
            Map.entry("reinforced_pickaxe", "Mining Pickaxe"),
            Map.entry("compacted_cobble_hammer", "Mining Pickaxe"),
            Map.entry("compacted_timber_axe", "Foraging Axe"),
            Map.entry("compacted_emerald_scythe", "Farming Hoe"),
            Map.entry("compressed_coal_ring", "Ring · Off-hand"),
            Map.entry("lapis_pendant", "Pendant · Off-hand"),
            Map.entry("emerald_crown", "Helmet"),
            Map.entry("resource_sack", "Storage"),
            Map.entry("booster_sack", "Storage"),
            Map.entry("quarry_core", "Quarry Part"),
            Map.entry("quarry_core_shard", "Quarry Part"),
            Map.entry("quarry_compressor", "Quarry Part"),
            Map.entry("quarry_compactor", "Quarry Part"),
            Map.entry("crystal_find", "Signature Find"),
            Map.entry("prize_crop", "Signature Find"),
            Map.entry("fish_trophy", "Signature Find"),
            Map.entry("weapon_schematic", "Schematic"),
            Map.entry("dungeon_weapon_schematic", "Schematic")
    );

    /** Boss / mob sets: the piece name already carries the set, so the type says slot first. */
    private static final java.util.Set<String> SET_FAMILIES = java.util.Set.of(
            "Aetherion", "Worldhide", "Dawnbearer", "Hollow Sun", "Saint's", "Healer", "Ironhide",
            "Rotten", "Bone", "Webweave"
    );

    private ItemSlotLabel() {
    }

    /** Dark-gray type line, or null if unknown. */
    public static String loreLine(String itemId) {
        String label = plain(itemId);
        return label == null || label.isBlank() ? null : "§8" + label;
    }

    /** Uppercase type fragment (legacy callers). */
    public static String footerType(String itemId) {
        String label = plain(itemId);
        return label == null || label.isBlank() ? "ITEM" : label.toUpperCase(Locale.ROOT);
    }

    public static String fromBannerLine(String line) {
        if (line == null || !LoreLayout.isCategoryBanner(line)) {
            return null;
        }
        String plain = ChatColor.stripColor(line).trim()
                .replaceAll("[^A-Za-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.isEmpty() ? null : plain;
    }

    public static String plain(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        String override = OVERRIDES.get(id);
        if (override != null) {
            return override;
        }
        if (id.startsWith("god2_")) {
            String slot = slotWord(id);
            return slot == null ? "Test Kit" : "Test Kit · " + slot;
        }
        if (id.startsWith("god_")) {
            String slot = slotWord(id);
            return slot == null ? "God Kit" : "God Kit · " + slot;
        }
        if (id.endsWith("_booster")) {
            boolean core = id.startsWith("coal_") || id.startsWith("iron_") || id.startsWith("gold_") || id.startsWith("diamond_");
            return core ? "Core Booster" : "Special Booster";
        }
        if (id.startsWith("blueprint_upgrade_stone")) {
            return "Upgrade Stone";
        }
        if (id.startsWith("blueprint_")) {
            return "Blueprint";
        }
        if (id.startsWith("dungeon_core")) {
            return "Dungeon Core";
        }
        if (id.startsWith("isle_heartwood_")) {
            return "Signature Find";
        }
        if (id.startsWith("quarry_")) {
            return "Island Quarry";
        }
        if (id.startsWith("dungeon_")) {
            return dungeonLabel(id);
        }
        if (id.startsWith("charm_") || id.contains("accessory") || id.contains("talisman")) {
            String skill = skillWord(id);
            return skill == null || id.startsWith("charm_estate") || id.startsWith("charm_forge") || id.startsWith("charm_shiny")
                    ? "Charm · Off-hand"
                    : skill + " Charm · Off-hand";
        }
        String slot = slotWord(id);
        if ((id.startsWith("compressed_") || id.startsWith("compacted_") || id.startsWith("refined_")) && slot == null) {
            return "Material";
        }
        if (id.startsWith("catcher_gaff")) {
            return "Catcher Gaff · Off-hand";
        }
        String family = familyWord(id);
        if (family != null && slot != null) {
            return SET_FAMILIES.contains(family) ? slot + " · " + family + " Set" : family + " " + slot;
        }
        if (slot != null) {
            return slot;
        }
        return family == null ? null : family + " Gear";
    }

    private static String dungeonLabel(String id) {
        if (id.startsWith("dungeon_relic_")) {
            return "Dungeon Relic";
        }
        if (id.startsWith("dungeon_vestige_")) {
            return "Dungeon Vestige";
        }
        DungeonPiece piece = DungeonPiece.fromItemId(id);
        DungeonCalling calling = DungeonCalling.fromItemId(id);
        if (piece != null) {
            return calling == null ? "Dungeon " + piece.display() : "Dungeon " + piece.display() + " · " + calling.display();
        }
        DungeonWeaponKind kind = DungeonWeaponKind.fromItemId(id);
        if (kind != null) {
            String slot = switch (kind) {
                case SWORD -> "Sword";
                case BOW -> "Shortbow";
                case WAND -> "Wand";
                case MACE -> "Maul";
                case STAFF -> "Staff";
            };
            return "Dungeon " + slot + " · " + kind.calling().display();
        }
        return "Dungeon Gear";
    }

    /** Armor slot words win over tool words (a catcher helmet is a helmet, not a gaff). */
    private static String slotWord(String id) {
        if (id.contains("helmet") || id.endsWith("_helm") || id.contains("crown") || id.contains("visor")
                || id.contains("veil")) {
            return "Helmet";
        }
        if (id.contains("chestplate") || id.contains("_vest") || id.contains("bodice")
                || id.contains("heartplate") || id.contains("cuirass")) {
            return "Chestplate";
        }
        if (id.contains("legging") || id.contains("skirt") || id.contains("greaves") || id.contains("pants")) {
            return "Leggings";
        }
        if (id.contains("boot") || id.contains("slipper") || id.contains("treads") || id.contains("sabaton")) {
            return "Boots";
        }
        if (id.contains("pickaxe")) {
            return "Pickaxe";
        }
        if (id.contains("longbow")) {
            return "Longbow";
        }
        if (id.contains("shortbow")) {
            return "Shortbow";
        }
        if (id.contains("crossbow") || id.endsWith("_bow")) {
            return "Bow";
        }
        if (id.contains("sword")) {
            return "Sword";
        }
        if (id.contains("dagger") || id.contains("knife")) {
            return "Dagger";
        }
        if (id.contains("scythe")) {
            return "Scythe";
        }
        if (id.contains("staff")) {
            return "Staff";
        }
        if (id.contains("maul") || id.contains("mace") || id.contains("hammer")) {
            return "Maul";
        }
        if (id.contains("gaff")) {
            return "Gaff";
        }
        if (id.contains("hoe")) {
            return "Hoe";
        }
        if (id.contains("axe")) {
            return "Axe";
        }
        if (id.endsWith("_rod") || id.matches(".*_rod_\\d")) {
            return "Rod";
        }
        if (id.contains("ring")) {
            return "Ring";
        }
        return null;
    }

    private static String familyWord(String id) {
        if (id.startsWith("combat_")) {
            return "Combat";
        }
        if (id.startsWith("mining_")) {
            return "Mining";
        }
        if (id.startsWith("farming_")) {
            return "Farming";
        }
        if (id.startsWith("foraging_")) {
            return "Foraging";
        }
        if (id.startsWith("fishing_")) {
            return "Fishing";
        }
        if (id.startsWith("diving_")) {
            return "Diving";
        }
        if (id.startsWith("catcher_")) {
            return "Catcher";
        }
        if (id.startsWith("simple_")) {
            return "Starter";
        }
        if (id.startsWith("aetherion_")) {
            return "Aetherion";
        }
        if (id.startsWith("worldhide_")) {
            return "Worldhide";
        }
        if (id.startsWith("helios_")) {
            return "Dawnbearer";
        }
        if (id.startsWith("hollow_sun_")) {
            return "Hollow Sun";
        }
        if (id.startsWith("seraphine_")) {
            return "Saint's";
        }
        if (id.startsWith("healer_")) {
            return "Healer";
        }
        if (id.startsWith("ironhide_")) {
            return "Ironhide";
        }
        if (id.startsWith("rotten_")) {
            return "Rotten";
        }
        if (id.startsWith("bone_")) {
            return "Bone";
        }
        if (id.startsWith("webweave_")) {
            return "Webweave";
        }
        return null;
    }

    private static String skillWord(String id) {
        if (id.contains("combat")) {
            return "Combat";
        }
        if (id.contains("mining")) {
            return "Mining";
        }
        if (id.contains("farming")) {
            return "Farming";
        }
        if (id.contains("foraging")) {
            return "Foraging";
        }
        if (id.contains("fishing")) {
            return "Fishing";
        }
        if (id.contains("utility")) {
            return "Utility";
        }
        return null;
    }
}
