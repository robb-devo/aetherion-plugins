package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.model.BossPhase;
import de.aetherion.bossengine.model.PhaseTransition;
import de.aetherion.bossengine.model.TransitionShape;
import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.SkillTrigger;
import de.aetherion.bossengine.skill.impl.ArrowShotSkill;
import de.aetherion.bossengine.skill.impl.EggShotSkill;
import de.aetherion.bossengine.skill.impl.ParticleAuraSkill;
import de.aetherion.bossengine.skill.impl.RingBurstSkill;
import de.aetherion.bossengine.skill.impl.SlamLeapSkill;
import de.aetherion.bossengine.util.AttributeUtil;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Borderlands four and Pathwarden: arrival beat, attack tells, quiet idle.
 * Every attack reads as tell, commit, recover. Timing and sound carry it;
 * a few thin dust shapes mark where it lands. Keyed on the boss id, so it
 * holds even when a server still runs an older copy of the YAML.
 */
public final class EarlyBossKit {

    private static final Color SKULL = Color.fromRGB(255, 112, 28);
    private static final Color LURKER = Color.fromRGB(48, 196, 188);
    private static final Color LURKER_DEEP = Color.fromRGB(12, 40, 48);
    private static final Color LURKER_EYE = Color.fromRGB(90, 230, 255);
    private static final Color NUGGET = Color.fromRGB(255, 196, 64);
    private static final Color TROLL = Color.fromRGB(214, 168, 72);
    private static final Color AZURE = Color.fromRGB(80, 180, 255);
    private static final Color CRIMSON = Color.fromRGB(220, 40, 55);
    private static final Color EDGE = Color.fromRGB(255, 245, 235);

    /** Storm finale edge. Arena-wide blasts cannot be dodged, so they cannot be read. */
    private static final double TROLL_STORM_EDGE = 10.0;

    private static final int SKULL_DRAW = 18;
    private static final int SKULL_DRAW_LATE = 13;
    private static final int SKULL_CADENCE = 60;
    private static final int SKULL_CADENCE_LATE = 42;
    private static final double SKULL_FAN = Math.toRadians(10);
    private static final int NUGGET_TELL = 14;
    private static final int NUGGET_TELL_LATE = 10;
    private static final int NUGGET_GAP = 5;
    private static final int NUGGET_CADENCE = 64;
    private static final int NUGGET_CADENCE_LATE = 48;
    private static final int TROLL_CROUCH = 14;
    private static final int LURKER_TELL = 12;
    /** Mirrors T2Mechanics ring timing: 10-tick windup, 6 ticks per wave. */
    private static final int RING_WINDUP = 10;
    private static final int RING_GAP = 6;

    private enum Kind {
        NONE(0), SKULL(30), LURKER(32), NUGGET(26), TROLL(34), WARDEN(40);

        final int arrival;

        Kind(int arrival) {
            this.arrival = arrival;
        }

        static Kind of(String id) {
            return switch (id == null ? "" : id.toLowerCase(Locale.ROOT)) {
                case "skuldugery" -> SKULL;
                case "hollow_lurker" -> LURKER;
                case "mcnugget" -> NUGGET;
                case "bridge_troll" -> TROLL;
                case "pathwarden" -> WARDEN;
                default -> NONE;
            };
        }
    }

    private enum Move { NONE, DRAW, PECK, CROUCH, PULSE }

    private final BossInstance instance;
    private Kind kind;
    private int arrival = -1;
    private Move move = Move.NONE;
    private int moveTick;
    private int moveLength;
    private AbstractBossSkill moveSkill;
    private SkillContext moveContext;
    private Location ringCenter;
    private long nextShotAt;
    private double restScale = -1;
    private int transitionTick = -1;

    EarlyBossKit(BossInstance instance) {
        this.instance = instance;
    }

    public boolean owns() {
        return kind() != Kind.NONE;
    }

    /**
     * Called before a skill runs. True = the kit took it (staged, held, or muted),
     * and the caller must not execute or mark it.
     */
    public boolean intercept(AbstractBossSkill skill, SkillContext context) {
        Kind k = kind();
        if (k == Kind.NONE || skill == null) {
            return false;
        }
        if (skill instanceof ParticleAuraSkill) {
            // Identity comes from silhouette and sound, not a constant cloud.
            instance.markCast(skill);
            return true;
        }
        if (skill.getTrigger() == SkillTrigger.ON_SPAWN) {
            beginArrival();
            return false;
        }
        if (skill.getTrigger() != SkillTrigger.ON_TIMER) {
            return false;
        }
        // Volley bosses keep their own clock, so the YAML interval only sets the ceiling.
        if (k == Kind.SKULL && skill instanceof ArrowShotSkill) {
            if (!busy() && instance.getTicksAlive() >= nextShotAt) {
                nextShotAt = instance.getTicksAlive() + (late() ? SKULL_CADENCE_LATE : SKULL_CADENCE);
                begin(Move.DRAW, skill, context, (late() ? SKULL_DRAW_LATE : SKULL_DRAW) + 6);
            }
            return true;
        }
        if (k == Kind.NUGGET && skill instanceof EggShotSkill) {
            if (!busy() && instance.getTicksAlive() >= nextShotAt) {
                nextShotAt = instance.getTicksAlive() + (late() ? NUGGET_CADENCE_LATE : NUGGET_CADENCE);
                begin(Move.PECK, skill, context, nuggetTell() + NUGGET_GAP * 2 + 6);
            }
            return true;
        }
        if (busy()) {
            // One thing at a time: anything else waits a full interval.
            instance.markCast(skill);
            return true;
        }
        if (k == Kind.TROLL && skill instanceof SlamLeapSkill) {
            instance.markCast(skill);
            if (!instance.isSlamPending()) {
                begin(Move.CROUCH, skill, context, TROLL_CROUCH + 2);
            }
            return true;
        }
        if (k == Kind.LURKER && skill instanceof RingBurstSkill) {
            instance.markCast(skill);
            // Stands his ground until the second wave is out, then gives chase again.
            begin(Move.PULSE, skill, context, LURKER_TELL + RING_WINDUP + RING_GAP * 2);
            return true;
        }
        return false;
    }

    void tick() {
        Kind k = kind();
        if (k == Kind.NONE) {
            return;
        }
        LivingEntity body = instance.getEntity();
        if (body == null || !body.isValid() || body.isDead()) {
            return;
        }
        if (instance.isTransitioning()) {
            endMove(body);
            if (arrival >= 0) {
                arrival = -1;
                restoreScale(body);
            }
            tickTransition(k, body);
            return;
        }
        transitionTick = -1;
        if (arrival >= 0) {
            tickArrival(k, body);
            return;
        }
        if (move != Move.NONE) {
            tickMove(body);
        }
    }

    void abort() {
        LivingEntity body = instance.getEntity();
        if (body != null && body.isValid()) {
            endMove(body);
            if (arrival >= 0) {
                restoreScale(body);
            }
        }
        arrival = -1;
        transitionTick = -1;
    }

    /** Troll only: the slam ring sits on the floor he will hit, not in the air under him. */
    boolean drawSlamTelegraph(Location center, double radius) {
        if (kind() != Kind.TROLL || center == null || center.getWorld() == null) {
            return false;
        }
        Location floor = groundBelow(center);
        ring(floor, radius, TROLL, 1.3f);
        floor.getWorld().spawnParticle(Particle.DUST, floor.clone().add(0, 0.1, 0), 2, 0.08, 0, 0.08, 0,
                new Particle.DustOptions(EDGE, 1.1f));
        return true;
    }

    double explodeRadius(double configured) {
        return kind() == Kind.TROLL ? Math.min(configured, TROLL_STORM_EDGE) : configured;
    }

    /** Hollow Lurker's ring waves: one clean teal band per heartbeat. False = generic wave. */
    public boolean ringWave(Location center, double radius, int index, int waves) {
        if (kind() != Kind.LURKER || center == null || center.getWorld() == null) {
            return false;
        }
        World world = center.getWorld();
        ring(center, radius, LURKER, 1.5f);
        if (index == waves) {
            ring(center, Math.max(0.8, radius - 0.6), LURKER_DEEP, 1.1f);
        }
        world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 0.55f + index * 0.1f);
        world.playSound(center, Sound.BLOCK_SCULK_BREAK, 0.6f, 0.7f + index * 0.08f);
        return true;
    }

    // ---------------------------------------------------------------- arrival

    private void beginArrival() {
        LivingEntity body = instance.getEntity();
        if (body == null || !body.isValid() || arrival >= 0 || !instance.tryAnnounce("early-kit-arrival")) {
            return;
        }
        arrival = 0;
        restScale = AttributeUtil.getBase(body, AttributeUtil.scale(), 1.0);
        if (kind() == Kind.LURKER) {
            setScale(body, 0.55);
        } else if (kind() == Kind.NUGGET) {
            setScale(body, 0.6);
        }
    }

    private void tickArrival(Kind k, LivingEntity body) {
        int t = arrival++;
        if (k != Kind.WARDEN) {
            hold(body);
            body.setInvulnerable(true);
            face(body, target(body, 40));
        }
        switch (k) {
            case SKULL -> arriveSkull(body, t);
            case LURKER -> arriveLurker(body, t);
            case NUGGET -> arriveNugget(body, t);
            case TROLL -> arriveTroll(body, t);
            case WARDEN -> arriveWarden(body, t);
            default -> {
            }
        }
        if (t + 1 >= k.arrival) {
            arrival = -1;
            restoreScale(body);
            if (body instanceof Mob mob) {
                mob.setAggressive(false);
                mob.setAware(true);
            }
            body.clearActiveItem();
        }
    }

    /** Nocks, draws at the sky, lights the tip, and looses one flare straight up. */
    private void arriveSkull(LivingEntity body, int t) {
        World world = body.getWorld();
        Location at = body.getLocation();
        if (t == 4) {
            world.playSound(at, Sound.ENTITY_SKELETON_AMBIENT, 1.0f, 0.5f);
        }
        if (t >= 8 && t < 22) {
            body.setRotation(at.getYaw(), -55f);
            if (t == 8) {
                body.startUsingItem(EquipmentSlot.HAND);
                if (body instanceof Mob mob) {
                    mob.setAggressive(true);
                }
                world.playSound(at, Sound.ITEM_CROSSBOW_LOADING_START, 0.9f, 0.6f);
            }
            if (t == 16) {
                world.spawnParticle(Particle.SMALL_FLAME, bowTip(body), 2, 0.02, 0.02, 0.02, 0.005);
                world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 0.35f, 0.9f);
                world.playSound(at, Sound.ITEM_CROSSBOW_LOADING_END, 0.7f, 0.7f);
            }
        }
        if (t == 22) {
            body.clearActiveItem();
            world.playSound(at, Sound.ENTITY_ARROW_SHOOT, 1.1f, 0.6f);
            world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 0.6f, 0.6f);
        }
        if (t >= 22) {
            // Flare climbs: one ember per tick, a readable streak instead of a burst.
            Location flare = body.getEyeLocation().add(0, 1.2 + (t - 22) * 1.7, 0);
            world.spawnParticle(Particle.FLAME, flare, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SMALL_FLAME, flare.clone().add(0, -0.8, 0), 1, 0, 0, 0, 0);
        }
    }

    /** Rises out of the dark on a quickening heartbeat; the eyes open last. */
    private void arriveLurker(LivingEntity body, int t) {
        World world = body.getWorld();
        Location at = body.getLocation();
        double u = Math.min(1.0, t / 24.0);
        setScale(body, 0.55 + 0.45 * easeOut(u));
        if (t % 3 == 0 && t <= 24) {
            ring(groundBelow(at), 4.2 - 3.0 * u, LURKER_DEEP, 1.3f);
        }
        if (t == 0 || t == 10 || t == 17 || t == 22) {
            world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 0.95f, 0.5f + t * 0.014f);
        }
        if (t >= 24 && t <= 30 && t % 2 == 0) {
            eyes(body, 1.3f);
        }
        if (t == 24) {
            world.playSound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.5f, 0.7f);
        }
        if (t == 27) {
            world.playSound(at, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.7f, 0.6f);
        }
    }

    /** Pops out on a spring, one big cluck, then the fryer ding. */
    private void arriveNugget(LivingEntity body, int t) {
        World world = body.getWorld();
        Location at = body.getLocation();
        if (t <= 20) {
            setScale(body, 1.0 - 0.4 * Math.cos(t * 0.52) * Math.exp(-t * 0.16));
        } else {
            setScale(body, 1.0);
        }
        if (t == 0) {
            world.playSound(at, Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.6f);
            world.spawnParticle(Particle.ITEM, at.clone().add(0, 0.8, 0), 8, 0.5, 0.3, 0.5, 0.06,
                    new ItemStack(Material.FEATHER));
        }
        if (t == 6) {
            world.playSound(at, Sound.ENTITY_PARROT_FLY, 0.9f, 0.6f);
        }
        if (t == 14) {
            world.playSound(at, Sound.ENTITY_CHICKEN_AMBIENT, 1.4f, 0.5f);
        }
        if (t == 20) {
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.6f);
        }
    }

    /** Two heavy steps onto the crossing, then the roar. */
    private void arriveTroll(LivingEntity body, int t) {
        World world = body.getWorld();
        Location at = body.getLocation();
        if (t == 2 || t == 14) {
            body.setVelocity(new Vector(0, 0.34, 0));
        }
        if (t == 6 || t == 18) {
            Location floor = groundBelow(at);
            float weight = t == 6 ? 0.75f : 1.0f;
            world.playSound(floor, Sound.BLOCK_ANVIL_LAND, weight, 0.5f);
            world.playSound(floor, Sound.ENTITY_RAVAGER_STEP, 1.0f, 0.6f);
            ring(floor, t == 6 ? 2.8 : 3.4, TROLL, 1.3f);
            Block under = floor.clone().add(0, -0.5, 0).getBlock();
            if (under.getType().isSolid()) {
                world.spawnParticle(Particle.BLOCK, floor.clone().add(0, 0.1, 0), 10, 0.7, 0.05, 0.7, 0.05,
                        under.getBlockData());
            }
        }
        if (t == 26) {
            body.swingMainHand();
            world.playSound(at, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.3f, 0.5f);
            world.playSound(at, Sound.BLOCK_CHAIN_PLACE, 0.6f, 0.5f);
        }
    }

    /** Two iron footfalls, then both blades planted across the path in front of him. */
    private void arriveWarden(LivingEntity body, int t) {
        World world = body.getWorld();
        Location floor = groundBelow(body.getLocation());
        if (t == 6 || t == 16) {
            world.playSound(floor, Sound.BLOCK_ANVIL_LAND, 0.9f, 0.45f);
            world.playSound(floor, Sound.ENTITY_IRON_GOLEM_STEP, 1.2f, 0.5f);
            ring(floor, t == 6 ? 3.2 : 3.8, t == 6 ? AZURE : CRIMSON, 1.3f);
        }
        if (t == 28) {
            world.playSound(floor, Sound.ITEM_TRIDENT_HIT_GROUND, 1.2f, 0.5f);
            world.playSound(floor, Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.5f);
        }
        if (t >= 28 && t <= 38 && t % 2 == 0) {
            double yaw = Math.toRadians(body.getLocation().getYaw());
            Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
            Location cross = floor.clone().add(forward.clone().multiply(3.5));
            line(cross, forward.clone().rotateAroundY(Math.PI / 4), 4.0, AZURE);
            line(cross, forward.clone().rotateAroundY(-Math.PI / 4), 4.0, CRIMSON);
        }
    }

    // ---------------------------------------------------------------- moves

    private void begin(Move next, AbstractBossSkill skill, SkillContext context, int length) {
        LivingEntity body = instance.getEntity();
        if (body == null || !body.isValid()) {
            return;
        }
        move = next;
        moveTick = 0;
        moveLength = length;
        moveSkill = skill;
        moveContext = context;
        ringCenter = null;
        restScale = AttributeUtil.getBase(body, AttributeUtil.scale(), 1.0);
    }

    private void tickMove(LivingEntity body) {
        int t = moveTick++;
        if (move != Move.CROUCH || t < TROLL_CROUCH) {
            hold(body);
        }
        switch (move) {
            case DRAW -> tickDraw(body, t);
            case PECK -> tickPeck(body, t);
            case CROUCH -> tickCrouch(body, t);
            case PULSE -> tickPulse(body, t);
            default -> {
            }
        }
        if (move != Move.NONE && t + 1 >= moveLength) {
            endMove(body);
        }
    }

    private void endMove(LivingEntity body) {
        if (move == Move.NONE) {
            return;
        }
        move = Move.NONE;
        moveSkill = null;
        moveContext = null;
        ringCenter = null;
        restoreScale(body);
        body.clearActiveItem();
        if (body instanceof Mob mob) {
            mob.setAggressive(false);
            mob.setAware(true);
        }
    }

    /** Skuldugery: draw, aim lane, ember, loose a three-arrow fan, lower the bow. */
    private void tickDraw(LivingEntity body, int t) {
        int draw = moveLength - 6;
        Player target = target(body, 48);
        World world = body.getWorld();
        Location at = body.getLocation();
        if (t <= draw) {
            face(body, target);
        }
        if (t == 0) {
            body.startUsingItem(EquipmentSlot.HAND);
            if (body instanceof Mob mob) {
                mob.setAggressive(true);
            }
            world.playSound(at, Sound.ITEM_CROSSBOW_LOADING_START, 0.9f, late() ? 0.8f : 0.7f);
        }
        if (t == draw - 8) {
            world.playSound(at, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 0.8f, 0.8f);
        }
        if (target != null && t >= draw - 8 && t < draw && t % 2 == 0) {
            Vector lane = target.getEyeLocation().toVector().subtract(bowTip(body).toVector());
            if (lane.lengthSquared() > 0.01) {
                dotted(bowTip(body), lane.normalize(), Math.min(9.0, lane.length()), SKULL);
            }
        }
        if (t == draw - 3) {
            world.spawnParticle(Particle.SMALL_FLAME, bowTip(body), 1, 0, 0, 0, 0);
            world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 0.3f, 1.2f);
        }
        if (t == draw) {
            body.clearActiveItem();
            if (target != null && moveSkill instanceof ArrowShotSkill bow) {
                Location from = body.getEyeLocation();
                Vector aim = target.getEyeLocation().toVector().subtract(from.toVector());
                if (aim.lengthSquared() > 0.01) {
                    aim.normalize();
                    for (int i = -1; i <= 1; i++) {
                        bow.loose(moveContext, from, aim.clone().rotateAroundY(i * SKULL_FAN), 0f);
                    }
                }
            }
            world.playSound(at, Sound.ENTITY_SKELETON_SHOOT, 1.15f, 0.9f);
            world.playSound(at, Sound.ITEM_CROSSBOW_SHOOT, 0.6f, 0.7f);
        }
    }

    /** McNugget: puffs up on three rising clucks, then three pecks, deflating one by one. */
    private void tickPeck(LivingEntity body, int t) {
        int tell = nuggetTell();
        World world = body.getWorld();
        Location at = body.getLocation();
        Player target = target(body, 40);
        face(body, target);
        if (t < tell) {
            setScale(body, 1.0 + 0.14 * easeOut(t / (double) tell));
            if (t == 0 || t == tell / 2 || t == tell - 2) {
                float pitch = t == 0 ? 0.9f : t == tell / 2 ? 1.1f : 1.3f;
                world.playSound(at, Sound.ENTITY_CHICKEN_AMBIENT, 1.1f, pitch);
            }
            return;
        }
        int shot = t - tell;
        if (shot % NUGGET_GAP == 0 && shot / NUGGET_GAP < 3) {
            int n = shot / NUGGET_GAP;
            setScale(body, 1.14 - 0.05 * (n + 1));
            if (target != null && moveSkill instanceof EggShotSkill eggs) {
                Location from = body.getEyeLocation();
                Vector aim = target.getEyeLocation().toVector().subtract(from.toVector());
                if (aim.lengthSquared() > 0.01) {
                    eggs.loose(moveContext, from, aim.normalize());
                }
            }
            world.playSound(at, Sound.ENTITY_EGG_THROW, 1.0f, 0.8f + n * 0.1f);
            world.playSound(at, Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.4f);
        }
    }

    /** Bridge Troll: squats and sizes up the target, then springs into the slam. */
    private void tickCrouch(LivingEntity body, int t) {
        World world = body.getWorld();
        Location at = body.getLocation();
        Player target = target(body, 40);
        if (t < TROLL_CROUCH) {
            face(body, target);
            setScale(body, 1.0 - 0.1 * easeOut(Math.min(1.0, t / 10.0)));
            if (t == 0) {
                world.playSound(at, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 0.9f, 0.6f);
                world.playSound(at, Sound.ITEM_ARMOR_EQUIP_GOLD, 0.8f, 0.6f);
            }
            if (t == 8) {
                world.playSound(at, Sound.BLOCK_CHAIN_PLACE, 0.6f, 0.5f);
            }
            if (target != null && t % 3 == 0 && moveSkill instanceof SlamLeapSkill slam) {
                ring(groundBelow(target.getLocation()), slam.getSlamRadius(), t >= TROLL_CROUCH - 4 ? EDGE : TROLL, 1.2f);
            }
            return;
        }
        if (t == TROLL_CROUCH) {
            setScale(body, 1.06);
            if (body instanceof Mob mob) {
                mob.setAware(true);
            }
            AbstractBossSkill skill = moveSkill;
            SkillContext context = moveContext;
            if (skill != null && context != null && !instance.isSlamPending()) {
                skill.execute(context);
            }
            return;
        }
        // Airborne now; let physics carry him, just settle the stretch.
        setScale(body, 1.0);
        if (body instanceof Mob mob) {
            mob.setAware(true);
        }
    }

    /** Hollow Lurker: goes still, eyes open, two heartbeats, then the rings roll out. */
    private void tickPulse(LivingEntity body, int t) {
        World world = body.getWorld();
        Location at = body.getLocation();
        if (t < LURKER_TELL) {
            face(body, target(body, 32));
            setScale(body, 1.0 - 0.08 * easeOut(Math.min(1.0, t / 8.0)));
            if (t == 0 || t == 7) {
                world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 0.95f, t == 0 ? 0.55f : 0.65f);
            }
            if (t == 10) {
                world.playSound(at, Sound.BLOCK_SCULK_CHARGE, 0.7f, 0.6f);
            }
            if (t >= 4 && t % 2 == 0) {
                eyes(body, 1.1f);
            }
            return;
        }
        if (t == LURKER_TELL) {
            setScale(body, 1.04);
            ringCenter = at.clone();
            if (moveSkill != null && moveContext != null) {
                moveSkill.execute(moveContext);
            }
            return;
        }
        if (t == LURKER_TELL + 2) {
            setScale(body, 1.0);
        }
        // Keep the wave edges on the floor through the windup so the gaps stay readable.
        if (t == LURKER_TELL + 5 && ringCenter != null && moveSkill instanceof RingBurstSkill ring) {
            int waves = Math.max(2, Math.min(8, ring.getWaves()));
            double step = Math.max(2.2, ring.getStep());
            for (int w = 1; w <= waves; w++) {
                ring(ringCenter, w * step, LURKER, 0.8f);
            }
        }
    }

    // ---------------------------------------------------------------- phase change

    /** If a phase change ends in a blast, the edge shows first; white on the last beat. */
    private void tickTransition(Kind k, LivingEntity body) {
        if (k == Kind.WARDEN) {
            return;
        }
        transitionTick++;
        BossPhase next = instance.getPendingPhase();
        PhaseTransition transition = next == null ? null : next.getTransition();
        if (transition == null || !transition.isEnabled()) {
            return;
        }
        int duration = Math.max(1, transition.getDurationTicks());
        double p = transitionTick / (double) duration;
        Location floor = groundBelow(body.getLocation());
        World world = body.getWorld();
        if (k == Kind.TROLL && transition.getShape() == TransitionShape.HOVER_STORM
                && p >= 0.38 && p < 0.6 && transitionTick % 4 == 0 && transition.getUnderRadius() > 0) {
            ring(floor, transition.getUnderRadius(), TROLL, 0.9f);
        }
        if (!transition.isExplode() || p < 0.6) {
            return;
        }
        int left = duration - transitionTick;
        boolean last = left <= 8;
        if (transitionTick % (last ? 2 : 3) == 0) {
            ring(floor, explodeRadius(transition.getExplodeRadius()), last ? EDGE : color(k), 1.3f);
        }
        if (left == 20) {
            world.playSound(floor, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
        if (left == 8) {
            world.playSound(floor, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.75f);
        }
    }

    // ---------------------------------------------------------------- helpers

    private Kind kind() {
        if (kind == null) {
            kind = Kind.of(instance.getTemplate() == null ? "" : instance.getTemplate().getId());
        }
        return kind;
    }

    private boolean busy() {
        return move != Move.NONE || (arrival >= 0 && kind() != Kind.WARDEN);
    }

    private boolean late() {
        BossPhase phase = instance.getCurrentPhase();
        return phase != null && phase.getHealthPercent() < 100.0;
    }

    private int nuggetTell() {
        return late() ? NUGGET_TELL_LATE : NUGGET_TELL;
    }

    private static Color color(Kind k) {
        return switch (k) {
            case SKULL -> SKULL;
            case LURKER -> LURKER;
            case NUGGET -> NUGGET;
            case TROLL -> TROLL;
            default -> EDGE;
        };
    }

    /** Stand still but keep physics: unaware mobs still fall and take knockback. */
    private static void hold(LivingEntity body) {
        if (body instanceof Mob mob) {
            mob.setAware(false);
        }
        Vector v = body.getVelocity();
        if (body.isOnGround() && (v.getX() != 0 || v.getZ() != 0)) {
            body.setVelocity(new Vector(0, v.getY(), 0));
        }
    }

    private static void face(LivingEntity body, Player target) {
        if (target == null) {
            return;
        }
        Vector to = target.getEyeLocation().toVector().subtract(body.getEyeLocation().toVector());
        if (to.lengthSquared() < 0.01) {
            return;
        }
        Location look = body.getLocation().setDirection(to);
        body.setRotation(look.getYaw(), look.getPitch());
        body.setBodyYaw(look.getYaw());
    }

    private void setScale(LivingEntity body, double multiplier) {
        if (restScale <= 0) {
            restScale = AttributeUtil.getBase(body, AttributeUtil.scale(), 1.0);
        }
        AttributeUtil.setBase(body, AttributeUtil.scale(), restScale * multiplier);
    }

    private void restoreScale(LivingEntity body) {
        if (restScale > 0) {
            AttributeUtil.setBase(body, AttributeUtil.scale(), restScale);
        }
        restScale = -1;
    }

    private static Location bowTip(LivingEntity body) {
        Location eye = body.getEyeLocation();
        return eye.add(eye.getDirection().multiply(0.9)).add(0, -0.35, 0);
    }

    private static void eyes(LivingEntity body, float size) {
        Location eye = body.getEyeLocation();
        double yaw = Math.toRadians(eye.getYaw());
        Vector side = new Vector(Math.cos(yaw), 0, Math.sin(yaw)).multiply(0.14 * Math.max(1.0, body.getHeight() / 2.0));
        Vector forward = eye.getDirection().setY(0).multiply(0.25);
        Particle.DustOptions glow = new Particle.DustOptions(LURKER_EYE, size);
        body.getWorld().spawnParticle(Particle.DUST, eye.clone().add(forward).add(side), 1, 0, 0, 0, 0, glow);
        body.getWorld().spawnParticle(Particle.DUST, eye.clone().add(forward).subtract(side), 1, 0, 0, 0, 0, glow);
    }

    private Player target(LivingEntity body, double range) {
        double r2 = range * range;
        if (body instanceof Mob mob && mob.getTarget() instanceof Player player && vulnerable(player)
                && player.getWorld().equals(body.getWorld())
                && player.getLocation().distanceSquared(body.getLocation()) <= r2) {
            return player;
        }
        Player best = null;
        double bestDist = r2;
        for (Player player : body.getWorld().getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            double d = player.getLocation().distanceSquared(body.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    private static boolean vulnerable(Player player) {
        return player != null
                && player.isValid()
                && !player.isDead()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static Location groundBelow(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return at.clone();
        }
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int top = (int) Math.floor(at.getY() - 0.05);
        for (int y = top; y > top - 14 && y > world.getMinHeight(); y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return new Location(world, at.getX(), y + 1.0, at.getZ(), at.getYaw(), 0f);
            }
        }
        return at.clone();
    }

    private static double easeOut(double t) {
        t = Math.max(0, Math.min(1, t));
        return 1.0 - Math.pow(1.0 - t, 3.0);
    }

    private static void ring(Location at, double radius, Color color, float size) {
        World world = at.getWorld();
        if (world == null || radius <= 0) {
            return;
        }
        int points = Math.max(18, (int) (radius * 5));
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, at.getX() + Math.cos(angle) * radius, at.getY() + 0.12,
                    at.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0, dust);
        }
    }

    private static void line(Location center, Vector dir, double half, Color color) {
        World world = center.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.3f);
        Vector step = dir.clone().normalize();
        for (double d = -half; d <= half; d += 0.5) {
            world.spawnParticle(Particle.DUST, center.clone().add(step.clone().multiply(d)).add(0, 0.12, 0),
                    1, 0, 0, 0, 0, dust);
        }
    }

    private static void dotted(Location from, Vector dir, double length, Color color) {
        World world = from.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.9f);
        for (double d = 1.0; d <= length; d += 1.3) {
            world.spawnParticle(Particle.DUST, from.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
        }
    }
}
