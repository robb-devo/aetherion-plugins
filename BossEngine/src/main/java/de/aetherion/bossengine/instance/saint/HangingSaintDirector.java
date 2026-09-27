package de.aetherion.bossengine.instance.saint;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.fx.FakeDestruction;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.BossPhase;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static de.aetherion.bossengine.instance.saint.SaintMath.HALF_PI;
import static de.aetherion.bossengine.instance.saint.SaintMath.PI;
import static de.aetherion.bossengine.instance.saint.SaintMath.TAU;
import static de.aetherion.bossengine.instance.saint.SaintMath.arc;
import static de.aetherion.bossengine.instance.saint.SaintMath.inCubic;
import static de.aetherion.bossengine.instance.saint.SaintMath.lerp;
import static de.aetherion.bossengine.instance.saint.SaintMath.outBack;
import static de.aetherion.bossengine.instance.saint.SaintMath.outCubic;
import static de.aetherion.bossengine.instance.saint.SaintMath.smooth;
import static de.aetherion.bossengine.instance.saint.SaintMath.window;

/**
 * SERAPHINE, THE HANGING SAINT.
 *
 * <p>A porcelain saint-doll whose devotion was so complete that heaven took her strings.
 * She dances on five golden threads held by the Hand Above, a colossal ivory hand hidden in
 * the clouds over a theatre that was torn loose and left floating in the sky. A skull audience
 * watches from the balconies.
 *
 * <p><b>Act I, Overture (100-66%).</b> She is a marionette. She never walks: she is dragged,
 * hoisted and dropped, toes scraping the boards. Only the Hand's fingertips show beneath the
 * clouds, and each finger flexes when the limb it owns is pulled. Tell: threads snap taut the
 * instant before she moves. Moves: Plummet, Pendulum Reap, Needlework (jump the gold threads,
 * duck the crimson ones), Pirouette.
 *
 * <p><b>Transition, The Hand Descends.</b> The clouds part and the Hand comes down into view,
 * flexes each finger in turn (her limbs jerk with it, bells toll), spins her and slams her.
 *
 * <p><b>Act II, The Hand (66-33%).</b> The puppeteer fights too. When the Hand attacks,
 * the puppet goes slack: that is your opening. Adds Palm Slam and Grasp (a thread shot at you,
 * then you are reeled in, hoisted and smashed down).
 *
 * <p><b>Transition, Severance.</b> Hoisted high, she turns her head backwards to look up at the
 * Hand, then cuts her strings one by one with her own needles. Each finger recoils as if stung.
 * She falls, shatters across the stage, lies in pieces in total silence, then crawls back
 * together wrong. Eyes open. The audience loses its heads.
 *
 * <p><b>Act III, Unstrung (33-0%).</b> She moves by herself now, in stop-motion. Moves: Lunge
 * (staccato hops leaving glass afterimages), Trapdoor (she drops through the stage and bursts
 * up under you), Severed Waltz (cuts land on the music box's downbeats), plus the Hand's Fist and
 * Restring as it tries to reclaim her. At 15%, <b>Encore</b>: she hooks the Hand with her own
 * thread and drags it down onto the stage. The fallen Hand becomes real terrain (solid
 * barrier volume under the displays) and your only cover from the Thousand Stitches.
 *
 * <p><b>Death.</b> Time stops. She curtseys, the one gesture no one strung her for. The Hand
 * frees itself and reaches for her; she shatters a moment before it closes. The pieces rise
 * like ash, the Hand withdraws into the closing clouds, dawn breaks, the audience gets its heads
 * back and applauds. One golden thread falls onto the empty stage.
 */
public final class HangingSaintDirector {

    public static final String ID = "hanging_saint";

    private enum Act { NONE, RAISING, INTRO, FIGHT, TRANSITION, DYING, DONE }

    private enum Move {
        IDLE, PLUMMET, PENDULUM, NEEDLEWORK, PIROUETTE, PALM, GRASP,
        LUNGE, TRAPDOOR, WALTZ, FIST, RESTRING, ENCORE, STITCHES
    }

    private static final int INTRO_TICKS = 196;
    private static final int T1_AUTHORED = 140;
    private static final int T2_AUTHORED = 220;
    private static final int DEATH_TICKS = 290;
    private static final int STRIKE_DELAY_TICKS = 20 * 45;
    private static final float[] HAND_Y = {43.5f, 29f, 26f};
    private static final float ARENA = 13.5f;
    /** Thread i runs from finger THREAD_FINGER[i] to a body anchor. */
    private static final int[] THREAD_FINGER = {HandBody.INDEX, HandBody.MIDDLE, HandBody.RING, HandBody.PINKY, HandBody.THUMB};
    private static final int T_RWRIST = 0;
    private static final int T_HEAD = 1;
    private static final int T_LWRIST = 2;
    private static final int T_LKNEE = 3;
    private static final int T_RKNEE = 4;

    private static final Color GOLD = SaintBody.GOLD;
    private static final Color CRIMSON = SaintBody.CRIMSON;
    private static final Color WHITE = SaintBody.WHITE;

    private final BossInstance instance;

    private SaintFx fx;
    private SaintStage stage;
    private StageDressing dressing;
    private SaintBody body;
    private HandBody hand;
    private final ThreadLine[] threads = new ThreadLine[5];
    private final boolean[] cut = new boolean[5];
    private SaintProps props;
    private MusicBox music;

    private Act act = Act.NONE;
    private int actTick;
    private Move move = Move.IDLE;
    private Move lastMove = Move.IDLE;
    private int moveTick;
    private int cooldown = 40;
    private int phase = 1;
    private int clock;
    private int hitstop;
    /** Cinematic time-stop: body holds its last pushed frame (death, shatter). */
    private boolean freezeBody;
    /** Set once she reassembles herself during Severance: no strings, so no dead limbs either. */
    private boolean unstrungBody;
    private boolean hiddenBody;
    private boolean handBusy;
    private boolean handPinned;
    private boolean encoreDone;
    private boolean deathFinished;
    private int transitionLength;
    private int transitionKind;

    private final Vector3f pos = new Vector3f();
    private float yaw;
    private final Vector3f handPos = new Vector3f();
    private float handYaw;
    private final Vector3f handGoal = new Vector3f();
    /** Floor point the key follow-spot is aimed at (eased toward her). */
    private final Vector3f spotAim = new Vector3f();
    /** The key spot was switched on (once she is fully assembled). */
    private boolean spotLit;
    /** The key spot follows her (from the first moment of the fight). */
    private boolean spotTracking;
    private float handFollow = 0.05f;
    private final float[] restLift = new float[5];

    private UUID target;
    private double lastHealth;
    private int lastHitReact;
    private final Set<String> taught = new HashSet<>();
    private final Map<String, Integer> gate = new HashMap<>();

    /* move scratch */
    private final Vector3f mA = new Vector3f();
    private final Vector3f mB = new Vector3f();
    private final Vector3f mC = new Vector3f();
    private float mF;
    private float mG;
    private int mI;
    private int mJ;
    private final List<SaintProps.GiantNeedle> needles = new ArrayList<>();
    private final List<SaintProps.Rope> ropes = new ArrayList<>();
    private final List<SaintProps.Bolt> bolts = new ArrayList<>();
    private ThreadLine graspLine;
    private UUID grasped;
    private int graspAt;
    private final ThreadLine[] encoreLines = new ThreadLine[2];

    public HangingSaintDirector(BossInstance instance) {
        this.instance = instance;
    }

    /* ================================================================== engine hooks */

    public boolean isMine() {
        return instance.getTemplate() != null && ID.equalsIgnoreCase(instance.getTemplate().getId());
    }

    /** The director drives the body: vanilla AI, unstick and visibility polish must stay out. */
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
        return isMine() && (act == Act.RAISING || act == Act.INTRO || act == Act.DYING || hiddenBody);
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
            // Same fight, new body (chunk reload / restore): keep the show running.
            placeHitbox();
            return;
        }
        stage = SaintStage.claim(instance.getPlugin(), instance.getSpawnLocation());
        fx = new SaintFx(instance, stage.center());
        props = new SaintProps();
        music = new MusicBox(fx);
        pos.set(0f, 0.6f, 0f);
        handPos.set(0f, HAND_Y[0] + 4f, 0f);
        handGoal.set(handPos);
        lastHealth = instance.getCombatHealth();
        phase = phaseFromTemplate();
        actTick = 0;
        if (stage.built()) {
            act = Act.INTRO;
            spawnCast();
            beginIntro();
        } else {
            act = Act.RAISING;
        }
        placeHitbox();
    }

    public boolean beginDeath() {
        if (!isMine() || act == Act.DYING || act == Act.DONE || fx == null) {
            return false;
        }
        endMove();
        if (act == Act.RAISING || body == null) {
            // Died before the curtain rose: skip straight to the end.
            act = Act.DYING;
            actTick = DEATH_TICKS;
            return true;
        }
        act = Act.DYING;
        actTick = 0;
        LivingEntity entity = instance.getEntity();
        if (entity != null) {
            entity.setInvulnerable(true);
        }
        return true;
    }

    public void abort() {
        if (!isMine()) {
            return;
        }
        clear();
    }

    /** Remove everything this encounter spawned. Idempotent. */
    public void clear() {
        if (fx == null) {
            return;
        }
        endMove();
        props.clear();
        music.stop();
        if (body != null) {
            body.rig.remove();
        }
        if (hand != null) {
            hand.rig.remove();
        }
        for (int i = 0; i < threads.length; i++) {
            if (threads[i] != null) {
                threads[i].remove();
                threads[i] = null;
            }
        }
        for (int i = 0; i < encoreLines.length; i++) {
            if (encoreLines[i] != null) {
                encoreLines[i].remove();
                encoreLines[i] = null;
            }
        }
        if (dressing != null) {
            dressing.clear();
        }
        fx.clear();
        if (stage != null) {
            stage.clearTemp();
            if (deathFinished && !stage.permanent()) {
                SaintStage s = stage;
                org.bukkit.plugin.Plugin plugin = instance.getPlugin();
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> s.strike(false, plugin), STRIKE_DELAY_TICKS);
                } else {
                    s.strike(true, plugin);
                }
            } else {
                stage.strike(true, instance.getPlugin());
            }
        }
        fx = null;
        body = null;
        hand = null;
        dressing = null;
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

        animate();
        props.tick();
        music.tick();
        dressing.tick();
        placeHitbox();
        if (act == Act.FIGHT || act == Act.TRANSITION) {
            rescueFallers();
        }
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
        entity.setCollidable(false);
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
        entity.setFireTicks(0);
        entity.setFallDistance(0f);
    }

    private int phaseFromTemplate() {
        BossPhase current = instance.getCurrentPhase();
        if (current == null) {
            return 1;
        }
        double hp = current.getHealthPercent();
        return hp > 66.5 ? 1 : hp > 33.5 ? 2 : 3;
    }

    private void spawnCast() {
        dressing = new StageDressing(fx, stage);
        dressing.spawn();
        dressing.hunter.material(Material.RED_STAINED_GLASS);
        body = new SaintBody();
        body.rig.rootPos.set(pos);
        body.poseHang(0);
        body.rig.snapAll();
        body.rig.spawn(fx);
        hand = new HandBody();
        hand.rig.rootPos.set(handPos);
        hand.open();
        hand.rig.snapAll();
        hand.rig.spawn(fx);
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new ThreadLine(fx, 4, 0.05f, Material.GOLD_BLOCK, ThreadLine.GOLD);
            threads[i].reach = 0f;
            cut[i] = false;
        }
        // Rest heights of each anchor relative to the pelvis, for finger coupling.
        body.rig.rootPos.set(0, SaintBody.HANG, 0);
        body.rig.solve();
        for (int i = 0; i < 5; i++) {
            restLift[i] = anchor(i).y - SaintBody.HANG;
        }
        body.rig.rootPos.set(pos);
        body.rig.solve();
    }

    /** If players left and the chunk unloaded, non-persistent displays are gone: rebuild them. */
    private void repairIfUnloaded() {
        if (clock % 20 != 0) {
            return;
        }
        boolean broken = !body.rig.intact() || !hand.rig.intact() || !dressing.intact();
        for (ThreadLine t : threads) {
            broken |= t != null && !t.intact();
        }
        if (!broken) {
            return;
        }
        props.clear();
        body.rig.remove();
        hand.rig.remove();
        dressing.clear();
        for (int i = 0; i < threads.length; i++) {
            if (threads[i] != null) {
                threads[i].remove();
            }
        }
        dressing = new StageDressing(fx, stage);
        dressing.spawn();
        dressing.hunter.material(Material.RED_STAINED_GLASS);
        body.rig.spawn(fx);
        hand.rig.spawn(fx);
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new ThreadLine(fx, 4, 0.05f, Material.GOLD_BLOCK, ThreadLine.GOLD);
            threads[i].visible = !cut[i] && phase < 3;
        }
        if (phase >= 2) {
            dressing.parting(1f);
            dressing.pushCloudsNow(1);
        }
        dressing.skyNow(18000f);
        if (spotLit) {
            dressing.key.set(true);
            dressing.key.aim(spotAim, 0);
        }
    }

    /* ================================================================== per-tick animation */

    private void animate() {
        body.rig.rootPos.set(pos);
        body.rig.yaw = yaw;
        if (hitstop > 0) {
            hitstop--;
        }
        body.rig.frozen = hitstop > 0 || freezeBody;
        body.inertia();
        applyCutLimbs();
        body.spinHalo(phase == 3 ? 0.02f : 0.045f);
        body.rig.step(1f);
        body.rig.render();

        if (!handPinned) {
            handPos.lerp(handGoal, handFollow);
        }
        hand.rig.rootPos.set(handPos);
        hand.rig.yaw = handYaw;
        if (!handBusy && !handPinned && phase < 3) {
            coupleFingers();
        }
        hand.rig.step(1f);
        hand.rig.render();

        for (int i = 0; i < threads.length; i++) {
            ThreadLine t = threads[i];
            if (t == null || cut[i] || !t.visible) {
                continue;
            }
            t.update(hand.fingertip(THREAD_FINGER[i]), anchor(i), 1);
        }

        if (act != Act.DYING && dressing != null) {
            // The follow-spot only tracks once she is fully assembled. It trails her slightly,
            // like an operator on a lamp, which also softens her stop-motion hops in Act III.
            if (spotTracking) {
                spotAim.lerp(new Vector3f(pos.x, 0f, pos.z), 0.3f);
                dressing.key.aim(spotAim, 2);
            }
            dressing.drift(new Vector3f(pos.x, pos.y, pos.z), fx.targets(), clock);
        }
        if (clock % 4 == 0 && !hiddenBody) {
            stage.light("saint", new Vector3f(pos.x, Math.max(1.2f, pos.y - 1.5f), pos.z), 12);
        }
    }

    private Vector3f anchor(int thread) {
        return switch (thread) {
            case T_RWRIST -> body.wrist(SaintBody.R);
            case T_HEAD -> body.headTop();
            case T_LWRIST -> body.wrist(SaintBody.L);
            case T_LKNEE -> body.knee(SaintBody.L);
            default -> body.knee(SaintBody.R);
        };
    }

    /** Every limb she lifts is visible as a flex in the finger that owns its thread. */
    private void coupleFingers() {
        for (int i = 0; i < 5; i++) {
            if (cut[i]) {
                hand.curl(THREAD_FINGER[i], 0.05f);
                continue;
            }
            float lift = anchor(i).y - pos.y - restLift[i];
            float curl = SaintMath.clamp(0.28f + lift * 0.24f + (pos.y - SaintBody.HANG) * 0.02f, 0f, 1f);
            hand.curl(THREAD_FINGER[i], curl);
        }
    }

    /** Limbs whose thread was cut hang dead no matter what pose is requested. */
    private void applyCutLimbs() {
        if (phase >= 3 || act == Act.DYING || unstrungBody) {
            return;
        }
        if (cut[T_RWRIST]) {
            limp(body.upper[SaintBody.R], body.fore[SaintBody.R]);
        }
        if (cut[T_LWRIST]) {
            limp(body.upper[SaintBody.L], body.fore[SaintBody.L]);
        }
        if (cut[T_LKNEE]) {
            limp(body.thigh[SaintBody.L], body.shin[SaintBody.L]);
        }
        if (cut[T_RKNEE]) {
            limp(body.thigh[SaintBody.R], body.shin[SaintBody.R]);
        }
        if (cut[T_HEAD]) {
            body.neck.target.x = 0.7f;
            body.neck.spring(0.05f, 0.08f);
        }
    }

    private static void limp(Skeleton.Bone... bones) {
        for (Skeleton.Bone b : bones) {
            b.target.zero();
            b.spring(0.05f, 0.07f);
        }
    }

    private void placeHitbox() {
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || fx == null) {
            return;
        }
        float feet = Math.max(0.05f, pos.y - SaintBody.STAND);
        if (hiddenBody || act == Act.RAISING) {
            feet = Math.max(0.05f, feet);
        }
        Location at = fx.at(pos.x, feet, pos.z);
        at.setYaw((float) Math.toDegrees(-yaw));
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
            fx.score(Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
            fx.score(Sound.ENTITY_WARDEN_EMERGE, 0.35f, 1.4f);
        }
        float progress = stage.raiseStep(Math.max(180, 7500 / 34));
        if (progress >= 1f) {
            seatAudience();
            act = Act.INTRO;
            actTick = 0;
            spawnCast();
            beginIntro();
        }
    }

    /** Players near the original spawn get hauled up to the stage by golden threads. */
    private void seatAudience() {
        Location spawn = instance.getSpawnLocation();
        Location c = stage.center();
        int i = 0;
        for (Player p : c.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location l = p.getLocation();
            double dxs = l.getX() - spawn.getX();
            double dzs = l.getZ() - spawn.getZ();
            if (dxs * dxs + dzs * dzs > 40 * 40) {
                continue;
            }
            double dy = l.getY() - c.getY();
            double dx = l.getX() - c.getX();
            double dz = l.getZ() - c.getZ();
            boolean onStage = dx * dx + dz * dz < 17 * 17 && dy > -1.5 && dy < 30;
            if (onStage) {
                continue;
            }
            double a = PI + (i++ * 0.7) - 0.7;
            Location to = c.clone().add(Math.sin(a) * 10, 0.1, Math.cos(a) * 10);
            to.setYaw((float) Math.toDegrees(Math.atan2(-(-Math.sin(a)), -Math.cos(a))));
            to.setDirection(c.toVector().subtract(to.toVector()).setY(0));
            p.teleport(to);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false, false));
            p.playSound(to, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
            p.playSound(to, Sound.BLOCK_TRIPWIRE_ATTACH, 1f, 0.5f);
        }
    }

    /* ================================================================== intro */

    private void beginIntro() {
        actTick = 0;
        hiddenBody = false;
        dressing.skyNow(13500f);
        dressing.sky(18000f, 75f);
        // Her pieces lie scattered in a heap at center stage. The (unseen) rig starts low so
        // the descending threads end at the heap, not in the air above it.
        pos.set(0f, SaintBody.SLUMP, 0f);
        yaw = 0f;
        body.rig.rootPos.set(pos);
        body.poseSlump();
        body.rig.snapAll();
        body.rig.solve();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Skeleton.Piece p : body.rig.pieces()) {
            float a = (float) r.nextDouble(TAU);
            float d = (float) r.nextDouble(0.3, 2.8);
            Vector3f c = new Vector3f((float) Math.sin(a) * d, 0.08f, (float) Math.cos(a) * d);
            Vector3f s = new Vector3f(p.size).mul(SaintBody.SCALE);
            float lieY = Math.min(s.x, Math.min(s.y, s.z)) * 0.5f + 0.02f;
            c.y = lieY;
            p.freeMatrix.set(new Matrix4f().translation(c).rotateY((float) r.nextDouble(TAU))
                    .rotateX(s.y > s.x && s.y > s.z ? HALF_PI : 0f)
                    .scale(s).translate(-0.5f, -0.5f, -0.5f));
            p.free = true;
        }
        body.rig.pushAll(0);
        body.haloLit(false);
        for (ThreadLine t : threads) {
            t.reach = 0f;
            t.slack(1.5f);
        }
        freezeBody = false;
    }

    private void tickIntro() {
        int t = actTick++;
        Skeleton rig = body.rig;
        handGoal.set(0, HAND_Y[0], 0);
        handFollow = 0.04f;
        if (t == 22) {
            music.play(10f, 1f, 0.85f, false);
        }
        // Threads descend from the clouds and hook the heap, one per 8 ticks.
        for (int i = 0; i < 5; i++) {
            float grow = window(t, 30 + i * 8, 46 + i * 8);
            threads[i].reach = smooth(grow);
            if (t == 46 + i * 8) {
                Vector3f hook = anchor(i);
                fx.sound(hook, Sound.BLOCK_TRIPWIRE_ATTACH, 1.4f, 0.6f + i * 0.1f);
                fx.sound(hook, Sound.BLOCK_CHAIN_PLACE, 1f, 1.4f);
                threads[i].taut();
            }
        }
        // Assembly, group by group, while the threads lift her off the boards.
        assembleGroup(t, 78, "body", "trim");
        assembleGroup(t, 90, "skirt", null);
        assembleGroup(t, 102, "joint", "needle");
        assembleGroup(t, 114, "veil", "halo");
        assembleGroup(t, 114, "eyesClosed", "crack");
        assembleGroup(t, 114, "eyesOpen", null);
        if (t >= 70 && t < 124) {
            float lift = outCubic(window(t, 70, 124));
            pos.y = lerp(SaintBody.SLUMP, SaintBody.HANG, lift);
            if (lift < 0.35f) {
                body.poseSlump();
            } else {
                body.poseLimp();
            }
        }
        if (t == 70) {
            for (ThreadLine th : threads) {
                th.taut();
                th.pluck(0.3f);
            }
            fx.sound(pos, Sound.ITEM_CROSSBOW_LOADING_END, 1.2f, 0.5f);
        }
        if (t == 128) {
            // Fully assembled: the follow-spot finds her (static until the fight starts).
            spotLit = true;
            spotAim.set(pos.x, 0f, pos.z);
            dressing.key.set(true);
            dressing.key.aim(spotAim, 0);
        }
        if (t == 124) {
            body.head.kick(-0.6f, 0, 0);
            body.neck.kick(-0.5f, 0, 0);
            body.haloLit(true);
            fx.score(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
            fx.score(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.9f);
            fx.dust(body.headTop(), GOLD, 1.4f, 20, 0.5);
        }
        if (t >= 124 && t < 132) {
            body.poseHang(t);
        }
        if (t >= 132 && t < 170) {
            // A curtsey to the audience.
            float depth = arc(window(t, 132, 170));
            body.poseBow(smooth(depth * 1.25f));
            yaw = SaintMath.approachAngle(yaw, 0f, 0.1f);
        }
        if (t == 132) {
            dressing.snapGaze(new Vector3f(pos));
        }
        if (t > 142 && t < 164 && t % 2 == 0) {
            dressing.applause(0.4f + arc(window(t, 142, 164)) * 0.6f);
        }
        if (t == 152) {
            fx.title("&f&lSeraphine", "&6the Hanging Saint", 12, 50, 20);
            fx.score(Sound.BLOCK_BELL_USE, 1f, 0.5f);
        }
        if (t >= 170) {
            body.poseHang(t);
            Player p = fx.nearestTarget(pos);
            if (p != null) {
                Vector3f tp = fx.stage(p.getLocation());
                yaw = SaintMath.approachAngle(yaw, SaintMath.yawToward(tp.x - pos.x, tp.z - pos.z), 0.08f);
            }
            body.head.target.z = 0.45f;
        }
        if (t == 186) {
            for (ThreadLine th : threads) {
                th.taut();
                th.pluck(0.25f);
            }
            fx.sound(pos, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.4f, 0.5f);
        }
        if (t >= INTRO_TICKS) {
            act = Act.FIGHT;
            actTick = 0;
            cooldown = 20;
            move = Move.IDLE;
            if (!spotLit) {
                spotLit = true;
                dressing.key.set(true);
            }
            spotAim.set(pos.x, 0f, pos.z);
            spotTracking = true;
            for (Skeleton.Piece p : rig.pieces()) {
                p.free = false;
            }
            lastHealth = instance.getCombatHealth();
        }
    }

    private void assembleGroup(int t, int at, String a, String b) {
        if (t != at) {
            return;
        }
        Skeleton rig = body.rig;
        rig.solve();
        for (Skeleton.Piece p : rig.pieces()) {
            if (p.group.equals(a) || p.group.equals(b)) {
                p.free = false;
                rig.push(p, 12, true);
                holdPiece(p, 12);
            }
        }
        fx.sound(pos, Sound.BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON, 1.4f, 0.6f);
        fx.sound(pos, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1.2f, 1.3f);
        fx.sound(pos, Sound.ENTITY_SKELETON_STEP, 1f, 0.7f);
    }

    /** Stop the per-tick push for a piece so a long interpolation can play out. */
    private void holdPiece(Skeleton.Piece p, int ticks) {
        body.rig.hold(p, ticks);
    }

    /* ================================================================== fight */

    private void tickFight() {
        actTick++;
        hitReactions();
        if (hitstop > 0) {
            return;
        }
        if (move == Move.IDLE) {
            idle();
            keepOutOfHand();
            if (--cooldown <= 0) {
                chooseMove();
            }
            return;
        }
        int t = moveTick++;
        switch (move) {
            case PLUMMET -> plummet(t);
            case PENDULUM -> pendulum(t);
            case NEEDLEWORK -> needlework(t);
            case PIROUETTE -> pirouette(t);
            case PALM -> palm(t);
            case GRASP -> grasp(t);
            case LUNGE -> lunge(t);
            case TRAPDOOR -> trapdoor(t);
            case WALTZ -> waltz(t);
            case FIST -> fist(t);
            case RESTRING -> restring(t);
            case ENCORE -> encore(t);
            case STITCHES -> stitches(t);
            default -> finish(20);
        }
        keepOutOfHand();
    }

    private void idle() {
        Player p = currentTarget();
        Vector3f goal = p == null ? new Vector3f() : fx.stage(p.getLocation());
        float dist = SaintMath.horizontal(goal, pos);
        if (phase < 3) {
            // Dragged across the boards by the threads, toes scraping.
            body.springs();
            body.poseHang(clock);
            float speed = phase == 1 ? 0.08f : 0.11f;
            if (dist > 4.5f) {
                Vector3f step = new Vector3f(goal.x - pos.x, 0, goal.z - pos.z).normalize(Math.min(speed, dist - 4.5f));
                pos.add(step);
                if (clock % 14 == 0) {
                    fx.sound(new Vector3f(pos.x, 0.1f, pos.z), Sound.ITEM_AXE_SCRAPE, 0.35f, 1.6f);
                    fx.particle(Particle.CRIT, new Vector3f(pos.x, 0.1f, pos.z), 3, 0.2, 0.02);
                }
            }
            pos.y = lerp(pos.y, SaintBody.HANG + (float) Math.sin(clock * 0.06f) * 0.12f, 0.15f);
            for (int i = 0; i < 5; i++) {
                if (!cut[i]) {
                    threads[i].slack(0.35f);
                }
            }
            body.haloLit(true);
        } else {
            // Unstrung: stop-motion stalking, one staccato step every five ticks.
            body.poseWrong(clock);
            body.rig.frameStep = 3;
            body.rig.interp = 1;
            if (clock % 5 == 0 && dist > 4f) {
                Vector3f step = new Vector3f(goal.x - pos.x, 0, goal.z - pos.z).normalize(Math.min(1.25f, dist - 4f));
                pos.add(step);
                fx.sound(new Vector3f(pos.x, 0.2f, pos.z), Sound.ENTITY_SKELETON_STEP, 1f, 0.55f);
                fx.sound(new Vector3f(pos.x, 0.2f, pos.z), Sound.BLOCK_BAMBOO_WOOD_BUTTON_CLICK_OFF, 1f, 0.6f);
            }
            pos.y = lerp(pos.y, SaintBody.STAND - 0.35f, 0.3f);
        }
        clampArena(pos, ARENA);
        faceToward(goal, phase < 3 ? 0.06f : 0.25f);
    }

    private void chooseMove() {
        Player p = currentTarget();
        if (p == null) {
            cooldown = 20;
            return;
        }
        float dist = SaintMath.horizontal(fx.stage(p.getLocation()), pos);
        if (phase == 3 && !encoreDone && instance.healthPercent() <= 15.0) {
            begin(Move.ENCORE);
            return;
        }
        List<Move> bag = new ArrayList<>();
        List<Float> weight = new ArrayList<>();
        if (phase == 1) {
            option(bag, weight, Move.PLUMMET, 2f);
            option(bag, weight, Move.PENDULUM, 2f);
            option(bag, weight, Move.NEEDLEWORK, 1.6f);
            option(bag, weight, Move.PIROUETTE, dist < 7f ? 3.5f : 0.4f);
        } else if (phase == 2) {
            option(bag, weight, Move.PLUMMET, 1.5f);
            option(bag, weight, Move.PENDULUM, 1.6f);
            option(bag, weight, Move.NEEDLEWORK, 1.3f);
            option(bag, weight, Move.PIROUETTE, dist < 7f ? 2.5f : 0.3f);
            option(bag, weight, Move.PALM, 2.2f);
            option(bag, weight, Move.GRASP, dist > 5f ? 2.4f : 1f);
        } else if (!encoreDone) {
            option(bag, weight, Move.LUNGE, 2.6f);
            option(bag, weight, Move.WALTZ, dist < 8f ? 3f : 0.8f);
            option(bag, weight, Move.TRAPDOOR, stage.built() ? 2f : 0f);
            option(bag, weight, Move.FIST, 2f);
            option(bag, weight, Move.RESTRING, 1.5f);
            option(bag, weight, Move.NEEDLEWORK, 0.9f);
        } else {
            option(bag, weight, Move.STITCHES, 3f);
            option(bag, weight, Move.LUNGE, 2f);
            option(bag, weight, Move.WALTZ, dist < 8f ? 2.5f : 0.6f);
            option(bag, weight, Move.TRAPDOOR, stage.built() && freeTraps() >= 2 ? 1.2f : 0f);
        }
        float total = 0f;
        for (int i = 0; i < bag.size(); i++) {
            if (bag.get(i) == lastMove) {
                weight.set(i, weight.get(i) * 0.15f);
            }
            total += weight.get(i);
        }
        float roll = (float) ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < bag.size(); i++) {
            roll -= weight.get(i);
            if (roll <= 0f) {
                begin(bag.get(i));
                return;
            }
        }
        begin(bag.get(0));
    }

    private static void option(List<Move> bag, List<Float> weight, Move m, float w) {
        if (w > 0f) {
            bag.add(m);
            weight.add(w);
        }
    }

    private void begin(Move m) {
        move = m;
        moveTick = 0;
        mI = 0;
        mJ = 0;
        mF = 0f;
        mG = 0f;
        Player p = currentTarget();
        target = p == null ? null : p.getUniqueId();
        body.rig.frameStep = phase == 3 ? 2 : 1;
        body.rig.interp = phase == 3 ? 1 : 2;
    }

    private void finish(int cool) {
        endMove();
        lastMove = move;
        move = Move.IDLE;
        // Act III keeps its stop-motion frame holds, but the dead air between moves is shorter.
        cooldown = phase == 3 ? Math.max(6, Math.round(cool * 0.6f)) : cool;
        body.springs();
        body.haloLit(true);
        body.rig.frameStep = phase == 3 ? 2 : 1;
        body.rig.interp = phase == 3 ? 1 : 2;
        dressing.hunter.set(false);
    }

    /** Tear down any move-owned props. Safe to call at any time. */
    private void endMove() {
        for (SaintProps.GiantNeedle n : needles) {
            if (n != null) {
                n.expire();
            }
        }
        needles.clear();
        ropes.clear();
        bolts.clear();
        if (graspLine != null) {
            graspLine.remove();
            graspLine = null;
        }
        grasped = null;
        if (!handPinned) {
            handBusy = false;
        }
        hiddenBody = false;
        if (stage != null) {
            for (int i = 0; i < 4; i++) {
                if (stage.trapOpen(i)) {
                    stage.setTrap(i, false);
                }
            }
        }
        if (body != null) {
            body.restoreVisibility(phase == 3);
        }
    }

    /* ================================================================== move: PLUMMET */

    /**
     * Hoisted limp into the air above you (the red follow-spot finds you), she hangs, tracks,
     * locks, and the threads let go. Needles first. Then she lies crumpled: the opening.
     * Act II drops her twice.
     */
    private void plummet(int t) {
        int base = mI == 0 ? 0 : 6;
        int k = t;
        Player p = currentTarget();
        if (k == base) {
            tautAll(true);
            body.springs();
            dressing.hunter.set(true);
            mA.set(pos);
        }
        if (k >= base && k < 22) {
            float h = outCubic(window(k, base, 22));
            pos.y = lerp(mA.y, 15f, h);
            if (p != null) {
                Vector3f tp = fx.stage(p.getLocation());
                pos.x = lerp(pos.x, tp.x, 0.05f);
                pos.z = lerp(pos.z, tp.z, 0.05f);
            }
            body.poseHoisted();
            handGoal.set(pos.x, HAND_Y[phase - 1] + 4f, pos.z);
        }
        if (k >= 22 && k < 34 && p != null) {
            Vector3f tp = fx.stage(p.getLocation());
            clampArena(tp, ARENA);
            pos.x = lerp(pos.x, tp.x, 0.16f);
            pos.z = lerp(pos.z, tp.z, 0.16f);
            pos.y = 15f + (float) Math.sin(k * 0.4f) * 0.25f;
            dressing.hunter.aim(tp, 2);
            body.poseHoisted();
        }
        if (k == 34) {
            mB.set(pos.x, 0, pos.z);
            clampArena(mB, ARENA);
            props.add(new SaintProps.Telegraph(fx, mB, 4.4f, 15, false));
            fx.sound(new Vector3f(mB.x, 0.2f, mB.z), Sound.BLOCK_NOTE_BLOCK_BASS, 1.6f, 0.5f);
            fx.sound(pos, Sound.BLOCK_BELL_USE, 1.3f, 1.4f);
            body.springsSharp();
            body.poseDive();
        }
        if (k > 34 && k < 44) {
            pos.x = lerp(pos.x, mB.x, 0.3f);
            pos.z = lerp(pos.z, mB.z, 0.3f);
            body.poseDive();
            hand.spread(0.2f + window(k, 36, 44) * 0.8f);
        }
        if (k == 44) {
            for (int i = 0; i < 5; i++) {
                if (!cut[i]) {
                    threads[i].slack(3.5f);
                    threads[i].sag = 3.5f;
                }
            }
            handBusy = true;
            hand.open();
            fx.sound(pos, Sound.BLOCK_TRIPWIRE_DETACH, 1.5f, 0.6f);
            fx.sound(pos, Sound.ENTITY_PHANTOM_SWOOP, 1.6f, 0.7f);
        }
        if (k >= 44 && k < 49) {
            float f = inCubic(window(k, 44, 49));
            pos.set(mB.x, lerp(15f, 1.9f, f), mB.z);
            body.poseDive();
        }
        if (k == 49) {
            pos.set(mB.x, 1.9f, mB.z);
            body.poseLanded();
            slam(new Vector3f(mB), 4.4f, 150, 11f, 12);
            hitstop = 4;
            body.haloLit(false);
            dressing.hunter.set(false);
        }
        if (phase == 2 && mI == 0 && k == 62) {
            // Act II: straight back up for a second drop.
            mI = 1;
            moveTick = 6;
            handBusy = false;
            dressing.hunter.set(true);
            return;
        }
        if (k > 49 && k < 82) {
            body.springsLimp();
            body.poseLanded();
            body.neck.target.x = 0.8f;
            pos.y = 1.9f;
        }
        if (k == 82) {
            handBusy = false;
            for (int i = 0; i < 5; i++) {
                if (!cut[i]) {
                    threads[i].slack(0.3f);
                }
            }
            fx.sound(pos, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.2f, 0.6f);
        }
        if (k >= 82 && k < 102) {
            body.springs();
            pos.y = lerp(1.9f, SaintBody.HANG, outBack(window(k, 82, 102)));
            body.poseHang(clock);
        }
        if (k == 96) {
            body.haloLit(true);
        }
        if (k >= 102) {
            finish(phase == 1 ? 30 : 22);
        }
    }

    /* ================================================================== move: PENDULUM */

    /**
     * Hauled back up one side of the stage, then swung like a pendulum straight through you,
     * spinning, both needles out. A golden lane shows the path. Two passes, three in Act II.
     */
    private void pendulum(int t) {
        int windup = 26;
        int pass = phase == 1 ? 30 : 24;
        int passes = phase == 1 ? 2 : 3;
        float length = 14.5f;
        float maxAngle = 0.95f;
        if (t == 0) {
            Player p = currentTarget();
            Vector3f tp = p == null ? new Vector3f(0, 0, 1) : fx.stage(p.getLocation());
            Vector3f dir = new Vector3f(tp.x - pos.x, 0, tp.z - pos.z);
            if (dir.lengthSquared() < 0.01f) {
                dir.set(0, 0, 1);
            }
            dir.normalize();
            Vector3f mid = new Vector3f(pos).lerp(tp, 0.5f);
            mid.y = 0;
            clampArena(mid, 6f);
            // Bottom of the arc puts the pelvis at 4: the reap cone's blade tips skim the boards.
            mA.set(mid.x, 4.0f + length, mid.z);
            mB.set(dir);
            float reach = length * (float) Math.sin(maxAngle);
            Vector3f from = new Vector3f(mA.x, 0, mA.z).fma(-reach, dir);
            Vector3f to = new Vector3f(mA.x, 0, mA.z).fma(reach, dir);
            // She spins through the swing, so the danger is the full blade cone (~4.6 each side).
            props.add(new SaintProps.Lane(fx, from, to, 9.2f, windup, false));
            tautAll(true);
            dressing.hunter.set(true);
            dressing.hunter.aim(tp, 3);
            mC.set(pos);
        }
        if (t < windup) {
            float w = smooth(window(t, 0, windup));
            Vector3f start = swing(-maxAngle, length);
            pos.set(lerp(mC.x, start.x, w), lerp(mC.y, start.y, w), lerp(mC.z, start.z, w));
            yaw = SaintMath.approachAngle(yaw, SaintMath.yawToward(mB.x, mB.z), 0.12f);
            body.poseReap(-1f);
            handGoal.set(mA.x, HAND_Y[phase - 1], mA.z);
            if (t % 6 == 0) {
                fx.sound(pos, Sound.BLOCK_CHAIN_STEP, 1.2f, 0.5f);
                fx.sound(pos, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 0.5f);
            }
            return;
        }
        int s = t - windup;
        if (s < pass * passes) {
            int which = s / pass;
            float local = (s % pass) / (float) pass;
            float dirSign = which % 2 == 0 ? 1f : -1f;
            float angle = -maxAngle * dirSign * (float) Math.cos(PI * local);
            pos.set(swing(angle, length));
            yaw += 0.42f;
            body.springsSharp();
            body.poseReap(dirSign * (float) Math.sin(PI * local));
            float bottom = Math.abs(local - 0.5f);
            if (bottom < 0.04f) {
                fx.sound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 0.5f);
                fx.sound(pos, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 0.8f);
            }
            if (pos.y < 6.5f) {
                hitNeedles(115, 1.0f, "pend" + which, 12);
                hitSphere(new Vector3f(pos.x, pos.y - 1.5f, pos.z), 2.2f, 100, pos, 1.0f, 0.5f, "pendb" + which, 12);
            }
            return;
        }
        int e = s - pass * passes;
        if (e == 0) {
            mC.set(pos);
            body.springs();
            body.haloLit(false);
            dressing.hunter.set(false);
        }
        float d = smooth(window(e, 0, 24));
        Vector3f land = new Vector3f(mC.x, 0, mC.z);
        clampArena(land, ARENA);
        pos.set(lerp(mC.x, land.x, d), lerp(mC.y, SaintBody.HANG, d), lerp(mC.z, land.z, d));
        yaw += 0.42f * (1f - d);
        body.poseHang(clock);
        body.head.target.z = (float) Math.sin(e * 0.5f) * 0.4f * (1f - d);
        if (e >= 32) {
            finish(26);
        }
    }

    private Vector3f swing(float angle, float length) {
        return new Vector3f(mA.x, mA.y, mA.z)
                .fma(length * (float) Math.sin(angle), mB)
                .add(0, -length * (float) Math.cos(angle), 0);
    }

    /* ================================================================== move: NEEDLEWORK */

    /**
     * The Hand drops giant needles into the stage one by one, then stitches thread between them.
     * Each stitch rises, hums, flashes white and plucks, in sequence, each on a note of her
     * lullaby. Gold stitches run at the ankle (jump). Crimson ones run at the neck (crouch).
     */
    private void needlework(int t) {
        int count = phase == 1 ? 5 : phase == 2 ? 6 : 7;
        int drop0 = 10;
        int dropGap = 5;
        int fall = 14;
        if (t == 0) {
            float rot = (float) ThreadLocalRandom.current().nextDouble(TAU);
            mI = count;
            for (int i = 0; i < count; i++) {
                float a = rot + i * TAU * 2f / count + (count % 2 == 0 ? i * 0.35f : 0f);
                float r = i % 2 == 0 ? 11.5f : 6.5f;
                Vector3f base = new Vector3f((float) Math.sin(a) * r, 0, (float) Math.cos(a) * r);
                needles.add(null);
                mC.set(base);
                storeNeedlePoint(i, base);
            }
            tautAll(false);
            body.springs();
            if (taught.add("needlework")) {
                fx.actionBar("&6Gold thread &7— jump it.     &cCrimson thread &7— crouch under it.");
            }
        }
        body.poseCrucifix();
        body.upper[SaintBody.R].target.z = -2.4f;
        body.upper[SaintBody.L].target.z = 2.4f;
        pos.y = lerp(pos.y, SaintBody.HANG + 0.8f, 0.1f);
        handBusy = true;
        for (int i = 0; i < mI; i++) {
            int at = drop0 + i * dropGap;
            Vector3f base = needlePoint(i);
            if (t == at - 8) {
                props.add(new SaintProps.Telegraph(fx, base, 1.3f, 8 + fall, false));
            }
            if (t == at) {
                SaintProps.GiantNeedle n = props.add(new SaintProps.GiantNeedle(fx, base, fall, 150));
                needles.set(i, n);
                int f = i % 5;
                hand.curl(f, 1f);
                hand.finger[f][0].kick(0.5f, 0, 0);
                fx.sound(new Vector3f(base.x, 8f, base.z), Sound.ITEM_TRIDENT_RIPTIDE_2, 1f, 1.6f);
            }
            if (t == at + 4) {
                hand.curl(i % 5, 0.2f);
            }
            if (t == at + fall) {
                fx.sound(base, Sound.BLOCK_WOOD_BREAK, 1.6f, 0.5f);
                fx.sound(base, Sound.ITEM_TRIDENT_HIT_GROUND, 1.6f, 0.6f);
                fx.sound(base, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
                FakeDestruction.spawnDebris(fx.at(base), instance, 4, 0.25, 0.5, 24,
                        new Material[]{Material.DARK_OAK_PLANKS, Material.SPRUCE_PLANKS});
                hitSphere(new Vector3f(base.x, 0.9f, base.z), 1.5f, 70, base, 0.8f, 0.4f, "needle" + i, 20);
            }
        }
        int stitch0 = drop0 + (mI - 1) * dropGap + fall + 4;
        if (t == stitch0) {
            // The very first stitches of the fight are all gold (jump only); after that they mix.
            boolean teach = phase == 1 && taught.add("needlework-first");
            for (int i = 0; i < mI - 1; i++) {
                boolean high = !teach && (i % 2 == 1);
                int arm = 24 + i * 7;
                ropes.add(props.add(new SaintProps.Rope(fx, needlePoint(i), needlePoint(i + 1), high, arm - 14, arm + 10)));
            }
            fx.sound(pos, Sound.ITEM_CROSSBOW_LOADING_START, 1.4f, 0.6f);
        }
        for (int i = 0; i < ropes.size(); i++) {
            SaintProps.Rope rope = ropes.get(i);
            if (rope.age == rope.pluckAt - 6) {
                // Audible tell in sync with the white flash: jump / duck now.
                Vector3f mid = new Vector3f(rope.a).add(rope.b).mul(0.5f).add(0, rope.height, 0);
                fx.sound(mid, Sound.BLOCK_NOTE_BLOCK_HAT, 1.8f, rope.high ? 0.6f : 1.4f);
            }
            if (rope.age == rope.pluckAt) {
                pluckRope(rope, i);
            }
        }
        if (t > stitch0 && !ropes.isEmpty() && ropes.get(ropes.size() - 1).age > ropes.get(ropes.size() - 1).life - 2) {
            handBusy = false;
            finish(phase == 3 ? 16 : 24);
        }
        if (t > 400) {
            finish(20);
        }
    }

    private final Vector3f[] needlePoints = new Vector3f[8];

    private void storeNeedlePoint(int i, Vector3f p) {
        needlePoints[i] = new Vector3f(p);
    }

    private Vector3f needlePoint(int i) {
        return new Vector3f(needlePoints[i]);
    }

    private void pluckRope(SaintProps.Rope rope, int index) {
        Vector3f mid = new Vector3f(rope.a).add(rope.b).mul(0.5f).add(0, rope.height, 0);
        float pitch = SaintMath.semitone(MusicBox.THEME[index * 3 % MusicBox.THEME.length] < 0 ? 15 : MusicBox.THEME[index * 3 % MusicBox.THEME.length]);
        fx.sound(mid, Sound.BLOCK_NOTE_BLOCK_GUITAR, 2f, Math.max(0.5f, Math.min(2f, pitch * 0.8f)));
        fx.sound(mid, Sound.BLOCK_TRIPWIRE_CLICK_OFF, 1.5f, 0.5f);
        for (Player p : fx.targets()) {
            Vector3f pp = fx.stage(p.getLocation());
            if (SaintMath.segmentDistanceXZ(pp, rope.a, rope.b) > 0.75f) {
                continue;
            }
            boolean safe = rope.high ? p.isSneaking() || p.isSwimming() : pp.y > 0.42f;
            if (safe) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.8f);
                continue;
            }
            Vector3f ab = new Vector3f(rope.b).sub(rope.a);
            Vector3f side = new Vector3f(-ab.z, 0, ab.x).normalize();
            float s = Math.signum(new Vector3f(pp).sub(rope.a).dot(side));
            Vector3f from = new Vector3f(pp).fma(-s, side);
            strike(p, 95, from, 0.9f, rope.high ? 0.25f : 0.7f, "rope" + index, 10);
        }
    }

    /* ================================================================== move: PIROUETTE */

    /**
     * The music box is wound: she coils against the spin, a ratchet clicking faster and higher.
     * Then she spins up onto pointe, skirt blown flat, needles out to a seven-block reach,
     * while the lullaby plays at triple speed. She ends dizzy. Opening.
     */
    private void pirouette(int t) {
        int wind = phase == 1 ? 32 : 26;
        int spin = 50;
        if (t == 0) {
            tautAll(true);
            props.add(new SaintProps.Telegraph(fx, new Vector3f(pos.x, 0, pos.z), 6.5f, wind, false));
            mF = yaw;
        }
        if (t < wind) {
            float w = window(t, 0, wind);
            body.springs();
            body.poseWind(w);
            yaw = mF - w * 1.2f;
            pos.y = lerp(pos.y, SaintBody.STAND + 0.2f, 0.12f);
            int every = Math.max(2, 5 - (int) (w * 4));
            if (t % every == 0) {
                fx.sound(pos, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1.3f, 0.6f + w * 0.9f);
            }
            return;
        }
        int s = t - wind;
        if (s == 0) {
            music.play(3f, 1f, 1f, true);
            fx.sound(pos, Sound.ITEM_CROSSBOW_LOADING_END, 1.6f, 1.2f);
            mG = 0.12f;
        }
        if (s < spin) {
            mG = Math.min(0.62f, mG + 0.025f);
            yaw += mG;
            body.springsSharp();
            body.posePirouette(1.25f);
            Player p = currentTarget();
            if (p != null) {
                Vector3f tp = fx.stage(p.getLocation());
                Vector3f step = new Vector3f(tp.x - pos.x, 0, tp.z - pos.z);
                if (step.length() > 2f) {
                    pos.add(step.normalize(phase == 1 ? 0.05f : 0.08f));
                }
            }
            clampArena(pos, ARENA);
            if (s % 3 == 0) {
                hitNeedles(90, 1.1f, "pir", 8);
            }
            if (s % 8 == 0) {
                fx.sound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f + mG);
            }
            return;
        }
        int e = s - spin;
        if (e == 0) {
            music.stop();
            fx.score(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 0.53f);
            body.haloLit(false);
            body.springs();
        }
        mG *= 0.82f;
        yaw += mG;
        body.poseHang(clock);
        body.rig.tilt.identity().rotateZ((float) Math.sin(e * 0.45f) * 0.12f * (1f - window(e, 0, 22)));
        pos.y = lerp(pos.y, SaintBody.HANG, 0.1f);
        if (e >= 26) {
            body.rig.tilt.identity();
            finish(24);
        }
    }

    /* ================================================================== move: PALM */

    /**
     * The Hand itself strikes: wrist bends, palm turns to the stage, a shadow spreads over you,
     * and it slams flat. While the Hand is busy, the puppet has no one holding her: she
     * collapses where she stands. Hit her.
     */
    private void palm(int t) {
        if (t == 0) {
            Player p = currentTarget();
            Vector3f tp = p == null ? new Vector3f() : fx.stage(p.getLocation());
            clampArena(tp, ARENA);
            mA.set(tp.x, 0, tp.z);
            Vector3f dir = new Vector3f(tp.x - pos.x, 0, tp.z - pos.z);
            if (dir.lengthSquared() < 0.1f) {
                dir.set(0, 0, 1);
            }
            dir.normalize();
            handYaw = SaintMath.yawToward(dir.x, dir.z);
            mB.set(mA).fma(-3.2f, dir);
            handBusy = true;
            handFollow = 0.12f;
            hand.spread(1f);
            for (int f = 0; f < 5; f++) {
                hand.curl(f, 0.05f);
            }
            props.add(new SaintProps.Telegraph(fx, mA, 6.6f, 34, false));
            dressing.hunter.set(true);
            dressing.hunter.aim(mA, 4);
            for (int i = 0; i < 5; i++) {
                if (!cut[i]) {
                    threads[i].slack(1.6f);
                }
            }
            fx.sound(new Vector3f(mA.x, 20f, mA.z), Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.5f);
            fx.sound(pos, Sound.BLOCK_TRIPWIRE_DETACH, 1.2f, 0.5f);
        }
        // She crumples: nobody is holding her strings.
        if (t < 90) {
            body.springsLimp();
            body.poseSlump();
            body.haloLit(false);
            float sink = smooth(window(t, 0, 16));
            pos.y = lerp(SaintBody.HANG, SaintBody.SLUMP, sink);
        }
        if (t < 28) {
            float w = smooth(window(t, 0, 26));
            hand.flex(w);
            handGoal.set(mB.x, lerp(HAND_Y[phase - 1], 12f + window(t, 18, 28) * 4f, w), mB.z);
        } else if (t < 34) {
            float f = inCubic(window(t, 28, 34));
            hand.flex(1f);
            handFollow = 1f;
            handGoal.set(mB.x, lerp(16f, 1.25f, f), mB.z);
        }
        if (t == 34) {
            Vector3f under = hand.palmUnder();
            slam(new Vector3f(under.x, 0, under.z), 6.6f, 170, 17f, 20);
            hitstop = 5;
            stageBounce(20f, 0.45f);
            fx.sound(under, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 0.5f);
            dressing.hunter.set(false);
        }
        if (t > 34 && t < 64) {
            handGoal.set(mB.x, 1.25f, mB.z);
            if (t % 7 == 0) {
                int f = ThreadLocalRandom.current().nextInt(5);
                hand.finger[f][0].kick(-0.25f, 0, 0);
            }
        }
        if (t == 64) {
            handFollow = 0.06f;
            fx.sound(new Vector3f(mB.x, 2f, mB.z), Sound.BLOCK_GRAVEL_BREAK, 2f, 0.5f);
        }
        if (t >= 64) {
            hand.flex(1f - smooth(window(t, 64, 90)));
            handGoal.set(pos.x, HAND_Y[phase - 1], pos.z);
        }
        if (t == 86) {
            handBusy = false;
            for (int i = 0; i < 5; i++) {
                if (!cut[i]) {
                    threads[i].slack(0.3f);
                }
            }
            fx.sound(pos, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.2f, 0.6f);
        }
        if (t >= 90) {
            body.springs();
            pos.y = lerp(SaintBody.SLUMP, SaintBody.HANG, outBack(window(t, 90, 108)));
            body.poseHang(clock);
        }
        if (t >= 108) {
            finish(24);
        }
    }

    /* ================================================================== move: GRASP */

    /**
     * She draws back and casts a thread at you like a fishing line. If it catches, you are
     * strung: a beat of humming tension, then you are reeled toward her, hoisted, and slammed.
     */
    private void grasp(int t) {
        int arm = SaintBody.R;
        if (t == 0) {
            Player p = currentTarget();
            if (p == null) {
                finish(10);
                return;
            }
            tautAll(true);
            dressing.hunter.set(true);
            Vector3f tp = fx.stage(p.getLocation());
            props.add(new SaintProps.Lane(fx, pos, tp, 1.2f, 16, true));
        }
        Player p = currentTarget();
        if (t < 16) {
            body.springs();
            body.poseDrawBack(arm, smooth(window(t, 0, 14)));
            if (p != null) {
                Vector3f tp = fx.stage(p.getLocation());
                faceToward(tp, 0.2f);
                dressing.hunter.aim(tp, 2);
                mA.set(tp).add(0, 1.1f, 0);
            }
            if (t == 8) {
                fx.sound(pos, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1.3f, 0.7f);
            }
            return;
        }
        if (t == 16) {
            body.springsSharp();
            body.poseThrust(arm);
            graspLine = new ThreadLine(fx, 5, 0.06f, Material.GOLD_BLOCK, CRIMSON);
            graspLine.reach = 0f;
            fx.sound(pos, Sound.ENTITY_FISHING_BOBBER_THROW, 1.6f, 0.6f);
            fx.sound(pos, Sound.ITEM_TRIDENT_THROW, 1.2f, 0.8f);
            mB.set(body.wrist(arm));
        }
        if (graspLine == null) {
            finish(10);
            return;
        }
        Vector3f wrist = body.wrist(arm);
        if (grasped == null) {
            float cast = window(t, 16, 28);
            graspLine.reach = cast;
            Vector3f tip = SaintMath.lerp(wrist, mA, cast, new Vector3f());
            graspLine.update(wrist, mA, 1);
            for (Player victim : fx.targets()) {
                Vector3f vp = fx.stage(victim.getLocation()).add(0, 1f, 0);
                if (vp.distance(tip) < 1.5f) {
                    grasped = victim.getUniqueId();
                    graspAt = t;
                    victim.playSound(victim.getLocation(), Sound.BLOCK_TRIPWIRE_ATTACH, 1.4f, 0.5f);
                    fx.sound(tip, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 1.6f);
                    break;
                }
            }
            if (grasped == null && t >= 28) {
                // Missed: reel the empty line back in.
                graspLine.reach = 1f - window(t, 28, 36);
                if (t >= 36) {
                    finish(18);
                }
            }
            return;
        }
        Player victim = Bukkit.getPlayer(grasped);
        if (victim == null || !victim.isValid() || victim.isDead()) {
            finish(14);
            return;
        }
        int g = t - graspAt;
        Vector3f vp = fx.stage(victim.getLocation()).add(0, 1f, 0);
        graspLine.reach = 1f;
        graspLine.slack(g < 14 ? 0.6f : 0f);
        if (g < 14) {
            graspLine.pluck(0.15f);
            body.poseSweep(arm, -0.6f);
            if (g % 3 == 0) {
                fx.sound(vp, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 0.8f + g * 0.05f);
            }
            victim.setVelocity(victim.getVelocity().multiply(0.4));
        }
        if (g == 14) {
            body.poseSweep(arm, 1f);
            Vector3f toHer = new Vector3f(pos.x - vp.x, 0, pos.z - vp.z);
            float d = toHer.length();
            if (d > 0.1f) {
                toHer.normalize(Math.min(1.2f, d * 0.09f));
            } else {
                toHer.zero();
            }
            victim.setVelocity(new Vector(toHer.x, 1.25, toHer.z));
            fx.sound(vp, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.6f, 0.5f);
            fx.sound(pos, Sound.ITEM_CROSSBOW_SHOOT, 1.4f, 0.5f);
        }
        if (g == 24) {
            victim.setVelocity(new Vector(0, -2.4, 0));
            body.poseThrust(arm);
            body.upper[arm].target.x = 0.6f;
            fx.sound(vp, Sound.ENTITY_PHANTOM_SWOOP, 1.4f, 0.6f);
        }
        if (g > 24 && (victim.isOnGround() || g >= 34) && mJ == 0) {
            mJ = 1;
            Vector3f land = fx.stage(victim.getLocation());
            strike(victim, 130, new Vector3f(land.x, 0, land.z + 0.01f), 0f, 0.35f, "grasp", 30);
            props.add(new SaintProps.Shockwave(fx, land, 0.5f, 4.5f, 0.6f, 10, Material.WHITE_STAINED_GLASS, null));
            fx.sound(land, Sound.ITEM_MACE_SMASH_GROUND, 1.6f, 0.7f);
            FakeDestruction.spawnDebris(fx.at(land), instance, 6, 0.3, 0.6, 26,
                    new Material[]{Material.DARK_OAK_PLANKS, Material.SPRUCE_PLANKS});
            graspLine.sever(0.5f, 6f, 0.05f, 12);
        }
        if (mJ == 0) {
            graspLine.update(wrist, vp, 1);
        }
        if (mJ == 1 && g > 46) {
            finish(22);
        }
    }

    /* ================================================================== move: LUNGE (Act III) */

    /** Hop phase: three staccato hops, one every six ticks. */
    private static final int LUNGE_HOPS = 18;
    /** Needle drawn back before the thrust (the golden lane's life). */
    private static final int LUNGE_DRAW = 11;

    /**
     * Three stop-motion hops toward you (each leaves a glass afterimage and a dry clack),
     * a drawn-back needle with a golden lane, then a thrust that crosses half the stage.
     */
    private void lunge(int t) {
        int arm = mI % 2 == 0 ? SaintBody.R : SaintBody.L;
        Player p = currentTarget();
        Vector3f tp = p == null ? new Vector3f() : fx.stage(p.getLocation());
        if (t < LUNGE_HOPS) {
            body.poseWrong(clock);
            body.rig.frameStep = 4;
            body.rig.interp = 0;
            if (t % 6 == 4) {
                props.add(new SaintProps.Afterimage(fx, body.rig, 14, false));
                Vector3f step = new Vector3f(tp.x - pos.x, 0, tp.z - pos.z);
                float d = step.length();
                if (d > 3.5f) {
                    pos.add(step.normalize(Math.min(3.2f, d - 3f)));
                }
                clampArena(pos, ARENA);
                yaw = SaintMath.yawToward(tp.x - pos.x, tp.z - pos.z);
                fx.sound(pos, Sound.ENTITY_SKELETON_STEP, 1.5f, 0.5f);
                fx.sound(pos, Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 1.2f, 0.5f);
                body.rig.solve();
                body.rig.pushAll(0);
            }
            return;
        }
        int w = t - LUNGE_HOPS;
        if (w == 0) {
            body.rig.frameStep = 1;
            body.rig.interp = 1;
            yaw = SaintMath.yawToward(tp.x - pos.x, tp.z - pos.z);
            mA.set(pos);
            Vector3f dir = new Vector3f((float) Math.sin(yaw), 0, (float) Math.cos(yaw));
            mB.set(pos).fma(8f, dir);
            clampArena(mB, ARENA + 1f);
            props.add(new SaintProps.Lane(fx, new Vector3f(pos.x, 0, pos.z), new Vector3f(mB.x, 0, mB.z), 2.2f, LUNGE_DRAW, false));
            fx.sound(pos, Sound.ENTITY_VEX_CHARGE, 1.4f, 0.55f);
        }
        if (w < LUNGE_DRAW) {
            body.springsSharp();
            body.poseDrawBack(arm, smooth(window(w, 0, LUNGE_DRAW - 2)));
            return;
        }
        int s = w - LUNGE_DRAW;
        if (s == 0) {
            fx.sound(pos, Sound.ITEM_TRIDENT_THROW, 1.6f, 0.6f);
            fx.sound(pos, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.4f, 0.5f);
            fx.sound(pos, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.3f);
        }
        if (s < 4) {
            float f = outCubic(window(s, 0, 3));
            pos.set(lerp(mA.x, mB.x, f), mA.y, lerp(mA.z, mB.z, f));
            body.poseThrust(arm);
            hitSegment(new Vector3f(mA.x, 1f, mA.z), new Vector3f(pos.x, 1f, pos.z), 1.6f, 140, 1.1f, 0.4f, "lunge", 20);
            hitNeedles(140, 1.0f, "lunge", 20);
            return;
        }
        body.poseThrust(arm);
        if (s == 5) {
            body.haloLit(false);
        }
        if (s >= 14) {
            if (phase == 3 && mI == 0 && instance.healthPercent() < 25) {
                // Late Act III: a second lunge off the other arm.
                mI = 1;
                moveTick = 0;
                return;
            }
            finish(16);
        }
    }

    /* ================================================================== move: TRAPDOOR (Act III) */

    /**
     * She skitters to a trapdoor and drops through the stage. Every other trapdoor starts to
     * rattle. The one near you rattles hardest and bleeds gold light. She erupts from it.
     */
    private void trapdoor(int t) {
        if (t == 0) {
            Player p = currentTarget();
            Vector3f tp = p == null ? new Vector3f() : fx.stage(p.getLocation());
            mI = nearestTrap(pos, -1);
            mJ = nearestTrap(tp, mI);
            mA.set(pos);
            if (mI < 0 || mJ < 0) {
                // The fallen Hand covers the hatches she would need.
                finish(6);
                return;
            }
        }
        Vector3f from = SaintStage.trapCenter(mI);
        Vector3f exit = SaintStage.trapCenter(mJ);
        if (t < 16) {
            body.poseWrong(clock);
            body.rig.frameStep = 3;
            if (t % 3 == 0) {
                float f = smooth(window(t, 0, 15));
                pos.x = lerp(mA.x, from.x, f);
                pos.z = lerp(mA.z, from.z, f);
                fx.sound(pos, Sound.ENTITY_SKELETON_STEP, 1f, 0.6f);
            }
            faceToward(from, 0.3f);
            return;
        }
        if (t == 16) {
            stage.setTrap(mI, true);
            fx.sound(from, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 2f, 0.5f);
            body.rig.frameStep = 1;
            body.springsSharp();
            body.poseDive();
        }
        if (t >= 16 && t < 22) {
            pos.set(from.x, lerp(SaintBody.STAND, -3.5f, inCubic(window(t, 16, 22))), from.z);
        }
        if (t == 22) {
            hiddenBody = true;
            for (Skeleton.Piece piece : body.rig.pieces()) {
                piece.hidden = true;
            }
            fx.sound(from, Sound.BLOCK_WOOD_BREAK, 1.2f, 0.5f);
        }
        if (t == 27) {
            stage.setTrap(mI, false);
            fx.sound(from, Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 2f, 0.5f);
        }
        if (t > 27 && t < 50) {
            pos.set(exit.x, -3.5f, exit.z);
            if (t % 5 == 0) {
                for (int i = 0; i < 4; i++) {
                    if (i == mJ) {
                        continue;
                    }
                    boolean open = ThreadLocalRandom.current().nextBoolean();
                    stage.setTrap(i, open);
                    fx.sound(SaintStage.trapCenter(i), open ? Sound.BLOCK_WOODEN_TRAPDOOR_OPEN : Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 1.2f, 0.7f);
                }
            }
            if (t % 3 == 0) {
                boolean open = (t / 3) % 2 == 0;
                stage.setTrap(mJ, open);
                fx.sound(exit, open ? Sound.BLOCK_WOODEN_TRAPDOOR_OPEN : Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 1.6f, 0.55f);
                fx.dust(new Vector3f(exit.x, 0.3f, exit.z), GOLD, 1.5f, 6, 0.7);
            }
            if (t == 30) {
                props.add(new SaintProps.Telegraph(fx, exit, 3.3f, 20, false));
                for (int i = 0; i < 4; i++) {
                    if (i != mJ) {
                        stage.setTrap(i, false);
                    }
                }
            }
        }
        if (t == 50) {
            hiddenBody = false;
            stage.setTrap(mJ, true);
            body.crackTo(1f - (float) instance.healthPercent() / 100f);
            body.restoreVisibility(true);
            body.eyesOpen(true);
            body.poseCrucifix();
            body.upper[SaintBody.R].target.z = -2.9f;
            body.upper[SaintBody.L].target.z = 2.9f;
            fx.sound(exit, Sound.BLOCK_WOOD_BREAK, 2f, 0.5f);
            fx.sound(exit, Sound.ENTITY_BREEZE_WIND_BURST, 1.4f, 0.8f);
            fx.sound(exit, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.4f, 0.8f);
            FakeDestruction.spawnDebris(fx.at(exit), instance, 12, 0.35, 1.1, 30,
                    new Material[]{Material.SPRUCE_TRAPDOOR, Material.DARK_OAK_PLANKS});
            hitSphere(new Vector3f(exit.x, 1f, exit.z), 3.4f, 150, exit, 0.5f, 1.25f, "trap", 30);
        }
        if (t >= 50 && t < 56) {
            pos.set(exit.x, lerp(-3.5f, 8f, outCubic(window(t, 50, 56))), exit.z);
            yaw += 0.6f;
        }
        if (t >= 56 && t < 72) {
            Vector3f land = new Vector3f(exit).mul(0.62f);
            float f = window(t, 56, 72);
            pos.set(lerp(exit.x, land.x, f), lerp(8f, SaintBody.STAND - 0.35f, inCubic(f)), lerp(exit.z, land.z, f));
            body.poseLanded();
        }
        if (t == 64) {
            stage.setTrap(mJ, false);
        }
        if (t == 72) {
            fx.sound(pos, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, 0.8f);
            body.haloLit(false);
        }
        if (t >= 86) {
            finish(18);
        }
    }

    /** @return the closest free trapdoor, or -1 when every candidate lies under the fallen Hand */
    private int nearestTrap(Vector3f from, int exclude) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            if (i == exclude || trapUnderHand(i)) {
                continue;
            }
            float d = SaintMath.horizontal(SaintStage.trapCenter(i), from);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private boolean trapUnderHand(int i) {
        return handPinned && insideHand(new Vector3f(SaintStage.trapCenter(i)).add(0, 0.5f, 0), 1.6f);
    }

    private int freeTraps() {
        int free = 0;
        for (int i = 0; i < 4; i++) {
            if (!trapUnderHand(i)) {
                free++;
            }
        }
        return free;
    }

    /* ================================================================== move: WALTZ (Act III) */

    private static final int WALTZ_BEAT = 7;
    /**
     * Beat index of each cut. She skips the fourth downbeat (beat 9) and holds through the
     * melody's two-beat rest, then cuts on beat 12: a delayed swing.
     */
    private static final int[] WALTZ_CUTS = {0, 3, 6, 12, 15};
    private static final int WALTZ_SPIN = 18;

    /**
     * The Severed Waltz. The music box plays her lullaby; each cut lands on a downbeat.
     * After the third cut the melody rests for two beats, and so does she. Panic-dodgers eat
     * the fourth. The last bar is a full spin.
     */
    private void waltz(int t) {
        if (t == 0) {
            music.play(WALTZ_BEAT, 1f, 1f, false);
            body.springsSharp();
            body.rig.frameStep = 1;
            body.rig.interp = 1;
            if (taught.add("waltz")) {
                fx.actionBar("&7&oListen to the music box. She cuts on the downbeat.");
            }
        }
        Player p = currentTarget();
        Vector3f tp = p == null ? new Vector3f(pos) : fx.stage(p.getLocation());
        for (int i = 0; i < WALTZ_CUTS.length; i++) {
            int cutAt = WALTZ_CUTS[i] * WALTZ_BEAT + 4;
            int arm = i % 2 == 0 ? SaintBody.R : SaintBody.L;
            if (t >= cutAt - 6 && t < cutAt) {
                float w = window(t, cutAt - 6, cutAt);
                body.poseSweep(arm, -1f);
                faceToward(tp, 0.35f);
                Vector3f step = new Vector3f(tp.x - pos.x, 0, tp.z - pos.z);
                if (step.length() > 3f) {
                    pos.add(step.normalize(0.4f));
                }
                if (w == 0f) {
                    fx.sound(pos, Sound.ITEM_ARMOR_EQUIP_CHAIN, 1.2f, 0.6f);
                }
            }
            if (t >= cutAt && t < cutAt + 5) {
                body.poseSweep(arm, 1f);
                if (t == cutAt) {
                    fx.sound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.8f, 0.6f);
                    fx.sound(pos, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.4f);
                    Vector3f fwd = new Vector3f((float) Math.sin(yaw), 0, (float) Math.cos(yaw));
                    pos.add(fwd.mul(0.9f));
                }
                if (t <= cutAt + 3) {
                    hitNeedles(100, 1.1f, "waltz" + i, 6);
                }
            }
        }
        int spinAt = WALTZ_SPIN * WALTZ_BEAT + 2;
        if (t >= spinAt && t < spinAt + 24) {
            body.posePirouette(1.3f);
            yaw += 0.55f;
            pos.y = lerp(pos.y, SaintBody.STAND + 1.2f * arc(window(t, spinAt, spinAt + 24)), 0.4f);
            if ((t - spinAt) % 3 == 0) {
                hitNeedles(110, 1.2f, "waltzspin", 8);
            }
            if ((t - spinAt) % 6 == 0) {
                fx.sound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.9f);
            }
        }
        clampArena(pos, ARENA);
        if (t >= spinAt + 24) {
            pos.y = lerp(pos.y, SaintBody.STAND - 0.35f, 0.3f);
            body.poseWrong(clock);
        }
        if (t == spinAt + 26) {
            body.haloLit(false);
            music.stop();
        }
        if (t >= spinAt + 34) {
            finish(20);
        }
    }

    /* ================================================================== move: FIST (Act III) */

    /** The Hand balls into a fist and hammers the stage three times, tracking you. */
    private void fist(int t) {
        int each = 22;
        int slam = t % each;
        int n = t / each;
        if (t == 0) {
            handBusy = true;
            handFollow = 0.14f;
            hand.fist();
            handYaw = yaw;
            fx.sound(new Vector3f(handPos), Sound.ENTITY_IRON_GOLEM_DAMAGE, 2f, 0.5f);
        }
        if (n >= 3) {
            hand.open();
            handGoal.set(pos.x, HAND_Y[2], pos.z);
            handFollow = 0.05f;
            if (t >= 3 * each + 12) {
                finish(18);
            }
            return;
        }
        body.poseWrong(clock);
        body.head.target.x = -0.6f;
        if (slam == 0) {
            Player p = currentTarget();
            Vector3f tp = p == null ? new Vector3f() : fx.stage(p.getLocation());
            clampArena(tp, ARENA);
            mA.set(tp.x, 0, tp.z);
            props.add(new SaintProps.Telegraph(fx, mA, 4.4f, 16, false));
            handFollow = 0.18f;
        }
        if (slam < 12) {
            handGoal.set(mA.x, 20f, mA.z);
        } else if (slam < 16) {
            handFollow = 1f;
            handGoal.set(mA.x, lerp(20f, 7.4f, inCubic(window(slam, 12, 16))), mA.z);
        }
        if (slam == 16) {
            slam(new Vector3f(mA), 4.4f, 160, 10f, 10);
            hitstop = 3;
            stageBounce(12f, 0.3f);
        }
        if (slam > 16) {
            handFollow = 0.2f;
            handGoal.set(mA.x, 12f, mA.z);
        }
    }

    /* ================================================================== move: RESTRING (Act III) */

    /** Floor offset across the sweep for each thread, ordered like the fingers (thumb .. pinky). */
    private static final float[] RESTRING_OFFSETS = {4.5f, 0f, -4.5f, -9f, 9f};

    /**
     * The Hand tries to take her back. It comes in low from the edge of the stage and combs
     * across it with five threads hanging to the boards. She skitters out of the way. You
     * might not.
     */
    private void restring(int t) {
        if (t == 0) {
            handBusy = true;
            Vector3f dir = new Vector3f(pos.x, 0, pos.z);
            if (dir.lengthSquared() < 1f) {
                dir.set(1, 0, 0);
            }
            dir.normalize();
            mB.set(dir);
            mA.set(new Vector3f(pos.x, 0, pos.z).fma(-16f, dir));
            mC.set(new Vector3f(pos.x, 0, pos.z).fma(14f, dir));
            // Local +X (the finger fan) runs across the sweep; the palm leads it.
            handYaw = SaintMath.yawToward(-dir.x, -dir.z);
            hand.spread(1.6f);
            for (int f = 0; f < 5; f++) {
                hand.curl(f, 0f);
            }
            handFollow = 0.12f;
            Vector3f side = new Vector3f(-dir.z, 0, dir.x);
            for (int i = 0; i < 5; i++) {
                Vector3f a = new Vector3f(mA).fma(RESTRING_OFFSETS[i], side);
                Vector3f b = new Vector3f(mC).fma(RESTRING_OFFSETS[i], side);
                props.add(new SaintProps.Lane(fx, a, b, 1.3f, 26, false));
            }
            for (int i = 0; i < 5; i++) {
                threads[i].visible = true;
                threads[i].reach = 0f;
                threads[i].taut();
            }
            fx.sound(new Vector3f(mA.x, 18f, mA.z), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.5f);
        }
        Vector3f side = new Vector3f(-mB.z, 0, mB.x);
        float sweep = t < 26 ? 0f : smooth(window(t, 26, 66));
        Vector3f center = SaintMath.lerp(mA, mC, sweep, new Vector3f());
        handGoal.set(center.x, 20f, center.z);
        if (t >= 26 && t < 66) {
            handFollow = 0.5f;
        }
        float reach = t < 26 ? window(t, 4, 24) : t < 66 ? 1f : 1f - window(t, 66, 78);
        for (int i = 0; i < 5; i++) {
            Vector3f floor = new Vector3f(handPos.x, 0.05f, handPos.z).fma(RESTRING_OFFSETS[i], side);
            ThreadLine th = threads[i];
            th.visible = reach > 0.01f;
            th.reach = reach;
            th.sag = 0f;
            th.sagTarget = 0f;
            th.update(hand.fingertip(THREAD_FINGER[i]), floor, 1);
            if (t >= 26 && t < 66) {
                for (Player p : fx.targets()) {
                    Vector3f pp = fx.stage(p.getLocation());
                    if (SaintMath.horizontal(pp, floor) < 0.9f) {
                        if (strike(p, 90, floor, 0.2f, 1.7f, "restring", 20)) {
                            p.playSound(p.getLocation(), Sound.BLOCK_TRIPWIRE_ATTACH, 1.5f, 0.5f);
                        }
                    }
                }
            }
        }
        // She escapes the comb sideways, in two frantic stop-motion hops.
        float along = new Vector3f(pos.x - handPos.x, 0, pos.z - handPos.z).dot(mB);
        if (t >= 26 && t < 66 && along < 5f && along > -2f && mI < 2 && t % 4 == 0) {
            props.add(new SaintProps.Afterimage(fx, body.rig, 10, true));
            float dodge = (mI == 0 ? 1f : 1f) * 2.1f;
            pos.fma(dodge, side);
            clampArena(pos, ARENA);
            fx.sound(pos, Sound.ENTITY_SKELETON_STEP, 1.4f, 0.8f);
            mI++;
        }
        body.poseWrong(clock);
        body.head.target.x = -0.8f;
        if (t >= 80) {
            for (ThreadLine th : threads) {
                th.visible = false;
                th.render(1);
            }
            handBusy = false;
            handFollow = 0.05f;
            finish(20);
        }
    }

    /* ================================================================== move: ENCORE (Act III, once) */

    /**
     * She leaps, hooks two threads from her needle eyes into the Hand's fingertips, lands and
     * pulls with everything she has. The Hand comes down onto the stage, palm flat, fingers
     * splayed across the boards. It stays there. It is solid. It is your cover.
     */
    private void encore(int t) {
        if (t == 0) {
            encoreDone = true;
            handBusy = true;
            music.stop();
            dressing.third.set(true);
            dressing.hunter.set(false);
            mA.set(pos);
            planEncore();
            fx.score(Sound.BLOCK_BELL_USE, 1f, 0.5f);
        }
        if (t < 20) {
            body.poseWrong(clock);
            if (t % 4 == 0) {
                float f = smooth(window(t, 0, 19));
                pos.x = lerp(mA.x, encoreSpot.x, f);
                pos.z = lerp(mA.z, encoreSpot.z, f);
                fx.sound(pos, Sound.ENTITY_SKELETON_STEP, 1.2f, 0.6f);
            }
            handGoal.set(mB.x, 32f, mB.z);
            handFollow = 0.08f;
            dressing.third.aim(pos, 3);
            return;
        }
        if (t < 30) {
            body.springsSharp();
            body.poseLanded();
            pos.y = lerp(pos.y, 2.1f, 0.4f);
            if (t == 22) {
                fx.sound(pos, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1.4f, 0.5f);
            }
            return;
        }
        if (t < 40) {
            pos.y = lerp(2.1f, 11f, outCubic(window(t, 30, 40)));
            body.poseCrucifix();
            body.upper[SaintBody.R].target.z = -2.8f;
            body.upper[SaintBody.L].target.z = 2.8f;
            if (t == 30) {
                fx.sound(pos, Sound.ENTITY_BREEZE_JUMP, 1.6f, 0.6f);
            }
            return;
        }
        if (t == 40) {
            for (int s = 0; s < 2; s++) {
                encoreLines[s] = new ThreadLine(fx, 5, 0.07f, Material.GOLD_BLOCK, WHITE);
                encoreLines[s].reach = 0f;
            }
            fx.sound(pos, Sound.ENTITY_FISHING_BOBBER_THROW, 2f, 0.5f);
            fx.sound(pos, Sound.ITEM_TRIDENT_THROW, 1.4f, 0.6f);
        }
        if (t >= 40 && t < 60) {
            float hook = window(t, 40, 47);
            int[] fingers = {HandBody.INDEX, HandBody.PINKY};
            for (int s = 0; s < 2; s++) {
                encoreLines[s].reach = hook;
                encoreLines[s].slack(t < 52 ? 0.4f : 0f);
                encoreLines[s].update(body.needleBase(s), hand.fingertip(fingers[s]), 1);
            }
            if (t == 47) {
                fx.sound(hand.fingertip(HandBody.MIDDLE), Sound.BLOCK_TRIPWIRE_ATTACH, 2f, 0.5f);
                hand.fist();
            }
            if (t >= 47) {
                pos.y = lerp(11f, 2.1f, inCubic(window(t, 47, 55)));
                body.poseLanded();
            }
            if (t == 55) {
                fx.sound(pos, Sound.ITEM_MACE_SMASH_GROUND, 1.4f, 0.8f);
            }
            return;
        }
        if (t == 60) {
            body.poseDrawBack(SaintBody.R, 1f);
            body.upper[SaintBody.L].target.x = 1.2f;
            fx.sound(pos, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2f, 0.5f);
            fx.sound(pos, Sound.BLOCK_CHAIN_BREAK, 2f, 0.5f);
            fx.sound(pos, Sound.ITEM_CROSSBOW_LOADING_END, 2f, 0.5f);
            for (ThreadLine l : encoreLines) {
                l.pluck(0.5f);
            }
        }
        if (t >= 60 && t < 80) {
            float f = inCubic(window(t, 62, 80));
            handFollow = 1f;
            hand.flex(smooth(window(t, 60, 74)));
            hand.open();
            hand.spread(ENCORE_SPREAD);
            handGoal.set(mB.x, lerp(32f, mB.y, f), mB.z);
            int[] fingers = {HandBody.INDEX, HandBody.PINKY};
            for (int s = 0; s < 2; s++) {
                encoreLines[s].update(body.needleBase(s), hand.fingertip(fingers[s]), 1);
            }
            body.poseDrawBack(SaintBody.R, 1f);
            if (t % 4 == 0) {
                fx.score(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.5f + f * 0.4f);
            }
            return;
        }
        if (t == 80) {
            handPos.set(mB);
            handGoal.set(mB);
            handPinned = true;
            // Settle the Hand into its exact final pose so the solid volume matches what is seen.
            hand.flex(1f);
            hand.open();
            hand.spread(ENCORE_SPREAD);
            hand.rig.rootPos.set(mB);
            hand.rig.yaw = handYaw;
            hand.rig.snapAll();
            hand.rig.solve();
            cacheHandVolumes();
            // She stands exactly between the middle and ring fingertips: the Hand reached for her
            // and closed around nothing. She flinches back from the impact.
            pos.set(encoreSpot.x, pos.y, encoreSpot.z);
            body.spine.kick(-0.35f, 0, 0);
            body.head.kick(-0.5f, 0, 0);
            Vector3f palm = hand.palmUnder();
            for (Player p : fx.targets()) {
                if (insideHand(fx.stage(p.getLocation()).add(0, 0.9f, 0), 0.6f)) {
                    strike(p, 200, new Vector3f(palm.x, 0, palm.z), 1.4f, 0.9f, "encore", 40);
                    p.teleport(fx.at(p.getLocation().getX() > fx.anchor().getX() + 4 ? 12f : -2f, 3.5f, 11f));
                }
            }
            slam(new Vector3f(palm.x, 0, palm.z), 7f, 0, 22f, 26);
            FakeDestruction.spawnDebris(fx.at(hand.fingertip(HandBody.MIDDLE)), instance, 16, 0.4, 0.9, 30,
                    new Material[]{Material.DARK_OAK_PLANKS, Material.SPRUCE_PLANKS, Material.SMOOTH_SANDSTONE});
            fx.score(Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
            fx.score(Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.5f);
            fx.score(Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 0.5f);
            fx.score(Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
            hitstop = 6;
            stageBounce(30f, 0.55f);
            for (ThreadLine l : encoreLines) {
                l.sever(0.5f, 10f, 0.05f, 14);
            }
            placeHandBarriers();
            dressing.parting(1f);
        }
        if (t > 80) {
            body.springs();
            body.poseWrong(clock);
            body.head.target.y = PI;
            faceToward(hand.palmCenter(), 0.1f);
            pos.y = lerp(pos.y, SaintBody.STAND - 0.35f, 0.2f);
        }
        if (t == 96) {
            if (taught.add("cover")) {
                fx.actionBar("&7&oThe Hand lies where she dragged it. It will stop a needle.");
            }
            for (ThreadLine l : encoreLines) {
                if (l != null) {
                    l.remove();
                }
            }
            encoreLines[0] = null;
            encoreLines[1] = null;
        }
        if (t >= 110) {
            finish(14);
        }
    }

    /** Finger fan of the fallen Hand. */
    private static final float ENCORE_SPREAD = 1.4f;
    /** Radius from center at which she makes her stand for the Encore. */
    private static final float ENCORE_RADIUS = 7.5f;
    /** How far back from the fingertips (toward the palm) she stands in the finger gap. */
    private static final float ENCORE_TIP_INSET = 0.3f;
    /** Clearance kept between her (hitbox + skirt) and the Hand's solid volume. */
    private static final float HAND_CLEARANCE = 1.25f;

    /** Where she stands when the Hand lands: between its middle and ring fingertips. */
    private final Vector3f encoreSpot = new Vector3f();

    /** Pinned Hand volume, cached once at impact: inverse box matrix + box size per piece. */
    private final List<Matrix4f> handInverse = new ArrayList<>();
    private final List<Vector3f> handSize = new ArrayList<>();

    /**
     * Lays out the Encore so the Hand lands deliberately around her. A throwaway probe hand (no
     * displays) is posed flat and measured, so the placement stays exact if the Hand's proportions
     * ever change. She takes her stand on her side of the stage; the Hand lies across the stage
     * center with its middle and ring fingers bracketing her.
     */
    private void planEncore() {
        Vector3f out = new Vector3f(pos.x, 0f, pos.z);
        if (out.lengthSquared() < 1f) {
            out.set(0f, 0f, 1f);
        }
        out.normalize();
        encoreSpot.set(out.x * ENCORE_RADIUS, 0f, out.z * ENCORE_RADIUS);

        HandBody probe = new HandBody();
        probe.flex(1f);
        probe.open();
        probe.spread(ENCORE_SPREAD);
        probe.rig.snapAll();
        probe.rig.solve();
        Vector3f middle = probe.fingertip(HandBody.MIDDLE);
        Vector3f ring = probe.fingertip(HandBody.RING);
        // Local (yaw 0) offset from wrist to her spot: centered in the gap, just inside the tips.
        Vector3f local = new Vector3f(middle).add(ring).mul(0.5f);
        float inset = ENCORE_TIP_INSET / Math.max(0.01f, (float) Math.hypot(local.x, local.z));
        local.set(local.x * (1f - inset), 0f, local.z * (1f - inset));

        // Rotate so the wrist lies on the far side of center: the offset must point from the
        // wrist toward her, i.e. along "out".
        float localAngle = (float) Math.atan2(local.x, local.z);
        handYaw = SaintMath.yawToward(out.x, out.z) - localAngle;
        Vector3f world = new Vector3f(local).rotateY(handYaw);
        mB.set(encoreSpot.x - world.x, 1.25f, encoreSpot.z - world.z);
    }

    private void cacheHandVolumes() {
        handInverse.clear();
        handSize.clear();
        for (Skeleton.Piece piece : hand.rig.pieces()) {
            if (!piece.group.equals("palm") && !piece.group.equals("finger")) {
                continue;
            }
            handInverse.add(hand.rig.matrixOf(piece).invert());
            handSize.add(new Vector3f(piece.size));
        }
    }

    /** Live or cached hand volume: (inverse matrix, size) pairs for palm and finger boxes. */
    private List<Matrix4f> handVolumes(List<Vector3f> sizes) {
        if (handPinned && !handInverse.isEmpty()) {
            sizes.addAll(handSize);
            return handInverse;
        }
        List<Matrix4f> live = new ArrayList<>();
        for (Skeleton.Piece piece : hand.rig.pieces()) {
            if (!piece.group.equals("palm") && !piece.group.equals("finger")) {
                continue;
            }
            live.add(hand.rig.matrixOf(piece).invert());
            sizes.add(new Vector3f(piece.size));
        }
        return live;
    }

    /**
     * Once the Hand lies on the stage she treats it as a wall: if any move or step would put her
     * inside it, she is set down at the nearest clear spot along its edge.
     */
    private void keepOutOfHand() {
        if (!handPinned || hiddenBody || handInverse.isEmpty() || !handCollides(pos)) {
            return;
        }
        Vector3f base = new Vector3f(pos);
        for (float r = 0.25f; r <= 12f; r += 0.25f) {
            for (int k = 0; k < 24; k++) {
                float a = k * TAU / 24f;
                Vector3f probe = new Vector3f(base.x + (float) Math.sin(a) * r, base.y, base.z + (float) Math.cos(a) * r);
                if (Math.hypot(probe.x, probe.z) > ARENA) {
                    continue;
                }
                if (!handCollides(probe)) {
                    pos.x = probe.x;
                    pos.z = probe.z;
                    return;
                }
            }
        }
    }

    private boolean handCollides(Vector3f at) {
        return insideHand(new Vector3f(at.x, 0.5f, at.z), HAND_CLEARANCE)
                || insideHand(new Vector3f(at.x, 1.6f, at.z), HAND_CLEARANCE);
    }

    /** The fallen Hand becomes solid: barrier blocks fill its palm and finger volumes. */
    private void placeHandBarriers() {
        hand.rig.solve();
        List<Vector3f> keepClear = new ArrayList<>();
        for (Player p : fx.targets()) {
            keepClear.add(fx.stage(p.getLocation()));
        }
        // Her hitbox must never end up inside a barrier either.
        keepClear.add(new Vector3f(pos.x, 0f, pos.z));
        for (Skeleton.Piece piece : hand.rig.pieces()) {
            if (!piece.group.equals("palm") && !piece.group.equals("finger")) {
                continue;
            }
            Matrix4f m = hand.rig.matrixOf(piece);
            Matrix4f inv = new Matrix4f(m).invert();
            Vector3f lo = new Vector3f(Float.MAX_VALUE);
            Vector3f hi = new Vector3f(-Float.MAX_VALUE);
            for (int c = 0; c < 8; c++) {
                Vector3f corner = m.transformPosition(new Vector3f(c & 1, (c >> 1) & 1, (c >> 2) & 1));
                lo.min(corner);
                hi.max(corner);
            }
            for (int x = (int) Math.floor(lo.x); x <= (int) Math.ceil(hi.x); x++) {
                for (int z = (int) Math.floor(lo.z); z <= (int) Math.ceil(hi.z); z++) {
                    for (int y = Math.max(0, (int) Math.floor(lo.y)); y <= Math.min(4, (int) Math.ceil(hi.y)); y++) {
                        Vector3f cell = new Vector3f(x, y + 0.5f, z);
                        Vector3f local = inv.transformPosition(new Vector3f(cell));
                        if (local.x < -0.05f || local.x > 1.05f || local.y < -0.05f || local.y > 1.05f
                                || local.z < -0.05f || local.z > 1.05f) {
                            continue;
                        }
                        boolean occupied = false;
                        for (Vector3f pp : keepClear) {
                            if (Math.abs(pp.x - x) < 1.0f && Math.abs(pp.z - z) < 1.0f && pp.y < y + 1.5f && pp.y > y - 2f) {
                                occupied = true;
                                break;
                            }
                        }
                        if (!occupied) {
                            stage.barrier(new Vector3f(x, y, z));
                        }
                    }
                }
            }
        }
    }

    private boolean insideHand(Vector3f point, float margin) {
        List<Vector3f> sizes = new ArrayList<>();
        List<Matrix4f> volumes = handVolumes(sizes);
        for (int i = 0; i < volumes.size(); i++) {
            Vector3f size = sizes.get(i);
            Vector3f local = volumes.get(i).transformPosition(new Vector3f(point));
            float mx = margin / Math.max(0.5f, size.x);
            float mz = margin / Math.max(0.5f, size.z);
            if (local.x > -mx && local.x < 1 + mx && local.y > -0.2f && local.y < 1.2f && local.z > -mz && local.z < 1 + mz) {
                return true;
            }
        }
        return false;
    }

    /** Ray from a to b against the pinned Hand. Returns the fraction where it first hits, or 1. */
    private float handBlock(Vector3f a, Vector3f b) {
        if (!handPinned) {
            return 1f;
        }
        float best = 1f;
        List<Vector3f> sizes = new ArrayList<>();
        for (Matrix4f inv : handVolumes(sizes)) {
            Vector3f la = inv.transformPosition(new Vector3f(a));
            Vector3f lb = inv.transformPosition(new Vector3f(b));
            Vector3f d = new Vector3f(lb).sub(la);
            float t0 = 0f;
            float t1 = 1f;
            boolean miss = false;
            for (int axis = 0; axis < 3 && !miss; axis++) {
                float o = la.get(axis);
                float dir = d.get(axis);
                if (Math.abs(dir) < 1e-6f) {
                    if (o < 0f || o > 1f) {
                        miss = true;
                    }
                    continue;
                }
                float ta = (0f - o) / dir;
                float tb = (1f - o) / dir;
                if (ta > tb) {
                    float tmp = ta;
                    ta = tb;
                    tb = tmp;
                }
                t0 = Math.max(t0, ta);
                t1 = Math.min(t1, tb);
                if (t0 > t1) {
                    miss = true;
                }
            }
            if (!miss && t0 < best) {
                best = t0;
            }
        }
        return best;
    }

    /* ================================================================== move: STITCHES (after Encore) */

    /**
     * Thousand Stitches: she draws the stage's light into herself (a golden ring collapsing
     * onto her), then throws four rings of needles outward. Anything behind the fallen Hand is
     * safe. The needles stick where they hit it.
     */
    private void stitches(int t) {
        int waves = 4;
        int gap = 9;
        if (t == 0) {
            props.add(new SaintProps.Shockwave(fx, pos, 15f, 0.6f, 1.4f, 16, Material.GOLD_BLOCK, GOLD));
            fx.score(Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        }
        if (t < 16) {
            body.springsSharp();
            body.poseCrucifix();
            body.upper[SaintBody.R].target.z = -1.2f;
            body.upper[SaintBody.L].target.z = 1.2f;
            pos.y = lerp(pos.y, SaintBody.STAND + 0.3f, 0.15f);
            if (t % 4 == 0) {
                music.single(t / 2, 1.2f, 0.8f);
            }
            body.needle[SaintBody.R].kick((float) Math.sin(t) * 0.05f, 0, 0);
            return;
        }
        int w = t - 16;
        if (w < waves * gap && w % gap == 0) {
            int k = w / gap;
            int count = 16;
            float offset = k * (PI / count);
            Vector3f from = new Vector3f(pos.x, 1.35f, pos.z);
            for (int i = 0; i < count; i++) {
                float a = offset + i * TAU / count;
                Vector3f to = new Vector3f(from).add((float) Math.sin(a) * 24f, 0, (float) Math.cos(a) * 24f);
                float block = handBlock(from, to);
                Vector3f end = SaintMath.lerp(from, to, block, new Vector3f());
                int flight = Math.max(2, Math.round(from.distance(end) / 1.15f));
                bolts.add(props.add(new SaintProps.Bolt(fx, from, end, flight, block < 1f)));
            }
            body.poseSweep(k % 2 == 0 ? SaintBody.R : SaintBody.L, k % 2 == 0 ? 1f : -1f);
            fx.sound(pos, Sound.ENTITY_ARROW_SHOOT, 1.6f, 0.6f);
            fx.sound(pos, Sound.ITEM_TRIDENT_THROW, 1.4f, 1.2f);
            music.single(k * 3, 1f, 0.9f);
        }
        for (SaintProps.Bolt bolt : new ArrayList<>(bolts)) {
            int age = bolt.age();
            if (age > bolt.flight) {
                if (age == bolt.flight + 1 && bolt.to.distance(bolt.from) < 23.5f) {
                    fx.sound(bolt.to, Sound.ENTITY_ARROW_HIT, 1.2f, 0.6f);
                    fx.sound(bolt.to, Sound.BLOCK_WOOD_HIT, 1.2f, 0.6f);
                }
                continue;
            }
            Vector3f at = bolt.positionAt(age);
            for (Player p : fx.targets()) {
                Vector3f pp = fx.stage(p.getLocation());
                if (Math.abs(pp.x - at.x) < 0.8f && Math.abs(pp.z - at.z) < 0.8f && at.y > pp.y - 0.2f && at.y < pp.y + 2f) {
                    strike(p, 85, new Vector3f(bolt.from), 0.6f, 0.2f, "stitch", 6);
                }
            }
        }
        if (w >= waves * gap + 20) {
            body.haloLit(false);
            finish(20);
        }
    }

    /* ================================================================== transitions */

    private void beginTransition() {
        endMove();
        props.clear();
        music.stop();
        move = Move.IDLE;
        act = Act.TRANSITION;
        actTick = 0;
        BossPhase pending = instance.getPendingPhase();
        transitionKind = pending != null && pending.getHealthPercent() < 50 ? 2 : 1;
        int configured = pending != null && pending.getTransition() != null ? pending.getTransition().getDurationTicks() : 0;
        transitionLength = configured > 0 ? configured : (transitionKind == 1 ? T1_AUTHORED : T2_AUTHORED);
        body.rig.frameStep = 1;
        body.rig.interp = 2;
        body.rig.tilt.identity();
        body.restoreVisibility(false);
        hitstop = 0;
        // Whatever the Hand was doing (palm slam, grasp), it lets go for the cinematic.
        hand.flex(0f);
        hand.open();
        handFollow = 0.05f;
        handBusy = false;
        for (int i = 0; i < 5; i++) {
            if (!cut[i]) {
                threads[i].visible = true;
                threads[i].reach = 1f;
            }
        }
    }

    private void tickTransition() {
        if (!instance.isTransitioning()) {
            finishTransition();
            return;
        }
        int authored = transitionKind == 1 ? T1_AUTHORED : T2_AUTHORED;
        int tau = Math.round(actTick * authored / (float) Math.max(1, transitionLength));
        int prevTau = actTick == 0 ? -1 : Math.round((actTick - 1) * authored / (float) Math.max(1, transitionLength));
        actTick++;
        for (int k = prevTau + 1; k <= tau; k++) {
            if (transitionKind == 1) {
                handDescends(k, k == tau);
            } else {
                severance(k, k == tau);
            }
        }
    }

    private void finishTransition() {
        act = Act.FIGHT;
        actTick = 0;
        cooldown = 16;
        phase = transitionKind == 1 ? 2 : 3;
        freezeBody = false;
        if (phase == 2) {
            dressing.parting(1f);
            handGoal.set(pos.x, HAND_Y[1], pos.z);
            for (int i = 0; i < 5; i++) {
                threads[i].visible = true;
                threads[i].reach = 1f;
            }
        } else {
            for (Skeleton.Piece p : body.rig.pieces()) {
                p.free = false;
            }
            body.rig.releaseHolds();
            for (int i = 0; i < 5; i++) {
                cut[i] = true;
                threads[i].visible = false;
                threads[i].render(1);
            }
            body.eyesOpen(true);
            body.cracksGlow(GOLD);
            body.springsSharp();
            body.rig.frameStep = 2;
            body.rig.interp = 1;
            handGoal.set(pos.x, HAND_Y[2], pos.z);
            // Act III: the threads object is reused by Restring; cut[] stops normal updates.
            for (int i = 0; i < 5; i++) {
                cut[i] = true;
            }
        }
        lastHealth = instance.getCombatHealth();
    }

    /**
     * THE HAND DESCENDS. Authored over 140 ticks (rescaled to the YAML duration).
     */
    private void handDescends(int t, boolean render) {
        if (t == 0) {
            tautAll(true);
            fx.score(Sound.ITEM_CROSSBOW_LOADING_END, 1f, 0.5f);
            fx.score(Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 0.5f);
            Player p = fx.nearestTarget(pos);
            if (p != null) {
                dressing.snapGaze(fx.stage(p.getLocation()));
            }
            dressing.sky(18000f, 40f);
            dressing.hunter.set(false);
            mA.set(pos);
            body.springs();
        }
        if (t < 30) {
            float f = smooth(window(t, 0, 30));
            pos.set(lerp(mA.x, 0f, f), lerp(mA.y, 9f, f), lerp(mA.z, 0f, f));
            body.poseHoisted();
        }
        if (t == 20) {
            dressing.parting(1f);
            fx.score(Sound.ENTITY_WARDEN_EMERGE, 0.6f, 0.6f);
        }
        if (t == 40) {
            fx.score(Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
        }
        if (t == 60) {
            fx.score(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.5f);
            fx.title("", "&7&oThe Hand descends.", 10, 40, 20);
        }
        if (t >= 20 && t < 80) {
            handFollow = 0.08f;
            handGoal.set(0f, lerp(HAND_Y[0], HAND_Y[1], smooth(window(t, 20, 80))), 0f);
            handYaw += 0.01f;
            handBusy = true;
            for (int f = 0; f < 5; f++) {
                hand.curl(f, 0.3f + 0.15f * (float) Math.sin(t * 0.1f + f));
            }
        }
        // Each finger flexes in turn; the limb it owns jerks. A bell for each.
        int[] beats = {80, 88, 96, 104, 112};
        for (int i = 0; i < beats.length; i++) {
            if (t == beats[i]) {
                int thread = i;
                hand.curl(THREAD_FINGER[thread], 1f);
                hand.finger[THREAD_FINGER[thread]][0].kick(0.6f, 0, 0);
                threads[thread].pluck(0.5f);
                switch (thread) {
                    case T_RWRIST -> body.upper[SaintBody.R].kick(-0.9f, 0, -0.6f);
                    case T_HEAD -> body.neck.kick(-0.9f, 0, 0);
                    case T_LWRIST -> body.upper[SaintBody.L].kick(-0.9f, 0, 0.6f);
                    case T_LKNEE -> body.thigh[SaintBody.L].kick(-1.1f, 0, 0);
                    default -> body.thigh[SaintBody.R].kick(-1.1f, 0, 0);
                }
                fx.score(Sound.BLOCK_BELL_USE, 1f, 0.62f - i * 0.03f);
                fx.sound(anchor(thread), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.4f, 0.6f);
            }
            if (t == beats[i] + 5) {
                hand.curl(THREAD_FINGER[i], 0.3f);
            }
        }
        if (t >= 80 && t < 116) {
            body.poseHang(clock);
            pos.y = 9f;
        }
        if (t == 116) {
            props.add(new SaintProps.Telegraph(fx, new Vector3f(), 5.2f, 18, false));
            body.springsSharp();
        }
        if (t >= 116 && t < 131) {
            body.poseCrucifix();
            yaw += TAU / 15f;
        }
        if (t == 128) {
            hand.fist();
            fx.sound(new Vector3f(handPos), Sound.ENTITY_IRON_GOLEM_DAMAGE, 2f, 0.5f);
        }
        if (t >= 131 && t < 134) {
            pos.y = lerp(9f, 1.9f, inCubic(window(t, 131, 134)));
            body.poseDive();
        }
        if (t == 134) {
            pos.y = 1.9f;
            body.poseLanded();
            slam(new Vector3f(), 5.2f, 140, 16f, 16);
            for (Player p : fx.targets()) {
                Vector3f pp = fx.stage(p.getLocation());
                float d = SaintMath.horizontal(pp, new Vector3f());
                if (d < 14f && d > 0.05f) {
                    Vector3f away = new Vector3f(pp.x, 0, pp.z).normalize(1.3f * (1f - d / 14f) + 0.3f);
                    p.setVelocity(new Vector(away.x, 0.45, away.z));
                }
            }
            hitstop = 5;
        }
        if (t > 134) {
            handBusy = false;
            hand.open();
            body.springs();
            pos.y = lerp(pos.y, SaintBody.HANG, 0.15f);
            body.poseHang(clock);
        }
    }

    /**
     * SEVERANCE. Authored over 220 ticks (rescaled to the YAML duration).
     */
    private void severance(int t, boolean render) {
        if (t == 0) {
            tautAll(true);
            music.play(9f, 0.94f, 0.8f, false);
            mA.set(pos);
            dressing.hunter.set(false);
            dressing.third.set(true);
            body.springs();
            handBusy = true;
        }
        if (t < 25) {
            float f = smooth(window(t, 0, 25));
            pos.set(lerp(mA.x, 0f, f), lerp(mA.y, 10f, f), lerp(mA.z, 0f, f));
            body.poseHang(clock);
            handGoal.set(0f, HAND_Y[1] + 3f, 0f);
        }
        dressing.third.aim(new Vector3f(pos.x, 0, pos.z), 3);
        if (t >= 25 && t < 135) {
            pos.set(0f, 10f + (float) Math.sin(t * 0.08f) * 0.2f, 0f);
        }
        if (t >= 25 && t < 48) {
            body.poseHang(clock);
            body.neck.spring(0.04f, 0.1f);
            body.neck.target.set(-0.5f, PI, 0f);
            if (t % 6 == 0) {
                fx.sound(body.headTop(), Sound.BLOCK_WOODEN_DOOR_OPEN, 1.4f, 0.5f);
            }
        }
        // Order: both knees, the head, the left wrist (all by the right needle), then the last
        // thread, the right wrist, which she grips and tears.
        int[] order = {T_RKNEE, T_LKNEE, T_HEAD, T_LWRIST, T_RWRIST};
        int[] when = {55, 72, 89, 106, 128};
        for (int i = 0; i < order.length; i++) {
            int c = when[i];
            int thread = order[i];
            if (t >= c - 12 && t < c) {
                if (thread == T_RWRIST) {
                    body.poseDrawBack(SaintBody.R, smooth(window(t, c - 12, c)));
                    threads[thread].pluck(0.2f);
                } else {
                    body.poseCut(SaintBody.R, smooth(window(t, c - 12, c - 2)));
                }
                body.neck.target.set(-0.5f, PI, 0f);
            }
            if (t == c) {
                threads[thread].sever(0.55f, 12f, 0.05f, 14);
                cut[thread] = true;
                int f = THREAD_FINGER[thread];
                hand.finger[f][0].kick(-1.4f, 0, 0);
                hand.finger[f][1].kick(-0.8f, 0, 0);
                handPos.y += 1.2f;
                fx.sound(anchor(thread), Sound.ENTITY_ITEM_BREAK, 1.6f, 1.3f - i * 0.12f);
                fx.sound(anchor(thread), Sound.BLOCK_TRIPWIRE_DETACH, 1.6f, 0.6f);
                music.single(21 - i * 3, 0.9f, 1f);
                fx.dust(anchor(thread), GOLD, 1.2f, 10, 0.3);
                body.rig.solve();
            }
        }
        if (t == 128) {
            music.stop();
        }
        if (t >= 128 && t < 136) {
            float f = inCubic(window(t, 128, 136));
            pos.set(0f, lerp(10f, 0.8f, f), 0f);
            body.springsLimp();
            body.poseLimp();
        }
        if (t == 136) {
            shatterOnFloor();
        }
        if (t > 136 && t < 176) {
            // Silence. Only the Hand, twitching.
            if (t % 9 == 0) {
                int f = ThreadLocalRandom.current().nextInt(5);
                hand.finger[f][0].kick(0.3f, 0, 0);
            }
            if (t == 150 || t == 160) {
                dressing.third.set(false);
            }
            if (t == 152 || t == 163) {
                dressing.third.set(true);
            }
        }
        if (t >= 176 && t < 206) {
            if (t == 176) {
                freezeBody = false;
                unstrungBody = true;
                body.springsSharp();
                body.poseWrong(clock);
                body.rig.snapAll();
                pos.set(0f, SaintBody.STAND - 0.35f, 0f);
                body.rig.rootPos.set(pos);
                body.rig.solve();
            }
            body.poseWrong(clock);
            pos.set(0f, SaintBody.STAND - 0.35f, 0f);
            List<Skeleton.Piece> pieces = body.rig.pieces();
            int per = Math.max(1, pieces.size() / 28);
            int start = (t - 176) * per;
            for (int i = start; i < Math.min(pieces.size(), start + per); i++) {
                Skeleton.Piece p = pieces.get(i);
                p.free = false;
                body.rig.push(p, 9, true);
                holdPiece(p, 9);
            }
            if (t % 2 == 0) {
                fx.sound(pos, Sound.BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON, 1.3f, 0.5f + (float) Math.random() * 0.4f);
                fx.sound(pos, Sound.ENTITY_SKELETON_STEP, 0.8f, 0.6f);
            }
        }
        if (t == 206) {
            for (Skeleton.Piece p : body.rig.pieces()) {
                p.free = false;
            }
            body.eyesOpen(true);
            body.crackTo(Math.max(0.6f, 1f - (float) instance.healthPercent() / 100f));
            body.cracksGlow(GOLD);
            hand.fist();
            fx.score(Sound.ENTITY_GHAST_SCREAM, 0.5f, 1.6f);
            fx.score(Sound.ENTITY_VEX_CHARGE, 0.9f, 0.5f);
            fx.score(Sound.ENTITY_ALLAY_DEATH, 0.8f, 0.5f);
            fx.title("", "&4&oUnstrung.", 4, 36, 16);
            dressing.dropHeads();
            dressing.third.set(false);
            body.rig.frameStep = 2;
            body.rig.interp = 1;
        }
        if (t > 206) {
            body.poseWrong(clock);
            handGoal.set(0f, HAND_Y[2], 0f);
        }
    }

    /** Break the doll across the boards. Pieces arc out and settle flat, then lie still. */
    private void shatterOnFloor() {
        body.rig.solve();
        body.rig.freeAll();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Skeleton.Piece p : body.rig.pieces()) {
            Matrix4f m = body.rig.matrixOf(p);
            Vector3f c = m.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
            float a = (float) r.nextDouble(TAU);
            float d = (float) r.nextDouble(0.8, 4.2);
            Vector3f s = new Vector3f(p.size).mul(SaintBody.SCALE);
            float lie = Math.min(s.x, Math.min(s.y, s.z)) * 0.5f + 0.02f;
            Vector3f land = new Vector3f(c.x + (float) Math.sin(a) * d, lie, c.z + (float) Math.cos(a) * d);
            p.freeMatrix.set(new Matrix4f().translation(land).rotateY((float) r.nextDouble(TAU))
                    .rotateX(s.y > s.x && s.y > s.z ? HALF_PI : 0f)
                    .scale(s).translate(-0.5f, -0.5f, -0.5f));
            body.rig.push(p, 6 + r.nextInt(6), true);
            holdPiece(p, 40);
        }
        freezeBody = true;
        fx.score(Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
        fx.score(Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
        fx.score(Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.6f);
        fx.score(Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 0.8f, 0.8f);
        fx.dust(new Vector3f(0, 0.5f, 0), WHITE, 2f, 40, 1.5);
        fx.dust(new Vector3f(0, 0.5f, 0), GOLD, 1.2f, 20, 1.2);
        for (Player p : fx.targets()) {
            Vector3f pp = fx.stage(p.getLocation());
            if (SaintMath.horizontal(pp, new Vector3f()) < 5f) {
                strike(p, 60, new Vector3f(), 1.1f, 0.4f, "shatter", 40);
            }
        }
    }

    /* ================================================================== death */

    private boolean tickDeath() {
        int t = actTick++;
        if (t >= DEATH_TICKS) {
            return true;
        }
        if (t == 0) {
            props.clear();
            music.stop();
            freezeBody = true;
            body.crackTo(1f);
            body.cracksGlow(WHITE);
            body.rig.frameStep = 1;
            body.rig.interp = 2;
            for (Skeleton.Piece p : body.rig.pieces()) {
                p.free = false;
            }
            body.restoreVisibility(true);
            body.eyesOpen(true);
            body.rig.releaseHolds();
            fx.score(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
            fx.score(Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.5f);
            dressing.hunter.set(false);
            dressing.third.set(true);
            dressing.third.aim(new Vector3f(pos.x, 0, pos.z), 6);
            for (ThreadLine th : threads) {
                if (th != null) {
                    th.visible = false;
                    th.render(4);
                }
            }
        }
        if (t == 16) {
            freezeBody = false;
            body.springs();
            body.rig.frameStep = 1;
        }
        Player nearest = fx.nearestTarget(pos);
        if (nearest == null) {
            for (Player p : fx.audience()) {
                nearest = p;
                break;
            }
        }
        if (t > 16 && t < 130) {
            pos.y = lerp(pos.y, SaintBody.STAND, 0.06f);
            if (nearest != null) {
                Vector3f np = fx.stage(nearest.getLocation());
                yaw = SaintMath.approachAngle(yaw, SaintMath.yawToward(np.x - pos.x, np.z - pos.z), 0.04f);
            }
            if (t < 44) {
                body.poseHang(clock);
                body.neck.target.y = 0f;
            } else {
                float depth = smooth(window(t, 44, 84)) * (1f - smooth(window(t, 104, 128)) * 0.3f);
                body.poseBow(depth);
            }
        }
        if (t == 44) {
            music.play(11f, 1f, 0.9f, false);
            music.windDown();
        }
        // The Hand frees itself and reaches for her.
        if (t == 50) {
            handPinned = false;
            handInverse.clear();
            handSize.clear();
            stage.clearBarriers();
            hand.flex(0f);
            hand.open();
            handFollow = 0.03f;
            fx.sound(new Vector3f(handPos), Sound.BLOCK_GRAVEL_BREAK, 2f, 0.5f);
        }
        if (t >= 50 && t < 130) {
            handBusy = true;
            handYaw = SaintMath.approachAngle(handYaw, yaw + PI, 0.02f);
            float reach = smooth(window(t, 90, 130));
            handGoal.set(pos.x, lerp(24f, pos.y + HandBody.REACH - 1.5f, reach), pos.z);
            for (int f = 0; f < 5; f++) {
                hand.curl(f, 0.15f + reach * 0.45f);
            }
            if (!handPinned && handPos.y < 1.5f) {
                handPos.y = lerp(handPos.y, 3f, 0.05f);
            }
        }
        if (t == 130) {
            deathShatter();
        }
        if (t > 130 && t < 146) {
            for (int f = 0; f < 5; f++) {
                hand.curl(f, lerp(0.6f, 1f, window(t, 130, 146)));
            }
        }
        if (t == 150) {
            dressing.sky(23600f, 45f);
            dressing.parting(0f);
            ascendFragments();
            fx.score(Sound.BLOCK_BELL_USE, 1f, 0.5f);
        }
        if (t > 150 && t < 240) {
            handFollow = 0.01f;
            handGoal.set(pos.x, 70f, pos.z);
            if (t % 3 == 0) {
                fx.particle(Particle.WHITE_ASH, new Vector3f(pos.x, pos.y + 2f, pos.z), 12, 2.5, 0.02);
                fx.particle(Particle.END_ROD, new Vector3f(pos.x, pos.y + 3f, pos.z), 2, 1.5, 0.02);
            }
        }
        if (t == 180) {
            // The audience gets its heads back for the curtain call.
            dressing.restoreHeads(new Vector3f(pos.x, 1f, pos.z), 24);
            fx.score(Sound.BLOCK_BONE_BLOCK_PLACE, 1f, 0.7f);
        }
        if (t >= 200 && t < 256) {
            float swell = arc(window(t, 200, 256));
            if (t % 2 == 0) {
                dressing.applause(0.3f + swell);
            }
            if (t % 10 == 0) {
                dressing.nod(0.4f, 4);
            } else if (t % 10 == 5) {
                dressing.nod(0f, 4);
            }
        }
        if (t == 244) {
            fx.title("&f&oCurtain.", "&7Seraphine, the Hanging Saint", 20, 50, 30);
        }
        if (t == 252) {
            props.add(new FallingThread(fx, new Vector3f(pos.x, 0, pos.z)));
            fx.sound(new Vector3f(pos.x, 1, pos.z), Sound.BLOCK_TRIPWIRE_DETACH, 1f, 0.5f);
        }
        if (t == 272) {
            music.single(21, 0.5f, 1f);
        }
        return false;
    }

    private void deathShatter() {
        body.rig.solve();
        body.rig.freeAll();
        freezeBody = true;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector3f core = new Vector3f(pos);
        for (Skeleton.Piece p : body.rig.pieces()) {
            Matrix4f m = body.rig.matrixOf(p);
            Vector3f c = m.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
            Vector3f out = new Vector3f(c).sub(core);
            if (out.lengthSquared() < 0.01f) {
                out.set((float) r.nextGaussian(), 0.5f, (float) r.nextGaussian());
            }
            out.normalize((float) r.nextDouble(1.8, 3.6)).add(0, (float) r.nextDouble(0.6, 2.0), 0);
            Vector3f s = new Vector3f(p.size).mul(SaintBody.SCALE);
            p.freeMatrix.set(new Matrix4f().translation(new Vector3f(c).add(out))
                    .rotateXYZ((float) r.nextDouble(-1.6, 1.6), (float) r.nextDouble(TAU), (float) r.nextDouble(-1.6, 1.6))
                    .scale(s).translate(-0.5f, -0.5f, -0.5f));
            body.rig.push(p, 10, true);
        }
        fx.score(Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
        fx.score(Sound.BLOCK_GLASS_BREAK, 1f, 0.7f);
        fx.score(Sound.BLOCK_GLASS_BREAK, 1f, 0.95f);
        fx.score(Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.5f);
        fx.dust(new Vector3f(pos), GOLD, 2f, 60, 1.6);
        fx.dust(new Vector3f(pos), WHITE, 1.5f, 40, 1.8);
    }

    private void ascendFragments() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Skeleton.Piece> pieces = body.rig.pieces();
        for (int i = 0; i < pieces.size(); i++) {
            Skeleton.Piece p = pieces.get(i);
            Vector3f c = p.freeMatrix.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
            Vector3f up = new Vector3f(c).add((float) r.nextGaussian() * 1.5f, (float) r.nextDouble(12, 22), (float) r.nextGaussian() * 1.5f);
            p.freeMatrix.set(new Matrix4f().translation(up).rotateY((float) r.nextDouble(TAU)).scale(0.001f));
            body.rig.push(p, 60 + (i % 20) * 2, true);
        }
    }

    /** One golden thread drifts down onto the empty stage and coils. */
    private static final class FallingThread implements SaintProps.Prop {
        private final ThreadLine line;
        private final Vector3f at;
        private int age;

        FallingThread(SaintFx fx, Vector3f at) {
            this.at = new Vector3f(at);
            this.line = new ThreadLine(fx, 6, 0.05f, Material.GOLD_BLOCK, ThreadLine.GOLD);
            line.update(new Vector3f(at).add(0, 34f, 0), new Vector3f(at).add(0.4f, 26f, 0.2f), 0);
        }

        @Override
        public boolean tick() {
            age++;
            float f = outCubic(window(age, 0, 34));
            Vector3f top = new Vector3f(at).add((float) Math.sin(age * 0.2f) * 0.6f * (1 - f), lerp(34f, 0.06f, f), 0);
            Vector3f bottom = new Vector3f(at).add(1.6f * f, lerp(26f, 0.05f, f), 0.9f * f);
            line.slack(2.5f * (1 - f));
            line.update(top, bottom, 2);
            return age < 120;
        }

        @Override
        public void remove() {
            line.remove();
        }
    }

    /* ================================================================== reactions */

    /** Porcelain hit feedback: a bright tink, a flinch away from the attacker, new seams. */
    private void hitReactions() {
        double hp = instance.getCombatHealth();
        if (hp < lastHealth - 0.01 && clock - lastHitReact >= 3 && !hiddenBody) {
            lastHitReact = clock;
            Player attacker = fx.nearestTarget(pos);
            Vector3f from = attacker == null ? new Vector3f(0, 0, 1) : fx.stage(attacker.getLocation());
            Vector3f away = new Vector3f(pos.x - from.x, 0, pos.z - from.z);
            if (away.lengthSquared() > 1e-4f) {
                away.normalize().rotateY(-yaw);
                body.chest.kick(away.z * 0.12f, 0, -away.x * 0.12f);
                body.head.kick(away.z * 0.16f, 0, -away.x * 0.16f);
                body.spine.kick(away.z * 0.06f, 0, 0);
            }
            Vector3f chest = body.chestPoint();
            fx.sound(chest, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.4f, 1.3f + (float) Math.random() * 0.5f);
            fx.sound(chest, Sound.BLOCK_GLASS_HIT, 1f, 1.6f);
            fx.dust(chest, WHITE, 0.8f, 5, 0.3);
            if (body.crackTo(1f - (float) (hp / Math.max(1.0, instance.getCombatMaxHealth())))) {
                fx.sound(chest, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.6f, 0.8f);
                fx.dust(chest, GOLD, 1.4f, 18, 0.5);
                if (phase == 3) {
                    body.cracksGlow(GOLD);
                }
            }
        }
        lastHealth = hp;
    }

    /* ================================================================== combat helpers */

    private Player currentTarget() {
        if (target != null) {
            Player p = Bukkit.getPlayer(target);
            if (p != null && fx.targets().contains(p)) {
                return p;
            }
        }
        Player p = fx.nearestTarget(pos);
        target = p == null ? null : p.getUniqueId();
        return p;
    }

    private void faceToward(Vector3f goal, float step) {
        float want = SaintMath.yawToward(goal.x - pos.x, goal.z - pos.z);
        yaw = SaintMath.approachAngle(yaw, want, step);
    }

    private static void clampArena(Vector3f v, float r) {
        float d = (float) Math.sqrt(v.x * v.x + v.z * v.z);
        if (d > r) {
            v.x *= r / d;
            v.z *= r / d;
        }
    }

    private void tautAll(boolean twang) {
        for (int i = 0; i < 5; i++) {
            if (threads[i] != null && !cut[i]) {
                threads[i].taut();
                threads[i].pluck(0.2f);
            }
        }
        if (twang) {
            fx.sound(pos, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.4f, 0.5f);
            fx.sound(pos, Sound.ITEM_CROSSBOW_LOADING_END, 1.2f, 0.6f);
            fx.sound(new Vector3f(pos.x, pos.y + 6f, pos.z), Sound.BLOCK_NOTE_BLOCK_GUITAR, 1.2f, 0.5f);
        }
    }

    private boolean strike(Player p, double power, Vector3f from, float knock, float lift, String key, int gateTicks) {
        String k = p.getUniqueId() + "|" + key;
        Integer last = gate.get(k);
        if (last != null && clock - last < gateTicks) {
            return false;
        }
        gate.put(k, clock);
        if (gate.size() > 256) {
            gate.entrySet().removeIf(e -> clock - e.getValue() > 200);
        }
        if (power > 0) {
            BossHits.hurt(p, instance.getEntity(), power);
        }
        Vector3f pp = fx.stage(p.getLocation());
        Vector3f dir = new Vector3f(pp.x - from.x, 0, pp.z - from.z);
        if (dir.lengthSquared() < 1e-3f) {
            dir.set((float) Math.sin(yaw), 0, (float) Math.cos(yaw));
        }
        dir.normalize(knock);
        p.setVelocity(new Vector(dir.x, lift, dir.z));
        Location l = p.getLocation();
        p.getWorld().playSound(l, Sound.ITEM_TRIDENT_HIT, 1f, 0.7f);
        p.getWorld().playSound(l, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.5f);
        fx.dust(new Vector3f(pp).add(0, 1f, 0), GOLD, 1.2f, 8, 0.3);
        return true;
    }

    private void hitSphere(Vector3f center, float radius, double power, Vector3f from, float knock, float lift, String key, int gateTicks) {
        for (Player p : fx.targets()) {
            Vector3f pp = fx.stage(p.getLocation()).add(0, 0.9f, 0);
            if (pp.distance(center) <= radius + 0.4f) {
                strike(p, power, from, knock, lift, key, gateTicks);
            }
        }
    }

    private void hitSegment(Vector3f a, Vector3f b, float radius, double power, float knock, float lift, String key, int gateTicks) {
        for (Player p : fx.targets()) {
            Vector3f pp = fx.stage(p.getLocation()).add(0, 0.9f, 0);
            if (SaintMath.segmentDistance(pp, a, b) <= radius) {
                strike(p, power, a, knock, lift, key, gateTicks);
            }
        }
    }

    /** Both needles, full length, as damage capsules. */
    private void hitNeedles(double power, float radius, String key, int gateTicks) {
        for (int s = 0; s < 2; s++) {
            hitSegment(body.needleBase(s), body.needleTip(s), radius, power, 0.9f, 0.35f, key, gateTicks);
        }
    }

    /**
     * A heavy landing: damage circle, shockwave ring, splintered boards (non-placing debris),
     * layered impact sound, and the stage bouncing under everyone's feet.
     */
    private void slam(Vector3f at, float radius, double power, float shock, int debris) {
        Vector3f ground = new Vector3f(at.x, 0f, at.z);
        if (power > 0) {
            hitSphere(new Vector3f(ground).add(0, 0.9f, 0), radius, power, ground, 1.2f, 0.55f, "slam" + clock, 1);
        }
        props.add(new SaintProps.Shockwave(fx, ground, 1f, shock, 1.1f, 13, Material.WHITE_STAINED_GLASS, null));
        props.add(new SaintProps.Shockwave(fx, ground, 0.5f, shock * 0.6f, 0.5f, 9, Material.GOLD_BLOCK, GOLD));
        FakeDestruction.spawnDebris(fx.at(ground), instance, debris, 0.42, 0.85, 30,
                new Material[]{Material.DARK_OAK_PLANKS, Material.SPRUCE_PLANKS, Material.POLISHED_BLACKSTONE});
        fx.sound(ground, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.6f);
        fx.sound(ground, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        fx.sound(ground, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.5f);
        fx.sound(ground, Sound.BLOCK_WOOD_BREAK, 2f, 0.5f);
        fx.particle(Particle.EXPLOSION, new Vector3f(ground).add(0, 0.3f, 0), 2, 0.4, 0);
        fx.world().spawnParticle(Particle.BLOCK, fx.at(new Vector3f(ground).add(0, 0.2f, 0)), 60, radius * 0.4, 0.2, radius * 0.4, 0,
                Material.DARK_OAK_PLANKS.createBlockData());
        stageBounce(radius * 2.5f, 0.28f);
    }

    /** Everyone standing near an impact is jolted off the boards for a moment. */
    private void stageBounce(float radius, float lift) {
        for (Player p : fx.targets()) {
            if (!p.isOnGround()) {
                continue;
            }
            Vector3f pp = fx.stage(p.getLocation());
            if (SaintMath.horizontal(pp, pos) < radius) {
                Vector v = p.getVelocity();
                p.setVelocity(new Vector(v.getX(), Math.max(v.getY(), lift), v.getZ()));
            }
        }
    }

    /** Nobody leaves the theatre during the show: a golden thread hauls fallers back. */
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
            if (dx * dx + dz * dz > 48 * 48 || dy > -7 || dy < -90) {
                continue;
            }
            double a = Math.atan2(dx, dz);
            Location to = c.clone().add(Math.sin(a) * 11, 0.2, Math.cos(a) * 11);
            to.setDirection(c.toVector().subtract(to.toVector()).setY(0));
            Vector3f from = fx.stage(l);
            p.teleport(to);
            p.setFallDistance(0f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false, false));
            BossHits.hurt(p, instance.getEntity(), 25);
            props.add(new ThreadFlash(fx, from, fx.stage(to)));
            p.playSound(to, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.4f, 0.5f);
            p.playSound(to, Sound.BLOCK_TRIPWIRE_ATTACH, 1.4f, 0.6f);
            if (taught.add("rescue-" + p.getUniqueId())) {
                p.sendMessage(de.aetherion.bossengine.util.TextUtil.component(
                        "&7&oA golden thread catches you and hauls you back to your seat. The play is not over."));
            }
        }
    }

    /** A thread that flashes from the sky to a rescued player and fades. */
    private static final class ThreadFlash implements SaintProps.Prop {
        private final ThreadLine line;
        private final Vector3f to;
        private int age;

        ThreadFlash(SaintFx fx, Vector3f from, Vector3f to) {
            this.to = new Vector3f(to).add(0, 1.2f, 0);
            line = new ThreadLine(fx, 4, 0.06f, Material.GOLD_BLOCK, ThreadLine.GOLD);
            line.update(new Vector3f(to.x, 40f, to.z), this.to, 0);
        }

        @Override
        public boolean tick() {
            age++;
            line.reach = 1f - window(age, 6, 14);
            line.update(new Vector3f(to.x, 40f, to.z), to, 1);
            return age < 15;
        }

        @Override
        public void remove() {
            line.remove();
        }
    }
}
