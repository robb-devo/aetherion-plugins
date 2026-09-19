package de.aetherion.items.world;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Paintable wild-pet biotopes. IDs match AetherMobs {@code PetHabitat} names.
 */
public enum PetHabitatKind {

    LUSH("lush", "Lush", Material.MOSS_BLOCK, "§a"),
    FOREST("forest", "Forest", Material.OAK_LEAVES, "§2"),
    MOUNTAIN("mountain", "Mountain", Material.ANDESITE, "§7"),
    DARK("dark", "Dark Grove", Material.SPRUCE_LOG, "§8"),
    JUNGLE("jungle", "Jungle", Material.JUNGLE_LEAVES, "§a"),
    DESERT("desert", "Desert", Material.SANDSTONE, "§e"),
    SNOW("snow", "Snow", Material.SNOW_BLOCK, "§b"),
    MUSHROOM("mushroom", "Mushroom", Material.RED_MUSHROOM_BLOCK, "§c"),
    SWAMP("swamp", "Swamp", Material.MANGROVE_ROOTS, "§2"),
    FLOWER("flower", "Flower", Material.PINK_PETALS, "§d"),
    ELDERVALE("eldervale", "Eldervale", Material.DEEPSLATE_DIAMOND_ORE, "§b");

    private final String id;
    private final String display;
    private final Material icon;
    private final String color;

    PetHabitatKind(String id, String display, Material icon, String color) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.color = color;
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

    public String color() {
        return color;
    }

    public String coloredName() {
        return color + display;
    }

    public static PetHabitatKind fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT).trim();
        for (PetHabitatKind kind : values()) {
            if (kind.id.equals(key) || kind.name().equalsIgnoreCase(raw)) {
                return kind;
            }
        }
        return null;
    }
}
