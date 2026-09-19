package de.aetherion.pit.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot WorldEdit / FAWE paste for the hub spawn schem.
 * Prefers FAWE (async, low heap). Vanilla WE uses ignoreAir + fastMode.
 */
public final class HubSchemPaster {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private HubSchemPaster() {
    }

    public static void maybePaste(JavaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("schem-paste.enabled", false)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> pasteNow(plugin, true), 100L);
    }

    /**
     * @param disableOnSuccess when true, flips schem-paste.enabled off after a successful paste
     * @return true if a paste was started (or already finished synchronously)
     */
    public static boolean pasteNow(JavaPlugin plugin, boolean disableOnSuccess) {
        if (!RUNNING.compareAndSet(false, true)) {
            plugin.getLogger().warning("Hub schem paste already running.");
            return false;
        }
        String fileName = plugin.getConfig().getString("schem-paste.file", "medieval-spawn-warzone.schem");
        File schem = new File(plugin.getDataFolder().getParentFile(), "WorldEdit/schematics/" + fileName);
        if (!schem.isFile()) {
            RUNNING.set(false);
            plugin.getLogger().warning("Schem missing: " + schem.getAbsolutePath());
            return false;
        }
        World world = Bukkit.getWorld(plugin.getConfig().getString("schem-paste.world", "world"));
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            RUNNING.set(false);
            throw new IllegalStateException("No world for schem paste");
        }
        Location at = new Location(
                world,
                plugin.getConfig().getDouble("schem-paste.x", 0.0),
                plugin.getConfig().getDouble("schem-paste.y", 64.0),
                plugin.getConfig().getDouble("schem-paste.z", 0.0)
        );
        final World pasteWorld = world;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Object clipboard = readClipboard(schem);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (tryFawePaste(plugin, clipboard, pasteWorld, at)) {
                            onSuccess(plugin, disableOnSuccess);
                            return;
                        }
                        pasteWorldEdit(plugin, clipboard, pasteWorld, at);
                        onSuccess(plugin, disableOnSuccess);
                    } catch (Throwable ex) {
                        plugin.getLogger().warning("Hub schem paste failed: " + ex);
                        if (ex.getCause() != null) {
                            plugin.getLogger().warning("  cause: " + ex.getCause());
                        }
                    } finally {
                        RUNNING.set(false);
                    }
                });
            } catch (Throwable ex) {
                RUNNING.set(false);
                plugin.getLogger().warning("Hub schem read failed: " + ex);
            }
        });
        return true;
    }

    private static void onSuccess(JavaPlugin plugin, boolean disableOnSuccess) {
        if (disableOnSuccess) {
            plugin.getConfig().set("schem-paste.enabled", false);
            plugin.saveConfig();
        }
        plugin.getLogger().info("Hub schem pasted — schem-paste.enabled="
                + plugin.getConfig().getBoolean("schem-paste.enabled"));
    }

    private static Object readClipboard(File schem) throws Exception {
        Class<?> formats = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
        Object format = formats.getMethod("findByFile", File.class).invoke(null, schem);
        if (format == null) {
            format = formats.getMethod("findByAlias", String.class).invoke(null, "sponge.3");
        }
        if (format == null) {
            throw new IllegalStateException("No ClipboardFormat for " + schem.getName());
        }
        Class<?> formatIface = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
        Method getReader = formatIface.getMethod("getReader", java.io.InputStream.class);
        try (FileInputStream in = new FileInputStream(schem)) {
            Object reader = getReader.invoke(format, in);
            Object clipboard = reader.getClass().getMethod("read").invoke(reader);
            try {
                reader.getClass().getMethod("close").invoke(reader);
            } catch (NoSuchMethodException ignored) {
            }
            return clipboard;
        }
    }

    private static boolean tryFawePaste(JavaPlugin plugin, Object clipboard, World world, Location at) {
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            return false;
        }
        try {
            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weWorld = bukkitAdapter.getMethod("adapt", World.class).invoke(null, world);
            Object to = bukkitAdapter.getMethod("asBlockVector", Location.class).invoke(null, at);

            // FAWE EditSessionBuilder — queue + close async-friendly
            Object worldEdit = Class.forName("com.sk89q.worldedit.WorldEdit").getMethod("getInstance").invoke(null);
            Object esFactory = worldEdit.getClass().getMethod("newEditSessionBuilder").invoke(worldEdit);
            esFactory.getClass().getMethod("world", Class.forName("com.sk89q.worldedit.world.World"))
                    .invoke(esFactory, weWorld);
            tryInvoke(esFactory, "fastMode", boolean.class, true);
            Object editSession = esFactory.getClass().getMethod("build").invoke(esFactory);
            try {
                Class<?> holder = Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
                Object clipHolder = holder.getConstructor(Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard"))
                        .newInstance(clipboard);
                Object pasteBuilder = clipHolder.getClass().getMethod("createPaste",
                        Class.forName("com.sk89q.worldedit.extent.Extent")).invoke(clipHolder, editSession);
                pasteBuilder.getClass().getMethod("to", Class.forName("com.sk89q.worldedit.math.BlockVector3"))
                        .invoke(pasteBuilder, to);
                tryInvoke(pasteBuilder, "ignoreAirBlocks", boolean.class, true);
                tryInvoke(pasteBuilder, "copyEntities", boolean.class, false);
                tryInvoke(pasteBuilder, "copyBiomes", boolean.class, false);
                Object op = pasteBuilder.getClass().getMethod("build").invoke(pasteBuilder);
                Class.forName("com.sk89q.worldedit.function.operation.Operations")
                        .getMethod("completeBlindly", Class.forName("com.sk89q.worldedit.function.operation.Operation"))
                        .invoke(null, op);
            } finally {
                editSession.getClass().getMethod("close").invoke(editSession);
            }
            plugin.getLogger().info("Hub schem pasted via FAWE.");
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("FAWE paste path failed, falling back to WorldEdit: " + ex.getMessage());
            return false;
        }
    }

    private static void pasteWorldEdit(JavaPlugin plugin, Object clipboard, World world, Location at)
            throws Exception {
        Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Object weWorld = bukkitAdapter.getMethod("adapt", World.class).invoke(null, world);
        Object to = bukkitAdapter.getMethod("asBlockVector", Location.class).invoke(null, at);

        Object worldEdit = Class.forName("com.sk89q.worldedit.WorldEdit").getMethod("getInstance").invoke(null);
        Object esFactory = worldEdit.getClass().getMethod("newEditSessionBuilder").invoke(worldEdit);
        esFactory.getClass().getMethod("world", Class.forName("com.sk89q.worldedit.world.World"))
                .invoke(esFactory, weWorld);
        tryInvoke(esFactory, "fastMode", boolean.class, true);
        Object editSession = esFactory.getClass().getMethod("build").invoke(esFactory);
        try {
            tryInvoke(editSession, "setFastMode", boolean.class, true);
            Class<?> holder = Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
            Object clipHolder = holder.getConstructor(Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard"))
                    .newInstance(clipboard);
            Object pasteBuilder = clipHolder.getClass().getMethod("createPaste",
                    Class.forName("com.sk89q.worldedit.extent.Extent")).invoke(clipHolder, editSession);
            pasteBuilder.getClass().getMethod("to", Class.forName("com.sk89q.worldedit.math.BlockVector3"))
                    .invoke(pasteBuilder, to);
            tryInvoke(pasteBuilder, "ignoreAirBlocks", boolean.class, true);
            tryInvoke(pasteBuilder, "copyEntities", boolean.class, false);
            tryInvoke(pasteBuilder, "copyBiomes", boolean.class, false);
            Object op = pasteBuilder.getClass().getMethod("build").invoke(pasteBuilder);
            Class.forName("com.sk89q.worldedit.function.operation.Operations")
                    .getMethod("completeBlindly", Class.forName("com.sk89q.worldedit.function.operation.Operation"))
                    .invoke(null, op);
        } finally {
            editSession.getClass().getMethod("close").invoke(editSession);
        }
        plugin.getLogger().info("Hub schem pasted via WorldEdit (ignoreAir/fastMode).");
    }

    private static void tryInvoke(Object target, String method, Class<?> type, Object value) {
        try {
            target.getClass().getMethod(method, type).invoke(target, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
