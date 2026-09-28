package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Unbroken's two set pieces that are not its body: the chains that bind it to the gate, and
 * the gate's two bedrock leaves, which its death tears out of the wall.
 */
final class GateProps {

    private GateProps() {
    }

    /**
     * A heavy chain of CHAIN block displays hung between two points, sagging when slack and pulled
     * straight when taut. Segments are sized about as long as they are wide so links keep their
     * proportions.
     */
    static final class ChainLine {
        private final WeFx fx;
        private final BlockDisplay[] links;
        private final float width;
        final float length;
        float extraSlack;
        final Vector3f a = new Vector3f();
        final Vector3f b = new Vector3f();
        private boolean hidden;

        ChainLine(WeFx fx, int segments, float width, float length) {
            this.fx = fx;
            this.width = width;
            this.length = length;
            this.links = new BlockDisplay[segments];
            for (int i = 0; i < segments; i++) {
                links[i] = fx.block(Material.CHAIN, null, 12);
            }
        }

        boolean intact() {
            for (BlockDisplay d : links) {
                if (d == null || !d.isValid()) {
                    return false;
                }
            }
            return true;
        }

        boolean taut() {
            return a.distance(b) >= length - 0.2f && extraSlack <= 0.01f;
        }

        float sag() {
            return Math.max(0f, length - a.distance(b)) * 0.45f + extraSlack;
        }

        Vector3f point(float t) {
            Vector3f p = WeMath.lerp(a, b, t, new Vector3f());
            p.y -= sag() * 4f * t * (1f - t);
            return p;
        }

        void hide(boolean hide) {
            hidden = hide;
        }

        void update(Vector3f from, Vector3f to, int interp) {
            a.set(from);
            b.set(to);
            int n = links.length;
            for (int i = 0; i < n; i++) {
                if (hidden) {
                    WeFx.push(links[i], WeFx.gone(point(i / (float) n)), interp);
                    continue;
                }
                Vector3f p0 = point(i / (float) n);
                Vector3f p1 = point((i + 1) / (float) n);
                WeFx.push(links[i], WeFx.beam(p0, p1, width), interp);
            }
        }

        /** The chain parts: both halves fall away. */
        void snap(float floor, int ticks) {
            int n = links.length;
            for (int i = 0; i < n; i++) {
                Vector3f p0 = point(i / (float) n);
                Vector3f p1 = point((i + 1) / (float) n);
                Vector3f q0 = new Vector3f(p0.x, floor + 0.1f, p0.z);
                Vector3f q1 = new Vector3f(p1.x + 0.4f, floor + 0.1f, p1.z);
                WeFx.push(links[i], WeFx.beam(q0, q1, width), ticks);
            }
            hidden = true;
        }

        void remove() {
            for (BlockDisplay d : links) {
                fx.kill(d);
            }
        }
    }

    /**
     * One leaf of the bedrock gate, rebuilt cell for cell out of displays so it looks exactly like
     * the blocks it replaced, then moved as one rigid body: ripped out, toppled onto the plaza,
     * dragged by its chain to the crater and dropped into the void.
     */
    static final class DoorLeaf {
        private final WeFx fx;
        private final List<BlockDisplay> cells = new ArrayList<>();
        private final List<Vector3f> offsets = new ArrayList<>();
        /** Hinge: the leaf's bottom edge on the plaza side, where it topples around. */
        final Vector3f hinge;
        final Vector3f pos = new Vector3f();
        final Quaternionf rot = new Quaternionf();
        private float scale = 1f;
        final float minX;
        final float maxX;

        DoorLeaf(WeFx fx, List<int[]> doorCells, List<BlockData> data, Vector3f hinge, float minX, float maxX) {
            this.fx = fx;
            this.hinge = new Vector3f(hinge);
            this.minX = minX;
            this.maxX = maxX;
            pos.set(hinge);
            for (int i = 0; i < doorCells.size(); i++) {
                int[] c = doorCells.get(i);
                Vector3f center = fx.stage(new org.bukkit.Location(fx.world(), c[0] + 0.5, c[1] + 0.5, c[2] + 0.5));
                offsets.add(new Vector3f(center).sub(hinge));
                BlockDisplay d = fx.block(data.get(i), null, 13);
                cells.add(d);
            }
            push(0);
        }

        /** Stage-space point of a leaf-local offset (relative to the hinge at rest). */
        Vector3f point(Vector3f local) {
            return rot.transform(new Vector3f(local).mul(scale)).add(pos);
        }

        /** The handle, where the chain is fastened (mid-height on the plaza face). */
        Vector3f handle() {
            return point(new Vector3f((minX + maxX) * 0.5f - hinge.x, 6.5f, 0f));
        }

        void pose(Vector3f at, Quaternionf rotation, float s) {
            pos.set(at);
            rot.set(rotation);
            scale = s;
        }

        void push(int interp) {
            for (int i = 0; i < cells.size(); i++) {
                Vector3f c = point(offsets.get(i));
                WeFx.push(cells.get(i), WeFx.box(c, new Vector3f(1.002f * scale), rot), interp);
            }
        }

        /** Does a stage point lie under the leaf's footprint once it lies flat? */
        boolean covers(Vector3f p, float pad) {
            Vector3f local = new Quaternionf(rot).conjugate().transform(new Vector3f(p).sub(pos));
            float lx = local.x + hinge.x;
            return lx > minX - pad && lx < maxX + pad && local.y > -2.5f && local.y < 17f + pad
                    && local.z > -2.5f && local.z < 2.5f;
        }

        void remove() {
            for (BlockDisplay d : cells) {
                fx.kill(d);
            }
            cells.clear();
        }
    }
}
