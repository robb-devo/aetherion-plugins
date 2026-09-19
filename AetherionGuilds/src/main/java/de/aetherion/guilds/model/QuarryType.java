package de.aetherion.guilds.model;

import org.bukkit.Material;

import java.util.Locale;

public enum QuarryType {

    COBBLESTONE("cobble_quarry", "cobblestone", Material.COBBLESTONE, Material.COBBLESTONE, "Cobble Quarry", 1.00),
    COAL("coal_quarry", "coal", Material.COAL, Material.COAL_BLOCK, "Coal Quarry", 0.80),
    RAW_IRON("raw_iron_quarry", "raw_iron", Material.RAW_IRON, Material.IRON_BLOCK, "Iron Quarry", 0.50),
    RAW_GOLD("raw_gold_quarry", "raw_gold", Material.RAW_GOLD, Material.GOLD_BLOCK, "Gold Quarry", 0.35),
    RAW_COPPER("raw_copper_quarry", "raw_copper", Material.RAW_COPPER, Material.COPPER_BLOCK, "Copper Quarry", 0.60),
    REDSTONE("redstone_quarry", "redstone", Material.REDSTONE, Material.REDSTONE_BLOCK, "Redstone Quarry", 0.60),
    LAPIS("lapis_quarry", "lapis", Material.LAPIS_LAZULI, Material.LAPIS_BLOCK, "Lapis Quarry", 0.40),
    DIAMOND("diamond_quarry", "diamond", Material.DIAMOND, Material.DIAMOND_BLOCK, "Diamond Quarry", 0.20),
    EMERALD("emerald_quarry", "emerald", Material.EMERALD, Material.EMERALD_BLOCK, "Emerald Quarry", 0.20),
    OAK_LOG("oak_log_quarry", "oak_log", Material.OAK_LOG, Material.OAK_LOG, "Oak Quarry", 0.85),
    BIRCH_LOG("birch_log_quarry", "birch_log", Material.BIRCH_LOG, Material.BIRCH_LOG, "Birch Quarry", 0.80),
    SPRUCE_LOG("spruce_log_quarry", "spruce_log", Material.SPRUCE_LOG, Material.SPRUCE_LOG, "Spruce Quarry", 0.80),
    JUNGLE_LOG("jungle_log_quarry", "jungle_log", Material.JUNGLE_LOG, Material.JUNGLE_LOG, "Jungle Quarry", 0.75),
    ACACIA_LOG("acacia_log_quarry", "acacia_log", Material.ACACIA_LOG, Material.ACACIA_LOG, "Acacia Quarry", 0.75),
    DARK_OAK_LOG("dark_oak_log_quarry", "dark_oak_log", Material.DARK_OAK_LOG, Material.DARK_OAK_LOG, "Dark Oak Quarry", 0.75),
    MANGROVE_LOG("mangrove_log_quarry", "mangrove_log", Material.MANGROVE_LOG, Material.MANGROVE_LOG, "Mangrove Quarry", 0.70),
    CHERRY_LOG("cherry_log_quarry", "cherry_log", Material.CHERRY_LOG, Material.CHERRY_LOG, "Cherry Quarry", 0.70),
    BAMBOO_BLOCK("bamboo_block_quarry", "bamboo_block", Material.BAMBOO_BLOCK, Material.BAMBOO_BLOCK, "Bamboo Quarry", 0.80),
    CRIMSON_STEM("crimson_stem_quarry", "crimson_stem", Material.CRIMSON_STEM, Material.CRIMSON_STEM, "Crimson Quarry", 0.60),
    WARPED_STEM("warped_stem_quarry", "warped_stem", Material.WARPED_STEM, Material.WARPED_STEM, "Warped Quarry", 0.60),
    LEATHER("leather_quarry", "leather", Material.LEATHER, Material.BROWN_WOOL, "Leather Quarry", 0.55),
    BONE("bone_quarry", "bone", Material.BONE, Material.BONE_BLOCK, "Bone Quarry", 0.50),
    STRING("string_quarry", "string", Material.STRING, Material.WHITE_WOOL, "String Quarry", 0.55),
    WHEAT("wheat_quarry", "wheat", Material.WHEAT, Material.HAY_BLOCK, "Wheat Quarry", 0.70),
    CARROT("carrot_quarry", "carrot", Material.CARROT, Material.ORANGE_WOOL, "Carrot Quarry", 0.70),
    POTATO("potato_quarry", "potato", Material.POTATO, Material.BROWN_TERRACOTTA, "Potato Quarry", 0.70),
    GUNPOWDER("gunpowder_quarry", "gunpowder", Material.GUNPOWDER, Material.TNT, "Gunpowder Quarry", 0.40),
    FEATHER("feather_quarry", "feather", Material.FEATHER, Material.WHITE_WOOL, "Feather Quarry", 0.60),
    ROTTEN_FLESH("rotten_flesh_quarry", "rotten_flesh", Material.ROTTEN_FLESH, Material.NETHERRACK, "Rotten Quarry", 0.65),
    COD("cod_quarry", "cod", Material.COD, Material.DRIED_KELP_BLOCK, "Cod Quarry", 0.75);

    public static final int MAX_LEVEL = 7;
    public static final String CORE_ID = "quarry_core";
    private static final int[] CURVE = {0, 1, 2, 4, 8, 16, 48, 128};

    private final String id;
    private final String resourceKey;
    private final Material product;
    private final Material icon;
    private final String display;
    private final double rate;

    QuarryType(String id, String resourceKey, Material product, Material icon, String display, double rate) {
        this.id = id;
        this.resourceKey = resourceKey;
        this.product = product;
        this.icon = icon;
        this.display = display;
        this.rate = rate;
    }

    public String id() {
        return id;
    }

    public String resourceKey() {
        return resourceKey;
    }

    public Material product() {
        return product;
    }

    public Material icon() {
        return icon;
    }

    public String display() {
        return display;
    }

    public String blurb() {
        return switch (this) {
            case COBBLESTONE -> "§8It mines the floor. The floor filed a complaint.";
            case COAL -> "§8A night shift that never learned to clock out.";
            case RAW_IRON -> "§8Eats stone. Pays in ingots and attitude.";
            case RAW_GOLD -> "§8Still not paying rent. Now it's unionized.";
            case RAW_COPPER -> "§8Oxidizes on a schedule. Very professional.";
            case REDSTONE -> "§8Click. Click. Profit. Click.";
            case LAPIS -> "§8Enchanting tables hate this one trick.";
            case DIAMOND -> "§8Pressure, but make it a business model.";
            case EMERALD -> "§8Villagers can smell this from spawn.";
            case OAK_LOG -> "§8A forest with a punch clock.";
            case BIRCH_LOG -> "§8Pale overtime. The bees filed a complaint.";
            case SPRUCE_LOG -> "§8Cold climate mill. Warm profits.";
            case JUNGLE_LOG -> "§8Canopy mill. Monkeys unionized.";
            case ACACIA_LOG -> "§8Savanna mill. Shade on a schedule.";
            case DARK_OAK_LOG -> "§8Thicket mill. Night shift forever.";
            case MANGROVE_LOG -> "§8Swamp mill. Frogs approved.";
            case CHERRY_LOG -> "§8Blossom mill. Petals optional.";
            case BAMBOO_BLOCK -> "§8Bamboo mill. Pandas filed a memo.";
            case CRIMSON_STEM -> "§8Crimson mill. Nether HR shrugged.";
            case WARPED_STEM -> "§8Warped mill. Dreams, automated.";
            case LEATHER -> "§8No cows were interviewed for this quarry.";
            case BONE -> "§8The skeleton's retirement plan, automated.";
            case STRING -> "§8Spider HR approved this installation.";
            case WHEAT -> "§8A field that learned to sit still. Then work.";
            case CARROT -> "§8Orange overtime. The rabbits filed a complaint.";
            case POTATO -> "§8A tuber with a punch clock.";
            case GUNPOWDER -> "§8Creeper diplomacy, now with a mill.";
            case FEATHER -> "§8Flight, cancelled. Production, not.";
            case ROTTEN_FLESH -> "§8Zombies send leftovers. Warmly.";
            case COD -> "§8A school that unionized and got a mill.";
        };
    }

    public static String upgradeLine(int newLevel) {
        return switch (Math.max(1, Math.min(MAX_LEVEL, newLevel))) {
            case 2 -> "§7It learned to chew. The island pretends not to notice.";
            case 3 -> "§7Overtime approved itself. Pay did not.";
            case 4 -> "§7Compressed. Like your patience, and the rocks.";
            case 5 -> "§7A core went in. Dignity did not come out.";
            case 6 -> "§7This is no longer mining. This is a hostage situation.";
            case 7 -> "§7Max. The quarry has a union, a lawyer, and a mill.";
            default -> "§7It ticks. You collect. Civilization continues.";
        };
    }

    public String productName() {
        String[] parts = resourceKey.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    public String compressedId() {
        return "compressed_" + resourceKey;
    }

    public String compactedId() {
        return "compacted_" + resourceKey;
    }

    public int perTick(int level) {
        int index = Math.max(1, Math.min(MAX_LEVEL, level));
        return Math.max(1, (int) Math.round(CURVE[index] * rate));
    }

    public int cap(int level) {
        int index = Math.max(1, Math.min(MAX_LEVEL, level));
        int[] storage = {0, 1_728, 6_912, 27_648, 110_592, 442_368, 1_769_472, 7_077_888};
        return Math.max(64, (int) Math.round(storage[index] * Math.max(0.2d, rate)));
    }

    public UpgradeCost upgradeCost(int fromLevel) {
        return switch (fromLevel) {
            case 1 -> new UpgradeCost(8, 0, 0);
            case 2 -> new UpgradeCost(32, 0, 0);
            case 3 -> new UpgradeCost(64, 0, 0);
            case 4 -> new UpgradeCost(0, 16, 0);
            case 5 -> new UpgradeCost(0, 48, 1);
            case 6 -> new UpgradeCost(0, 64, 3);
            default -> UpgradeCost.NONE;
        };
    }

    public static QuarryType fromId(String id) {
        if (id == null || id.isBlank()) {
            return COBBLESTONE;
        }
        String raw = id.toLowerCase(Locale.ROOT);
        for (QuarryType type : values()) {
            if (type.id.equals(raw)
                    || type.resourceKey.equals(raw)
                    || ("quarry_" + type.resourceKey).equals(raw)
                    || type.name().toLowerCase(Locale.ROOT).equals(raw)) {
                return type;
            }
        }
        return COBBLESTONE;
    }

    public record UpgradeCost(int compressed, int compacted, int cores) {
        public static final UpgradeCost NONE = new UpgradeCost(0, 0, 0);

        public boolean isEmpty() {
            return compressed <= 0 && compacted <= 0 && cores <= 0;
        }
    }
}
