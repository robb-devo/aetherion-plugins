package de.aetherion.bossengine.helios.world;

import de.aetherion.bossengine.helios.core.HMath;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The arena as data. This list IS the snapshot: building, restoring after the fight and repairing
 * after a crash all rebuild from it, so there is no schematic to lose or corrupt.
 *
 * <pre>
 *   The Observatory of a Dead Sun: three concentric walkable rings around an open pit, free-floating
 *   in the void. The star hangs above the pit.
 *
 *            r  0 ..  8   pit (open void), the star above it
 *   RING 0   r  9 .. 15   the Crown   (8 sectors)   gilded pit rim, polished blackstone
 *   RING 1   r 16 .. 23   the Course  (12 sectors)  polished deepslate
 *   RING 2   r 24 .. 31   the Corona  (16 sectors)  deepslate tiles, chiseled rim
 *
 *   Every ring starts with a glowing seam (ochre froglight) and is cut into sectors by thin quartz
 *   spokes, so the floor itself shows the geometry the fight will break along.
 *   Underneath: a blackstone keel with hanging basalt teeth so the arena reads as an island from below.
 * </pre>
 *
 * Coordinates are relative to the arena center block; the walkable surface is at dy = 0 (the top
 * floor blocks sit at dy = -1).
 */
public final class ArenaLayout {

    public static final int RADIUS = 31;
    public static final int[] RING_IN = {9, 16, 24};
    public static final int[] RING_OUT = {15, 23, 31};
    public static final int[] SECTORS = {8, 12, 16};
    public static final int PIT = 8;
    /** Region the arena may touch (clears scan it): horizontal half-size, below, above. */
    public static final int BOX_R = 40;
    public static final int BOX_DOWN = 26;
    public static final int BOX_UP = 48;

    /** One block of the arena. {@code layer} 0 = walking surface, 1 = under-floor, 2+ = keel. */
    public record Cell(int dx, int dy, int dz, BlockData data, int ring, int sector, int layer) {
        public float radius() {
            return (float) Math.sqrt(dx * dx + dz * dz);
        }

        public float angle() {
            return HMath.angleOf(dx, dz);
        }
    }

    private static List<Cell> cached;
    private static Map<Long, Cell> byKey;

    private ArenaLayout() {
    }

    public static synchronized List<Cell> cells() {
        if (cached == null) {
            cached = Collections.unmodifiableList(build());
            Map<Long, Cell> map = new HashMap<>();
            for (Cell c : cached) {
                map.put(key(c.dx, c.dy, c.dz), c);
            }
            byKey = map;
        }
        return cached;
    }

    public static Cell at(int dx, int dy, int dz) {
        cells();
        return byKey.get(key(dx, dy, dz));
    }

    public static long key(int dx, int dy, int dz) {
        return ((long) (dx + 512) << 32) | ((long) (dy + 512) << 16) | (dz + 512);
    }

    /** Ring index for a horizontal radius, or -1 (pit / outside). */
    public static int ringOf(float r) {
        for (int i = 0; i < RING_IN.length; i++) {
            if (r >= RING_IN[i] - 0.5f && r < RING_OUT[i] + 0.5f) {
                return i;
            }
        }
        return -1;
    }

    public static int sectorOf(int ring, float angle) {
        int n = SECTORS[ring];
        float a = angle < 0 ? angle + HMath.TAU : angle;
        return Math.min(n - 1, (int) (a / (HMath.TAU / n)));
    }

    /** Center angle of a sector. */
    public static float sectorAngle(int ring, int sector) {
        float span = HMath.TAU / SECTORS[ring];
        return span * (sector + 0.5f);
    }

    public static float ringMid(int ring) {
        return (RING_IN[ring] + RING_OUT[ring]) * 0.5f;
    }

    private static List<Cell> build() {
        List<Cell> out = new ArrayList<>();
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                float r = (float) Math.sqrt(dx * dx + dz * dz);
                int ring = ringOf(r);
                if (ring < 0) {
                    continue;
                }
                float ang = HMath.angleOf(dx, dz);
                int sector = sectorOf(ring, ang);
                out.add(new Cell(dx, -1, dz, surface(ring, r, ang, dx, dz).createBlockData(), ring, sector, 0));
                out.add(new Cell(dx, -2, dz, under(ring, r).createBlockData(), ring, sector, 1));
                keel(out, dx, dz, r, ring, sector);
                light(out, dx, dz, r, ring, sector);
            }
        }
        return out;
    }

    private static Material surface(int ring, float r, float ang, int dx, int dz) {
        int in = RING_IN[ring];
        int outR = RING_OUT[ring];
        // Seam: the first block of every ring glows.
        if (r < in + 0.5f) {
            return ring == 0 ? Material.GILDED_BLACKSTONE : Material.OCHRE_FROGLIGHT;
        }
        if (ring == 2 && r >= outR - 0.5f) {
            return Material.CHISELED_POLISHED_BLACKSTONE;
        }
        // Spokes along sector boundaries (the fault lines).
        int n = SECTORS[ring];
        float span = HMath.TAU / n;
        float a = ang < 0 ? ang + HMath.TAU : ang;
        float off = a - span * Math.round(a / span);
        if (Math.abs(off) * r < 0.55f) {
            return Material.SMOOTH_QUARTZ;
        }
        return switch (ring) {
            case 0 -> ((dx + dz) & 7) == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE;
            case 1 -> Math.abs(r - ringMid(1)) < 0.5f ? Material.CHISELED_DEEPSLATE : Material.POLISHED_DEEPSLATE;
            default -> Math.abs(r - 27.5f) < 0.5f ? Material.DEEPSLATE_BRICKS : Material.DEEPSLATE_TILES;
        };
    }

    private static Material under(int ring, float r) {
        return ring == 0 ? Material.BLACKSTONE : Material.DEEPSLATE;
    }

    /** The keel: thicker toward each ring's middle, and hanging teeth under each sector. */
    private static void keel(List<Cell> out, int dx, int dz, float r, int ring, int sector) {
        float mid = ringMid(ring);
        float half = (RING_OUT[ring] - RING_IN[ring]) * 0.5f;
        float depth = 1.5f + 2.5f * (1f - Math.abs(r - mid) / half);
        float jitter = HMath.hash(dx * 31 + 7, dz * 17 + 3) * 1.4f;
        int d = (int) Math.floor(depth + jitter - 0.7f);
        for (int i = 0; i < d; i++) {
            out.add(new Cell(dx, -3 - i, dz, Material.BLACKSTONE.createBlockData(), ring, sector, 2 + i));
        }
        // A tooth hangs under roughly one cell in fifteen near the ring's middle.
        if (Math.abs(r - mid) < 1.2f && HMath.hash(dx * 13 + 1, dz * 29 + 5) > 0.93f) {
            int len = 2 + (int) (HMath.hash(dx, dz) * 4f);
            for (int i = 0; i < len; i++) {
                Material m = i == len - 1 ? Material.POINTED_DRIPSTONE : Material.BASALT;
                BlockData data = m.createBlockData();
                if (data instanceof org.bukkit.block.data.type.PointedDripstone drip) {
                    drip.setVerticalDirection(org.bukkit.block.BlockFace.DOWN);
                    drip.setThickness(org.bukkit.block.data.type.PointedDripstone.Thickness.TIP);
                }
                out.add(new Cell(dx, -3 - d - i, dz, data, ring, sector, 2 + d + i));
            }
        }
    }

    /** Invisible light over the floor so the rings read at night (the star itself casts no world light). */
    private static void light(List<Cell> out, int dx, int dz, float r, int ring, int sector) {
        if (Math.floorMod(dx, 6) == 0 && Math.floorMod(dz, 6) == 0) {
            BlockData data = Material.LIGHT.createBlockData();
            if (data instanceof org.bukkit.block.data.Levelled lvl) {
                lvl.setLevel(ring == 0 ? 11 : 9);
            }
            out.add(new Cell(dx, 3, dz, data, ring, sector, -1));
        }
    }

    /** Surface cells (layer 0) only. */
    public static List<Cell> surface() {
        List<Cell> out = new ArrayList<>();
        for (Cell c : cells()) {
            if (c.layer == 0) {
                out.add(c);
            }
        }
        return out;
    }
}
