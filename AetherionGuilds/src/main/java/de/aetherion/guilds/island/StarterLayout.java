package de.aetherion.guilds.island;

import de.aetherion.guilds.model.IslandBiome;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * The authored island starters (about 41 across, each with an open, flat work yard). Numbers mirror the
 * template authoring ({@code aeg/starters.py}): spawn is relative to the island origin (surface = y 0), the hut
 * pad is the 5x5 Hut Site marked with a path ring. Spawns are unchanged from the first, smaller starters, so
 * islands pasted from those keep working.
 */
public enum StarterLayout {

    GROVE("grove", "Grove Camp", "starter_grove", Material.OAK_SAPLING, IslandBiome.FOREST,
            0, 1, 12, 180f, 7, 4,
            Land.GRASSY,
            List.of(
                    "§7A mossy woodland isle under a",
                    "§7big old oak. Camp, tent and a",
                    "§7lily pond. Quiet and green.",
                    "",
                    "§8Best for: §7builders, foragers,",
                    "§7a slow first evening."
            )),
    QUARRY("quarry", "Quarry Outpost", "starter_quarry", Material.IRON_PICKAXE, IslandBiome.PLAINS,
            0, 1, 12, 180f, 5, 7,
            Land.ROCKY,
            List.of(
                    "§7A rocky isle with an ore-veined",
                    "§7outcrop, a timber mine mouth,",
                    "§7a tool shed and a ready",
                    "§7§fQuarry Pad§7.",
                    "",
                    "§8Best for: §7production chains."
            )),
    TIDE("tide", "Tide Dock", "starter_tide", Material.OAK_BOAT, IslandBiome.DESERT,
            0, 1, -12, 0f, 7, -7,
            Land.SANDY,
            List.of(
                    "§7A sandy isle round a sheltered",
                    "§7lagoon. Plank dock, net shack,",
                    "§7a rowboat tied up and waiting.",
                    "",
                    "§8Best for: §7harbour folk,",
                    "§7fishers, sunsets."
            )),
    GUILD("guild_harbour", "Guild Harbour", "starter_guild", Material.WHITE_BANNER, IslandBiome.PLAINS,
            0, 1, 19, 180f, 9, 4,
            Land.GRASSY,
            List.of(
                    "§7Paved plaza, covered well, banner",
                    "§7poles, the project board and two",
                    "§7claimed project sites."
            ));

    /** Guild Harbour: the project board lectern and the two reserved project sites (relative to origin). */
    public static final int[] GUILD_BOARD = {0, 1, 1};
    public static final int[] GUILD_HALL_SITE = {0, 0, -9};
    public static final int[] GUILD_BEACON_SITE = {13, 0, -1};

    public enum Land {
        GRASSY,
        ROCKY,
        SANDY
    }

    private final String id;
    private final String display;
    private final String templateId;
    private final Material icon;
    private final IslandBiome biome;
    private final int spawnX;
    private final int spawnY;
    private final int spawnZ;
    private final float yaw;
    private final int hutPadX;
    private final int hutPadZ;
    private final Land land;
    private final List<String> lore;

    StarterLayout(String id, String display, String templateId, Material icon, IslandBiome biome,
                  int spawnX, int spawnY, int spawnZ, float yaw, int hutPadX, int hutPadZ, Land land,
                  List<String> lore) {
        this.id = id;
        this.display = display;
        this.templateId = templateId;
        this.icon = icon;
        this.biome = biome;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.yaw = yaw;
        this.hutPadX = hutPadX;
        this.hutPadZ = hutPadZ;
        this.land = land;
        this.lore = lore;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public String templateId() {
        return templateId;
    }

    public Material icon() {
        return icon;
    }

    public IslandBiome biome() {
        return biome;
    }

    public int spawnX() {
        return spawnX;
    }

    public int spawnY() {
        return spawnY;
    }

    public int spawnZ() {
        return spawnZ;
    }

    public float yaw() {
        return yaw;
    }

    public int hutPadX() {
        return hutPadX;
    }

    public int hutPadZ() {
        return hutPadZ;
    }

    public Land land() {
        return land;
    }

    public List<String> lore() {
        return lore;
    }

    public boolean personal() {
        return this != GUILD;
    }

    public static StarterLayout byId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (StarterLayout layout : values()) {
            if (layout.id.equals(key) || layout.name().toLowerCase(Locale.ROOT).equals(key)) {
                return layout;
            }
        }
        return null;
    }

    public static List<StarterLayout> personalChoices() {
        return List.of(GROVE, QUARRY, TIDE);
    }

    /** Land look for islands without a starter (the classic biome pads). */
    public static Land landFor(IslandBiome biome) {
        if (biome == IslandBiome.DESERT) {
            return Land.SANDY;
        }
        return Land.GRASSY;
    }
}
