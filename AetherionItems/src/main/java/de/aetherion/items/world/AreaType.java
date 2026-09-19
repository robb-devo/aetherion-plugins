package de.aetherion.items.world;

import org.bukkit.Material;

import java.util.Locale;

public enum AreaType {

    STARTER_PORT("starter_port", "Anker Harbour", Material.OAK_BOAT),
    ORE_RIDGE("ore_ridge", "Ore Ridge", Material.IRON_ORE),
    FORAGE_ISLE("forage_isle", "Forage Isle", Material.OAK_LEAVES),
    SHABBY_MINE("shabby_mine", "Shabby Mine", Material.STONE_PICKAXE),
    FARM("farm", "Farm", Material.WHEAT),
    PATH("path", "Path", Material.DIRT_PATH),
    SQUIDS_LAKE("squids_lake", "Squid´s lake", Material.INK_SAC),
    DARK_CAVE("dark_cave", "Dark cave", Material.DEEPSLATE),
    CAPITAL("capital", "Capital", Material.GOLDEN_HELMET),
    BORDERLANDS("borderlands", "Borderlands", Material.COARSE_DIRT),
    COLOSSEUM("colosseum", "Colosseum", Material.SANDSTONE),
    ELDERVALE("eldervale", "Eldervale", Material.DEEPSLATE_DIAMOND_ORE);

    private final String id;
    private final String display;
    private final Material icon;

    AreaType(String id, String display, Material icon) {
        this.id = id;
        this.display = display;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    /** Default DevMenu area-tool radius (blocks). */
    public int defaultRadius() {
        if (this == ELDERVALE) {
            return 200;
        }
        if (this == FORAGE_ISLE) {
            return 400;
        }
        return 50;
    }

    /** Horizontal disk (XZ only) vs full sphere. */
    public boolean horizontalDisk() {
        return this == BORDERLANDS || this == COLOSSEUM || this == ELDERVALE || this == FORAGE_ISLE;
    }

    public static AreaType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT).replace(' ', '_').replace("´", "").replace("'", "");
        for (AreaType type : values()) {
            if (type.id.equals(key) || type.name().equalsIgnoreCase(raw) || type.display.equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }
}
