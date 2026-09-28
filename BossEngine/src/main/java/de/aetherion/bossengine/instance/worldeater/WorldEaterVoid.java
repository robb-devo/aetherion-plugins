package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Empty world for the World Eater: no terrain, no caves, no structures, no mobs.
 *
 * <p>The whole world is one biome, {@code the_end}, in a NORMAL (overworld) dimension. That gives
 * the arena a black sky with real stars at night, a real sun by day, and no precipitation, which
 * the encounter depends on: per-player "rain" then fades the sun, the moon and every star out of
 * the sky without a single raindrop falling (see {@link Senses}).
 *
 * <p>Multiverse: {@code /mv create world_eater normal -g BossEngine:worldeater}.
 */
public final class WorldEaterVoid extends ChunkGenerator {

    public static final String GENERATOR_ID = "worldeater";

    private static final BiomeProvider BLACK_SKY = new BiomeProvider() {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return Biome.THE_END;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(Biome.THE_END);
        }
    };

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

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return BLACK_SKY;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return SiteLayout.arrival(world);
    }
}
