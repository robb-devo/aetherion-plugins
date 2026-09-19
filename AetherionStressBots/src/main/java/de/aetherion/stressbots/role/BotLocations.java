package de.aetherion.stressbots.role;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
        Location assigned = assignedAnchor(player, section);
        if (assigned == null) {
            return null;
        }
        double radius = section == null ? 4 : section.getDouble("scatter-radius", 4);
        return scatter(assigned, radius, ThreadLocalRandom.current());
    }

    /**
     * Stable per-bot pad with solid-ground snap. Used for void/death recovery (no random scatter).
     */
    public static Location assignedAnchor(Player player, ConfigurationSection section) {
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
        return firstSolid(anchors.get(index), 6);
    }

    public static Location nearestAnchor(Location here, ConfigurationSection section) {
        List<Location> anchors = readAnchors(section);
        if (here == null) {
            return anchors.isEmpty() ? null : firstSolid(anchors.get(0), 6);
        }
        if (anchors.isEmpty()) {
            Location point = readPoint(section);
            return point == null ? null : firstSolid(point, 6);
        }
        Location best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Location anchor : anchors) {
            if (anchor.getWorld() != here.getWorld()) {
                continue;
            }
            double dx = anchor.getX() - here.getX();
            double dz = anchor.getZ() - here.getZ();
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                best = anchor;
                bestDist = dist;
            }
        }
        return best == null ? firstSolid(anchors.get(0), 6) : firstSolid(best, 6);
    }

    public static Location otherAnchor(Player player, ConfigurationSection section, Location avoid) {
        List<Location> anchors = readAnchors(section);
        if (anchors.size() < 2) {
            return assignedAnchor(player, section);
        }
        Location pick = anchors.get(ThreadLocalRandom.current().nextInt(anchors.size()));
        if (avoid != null && samePad(pick, avoid) && anchors.size() > 1) {
            for (Location candidate : anchors) {
                if (!samePad(candidate, avoid)) {
                    pick = candidate;
                    break;
                }
            }
        }
        return firstSolid(pick, 6);
    }

    public static double horizontalDistance(Location a, Location b) {
        if (a == null || b == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (a.getWorld() != b.getWorld()) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.hypot(dx, dz);
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
        Location groundedBase = firstSolid(base, 6);
        if (radius <= 0) {
            return groundedBase;
        }
        for (int i = 0; i < 18; i++) {
            double angle = rng.nextDouble(0, Math.PI * 2);
            double dist = Math.sqrt(rng.nextDouble()) * radius;
            Location candidate = groundedBase.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            Location solid = firstSolid(candidate, 2);
            if (solid != null && isSolidStand(solid)) {
                solid.setYaw(base.getYaw());
                solid.setPitch(base.getPitch());
                return solid;
            }
        }
        return groundedBase;
    }

    public static Location safeStanding(Location location) {
        return firstSolid(location, 4);
    }

    /**
     * Snap to a two-block-tall air column above a solid, non-liquid floor.
     * If the exact column is void, spiral outward. Last resort: centered original XYZ.
     */
    public static Location firstSolid(Location around, int xzRadius) {
        if (around == null || around.getWorld() == null) {
            return around;
        }
        Location centered = center(around);
        int radius = Math.max(0, xzRadius);
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    Location column = centered.clone().add(dx, 0, dz);
                    Location found = scanColumn(column);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return centered;
    }

    public static boolean isSolidStand(Location feet) {
        if (feet == null || feet.getWorld() == null) {
            return false;
        }
        Block below = feet.clone().add(0, -1, 0).getBlock();
        Block at = feet.getBlock();
        Block head = feet.clone().add(0, 1, 0).getBlock();
        return standableFloor(below) && at.isPassable() && head.isPassable();
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

    private static Location scanColumn(Location column) {
        for (int dy = 4; dy >= -10; dy--) {
            Location candidate = column.clone().add(0, dy, 0);
            if (isSolidStand(candidate)) {
                candidate.setYaw(column.getYaw());
                candidate.setPitch(column.getPitch());
                return candidate;
            }
        }
        return null;
    }

    private static boolean standableFloor(Block block) {
        if (block == null || block.isPassable() || block.isLiquid()) {
            return false;
        }
        Material type = block.getType();
        if (type.isAir()) {
            return false;
        }
        String name = type.name();
        return !name.contains("LEAVES") && !name.contains("CARPET") && !name.contains("SIGN");
    }

    private static Location center(Location location) {
        Location best = location.clone();
        best.setX(Math.floor(best.getX()) + 0.5);
        best.setZ(Math.floor(best.getZ()) + 0.5);
        return best;
    }

    private static boolean samePad(Location a, Location b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getWorld() == b.getWorld()
                && Math.abs(a.getX() - b.getX()) < 1.5
                && Math.abs(a.getZ() - b.getZ()) < 1.5;
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
