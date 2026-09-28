package de.aetherion.bossengine.helios.world;

import de.aetherion.bossengine.helios.HeliosConfig;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * The encounter's own void world. Overworld sky (so per-player time works), but every chunk is
 * {@code the_void}: no precipitation, so per-player rain only fades the sun, moon and stars.
 * Nothing in any other world is ever edited by Helios.
 */
public final class HeliosWorld {

    private HeliosWorld() {
    }

    public static World ensure(HeliosConfig config, Logger log) {
        String name = config.worldName();
        World world = Bukkit.getWorld(name);
        if (world == null) {
            WorldCreator creator = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .generator(new Void())
                    .biomeProvider(new VoidBiomes())
                    .generateStructures(false);
            world = creator.createWorld();
            if (world == null) {
                log.severe("[Helios] Could not create world '" + name + "'.");
                return null;
            }
            log.info("[Helios] Created encounter world '" + name + "'.");
        }
        configure(world, config);
        return world;
    }

    public static boolean isHelios(World world, HeliosConfig config) {
        return world != null && world.getName().equalsIgnoreCase(config.worldName());
    }

    private static void configure(World w, HeliosConfig config) {
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRule.KEEP_INVENTORY, config.keepInventory());
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        w.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 0);
        w.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        w.setGameRule(GameRule.DO_TILE_DROPS, false);
        w.setDifficulty(Difficulty.HARD);
        w.setStorm(false);
        w.setThundering(false);
        w.setTime(13_000L);
        w.setSpawnFlags(false, false);
        w.setSpawnLocation(new Location(w, 0.5, config.arenaY() + 1, 0.5));
    }

    /** Empty chunks. */
    public static final class Void extends ChunkGenerator {

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
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 161, 0.5);
        }
    }

    /** Every chunk is the_void: no rain, no snow, no ambient mobs. */
    public static final class VoidBiomes extends BiomeProvider {

        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return Biome.THE_VOID;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(Biome.THE_VOID);
        }
    }
}
