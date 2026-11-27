package de.aetherion.foraging;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Locale;

/**
 * Trees that grew from a sapling on a private or guild island (2026-11-27, Robb: felling must be the same
 * everywhere). On islands people build with logs and leaves, so a log only counts as a tree when it grew there:
 * every log of a {@link StructureGrowEvent} is remembered in its chunk's data, and only those take the fell with
 * hits. Built logs break the normal way.
 */
final class GrownTrees implements Listener {

    private static NamespacedKey key;

    GrownTrees(JavaPlugin plugin) {
        key = new NamespacedKey(plugin, "grown_logs");
    }

    /** Private island, guild island or the island test world. */
    static boolean islandWorld(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.equals("aether_islands") || name.equals("aether_guilds")
                || name.equals("aether_test") || name.startsWith("aether_test_");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        if (!islandWorld(event.getWorld())) {
            return;
        }
        for (BlockState state : event.getBlocks()) {
            if (Tag.LOGS.isTagged(state.getType())) {
                mark(state.getBlock(), true);
            }
        }
    }

    /** A grown log broken the normal way is no longer a tree log. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (islandWorld(event.getBlock().getWorld()) && isGrown(event.getBlock())) {
            mark(event.getBlock(), false);
        }
    }

    static boolean isGrown(Block block) {
        if (key == null || block == null) {
            return false;
        }
        int[] packed = read(block.getChunk());
        int me = pack(block);
        for (int value : packed) {
            if (value == me) {
                return true;
            }
        }
        return false;
    }

    static void forget(Block block) {
        mark(block, false);
    }

    private static void mark(Block block, boolean add) {
        if (key == null || block == null) {
            return;
        }
        Chunk chunk = block.getChunk();
        int[] packed = read(chunk);
        int me = pack(block);
        int at = -1;
        for (int i = 0; i < packed.length; i++) {
            if (packed[i] == me) {
                at = i;
                break;
            }
        }
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        if (add && at < 0) {
            int[] next = Arrays.copyOf(packed, packed.length + 1);
            next[packed.length] = me;
            data.set(key, PersistentDataType.INTEGER_ARRAY, next);
        } else if (!add && at >= 0) {
            int[] next = new int[packed.length - 1];
            System.arraycopy(packed, 0, next, 0, at);
            System.arraycopy(packed, at + 1, next, at, packed.length - at - 1);
            if (next.length == 0) {
                data.remove(key);
            } else {
                data.set(key, PersistentDataType.INTEGER_ARRAY, next);
            }
        }
    }

    private static int[] read(Chunk chunk) {
        int[] stored = chunk.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        return stored == null ? new int[0] : stored;
    }

    /** Block position inside its chunk: y (offset to stay positive), x and z low bits. */
    private static int pack(Block block) {
        return ((block.getY() + 2048) << 8) | ((block.getX() & 15) << 4) | (block.getZ() & 15);
    }
}
