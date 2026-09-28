package de.aetherion.bossengine.helios.world;

import de.aetherion.bossengine.helios.core.BlockCracks;
import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The live arena of one instance: which layout cells are really there, which sectors have fallen or
 * burned, the temporary blocks the fight placed (cover pillars, heat), and the replicas that let real
 * floor fall, tumble and rise again.
 *
 * <p>Destruction is real (the arena is ours, in our own world): when a sector falls the blocks are
 * gone and you fall with it. Everything is recorded so {@link #restoreAll} and the crash sweep can put
 * the layout back exactly.
 */
public final class Arena {

    public enum SectorState { INTACT, FALLEN, BURNED, SHATTERED }

    private final World world;
    private final int cx;
    private final int cy;
    private final int cz;
    private final HeliosStage stage;
    private final BlockCracks cracks;
    private final SectorState[][] sectors = new SectorState[3][];
    private final Map<Long, ArenaLayout.Cell> missing = new HashMap<>();
    /** Non-layout blocks we placed (cover, heat swaps), with what was there before. */
    private final Map<Long, BlockData> temp = new LinkedHashMap<>();
    /** Cells waiting to be restored because someone stands in them. */
    private final Map<Long, ArenaLayout.Cell> pending = new LinkedHashMap<>();
    private final boolean[] ringGone = new boolean[3];

    public Arena(World world, int cx, int cy, int cz, HeliosStage stage) {
        this.world = world;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.stage = stage;
        this.cracks = new BlockCracks(world, stage::audience);
        for (int r = 0; r < 3; r++) {
            sectors[r] = new SectorState[ArenaLayout.SECTORS[r]];
            java.util.Arrays.fill(sectors[r], SectorState.INTACT);
        }
    }

    public World world() {
        return world;
    }

    public BlockCracks cracks() {
        return cracks;
    }

    public int cx() {
        return cx;
    }

    public int cy() {
        return cy;
    }

    public int cz() {
        return cz;
    }

    public void tick() {
        cracks.tick();
        if (!pending.isEmpty()) {
            for (Iterator<Map.Entry<Long, ArenaLayout.Cell>> it = pending.entrySet().iterator(); it.hasNext(); ) {
                ArenaLayout.Cell c = it.next().getValue();
                if (!occupied(c.dx(), c.dy(), c.dz())) {
                    place(c);
                    it.remove();
                }
            }
        }
    }

    /* ================================================================== queries */

    public SectorState state(int ring, int sector) {
        return sectors[ring][Math.floorMod(sector, sectors[ring].length)];
    }

    public boolean ringGone(int ring) {
        return ringGone[ring];
    }

    /** Is there floor under stage point (x, z)? */
    public boolean solidAt(float x, float z) {
        Block b = world.getBlockAt(cx + (int) Math.floor(x), cy - 1, cz + (int) Math.floor(z));
        return b.getType().isSolid();
    }

    /** Nearest standable surface cell to {@code near} (stage coords), or the arena spawn. */
    public Vector3f safeSpot(Vector3fc near) {
        Vector3f best = null;
        float bestD = Float.MAX_VALUE;
        for (ArenaLayout.Cell c : ArenaLayout.surface()) {
            if (missing.containsKey(key(c))) {
                continue;
            }
            Block floor = world.getBlockAt(cx + c.dx(), cy - 1, cz + c.dz());
            if (!floor.getType().isSolid() || floor.getType() == Material.MAGMA_BLOCK) {
                continue;
            }
            if (!world.getBlockAt(cx + c.dx(), cy, cz + c.dz()).isPassable()
                    || !world.getBlockAt(cx + c.dx(), cy + 1, cz + c.dz()).isPassable()) {
                continue;
            }
            float dx = c.dx() + 0.5f - near.x();
            float dz = c.dz() + 0.5f - near.z();
            float d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = new Vector3f(c.dx() + 0.5f, 0f, c.dz() + 0.5f);
            }
        }
        return best == null ? spawnPoint() : best;
    }

    /** Where players enter: south side of the Corona, facing the star. */
    public static Vector3f spawnPoint() {
        return new Vector3f(0.5f, 0f, 27.5f);
    }

    public Location toLocation(Vector3fc stagePoint, float yaw) {
        Location l = stage.at(stagePoint);
        l.setYaw(yaw);
        return l;
    }

    public List<ArenaLayout.Cell> sectorCells(int ring, int sector, boolean surfaceOnly) {
        List<ArenaLayout.Cell> out = new ArrayList<>();
        for (ArenaLayout.Cell c : ArenaLayout.cells()) {
            if (c.ring() == ring && c.sector() == sector && (!surfaceOnly || c.layer() == 0)) {
                out.add(c);
            }
        }
        return out;
    }

    public List<ArenaLayout.Cell> ringCells(int ring, boolean surfaceOnly) {
        List<ArenaLayout.Cell> out = new ArrayList<>();
        for (ArenaLayout.Cell c : ArenaLayout.cells()) {
            if (c.ring() == ring && (!surfaceOnly || c.layer() == 0)) {
                out.add(c);
            }
        }
        return out;
    }

    /* ================================================================== cracks */

    public void crackSector(int ring, int sector, float progress, int ticks) {
        for (ArenaLayout.Cell c : sectorCells(ring, sector, true)) {
            if (!missing.containsKey(key(c))) {
                // Cracks spread from the sector's middle outward: the edge lags a little.
                float mid = ArenaLayout.ringMid(ring);
                float lag = Math.abs(c.radius() - mid) / 8f;
                cracks.set(cx + c.dx(), cy + c.dy(), cz + c.dz(), progress - lag * 0.4f, ticks);
            }
        }
    }

    public void crackCell(int dx, int dz, float progress, int ticks) {
        cracks.set(cx + dx, cy - 1, cz + dz, progress, ticks);
    }

    /** Cracks along a line on the floor (seismic fissures). */
    public void crackLine(Vector3fc from, Vector3fc to, float progress, int ticks) {
        float len = new Vector3f(to).sub(from).length();
        int steps = Math.max(1, (int) (len * 1.4f));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = (int) Math.floor(HMath.lerp(from.x(), to.x(), t));
            int z = (int) Math.floor(HMath.lerp(from.z(), to.z(), t));
            if (world.getBlockAt(cx + x, cy - 1, cz + z).getType().isSolid()) {
                cracks.set(cx + x, cy - 1, cz + z, progress, ticks);
            }
        }
    }

    /* ================================================================== real edits */

    /** Removes cells for real. The removed cells are remembered for restore. */
    public void remove(List<ArenaLayout.Cell> cells) {
        for (ArenaLayout.Cell c : cells) {
            long k = key(c);
            pending.remove(k);
            missing.put(k, c);
            Block b = world.getBlockAt(cx + c.dx(), cy + c.dy(), cz + c.dz());
            if (b.getType() != Material.AIR) {
                b.setType(Material.AIR, false);
            }
            cracks.clear(cx + c.dx(), cy + c.dy(), cz + c.dz());
        }
    }

    /** Puts cells back; cells a player stands in are retried every tick until free. */
    public void restore(List<ArenaLayout.Cell> cells) {
        for (ArenaLayout.Cell c : cells) {
            long k = key(c);
            if (occupied(c.dx(), c.dy(), c.dz())) {
                pending.put(k, c);
                continue;
            }
            place(c);
        }
    }

    private void place(ArenaLayout.Cell c) {
        missing.remove(key(c));
        Block b = world.getBlockAt(cx + c.dx(), cy + c.dy(), cz + c.dz());
        if (!b.getBlockData().matches(c.data())) {
            b.setBlockData(c.data(), false);
        }
    }

    public void markSector(int ring, int sector, SectorState state) {
        sectors[ring][Math.floorMod(sector, sectors[ring].length)] = state;
    }

    public void markRingGone(int ring) {
        ringGone[ring] = true;
        java.util.Arrays.fill(sectors[ring], SectorState.BURNED);
    }

    /** Swaps a surface cell to another material for now (heat, ash); restored with the arena. */
    public void swap(int dx, int dy, int dz, Material m) {
        Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
        if (b.getType() == Material.AIR) {
            return;
        }
        long k = ArenaLayout.key(dx, dy, dz);
        temp.putIfAbsent(k, b.getBlockData());
        b.setType(m, false);
    }

    /** A temporary non-layout block (cover pillars). */
    public boolean placeTemp(int dx, int dy, int dz, BlockData data) {
        if (occupied(dx, dy, dz)) {
            return false;
        }
        Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
        long k = ArenaLayout.key(dx, dy, dz);
        temp.putIfAbsent(k, b.getBlockData());
        b.setBlockData(data, false);
        return true;
    }

    /** Reverts a temporary block. If the cell has been destroyed since (air), it stays gone. */
    public void clearTemp(int dx, int dy, int dz) {
        long k = ArenaLayout.key(dx, dy, dz);
        BlockData before = temp.remove(k);
        if (before == null) {
            return;
        }
        Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
        boolean layoutCell = ArenaLayout.at(dx, dy, dz) != null;
        if (layoutCell && b.getType() == Material.AIR) {
            return;
        }
        b.setBlockData(before, false);
    }

    /** Drops temp records for cells about to be removed (they are restored from the layout later). */
    public void revertTempsIn(List<ArenaLayout.Cell> cells) {
        for (ArenaLayout.Cell c : cells) {
            temp.remove(ArenaLayout.key(c.dx(), c.dy(), c.dz()));
        }
    }

    /** Is the block at stage cell (dx, dy, dz) inside any fighter's body? */
    public boolean occupied(int dx, int dy, int dz) {
        BoundingBox cell = new BoundingBox(cx + dx, cy + dy, cz + dz, cx + dx + 1, cy + dy + 1, cz + dz + 1);
        for (Player p : stage.audience()) {
            if (p.getBoundingBox().overlaps(cell)) {
                return true;
            }
        }
        return false;
    }

    /** Line of sight between two stage points through the real arena blocks (cover). */
    public boolean blocked(Vector3fc from, Vector3fc to) {
        Location a = stage.at(from);
        Vector3f d = new Vector3f(to).sub(from);
        float len = d.length();
        if (len < 0.1f) {
            return false;
        }
        org.bukkit.util.RayTraceResult hit = world.rayTraceBlocks(a, new org.bukkit.util.Vector(d.x / len, d.y / len, d.z / len),
                len, org.bukkit.FluidCollisionMode.NEVER, true);
        return hit != null && hit.getHitBlock() != null;
    }

    /* ================================================================== restore */

    /** Everything the fight changed: missing layout cells back, temp blocks reverted, cracks gone. */
    public List<ArenaLayout.Cell> damaged() {
        return new ArrayList<>(missing.values());
    }

    public void revertTemps() {
        for (Map.Entry<Long, BlockData> e : new ArrayList<>(temp.entrySet())) {
            long k = e.getKey();
            int dx = (int) ((k >> 32) & 0xFFFF) - 512;
            int dy = (int) ((k >> 16) & 0xFFFF) - 512;
            int dz = (int) (k & 0xFFFF) - 512;
            world.getBlockAt(cx + dx, cy + dy, cz + dz).setBlockData(e.getValue(), false);
        }
        temp.clear();
    }

    public void forgetDamage() {
        missing.clear();
        pending.clear();
        for (int r = 0; r < 3; r++) {
            java.util.Arrays.fill(sectors[r], SectorState.INTACT);
            ringGone[r] = false;
        }
    }

    private static long key(ArenaLayout.Cell c) {
        return ArenaLayout.key(c.dx(), c.dy(), c.dz());
    }

    /* ================================================================== replicas */

    /**
     * A display copy of some arena cells, merged into 2x2 blocks so a whole sector costs a couple of
     * dozen displays. It sits exactly where the real blocks were, so the swap real → replica is
     * invisible; then it can fall, tumble, burn or rise.
     */
    public Replica replica(HeliosStage.Group g, List<ArenaLayout.Cell> cells, boolean surfaceOnly) {
        Map<Long, List<ArenaLayout.Cell>> groups = new LinkedHashMap<>();
        Set<Integer> layers = new HashSet<>();
        for (ArenaLayout.Cell c : cells) {
            if (surfaceOnly && c.layer() != 0) {
                continue;
            }
            if (c.layer() < 0) {
                continue;
            }
            layers.add(c.layer());
            long k = ArenaLayout.key(Math.floorDiv(c.dx(), 2), c.dy(), Math.floorDiv(c.dz(), 2));
            groups.computeIfAbsent(k, ignored -> new ArrayList<>()).add(c);
        }
        Replica rep = new Replica(stage);
        for (List<ArenaLayout.Cell> chunk : groups.values()) {
            float minX = Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            Map<Material, Integer> votes = new HashMap<>();
            for (ArenaLayout.Cell c : chunk) {
                minX = Math.min(minX, c.dx());
                minZ = Math.min(minZ, c.dz());
                maxX = Math.max(maxX, c.dx() + 1);
                maxZ = Math.max(maxZ, c.dz() + 1);
                votes.merge(c.data().getMaterial(), 1, Integer::sum);
            }
            Material m = votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Material.DEEPSLATE);
            int bright = m == Material.OCHRE_FROGLIGHT ? 15 : -1;
            BlockDisplay d = g.block(m.createBlockData(), null, bright, true);
            if (d == null) {
                continue;
            }
            float y = chunk.get(0).dy();
            Vector3f center = new Vector3f((minX + maxX) * 0.5f, y + 0.5f, (minZ + maxZ) * 0.5f);
            Vector3f size = new Vector3f(maxX - minX, 1f, maxZ - minZ);
            rep.add(d, center, size);
        }
        return rep;
    }

    public static final class Replica {
        private final HeliosStage stage;
        private final List<BlockDisplay> parts = new ArrayList<>();
        private final List<Vector3f> centers = new ArrayList<>();
        private final List<Vector3f> sizes = new ArrayList<>();
        private final Vector3f pivot = new Vector3f();

        Replica(HeliosStage stage) {
            this.stage = stage;
        }

        void add(BlockDisplay d, Vector3f center, Vector3f size) {
            parts.add(d);
            centers.add(center);
            sizes.add(size);
            pivot.set(0f);
            for (Vector3f c : centers) {
                pivot.add(c);
            }
            pivot.div(centers.size());
        }

        public Vector3f pivot() {
            return new Vector3f(pivot);
        }

        public int size() {
            return parts.size();
        }

        /** Rigid pose: rotate about the pivot by {@code rot}, move by {@code offset}, scale each piece. */
        public void pose(Quaternionf rot, Vector3fc offset, float scale, int interp) {
            for (int i = 0; i < parts.size(); i++) {
                Vector3f rel = new Vector3f(centers.get(i)).sub(pivot);
                Vector3f at = new Quaternionf(rot).transform(rel).add(pivot).add(offset);
                Vector3f size = new Vector3f(sizes.get(i)).mul(scale);
                stage.push(parts.get(i), HeliosStage.box(at, size, rot), interp);
            }
        }

        /** Every piece on its own: the sector crumbles apart while it falls. */
        public void scatter(float t, Vector3fc offset, float spread, int interp) {
            for (int i = 0; i < parts.size(); i++) {
                Vector3f c = centers.get(i);
                float h = HMath.hash(i, 77);
                Vector3f out = new Vector3f(c).sub(pivot).mul(spread * t);
                Vector3f at = new Vector3f(c).add(out).add(offset).add(0f, -t * t * 6f * h, 0f);
                Quaternionf rot = new Quaternionf().rotateXYZ(t * (h - 0.5f) * 3f, t * h * 2f, t * (0.5f - h) * 3f);
                stage.push(parts.get(i), HeliosStage.box(at, new Vector3f(sizes.get(i)).mul(1f - 0.4f * t), rot), interp);
            }
        }

        public void material(Material m) {
            for (BlockDisplay d : parts) {
                HeliosStage.material(d, m);
            }
        }

        public void brightness(int level) {
            for (BlockDisplay d : parts) {
                HeliosStage.brightness(d, level);
            }
        }

        public void glow(org.bukkit.Color c) {
            for (BlockDisplay d : parts) {
                HeliosStage.glow(d, c);
            }
        }
    }
}
