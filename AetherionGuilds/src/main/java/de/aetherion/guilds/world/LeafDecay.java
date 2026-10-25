package de.aetherion.guilds.world;

import de.aetherion.guilds.island.HostService;

import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leaves on the islands fall once their tree is cut. The starter trees are pasted from templates whose leaves are all
 * {@code persistent=true}, so vanilla never decays them and players had to break every leaf by hand.
 *
 * <p>When a log breaks in an island world, the leaves that touched it (through other leaves, up to vanilla's 6 steps)
 * are checked against the logs still standing: every leaf with no log within 6 steps falls, a few a tick, with its
 * normal drops. Leaves another trunk still holds stay, so the next tree over is never touched.
 */
public final class LeafDecay implements Listener {

    private static final int REACH = 6;
    private static final int MAX_LEAVES = 600;
    private static final int PER_TICK = 6;
    private static final BlockFace[] SIDES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final JavaPlugin plugin;
    private final HostService hosts;

    public LeafDecay(JavaPlugin plugin, HostService hosts) {
        this.plugin = plugin;
        this.hosts = hosts;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block log = event.getBlock();
        if (!Tag.LOGS.isTagged(log.getType()) || !hosts.isIslandWorld(log.getWorld())) {
            return;
        }
        World world = log.getWorld();
        int x = log.getX();
        int y = log.getY();
        int z = log.getZ();
        // Next tick: the log is gone by then.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> fell(world.getBlockAt(x, y, z)), 2L);
    }

    private void fell(Block cut) {
        if (Tag.LOGS.isTagged(cut.getType())) {
            return;
        }
        List<Block> crown = crown(cut);
        if (crown.isEmpty()) {
            return;
        }
        List<Block> loose = unsupported(crown);
        if (loose.isEmpty()) {
            return;
        }
        Collections.shuffle(loose);
        new BukkitRunnable() {
            int next;

            @Override
            public void run() {
                for (int i = 0; i < PER_TICK && next < loose.size(); i++, next++) {
                    Block leaf = loose.get(next);
                    if (Tag.LEAVES.isTagged(leaf.getType())) {
                        leaf.breakNaturally();
                    }
                }
                if (next >= loose.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Leaves reachable from the cut log through leaves, at most {@link #REACH} steps away. */
    private static List<Block> crown(Block cut) {
        Map<Block, Integer> seen = new HashMap<>();
        Deque<Block> queue = new ArrayDeque<>();
        List<Block> leaves = new ArrayList<>();
        seen.put(cut, 0);
        queue.add(cut);
        while (!queue.isEmpty() && leaves.size() < MAX_LEAVES) {
            Block at = queue.poll();
            int steps = seen.get(at);
            if (steps >= REACH) {
                continue;
            }
            for (BlockFace face : SIDES) {
                Block next = at.getRelative(face);
                if (seen.containsKey(next) || !Tag.LEAVES.isTagged(next.getType())) {
                    continue;
                }
                seen.put(next, steps + 1);
                leaves.add(next);
                queue.add(next);
            }
        }
        return leaves;
    }

    /**
     * The crown leaves no standing log reaches within {@link #REACH} steps through leaves (vanilla's rule). Every
     * leaf touching a log in the crown's box (grown by {@link #REACH}) seeds the search, so a neighbouring trunk
     * holds its own crown even where it overlaps this one.
     */
    private static List<Block> unsupported(List<Block> crown) {
        World world = crown.get(0).getWorld();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Block leaf : crown) {
            minX = Math.min(minX, leaf.getX());
            minY = Math.min(minY, leaf.getY());
            minZ = Math.min(minZ, leaf.getZ());
            maxX = Math.max(maxX, leaf.getX());
            maxY = Math.max(maxY, leaf.getY());
            maxZ = Math.max(maxZ, leaf.getZ());
        }
        minX -= REACH;
        minZ -= REACH;
        maxX += REACH;
        maxZ += REACH;
        minY = Math.max(world.getMinHeight(), minY - REACH);
        maxY = Math.min(world.getMaxHeight() - 1, maxY + REACH);

        Map<Block, Integer> held = new HashMap<>();
        Deque<Block> queue = new ArrayDeque<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block leaf = world.getBlockAt(x, y, z);
                    if (!Tag.LEAVES.isTagged(leaf.getType())) {
                        continue;
                    }
                    for (BlockFace face : SIDES) {
                        if (Tag.LOGS.isTagged(leaf.getRelative(face).getType())) {
                            held.put(leaf, 1);
                            queue.add(leaf);
                            break;
                        }
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            Block at = queue.poll();
            int steps = held.get(at);
            if (steps >= REACH) {
                continue;
            }
            for (BlockFace face : SIDES) {
                Block next = at.getRelative(face);
                if (next.getX() < minX || next.getX() > maxX || next.getY() < minY || next.getY() > maxY
                        || next.getZ() < minZ || next.getZ() > maxZ
                        || held.containsKey(next) || !Tag.LEAVES.isTagged(next.getType())) {
                    continue;
                }
                held.put(next, steps + 1);
                queue.add(next);
            }
        }
        List<Block> loose = new ArrayList<>();
        for (Block leaf : crown) {
            if (!held.containsKey(leaf)) {
                loose.add(leaf);
            }
        }
        return loose;
    }
}
