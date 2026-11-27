package de.aetherion.dungeons.instance;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps a run's chunks loaded. Dungeon worlds never save (autosave is off), so whatever sits in a chunk
 * that unloads is gone for good: Floor 2 mobs spawned far from the party vanished and the clearance bar
 * could not fill. Chunks are loaded off the server thread and pinned with a plugin ticket until
 * {@link #release} (or the world unloads).
 */
final class DungeonChunks {

    /** Longest wait for the async loads before the run goes on anyway (the rest then loads on touch). */
    private static final long FALLBACK_TICKS = 100L;

    private DungeonChunks() {
    }

    /** Pins the chunks holding these block columns ({x, ?, z} or {x, z}), then runs {@code then} on the server thread. */
    static void pinColumns(Plugin plugin, World world, Collection<int[]> columns, Runnable then) {
        Set<Long> keys = new LinkedHashSet<>();
        for (int[] column : columns) {
            if (column == null || column.length < 2) {
                continue;
            }
            int x = column[0];
            int z = column.length >= 3 ? column[2] : column[1];
            keys.add(key(x >> 4, z >> 4));
        }
        pin(plugin, world, keys, then);
    }

    /** Pins every chunk of a block area, then runs {@code then} on the server thread. */
    static void pinArea(Plugin plugin, World world, int minX, int maxX, int minZ, int maxZ, Runnable then) {
        Set<Long> keys = new LinkedHashSet<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                keys.add(key(cx, cz));
            }
        }
        pin(plugin, world, keys, then);
    }

    /** Pins the chunks of an area that the build already loaded; no loading, no waiting. */
    static int pinLoadedArea(Plugin plugin, World world, int minX, int maxX, int minZ, int maxZ) {
        int pinned = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (world.isChunkLoaded(cx, cz) && world.addPluginChunkTicket(cx, cz, plugin)) {
                    pinned++;
                }
            }
        }
        return pinned;
    }

    static void release(Plugin plugin, World world) {
        if (plugin != null && world != null && Bukkit.getWorld(world.getUID()) != null) {
            world.removePluginChunkTickets(plugin);
        }
    }

    private static void pin(Plugin plugin, World world, Set<Long> keys, Runnable then) {
        AtomicBoolean done = new AtomicBoolean(false);
        Runnable finish = () -> {
            if (done.compareAndSet(false, true) && Bukkit.getWorld(world.getUID()) != null) {
                then.run();
            }
        };
        if (keys.isEmpty()) {
            finish.run();
            return;
        }
        AtomicInteger left = new AtomicInteger(keys.size());
        for (long key : keys) {
            int cx = (int) key;
            int cz = (int) (key >> 32);
            world.getChunkAtAsync(cx, cz, true).whenComplete((chunk, error) -> {
                Runnable step = () -> {
                    if (chunk != null && Bukkit.getWorld(world.getUID()) != null) {
                        chunk.addPluginChunkTicket(plugin);
                    }
                    if (left.decrementAndGet() == 0) {
                        finish.run();
                    }
                };
                if (Bukkit.isPrimaryThread()) {
                    step.run();
                } else {
                    Bukkit.getScheduler().runTask(plugin, step);
                }
            });
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!done.get()) {
                plugin.getLogger().warning("[Chunks] " + left.get() + " of " + keys.size() + " chunks in "
                        + world.getName() + " still loading after " + (FALLBACK_TICKS / 20) + " s; going on.");
                finish.run();
            }
        }, FALLBACK_TICKS);
    }

    private static long key(int cx, int cz) {
        return ((long) cx & 0xffffffffL) | (((long) cz & 0xffffffffL) << 32);
    }
}
