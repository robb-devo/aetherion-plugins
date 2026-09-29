package de.aetherion.mining.isle;

import org.bukkit.Color;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Ore families the Mining Eldervale loops know about: mastery, collections, Crystal Finds,
 * contracts and the Hearth. {@code blocks} is what sits in the rock (ore + dense mineral block),
 * {@code resource} is what a break pays. {@code scale} stretches mastery / collection thresholds
 * (common rock needs more swings than a diamond).
 */
public enum IsleOre {

    STONE("Stone", "§7", Material.COBBLESTONE, "stone", "cobblestone", 1L, 4.0d,
            null, 0.0d, 0.0d, Material.COBBLESTONE, Color.fromRGB(150, 150, 150),
            EnumSet.of(Material.STONE, Material.DEEPSLATE)),
    COAL("Coal", "§8", Material.COAL, "coal", "coal", 2L, 1.5d,
            "Jet Heart", 2.0d, 9.0d, Material.COAL_BLOCK, Color.fromRGB(70, 70, 80),
            EnumSet.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.COAL_BLOCK)),
    COPPER("Copper", "§6", Material.COPPER_INGOT, "copper", "raw_copper", 1L, 1.5d,
            "Verdigris Geode", 2.5d, 11.0d, Material.OXIDIZED_COPPER, Color.fromRGB(80, 200, 160),
            EnumSet.of(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.RAW_COPPER_BLOCK, Material.COPPER_BLOCK)),
    IRON("Iron", "§f", Material.IRON_INGOT, "iron", "raw_iron", 3L, 1.0d,
            "Ironheart Nodule", 3.0d, 13.0d, Material.RAW_IRON_BLOCK, Color.fromRGB(230, 200, 180),
            EnumSet.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.RAW_IRON_BLOCK, Material.IRON_BLOCK)),
    REDSTONE("Redstone", "§c", Material.REDSTONE, "redstone", "redstone", 1L, 1.0d,
            "Emberstone", 2.5d, 12.0d, Material.REDSTONE_BLOCK, Color.fromRGB(255, 40, 30),
            EnumSet.of(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE_BLOCK)),
    LAPIS("Lapis", "§9", Material.LAPIS_LAZULI, "lapis", "lapis", 2L, 1.0d,
            "Skyglass Lapis", 3.0d, 14.0d, Material.LAPIS_BLOCK, Color.fromRGB(40, 90, 255),
            EnumSet.of(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_BLOCK)),
    GOLD("Gold", "§e", Material.GOLD_INGOT, "gold", "raw_gold", 4L, 0.6d,
            "Sun Nugget", 3.5d, 16.0d, Material.RAW_GOLD_BLOCK, Color.fromRGB(255, 210, 40),
            EnumSet.of(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
                    Material.RAW_GOLD_BLOCK, Material.GOLD_BLOCK)),
    QUARTZ("Quartz", "§f", Material.QUARTZ, "quartz", null, 2L, 0.6d,
            "Moonquartz Spire", 3.0d, 15.0d, Material.QUARTZ_PILLAR, Color.fromRGB(245, 240, 255),
            EnumSet.of(Material.NETHER_QUARTZ_ORE, Material.QUARTZ_BLOCK)),
    AMETHYST("Amethyst", "§d", Material.AMETHYST_SHARD, "amethyst", null, 2L, 0.5d,
            "Violet Crown", 4.0d, 18.0d, Material.AMETHYST_CLUSTER, Color.fromRGB(190, 110, 255),
            EnumSet.of(Material.AMETHYST_CLUSTER)),
    DIAMOND("Diamond", "§b", Material.DIAMOND, "diamond", "diamond", 8L, 0.25d,
            "Starcore Diamond", 4.0d, 22.0d, Material.DIAMOND_BLOCK, Color.fromRGB(80, 230, 255),
            EnumSet.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND_BLOCK)),
    EMERALD("Emerald", "§a", Material.EMERALD, "emerald", "emerald", 6L, 0.15d,
            "Verdant Eye", 4.5d, 24.0d, Material.EMERALD_BLOCK, Color.fromRGB(40, 255, 110),
            EnumSet.of(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD_BLOCK)),
    DEBRIS("Ancient Debris", "§5", Material.NETHERITE_INGOT, "ancient_debris", null, 25L, 0.08d,
            "Hollow Ember", 6.0d, 30.0d, Material.ANCIENT_DEBRIS, Color.fromRGB(170, 60, 40),
            EnumSet.of(Material.ANCIENT_DEBRIS, Material.NETHERITE_BLOCK));

    private final String display;
    private final String color;
    private final Material resource;
    private final String codexId;
    private final String compressedKey;
    private final long unitValue;
    private final double scale;
    private final String crystalName;
    private final double minCarat;
    private final double maxCarat;
    private final Material showcase;
    private final Color glow;
    private final Set<Material> blocks;

    IsleOre(String display, String color, Material resource, String codexId, String compressedKey, long unitValue,
            double scale, String crystalName, double minCarat, double maxCarat, Material showcase, Color glow,
            Set<Material> blocks) {
        this.display = display;
        this.color = color;
        this.resource = resource;
        this.codexId = codexId;
        this.compressedKey = compressedKey;
        this.unitValue = unitValue;
        this.scale = scale;
        this.crystalName = crystalName;
        this.minCarat = minCarat;
        this.maxCarat = maxCarat;
        this.showcase = showcase;
        this.glow = glow;
        this.blocks = blocks;
    }

    public String display() {
        return display;
    }

    public String color() {
        return color;
    }

    public String colored() {
        return color + display;
    }

    public Material resource() {
        return resource;
    }

    /** Codex block family id — the Collection counts live there. */
    public String codexId() {
        return codexId;
    }

    /** Items {@code CompressedResource} key, or {@code null} when the ore has no compressed form. */
    public String compressedKey() {
        return compressedKey;
    }

    /** Trader value of one resource unit (economy.yml) — contracts pay a multiple of it. */
    public long unitValue() {
        return unitValue;
    }

    public double scale() {
        return scale;
    }

    /** Crystal Find name, or {@code null} for plain stone (stone never pops a crystal). */
    public String crystalName() {
        return crystalName;
    }

    public boolean hasCrystal() {
        return crystalName != null;
    }

    public double minCarat() {
        return minCarat;
    }

    public double maxCarat() {
        return maxCarat;
    }

    /** Block shown inside a Crystal Find / specimen plinth. */
    public Material showcase() {
        return showcase;
    }

    public Color glow() {
        return glow;
    }

    public Set<Material> blocks() {
        return blocks;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Rarer families roll heavier on the contract board and the Tremor rubble. */
    public boolean rare() {
        return this == DIAMOND || this == EMERALD || this == DEBRIS || this == AMETHYST;
    }

    public static IsleOre fromBlock(Material material) {
        if (material == null) {
            return null;
        }
        for (IsleOre ore : values()) {
            if (ore.blocks.contains(material)) {
                return ore;
            }
        }
        return null;
    }

    public static IsleOre byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Families with a Crystal Find (everything but stone). */
    public static IsleOre[] crystals() {
        return java.util.Arrays.stream(values()).filter(IsleOre::hasCrystal).toArray(IsleOre[]::new);
    }
}
