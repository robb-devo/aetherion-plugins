package de.aetherion.aethermobs.pet.body;

import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.items.model.Rarity;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Runtime of one pet body: reads how the anchor moved, decides the body state, schedules keyframes at gait
 * extremes, enforces LOD + the global write budget and plays one-shot actions. {@code PetEntity} owns where
 * the anchor goes; this class owns how the creature looks while it gets there.
 */
public final class PetBody {

    public enum Mode {
        /** Walking / flying / swimming in its own element. */
        NORMAL,
        /** Ground pet in water: paddles at the surface. */
        PADDLE,
        /** Water pet on land: rides in a bubble. */
        BUBBLE,
        /** Equipped ground pet while the owner flies / glides / rides: back inside its sphere. */
        BALL,
        /** Inside a catch sphere: body collapsed, nothing animates. */
        CAPTURED
    }

    /** Companion sphere model (resource pack), falls back to a snowball. */
    public static final int CMD_COMPANION_BALL = 3613;

    /** Global keyframe-write budget per server tick. */
    private static final int BUDGET_PER_TICK = 320;
    private static long budgetTick = -1L;
    private static int budgetUsed;

    private final Rig rig;
    private final PetInstance pet;
    private final float seed;
    private final boolean equipped;
    private final Gaits.Motion motion = new Gaits.Motion();
    private final BodyFx fx;

    private int age;
    private int nextKey;
    private double phase;
    private float speed;
    private float vy;
    private float turn;
    private float lastHeading;
    private float targetHeading;
    private float turnRate = 10f;
    private float keyedTarget = Float.NaN;
    private double lastX = Double.NaN, lastY, lastZ;
    private int idle;
    private Mode mode = Mode.NORMAL;
    private boolean modeDirty = true;
    private boolean flying;

    private int breathTicks;
    private int happyTicks;
    private int puffTicks;
    private int rollTicks;
    private int flexTicks;
    private int glideTicks;
    private int glideCooldown = 60;
    private float sit;
    private boolean perched;

    private float presenceTarget = 1f;
    private int presenceTicks;

    private PetBody(Rig rig, PetInstance pet, boolean equipped) {
        this.rig = rig;
        this.pet = pet;
        this.equipped = equipped;
        this.seed = ThreadLocalRandom.current().nextFloat() * 10f;
        this.fx = new BodyFx(this, pet);
        this.flying = rig.skeleton.airborne() || rig.skeleton == Skeleton.WYRM;
    }

    /** Build the body for a pet. Equipped ground pets get a companion sphere, water pets a bubble. */
    public static PetBody create(PetInstance pet, boolean equipped, boolean forced) {
        Rig rig = PetBodies.create(pet, equipped);
        Skeleton sk = rig.skeleton;
        if (equipped && !sk.aquatic()) {
            ItemStack ball = new ItemStack(Material.SNOWBALL);
            ItemMeta meta = ball.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(CMD_COMPANION_BALL);
                ball.setItemMeta(meta);
            }
            Rig.Part part = rig.item(0, ball, 0f, 0f, 0f, 0.62f / Math.max(0.4f, rig.size), 0f, 0f, 0f, Rig.CORE).hidden().bright();
            rig.roles().ball = part;
        }
        if ((equipped || forced) && sk.aquatic()) {
            // A water bubble a little larger than the body's own rest bounds.
            float[] b = rig.restBounds();
            float pad = 0.1f;
            float w = Math.max(0.5f, b[3] - b[0] + pad * 2f);
            float h = Math.max(0.45f, b[4] - b[1] + pad * 2f);
            float l = Math.max(0.5f, b[5] - b[2] + pad * 2f);
            Rig.Part bubble = rig.box(0, BodyKit.bd(Material.LIGHT_BLUE_STAINED_GLASS),
                    (b[0] + b[3]) / 2f, (b[1] + b[4]) / 2f, (b[2] + b[5]) / 2f, w, h, l, Rig.CORE).hidden().view(0.5f);
            rig.roles().bubble = bubble;
        }
        return new PetBody(rig, pet, equipped);
    }

    public Rig rig() {
        return rig;
    }

    public Skeleton skeleton() {
        return rig.skeleton;
    }

    public boolean isEquipped() {
        return equipped;
    }

    public void attach(Plugin plugin, ItemDisplay anchor) {
        rig.heading = anchor.getLocation().getYaw();
        lastHeading = rig.heading;
        rig.attach(plugin, anchor, equipped);
        keyNow();
    }

    public void remove() {
        rig.remove();
    }

    /*
     * =========================================================
     * MODES + ACTIONS
     * =========================================================
     */

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode next) {
        if (next == null || next == mode) {
            return;
        }
        Mode prev = mode;
        mode = next;
        modeDirty = true;
        applyVisibility(next);
        if (prev == Mode.BALL || next == Mode.BALL) {
            fx.ballSwap(next == Mode.BALL);
        }
        keyNow();
    }

    /** Sphere and bubble show only in their own mode; the body hides inside the sphere. */
    private void applyVisibility(Mode which) {
        boolean ball = which == Mode.BALL;
        boolean bubble = which == Mode.BUBBLE;
        for (Rig.Part part : rig.parts) {
            if (part == rig.roles().ball) {
                part.hidden = !ball;
            } else if (part == rig.roles().bubble) {
                part.hidden = !bubble;
            } else {
                part.hidden = ball;
            }
        }
    }

    /** Dragons: wild ones walk, equipped ones take wing. */
    public void setFlying(boolean flying) {
        if (rig.skeleton != Skeleton.DRAGON || this.flying == flying) {
            return;
        }
        this.flying = flying;
        keyNow();
    }

    public boolean isFlying() {
        return flying;
    }

    /** Riding the owner's shoulder: wings folded, heading follows the owner. */
    public void setPerched(boolean perched) {
        if (this.perched == perched) {
            return;
        }
        this.perched = perched;
        keyNow();
    }

    public void happy() {
        happyTicks = 30;
        keyNow();
    }

    public void breathe() {
        if (breathTicks <= 0 && rig.isDragon()) {
            breathTicks = 34;
            keyNow();
        }
    }

    public void keyNow() {
        nextKey = age;
    }

    /** Scale the whole body to {@code target} over {@code ticks} (materialize / vanish). */
    public void presence(float from, float target, int ticks) {
        rig.presence = Math.max(0.001f, from);
        presenceTarget = target;
        presenceTicks = Math.max(0, ticks);
        rig.apply(0, true);
        nextKey = age + 2;
        if (ticks > 0) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    rig.presence = presenceTarget;
                    poseNow(Math.max(1, presenceTicks));
                }, 2L);
            } else {
                rig.presence = target;
            }
        } else {
            rig.presence = target;
        }
    }

    /** Shrink the whole body away over {@code ticks} (vanish); the caller removes it afterwards. */
    public void fade(int ticks) {
        mode = Mode.CAPTURED;
        rig.presence = 0.001f;
        rig.apply(Math.max(1, ticks), true);
    }

    /** Collapse into / out of a sphere at {@code ballWorld} (catch ritual). */
    public void capture(Location ballWorld, int ticks) {
        Location anchor = rig.anchor() == null ? null : rig.anchor().getLocation();
        if (anchor == null || ballWorld == null) {
            return;
        }
        mode = Mode.CAPTURED;
        Vector3f local = new Vector3f((float) (ballWorld.getX() - anchor.getX()),
                (float) (ballWorld.getY() - anchor.getY()), (float) (ballWorld.getZ() - anchor.getZ()));
        rig.collapseTo = local;
        rig.apply(Math.max(1, ticks), true);
    }

    public void release(int ticks) {
        rig.collapseTo = null;
        mode = Mode.NORMAL;
        applyVisibility(Mode.NORMAL);
        modeDirty = true;
        poseNow(Math.max(1, ticks));
        happyTicks = 0;
    }

    public void setGlow(boolean on, Color color) {
        rig.setGlow(on, color);
    }

    public Location center() {
        return rig.worldPoint(0, 0f, rig.middle, 0f);
    }

    public float nameplateHeight() {
        if (mode == Mode.BALL) {
            return 0.55f;
        }
        return rig.scaledTop();
    }

    /*
     * =========================================================
     * TICK
     * =========================================================
     */

    /**
     * @param dt     ticks since the last call
     * @param at     anchor position after this tick's move
     * @param look   look yaw chosen by the movement brain
     * @param frozen catching: the body holds still
     */
    public void tick(int dt, Location at, float look, boolean frozen) {
        age += dt;
        if (at == null || !rig.refreshViewers(false)) {
            return;
        }
        if (rig.wake) {
            rig.wake = false;
            presence(0.05f, 1f, 8);
        }

        // Motion from anchor deltas.
        if (!Double.isNaN(lastX)) {
            double dx = at.getX() - lastX;
            double dz = at.getZ() - lastZ;
            double dy = at.getY() - lastY;
            float horizontal = (float) Math.sqrt(dx * dx + dz * dz) / dt;
            if (horizontal > 2.5f) {
                horizontal = 0f; // teleport, not a sprint
            }
            speed += (horizontal - speed) * 0.55f;
            if (perched) {
                speed = 0f;
            }
            vy += ((float) dy / dt - vy) * 0.5f;
            if (perched) {
                horizontal = 0f;
            }
            if (horizontal > 0.012f) {
                float travel = (float) Math.toDegrees(Math.atan2(-dx, dz));
                steerTo(travel, dt, mode == Mode.BALL ? 4f : 12f);
            } else {
                steerTo(look, dt, 7f);
            }
        } else {
            rig.heading = look;
            targetHeading = look;
        }
        lastX = at.getX();
        lastY = at.getY();
        lastZ = at.getZ();
        turn = wrap(rig.heading - lastHeading) / dt;
        lastHeading = rig.heading;
        rig.lookOffset = wrap(look - rig.heading);

        boolean moving = speed > 0.012f;
        idle = moving ? 0 : idle + dt;
        tickActions(dt, moving);

        // Phase advance.
        double rate = phaseRate(moving);
        phase += rate * dt;
        if (phase > Math.PI * 2000) {
            phase -= Math.PI * 2000;
        }

        if (mode != Mode.CAPTURED) {
            fx.tick(dt, at, moving, rig.near());
        }

        if (frozen || mode == Mode.CAPTURED) {
            return;
        }
        double nearestSq = rig.nearestViewerSq();
        boolean near = rig.near();
        boolean visible = nearestSq <= 44.0 * 44.0 || equipped;
        float headingDrift = Float.isNaN(keyedTarget) ? 999f : Math.abs(wrap(targetHeading - keyedTarget));
        boolean due = age >= nextKey || modeDirty || headingDrift > (near ? 12f : 25f);
        if (!due || !visible) {
            return;
        }
        if (!takeBudget(rig.parts.size(), equipped && near)) {
            nextKey = age + 1;
            return;
        }

        int lookahead = lookahead(moving, rate, near);
        // Pose for where the body will be facing when the keyframe lands, then restore.
        float now = rig.heading;
        float cap = turnRate * lookahead;
        rig.heading = wrap(now + Math.max(-cap, Math.min(cap, wrap(targetHeading - now))));
        poseAt(lookahead, rate, moving, near);
        rig.apply(modeDirty ? Math.max(4, lookahead) : lookahead, near);
        rig.heading = now;
        keyedTarget = targetHeading;
        modeDirty = false;
        nextKey = age + lookahead;
    }

    private void poseNow(int interpolation) {
        poseAt(0, phaseRate(speed > 0.012f), speed > 0.012f, rig.near());
        rig.apply(interpolation, true);
        keyedTarget = targetHeading;
        nextKey = age + interpolation;
    }

    private void poseAt(int lookahead, double rate, boolean moving, boolean near) {
        Gaits.Motion m = motion;
        m.t = age + lookahead;
        m.phase = phase + rate * lookahead;
        m.speed = speed;
        m.vy = vy;
        m.turn = turn;
        m.moving = moving;
        m.equipped = equipped;
        m.flying = flying;
        m.near = near;
        m.mode = mode;
        m.idle = idle;
        m.seed = seed;
        m.breath = breathTicks > 0 ? envelope(breathTicks, 34, 6) : 0f;
        m.happy = happyTicks > 0 ? envelope(happyTicks, 30, 5) : 0f;
        m.puff = puffTicks > 0 ? envelope(puffTicks, 60, 8) : 0f;
        m.roll = rollTicks > 0 ? envelope(rollTicks, 80, 10) : 0f;
        m.flex = flexTicks > 0 ? envelope(flexTicks, 40, 10) : 0f;
        m.sit = sit;
        m.glide = glideTicks > 0 ? envelope(glideTicks, 50, 8) : 0f;
        m.perched = perched;
        Gaits.pose(rig, m);
    }

    private static float envelope(int left, int total, int ramp) {
        int done = total - left;
        if (done < ramp) {
            return done / (float) ramp;
        }
        if (left < ramp) {
            return left / (float) ramp;
        }
        return 1f;
    }

    /** Ticks until the next keyframe: on a gait extreme for cycles, slower for idle life. */
    private int lookahead(boolean moving, double rate, boolean near) {
        int min = near ? 2 : 4;
        Skeleton sk = rig.skeleton;
        boolean cyclic = !perched
                && (moving || sk.airborne() || sk.aquatic() || sk == Skeleton.WYRM || (sk == Skeleton.DRAGON && flying));
        if (mode == Mode.BALL) {
            return near ? 8 : 12;
        }
        if (!cyclic || rate <= 0.0) {
            int idleKey = sk == Skeleton.HOVER && near ? 2 : (happyTicks > 0 ? 3 : 8);
            return near ? idleKey : idleKey * 2;
        }
        double quantum = sk == Skeleton.FLUTTER || (sk == Skeleton.FLAP && glideTicks <= 0) || sk == Skeleton.SOAR
                ? Math.PI : Math.PI / 2.0;
        double offset = 0.0;
        boolean symmetric = sk == Skeleton.TROT || sk == Skeleton.STRIDE || sk == Skeleton.LUMBER
                || sk == Skeleton.SCUTTLE || sk == Skeleton.WADDLE || sk == Skeleton.CRAWL;
        if (symmetric && moving && speed <= 0.17f && rate > 2 * Math.PI / 12.0) {
            // Quick two-phase gaits: key only the swing extremes, the client draws the swing between.
            quantum = Math.PI;
            offset = Math.PI / 2.0;
        }
        if (sk == Skeleton.WYRM || sk == Skeleton.DRIFT || sk == Skeleton.HAUNT) {
            return near ? (moving ? 5 : 8) : (moving ? 10 : 14);
        }
        if (sk == Skeleton.HOVER) {
            return near ? 2 : 6;
        }
        double next = (Math.floor((phase - offset) / quantum) + 1.0) * quantum + offset;
        double ticks = (next - phase) / rate;
        while (ticks < min) {
            next += quantum;
            ticks = (next - phase) / rate;
        }
        int key = (int) Math.round(ticks);
        if (!near) {
            key = Math.max(key, 5);
        }
        return Math.max(min, Math.min(14, key));
    }

    /** Radians per tick of the gait cycle. */
    private double phaseRate(boolean moving) {
        Skeleton sk = rig.skeleton;
        float size = Math.max(0.3f, rig.size);
        switch (sk) {
            case FLUTTER:
                return 2 * Math.PI / 9.0;
            case FLAP:
                return 2 * Math.PI / (moving ? 9.0 : 6.0);
            case SOAR:
                return 2 * Math.PI / 16.0;
            case HOVER:
                return 2 * Math.PI / 4.0;
            case SWIM:
                return 2 * Math.PI / (moving ? Math.max(7.0, 14.0 - speed * 60.0) : 22.0);
            case JET:
                return 2 * Math.PI / 30.0;
            case PORPOISE:
                return 2 * Math.PI / (moving ? 16.0 : 30.0);
            case WYRM:
                return 2 * Math.PI / (moving ? Math.max(14.0, 30.0 - speed * 80.0) : 40.0);
            case DRAGON:
                if (flying) {
                    return 2 * Math.PI / (glideTicks > 0 ? 30.0 : 18.0);
                }
                break;
            default:
                break;
        }
        if (!moving) {
            return 0.0;
        }
        double period = sk.stride() * size / Math.max(0.01f, speed);
        double min = sk == Skeleton.HOP || sk == Skeleton.BOUNCE ? 9.0 : 6.0;
        period = Math.max(min, Math.min(28.0, period));
        return 2 * Math.PI / period;
    }

    private void tickActions(int dt, boolean moving) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        breathTicks = Math.max(0, breathTicks - dt);
        happyTicks = Math.max(0, happyTicks - dt);
        puffTicks = Math.max(0, puffTicks - dt);
        rollTicks = Math.max(0, rollTicks - dt);
        flexTicks = Math.max(0, flexTicks - dt);
        glideTicks = Math.max(0, glideTicks - dt);
        glideCooldown = Math.max(0, glideCooldown - dt);

        boolean near = rig.near();
        String id = pet == null || pet.getDefinition() == null ? "" : pet.getDefinition().getId();
        Skeleton sk = rig.skeleton;

        // Canines and cats sit when their owner stands around.
        if (equipped && sk == Skeleton.TROT && !moving && idle > 120) {
            if (sit < 1f) {
                sit = Math.min(1f, sit + 0.25f);
                keyNow();
            }
        } else if (sit > 0f) {
            sit = 0f;
            keyNow();
        }
        if (!near) {
            return;
        }
        if (glideCooldown <= 0 && (sk == Skeleton.FLAP || sk == Skeleton.SOAR || (sk == Skeleton.DRAGON && flying))
                && moving && random.nextInt(sk == Skeleton.SOAR ? 3 : 8) == 0) {
            glideTicks = 50;
            glideCooldown = 140;
        }
        if (puffTicks <= 0 && id.equals("pufferfish") && random.nextInt(600) < dt) {
            puffTicks = 60;
            keyNow();
        }
        if (rollTicks <= 0 && id.equals("armadillo") && !moving && idle > 80 && random.nextInt(900) < dt) {
            rollTicks = 80;
            keyNow();
        }
        if (flexTicks <= 0 && sk == Skeleton.DRAGON && !flying && !moving && random.nextInt(700) < dt) {
            flexTicks = 40;
            keyNow();
        }
        if (breathTicks <= 0 && rig.isDragon() && !moving && idle > 100 && random.nextInt(1400) < dt) {
            breathe();
        }
        if (breathTicks > 0) {
            fx.breath(breathTicks);
        }
    }

    private void steerTo(float target, int dt, float maxPerTick) {
        targetHeading = target;
        turnRate = maxPerTick;
        float delta = wrap(target - rig.heading);
        float step = maxPerTick * dt;
        if (Math.abs(delta) <= step) {
            rig.heading = target;
        } else {
            rig.heading = wrap(rig.heading + Math.copySign(step, delta));
        }
    }

    static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) {
            deg -= 360f;
        }
        if (deg < -180f) {
            deg += 360f;
        }
        return deg;
    }

    private static boolean takeBudget(int cost, boolean priority) {
        long now = Bukkit.getCurrentTick();
        if (now != budgetTick) {
            budgetTick = now;
            budgetUsed = 0;
        }
        if (!priority && budgetUsed + cost > BUDGET_PER_TICK) {
            return false;
        }
        budgetUsed += cost;
        return true;
    }

    float speed() {
        return speed;
    }

    int age() {
        return age;
    }

    Rarity rarity() {
        return pet == null || pet.getRarity() == null ? Rarity.COMMON : pet.getRarity();
    }

    public boolean breathing() {
        return breathTicks > 0;
    }
}
