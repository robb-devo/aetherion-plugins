package de.aetherion.bossengine.helios.herald;

import de.aetherion.bossengine.helios.core.HMath;
import de.aetherion.bossengine.helios.core.HeliosStage;
import de.aetherion.bossengine.helios.core.Shapes;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * THE HERALD's body: a three-block armored warden made of ~40 block displays on a small forward
 * kinematics skeleton, plus four floating blades.
 *
 * <pre>
 *   head     netherite helm, a white-hot visor slit, a crown of five gold rays and a gold halo ring
 *   torso    netherite chest with gold pauldrons; a sun core burns in the chest
 *   arms     blackstone upper arms, netherite forearms, gold gauntlets
 *   legs     blackstone thighs, netherite greaves, gold sabatons (he hovers, toes down)
 *   blades   four long quartz blades with gold edges; at rest they fan out behind him like wings
 * </pre>
 *
 * The script sets a target {@link Pose}; the rig eases toward it and pushes every part. Parts can also
 * be flown in from anywhere (assembly), made to flicker (mirror clones), and stretched toward a point
 * and swallowed (death).
 */
public final class HeraldRig {

    public static final Color CORE = Color.fromRGB(255, 190, 60);
    public static final Color VISOR = Color.fromRGB(255, 244, 214);
    public static final Color EDGE = Color.fromRGB(255, 215, 110);

    /** Joint angles and offsets; everything the script animates. */
    public static final class Pose {
        public float crouch;
        public float lean;
        public float twist;
        public float headYaw;
        public float headPitch;
        public final float[] shoulderPitch = {0.15f, 0.15f};
        public final float[] shoulderRoll = {0.18f, 0.18f};
        public final float[] elbow = {0.25f, 0.25f};
        public float hover = 0.45f;

        public Pose set(Pose o) {
            crouch = o.crouch;
            lean = o.lean;
            twist = o.twist;
            headYaw = o.headYaw;
            headPitch = o.headPitch;
            System.arraycopy(o.shoulderPitch, 0, shoulderPitch, 0, 2);
            System.arraycopy(o.shoulderRoll, 0, shoulderRoll, 0, 2);
            System.arraycopy(o.elbow, 0, elbow, 0, 2);
            hover = o.hover;
            return this;
        }

        void approach(Pose t, float k) {
            crouch = HMath.lerp(crouch, t.crouch, k);
            lean = HMath.lerp(lean, t.lean, k);
            twist = HMath.lerp(twist, t.twist, k);
            headYaw = HMath.lerp(headYaw, t.headYaw, k);
            headPitch = HMath.lerp(headPitch, t.headPitch, k);
            for (int i = 0; i < 2; i++) {
                shoulderPitch[i] = HMath.lerp(shoulderPitch[i], t.shoulderPitch[i], k);
                shoulderRoll[i] = HMath.lerp(shoulderRoll[i], t.shoulderRoll[i], k);
                elbow[i] = HMath.lerp(elbow[i], t.elbow[i], k);
            }
            hover = HMath.lerp(hover, t.hover, k);
        }

        /** Arms: side 0 = right, 1 = left. */
        public Pose arm(int side, float pitch, float roll, float bend) {
            shoulderPitch[side] = pitch;
            shoulderRoll[side] = roll;
            elbow[side] = bend;
            return this;
        }

        public static Pose idle() {
            return new Pose();
        }

        public static Pose guard() {
            Pose p = new Pose();
            p.crouch = 0.25f;
            p.lean = 0.12f;
            p.arm(0, 0.9f, 0.15f, 1.1f).arm(1, 0.7f, 0.2f, 1.2f);
            return p;
        }

        public static Pose raise() {
            Pose p = new Pose();
            p.lean = -0.12f;
            p.headPitch = -0.45f;
            p.arm(0, 2.9f, 0.35f, 0.15f).arm(1, 2.9f, 0.35f, 0.15f);
            p.hover = 0.9f;
            return p;
        }

        public static Pose slashWindup() {
            Pose p = new Pose();
            p.crouch = 0.35f;
            p.twist = 0.6f;
            p.lean = 0.1f;
            p.arm(0, 1.9f, 0.9f, 0.6f).arm(1, 0.5f, 0.3f, 0.9f);
            return p;
        }

        public static Pose slash() {
            Pose p = new Pose();
            p.crouch = 0.45f;
            p.twist = -0.7f;
            p.lean = 0.3f;
            p.arm(0, 1.2f, -0.5f, 0.1f).arm(1, 0.3f, 0.5f, 0.6f);
            return p;
        }

        public static Pose point(float pitch) {
            Pose p = new Pose();
            p.lean = 0.05f;
            p.arm(0, 1.55f + pitch, 0.05f, 0.05f).arm(1, 0.2f, 0.25f, 0.4f);
            return p;
        }

        public static Pose kneel() {
            Pose p = new Pose();
            p.crouch = 1f;
            p.lean = 0.65f;
            p.headPitch = 0.7f;
            p.hover = 0.0f;
            p.arm(0, 0.1f, 0.1f, 0.2f).arm(1, 0.1f, 0.1f, 0.2f);
            return p;
        }
    }

    /** One rigid part: display + where it sits on its bone. */
    private static final class Part {
        final BlockDisplay d;
        final Vector3f size;
        Material base;
        final Vector3f worldCenter = new Vector3f();
        final Quaternionf worldRot = new Quaternionf();

        Part(BlockDisplay d, Vector3f size, Material base) {
            this.d = d;
            this.size = size;
            this.base = base;
        }
    }

    /** A blade's pose in stage space: hilt position and orientation (+Y runs along the blade). */
    public static final class Blade {
        public final Vector3f pos = new Vector3f();
        public final Quaternionf rot = new Quaternionf();
        public float scale = 1f;
        public boolean hot;
        /** When true the script moves this blade; otherwise it rests as a wing. */
        public boolean free;
        final BlockDisplay grip;
        final BlockDisplay guard;
        final BlockDisplay edge;

        Blade(BlockDisplay grip, BlockDisplay guard, BlockDisplay edge) {
            this.grip = grip;
            this.guard = guard;
            this.edge = edge;
        }

        /** Tip of the blade in stage space. */
        public Vector3f tip() {
            return new Quaternionf(rot).transform(new Vector3f(0f, 2.3f * scale, 0f)).add(pos);
        }
    }

    private final HeliosStage stage;
    private final HeliosStage.Group group;
    private final List<Part> parts = new ArrayList<>();
    private final Blade[] blades = new Blade[4];
    private final Shapes.Ring halo;

    private Part pelvis;
    private Part abdomen;
    private Part chest;
    private Part core;
    private Part coreFrame;
    private Part head;
    private Part visor;
    private final Part[] pauldron = new Part[2];
    private final Part[] upper = new Part[2];
    private final Part[] fore = new Part[2];
    private final Part[] gauntlet = new Part[2];
    private final Part[] thigh = new Part[2];
    private final Part[] shin = new Part[2];
    private final Part[] foot = new Part[2];
    private final Part[] ray = new Part[5];

    /** Feet position (stage) and facing (radians, forward = (sin, 0, cos)). */
    public final Vector3f root = new Vector3f();
    public float face;
    private final Pose pose = new Pose();
    private final Pose target = new Pose();
    private float blend = 0.25f;
    private int clock;
    private boolean visible = true;
    /** Free blades (flying for an attack or the intro) show on their own, even while the body is hidden. */
    private boolean bladesVisible = true;
    private float scale = 1f;
    private boolean ghost;
    private float coreHeat = 0.6f;
    /** Next push is a cut (no interpolation): the rig jumped (mirror shuffle / reveal) and must not smear. */
    private boolean cutNext;

    /* assembly: every part flies in from its own start point */
    private float assemble = 1f;
    private final List<Vector3f> assemblyFrom = new ArrayList<>();

    /* death: stretched toward a point and swallowed */
    private final Vector3f pull = new Vector3f();
    private float stretch;
    private float swallowed;

    public HeraldRig(HeliosStage stage, boolean ghost) {
        this.stage = stage;
        this.group = stage.group();
        this.ghost = ghost;
        build();
        halo = new Shapes.Ring(stage, group, ghost ? 8 : 12, Material.GOLD_BLOCK, ghost ? null : EDGE, 15, true);
    }

    private Part part(Material m, float sx, float sy, float sz, Color glow, int bright) {
        BlockDisplay d = group.block(m.createBlockData(), glow, bright, true);
        Part p = new Part(d, new Vector3f(sx, sy, sz), m);
        parts.add(p);
        return p;
    }

    private void build() {
        pelvis = part(Material.POLISHED_BLACKSTONE, 0.62f, 0.28f, 0.4f, null, -1);
        abdomen = part(Material.CHISELED_POLISHED_BLACKSTONE, 0.55f, 0.42f, 0.36f, null, -1);
        chest = part(Material.NETHERITE_BLOCK, 0.92f, 0.62f, 0.5f, null, -1);
        coreFrame = part(Material.GOLD_BLOCK, 0.42f, 0.42f, 0.08f, null, 13);
        core = part(Material.OCHRE_FROGLIGHT, 0.28f, 0.28f, 0.12f, CORE, 15);
        head = part(Material.NETHERITE_BLOCK, 0.4f, 0.46f, 0.44f, null, -1);
        visor = part(Material.WHITE_CONCRETE, 0.3f, 0.05f, 0.03f, VISOR, 15);
        for (int s = 0; s < 2; s++) {
            pauldron[s] = part(Material.GOLD_BLOCK, 0.46f, 0.22f, 0.52f, null, 12);
            upper[s] = part(Material.POLISHED_BLACKSTONE, 0.22f, 0.56f, 0.22f, null, -1);
            fore[s] = part(Material.NETHERITE_BLOCK, 0.25f, 0.54f, 0.25f, null, -1);
            gauntlet[s] = part(Material.GOLD_BLOCK, 0.27f, 0.2f, 0.27f, null, 12);
            thigh[s] = part(Material.POLISHED_BLACKSTONE, 0.27f, 0.62f, 0.27f, null, -1);
            shin[s] = part(Material.NETHERITE_BLOCK, 0.25f, 0.6f, 0.25f, null, -1);
            foot[s] = part(Material.GOLD_BLOCK, 0.24f, 0.1f, 0.4f, null, 12);
        }
        for (int i = 0; i < ray.length; i++) {
            ray[i] = part(Material.GOLD_BLOCK, 0.05f, 0.34f - Math.abs(i - 2) * 0.06f, 0.05f, ghost ? null : EDGE, 15);
        }
        for (int i = 0; i < blades.length; i++) {
            BlockDisplay grip = group.block(Material.BLACKSTONE.createBlockData(), null, -1, true);
            BlockDisplay guard = group.block(Material.GOLD_BLOCK.createBlockData(), null, 12, true);
            BlockDisplay edge = group.block(Material.SMOOTH_QUARTZ.createBlockData(), ghost ? null : EDGE, 15, true);
            blades[i] = new Blade(grip, guard, edge);
        }
    }

    /* ================================================================== controls */

    public void pose(Pose p, float blendRate) {
        target.set(p);
        blend = blendRate;
    }

    public void snap(Pose p) {
        target.set(p);
        pose.set(p);
    }

    public Pose current() {
        return pose;
    }

    public Blade blade(int i) {
        return blades[i];
    }

    public void visible(boolean on) {
        this.visible = on;
    }

    /** The next render places everything instantly (a jump cut instead of a slide across the arena). */
    public void cut() {
        this.cutNext = true;
    }

    public boolean visible() {
        return visible;
    }

    public void bladesVisible(boolean on) {
        this.bladesVisible = on;
    }

    public void scale(float s) {
        this.scale = s;
    }

    public void coreHeat(float heat) {
        this.coreHeat = HMath.clamp01(heat);
    }

    public void faceToward(Vector3f p, float maxStep) {
        float want = (float) Math.atan2(p.x - root.x, p.z - root.z);
        face = HMath.approachAngle(face, want, maxStep);
    }

    /** Start the assembly: every part begins at a random point around {@code from} (radius r). */
    public void beginAssembly(Vector3f from, float r) {
        assemble = 0f;
        assemblyFrom.clear();
        for (int i = 0; i < parts.size(); i++) {
            Vector3f dir = new Vector3f(HMath.hash(i, 3) - 0.5f, HMath.hash(i, 5) - 0.3f, HMath.hash(i, 9) - 0.5f).normalize();
            assemblyFrom.add(new Vector3f(dir).mul(r * (0.6f + HMath.hash(i, 13) * 0.4f)).add(from));
        }
    }

    /** 0..1: parts fly in, feet first and the head last. */
    public void assembly(float f) {
        this.assemble = HMath.clamp01(f);
    }

    /** Death: stretch toward {@code point} by {@code amount} (0..1) and swallow {@code eaten} (0..1) of the parts. */
    public void swallow(Vector3f point, float amount, float eaten) {
        pull.set(point);
        stretch = HMath.clamp01(amount);
        swallowed = HMath.clamp01(eaten);
    }

    public Vector3f chestPoint() {
        return new Vector3f(chest.worldCenter);
    }

    public Vector3f corePoint() {
        return new Vector3f(core.worldCenter);
    }

    public Vector3f headPoint() {
        return new Vector3f(head.worldCenter);
    }

    public Vector3f handPoint(int side) {
        return new Vector3f(gauntlet[side].worldCenter);
    }

    public Vector3f forward() {
        return new Vector3f((float) Math.sin(face), 0f, (float) Math.cos(face));
    }

    /* ================================================================== solve + push */

    /**
     * Solve and push. {@code interp} is the body's interpolation per push: call every tick with
     * {@link HeliosStage#SMOOTH_1} (idle glide) or 1 (snappy strikes); clones every second tick with
     * {@link HeliosStage#SMOOTH_2}. Free blades are always pushed at 1: attacks steer them tick by tick and
     * their hit tests must match what you see.
     */
    public void render(int interp) {
        clock++;
        if (cutNext) {
            cutNext = false;
            interp = 0;
        }
        pose.approach(target, blend);
        solve();
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            Vector3f c = new Vector3f(p.worldCenter);
            Quaternionf r = new Quaternionf(p.worldRot);
            Vector3f size = new Vector3f(p.size).mul(scale);
            if (assemble < 1f && i < assemblyFrom.size()) {
                // Feet first, head last: each part has its own window of the assembly.
                float order = partOrder(i);
                float f = HMath.window((int) (assemble * 1000), (int) (order * 600), (int) (order * 600 + 400));
                float e = HMath.inOutCubic(f);
                Vector3f from = assemblyFrom.get(i);
                Vector3f mid = new Vector3f(from).add(c).mul(0.5f).add(0f, 3f, 0f);
                c = HMath.bezier(from, mid, c, e, new Vector3f());
                r = new Quaternionf().rotateXYZ((1f - e) * 6f * HMath.hash(i, 1), (1f - e) * 5f, 0f).mul(r);
                size.mul(0.35f + 0.65f * e);
            }
            if (stretch > 0f) {
                float order = 1f - partOrder(i);
                Vector3f to = new Vector3f(pull).sub(c);
                float dist = to.length();
                if (dist > 1e-3f) {
                    to.div(dist);
                    float k = stretch * (0.4f + 0.6f * order);
                    Quaternionf along = HMath.alignY(to);
                    r = new Quaternionf(r).slerp(along, Math.min(1f, k * 1.4f));
                    size.set(size.x * (1f - 0.7f * k), size.y * (1f + 5f * k), size.z * (1f - 0.7f * k));
                    c.add(new Vector3f(to).mul(dist * HMath.inQuad(k) * 0.35f));
                }
                if (swallowed > 0f && order < swallowed) {
                    // Swallowed parts: pulled all the way in, thinned to nothing.
                    float g = HMath.clamp01((swallowed - order) * 4f);
                    c.lerp(pull, HMath.inCubic(g));
                    size.mul(1f - g * 0.98f);
                }
            }
            if (!visible) {
                stage.push(p.d, HeliosStage.gone(c), interp);
                continue;
            }
            stage.push(p.d, HeliosStage.box(c, size, r), interp);
        }
        renderHalo(interp);
        renderBlades(interp);
        if (ghost && clock % 17 == 0) {
            // Clones are a little out of step with the star: a one-tick dip in their light.
            HeliosStage.brightness(core.d, 6);
        } else if (clock % 17 == 1) {
            HeliosStage.brightness(core.d, 15);
        }
    }

    /** 0 = feet … 1 = head: the order of assembly and the reverse order of being swallowed. */
    private float partOrder(int i) {
        Part p = parts.get(i);
        float y = p.worldCenter.y - root.y;
        return HMath.clamp01(y / 3.2f);
    }

    private void solve() {
        float s = scale;
        float hipH = (1.35f - pose.crouch * 0.45f + pose.hover) * s;
        Vector3f hip = new Vector3f(root).add(0f, hipH, 0f);
        Quaternionf body = new Quaternionf().rotateY(face);
        Quaternionf torso = new Quaternionf(body).rotateY(pose.twist).rotateX(pose.lean);

        place(pelvis, hip, body, 0f, 0f, 0f);
        place(abdomen, hip, torso, 0f, 0.36f * s, 0f);
        place(chest, hip, torso, 0f, 0.82f * s, 0f);
        place(coreFrame, hip, torso, 0f, 0.84f * s, 0.26f * s);
        place(core, hip, torso, 0f, 0.84f * s, 0.3f * s);
        float heat = 0.28f * (0.8f + 0.4f * coreHeat);
        core.size.set(heat, heat, 0.12f);

        Vector3f neck = new Quaternionf(torso).transform(new Vector3f(0f, 1.15f * s, 0f)).add(hip);
        Quaternionf headRot = new Quaternionf(torso).rotateY(pose.headYaw).rotateX(pose.headPitch);
        place(head, neck, headRot, 0f, 0.26f * s, 0f);
        place(visor, neck, headRot, 0f, 0.3f * s, 0.225f * s);
        for (int i = 0; i < ray.length; i++) {
            float a = (i - 2) * 0.42f;
            Quaternionf rr = new Quaternionf(headRot).rotateZ(a);
            Vector3f base = new Quaternionf(headRot).transform(new Vector3f(0f, 0.5f * s, -0.05f * s)).add(neck);
            place(ray[i], base, rr, 0f, ray[i].size.y * 0.5f * s, 0f);
        }

        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1f : 1f;
            Vector3f shoulder = new Quaternionf(torso).transform(new Vector3f(0.56f * sx * s, 0.98f * s, 0f)).add(hip);
            place(pauldron[side], shoulder, new Quaternionf(torso).rotateZ(sx * 0.35f), 0.04f * sx * s, 0.08f * s, 0f);
            Quaternionf sh = new Quaternionf(torso).rotateX(-pose.shoulderPitch[side]).rotateZ(sx * pose.shoulderRoll[side]);
            place(upper[side], shoulder, sh, 0f, -0.3f * s, 0f);
            Vector3f elbow = new Quaternionf(sh).transform(new Vector3f(0f, -0.6f * s, 0f)).add(shoulder);
            Quaternionf fa = new Quaternionf(sh).rotateX(-pose.elbow[side]);
            place(fore[side], elbow, fa, 0f, -0.28f * s, 0f);
            place(gauntlet[side], elbow, fa, 0f, -0.62f * s, 0f);

            Vector3f hipJoint = new Quaternionf(body).transform(new Vector3f(0.2f * sx * s, -0.06f * s, 0f)).add(hip);
            float thighPitch = pose.crouch * 1.05f + 0.12f * (1f - pose.crouch) * (float) Math.sin(clock * 0.025f + side);
            Quaternionf th = new Quaternionf(body).rotateX(-thighPitch);
            place(thigh[side], hipJoint, th, 0f, -0.33f * s, 0f);
            Vector3f knee = new Quaternionf(th).transform(new Vector3f(0f, -0.66f * s, 0f)).add(hipJoint);
            Quaternionf sn = new Quaternionf(th).rotateX(pose.crouch * 1.9f + 0.25f);
            place(shin[side], knee, sn, 0f, -0.32f * s, 0f);
            // Toes point down: he hovers.
            Quaternionf ft = new Quaternionf(sn).rotateX(0.9f);
            place(foot[side], knee, sn, 0f, -0.66f * s, 0.06f * s);
            foot[side].worldRot.set(ft);
        }
    }

    private void place(Part p, Vector3f joint, Quaternionf rot, float ox, float oy, float oz) {
        p.worldRot.set(rot);
        p.worldCenter.set(new Quaternionf(rot).transform(new Vector3f(ox, oy, oz)).add(joint));
    }

    private void renderHalo(int interp) {
        if (!visible || assemble < 0.95f || stretch > 0.3f) {
            halo.hide(head.worldCenter, interp);
            return;
        }
        Quaternionf headRot = new Quaternionf(head.worldRot);
        Vector3f at = headRot.transform(new Vector3f(0f, 0.1f * scale, -0.32f * scale)).add(head.worldCenter);
        Quaternionf orient = new Quaternionf(headRot).rotateX(HMath.HALF_PI);
        halo.pose(at, orient, 0.52f * scale, 0.05f, 0.05f, clock * 0.01f, 0f, 0f, interp);
    }

    private void renderBlades(int interp) {
        Quaternionf body = new Quaternionf().rotateY(face);
        Vector3f back = body.transform(new Vector3f(0f, 0f, -0.55f)).add(chest.worldCenter);
        for (int i = 0; i < blades.length; i++) {
            Blade b = blades[i];
            if (!b.free) {
                // Wings: two blades each side, fanned up and out behind the shoulders, breathing.
                float sx = i < 2 ? -1f : 1f;
                float spread = (i % 2 == 0 ? 0.55f : 1.05f) + 0.06f * (float) Math.sin(clock * 0.035f + i);
                Quaternionf r = new Quaternionf(body).rotateZ(sx * spread).rotateX(-0.25f);
                b.rot.set(r);
                b.pos.set(new Quaternionf(body).transform(new Vector3f(sx * (0.35f + (i % 2) * 0.12f), -0.2f, -0.1f)).add(back));
                b.scale = scale * 0.85f;
                b.hot = false;
            }
            pushBlade(b, b.free && interp > 0 ? 1 : interp);
        }
    }

    private void pushBlade(Blade b, int interp) {
        boolean show = (b.free ? bladesVisible : visible && assemble >= 0.9f) && b.scale > 0.01f;
        if (!show) {
            stage.push(b.grip, HeliosStage.gone(b.pos), interp);
            stage.push(b.guard, HeliosStage.gone(b.pos), interp);
            stage.push(b.edge, HeliosStage.gone(b.pos), interp);
            return;
        }
        float s = b.scale;
        Quaternionf r = b.rot;
        stage.push(b.grip, HeliosStage.box(new Quaternionf(r).transform(new Vector3f(0f, 0.12f * s, 0f)).add(b.pos),
                new Vector3f(0.08f * s, 0.26f * s, 0.08f * s), r), interp);
        stage.push(b.guard, HeliosStage.box(new Quaternionf(r).transform(new Vector3f(0f, 0.27f * s, 0f)).add(b.pos),
                new Vector3f(0.46f * s, 0.07f * s, 0.1f * s), r), interp);
        stage.push(b.edge, HeliosStage.box(new Quaternionf(r).transform(new Vector3f(0f, 1.3f * s, 0f)).add(b.pos),
                new Vector3f(0.15f * s, 2.0f * s, 0.035f * s), r), interp);
        HeliosStage.material(b.edge, b.hot ? Material.WHITE_CONCRETE : Material.SMOOTH_QUARTZ);
    }

    /** Pushes the blades only (the body frozen), for blade-heavy moves at a finer cadence. */
    public void renderBladesOnly(int interp) {
        for (Blade b : blades) {
            if (b.free) {
                pushBlade(b, interp);
            }
        }
    }

    public void flare(int level) {
        HeliosStage.brightness(visor.d, level);
        HeliosStage.brightness(core.d, level);
    }

    public void clear() {
        group.clear();
    }

    public HeliosStage.Group group() {
        return group;
    }
}
