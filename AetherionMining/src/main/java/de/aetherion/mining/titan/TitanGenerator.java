package de.aetherion.mining.titan;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Builds the Swallowed Isles from {@link TitanShape}: every block of a column first, then what grows on the
 * surfaces (dripstone, glow vines, sea pickles, snow, marrow strands) and invisible light in the big halls.
 * Nothing vanilla runs: no noise, caves, ores, structures or mobs.
 */
final class TitanGenerator extends ChunkGenerator {

    private static final int H = TitanShape.TOP + 1;

    private final TitanShape shape;
    private final TitanShape.Palette p;

    TitanGenerator(TitanShape shape) {
        this.shape = shape;
        this.p = shape.p;
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        if (baseX + 15 < TitanShape.MIN_X || baseX > TitanShape.MAX_X || baseZ + 15 < TitanShape.MIN_Z
                || baseZ > TitanShape.MAX_Z) {
            return;
        }
        BlockData[] column = new BlockData[H];
        int[] codes = new int[H];
        int[] code = new int[2];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = baseX + lx;
                int z = baseZ + lz;
                if (x < TitanShape.MIN_X || x >= TitanShape.MAX_X || z < TitanShape.MIN_Z || z >= TitanShape.MAX_Z) {
                    continue;
                }
                for (int y = 0; y < H; y++) {
                    column[y] = shape.at(x, y, z, code);
                    codes[y] = code[0];
                }
                decorate(x, z, column, codes);
                for (int y = 0; y < H; y++) {
                    BlockData block = column[y];
                    if (block != p.cave) {
                        data.setBlock(lx, y, lz, block);
                    }
                }
            }
        }
    }

    private boolean air(BlockData block) {
        return block == p.cave;
    }

    private boolean solid(BlockData block) {
        return block != p.cave && block != p.water && block != p.lava && block != p.light12 && block != p.light10
                && block != p.rail && block != p.ladderWest && block != p.lantern;
    }

    /** Surface growth and light, all inside one column so chunks never wait on their neighbours. */
    private void decorate(int x, int z, BlockData[] col, int[] codes) {
        for (int y = 2; y < H - 2; y++) {
            if (!air(col[y])) {
                if (codes[y] == TitanShape.WATER && solid(col[y - 1]) && shape.hash(x, y, z, 41) < 0.035) {
                    col[y] = p.pickles[(int) (shape.hash(x, y, z, 42) * 3)];
                }
                continue;
            }
            int code = codes[y];
            boolean ceiling = solid(col[y + 1]);
            boolean ground = solid(col[y - 1]);
            switch (code) {
                case TitanShape.AIR_RIB -> {
                    if (ceiling && shape.hash(x, y, z, 51) < 0.05) {
                        hang(col, y, 1 + (int) (shape.hash(x, y, z, 52) * 5), p.dripDown);
                    } else if (ground && shape.hash(x, y, z, 53) < 0.012) {
                        stand(col, y, 1 + (int) (shape.hash(x, y, z, 54) * 3), p.dripUp);
                    }
                }
                case TitanShape.AIR_SEA -> {
                    if (ceiling && shape.hash(x, y, z, 61) < 0.07) {
                        vines(col, y, 3 + (int) (shape.hash(x, y, z, 62) * 8), x, z);
                    }
                }
                case TitanShape.AIR_LUNG -> {
                    if (ground && shape.hash(x, y, z, 71) < 0.35) {
                        col[y] = p.snowLayer;
                    }
                }
                case TitanShape.AIR_MARROW -> {
                    if (ceiling) {
                        double h = shape.hash(x, y, z, 81);
                        if (h < 0.03) {
                            col[y + 1] = p.shroomlight;
                        } else if (h < 0.08) {
                            weeping(col, y, 2 + (int) (shape.hash(x, y, z, 82) * 4));
                        }
                    } else if (ground) {
                        double h = shape.hash(x, y, z, 83);
                        if (h < 0.25 && (col[y - 1] == p.netherrack || col[y - 1] == p.blackstone)) {
                            col[y - 1] = p.magma;
                        } else if (h < 0.3 && col[y - 1] == p.netherrack) {
                            col[y] = p.crimsonRoots;
                        }
                    }
                }
                default -> {
                }
            }
        }
        // Invisible light so the halls read from across the room.
        for (int y = 2; y < H - 2; y++) {
            if (!air(col[y])) {
                continue;
            }
            switch (codes[y]) {
                case TitanShape.AIR_RIB, TitanShape.AIR_SEA -> {
                    if ((x & 7) == 0 && (z & 7) == 0 && y % 7 == 0) {
                        col[y] = p.light12;
                    }
                }
                case TitanShape.AIR_SPINE -> {
                    if ((x & 7) == 0 && z == 0 && y == 187) {
                        col[y] = p.light10;
                    }
                }
                case TitanShape.AIR_LUNG, TitanShape.AIR_MARROW, TitanShape.AIR_OTHER -> {
                    if ((x & 15) == 0 && (z & 15) == 0 && y % 8 == 0) {
                        col[y] = p.light10;
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Stalactite of up to {@code length} blocks hanging from the ceiling above {@code y}. */
    private void hang(BlockData[] col, int y, int length, BlockData[] drip) {
        int n = 0;
        while (n < length && y - n > 2 && air(col[y - n])) {
            n++;
        }
        place(col, y, n, -1, drip);
    }

    private void stand(BlockData[] col, int y, int length, BlockData[] drip) {
        int n = 0;
        while (n < length && y + n < H - 2 && air(col[y + n])) {
            n++;
        }
        place(col, y, n, 1, drip);
    }

    /** Thickness from the root to the tip: base, middle, frustum, tip. */
    private void place(BlockData[] col, int start, int n, int step, BlockData[] drip) {
        for (int i = 0; i < n; i++) {
            int fromTip = n - 1 - i;
            BlockData state = fromTip == 0 ? drip[0] : fromTip == 1 ? drip[1] : i == 0 ? drip[3] : drip[2];
            col[start + i * step] = state;
        }
    }

    private void vines(BlockData[] col, int y, int length, int x, int z) {
        int n = 0;
        while (n < length && y - n > 2 && air(col[y - n])) {
            n++;
        }
        for (int i = 0; i < n; i++) {
            boolean tip = i == n - 1;
            col[y - i] = tip ? p.glowVine : (shape.hash(x, y - i, z, 63) < 0.3 ? p.vinePlantLit : p.vinePlant);
        }
    }

    private void weeping(BlockData[] col, int y, int length) {
        int n = 0;
        while (n < length && y - n > 2 && air(col[y - n])) {
            n++;
        }
        for (int i = 0; i < n; i++) {
            col[y - i] = i == n - 1 ? p.weeping : p.weepingPlant;
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return switch (shape.zoneAt(x, y, z)) {
                    case HEART -> Biome.CRIMSON_FOREST;
                    case LUNGS -> Biome.SNOWY_SLOPES;
                    case SEA -> Biome.MANGROVE_SWAMP;
                    case MARROW -> Biome.NETHER_WASTES;
                    default -> Biome.DRIPSTONE_CAVES;
                };
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.CRIMSON_FOREST, Biome.SNOWY_SLOPES, Biome.MANGROVE_SWAMP, Biome.NETHER_WASTES,
                        Biome.DRIPSTONE_CAVES);
            }
        };
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        double[] a = shape.arrival();
        return new Location(world, a[0], a[1], a[2], -90f, 0f);
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
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
}
