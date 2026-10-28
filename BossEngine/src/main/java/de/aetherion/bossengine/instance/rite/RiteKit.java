package de.aetherion.bossengine.instance.rite;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.combat.Openings;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.BossPhase;
import de.aetherion.bossengine.skill.t2.T2Mechanics;
import de.aetherion.bossengine.util.TextUtil;
import de.aetherion.core.api.AetherServices;

import net.kyori.adventure.title.Title;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.MobGoals;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One kit per Borderlands rite boss. Each kit owns everything you see of its boss: how it arrives, what it wears,
 * its signature move, how its generic skills (slam, ring, shots) look, its phase change and its death. Nothing is
 * shared between kits but this lifecycle and the {@link Stage} toolkit, so no two bosses look alike.
 *
 * <p>Damage numbers of the signature moves are the old ones; this is a visual overhaul, not a rebalance.
 */
public abstract class RiteKit {

    protected static final Color WHITE = Color.fromRGB(0xFFFFFF);
    protected static final int FLASH = 8;
    protected static final double REACH = 28.0;
    /** Ticks a rite boss stays open (combat-system opening bonus) after its signature lands. */
    private static final int OPENING_TICKS = 30;

    private static final Map<UUID, RiteKit> LIVE = new ConcurrentHashMap<>();

    public static boolean owns(BossInstance instance) {
        return switch (idOf(instance)) {
            case "hollow_lurker", "bridge_troll", "mcnugget", "skuldugery" -> true;
            default -> false;
        };
    }

    /** The kit of this boss, created on first use; null for anyone who is not a rite boss. */
    public static RiteKit of(BossInstance instance) {
        if (!owns(instance)) {
            return null;
        }
        return LIVE.computeIfAbsent(instance.getInstanceId(), ignored -> switch (idOf(instance)) {
            case "hollow_lurker" -> new LurkerKit(instance);
            case "bridge_troll" -> new TrollKit(instance);
            case "mcnugget" -> new NuggetKit(instance);
            default -> new SkullKit(instance);
        });
    }

    public static void drop(BossInstance instance) {
        if (instance == null) {
            return;
        }
        RiteKit kit = LIVE.remove(instance.getInstanceId());
        if (kit != null) {
            kit.clear();
        }
    }

    private static String idOf(BossInstance instance) {
        if (instance == null || instance.getTemplate() == null || instance.getTemplate().getId() == null) {
            return "";
        }
        return instance.getTemplate().getId().toLowerCase(Locale.ROOT);
    }

    protected final BossInstance instance;
    final Stage stage;
    private final List<Follow> follows = new ArrayList<>();
    private boolean bound;
    private boolean fresh;
    private int arrival = -1;
    private Location arrivalAt;
    private int age;
    private int cooldown = 120;
    private int windup = -1;
    private boolean dying;
    private boolean calmed;
    private Location lastSpot;
    private int stuckTicks;
    private int hopCd;
    private int contactCd;

    protected RiteKit(BossInstance instance) {
        this.instance = instance;
        this.stage = new Stage(instance);
    }

    /* ================================================================== lifecycle (called by the engine) */

    /** Body bound. {@code freshSpawn} is false for a body adopted after a restart or a chunk reload. */
    public final void bind(LivingEntity body, boolean freshSpawn) {
        if (body == null) {
            return;
        }
        dress(body);
        if (!bound) {
            bound = true;
            fresh = freshSpawn;
        }
    }

    public final boolean isArriving() {
        return arrival >= 0;
    }

    /** Every tick while the boss is alive (transitions included). */
    public final void tick() {
        stage.tick();
        LivingEntity boss = instance.getEntity();
        if (dying || boss == null || !boss.isValid() || !instance.isAlive()) {
            return;
        }
        age++;
        if (fresh) {
            fresh = false;
            arrival = 0;
            arrivalAt = boss.getLocation().clone();
            freeze(boss);
            arrivalStart(boss, arrivalAt.clone());
        }
        if (arrival >= 0) {
            arrival++;
            arrivalTick(boss, arrivalAt.clone(), arrival);
            if (arrival >= arrivalTicks()) {
                arrival = -1;
                release(boss);
                arrivalEnd(boss, arrivalAt.clone());
            }
            followAll(boss);
            return;
        }
        followAll(boss);
        ambient(boss, age);
        if (instance.isTransitioning()) {
            if (windup >= 0) {
                windup = -1;
                cancelMove();
            }
            return;
        }
        if (windup >= 0) {
            charge(boss, windup, late());
            if (windup == FLASH) {
                flash(boss);
            }
            if (windup-- == 0) {
                windup = -1;
                strike(boss, late());
                Openings.open(boss, OPENING_TICKS);
            }
            return;
        }
        think(boss);
        if (--cooldown <= 0) {
            List<Player> near = T2Mechanics.nearby(boss, REACH);
            if (near.isEmpty() || instance.isSlamPending()) {
                cooldown = 40;
                return;
            }
            cooldown = cooldown(late());
            int length = begin(boss, near, late());
            if (length > 0) {
                windup = length;
                // The combat kit's language over the signature: yellow "!" if it can be parried, red "!!" if only
                // a roll escapes it. It lands with strike(), length + 1 ticks from now.
                if (parryable()) {
                    instance.guard().parryTell(length + 1);
                } else {
                    instance.guard().rollTell(length + 1);
                }
            }
        }
    }

    /** The death cinematic drives the stage once the boss stops ticking. */
    public final void death(Location core, LivingEntity body, int tick, int duration, double startScale) {
        if (!dying) {
            dying = true;
            windup = -1;
            dropFollows();
            deathStart(core.clone(), body);
        }
        stage.tick();
        deathTick(core, body, tick, duration, startScale);
    }

    public void clear() {
        dropFollows();
        stage.clear();
    }

    protected final boolean late() {
        BossPhase phase = instance.getCurrentPhase();
        return phase != null && phase.getHealthPercent() < 100;
    }

    /* ================================================================== hooks the engine routes here */

    /** SLAM_LEAP take-off. */
    public void leapOff(Location feet) {
    }

    /** Airborne slam marker, called every few ticks while the boss is in the air. */
    public void slamTell(Location centre, double radius) {
    }

    public void slam(Location centre, double radius) {
    }

    /** RING_BURST: the preview of all waves, then one call per wave. */
    public void ringPreview(Location centre, double step, int waves, int windupTicks) {
    }

    public void ringWave(Location centre, double radius, int index, boolean last) {
    }

    /** ARROW_SHOT / EGG_SHOT muzzle; {@code projectile} may carry a prop. */
    public void shot(Location from, Vector direction, org.bukkit.entity.Entity projectile) {
    }

    public void meteor(Location ground) {
    }

    public void transitionStart(Location at) {
    }

    public void transitionTick(Location at, int tick, int duration) {
    }

    public void transitionEnd(Location at) {
    }

    /** The explode beat at the end of a transition (damage stays in the engine). */
    public void transitionBlast(Location centre, double radius) {
    }

    /** HOVER_STORM: the run to the middle, then each strike on the hovering body. */
    public void stormWalk(Location feet, int tick) {
    }

    public void stormStrike(Location at) {
    }

    /* ================================================================== what each kit writes */

    protected abstract int arrivalTicks();

    protected abstract void arrivalStart(LivingEntity boss, Location at);

    protected abstract void arrivalTick(LivingEntity boss, Location at, int t);

    protected abstract void arrivalEnd(LivingEntity boss, Location at);

    protected void dress(LivingEntity boss) {
    }

    protected void ambient(LivingEntity boss, int age) {
    }

    protected abstract int cooldown(boolean late);

    /** Pick targets and lay the tell; returns the windup in ticks, or 0 to skip. */
    protected abstract int begin(LivingEntity boss, List<Player> near, boolean late);

    protected void charge(LivingEntity boss, int left, boolean late) {
    }

    /** White flash {@link #FLASH} ticks before the hit: "white means move". */
    protected abstract void flash(LivingEntity boss);

    protected abstract void strike(LivingEntity boss, boolean late);

    protected void cancelMove() {
    }

    /** The signature being wound up can be parried (yellow); default: roll only (red). */
    protected boolean parryable() {
        return false;
    }

    /** Guard broken ({@link de.aetherion.bossengine.combat.BossGuard}): drop the wind-up and hold off the next. */
    public final void stagger(int ticks) {
        if (windup >= 0) {
            windup = -1;
            cancelMove();
        }
        cooldown = Math.max(cooldown, ticks + 20);
        contactCd = Math.max(contactCd, ticks);
    }

    protected abstract void deathStart(Location core, LivingEntity body);

    protected abstract void deathTick(Location core, LivingEntity body, int tick, int duration, double startScale);

    /* ================================================================== helpers */

    protected final void freeze(LivingEntity boss) {
        boss.setInvulnerable(true);
        boss.setGravity(false);
        boss.setVelocity(new Vector());
        if (boss instanceof Mob mob) {
            mob.setAI(false);
        }
    }

    protected final void release(LivingEntity boss) {
        if (!boss.isValid()) {
            return;
        }
        boss.setGravity(true);
        boss.setInvulnerable(false);
        boss.setFallDistance(0);
        if (boss instanceof Mob mob) {
            mob.setAI(true);
        }
    }

    protected final void place(LivingEntity boss, Location to) {
        instance.runInternalTeleport(() -> {
            if (boss.isValid()) {
                boss.teleport(to);
            }
        });
    }

    protected final List<Player> near(LivingEntity boss, double range) {
        return T2Mechanics.nearby(boss, range);
    }

    protected final List<Player> hitCircle(LivingEntity boss, Location centre, double radius, double power, double lift) {
        List<Player> hit = new ArrayList<>();
        for (Player p : T2Mechanics.nearby(boss, REACH + 8)) {
            Location l = p.getLocation();
            double dx = l.getX() - centre.getX();
            double dz = l.getZ() - centre.getZ();
            if (dx * dx + dz * dz > radius * radius || Math.abs(l.getY() - centre.getY()) > 3.0) {
                continue;
            }
            if (lift > 0) {
                p.setVelocity(p.getVelocity().setY(lift));
            }
            if (parryable()) {
                BossHits.hurt(p, boss, instance.scaleDamage(power));
                instance.guard().parriedBy(p);
            } else {
                BossHits.peril(p, boss, instance.scaleDamage(power));
            }
            hit.add(p);
        }
        return hit;
    }

    protected final void cue(List<Player> players, String en, String de) {
        for (Player p : players) {
            p.sendActionBar(TextUtil.component(AetherServices.german(p) ? de : en));
        }
    }

    protected final void titleCard(LivingEntity boss, String name, String en, String de) {
        Title.Times times = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2600), Duration.ofMillis(900));
        for (Player p : near(boss, 44)) {
            p.showTitle(Title.title(TextUtil.component(name),
                    TextUtil.component("&7" + (AetherServices.german(p) ? de : en)), times));
        }
    }

    protected final double scale(LivingEntity boss) {
        return de.aetherion.bossengine.util.AttributeUtil.getValue(boss,
                de.aetherion.bossengine.util.AttributeUtil.scale(), 1.0);
    }

    protected static double yawRad(Location at) {
        return Math.toRadians(at.getYaw());
    }

    /** The feet with the yaw of the body the client draws (a mob's own yaw is where it walks, not where it faces). */
    protected static Location bodyFeet(LivingEntity boss) {
        Location feet = boss.getLocation();
        feet.setYaw((float) boss.getBodyYaw());
        return feet;
    }

    /** Body-space point: +X left, +Y up, +Z forward, measured from the feet. */
    protected static Location bodyPoint(Location feet, double left, double up, double forward) {
        double yaw = yawRad(feet);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        return feet.clone().add(fx * forward + lx * left, up, fz * forward + lz * left);
    }

    /* ------------------------------------------------------------------ worn props */

    /** A display that rides along with the body: offset and rotation in body space, scaled with the mob. */
    protected final class Follow {
        final Display display;
        final Vector3f local;
        Quaternionf spin;
        float size;
        float sx = 1f;
        float sy = 1f;
        float sz = 1f;
        boolean block;

        Follow(Display display, Vector3f local, Quaternionf spin, float size, boolean block) {
            this.display = display;
            this.local = local;
            this.spin = spin;
            this.size = size;
            this.block = block;
        }
    }

    protected final Follow wear(Display display, Vector3f local, Quaternionf spin, float size, boolean block) {
        if (display == null) {
            return null;
        }
        Follow f = new Follow(display, local, spin, size, block);
        follows.add(f);
        return f;
    }

    protected final void dropFollows() {
        for (Follow f : follows) {
            stage.remove(f.display);
        }
        follows.clear();
    }

    protected final boolean wearing() {
        follows.removeIf(f -> !Stage.alive(f.display));
        return !follows.isEmpty();
    }

    private void followAll(LivingEntity boss) {
        if (follows.isEmpty()) {
            return;
        }
        Location feet = bodyFeet(boss);
        float s = (float) scale(boss);
        Quaternionf body = new Quaternionf().rotateY((float) -yawRad(feet));
        Location anchor = feet.clone();
        anchor.setYaw(0f);
        anchor.setPitch(0f);
        for (Follow f : follows) {
            if (!Stage.alive(f.display)) {
                continue;
            }
            Vector3f at = new Vector3f(f.local).mul(s);
            body.transform(at);
            Quaternionf r = new Quaternionf(body).mul(f.spin);
            Matrix4f m = f.block
                    ? Stage.cube(at, r, f.sx * f.size * s, f.sy * f.size * s, f.sz * f.size * s)
                    : Stage.model(at, r, f.size * s);
            Stage.move(f.display, anchor, 2);
            Stage.push(f.display, m, 2);
        }
    }

    /* ------------------------------------------------------------------ brain */

    /** 0 for a melee boss; a ranged one holds about this far from its target and circles it. */
    protected double holdRange() {
        return 0.0;
    }

    /** False for a boss that must never leave the ground on its own (the troll fights on a bridge). */
    protected boolean hops() {
        return true;
    }

    /** Reach of {@link #contact}, for a body whose vanilla AI cannot hit (0 = none). */
    protected double contactReach() {
        return 0.0;
    }

    /** A hit from the body itself, at most every 25 ticks, when the target is within {@link #contactReach}. */
    protected void contact(LivingEntity boss, Player target) {
    }

    /**
     * Between signatures: stick to one target, path to it (or circle it at {@link #holdRange}), and hop when the
     * body has not moved for 1.5 s while it should. Vanilla left them wandering: the chicken panicked, the skeleton
     * had no attack goal at all once its bow AI was taken away.
     */
    private void think(LivingEntity boss) {
        if (!(boss instanceof Mob mob) || !mob.hasAI() || age % 5 != 0 || instance.isSlamPending()) {
            return;
        }
        if (!calmed) {
            calmed = true;
            calm(mob);
        }
        List<Player> near = near(boss, REACH);
        if (near.isEmpty()) {
            return;
        }
        Location at = boss.getLocation();
        Player target = mob.getTarget() instanceof Player current && near.contains(current) ? current : null;
        if (target == null) {
            double best = Double.MAX_VALUE;
            for (Player p : near) {
                double d = p.getLocation().distanceSquared(at);
                if (d < best) {
                    best = d;
                    target = p;
                }
            }
            mob.setTarget(target);
        }
        Location to = target.getLocation();
        double dx = to.getX() - at.getX();
        double dz = to.getZ() - at.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double hold = holdRange();
        if (hold <= 0) {
            if (d > 1.8) {
                mob.getPathfinder().moveTo(target, 1.0);
            }
        } else if (d > hold + 4) {
            mob.getPathfinder().moveTo(target, 1.0);
        } else {
            // circle at range, switching sides every 4 s; too close and he backs out to it
            double away = Math.atan2(-dz, -dx) + ((age / 80) % 2 == 0 ? 0.55 : -0.55);
            double r = Math.max(hold - 1.0, Math.min(hold + 1.0, d < hold - 3.0 ? hold : d));
            mob.getPathfinder().moveTo(to.clone().add(Math.cos(away) * r, 0, Math.sin(away) * r), 1.0);
        }
        if (contactCd > 0) {
            contactCd -= 5;
        }
        if (contactCd <= 0 && contactReach() > 0 && d <= contactReach() && Math.abs(to.getY() - at.getY()) < 2.0) {
            contactCd = 25;
            contact(boss, target);
        }
        if (hopCd > 0) {
            hopCd -= 5;
        }
        boolean wantsToMove = hold <= 0 ? d > 3.5 : d > hold + 4;
        boolean still = lastSpot != null && lastSpot.getWorld() == at.getWorld() && lastSpot.distanceSquared(at) < 0.09;
        stuckTicks = still && wantsToMove ? stuckTicks + 5 : 0;
        lastSpot = at.clone();
        if (stuckTicks >= 30 && hopCd <= 0 && hops() && boss.isOnGround() && d > 0.01) {
            stuckTicks = 0;
            hopCd = 60;
            boss.setVelocity(new Vector(dx / d * 0.55, 0.52, dz / d * 0.55));
            at.getWorld().playSound(at, Sound.ENTITY_GOAT_LONG_JUMP, 0.9f, 0.7f);
        }
    }

    /** Drops the wander, panic and livestock goals; the targeting and attack goals stay. */
    private static void calm(Mob mob) {
        try {
            MobGoals goals = Bukkit.getMobGoals();
            for (Goal<Mob> goal : new ArrayList<>(goals.getAllGoals(mob))) {
                String key = goal.getKey().getNamespacedKey().getKey();
                if (key.contains("panic") || key.contains("stroll") || key.contains("tempt") || key.contains("breed")
                        || key.contains("follow_parent") || key.contains("avoid") || key.contains("look_around")) {
                    goals.removeGoal(mob, goal);
                }
            }
        } catch (RuntimeException ignored) {
            // goal keys differ between versions; a boss that keeps its wander goal still works
        }
    }

    protected static World world(Location at) {
        return at == null ? null : at.getWorld();
    }
}
