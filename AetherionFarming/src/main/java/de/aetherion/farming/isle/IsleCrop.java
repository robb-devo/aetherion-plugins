package de.aetherion.farming.isle;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Crops the Eldervale loops know about: mastery, orders, prize finds, bakehouse costs.
 * {@code block} is what grows in the field, {@code yield} is the item a harvest pays.
 */
public enum IsleCrop {

    WHEAT("Wheat", "§e", Material.WHEAT, Material.WHEAT, "Prize Wheat Sheaf", 1.5, 6.0),
    CARROT("Carrot", "§6", Material.CARROTS, Material.CARROT, "Prize Carrot", 0.4, 2.6),
    POTATO("Potato", "§6", Material.POTATOES, Material.POTATO, "Prize Potato", 0.6, 3.4),
    BEETROOT("Beetroot", "§c", Material.BEETROOTS, Material.BEETROOT, "Prize Beetroot", 0.5, 2.9),
    SUGAR_CANE("Sugar Cane", "§a", Material.SUGAR_CANE, Material.SUGAR_CANE, "Prize Cane Bundle", 1.0, 5.2),
    NETHER_WART("Nether Wart", "§4", Material.NETHER_WART, Material.NETHER_WART, "Prize Nether Wart", 0.2, 1.3),
    MELON("Melon", "§a", Material.MELON, Material.MELON_SLICE, "Prize Melon", 6.0, 42.0),
    PUMPKIN("Pumpkin", "§6", Material.PUMPKIN, Material.PUMPKIN, "Prize Pumpkin", 8.0, 95.0),
    COCOA("Cocoa", "§6", Material.COCOA, Material.COCOA_BEANS, "Prize Cocoa Pod", 0.3, 1.5);

    private final String display;
    private final String color;
    private final Material block;
    private final Material yield;
    private final String prizeName;
    private final double minKg;
    private final double maxKg;

    IsleCrop(String display, String color, Material block, Material yield, String prizeName, double minKg, double maxKg) {
        this.display = display;
        this.color = color;
        this.block = block;
        this.yield = yield;
        this.prizeName = prizeName;
        this.minKg = minKg;
        this.maxKg = maxKg;
    }

    public String display() {
        return display;
    }

    /** Legacy colour code for chat / lore. */
    public String color() {
        return color;
    }

    public String colored() {
        return color + display;
    }

    public Material block() {
        return block;
    }

    public Material yield() {
        return yield;
    }

    public String prizeName() {
        return prizeName;
    }

    public double minKg() {
        return minKg;
    }

    public double maxKg() {
        return maxKg;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Crops that actually grow on Eldervale — orders and the bakehouse only ask for these. */
    public boolean onIsle() {
        return this == WHEAT || this == CARROT || this == POTATO || this == BEETROOT || this == SUGAR_CANE;
    }

    public static IsleCrop fromBlock(Material material) {
        if (material == null) {
            return null;
        }
        for (IsleCrop crop : values()) {
            if (crop.block == material) {
                return crop;
            }
        }
        return null;
    }

    public static IsleCrop fromYield(Material material) {
        if (material == null) {
            return null;
        }
        for (IsleCrop crop : values()) {
            if (crop.yield == material) {
                return crop;
            }
        }
        return material == Material.WHEAT_SEEDS ? WHEAT : material == Material.BEETROOT_SEEDS ? BEETROOT : null;
    }

    /** Block or item form. */
    public static IsleCrop from(Material material) {
        IsleCrop crop = fromBlock(material);
        return crop != null ? crop : fromYield(material);
    }

    public static IsleCrop byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
