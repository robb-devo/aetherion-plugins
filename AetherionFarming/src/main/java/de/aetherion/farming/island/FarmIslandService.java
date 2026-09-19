package de.aetherion.farming.island;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffectSet;

import de.aetherion.core.world.VoidChunkGenerator;
import de.aetherion.farming.AetherionFarming;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Shared communal farm island in a void world.
 */
public final class FarmIslandService {

    public static final String DEFAULT_WORLD = "aether_farm_island";
    public static final String RESOURCE_PATH = "structures/tiny-farming-island.schem";
    private static final int PASTE_Y = 64;

    private final AetherionFarming plugin;

    public FarmIslandService(AetherionFarming plugin) {
        this.plugin = plugin;
    }

    public String worldName() {
        return plugin.getConfig().getString("farm-island.world", DEFAULT_WORLD);
    }

    public World ensureWorld() {
        String name = worldName();
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }
        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidChunkGenerator());
        creator.environment(World.Environment.NORMAL);
        World created = creator.createWorld();
        if (created != null) {
            created.setSpawnLocation(0, PASTE_Y + 1, 0);
            created.setKeepSpawnInMemory(true);
            created.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            created.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            created.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            created.setTime(6000L);
            plugin.getLogger().info("Created farm island world: " + name);
        }
        return created;
    }

    public boolean worldEditPresent() {
        Plugin we = Bukkit.getPluginManager().getPlugin("WorldEdit");
        return we != null && we.isEnabled();
    }

    public void ensureSchematicOnDisk() {
        File out = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (out.isFile()) {
            return;
        }
        File dir = out.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create " + dir.getAbsolutePath());
        }
        try {
            plugin.saveResource(RESOURCE_PATH, false);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Farm island schematic missing from jar: " + RESOURCE_PATH);
        }
    }

    public boolean isPasted() {
        return plugin.getConfig().getBoolean("farm-island.pasted", false);
    }

    /**
     * Load world and paste schematic once (or force rebuild).
     *
     * @return human status message
     */
    public String ensureIsland(boolean forceRebuild) {
        if (!plugin.getConfig().getBoolean("farm-island.enabled", true)) {
            return "§cFarm island is disabled in config.";
        }
        if (!worldEditPresent()) {
            return "§cWorldEdit is required to paste the farm island.";
        }
        World world = ensureWorld();
        if (world == null) {
            return "§cCould not create farm island world.";
        }
        if (isPasted() && !forceRebuild) {
            return "§aFarm island ready (§f" + world.getName() + "§a).";
        }
        try {
            if (forceRebuild) {
                plugin.getConfig().set("farm-island.ambience-ready", false);
            }
            PasteBounds bounds = paste(world);
            Location exit = findPortalCenter(world, bounds);
            if (exit == null) {
                exit = new Location(world, 0.5, PASTE_Y + 2, 0.5, 0f, 0f);
            }
            plugin.getConfig().set("farm-island.pasted", true);
            plugin.getConfig().set("farm-island.island-exit.world", world.getName());
            plugin.getConfig().set("farm-island.island-exit.x", exit.getX());
            plugin.getConfig().set("farm-island.island-exit.y", exit.getY());
            plugin.getConfig().set("farm-island.island-exit.z", exit.getZ());
            plugin.getConfig().set("farm-island.island-exit.yaw", exit.getYaw());
            plugin.getConfig().set("farm-island.island-exit.pitch", exit.getPitch());
            plugin.saveConfig();
            world.setSpawnLocation(exit.getBlockX(), exit.getBlockY(), exit.getBlockZ());
            if (plugin.ambience() != null) {
                plugin.ambience().setup(
                        world,
                        bounds.minX(), bounds.maxX(),
                        bounds.minY(), bounds.maxY(),
                        bounds.minZ(), bounds.maxZ()
                );
            }
            return "§aFarm island pasted into §f" + world.getName()
                    + "§a. Exit near " + exit.getBlockX() + " " + exit.getBlockY() + " " + exit.getBlockZ();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Farm island paste failed", exception);
            return "§cPaste failed: " + exception.getMessage();
        }
    }

    private PasteBounds paste(World world) {
        ensureSchematicOnDisk();
        File file = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!file.isFile()) {
            throw new IllegalStateException("Schematic not found: " + file.getAbsolutePath());
        }
        Clipboard clipboard;
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IllegalStateException("Unknown schematic format: " + file.getName());
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read schematic: " + exception.getMessage(), exception);
        }

        BlockVector3 dims = clipboard.getDimensions();
        BlockVector3 pasteOrigin = BlockVector3.at(0, PASTE_Y, 0);
        long started = System.currentTimeMillis();
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .maxBlocks(-1)
                .build()) {
            try {
                editSession.setSideEffectApplier(SideEffectSet.none());
            } catch (Throwable ignored) {
            }
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(pasteOrigin)
                    .ignoreAirBlocks(true)
                    .copyEntities(false)
                    .copyBiomes(false)
                    .build();
            Operations.complete(operation);
        } catch (Exception exception) {
            throw new IllegalStateException("Paste failed: " + exception.getMessage(), exception);
        }
        plugin.getLogger().info("Farm island paste done in " + (System.currentTimeMillis() - started) + "ms ("
                + dims.x() + "x" + dims.y() + "x" + dims.z() + ")");

        BlockVector3 offset = pasteOrigin.subtract(clipboard.getOrigin());
        BlockVector3 worldMin = clipboard.getMinimumPoint().add(offset);
        BlockVector3 worldMax = clipboard.getMaximumPoint().add(offset);
        return new PasteBounds(
                worldMin.x(), worldMax.x(),
                worldMin.y(), worldMax.y(),
                worldMin.z(), worldMax.z()
        );
    }

    private Location findPortalCenter(World world, PasteBounds bounds) {
        int minX = Math.min(bounds.minX, bounds.maxX);
        int maxX = Math.max(bounds.minX, bounds.maxX);
        int minY = Math.min(bounds.minY, bounds.maxY);
        int maxY = Math.max(bounds.minY, bounds.maxY);
        int minZ = Math.min(bounds.minZ, bounds.maxZ);
        int maxZ = Math.max(bounds.minZ, bounds.maxZ);

        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.NETHER_PORTAL) {
                        sumX += x;
                        sumY += y;
                        sumZ += z;
                        count++;
                    }
                }
            }
        }
        if (count == 0) {
            return null;
        }
        double cx = (sumX / (double) count) + 0.5;
        double cy = (sumY / (double) count);
        double cz = (sumZ / (double) count) + 0.5;
        // Stand just outside portal so return doesn't instantly re-enter.
        return new Location(world, cx, cy, cz + 1.5, 180f, 0f);
    }

    private record PasteBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }
}
