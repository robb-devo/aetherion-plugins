package de.aetherion.mining.isle;

import de.aetherion.mining.AetherionMining;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Named places of Mining Eldervale — halls, quarries, shafts, the lake — from
 * {@code mine-isle-districts} in config, plus the four depth bands under the island.
 *
 * <p>Unlike the Farm Isle's flat plots, a district can be <b>underground</b>: every district has an
 * optional y-range, so the same x/z can be "Lantern Row" on the surface and "The Undercroft" 70
 * blocks below it. Lookup order: halls and landmarks, then shafts (y-bounded), then water, then
 * quarries; smaller areas first inside a kind.
 */
public final class MineDistricts {

    public enum Kind {
        HALL(0, "§6", "hall"),
        SHAFT(1, "§3", "shaft"),
        WATER(2, "§b", "water"),
        QUARRY(3, "§e", "quarry");

        private final int priority;
        private final String color;
        private final String noun;

        Kind(int priority, String color, String noun) {
            this.priority = priority;
            this.color = color;
            this.noun = noun;
        }

        public String color() {
            return color;
        }

        public String noun() {
            return noun;
        }

        /** Rich Vein picks from places where ore actually gets mined. */
        public boolean mineable() {
            return this == SHAFT || this == QUARRY;
        }
    }

    /**
     * Depth under the island. Bonuses scale with Depth Gauge; Cave Sense adds a little more
     * Crystal Find luck in the two deepest bands.
     */
    public enum Band {
        SURFACE("Surface", "§a", 0.0d, 1.00d, 0),
        GALLERIES("Upper Galleries", "§e", 5.0d, 1.10d, 0),
        DEEP_WORKS("Deep Works", "§6", 12.0d, 1.25d, 1),
        UNDERCROFT("The Undercroft", "§c", 20.0d, 1.50d, 2);

        private final String display;
        private final String color;
        private final double fortune;
        private final double crystalMult;
        private final int bonusXp;

        Band(String display, String color, double fortune, double crystalMult, int bonusXp) {
            this.display = display;
            this.color = color;
            this.fortune = fortune;
            this.crystalMult = crystalMult;
            this.bonusXp = bonusXp;
        }

        public String display() {
            return display;
        }

        public String colored() {
            return color + display;
        }

        public double fortune() {
            return fortune;
        }

        public double crystalMult() {
            return crystalMult;
        }

        public int bonusXp() {
            return bonusXp;
        }

        public boolean deep() {
            return this == DEEP_WORKS || this == UNDERCROFT;
        }
    }

    public record Circle(double x, double z, double radius) {
        boolean contains(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    public record District(String id, String name, Kind kind, String blurb, List<Circle> circles,
                           double minY, double maxY, Location anchor) {

        public boolean contains(Location at) {
            if (at.getY() < minY || at.getY() > maxY) {
                return false;
            }
            for (Circle circle : circles) {
                if (circle.contains(at.getX(), at.getZ())) {
                    return true;
                }
            }
            return false;
        }

        public String colored() {
            return kind.color() + name;
        }

        /**
         * Where to send someone: the configured anchor, else the surface at the main circle
         * (shafts use the middle of their y-range so the arrow points down the mine).
         */
        public Location center(World world) {
            if (anchor != null && anchor.getWorld() != null) {
                return anchor.clone();
            }
            if (world == null || circles.isEmpty()) {
                return null;
            }
            Circle main = circles.get(0);
            int bx = (int) Math.floor(main.x());
            int bz = (int) Math.floor(main.z());
            double y = kind == Kind.SHAFT && maxY < 300
                    ? (Math.max(minY, -60) + maxY) / 2.0d
                    : world.getHighestBlockYAt(bx, bz) + 1;
            return new Location(world, bx + 0.5, y, bz + 0.5);
        }

        private double smallestRadius() {
            double best = Double.MAX_VALUE;
            for (Circle circle : circles) {
                best = Math.min(best, circle.radius());
            }
            return best;
        }
    }

    private final AetherionMining plugin;
    private List<District> ordered = List.of();
    private Map<String, District> byId = Map.of();
    private double galleriesBelow;
    private double deepBelow;
    private double undercroftBelow;

    public MineDistricts(AetherionMining plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var config = plugin.getConfig();
        List<District> loaded = config.getBoolean("mine-isle-districts.enabled", true)
                ? parse(config.getConfigurationSection("mine-isle-districts.districts"), plugin.getLogger())
                : List.of();
        Map<String, District> index = new LinkedHashMap<>();
        for (District district : loaded) {
            index.put(district.id(), district);
        }
        ordered = loaded;
        byId = Collections.unmodifiableMap(index);
        galleriesBelow = config.getDouble("mine-isle.depth.galleries-below-y", 82.0d);
        deepBelow = config.getDouble("mine-isle.depth.deep-works-below-y", 56.0d);
        undercroftBelow = config.getDouble("mine-isle.depth.undercroft-below-y", 26.0d);
    }

    static List<District> parse(ConfigurationSection root, java.util.logging.Logger log) {
        List<District> loaded = new ArrayList<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                List<Circle> circles = new ArrayList<>();
                for (String raw : section.getStringList("circles")) {
                    Circle circle = parseCircle(raw);
                    if (circle != null) {
                        circles.add(circle);
                    }
                }
                if (circles.isEmpty()) {
                    if (log != null) {
                        log.warning("Mining Eldervale district '" + id + "' has no circles — skipped.");
                    }
                    continue;
                }
                Kind kind;
                try {
                    kind = Kind.valueOf(section.getString("kind", "quarry").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    kind = Kind.QUARRY;
                }
                Location anchor = null;
                if (section.contains("anchor.x")) {
                    World world = org.bukkit.Bukkit.getWorld(section.getString("anchor.world", "world"));
                    if (world != null) {
                        anchor = new Location(world, section.getDouble("anchor.x"), section.getDouble("anchor.y"),
                                section.getDouble("anchor.z"), (float) section.getDouble("anchor.yaw", 0.0d), 0.0f);
                    }
                }
                loaded.add(new District(
                        id.toLowerCase(Locale.ROOT),
                        section.getString("name", id),
                        kind,
                        section.getString("blurb", ""),
                        List.copyOf(circles),
                        section.getDouble("min-y", -64.0d),
                        section.getDouble("max-y", 320.0d),
                        anchor
                ));
            }
        }
        loaded.sort(Comparator.comparingInt((District district) -> district.kind().priority)
                .thenComparingDouble(District::smallestRadius));
        return Collections.unmodifiableList(loaded);
    }

    /** District under {@code at}, or {@code null} outside every named place (or off the isle). */
    public District at(Location at) {
        if (at == null || at.getWorld() == null || !MineWorld.onIsle(plugin, at)) {
            return null;
        }
        for (District district : ordered) {
            if (district.contains(at)) {
                return district;
            }
        }
        return null;
    }

    public District byId(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public List<District> all() {
        return ordered;
    }

    public List<District> mineable() {
        List<District> out = new ArrayList<>();
        for (District district : ordered) {
            if (district.kind().mineable()) {
                out.add(district);
            }
        }
        return out;
    }

    public int size() {
        return ordered.size();
    }

    /** Depth band at {@code y} (only meaningful on the isle). */
    public Band band(double y) {
        if (y < undercroftBelow) {
            return Band.UNDERCROFT;
        }
        if (y < deepBelow) {
            return Band.DEEP_WORKS;
        }
        if (y < galleriesBelow) {
            return Band.GALLERIES;
        }
        return Band.SURFACE;
    }

    public Band band(Location at) {
        return at == null || !MineWorld.onIsle(plugin, at) ? Band.SURFACE : band(at.getY());
    }

    /** Top Y of a band (for the Surveyor's depth card). */
    public double bandTop(Band band) {
        return switch (band) {
            case SURFACE -> Double.POSITIVE_INFINITY;
            case GALLERIES -> galleriesBelow;
            case DEEP_WORKS -> deepBelow;
            case UNDERCROFT -> undercroftBelow;
        };
    }

    private static Circle parseCircle(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.trim().split("[\\s,]+");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new Circle(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Math.max(2.0d, Double.parseDouble(parts[2])));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
