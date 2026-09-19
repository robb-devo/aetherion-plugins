package de.aetherion.aethermobs.pet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * No-pet bubbles around hub quest NPCs. Quests stays optional (reflection / yml only).
 */
public final class PetSpawnZones {

    private static final double NPC_RADIUS = 10.0;
    private static final double NPC_RADIUS_SQUARED = NPC_RADIUS * NPC_RADIUS;

    /**
     * Hub cast that should stay pet-quiet. IDs match QuestNPCRegistry / LivingNpcProfile.
     */
    private static final Set<String> QUIET_NPC_IDS = Set.of(
            "egon",
            "quartermaster",
            "ledger",
            "lark",
            "hunter",
            "merchant",
            "collector",
            "vex"
    );

    /** Last-resort hubs if Quests data is missing. */
    private static final Map<String, double[]> FALLBACK_XYZ = Map.of(
            "egon", new double[]{534.2355, 32.0, 257.4345},
            "vex", new double[]{540.5, 32.0, 260.5},
            "ledger", new double[]{540.5, 32.0, 254.5},
            "quartermaster", new double[]{505.7, 41.0, 269.5},
            "hunter", new double[]{498.4, 43.0, 251.1},
            "collector", new double[]{476.5, 46.0, 276.5},
            "merchant", new double[]{493.5, 43.0, 266.5},
            "lark", new double[]{519.5, 39.0, 271.5}
    );

    private static final long CACHE_MS = 1_000L;

    private static volatile CacheSnapshot cached = CacheSnapshot.EMPTY;

    private PetSpawnZones() {
    }

    public static boolean isBlocked(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        for (Location center : centersInWorld(location.getWorld())) {
            if (center.distanceSquared(location) <= NPC_RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nearest quiet NPC within {@code radius}, or {@code null}.
     */
    public static Location nearestWithin(Location location, double radius) {
        if (location == null || location.getWorld() == null || radius <= 0) {
            return null;
        }
        double limit = radius * radius;
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (Location center : centersInWorld(location.getWorld())) {
            double dist = center.distanceSquared(location);
            if (dist <= limit && dist < bestDist) {
                bestDist = dist;
                best = center;
            }
        }
        return best;
    }

    public static double npcAvoidRadius() {
        return NPC_RADIUS;
    }

    private static List<Location> centersInWorld(World world) {
        List<Location> out = new ArrayList<>();
        for (Location center : snapshot().centers()) {
            if (center.getWorld() != null && center.getWorld().equals(world)) {
                out.add(center);
            }
        }
        return out;
    }

    private static CacheSnapshot snapshot() {
        long now = System.currentTimeMillis();
        CacheSnapshot hit = cached;
        if (now - hit.atMs() < CACHE_MS && hit.resolved()) {
            return hit;
        }
        CacheSnapshot next = resolveCenters();
        cached = next;
        return next;
    }

    private static CacheSnapshot resolveCenters() {
        List<Location> out = new ArrayList<>(QUIET_NPC_IDS.size());
        Plugin quests = Bukkit.getPluginManager().getPlugin("AetherionQuests");
        YamlConfiguration npcsYml = loadNpcsYml(quests);

        for (String id : QUIET_NPC_IDS) {
            Location loc = resolveQuietNpc(quests, npcsYml, id);
            if (loc != null && loc.getWorld() != null) {
                out.add(loc);
            }
        }

        return new CacheSnapshot(List.copyOf(out), System.currentTimeMillis(), true);
    }

    private static Location resolveQuietNpc(
            Plugin quests,
            YamlConfiguration npcsYml,
            String npcId
    ) {
        Location living = livingLocation(quests, npcId);
        if (living != null) {
            return living;
        }

        Location saved = savedLocation(quests, npcId);
        if (saved != null) {
            return saved;
        }

        Location fromFile = locationFromYml(npcsYml, npcId);
        if (fromFile != null) {
            return fromFile;
        }

        return fallbackLocation(npcId);
    }

    private static Location livingLocation(Plugin quests, String npcId) {
        if (quests == null || !quests.isEnabled()) {
            return null;
        }
        try {
            Object service = quests.getClass().getMethod("getLivingNpcService").invoke(quests);
            if (service == null) {
                return null;
            }
            Object loc = service.getClass()
                    .getMethod("locationOf", String.class)
                    .invoke(service, npcId);
            return loc instanceof Location location ? location.clone() : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Location savedLocation(Plugin quests, String npcId) {
        if (quests == null || !quests.isEnabled()) {
            return null;
        }
        try {
            Object storage = quests.getClass().getMethod("getNpcDataStorage").invoke(quests);
            if (storage == null) {
                return null;
            }
            Object loc = storage.getClass()
                    .getMethod("getSavedLocation", String.class)
                    .invoke(storage, npcId);
            return loc instanceof Location location ? location.clone() : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static YamlConfiguration loadNpcsYml(Plugin quests) {
        if (quests == null) {
            return null;
        }
        File file = new File(quests.getDataFolder(), "npcs.yml");
        if (!file.isFile()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private static Location locationFromYml(YamlConfiguration config, String npcId) {
        if (config == null || npcId == null) {
            return null;
        }
        String path = "npcs." + npcId.toLowerCase(Locale.ROOT);
        String worldName = config.getString(path + ".world");
        if (worldName == null || worldName.isBlank() || !config.contains(path + ".x")) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z")
        );
    }

    private static Location fallbackLocation(String npcId) {
        double[] xyz = FALLBACK_XYZ.get(npcId.toLowerCase(Locale.ROOT));
        if (xyz == null) {
            return null;
        }
        World world = Bukkit.getWorld("world");
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return null;
        }
        return new Location(world, xyz[0], xyz[1], xyz[2]);
    }

    private record CacheSnapshot(List<Location> centers, long atMs, boolean resolved) {
        private static final CacheSnapshot EMPTY =
                new CacheSnapshot(List.of(), 0L, false);
    }
}
