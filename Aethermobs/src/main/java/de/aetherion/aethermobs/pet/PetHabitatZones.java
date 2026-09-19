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
import java.util.Locale;

/**
 * Painted pet biotopes from AetherionItems {@code pet-habitats.yml}.
 * Prefer these over block auto-detect.
 */
public final class PetHabitatZones {

    private static final long CACHE_MS = 1_500L;

    private static volatile CacheSnapshot cached = CacheSnapshot.EMPTY;

    private PetHabitatZones() {
    }

    public static PetHabitat habitatAt(Location location) {
        Zone zone = zoneAt(location);
        return zone == null ? null : zone.habitat();
    }

    public static Zone zoneAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        World world = location.getWorld();
        double x = location.getX();
        double z = location.getZ();
        Zone best = null;
        double bestRadius = Double.MAX_VALUE;
        for (Zone zone : snapshot().zones()) {
            if (zone.world() == null || !zone.world().equals(world)) {
                continue;
            }
            double dx = x - zone.x();
            double dz = z - zone.z();
            double r = zone.radius();
            if (dx * dx + dz * dz > r * r) {
                continue;
            }
            // Smaller paint wins on overlap.
            if (r < bestRadius) {
                bestRadius = r;
                best = zone;
            }
        }
        return best;
    }

    public static boolean hasAnyLoaded() {
        return !snapshot().zones().isEmpty();
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
            File file = new File(items.getDataFolder(), "pet-habitats.yml");
            if (file.isFile()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection root = yaml.getConfigurationSection("zones");
                if (root != null) {
                    for (String key : root.getKeys(false)) {
                        ConfigurationSection section = root.getConfigurationSection(key);
                        if (section == null) {
                            continue;
                        }
                        PetHabitat habitat = parseHabitat(section.getString("habitat"));
                        if (habitat == null || habitat == PetHabitat.ANY) {
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
                                habitat,
                                section.getDouble("x"),
                                section.getDouble("y"),
                                section.getDouble("z"),
                                Math.max(8.0, section.getDouble("radius", 50.0))
                        ));
                    }
                }
            }
        }
        return new CacheSnapshot(List.copyOf(zones), System.currentTimeMillis(), true);
    }

    private static PetHabitat parseHabitat(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PetHabitat.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record Zone(
            World world,
            PetHabitat habitat,
            double x,
            double y,
            double z,
            double radius
    ) {
    }

    private record CacheSnapshot(List<Zone> zones, long atMs, boolean resolved) {
        private static final CacheSnapshot EMPTY =
                new CacheSnapshot(List.of(), 0L, false);
    }
}
