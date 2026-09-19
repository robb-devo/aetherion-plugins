package de.aetherion.guilds.model;

import org.bukkit.Material;

public enum IslandBiome {

    PLAINS(
            "Plains",
            Material.GRASS_BLOCK,
            "§aGentle grass, oak accents.",
            "§7A calm starter meadow."
    ),
    FOREST(
            "Forest",
            Material.PODZOL,
            "§2Mossy floor, spruce posts.",
            "§7Shaded woodland vibes."
    ),
    DESERT(
            "Desert",
            Material.SAND,
            "§eSandstone rim, dry scrub.",
            "§7Warm dunes without the thirst."
    );

    private final String display;
    private final Material icon;
    private final String look;
    private final String blurb;

    IslandBiome(String display, Material icon, String look, String blurb) {
        this.display = display;
        this.icon = icon;
        this.look = look;
        this.blurb = blurb;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    public String look() {
        return look;
    }

    public String blurb() {
        return blurb;
    }

    public static IslandBiome fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return PLAINS;
        }
        try {
            return IslandBiome.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return PLAINS;
        }
    }
}
