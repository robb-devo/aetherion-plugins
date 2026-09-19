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
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.block.BlockTypes;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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
            Footprint footprint = footprintOf(clipboard, x, y, z, rotateY, world.getName());
            persistLast(plugin, islandId, x, y, z, dims, footprint);
            plugin.getLogger().info("aetherpaste loaded " + islandId
                    + " size=" + dims.x() + "x" + dims.y() + "x" + dims.z()
                    + " footprint=" + footprint.minX() + "," + footprint.minY() + "," + footprint.minZ()
                    + ".." + footprint.maxX() + "," + footprint.maxY() + "," + footprint.maxZ()
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

    public static void clearBox(
            Plugin plugin,
            CommandSender sender,
            String label,
            World world,
            int x1,
            int y1,
            int z1,
            int x2,
            int y2,
            int z2
    ) {
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        long start = System.currentTimeMillis();
        AtomicBoolean running = new AtomicBoolean(true);
        BukkitTask progress = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!running.get()) {
                return;
            }
            long elapsed = System.currentTimeMillis() - start;
            plugin.getLogger().info("aetherpaste progress clear " + label
                    + " elapsed=" + elapsed + "ms (FAWE queue still running)");
            tell(plugin, sender, "§7aetherpaste clear §f" + label + " §7still running… §f" + (elapsed / 1000L) + "s");
        }, 100L, 100L);

        try {
            plugin.getLogger().info("aetherpaste clear start " + label
                    + " world=" + world.getName()
                    + " box=" + minX + " " + minY + " " + minZ
                    + " .. " + maxX + " " + maxY + " " + maxZ
                    + " volume=" + volume);
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
                CuboidRegion region = new CuboidRegion(
                        BlockVector3.at(minX, minY, minZ),
                        BlockVector3.at(maxX, maxY, maxZ)
                );
                if (BlockTypes.AIR == null) {
                    throw new IllegalStateException("BlockTypes.AIR is unavailable");
                }
                changed = editSession.setBlocks(region, BlockTypes.AIR.getDefaultState());
            }
            long ms = System.currentTimeMillis() - start;
            plugin.getLogger().info("aetherpaste clear done " + label
                    + " blocks=" + changed
                    + " elapsed=" + ms + "ms"
                    + " world=" + world.getName());
            tell(plugin, sender, "§aCleared §f" + label + " §ain §f" + ms + "ms§a"
                    + " (§f" + changed + " §ablocks) §7"
                    + minX + " " + minY + " " + minZ + " → "
                    + maxX + " " + maxY + " " + maxZ + " " + world.getName() + "§a.");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "aetherpaste clear failed " + label, t);
            tell(plugin, sender, "§cClear failed: " + t.getMessage());
        } finally {
            running.set(false);
            progress.cancel();
            finish();
        }
    }

    public static Footprint loadLastFootprint(Plugin plugin, String islandId) {
        ConfigurationSection last = plugin.getConfig().getConfigurationSection("aether-paste." + islandId + ".last");
        if (last == null || !last.isConfigurationSection("footprint.min") || !last.isConfigurationSection("footprint.max")) {
            return null;
        }
        ConfigurationSection min = last.getConfigurationSection("footprint.min");
        ConfigurationSection max = last.getConfigurationSection("footprint.max");
        if (min == null || max == null) {
            return null;
        }
        return new Footprint(
                last.getString("world", "world"),
                min.getInt("x"),
                min.getInt("y"),
                min.getInt("z"),
                max.getInt("x"),
                max.getInt("y"),
                max.getInt("z")
        );
    }

    public static LastOrigin loadLastOrigin(Plugin plugin, String islandId) {
        ConfigurationSection last = plugin.getConfig().getConfigurationSection("aether-paste." + islandId + ".last");
        if (last == null || !last.isConfigurationSection("origin")) {
            return null;
        }
        ConfigurationSection origin = last.getConfigurationSection("origin");
        if (origin == null) {
            return null;
        }
        return new LastOrigin(
                last.getString("world", "world"),
                origin.getInt("x"),
                origin.getInt("y"),
                origin.getInt("z")
        );
    }

    public static Footprint footprintFromSchem(File schem, int x, int y, int z, int rotateY, String worldName) throws Exception {
        return footprintOf(readClipboard(schem), x, y, z, rotateY, worldName);
    }

    public record Footprint(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    public record LastOrigin(String world, int x, int y, int z) {
    }

    private static Footprint footprintOf(Clipboard clipboard, int x, int y, int z, int rotateY, String worldName) {
        BlockVector3 pasteAt = BlockVector3.at(x, y, z);
        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        AffineTransform transform = rotateY == 0 ? null : new AffineTransform().rotateY(rotateY);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int[] xs = {min.x(), max.x()};
        int[] ys = {min.y(), max.y()};
        int[] zs = {min.z(), max.z()};
        for (int cx : xs) {
            for (int cy : ys) {
                for (int cz : zs) {
                    BlockVector3 offset = BlockVector3.at(cx, cy, cz).subtract(origin);
                    BlockVector3 worldPos;
                    if (transform == null) {
                        worldPos = pasteAt.add(offset);
                    } else {
                        var vector = transform.apply(offset.toVector3()).round();
                        worldPos = pasteAt.add(BlockVector3.at(vector.x(), vector.y(), vector.z()));
                    }
                    minX = Math.min(minX, worldPos.x());
                    minY = Math.min(minY, worldPos.y());
                    minZ = Math.min(minZ, worldPos.z());
                    maxX = Math.max(maxX, worldPos.x());
                    maxY = Math.max(maxY, worldPos.y());
                    maxZ = Math.max(maxZ, worldPos.z());
                }
            }
        }
        return new Footprint(worldName, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void persistLast(
            Plugin plugin,
            String islandId,
            int x,
            int y,
            int z,
            BlockVector3 dims,
            Footprint footprint
    ) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            String root = "aether-paste." + islandId + ".last";
            plugin.getConfig().set(root + ".world", footprint.world());
            plugin.getConfig().set(root + ".origin.x", x);
            plugin.getConfig().set(root + ".origin.y", y);
            plugin.getConfig().set(root + ".origin.z", z);
            plugin.getConfig().set(root + ".size.x", dims.x());
            plugin.getConfig().set(root + ".size.y", dims.y());
            plugin.getConfig().set(root + ".size.z", dims.z());
            plugin.getConfig().set(root + ".footprint.min.x", footprint.minX());
            plugin.getConfig().set(root + ".footprint.min.y", footprint.minY());
            plugin.getConfig().set(root + ".footprint.min.z", footprint.minZ());
            plugin.getConfig().set(root + ".footprint.max.x", footprint.maxX());
            plugin.getConfig().set(root + ".footprint.max.y", footprint.maxY());
            plugin.getConfig().set(root + ".footprint.max.z", footprint.maxZ());
            plugin.saveConfig();
            plugin.getLogger().info("aetherpaste saved last footprint for " + islandId
                    + " at " + footprint.minX() + "," + footprint.minY() + "," + footprint.minZ()
                    + ".." + footprint.maxX() + "," + footprint.maxY() + "," + footprint.maxZ());
        });
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
