package de.aetherion.foraging;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.SideEffectSet;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * FastAsyncWorldEdit's async queue for big schematic pastes, so the main thread only applies finished
 * chunks in small batches. Plain WorldEdit cannot paste off the main thread; callers keep their
 * synchronous path when {@link #present()} is false.
 * <p>
 * FAWE-only builder methods are invoked reflectively so this compiles against WorldEdit 7.3
 * (same approach as AetherionHub's FaweIslandPaste).
 */
public final class FaweQueue {

    private FaweQueue() {
    }

    public static boolean present() {
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

    /** Runs the task on FAWE's task manager, or a Bukkit async task if that is unavailable. */
    public static void runAsync(Plugin plugin, Runnable task) {
        try {
            Class<?> faweApi = Class.forName("com.fastasyncworldedit.core.FaweAPI");
            Object manager = faweApi.getMethod("getTaskManager").invoke(null);
            manager.getClass().getMethod("async", Runnable.class).invoke(manager, task);
            return;
        } catch (Throwable ignored) {
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    /** Back on the main thread, unless the plugin was disabled meanwhile. */
    public static void runMain(Plugin plugin, Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Unrestricted, history-free edit session on FAWE's chunk queue. Close it off the main thread. */
    public static EditSession session(World world) {
        Object builder = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .maxBlocks(-1);
        builder = invokeNoArg(builder, "limitUnlimited");
        builder = invokeNoArg(builder, "allowedRegionsEverywhere");
        builder = invokeNoArg(builder, "changeSetNull");
        builder = invokeOne(builder, "fastMode", Boolean.class, Boolean.TRUE);
        builder = invokeOne(builder, "checkMemory", Boolean.class, Boolean.FALSE);
        EditSession session;
        try {
            Object built = builder.getClass().getMethod("build").invoke(builder);
            if (!(built instanceof EditSession editSession)) {
                throw new IllegalStateException("EditSessionBuilder.build() did not return an EditSession");
            }
            session = editSession;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not build FAWE EditSession", exception);
        }
        try {
            session.setSideEffectApplier(SideEffectSet.none());
        } catch (Throwable ignored) {
        }
        return session;
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
}
