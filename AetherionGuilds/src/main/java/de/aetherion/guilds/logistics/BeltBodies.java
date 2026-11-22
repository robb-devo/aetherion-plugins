package de.aetherion.guilds.logistics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * What a conveyor run looks like, as display parts in the run's own space: x/z from the run's lowest tile corner,
 * y from the floor of the belt tile (the iron plate under it is 3/16 high). Pure geometry, no world access:
 * {@link BeltVisuals} spawns the parts near players and moves the cleats.
 * <ul>
 *   <li>a dark rubber <b>belt</b> between two timber <b>side rails</b> (grain along the rail) with a copper cap
 *   and a dark iron <b>post</b> at every tile joint; opened where a side belt merges in,
 *   with a short belt tongue there,</li>
 *   <li>chevron <b>cleats</b> that run at the cargo's pace, rise out of the tail <b>drum</b> and dive into the head
 *   drum; a few of them are gold <b>markers</b> that glow while the line carries goods,</li>
 *   <li>an end wall at the tail, and a <b>chamfered</b> outer corner where the line bends,</li>
 *   <li>a copper <b>inlet hood</b> with a chain curtain and a green lip where the belt plugs into a machine; under
 *   a chute the end wall wears an orange lip.</li>
 * </ul>
 */
final class BeltBodies {

    /** Belt speed: one tile per this many ticks. Cargo glides at the same pace, so goods ride the belt. */
    static final int TICKS_PER_TILE = 4;

    /** Belt surface: bed from {@link #BED_Y}, {@link #BED_H} thick. */
    static final float BED_Y = 0.19f;
    static final float BED_H = 0.055f;
    static final float TOP = BED_Y + BED_H;
    private static final float BED_IN = 0.155f;
    private static final float RAIL_OUT = 0.04f;
    private static final float RAIL_IN = 0.13f;
    private static final float RAIL_Y = 0.17f;
    private static final float RAIL_H = 0.16f;
    private static final float CAP_H = 0.03f;
    private static final float RAIL_TOP = RAIL_Y + RAIL_H;
    private static final float CHAMFER = 0.25f;
    private static final int RAIL_SPAN = 4;

    // the cleat cycle along the run: hidden at the tail, risen, starts diving, hidden at the head (flow units)
    static final float CLEAT_TAIL = 0.17f;
    static final float CLEAT_START = 0.42f;
    private static final float CLEAT_END_GAP = 0.33f;
    private static final float CLEAT_GONE_GAP = 0.08f;
    private static final float CLEAT_REACH = 0.16f;
    private static final float CLEAT_HALF = 0.29f;
    private static final float CLEAT_W = 0.065f;
    private static final float CLEAT_H = 0.024f;
    private static final float CLEAT_SINK = 0.035f;

    static final BlockData BELT = Material.BLACK_CONCRETE.createBlockData();
    private static final BlockData RAIL_X = Bukkit.createBlockData("minecraft:stripped_dark_oak_log[axis=x]");
    private static final BlockData RAIL_Z = Bukkit.createBlockData("minecraft:stripped_dark_oak_log[axis=z]");
    private static final BlockData CAP = Material.WAXED_CUT_COPPER.createBlockData();
    private static final BlockData POST = Material.POLISHED_DEEPSLATE.createBlockData();
    private static final BlockData DRUM_X = Bukkit.createBlockData("minecraft:polished_basalt[axis=x]");
    private static final BlockData DRUM_Z = Bukkit.createBlockData("minecraft:polished_basalt[axis=z]");
    private static final BlockData COPPER = Material.WAXED_CUT_COPPER.createBlockData();
    private static final BlockData CHAIN = Bukkit.createBlockData("minecraft:chain[axis=y]");
    private static final BlockData LIP_IN = Material.LIME_CONCRETE.createBlockData();
    private static final BlockData LIP_OUT = Material.ORANGE_CONCRETE.createBlockData();
    static final BlockData CLEAT = Material.LIGHT_GRAY_CONCRETE.createBlockData();
    static final BlockData MARKER = Material.GOLD_BLOCK.createBlockData();

    private BeltBodies() {
    }

    /** A straight stretch of tiles with one direction. {@code minX/minZ} = its lowest tile corner. */
    record Run(int minX, int y, int minZ, BlockFace dir, int length, List<Long> tiles, boolean[] leftOpen,
               boolean[] rightOpen, List<int[]> stubs, boolean turn, boolean plugged, boolean chute) {

        boolean alongX() {
            return dir == BlockFace.EAST || dir == BlockFace.WEST;
        }

        boolean forward() {
            return dir == BlockFace.EAST || dir == BlockFace.SOUTH;
        }

        /** Box coordinate along the run of a flow position (0 = tail, length = head). */
        float at(float flow) {
            return forward() ? flow : length - flow;
        }

        String key() {
            return minX + "," + y + "," + minZ + "," + dir.ordinal() + "," + length + "," + stubs.size()
                    + "," + open(leftOpen) + "," + open(rightOpen) + "," + turn + plugged + chute;
        }

        private static String open(boolean[] flags) {
            StringBuilder out = new StringBuilder(flags.length);
            for (boolean flag : flags) {
                out.append(flag ? '1' : '0');
            }
            return out.toString();
        }

        double distanceSq(double x, double z) {
            double maxX = minX + (alongX() ? length : 1);
            double maxZ = minZ + (alongX() ? 1 : length);
            double dx = x < minX ? minX - x : Math.max(0, x - maxX);
            double dz = z < minZ ? minZ - z : Math.max(0, z - maxZ);
            return dx * dx + dz * dz;
        }
    }

    /** One still display of a run: its block, where it sits, lit or not. */
    record Piece(BlockData block, Transformation transformation, boolean glow) {
    }

    // ------------------------------------------------------------------------------------------------
    // the still parts
    // ------------------------------------------------------------------------------------------------

    static List<Piece> frame(Run run) {
        List<Piece> out = new ArrayList<>();
        boolean x = run.alongX();
        int n = run.length();
        float len = n;
        // the head end in box coordinates, and how far rails stop short of it under a hood (no flicker)
        boolean headHigh = run.forward();
        float cut = run.plugged() ? 0.012f : 0f;
        float lo = headHigh ? 0f : cut;
        float hi = headHigh ? len - cut : len;
        out.add(new Piece(BELT, box(x, 0f, len, BED_IN, 1f - BED_IN, BED_Y, BED_H), false));

        // side rails with the grain along them (a piece spans at most a few tiles, so the grain keeps its look),
        // a copper cap per solid stretch, posts at the joints
        BlockData rail = x ? RAIL_X : RAIL_Z;
        for (int side = 0; side < 2; side++) {
            boolean[] open = side == 0 ? run.leftOpen() : run.rightOpen();
            float c0 = side == 0 ? RAIL_OUT : 1f - RAIL_IN;
            float c1 = side == 0 ? RAIL_IN : 1f - RAIL_OUT;
            int start = -1;
            for (int i = 0; i <= n; i++) {
                boolean solid = i < n && !open[i];
                if (solid && start < 0) {
                    start = i;
                } else if (!solid && start >= 0) {
                    for (int from = start; from < i; from += RAIL_SPAN) {
                        int to = Math.min(i, from + RAIL_SPAN);
                        out.add(new Piece(rail, box(x, Math.max(lo, from), Math.min(hi, to), c0, c1, RAIL_Y, RAIL_H), false));
                    }
                    out.add(new Piece(CAP, box(x, Math.max(lo, start), Math.min(hi, i), c0 - 0.01f, c1 + 0.01f,
                            RAIL_TOP, CAP_H), false));
                    start = -1;
                }
            }
            // posts where two rail pieces meet (the run's ends get the drum bearings instead)
            float p0 = side == 0 ? 0.02f : 1f - 0.15f;
            for (int j = 1; j < n; j++) {
                if (open[j - 1] && open[j]) {
                    continue;
                }
                out.add(new Piece(POST, box(x, j - 0.035f, j + 0.035f, p0, p0 + 0.13f, BED_Y, RAIL_TOP + CAP_H + 0.015f - BED_Y), false));
            }
        }

        // the tongue of belt where a side belt merges in
        for (int[] stub : run.stubs()) {
            float c0 = stub[1] < 0 ? 0f : 1f - BED_IN;
            float c1 = stub[1] < 0 ? BED_IN : 1f;
            out.add(new Piece(BELT, box(x, stub[0] + BED_IN, stub[0] + 1f - BED_IN, c0, c1, BED_Y, BED_H), false));
        }

        // drums: the belt wraps round them at both ends; bearings with a copper axle cap on the solid sides
        drum(out, run, 0.19f, 0, true);
        drum(out, run, len - 0.1f, n - 1, !run.plugged());

        // tail: an end wall closes the frame; on a bend a chamfer rounds off the outer corner
        int tail = run.forward() ? 0 : n - 1;
        boolean leftIn = run.leftOpen()[tail];
        boolean rightIn = run.rightOpen()[tail];
        float w0 = leftIn ? 0f : RAIL_IN;
        float w1 = rightIn ? 1f : 1f - RAIL_IN;
        out.add(new Piece(rail(run, true), span(run, RAIL_OUT, RAIL_IN, w0, w1, RAIL_Y, RAIL_H), false));
        // (under a chute its cap is the orange landing lip)
        out.add(new Piece(run.chute() ? LIP_OUT : CAP, span(run, RAIL_OUT - 0.01f, RAIL_IN + 0.01f,
                w0 + (leftIn ? 0f : 0.01f), w1 - (rightIn ? 0f : 0.01f), RAIL_TOP, CAP_H), run.chute()));
        if (run.turn() && leftIn != rightIn) {
            // a chamfered rail across the outer corner (centre line from the end wall to the side rail)
            float mid = (RAIL_OUT + RAIL_IN) / 2f;
            float outer = leftIn ? 1f - mid : mid;
            float inward = leftIn ? -1f : 1f;
            float[] from = xz(run, mid, outer + inward * CHAMFER);
            float[] to = xz(run, mid + CHAMFER, outer);
            float tuck = (RAIL_IN - RAIL_OUT) / 2f;
            out.add(new Piece(RAIL_X, bar(from, to, RAIL_Y, RAIL_H, RAIL_IN - RAIL_OUT, tuck, tuck), false));
            out.add(new Piece(CAP, bar(from, to, RAIL_TOP, CAP_H, RAIL_IN - RAIL_OUT + 0.02f, tuck, tuck), false));
        }

        // head under a machine: a copper hood with a chain curtain and a green lip
        if (run.plugged()) {
            float f0 = len - 0.13f;
            float f1 = len - 0.005f;
            out.add(new Piece(COPPER, span(run, f0, f1, 0.02f, 0.145f, BED_Y, 0.56f), false));
            out.add(new Piece(COPPER, span(run, f0, f1, 0.855f, 0.98f, BED_Y, 0.56f), false));
            out.add(new Piece(COPPER, span(run, f0 - 0.01f, f1, 0.01f, 0.99f, BED_Y + 0.56f, 0.1f), false));
            out.add(new Piece(LIP_IN, span(run, f0, f1, BED_IN, 1f - BED_IN, TOP, 0.025f), true));
            for (float c : new float[]{0.31f, 0.5f, 0.69f}) {
                float[] at = xz(run, len - 0.07f, c);
                out.add(new Piece(CHAIN, new Transformation(new Vector3f(at[0] - 0.3f, TOP + 0.13f, at[1] - 0.3f),
                        new Quaternionf(), new Vector3f(0.6f, BED_Y + 0.56f - TOP - 0.13f, 0.6f), new Quaternionf()), false));
            }
        }
        return out;
    }

    /** A drum across the belt {@code flow} tiles from the tail, bearings on the solid sides of tile {@code tile}. */
    private static void drum(List<Piece> out, Run run, float flow, int tileFlow, boolean bearings) {
        boolean x = run.alongX();
        out.add(new Piece(x ? DRUM_Z : DRUM_X, span(run, flow - 0.065f, flow + 0.065f, RAIL_IN, 1f - RAIL_IN,
                TOP + 0.04f - 0.15f, 0.15f), false));
        if (!bearings) {
            return;
        }
        int tile = run.forward() ? tileFlow : run.length() - 1 - tileFlow;
        for (int side = 0; side < 2; side++) {
            boolean open = side == 0 ? run.leftOpen()[tile] : run.rightOpen()[tile];
            if (open) {
                continue;
            }
            float c0 = side == 0 ? 0.015f : 1f - 0.15f;
            out.add(new Piece(POST, span(run, flow - 0.065f, flow + 0.065f, c0, c0 + 0.135f, BED_Y, 0.15f), false));
            float a0 = side == 0 ? 0.004f : 1f - 0.015f;
            out.add(new Piece(COPPER, span(run, flow - 0.035f, flow + 0.035f, a0, a0 + 0.011f, BED_Y + 0.04f, 0.07f), false));
        }
    }

    private static BlockData rail(Run run, boolean across) {
        boolean x = run.alongX() != across;
        return x ? RAIL_X : RAIL_Z;
    }

    // ------------------------------------------------------------------------------------------------
    // the cleats
    // ------------------------------------------------------------------------------------------------

    /** Cleats on a run: one per tile. */
    static int cleats(Run run) {
        return run.length();
    }

    /** Where cleat {@code k} waits while the line idles. */
    static float rest(int k) {
        return CLEAT_START + k;
    }

    /** Fully risen cleats glide from {@link #CLEAT_START} to here, then dive. */
    static float glideEnd(Run run) {
        return run.length() - CLEAT_END_GAP;
    }

    /** Hidden in the head drum. */
    static float gone(Run run) {
        return run.length() - CLEAT_GONE_GAP;
    }

    /** About every third cleat is a gold marker, spread evenly round the loop. */
    static boolean marker(Run run, int k) {
        int n = cleats(run);
        int m = Math.max(1, Math.round(n / 3f));
        return k == 0 || (k * m) / n != ((k - 1) * m) / n;
    }

    /** The two arms of a chevron cleat, tip {@code flow} tiles from the tail, {@code sunk} 0..1 into the belt. */
    static Transformation[] cleat(Run run, float flow, float sunk) {
        float y = TOP + 0.001f - sunk * CLEAT_SINK;
        float[] tip = xz(run, flow, 0.5f);
        Transformation[] arms = new Transformation[2];
        for (int i = 0; i < 2; i++) {
            float[] end = xz(run, flow - CLEAT_REACH, 0.5f + (i == 0 ? -CLEAT_HALF : CLEAT_HALF));
            arms[i] = bar(tip, end, y, CLEAT_H, CLEAT_W, CLEAT_W * 0.45f, 0f);
        }
        return arms;
    }

    // ------------------------------------------------------------------------------------------------
    // geometry
    // ------------------------------------------------------------------------------------------------

    /** Run-space (x, z) of a point {@code flow} tiles from the tail and {@code across} 0..1 over the belt. */
    private static float[] xz(Run run, float flow, float across) {
        float a = run.at(flow);
        return run.alongX() ? new float[]{a, across} : new float[]{across, a};
    }

    /** A box between two flow positions (either order), {@code [c0, c1]} across, from {@code y}, {@code h} high. */
    private static Transformation span(Run run, float f0, float f1, float c0, float c1, float y, float h) {
        float a0 = run.at(f0);
        float a1 = run.at(f1);
        return box(run.alongX(), Math.min(a0, a1), Math.max(a0, a1), c0, c1, y, h);
    }

    /** A box along the run: [a, b] along it (box coordinates), [c0, c1] across it, from {@code y}, {@code h} high. */
    static Transformation box(boolean alongX, float a, float b, float c0, float c1, float y, float h) {
        Vector3f translation = alongX ? new Vector3f(a, y, c0) : new Vector3f(c0, y, a);
        Vector3f scale = alongX ? new Vector3f(b - a, h, c1 - c0) : new Vector3f(c1 - c0, h, b - a);
        return new Transformation(translation, new Quaternionf(), scale, new Quaternionf());
    }

    /**
     * A flat bar on the line p to q (run-space x/z), bottom at {@code y}, {@code h} high, {@code w} wide, reaching
     * {@code before} past p and {@code after} past q.
     */
    private static Transformation bar(float[] p, float[] q, float y, float h, float w, float before, float after) {
        float dx = q[0] - p[0];
        float dz = q[1] - p[1];
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        Quaternionf rot = new Quaternionf().rotateY((float) Math.atan2(-dz / len, dx / len));
        Vector3f origin = new Vector3f(p[0], y, p[1]).add(rot.transform(new Vector3f(-before, 0f, -w / 2f)));
        return new Transformation(origin, rot, new Vector3f(len + before + after, h, w), new Quaternionf());
    }
}
