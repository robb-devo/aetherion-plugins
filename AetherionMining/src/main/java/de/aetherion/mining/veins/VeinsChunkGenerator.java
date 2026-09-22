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
 * Safety generator for {@code aether_veins} only if a chunk is force-regenerated.
 * Fills air — never rebuilds the discarded Deep Veins Y~220 stone-cube prototype.
 * Live dig content is painted by {@link VeinsDigZones}; the BreadBuilds hub is pasted separately.
 */
public final class VeinsChunkGenerator extends ChunkGenerator {

    private final int hubY;

    public VeinsChunkGenerator(int radius, int hubY) {
        this.hubY = hubY;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return true;
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

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new VeinsBiomes();
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        int minY = data.getMinHeight();
        int maxY = data.getMaxHeight();
        data.setRegion(0, minY, 0, 16, maxY, 16, Material.AIR);
    }

    @Override
    public org.bukkit.Location getFixedSpawnLocation(World world, Random random) {
        return new org.bukkit.Location(world, 8.5, Math.max(16, hubY), 8.5, 0f, 0f);
    }

    /** Quadrant style mirror for biomes only — dig ore mixes live in {@link VeinsDigZones}. */
    static VeinsDigZones.Style style(int x, int z) {
        return VeinsDigZones.styleAt(x, z, 8, 8);
    }

    private static final class VeinsBiomes extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return switch (style(x, z)) {
                case CRYSTAL -> Biome.DRIPSTONE_CAVES;
                case LUSH -> Biome.LUSH_CAVES;
                case FORGE -> Biome.STONY_PEAKS;
                case CINDER -> Biome.NETHER_WASTES;
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
