package de.aetherion.aethermobs.pet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Overworld stand-in for Nether pet spawns — reads AetherionItems areas.yml
 * zones with type {@code borderlands}.
 */
public final class PetBorderlands {

    private static final long CACHE_MS = 2_000L;

    private static volatile CacheSnapshot cached = CacheSnapshot.EMPTY;

    private PetBorderlands() {
    }

    public static boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();
        double x = location.getX();
        double z = location.getZ();
        for (Zone zone : snapshot().zones()) {
            if (zone.world() == null || !zone.world().equals(world)) {
                continue;
            }
            // Horizontal only — terrain height must not eject the zone.
            double dx = x - zone.x();
            double dz = z - zone.z();
            double r = zone.radius();
            if (dx * dx + dz * dz <= r * r) {
                return true;
            }
        }
        return false;
    }

    private static CacheSnapshot snapshot() {
        long now = System.currentTimeMillis();
        CacheSnapshot hit = cached;
        if (now - hit.atMs() < CACHE_MS && hit.resolved()) {
            return hit;
        }
        CacheSnapshot next = load();
        cached = next;
        return next;
    }

    private static CacheSnapshot load() {
        List<Zone> zones = new ArrayList<>();
        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items != null) {
            File file = new File(items.getDataFolder(), "areas.yml");
            if (file.isFile()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection root = yaml.getConfigurationSection("zones");
                if (root != null) {
                    for (String key : root.getKeys(false)) {
                        ConfigurationSection section = root.getConfigurationSection(key);
                        if (section == null) {
                            continue;
                        }
                        if (!"borderlands".equalsIgnoreCase(section.getString("type", ""))) {
                            continue;
                        }
                        String worldName = section.getString("world");
                        if (worldName == null || worldName.isBlank()) {
                            continue;
                        }
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) {
                            continue;
                        }
                        zones.add(new Zone(
                                world,
                                section.getDouble("x"),
                                section.getDouble("y"),
                                section.getDouble("z"),
                                Math.max(8.0, section.getDouble("radius", 40.0))
                        ));
                    }
                }
            }
        }
        return new CacheSnapshot(List.copyOf(zones), System.currentTimeMillis(), true);
    }

    private record Zone(World world, double x, double y, double z, double radius) {
    }

    private record CacheSnapshot(List<Zone> zones, long atMs, boolean resolved) {
        private static final CacheSnapshot EMPTY =
                new CacheSnapshot(List.of(), 0L, false);
    }
}
