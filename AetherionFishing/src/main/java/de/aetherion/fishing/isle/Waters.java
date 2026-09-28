package de.aetherion.fishing.isle;

import de.aetherion.fishing.AetherionFishing;

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
 * The named waters of Fishing Eldervale from {@code waters} in config. Default circles were fitted
 * to every water body of Fishing-Eldervale-Island.schem (surface scan + closing, margin 3) and mapped
 * to the live paste: world = (x − 800, y − 42, z − 864).
 *
 * <p>Lake waters only match at lake level; tarns only above it — so a tarn on a cliff never steals a
 * cast into the lake under it.
 */
public final class Waters {

    public enum Kind {
        FOUNTAIN(0, "§e", "Fountain"),
        TARN(1, "§3", "Highland tarn"),
        LAKE(2, "§b", "Lake");

        private final int priority;
        private final String color;
        private final String label;

        Kind(int priority, String color, String label) {
            this.priority = priority;
            this.color = color;
            this.label = label;
        }

        public String color() {
            return color;
        }

        public String label() {
            return label;
        }
    }

    public record Circle(double x, double z, double radius) {
        boolean contains(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    public record Water(String id, String name, Kind kind, String blurb, List<Circle> circles) {

        public boolean contains(Location at) {
            if (at == null) {
                return false;
            }
            boolean lakeLevel = LakeWorld.lakeLevel(at.getY());
            if (kind == Kind.LAKE && !lakeLevel) {
                return false;
            }
            if (kind == Kind.TARN && at.getY() < LakeWorld.lakeY() + 4) {
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

        /** Centre of the main circle at the surface of {@code world}. */
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

    private final AetherionFishing plugin;
    private List<Water> ordered = List.of();
    private Map<String, Water> byId = Map.of();

    public Waters(AetherionFishing plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        List<Water> loaded = parse(plugin.getConfig().getConfigurationSection("waters"), plugin.getLogger());
        Map<String, Water> index = new LinkedHashMap<>();
        for (Water water : loaded) {
            index.put(water.id(), water);
        }
        ordered = loaded;
        byId = Collections.unmodifiableMap(index);
    }

    /** Waters from a {@code waters} section, in lookup order: fountain, tarns, lakes; smaller first. */
    static List<Water> parse(ConfigurationSection root, java.util.logging.Logger log) {
        List<Water> loaded = new ArrayList<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                List<Circle> circles = new ArrayList<>();
                for (String raw : section.getStringList("circles")) {
                    double[] v = LakeText.numbers(raw, 3);
                    if (v != null) {
                        circles.add(new Circle(v[0], v[1], Math.max(2.0d, v[2])));
                    }
                }
                if (circles.isEmpty()) {
                    if (log != null) {
                        log.warning("Fishing Eldervale water '" + id + "' has no circles — skipped.");
                    }
                    continue;
                }
                Kind kind;
                try {
                    kind = Kind.valueOf(section.getString("kind", "lake").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    kind = Kind.LAKE;
                }
                loaded.add(new Water(id.toLowerCase(Locale.ROOT), section.getString("name", id), kind,
                        section.getString("blurb", ""), List.copyOf(circles)));
            }
        }
        loaded.sort(Comparator.comparingInt((Water water) -> water.kind().priority)
                .thenComparingDouble(Water::smallestRadius));
        return Collections.unmodifiableList(loaded);
    }

    /** The named water at {@code at} (a bobber or a player), or {@code null}. */
    public Water at(Location at) {
        if (!LakeWorld.onIsle(at)) {
            return null;
        }
        for (Water water : ordered) {
            if (water.contains(at)) {
                return water;
            }
        }
        return null;
    }

    public Water byId(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Water> all() {
        return ordered;
    }

    /** Display order for boards: lakes, then tarns, then the fountain. */
    public List<Water> forBoards() {
        List<Water> out = new ArrayList<>(ordered);
        out.sort(Comparator.comparingInt((Water water) -> -water.kind().priority));
        return out;
    }

    public int size() {
        return ordered.size();
    }
}
