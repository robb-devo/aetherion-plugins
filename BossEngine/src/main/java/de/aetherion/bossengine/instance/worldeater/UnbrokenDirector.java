package de.aetherion.bossengine.instance.worldeater;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.fx.FakeDestruction;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.BossPhase;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static de.aetherion.bossengine.instance.worldeater.WeMath.PI;
import static de.aetherion.bossengine.instance.worldeater.WeMath.TAU;
import static de.aetherion.bossengine.instance.worldeater.WeMath.inCubic;
import static de.aetherion.bossengine.instance.worldeater.WeMath.lerp;
import static de.aetherion.bossengine.instance.worldeater.WeMath.outCubic;
import static de.aetherion.bossengine.instance.worldeater.WeMath.smooth;
import static de.aetherion.bossengine.instance.worldeater.WeMath.window;

/**
 * THE UNBROKEN, keeper of the gate of the Last Seed.
 *
 * <p>Every world has a floor: the layer of bedrock that nothing breaks, the only thing between a
 * world and the void beneath it. When the World Eater came for the last world, its floor stood up,
 * chained itself to the gate and knelt there. It does not know what you are. Anything that walks
 * up to the gate is hunger, to it.
 *
 * <p><b>Language.</b> Heavy, slow, inevitable. It never jumps and never hurries. Its tells are the
 * floor itself: cracks racing ahead of a blow, tiles turning to magma where the ground is about to
 * rise. Bedrock takes almost nothing from your blades. It only cracks when it strains: after a
 * Hammerfall its forearms split, after Strata its chest shears, and while it presses the Weight it
 * is wide open. The violet in the cracks is the void it holds out.
 *
 * <p><b>Moves.</b> Hammerfall (both fists; it can reach a long way). Fault Line (a fist dragged
 * through the floor; stone erupts along the line). Keystone (a slab of floor torn out and thrown).
 * Strata (the plaza splits into wedges that rise in two waves: stand where the floor stays dark).
 * Weight, from half health (it kneels and the edge of the plaza closes in: get close to it).
 *
 * <p><b>Half health.</b> A Hammerfall too hard for its own arm: the left arm shatters.
 *
 * <p><b>Death is the gate.</b> It walks back to its place and kneels. "It can see us now." The
 * floor of the world breaks under it and it falls. Its chains go taut and tear the gate's leaves
 * out of the wall: they topple onto the plaza, are dragged to the crater and follow it down.
 * Daylight comes through the open gate. Far below, something vast answers.
 */
public final class UnbrokenDirector {

    public static final String ID = WorldEaterSite.UNBROKEN_ID;

    private enum Act { NONE, WAKING, FIGHT, BREAKING, DYING, DONE }

    private enum Move { IDLE, HAMMER, FAULT, KEYSTONE, STRATA, WEIGHT }

    private static final int WAKE_TICKS = 160;
    private static final int BREAK_AUTHORED = 120;
    private static final int DEATH_TICKS = 300;
    private static final float CHAIN_LENGTH = 24f;
    private static final float GATE_REACH = 22f;
    /** Stage-space chain fastenings on the door leaves (left, right). */
    private static final Vector3f[] HANDLES = {new Vector3f(-3f, 6.5f, 16.0f), new Vector3f(3f, 6.5f, 16.0f)};

    private final BossInstance instance;

    private WeFx fx;
    private WeProps props;
    private FakeBlocks paint;
    private UnbrokenBody body;
    private GateProps.ChainLine[] chains;
    private GateProps.DoorLeaf[] leaves;
    private final List<Crust> crust = new ArrayList<>();
    private boolean site;
    private Senses ownSenses;

    private Act act = Act.NONE;
    private Move move = Move.IDLE;
    private Move lastMove = Move.IDLE;
    private int actTick;
    private int moveTick;
    private int cooldown = 30;
    private int phase = 1;
    private int clock;
    private int hitstop;
    private boolean freezeBody;
    private boolean deathFinished;
    private int transitionLength = BREAK_AUTHORED;

    private final Vector3f pos = new Vector3f();
    private float yaw = PI;
    private float walkPhase;
    private float arenaRadius = 12.5f;

    private int exposeTicks;
    private double weightDamage;
    private double lastHealth;
    private int lastHitReact;
    private UUID target;
    private final Map<String, Integer> gate = new HashMap<>();
    private final Set<String> taught = new HashSet<>();

    /* move scratch */
    private final Vector3f mA = new Vector3f();
    private final Vector3f mB = new Vector3f();
    private final Vector3f mC = new Vector3f();
    private float mF;
    private int mI;
    private int mJ;
    private int side = UnbrokenBody.R;
    private BlockDisplay slab;
    private final List<int[]> ripped = new ArrayList<>();
    private int ripRestoreAt = -1;
    private float weightRing;

    public UnbrokenDirector(BossInstance instance) {
        this.instance = instance;
    }

    /* ================================================================== engine hooks */

    public boolean isMine() {
        return instance.getTemplate() != null && ID.equalsIgnoreCase(instance.getTemplate().getId());
    }

    public boolean ownsBody() {
        return isMine() && act != Act.NONE;
    }

    public boolean ownsTransition() {
        return isMine();
    }

    public boolean isDying() {
        return isMine() && act == Act.DYING;
    }

    public boolean blocksDamage() {
        return isMine() && (act == Act.WAKING || act == Act.DYING || act == Act.BREAKING);
    }

    /** Full damage in the fight: no hidden damage limit on the crust. */
    public double scaleIncoming(double amount) {
        return amount;
    }

    public void onDamaged(double amount) {
        if (!isMine()) {
            return;
        }
        if (move == Move.WEIGHT) {
            weightDamage += amount;
        }
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
            // Same fight, new body (chunk reload): keep the show running.
            placeHitbox();
            return;
        }
        WorldEaterSite s = WorldEaterSite.get();
        site = s != null && s.atSite(instance.getSpawnLocation(), false);
        Location anchor = site ? SiteLayout.plazaAnchor(instance.getSpawnLocation().getWorld()) : instance.getSpawnLocation().clone();
        fx = new WeFx(anchor, instance.getKeys(), instance.getInstanceId())
                .audience(96.0)
                .combat(site ? 24.0 : Math.max(18.0, instance.getConditions().getLeashRadius()), 12.0, 30.0);
        props = new WeProps();
        paint = new FakeBlocks(fx.world(), fx::audience);
        arenaRadius = site ? 12.5f : (float) Math.max(10.0, instance.getConditions().getLeashRadius() * 0.7);
        body = new UnbrokenBody();
        lastHealth = instance.getCombatHealth();
        phase = phaseFromTemplate();
        pos.set(0f, site ? UnbrokenBody.KNEEL : -UnbrokenBody.STAND, 0f);
        yaw = site ? UnbrokenBody.facingNorth() : faceNearest();
        body.rig.rootPos.set(pos);
        body.rig.yaw = yaw;
        body.poseKneel(0f);
        body.rig.snapAll();
        body.rig.spawn(fx);
        if (site) {
            spawnCrust();
            spawnChains();
            s.adoptGuardian(instance);
        }
        act = Act.WAKING;
        actTick = 0;
        placeHitbox();
    }

    public boolean beginDeath() {
        if (!isMine() || act == Act.DYING || act == Act.DONE || fx == null) {
            return false;
        }
        endMove();
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

    /** Remove everything this fight spawned; the statue kneels again if the fight never ended. */
    public void clear() {
        if (fx == null) {
            return;
        }
        endMove();
        props.clear();
        paint.clear();
        clearCrust();
        if (chains != null) {
            for (GateProps.ChainLine c : chains) {
                c.remove();
            }
            chains = null;
        }
        if (leaves != null) {
            for (GateProps.DoorLeaf l : leaves) {
                if (l != null) {
                    l.remove();
                }
            }
            leaves = null;
        }
        if (body != null) {
            body.rig.remove(fx);
        }
        restoreRipped();
        if (site && !deathFinished) {
            WorldEaterSite s = WorldEaterSite.get();
            SiteTerrain t = s == null ? null : s.terrain();
            if (t != null) {
                t.restoreCells(SiteLayout.get().statueCells());
            }
        }
        if (ownSenses != null) {
            ownSenses.close();
            ownSenses = null;
        }
        fx.clear();
        fx = null;
        body = null;
        act = Act.NONE;
    }

    /** @return true when the death cinematic finished and loot should be paid */
    public boolean tick() {
        if (!isMine() || fx == null) {
            return false;
        }
        clock++;
        LivingEntity entity = instance.getEntity();
        if (entity != null && entity.isValid()) {
            maintainBody(entity);
        }
        repairIfUnloaded();
        boolean finished = false;
        switch (act) {
            case WAKING -> tickWake();
            case FIGHT -> {
                if (instance.isTransitioning()) {
                    beginBreak();
                    tickBreak();
                } else {
                    tickFight();
                }
            }
            case BREAKING -> tickBreak();
            case DYING -> finished = tickDeath();
            default -> {
            }
        }
        if (act == Act.DONE || finished) {
            deathFinished = true;
            act = Act.DONE;
            return true;
        }
        animate();
        props.tick();
        paint.tick();
        tickCrust();
        if (ownSenses != null) {
            ownSenses.tick();
        }
        if (ripRestoreAt >= 0 && clock >= ripRestoreAt) {
            restoreRipped();
        }
        placeHitbox();
        return false;
    }

    /* ================================================================== body */

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
        entity.setFireTicks(0);
        entity.setFallDistance(0f);
    }

    private int phaseFromTemplate() {
        BossPhase current = instance.getCurrentPhase();
        return current == null || current.getHealthPercent() > 50.5 ? 1 : 2;
    }

    private void placeHitbox() {
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || fx == null) {
            return;
        }
        float feet = act == Act.DYING ? Math.min(0f, pos.y - UnbrokenBody.STAND) : 0.02f;
        Location at = fx.at(pos.x, feet, pos.z);
        at.setYaw((float) Math.toDegrees(-yaw));
        at.setPitch(0f);
        Location current = entity.getLocation();
        if (current.getWorld() != at.getWorld() || current.distanceSquared(at) > 0.0004) {
            instance.runInternalTeleport(() -> entity.teleport(at));
        }
        entity.setVelocity(new Vector());
    }

    private void repairIfUnloaded() {
        if (clock % 20 != 0 || body == null) {
            return;
        }
        boolean broken = !body.rig.intact();
        if (chains != null) {
            for (GateProps.ChainLine c : chains) {
                broken |= !c.intact();
            }
        }
        if (!broken) {
            return;
        }
        body.rig.remove(fx);
        body.rig.spawn(fx);
        if (chains != null) {
            for (GateProps.ChainLine c : chains) {
                c.remove();
            }
            spawnChains();
        }
    }

    private void animate() {
        body.rig.rootPos.set(pos);
        body.rig.yaw = yaw;
        if (hitstop > 0) {
            hitstop--;
        }
        body.rig.frozen = hitstop > 0 || freezeBody;
        body.rig.step(1f);
        body.rig.render();
        if (chains != null) {
            for (int s = 0; s < 2; s++) {
                Vector3f to = leaves != null && leaves[s] != null ? leaves[s].handle() : HANDLES[s];
                chains[s].update(body.mountPoint(s), to, 2);
            }
        }
        if (exposeTicks > 0) {
            exposeTicks--;
            if (exposeTicks == 0) {
                body.exposeGlow(false);
                body.armCrack(UnbrokenBody.R, false);
                body.armCrack(UnbrokenBody.L, false);
            }
        }
    }

    private void expose(int ticks, boolean arms) {
        boolean was = exposeTicks > 0;
        exposeTicks = Math.max(exposeTicks, ticks);
        body.exposeGlow(true);
        if (arms) {
            body.armCrack(UnbrokenBody.R, true);
            body.armCrack(UnbrokenBody.L, true);
        }
        if (!was) {
            fx.sound(body.chestPoint(), Sound.BLOCK_DEEPSLATE_BREAK, 2f, 0.5f);
            fx.sound(body.chestPoint(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 0.5f);
            fx.dust(body.chestPoint(), UnbrokenBody.CRACK, 1.6f, 24, 1.4);
        }
        if (taught.add("expose")) {
            fx.actionBar("&5It cracks under its own weight. &fStrike the cracks.");
        }
    }

    /* ================================================================== the statue wakes */

    /** One display per statue block, identical to it, so the swap from blocks is invisible. */
    private final class Crust {
        final BlockDisplay d;
        final Vector3f center;
        final Vector3f fly;
        final int dropAt;
        int state;

        Crust(BlockData data, Vector3f center, int dropAt) {
            this.d = fx.block(data, null, 13);
            this.center = new Vector3f(center);
            this.dropAt = dropAt;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vector3f out = new Vector3f(center.x, 0f, center.z);
            if (out.lengthSquared() < 0.01f) {
                out.set((float) r.nextGaussian(), 0f, (float) r.nextGaussian());
            }
            out.normalize((float) r.nextDouble(1.5, 4.5));
            this.fly = out;
            WeFx.push(d, WeFx.cube(center, 1.002f, new Quaternionf()), 0);
        }
    }

    private void spawnCrust() {
        SiteLayout layout = SiteLayout.get();
        for (int[] c : layout.statueCells()) {
            BlockData data = layout.expected(c[0], c[1], c[2]);
            if (data == null) {
                continue;
            }
            Vector3f center = fx.stage(new Location(fx.world(), c[0] + 0.5, c[1] + 0.5, c[2] + 0.5));
            // Crust falls from the top down: the head first (its eyes open), the knees last.
            int dropAt = 44 + (int) Math.round((9.5f - center.y) * 6f) + ThreadLocalRandom.current().nextInt(6);
            crust.add(new Crust(data, center, Math.max(40, dropAt)));
        }
    }

    private void tickCrust() {
        if (crust.isEmpty()) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = crust.size() - 1; i >= 0; i--) {
            Crust c = crust.get(i);
            int t = act == Act.WAKING ? actTick : 999;
            if (c.state == 0 && t >= 12 && t < c.dropAt && t % 2 == 0) {
                // The statue shivers.
                float j = 0.03f + 0.05f * window(t, 12, c.dropAt);
                Vector3f jitter = new Vector3f(c.center).add((float) r.nextGaussian() * j, 0f, (float) r.nextGaussian() * j);
                WeFx.push(c.d, WeFx.cube(jitter, 1.002f, new Quaternionf()), 2);
            }
            if (c.state == 0 && t >= c.dropAt) {
                c.state = 1;
                Vector3f peak = new Vector3f(c.center).add(c.fly.x * 0.4f, 0.8f, c.fly.z * 0.4f);
                WeFx.push(c.d, WeFx.cube(peak, 0.95f, new Quaternionf().rotateXYZ((float) r.nextGaussian(), (float) r.nextGaussian(), 0f)), 6);
                if (i % 5 == 0) {
                    fx.sound(c.center, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.6f + r.nextFloat() * 0.3f);
                }
                if (i % 3 == 0) {
                    fx.blockDust(c.center, Material.DEEPSLATE, 6, 0.4);
                }
            } else if (c.state == 1 && t >= c.dropAt + 6) {
                c.state = 2;
                Vector3f land = new Vector3f(c.center.x + c.fly.x, 0.35f, c.center.z + c.fly.z);
                WeFx.push(c.d, WeFx.cube(land, 0.7f, new Quaternionf().rotateXYZ(r.nextFloat() * 3f, r.nextFloat() * 3f, 0f)), 10);
            } else if (c.state == 2 && t >= c.dropAt + 40) {
                c.state = 3;
                WeFx.push(c.d, WeFx.cube(new Vector3f(c.center.x + c.fly.x, -0.2f, c.center.z + c.fly.z), 0.01f, new Quaternionf()), 16);
            } else if (c.state == 3 && t >= c.dropAt + 58) {
                fx.kill(c.d);
                crust.remove(i);
            }
        }
    }

    private void clearCrust() {
        for (Crust c : crust) {
            fx.kill(c.d);
        }
        crust.clear();
    }

    private void spawnChains() {
        chains = new GateProps.ChainLine[2];
        for (int s = 0; s < 2; s++) {
            chains[s] = new GateProps.ChainLine(fx, 11, 2.2f, CHAIN_LENGTH);
            chains[s].extraSlack = 3.2f;
        }
    }

    private void tickWake() {
        int t = actTick++;
        if (t == 0) {
            fx.silence();
            if (site) {
                Senses s = WorldEaterSite.get() == null ? null : WorldEaterSite.get().senses();
                if (s != null) {
                    s.skyNow(18000f);
                }
            }
        }
        if (t == 4) {
            fx.score(Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
            fx.score(Sound.BLOCK_GRINDSTONE_USE, 0.8f, 0.5f);
        }
        if (t >= 12 && t < 90 && t % 14 == 0) {
            fx.sound(new Vector3f(pos.x, 4f, pos.z), Sound.BLOCK_DEEPSLATE_BREAK, 1.6f, 0.5f);
            fx.fallingDust(new Vector3f(pos.x, 7f, pos.z), Material.DEEPSLATE, 14, 1.2);
        }
        if (t >= 12 && t < 90 && t % 3 == 0) {
            fx.blockDust(new Vector3f(pos.x, 6f, pos.z), Material.DEEPSLATE, 4, 1.4);
        }
        if (t == 40) {
            // Its eyes open.
            for (WeRig.Piece p : body.rig.group("visor")) {
                body.rig.setGlow(p, UnbrokenBody.VOID);
            }
            fx.sound(body.headPoint(), Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.5f);
            fx.sound(body.headPoint(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.4f, 0.5f);
        }
        if (!site) {
            // Rising out of the ground where it was summoned.
            float rise = outCubic(window(t, 10, 90));
            pos.y = lerp(-UnbrokenBody.STAND, UnbrokenBody.KNEEL, rise);
            if (t % 4 == 0 && t < 90) {
                fx.blockDust(new Vector3f(pos.x, 0.3f, pos.z), Material.DEEPSLATE, 20, 2.0);
                fx.sound(new Vector3f(pos.x, 0.2f, pos.z), Sound.BLOCK_ROOTED_DIRT_BREAK, 1.4f, 0.5f);
            }
        }
        if (t < 96) {
            body.poseKneel(t);
            if (t == 60 && chains != null) {
                // The chains draw tight with one clank.
                for (GateProps.ChainLine c : chains) {
                    c.extraSlack = 0f;
                }
                fx.sound(HANDLES[0], Sound.BLOCK_CHAIN_PLACE, 2f, 0.5f);
                fx.sound(HANDLES[1], Sound.BLOCK_CHAIN_PLACE, 2f, 0.5f);
                fx.sound(new Vector3f(0f, 6f, 8f), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
            }
        } else if (t < 130) {
            // It stands: one fist pushes off the floor, then the rest follows.
            float w = smooth(window(t, 96, 128));
            body.poseStand(clock);
            body.upper[UnbrokenBody.R].target.x = lerp(-0.64f, -0.12f, w);
            pos.y = lerp(UnbrokenBody.KNEEL, UnbrokenBody.STAND, outCubic(w));
            if (t == 100) {
                fx.sound(new Vector3f(pos.x, 0f, pos.z), Sound.ENTITY_IRON_GOLEM_STEP, 2f, 0.5f);
            }
        } else if (t < WAKE_TICKS) {
            body.poseRoar(smooth(window(t, 130, 142)) * (1f - smooth(window(t, 150, WAKE_TICKS))));
            pos.y = UnbrokenBody.STAND;
        }
        if (t == 136) {
            fx.score(Sound.ENTITY_WARDEN_ROAR, 1f, 0.5f);
            fx.score(Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
            fx.sound(new Vector3f(pos.x, 1f, pos.z), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.5f);
            bounce(20f, 0.4f);
            props.add(new WeProps.Shockwave(fx, new Vector3f(pos.x, 0f, pos.z), 1f, 16f, 1.2f, 16, Material.DEEPSLATE_TILES, null));
            fx.title("&7&lTHE UNBROKEN", "&8the floor of the world", 10, 50, 20);
        }
        if (t >= WAKE_TICKS) {
            act = Act.FIGHT;
            actTick = 0;
            cooldown = 20;
            move = Move.IDLE;
            body.rig.releaseHolds();
            lastHealth = instance.getCombatHealth();
        }
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
            if (--cooldown <= 0) {
                chooseMove();
            }
            return;
        }
        int t = moveTick++;
        switch (move) {
            case HAMMER -> hammer(t);
            case FAULT -> fault(t);
            case KEYSTONE -> keystone(t);
            case STRATA -> strata(t);
            case WEIGHT -> weight(t);
            default -> finish(20);
        }
    }

    private void idle() {
        Player p = currentTarget();
        Vector3f goal = p == null ? new Vector3f() : fx.stage(p.getLocation());
        float dist = WeMath.horizontal(goal, pos);
        float want = WeMath.yawToward(goal.x - pos.x, goal.z - pos.z);
        yaw = WeMath.approachAngle(yaw, want, 0.035f);
        if (dist > 6.5f && Math.abs(WeMath.wrap(want - yaw)) < 0.6f) {
            walk(0.11f);
        } else {
            body.poseStand(clock);
            pos.y = WeMath.approach(pos.y, UnbrokenBody.STAND, 0.08f);
        }
    }

    /** A heavy stride along its facing, clamped by its chains and the plaza. */
    private void walk(float speed) {
        float before = walkPhase;
        walkPhase += speed / 7f;
        body.poseWalk(walkPhase);
        pos.y = UnbrokenBody.STAND - UnbrokenBody.walkDip(walkPhase);
        Vector3f step = new Vector3f((float) Math.sin(yaw), 0f, (float) Math.cos(yaw)).mul(speed);
        pos.add(step);
        clamp(pos);
        // A footfall every half cycle.
        if ((int) (before * 2f) != (int) (walkPhase * 2f)) {
            int leg = ((int) (walkPhase * 2f)) % 2 == 0 ? UnbrokenBody.R : UnbrokenBody.L;
            footfall(leg);
        }
    }

    private void footfall(int leg) {
        Vector3f f = body.footPoint(leg);
        f.y = 0.1f;
        fx.sound(f, Sound.ENTITY_IRON_GOLEM_STEP, 2f, 0.4f);
        fx.sound(f, Sound.BLOCK_DEEPSLATE_STEP, 2f, 0.5f);
        fx.sound(f, Sound.ENTITY_WARDEN_STEP, 1.2f, 0.5f);
        fx.blockDust(f, Material.DEEPSLATE, 10, 0.6);
        bounce(7f, 0.18f);
    }

    private void clamp(Vector3f v) {
        float d = (float) Math.sqrt(v.x * v.x + v.z * v.z);
        if (d > arenaRadius) {
            v.x *= arenaRadius / d;
            v.z *= arenaRadius / d;
        }
        if (site) {
            // The chains hold it: it can never walk further than they reach from the gate.
            float gx = v.x;
            float gz = v.z - 16f;
            float g = (float) Math.sqrt(gx * gx + gz * gz);
            if (g > GATE_REACH) {
                v.x = gx * GATE_REACH / g;
                v.z = 16f + gz * GATE_REACH / g;
                if (clock % 30 == 0 && chains != null) {
                    fx.sound(HANDLES[0], Sound.BLOCK_CHAIN_HIT, 2f, 0.5f);
                }
            }
        }
    }

    private void chooseMove() {
        Player p = currentTarget();
        if (p == null) {
            cooldown = 20;
            return;
        }
        Vector3f tp = fx.stage(p.getLocation());
        float dist = WeMath.horizontal(tp, pos);
        float facing = Math.abs(WeMath.wrap(WeMath.yawToward(tp.x - pos.x, tp.z - pos.z) - yaw));
        List<Move> bag = new ArrayList<>();
        List<Float> weight = new ArrayList<>();
        option(bag, weight, Move.HAMMER, dist < 8.5f && facing < 0.9f ? 3.2f : dist < 11f ? 0.8f : 0f);
        option(bag, weight, Move.FAULT, dist > 4f && dist < 19f ? 2.2f : 0.4f);
        option(bag, weight, Move.KEYSTONE, dist > 9f ? 3f : 0.8f);
        option(bag, weight, Move.STRATA, lastMove == Move.STRATA ? 0f : 1.4f);
        if (phase >= 2) {
            option(bag, weight, Move.WEIGHT, lastMove == Move.WEIGHT || actTick < 200 ? 0f : 1.5f);
        }
        float total = 0f;
        for (int i = 0; i < bag.size(); i++) {
            if (bag.get(i) == lastMove) {
                weight.set(i, weight.get(i) * 0.3f);
            }
            total += weight.get(i);
        }
        if (total <= 0f) {
            begin(Move.FAULT);
            return;
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
        Player p = currentTarget();
        target = p == null ? null : p.getUniqueId();
        side = body.armGone() ? UnbrokenBody.R : (ThreadLocalRandom.current().nextBoolean() ? UnbrokenBody.R : UnbrokenBody.L);
    }

    private void finish(int cool) {
        endMove();
        lastMove = move;
        move = Move.IDLE;
        cooldown = phase >= 2 ? Math.max(8, Math.round(cool * 0.7f)) : cool;
        body.springs();
    }

    private void endMove() {
        if (slab != null) {
            fx.kill(slab);
            slab = null;
        }
        weightRing = 0f;
    }

    /* ================================================================== move: HAMMERFALL */

    /**
     * Both fists overhead, arching back, dust sifting off them; then it folds over and drives them
     * into the floor four blocks ahead. Its forearms split: the opening. Phase two: one fist, twice.
     */
    private void hammer(int t) {
        boolean oneArm = body.armGone();
        // The second blow of the phase-two pair comes faster: it is already bent over.
        int wind = mI == 1 ? 14 : phase >= 2 ? 20 : 26;
        int hold = phase >= 2 && mI == 0 ? 24 : 40;
        if (t == 0) {
            if (mI == 1) {
                target = null;
            }
            Player p = currentTarget();
            Vector3f tp = p == null ? new Vector3f(pos).add(fwd().mul(5f)) : fx.stage(p.getLocation());
            yaw = WeMath.yawToward(tp.x - pos.x, tp.z - pos.z);
            float dist = WeMath.horizontal(tp, pos);
            // Step in so the blow lands on the target.
            float stepIn = WeMath.clamp(dist - 4.35f, -1.5f, 3.5f);
            mB.set(pos).add(fwd().mul(stepIn));
            clamp(mB);
            mA.set(mB).add(fwd().mul(4.35f));
            mA.y = 0f;
            mC.set(pos);
            props.add(new WeProps.Circle(fx, mA, 4.6f, wind + 4, Material.ORANGE_STAINED_GLASS, WeProps.EMBER));
            List<Vector3f> crack = WeProps.Crack.jagged(new Vector3f(mB.x, 0f, mB.z), mA, 5, 0.6f);
            props.add(new WeProps.Crack(fx, crack, 0.18f, wind + 30, wind - 4));
            fx.sound(mA, Sound.BLOCK_GRINDSTONE_USE, 1.6f, 0.5f);
            fx.sound(body.chestPoint(), Sound.ENTITY_WARDEN_SNIFF, 1.6f, 0.5f);
            body.springs();
        }
        if (t < wind) {
            float w = smooth(window(t, 0, wind - 4));
            body.poseHammerRaise(w);
            if (oneArm) {
                body.upper[UnbrokenBody.L].target.set(0f, 0f, 0.3f);
            }
            pos.x = lerp(mC.x, mB.x, smooth(window(t, 0, wind)));
            pos.z = lerp(mC.z, mB.z, smooth(window(t, 0, wind)));
            pos.y = WeMath.approach(pos.y, UnbrokenBody.STAND + 0.2f * w, 0.1f);
            if (t % 3 == 0) {
                fx.fallingDust(body.fistPoint(UnbrokenBody.R), Material.DEEPSLATE, 4, 0.4);
                fx.blockDust(body.fistPoint(UnbrokenBody.R), Material.BEDROCK, 3, 0.3);
            }
            if (t % 8 == 0) {
                fx.sound(body.chestPoint(), Sound.BLOCK_GRINDSTONE_USE, 1f, 0.5f + w * 0.2f);
            }
            return;
        }
        int s = t - wind;
        if (s == 0) {
            body.springsFast();
            body.poseHammerDown();
            if (oneArm) {
                body.upper[UnbrokenBody.L].target.set(0.2f, 0f, 0.4f);
            }
            fx.sound(body.chestPoint(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2f, 0.4f);
        }
        if (s < 4) {
            pos.y = lerp(UnbrokenBody.STAND + 0.2f, UnbrokenBody.HAMMER, inCubic(window(s, 0, 4)));
            return;
        }
        if (s == 4) {
            pos.y = UnbrokenBody.HAMMER;
            impact(mA, 4.6f, 150, 1.1f, 0.6f, "hammer" + mI);
            hitstop = 4;
            expose(phase >= 2 ? 34 : 44, true);
            if (taught.add("hammer")) {
                fx.actionBar("&6Cracks race ahead of the blow. &7Leave the circle.");
            }
        }
        if (s < hold) {
            body.poseHammerDown();
            if (oneArm) {
                body.upper[UnbrokenBody.L].target.set(0.2f, 0f, 0.4f);
            }
            if (s % 6 == 0) {
                fx.blockDust(mA, Material.DEEPSLATE, 6, 1.5);
            }
            return;
        }
        if (s == hold && phase >= 2 && mI == 0) {
            // Twice: it pulls free and swings again at whoever is closest now.
            mI = 1;
            moveTick = 0;
            return;
        }
        body.springs();
        body.poseStand(clock);
        pos.y = WeMath.approach(pos.y, UnbrokenBody.STAND, 0.08f);
        if (s >= hold + 18) {
            finish(26);
        }
    }

    /* ================================================================== move: FAULT LINE */

    /**
     * It drags one fist down through the floor toward you; a crack races along the line and stone
     * erupts from it, one column after another, faster than you can run along it. Step aside.
     */
    private void fault(int t) {
        int drag = 18;
        if (t == 0) {
            Player p = currentTarget();
            Vector3f tp = p == null ? new Vector3f(pos).add(fwd().mul(8f)) : fx.stage(p.getLocation());
            yaw = WeMath.yawToward(tp.x - pos.x, tp.z - pos.z);
            Vector3f f = fwd();
            mA.set(pos).add(f.mul(3.0f, new Vector3f()));
            mA.y = 0f;
            mB.set(mA).add(f.mul(18f, new Vector3f()));
            clampLine(mA, mB);
            props.add(new WeProps.Lane(fx, mA, mB, 3.2f, drag, drag - 3, drag + 30, Material.ORANGE_STAINED_GLASS, WeProps.EMBER));
            props.add(new WeProps.Crack(fx, WeProps.Crack.jagged(mA, mB, 9, 0.7f), 0.16f, drag + 34, drag - 3));
            fx.sound(mA, Sound.BLOCK_GRINDSTONE_USE, 1.6f, 0.6f);
            body.springs();
        }
        if (t < drag) {
            float w = smooth(window(t, 0, drag - 2));
            body.poseDrag(side, w);
            pos.y = WeMath.approach(pos.y, UnbrokenBody.dragHeight(w), 0.12f);
            if (t > drag - 8 && t % 2 == 0) {
                fx.particle(Particle.CRIT, body.fistPoint(side), 6, 0.3, 0.2);
                fx.sound(body.fistPoint(side), Sound.ITEM_AXE_SCRAPE, 1.4f, 0.5f);
            }
            return;
        }
        int s = t - drag;
        float length = mA.distance(mB);
        float front = s * 1.25f;
        float prev = (s - 1) * 1.25f;
        if (prev < length) {
            body.poseDrag(side, 1f);
            body.springsFast();
            Vector3f dir = new Vector3f(mB).sub(mA).normalize();
            for (float d = Math.max(0f, prev); d < Math.min(length, front); d += 1.25f) {
                Vector3f at = new Vector3f(mA).fma(d, dir);
                Material m = ThreadLocalRandom.current().nextFloat() < 0.5f ? Material.DEEPSLATE : Material.TUFF;
                props.add(new WeProps.Column(fx, m, at, 1.6f, 3.2f + ThreadLocalRandom.current().nextFloat(), 3, 12, 10));
                fx.sound(at, Sound.BLOCK_DEEPSLATE_BREAK, 1.6f, 0.6f);
                fx.sound(at, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.4f, 0.5f);
                fx.blockDust(at, Material.DEEPSLATE, 12, 0.6);
                for (Player pl : fx.targets()) {
                    Vector3f pp = fx.stage(pl.getLocation());
                    if (WeMath.horizontal(pp, at) < 1.9f && pp.y < 3.5f) {
                        strike(pl, 120, at, 0.3f, 0.95f, "fault", 30);
                    }
                }
            }
            return;
        }
        body.springs();
        body.poseStand(clock);
        pos.y = WeMath.approach(pos.y, UnbrokenBody.STAND, 0.08f);
        if (s > length / 1.25f + 22) {
            finish(22);
        }
    }

    private void clampLine(Vector3f a, Vector3f b) {
        float r = arenaRadius + 2.5f;
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1e-3f) {
            return;
        }
        d.div(len);
        // Shorten b until inside the circle.
        for (int i = 0; i < 40 && Math.sqrt(b.x * b.x + b.z * b.z) > r; i++) {
            len -= 0.5f;
            b.set(a).fma(len, d);
        }
    }

    /* ================================================================== move: KEYSTONE */

    /**
     * It drives its fist into the floor and tears a slab out of it (the floor there is really gone
     * for a while), heaves it over its shoulder while a ring tracks you, and throws. Twice from half health.
     */
    private void keystone(int t) {
        int rip = 20;
        int lift = 22;
        int flight = 16;
        int cycle = rip + lift + 6 + flight + 10;
        int k = t % cycle;
        int n = t / cycle;
        if (n >= (phase >= 2 ? 2 : 1)) {
            body.springs();
            body.poseStand(clock);
            pos.y = WeMath.approach(pos.y, UnbrokenBody.STAND, 0.08f);
            if (k > 16) {
                finish(24);
            }
            return;
        }
        if (k == 0) {
            Player p = n == 0 ? currentTarget() : farthest();
            Vector3f tp = p == null ? new Vector3f(pos).add(fwd().mul(10f)) : fx.stage(p.getLocation());
            yaw = WeMath.yawToward(tp.x - pos.x, tp.z - pos.z);
            target = p == null ? null : p.getUniqueId();
            body.springs();
        }
        if (k < rip) {
            float w = smooth(window(k, 0, rip - 4));
            body.poseRip(side, w);
            pos.y = WeMath.approach(pos.y, UnbrokenBody.dragHeight(w), 0.12f);
            return;
        }
        if (k == rip) {
            Vector3f at = body.fistPoint(side);
            at.y = 0f;
            tearFloor(at);
            slab = fx.block(Material.BEDROCK, null, 13);
            fx.sound(at, Sound.BLOCK_DEEPSLATE_BREAK, 2f, 0.4f);
            fx.sound(at, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.6f, 0.5f);
            fx.blockDust(at, Material.DEEPSLATE_TILES, 30, 1.0);
            bounce(8f, 0.2f);
        }
        if (k < rip + lift) {
            float w = smooth(window(k, rip, rip + lift - 4));
            body.poseThrow(side, 0f);
            body.upper[side].target.x = lerp(-1.0f, -2.6f, w);
            pos.y = WeMath.approach(pos.y, UnbrokenBody.STAND, 0.1f);
            Player p = currentTarget();
            if (p != null) {
                mA.set(fx.stage(p.getLocation()));
                mA.y = 0f;
                yaw = WeMath.approachAngle(yaw, WeMath.yawToward(mA.x - pos.x, mA.z - pos.z), 0.08f);
            }
            if (k == rip + 6) {
                mJ = props.size();
                props.add(new WeProps.Circle(fx, mA, 3.6f, lift + 6 + flight - 4, Material.ORANGE_STAINED_GLASS, WeProps.EMBER));
            }
            holdSlab(k == rip ? 0 : 2);
            return;
        }
        if (k < rip + lift + 6) {
            // Wind up the throw; the ring locks where you stand now.
            float w = smooth(window(k, rip + lift, rip + lift + 6));
            body.springsFast();
            body.poseThrow(side, w);
            holdSlab(2);
            if (k == rip + lift) {
                mB.set(mA);
                fx.sound(body.chestPoint(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 2f, 0.5f);
            }
            if (k == rip + lift + 5) {
                mC.set(slabPoint());
                fx.sound(mC, Sound.ENTITY_WITHER_SHOOT, 1.4f, 0.5f);
            }
            return;
        }
        int f = k - (rip + lift + 6);
        if (f < flight) {
            float u = (f + 1) / (float) flight;
            Vector3f at = WeMath.lerp(mC, new Vector3f(mB.x, 0.6f, mB.z), u, new Vector3f());
            at.y += WeMath.arc(u) * 6f;
            if (slab != null) {
                WeFx.push(slab, WeFx.box(at, new Vector3f(2.4f, 1.2f, 2.4f), new Quaternionf().rotateXYZ(u * 5f, u * 3f, 0f)), 1);
            }
            if (f % 3 == 0) {
                fx.fallingDust(at, Material.DEEPSLATE, 6, 0.6);
            }
            return;
        }
        if (f == flight) {
            if (slab != null) {
                fx.kill(slab);
                slab = null;
            }
            impact(mB, 3.6f, 140, 1.0f, 0.55f, "keystone" + n);
        }
    }

    private Vector3f slabPoint() {
        return body.fistPoint(side).add(0f, 0.6f, 0f);
    }

    private void holdSlab(int interp) {
        if (slab != null) {
            WeFx.push(slab, WeFx.box(slabPoint(), new Vector3f(2.4f, 1.2f, 2.4f), new Quaternionf().rotateY(yaw)), interp);
        }
    }

    /** The torn-out floor is really gone for ten seconds (a shallow pit; the keel is below). */
    private void tearFloor(Vector3f at) {
        if (!site) {
            return;
        }
        WorldEaterSite s = WorldEaterSite.get();
        SiteTerrain t = s == null ? null : s.terrain();
        if (t == null) {
            return;
        }
        Location w = fx.at(at);
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                int x = w.getBlockX() + dx;
                int z = w.getBlockZ() + dz;
                if (!SiteLayout.onPlaza(x + 0.5, z + 0.5) || SiteLayout.get().isStatue(x, SiteLayout.FLOOR, z)) {
                    continue;
                }
                if (t.clear(x, SiteLayout.FLOOR, z) != null) {
                    ripped.add(new int[]{x, SiteLayout.FLOOR, z});
                }
            }
        }
        ripRestoreAt = clock + 200;
    }

    private void restoreRipped() {
        if (ripped.isEmpty()) {
            ripRestoreAt = -1;
            return;
        }
        WorldEaterSite s = WorldEaterSite.get();
        SiteTerrain t = s == null ? null : s.terrain();
        if (t != null) {
            t.restoreCells(ripped);
        }
        ripped.clear();
        ripRestoreAt = -1;
    }

    /* ================================================================== move: STRATA */

    /**
     * It stamps. The plaza splits into eight wedges around it. Four turn to magma under your feet
     * (only you can see it), then they heave up as walls of stone; then the other four. Stand where
     * the floor stays dark, and move once. Straining like that shears its chest open.
     */
    private void strata(int t) {
        int lift = 22;
        if (t == 0) {
            mF = (float) ThreadLocalRandom.current().nextDouble(TAU);
            side = ThreadLocalRandom.current().nextBoolean() ? UnbrokenBody.R : UnbrokenBody.L;
            mA.set(pos.x, 0f, pos.z);
            paintWedges(0, lift + 4);
            fx.sound(mA, Sound.BLOCK_GRINDSTONE_USE, 1.6f, 0.5f);
            if (taught.add("strata")) {
                fx.actionBar("&6The floor turns to magma where it will rise. &7Stand where it stays dark.");
            }
        }
        if (t < lift) {
            body.poseStomp(side, smooth(window(t, 0, lift - 4)));
            return;
        }
        if (t == lift) {
            body.springsFast();
            body.poseStand(clock);
            footfall(side);
            fx.sound(mA, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.5f);
            raiseWedges(0);
            paintWedges(1, 22);
        }
        if (t == lift + 22) {
            fx.sound(mA, Sound.BLOCK_DEEPSLATE_BREAK, 2f, 0.4f);
            raiseWedges(1);
            expose(54, false);
        }
        if (t > lift) {
            body.springs();
            body.poseStand(clock);
            body.spine.target.x = 0.3f;
            body.chest.target.y = (float) Math.sin(t * 0.3f) * 0.1f;
        }
        if (t >= lift + 60) {
            finish(26);
        }
    }

    private boolean inWedge(Vector3f p, int wave) {
        float a = WeMath.wrap((float) Math.atan2(p.x - mA.x, p.z - mA.z) - mF);
        int wedge = Math.floorMod((int) Math.floor((a + PI) / (PI / 4f)), 8);
        return wedge % 2 == wave;
    }

    private void paintWedges(int wave, int ticks) {
        BlockData magma = Material.MAGMA_BLOCK.createBlockData();
        Location c = fx.at(mA);
        int r = 16;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || dx * dx + dz * dz < 5) {
                    continue;
                }
                int x = c.getBlockX() + dx;
                int z = c.getBlockZ() + dz;
                Vector3f cell = fx.stage(new Location(fx.world(), x + 0.5, 0, z + 0.5));
                if (inWedge(cell, wave)) {
                    paint.show(x, c.getBlockY() - 1, z, magma, ticks);
                }
            }
        }
    }

    private void raiseWedges(int wave) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            float center = mF - PI + (2 * i + wave + 0.5f) * (PI / 4f);
            for (float rad = 3f; rad < 15.5f; rad += 2.6f) {
                float jitter = (float) r.nextDouble(-0.2, 0.2);
                Vector3f at = new Vector3f(mA.x + (float) Math.sin(center + jitter) * rad, 0f, mA.z + (float) Math.cos(center + jitter) * rad);
                Material m = rad < 7 ? Material.DEEPSLATE : rad < 11 ? Material.STONE : Material.DIRT;
                props.add(new WeProps.Column(fx, m, at, 2.0f, 2.2f + rad * 0.12f, 3, 14, 12));
            }
        }
        for (Player pl : fx.targets()) {
            Vector3f pp = fx.stage(pl.getLocation());
            float d = WeMath.horizontal(pp, mA);
            if (d > 2.2f && d < 16f && pp.y < 3f && inWedge(pp, wave)) {
                strike(pl, 130, mA, 0.4f, 1.0f, "strata" + wave, 30);
            }
        }
        bounce(18f, 0.25f);
    }

    /* ================================================================== move: WEIGHT */

    /**
     * Down on one knee, fists on the floor, it presses the whole world down. Everyone grows heavy
     * and the edge of the plaza closes in, a ring of rising stone marching toward it. Get close:
     * next to it is the only floor that stays. It is wide open while it presses, and enough damage
     * breaks the press early.
     */
    private void weight(int t) {
        int kneel = 20;
        int close = 100;
        if (t == 0) {
            weightDamage = 0;
            weightRing = arenaRadius + 3f;
            mA.set(pos.x, 0f, pos.z);
            fx.sound(mA, Sound.ENTITY_WARDEN_ROAR, 1.6f, 0.4f);
            if (taught.add("weight")) {
                fx.actionBar("&5The edge is closing. &fGet close to it.");
            }
        }
        if (t < kneel) {
            body.posePress(clock);
            pos.y = lerp(pos.y, UnbrokenBody.PRESS, 0.15f);
            return;
        }
        if (t == kneel) {
            impact(new Vector3f(pos.x, 0f, pos.z), 2.8f, 60, 0.8f, 0.3f, "press");
            expose(close + 40, true);
        }
        int s = t - kneel;
        body.posePress(clock);
        pos.y = UnbrokenBody.PRESS;
        boolean broken = weightDamage >= instance.getCombatMaxHealth() * 0.04;
        if (s < close && !broken) {
            weightRing = lerp(arenaRadius + 3f, 5.5f, s / (float) close);
            if (s % 5 == 0) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                int n = Math.max(8, (int) (weightRing * 1.6f));
                for (int i = 0; i < n; i++) {
                    float a = i * TAU / n + r.nextFloat() * 0.2f;
                    Vector3f at = new Vector3f(mA.x + (float) Math.sin(a) * weightRing, 0f, mA.z + (float) Math.cos(a) * weightRing);
                    props.add(new WeProps.Column(fx, Material.BEDROCK, at, 1.3f, 2.4f, 3, 4, 6));
                }
                paintRing(weightRing - 1.8f, weightRing - 0.4f, 12);
                fx.sound(mA, Sound.BLOCK_DEEPSLATE_BREAK, 1.4f, 0.5f);
            }
            if (s % 10 == 0) {
                for (Player pl : fx.targets()) {
                    Vector3f pp = fx.stage(pl.getLocation());
                    pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false, false));
                    if (WeMath.horizontal(pp, mA) > weightRing) {
                        strike(pl, 70, mA, -0.5f, 0.2f, "weightedge", 10);
                    }
                }
            }
            return;
        }
        if (s == close || broken && mI == 0) {
            mI = 1;
            if (broken) {
                fx.sound(body.chestPoint(), Sound.BLOCK_DEEPSLATE_BREAK, 2f, 0.4f);
                fx.sound(body.chestPoint(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 2f, 0.4f);
                fx.title("", "&5The press breaks.", 4, 24, 10);
                expose(60, true);
                hitstop = 6;
            } else {
                for (Player pl : fx.targets()) {
                    Vector3f pp = fx.stage(pl.getLocation());
                    if (WeMath.horizontal(pp, mA) > 5.5f) {
                        strike(pl, 180, mA, -0.6f, 0.8f, "weightcrush", 40);
                    }
                }
                fx.sound(mA, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.4f);
                bounce(20f, 0.5f);
            }
            mJ = s;
        }
        if (s >= mJ + 30) {
            body.springs();
            body.poseStand(clock);
            pos.y = WeMath.approach(pos.y, UnbrokenBody.STAND, 0.08f);
        }
        if (s >= mJ + 50) {
            finish(30);
        }
    }

    private void paintRing(float r0, float r1, int ticks) {
        BlockData magma = Material.MAGMA_BLOCK.createBlockData();
        Location c = fx.at(mA);
        int r = (int) Math.ceil(r1);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d < r0 || d > r1) {
                    continue;
                }
                paint.show(c.getBlockX() + dx, c.getBlockY() - 1, c.getBlockZ() + dz, magma, ticks);
            }
        }
    }

    /* ================================================================== the arm breaks */

    private void beginBreak() {
        endMove();
        props.clear();
        move = Move.IDLE;
        act = Act.BREAKING;
        actTick = 0;
        BossPhase pending = instance.getPendingPhase();
        int configured = pending != null && pending.getTransition() != null ? pending.getTransition().getDurationTicks() : 0;
        transitionLength = configured > 0 ? configured : BREAK_AUTHORED;
        body.springs();
    }

    private void tickBreak() {
        if (!instance.isTransitioning()) {
            finishBreak();
            return;
        }
        int tau = Math.round(actTick * BREAK_AUTHORED / (float) Math.max(1, transitionLength));
        int prev = actTick == 0 ? -1 : Math.round((actTick - 1) * BREAK_AUTHORED / (float) Math.max(1, transitionLength));
        actTick++;
        for (int k = prev + 1; k <= tau; k++) {
            armBreaks(k);
        }
    }

    /** A blow too hard for its own arm. Authored over 120 ticks. */
    private void armBreaks(int t) {
        if (t == 0) {
            fx.sound(body.chestPoint(), Sound.ENTITY_WARDEN_ANGRY, 2f, 0.5f);
            body.armCrack(UnbrokenBody.L, true);
            body.exposeGlow(true);
        }
        if (t < 30) {
            body.poseHammerRaise(smooth(window(t, 0, 26)));
            body.upper[UnbrokenBody.R].target.set(-0.3f, 0f, -0.4f);
            pos.y = WeMath.approach(pos.y, UnbrokenBody.STAND + 0.2f, 0.05f);
            if (t % 6 == 0) {
                fx.sound(body.fistPoint(UnbrokenBody.L), Sound.BLOCK_DEEPSLATE_BREAK, 1.6f, 0.6f + t * 0.01f);
                fx.dust(body.fistPoint(UnbrokenBody.L), UnbrokenBody.CRACK, 1.4f, 10, 0.6);
            }
        } else if (t < 36) {
            body.springsFast();
            body.poseHammerDown();
            body.upper[UnbrokenBody.R].target.set(0.2f, 0f, -0.4f);
            pos.y = lerp(pos.y, UnbrokenBody.HAMMER, 0.4f);
        }
        if (t == 36) {
            Vector3f at = body.fistPoint(UnbrokenBody.L);
            at.y = 0f;
            impact(at, 4.5f, 120, 1.2f, 0.6f, "armbreak");
            List<WeRig.Piece> arm = body.breakArm();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (WeRig.Piece p : arm) {
                Vector3f out = new Vector3f(p.freePos.x - pos.x, 0f, p.freePos.z - pos.z);
                if (out.lengthSquared() < 0.01f) {
                    out.set(1f, 0f, 0f);
                }
                out.normalize((float) r.nextDouble(2.5, 6.0));
                p.freePos.set(p.freePos.x + out.x, Math.max(0.5f, body.rig.sizeOf(p).y * 0.3f), p.freePos.z + out.z);
                p.freeRot.rotateXYZ((float) r.nextDouble(-1.5, 1.5), (float) r.nextDouble(-1.5, 1.5), (float) r.nextDouble(-1.5, 1.5));
                body.rig.push(p, 10, true);
                body.rig.hold(p, 70);
            }
            fx.score(Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.4f);
            fx.score(Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f);
            fx.score(Sound.BLOCK_ANVIL_DESTROY, 0.8f, 0.5f);
            hitstop = 8;
        }
        if (t > 36 && t < 80) {
            body.poseStagger(0.6f);
            body.upper[UnbrokenBody.R].target.set(-0.4f, 0f, -0.3f);
            pos.y = WeMath.approach(pos.y, UnbrokenBody.STAND, 0.06f);
            // Void pours out of the stump.
            Vector3f stump = body.rig.point(body.upper[UnbrokenBody.L], 0f, 0f, 0f);
            fx.particle(Particle.REVERSE_PORTAL, stump, 12, 0.4, 0.05);
            fx.dust(stump, UnbrokenBody.VOID, 1.8f, 6, 0.4);
            if (t % 10 == 0) {
                fx.sound(stump, Sound.BLOCK_PORTAL_AMBIENT, 1.4f, 0.5f);
            }
        }
        if (t == 70) {
            // The broken arm settles and sinks away.
            for (WeRig.Piece p : body.rig.pieces()) {
                if (p.free && p.group.equals("arm" + UnbrokenBody.L)) {
                    p.freePos.y -= 1.5f;
                    p.freeSize.mul(0.01f);
                    body.rig.push(p, 40, true);
                    body.rig.hold(p, 60);
                }
            }
        }
        if (t >= 80) {
            body.poseRoar(smooth(window(t, 80, 96)) * (1f - smooth(window(t, 108, 120))));
            body.upper[UnbrokenBody.L].target.zero();
        }
        if (t == 90) {
            fx.score(Sound.ENTITY_WARDEN_ROAR, 0.9f, 0.45f);
            bounce(18f, 0.35f);
        }
    }

    private void finishBreak() {
        act = Act.FIGHT;
        actTick = 0;
        cooldown = 16;
        phase = 2;
        freezeBody = false;
        body.exposeGlow(false);
        body.rig.releaseHolds();
        for (WeRig.Piece p : body.rig.pieces()) {
            if (p.free && (p.group.equals("arm" + UnbrokenBody.L) || p.group.equals("armcrack" + UnbrokenBody.L))) {
                p.hidden = true;
            }
        }
        lastHealth = instance.getCombatHealth();
    }

    /* ================================================================== death: the gate */

    private boolean tickDeath() {
        int t = actTick++;
        if (t >= DEATH_TICKS) {
            finishDeath();
            return true;
        }
        if (t == 0) {
            props.clear();
            freezeBody = true;
            hitstop = 0;
            body.crackTo(1f);
            body.exposeGlow(true);
            fx.score(Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
            fx.score(Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.4f);
            fx.score(Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.5f);
            mC.set(pos);
        }
        if (t == 2) {
            fx.silence();
        }
        if (t == 12) {
            freezeBody = false;
            body.springs();
        }
        if (t > 12 && t < 64) {
            // Back to its place: three heavy steps home.
            float f = smooth(window(t, 14, 62));
            pos.x = lerp(mC.x, 0f, f);
            pos.z = lerp(mC.z, 0f, f);
            yaw = WeMath.approachAngle(yaw, UnbrokenBody.facingNorth(), 0.05f);
            body.poseWalk(t / 34f);
            pos.y = UnbrokenBody.STAND - UnbrokenBody.walkDip(t / 34f);
            if (t == 22 || t == 38 || t == 54) {
                footfall(t == 38 ? UnbrokenBody.L : UnbrokenBody.R);
            }
        }
        if (t >= 64 && t < 150) {
            body.poseKneel(clock);
            pos.y = WeMath.approach(pos.y, UnbrokenBody.KNEEL, 0.1f);
            pos.x = WeMath.approach(pos.x, 0f, 0.1f);
            pos.z = WeMath.approach(pos.z, 0f, 0.1f);
            if (body.armGone()) {
                body.upper[UnbrokenBody.L].target.zero();
            }
        }
        if (t == 80) {
            fx.sound(new Vector3f(pos.x, 0f, pos.z), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.4f, 0.5f);
            fx.blockDust(new Vector3f(pos.x, 0.2f, pos.z), Material.DEEPSLATE, 40, 2.0);
        }
        if (t >= 96 && t < 132) {
            // It raises its head and looks at you.
            body.head.target.x = -0.35f;
            body.neck.target.x = -0.2f;
            Player p = fx.nearestTarget(pos);
            if (p != null) {
                Vector3f pp = fx.stage(p.getLocation());
                float want = WeMath.yawToward(pp.x - pos.x, pp.z - pos.z);
                body.head.target.y = WeMath.clamp(WeMath.wrap(want - yaw), -0.7f, 0.7f);
            }
        }
        if (t == 104) {
            fx.title("", "&8&oIt can see us now.", 10, 50, 20);
            fx.score(Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.45f);
            for (WeRig.Piece p : body.rig.group("visor")) {
                body.rig.setGlow(p, WeProps.WHITE);
            }
        }
        if (t >= 132 && t < 150) {
            // It bows its head, kneeling as it knelt for an age before anyone came.
            body.springsHeavy();
            body.head.target.set(0.6f, 0f, 0f);
            body.neck.target.set(0.4f, 0f, 0f);
        }
        if (t == 150) {
            breakFloor();
        }
        if (t > 150 && t < 214) {
            // One rigid body, no ringing: it sinks to the lip of the crater reaching up for its
            // gate; the leaves slam down over its hands (t 184) and knock it into the void.
            if (t < 184) {
                body.springsHeavy();
                pos.y = lerp(UnbrokenBody.KNEEL, -1.4f, smooth(window(t, 150, 182)));
                body.poseReach(smooth(window(t, 152, 178)));
                if (t % 6 == 0) {
                    fx.blockDust(new Vector3f(pos.x, 0.2f, pos.z), Material.DEEPSLATE, 12, 2.4);
                }
            } else {
                if (t == 184) {
                    // Struck: arms buckle, head snaps down, a short hold before the drop.
                    body.springsFast();
                    body.chest.kick(0.2f, 0f, 0f);
                    hitstop = 3;
                    fx.score(Sound.ENTITY_IRON_GOLEM_DEATH, 0.9f, 0.5f);
                } else {
                    body.springs();
                }
                body.poseReach(1f - smooth(window(t, 184, 194)) * 0.85f);
                body.head.target.x = 0.7f;
                float f = inCubic(window(t, 186, 212));
                pos.y = lerp(-1.4f, -34f, f);
            }
            if (chains != null && t == 158) {
                for (GateProps.ChainLine c : chains) {
                    c.extraSlack = 0f;
                }
                fx.score(Sound.BLOCK_CHAIN_BREAK, 1f, 0.5f);
                fx.score(Sound.BLOCK_ANVIL_LAND, 0.9f, 0.5f);
            }
        }
        if (site && t >= 158) {
            tickLeaves(t - 158);
        }
        if (t == 232) {
            fx.score(Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.5f);
            fx.score(Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 0.5f);
        }
        if (t == 240 && site) {
            WorldEaterSite s = WorldEaterSite.get();
            Senses senses = s == null ? null : s.senses();
            if (senses != null) {
                senses.skyNow(1000f);
            }
            fx.title("", "&7The floor of the world is broken.", 10, 60, 30);
        }
        if (t == 262) {
            fx.actionBar("&8The gate stands open.");
            // Far below, something vast answers.
            fx.score(Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1f, 0.5f);
            fx.score(Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1f, 0.5f);
        }
        return false;
    }

    /** The plaza's center gives way: a crater straight through the keel into the void. */
    private void breakFloor() {
        fx.score(Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.35f);
        fx.score(Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.45f);
        fx.score(Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.4f);
        // Nobody falls with it: everyone near the edge is thrown clear.
        for (Player p : fx.targets()) {
            Vector3f pp = fx.stage(p.getLocation());
            float d = WeMath.horizontal(pp, new Vector3f());
            if (d < 7f) {
                Vector3f away = new Vector3f(pp.x, 0f, pp.z);
                if (away.lengthSquared() < 0.01f) {
                    away.set(0f, 0f, -1f);
                }
                away.normalize(1.1f);
                p.setVelocity(new Vector(away.x, 0.55, away.z));
            }
        }
        if (!site) {
            BlockData black = Material.BLACK_CONCRETE.createBlockData();
            Location c = fx.anchor();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx * dx + dz * dz <= 18) {
                        paint.show(c.getBlockX() + dx, c.getBlockY() - 1, c.getBlockZ() + dz, black, 200);
                    }
                }
            }
            return;
        }
        WorldEaterSite s = WorldEaterSite.get();
        SiteTerrain terrain = s == null ? null : s.terrain();
        if (terrain == null) {
            return;
        }
        int n = 0;
        for (int x = 18; x <= 30; x++) {
            for (int z = -34; z <= -22; z++) {
                double d = Math.hypot(x + 0.5 - SiteLayout.PLAZA_X, z + 0.5 - SiteLayout.PLAZA_Z);
                if (d > 4.6) {
                    continue;
                }
                for (int y = SiteLayout.FLOOR; y >= 76; y--) {
                    Material was = terrain.clear(x, y, z);
                    if (was != null && y >= SiteLayout.FLOOR - 1 && n++ % 2 == 0 && was.isBlock()) {
                        Vector3f c = fx.stage(new Location(fx.world(), x + 0.5, y + 0.5, z + 0.5));
                        props.add(new WeProps.Plunge(fx, was, c, new Vector3f(1f), 40f + (float) (d * 3), 30 + (int) (d * 4)));
                    }
                }
            }
        }
        // The gate leaves come out of the wall as displays of themselves.
        List<int[]> left = new ArrayList<>();
        List<int[]> right = new ArrayList<>();
        List<BlockData> leftData = new ArrayList<>();
        List<BlockData> rightData = new ArrayList<>();
        SiteLayout layout = SiteLayout.get();
        for (int[] c : layout.doorCells()) {
            BlockData data = layout.expected(c[0], c[1], c[2]);
            if (data == null) {
                continue;
            }
            if (c[0] <= 23) {
                left.add(c);
                leftData.add(data);
            } else {
                right.add(c);
                rightData.add(data);
            }
        }
        leaves = new GateProps.DoorLeaf[]{
                new GateProps.DoorLeaf(fx, left, leftData, new Vector3f(-3f, 0f, 17.0f), -6f, 0f),
                new GateProps.DoorLeaf(fx, right, rightData, new Vector3f(3f, 0f, 17.0f), 0f, 6f)};
        terrain.clearCells(layout.doorCells());
        // Show where they will fall.
        props.add(new WeProps.Lane(fx, new Vector3f(-3f, 0f, 16.5f), new Vector3f(-3f, 0f, 1f), 6.2f, 14, 22, 26,
                Material.ORANGE_STAINED_GLASS, WeProps.EMBER));
        props.add(new WeProps.Lane(fx, new Vector3f(3f, 0f, 16.5f), new Vector3f(3f, 0f, 1f), 6.2f, 14, 22, 26,
                Material.ORANGE_STAINED_GLASS, WeProps.EMBER));
    }

    /**
     * The leaves, pulled by the chains of the falling Unbroken: torn loose (0-10), toppled flat onto
     * the plaza (10-26), dragged to the crater (26-50), tipped in and gone (50-80).
     */
    private void tickLeaves(int t) {
        if (leaves == null) {
            return;
        }
        for (int s = 0; s < 2; s++) {
            GateProps.DoorLeaf leaf = leaves[s];
            if (leaf == null) {
                continue;
            }
            Vector3f at = new Vector3f(leaf.hinge);
            Quaternionf rot = new Quaternionf();
            float scale = 1f;
            if (t < 10) {
                float f = smooth(window(t, 0, 10));
                at.z -= 1.2f * f;
                rot.rotateX(-0.25f * f);
            } else if (t < 26) {
                float f = inCubic(window(t, 10, 26));
                at.z -= 1.2f;
                rot.rotateX(lerp(-0.25f, -WeMath.HALF_PI, f));
            } else if (t < 50) {
                float f = smooth(window(t, 26, 50));
                at.z -= 1.2f + 7.5f * f;
                rot.rotateX(-WeMath.HALF_PI);
            } else {
                float f = inCubic(window(t, 50, 80));
                at.z -= 8.7f + 3f * f;
                at.y -= 30f * f;
                rot.rotateX(-WeMath.HALF_PI - 1.2f * f);
                scale = 1f - 0.6f * window(t, 66, 80);
            }
            leaf.pose(at, rot, scale);
            if (t % 2 == 0 || t == 26) {
                leaf.push(2);
            }
            if (t == 26) {
                // Slam. Anyone under a leaf is crushed; everyone near is thrown aside.
                for (Player p : fx.targets()) {
                    Vector3f pp = fx.stage(p.getLocation());
                    if (leaf.covers(pp, 0.6f)) {
                        strike(p, 100, new Vector3f(leaf.hinge.x, 0f, pp.z), 1.3f, 0.5f, "leaf" + s, 40);
                    }
                }
                fx.sound(new Vector3f(leaf.hinge.x, 0f, 9f), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.4f);
                fx.sound(new Vector3f(leaf.hinge.x, 0f, 9f), Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.5f);
                fx.blockDust(new Vector3f(leaf.hinge.x, 0.3f, 9f), Material.DEEPSLATE_TILES, 60, 3.0);
                bounce(24f, 0.35f);
            }
            if (t > 26 && t < 50 && t % 3 == 0) {
                Vector3f scrape = new Vector3f(leaf.hinge.x, 0.2f, at.z - 15f);
                fx.particle(Particle.CRIT, scrape, 8, 1.5, 0.3);
                fx.sound(scrape, Sound.ITEM_AXE_SCRAPE, 2f, 0.4f);
                fx.sound(scrape, Sound.BLOCK_GRINDSTONE_USE, 1.2f, 0.5f);
            }
            if (t == 52) {
                fx.sound(new Vector3f(leaf.hinge.x, 0f, 2f), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.4f);
            }
            if (t == 80) {
                leaf.remove();
                leaves[s] = null;
            }
        }
        if (t == 80 && chains != null) {
            for (GateProps.ChainLine c : chains) {
                c.hide(true);
            }
        }
    }

    private void finishDeath() {
        deathFinished = true;
        if (site) {
            WorldEaterSite s = WorldEaterSite.get();
            if (s != null) {
                s.guardianFell();
            }
        }
        LivingEntity entity = instance.getEntity();
        clear();
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    /* ================================================================== reactions */

    private void hitReactions() {
        double hp = instance.getCombatHealth();
        if (hp < lastHealth - 0.01 && clock - lastHitReact >= 4) {
            lastHitReact = clock;
            Vector3f chest = body.chestPoint();
            if (exposeTicks > 0) {
                fx.sound(chest, Sound.BLOCK_DEEPSLATE_BREAK, 1.4f, 0.7f + ThreadLocalRandom.current().nextFloat() * 0.3f);
                fx.sound(chest, Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, 0.5f);
                fx.dust(chest, UnbrokenBody.CRACK, 1.2f, 8, 0.8);
                body.chest.kick(-0.03f, 0f, 0f);
            } else {
                fx.sound(chest, Sound.BLOCK_ANVIL_LAND, 0.35f, 1.9f);
                fx.sound(chest, Sound.ITEM_SHIELD_BLOCK, 0.9f, 0.6f);
                fx.particle(Particle.CRIT, chest, 6, 0.6, 0.2);
                if (taught.add("armor")) {
                    fx.actionBar("&7Bedrock. &8Nothing breaks it... &7except itself.");
                }
            }
            if (body.crackTo(1f - (float) (hp / Math.max(1.0, instance.getCombatMaxHealth())))) {
                fx.sound(chest, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.4f, 0.5f);
                fx.dust(chest, UnbrokenBody.CRACK, 1.6f, 14, 1.0);
                if (exposeTicks > 0) {
                    body.exposeGlow(true);
                }
            }
        }
        lastHealth = hp;
    }

    /* ================================================================== combat helpers */

    private Vector3f fwd() {
        return new Vector3f((float) Math.sin(yaw), 0f, (float) Math.cos(yaw));
    }

    private float faceNearest() {
        Player p = fx == null ? null : fx.nearestTarget(new Vector3f());
        if (p == null) {
            return 0f;
        }
        Vector3f pp = fx.stage(p.getLocation());
        return WeMath.yawToward(pp.x, pp.z);
    }

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

    private Player farthest() {
        return fx.farthestTarget(pos);
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
        Vector3f dir = new Vector3f(pp.x - from.x, 0f, pp.z - from.z);
        if (dir.lengthSquared() < 1e-3f) {
            dir.set(fwd());
        }
        dir.normalize(knock);
        p.setVelocity(new Vector(dir.x, lift, dir.z));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.6f);
        fx.blockDust(new Vector3f(pp).add(0f, 1f, 0f), Material.DEEPSLATE, 8, 0.3);
        return true;
    }

    /** A heavy blow into the floor: damage ring, shockwave, flying floor, the plaza jolts. */
    private void impact(Vector3f at, float radius, double power, float knock, float lift, String key) {
        Vector3f ground = new Vector3f(at.x, 0f, at.z);
        if (power > 0) {
            for (Player p : fx.targets()) {
                Vector3f pp = fx.stage(p.getLocation());
                if (WeMath.horizontal(pp, ground) <= radius + 0.4f && pp.y < 3.5f) {
                    strike(p, power, ground, knock, lift, key, 20);
                }
            }
        }
        props.add(new WeProps.Shockwave(fx, ground, 1f, radius * 2.6f, 1.2f, 14, Material.DEEPSLATE_TILES, null));
        props.add(new WeProps.Shockwave(fx, ground, 0.5f, radius * 1.6f, 0.6f, 10, Material.MAGMA_BLOCK, WeProps.EMBER));
        FakeDestruction.spawnDebris(fx.at(ground), instance, 10, 0.45, 0.9, 30,
                new Material[]{Material.DEEPSLATE_TILES, Material.BEDROCK, Material.POLISHED_DEEPSLATE, Material.TUFF});
        fx.sound(ground, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.5f);
        fx.sound(ground, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.5f);
        fx.sound(ground, Sound.BLOCK_ANVIL_LAND, 0.9f, 0.5f);
        fx.sound(ground, Sound.BLOCK_DEEPSLATE_BREAK, 2f, 0.5f);
        fx.particle(Particle.EXPLOSION, new Vector3f(ground).add(0f, 0.4f, 0f), 2, 0.5, 0);
        fx.blockDust(new Vector3f(ground).add(0f, 0.2f, 0f), Material.DEEPSLATE_TILES, 50, radius * 0.4);
        // Cracked tiles around the impact for a while (client-side only).
        BlockData cracked = Material.CRACKED_DEEPSLATE_TILES.createBlockData();
        BlockData black = Material.BLACKSTONE.createBlockData();
        Location c = fx.at(ground);
        int r = (int) Math.ceil(radius);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius || rnd.nextFloat() < 0.35f) {
                    continue;
                }
                paint.show(c.getBlockX() + dx, c.getBlockY() - 1, c.getBlockZ() + dz, rnd.nextFloat() < 0.7f ? cracked : black, 90 + rnd.nextInt(40));
            }
        }
        bounce(radius * 2.5f, 0.28f);
    }

    /** Everyone standing near a blow is jolted off the floor for a moment. */
    private void bounce(float radius, float lift) {
        for (Player p : fx.targets()) {
            if (!p.isOnGround()) {
                continue;
            }
            Vector3f pp = fx.stage(p.getLocation());
            if (WeMath.horizontal(pp, pos) < radius) {
                Vector v = p.getVelocity();
                p.setVelocity(new Vector(v.getX(), Math.max(v.getY(), lift), v.getZ()));
            }
        }
    }
}
