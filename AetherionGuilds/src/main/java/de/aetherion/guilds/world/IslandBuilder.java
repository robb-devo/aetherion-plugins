package de.aetherion.guilds.world;

import de.aetherion.guilds.model.IslandBiome;
import de.aetherion.guilds.model.IslandTiers;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class IslandBuilder {

    private static final int CLEAR_RADIUS = 80;
    private static final int Y = 64;

    private IslandBuilder() {
    }

    public static Location build(World world, int originX, int originZ) {
        return build(world, originX, originZ, 1, IslandBiome.PLAINS);
    }

    public static Location build(World world, int originX, int originZ, int level) {
        return build(world, originX, originZ, level, IslandBiome.PLAINS);
    }

    public static Location build(World world, int originX, int originZ, int level, IslandBiome biome) {
        IslandBiome look = biome == null ? IslandBiome.PLAINS : biome;
        applyPlatform(world, originX, originZ, level, 0, true, look);
        placeStarter(world, originX, originZ);
        placeDecor(world, originX, originZ, level, true, look);
        placeBiomeAccents(world, originX, originZ, level, look, true);
        return new Location(world, originX + 0.5, Y + 1, originZ - 3.5, 0, 0);
    }

    public static void upgrade(World world, int originX, int originZ, int fromLevel, int toLevel) {
        upgrade(world, originX, originZ, fromLevel, toLevel, IslandBiome.PLAINS);
    }

    public static void upgrade(World world, int originX, int originZ, int fromLevel, int toLevel, IslandBiome biome) {
        IslandBiome look = biome == null ? IslandBiome.PLAINS : biome;
        applyPlatform(world, originX, originZ, toLevel, IslandTiers.platformRadius(fromLevel), false, look);
        placeDecor(world, originX, originZ, toLevel, false, look);
        placeBiomeAccents(world, originX, originZ, toLevel, look, false);
    }

    public static void clear(World world, int originX, int originZ) {
        if (world == null) {
            return;
        }
        int minCx = (originX - CLEAR_RADIUS) >> 4;
        int maxCx = (originX + CLEAR_RADIUS) >> 4;
        int minCz = (originZ - CLEAR_RADIUS) >> 4;
        int maxCz = (originZ + CLEAR_RADIUS) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.isChunkGenerated(cx, cz)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(cx, cz);
                for (Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof Player)) {
                        entity.remove();
                    }
                }
                int minX = Math.max(originX - CLEAR_RADIUS, cx << 4);
                int maxX = Math.min(originX + CLEAR_RADIUS, (cx << 4) + 15);
                int minZ = Math.max(originZ - CLEAR_RADIUS, cz << 4);
                int maxZ = Math.min(originZ + CLEAR_RADIUS, (cz << 4) + 15);
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int y = 1; y <= 96; y++) {
                            Block block = chunk.getBlock(x & 15, y, z & 15);
                            if (!block.getType().isAir()) {
                                block.setType(Material.AIR, false);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void applyPlatform(
            World world,
            int originX,
            int originZ,
            int level,
            int keepRadius,
            boolean force,
            IslandBiome biome
    ) {
        int r = IslandTiers.platformRadius(level);
        Material edge = edgeMaterial(level, biome);
        Material surface = surfaceMaterial(biome);
        Material fill = fillMaterial(biome);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                if (ring <= keepRadius) {
                    continue;
                }
                int ax = originX + x;
                int az = originZ + z;
                boolean border = Math.abs(x) == r || Math.abs(z) == r;
                boolean corner = Math.abs(x) == r && Math.abs(z) == r;
                setIfAllowed(world.getBlockAt(ax, Y - 1, az), border ? edge : fill, force);
                setIfAllowed(world.getBlockAt(ax, Y, az), border ? edge : surface, force);
                if (corner) {
                    Block post = world.getBlockAt(ax, Y + 1, az);
                    if (force || replaceable(post)) {
                        fill(world, ax, Y + 1, az, ax, Y + 3, az, postLog(biome));
                        world.getBlockAt(ax, Y + 4, az).setType(lantern(level), false);
                    }
                }
            }
        }
        setIfAllowed(world.getBlockAt(originX, Y, originZ), Material.END_PORTAL_FRAME, force);
    }

    private static void placeStarter(World world, int originX, int originZ) {
        setIfAllowed(world.getBlockAt(originX + 2, Y + 1, originZ), Material.CHEST, true);
        setIfAllowed(world.getBlockAt(originX - 2, Y + 1, originZ), Material.CRAFTING_TABLE, true);
        setIfAllowed(world.getBlockAt(originX, Y + 1, originZ + 2), Material.FURNACE, true);
    }

    private static void placeDecor(
            World world,
            int originX,
            int originZ,
            int level,
            boolean force,
            IslandBiome biome
    ) {
        if (level >= 2) {
            Block fence = world.getBlockAt(originX, Y + 1, originZ - 6);
            Block banner = world.getBlockAt(originX, Y + 2, originZ - 6);
            if (force || canDecor(fence)) {
                fence.setType(fenceMaterial(biome), false);
            }
            if (force || canDecor(banner)) {
                banner.setType(bannerMaterial(biome), false);
                if (banner.getBlockData() instanceof Directional directional) {
                    directional.setFacing(BlockFace.SOUTH);
                    banner.setBlockData(directional, false);
                }
            }
        }
        if (level >= 3) {
            for (int[] offset : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                Block lamp = world.getBlockAt(originX + offset[0], Y - 1, originZ + offset[1]);
                setDecor(lamp, Material.GLOWSTONE, force);
            }
        }
        if (level >= 4) {
            int bx = originX;
            int bz = originZ + 6;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    setDecor(world.getBlockAt(bx + x, Y - 1, bz + z), Material.IRON_BLOCK, force);
                    setDecor(world.getBlockAt(bx + x, Y, bz + z), Material.IRON_BLOCK, force);
                }
            }
            setDecor(world.getBlockAt(bx, Y + 1, bz), Material.BEACON, force);
        }
        if (level >= 5) {
            setDecor(world.getBlockAt(originX, Y + 2, originZ + 6), glassMaterial(biome), force);
            for (int[] offset : new int[][]{{3, 3}, {3, -3}, {-3, 3}, {-3, -3}}) {
                setDecor(world.getBlockAt(originX + offset[0], Y, originZ + offset[1]), Material.GOLD_BLOCK, force);
                setDecor(world.getBlockAt(originX + offset[0], Y + 1, originZ + offset[1]), Material.END_ROD, force);
            }
        }
    }

    private static void placeBiomeAccents(
            World world,
            int originX,
            int originZ,
            int level,
            IslandBiome biome,
            boolean force
    ) {
        int[][] spots = {{4, 2}, {-4, 2}, {3, -3}, {-3, -3}, {1, 4}, {-1, 4}};
        for (int[] offset : spots) {
            int ax = originX + offset[0];
            int az = originZ + offset[1];
            if (Math.max(Math.abs(offset[0]), Math.abs(offset[1])) > IslandTiers.platformRadius(level) - 1) {
                continue;
            }
            switch (biome) {
                case FOREST -> {
                    setDecor(world.getBlockAt(ax, Y + 1, az), Material.FERN, force);
                    if (force && Math.abs(offset[0] + offset[1]) % 3 == 0) {
                        setDecor(world.getBlockAt(ax, Y + 1, az), Material.SPRUCE_SAPLING, true);
                    }
                }
                case DESERT -> {
                    setDecor(world.getBlockAt(ax, Y + 1, az), Material.DEAD_BUSH, force);
                    if (force && Math.abs(offset[0]) == 4) {
                        setDecor(world.getBlockAt(ax, Y + 1, az), Material.CACTUS, true);
                        setDecor(world.getBlockAt(ax, Y + 2, az), Material.CACTUS, true);
                    }
                }
                default -> setDecor(world.getBlockAt(ax, Y + 1, az), Material.DANDELION, force);
            }
        }
    }

    private static Material surfaceMaterial(IslandBiome biome) {
        return switch (biome) {
            case FOREST -> Material.PODZOL;
            case DESERT -> Material.SAND;
            default -> Material.GRASS_BLOCK;
        };
    }

    private static Material fillMaterial(IslandBiome biome) {
        return switch (biome) {
            case FOREST -> Material.DIRT;
            case DESERT -> Material.SANDSTONE;
            default -> Material.DIRT;
        };
    }

    private static Material postLog(IslandBiome biome) {
        return switch (biome) {
            case FOREST -> Material.SPRUCE_LOG;
            case DESERT -> Material.SMOOTH_SANDSTONE;
            default -> Material.OAK_LOG;
        };
    }

    private static Material fenceMaterial(IslandBiome biome) {
        return switch (biome) {
            case FOREST -> Material.SPRUCE_FENCE;
            case DESERT -> Material.SANDSTONE_WALL;
            default -> Material.OAK_FENCE;
        };
    }

    private static Material bannerMaterial(IslandBiome biome) {
        return switch (biome) {
            case FOREST -> Material.GREEN_BANNER;
            case DESERT -> Material.ORANGE_BANNER;
            default -> Material.YELLOW_BANNER;
        };
    }

    private static Material glassMaterial(IslandBiome biome) {
        return switch (biome) {
            case FOREST -> Material.LIME_STAINED_GLASS;
            case DESERT -> Material.ORANGE_STAINED_GLASS;
            default -> Material.YELLOW_STAINED_GLASS;
        };
    }

    private static Material edgeMaterial(int level, IslandBiome biome) {
        if (biome == IslandBiome.DESERT) {
            return switch (IslandTiers.clamp(level)) {
                case 2 -> Material.CUT_SANDSTONE;
                case 3 -> Material.SMOOTH_SANDSTONE;
                case 4 -> Material.RED_SANDSTONE;
                case 5 -> Material.SMOOTH_RED_SANDSTONE;
                default -> Material.SANDSTONE;
            };
        }
        if (biome == IslandBiome.FOREST) {
            return switch (IslandTiers.clamp(level)) {
                case 2 -> Material.MOSSY_COBBLESTONE;
                case 3 -> Material.MOSSY_STONE_BRICKS;
                case 4 -> Material.DEEPSLATE_BRICKS;
                case 5 -> Material.POLISHED_DEEPSLATE;
                default -> Material.COBBLESTONE;
            };
        }
        return switch (IslandTiers.clamp(level)) {
            case 2 -> Material.POLISHED_ANDESITE;
            case 3 -> Material.STONE_BRICKS;
            case 4 -> Material.QUARTZ_BLOCK;
            case 5 -> Material.SMOOTH_QUARTZ;
            default -> Material.STONE_BRICKS;
        };
    }

    private static Material lantern(int level) {
        return level >= 4 ? Material.SOUL_LANTERN : Material.LANTERN;
    }

    private static void setIfAllowed(Block block, Material material, boolean force) {
        if (force || replaceable(block)) {
            block.setType(material, false);
        }
    }

    private static void setDecor(Block block, Material material, boolean force) {
        if (force || canDecor(block)) {
            block.setType(material, false);
        }
    }

    private static boolean canDecor(Block block) {
        Material type = block.getType();
        return replaceable(block)
                || type == Material.GRASS_BLOCK
                || type == Material.DIRT
                || type == Material.PODZOL
                || type == Material.SAND
                || type == Material.SANDSTONE
                || type == Material.STONE_BRICKS
                || type == Material.POLISHED_ANDESITE
                || type == Material.QUARTZ_BLOCK
                || type == Material.SMOOTH_QUARTZ
                || type == Material.IRON_BLOCK
                || type == Material.MOSSY_COBBLESTONE
                || type == Material.COBBLESTONE;
    }

    private static boolean replaceable(Block block) {
        Material type = block.getType();
        return type.isAir()
                || type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS
                || type == Material.SNOW
                || type == Material.FERN
                || type == Material.DEAD_BUSH
                || type == Material.DANDELION
                || type == Material.CACTUS
                || type == Material.SPRUCE_SAPLING;
    }

    private static void fill(World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }
}
