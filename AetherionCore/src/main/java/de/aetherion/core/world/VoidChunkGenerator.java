package de.aetherion.core.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/**
 * Empty-chunk generator shared by test arena, guild/farm islands, and dungeon instances.
 * Default spawn matches the historical Items/Guilds/Farming copy ({@code 0.5, 65, 0.5}).
 * Dungeon worlds keep {@link #forDungeons()} ({@code z = 3.5}).
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final float yaw;
    private final float pitch;

    public VoidChunkGenerator() {
        this(0.5, 65.0, 0.5, 0f, 0f);
    }

    public VoidChunkGenerator(double spawnX, double spawnY, double spawnZ) {
        this(spawnX, spawnY, spawnZ, 0f, 0f);
    }

    public VoidChunkGenerator(double spawnX, double spawnY, double spawnZ, float yaw, float pitch) {
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** Spawn used by dungeon instance worlds (legacy AetherionDungeons copy). */
    public static VoidChunkGenerator forDungeons() {
        return new VoidChunkGenerator(0.5, 65.0, 3.5, 0f, 0f);
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

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, spawnX, spawnY, spawnZ, yaw, pitch);
    }
}
