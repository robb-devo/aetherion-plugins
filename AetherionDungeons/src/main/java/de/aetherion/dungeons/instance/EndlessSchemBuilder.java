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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Dev-only test floor: paste {@code dungeon1-endlessxl.schem} via WorldEdit into a void instance.
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
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(pasteOrigin)
                    .ignoreAirBlocks(true)
                    .copyEntities(false)
                    .copyBiomes(false)
                    .build();
            Operations.complete(operation);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to paste endless schematic", exception);
            throw new IllegalStateException("Paste failed: " + exception.getMessage(), exception);
        }
        plugin.getLogger().info("Endless paste done in " + (System.currentTimeMillis() - pasteStarted) + "ms"
                + " (" + sizeX + "x" + sizeY + "x" + sizeZ + ", air skipped)");

        BlockVector3 offset = pasteOrigin.subtract(clipboard.getOrigin());
        BlockVector3 worldMin = clipboard.getMinimumPoint().add(offset);
        BlockVector3 worldMax = clipboard.getMaximumPoint().add(offset);

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
