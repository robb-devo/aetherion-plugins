package de.aetherion.bossengine.instance.eggquelizer;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.BossPhase;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static de.aetherion.bossengine.instance.eggquelizer.EggMath.PI;
import static de.aetherion.bossengine.instance.eggquelizer.EggMath.arc;
import static de.aetherion.bossengine.instance.eggquelizer.EggMath.clamp01;
import static de.aetherion.bossengine.instance.eggquelizer.EggMath.lerp;
import static de.aetherion.bossengine.instance.eggquelizer.EggMath.semitone;
import static de.aetherion.bossengine.instance.eggquelizer.EggMath.smooth;
import static de.aetherion.bossengine.instance.eggquelizer.EggMath.window;

/**
 * EGGQUELIZER. A giant egg on tiny legs with a speaker for a face, running the arena's PA.
 *
 * <p>The joke is the egg. The fight is dead serious: everything is quantized to a beat, every
 * arena speaker telegraphs through one honest state machine (IDLE, CHARGING, PRIMED, FIRING,
 * COOLDOWN), every hit is a physical pulse you can jump or sidestep, and silence is a weapon.
 *
 * <p>Acts: RAISING (stage unfolds) → INTRO (soundcheck: the network plays its melody in loop
 * order, teaching the feedback path) → FIGHT ⇄ TRANSITION (60%: OVERCLOCK setpiece, then it falls
 * on its back; 30%: DISTORTION, the shell cracks and the lid pops off) → DYING (final charge,
 * hard silence, the network dies speaker by speaker, the egg tips over like a dead appliance,
 * and lays one tiny egg).
 *
 * <p>Moves: BASS_DROP (aimed frontal pressure wave, jump it), CHANNELS (left/right wall banks,
 * the lamps never lie, the egg sometimes does), SUBWOOFER (floor launch pads), SUB_HOP (the egg
 * launches itself off its own sub), TWEETERS (treble balls from the towers), FEEDBACK (a signal
 * runs the cable loop and every node fires in order), OVERCLOCK (setpiece), TIPPED (punish window).
 */
public final class EggquelizerDirector {

    public static final String ID = "eggquelizer";

    private enum Act { NONE, RAISING, INTRO, FIGHT, TRANSITION, DYING, DONE }

    private enum Move { IDLE, WADDLE, BASS_DROP, CHANNELS, SUBWOOFER, SUB_HOP, TWEETERS, FEEDBACK, OVERCLOCK, TIPPED }

    private static final int INTRO_TICKS = 152;
    private static final int OVERCLOCK_TICKS = 222;
    private static final int DISTORT_TICKS = 170;
    private static final int DEATH_TICKS = 300;
    private static final int STRIKE_DELAY_TICKS = 120;
    private static final float EGG_R = 12f;

    /** Loop melody: rising then falling pentatonic arch. The soundcheck teaches it, feedback replays it. */
    private static final int[] MELODY = {0, 3, 5, 7, 10, 12, 15, 17, 19, 22, 24, 22, 19, 17, 15, 12};

    private static final Color AIR = Color.fromRGB(190, 230, 255);
    private static final Color SIGNAL = Color.fromRGB(120, 255, 255);
    private static final Color SIGNAL_HOT = Color.fromRGB(255, 60, 60);
    private static final Color TREBLE = Color.fromRGB(255, 255, 255);

    private final BossInstance instance;

    private EggFx fx;
    private EggStage stage;
    private EggBody body;
    private EggProps props;

    private Act act = Act.NONE;
    private int actTick;
    private Move move = Move.IDLE;
    private Move lastMove = Move.IDLE;
    private int moveTick;
    private int cooldown = 40;
    private int phase = 1;
    private int clock;
    private int beatClock;
    private int beatLen = 10;
    /** Ticks of silence left: the background beat holds its breath. */
    private int silence;
    private boolean deathFinished;
    private boolean encoreDone;
    private int transitionKind;
    private int transitionLength;
    private float walkPhase;
    private boolean stepSide;

    private UUID target;
    private double lastHealth;
    private int lastHitReact;
    private int smugUntil;
    private final Map<String, Integer> gate = new HashMap<>();
    private final Set<String> taught = new HashSet<>();

    private final List<EggProps.RingWave> waves = new ArrayList<>();
    private final List<EggProps.LaneWall> lanes = new ArrayList<>();
    private final List<Signal> signals = new ArrayList<>();
    private EggProps.Lid lid;
    private ItemDisplay chick;

    /* move scratch */
    private final Vector3f mA = new Vector3f();
    private final Vector3f mB = new Vector3f();
    private float mF;
    private int mI;
    private int mJ;
    private int mK;
    private int mFire;
    private boolean mFlag;
    private final List<EggSpeaker> mList = new ArrayList<>();

    /** A signal running the cable loop (Feedback). */
    private static final class Signal {
        int node;
        int dir;
        int left;
        int hopsDone;
        int reverseAt = -1;
        int arcAt = -1;
        boolean done;
    }

    public EggquelizerDirector(BossInstance instance) {
        this.instance = instance;
    }

    /* ================================================================== engine hooks */

    public boolean isMine() {
        return instance.getTemplate() != null && ID.equalsIgnoreCase(instance.getTemplate().getId());
    }

    /** The director drives the body: vanilla AI, unstick, leash and visibility polish stay out. */
    public boolean ownsBody() {
        return isMine() && act != Act.NONE;
    }

    /** The director stages both phase transitions itself. */
    public boolean ownsTransition() {
        return isMine();
    }

    public boolean isDying() {
        return isMine() && act == Act.DYING;
    }

    public boolean blocksDamage() {
        return isMine() && (act == Act.RAISING || act == Act.INTRO || act == Act.DYING);
    }

    public void onBind() {
        if (!isMine()) {
            return;
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null) {
            return;
        }
        prepareBody(entity);
        if (fx != null) {
            // Same fight, new body (chunk reload / restore): the show keeps running.
            placeHitbox();
            fx.scrubProps();
            return;
        }
        stage = EggStage.claim(instance.getPlugin(), instance.getSpawnLocation());
        fx = new EggFx(instance, stage.center());
        props = new EggProps();
        lastHealth = instance.getCombatHealth();
        phase = phaseFromTemplate();
        beatLen = beatFor(phase);
        act = Act.RAISING;
        actTick = 0;
        placeHitbox();
    }

    public boolean beginDeath() {
        if (!isMine() || act == Act.DYING || act == Act.DONE || fx == null) {
            return false;
        }
        endMove();
        act = Act.DYING;
        actTick = body == null ? DEATH_TICKS : 0;
        LivingEntity entity = instance.getEntity();
        if (entity != null) {
            entity.setInvulnerable(true);
        }
        return true;
    }

    public void abort() {
        if (isMine()) {
            clear();
        }
    }

    /** Remove everything this encounter spawned. Idempotent. */
    public void clear() {
        if (fx == null) {
            return;
        }
        endMove();
        signals.clear();
        waves.clear();
        lanes.clear();
        props.clear();
        lid = null;
        fx.scrubProps();
        org.bukkit.plugin.Plugin plugin = instance.getPlugin();
        if (body != null) {
            if (deathFinished && plugin.isEnabled()) {
                // The dead appliance lingers a moment, then unplugs.
                List<BlockDisplay> corpse = body.detach();
                ItemDisplay egg = chick;
                Location puff = fx.at(body.centerPoint());
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (BlockDisplay d : corpse) {
                        EggFx.kill(d);
                    }
                    EggFx.kill(egg);
                    if (puff.getWorld() != null) {
                        puff.getWorld().spawnParticle(Particle.CLOUD, puff, 30, 1.2, 1.2, 1.2, 0.02);
                        puff.getWorld().playSound(puff, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
                    }
                }, 100L);
            } else {
                body.remove();
                EggFx.kill(chick);
            }
        } else {
            EggFx.kill(chick);
        }
        chick = null;
        if (stage != null) {
            boolean animated = deathFinished && plugin.isEnabled();
            stage.strike(plugin, animated ? STRIKE_DELAY_TICKS : -1);
        }
        fx = null;
        body = null;
        stage = null;
        act = Act.NONE;
    }

    /**
     * @return true when the death cinematic finished and loot should be paid
     */
    public boolean tick() {
        if (!isMine() || fx == null) {
            return false;
        }
        clock++;
        if (clock == 1) {
            fx.scrubProps();
        }
        LivingEntity entity = instance.getEntity();
        if (entity != null && entity.isValid()) {
            maintainBody(entity);
        }
        if (act == Act.RAISING) {
            tickRaise();
            placeHitbox();
            return false;
        }
        if (body == null) {
            if (act == Act.DYING) {
                deathFinished = true;
                act = Act.DONE;
                return true;
            }
            return false;
        }
        repairIfUnloaded();

        boolean finished = false;
        switch (act) {
            case INTRO -> tickIntro();
            case FIGHT -> {
                if (instance.isTransitioning()) {
                    beginTransition();
                    tickTransition();
                } else {
                    tickFight();
                }
            }
            case TRANSITION -> tickTransition();
            case DYING -> finished = tickDeath();
            default -> {
            }
        }

        body.tickSprings();
        body.render();
        stage.tickSpeakers();
        props.tick();
        // Safety net: if prop tracking desynced, wipe stained-glass orphans without touching the cast.
        if (clock % 100 == 0 && props.live().stream().noneMatch(p -> !p.persistent())) {
            fx.scrubProps();
        }
        if (act == Act.FIGHT || act == Act.TRANSITION) {
            tickHazards();
            tickBeat();
            rescueFallers();
        }
        placeHitbox();
        if (finished) {
            deathFinished = true;
            act = Act.DONE;
        }
        return finished;
    }

    /* ================================================================== setup */

    private void prepareBody(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
            mob.setTarget(null);
        }
        entity.setGravity(false);
        entity.setInvisible(true);
        entity.setSilent(true);
        entity.setFireTicks(0);
        entity.setVelocity(new Vector());
        if (entity.getEquipment() != null) {
            entity.getEquipment().clear();
        }
    }

    private void maintainBody(LivingEntity entity) {
        if (entity instanceof Mob mob && mob.hasAI()) {
            mob.setAI(false);
            mob.setAware(false);
        }
        if (entity.hasGravity()) {
            entity.setGravity(false);
        }
        if (!entity.isInvisible()) {
            entity.setInvisible(true);
        }
        // Must stay collidable: projectiles otherwise pass through the invisible hitbox.
        if (!entity.isCollidable()) {
            entity.setCollidable(true);
        }
        entity.setFireTicks(0);
        entity.setFallDistance(0f);
    }

    private int phaseFromTemplate() {
        BossPhase current = instance.getCurrentPhase();
        if (current == null) {
            return 1;
        }
        double hp = current.getHealthPercent();
        return hp > 60.5 ? 1 : hp > 30.5 ? 2 : 3;
    }

    private static int beatFor(int phase) {
        return phase >= 3 ? 8 : phase == 2 ? 9 : 10;
    }

    private void spawnCast() {
        stage.spawnRig(fx);
        body = new EggBody();
        body.pos.set(0f, 0f, 0f);
        body.yaw = 0f;
        // Unplugged: slumped forward, sitting on its little legs.
        body.pitch.snap(0.55f);
        body.squash.snap(0.78f);
        body.bob = -0.35f;
        body.spawn(fx);
        body.ledsOff();
        if (phase >= 3) {
            applyDistortedLook(false);
        }
    }

    /** If players left and the chunk unloaded, non-persistent displays are gone: rebuild them. */
    private void repairIfUnloaded() {
        if (clock % 20 != 0) {
            return;
        }
        if (body.intact() && stage.rigIntact()) {
            return;
        }
        props.clear();
        waves.clear();
        lanes.clear();
        signals.clear();
        lid = null;
        fx.scrubProps();
        body.remove();
        stage.removeRig();
        stage.spawnRig(fx);
        body.spawn(fx);
        if (phase >= 3) {
            applyDistortedLook(false);
        }
    }

    private void placeHitbox() {
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || fx == null) {
            return;
        }
        float x = body == null ? 0f : body.pos.x;
        float z = body == null ? 0f : body.pos.z;
        float y = body == null ? 0.05f : Math.max(0.05f, body.pos.y + body.air);
        Location at = fx.at(x, y, z);
        at.setYaw((float) Math.toDegrees(-(body == null ? 0f : body.yaw)));
        at.setPitch(0f);
        Location current = entity.getLocation();
        if (current.getWorld() != at.getWorld() || current.distanceSquared(at) > 0.0004) {
            instance.runInternalTeleport(() -> entity.teleport(at));
        }
        entity.setVelocity(new Vector());
    }

    /* ================================================================== raise */

    private void tickRaise() {
        actTick++;
        if (actTick == 1) {
            fx.score(Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
        }
        float progress = stage.raiseStep(260);
        if (progress >= 1f) {
            seatAudience();
            spawnCast();
            act = Act.INTRO;
            actTick = 0;
            beatClock = 0;
        }
    }

    /** Players near the spawn are brought up onto the stage (it may float above the spawn). */
    private void seatAudience() {
        Location spawn = instance.getSpawnLocation();
        Location c = stage.center();
        if (spawn.getWorld() == null || Math.abs(c.getY() - spawn.getY()) < 3) {
            return;
        }
        for (Player p : spawn.getWorld().getPlayers()) {
            Location l = p.getLocation();
            double dx = l.getX() - spawn.getX();
            double dz = l.getZ() - spawn.getZ();
            if (dx * dx + dz * dz > 34 * 34 || Math.abs(l.getY() - spawn.getY()) > 20) {
                continue;
            }
            double a = Math.atan2(dx, dz);
            Location to = c.clone().add(Math.sin(a) * 12, 0.1, Math.cos(a) * 12);
            to.setDirection(c.toVector().subtract(to.toVector()).setY(0));
            p.teleport(to);
            p.setFallDistance(0f);
            p.playSound(to, Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);
        }
    }

    /* ================================================================== intro: SOUNDCHECK */

    private void tickIntro() {
        int t = actTick++;
        beatClock++;
        Vector3f face = body.facePoint();
        if (t == 12 || t == 22) {
            // Mic taps.
            fx.sound(face, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.8f, 0.55f);
            fx.sound(face, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.2f, 0.5f);
            body.cone.kick(0.18f);
            body.squash.kick(-0.03f);
        }
        if (t >= 32 && t < 40) {
            // Feedback squeal: it wakes up yelling.
            fx.sound(face, Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.9f, 1.6f + (t - 32) * 0.05f);
            if (t % 2 == 0) {
                fx.sound(face, Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, 2f);
            }
        }
        if (t == 34) {
            body.pitch.target = 0f;
            body.pitch.kick(-0.25f);
            body.squash.target = 1f;
            body.squash.kick(0.12f);
            fx.sound(body.pos, Sound.ENTITY_CHICKEN_STEP, 1.6f, 0.6f);
        }
        if (t >= 34) {
            body.bob = lerp(body.bob, 0f, 0.2f);
        }
        if (t >= 42 && t < 50) {
            body.leds(t % 2 == 0 ? 5 : 0);
        }
        if (t == 50) {
            body.leds(0);
        }
        // SOUNDCHECK: every installation answers in loop order, one note each.
        int n = stage.size();
        if (t >= 52 && t < 52 + n * 4) {
            int k = t - 52;
            int i = k / 4;
            EggSpeaker s = stage.node(i);
            if (k % 4 == 0) {
                s.wakeFlash();
                fx.clean(s.cone(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.3f, semitone(MELODY[i]));
                fx.clean(s.cone(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, semitone(MELODY[i]));
                fx.particle(Particle.NOTE, new Vector3f(s.cone()).add(0f, 1.2f, 0f), 1, 0.0, 1.0);
                stage.cableLit(i, SIGNAL);
                if (i > 0) {
                    stage.cableLit(i - 1, null);
                }
                body.leds(1 + i % 5);
            }
            turnToward(s.base, 0.22f);
        }
        if (t == 52 + n * 4) {
            stage.cableLit(n - 1, null);
            body.leds(0);
        }
        if (t >= 118 && t < 140) {
            Player p = currentTarget();
            if (p != null) {
                turnToward(fx.stage(p.getLocation()), 0.12f);
            }
            body.pitch.target = -0.14f;
            body.roll.target = 0.08f;
        }
        if (t == 120) {
            fx.title("&f&lEGGQUELIZER", "&7check one, two.", 6, 40, 14);
        }
        if (t == 122 || t == 132 || t == 142) {
            fx.sound(face, Sound.BLOCK_NOTE_BLOCK_HAT, 1.4f, 1.1f);
            body.leds((t - 112) / 10 + 1);
        }
        if (t >= 142) {
            body.squash.target = 0.84f;
            body.coneHot(true);
            body.cone.target = -0.3f;
        }
        if (t >= INTRO_TICKS) {
            body.pitch.target = 0f;
            body.roll.target = 0f;
            body.squash.target = 1f;
            body.squash.kick(0.3f);
            body.cone.target = 0f;
            body.cone.value = 0.5f;
            body.coneHot(false);
            body.leds(0);
            drop(face, 0.9f);
            EggProps.RingWave show = props.add(new EggProps.RingWave(fx, body.pos, body.yaw, PI, 2f, 22f, 0.6f, 20,
                    Material.WHITE_STAINED_GLASS, AIR));
            show.power = 0;
            for (EggSpeaker s : stage.speakers) {
                s.kick(0.3f);
            }
            act = Act.FIGHT;
            actTick = 0;
            beatClock = 0;
            cooldown = 30;
            move = Move.IDLE;
        }
    }

    /* ================================================================== the beat */

    private boolean onBeat() {
        return beatClock % beatLen == 0;
    }

    /** Ticks until a beat at least {@code min} ticks away. */
    private int toBeat(int min) {
        int t = Math.max(1, min);
        while ((beatClock + t) % beatLen != 0) {
            t++;
        }
        return t;
    }

    private void tickBeat() {
        beatClock++;
        if (silence > 0) {
            silence--;
            return;
        }
        if (act != Act.FIGHT || move == Move.TIPPED) {
            return;
        }
        if (onBeat()) {
            int bar = (beatClock / beatLen) % 4;
            fx.sound(body.centerPoint(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, bar == 0 ? 0.9f : 0.55f, 0.55f);
            if (bar == 0) {
                fx.sound(body.centerPoint(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
            }
            for (EggSpeaker s : stage.speakers) {
                s.kick(0.05f);
            }
            if (move == Move.IDLE || move == Move.WADDLE) {
                body.squash.kick(-0.035f);
                body.cone.kick(0.08f);
            }
            body.tip(true);
        } else if (beatClock % beatLen == 3) {
            body.tip(false);
        }
        if (phase >= 2 && beatClock % beatLen == beatLen / 2) {
            EggSpeaker s = stage.walls.get(ThreadLocalRandom.current().nextInt(stage.walls.size()));
            fx.sound(s.cone(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.5f);
        }
    }

    /* ================================================================== fight */

    private void tickFight() {
        actTick++;
        hitReactions();
        switch (move) {
            case IDLE -> idle();
            case WADDLE -> waddle();
            case BASS_DROP -> bassDrop(moveTick++);
            case CHANNELS -> channels(moveTick++);
            case SUBWOOFER -> subwoofer(moveTick++);
            case SUB_HOP -> subHop(moveTick++);
            case TWEETERS -> tweeters(moveTick++);
            case FEEDBACK -> feedback(moveTick++);
            case OVERCLOCK -> {
                if (overclock(moveTick++, true)) {
                    begin(Move.TIPPED);
                }
            }
            case TIPPED -> tipped(moveTick++);
        }
        if (phase >= 3) {
            body.jitter = Math.max(body.jitter, 0.012f);
        }
    }

    private void idle() {
        Player p = currentTarget();
        body.squash.target = 1f;
        body.cone.target = 0f;
        body.jitter = phase >= 3 ? 0.012f : 0f;
        if (clock < smugUntil) {
            body.pitch.target = -0.16f;
            body.roll.target = 0.1f;
        } else {
            body.pitch.target = 0f;
            // Sway with the beat.
            body.roll.target = (float) Math.sin(beatClock * PI / beatLen) * 0.05f;
        }
        if (p != null) {
            Vector3f pp = fx.stage(p.getLocation());
            float d = EggMath.horizontal(pp, body.pos);
            turnToward(pp, 0.08f);
            if (d > 11f) {
                stepToward(pp, 0.11f, true);
            } else if (d < 4.5f) {
                // Back away without turning around: an egg moonwalk.
                Vector3f away = new Vector3f(body.pos).sub(pp);
                away.y = 0f;
                if (away.lengthSquared() < 0.01f) {
                    away.set(0f, 0f, 1f);
                }
                away.normalize(4f).add(body.pos);
                stepToward(away, 0.09f, false);
            } else {
                settleLegs();
            }
        } else {
            settleLegs();
        }
        if (--cooldown <= 0) {
            chooseMove();
        }
    }

    private void chooseMove() {
        if (phase >= 3 && !encoreDone && instance.getCombatHealth() <= instance.getCombatMaxHealth() * 0.15) {
            encoreDone = true;
            begin(Move.OVERCLOCK);
            return;
        }
        List<Move> bag = new ArrayList<>();
        List<Float> weight = new ArrayList<>();
        option(bag, weight, Move.BASS_DROP, 3f);
        option(bag, weight, Move.CHANNELS, 2.5f);
        option(bag, weight, Move.SUBWOOFER, 2f);
        option(bag, weight, Move.FEEDBACK, phase == 1 ? 1.6f : 2.4f);
        option(bag, weight, Move.TWEETERS, phase == 1 ? 1.2f : 2f);
        if (phase >= 2) {
            option(bag, weight, Move.SUB_HOP, 1.6f);
        }
        float total = 0f;
        for (float w : weight) {
            total += w;
        }
        float r = ThreadLocalRandom.current().nextFloat() * total;
        for (int i = 0; i < bag.size(); i++) {
            r -= weight.get(i);
            if (r <= 0f) {
                begin(bag.get(i));
                return;
            }
        }
        begin(bag.get(0));
    }

    private void option(List<Move> bag, List<Float> weight, Move m, float w) {
        if (m == lastMove) {
            return;
        }
        bag.add(m);
        weight.add(w);
    }

    private void begin(Move m) {
        endMove();
        move = m;
        moveTick = 0;
        mI = 0;
        mJ = 0;
        mK = 0;
        mF = 0f;
        mFire = -1;
        mFlag = false;
        mList.clear();
        if (m != Move.TIPPED && m != Move.WADDLE) {
            lastMove = m;
        }
    }

    private void finish(int cool) {
        endMove();
        move = Move.IDLE;
        moveTick = 0;
        cooldown = Math.max(8, cool - (phase - 1) * 6);
        body.coneHot(false);
        body.cone.target = 0f;
        body.jitter = 0f;
        body.overfill = 0f;
        body.squash.target = 1f;
        body.pitch.target = 0f;
        body.roll.target = 0f;
        body.air = 0f;
        body.kickL = 0f;
        body.kickR = 0f;
        body.leds(0);
    }

    /** Tear down move-owned state. Speakers keep their own state machines running. */
    private void endMove() {
        if (body != null) {
            body.frozen = false;
            body.interp(2);
        }
    }

    private void waddle() {
        int t = moveTick++;
        if (stepToward(mA, 0.13f, true) || t > 80) {
            finish(10);
        }
    }

    /* ================================================================== BASS DROP */

    private void bassDrop(int t) {
        int aim = phase == 1 ? 24 : phase == 2 ? 20 : 16;
        if (t == 0) {
            Player p = currentTarget();
            mA.set(p == null ? new Vector3f(0f, 0f, 6f) : fx.stage(p.getLocation()));
            mFlag = phase >= 2 && ThreadLocalRandom.current().nextFloat() < 0.35f;
            fx.sound(body.facePoint(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.5f);
        }
        if (t < aim) {
            float p = t / (float) aim;
            Player pl = currentTarget();
            if (pl != null) {
                mA.set(fx.stage(pl.getLocation()));
            }
            turnToward(mA, 0.12f);
            settleLegs();
            body.squash.target = 1f - 0.16f * p;
            body.pitch.target = -0.12f * p;
            body.cone.target = -0.3f * p;
            body.jitter = 0.015f + 0.04f * p;
            body.leds(Math.round(5 * p));
            if (t % 3 == 0) {
                fx.sound(body.facePoint(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.5f + 0.6f * p);
            }
            return;
        }
        if (t == aim) {
            // Lock. Then nothing. The silence is the telegraph.
            int hold = phase == 1 ? 12 : phase == 2 ? 10 : 8;
            if (mFlag && mI == 0) {
                hold += 16;
            }
            mFire = aim + toBeat(hold);
            silence = mFire - aim + 2;
            body.jitter = 0f;
            body.cone.snap(-0.32f);
            body.coneHot(true);
            body.leds(5);
            fx.sound(body.facePoint(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.5f);
            fx.particle(Particle.CLOUD, body.facePoint(), 6, 0.3, 0.02);
            float arcHalf = 0.95f;
            int life = mFire - aim + 3;
            for (int s = -1; s <= 1; s += 2) {
                Vector3f dir = EggMath.flat(body.yaw + s * arcHalf, 1f);
                Vector3f a = new Vector3f(body.pos).fma(1.6f, dir);
                Vector3f b = new Vector3f(body.pos).fma(15f, dir);
                props.add(new EggProps.Stripe(fx, a, b, 0.32f, Material.RED_STAINED_GLASS, EggSpeaker.RED, life));
            }
            return;
        }
        if (t < mFire) {
            return;
        }
        if (t == mFire) {
            Vector3f dir = EggMath.flat(body.yaw, 1f);
            Vector3f origin = new Vector3f(body.pos).fma(1.4f, dir);
            EggProps.RingWave w = props.add(new EggProps.RingWave(fx, origin, body.yaw, 0.95f, 1.2f, 24f, 0.85f, 26,
                    Material.WHITE_STAINED_GLASS, AIR));
            w.power = 12;
            w.knock = 1.5f;
            waves.add(w);
            // Point blank: the air itself hits you.
            for (Player p : fx.targets()) {
                Vector3f pp = fx.stage(p.getLocation());
                Vector3f rel = new Vector3f(pp).sub(body.pos);
                float d = EggMath.radius(rel);
                if (d < 3.6f && Math.abs(EggMath.wrap((float) Math.atan2(rel.x, rel.z) - body.yaw)) < 1.0f) {
                    strike(p, 12, body.pos, 1.8f, 0.5f, "blank" + clock, 1);
                    w.hit.add(p.getUniqueId());
                }
            }
            drop(body.facePoint(), 1f);
            body.coneHot(false);
            body.cone.value = 0.55f;
            body.cone.target = 0f;
            body.leds(0);
            body.squash.target = 1f;
            body.squash.kick(0.32f);
            body.pitch.kick(-0.3f);
            body.pitch.target = 0f;
            mB.set(dir).mul(-1f);
            return;
        }
        int after = t - mFire;
        if (after <= 7) {
            // Recoil: its own bass shoves it back on its tiny legs.
            Vector3f to = new Vector3f(body.pos).fma(0.26f * (1f - after / 8f), mB);
            clampArena(to);
            body.pos.set(to);
            scramble(after);
        } else {
            settleLegs();
        }
        if (after == 26) {
            if (phase >= 2 && mI == 0 && ThreadLocalRandom.current().nextFloat() < 0.4f) {
                // Double drop: re-aim fast at someone else.
                mI = 1;
                mFlag = false;
                moveTick = aim - (phase == 2 ? 10 : 12);
                Player other = otherTarget();
                if (other != null) {
                    target = other.getUniqueId();
                }
                return;
            }
            finish(34);
        }
    }

    /** The drop itself: everything heard as one physical hit. */
    private void drop(Vector3f at, float scale) {
        fx.sound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.3f * scale, 0.7f);
        fx.sound(at, Sound.BLOCK_NOTE_BLOCK_BASS, 2f, 0.5f);
        fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f * scale, 0.5f);
        fx.sound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f * scale, 0.6f);
        fx.particle(Particle.SONIC_BOOM, at, 1, 0.0, 0.0);
    }

    /* ================================================================== CHANNELS */

    private void channels(int t) {
        int volleys = phase == 1 ? 4 : phase == 2 ? 6 : 8;
        int spacing = phase == 1 ? 3 : 2;
        if (t == 0) {
            Player p = currentTarget();
            Vector3f pp = p == null ? new Vector3f(0f, 0f, 8f) : fx.stage(p.getLocation());
            float want = EggMath.yawToward(pp.x, pp.z);
            // Snap the facing between two walls so the split into banks is clean.
            float step = PI / 4f;
            mF = Math.round((want - step / 2f) / step) * step + step / 2f;
            mA.set(body.pos);
            if (EggMath.radius(mA) > 3f) {
                mA.normalize(2.5f);
            }
            mI = 0;
            mJ = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            mK = toBeat(26);
            fx.sound(body.facePoint(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
        }
        if (t < mK) {
            stepToward(mA, 0.16f, false);
            body.yaw = EggMath.approachAngle(body.yaw, mF, 0.12f);
            return;
        }
        body.yaw = EggMath.approachAngle(body.yaw, mF, 0.12f);
        settleLegs();
        int local = t - mK;
        int period = spacing * beatLen;
        if (mI < volleys && local == mI * period + 5) {
            int side = mJ;
            boolean full = phase >= 3 && mI % 3 == 2;
            boolean fake = !full && phase >= 2 && ThreadLocalRandom.current().nextFloat() < 0.3f;
            int gap = ThreadLocalRandom.current().nextInt(8);
            int fireAt = period - 5;
            List<EggSpeaker> bank = new ArrayList<>();
            for (int k = 0; k < 8; k++) {
                EggSpeaker w = stage.walls.get(k);
                float lx = localX(w.base);
                boolean onSide = side > 0 ? lx > 0f : lx < 0f;
                if (full ? (k != gap && k != (gap + 1) % 8) : onSide) {
                    bank.add(w);
                }
            }
            for (EggSpeaker w : bank) {
                w.arm(Math.max(3, fireAt - 7), 7, lanePayload());
            }
            // The egg leans to the bank it claims. Sometimes it claims the wrong one; the lamps never lie.
            float lean = full ? 0f : (fake ? -side : side) * 0.3f;
            body.roll.target = -lean;
            body.pitch.target = full ? -0.2f : 0f;
            fx.sound(body.facePoint(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, side > 0 ? 0.6f : 0.75f);
            mJ = -mJ;
            mI++;
        }
        if (local % beatLen == 0) {
            body.squash.kick(-0.05f);
        }
        if (mI >= volleys && local >= (volleys - 1) * period + period + 20) {
            finish(30);
        }
    }

    /** Local +X of the egg (its left) for a stage point. */
    private float localX(Vector3f p) {
        float dx = p.x - body.pos.x;
        float dz = p.z - body.pos.z;
        return dx * (float) Math.cos(body.yaw) - dz * (float) Math.sin(body.yaw);
    }

    /* ================================================================== SUBWOOFER */

    private void subwoofer(int t) {
        int count = phase == 1 ? 2 : phase == 2 ? 3 : 4;
        if (t == 0) {
            mList.clear();
            List<EggSpeaker> subs = new ArrayList<>(stage.subs);
            subs.sort(Comparator.comparingDouble(this::nearestTargetDistance));
            for (int i = 0; i < Math.min(count, subs.size()); i++) {
                mList.add(subs.get(i));
            }
            mI = 0;
            mK = toBeat(6);
        }
        Player p = currentTarget();
        if (p != null) {
            turnToward(fx.stage(p.getLocation()), 0.06f);
        }
        int local = t - mK;
        if (local >= 0 && mI < mList.size() && local == mI * beatLen) {
            // Stomp: tiny leg, big consequence.
            EggSpeaker s = mList.get(mI);
            boolean left = mI % 2 == 0;
            if (left) {
                body.liftL = 1f;
            } else {
                body.liftR = 1f;
            }
            body.pitch.kick(0.06f);
            body.squash.kick(-0.06f);
            fx.sound(body.pos, Sound.ENTITY_CHICKEN_STEP, 2f, 0.5f);
            fx.sound(body.pos, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.2f, 0.5f);
            int charge = toBeat(beatLen * 2 + 4) - 6;
            if (s.arm(charge, 6, subPayload())) {
                props.add(new EggProps.FloorRing(fx, s.base, 3.4f, 3.4f, Material.ORANGE_STAINED_GLASS, EggSpeaker.AMBER, charge + 2));
            }
            mI++;
        }
        body.liftL = Math.max(0f, body.liftL - 0.15f);
        body.liftR = Math.max(0f, body.liftR - 0.15f);
        if (mI >= mList.size() && local > (mList.size() + 3) * beatLen + 12) {
            finish(28);
        }
    }

    private double nearestTargetDistance(EggSpeaker s) {
        double best = 999;
        for (Player p : fx.targets()) {
            best = Math.min(best, EggMath.horizontal(fx.stage(p.getLocation()), s.base));
        }
        return best;
    }

    /* ================================================================== SUB HOP */

    private void subHop(int t) {
        if (t == 0) {
            EggSpeaker best = null;
            float bestD = Float.MAX_VALUE;
            for (EggSpeaker s : stage.subs) {
                float d = EggMath.horizontal(s.base, body.pos);
                if (s.free() && d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
            if (best == null) {
                finish(10);
                return;
            }
            mList.clear();
            mList.add(best);
            mA.set(best.base.x, 0f, best.base.z);
            mFire = -1;
        }
        EggSpeaker sub = mList.isEmpty() ? null : mList.get(0);
        if (sub == null) {
            finish(10);
            return;
        }
        if (mFire < 0) {
            // Waddle onto the pad.
            if (stepToward(mA, 0.17f, true) || t > 90) {
                body.pos.set(mA);
                int charge = toBeat(beatLen + 8) - 6;
                mFire = t + charge + 6;
                sub.arm(charge, 6, s -> {
                    fireSub(s);
                });
                fx.sound(body.pos, Sound.ENTITY_CHICKEN_STEP, 1.6f, 1.4f);
            }
            return;
        }
        if (t < mFire) {
            // Braced on the pad. It knows what is about to happen. It is excited.
            settleLegs();
            float p = window(t, mFire - 20, mFire);
            body.squash.target = 1f - 0.22f * p;
            body.jitter = 0.03f * p;
            body.pitch.target = 0.08f;
            return;
        }
        if (t == mFire) {
            Player p = currentTarget();
            mB.set(p == null ? new Vector3f(0f, 0f, 0f) : fx.stage(p.getLocation()));
            mB.y = 0f;
            clampArena(mB);
            mA.set(body.pos);
            mK = 30;
            body.jitter = 0f;
            body.squash.snap(1.35f);
            body.squash.target = 1.1f;
            props.add(new EggProps.FloorRing(fx, mB, 3.8f, 1.2f, Material.RED_STAINED_GLASS, EggSpeaker.RED, mK + 2));
            fx.sound(body.pos, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.6f, 0.6f);
            return;
        }
        int fly = t - mFire;
        if (fly < mK) {
            float u = fly / (float) mK;
            body.pos.set(lerp(mA.x, mB.x, smooth(u)), 0f, lerp(mA.z, mB.z, smooth(u)));
            body.air = arc(u) * 10f;
            body.kickL = (float) Math.sin(fly * 1.3f) * 0.9f;
            body.kickR = (float) Math.cos(fly * 1.3f) * 0.9f;
            body.pitch.target = u < 0.6f ? -0.25f : 0.45f;
            turnToward(mB, 0.2f);
            if (fly % 6 == 0) {
                fx.sound(body.centerPoint(), Sound.ENTITY_CHICKEN_STEP, 1.2f, 1.8f);
            }
            return;
        }
        if (fly == mK) {
            body.air = 0f;
            body.kickL = 0f;
            body.kickR = 0f;
            body.pos.set(mB);
            body.squash.snap(0.6f);
            body.squash.target = 1f;
            body.pitch.target = 0f;
            body.pitch.kick(-0.2f);
            EggProps.RingWave w = props.add(new EggProps.RingWave(fx, mB, 0f, PI, 1.5f, 16f, 0.8f, 20,
                    Material.WHITE_STAINED_GLASS, AIR));
            w.power = 10;
            waves.add(w);
            for (Player p : fx.targets()) {
                if (EggMath.horizontal(fx.stage(p.getLocation()), mB) < 3.4f) {
                    strike(p, 14, mB, 1.6f, 0.6f, "hop" + clock, 1);
                    w.hit.add(p.getUniqueId());
                }
            }
            fx.sound(mB, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.5f);
            fx.sound(mB, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
            fx.sound(mB, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            fx.particle(Particle.EXPLOSION, new Vector3f(mB).add(0f, 0.4f, 0f), 2, 0.6, 0.0);
            return;
        }
        int dizzy = fly - mK;
        // Dizzy: it wobbles in a little circle. Free hits.
        body.roll.target = (float) Math.sin(dizzy * 0.35f) * 0.22f;
        body.pitch.target = (float) Math.cos(dizzy * 0.35f) * 0.12f;
        if (dizzy % 10 == 0) {
            fx.sound(body.topPoint(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.8f - dizzy * 0.01f);
        }
        if (dizzy > 36) {
            finish(26);
        }
    }

    /* ================================================================== TWEETERS */

    private void tweeters(int t) {
        int count = phase == 1 ? 2 : 4;
        int rounds = phase >= 3 ? 2 : 1;
        if (t == 0) {
            mList.clear();
            List<EggSpeaker> tw = new ArrayList<>(stage.tweeters);
            java.util.Collections.shuffle(tw);
            for (int i = 0; i < count; i++) {
                mList.add(tw.get(i % tw.size()));
            }
            mI = 0;
            mK = toBeat(8);
        }
        int total = mList.size() * rounds;
        int local = t - mK;
        if (!mList.isEmpty()) {
            EggSpeaker cue = mList.get(Math.min(mI, total - 1) % mList.size());
            // Point the speaker up at the tower being cued.
            turnToward(cue.base, 0.1f);
            body.pitch.target = -0.32f;
        }
        settleLegs();
        if (local >= 0 && mI < total && local == mI * beatLen) {
            EggSpeaker s = mList.get(mI % mList.size());
            s.arm(toBeat(beatLen * 2) - 8, 8, tweeterPayload());
            fx.sound(body.facePoint(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.6f + (mI % 4) * 0.1f);
            body.cone.kick(0.12f);
            mI++;
        }
        if (mI >= total && local > (total + 4) * beatLen + 10) {
            finish(26);
        }
    }

    /* ================================================================== FEEDBACK */

    private void feedback(int t) {
        int hop = beatLen * 2;
        if (t == 0) {
            signals.clear();
            // Start a few nodes "upstream" of the target so the signal travels toward them.
            Player p = currentTarget();
            Vector3f pp = p == null ? new Vector3f(0f, 0f, 10f) : fx.stage(p.getLocation());
            int near = 0;
            float best = Float.MAX_VALUE;
            for (EggSpeaker s : stage.speakers) {
                float d = EggMath.horizontal(s.base, pp);
                if (d < best) {
                    best = d;
                    near = s.loop;
                }
            }
            mI = Math.floorMod(near - 3, stage.size());
            mK = toBeat(24);
        }
        EggSpeaker start = stage.node(mI);
        if (t < mK) {
            float p = t / (float) mK;
            turnToward(start.base, 0.14f);
            settleLegs();
            body.pitch.target = 0.1f;
            body.jitter = 0.02f * p;
            body.overfill = 0.3f * p;
            if (t % 2 == 0) {
                // The squeal builds.
                fx.sound(body.facePoint(), Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.5f + 0.5f * p, 1.2f + 0.8f * p);
            }
            if (phase >= 3 && t % 4 == 0) {
                fx.sound(body.facePoint(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 1.6f + 0.4f * p);
            }
            return;
        }
        if (t == mK) {
            body.jitter = 0f;
            body.overfill = 0f;
            body.cone.value = 0.4f;
            fx.sound(body.facePoint(), Sound.BLOCK_NOTE_BLOCK_FLUTE, 1.4f, 2f);
            fx.sound(body.facePoint(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f);
            int hops = phase == 1 ? 8 : 7;
            Signal a = new Signal();
            a.node = -1;
            a.dir = 1;
            a.left = hops;
            if (phase >= 3) {
                a.reverseAt = 4;
                a.arcAt = 2;
            }
            signals.add(a);
            injectSignal(a, start, hop);
            if (phase >= 2) {
                Signal b = new Signal();
                b.node = -1;
                b.dir = 1;
                b.left = hops;
                if (phase >= 3) {
                    b.arcAt = 5;
                }
                signals.add(b);
                injectSignal(b, stage.node(mI + stage.size() / 2), hop);
            }
            return;
        }
        // The egg follows the first live signal with its whole body, LEDs chasing.
        Signal lead = null;
        for (Signal s : signals) {
            if (!s.done) {
                lead = s;
                break;
            }
        }
        if (lead != null && lead.node >= 0) {
            turnToward(stage.node(lead.node + lead.dir).base, 0.1f);
            body.leds(lead.hopsDone % 6);
        }
        settleLegs();
        body.pitch.target = 0f;
        if (lead == null) {
            if (mFire < 0) {
                mFire = t;
            }
            if (t - mFire > 30) {
                finish(34);
            }
        }
    }

    private void injectSignal(Signal s, EggSpeaker first, int hop) {
        first.arm(hop, beatLen, payloadFor(first));
        props.add(new EggProps.Packet(fx, body.facePoint(), first.port, hop, SIGNAL, () -> arrive(s, first, -1)));
        fx.sound(first.cone(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.1f, semitone(MELODY[first.loop]));
    }

    private void arrive(Signal s, EggSpeaker at, int cable) {
        if (fx == null) {
            return;
        }
        if (cable >= 0) {
            stage.cableLit(cable, null);
        }
        if (s.done) {
            return;
        }
        s.node = at.loop;
        s.left--;
        s.hopsDone++;
        if (s.left <= 0 || act != Act.FIGHT || move != Move.FEEDBACK) {
            s.done = true;
            return;
        }
        int hop = beatLen * 2;
        boolean reversing = s.hopsDone == s.reverseAt;
        if (reversing) {
            s.dir = -s.dir;
            fx.sound(at.cone(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.2f, 0.7f);
            fx.sound(at.cone(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.8f, 1.4f);
            fx.particle(Particle.ELECTRIC_SPARK, at.cone(), 14, 0.4, 0.1);
        }
        boolean arcing = s.hopsDone == s.arcAt;
        int step = arcing ? 2 : 1;
        EggSpeaker next = stage.node(s.node + s.dir * step);
        int nextCable = s.dir > 0 ? s.node : Math.floorMod(s.node - 1, stage.size());
        if (arcing) {
            EggSpeaker skipped = stage.node(s.node + s.dir);
            skipped.wakeFlash();
            Vector3f a = new Vector3f(at.port).add(0f, 0.6f, 0f);
            Vector3f b = new Vector3f(next.port).add(0f, 0.6f, 0f);
            for (int i = 0; i <= 8; i++) {
                fx.particle(Particle.ELECTRIC_SPARK, EggMath.lerpVec(a, b, i / 8f), 2, 0.15, 0.02);
            }
            fx.sound(at.cone(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
            nextCable = -1;
        } else {
            stage.cableLit(nextCable, reversing ? SIGNAL_HOT : SIGNAL);
        }
        next.arm(hop, beatLen, payloadFor(next));
        fx.sound(next.cone(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.1f, semitone(MELODY[next.loop]));
        final int travelled = nextCable;
        props.add(new EggProps.Packet(fx, at.port, next.port, hop, reversing ? SIGNAL_HOT : SIGNAL,
                () -> arrive(s, next, travelled)));
    }

    /* ================================================================== payloads */

    private EggSpeaker.Payload payloadFor(EggSpeaker s) {
        return switch (s.kind) {
            case WALL -> lanePayload();
            case SUB -> subPayload();
            case TWEETER -> tweeterPayload();
        };
    }

    private EggSpeaker.Payload lanePayload() {
        return new EggSpeaker.Payload() {
            @Override
            public void primed(EggSpeaker s) {
                Vector3f dir = s.facing();
                Vector3f a = new Vector3f(s.base.x, 0f, s.base.z).fma(1.2f, new Vector3f(dir.x, 0f, dir.z).normalize());
                Vector3f b = new Vector3f(a).fma(18f, new Vector3f(dir.x, 0f, dir.z).normalize());
                props.add(new EggProps.Stripe(fx, a, b, 3.3f, Material.RED_STAINED_GLASS, null, 9));
            }

            @Override
            public void fire(EggSpeaker s) {
                fireLane(s);
            }
        };
    }

    private void fireLane(EggSpeaker s) {
        Vector3f dir = s.facing();
        dir.y = 0f;
        dir.normalize();
        Vector3f start = new Vector3f(s.base.x, 0f, s.base.z).fma(1.0f, dir);
        EggProps.LaneWall lane = props.add(new EggProps.LaneWall(fx, start, dir, 18.5f, 3.4f, 13, AIR));
        lane.power = 10;
        lanes.add(lane);
        fx.sound(s.cone(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.6f, 0.55f);
        fx.sound(s.cone(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.75f);
        fx.particle(Particle.GUST, s.cone(), 1, 0.0, 0.0);
    }

    private EggSpeaker.Payload subPayload() {
        return new EggSpeaker.Payload() {
            @Override
            public void primed(EggSpeaker s) {
                props.add(new EggProps.FloorRing(fx, s.base, 3.4f, 3.4f, Material.RED_STAINED_GLASS, EggSpeaker.RED, 9));
            }

            @Override
            public void fire(EggSpeaker s) {
                fireSub(s);
            }
        };
    }

    private void fireSub(EggSpeaker s) {
        props.add(new EggProps.Column(fx, s.base, 2.6f));
        EggProps.RingWave ring = props.add(new EggProps.RingWave(fx, s.base, 0f, PI, 0.8f, 4.5f, 0.35f, 8,
                Material.WHITE_STAINED_GLASS, null));
        ring.power = 0;
        for (Player p : fx.targets()) {
            Vector3f pp = fx.stage(p.getLocation());
            if (EggMath.horizontal(pp, s.base) < 3.4f && pp.y < 2.4f && gateOnce(p, "sub" + s.loop + ":" + clock, 4)) {
                BossHits.hurt(p, instance.getEntity(), 6);
                Vector3f out = new Vector3f(pp.x - s.base.x, 0f, pp.z - s.base.z);
                if (out.lengthSquared() > 1e-3f) {
                    out.normalize(0.18f);
                }
                p.setVelocity(new Vector(out.x, 1.35, out.z));
                p.setFallDistance(0f);
                onPlayerHit();
            }
        }
        fx.sound(s.cone(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.8f, 0.5f);
        fx.sound(s.cone(), Sound.BLOCK_NOTE_BLOCK_BASS, 2f, 0.5f);
        fx.sound(s.cone(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.5f);
    }

    private EggSpeaker.Payload tweeterPayload() {
        return new EggSpeaker.Payload() {
            private Vector3f spot;
            private int lockedAt;

            private void lock(EggSpeaker s, int ringLife) {
                Player p = randomTarget();
                spot = p == null ? new Vector3f(EggMath.rnd(-8f, 8f), 0f, EggMath.rnd(-8f, 8f)) : fx.stage(p.getLocation());
                spot.y = 0f;
                clampArena(spot);
                lockedAt = clock;
                props.add(new EggProps.FloorRing(fx, spot, 3.1f, 0.9f, Material.RED_STAINED_GLASS, EggSpeaker.RED, ringLife));
                fx.sound(s.cone(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 2f);
            }

            @Override
            public void primed(EggSpeaker s) {
                lock(s, 8 + 14);
            }

            @Override
            public void fire(EggSpeaker s) {
                if (spot == null || clock - lockedAt > 20) {
                    // Held primed for a setpiece: aim now, the ring is the timer.
                    lock(s, 14);
                }
                Vector3f to = new Vector3f(spot);
                fx.sound(s.cone(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f, 2f);
                fx.sound(s.cone(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.6f);
                props.add(new EggProps.Ball(fx, s.cone(), new Vector3f(to).add(0f, 0.3f, 0f), 3.5f, 14, 0.7f,
                        Material.WHITE_CONCRETE, TREBLE, () -> trebleImpact(to)));
            }
        };
    }

    private void trebleImpact(Vector3f at) {
        if (fx == null) {
            return;
        }
        for (Player p : fx.targets()) {
            Vector3f pp = fx.stage(p.getLocation());
            if (EggMath.horizontal(pp, at) < 3.1f && pp.y < 3f) {
                strike(p, 9, at, 1.0f, 0.5f, "treble" + clock, 1);
            }
        }
        EggProps.RingWave ring = props.add(new EggProps.RingWave(fx, at, 0f, PI, 0.6f, 4.2f, 0.4f, 8,
                Material.WHITE_STAINED_GLASS, TREBLE));
        ring.power = 0;
        fx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.6f, 1.6f);
        fx.sound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f, 2f);
        fx.sound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.5f);
        fx.particle(Particle.END_ROD, new Vector3f(at).add(0f, 0.4f, 0f), 12, 0.4, 0.12);
    }

    /* ================================================================== OVERCLOCK */

    /**
     * The setpiece. Freeze, vibrate, overfill, the network wakes low to high, hard silence, then
     * eight beats of BASS OVERLOAD: rings on even beats (jump), one wall set on odd beats (stand
     * in the dark lanes), then the final drop. Then it overheats and falls on its back.
     *
     * @return true when the authored timeline has run out
     */
    private boolean overclock(int t, boolean encore) {
        final int beat = 10;
        if (t == 0) {
            silence = OVERCLOCK_TICKS;
            mA.set(0f, 0f, 0f);
            fx.sound(body.facePoint(), Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 0.5f);
            for (EggSpeaker s : stage.speakers) {
                s.cancel();
            }
        }
        if (t < 22) {
            // Hustle to center: whatever speed it takes.
            float d = EggMath.horizontal(body.pos, mA);
            if (!stepToward(mA, Math.max(0.3f, d / Math.max(1, 21 - t)), true)) {
                return false;
            }
            settleLegs();
            return false;
        }
        if (t < 62) {
            float p = window(t, 22, 62);
            body.jitter = 0.01f + 0.07f * p;
            body.overfill = p;
            body.coneHot(true);
            body.leds(Math.min(5, (t - 22) / 8 + 1));
            body.squash.target = 1f - 0.1f * p;
            if (t % 2 == 0) {
                fx.sound(body.facePoint(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, 0.5f + 0.7f * p);
            }
            if (t == 30) {
                fx.sound(body.facePoint(), Sound.BLOCK_BEACON_AMBIENT, 2f, 0.6f);
            }
            return false;
        }
        if (t < 102) {
            // The network wakes in a rising pattern: floor, walls, towers. Low to high.
            int k = t - 62;
            if (k % 2 == 0) {
                int idx = k / 2;
                List<EggSpeaker> order = new ArrayList<>(stage.subs);
                order.addAll(stage.walls);
                order.addAll(stage.tweeters);
                if (idx < order.size()) {
                    EggSpeaker s = order.get(idx);
                    if (s.kind == EggSpeaker.Kind.WALL) {
                        s.wakeFlash();
                    } else {
                        s.armHold(10, s.kind == EggSpeaker.Kind.SUB ? subPayload() : tweeterPayload());
                    }
                    fx.clean(s.cone(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, semitone(Math.min(24, idx * 3 / 2)));
                }
            }
            body.jitter = 0.08f;
            if (t % 3 == 0) {
                fx.sound(body.facePoint(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1.2f + 0.02f * (t - 62));
            }
            return false;
        }
        if (t < 112) {
            // Hard silence. Everything holds.
            body.jitter = 0f;
            body.squash.target = 1.22f;
            body.leds(5);
            return false;
        }
        int o = t - 112;
        if (o < 8 * beat) {
            int k = o / beat;
            int in = o % beat;
            if (in == beat - 5 || (o == 0)) {
                // Count-in tick: this is the jump cue.
                fx.sound(body.topPoint(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.6f, 1.2f);
            }
            if (k % 2 == 1 && in == 0) {
                // Odd beat: one wall set was armed a beat ago and fires now.
                fx.sound(body.facePoint(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.4f, 0.6f);
            }
            if (in == 0 && k % 2 == 0 && k < 7) {
                // Even beat: the egg itself pulses. Jump the ring.
                bodyPulse(0.85f, 22f, 22, 11);
                if (k < 6) {
                    // Arm the next wall set to fire on the following beat; the dark walls are the safe lanes.
                    int set = (k / 2) % 2;
                    for (int w = set; w < 8; w += 2) {
                        stage.walls.get(w).arm(beat - 6, 6, lanePayload());
                    }
                }
            }
            if (k == 7 && in == 0) {
                // FINAL DROP: subs launch, tweeters fire, the egg detonates.
                for (EggSpeaker s : stage.subs) {
                    s.release();
                }
                for (EggSpeaker s : stage.tweeters) {
                    s.release();
                }
                bodyPulse(1.0f, 26f, 24, 16);
                body.squash.snap(1.35f);
                fx.score(Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f);
            }
            body.jitter = 0.04f;
            return false;
        }
        int h = o - 8 * beat;
        // Overheat: steam, fizz, flicker, then it tips over backwards.
        body.jitter = h < 14 ? 0.05f : 0.0f;
        body.overfill = Math.max(0f, 1f - h / 10f);
        if (h % 3 == 0) {
            fx.particle(Particle.CLOUD, body.topPoint(), 4, 0.4, 0.06);
            fx.particle(Particle.SMOKE, body.facePoint(), 3, 0.3, 0.03);
        }
        if (h % 5 == 0) {
            fx.sound(body.facePoint(), Sound.BLOCK_FIRE_EXTINGUISH, 0.9f, 0.8f + h * 0.02f);
            body.leds(h % 10 == 0 ? 5 : 0);
        }
        if (h == 18) {
            body.coneHot(false);
            body.leds(0);
            body.pitch.target = -1.42f;
            body.pitch.kick(-0.12f);
        }
        if (h == 26) {
            fx.sound(body.pos, Sound.BLOCK_ANVIL_LAND, 0.9f, 0.6f);
            fx.sound(body.pos, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
            fx.particle(Particle.CLOUD, new Vector3f(body.pos).add(0f, 0.4f, -2f), 14, 1.2, 0.05);
        }
        return h >= OVERCLOCK_TICKS - 112 - 8 * beat;
    }

    /** The egg's own 360-degree pressure ring (overload beats). */
    private void bodyPulse(float height, float reach, int life, double power) {
        EggProps.RingWave w = props.add(new EggProps.RingWave(fx, body.pos, 0f, PI, 1.6f, reach, height, life,
                Material.WHITE_STAINED_GLASS, AIR));
        w.power = power;
        waves.add(w);
        body.cone.value = 0.5f;
        body.squash.kick(0.25f);
        drop(body.facePoint(), 0.8f);
        for (EggSpeaker s : stage.speakers) {
            s.kick(0.2f);
        }
    }

    /* ================================================================== TIPPED */

    private void tipped(int t) {
        int length = 76;
        body.pitch.target = -1.42f;
        body.jitter = 0f;
        // Tiny legs kicking at the sky.
        body.kickL = (float) Math.sin(t * 0.9f) * 0.8f;
        body.kickR = (float) Math.sin(t * 0.9f + 1.7f) * 0.8f;
        if (t % 3 == 0) {
            fx.sound(body.pos, Sound.ENTITY_CHICKEN_STEP, 0.7f, 1.7f);
        }
        if (t % 6 == 0) {
            fx.particle(Particle.SMOKE, body.facePoint(), 3, 0.25, 0.04);
        }
        if (t % 11 == 0) {
            body.leds(ThreadLocalRandom.current().nextInt(4));
        }
        if (t == 50 || t == 58) {
            body.roll.kick(t == 50 ? 0.3f : -0.35f);
            fx.sound(body.pos, Sound.BLOCK_DECORATED_POT_HIT, 1.2f, 0.7f);
        }
        if (t == length) {
            body.kickL = 0f;
            body.kickR = 0f;
            body.pitch.target = 0f;
            body.pitch.kick(0.25f);
            body.squash.snap(0.7f);
            body.squash.target = 1f;
            fx.sound(body.pos, Sound.ENTITY_SLIME_JUMP, 1.4f, 0.7f);
            fx.sound(body.pos, Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, 0.7f);
        }
        if (t >= length + 16) {
            finish(20);
        }
    }

    /* ================================================================== transitions */

    private void beginTransition() {
        endMove();
        move = Move.IDLE;
        act = Act.TRANSITION;
        actTick = 0;
        for (Signal s : signals) {
            s.done = true;
        }
        signals.clear();
        BossPhase pending = instance.getPendingPhase();
        transitionKind = pending != null && pending.getHealthPercent() < 45 ? 2 : 1;
        int configured = pending != null && pending.getTransition() != null ? pending.getTransition().getDurationTicks() : 0;
        int authored = transitionKind == 1 ? OVERCLOCK_TICKS : DISTORT_TICKS;
        transitionLength = configured > 0 ? configured : authored;
        body.kickL = 0f;
        body.kickR = 0f;
        body.air = 0f;
        body.liftL = 0f;
        body.liftR = 0f;
        body.bob = 0f;
        body.jitter = 0f;
        body.overfill = 0f;
        body.coneHot(false);
        body.pitch.target = 0f;
        body.roll.target = 0f;
        body.squash.target = 1f;
        body.cone.target = 0f;
        moveTick = 0;
        mFire = -1;
    }

    private void tickTransition() {
        if (!instance.isTransitioning()) {
            finishTransition();
            return;
        }
        int authored = transitionKind == 1 ? OVERCLOCK_TICKS : DISTORT_TICKS;
        int tau = Math.round(actTick * authored / (float) Math.max(1, transitionLength));
        int prev = actTick == 0 ? -1 : Math.round((actTick - 1) * authored / (float) Math.max(1, transitionLength));
        actTick++;
        for (int k = prev + 1; k <= tau; k++) {
            if (transitionKind == 1) {
                overclock(k, false);
            } else {
                distortion(k);
            }
        }
    }

    private void finishTransition() {
        act = Act.FIGHT;
        actTick = 0;
        phase = transitionKind == 1 ? 2 : 3;
        beatLen = beatFor(phase);
        beatClock = 0;
        silence = 0;
        lastHealth = instance.getCombatHealth();
        if (transitionKind == 1) {
            // It overheated: on its back, legs kicking. Punish window.
            begin(Move.TIPPED);
            moveTick = 0;
        } else {
            applyDistortedLook(true);
            finish(24);
        }
    }

    /**
     * DISTORTION. Feedback scream, hard silence, three cracks, the lid pops off, a detuned reboot,
     * one WUB. From here every sound warbles and the wobble never quite settles.
     */
    private void distortion(int t) {
        Vector3f face = body.facePoint();
        if (t == 0) {
            silence = DISTORT_TICKS;
            for (EggSpeaker s : stage.speakers) {
                s.cancel();
            }
        }
        if (t < 18) {
            body.jitter = 0.1f;
            body.overfill = (t % 4 < 2) ? 0.6f : 0f;
            if (t % 2 == 0) {
                fx.sound(face, Sound.BLOCK_NOTE_BLOCK_BIT, 1.4f, 2f);
                fx.sound(face, Sound.BLOCK_NOTE_BLOCK_FLUTE, 1.2f, 1.9f + EggMath.rnd(-0.1f, 0.1f));
            }
            if (t == 2) {
                fx.sound(face, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 2f);
            }
            if (t % 3 == 0) {
                stage.node(ThreadLocalRandom.current().nextInt(stage.size())).wakeFlash();
            }
            return;
        }
        if (t == 18) {
            body.jitter = 0f;
            body.overfill = 0f;
            body.leds(0);
        }
        if (t == 36 || t == 52 || t == 68) {
            int n = (t - 36) / 16;
            body.crack();
            body.crackOpen = 0.35f * (n + 1);
            if (n >= 1) {
                body.dented = true;
            }
            body.pitch.kick(n % 2 == 0 ? 0.12f : -0.1f);
            body.roll.kick(n % 2 == 0 ? -0.1f : 0.12f);
            body.squash.kick(-0.08f);
            fx.sound(face, n < 2 ? Sound.ENTITY_TURTLE_EGG_CRACK : Sound.ENTITY_TURTLE_EGG_BREAK, 2f, 0.5f + n * 0.05f);
            fx.sound(face, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.5f);
            if (n == 1) {
                fx.sound(face, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
            }
            fx.dust(body.centerPoint(), Color.WHITE, 1.4f, 14, 0.9);
            fx.dust(body.centerPoint(), EggBody.ORANGE, 1.2f, 8, 0.6);
        }
        if (t == 86) {
            popLid();
        }
        if (t > 86 && t < 100 && t % 2 == 0) {
            fx.dust(body.topPoint(), EggBody.ORANGE, 1.6f, 4, 0.3);
            fx.particle(Particle.LAVA, body.topPoint(), 1, 0.2, 0.0);
        }
        if (t >= 102 && t < 142) {
            // Detuned reboot: a startup chime that is not quite in tune any more.
            fx.warble = 0.08f;
            int k = t - 102;
            if (k % 5 == 0) {
                fx.sound(face, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, semitone(MELODY[(k / 5) % 8] + 1));
            }
            if (k % 2 == 0) {
                int idx = (k / 2 * 7) % stage.size();
                EggSpeaker s = stage.node(idx);
                s.distorted = true;
                s.wakeFlash();
            }
            body.leds((k / 4) % 6);
        }
        if (t >= 146 && t < 158) {
            float u = (t - 146) / 12f;
            body.air = arc(u) * 1.6f;
            body.squash.target = 1.1f;
        }
        if (t == 158) {
            body.air = 0f;
            body.squash.snap(0.68f);
            body.squash.target = 1f;
            bodyPulse(0.6f, 18f, 20, 8);
        }
    }

    private void popLid() {
        Vector3f top = body.topPoint();
        Vector3f back = EggMath.flat(body.yaw + PI + 0.6f, 4.5f).add(body.pos);
        back.y = 0.55f;
        clampArena(back);
        lid = props.add(new EggProps.Lid(fx, body.lidMatrices(), body.lidMaterials(), new Vector3f(top).add(0f, -0.4f, 0f), back));
        body.lidOff = true;
        fx.sound(top, Sound.ENTITY_CHICKEN_EGG, 2f, 0.5f);
        fx.sound(top, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.4f, 0.6f);
        fx.sound(top, Sound.BLOCK_PISTON_EXTEND, 1f, 0.5f);
        fx.particle(Particle.CLOUD, top, 12, 0.4, 0.08);
    }

    /** Phase 3 look and feel: cracked, lidless, purple idle lamps, wobbly, warbled. */
    private void applyDistortedLook(boolean live) {
        body.crack();
        body.crackOpen = 1f;
        body.dented = true;
        if (!live) {
            body.lidOff = true;
        }
        body.pitch.d = 0.12f;
        body.roll.d = 0.12f;
        body.squash.d = 0.16f;
        fx.warble = 0.08f;
        for (EggSpeaker s : stage.speakers) {
            s.distorted = true;
        }
    }

    /* ================================================================== death */

    /**
     * 1 final charge (everything wakes) · 2 peak tension · 3 hard silence · 4 the network dies one
     * speaker at a time · 5 the egg loses power and tips over like a dead appliance · 6 one tiny
     * egg plops out. Pop.
     */
    private boolean tickDeath() {
        int t = actTick++;
        Vector3f face = body.facePoint();
        int n = stage.size();
        if (t == 0) {
            for (Signal s : signals) {
                s.done = true;
            }
            signals.clear();
            waves.clear();
            lanes.clear();
            body.kickL = 0f;
            body.kickR = 0f;
            body.air = 0f;
            body.overfill = 0f;
            body.pitch.target = 0f;
            body.roll.target = 0f;
            for (EggSpeaker s : stage.speakers) {
                s.cancel();
            }
            fx.sound(face, Sound.BLOCK_BEACON_POWER_SELECT, 1.4f, 0.5f);
        }
        if (t < 44) {
            // 1. Final charge: it tries one last time. Everything wakes, rising.
            float p = t / 44f;
            body.pos.lerp(new Vector3f(0f, 0f, 0f), 0.05f);
            body.jitter = 0.02f + 0.06f * p;
            body.overfill = p;
            body.coneHot(true);
            body.leds(Math.round(5 * p));
            if (t % 2 == 0 && t / 2 < n) {
                EggSpeaker s = stage.node(t / 2);
                s.armHold(10, s2 -> {
                });
                fx.clean(s.cone(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, semitone(MELODY[t / 2]));
            }
            if (t % 3 == 0) {
                fx.clean(face, Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, 0.5f + 1.3f * p);
            }
            return false;
        }
        if (t < 62) {
            // 2. Peak tension: on its tiptoes, every lamp red, the pitch at the top.
            body.squash.target = 1.28f;
            body.jitter = 0.09f;
            body.ledsAll(Material.RED_CONCRETE, EggSpeaker.RED);
            if (t % 2 == 0) {
                fx.clean(face, Sound.BLOCK_NOTE_BLOCK_FLUTE, 1.2f, 2f);
            }
            return false;
        }
        if (t == 62) {
            // 3. Hard silence. No hum, no beat, nothing moves.
            body.jitter = 0f;
            body.frozen = true;
        }
        if (t < 94) {
            return false;
        }
        if (t == 94) {
            body.frozen = false;
            body.overfill = 0f;
            body.coneHot(false);
        }
        int dieStart = 94;
        int dieGap = 5;
        if (t < dieStart + n * dieGap) {
            // 4. The network dies, one installation at a time, in loop order.
            int k = t - dieStart;
            if (k % dieGap == 0) {
                EggSpeaker s = stage.node(k / dieGap);
                s.die();
                stage.cableLit(k / dieGap, null);
                body.leds(5 - (k / dieGap) * 5 / n);
            }
            body.squash.target = lerp(1.2f, 1f, window(t, dieStart, dieStart + n * dieGap));
            return false;
        }
        int e = t - (dieStart + n * dieGap);
        if (e == 0) {
            // 5. Power loss.
            body.powerDown();
            body.cone.target = -0.12f;
            fx.clean(face, Sound.BLOCK_BEACON_DEACTIVATE, 1.6f, 0.5f);
            fx.clean(face, Sound.BLOCK_NOTE_BLOCK_BASS, 1.4f, 0.5f);
        }
        if (e > 0 && e < 30) {
            // Knees go. It sags, sways once, and leans past the point of no return.
            body.squash.target = 0.88f;
            body.roll.target = e < 14 ? 0.12f : -0.05f;
            body.pitch.target = e < 14 ? -0.08f : 0.35f;
            body.bob = -0.15f * window(e, 0, 20);
        }
        if (e == 30) {
            body.pitch.target = 1.5f;
            body.pitch.k = 0.1f;
            body.pitch.d = 0.18f;
            fx.clean(body.pos, Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.8f, 0.5f);
        }
        if (e == 44) {
            // Face-first into the floor like a dead appliance. Thud.
            fx.clean(face, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f);
            fx.clean(face, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f);
            fx.clean(face, Sound.BLOCK_DECORATED_POT_BREAK, 1.2f, 0.6f);
            fx.particle(Particle.CLOUD, new Vector3f(face.x, 0.3f, face.z), 20, 1.4, 0.04);
            body.squash.kick(-0.1f);
        }
        if (e > 50 && e < 80) {
            // One leg twitches. Then doesn't.
            body.kickR = e % 8 < 2 && e < 74 ? 0.5f : 0f;
        }
        if (e == 86) {
            // 6. Pop.
            fx.clean(face, Sound.ENTITY_CHICKEN_EGG, 1.6f, 1f);
            spawnChick();
        }
        if (e > 86 && e < 104) {
            rollChick(e - 86);
        }
        if (e == 112) {
            fx.clean(face, Sound.BLOCK_NOTE_BLOCK_BIT, 0.25f, 2f);
        }
        return e >= 124;
    }

    private void spawnChick() {
        Vector3f back = EggMath.flat(body.yaw + PI, 2.2f).add(body.pos);
        mB.set(back.x, 0.2f, back.z);
        mA.set(EggMath.flat(body.yaw + PI + 0.5f, 4.2f).add(body.pos));
        mA.y = 0.2f;
        clampArena(mA);
        chick = fx.item(new ItemStack(Material.EGG), 15);
        EggFx.push(chick, new Matrix4f().translation(mB).scale(0.9f), 0);
        fx.particle(Particle.CLOUD, mB, 4, 0.15, 0.02);
    }

    private void rollChick(int k) {
        if (chick == null) {
            return;
        }
        float u = k / 18f;
        Vector3f p = EggMath.lerpVec(mB, mA, EggMath.outCubic(u));
        p.y = 0.2f + arc(Math.min(1f, u * 2f)) * 0.8f;
        EggFx.push(chick, new Matrix4f().translation(p).rotateX(u * PI * 3f).scale(0.9f), 1);
        if (k == 6) {
            fx.clean(p, Sound.BLOCK_DECORATED_POT_HIT, 0.6f, 1.8f);
        }
    }

    /* ================================================================== hazards */

    private void tickHazards() {
        for (Iterator<EggProps.RingWave> it = waves.iterator(); it.hasNext(); ) {
            if (it.next().done()) {
                it.remove();
            }
        }
        for (Iterator<EggProps.LaneWall> it = lanes.iterator(); it.hasNext(); ) {
            if (it.next().done()) {
                it.remove();
            }
        }
        if (waves.isEmpty() && lanes.isEmpty()) {
            return;
        }
        for (Player p : fx.targets()) {
            Vector3f feet = fx.stage(p.getLocation());
            UUID id = p.getUniqueId();
            for (EggProps.RingWave w : waves) {
                if (w.power > 0 && w.catches(feet, 0.95f) && w.hit.add(id)) {
                    strike(p, w.power, w.center, w.knock, w.lift, "wave" + System.identityHashCode(w), 1);
                }
            }
            for (EggProps.LaneWall l : lanes) {
                if (l.power > 0 && l.catches(feet) && l.hit.add(id)) {
                    Vector3f from = new Vector3f(feet).fma(-2f, l.dir);
                    strike(p, l.power, from, 1.6f, 0.4f, "lane" + System.identityHashCode(l), 1);
                }
            }
        }
    }

    private boolean gateOnce(Player p, String key, int ticks) {
        String k = p.getUniqueId() + "|" + key;
        Integer last = gate.get(k);
        if (last != null && clock - last < ticks) {
            return false;
        }
        gate.put(k, clock);
        if (gate.size() > 256) {
            gate.entrySet().removeIf(en -> clock - en.getValue() > 200);
        }
        return true;
    }

    private boolean strike(Player p, double power, Vector3f from, float knock, float lift, String key, int gateTicks) {
        if (!gateOnce(p, key, gateTicks)) {
            return false;
        }
        if (power > 0) {
            BossHits.hurt(p, instance.getEntity(), power);
        }
        Vector3f pp = fx.stage(p.getLocation());
        Vector3f dir = new Vector3f(pp.x - from.x, 0f, pp.z - from.z);
        if (dir.lengthSquared() < 1e-3f) {
            dir.set((float) Math.sin(body.yaw), 0f, (float) Math.cos(body.yaw));
        }
        dir.normalize(knock);
        p.setVelocity(new Vector(dir.x, lift, dir.z));
        Location l = p.getLocation();
        p.getWorld().playSound(l, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        p.getWorld().playSound(l, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        onPlayerHit();
        return true;
    }

    /** Somebody got hit: a little smug lean, no words. */
    private void onPlayerHit() {
        if (move == Move.IDLE || move == Move.CHANNELS || move == Move.FEEDBACK) {
            smugUntil = clock + 18;
            body.pitch.kick(-0.05f);
        }
    }

    /** Shell hit feedback: a ceramic tock, a flinch away from the attacker, a burst of static. */
    private void hitReactions() {
        double hp = instance.getCombatHealth();
        if (hp < lastHealth - 0.01 && clock - lastHitReact >= 4) {
            lastHitReact = clock;
            Player attacker = fx.nearestTarget(body.pos);
            if (attacker != null) {
                Vector3f from = fx.stage(attacker.getLocation());
                float lx = localX(from);
                body.roll.kick(lx > 0 ? -0.04f : 0.04f);
                body.pitch.kick(-0.03f);
            }
            body.squash.kick(-0.05f);
            Vector3f c = body.centerPoint();
            fx.sound(c, Sound.BLOCK_DECORATED_POT_HIT, 1.2f, 1.2f + EggMath.rnd() * 0.4f);
            if (phase >= 3 || ThreadLocalRandom.current().nextFloat() < 0.3f) {
                fx.sound(body.facePoint(), Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 1.8f);
                fx.particle(Particle.ELECTRIC_SPARK, body.facePoint(), 3, 0.4, 0.05);
            }
        }
        lastHealth = hp;
    }

    /** Nobody leaves mid-set: a bounce from the PA throws fallers back onto the stage. */
    private void rescueFallers() {
        if (clock % 5 != 0) {
            return;
        }
        Location c = fx.anchor();
        for (Player p : c.getWorld().getPlayers()) {
            if (!instance.isCombatTarget(p) || p.isDead()) {
                continue;
            }
            Location l = p.getLocation();
            double dx = l.getX() - c.getX();
            double dz = l.getZ() - c.getZ();
            double dy = l.getY() - c.getY();
            if (dx * dx + dz * dz > 46 * 46 || dy > -6 || dy < -90) {
                continue;
            }
            double a = Math.atan2(dx, dz);
            Location to = c.clone().add(Math.sin(a) * 13, 0.2, Math.cos(a) * 13);
            to.setDirection(c.toVector().subtract(to.toVector()).setY(0));
            p.teleport(to);
            p.setFallDistance(0f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false, false));
            BossHits.hurt(p, instance.getEntity(), 5);
            p.playSound(to, Sound.ENTITY_SLIME_JUMP, 1.4f, 0.6f);
            p.playSound(to, Sound.BLOCK_NOTE_BLOCK_BASS, 1.4f, 0.5f);
            if (taught.add("rescue-" + p.getUniqueId())) {
                p.sendMessage(de.aetherion.bossengine.util.TextUtil.component(
                        "&7&oThe PA bounces you back on stage. The set is not over."));
            }
        }
    }

    /* ================================================================== locomotion */

    /**
     * Waddle toward {@code goal}. Feet alternate, the body rolls side to side, every footfall is a
     * chicken step. @return true when arrived
     */
    private boolean stepToward(Vector3f goal, float speed, boolean face) {
        Vector3f to = new Vector3f(goal.x - body.pos.x, 0f, goal.z - body.pos.z);
        float d = to.length();
        if (d < 0.25f) {
            settleLegs();
            return true;
        }
        to.normalize(Math.min(speed, d));
        Vector3f next = new Vector3f(body.pos).add(to);
        clampArena(next);
        body.pos.set(next);
        if (face) {
            body.yaw = EggMath.approachAngle(body.yaw, EggMath.yawToward(to.x, to.z), 0.16f);
        }
        float before = walkPhase;
        walkPhase += speed * 2.4f;
        float s = (float) Math.sin(walkPhase);
        body.liftL = Math.max(0f, s);
        body.liftR = Math.max(0f, -s);
        body.roll.target = s * 0.13f;
        body.bob = Math.abs(s) * 0.07f;
        if (Math.floor(before / PI) != Math.floor(walkPhase / PI)) {
            stepSide = !stepSide;
            fx.sound(body.pos, Sound.ENTITY_CHICKEN_STEP, 1.3f, 0.6f + EggMath.rnd() * 0.15f);
            body.squash.kick(-0.025f);
        }
        return false;
    }

    /** Fast panicked steps (recoil). */
    private void scramble(int k) {
        float s = (float) Math.sin(k * 1.6f);
        body.liftL = Math.max(0f, s);
        body.liftR = Math.max(0f, -s);
        if (k % 2 == 0) {
            fx.sound(body.pos, Sound.ENTITY_CHICKEN_STEP, 1f, 1.2f);
        }
    }

    private void settleLegs() {
        body.liftL *= 0.6f;
        body.liftR *= 0.6f;
        body.bob *= 0.7f;
    }

    private void turnToward(Vector3f goal, float step) {
        float want = EggMath.yawToward(goal.x - body.pos.x, goal.z - body.pos.z);
        body.yaw = EggMath.approachAngle(body.yaw, want, step);
    }

    private static void clampArena(Vector3f v) {
        float d = (float) Math.sqrt(v.x * v.x + v.z * v.z);
        if (d > EGG_R) {
            v.x *= EGG_R / d;
            v.z *= EGG_R / d;
        }
    }

    /* ================================================================== targeting */

    private Player currentTarget() {
        if (target != null) {
            Player p = Bukkit.getPlayer(target);
            if (p != null && fx.targets().contains(p)) {
                return p;
            }
        }
        Player p = fx.nearestTarget(body == null ? new Vector3f() : body.pos);
        target = p == null ? null : p.getUniqueId();
        return p;
    }

    private Player otherTarget() {
        List<Player> all = fx.targets();
        for (Player p : all) {
            if (!p.getUniqueId().equals(target)) {
                return p;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }

    private Player randomTarget() {
        List<Player> all = fx.targets();
        return all.isEmpty() ? null : all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }
}
