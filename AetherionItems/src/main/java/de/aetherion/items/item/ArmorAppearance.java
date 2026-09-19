package de.aetherion.items.item;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

/**
 * Vanilla armor dyes/trims for everything that is not bound to the resource pack.
 * Combat V and Mining V keep their pack icons and stay untrimmed.
 */
public final class ArmorAppearance {

    private ArmorAppearance() {
    }

    public static void apply(ItemMeta meta, String itemId) {
        if (meta == null) {
            return;
        }

        meta.addItemFlags(
                ItemFlag.HIDE_ARMOR_TRIM,
                ItemFlag.HIDE_DYE
        );

        if (!(meta instanceof ArmorMeta armorMeta)
                || itemId == null
                || itemId.isBlank()) {
            return;
        }

        ArmorTrim trim = trimFor(itemId.toLowerCase());
        if (trim == null) {
            return;
        }

        armorMeta.setTrim(trim);
    }

    private static ArmorTrim trimFor(String id) {
        if (id.startsWith("simple_")) {
            return null;
        }

        if (id.startsWith("aetherion_")) {
            return new ArmorTrim(TrimMaterial.AMETHYST, TrimPattern.FLOW);
        }

        if (id.startsWith("rotten_")) {
            return new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.RIB);
        }

        if (id.startsWith("bone_")) {
            return new ArmorTrim(TrimMaterial.QUARTZ, TrimPattern.DUNE);
        }

        if (id.startsWith("webweave_")) {
            return new ArmorTrim(TrimMaterial.IRON, TrimPattern.COAST);
        }

        if (id.startsWith("god2_")) {
            return new ArmorTrim(TrimMaterial.NETHERITE, TrimPattern.RIB);
        }

        if (id.startsWith("god_")) {
            return new ArmorTrim(TrimMaterial.GOLD, TrimPattern.SILENCE);
        }

        if (id.startsWith("catcher_")) {
            return new ArmorTrim(TrimMaterial.COPPER, TrimPattern.COAST);
        }

        if (id.equals("squids_boot")) {
            return new ArmorTrim(TrimMaterial.LAPIS, TrimPattern.TIDE);
        }

        if (id.startsWith("dungeon_relic_")) {
            return new ArmorTrim(TrimMaterial.NETHERITE, TrimPattern.SILENCE);
        }

        String dungeonId = id.replace("dungeon_t2_", "dungeon_").replace("dungeon_t3_", "dungeon_");
        if (dungeonId.startsWith("dungeon_vestige_")) {
            return new ArmorTrim(TrimMaterial.IRON, TrimPattern.SILENCE);
        }

        if (dungeonId.startsWith("dungeon_tank_")) {
            return new ArmorTrim(TrimMaterial.IRON, TrimPattern.WARD);
        }

        if (dungeonId.startsWith("dungeon_assassin_")) {
            return new ArmorTrim(TrimMaterial.AMETHYST, TrimPattern.SILENCE);
        }

        if (dungeonId.startsWith("dungeon_soldier_")) {
            return new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.SENTRY);
        }

        if (dungeonId.startsWith("dungeon_healer_")) {
            return new ArmorTrim(TrimMaterial.GOLD, TrimPattern.SILENCE);
        }

        if (dungeonId.startsWith("dungeon_shaman_")) {
            return new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.TIDE);
        }

        if (id.startsWith("ironhide_")) {
            return new ArmorTrim(TrimMaterial.IRON, TrimPattern.WARD);
        }

        if (id.equals("compressed_oak_chestplate")) {
            return new ArmorTrim(TrimMaterial.COPPER, TrimPattern.WILD);
        }

        if (id.equals("redstone_infused_boots")) {
            return new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.SPIRE);
        }

        if (id.equals("compacted_diamond_chestplate")) {
            return new ArmorTrim(TrimMaterial.DIAMOND, TrimPattern.SILENCE);
        }

        if (id.equals("emerald_crown")) {
            return new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.SENTRY);
        }

        if (id.startsWith("healer_")) {
            return new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.SILENCE);
        }

        if (id.startsWith("combat_")) {
            return switch (suffixTier(id)) {
                case 5 -> null;
                case 4 -> new ArmorTrim(TrimMaterial.NETHERITE, TrimPattern.SNOUT);
                case 3 -> new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.RIB);
                case 2 -> new ArmorTrim(TrimMaterial.COPPER, TrimPattern.DUNE);
                default -> new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.SENTRY);
            };
        }

        if (id.startsWith("mining_")) {
            return switch (suffixTier(id)) {
                case 5 -> null;
                case 4 -> new ArmorTrim(TrimMaterial.DIAMOND, TrimPattern.SILENCE);
                case 3 -> new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.WARD);
                case 2 -> new ArmorTrim(TrimMaterial.LAPIS, TrimPattern.WARD);
                default -> new ArmorTrim(TrimMaterial.LAPIS, TrimPattern.COAST);
            };
        }

        if (id.startsWith("farming_")) {
            return switch (suffixTier(id)) {
                case 5 -> new ArmorTrim(TrimMaterial.AMETHYST, TrimPattern.SILENCE);
                case 4 -> new ArmorTrim(TrimMaterial.DIAMOND, TrimPattern.WILD);
                case 3 -> new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.WILD);
                case 2 -> new ArmorTrim(TrimMaterial.GOLD, TrimPattern.COAST);
                default -> new ArmorTrim(TrimMaterial.GOLD, TrimPattern.WILD);
            };
        }

        if (id.startsWith("foraging_")) {
            return switch (suffixTier(id)) {
                case 5 -> new ArmorTrim(TrimMaterial.AMETHYST, TrimPattern.WARD);
                case 4 -> new ArmorTrim(TrimMaterial.DIAMOND, TrimPattern.SILENCE);
                case 3 -> new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.SILENCE);
                case 2 -> new ArmorTrim(TrimMaterial.COPPER, TrimPattern.WILD);
                default -> new ArmorTrim(TrimMaterial.COPPER, TrimPattern.COAST);
            };
        }

        if (id.startsWith("fishing_")) {
            return switch (suffixTier(id)) {
                case 5 -> new ArmorTrim(TrimMaterial.AMETHYST, TrimPattern.TIDE);
                case 4 -> new ArmorTrim(TrimMaterial.EMERALD, TrimPattern.TIDE);
                case 3 -> new ArmorTrim(TrimMaterial.DIAMOND, TrimPattern.TIDE);
                case 2 -> new ArmorTrim(TrimMaterial.LAPIS, TrimPattern.COAST);
                default -> new ArmorTrim(TrimMaterial.LAPIS, TrimPattern.TIDE);
            };
        }

        return null;
    }

    private static int suffixTier(String id) {
        int separator = id.lastIndexOf('_');
        if (separator < 0 || separator == id.length() - 1) {
            return 1;
        }

        String tail = id.substring(separator + 1);
        if (tail.length() == 1 && tail.charAt(0) >= '1' && tail.charAt(0) <= '9') {
            return tail.charAt(0) - '0';
        }

        return 1;
    }
}
