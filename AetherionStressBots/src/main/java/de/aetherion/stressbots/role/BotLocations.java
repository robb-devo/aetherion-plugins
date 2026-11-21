package de.aetherion.stressbots.role;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.HubAccess;
import de.aetherion.core.api.QuestProgressAccess;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World/anchor helpers shared by role handlers, the provisioner and the safety watchdog.
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
     * Every spot a role may be sent to: the YAML {@code anchors} (or {@code waypoints}), then the live
     * Hub spawns named in {@code hub-spawns}, then quest NPC spots when {@code use-quest-npcs} is on.
     * Hub and NPC spots come from the running server, so they follow the map when it moves.
     */
    public static List<Location> candidates(Player player, ConfigurationSection section) {
        List<Location> out = new ArrayList<>(readAnchors(section));
        if (section == null) {
            return out;
        }
        HubAccess hub = AetherServices.hub();
        if (hub != null) {
            for (String id : section.getStringList("hub-spawns")) {
                Location spawn = hub.location(id);
                if (spawn != null && spawn.getWorld() != null) {
                    out.add(spawn.clone());
                }
            }
        }
        if (player != null && section.getBoolean("use-quest-npcs", false)) {
            QuestProgressAccess quests = AetherServices.quests();
            if (quests != null) {
                List<Location> npcs = new ArrayList<>(quests.npcsNear(player, section.getDouble("quest-npc-radius", 1500)));
                // Stable order, so a bot keeps the same NPC across restarts.
                npcs.sort(Comparator.comparingDouble(Location::getX).thenComparingDouble(Location::getZ));
                for (Location npc : npcs) {
                    out.add(npc.clone());
                }
            }
        }
        if (out.isEmpty() && section.contains("x") && section.contains("z")) {
            Location point = readPoint(section);
            if (point != null) {
                out.add(point);
            }
        }
        return out;
    }

    /**
     * Stable per-bot spot, snapped to solid ground. A spot that has no floor (moved build, void) is skipped
     * for the next one; null only when no candidate has ground under it.
     */
    public static Location assignedAnchor(Player player, ConfigurationSection section) {
        List<Location> spots = candidates(player, section);
        if (spots.isEmpty()) {
            return null;
        }
        int start = Math.floorMod(stableIndex(player == null ? "bot" : player.getName()), spots.size());
        for (int i = 0; i < spots.size(); i++) {
            Location solid = standOn(spots.get((start + i) % spots.size()), 6);
            if (solid != null) {
                return solid;
            }
        }
        return null;
    }

    /** Nearest configured anchor by XZ, unsnapped (cheap: used every watchdog tick). */
    public static Location nearestAnchor(Location here, ConfigurationSection section) {
        if (here == null) {
            return null;
        }
        Location best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Location anchor : readAnchors(section)) {
            double dist = horizontalDistance(anchor, here);
            if (dist < bestDist) {
                best = anchor;
                bestDist = dist;
            }
        }
        return best;
    }

    public static Location otherAnchor(Player player, ConfigurationSection section, Location avoid) {
        List<Location> spots = candidates(player, section);
        if (spots.size() < 2) {
            return assignedAnchor(player, section);
        }
        int start = ThreadLocalRandom.current().nextInt(spots.size());
        for (int i = 0; i < spots.size(); i++) {
            Location pick = spots.get((start + i) % spots.size());
            if (avoid != null && samePad(pick, avoid)) {
                continue;
            }
            Location solid = standOn(pick, 6);
            if (solid != null) {
                return solid;
            }
        }
        return assignedAnchor(player, section);
    }

    /** Void floor for a role: {@code void-floor-y} in its section, else {@code testbots.safety.void-floor-y}. */
    public static double voidFloor(ConfigurationSection root, ConfigurationSection section) {
        double global = root == null ? 40 : root.getDouble("testbots.safety.void-floor-y", 40);
        return section == null ? global : section.getDouble("void-floor-y", global);
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
        Location groundedBase = isSolidStand(base) ? base : standOn(base, 6);
        if (groundedBase == null) {
            return null;
        }
        if (radius <= 0) {
            return groundedBase;
        }
        for (int i = 0; i < 18; i++) {
            double angle = rng.nextDouble(0, Math.PI * 2);
            double dist = Math.sqrt(rng.nextDouble()) * radius;
            Location candidate = groundedBase.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            Location solid = standOn(candidate, 2);
            if (solid != null && Math.abs(solid.getY() - groundedBase.getY()) <= 3) {
                solid.setYaw(base.getYaw());
                solid.setPitch(base.getPitch());
                return solid;
            }
        }
        return groundedBase;
    }

    public static Location safeStanding(Location location) {
        return standOn(location, 4);
    }

    /**
     * Snap to a two-block-tall air column above a solid, non-liquid floor, spiralling outward up to
     * {@code xzRadius}. Null when there is no such column: never hand back a spot over the void.
     */
    public static Location standOn(Location around, int xzRadius) {
        if (around == null || around.getWorld() == null) {
            return null;
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
        return null;
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
