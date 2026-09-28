package de.aetherion.bossengine.instance.saint;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny skeletal animation system built out of BlockDisplays.
 *
 * <p>Bones form a hierarchy. Every bone carries spring-damped Euler angles that chase a target
 * pose, so authored poses read as muscle (high stiffness) or as a dangling marionette (low
 * stiffness) without hand-keying secondary motion. Pieces are boxes glued to bones. Each tick
 * the skeleton solves world matrices and pushes only the displays whose matrix actually changed.
 *
 * <p>Pieces can be cut loose ({@link Piece#free}) and driven directly by a matrix, which is how
 * the doll shatters, lies scattered, and crawls back together.
 */
final class Skeleton {

    static final class Bone {
        final int index;
        final Bone parent;
        final Vector3f offset;
        final Quaternionf rest;
        final Vector3f angle = new Vector3f();
        final Vector3f vel = new Vector3f();
        final Vector3f target = new Vector3f();
        float stiffness = 0.24f;
        float damping = 0.32f;
        final Matrix4f world = new Matrix4f();

        Bone(int index, Bone parent, Vector3f offset, Quaternionf rest) {
            this.index = index;
            this.parent = parent;
            this.offset = offset;
            this.rest = rest;
        }

        void aim(float x, float y, float z) {
            target.set(x, y, z);
        }

        void snap() {
            angle.set(target);
            vel.zero();
        }

        void kick(float x, float y, float z) {
            vel.add(x, y, z);
        }

        void spring(float k, float d) {
            stiffness = k;
            damping = d;
        }
    }

    static final class Piece {
        final Bone bone;
        final Vector3f center;
        final Vector3f size;
        final Quaternionf rot;
        final String group;
        Material material;
        /** Created lazily at spawn so the rig can be built and solved without a server. */
        BlockData data;
        Color glow;
        int brightness;
        boolean hidden;
        boolean free;
        final Matrix4f freeMatrix = new Matrix4f();
        BlockDisplay display;
        final Matrix4f sent = new Matrix4f();
        boolean hasSent;
        /** Skeleton clock tick until which per-tick pushes skip this piece (long interpolation playing). */
        int holdUntil;

        Piece(Bone bone, Vector3f center, Vector3f size, Quaternionf rot, Material material, String group, int brightness) {
            this.bone = bone;
            this.center = center;
            this.size = size;
            this.rot = rot;
            this.material = material;
            this.group = group;
            this.brightness = brightness;
        }
    }

    private final List<Bone> bones = new ArrayList<>();
    private final List<Piece> pieces = new ArrayList<>();

    final Vector3f rootPos = new Vector3f();
    final Quaternionf tilt = new Quaternionf();
    float yaw;
    float scale = 1f;
    /** Client interpolation per push. 2 = silky; 0/1 with frameStep = stop-motion. */
    int interp = 2;
    /** Push only every N ticks (stop-motion frame holds). 1 = every tick. */
    int frameStep = 1;
    /** Freezes springs + pushes entirely (hit-stop, cinematic overrides). */
    boolean frozen;
    private int clock;
    private final Vector3f lastRoot = new Vector3f();
    private final Vector3f lastRootVel = new Vector3f();
    final Vector3f rootAccel = new Vector3f();

    Bone bone(Bone parent, float ox, float oy, float oz) {
        return bone(parent, ox, oy, oz, new Quaternionf());
    }

    Bone bone(Bone parent, float ox, float oy, float oz, Quaternionf rest) {
        Bone b = new Bone(bones.size(), parent, new Vector3f(ox, oy, oz), rest);
        bones.add(b);
        return b;
    }

    Piece box(Bone bone, Material material, String group, float cx, float cy, float cz, float sx, float sy, float sz) {
        return box(bone, material, group, cx, cy, cz, sx, sy, sz, new Quaternionf());
    }

    Piece box(Bone bone, Material material, String group,
              float cx, float cy, float cz, float sx, float sy, float sz, Quaternionf rot) {
        Piece p = new Piece(bone, new Vector3f(cx, cy, cz), new Vector3f(sx, sy, sz), rot,
                material, group, 15);
        pieces.add(p);
        return p;
    }

    List<Bone> bones() {
        return bones;
    }

    List<Piece> pieces() {
        return pieces;
    }

    List<Piece> group(String group) {
        List<Piece> out = new ArrayList<>();
        for (Piece p : pieces) {
            if (group.equals(p.group)) {
                out.add(p);
            }
        }
        return out;
    }

    /* ------------------------------------------------------------------ lifecycle */

    void spawn(SaintFx fx) {
        solve();
        for (Piece p : pieces) {
            if (p.display != null && p.display.isValid()) {
                continue;
            }
            if (p.data == null) {
                p.data = p.material.createBlockData();
            }
            p.display = fx.block(p.data, p.glow, p.brightness);
            p.hasSent = false;
        }
        pushAll(0);
    }

    boolean intact() {
        for (Piece p : pieces) {
            if (p.display == null || !p.display.isValid()) {
                return false;
            }
        }
        return true;
    }

    void remove() {
        for (Piece p : pieces) {
            SaintFx.kill(p.display);
            p.display = null;
            p.hasSent = false;
        }
    }

    /* ------------------------------------------------------------------ simulation */

    /** Advance springs, feed root acceleration into listed dangly bones. */
    void step(float tension) {
        clock++;
        Vector3f vel = new Vector3f(rootPos).sub(lastRoot);
        rootAccel.set(vel).sub(lastRootVel);
        lastRootVel.set(vel);
        lastRoot.set(rootPos);
        if (frozen) {
            return;
        }
        for (Bone b : bones) {
            float k = b.stiffness * tension;
            float d = b.damping;
            b.vel.x += (b.target.x - b.angle.x) * k;
            b.vel.y += (b.target.y - b.angle.y) * k;
            b.vel.z += (b.target.z - b.angle.z) * k;
            b.vel.mul(1f - d);
            b.angle.add(b.vel);
        }
    }

    /** Local-space root acceleration (yaw removed): x = right(-)/left(+), y = up, z = forward. */
    Vector3f localAccel() {
        return new Vector3f(rootAccel).rotateY(-yaw);
    }

    void solve() {
        Matrix4f root = new Matrix4f().translation(rootPos).rotateY(yaw).rotate(tilt).scale(scale);
        for (Bone b : bones) {
            Matrix4f parent = b.parent == null ? root : b.parent.world;
            b.world.set(parent)
                    .translate(b.offset)
                    .rotate(b.rest)
                    .rotateY(b.angle.y)
                    .rotateX(b.angle.x)
                    .rotateZ(b.angle.z);
        }
    }

    Matrix4f matrixOf(Piece p) {
        if (p.free) {
            return new Matrix4f(p.freeMatrix);
        }
        return new Matrix4f(p.bone.world)
                .translate(p.center)
                .rotate(p.rot)
                .scale(p.size)
                .translate(-0.5f, -0.5f, -0.5f);
    }

    private Matrix4f hiddenOf(Piece p) {
        Matrix4f m = matrixOf(p);
        Vector3f c = m.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
        return new Matrix4f().translation(c).scale(0.001f);
    }

    /** Solve + push, respecting frame holds and hit-stop. */
    void render() {
        if (frozen) {
            return;
        }
        solve();
        if (frameStep > 1 && clock % frameStep != 0) {
            return;
        }
        pushAll(interp);
    }

    void pushAll(int interpolation) {
        for (Piece p : pieces) {
            push(p, interpolation, false);
        }
    }

    void push(Piece p, int interpolation, boolean force) {
        if (p.display == null || !p.display.isValid()) {
            return;
        }
        if (!force && clock < p.holdUntil) {
            return;
        }
        Matrix4f m = p.hidden ? hiddenOf(p) : matrixOf(p);
        if (!force && p.hasSent && m.equals(p.sent, 0.0015f)) {
            return;
        }
        p.sent.set(m);
        p.hasSent = true;
        SaintFx.push(p.display, m, interpolation);
    }

    /* ------------------------------------------------------------------ helpers */

    /** Let a long interpolation on this piece play out without per-tick pushes overriding it. */
    void hold(Piece p, int ticks) {
        p.holdUntil = clock + ticks;
    }

    void releaseHolds() {
        for (Piece p : pieces) {
            p.holdUntil = 0;
        }
    }

    /** Stage-space position of a bone-local point (after the last solve). */
    Vector3f point(Bone b, float x, float y, float z) {
        return b.world.transformPosition(new Vector3f(x, y, z));
    }

    void setBlock(Piece p, Material material) {
        p.material = material;
        p.data = material.createBlockData();
        if (p.display != null && p.display.isValid()) {
            p.display.setBlock(p.data);
        }
    }

    void setGlow(Piece p, Color color) {
        p.glow = color;
        SaintFx.glow(p.display, color);
    }

    void setBrightness(Piece p, int level) {
        p.brightness = level;
        if (p.display != null && p.display.isValid()) {
            p.display.setBrightness(new org.bukkit.entity.Display.Brightness(level, level));
        }
    }

    void setHidden(Piece p, boolean hidden) {
        p.hidden = hidden;
    }

    void setGroupHidden(String group, boolean hidden) {
        for (Piece p : pieces) {
            if (group.equals(p.group)) {
                p.hidden = hidden;
            }
        }
    }

    void setGroupGlow(String group, Color color) {
        for (Piece p : pieces) {
            if (group.equals(p.group)) {
                setGlow(p, color);
            }
        }
    }

    void setAllGlow(Color color) {
        for (Piece p : pieces) {
            setGlow(p, color);
        }
    }

    /** Cut every piece loose where it currently is. */
    void freeAll() {
        solve();
        for (Piece p : pieces) {
            if (!p.free) {
                p.freeMatrix.set(matrixOf(p));
                p.free = true;
            }
        }
    }

    /** Re-bind every piece to its bone. The next push interpolates them home. */
    void bindAll() {
        for (Piece p : pieces) {
            p.free = false;
        }
    }

    void snapAll() {
        for (Bone b : bones) {
            b.snap();
        }
    }
}
