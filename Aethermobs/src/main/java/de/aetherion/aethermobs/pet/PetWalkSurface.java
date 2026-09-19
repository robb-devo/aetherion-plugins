package de.aetherion.aethermobs.pet;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;

/**
 * Walkable ground for surface pets. Vanilla heightmaps treat logs as
 * ground, which parked pets on tree canopies the moment they walked
 * under a tree. This walks down through foliage and wood to real terrain.
 */
public final class PetWalkSurface {

    private PetWalkSurface() {
    }

    public static int groundY(
            World world,
            int x,
            int z
    ) {

        if (world == null) {
            return 0;
        }

        int y =
                world.getHighestBlockYAt(
                        x,
                        z,
                        HeightMap.MOTION_BLOCKING
                );

        int minY =
                world.getMinHeight()
                        + 1;

        while (y > minY) {

            Block block =
                    world.getBlockAt(
                            x,
                            y,
                            z
                    );

            Material type =
                    block.getType();

            if (isCanopy(type)
                    || isWatery(block)
                    || !type.isSolid()) {

                y--;
                continue;
            }

            return y;
        }

        return world.getHighestBlockYAt(
                x,
                z,
                HeightMap.MOTION_BLOCKING_NO_LEAVES
        );
    }

    /**
     * Walkable ground relative to where the pet already is. Under roofs /
     * overhangs (mushroom caves, lush) classic {@link #groundY} latches onto
     * the ceiling — keep the local floor near {@code nearY} instead.
     */
    public static int walkGroundY(World world, int x, int z, int nearY) {
        if (world == null) {
            return 0;
        }
        int top = groundY(world, x, z);
        if (top >= nearY + 3) {
            return groveFloorY(world, x, z, nearY);
        }
        return top;
    }

    /**
     * Dry check for the column the pet would actually stand on near {@code nearY}.
     */
    public static boolean isDryWalkColumn(World world, int x, int z, int nearY) {
        if (world == null) {
            return false;
        }
        int ground = walkGroundY(world, x, z, nearY);
        Block groundBlock = world.getBlockAt(x, ground, z);
        if (!groundBlock.getType().isSolid()
                || isWatery(groundBlock)
                || isCanopy(groundBlock.getType())) {
            return false;
        }
        for (int dy = 1; dy <= 3; dy++) {
            if (isWatery(world.getBlockAt(x, ground + dy, z))) {
                return false;
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (isWatery(world.getBlockAt(x + dx, ground + 1, z + dz))
                        || isWatery(world.getBlockAt(x + dx, ground, z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Standable floor near {@code nearY} for covered groves (mushroom caves,
     * lush overhangs). Vanilla heightmaps latch onto the roof.
     */
    public static int groveFloorY(World world, int x, int z, int nearY) {
        if (world == null) {
            return 0;
        }
        int minY = world.getMinHeight() + 1;
        int maxY = Math.min(world.getMaxHeight() - 3, nearY + 10);
        int start = Math.max(minY, nearY + 6);
        for (int y = start; y >= Math.max(minY, nearY - 40); y--) {
            Block ground = world.getBlockAt(x, y, z);
            Material type = ground.getType();
            if (!type.isSolid() || isCanopy(type) || isWatery(ground)) {
                continue;
            }
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            if (!feet.isPassable() || !head.isPassable()) {
                continue;
            }
            if (isWatery(feet) || isWatery(head)) {
                continue;
            }
            return y;
        }
        // Fallback: still try classic surface walk.
        int fallback = groundY(world, x, z);
        return fallback <= maxY ? fallback : Math.max(minY, nearY - 1);
    }

    /**
     * True when the standable column is dry: solid ground, no liquid /
     * waterlogged air column, and no adjacent surface water that would
     * visually park surface pets "in the water".
     */
    public static boolean isDrySurfaceColumn(World world, int x, int z) {
        if (world == null) {
            return false;
        }
        int groundY = groundY(world, x, z);
        Block ground = world.getBlockAt(x, groundY, z);
        if (!ground.getType().isSolid()
                || isWatery(ground)
                || isCanopy(ground.getType())) {
            return false;
        }

        for (int dy = 1; dy <= 3; dy++) {
            if (isWatery(world.getBlockAt(x, groundY + dy, z))) {
                return false;
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block neighborFeet =
                        world.getBlockAt(x + dx, groundY + 1, z + dz);
                Block neighborGround =
                        world.getBlockAt(x + dx, groundY, z + dz);
                if (isWatery(neighborFeet)
                        || isWatery(neighborGround)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean isWatery(Block block) {
        if (block == null) {
            return false;
        }
        if (block.isLiquid()) {
            return true;
        }

        Material type = block.getType();
        if (type == Material.BUBBLE_COLUMN
                || type == Material.KELP
                || type == Material.KELP_PLANT
                || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS
                || type == Material.WATER_CAULDRON
                || type == Material.LAVA_CAULDRON) {
            return true;
        }

        try {
            if (block.getBlockData() instanceof Waterlogged waterlogged
                    && waterlogged.isWaterlogged()) {
                return true;
            }
        } catch (Throwable ignored) {
            // older block data / unexpected states
        }

        return false;
    }

    /**
     * Full source-water cell only. Waterlogged slabs/stairs and flowing
     * edge water are not swim space for aquatic pets.
     */
    public static boolean isFullWaterBlock(Block block) {
        if (block == null || block.getType() != Material.WATER) {
            return false;
        }
        try {
            if (block.getBlockData() instanceof Levelled levelled
                    && levelled.getLevel() != 0) {
                return false;
            }
        } catch (Throwable ignored) {
            // keep source-looking WATER if level data is unavailable
        }
        return true;
    }

    /**
     * Slab / stair / trapdoor / waterlogged filler that shrinks a water cell
     * to a half-space — aquatic pets should skip these columns entirely.
     */
    public static boolean isPartialWaterObstacle(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        String name = type.name();
        if (name.contains("SLAB")
                || name.contains("STAIRS")
                || name.contains("TRAPDOOR")
                || name.contains("CARPET")
                || type == Material.SNOW) {
            return true;
        }
        if (type == Material.WATER) {
            return false;
        }
        try {
            if (block.getBlockData() instanceof Waterlogged waterlogged
                    && waterlogged.isWaterlogged()) {
                return true;
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return false;
    }

    /** Swim cell: full water at feet, no half-block clutter in feet/head. */
    public static boolean isClearFullWaterColumn(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        if (!isFullWaterBlock(feet)) {
            return false;
        }
        if (isPartialWaterObstacle(feet) || isPartialWaterObstacle(head)) {
            return false;
        }
        return true;
    }

    public static boolean isCanopy(
            Material type
    ) {

        if (type == null) {
            return false;
        }

        String name =
                type.name();

        if (name.contains("LEAVES")
                || name.contains("_LOG")
                || name.endsWith("_WOOD")
                || name.contains("HYPHAE")
                || name.contains("VINE")
                || name.endsWith("_SAPLING")
                || name.contains("AZALEA")) {

            return true;
        }

        if (name.contains("_STEM")
                && !name.contains("PUMPKIN")
                && !name.contains("MELON")
                && !name.contains("ATTACHED")) {

            return true;
        }

        return type == Material.MANGROVE_ROOTS
                || type == Material.MUDDY_MANGROVE_ROOTS
                || type == Material.BEE_NEST
                || type == Material.BEEHIVE
                || type == Material.COCOA
                || type == Material.BAMBOO
                || type == Material.BAMBOO_SAPLING
                || type == Material.MOSS_CARPET
                || type == Material.MUSHROOM_STEM
                || type == Material.BROWN_MUSHROOM_BLOCK
                || type == Material.RED_MUSHROOM_BLOCK
                || type == Material.CACTUS;
    }
}
