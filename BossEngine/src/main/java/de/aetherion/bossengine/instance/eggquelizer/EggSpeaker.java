package de.aetherion.bossengine.instance.eggquelizer;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * One arena speaker: a display rig plus a strict, readable state machine.
 *
 * <pre>
 * IDLE ─arm()→ CHARGING ─→ PRIMED ─→ FIRING ─→ COOLDOWN ─→ IDLE
 *        hum, amber lamp,   red lamp, cone      white flash,   sag, dim, crackle
 *        cone shakes back   dead still (tell)   cone punches
 * </pre>
 *
 * The lamp never lies. The egg may lean the wrong way; the lamps are always honest.
 */
final class EggSpeaker {

    enum Kind { WALL, SUB, TWEETER }

    enum State { IDLE, CHARGING, PRIMED, FIRING, COOLDOWN, DEAD }

    /** What happens when this speaker locks in and when it fires. Supplied by the director. */
    interface Payload {
        default void primed(EggSpeaker s) {
        }

        void fire(EggSpeaker s);
    }

    static final Color AMBER = Color.fromRGB(255, 176, 32);
    static final Color RED = Color.fromRGB(255, 40, 40);
    static final Color WHITE = Color.fromRGB(255, 255, 255);
    static final Color VIOLET = Color.fromRGB(176, 64, 255);

    private static final int FIRE_TICKS = 4;
    private static final int COOL_TICKS = 22;

    final Kind kind;
    /** Position in the feedback loop (cable order). */
    final int loop;
    /** Mount point in stage space (base of the speaker on its surface). */
    final Vector3f base;
    final float yaw;
    final float pitch;
    /** Where cables plug in (floor level). */
    final Vector3f port;

    private final List<Part> parts = new ArrayList<>();
    private Part lamp;
    private Part fill;
    private final List<Part> nubs = new ArrayList<>();
    private EggFx fx;

    State state = State.IDLE;
    private int stateTick;
    private int chargeTicks;
    private int primeTicks;
    private Payload payload;
    /** Lamp idle tint once the egg has cracked. */
    boolean distorted;

    private float breath;
    private float breathVel;
    private float tilt;
    private float tiltVel;
    private boolean dirty = true;

    private static final class Part {
        final Matrix4f local;
        final int role;
        BlockDisplay d;
        final Material material;

        Part(Matrix4f local, int role, Material material) {
            this.local = local;
            this.role = role;
            this.material = material;
        }
    }

    private static final int CAB = 0;
    private static final int CONE = 1;
    private static final int LAMP = 2;
    private static final int FILL = 3;
    private static final int NUB = 4;

    EggSpeaker(Kind kind, int loop, Vector3f base, float yaw, float pitch, Vector3f port) {
        this.kind = kind;
        this.loop = loop;
        this.base = base;
        this.yaw = yaw;
        this.pitch = pitch;
        this.port = port;
        switch (kind) {
            case WALL -> buildWall();
            case SUB -> buildSub();
            case TWEETER -> buildTweeter();
        }
    }

    /* ================================================================== geometry */

    private void add(int role, Material m, float cx, float cy, float cz, float sx, float sy, float sz, float rotZ, float rotY) {
        Matrix4f local = new Matrix4f().translation(cx, cy, cz).rotateY(rotY).rotateZ(rotZ).scale(sx, sy, sz).translate(-0.5f, -0.5f, -0.5f);
        Part p = new Part(local, role, m);
        parts.add(p);
        if (role == LAMP) {
            lamp = p;
        } else if (role == FILL) {
            fill = p;
        } else if (role == NUB) {
            nubs.add(p);
        }
    }

    /** A round-ish disc facing +Z: two squares crossed at 45 degrees. */
    private void disc(int role, Material m, float cx, float cy, float cz, float size, float depth) {
        add(role, m, cx, cy, cz, size, size, depth, 0f, 0f);
        add(role, m, cx, cy, cz, size * 0.92f, size * 0.92f, depth, EggMath.PI / 4f, 0f);
    }

    /** A round-ish disc facing +Y. */
    private void floorDisc(int role, Material m, float cy, float size, float depth) {
        add(role, m, 0f, cy, 0f, size, depth, size, 0f, 0f);
        add(role, m, 0f, cy, 0f, size * 0.92f, depth, size * 0.92f, 0f, EggMath.PI / 4f);
    }

    private void buildWall() {
        float w = 2.3f;
        float h = 3.4f;
        float d = 1.3f;
        float front = d / 2f;
        add(CAB, Material.BLACK_CONCRETE, 0f, h / 2f, 0f, w, h, d, 0f, 0f);
        add(CAB, Material.POLISHED_BLACKSTONE, 0f, 0.08f, 0f, w + 0.15f, 0.16f, d + 0.15f, 0f, 0f);
        add(CAB, Material.POLISHED_BLACKSTONE, 0f, h - 0.06f, 0f, w + 0.1f, 0.12f, d + 0.1f, 0f, 0f);
        // Woofer: surround, cone, dust cap.
        disc(CAB, Material.GRAY_CONCRETE, 0f, 1.25f, front + 0.03f, 1.85f, 0.08f);
        disc(CONE, Material.COAL_BLOCK, 0f, 1.25f, front + 0.06f, 1.4f, 0.1f);
        add(CONE, Material.IRON_BLOCK, 0f, 1.25f, front + 0.12f, 0.46f, 0.46f, 0.12f, 0f, 0f);
        // Tweeter.
        disc(CAB, Material.GRAY_CONCRETE, 0f, 2.75f, front + 0.03f, 0.62f, 0.06f);
        add(CAB, Material.IRON_BLOCK, 0f, 2.75f, front + 0.06f, 0.2f, 0.2f, 0.06f, 0f, 0f);
        // Status strip (the charge fill grows across it) and the crown lamp.
        add(CAB, Material.BLACK_STAINED_GLASS, 0f, 2.28f, front + 0.04f, 1.7f, 0.16f, 0.05f, 0f, 0f);
        add(FILL, Material.YELLOW_CONCRETE, 0f, 2.28f, front + 0.06f, 1.6f, 0.12f, 0.05f, 0f, 0f);
        add(LAMP, Material.GRAY_CONCRETE, 0f, h + 0.17f, 0f, 1.0f, 0.34f, 0.9f, 0f, 0f);
    }

    private void buildSub() {
        add(CAB, Material.POLISHED_BLACKSTONE, 0f, 0.05f, 0f, 3.0f, 0.1f, 3.0f, 0f, 0f);
        floorDisc(CAB, Material.GRAY_CONCRETE, 0.1f, 2.45f, 0.06f);
        floorDisc(CONE, Material.COAL_BLOCK, 0.13f, 1.95f, 0.08f);
        add(CONE, Material.IRON_BLOCK, 0f, 0.17f, 0f, 0.62f, 0.1f, 0.62f, 0f, 0f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                add(NUB, Material.GRAY_CONCRETE, sx * 1.3f, 0.16f, sz * 1.3f, 0.3f, 0.14f, 0.3f, 0f, 0f);
            }
        }
    }

    private void buildTweeter() {
        float w = 1.6f;
        float h = 1.6f;
        float d = 1.2f;
        float front = d / 2f;
        add(CAB, Material.BLACK_CONCRETE, 0f, h / 2f, 0f, w, h, d, 0f, 0f);
        // A horn: stepped flare.
        add(CAB, Material.GRAY_CONCRETE, 0f, 0.8f, front + 0.05f, 1.3f, 1.0f, 0.1f, 0f, 0f);
        add(CAB, Material.BLACK_CONCRETE, 0f, 0.8f, front + 0.08f, 1.0f, 0.72f, 0.1f, 0f, 0f);
        add(CONE, Material.IRON_BLOCK, 0f, 0.8f, front + 0.12f, 0.42f, 0.3f, 0.1f, 0f, 0f);
        add(FILL, Material.YELLOW_CONCRETE, 0f, 1.42f, front + 0.04f, 1.2f, 0.1f, 0.05f, 0f, 0f);
        add(LAMP, Material.GRAY_CONCRETE, 0f, h + 0.16f, 0f, 0.8f, 0.32f, 0.8f, 0f, 0f);
    }

    /* ================================================================== lifecycle */

    void spawn(EggFx fx) {
        this.fx = fx;
        for (Part p : parts) {
            p.d = fx.block(p.material, null, p.role == CAB || p.role == CONE ? 11 : 6);
        }
        dirty = true;
        applyLamp(true);
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

    /* ================================================================== control */

    boolean free() {
        return state == State.IDLE;
    }

    boolean busy() {
        return state == State.CHARGING || state == State.PRIMED || state == State.FIRING;
    }

    /** Start the readable charge. Ignored unless idle or cooling (cooldown is cut short). */
    boolean arm(int charge, int prime, Payload payload) {
        if (state == State.DEAD || busy()) {
            return false;
        }
        this.chargeTicks = Math.max(1, charge);
        this.primeTicks = Math.max(1, prime);
        this.payload = payload;
        enter(State.CHARGING);
        // Armed before this tick's speaker update: that update is charge tick 0, so the fire
        // lands exactly charge + prime ticks after arming (on the beat the director asked for).
        stateTick = -1;
        fx.sound(cone(), Sound.BLOCK_LEVER_CLICK, 0.9f, 0.55f);
        fx.sound(cone(), Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.6f);
        return true;
    }

    /** Charging, then hold primed until {@link #release()} (setpieces that fire on a cue). */
    boolean armHold(int charge, Payload payload) {
        boolean ok = arm(charge, 100000, payload);
        return ok;
    }

    /** Fire a speaker that is primed and holding. */
    void release() {
        if (state == State.PRIMED || state == State.CHARGING) {
            fire();
        }
    }

    void cancel() {
        if (state == State.CHARGING || state == State.PRIMED) {
            payload = null;
            enter(State.COOLDOWN);
        }
    }

    /** Beat pulse while idle: the whole network breathes with the kick. */
    void kick(float amount) {
        if (state == State.IDLE || state == State.COOLDOWN) {
            breathVel += amount;
            dirty = true;
        }
    }

    /** Death sequence: the speaker dies and stays dead. */
    void die() {
        if (state == State.DEAD) {
            return;
        }
        payload = null;
        enter(State.DEAD);
        fx.clean(cone(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 0.7f + loop * 0.04f);
        fx.clean(cone(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.4f);
        fx.particle(Particle.SMOKE, cone(), 8, 0.3, 0.02);
        tiltVel += 0.06f;
    }

    /** Wake a dead speaker (repair after a chunk reload never needs this; the death is final). */
    void wakeFlash() {
        if (state == State.DEAD) {
            return;
        }
        breathVel += 0.12f;
        dirty = true;
        if (lamp != null && lamp.d != null && lamp.d.isValid()) {
            lamp.d.setBlock(Material.WHITE_CONCRETE.createBlockData());
            EggFx.glow(lamp.d, WHITE);
            EggFx.light(lamp.d, 15);
        }
        stateTick = -6;
    }

    float progress() {
        return state == State.CHARGING ? EggMath.clamp01(stateTick / (float) chargeTicks) : state == State.PRIMED || state == State.FIRING ? 1f : 0f;
    }

    /* ================================================================== tick */

    void tick() {
        stateTick++;
        switch (state) {
            case CHARGING -> {
                float p = progress();
                if (stateTick % 5 == 0) {
                    fx.sound(cone(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f + 0.5f * p, 0.5f + 0.5f * p);
                }
                if (p >= 0.5f && stateTick == chargeTicks / 2) {
                    applyLamp(true);
                }
                dirty = true;
                if (stateTick >= chargeTicks) {
                    enter(State.PRIMED);
                    fx.sound(cone(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.6f);
                    fx.particle(Particle.CLOUD, cone(), 6, 0.25, 0.02);
                    if (payload != null) {
                        payload.primed(this);
                    }
                }
            }
            case PRIMED -> {
                if (stateTick >= primeTicks) {
                    fire();
                }
            }
            case FIRING -> {
                if (stateTick >= FIRE_TICKS) {
                    enter(State.COOLDOWN);
                    fx.sound(cone(), Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 1.7f);
                    fx.particle(Particle.SMOKE, cone(), 4, 0.25, 0.01);
                }
            }
            case COOLDOWN -> {
                if (stateTick % 7 == 3) {
                    fx.particle(Particle.ELECTRIC_SPARK, cone(), 2, 0.3, 0.05);
                }
                if (stateTick == 8) {
                    applyLamp(true);
                }
                if (stateTick >= COOL_TICKS) {
                    enter(State.IDLE);
                }
            }
            case IDLE -> {
                if (stateTick == 0) {
                    applyLamp(true);
                }
            }
            default -> {
            }
        }
        // Springs.
        float breathTarget = switch (state) {
            case CHARGING -> -0.2f * progress();
            case PRIMED -> -0.24f;
            case COOLDOWN, DEAD -> -0.06f;
            default -> 0f;
        };
        float tiltTarget = switch (state) {
            case COOLDOWN -> 0.05f;
            case DEAD -> 0.16f;
            default -> 0f;
        };
        float lastB = breath;
        float lastT = tilt;
        if (state == State.PRIMED) {
            // Dead still is the tell.
            breath = breathTarget;
            breathVel = 0f;
        } else {
            breathVel += (breathTarget - breath) * 0.35f;
            breathVel *= 0.55f;
            breath += breathVel;
        }
        tiltVel += (tiltTarget - tilt) * 0.2f;
        tiltVel *= 0.7f;
        tilt += tiltVel;
        if (Math.abs(breath - lastB) > 1e-3f || Math.abs(tilt - lastT) > 1e-3f) {
            dirty = true;
        }
        if (dirty) {
            render(state == State.CHARGING ? 1 : 2);
        }
    }

    private void fire() {
        Payload p = payload;
        payload = null;
        enter(State.FIRING);
        breath = 0.38f;
        breathVel = 0f;
        tiltVel -= 0.12f;
        fx.sound(cone(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.6f, 0.5f);
        if (p != null) {
            p.fire(this);
        }
    }

    private void enter(State s) {
        state = s;
        stateTick = 0;
        applyLamp(false);
        dirty = true;
    }

    private void applyLamp(boolean refresh) {
        Material lampMat;
        Color glow;
        int light;
        switch (state) {
            case CHARGING -> {
                boolean hot = progress() >= 0.5f;
                lampMat = hot ? Material.ORANGE_CONCRETE : Material.YELLOW_CONCRETE;
                glow = AMBER;
                light = 12;
            }
            case PRIMED -> {
                lampMat = Material.RED_CONCRETE;
                glow = RED;
                light = 15;
            }
            case FIRING -> {
                lampMat = Material.WHITE_CONCRETE;
                glow = WHITE;
                light = 15;
            }
            case COOLDOWN -> {
                lampMat = stateTick < 8 ? Material.LIGHT_BLUE_CONCRETE : Material.BLUE_CONCRETE;
                glow = null;
                light = 5;
            }
            case DEAD -> {
                lampMat = Material.BLACK_CONCRETE;
                glow = null;
                light = 0;
            }
            default -> {
                lampMat = distorted ? Material.PURPLE_CONCRETE : Material.GRAY_CONCRETE;
                glow = null;
                light = distorted ? 9 : 4;
            }
        }
        BlockData data = lampMat.createBlockData();
        // The whole cabinet outlines red when it is locked in: readable across the arena, through anything.
        Part shell = parts.isEmpty() ? null : parts.get(0);
        if (shell != null && shell.d != null && shell.d.isValid()) {
            EggFx.glow(shell.d, state == State.PRIMED ? RED : state == State.FIRING ? WHITE : null);
        }
        for (Part p : parts) {
            if (p.d == null || !p.d.isValid()) {
                continue;
            }
            if (p.role == LAMP || p.role == NUB) {
                p.d.setBlock(data);
                EggFx.glow(p.d, glow);
                EggFx.light(p.d, light);
            } else if (p.role == FILL) {
                Material fm = state == State.PRIMED || state == State.FIRING ? Material.RED_CONCRETE
                        : state == State.CHARGING && progress() >= 0.5f ? Material.ORANGE_CONCRETE : Material.YELLOW_CONCRETE;
                p.d.setBlock(fm.createBlockData());
                EggFx.light(p.d, state == State.IDLE || state == State.DEAD ? 2 : 15);
            }
        }
    }

    /* ================================================================== render */

    Matrix4f mount() {
        return new Matrix4f().translation(base).rotateY(yaw).rotateX(pitch);
    }

    /** World (stage) point at the cone face. */
    Vector3f cone() {
        Vector3f local = switch (kind) {
            case WALL -> new Vector3f(0f, 1.25f, 0.9f);
            case SUB -> new Vector3f(0f, 0.3f, 0f);
            case TWEETER -> new Vector3f(0f, 0.8f, 0.8f);
        };
        return mount().transformPosition(local);
    }

    /** Unit direction the cone points (stage space). */
    Vector3f facing() {
        if (kind == Kind.SUB) {
            return new Vector3f(0f, 1f, 0f);
        }
        return mount().transformDirection(new Vector3f(0f, 0f, 1f)).normalize();
    }

    private void render(int interp) {
        dirty = false;
        if (fx == null) {
            return;
        }
        float jitter = state == State.CHARGING ? 0.012f + 0.045f * progress() : 0f;
        Matrix4f m = mount();
        // Cabinet tilt pivots on the front-bottom edge (recoil back, sag forward).
        Matrix4f cab = new Matrix4f(m);
        if (kind != Kind.SUB) {
            cab.translate(0f, 0f, 0.6f).rotateX(tilt).translate(0f, 0f, -0.6f);
        }
        float fillW = state == State.IDLE || state == State.DEAD || state == State.COOLDOWN ? 0.001f
                : state == State.CHARGING ? Math.max(0.001f, progress()) : 1f;
        for (Part p : parts) {
            if (p.d == null) {
                continue;
            }
            Matrix4f out = new Matrix4f(cab);
            if (p.role == CONE) {
                float jx = jitter > 0f ? (EggMath.rnd() * 2f - 1f) * jitter : 0f;
                float jy = jitter > 0f ? (EggMath.rnd() * 2f - 1f) * jitter : 0f;
                if (kind == Kind.SUB) {
                    out.translate(jx, breath * 0.6f, jy);
                } else {
                    out.translate(jx, jy, breath);
                }
            }
            if (p.role == FILL) {
                // Grow from the left edge.
                Matrix4f l = new Matrix4f(p.local);
                float shift = -(1f - fillW) * 0.5f;
                out.translate(shift * (kind == Kind.WALL ? 1.6f : 1.2f), 0f, 0f).scale(fillW, 1f, 1f);
                out.mul(l);
            } else {
                out.mul(p.local);
            }
            EggFx.push(p.d, out, interp);
        }
    }
}
