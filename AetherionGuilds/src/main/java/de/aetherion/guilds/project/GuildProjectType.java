package de.aetherion.guilds.project;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * Guild projects: a building that rises in visible stages as the guild pays in. Requirement keys:
 * {@code coins}, {@code mat:<MATERIAL>} (plain items), {@code item:<aetherion id>} (compressed / compacted).
 * Quarry output fits: raw goes to "mat:", milled goes to "item:", so a guild Storage Hut can pay directly.
 */
public enum GuildProjectType {

    GUILD_HALL("guild_hall", "Guild Hall", Material.BELL, "gp_site_hall",
            new int[]{0, 0, -9},
            List.of(
                    new Stage("Foundations", "gp_hall_1", List.of(
                            new Req("coins", 25_000L, "Coins"),
                            new Req("mat:COBBLESTONE", 1_024L, "Cobblestone"),
                            new Req("mat:OAK_LOG", 256L, "Oak Log"))),
                    new Stage("Walls", "gp_hall_2", List.of(
                            new Req("coins", 60_000L, "Coins"),
                            new Req("item:compressed_cobblestone", 16L, "Compressed Cobblestone"),
                            new Req("mat:SPRUCE_LOG", 512L, "Spruce Log"),
                            new Req("mat:COAL", 256L, "Coal"))),
                    new Stage("Roof & Banners", "gp_hall_3", List.of(
                            new Req("coins", 150_000L, "Coins"),
                            new Req("item:compacted_cobblestone", 2L, "Compacted Cobblestone"),
                            new Req("mat:RAW_IRON", 512L, "Raw Iron"),
                            new Req("mat:STRING", 256L, "String")))
            ),
            List.of("§7A timber-and-stone hall on the",
                    "§7harbour's north site. Long table,",
                    "§7chimney smoke, your banners.")),
    HARBOUR_BEACON("harbour_beacon", "Harbour Beacon", Material.SEA_LANTERN, "gp_site_beacon",
            new int[]{13, 0, -1},
            List.of(
                    new Stage("Plinth", "gp_beacon_1", List.of(
                            new Req("coins", 15_000L, "Coins"),
                            new Req("mat:COBBLESTONE", 512L, "Cobblestone"))),
                    new Stage("Tower", "gp_beacon_2", List.of(
                            new Req("coins", 40_000L, "Coins"),
                            new Req("item:compressed_cobblestone", 8L, "Compressed Cobblestone"),
                            new Req("mat:COAL", 512L, "Coal"))),
                    new Stage("Beacon Flame", "gp_beacon_3", List.of(
                            new Req("coins", 90_000L, "Coins"),
                            new Req("mat:RAW_COPPER", 512L, "Raw Copper"),
                            new Req("mat:REDSTONE", 256L, "Redstone"),
                            new Req("mat:RAW_GOLD", 128L, "Raw Gold")))
            ),
            List.of("§7A stone signal tower with a",
                    "§7copper lamp room. Ships (and",
                    "§7rivals) see it from far away."));

    public record Req(String key, long amount, String label) {
        public boolean coins() {
            return key.equals("coins");
        }
    }

    public record Stage(String name, String templateId, List<Req> reqs) {
    }

    private final String id;
    private final String display;
    private final Material icon;
    private final String siteTemplate;
    private final int[] harbourSite;
    private final List<Stage> stages;
    private final List<String> blurb;

    GuildProjectType(String id, String display, Material icon, String siteTemplate, int[] harbourSite,
                     List<Stage> stages, List<String> blurb) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.siteTemplate = siteTemplate;
        this.harbourSite = harbourSite;
        this.stages = stages;
        this.blurb = blurb;
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

    public String siteTemplate() {
        return siteTemplate;
    }

    /** Reserved site on the Guild Harbour starter, relative to the island origin. */
    public int[] harbourSite() {
        return harbourSite;
    }

    public List<Stage> stages() {
        return stages;
    }

    public List<String> blurb() {
        return blurb;
    }

    /** The finished shape: used to check the whole site is clear before starting. */
    public String finalTemplate() {
        return stages.get(stages.size() - 1).templateId();
    }

    public static GuildProjectType byId(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (GuildProjectType type : values()) {
            if (type.id.equals(key) || type.name().toLowerCase(Locale.ROOT).equals(key)) {
                return type;
            }
        }
        return null;
    }
}
