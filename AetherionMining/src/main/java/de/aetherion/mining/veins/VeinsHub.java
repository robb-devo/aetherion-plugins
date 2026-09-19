package de.aetherion.mining.veins;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;

public final class VeinsHub {

    static final int RADIUS = VeinsChunkGenerator.HUB;

    private VeinsHub() {
    }

    public static boolean protectedSpot(Location location, int hubY) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (y < hubY - 4 || y > hubY + 8) {
            return false;
        }
        if (Math.abs(x) <= RADIUS && Math.abs(z) <= RADIUS && y >= hubY - 2) {
            return true;
        }
        if (Math.abs(x) <= 1 && Math.abs(z) <= RADIUS) {
            return true;
        }
        return Math.abs(z) <= 1 && Math.abs(x) <= RADIUS;
    }

    public static void build(World world, int hubY) {
        if (world == null) {
            return;
        }
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.getChunkAt(cx, cz).load();
            }
        }
        int floor = hubY;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                boolean rim = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
                world.getBlockAt(x, floor, z).setType(rim ? Material.DEEPSLATE_BRICKS : Material.POLISHED_DEEPSLATE, false);
                for (int y = floor + 1; y <= floor + 7; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
                world.getBlockAt(x, floor - 1, z).setType(Material.BEDROCK, false);
            }
        }
        lantern(world, -7, floor + 1, -7);
        lantern(world, 7, floor + 1, -7);
        lantern(world, -7, floor + 1, 7);
        lantern(world, 7, floor + 1, 7);
        lantern(world, 0, floor + 1, 0);
        world.getBlockAt(4, floor + 1, -7).setType(Material.SMITHING_TABLE, false);
        world.getBlockAt(5, floor + 1, -7).setType(Material.CHEST, false);
        descent(world, floor, 0, 1, BlockFace.SOUTH);
        descent(world, floor, 0, -1, BlockFace.NORTH);
        descent(world, floor, 1, 0, BlockFace.EAST);
        descent(world, floor, -1, 0, BlockFace.WEST);
    }

    private static void lantern(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.LANTERN, false);
    }

    private static void descent(World world, int floor, int dx, int dz, BlockFace face) {
        int road = floor - 4;
        int start = RADIUS - 6;
        for (int along = start; along <= RADIUS; along++) {
            int dropped = Math.max(0, along - start - 2);
            int y = Math.max(road, floor - dropped);
            int cx = dx * along;
            int cz = dz * along;
            for (int w = -1; w <= 1; w++) {
                int x = dx == 0 ? w : cx;
                int z = dz == 0 ? w : cz;
                for (int head = 0; head <= 4; head++) {
                    world.getBlockAt(x, y + head, z).setType(Material.AIR, false);
                }
                stair(world, x, y, z, face);
            }
        }
    }

    private static void stair(World world, int x, int y, int z, BlockFace face) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.DEEPSLATE_BRICK_STAIRS, false);
        if (block.getBlockData() instanceof Stairs stairs) {
            stairs.setFacing(face);
            stairs.setHalf(Bisected.Half.BOTTOM);
            block.setBlockData(stairs, false);
        }
    }
}
