package de.aetherion.quests.editor;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Clickable gather / reward targets the live quest engine already understands
 * (vanilla materials). Curated by category so camping meats are one click,
 * not a dump of {@link Material}.
 */
public final class GatherItemCatalog {

    public enum Kind {
        ALL("All", Material.ITEM_FRAME),
        MEAT("Meats", Material.COOKED_BEEF),
        FISH("Fish", Material.COOKED_COD),
        CROP("Crops", Material.WHEAT),
        FOOD("Food", Material.BREAD),
        LOG("Logs", Material.OAK_LOG),
        ORE("Ores", Material.COAL),
        DROP("Drops", Material.LEATHER);

        private final String label;
        private final Material icon;

        Kind(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }

        public String label() {
            return label;
        }

        public Material icon() {
            return icon;
        }

        public String langKey() {
            return "editor_cat_" + name().toLowerCase(Locale.ROOT);
        }

        public static Kind parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return ALL;
            }
            try {
                return Kind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ALL;
            }
        }
    }

    public record Entry(String id, String label, Material icon, Kind kind, String... aliases) {
        public boolean matches(String query) {
            if (query == null || query.isBlank()) {
                return true;
            }
            String needle = query.toLowerCase(Locale.ROOT).trim();
            if (id.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
            if (label.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
            if (kind.label().toLowerCase(Locale.ROOT).contains(needle)
                    || kind.name().toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
            if (aliases != null) {
                for (String alias : aliases) {
                    if (alias != null && alias.toLowerCase(Locale.ROOT).contains(needle)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static final List<Entry> ENTRIES = List.copyOf(build());

    private GatherItemCatalog() {
    }

    public static List<Entry> all() {
        return ENTRIES;
    }

    public static List<Entry> of(Kind kind) {
        if (kind == null || kind == Kind.ALL) {
            return ENTRIES;
        }
        List<Entry> out = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.kind() == kind) {
                out.add(entry);
            }
        }
        return out;
    }

    public static List<Entry> find(Kind kind, String query) {
        List<Entry> base = of(kind);
        if (query == null || query.isBlank()) {
            return base;
        }
        String needle = query.trim();
        Kind implied = kindFromSearch(needle);
        if (implied != null && (kind == null || kind == Kind.ALL)) {
            base = of(implied);
            // "meat" / "meats" selects the whole shelf; more specific words still filter.
            if (isKindWord(needle, implied)) {
                return base;
            }
        }
        List<Entry> out = new ArrayList<>();
        for (Entry entry : base) {
            if (entry.matches(needle)) {
                out.add(entry);
            }
        }
        return out;
    }

    public static boolean containsId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String needle = id.trim();
        for (Entry entry : ENTRIES) {
            if (entry.id().equalsIgnoreCase(needle)) {
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

    private static Kind kindFromSearch(String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        if (isKindWord(needle, Kind.MEAT)
                || needle.equals("steak")
                || needle.equals("fleisch")
                || needle.equals("fleischsorten")) {
            return Kind.MEAT;
        }
        if (isKindWord(needle, Kind.FISH) || needle.equals("seafood") || needle.equals("fisch") || needle.equals("fische")) {
            return Kind.FISH;
        }
        if (isKindWord(needle, Kind.CROP)
                || needle.equals("farm")
                || needle.equals("harvest")
                || needle.equals("ernte")
                || needle.equals("pflanzen")) {
            return Kind.CROP;
        }
        if (isKindWord(needle, Kind.FOOD) || needle.equals("essen") || needle.equals("nahrung")) {
            return Kind.FOOD;
        }
        if (isKindWord(needle, Kind.LOG)
                || needle.equals("wood")
                || needle.equals("timber")
                || needle.equals("holz")
                || needle.equals("stamm")
                || needle.equals("stämme")
                || needle.equals("staemme")) {
            return Kind.LOG;
        }
        if (isKindWord(needle, Kind.ORE)
                || needle.equals("mineral")
                || needle.equals("mining")
                || needle.equals("erz")
                || needle.equals("erze")) {
            return Kind.ORE;
        }
        if (isKindWord(needle, Kind.DROP) || needle.equals("mob") || needle.equals("beute")) {
            return Kind.DROP;
        }
        return null;
    }

    private static boolean isKindWord(String needle, Kind kind) {
        String name = kind.name().toLowerCase(Locale.ROOT);
        String label = kind.label().toLowerCase(Locale.ROOT);
        return needle.equals(name)
                || needle.equals(label)
                || needle.equals(name + "s")
                || needle.equals(label + "s")
                || (kind == Kind.MEAT && (needle.equals("meat") || needle.equals("meats")));
    }

    private static List<Entry> build() {
        List<Entry> out = new ArrayList<>();
        // Meats first so camping / cook-out jobs are not buried under logs.
        add(out, Kind.MEAT, Material.BEEF, "beef", "steak", "raw beef", "meat", "fleisch", "rind", "rindfleisch", "cook", "camping");
        add(out, Kind.MEAT, Material.COOKED_BEEF, "steak", "cooked beef", "meat", "fleisch", "rind", "cook", "camping");
        add(out, Kind.MEAT, Material.PORKCHOP, "pork", "bacon", "raw pork", "meat", "fleisch", "schwein", "schweinefleisch", "cook", "camping");
        add(out, Kind.MEAT, Material.COOKED_PORKCHOP, "pork", "bacon", "cooked pork", "meat", "fleisch", "schwein", "cook", "camping");
        add(out, Kind.MEAT, Material.CHICKEN, "raw chicken", "meat", "fleisch", "huhn", "hähnchen", "haehnchen", "cook", "camping");
        add(out, Kind.MEAT, Material.COOKED_CHICKEN, "meat", "fleisch", "huhn", "hähnchen", "haehnchen", "cook", "camping");
        add(out, Kind.MEAT, Material.MUTTON, "lamb", "sheep", "raw mutton", "meat", "fleisch", "lamm", "hammel", "cook", "camping");
        add(out, Kind.MEAT, Material.COOKED_MUTTON, "lamb", "sheep", "meat", "fleisch", "lamm", "hammel", "cook", "camping");
        add(out, Kind.MEAT, Material.RABBIT, "raw rabbit", "meat", "fleisch", "hase", "kaninchen", "cook", "camping");
        add(out, Kind.MEAT, Material.COOKED_RABBIT, "meat", "fleisch", "hase", "kaninchen", "cook", "camping");

        add(out, Kind.FISH, Material.COD, "raw cod", "fish");
        add(out, Kind.FISH, Material.COOKED_COD, "fish");
        add(out, Kind.FISH, Material.SALMON, "raw salmon", "fish");
        add(out, Kind.FISH, Material.COOKED_SALMON, "fish");
        add(out, Kind.FISH, Material.TROPICAL_FISH, "clownfish", "fish");
        add(out, Kind.FISH, Material.PUFFERFISH, "fish");

        add(out, Kind.CROP, Material.WHEAT, "grain", "crop");
        add(out, Kind.CROP, Material.WHEAT_SEEDS, "seed", "crop");
        add(out, Kind.CROP, Material.CARROT, "crop");
        add(out, Kind.CROP, Material.POTATO, "crop");
        add(out, Kind.CROP, Material.BEETROOT, "crop");
        add(out, Kind.CROP, Material.BEETROOT_SEEDS, "seed", "crop");
        add(out, Kind.CROP, Material.PUMPKIN, "crop");
        add(out, Kind.CROP, Material.MELON, "crop");
        add(out, Kind.CROP, Material.MELON_SLICE, "crop");
        add(out, Kind.CROP, Material.SUGAR_CANE, "sugar", "crop");
        add(out, Kind.CROP, Material.BAMBOO, "crop");
        add(out, Kind.CROP, Material.SWEET_BERRIES, "berry", "crop");
        add(out, Kind.CROP, Material.GLOW_BERRIES, "berry", "crop");
        add(out, Kind.CROP, Material.COCOA_BEANS, "cocoa", "crop");
        add(out, Kind.CROP, Material.CACTUS, "crop");
        add(out, Kind.CROP, Material.NETHER_WART, "crop");
        add(out, Kind.CROP, Material.KELP, "seaweed", "crop");
        add(out, Kind.CROP, Material.BROWN_MUSHROOM, "mushroom", "crop");
        add(out, Kind.CROP, Material.RED_MUSHROOM, "mushroom", "crop");

        add(out, Kind.FOOD, Material.BREAD, "food");
        add(out, Kind.FOOD, Material.BAKED_POTATO, "food");
        add(out, Kind.FOOD, Material.COOKIE, "food");
        add(out, Kind.FOOD, Material.PUMPKIN_PIE, "food");
        add(out, Kind.FOOD, Material.CAKE, "food");
        add(out, Kind.FOOD, Material.APPLE, "food");
        add(out, Kind.FOOD, Material.GOLDEN_APPLE, "food");
        add(out, Kind.FOOD, Material.GOLDEN_CARROT, "food");
        add(out, Kind.FOOD, Material.MUSHROOM_STEW, "soup", "food");
        add(out, Kind.FOOD, Material.BEETROOT_SOUP, "soup", "food");
        add(out, Kind.FOOD, Material.RABBIT_STEW, "soup", "food");
        add(out, Kind.FOOD, Material.HONEY_BOTTLE, "honey", "food");
        add(out, Kind.FOOD, Material.HONEYCOMB, "honey", "food");
        add(out, Kind.FOOD, Material.DRIED_KELP, "food");
        add(out, Kind.FOOD, Material.EGG, "food");
        add(out, Kind.FOOD, Material.SUGAR, "food");
        add(out, Kind.FOOD, Material.MILK_BUCKET, "milk", "food");
        add(out, Kind.FOOD, Material.BOWL, "food");

        add(out, Kind.LOG, Material.OAK_LOG, "wood", "timber");
        add(out, Kind.LOG, Material.SPRUCE_LOG, "wood", "timber");
        add(out, Kind.LOG, Material.BIRCH_LOG, "wood", "timber");
        add(out, Kind.LOG, Material.JUNGLE_LOG, "wood", "timber");
        add(out, Kind.LOG, Material.ACACIA_LOG, "wood", "timber");
        add(out, Kind.LOG, Material.DARK_OAK_LOG, "wood", "timber");
        add(out, Kind.LOG, Material.MANGROVE_LOG, "wood", "timber");
        add(out, Kind.LOG, Material.CHERRY_LOG, "wood", "timber");

        add(out, Kind.ORE, Material.COAL, "ore", "fuel", "cook");
        add(out, Kind.ORE, Material.CHARCOAL, "ore", "fuel", "cook", "camping");
        add(out, Kind.ORE, Material.COAL_ORE, "ore");
        add(out, Kind.ORE, Material.RAW_IRON, "ore");
        add(out, Kind.ORE, Material.IRON_ORE, "ore");
        add(out, Kind.ORE, Material.RAW_COPPER, "ore");
        add(out, Kind.ORE, Material.COPPER_ORE, "ore");
        add(out, Kind.ORE, Material.RAW_GOLD, "ore");
        add(out, Kind.ORE, Material.GOLD_ORE, "ore");
        add(out, Kind.ORE, Material.DIAMOND, "ore");
        add(out, Kind.ORE, Material.EMERALD, "ore");
        add(out, Kind.ORE, Material.REDSTONE, "ore");
        add(out, Kind.ORE, Material.LAPIS_LAZULI, "ore", "lapis");
        add(out, Kind.ORE, Material.COBBLESTONE, "stone");
        add(out, Kind.ORE, Material.STONE, "stone");
        add(out, Kind.ORE, Material.GRAVEL);
        add(out, Kind.ORE, Material.SAND);
        add(out, Kind.ORE, Material.CLAY_BALL, "clay");

        add(out, Kind.DROP, Material.LEATHER, "hide");
        add(out, Kind.DROP, Material.RABBIT_HIDE, "hide");
        add(out, Kind.DROP, Material.RABBIT_FOOT);
        add(out, Kind.DROP, Material.FEATHER);
        add(out, Kind.DROP, Material.BONE);
        add(out, Kind.DROP, Material.STRING);
        add(out, Kind.DROP, Material.SPIDER_EYE);
        add(out, Kind.DROP, Material.GUNPOWDER);
        add(out, Kind.DROP, Material.ROTTEN_FLESH, "zombie", "meat");
        add(out, Kind.DROP, Material.ENDER_PEARL);
        add(out, Kind.DROP, Material.STICK);

        Map<String, Entry> unique = new LinkedHashMap<>();
        for (Entry entry : out) {
            unique.putIfAbsent(entry.id(), entry);
        }
        return new ArrayList<>(unique.values());
    }

    private static void add(List<Entry> out, Kind kind, Material material, String... aliases) {
        if (material == null) {
            return;
        }
        out.add(new Entry(material.name(), pretty(material.name()), material, kind, aliases));
    }
}
