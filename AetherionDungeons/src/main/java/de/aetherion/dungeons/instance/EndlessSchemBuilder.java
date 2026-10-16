package de.aetherion.dungeons.instance;

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
import com.sk89q.worldedit.world.block.BlockState;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Floor 2 (Frostbound): paste {@code dungeon1-endlessxl.schem} once into the warm void base.
 * The schematic is about 226×118×218. Spawn sits at one end; the gate and Frostbound are
 * ~140–170 blocks toward negative Z. That far half is void if the paste is not flushed
 * and saved before chunks outside the spawn radius are unloaded.
 */
public final class EndlessSchemBuilder {

    public static final String FLOOR_ID = "prototype_endless_test";
    /** Dev enter code (Dev Menu / {@code /dungeon enter endless}). */
    public static final int ENTER_CODE = 6;

    public static final String RESOURCE_PATH = "structures/endless/dungeon1-endlessxl.schem";
    public static final String FILE_NAME = "dungeon1-endlessxl.schem";

    private static final int PASTE_Y = DungeonLayout.FLOOR_Y;

    private EndlessSchemBuilder() {
    }

    public static boolean worldEditPresent() {
        Plugin we = Bukkit.getPluginManager().getPlugin("WorldEdit");
        return we != null && we.isEnabled();
    }

    public static void ensureSchematic(Plugin plugin) {
        if (plugin == null) {
            return;
        }
        File dir = new File(plugin.getDataFolder(), "structures/endless");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create structures/endless.");
        }
        File out = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!out.exists()) {
            try {
                plugin.saveResource(RESOURCE_PATH, false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Endless schematic missing from jar: " + RESOURCE_PATH);
            }
        }
    }

    public record PasteResult(
            DungeonLayout layout,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
    ) {
    }

    public record BuildResult(
            Location spawn,
            DungeonLayout layout,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
    ) {
    }

    /** Paste schematic only — used by the warm pool so enter skips the heavy paste. */
    public static PasteResult paste(Plugin plugin, World world) {
        if (plugin == null || world == null) {
            throw new IllegalArgumentException("plugin/world required");
        }
        if (!worldEditPresent()) {
            throw new IllegalStateException("WorldEdit is required to paste the endless schematic.");
        }
        ensureSchematic(plugin);
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
        int sizeX = Math.max(1, dims.x());
        int sizeY = Math.max(1, dims.y());
        int sizeZ = Math.max(1, dims.z());

        BlockVector3 pasteOrigin = BlockVector3.at(0, PASTE_Y, 0);
        BlockVector3 shift = pasteOrigin.subtract(clipboard.getOrigin());
        BlockVector3 worldMin = clipboard.getMinimumPoint().add(shift);
        BlockVector3 worldMax = clipboard.getMaximumPoint().add(shift);
        assertFitsWorldHeight(world, worldMin.y(), worldMax.y());

        int previousView = worldDistance(world, "getViewDistance", 4);
        int previousSim = worldDistance(world, "getSimulationDistance", 4);
        // Keep the whole footprint ticketed while FAWE/WorldEdit writes it.
        // Simulation distance 4 around spawn drops the Frostbound half mid-paste.
        int cover = Math.max(16, chunkSpan(worldMin, worldMax) + 2);
        setWorldDistance(world, cover, cover);
        long pasteStarted = System.currentTimeMillis();
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .maxBlocks(-1)
                .build()) {
            try {
                editSession.setSideEffectApplier(SideEffectSet.none());
            } catch (Throwable ignored) {
                // Older WE builds may differ — ignoreAirBlocks is still the big win.
            }
            try {
                editSession.setBlockChangeLimit(-1);
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
            // FAWE queues the paste. Closing without a flush cancels the far half.
            flushEditSession(editSession);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to paste endless schematic", exception);
            throw new IllegalStateException("Paste failed: " + exception.getMessage(), exception);
        } finally {
            setWorldDistance(world, previousView, previousSim);
        }
        // Write every pasted chunk before anything unloads them. Auto-save is off on
        // dungeon worlds, and unload then discards the half outside the spawn radius.
        persistExtent(world, worldMin.x(), worldMax.x(), worldMin.z(), worldMax.z());
        verifyPaste(plugin, world, clipboard, shift, worldMin, worldMax);
        plugin.getLogger().info("Floor 2 paste done in " + (System.currentTimeMillis() - pasteStarted) + "ms"
                + " (" + sizeX + "x" + sizeY + "x" + sizeZ + ", air skipped)"
                + " bounds x=" + worldMin.x() + ".." + worldMax.x()
                + " y=" + worldMin.y() + ".." + worldMax.y()
                + " z=" + worldMin.z() + ".." + worldMax.z());

        DungeonLayout layout = DungeonLayout.schemShell(
                worldMin.x(),
                worldMax.x(),
                worldMin.z(),
                worldMax.z()
        );
        return new PasteResult(
                layout,
                worldMin.x(),
                worldMax.x(),
                worldMin.y(),
                worldMax.y(),
                worldMin.z(),
                worldMax.z()
        );
    }

    /**
     * True when the corridor past the old 3-chunk park radius and the Frostbound
     * slice both still have blocks. A truncated warm base fails this and must be pasted again.
     */
    public static boolean extentPresent(World world, PasteResult paste) {
        if (world == null || paste == null) {
            return false;
        }
        int minY = Math.max(paste.minY(), world.getMinHeight());
        int maxY = Math.min(paste.maxY(), world.getMaxHeight() - 1);
        if (minY > maxY) {
            return false;
        }
        // z=-64 is the first chunk beyond the old park window (chunks -3..3 around spawn).
        return sliceHasSolid(world, 0, 12, -64, minY, maxY)
                && sliceHasSolid(world, 0, 12, EndlessEncounter.BOSS_Z, minY, maxY);
    }

    private static boolean sliceHasSolid(World world, int x0, int x1, int z, int minY, int maxY) {
        int from = Math.min(x0, x1);
        int to = Math.max(x0, x1);
        for (int x = from; x <= to; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (world.getBlockAt(x, y, z).getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertFitsWorldHeight(World world, int minY, int maxY) {
        if (minY < world.getMinHeight() || maxY >= world.getMaxHeight()) {
            throw new IllegalStateException(
                    "Floor 2 schematic Y " + minY + ".." + maxY
                            + " does not fit world height " + world.getMinHeight()
                            + ".." + (world.getMaxHeight() - 1)
                            + ". Refusing a height-clipped paste."
            );
        }
    }

    private static void flushEditSession(EditSession editSession) {
        try {
            editSession.getClass().getMethod("flushQueue").invoke(editSession);
        } catch (NoSuchMethodException ignored) {
            // Vanilla WorldEdit commits inside EditSession.close().
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Failed to flush Floor 2 paste: " + cause.getMessage(), cause);
        }
    }

    private static void persistExtent(World world, int minX, int maxX, int minZ, int maxZ) {
        int x0 = Math.min(minX, maxX) >> 4;
        int x1 = Math.max(minX, maxX) >> 4;
        int z0 = Math.min(minZ, maxZ) >> 4;
        int z1 = Math.max(minZ, maxZ) >> 4;
        boolean previous = world.isAutoSave();
        world.setAutoSave(true);
        try {
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) {
                    world.getChunkAt(cx, cz).load(true);
                }
            }
            world.save();
        } finally {
            world.setAutoSave(previous);
        }
    }

    private static void verifyPaste(
            Plugin plugin,
            World world,
            Clipboard clipboard,
            BlockVector3 shift,
            BlockVector3 worldMin,
            BlockVector3 worldMax
    ) {
        int span = Math.max(1, worldMax.z() - worldMin.z());
        int[] zs = {
                worldMin.z() + span / 8,
                worldMin.z() + span / 2,
                worldMin.z() + (span * 7) / 8
        };
        int totalChecked = 0;
        int totalMissed = 0;
        for (int worldZ : zs) {
            int checked = 0;
            int missed = 0;
            int minX = Math.min(worldMin.x(), worldMax.x());
            int maxX = Math.max(worldMin.x(), worldMax.x());
            int minY = Math.min(worldMin.y(), worldMax.y());
            int maxY = Math.max(worldMin.y(), worldMax.y());
            for (int x = minX; x <= maxX && checked < 40; x += 3) {
                for (int y = minY; y <= maxY && checked < 40; y += 3) {
                    BlockVector3 worldPos = BlockVector3.at(x, y, worldZ);
                    BlockVector3 clipPos = worldPos.subtract(shift);
                    if (!clipboard.getRegion().contains(clipPos)) {
                        continue;
                    }
                    BlockState state = clipboard.getBlock(clipPos);
                    if (state.getBlockType().getMaterial().isAir()) {
                        continue;
                    }
                    checked++;
                    Material expected = BukkitAdapter.adapt(state.getBlockType());
                    Material found = world.getBlockAt(x, y, worldZ).getType();
                    if (found != expected) {
                        missed++;
                    }
                }
            }
            if (checked < 8 || missed * 2 > checked) {
                throw new IllegalStateException(
                        "Floor 2 schematic paste is incomplete at z=" + worldZ
                                + " (" + missed + "/" + checked + " samples missing)."
                );
            }
            totalChecked += checked;
            totalMissed += missed;
        }
        plugin.getLogger().info("Floor 2 paste verified " + totalChecked + " blocks, mismatches=" + totalMissed);
    }

    private static int chunkSpan(BlockVector3 min, BlockVector3 max) {
        int dx = Math.abs((max.x() >> 4) - (min.x() >> 4));
        int dz = Math.abs((max.z() >> 4) - (min.z() >> 4));
        return Math.max(dx, dz);
    }

    private static int worldDistance(World world, String method, int fallback) {
        try {
            Object value = world.getClass().getMethod(method).invoke(world);
            if (value instanceof Integer distance && distance > 0) {
                return distance;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static void setWorldDistance(World world, int view, int simulation) {
        try {
            world.getClass().getMethod("setViewDistance", int.class).invoke(world, view);
        } catch (Throwable ignored) {
        }
        try {
            world.getClass().getMethod("setSimulationDistance", int.class).invoke(world, simulation);
        } catch (Throwable ignored) {
        }
    }

    public static BuildResult build(Plugin plugin, World world, DungeonSession session) {
        PasteResult pasted = paste(plugin, world);
        EndlessEncounter.Prep prep = EndlessEncounter.prepare(
                plugin,
                world,
                pasted.minX(),
                pasted.maxX(),
                pasted.minY(),
                pasted.maxY(),
                pasted.minZ(),
                pasted.maxZ()
        );
        Location spawn = EndlessEncounter.begin(
                plugin,
                world,
                session,
                pasted.minX(),
                pasted.maxX(),
                pasted.minY(),
                pasted.maxY(),
                pasted.minZ(),
                pasted.maxZ(),
                prep
        );

        plugin.getLogger().info("Endless schematic ready. spawn="
                + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ());
        return new BuildResult(
                spawn,
                pasted.layout(),
                pasted.minX(),
                pasted.maxX(),
                pasted.minY(),
                pasted.maxY(),
                pasted.minZ(),
                pasted.maxZ()
        );
    }
}
