package de.aetherion.bossengine.instance.worldeater;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A small skeletal animation system built out of displays.
 *
 * <p>Bones form a hierarchy with spring-damped Euler angles chasing a target pose, so authored
 * poses read as weight (stiff springs) or as slack (soft springs) without hand-keying secondary
 * motion. Pieces are boxes (block displays) or item sprites (item displays) glued to bones.
 *
 * <p>Unlike a matrix rig, every bone's world transform is kept as position + rotation with one
 * uniform rig scale, so every piece decomposes exactly into translation / rotation / scale.
 * The server never has to decompose a matrix, which is what makes thin rotated parts flip.
 *
 * <p>Pieces can be cut loose ({@link Piece#free}) and driven by an absolute transform: shatter,
 * collapse, and the statue crust falling away all use it.
 */
public final class WeRig {

    public static final class Bone {
        final int index;
        final Bone parent;
        final Vector3f offset;
        final Quaternionf rest;
        public final Vector3f angle = new Vector3f();
        public final Vector3f vel = new Vector3f();
        public final Vector3f target = new Vector3f();
        float stiffness = 0.24f;
        float damping = 0.32f;
        final Vector3f wPos = new Vector3f();
        final Quaternionf wRot = new Quaternionf();

        Bone(int index, Bone parent, Vector3f offset, Quaternionf rest) {
            this.index = index;
            this.parent = parent;
            this.offset = offset;
            this.rest = rest;
        }

        public Bone aim(float x, float y, float z) {
            target.set(x, y, z);
            return this;
        }

        public void snap() {
            angle.set(target);
            vel.zero();
        }

        public void kick(float x, float y, float z) {
            vel.add(x, y, z);
        }

        public void spring(float k, float d) {
            stiffness = k;
            damping = d;
        }

        public Vector3f worldPos() {
            return new Vector3f(wPos);
        }

        public Quaternionf worldRot() {
            return new Quaternionf(wRot);
        }
    }

    public static final class Piece {
        final Bone bone;
        final Vector3f center;
        final Vector3f size;
        final Quaternionf rot;
        public final String group;
        final boolean item;
        Material material;
        BlockData data;
        ItemStack stack;
        Color glow;
        int brightness;
        public boolean hidden;
        public boolean free;
        public final Vector3f freePos = new Vector3f();
        public final Quaternionf freeRot = new Quaternionf();
        public final Vector3f freeSize = new Vector3f();
        Display display;
        Transformation sent;
        int holdUntil;

        Piece(Bone bone, Vector3f center, Vector3f size, Quaternionf rot, String group, boolean item) {
            this.bone = bone;
            this.center = center;
            this.size = size;
            this.rot = rot;
            this.group = group;
            this.item = item;
        }

        public Display display() {
            return display;
        }

        public Vector3f size() {
            return new Vector3f(size);
        }
    }

    private final List<Bone> bones = new ArrayList<>();
    private final List<Piece> pieces = new ArrayList<>();

    public final Vector3f rootPos = new Vector3f();
    public final Quaternionf tilt = new Quaternionf();
    public float yaw;
    public float scale = 1f;
    /** Client interpolation per push. 2 = silky; 0/1 with frameStep = stop-motion. */
    public int interp = 2;
    /** Push only every N ticks (frame holds). */
    public int frameStep = 1;
    /** Freezes springs and pushes entirely (hit-stop, cinematic freeze). */
    public boolean frozen;
    private int clock;
    private final Vector3f lastRoot = new Vector3f();
    private final Vector3f lastRootVel = new Vector3f();
    private final Vector3f rootAccel = new Vector3f();
    private boolean primed;

    public Bone bone(Bone parent, float ox, float oy, float oz) {
        return bone(parent, ox, oy, oz, new Quaternionf());
    }

    public Bone bone(Bone parent, float ox, float oy, float oz, Quaternionf rest) {
        Bone b = new Bone(bones.size(), parent, new Vector3f(ox, oy, oz), rest);
        bones.add(b);
        return b;
    }

    public Piece box(Bone bone, Material material, String group, float cx, float cy, float cz, float sx, float sy, float sz) {
        return box(bone, material, group, cx, cy, cz, sx, sy, sz, new Quaternionf());
    }

    public Piece box(Bone bone, Material material, String group,
                     float cx, float cy, float cz, float sx, float sy, float sz, Quaternionf rot) {
        Piece p = new Piece(bone, new Vector3f(cx, cy, cz), new Vector3f(sx, sy, sz), rot, group, false);
        p.material = material;
        p.brightness = 15;
        pieces.add(p);
        return p;
    }

    public Piece box(Bone bone, BlockData data, String group,
                     float cx, float cy, float cz, float sx, float sy, float sz, Quaternionf rot) {
        Piece p = new Piece(bone, new Vector3f(cx, cy, cz), new Vector3f(sx, sy, sz), rot, group, false);
        p.material = data.getMaterial();
        p.data = data;
        p.brightness = 15;
        pieces.add(p);
        return p;
    }

    /** An item sprite centered on (cx, cy, cz), facing local +Z. */
    public Piece item(Bone bone, ItemStack stack, String group, float cx, float cy, float cz, float s, Quaternionf rot) {
        Piece p = new Piece(bone, new Vector3f(cx, cy, cz), new Vector3f(s, s, s), rot, group, true);
        p.stack = stack;
        p.brightness = 15;
        pieces.add(p);
        return p;
    }

    public List<Bone> bones() {
        return bones;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public List<Piece> group(String group) {
        List<Piece> out = new ArrayList<>();
        for (Piece p : pieces) {
            if (group.equals(p.group)) {
                out.add(p);
            }
        }
        return out;
    }

    /* ------------------------------------------------------------------ lifecycle */

    public void spawn(WeFx fx) {
        solve();
        for (Piece p : pieces) {
            if (p.display != null && p.display.isValid()) {
                continue;
            }
            if (p.item) {
                p.display = fx.item(p.stack, p.brightness);
            } else {
                if (p.data == null) {
                    p.data = p.material.createBlockData();
                }
                p.display = fx.block(p.data, p.glow, p.brightness);
            }
            if (p.item && p.glow != null) {
                WeFx.glow(p.display, p.glow);
            }
            p.sent = null;
        }
        pushAll(0);
    }

    public boolean intact() {
        for (Piece p : pieces) {
            if (p.display == null || !p.display.isValid()) {
                return false;
            }
        }
        return true;
    }

    public void remove(WeFx fx) {
        for (Piece p : pieces) {
            if (fx != null) {
                fx.kill(p.display);
            } else if (p.display != null && p.display.isValid()) {
                p.display.remove();
            }
            p.display = null;
            p.sent = null;
        }
    }

    /* ------------------------------------------------------------------ simulation */

    /** Advance the springs; records root acceleration for secondary motion. */
    public void step(float tension) {
        clock++;
        Vector3f vel = new Vector3f(rootPos).sub(lastRoot);
        if (!primed) {
            vel.zero();
            primed = true;
        }
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

    /** Local-space root acceleration (yaw removed): x = side, y = up, z = forward. */
    public Vector3f localAccel() {
        return new Vector3f(rootAccel).rotateY(-yaw);
    }

    public Quaternionf rootRot() {
        return new Quaternionf().rotateY(yaw).mul(tilt);
    }

    public void solve() {
        Quaternionf rootRot = rootRot();
        for (Bone b : bones) {
            Vector3f pPos = b.parent == null ? rootPos : b.parent.wPos;
            Quaternionf pRot = b.parent == null ? rootRot : b.parent.wRot;
            Vector3f off = pRot.transform(new Vector3f(b.offset).mul(scale));
            b.wPos.set(pPos).add(off);
            b.wRot.set(pRot).mul(b.rest).rotateY(b.angle.y).rotateX(b.angle.x).rotateZ(b.angle.z);
        }
    }

    /** Stage-space transform of a piece (after the last solve). */
    public Transformation transformOf(Piece p) {
        if (p.free) {
            if (p.item) {
                return new Transformation(new Vector3f(p.freePos), new Quaternionf(p.freeRot), new Vector3f(p.freeSize), new Quaternionf());
            }
            Vector3f corner = p.freeRot.transform(new Vector3f(p.freeSize).mul(-0.5f));
            return new Transformation(new Vector3f(p.freePos).add(corner), new Quaternionf(p.freeRot), new Vector3f(p.freeSize), new Quaternionf());
        }
        Quaternionf q = new Quaternionf(p.bone.wRot).mul(p.rot);
        Vector3f s = new Vector3f(p.size).mul(scale);
        Vector3f c = p.bone.wRot.transform(new Vector3f(p.center).mul(scale)).add(p.bone.wPos);
        if (p.item) {
            return new Transformation(c, q, s, new Quaternionf());
        }
        Vector3f corner = q.transform(new Vector3f(s).mul(-0.5f));
        return new Transformation(c.add(corner), q, s, new Quaternionf());
    }

    /** Stage-space center of a piece (after the last solve). */
    public Vector3f centerOf(Piece p) {
        if (p.free) {
            return new Vector3f(p.freePos);
        }
        return p.bone.wRot.transform(new Vector3f(p.center).mul(scale)).add(p.bone.wPos);
    }

    /** World rotation of a piece (after the last solve). */
    public Quaternionf rotationOf(Piece p) {
        if (p.free) {
            return new Quaternionf(p.freeRot);
        }
        return new Quaternionf(p.bone.wRot).mul(p.rot);
    }

    /** Scaled size of a piece. */
    public Vector3f sizeOf(Piece p) {
        return p.free ? new Vector3f(p.freeSize) : new Vector3f(p.size).mul(scale);
    }

    private Transformation hiddenOf(Piece p) {
        return WeFx.gone(centerOf(p));
    }

    /** Solve + push, respecting frame holds and hit-stop. */
    public void render() {
        if (frozen) {
            return;
        }
        solve();
        if (frameStep > 1 && clock % frameStep != 0) {
            return;
        }
        pushAll(interp);
    }

    public void pushAll(int interpolation) {
        for (Piece p : pieces) {
            push(p, interpolation, false);
        }
    }

    public void push(Piece p, int interpolation, boolean force) {
        if (p.display == null || !p.display.isValid()) {
            return;
        }
        if (!force && clock < p.holdUntil) {
            return;
        }
        Transformation t = p.hidden ? hiddenOf(p) : transformOf(p);
        if (!force && p.sent != null && same(p.sent, t)) {
            return;
        }
        p.sent = t;
        WeFx.push(p.display, t, interpolation);
    }

    private static boolean same(Transformation a, Transformation b) {
        return a.getTranslation().distanceSquared(b.getTranslation()) < 4e-6f
                && a.getScale().distanceSquared(b.getScale()) < 4e-6f
                && Math.abs(a.getLeftRotation().dot(b.getLeftRotation())) > 0.999995f;
    }

    /* ------------------------------------------------------------------ helpers */

    /** Let a long interpolation on this piece play out without per-tick pushes overriding it. */
    public void hold(Piece p, int ticks) {
        p.holdUntil = clock + ticks;
    }

    public void releaseHolds() {
        for (Piece p : pieces) {
            p.holdUntil = 0;
        }
    }

    /** Stage-space position of a bone-local point (after the last solve). */
    public Vector3f point(Bone b, float x, float y, float z) {
        return b.wRot.transform(new Vector3f(x, y, z).mul(scale)).add(b.wPos);
    }

    /** Stage-space direction of a bone-local axis (after the last solve). */
    public Vector3f axis(Bone b, float x, float y, float z) {
        return b.wRot.transform(new Vector3f(x, y, z)).normalize();
    }

    public void setBlock(Piece p, Material material) {
        p.material = material;
        p.data = material.createBlockData();
        if (p.display instanceof BlockDisplay bd && bd.isValid()) {
            bd.setBlock(p.data);
        }
    }

    public void setItem(Piece p, ItemStack stack) {
        p.stack = stack;
        if (p.display instanceof ItemDisplay id && id.isValid()) {
            id.setItemStack(stack);
        }
    }

    public void setGlow(Piece p, Color color) {
        p.glow = color;
        WeFx.glow(p.display, color);
    }

    public void setBrightness(Piece p, int level) {
        p.brightness = level;
        WeFx.brightness(p.display, level);
    }

    public void setGroupHidden(String group, boolean hidden) {
        for (Piece p : pieces) {
            if (group.equals(p.group)) {
                p.hidden = hidden;
            }
        }
    }

    public void setGroupGlow(String group, Color color) {
        for (Piece p : pieces) {
            if (group.equals(p.group)) {
                setGlow(p, color);
            }
        }
    }

    public void setGroupBlock(String group, Material material) {
        for (Piece p : pieces) {
            if (group.equals(p.group) && !p.item) {
                setBlock(p, material);
            }
        }
    }

    public void setAllBrightness(int level) {
        for (Piece p : pieces) {
            setBrightness(p, level);
        }
    }

    /** Cut a piece loose exactly where it currently is. */
    public void free(Piece p) {
        if (p.free) {
            return;
        }
        p.freePos.set(centerOf(p));
        p.freeRot.set(rotationOf(p));
        p.freeSize.set(sizeOf(p));
        p.free = true;
    }

    public void freeAll() {
        solve();
        for (Piece p : pieces) {
            free(p);
        }
    }

    public void bindAll() {
        for (Piece p : pieces) {
            p.free = false;
        }
    }

    public void snapAll() {
        for (Bone b : bones) {
            b.snap();
        }
    }

    public int clock() {
        return clock;
    }
}
