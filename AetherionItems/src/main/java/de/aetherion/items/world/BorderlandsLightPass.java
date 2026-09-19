package de.aetherion.items.world;

import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft LIGHT blocks on Borderlands overworld surface only (above Crypt ceiling).
 * Leaves the Crypt dark. Safe to re-run — skips existing LIGHT.
 */
public final class BorderlandsLightPass {

    private static final int STEP = 3;
    private static final int MIN_LIGHT = 9;
    private static final int LIGHT_LEVEL = 12;

    private BorderlandsLightPass() {
    }

    /** Schedule a one-shot pass after boot so chunks can load. */
    public static void schedule(JavaPlugin plugin) {
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> run(plugin), 100L);
    }

    public static void run(JavaPlugin plugin) {
        AetherionItems items = plugin instanceof AetherionItems aetherion
                ? aetherion
                : AetherionItems.getInstance();
        if (items == null || items.getAreas() == null) {
            return;
        }
        List<AreaZone> zones = items.getAreas().zonesOf(AreaType.BORDERLANDS);
        if (zones.isEmpty()) {
            plugin.getLogger().info("Borderlands light: no area zones marked.");
            return;
        }
        List<Column> jobs = new ArrayList<>();
        for (AreaZone zone : zones) {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) {
                continue;
            }
            int cx = (int) Math.floor(zone.getX());
            int cz = (int) Math.floor(zone.getZ());
            int r = Math.max(16, zone.getRadius());
            double r2 = (double) r * r;
            for (int x = cx - r; x <= cx + r; x += STEP) {
                for (int z = cz - r; z <= cz + r; z += STEP) {
                    double dx = (x + 0.5) - zone.getX();
                    double dz = (z + 0.5) - zone.getZ();
                    if (dx * dx + dz * dz > r2) {
                        continue;
                    }
                    jobs.add(new Column(world, x, z));
                }
            }
        }
        if (jobs.isEmpty()) {
            return;
        }
        plugin.getLogger().info("Borderlands light: scanning " + jobs.size() + " surface columns…");
        final int[] placed = {0};
        final int[] index = {0};
        final int batch = 48;
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(index[0] + batch, jobs.size());
            for (int i = index[0]; i < end; i++) {
                Column col = jobs.get(i);
                placed[0] += lightSurfaceColumn(col.world, col.x, col.z);
            }
            index[0] = end;
            if (index[0] >= jobs.size()) {
                task.cancel();
                plugin.getLogger().info("Borderlands light done: +" + placed[0] + " LIGHT blocks (surface only).");
            }
        }, 1L, 1L);
    }

    /** Overworld band only: Crypt ceiling + 1 → surface + 2. */
    private static int lightSurfaceColumn(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.getChunkAt(x >> 4, z >> 4);
        }
        int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        int minY = MobZoneService.CRYPT_CEILING_Y + 1;
        int maxY = Math.min(world.getMaxHeight() - 2, Math.max(surface + 2, minY));
        int placed = 0;
        placed += tryPlace(world, x, Math.max(minY, Math.min(surface + 1, maxY)), z);
        for (int y = minY; y <= maxY; y += 3) {
            placed += tryPlace(world, x, y, z);
        }
        return placed;
    }

    private static int tryPlace(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        if (type == Material.LIGHT) {
            return 0;
        }
        if (!type.isAir() && type != Material.CAVE_AIR && type != Material.VOID_AIR) {
            return 0;
        }
        if (block.getLightFromSky() >= 12 && block.getLightFromBlocks() == 0) {
            return 0;
        }
        if (block.getLightLevel() > MIN_LIGHT) {
            return 0;
        }
        if (!nearSolid(world, x, y, z)) {
            return 0;
        }
        block.setType(Material.LIGHT, false);
        if (block.getBlockData() instanceof Light data) {
            data.setLevel(LIGHT_LEVEL);
            block.setBlockData(data, false);
        }
        return 1;
    }

    private static boolean nearSolid(World world, int x, int y, int z) {
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Material m = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (m.isSolid() && m != Material.LIGHT) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private record Column(World world, int x, int z) {
    }
}
