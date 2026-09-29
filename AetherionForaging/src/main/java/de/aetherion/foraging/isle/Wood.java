package de.aetherion.foraging.isle;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Wood families the Grove loops know about: mastery, collections and orders. {@code log} is the
 * typed drop the isle pays, {@code codex} the Codex block id, {@code heart} the Items heartwood key.
 * {@code scale} stretches thresholds (giant jungle and cherry trees come down less often than oak).
 */
public enum Wood {

    OAK(Material.OAK_LOG, "oak", "Oak", "§6", 1.0d),
    BIRCH(Material.BIRCH_LOG, "birch", "Birch", "§f", 1.0d),
    SPRUCE(Material.SPRUCE_LOG, "spruce", "Spruce", "§b", 1.0d),
    CHERRY(Material.CHERRY_LOG, "cherry", "Cherry", "§d", 0.8d),
    ACACIA(Material.ACACIA_LOG, "acacia", "Acacia", "§6", 0.85d),
    DARK_OAK(Material.DARK_OAK_LOG, "dark_oak", "Dark Oak", "§8", 0.85d),
    MANGROVE(Material.MANGROVE_LOG, "mangrove", "Mangrove", "§2", 0.8d),
    JUNGLE(Material.JUNGLE_LOG, "jungle", "Jungle", "§a", 0.7d),
    BAMBOO(Material.BAMBOO_BLOCK, "bamboo", "Bamboo", "§a", 0.7d);

    private final Material log;
    private final String key;
    private final String display;
    private final String color;
    private final double scale;

    Wood(Material log, String key, String display, String color, double scale) {
        this.log = log;
        this.key = key;
        this.display = display;
        this.color = color;
        this.scale = scale;
    }

    public Material log() {
        return log;
    }

    /** Codex block id and heartwood key are the same word. */
    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public String colored() {
        return color + display;
    }

    public double scale() {
        return scale;
    }

    public Grove grove() {
        return Grove.ofWood(log);
    }

    public static Wood of(Material drop) {
        if (drop == null) {
            return null;
        }
        for (Wood wood : values()) {
            if (wood.log == drop) {
                return wood;
            }
        }
        return null;
    }

    public static Wood byKey(String raw) {
        if (raw == null) {
            return null;
        }
        String k = raw.trim().toLowerCase(Locale.ROOT);
        for (Wood wood : values()) {
            if (wood.key.equals(k) || wood.name().equalsIgnoreCase(k)) {
                return wood;
            }
        }
        return null;
    }
}
