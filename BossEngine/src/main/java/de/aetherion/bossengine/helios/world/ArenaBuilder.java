package de.aetherion.bossengine.helios.world;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Throttled block work for arena slots: build from the layout, restore it, clear a whole slot box.
 * All edits are physics-free and happen only inside the Helios world. Jobs run one after another,
 * a few thousand blocks per tick, so even a crash-recovery sweep of every slot never stalls the server.
 */
public final class ArenaBuilder {

    private interface Job {
        /** @return true when finished */
        boolean step(int budget);
    }

    private final Plugin plugin;
    private final Deque<Job> jobs = new ArrayDeque<>();
    private final int buildPerTick;
    private final int clearPerTick;

    public ArenaBuilder(Plugin plugin, int buildPerTick, int clearPerTick) {
        this.plugin = plugin;
        this.buildPerTick = buildPerTick;
        this.clearPerTick = clearPerTick;
    }

    public void tick() {
        Job job = jobs.peekFirst();
        if (job == null) {
            return;
        }
        boolean clearing = job instanceof ClearJob;
        if (job.step(clearing ? clearPerTick : buildPerTick)) {
            jobs.pollFirst();
        }
    }

    public boolean idle() {
        return jobs.isEmpty();
    }

    public int queued() {
        return jobs.size();
    }

    /** Runs every queued job to completion right now (plugin disable). */
    public void flush() {
        int guard = 0;
        while (!jobs.isEmpty() && guard++ < 100_000) {
            Job job = jobs.peekFirst();
            if (job.step(Integer.MAX_VALUE)) {
                jobs.pollFirst();
            }
        }
    }

    /** Places {@code cells} (in list order) around the arena center, then runs {@code done}. */
    public void build(World world, int cx, int cy, int cz, List<ArenaLayout.Cell> cells, boolean onlyIfDifferent, Runnable done) {
        jobs.addLast(new BuildJob(world, cx, cy, cz, new ArrayList<>(cells), onlyIfDifferent, done, null));
    }

    /** Like {@link #build} but reports progress (placed count) every tick, for animated restores. */
    public void buildAnimated(World world, int cx, int cy, int cz, List<ArenaLayout.Cell> cells, IntConsumer progress, Runnable done) {
        jobs.addLast(new BuildJob(world, cx, cy, cz, new ArrayList<>(cells), true, done, progress));
    }

    /** Clears every non-air block in the slot box, then runs {@code done}. */
    public void clear(World world, int cx, int cy, int cz, Runnable done) {
        jobs.addLast(new ClearJob(world, cx, cy, cz, done));
    }

    public static void loadAndHold(Plugin plugin, World world, int cx, int cz) {
        int r = ArenaLayout.BOX_R + 8;
        for (int x = (cx - r) >> 4; x <= (cx + r) >> 4; x++) {
            for (int z = (cz - r) >> 4; z <= (cz + r) >> 4; z++) {
                Chunk chunk = world.getChunkAt(x, z);
                chunk.addPluginChunkTicket(plugin);
            }
        }
    }

    public static void release(Plugin plugin, World world, int cx, int cz) {
        int r = ArenaLayout.BOX_R + 8;
        for (int x = (cx - r) >> 4; x <= (cx + r) >> 4; x++) {
            for (int z = (cz - r) >> 4; z <= (cz + r) >> 4; z++) {
                world.removePluginChunkTicket(x, z, plugin);
            }
        }
    }

    private final class BuildJob implements Job {
        private final World world;
        private final int cx;
        private final int cy;
        private final int cz;
        private final List<ArenaLayout.Cell> cells;
        private final boolean onlyIfDifferent;
        private final Runnable done;
        private final IntConsumer progress;
        private int cursor;
        private boolean loaded;

        BuildJob(World world, int cx, int cy, int cz, List<ArenaLayout.Cell> cells, boolean onlyIfDifferent,
                 Runnable done, IntConsumer progress) {
            this.world = world;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.cells = cells;
            this.onlyIfDifferent = onlyIfDifferent;
            this.done = done;
            this.progress = progress;
        }

        @Override
        public boolean step(int budget) {
            if (!loaded) {
                loadAndHold(plugin, world, cx, cz);
                loaded = true;
            }
            int end = Math.min(cells.size(), cursor + Math.max(1, budget));
            for (; cursor < end; cursor++) {
                ArenaLayout.Cell c = cells.get(cursor);
                Block b = world.getBlockAt(cx + c.dx(), cy + c.dy(), cz + c.dz());
                BlockData data = c.data();
                if (onlyIfDifferent && b.getBlockData().matches(data)) {
                    continue;
                }
                b.setBlockData(data, false);
            }
            if (progress != null) {
                progress.accept(cursor);
            }
            if (cursor >= cells.size()) {
                if (done != null) {
                    done.run();
                }
                return true;
            }
            return false;
        }
    }

    private final class ClearJob implements Job {
        private final World world;
        private final int cx;
        private final int cy;
        private final int cz;
        private final Runnable done;
        private final int minY;
        private final int maxY;
        private final int span;
        private final int height;
        private long cursor;
        private final long total;
        private boolean loaded;

        ClearJob(World world, int cx, int cy, int cz, Runnable done) {
            this.world = world;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.done = done;
            this.minY = Math.max(world.getMinHeight(), cy - ArenaLayout.BOX_DOWN);
            this.maxY = Math.min(world.getMaxHeight() - 1, cy + ArenaLayout.BOX_UP);
            this.span = ArenaLayout.BOX_R * 2 + 1;
            this.height = maxY - minY + 1;
            this.total = (long) span * span * height;
        }

        @Override
        public boolean step(int budget) {
            if (!loaded) {
                loadAndHold(plugin, world, cx, cz);
                loaded = true;
            }
            long end = Math.min(total, cursor + Math.max(1, budget));
            for (; cursor < end; cursor++) {
                int y = (int) (cursor % height);
                long rest = cursor / height;
                int x = (int) (rest % span) - ArenaLayout.BOX_R;
                int z = (int) (rest / span) - ArenaLayout.BOX_R;
                Block b = world.getBlockAt(cx + x, minY + y, cz + z);
                if (b.getType() != Material.AIR) {
                    b.setType(Material.AIR, false);
                }
            }
            if (cursor >= total) {
                if (done != null) {
                    done.run();
                }
                return true;
            }
            return false;
        }
    }
}
