package de.aetherion.quests.editor;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Clickable gather / reward targets that the live quest engine already understands
 * (vanilla materials + Coins / XP). Custom item ids are not used here because
 * COLLECT / giveRewards match materials and named currencies.
 */
public final class GatherItemCatalog {

    public record Entry(String id, String label, Material icon) {
    }

    private static final Material[] VANILLA = {
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.COAL, Material.COAL_ORE, Material.RAW_IRON, Material.IRON_ORE,
            Material.RAW_COPPER, Material.COPPER_ORE, Material.RAW_GOLD, Material.GOLD_ORE,
            Material.DIAMOND, Material.EMERALD, Material.REDSTONE, Material.LAPIS_LAZULI,
            Material.COBBLESTONE, Material.STONE, Material.GRAVEL, Material.SAND, Material.CLAY_BALL,
            Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT, Material.SWEET_BERRIES,
            Material.PUMPKIN, Material.MELON_SLICE, Material.SUGAR_CANE, Material.BAMBOO,
            Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH,
            Material.APPLE, Material.STICK, Material.STRING, Material.LEATHER, Material.BONE,
            Material.ROTTEN_FLESH, Material.GUNPOWDER, Material.SPIDER_EYE, Material.ENDER_PEARL
    };

    private GatherItemCatalog() {
    }

    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        for (Material material : VANILLA) {
            if (material == null) {
                continue;
            }
            out.add(new Entry(material.name(), pretty(material.name()), material));
        }
        return out;
    }

    public static boolean containsId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String needle = id.trim();
        for (Material material : VANILLA) {
            if (material != null && material.name().equalsIgnoreCase(needle)) {
                return true;
            }
        }
        return false;
    }

    public static String pretty(String id) {
        if (id == null || id.isBlank()) {
            return "Item";
        }
        String[] parts = id.toLowerCase(Locale.ROOT).replace(':', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.isEmpty() ? id : out.toString();
    }
}
