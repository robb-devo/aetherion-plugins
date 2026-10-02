package de.aetherion.farming.island;

import de.aetherion.farming.AetherionFarming;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared “are we on a farm field?” checks for bird scare / ambience / featured crop.
 * Live setup: Eldervale Farming Island is pasted into {@code world} (not aether_farm_island).
 */
public final class FarmIsleZones {

    public record Zone(String world, double x, double z, double radius) {
        boolean contains(Location at) {
            if (at == null || at.getWorld() == null) {
                return false;
            }
            if (!at.getWorld().getName().equalsIgnoreCase(world)) {
                return false;
            }
            double dx = at.getX() - x;
            double dz = at.getZ() - z;
            return (dx * dx + dz * dz) <= radius * radius;
        }
    }

    private FarmIsleZones() {
    }

    /** Config zones + legacy single farm-zone bubble. */
    public static List<Zone> birdZones(AetherionFarming plugin) {
        List<Zone> zones = new ArrayList<>();
        if (plugin == null) {
            return zones;
        }
        List<?> list = plugin.getConfig().getList("bird-scare.zones");
        if (list != null && !list.isEmpty()) {
            for (Object raw : list) {
                Zone zone = parseZone(raw);
                if (zone != null) {
                    zones.add(zone);
                }
            }
        }
        if (zones.isEmpty() && plugin.getConfig().getBoolean("bird-scare.farm-zone.enabled", true)) {
            String world = plugin.getConfig().getString("bird-scare.farm-zone.world", "world");
            double x = plugin.getConfig().getDouble("bird-scare.farm-zone.x", -211.5d);
            double z = plugin.getConfig().getDouble("bird-scare.farm-zone.z", 183.5d);
            double r = Math.max(16.0d, plugin.getConfig().getDouble("bird-scare.farm-zone.radius", 90.0d));
            zones.add(new Zone(world, x, z, r));
        }
        return zones;
    }

    public static boolean inBirdZone(AetherionFarming plugin, Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        if (plugin != null && plugin.getConfig().getBoolean("bird-scare.anywhere-near-crops", false)) {
            return true;
        }
        List<Zone> zones = birdZones(plugin);
        if (zones.isEmpty()) {
            return true;
        }
        for (Zone zone : zones) {
            if (zone.contains(at)) {
                return true;
            }
        }
        return false;
    }

    /** Soft footprint used for wind ambience + featured crop toast. */
    public static boolean inFarmIsleFootprint(AetherionFarming plugin, Location at) {
        if (at == null || at.getWorld() == null || plugin == null) {
            return false;
        }
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("farm-isle-footprint");
        if (sec == null || !sec.getBoolean("enabled", true)) {
            // Fallback: any configured bird zone that looks like the isle (large radius).
            for (Zone zone : birdZones(plugin)) {
                if (zone.radius() >= 150.0d && zone.contains(at)) {
                    return true;
                }
            }
            return false;
        }
        String world = sec.getString("world", "world");
        if (!at.getWorld().getName().equalsIgnoreCase(world)) {
            return false;
        }
        if (sec.contains("min-x")) {
            double minX = sec.getDouble("min-x");
            double maxX = sec.getDouble("max-x");
            double minZ = sec.getDouble("min-z");
            double maxZ = sec.getDouble("max-z");
            double x = at.getX();
            double z = at.getZ();
            return x >= Math.min(minX, maxX) && x <= Math.max(minX, maxX)
                    && z >= Math.min(minZ, maxZ) && z <= Math.max(minZ, maxZ);
        }
        double x = sec.getDouble("x", -300.0d);
        double z = sec.getDouble("z", 500.0d);
        double r = Math.max(32.0d, sec.getDouble("radius", 200.0d));
        double dx = at.getX() - x;
        double dz = at.getZ() - z;
        return (dx * dx + dz * dz) <= r * r;
    }

    /** Rectangular footprint bounds — radius-style config is converted to its bounding box. */
    public record Footprint(World world, int minX, int maxX, int minZ, int maxZ) {
        public int blockCount() {
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }
    }

    /** Null when the footprint is disabled or its world is not loaded. */
    public static Footprint footprint(AetherionFarming plugin) {
        if (plugin == null) {
            return null;
        }
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("farm-isle-footprint");
        if (sec == null || !sec.getBoolean("enabled", true)) {
            return null;
        }
        World world = org.bukkit.Bukkit.getWorld(sec.getString("world", "world"));
        if (world == null) {
            return null;
        }
        if (sec.contains("min-x")) {
            int minX = (int) Math.floor(Math.min(sec.getDouble("min-x"), sec.getDouble("max-x")));
            int maxX = (int) Math.ceil(Math.max(sec.getDouble("min-x"), sec.getDouble("max-x")));
            int minZ = (int) Math.floor(Math.min(sec.getDouble("min-z"), sec.getDouble("max-z")));
            int maxZ = (int) Math.ceil(Math.max(sec.getDouble("min-z"), sec.getDouble("max-z")));
            return new Footprint(world, minX, maxX, minZ, maxZ);
        }
        double x = sec.getDouble("x", -300.0d);
        double z = sec.getDouble("z", 500.0d);
        double r = Math.max(32.0d, sec.getDouble("radius", 200.0d));
        return new Footprint(
                world,
                (int) Math.floor(x - r),
                (int) Math.ceil(x + r),
                (int) Math.floor(z - r),
                (int) Math.ceil(z + r)
        );
    }

    public static World resolveWorld(AetherionFarming plugin) {
        if (plugin == null) {
            return null;
        }
        String name = plugin.getConfig().getString("farm-isle-footprint.world", "world");
        return org.bukkit.Bukkit.getWorld(name);
    }

    @SuppressWarnings("unchecked")
    private static Zone parseZone(Object raw) {
        if (raw instanceof ConfigurationSection section) {
            String world = section.getString("world", "world");
            double x = section.getDouble("x");
            double z = section.getDouble("z");
            double r = Math.max(16.0d, section.getDouble("radius", 90.0d));
            return new Zone(world, x, z, r);
        }
        if (raw instanceof java.util.Map<?, ?> map) {
            Object w = map.get("world");
            Object x = map.get("x");
            Object z = map.get("z");
            Object r = map.get("radius");
            if (x == null || z == null) {
                return null;
            }
            String world = w == null ? "world" : String.valueOf(w);
            double radius = r == null ? 90.0d : ((Number) r).doubleValue();
            return new Zone(world, ((Number) x).doubleValue(), ((Number) z).doubleValue(), Math.max(16.0d, radius));
        }
        return null;
    }
}
