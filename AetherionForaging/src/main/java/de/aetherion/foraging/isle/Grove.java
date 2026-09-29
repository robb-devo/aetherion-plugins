package de.aetherion.foraging.isle;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * The seven forests of Foraging Eldervale. Every one is a real part of the build, measured from
 * {@code Foraging-Eldervale-Island.schem} (see {@code docs/FORAGING_ELDERVALE.md}): the district grid in
 * {@code forage-districts.bin} stores {@link #code} per 8×8×8 cell, so the same x/z can be the
 * Gloamwood Hollow on the valley floor and the Blossom Shelf a hundred blocks above it.
 *
 * <p>{@link #weather} is the habitat id {@code IsleWeatherService} rolls its tables with, and
 * {@link #woods} are the log drops that count as "this district's wood" (mastery, orders, extras).
 */
public enum Grove {

    FROSTPINE(1, "frostpine", "Frostpine Ridge", "§b", Material.SPRUCE_LOG, "snow",
            "Spruce · the snow keeps the count", List.of(Material.SPRUCE_LOG)),
    BLOSSOM(2, "blossom", "Blossom Shelf", "§d", Material.CHERRY_LOG, "flower",
            "Cherry · the shelf above the clouds", List.of(Material.CHERRY_LOG)),
    SUNSCAR(3, "sunscar", "Sunscar Mesa", "§6", Material.ACACIA_LOG, "savanna",
            "Acacia · red rock, long shadows", List.of(Material.ACACIA_LOG)),
    ELDERWOOD(4, "elderwood", "Elderwood Vale", "§a", Material.OAK_LOG, "plains",
            "Oak & Birch · where the streams run", List.of(Material.OAK_LOG, Material.BIRCH_LOG)),
    GLOAMWOOD(5, "gloamwood", "Gloamwood Hollow", "§8", Material.DARK_OAK_LOG, "dark_thicket",
            "Dark Oak · lanterns under the shelf", List.of(Material.DARK_OAK_LOG)),
    BRINEFALL(6, "brinefall", "Brinefall Mangroves", "§3", Material.MANGROVE_LOG, "swamp",
            "Mangrove · falls into the lakes", List.of(Material.MANGROVE_LOG)),
    CANOPY_CROWN(7, "crown", "Canopy Crown", "§2", Material.JUNGLE_LOG, "jungle",
            "Jungle · the island's roof", List.of(Material.JUNGLE_LOG, Material.BAMBOO_BLOCK));

    private final int code;
    private final String id;
    private final String display;
    private final String color;
    private final Material icon;
    private final String weather;
    private final String tagline;
    private final List<Material> woods;

    Grove(int code, String id, String display, String color, Material icon, String weather,
          String tagline, List<Material> woods) {
        this.code = code;
        this.id = id;
        this.display = display;
        this.color = color;
        this.icon = icon;
        this.weather = weather;
        this.tagline = tagline;
        this.woods = woods;
    }

    public int code() {
        return code;
    }

    public String id() {
        return id;
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

    public Material icon() {
        return icon;
    }

    public String weather() {
        return weather;
    }

    public String tagline() {
        return tagline;
    }

    public List<Material> woods() {
        return woods;
    }

    /** First wood — what hand-in orders ask for. */
    public Material mainWood() {
        return woods.getFirst();
    }

    public boolean owns(Material wood) {
        return wood != null && woods.contains(wood);
    }

    public static Grove byCode(int code) {
        for (Grove grove : values()) {
            if (grove.code == code) {
                return grove;
            }
        }
        return null;
    }

    public static Grove byId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (Grove grove : values()) {
            if (grove.id.equals(key) || grove.name().equalsIgnoreCase(key)) {
                return grove;
            }
        }
        return null;
    }

    /** District whose wood this is (oak and birch both mean the Vale; bamboo is the Crown's). */
    public static Grove ofWood(Material wood) {
        for (Grove grove : values()) {
            if (grove.owns(wood)) {
                return grove;
            }
        }
        return null;
    }
}
