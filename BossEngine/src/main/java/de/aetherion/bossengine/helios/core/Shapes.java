package de.aetherion.bossengine.helios.core;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * The light-geometry vocabulary every tell and hazard is built from. All pieces are block displays in a
 * {@link HeliosStage.Group}; they are posed with one push per piece and animate by interpolation.
 *
 * <ul>
 *   <li>{@link Line}: a hairline, the "aim" state of every telegraph.</li>
 *   <li>{@link Beam}: a hot core inside a translucent shell, the "hit" state of every beam.</li>
 *   <li>{@link Ring}: segmented ring on any plane (portals, plasma rings, corona, horizons).</li>
 *   <li>{@link Disc}: a filled circle from three rotated squares (a dodecagon), for floor markers.</li>
 * </ul>
 */
public final class Shapes {

    private Shapes() {
    }

    /* ================================================================== line */

    public static final class Line {
        private final HeliosStage stage;
        private final BlockDisplay d;

        public Line(HeliosStage stage, HeliosStage.Group g, Material m, Color glow) {
            this.stage = stage;
            this.d = g.block(m.createBlockData(), glow, 15, true);
        }

        public void set(Vector3fc a, Vector3fc b, float width, int interp) {
            stage.push(d, HeliosStage.beam(a, b, width), interp);
        }

        public void hide(Vector3fc at, int interp) {
            stage.push(d, HeliosStage.gone(at), interp);
        }

        public void material(Material m) {
            HeliosStage.material(d, m);
        }

        public void glow(Color c) {
            HeliosStage.glow(d, c);
        }

        public BlockDisplay display() {
            return d;
        }
    }

    /* ================================================================== beam */

    public static final class Beam {
        private final HeliosStage stage;
        private final BlockDisplay core;
        private final BlockDisplay shell;
        private final Vector3f a = new Vector3f();
        private final Vector3f b = new Vector3f();
        private float width;

        public Beam(HeliosStage stage, HeliosStage.Group g, Material coreMat, Material shellMat, Color glow) {
            this.stage = stage;
            this.core = g.block(coreMat.createBlockData(), null, 15, true);
            this.shell = g.block(shellMat.createBlockData(), glow, 15, true);
        }

        public void set(Vector3fc from, Vector3fc to, float w, int interp) {
            a.set(from);
            b.set(to);
            width = w;
            stage.push(core, HeliosStage.beam(from, to, w * 0.45f), interp);
            stage.push(shell, HeliosStage.beam(from, to, w), interp);
        }

        /** Collapses the beam into a hairline along its current axis. */
        public void thin(int interp) {
            stage.push(core, HeliosStage.beam(a, b, 0.02f), interp);
            stage.push(shell, HeliosStage.beam(a, b, 0.04f), interp);
        }

        public void hide(int interp) {
            stage.push(core, HeliosStage.gone(b), interp);
            stage.push(shell, HeliosStage.gone(b), interp);
        }

        public void shellMaterial(Material m) {
            HeliosStage.material(shell, m);
        }

        public Vector3f from() {
            return a;
        }

        public Vector3f to() {
            return b;
        }

        public float width() {
            return width;
        }
    }

    /* ================================================================== ring */

    public static final class Ring {
        private final HeliosStage stage;
        private final List<BlockDisplay> segs = new ArrayList<>();

        public Ring(HeliosStage stage, HeliosStage.Group g, int segments, Material m, Color glow, int brightness, boolean force) {
            this.stage = stage;
            for (int i = 0; i < segments; i++) {
                BlockDisplay d = g.block(m.createBlockData(), glow, brightness, force);
                if (d != null) {
                    segs.add(d);
                }
            }
        }

        public int size() {
            return segs.size();
        }

        /**
         * Poses the ring around {@code center}. {@code orient} rotates the ring's plane (identity = flat,
         * lying on XZ). {@code thick} is the radial width, {@code tall} the height of each segment,
         * {@code spin} turns the segments around the ring's axis, {@code gapFrom..gapTo} (angles in the
         * ring's plane, radians) leaves an opening; pass gapFrom = gapTo for a closed ring.
         */
        public void pose(Vector3fc center, Quaternionf orient, float radius, float thick, float tall,
                         float spin, float gapFrom, float gapTo, int interp) {
            int n = segs.size();
            if (n == 0) {
                return;
            }
            float arc = HMath.TAU / n;
            float len = Math.max(0.02f, 2f * radius * (float) Math.tan(arc * 0.5f) * 1.04f);
            for (int i = 0; i < n; i++) {
                float ang = spin + arc * i;
                boolean hidden = gapFrom != gapTo && inGap(ang, gapFrom, gapTo);
                Vector3f local = new Vector3f((float) Math.cos(ang) * radius, 0f, (float) Math.sin(ang) * radius);
                Vector3f world = new Quaternionf(orient).transform(local).add(center);
                if (hidden || radius < 0.05f) {
                    stage.push(segs.get(i), HeliosStage.gone(world), interp);
                    continue;
                }
                Quaternionf rot = new Quaternionf(orient).rotateY(-ang);
                stage.push(segs.get(i), HeliosStage.box(world, new Vector3f(thick, tall, len), rot), interp);
            }
        }

        /**
         * All segments spread over the arc [from, to] (angles in the ring's plane): a partial ring with
         * full resolution, for slash arcs and sweeping fronts.
         */
        public void arc(Vector3fc center, Quaternionf orient, float radius, float thick, float tall,
                        float from, float to, int interp) {
            int n = segs.size();
            if (n == 0) {
                return;
            }
            float span = to - from;
            float step = span / n;
            float len = Math.max(0.02f, Math.abs(2f * radius * (float) Math.tan(step * 0.5f)) * 1.08f);
            for (int i = 0; i < n; i++) {
                float ang = from + step * (i + 0.5f);
                Vector3f local = new Vector3f((float) Math.cos(ang) * radius, 0f, (float) Math.sin(ang) * radius);
                Vector3f world = new Quaternionf(orient).transform(local).add(center);
                if (radius < 0.05f) {
                    stage.push(segs.get(i), HeliosStage.gone(world), interp);
                    continue;
                }
                Quaternionf rot = new Quaternionf(orient).rotateY(-ang);
                stage.push(segs.get(i), HeliosStage.box(world, new Vector3f(thick, tall, len), rot), interp);
            }
        }

        public void flat(Vector3fc center, float radius, float thick, float tall, float spin, int interp) {
            pose(center, new Quaternionf(), radius, thick, tall, spin, 0f, 0f, interp);
        }

        public void hide(Vector3fc at, int interp) {
            for (BlockDisplay d : segs) {
                stage.push(d, HeliosStage.gone(at), interp);
            }
        }

        public void material(Material m) {
            for (BlockDisplay d : segs) {
                HeliosStage.material(d, m);
            }
        }

        public void glow(Color c) {
            for (BlockDisplay d : segs) {
                HeliosStage.glow(d, c);
            }
        }

        public void brightness(int level) {
            for (BlockDisplay d : segs) {
                HeliosStage.brightness(d, level);
            }
        }

        private static boolean inGap(float ang, float from, float to) {
            float a = HMath.wrap(ang - from);
            float span = HMath.wrap(to - from);
            if (span < 0) {
                span += HMath.TAU;
            }
            if (a < 0) {
                a += HMath.TAU;
            }
            return a <= span;
        }
    }

    /* ================================================================== disc */

    public static final class Disc {
        private final HeliosStage stage;
        private final BlockDisplay[] parts = new BlockDisplay[3];

        public Disc(HeliosStage stage, HeliosStage.Group g, Material m, Color glow, int brightness) {
            this.stage = stage;
            for (int i = 0; i < parts.length; i++) {
                parts[i] = g.block(m.createBlockData(), glow, brightness, true);
            }
        }

        /** A flat filled circle of {@code radius} at {@code center}, {@code thick} high. */
        public void set(Vector3fc center, float radius, float thick, float yaw, int interp) {
            float side = radius * 2f * 0.9659f; // square inscribed so the 12-gon matches the circle
            for (int i = 0; i < parts.length; i++) {
                stage.push(parts[i], HeliosStage.flat(center, side, thick, side, yaw + i * HMath.PI / 6f), interp);
            }
        }

        public void hide(Vector3fc at, int interp) {
            for (BlockDisplay d : parts) {
                stage.push(d, HeliosStage.gone(at), interp);
            }
        }

        public void material(Material m) {
            for (BlockDisplay d : parts) {
                HeliosStage.material(d, m);
            }
        }

        public void glow(Color c) {
            for (BlockDisplay d : parts) {
                HeliosStage.glow(d, c);
            }
        }
    }
}
