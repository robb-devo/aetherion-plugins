package de.aetherion.gnomon.render;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * One block display that is posed purely through its transformation (it never teleports). All positions are world
 * coordinates; the part converts them to its anchor. Poses are dirty-checked so static parts cost nothing.
 */
public final class Part {

    private static final float EPS = 0.0015f;
    private static final Quaternionf IDENTITY = new Quaternionf();

    private final BlockDisplay display;
    private final Vector3d anchor;
    private final Vector3f lastT = new Vector3f(Float.NaN, 0, 0);
    private final Vector3f lastS = new Vector3f();
    private final Quaternionf lastR = new Quaternionf();
    private BlockData block;
    private int brightness = -2;
    private boolean glowing;

    Part(BlockDisplay display, Location anchor, BlockData block) {
        this.display = display;
        this.anchor = new Vector3d(anchor.getX(), anchor.getY(), anchor.getZ());
        this.block = block;
    }

    public BlockDisplay entity() {
        return display;
    }

    public boolean valid() {
        return display != null && display.isValid();
    }

    /**
     * A box of {@code size}, centered on {@code center}, rotated by {@code rot}.
     *
     * @param interp client interpolation ticks for this change
     */
    public void box(Vector3d center, Quaternionf rot, double sx, double sy, double sz, int interp) {
        Quaternionf q = rot == null ? IDENTITY : rot;
        apply(translation(anchor, center, q, sx, sy, sz), q, new Vector3f((float) sx, (float) sy, (float) sz), interp);
    }

    /**
     * Display translation that centers a box of size (sx, sy, sz), rotated by q, on {@code center}.
     * A model vertex v in [0,1]^3 renders at anchor + T + q * (S * v); centered means T = center - anchor - q * (S / 2).
     */
    public static Vector3f translation(Vector3d anchor, Vector3d center, Quaternionf q, double sx, double sy, double sz) {
        Vector3f half = new Vector3f((float) (sx * 0.5), (float) (sy * 0.5), (float) (sz * 0.5));
        q.transform(half);
        return new Vector3f(
                (float) (center.x - anchor.x) - half.x,
                (float) (center.y - anchor.y) - half.y,
                (float) (center.z - anchor.z) - half.z);
    }

    /** A beam of square cross-section {@code thickness} from A to B. */
    public void bone(Vector3d a, Vector3d b, double thickness, int interp) {
        bone(a, b, thickness, thickness, interp);
    }

    public void bone(Vector3d a, Vector3d b, double thickX, double thickZ, int interp) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4) {
            hide(interp);
            return;
        }
        Quaternionf q = new Quaternionf().rotationTo(0f, 1f, 0f, (float) (dx / len), (float) (dy / len), (float) (dz / len));
        Vector3d center = new Vector3d(a).add(b).mul(0.5);
        box(center, q, thickX, len, thickZ, interp);
    }

    /** Flat plate lying on the floor, rotated by {@code yawRad} around Y (math angle of its local +X). */
    public void plate(double cx, double y, double cz, double lengthX, double widthZ, double thickness, double angle, int interp) {
        Quaternionf q = new Quaternionf().rotationY((float) -angle);
        box(new Vector3d(cx, y + thickness * 0.5, cz), q, lengthX, thickness, widthZ, interp);
    }

    public void hide(int interp) {
        apply(new Vector3f(lastT.isFinite() ? lastT : new Vector3f()), lastR, new Vector3f(0, 0, 0), interp);
    }

    private void apply(Vector3f t, Quaternionf q, Vector3f s, int interp) {
        if (!valid()) {
            return;
        }
        if (lastT.isFinite() && near(t, lastT) && near(s, lastS) && Math.abs(q.dot(lastR)) > 1f - 1e-6f) {
            return;
        }
        lastT.set(t);
        lastS.set(s);
        lastR.set(q);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, interp));
        display.setTransformation(new Transformation(t, new Quaternionf(q), s, new Quaternionf()));
    }

    private static boolean near(Vector3f a, Vector3f b) {
        return Math.abs(a.x - b.x) < EPS && Math.abs(a.y - b.y) < EPS && Math.abs(a.z - b.z) < EPS;
    }

    public void block(BlockData data) {
        if (data != null && valid() && !data.equals(block)) {
            block = data;
            display.setBlock(data);
        }
    }

    public BlockData block() {
        return block;
    }

    /** Full-bright 0..15, or -1 to use world light. */
    public void brightness(int level) {
        if (!valid() || level == brightness) {
            return;
        }
        brightness = level;
        display.setBrightness(level < 0 ? null : new Display.Brightness(level, level));
    }

    public void glow(Color color) {
        if (!valid()) {
            return;
        }
        if (color == null) {
            if (glowing) {
                display.setGlowing(false);
                glowing = false;
            }
            return;
        }
        display.setGlowColorOverride(color);
        if (!glowing) {
            display.setGlowing(true);
            glowing = true;
        }
    }

    public void remove() {
        if (display != null && display.isValid()) {
            display.remove();
        }
    }
}
