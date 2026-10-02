package de.aetherion.bossengine.instance.eggquelizer;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Eggquelizer itself: a giant egg on tiny chicken legs with a huge speaker for a face.
 *
 * <p>Body space: origin at the ground point between the feet, +Y up, +Z out of the speaker.
 * Every pose is a handful of springs (pitch, roll, squash) plus a few authored channels
 * (cone breath, LED level, foot lifts, jitter). No face: personality is where the speaker
 * points, how it leans, how it squashes, and when it goes completely still.
 */
final class EggBody {

    static final float LEG_H = 0.95f;
    static final float SHELL_H = 4.7f;
    static final float SHELL_R = 1.8f;
    static final float FACE_H = 1.95f;
    static final float FACE_Z = 1.62f;
    static final int SLICES = 10;

    static final Color ORANGE = Color.fromRGB(255, 120, 20);
    static final Color HOT = Color.fromRGB(255, 70, 30);

    private static final int BODY = 0;
    private static final int LEG_L = 1;
    private static final int LEG_R = 2;

    private static final int R_SHELL = 0;
    private static final int R_FACE = 1;
    private static final int R_CONE = 2;
    private static final int R_LED = 3;
    private static final int R_CRACK = 4;
    private static final int R_CORE = 5;
    private static final int R_LID = 6;
    private static final int R_GRILLE = 7;
    private static final int R_LEG = 8;
    private static final int R_TIP = 9;
    private static final int R_AMP = 10;

    private static final class Part {
        final int space;
        final int role;
        final Matrix4f local;
        final Material material;
        final int brightness;
        int index;
        int slice = -1;
        BlockDisplay d;
        final Matrix4f sent = new Matrix4f();
        boolean hasSent;
        boolean hiddenSent;

        Part(int space, int role, Matrix4f local, Material material, int brightness) {
            this.space = space;
            this.role = role;
            this.local = local;
            this.material = material;
            this.brightness = brightness;
        }
    }

    private final List<Part> parts = new ArrayList<>();
    private final List<Part> leds = new ArrayList<>();
    private Part tip;

    /* ------------------------------------------------------------------ pose channels */

    final Vector3f pos = new Vector3f();
    float yaw;
    final EggMath.Spring pitch = new EggMath.Spring(0f, 0.16f, 0.22f);
    final EggMath.Spring roll = new EggMath.Spring(0f, 0.16f, 0.22f);
    final EggMath.Spring squash = new EggMath.Spring(1f, 0.28f, 0.24f);
    final EggMath.Spring cone = new EggMath.Spring(0f, 0.4f, 0.4f);
    /** Extra forward bulge of the face cone (Overclock overfill). */
    float overfill;
    /** Body vibration amplitude in blocks. */
    float jitter;
    /** Vertical bob above the legs (waddle bounce, hops). */
    float bob;
    /** Whole-body lift off the floor (airborne hops). */
    float air;
    /** Foot lift per leg, 0..1. */
    float liftL;
    float liftR;
    /** Leg kick angle (tipped over, flailing). */
    float kickL;
    float kickR;
    /** 0..1 shell separation once cracked. */
    float crackOpen;
    boolean cracked;
    boolean lidOff;
    /** Face cone dented (distortion). */
    boolean dented;
    /** Body frozen: holds its last frame (cinematic stills). */
    boolean frozen;

    private int ledLevel = -1;
    private Color ledGlow;
    private boolean coneHot;
    private boolean tipOn;
    private int interp = 2;
    private EggFx fx;

    EggBody() {
        build();
    }

    /* ================================================================== construction */

    private Part add(int space, int role, Material m, int bright, float cx, float cy, float cz,
                     float sx, float sy, float sz, float rotX, float rotY, float rotZ) {
        Matrix4f local = new Matrix4f().translation(cx, cy, cz).rotateY(rotY).rotateX(rotX).rotateZ(rotZ)
                .scale(sx, sy, sz).translate(-0.5f, -0.5f, -0.5f);
        Part p = new Part(space, role, local, m, bright);
        p.index = parts.size();
        parts.add(p);
        return p;
    }

    /** Egg profile: radius at normalized height h (0 bottom, 1 top). */
    static float profile(float h) {
        float hc = 0.4f;
        float t = h < hc ? (h - hc) / hc : (h - hc) / (1f - hc);
        float r = (float) Math.sqrt(Math.max(0f, 1f - t * t));
        if (h > hc) {
            r *= 1f - 0.12f * (h - hc) / (1f - hc);
        }
        return SHELL_R * r;
    }

    private void build() {
        float sliceH = SHELL_H / SLICES;
        for (int i = 0; i < SLICES; i++) {
            float h0 = i / (float) SLICES;
            float hm = (i + 0.5f) / SLICES;
            float r = Math.max(0.35f, profile(hm));
            float y = LEG_H + (h0 + 0.5f / SLICES) * SHELL_H;
            float twist = i * EggMath.PI / 16f;
            int bright = 10 + Math.min(5, i);
            int role = i == SLICES - 1 || i == SLICES - 2 ? R_LID : R_SHELL;
            float s = r * 1.74f;
            Part a = add(BODY, role, Material.WHITE_CONCRETE, bright, 0f, y, 0f, s, sliceH + 0.04f, s, 0f, twist, 0f);
            Part b = add(BODY, role, Material.WHITE_CONCRETE, bright, 0f, y, 0f, s, sliceH + 0.04f, s, 0f, twist + EggMath.PI / 4f, 0f);
            a.slice = i;
            b.slice = i;
        }
        // Glowing core: invisible until the shell cracks open.
        add(BODY, R_CORE, Material.SHROOMLIGHT, 15, 0f, LEG_H + SHELL_H * 0.45f, 0f, 2.6f, SHELL_H * 0.7f, 2.6f, 0f, 0f, 0f);

        float fy = LEG_H + FACE_H;
        // Speaker housing and baffle.
        add(BODY, R_FACE, Material.BLACK_CONCRETE, 9, 0f, fy, FACE_Z - 0.35f, 2.9f, 2.9f, 0.8f, 0f, 0f, 0f);
        add(BODY, R_FACE, Material.BLACK_CONCRETE, 9, 0f, fy, FACE_Z - 0.3f, 2.5f, 2.5f, 0.78f, 0f, 0f, EggMath.PI / 4f);
        add(BODY, R_FACE, Material.POLISHED_BLACKSTONE, 10, 0f, fy, FACE_Z + 0.06f, 2.55f, 2.55f, 0.06f, 0f, 0f, 0f);
        // Surround ring.
        add(BODY, R_FACE, Material.GRAY_CONCRETE, 11, 0f, fy, FACE_Z + 0.1f, 2.2f, 2.2f, 0.08f, 0f, 0f, 0f);
        add(BODY, R_FACE, Material.GRAY_CONCRETE, 11, 0f, fy, FACE_Z + 0.1f, 2.0f, 2.0f, 0.08f, 0f, 0f, EggMath.PI / 4f);
        // Cone and dust cap: they breathe.
        add(BODY, R_CONE, Material.COAL_BLOCK, 11, 0f, fy, FACE_Z + 0.15f, 1.7f, 1.7f, 0.1f, 0f, 0f, 0f);
        add(BODY, R_CONE, Material.COAL_BLOCK, 11, 0f, fy, FACE_Z + 0.15f, 1.55f, 1.55f, 0.1f, 0f, 0f, EggMath.PI / 4f);
        add(BODY, R_CONE, Material.IRON_BLOCK, 13, 0f, fy, FACE_Z + 0.24f, 0.62f, 0.62f, 0.16f, 0f, 0f, 0f);
        // Grille bars across the face.
        for (int k = -1; k <= 1; k++) {
            add(BODY, R_GRILLE, Material.IRON_BLOCK, 12, 0f, fy + k * 0.62f, FACE_Z + 0.42f, 2.3f, 0.07f, 0.07f, 0f, 0f, 0f);
        }
        // LED meter: five lamps arched over the speaker.
        for (int k = 0; k < 5; k++) {
            float a = (k - 2) * 0.32f;
            float lx = (float) Math.sin(a) * 1.35f;
            float ly = fy + 1.62f + (float) Math.cos(a) * 0.25f - 0.25f;
            Part led = add(BODY, R_LED, Material.GRAY_CONCRETE, 6, lx, ly, FACE_Z - 0.02f, 0.3f, 0.22f, 0.12f, 0f, 0f, 0f);
            leds.add(led);
        }
        // Side amps ("ears").
        for (int s = -1; s <= 1; s += 2) {
            float ax = s * (SHELL_R + 0.1f);
            add(BODY, R_AMP, Material.BLACK_CONCRETE, 9, ax, fy - 0.1f, -0.1f, 0.5f, 1.0f, 1.0f, 0f, 0f, 0f);
            add(BODY, R_AMP, Material.GRAY_CONCRETE, 11, ax + s * 0.27f, fy - 0.1f, -0.1f, 0.06f, 0.7f, 0.7f, 0f, 0f, 0f);
        }
        // Antenna with a beat light.
        float topY = LEG_H + SHELL_H;
        add(BODY, R_LID, Material.LIGHTNING_ROD, 12, 0.3f, topY + 0.45f, -0.3f, 0.9f, 1.1f, 0.9f, 0f, 0f, 0.15f);
        tip = add(BODY, R_TIP, Material.RED_CONCRETE, 8, 0.38f, topY + 1.05f, -0.3f, 0.2f, 0.2f, 0.2f, 0f, 0f, 0f);
        // Rear cable: from the egg's back down to the floor.
        add(BODY, R_SHELL, Material.BLACK_CONCRETE, 7, 0f, LEG_H + 0.55f, -SHELL_R + 0.05f, 0.16f, 0.16f, 0.6f, 0f, 0f, 0f);
        add(BODY, R_SHELL, Material.IRON_BLOCK, 12, 0f, LEG_H + 0.55f, -SHELL_R - 0.25f, 0.28f, 0.22f, 0.22f, 0f, 0f, 0f);
        // Cracks (zigzags across the front-left, front-right and back). Hidden until cracked.
        float[][] cracks = {
                {-0.9f, 3.6f, 1.25f, 0.5f, 0.0f}, {-1.1f, 3.0f, 1.1f, -0.6f, 0.0f}, {-0.8f, 2.35f, 1.35f, 0.4f, 0.0f},
                {1.0f, 3.9f, 1.05f, -0.5f, 0.0f}, {1.25f, 3.3f, 0.8f, 0.55f, 0.0f}, {1.05f, 2.7f, 1.0f, -0.4f, 0.0f},
                {0.4f, 4.2f, -1.2f, 0.6f, 1.0f}, {-0.2f, 3.6f, -1.45f, -0.5f, 1.0f}, {0.3f, 2.9f, -1.5f, 0.45f, 1.0f},
        };
        for (float[] c : cracks) {
            float yawOut = (float) Math.atan2(c[0], c[2]);
            float rr = profile(c[1] / SHELL_H) * 1.1f;
            add(BODY, R_CRACK, Material.BLACK_CONCRETE, 4, (float) Math.sin(yawOut) * rr, LEG_H + c[1], (float) Math.cos(yawOut) * rr,
                    0.12f, 0.8f, 0.12f, 0f, yawOut, c[3]);
        }
        // Tiny chicken legs: shin + three toes forward + one back.
        for (int s = -1; s <= 1; s += 2) {
            int space = s < 0 ? LEG_L : LEG_R;
            float lx = s * 0.6f;
            add(space, R_LEG, Material.ORANGE_TERRACOTTA, 11, lx, LEG_H * 0.5f + 0.05f, 0f, 0.2f, LEG_H + 0.1f, 0.2f, 0f, 0f, 0f);
            for (int t = -1; t <= 1; t++) {
                add(space, R_LEG, Material.ORANGE_TERRACOTTA, 11, lx + t * 0.16f, 0.05f, 0.24f, 0.11f, 0.08f, 0.5f, 0f, t * 0.42f, 0f);
            }
            add(space, R_LEG, Material.ORANGE_TERRACOTTA, 11, lx, 0.05f, -0.18f, 0.1f, 0.08f, 0.32f, 0f, 0f, 0f);
        }
    }

    /* ================================================================== lifecycle */

    void spawn(EggFx fx) {
        this.fx = fx;
        for (Part p : parts) {
            p.d = fx.block(p.material, null, p.brightness);
            p.hasSent = false;
            p.hiddenSent = false;
        }
        ledLevel = -1;
        coneHot = false;
        tipOn = false;
        render(0);
    }

    boolean intact() {
        for (Part p : parts) {
            if (p.d == null || !p.d.isValid()) {
                return false;
            }
        }
        return true;
    }

    void remove() {
        for (Part p : parts) {
            EggFx.kill(p.d);
            p.d = null;
        }
    }

    /** Detach the display list (death corpse lingers a moment after the encounter clears). */
    List<BlockDisplay> detach() {
        List<BlockDisplay> out = new ArrayList<>();
        for (Part p : parts) {
            if (p.d != null && p.d.isValid()) {
                out.add(p.d);
            }
            p.d = null;
        }
        return out;
    }

    /* ================================================================== channels */

    /** LED meter: 0..5 lamps lit, colored green/yellow/red by position. */
    void leds(int level) {
        level = Math.max(0, Math.min(5, level));
        if (level == ledLevel && ledGlow == null) {
            return;
        }
        ledLevel = level;
        ledGlow = null;
        for (int k = 0; k < leds.size(); k++) {
            Part p = leds.get(k);
            if (p.d == null || !p.d.isValid()) {
                continue;
            }
            boolean on = k < level;
            Material m = !on ? Material.GRAY_CONCRETE
                    : k < 2 ? Material.LIME_CONCRETE : k < 4 ? Material.YELLOW_CONCRETE : Material.RED_CONCRETE;
            p.d.setBlock(m.createBlockData());
            EggFx.light(p.d, on ? 15 : 5);
            EggFx.glow(p.d, on && level == 5 ? EggSpeaker.RED : null);
        }
    }

    /** Every LED one color (overload, distortion). */
    void ledsAll(Material m, Color glow) {
        ledLevel = 5;
        ledGlow = glow;
        for (Part p : leds) {
            if (p.d != null && p.d.isValid()) {
                p.d.setBlock(m.createBlockData());
                EggFx.light(p.d, 15);
                EggFx.glow(p.d, glow);
            }
        }
    }

    void ledsOff() {
        ledLevel = 0;
        ledGlow = null;
        for (Part p : leds) {
            if (p.d != null && p.d.isValid()) {
                p.d.setBlock(Material.BLACK_CONCRETE.createBlockData());
                EggFx.light(p.d, 0);
                EggFx.glow(p.d, null);
            }
        }
    }

    /** The cone and cap glow: armed / overfilling. */
    void coneHot(boolean hot) {
        if (hot == coneHot) {
            return;
        }
        coneHot = hot;
        for (Part p : parts) {
            if (p.role == R_CONE && p.d != null && p.d.isValid()) {
                EggFx.glow(p.d, hot ? EggSpeaker.RED : null);
                EggFx.light(p.d, hot ? 15 : p.brightness);
            }
        }
    }

    void tip(boolean on) {
        if (on == tipOn || tip == null || tip.d == null || !tip.d.isValid()) {
            return;
        }
        tipOn = on;
        tip.d.setBlock((on ? Material.REDSTONE_BLOCK : Material.RED_CONCRETE).createBlockData());
        EggFx.light(tip.d, on ? 15 : 6);
        EggFx.glow(tip.d, on ? EggSpeaker.RED : null);
    }

    /** Power loss: every light on the body goes dark. */
    void powerDown() {
        ledsOff();
        coneHot(false);
        tip(false);
        for (Part p : parts) {
            if (p.d != null && p.d.isValid() && (p.role == R_CORE)) {
                p.d.setBlock(Material.BLACK_CONCRETE.createBlockData());
                EggFx.light(p.d, 2);
            }
        }
    }

    /** Distortion: show cracks and the inner glow. */
    void crack() {
        if (cracked) {
            return;
        }
        cracked = true;
        for (Part p : parts) {
            if (p.role == R_CORE && p.d != null && p.d.isValid()) {
                EggFx.glow(p.d, ORANGE);
            }
        }
    }

    void interp(int ticks) {
        this.interp = ticks;
    }

    /** World point at the center of the speaker face (stage space). */
    Vector3f facePoint() {
        return bodyMatrix().transformPosition(new Vector3f(0f, LEG_H + FACE_H, FACE_Z + 0.5f));
    }

    /** Unit facing of the speaker (stage space). */
    Vector3f faceDir() {
        Vector3f d = bodyMatrix().transformDirection(new Vector3f(0f, 0f, 1f));
        return d.lengthSquared() < 1e-6f ? new Vector3f(0f, 0f, 1f) : d.normalize();
    }

    Vector3f topPoint() {
        return bodyMatrix().transformPosition(new Vector3f(0f, LEG_H + SHELL_H, 0f));
    }

    Vector3f centerPoint() {
        return bodyMatrix().transformPosition(new Vector3f(0f, LEG_H + SHELL_H * 0.42f, 0f));
    }

    /* ================================================================== solve + push */

    void tickSprings() {
        if (frozen) {
            return;
        }
        pitch.tick();
        roll.tick();
        squash.tick();
        cone.tick();
    }

    private float lift() {
        // Lying down: raise the pivot so the shell rests on the floor instead of sinking into it.
        float p = Math.abs((float) Math.sin(pitch.value));
        float r = Math.abs((float) Math.sin(roll.value));
        float f = EggMath.clamp01((Math.max(p, r) - 0.4f) / 0.6f);
        float extra = pitch.value > 0f ? 0.45f : 0.1f;
        return f * (SHELL_R - LEG_H * 0.35f + extra);
    }

    private Matrix4f rootMatrix(float tiltScale) {
        float jx = jitter > 0f ? (EggMath.rnd() * 2f - 1f) * jitter : 0f;
        float jz = jitter > 0f ? (EggMath.rnd() * 2f - 1f) * jitter : 0f;
        return new Matrix4f()
                .translation(pos.x + jx, pos.y + air + lift(), pos.z + jz)
                .rotateY(yaw)
                .rotateX(pitch.value * tiltScale)
                .rotateZ(roll.value * tiltScale);
    }

    Matrix4f bodyMatrix() {
        float sy = Math.max(0.3f, squash.value);
        float sxz = 1f / (float) Math.sqrt(sy);
        return rootMatrix(1f)
                .translate(0f, bob, 0f)
                .translate(0f, LEG_H, 0f)
                .scale(sxz, sy, sxz)
                .translate(0f, -LEG_H, 0f);
    }

    void render() {
        if (frozen || fx == null) {
            return;
        }
        render(jitter > 0.01f ? 1 : interp);
    }

    private void render(int push) {
        Matrix4f body = bodyMatrix();
        Matrix4f legs = rootMatrix(1f);
        float fy = LEG_H + FACE_H;
        float sep = cracked ? crackOpen : 0f;
        for (Part p : parts) {
            if (p.d == null) {
                continue;
            }
            Matrix4f out;
            if (p.space == BODY) {
                out = new Matrix4f(body);
                switch (p.role) {
                    case R_CONE -> {
                        out.translate(0f, fy, 0f).translate(0f, 0f, cone.value + overfill * 0.4f);
                        float s = 1f + overfill * 0.35f;
                        if (dented) {
                            out.translate(0.12f, -0.08f, 0f).rotateZ(0.22f).rotateY(0.1f);
                        }
                        out.scale(s, s, 1f).translate(0f, -fy, 0f);
                    }
                    case R_GRILLE -> {
                        if (dented && p.index % 3 == 0) {
                            out.translate(0f, fy, FACE_Z + 0.42f).rotateZ(0.35f).rotateY(0.2f).translate(0f, -fy, -FACE_Z - 0.42f);
                        }
                    }
                    case R_CRACK, R_CORE -> {
                        if (!cracked) {
                            hide(p);
                            continue;
                        }
                    }
                    case R_LID, R_TIP -> {
                        if (lidOff) {
                            hide(p);
                            continue;
                        }
                    }
                    default -> {
                    }
                }
                if (p.slice >= 0 && sep > 0f) {
                    // Cracked shell: slices drift apart, each a little skewed.
                    float k = p.slice - SLICES * 0.45f;
                    out.translate(((p.slice * 37) % 5 - 2) * 0.025f * sep, k * 0.05f * sep, ((p.slice * 53) % 5 - 2) * 0.025f * sep);
                }
            } else {
                boolean left = p.space == LEG_L;
                float lift = left ? liftL : liftR;
                float kick = left ? kickL : kickR;
                out = new Matrix4f(legs).translate(0f, bob * 0.35f + lift * 0.35f, lift * 0.15f);
                if (kick != 0f) {
                    out.translate(0f, LEG_H, 0f).rotateX(kick).translate(0f, -LEG_H, 0f);
                }
            }
            out.mul(p.local);
            p.hiddenSent = false;
            if (push > 0 && p.hasSent && p.sent.equals(out, 1e-4f)) {
                continue;
            }
            p.sent.set(out);
            p.hasSent = true;
            EggFx.push(p.d, out, push);
        }
    }

    private void hide(Part p) {
        if (p.hiddenSent) {
            return;
        }
        p.hiddenSent = true;
        p.hasSent = false;
        EggFx.push(p.d, EggFx.gone(new Vector3f(pos)), 0);
    }

    /** The two top slices and the antenna, as matrices, for the lid that pops off. */
    List<Matrix4f> lidMatrices() {
        List<Matrix4f> out = new ArrayList<>();
        Matrix4f body = bodyMatrix();
        for (Part p : parts) {
            if (p.role == R_LID) {
                out.add(new Matrix4f(body).mul(p.local));
            }
        }
        return out;
    }

    List<Material> lidMaterials() {
        List<Material> out = new ArrayList<>();
        for (Part p : parts) {
            if (p.role == R_LID) {
                out.add(p.material);
            }
        }
        return out;
    }
}
