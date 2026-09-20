package de.aetherion.quests.editor;

import de.aetherion.items.model.ItemProfile;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Clickable gather / reward / requirement targets: Aetherion custom items + common vanilla.
 */
public final class GatherItemCatalog {

    public enum Kind {
        CUSTOM,
        VANILLA
    }

    public record Entry(String id, String label, Material icon, Kind kind) {
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
            Material.ROTTEN_FLESH, Material.GUNPOWDER, Material.SPIDER_EYE, Material.ENDER_PEARL,
            Material.BLAZE_ROD, Material.NETHER_WART, Material.GLOWSTONE_DUST, Material.QUARTZ,
            Material.PRISMARINE_SHARD, Material.KELP, Material.INK_SAC, Material.OAK_SAPLING
    };

    private GatherItemCatalog() {
    }

    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        out.addAll(custom());
        out.addAll(vanilla());
        return out;
    }

    public static List<Entry> custom() {
        List<Entry> out = new ArrayList<>();
        for (ItemProfile profile : ItemProfile.values()) {
            String id = profile.getItemId();
            if (id == null || id.isBlank()) {
                continue;
            }
            out.add(new Entry(id, pretty(id), iconForCustom(id), Kind.CUSTOM));
        }
        return out;
    }

    public static List<Entry> vanilla() {
        List<Entry> out = new ArrayList<>();
        for (Material material : VANILLA) {
            if (material == null) {
                continue;
            }
            out.add(new Entry(material.name(), pretty(material.name()), material, Kind.VANILLA));
        }
        return out;
    }

    public static List<Entry> filtered(Kind kind) {
        if (kind == Kind.CUSTOM) {
            return custom();
        }
        if (kind == Kind.VANILLA) {
            return vanilla();
        }
        return all();
    }

    public static boolean containsId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        for (Entry entry : custom()) {
            if (entry.id().equalsIgnoreCase(id)) {
                return true;
            }
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

    private static Material iconForCustom(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (key.contains("pickaxe")) {
            return Material.IRON_PICKAXE;
        }
        if (key.contains("axe") && !key.contains("pick")) {
            return Material.IRON_AXE;
        }
        if (key.contains("hoe")) {
            return Material.IRON_HOE;
        }
        if (key.contains("rod") || key.contains("fish")) {
            return Material.FISHING_ROD;
        }
        if (key.contains("sword")) {
            return Material.IRON_SWORD;
        }
        if (key.contains("bow")) {
            return Material.BOW;
        }
        if (key.contains("helmet")) {
            return Material.IRON_HELMET;
        }
        if (key.contains("chest") || key.contains("tunic")) {
            return Material.IRON_CHESTPLATE;
        }
        if (key.contains("boot")) {
            return Material.IRON_BOOTS;
        }
        if (key.contains("leg")) {
            return Material.IRON_LEGGINGS;
        }
        if (key.contains("log") || key.contains("wood") || key.contains("heartwood")) {
            return Material.OAK_LOG;
        }
        return Material.PAPER;
    }
}
