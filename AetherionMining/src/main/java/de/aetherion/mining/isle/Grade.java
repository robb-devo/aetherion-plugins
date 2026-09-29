package de.aetherion.mining.isle;

import java.util.Locale;

/**
 * Crystal Find grades. Heartstones only grow in The Veins — the endgame annex has the one thing
 * the island can't give you.
 */
public enum Grade {

    ROUGH("Rough", "§7", 0.60d, 3, 1.0d),
    FLAWLESS("Flawless", "§b", 0.30d, 4, 2.0d),
    PERFECT("Perfect", "§d", 0.10d, 5, 4.0d),
    HEARTSTONE("Heartstone", "§6", 0.0d, 7, 12.0d);

    private final String display;
    private final String color;
    private final double weight;
    private final int cracks;
    private final double valueMult;

    Grade(String display, String color, double weight, int cracks, double valueMult) {
        this.display = display;
        this.color = color;
        this.weight = weight;
        this.cracks = cracks;
        this.valueMult = valueMult;
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

    /** Island roll weight (Heartstone is Veins-only and rolled separately). */
    public double weight() {
        return weight;
    }

    /** Pickaxe hits to crack the geode open. */
    public int cracks() {
        return cracks;
    }

    public double valueMult() {
        return valueMult;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Grade byId(String id) {
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
