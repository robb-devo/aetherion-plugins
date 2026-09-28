package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Real block edits in the World Eater site, and only there.
 *
 * <p>Every write is clamped to the site boxes of {@link SiteLayout} in the site world, uses no
 * physics, and can be undone by asking the layout what belongs there. Removing is "set air";
 * restoring is "set what the layout says". There is no journal to lose in a crash: a full
 * {@link #restoreAll(Consumer)} after a restart brings every eaten chunk back.
 */
public final class SiteTerrain {

    private final World world;
    private final SiteLayout layout;

    public SiteTerrain(World world) {
        this.world = world;
        this.layout = SiteLayout.get();
    }

    public World world() {
        return world;
    }

    public SiteLayout layout() {
        return layout;
    }

    /* ================================================================== single cells */

    /** Clears one site cell. @return the material that was there, or null if nothing changed */
    public Material clear(int x, int y, int z) {
        if (world == null || !SiteLayout.inSite(x, y, z)) {
            return null;
        }
        Block b = world.getBlockAt(x, y, z);
        Material was = b.getType();
        if (was.isAir()) {
            return null;
        }
        b.setType(Material.AIR, false);
        return was;
    }

    /** Puts back what the layout says belongs in one cell. @return true if the block changed */
    public boolean restore(int x, int y, int z) {
        if (world == null || !SiteLayout.inSite(x, y, z)) {
            return false;
        }
        Block b = world.getBlockAt(x, y, z);
        BlockData want = layout.expected(x, y, z);
        if (want == null) {
            if (!b.getType().isAir()) {
                b.setType(Material.AIR, false);
                return true;
            }
            return false;
        }
        if (b.getType() != want.getMaterial() || !b.getBlockData().equals(want)) {
            b.setBlockData(want, false);
            return true;
        }
        return false;
    }

    /** Is there a solid, walkable block at the island surface above (x, z)? */
    public boolean solidAt(int x, int y, int z) {
        return world != null && world.getBlockAt(x, y, z).getType().isSolid();
    }

    /* ================================================================== eating */

    /**
     * Eats an entire island chunk column, top to bottom. {@code sink} returns the removed cells so
     * the caller can throw a few of them as debris.
     *
     * @return number of blocks removed
     */
    public int eatColumn(int cx, int cz, List<int[]> sink) {
        if (cx < 0 || cx > 2 || cz < 0 || cz > 2) {
            return 0;
        }
        int removed = 0;
        int x0 = cx << 4;
        int z0 = cz << 4;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (int y = SiteLayout.ISLAND.y1(); y >= SiteLayout.ISLAND.y0(); y--) {
                    Material was = clear(x, y, z);
                    if (was != null) {
                        removed++;
                        if (sink != null && y >= SiteLayout.FLOOR - 1 && (x + z + y) % 7 == 0) {
                            sink.add(new int[]{x, y, z, was.ordinal()});
                        }
                    }
                }
            }
        }
        return removed;
    }

    /** One horizontal slice of a chunk column (for eating a chunk bottom-up or top-down over ticks). */
    public int eatSlice(int cx, int cz, int y) {
        int removed = 0;
        int x0 = cx << 4;
        int z0 = cz << 4;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                if (clear(x, y, z) != null) {
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * A bite: everything inside the sphere goes, and any plant left standing on nothing goes with it.
     *
     * @return the removed surface cells (for debris)
     */
    public List<int[]> eatSphere(double cx, double cy, double cz, double r) {
        List<int[]> surface = new ArrayList<>();
        int ri = (int) Math.ceil(r);
        int bx = (int) Math.floor(cx);
        int by = (int) Math.floor(cy);
        int bz = (int) Math.floor(cz);
        double r2 = r * r;
        for (int x = bx - ri; x <= bx + ri; x++) {
            for (int z = bz - ri; z <= bz + ri; z++) {
                for (int y = by + ri; y >= by - ri; y--) {
                    double dx = x + 0.5 - cx;
                    double dy = y + 0.5 - cy;
                    double dz = z + 0.5 - cz;
                    if (dx * dx + dy * dy + dz * dz > r2) {
                        continue;
                    }
                    Material was = clear(x, y, z);
                    if (was != null && y >= SiteLayout.FLOOR - 1) {
                        surface.add(new int[]{x, y, z, was.ordinal()});
                    }
                }
                dropFloating(x, z, by + ri + 3);
            }
        }
        return surface;
    }

    /**
     * A trench from a to b (world XZ), {@code width} wide and {@code depth} deep below the floor.
     *
     * @return the removed surface cells
     */
    public List<int[]> eatLane(double ax, double az, double bx, double bz, double width, int depth) {
        List<int[]> surface = new ArrayList<>();
        int minX = (int) Math.floor(Math.min(ax, bx) - width);
        int maxX = (int) Math.ceil(Math.max(ax, bx) + width);
        int minZ = (int) Math.floor(Math.min(az, bz) - width);
        int maxZ = (int) Math.ceil(Math.max(az, bz) + width);
        Vector3f a = new Vector3f((float) ax, 0f, (float) az);
        Vector3f b = new Vector3f((float) bx, 0f, (float) bz);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Vector3f p = new Vector3f(x + 0.5f, 0f, z + 0.5f);
                float d = WeMath.segmentDistanceXZ(p, a, b);
                if (d > width * 0.5f) {
                    continue;
                }
                int dd = d > width * 0.35f ? Math.max(1, depth - 1) : depth;
                for (int y = SiteLayout.FLOOR + 8; y > SiteLayout.FLOOR - dd; y--) {
                    Material was = clear(x, y, z);
                    if (was != null && y >= SiteLayout.FLOOR - 1 && (x + z) % 3 == 0) {
                        surface.add(new int[]{x, y, z, was.ordinal()});
                    }
                }
            }
        }
        return surface;
    }

    /** Removes plants and leaves left floating over an eaten cell in column (x, z). */
    private void dropFloating(int x, int z, int fromY) {
        if (world == null) {
            return;
        }
        for (int y = Math.min(fromY, SiteLayout.ISLAND.y1()); y > SiteLayout.FLOOR; y--) {
            Block above = world.getBlockAt(x, y, z);
            Block below = world.getBlockAt(x, y - 1, z);
            Material m = above.getType();
            if (m.isAir() || !below.getType().isAir()) {
                continue;
            }
            if (!m.isSolid() || m == Material.OAK_FENCE || m == Material.LANTERN) {
                clear(x, y, z);
            }
        }
    }

    /* ================================================================== restoring */

    /** Restores one island chunk column between two heights (inclusive). @return blocks changed */
    public int restoreChunk(int cx, int cz, int yMin, int yMax) {
        int changed = 0;
        int x0 = cx << 4;
        int z0 = cz << 4;
        for (int y = Math.max(yMin, SiteLayout.ISLAND.y0()); y <= Math.min(yMax, SiteLayout.ISLAND.y1()); y++) {
            for (int x = x0; x < x0 + 16; x++) {
                for (int z = z0; z < z0 + 16; z++) {
                    if (restore(x, y, z)) {
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    /** Restores every listed cell (statue, doors, a hole). */
    public int restoreCells(List<int[]> cells) {
        int changed = 0;
        for (int[] c : cells) {
            if (restore(c[0], c[1], c[2])) {
                changed++;
            }
        }
        return changed;
    }

    public int clearCells(List<int[]> cells) {
        int changed = 0;
        for (int[] c : cells) {
            if (clear(c[0], c[1], c[2]) != null) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Makes the whole site match the layout, spread over ticks by the caller: returns a job that
     * restores up to {@code budget} cells per call to {@link Job#step()}.
     */
    public Job restoreAll(Consumer<Integer> onDone) {
        return new Job(this, SiteLayout.SITE, onDone);
    }

    public Job restoreBoxes(Consumer<Integer> onDone, SiteLayout.Box... boxes) {
        return new Job(this, boxes, onDone);
    }

    /** A resumable pass over boxes. */
    public static final class Job {
        private final SiteTerrain terrain;
        private final SiteLayout.Box[] boxes;
        private final Consumer<Integer> onDone;
        private int box;
        private int x;
        private int y;
        private int z;
        private boolean started;
        private boolean done;
        private int changed;

        Job(SiteTerrain terrain, SiteLayout.Box[] boxes, Consumer<Integer> onDone) {
            this.terrain = terrain;
            this.boxes = boxes;
            this.onDone = onDone;
        }

        /** @return true when finished */
        public boolean step(int budget) {
            if (done) {
                return true;
            }
            int work = 0;
            while (work < budget) {
                if (box >= boxes.length) {
                    done = true;
                    if (onDone != null) {
                        onDone.accept(changed);
                    }
                    return true;
                }
                SiteLayout.Box b = boxes[box];
                if (!started) {
                    x = b.x0();
                    y = b.y0();
                    z = b.z0();
                    started = true;
                }
                if (terrain.restore(x, y, z)) {
                    changed++;
                }
                work++;
                if (++z > b.z1()) {
                    z = b.z0();
                    if (++x > b.x1()) {
                        x = b.x0();
                        if (++y > b.y1()) {
                            box++;
                            started = false;
                        }
                    }
                }
            }
            return false;
        }

        public boolean done() {
            return done;
        }

        public int changed() {
            return changed;
        }
    }

    /* ================================================================== helpers */

    public Location center(int[] cell) {
        return new Location(world, cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5);
    }

    /** Material of a sink cell recorded by the eat methods. */
    public static Material material(int[] cell) {
        return Material.values()[cell[3]];
    }
}
