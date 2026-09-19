package de.aetherion.pit.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Safe spawn = axis-aligned box inside the red line (no PvP).
 * Pit combat = outside that box.
 */
public final class SafeZoneService {

    private final JavaPlugin plugin;
    private String worldName;
    private double minX;
    private double maxX;
    private double minZ;
    private double maxZ;

    public SafeZoneService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        worldName = cfg.getString("safe-zone.world", "world");
        // Prefer explicit box corners; fall back to legacy cylinder if box missing.
        if (cfg.contains("safe-zone.min-x")) {
            minX = Math.min(cfg.getDouble("safe-zone.min-x"), cfg.getDouble("safe-zone.max-x"));
            maxX = Math.max(cfg.getDouble("safe-zone.min-x"), cfg.getDouble("safe-zone.max-x"));
            minZ = Math.min(cfg.getDouble("safe-zone.min-z"), cfg.getDouble("safe-zone.max-z"));
            maxZ = Math.max(cfg.getDouble("safe-zone.min-z"), cfg.getDouble("safe-zone.max-z"));
        } else {
            double x = cfg.getDouble("safe-zone.x", 0.5);
            double z = cfg.getDouble("safe-zone.z", 0.5);
            double r = Math.max(8.0, cfg.getDouble("safe-zone.radius", 48.0));
            minX = x - r;
            maxX = x + r;
            minZ = z - r;
            maxZ = z + r;
        }
    }

    public boolean isSafe(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return true;
        }
        if (!loc.getWorld().getName().equalsIgnoreCase(worldName)) {
            return true;
        }
        double x = loc.getX();
        double z = loc.getZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean isSafe(Player player) {
        return player != null && isSafe(player.getLocation());
    }

    public boolean isPitWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(worldName);
    }

    public Location spawn() {
        FileConfiguration cfg = plugin.getConfig();
        World world = Bukkit.getWorld(cfg.getString("spawn.world", worldName));
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                cfg.getDouble("spawn.x", 0.5),
                cfg.getDouble("spawn.y", 64.0),
                cfg.getDouble("spawn.z", 0.5),
                (float) cfg.getDouble("spawn.yaw", 0.0),
                (float) cfg.getDouble("spawn.pitch", 0.0)
        );
    }

    public String describe() {
        return String.format("x[%.0f..%.0f] z[%.0f..%.0f]", minX, maxX, minZ, maxZ);
    }

    public double minX() {
        return minX;
    }

    public double maxX() {
        return maxX;
    }

    public double minZ() {
        return minZ;
    }

    public double maxZ() {
        return maxZ;
    }
}
