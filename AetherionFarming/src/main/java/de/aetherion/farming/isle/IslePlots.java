package de.aetherion.farming.isle;

import de.aetherion.farming.AetherionFarming;

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
 * Named areas of Eldervale — fields, landmarks, water — from {@code farm-isle-plots} in config.
 * Default circles were measured from Farming-Eldervale-Island.schem (crop colour clusters, marker
 * clusters, doors) and mapped with the live paste: world = (-481 + x, y - 92, 420 + z).
 */
public final class IslePlots {

    public enum Kind {
        LANDMARK(0, "§6"),
        WATER(1, "§b"),
        FIELD(2, "§e");

        private final int priority;
        private final String color;

        Kind(int priority, String color) {
            this.priority = priority;
            this.color = color;
        }

        public String color() {
            return color;
        }
    }

    public record Circle(double x, double z, double radius) {
        boolean contains(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    public record Plot(String id, String name, Kind kind, String blurb, List<Circle> circles) {

        public boolean contains(Location at) {
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

        /** Centre of the first (main) circle at the surface of {@code world}. */
        public Location center(World world) {
            if (world == null || circles.isEmpty()) {
                return null;
            }
            Circle main = circles.get(0);
            int bx = (int) Math.floor(main.x());
            int bz = (int) Math.floor(main.z());
            int y = world.getHighestBlockYAt(bx, bz) + 1;
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

    private final AetherionFarming plugin;
    private List<Plot> ordered = List.of();
    private Map<String, Plot> byId = Map.of();

    public IslePlots(AetherionFarming plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        List<Plot> loaded = plugin.getConfig().getBoolean("farm-isle-plots.enabled", true)
                ? parse(plugin.getConfig().getConfigurationSection("farm-isle-plots.plots"), plugin.getLogger())
                : List.of();
        Map<String, Plot> index = new LinkedHashMap<>();
        for (Plot plot : loaded) {
            index.put(plot.id(), plot);
        }
        ordered = loaded;
        byId = Collections.unmodifiableMap(index);
    }

    /**
     * Plots from a {@code farm-isle-plots.plots} section, in lookup order: landmarks, then water,
     * then fields; smaller areas first inside a kind.
     */
    static List<Plot> parse(ConfigurationSection root, java.util.logging.Logger log) {
        List<Plot> loaded = new ArrayList<>();
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
                        log.warning("Farm Isle plot '" + id + "' has no circles — skipped.");
                    }
                    continue;
                }
                Kind kind;
                try {
                    kind = Kind.valueOf(section.getString("kind", "field").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    kind = Kind.FIELD;
                }
                loaded.add(new Plot(
                        id.toLowerCase(Locale.ROOT),
                        section.getString("name", id),
                        kind,
                        section.getString("blurb", ""),
                        List.copyOf(circles)
                ));
            }
        }
        // Landmarks and water win over the fields they sit in; smaller areas win ties.
        loaded.sort(Comparator.comparingInt((Plot plot) -> plot.kind().priority)
                .thenComparingDouble(Plot::smallestRadius));
        return Collections.unmodifiableList(loaded);
    }

    /** First plot of {@code plots} containing x/z — the lookup rule without the footprint check. */
    static Plot first(List<Plot> plots, double x, double z) {
        for (Plot plot : plots) {
            for (Circle circle : plot.circles()) {
                if (circle.contains(x, z)) {
                    return plot;
                }
            }
        }
        return null;
    }

    /** Plot under {@code at}, or {@code null} outside every named area (or off the isle). */
    public Plot at(Location at) {
        if (at == null || at.getWorld() == null || !IsleWorld.onIsle(plugin, at)) {
            return null;
        }
        return first(ordered, at.getX(), at.getZ());
    }

    public Plot byId(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Plot> all() {
        return ordered;
    }

    public List<Plot> fields() {
        List<Plot> out = new ArrayList<>();
        for (Plot plot : ordered) {
            if (plot.kind() == Kind.FIELD) {
                out.add(plot);
            }
        }
        return out;
    }

    public int size() {
        return ordered.size();
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
