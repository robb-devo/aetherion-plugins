package de.aetherion.quests.npc;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;

/**
 * Soft lights around an outdoor NPC camp so hostiles don't spawn on them.
 */
public final class NpcCampComfort {

    private NpcCampComfort() {
    }

    public static int lightCamp(Location center, int radius, int step, int minLight, int lightLevel) {
        if (center == null || center.getWorld() == null) {
            return 0;
        }
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int r = Math.max(4, Math.min(radius, 48));
        int grid = Math.max(2, Math.min(step, 8));
        int threshold = Math.max(0, Math.min(minLight, 14));
        int level = Math.max(1, Math.min(lightLevel, 15));
        int placed = 0;

        for (int x = cx - r; x <= cx + r; x += grid) {
            for (int z = cz - r; z <= cz + r; z += grid) {
                if (((x - cx) * (x - cx) + (z - cz) * (z - cz)) > r * r) {
                    continue;
                }
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Block ground = world.getBlockAt(x, y, z);
                Block air = ground.getRelative(0, 1, 0);
                if (air.getType().isSolid() && air.getType() != Material.LIGHT) {
                    continue;
                }
                if (air.getLightFromBlocks() >= threshold && air.getLightFromSky() >= threshold) {
                    continue;
                }
                if (air.getType() == Material.LIGHT) {
                    continue;
                }
                if (air.getType() != Material.AIR && air.getType() != Material.CAVE_AIR && air.getType() != Material.VOID_AIR) {
                    continue;
                }
                air.setType(Material.LIGHT, false);
                if (air.getBlockData() instanceof Light light) {
                    light.setLevel(level);
                    air.setBlockData(light, false);
                }
                placed++;
            }
        }
        return placed;
    }
}
