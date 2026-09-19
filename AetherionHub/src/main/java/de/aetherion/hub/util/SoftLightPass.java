package de.aetherion.hub.util;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Places invisible {@code LIGHT} blocks on a coarse grid where natural+block light is low.
 * Safe to re-run: skips existing LIGHT blocks.
 */
public final class SoftLightPass {

    private SoftLightPass() {
    }

    public static void run(
            JavaPlugin plugin,
            CommandSender sender,
            World world,
            int centerX,
            int centerZ,
            int radius,
            int minLight,
            int step,
            int lightLevel
    ) {
        if (world == null) {
            sender.sendMessage("§cNo world.");
            return;
        }
        int r = Math.max(32, Math.min(radius, 1200));
        int grid = Math.max(3, Math.min(step, 16));
        int threshold = Math.max(0, Math.min(minLight, 14));
        int level = Math.max(1, Math.min(lightLevel, 15));

        sender.sendMessage("§eSoftlight: scanning §f" + world.getName()
                + " §eradius §f" + r + " §estep §f" + grid
                + " §e(min light §f" + threshold + "§e)…");

        final int[] placed = {0};
        final int[] checked = {0};
        final int minX = centerX - r;
        final int maxX = centerX + r;
        final int minZ = centerZ - r;
        final int maxZ = centerZ + r;

        java.util.List<int[]> columns = new java.util.ArrayList<>();
        for (int x = minX; x <= maxX; x += grid) {
            for (int z = minZ; z <= maxZ; z += grid) {
                columns.add(new int[]{x, z});
            }
        }

        final int batch = 40;
        final int[] index = {0};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(index[0] + batch, columns.size());
            for (int i = index[0]; i < end; i++) {
                int[] xz = columns.get(i);
                placeColumn(world, xz[0], xz[1], threshold, level, placed, checked);
            }
            index[0] = end;
            if (index[0] >= columns.size()) {
                task.cancel();
                sender.sendMessage("§aSoftlight done. §f" + placed[0]
                        + " §alights placed (§f" + checked[0] + " §achecked).");
            } else if (index[0] % (batch * 25) < batch) {
                int pct = (int) ((index[0] * 100L) / Math.max(1, columns.size()));
                sender.sendMessage("§7Softlight… §f" + pct + "% §8(§f" + placed[0] + "§8 placed)");
            }
        }, 1L, 1L);
    }

    private static void placeColumn(
            World world,
            int x,
            int z,
            int threshold,
            int level,
            int[] placed,
            int[] checked
    ) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.getChunkAt(x >> 4, z >> 4);
        }

        int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        int minY = world.getMinHeight() + 1;
        int maxY = Math.min(world.getMaxHeight() - 2, surface + 8);

        // Surface / canopy shade: one sample near feet height.
        tryPlace(world, x, Math.max(minY, surface + 1), z, threshold, level, placed, checked);

        // Interior / cave samples below surface on the same grid.
        for (int y = minY; y <= maxY; y += 4) {
            tryPlace(world, x, y, z, threshold, level, placed, checked);
        }
    }

    private static void tryPlace(
            World world,
            int x,
            int y,
            int z,
            int threshold,
            int level,
            int[] placed,
            int[] checked
    ) {
        Block block = world.getBlockAt(x, y, z);
        checked[0]++;
        Material type = block.getType();
        if (type == Material.LIGHT) {
            return;
        }
        if (!type.isAir() && type != Material.CAVE_AIR && type != Material.VOID_AIR) {
            return;
        }
        // Skip open sky bright areas.
        if (block.getLightFromSky() >= 12 && block.getLightFromBlocks() == 0) {
            return;
        }
        byte light = block.getLightLevel();
        if (light > threshold) {
            return;
        }
        // Prefer spots with something solid nearby (paths, rooms, under trees) — skip mid-void.
        if (!nearSolid(world, x, y, z)) {
            return;
        }

        block.setType(Material.LIGHT, false);
        if (block.getBlockData() instanceof Light data) {
            data.setLevel(level);
            block.setBlockData(data, false);
        }
        placed[0]++;
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
}
