package de.aetherion.mining.veins;

import de.aetherion.mining.AetherionMining;

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
import com.sk89q.worldedit.EditSession;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Pastes the hand-built Amethyst Mines schematic into {@code aether_veins}.
 * This replaces the old procedural Deep Mines dig world.
 * <p>
 * With FastAsyncWorldEdit the paste runs on FAWE's async queue, so the main thread only applies
 * finished chunks in small batches (the 10-18 watchdog stop on mmo-r was a 6 s synchronous paste).
 * Plain WorldEdit cannot paste off the main thread, so it keeps the old synchronous path.
 */
public final class AmethystPaste {

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private AmethystPaste() {
    }

    /** True while a paste is queued or running. */
    public static boolean isBusy() {
        return BUSY.get();
    }

    /**
     * Starts the paste and calls {@code done} on the main thread with the result.
     *
     * @param restore true for the daily reset: air is pasted too, so dug tunnels and placed blocks go away
     * @return false when another paste is still running or the schematic is missing ({@code done} is not called)
     */
    public static boolean pasteAsync(AetherionMining plugin, World world, CommandSender sender, boolean restore,
                                     Consumer<Boolean> done) {
        if (plugin == null || world == null) {
            return false;
        }
        String schemName = plugin.getConfig().getString("veins.schematic", "amethyst-mines.schem");
        File schem = resolveSchematic(plugin, schemName);
        if (schem == null || !schem.isFile()) {
            message(sender, "§cAmethyst schematic not found: " + schemName);
            message(sender, "§7Expected under plugins/WorldEdit/schematics/ or FastAsyncWorldEdit/schematics/");
            return false;
        }
        if (!BUSY.compareAndSet(false, true)) {
            message(sender, "§cAn Amethyst Mines paste is already running.");
            return false;
        }
        int x = plugin.getConfig().getInt("veins.paste.x", 0);
        int y = plugin.getConfig().getInt("veins.paste.y", 0);
        int z = plugin.getConfig().getInt("veins.paste.z", 0);
        boolean ignoreAir = !restore && plugin.getConfig().getBoolean("veins.ignore-air", false);
        world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 0);

        if (!fawePresent()) {
            message(sender, "§dPasting Amethyst Mines at §f" + x + " " + y + " " + z
                    + " §d(ignoreAir=" + ignoreAir + ", WorldEdit, main thread)…");
            boolean ok;
            try {
                ok = pasteBlocking(plugin, world, sender, schem, x, y, z, ignoreAir, false);
            } finally {
                BUSY.set(false);
            }
            done.accept(ok);
            return true;
        }

        message(sender, "§dPasting Amethyst Mines at §f" + x + " " + y + " " + z
                + " §d(ignoreAir=" + ignoreAir + ", FAWE async)…");
        Runnable task = () -> {
            boolean ok = false;
            try {
                ok = pasteBlocking(plugin, world, sender, schem, x, y, z, ignoreAir, true);
            } finally {
                BUSY.set(false);
                boolean result = ok;
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, () -> done.accept(result));
                }
            }
        };
        try {
            runAsync(plugin, task);
        } catch (RuntimeException exception) {
            BUSY.set(false);
            plugin.getLogger().log(Level.SEVERE, "Could not start the Amethyst Mines paste", exception);
            message(sender, "§cCould not start the paste: " + exception.getMessage());
            return false;
        }
        return true;
    }

    /** Reads and pastes the schematic on the calling thread. Off the main thread only with FAWE. */
    private static boolean pasteBlocking(AetherionMining plugin, World world, CommandSender sender, File schem,
                                         int x, int y, int z, boolean ignoreAir, boolean queued) {
        long start = System.currentTimeMillis();
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schem);
            if (format == null) {
                message(plugin, sender, queued, "§cUnknown schematic format.");
                return false;
            }
            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(new FileInputStream(schem))) {
                clipboard = reader.read();
            }
            int changed;
            try (EditSession editSession = queued ? buildQueuedSession(world) : WorldEdit.getInstance()
                    .newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(world))
                    .maxBlocks(-1)
                    .build()) {
                if (queued) {
                    try {
                        editSession.setSideEffectApplier(SideEffectSet.none());
                    } catch (Throwable ignored) {
                    }
                }
                Operation op = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(x, y, z))
                        .ignoreAirBlocks(ignoreAir)
                        .build();
                // With FAWE this blocks the async worker until the queue has flushed, never the main thread.
                Operations.complete(op);
                changed = editSession.getBlockChangeCount();
            }
            long ms = System.currentTimeMillis() - start;
            message(plugin, sender, queued, "§aAmethyst Mines pasted §7(" + ms + "ms, " + changed + " blocks) from §f"
                    + schem.getName());
            plugin.getLogger().info("Amethyst Mines pasted into " + world.getName()
                    + " at " + x + "," + y + "," + z + " in " + ms + "ms (" + changed + " blocks, "
                    + (queued ? "FAWE async" : "main thread") + ") from " + schem.getAbsolutePath());
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Amethyst Mines paste failed", ex);
            message(plugin, sender, queued, "§cPaste failed: " + ex.getMessage());
            return false;
        }
    }

    private static boolean fawePresent() {
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

    private static void runAsync(AetherionMining plugin, Runnable task) {
        try {
            Class<?> faweApi = Class.forName("com.fastasyncworldedit.core.FaweAPI");
            Object manager = faweApi.getMethod("getTaskManager").invoke(null);
            manager.getClass().getMethod("async", Runnable.class).invoke(manager, task);
            return;
        } catch (Throwable ignored) {
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    /**
     * FAWE EditSessionBuilder: unrestricted limits, no history, chunk queue.
     * The FAWE-only builder methods are invoked reflectively so this compiles against WorldEdit 7.3
     * (same approach as AetherionHub's FaweIslandPaste).
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

    private static void message(CommandSender sender, String line) {
        if (sender != null) {
            sender.sendMessage(line);
        }
    }

    /** Off the main thread, chat goes back through the scheduler. */
    private static void message(AetherionMining plugin, CommandSender sender, boolean async, String line) {
        if (sender == null) {
            return;
        }
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(line));
        } else {
            sender.sendMessage(line);
        }
    }

    private static File resolveSchematic(AetherionMining plugin, String name) {
        File plugins = plugin.getDataFolder().getParentFile();
        File[] candidates = {
                new File(plugins, "WorldEdit/schematics/" + name),
                new File(plugins, "FastAsyncWorldEdit/schematics/" + name),
                new File(plugin.getDataFolder(), "schematics/" + name),
                new File(Bukkit.getWorldContainer(), name)
        };
        for (File file : candidates) {
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }
}
