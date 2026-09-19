package de.aetherion.mining.veins;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Bounded stone cube, packed with ore, classic mineshafts, sparse caves, one style per corner.
 */
public final class VeinsChunkGenerator extends ChunkGenerator {

    static final int HUB = 10;
    private static final int GRID = 28;

    private final int radius;
    private final int hubY;

    public VeinsChunkGenerator(int radius, int hubY) {
        this.radius = Math.max(32, radius);
        this.hubY = hubY;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new VeinsBiomes();
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        paint(info, chunkX, chunkZ, data);
    }

    @Override
    public void generateSurface(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        paint(info, chunkX, chunkZ, data);
    }

    @Override
    public org.bukkit.Location getFixedSpawnLocation(World world, Random random) {
        return new org.bukkit.Location(world, 0.5, hubY + 1, 0.5, 0f, 0f);
    }

    private void paint(WorldInfo info, int chunkX, int chunkZ, ChunkData data) {
        int minY = data.getMinHeight();
        int maxY = data.getMaxHeight();
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        int inner = radius - 1;
        long seed = info.getSeed();
        Random rng = new Random(seed ^ ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L));

        boolean anyInside = false;
        for (int x = 0; x < 16 && !anyInside; x++) {
            for (int z = 0; z < 16 && !anyInside; z++) {
                if (inside(originX + x, originZ + z)) {
                    anyInside = true;
                }
            }
        }
        if (!anyInside) {
            data.setRegion(0, minY, 0, 16, maxY, 16, Material.AIR);
            return;
        }

        data.setRegion(0, minY, 0, 16, minY + 1, 16, Material.BEDROCK);
        data.setRegion(0, maxY - 1, 0, 16, maxY, 16, Material.BEDROCK);
        data.setRegion(0, minY + 1, 0, 16, 0, 16, Material.DEEPSLATE);
        data.setRegion(0, 0, 0, 16, maxY - 1, 16, Material.STONE);

        for (int x = 0; x < 16; x++) {
            int wx = originX + x;
            for (int z = 0; z < 16; z++) {
                int wz = originZ + z;
                if (!inside(wx, wz)) {
                    data.setRegion(x, minY, z, x + 1, maxY, z + 1, Material.AIR);
                    continue;
                }
                if (Math.abs(wx) >= inner || Math.abs(wz) >= inner) {
                    data.setRegion(x, minY, z, x + 1, maxY, z + 1, Material.BEDROCK);
                    continue;
                }
                VeinStyle style = style(wx, wz);
                for (int y = minY + 1; y < maxY - 1; y++) {
                    if (hubAir(wx, y, wz) || hubDescent(wx, y, wz)) {
                        data.setBlock(x, y, z, Material.AIR);
                        continue;
                    }
                    Shaft shaft = shaft(wx, y, wz);
                    if (shaft != Shaft.NONE) {
                        data.setBlock(x, y, z, shaftBlock(shaft, wx, y, wz, rng));
                        continue;
                    }
                    if (cave(wx, y, wz, seed)) {
                        data.setBlock(x, y, z, caveBlock(wx, y, wz, seed, style, rng));
                        continue;
                    }
                    data.setBlock(x, y, z, oreOrBase(style, y, rng));
                }
            }
        }
    }

    private boolean inside(int x, int z) {
        return Math.abs(x) <= radius && Math.abs(z) <= radius;
    }

    private boolean hubAir(int x, int y, int z) {
        return Math.abs(x) <= HUB && Math.abs(z) <= HUB && y >= hubY - 1 && y <= hubY + 7;
    }

    private boolean hubDescent(int x, int y, int z) {
        int road = hubY - 4;
        if (y < road || y > hubY + 4) {
            return false;
        }
        if (Math.abs(x) <= 1 && z >= HUB - 6 && z <= HUB) {
            return onStep(y, z - (HUB - 6), road);
        }
        if (Math.abs(x) <= 1 && z <= -(HUB - 6) && z >= -HUB) {
            return onStep(y, -(HUB - 6) - z, road);
        }
        if (Math.abs(z) <= 1 && x >= HUB - 6 && x <= HUB) {
            return onStep(y, x - (HUB - 6), road);
        }
        if (Math.abs(z) <= 1 && x <= -(HUB - 6) && x >= -HUB) {
            return onStep(y, -(HUB - 6) - x, road);
        }
        return false;
    }

    private boolean onStep(int y, int step, int road) {
        int dropped = Math.max(0, step - 2);
        int stairY = Math.max(road, hubY - dropped);
        return y >= stairY && y <= stairY + 4;
    }

    private Shaft shaft(int x, int y, int z) {
        if (Math.abs(x) <= HUB && Math.abs(z) <= HUB && y >= hubY - 2) {
            return Shaft.NONE;
        }
        int road = hubY - 4;
        if (y >= road && y <= road + 3 && (Math.abs(x) <= 1 || Math.abs(z) <= 1)) {
            if (y == road) {
                return Shaft.FLOOR;
            }
            if (y == road + 3) {
                return Shaft.CEILING;
            }
            return Shaft.AIR;
        }
        Band band = gridBand(x, z);
        if (band == Band.NONE) {
            return Shaft.NONE;
        }
        int[] levels = {hubY - 18, 96, 64, 32, 4, -16, -40};
        for (int level : levels) {
            if (y >= level && y <= level + 3) {
                if (y == level) {
                    return Shaft.FLOOR;
                }
                if (y == level + 3) {
                    return Shaft.CEILING;
                }
                return Shaft.AIR;
            }
        }
        return Shaft.NONE;
    }

    private Material shaftBlock(Shaft shaft, int x, int y, int z, Random rng) {
        boolean hubRoad = y >= hubY - 4 && y <= hubY - 1 && (Math.abs(x) <= 1 || Math.abs(z) <= 1);
        Band band = hubRoad ? hubBand(x, z) : gridBand(x, z);
        boolean side = side(band, x, z, hubRoad);
        boolean support = support(band, x, z, hubRoad);
        if (shaft == Shaft.FLOOR) {
            if (!side && Math.floorMod(x + z, 12) == 0) {
                return Material.RAIL;
            }
            return Material.OAK_PLANKS;
        }
        if (shaft == Shaft.CEILING) {
            if (!support) {
                return Material.AIR;
            }
            if (!side && Math.floorMod(x + z, 10) == 0) {
                return Material.LANTERN;
            }
            return Material.OAK_PLANKS;
        }
        if (side && support) {
            return Material.OAK_FENCE;
        }
        if (side && rng.nextInt(18) == 0) {
            return Material.COBWEB;
        }
        return Material.AIR;
    }

    private static Band hubBand(int x, int z) {
        boolean ns = Math.abs(x) <= 1;
        boolean ew = Math.abs(z) <= 1;
        if (ns && ew) {
            return Band.CROSS;
        }
        if (ns) {
            return Band.NS;
        }
        if (ew) {
            return Band.EW;
        }
        return Band.NONE;
    }

    private static Band gridBand(int x, int z) {
        boolean ns = Math.floorMod(x, GRID) <= 2;
        boolean ew = Math.floorMod(z, GRID) <= 2;
        if (ns && ew) {
            return Band.CROSS;
        }
        if (ns) {
            return Band.NS;
        }
        if (ew) {
            return Band.EW;
        }
        return Band.NONE;
    }

    private static boolean side(Band band, int x, int z, boolean hub) {
        if (band == Band.NONE) {
            return false;
        }
        if (hub) {
            if (band == Band.NS) {
                return Math.abs(x) == 1;
            }
            if (band == Band.EW) {
                return Math.abs(z) == 1;
            }
            return Math.abs(x) == 1 || Math.abs(z) == 1;
        }
        int lx = Math.floorMod(x, GRID);
        int lz = Math.floorMod(z, GRID);
        if (band == Band.NS) {
            return lx == 0 || lx == 2;
        }
        if (band == Band.EW) {
            return lz == 0 || lz == 2;
        }
        return (lx == 0 || lx == 2) && (lz == 0 || lz == 2);
    }

    private static boolean support(Band band, int x, int z, boolean hub) {
        if (band == Band.NONE) {
            return false;
        }
        if (hub) {
            if (band == Band.NS) {
                return Math.floorMod(z, 5) == 0;
            }
            if (band == Band.EW) {
                return Math.floorMod(x, 5) == 0;
            }
            return Math.floorMod(x, 5) == 0 || Math.floorMod(z, 5) == 0;
        }
        if (band == Band.NS) {
            return Math.floorMod(z, 5) == 0;
        }
        if (band == Band.EW) {
            return Math.floorMod(x, 5) == 0;
        }
        return Math.floorMod(x, 5) == 0 || Math.floorMod(z, 5) == 0;
    }

    private boolean cave(int x, int y, int z, long seed) {
        if (Math.abs(x) <= HUB + 8 && Math.abs(z) <= HUB + 8 && y >= hubY - 16) {
            return false;
        }
        if (y >= hubY - 6) {
            return false;
        }
        int cell = 42;
        int lx = Math.floorMod(x, cell);
        int lz = Math.floorMod(z, cell);
        int ly = Math.floorMod(y + 80, 26);
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        int jitter = Math.floorMod(cx * 13 + cz * 29 + (int) seed, 9) - 4;
        int dx = lx - 21 + jitter;
        int dz = lz - 21 - jitter;
        int dy = ly - 11;
        if (dx * dx + dz * dz + dy * dy * 3 < 78) {
            return true;
        }
        double w = Math.sin((x + 0.37 * seed) * 0.055)
                + Math.sin((z - 0.19 * seed) * 0.055)
                + Math.sin(y * 0.12);
        return Math.abs(w) < 0.14
                && Math.abs(Math.sin((x + z) * 0.027 + y * 0.02)) > 0.58;
    }

    private Material caveBlock(int x, int y, int z, long seed, VeinStyle style, Random rng) {
        boolean floor = !cave(x, y - 1, z, seed);
        boolean ceil = !cave(x, y + 1, z, seed);
        if (floor) {
            if (style == VeinStyle.EMERALD && rng.nextInt(3) == 0) {
                return Material.MOSS_CARPET;
            }
            if (style == VeinStyle.DIAMOND && rng.nextInt(5) == 0) {
                return Material.DRIPSTONE_BLOCK;
            }
            if (style == VeinStyle.NETHER && rng.nextInt(4) == 0) {
                return Material.SOUL_SOIL;
            }
        }
        if (!floor && ceil && style == VeinStyle.DIAMOND && rng.nextInt(7) == 0) {
            return Material.POINTED_DRIPSTONE;
        }
        if (rng.nextInt(28) == 0) {
            return Material.COBWEB;
        }
        return Material.AIR;
    }

    static VeinStyle style(int x, int z) {
        boolean east = x >= 0;
        boolean south = z >= 0;
        if (east && !south) {
            return VeinStyle.DIAMOND;
        }
        if (!east && !south) {
            return VeinStyle.EMERALD;
        }
        if (east) {
            return VeinStyle.IRON;
        }
        return VeinStyle.NETHER;
    }

    private static Material oreOrBase(VeinStyle style, int y, Random rng) {
        double roll = rng.nextDouble();
        boolean deep = y < 0;
        if (roll < 0.18) {
            return deep(featured(style), deep && style != VeinStyle.NETHER);
        }
        if (style == VeinStyle.NETHER && roll < 0.30) {
            int nether = rng.nextInt(3);
            if (nether == 0) {
                return Material.ANCIENT_DEBRIS;
            }
            return nether == 1 ? Material.NETHER_QUARTZ_ORE : Material.NETHER_GOLD_ORE;
        }
        if (roll < 0.46) {
            return deep(mix(rng), deep && style != VeinStyle.NETHER);
        }
        return filler(style, deep, rng);
    }

    private static Material featured(VeinStyle style) {
        return switch (style) {
            case DIAMOND -> Material.DIAMOND_ORE;
            case EMERALD -> Material.EMERALD_ORE;
            case IRON -> Material.IRON_ORE;
            case NETHER -> Material.ANCIENT_DEBRIS;
        };
    }

    private static Material filler(VeinStyle style, boolean deep, Random rng) {
        int pick = rng.nextInt(100);
        return switch (style) {
            case DIAMOND -> {
                if (pick < 28) {
                    yield Material.CALCITE;
                }
                if (pick < 50) {
                    yield Material.TUFF;
                }
                if (pick < 62) {
                    yield Material.DRIPSTONE_BLOCK;
                }
                if (pick < 74) {
                    yield Material.SMOOTH_BASALT;
                }
                yield deep ? Material.DEEPSLATE : Material.CALCITE;
            }
            case EMERALD -> {
                if (pick < 22) {
                    yield Material.MOSS_BLOCK;
                }
                if (pick < 40) {
                    yield Material.MOSSY_COBBLESTONE;
                }
                if (pick < 55) {
                    yield Material.CLAY;
                }
                if (pick < 68) {
                    yield Material.ROOTED_DIRT;
                }
                yield deep ? Material.DEEPSLATE : Material.STONE;
            }
            case IRON -> {
                if (pick < 22) {
                    yield Material.ANDESITE;
                }
                if (pick < 40) {
                    yield Material.GRANITE;
                }
                if (pick < 55) {
                    yield Material.DIORITE;
                }
                if (pick < 66) {
                    yield Material.COBBLESTONE;
                }
                yield deep ? Material.DEEPSLATE : Material.STONE;
            }
            case NETHER -> {
                if (pick < 40) {
                    yield Material.NETHERRACK;
                }
                if (pick < 62) {
                    yield Material.BLACKSTONE;
                }
                if (pick < 80) {
                    yield Material.BASALT;
                }
                if (pick < 90) {
                    yield Material.SOUL_SOIL;
                }
                yield Material.NETHERRACK;
            }
        };
    }

    private static Material mix(Random rng) {
        int pick = rng.nextInt(100);
        if (pick < 18) {
            return Material.COAL_ORE;
        }
        if (pick < 32) {
            return Material.IRON_ORE;
        }
        if (pick < 44) {
            return Material.COPPER_ORE;
        }
        if (pick < 54) {
            return Material.GOLD_ORE;
        }
        if (pick < 62) {
            return Material.REDSTONE_ORE;
        }
        if (pick < 69) {
            return Material.LAPIS_ORE;
        }
        if (pick < 75) {
            return Material.NETHER_QUARTZ_ORE;
        }
        if (pick < 80) {
            return Material.NETHER_GOLD_ORE;
        }
        if (pick < 87) {
            return Material.DIAMOND_ORE;
        }
        if (pick < 93) {
            return Material.EMERALD_ORE;
        }
        return Material.ANCIENT_DEBRIS;
    }

    private static Material deep(Material ore, boolean deepslate) {
        if (!deepslate) {
            return ore;
        }
        return switch (ore) {
            case COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
            case COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
            case IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
            case GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
            case REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
            case LAPIS_ORE -> Material.DEEPSLATE_LAPIS_ORE;
            case DIAMOND_ORE -> Material.DEEPSLATE_DIAMOND_ORE;
            case EMERALD_ORE -> Material.DEEPSLATE_EMERALD_ORE;
            default -> ore;
        };
    }

    private enum Shaft {
        NONE, FLOOR, AIR, CEILING
    }

    private enum Band {
        NONE, NS, EW, CROSS
    }

    enum VeinStyle {
        DIAMOND, EMERALD, IRON, NETHER
    }

    private static final class VeinsBiomes extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return switch (style(x, z)) {
                case DIAMOND -> Biome.DRIPSTONE_CAVES;
                case EMERALD -> Biome.LUSH_CAVES;
                case IRON -> Biome.STONY_PEAKS;
                case NETHER -> Biome.NETHER_WASTES;
            };
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(
                    Biome.DRIPSTONE_CAVES,
                    Biome.LUSH_CAVES,
                    Biome.STONY_PEAKS,
                    Biome.NETHER_WASTES
            );
        }
    }
}
