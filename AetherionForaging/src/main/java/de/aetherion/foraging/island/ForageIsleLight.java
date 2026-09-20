package de.aetherion.foraging.island;

import de.aetherion.foraging.AetherionForaging;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft invisible {@link Material#LIGHT} fill for the forage isle AABB.
 * Batched — safe to re-run (skips existing LIGHT). Blocks only; never entities.
 */
public final class ForageIsleLight {

    private static final int STEP_XZ = 3;
    private static final int STEP_Y = 3;
    private static final int MIN_LIGHT = 9;
    private static final int LIGHT_LEVEL = 12;
    private static final int BATCH = 40;

    private ForageIsleLight() {
    }

    public static void run(AetherionForaging plugin, CommandSender sender) {
        String worldName = plugin.getConfig().getString("forage-isle.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("§cWorld not loaded: " + worldName);
            return;
        }

        // Footprint for Eldervale forage paste (rotate-y 180, center ~788/-234).
        int minX = plugin.getConfig().getInt("forage-isle.light.min-x", 500);
        int maxX = plugin.getConfig().getInt("forage-isle.light.max-x", 1070);
        int minY = plugin.getConfig().getInt("forage-isle.light.min-y", 50);
        int maxY = plugin.getConfig().getInt("forage-isle.light.max-y", 220);
        int minZ = plugin.getConfig().getInt("forage-isle.light.min-z", -500);
        int maxZ = plugin.getConfig().getInt("forage-isle.light.max-z", 30);

        List<int[]> columns = new ArrayList<>();
        for (int x = minX; x <= maxX; x += STEP_XZ) {
            for (int z = minZ; z <= maxZ; z += STEP_XZ) {
                columns.add(new int[]{x, z});
            }
        }
        sender.sendMessage("§eForage light pass: §f" + columns.size()
                + " §ecolumns (" + minX + ".." + maxX + " / " + minZ + ".." + maxZ + ")…");
        plugin.getLogger().info("Forage light: scanning " + columns.size() + " columns…");

        final int[] placed = {0};
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(index[0] + BATCH, columns.size());
            for (int i = index[0]; i < end; i++) {
                int[] col = columns.get(i);
                placed[0] += lightColumn(world, col[0], col[1], minY, maxY);
            }
            index[0] = end;
            if (index[0] >= columns.size()) {
                task.cancel();
                String msg = "§aForage light done: §f+" + placed[0] + " §aLIGHT blocks.";
                sender.sendMessage(msg);
                plugin.getLogger().info("Forage light done: +" + placed[0] + " LIGHT blocks.");
            }
        }, 1L, 1L);
    }

    private static int lightColumn(World world, int x, int z, int minY, int maxY) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.getChunkAt(x >> 4, z >> 4);
        }
        int placed = 0;
        for (int y = minY; y <= maxY; y += STEP_Y) {
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
        // Bright open sky — skip.
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
}
