package de.aetherion.hub.island;

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
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffectSet;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * One-shot FAWE async schematic paste. Runs off the main thread so large
 * Eldervale islands do not freeze TPS.
 */
public final class FaweIslandPaste {

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private FaweIslandPaste() {
    }

    public static boolean fawePresent() {
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            return true;
        }
        try {
            Class.forName("com.fastasyncworldedit.core.FaweAPI");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static File resolveSchematic(Plugin plugin, String schemName) {
        File plugins = plugin.getDataFolder().getParentFile();
        File[] candidates = {
                new File(plugins, "FastAsyncWorldEdit/schematics/" + schemName),
                new File(plugins, "WorldEdit/schematics/" + schemName)
        };
        for (File file : candidates) {
            if (file.isFile()) {
                return file;
            }
        }
        return candidates[0];
    }

    public static List<String> schematicSearchHint(Plugin plugin, String schemName) {
        File plugins = plugin.getDataFolder().getParentFile();
        List<String> paths = new ArrayList<>(2);
        paths.add(new File(plugins, "FastAsyncWorldEdit/schematics/" + schemName).getPath());
        paths.add(new File(plugins, "WorldEdit/schematics/" + schemName).getPath());
        return paths;
    }

    public static boolean tryBegin() {
        return BUSY.compareAndSet(false, true);
    }

    public static void finish() {
        BUSY.set(false);
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (tryFaweTaskManager(task)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static void paste(
            Plugin plugin,
            CommandSender sender,
            String islandId,
            File schem,
            World world,
            int x,
            int y,
            int z,
            boolean ignoreAir,
            int rotateY
    ) {
        long start = System.currentTimeMillis();
        AtomicBoolean running = new AtomicBoolean(true);
        BukkitTask progress = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!running.get()) {
                return;
            }
            long elapsed = System.currentTimeMillis() - start;
            String line = "aetherpaste progress " + islandId + " elapsed=" + elapsed + "ms (FAWE queue still running)";
            plugin.getLogger().info(line);
            tell(plugin, sender, "§7aetherpaste §f" + islandId + " §7still pasting… §f" + (elapsed / 1000L) + "s");
        }, 100L, 100L);

        try {
            Clipboard clipboard = readClipboard(schem);
            BlockVector3 dims = clipboard.getDimensions();
            plugin.getLogger().info("aetherpaste loaded " + islandId
                    + " size=" + dims.x() + "x" + dims.y() + "x" + dims.z()
                    + " file=" + schem.getName());

            int changed;
            try (EditSession editSession = buildQueuedSession(world)) {
                try {
                    editSession.setFastMode(true);
                } catch (Throwable ignored) {
                }
                try {
                    editSession.setSideEffectApplier(SideEffectSet.none());
                } catch (Throwable ignored) {
                }
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                if (rotateY != 0) {
                    holder.setTransform(new AffineTransform().rotateY(rotateY));
                }
                Operation operation = holder
                        .createPaste(editSession)
                        .to(BlockVector3.at(x, y, z))
                        .ignoreAirBlocks(ignoreAir)
                        .copyEntities(false)
                        .copyBiomes(false)
                        .build();
                // Must stay off the main thread — Operations.complete / close() block.
                Operations.complete(operation);
                changed = editSession.getBlockChangeCount();
            }

            long ms = System.currentTimeMillis() - start;
            plugin.getLogger().info("aetherpaste done " + islandId
                    + " blocks=" + changed
                    + " elapsed=" + ms + "ms"
                    + " origin=" + x + " " + y + " " + z
                    + " world=" + world.getName());
            tell(plugin, sender, "§aPasted §f" + islandId + " §ain §f" + ms + "ms§a"
                    + " (§f" + changed + " §ablocks) at §f"
                    + x + " " + y + " " + z + " §7" + world.getName() + "§a.");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "aetherpaste failed " + islandId, t);
            tell(plugin, sender, "§cPaste failed: " + t.getMessage());
        } finally {
            running.set(false);
            progress.cancel();
            finish();
        }
    }

    private static Clipboard readClipboard(File schem) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(schem);
        if (format == null) {
            throw new IllegalStateException("Unknown schematic format: " + schem.getName());
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(schem))) {
            return reader.read();
        }
    }

    /**
     * FAWE EditSessionBuilder: unrestricted limits + chunk queue (not WNA).
     * Extra builder methods are invoked reflectively so we compile against WorldEdit 7.3.
     */
    private static EditSession buildQueuedSession(World world) {
        Object builder = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .maxBlocks(-1);
        builder = invokeNoArg(builder, "limitUnlimited");
        builder = invokeNoArg(builder, "allowedRegionsEverywhere");
        builder = invokeNoArg(builder, "changeSetNull");
        builder = invokeOne(builder, "fastMode", Boolean.class, Boolean.TRUE);
        builder = invokeOne(builder, "checkMemory", Boolean.class, Boolean.FALSE);
        try {
            Object session = builder.getClass().getMethod("build").invoke(builder);
            if (!(session instanceof EditSession editSession)) {
                throw new IllegalStateException("EditSessionBuilder.build() did not return an EditSession");
            }
            return editSession;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not build FAWE EditSession", exception);
        }
    }

    private static boolean tryFaweTaskManager(Runnable task) {
        try {
            Class<?> faweApi = Class.forName("com.fastasyncworldedit.core.FaweAPI");
            Object manager = faweApi.getMethod("getTaskManager").invoke(null);
            manager.getClass().getMethod("async", Runnable.class).invoke(manager, task);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeNoArg(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            Object result = method.invoke(target);
            return result != null ? result : target;
        } catch (ReflectiveOperationException ignored) {
            return target;
        }
    }

    private static Object invokeOne(Object target, String name, Class<?> type, Object value) {
        try {
            Method method = target.getClass().getMethod(name, type);
            Object result = method.invoke(target, value);
            return result != null ? result : target;
        } catch (ReflectiveOperationException ignored) {
            return target;
        }
    }

    private static void tell(Plugin plugin, CommandSender sender, String message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(message));
    }
}
