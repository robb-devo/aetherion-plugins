package de.aetherion.stressbots.role;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * YAML world/anchor helpers shared by role handlers and the provisioner.
 */
public final class BotLocations {

    private BotLocations() {
    }

    public static Location pickAnchor(Player player, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        List<Location> anchors = readAnchors(section);
        if (anchors.isEmpty()) {
            Location point = readPoint(section);
            if (point == null) {
                return null;
            }
            anchors = List.of(point);
        }
        int index = Math.floorMod(stableIndex(player == null ? "bot" : player.getName()), anchors.size());
        Location base = anchors.get(index);
        return scatter(base, section.getDouble("scatter-radius", 8), ThreadLocalRandom.current());
    }

    public static List<Location> readAnchors(ConfigurationSection section) {
        List<Location> out = new ArrayList<>();
        if (section == null) {
            return out;
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return out;
        }
        float yaw = (float) section.getDouble("yaw", 0.0);
        float pitch = (float) section.getDouble("pitch", 0.0);
        List<?> raw = section.getList("anchors");
        if (raw == null) {
            raw = section.getList("waypoints");
        }
        if (raw == null) {
            return out;
        }
        for (Object entry : raw) {
            Location parsed = parsePoint(world, yaw, pitch, entry);
            if (parsed != null) {
                out.add(parsed);
            }
        }
        return out;
    }

    public static Location readPoint(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0)
        );
    }

    public static Location scatter(Location base, double radius, ThreadLocalRandom rng) {
        if (base == null || base.getWorld() == null) {
            return base;
        }
        if (radius <= 0) {
            return base.clone();
        }
        double angle = rng.nextDouble(0, Math.PI * 2);
        double dist = rng.nextDouble(0, radius);
        Location out = base.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        out.setYaw(base.getYaw());
        out.setPitch(base.getPitch());
        return safeStanding(out);
    }

    public static Location safeStanding(Location location) {
        if (location == null || location.getWorld() == null) {
            return location;
        }
        Location best = location.clone();
        best.setX(Math.floor(best.getX()) + 0.5);
        best.setZ(Math.floor(best.getZ()) + 0.5);
        for (int dy = 0; dy <= 8; dy++) {
            for (int sign : new int[]{0, 1, -1}) {
                if (dy == 0 && sign != 0) {
                    continue;
                }
                Location candidate = best.clone().add(0, sign * dy, 0);
                if (!candidate.getBlock().isPassable()) {
                    continue;
                }
                if (!candidate.clone().add(0, 1, 0).getBlock().isPassable()) {
                    continue;
                }
                if (candidate.clone().add(0, -1, 0).getBlock().isPassable()) {
                    continue;
                }
                return candidate;
            }
        }
        return best;
    }

    public static String format(Location location) {
        if (location == null || location.getWorld() == null) {
            return "nowhere";
        }
        return location.getWorld().getName()
                + " "
                + String.format(Locale.ROOT, "%.1f %.1f %.1f", location.getX(), location.getY(), location.getZ());
    }

    static int stableIndex(String name) {
        int hash = 0;
        for (int i = 0; i < name.length(); i++) {
            hash = 31 * hash + name.charAt(i);
        }
        return Math.abs(hash);
    }

    @SuppressWarnings("unchecked")
    private static Location parsePoint(World world, float yaw, float pitch, Object entry) {
        double x;
        double y;
        double z;
        if (entry instanceof ConfigurationSection point) {
            x = point.getDouble("x");
            y = point.getDouble("y");
            z = point.getDouble("z");
        } else if (entry instanceof Map<?, ?> map) {
            Map<String, Object> typed = (Map<String, Object>) map;
            x = toDouble(typed.get("x"));
            y = toDouble(typed.get("y"));
            z = toDouble(typed.get("z"));
        } else {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
