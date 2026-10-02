package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.model.BossPhase;
import de.aetherion.bossengine.util.AttributeUtil;
import de.aetherion.bossengine.util.TextUtil;
import de.aetherion.core.AetherKeys;

import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Aetherion, Sovereign of Ash: the Floor 3 dragon of the Throne of Ashes.
 *
 * He was a sky once. He burned it out, and the Throne was built around him as a prison.
 * Sovereign rules the air: a burning strafe, cinder rain, a marked talon dive, skyfire orbs.
 * At 66% the Throne answers the challengers: pylons erupt and chain him to the floor.
 * Chained he fights within reach: tail sweep, sweeping breath, eruption rings with gaps, lunge, chain lash.
 * The Last Sky (33%) is the signature: he snaps the chains, climbs into a darkened sky and kindles a sun.
 * The broken pylons become soul wards. The sun falls. Anyone outside a ward is burned to one heartbeat.
 * Unchained he burns: faster, lower, crowned in embers, and he falls on you like a meteor.
 * Death: the heart cracks, he crashes, and the Throne chains him down one last time.
 *
 * Tell language, identical in every phase:
 * ember ground = fire lands here, get out. Crimson ring = you are marked. White strobe = it fires now.
 * Soul blue = safety (gaps, wards). Green heart = opening, hit him now.
 *
 * The body is a vanilla Ender Dragon placed by this director every tick. Vanilla contact hits are
 * suppressed through {@link BossInstance#suppressesContactHits()} so every hit comes from a telegraphed move.
 */
final class AshenSovereignDirector {

    private static final String ID = "dungeon_aetherion";

    private static final Color EMBER = Color.fromRGB(255, 122, 32);
    private static final Color CINDER = Color.fromRGB(255, 70, 20);
    private static final Color HOT = Color.fromRGB(255, 255, 255);
    private static final Color GOLD = Color.fromRGB(255, 196, 64);
    private static final Color CRIMSON = Color.fromRGB(205, 18, 40);
    private static final Color ROYAL = Color.fromRGB(170, 90, 255);
    private static final Color ASH = Color.fromRGB(96, 90, 88);
    private static final Color SOUL = Color.fromRGB(90, 210, 255);
    private static final Color OPENING = Color.fromRGB(110, 255, 140);

    private static final int INTRO_TICKS = 96;
    private static final int INTRO_LAND = 70;
    private static final int THRONE_BASE = 120;
    private static final int LAST_SKY_BASE = 260;
    private static final int SKY_SUN = 84;
    private static final int SKY_WARDS = 92;
    private static final int SKY_FALL = 152;
    private static final int SKY_IMPACT = 206;
    private static final int DEATH_TICKS = 336;
    /* Throne Eclipse finale beats. Everything from ECLIPSE_HUSH on is visual only. */
    private static final int ECLIPSE_HUSH = 185;
    private static final int ECLIPSE_HAUL = 204;
    private static final int ECLIPSE_COLLAPSE = 224;
    private static final int ECLIPSE_SILENCE = 236;
    private static final int ECLIPSE_NOVA = 240;
    private static final int ECLIPSE_SHUT = 282;
    private static final int ECLIPSE_SETTLE = 310;
    private static final int ECLIPSE_CLEAR = 330;
    /** Spears of ash that fall in off the rim. Thin on purpose: they must never block the camera. */
    private static final Material[] ASH_IN = {
            Material.GRAY_CONCRETE, Material.DEEPSLATE, Material.BLACKSTONE, Material.BASALT};
    private static final int ASH_SPEARS = 12;
    /** The rebound the Throne throws back out, high enough that nobody ends up inside it. */
    private static final Material[] ASH_OUT = {
            Material.PURPLE_STAINED_GLASS, Material.MAGMA_BLOCK, Material.BLACK_CONCRETE};
    private static final double[] ASH_OUT_REACH = {1.0, 0.66, 1.35};
    private static final int[] ASH_OUT_TICKS = {16, 11, 22};
    /** Keep the Ender Dragon body clear of the Throne floor (hitbox sits low). */
    private static final double BODY_LIFT = 5.0;
    private static final int CHAINS = 3;
    private static final double LINK = 1.5;
    private static final double WARD_R = 3.4;
    private static final int MAX_BURNS = 48;
    private static final int MAX_DECALS = 26;

    private enum Form { SOVEREIGN, CHAINED, UNCHAINED }

    private enum Move { NONE, WAKE, CINDERFALL, TALON, VOLLEY, METEOR, LANDED, TAKEOFF, SWEEP, BREATH, ERUPTION, LUNGE, LASH }

    private enum Cinematic { NONE, THRONE, LAST_SKY, HOLD }

    private final BossInstance instance;

    /* Every non-rig display. Cleared as one list so nothing can leak. */
    private final List<Display> fx = new ArrayList<>();
    private final List<BlockDisplay> rig = new ArrayList<>();
    private final List<Ember> embers = new ArrayList<>();
    private final List<Shockwave> waves = new ArrayList<>();
    private final List<Lane> lanes = new ArrayList<>();
    private final List<RingBurst> ringBursts = new ArrayList<>();
    private final List<Burn> burns = new ArrayList<>();
    private final List<Debris> debris = new ArrayList<>();
    private final List<Chain> chains = new ArrayList<>();
    private final List<BlockDisplay> wardRings = new ArrayList<>();
    private final List<Location> wards = new ArrayList<>();
    private final List<BlockDisplay> fireWall = new ArrayList<>();
    private final BlockDisplay[] sunRays = new BlockDisplay[8];
    private final Map<String, Long> hitGate = new HashMap<>();
    private final Set<String> beats = new HashSet<>();
    private final Set<UUID> whooshHeard = new HashSet<>();
    private final Set<UUID> darkened = new HashSet<>();
    private final EnumSet<Move> taught = EnumSet.noneOf(Move.class);

    /* Arena, measured once from the spawn point. */
    private Location center;
    private double floorY;
    private double arenaRadius = 22.0;
    private double skyRise = 24.0;
    private double anchorR = 12.0;

    /* Body. */
    private Location pos;
    private Vector heading = new Vector(0, 0, 1);
    private Vector vel = new Vector();
    private boolean grounded;
    private final Vector[] partOffset = new Vector[8];
    private boolean partsLive;

    /* Rig. */
    private BlockDisplay heartCore;
    private BlockDisplay heartShell;
    private final List<BlockDisplay> crown = new ArrayList<>();
    private int crownShown;
    private double heartBoost;
    private double heartKick;
    private Material heartCoreMat;
    private Material heartShellMat;

    /* Encounter state. */
    private long clock;
    private boolean introDone;
    private int introTick = -1;
    private double introAngle;
    private boolean openingMove = true;
    private Move move = Move.NONE;
    private int actionTick;
    private UUID moveTarget;
    private int breath;
    private int wakeCd;
    private int cinderCd;
    private int talonCd;
    private int volleyCd;
    private int meteorCd;
    private int sweepCd;
    private int breathCd;
    private int eruptCd;
    private int lungeCd;
    private int lashCd;
    private int exposedTicks;
    private double exposedMul = 1.0;
    private double cruiseAngle;
    private int cruiseDir = 1;
    private double cruiseR = 12.0;
    private double cruiseH = 11.0;

    /* Per-move scratch. */
    private int windup;
    private Location mark;
    private Location from;
    private Location to;
    private Vector aim = new Vector(0, 0, 1);
    private Vector sweepFrom = new Vector(0, 0, 1);
    private double laneLen;
    private int passes;
    private int passTicks;
    private int sign = 1;
    private int volleyLeft;
    private int landedFor;
    private boolean takeoffAfter;
    private Location lastBreathGround;
    private double lastBurnAt;
    private Chain lashChain;

    /* Cinematics. */
    private Cinematic cinematic = Cinematic.NONE;
    private int beatFloor;
    private Location cinStart;
    private Location sunPos;
    private BlockDisplay sunCore;
    private BlockDisplay sunShell;
    private BlockDisplay sunHalo;
    private float sunSpin;
    private double wallRadius = -1;
    private int deathTick = -1;
    private Location restAt;
    private Location deathSky;
    private Location heartFloat;
    /** One Sovereign Reliquary per death cinematic (Ashen Void / live anchor). */
    private boolean deathChestPlaced;

    /* Throne Eclipse finale: purely visual. Nothing here touches a player. Every display is also in fx. */
    private final List<BlockDisplay> ashIn = new ArrayList<>();
    private final List<Location> ashInFrom = new ArrayList<>();
    private final List<BlockDisplay> ashOut = new ArrayList<>();
    private final List<SealPiece> seal = new ArrayList<>();
    private final List<SealPiece> corona = new ArrayList<>();
    private final List<BlockDisplay> skyChains = new ArrayList<>();
    private final List<Location> skyChainFoot = new ArrayList<>();
    private final List<Vector> skyChainDir = new ArrayList<>();
    private final BlockDisplay[] eclipseDisc = new BlockDisplay[4];
    private BlockDisplay tearGash;
    private BlockDisplay tearCore;
    private BlockDisplay eclipseSeed;
    private Location eclipseSky;
    private double tearYaw;
    private double tearLen;

    AshenSovereignDirector(BossInstance instance) {
        this.instance = instance;
    }

    // ------------------------------------------------------------------ BossInstance hooks

    boolean isMine() {
        return instance.getTemplate() != null && ID.equalsIgnoreCase(instance.getTemplate().getId());
    }

    boolean isDying() {
        return deathTick >= 0;
    }

    /** The director flies the body at all times; vanilla dragon AI only animates it. */
    boolean holdsBody() {
        return isMine();
    }

    /** The descent is a cinematic: nothing to hit until he has landed and roared. */
    boolean blocksDamage() {
        return isMine() && introTick >= 0;
    }

    double scaleIncoming(double amount) {
        if (!isMine() || exposedTicks <= 0) {
            return amount;
        }
        return amount * exposedMul;
    }

    void onDamaged(double amount) {
        if (!isMine() || amount <= 0) {
            return;
        }
        heartKick = Math.min(0.18, heartKick + 0.05);
    }

    void onBind() {
        if (!isMine()) {
            return;
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null) {
            return;
        }
        clearCombatFx();
        clearRig();
        if (center == null) {
            measureArena();
        }
        clearArenaVictoryChest();
        entity.setGravity(false);
        if (entity instanceof Mob mob) {
            mob.setAware(false);
        }
        pos = entity.getLocation().clone();
        vel = new Vector();
        deathTick = -1;
        exposedTicks = 0;
        move = Move.NONE;
        actionTick = 0;
        if (!introDone) {
            beginIntro(entity);
            return;
        }
        spawnRig();
        if (form() == Form.CHAINED && !instance.isTransitioning()) {
            spawnChains(true);
            grounded = true;
        }
        resetCooldowns();
    }

    void abort() {
        clearCombatFx();
        clearRig();
        stopWhoosh();
        restoreSky();
        deathTick = -1;
        introTick = -1;
        move = Move.NONE;
        cinematic = Cinematic.NONE;
        deathChestPlaced = false;
    }

    boolean beginDeath() {
        if (!isMine() || isDying()) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        if (center == null) {
            measureArena();
        }
        if (center == null || center.getWorld() == null) {
            return false;
        }
        clearCombatFx();
        stopWhoosh();
        restoreSky();
        introTick = -1;
        cinematic = Cinematic.NONE;
        exposedTicks = 0;
        move = Move.NONE;
        deathChestPlaced = false;
        clearArenaVictoryChest();
        if (entity != null && entity.isValid()) {
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setVelocity(new Vector());
            entity.setCustomNameVisible(false);
            pos = entity.getLocation().clone();
            if (heartCore == null || !heartCore.isValid()) {
                spawnRig();
            }
        }
        if (pos == null) {
            pos = center.clone().add(0, 8, 0);
        }
        // Crash / bind / supernova all play on the Sovereign Anchor — not an offset pad.
        restAt = ground(center);
        deathSky = center.clone().add(0, Math.min(14.0, skyRise * 0.6), 0);
        heartFloat = null;
        deathTick = 0;
        World world = center.getWorld();
        world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        world.playSound(pos, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.5f);
        world.playSound(pos, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 0.5f);
        shout("&5&lAetherion&7: &fNo— not the Throne— &cnot again—");
        return true;
    }

    /**
     * @return true when the death cinematic finished and the body should die
     */
    boolean tick() {
        if (!isMine()) {
            return false;
        }
        if (isDying()) {
            return tickDeath();
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid()) {
            return false;
        }
        clock++;
        if (center == null) {
            measureArena();
        }
        if (center == null) {
            return false;
        }
        if (pos == null || pos.getWorld() != entity.getWorld()) {
            pos = entity.getLocation().clone();
        }
        readParts(entity);
        if (entity.hasGravity()) {
            entity.setGravity(false);
        }
        entity.setFireTicks(0);
        if (introTick >= 0) {
            tickIntro(entity);
            tickHazards(entity);
            syncRig();
            return false;
        }
        if (instance.isTransitioning()) {
            tickHazards(entity);
            return false;
        }
        Form form = form();
        tickCooldowns();
        tickHazards(entity);
        if (move != Move.NONE) {
            tickMove(entity, form);
        } else {
            maybeStartMove(entity, form);
            if (move == Move.NONE) {
                idle(entity, form);
            }
        }
        tickChains();
        ambient(form);
        syncRig();
        return false;
    }

    void beginTransition(BossPhase next) {
        if (!isMine() || next == null) {
            return;
        }
        LivingEntity entity = instance.getEntity();
        cancelMove();
        fizzleHazards();
        exposedTicks = 0;
        beats.clear();
        beatFloor = 0;
        if (center == null) {
            measureArena();
        }
        if (pos == null && entity != null) {
            pos = entity.getLocation().clone();
        }
        cinStart = pos != null ? pos.clone() : center.clone();
        Form now = form();
        Form after = formOf(next);
        if (after == Form.CHAINED && now == Form.SOVEREIGN) {
            cinematic = Cinematic.THRONE;
        } else if (after == Form.UNCHAINED && now != Form.UNCHAINED) {
            cinematic = Cinematic.LAST_SKY;
        } else {
            cinematic = Cinematic.HOLD;
        }
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        Location at = cinStart;
        if (cinematic == Cinematic.THRONE) {
            world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.7f);
            shout("&5&lAetherion&7: &fEnough games. &dI will show you the whole sky—");
        } else if (cinematic == Cinematic.LAST_SKY) {
            world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
            world.playSound(at, Sound.BLOCK_CHAIN_BREAK, 1.4f, 0.5f);
            shout("&5&lAetherion&7: &cChains? &fI was a sky before this Throne was a pebble.");
        } else {
            world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f, 0.8f);
        }
    }

    void tickTransition(int tick, int duration) {
        if (!isMine()) {
            return;
        }
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || center == null) {
            return;
        }
        if (cinematic == Cinematic.NONE) {
            // Body was rebuilt mid-transition: resume without replaying beats already past.
            beginTransition(instance.getPendingPhase());
            int base = cinematic == Cinematic.LAST_SKY ? LAST_SKY_BASE : THRONE_BASE;
            beatFloor = scaled(tick, duration, base) + 1;
        }
        switch (cinematic) {
            case THRONE -> tickThrone(entity, scaled(tick, duration, THRONE_BASE));
            case LAST_SKY -> tickLastSky(entity, scaled(tick, duration, LAST_SKY_BASE));
            default -> tickHold(entity, tick);
        }
        tickChains();
        syncRig();
    }

    void finishTransition() {
        if (!isMine()) {
            return;
        }
        stopWhoosh();
        Cinematic done = cinematic;
        cinematic = Cinematic.NONE;
        heartBoost = 0.0;
        if (done == Cinematic.THRONE) {
            grounded = true;
            if (!chainsAlive()) {
                spawnChains(true);
            }
            for (Chain chain : chains) {
                chain.reach = 1.0;
            }
            sweepCd = 30;
            breathCd = 60;
            eruptCd = 110;
            lungeCd = 80;
            lashCd = 140;
            breath = 20;
        } else if (done == Cinematic.LAST_SKY) {
            clearSky();
            restoreSky();
            grounded = false;
            crownShown = 5;
            meteorCd = 220;
            wakeCd = 40;
            cinderCd = 120;
            talonCd = 80;
            volleyCd = 60;
            breath = 20;
        }
    }

    // ------------------------------------------------------------------ arena

    private void measureArena() {
        Location spawn = instance.getSpawnLocation();
        World world = spawn.getWorld();
        if (world == null) {
            return;
        }
        Double floor = floorAt(world, spawn.getX(), spawn.getZ(), spawn.getY());
        floorY = floor == null ? spawn.getY() : floor;
        // Lift the combat center so the dragon hitbox never sits inside the Throne floor.
        center = new Location(world, spawn.getX(), floorY + BODY_LIFT, spawn.getZ());
        List<Double> edges = new ArrayList<>();
        int open = 0;
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            double edge = -1;
            for (double r = 2.0; r <= 40.0; r += 1.0) {
                Double y = floorAt(world, center.getX() + Math.cos(a) * r, center.getZ() + Math.sin(a) * r, floorY);
                if (y == null || Math.abs(y - floorY) > 3.0) {
                    edge = r - 1.0;
                    break;
                }
            }
            if (edge < 0) {
                open++;
            } else {
                edges.add(edge);
            }
        }
        if (open >= 24 || edges.isEmpty()) {
            arenaRadius = 26.0;
        } else {
            edges.sort(Double::compare);
            arenaRadius = clamp(edges.get((int) (edges.size() * 0.3)), 10.0, 34.0);
        }
        int top = -1;
        for (int dy = 4; dy <= 60; dy++) {
            if (!world.getBlockAt(center.getBlockX(), (int) Math.floor(floorY) + dy, center.getBlockZ()).isPassable()) {
                top = dy;
                break;
            }
        }
        skyRise = top < 0 ? 30.0 : clamp(top - 4.0, 10.0, 34.0);
        anchorR = clamp(arenaRadius * 0.5, 9.0, 15.0);
    }

    private Location anchor(int index) {
        double a = Math.toRadians(90 + index * (360.0 / CHAINS));
        return ground(center.clone().add(Math.cos(a) * anchorR, 0, Math.sin(a) * anchorR));
    }

    private double leashR() {
        return Math.max(4.0, anchorR * 0.55);
    }

    // ------------------------------------------------------------------ body

    /** Puts the dragon at {@code at}, head pointing along {@code face}. Vanilla renders dragons facing backwards. */
    private void moveBody(LivingEntity entity, Location at, Vector face) {
        if (face != null) {
            Vector flat = face.clone().setY(0);
            if (flat.lengthSquared() > 1.0E-4) {
                heading = flat.normalize();
            }
        }
        Location dest = at.clone();
        dest.setYaw(wrapDeg(yawOf(heading) + 180.0f));
        dest.setPitch(0.0f);
        pos = dest.clone();
        if (entity instanceof EnderDragon dragon) {
            // Re-entering HOVER re-seeds its fly target to where we just put him, so vanilla never tugs back.
            dragon.setPhase(EnderDragon.Phase.CIRCLING);
            dragon.setPhase(EnderDragon.Phase.HOVER);
            nmsMoveTo(dragon, dest);
            if (dragon.getLocation().distanceSquared(dest) > 0.05) {
                instance.runInternalTeleport(() -> dragon.teleport(dest));
            }
            dragon.setVelocity(new Vector());
            dragon.setRotation(dest.getYaw(), 0.0f);
            return;
        }
        instance.runInternalTeleport(() -> {
            if (entity.isValid()) {
                entity.teleport(dest);
            }
        });
        entity.setVelocity(new Vector());
    }

    /** Banking flight: velocity steers toward the goal so turns read as curves, never snaps. */
    private void fly(LivingEntity entity, Location goal, double speed, double turn) {
        Vector want = goal.toVector().subtract(pos.toVector());
        double d = want.length();
        Vector desired = d < 1.0E-3 ? new Vector() : want.multiply(Math.min(speed, d * 0.3 + 0.04) / d);
        vel = vel.clone().multiply(1.0 - turn).add(desired.multiply(turn));
        Location next = pos.clone().add(vel);
        double minY = groundY(next.getX(), next.getZ()) + 1.2;
        if (next.getY() < minY) {
            next.setY(minY);
        }
        clampToArena(next, -4.0);
        Vector face = vel.clone().setY(0);
        moveBody(entity, next, face.lengthSquared() > 0.0025 ? steer(heading, face.normalize(), 0.35) : null);
    }

    private void holdAt(LivingEntity entity, Location at, Vector face, double bobAmp) {
        Location hold = at.clone().add(0, Math.sin(clock * 0.09) * bobAmp, 0);
        vel = new Vector();
        moveBody(entity, hold, face);
    }

    private void turnToward(LivingEntity entity, Location target, float maxDeg) {
        Vector want = target.toVector().subtract(pos.toVector()).setY(0);
        if (want.lengthSquared() < 0.01) {
            return;
        }
        float wanted = yawOf(want);
        float now = yawOf(heading);
        float delta = clampF(wrapDeg(wanted - now), -maxDeg, maxDeg);
        moveBody(entity, pos, dirOf(now + delta));
    }

    private void readParts(LivingEntity entity) {
        partsLive = false;
        if (!(entity instanceof EnderDragon dragon)) {
            return;
        }
        Location base = entity.getLocation();
        int i = 0;
        for (ComplexEntityPart part : dragon.getParts()) {
            if (i >= partOffset.length) {
                break;
            }
            partOffset[i++] = part.getLocation().toVector().subtract(base.toVector());
        }
        partsLive = i >= 8;
    }

    /** 0 head · 1 neck · 2 body · 3-5 tail · 6-7 wings. Falls back to a heading model if parts are missing. */
    private Location partPos(int index) {
        if (partsLive && partOffset[index] != null) {
            return pos.clone().add(partOffset[index]);
        }
        Vector right = right(heading);
        return switch (index) {
            case 0 -> pos.clone().add(heading.clone().multiply(6.5)).add(0, 1.2, 0);
            case 1 -> pos.clone().add(heading.clone().multiply(4.0)).add(0, 1.2, 0);
            case 3, 4, 5 -> pos.clone().add(heading.clone().multiply(-2.5 * (index - 2))).add(0, 1.0, 0);
            case 6 -> pos.clone().add(right.multiply(4.5)).add(0, 2.0, 0);
            case 7 -> pos.clone().add(right.multiply(-4.5)).add(0, 2.0, 0);
            default -> pos.clone().add(0, 0.5, 0);
        };
    }

    private Location headPos() {
        return partPos(0).add(0, 0.5, 0);
    }

    private Location heartPos() {
        return partPos(2).add(0, 1.0, 0).add(heading.clone().multiply(1.4));
    }

    // ------------------------------------------------------------------ intro

    private void beginIntro(LivingEntity entity) {
        introTick = 0;
        grounded = false;
        entity.setCustomNameVisible(false);
        introAngle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        Location start = center.clone().add(Math.cos(introAngle) * 20.0, skyRise + 6.0, Math.sin(introAngle) * 20.0);
        moveBody(entity, start, new Vector(-Math.sin(introAngle), 0, Math.cos(introAngle)));
    }

    /*
     * 0-70 a great spiral down through falling ash · 70 he lands on the Throne, the floor cracks ·
     * 76 the roar, the heart ignites · 80 name card · 96 he takes the sky.
     */
    private void tickIntro(LivingEntity entity) {
        introTick++;
        World world = center.getWorld();
        int t = introTick;
        if (t == 1) {
            startWhoosh(center, 1.0f, 0.6f);
            world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.55f);
        }
        if (t % 2 == 0) {
            world.spawnParticle(Particle.WHITE_ASH, center.clone().add(0, 8, 0), 26, arenaRadius * 0.6, 6, arenaRadius * 0.6, 0.01, null, true);
        }
        if (t <= INTRO_LAND) {
            double u = easeInOut(t / (double) INTRO_LAND);
            double a = introAngle + u * Math.PI * 2.2;
            double r = 20.0 * (1.0 - u);
            double h = (skyRise + 6.0) * (1.0 - u);
            Location perch = introPerch();
            Location at = center.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
            // Last beat: pull hard onto the floor perch so HOVER never freezes a few blocks high.
            if (t > INTRO_LAND - 12) {
                double blend = (t - (INTRO_LAND - 12)) / 12.0;
                blend = easeInOut(blend);
                at.setX(at.getX() + (perch.getX() - at.getX()) * blend);
                at.setY(at.getY() + (perch.getY() - at.getY()) * blend);
                at.setZ(at.getZ() + (perch.getZ() - at.getZ()) * blend);
                Player near = nearest(center, fighters());
                if (near != null) {
                    moveBody(entity, at, steer(heading, flatDir(center, near.getLocation(), heading), 0.2));
                } else {
                    moveBody(entity, at, new Vector(-Math.sin(a), -0.35, Math.cos(a)));
                }
            } else {
                moveBody(entity, at, new Vector(-Math.sin(a), -0.2, Math.cos(a)));
            }
            if (t % 14 == 0) {
                world.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, 0.8f);
            }
            if (t > 30) {
                world.spawnParticle(Particle.FLAME, partPos(6), 2, 0.6, 0.2, 0.6, 0.01, null, true);
                world.spawnParticle(Particle.FLAME, partPos(7), 2, 0.6, 0.2, 0.6, 0.01, null, true);
            }
        }
        if (t == INTRO_LAND) {
            stopWhoosh();
            Location land = introPerch();
            // Hard snap onto the Throne floor — never leave the body mid-air from a soft HOVER seek.
            snapBody(entity, land, heading);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, land.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0, null, true);
            floorBurst(world, land, 90, 2.4);
            world.spawnParticle(Particle.LAVA, land.clone().add(0, 0.4, 0), 30, 2.0, 0.3, 2.0, 0, null, true);
            world.playSound(land, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.5f);
            world.playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.55f);
            world.playSound(land, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
            waves.add(new Shockwave(land, 1.0, 0.9, arenaRadius, EMBER, 0.0, 0));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + 0.4;
                addBurn(land.clone().add(Math.cos(a) * 4.0, 0, Math.sin(a) * 4.0), 1.6, 80, true, false);
            }
            for (Player player : fighters()) {
                Vector out = away(player.getLocation(), land);
                if (horizontal(player.getLocation(), land) < 7.0) {
                    player.setVelocity(out.multiply(0.8).setY(0.45));
                }
            }
            grounded = true;
            spawnRig();
            heartBoost = 0.8;
        }
        if (t > INTRO_LAND) {
            heartBoost *= 0.92;
            Location perch = introPerch();
            Player near = nearest(center, fighters());
            if (near != null) {
                holdAt(entity, perch, flatDir(perch, near.getLocation(), heading), 0.0);
            } else {
                holdAt(entity, perch, heading, 0.0);
            }
        }
        if (t == 76) {
            Location head = headPos();
            world.playSound(head, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.55f);
            world.playSound(head, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 0.6f);
            burst(world, head, Particle.FLAME, 60, 0.4);
            world.spawnParticle(Particle.FLASH, heartPos(), 1, 0, 0, 0, 0, null, true);
            heartBoost = 1.0;
        }
        if (t == 80) {
            titleNear("&5&l✦ AETHERION ✦", "&7Sovereign of the Ashen Sky");
            shout("&5&lAetherion&7: &fA dungeon. How quaint. &cKneel, or burn standing.");
        }
        if (t >= INTRO_TICKS) {
            introTick = -1;
            introDone = true;
            entity.setCustomNameVisible(instance.getTemplate().getOptions().isCustomNameVisible());
            resetCooldowns();
            breath = 24;
        }
    }

    // ------------------------------------------------------------------ core loop

    private void resetCooldowns() {
        wakeCd = 20;
        cinderCd = 120;
        talonCd = 80;
        volleyCd = 60;
        meteorCd = 200;
        sweepCd = 30;
        breathCd = 60;
        eruptCd = 110;
        lungeCd = 80;
        lashCd = 140;
        breath = 30;
    }

    private void tickCooldowns() {
        wakeCd = Math.max(0, wakeCd - 1);
        cinderCd = Math.max(0, cinderCd - 1);
        talonCd = Math.max(0, talonCd - 1);
        volleyCd = Math.max(0, volleyCd - 1);
        meteorCd = Math.max(0, meteorCd - 1);
        sweepCd = Math.max(0, sweepCd - 1);
        breathCd = Math.max(0, breathCd - 1);
        eruptCd = Math.max(0, eruptCd - 1);
        lungeCd = Math.max(0, lungeCd - 1);
        lashCd = Math.max(0, lashCd - 1);
        if (move == Move.NONE && breath > 0) {
            breath--;
        }
        if (exposedTicks > 0) {
            exposedTicks--;
        }
        heartKick *= 0.82;
        heartBoost *= 0.94;
    }

    private int breathFor(Form form) {
        return switch (form) {
            case SOVEREIGN -> 36;
            case CHAINED -> 26;
            case UNCHAINED -> 22;
        };
    }

    private void maybeStartMove(LivingEntity entity, Form form) {
        if (breath > 0) {
            return;
        }
        List<Player> fighters = fighters();
        if (fighters.isEmpty()) {
            return;
        }
        Player near = nearest(pos, fighters);
        double nd = near == null ? 99.0 : horizontal(near.getLocation(), pos);
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (form == Form.CHAINED) {
            int behind = 0;
            int crowd = 0;
            for (Player player : fighters) {
                double d = horizontal(player.getLocation(), pos);
                if (d <= 9.0 && angleFrom(heading, pos, player.getLocation()) > 110.0) {
                    behind++;
                }
                if (d <= 8.0) {
                    crowd++;
                }
            }
            if (behind >= 1 && sweepCd <= 0 && roll < 70) {
                beginSweep(false);
            } else if (nd > 11.0 && lungeCd <= 0) {
                beginLunge(near);
            } else if (eruptCd <= 0 && (crowd >= 2 || roll < 30)) {
                beginEruption();
            } else if (breathCd <= 0 && roll < 55) {
                beginBreath(pick(fighters));
            } else if (lashCd <= 0 && roll < 80 && chainsAlive()) {
                beginLash();
            } else if (breathCd <= 0) {
                beginBreath(pick(fighters));
            } else if (sweepCd <= 0 && nd < 9.0) {
                beginSweep(false);
            }
            return;
        }
        if (grounded) {
            beginTakeoff();
            return;
        }
        if (openingMove) {
            openingMove = false;
            beginWake(fighters, 1);
            return;
        }
        if (form == Form.SOVEREIGN) {
            int under = 0;
            for (Player player : fighters) {
                if (horizontal(player.getLocation(), pos) <= 6.0) {
                    under++;
                }
            }
            if (under >= 2 && talonCd <= 0) {
                beginTalon(pick(fighters));
            } else if (cinderCd <= 0 && roll < 35) {
                beginCinderfall(fighters, false);
            } else if (wakeCd <= 0 && roll < 60) {
                beginWake(fighters, 1);
            } else if (talonCd <= 0 && roll < 82) {
                beginTalon(pick(fighters));
            } else if (volleyCd <= 0) {
                beginVolley(3);
            } else if (wakeCd <= 0) {
                beginWake(fighters, 1);
            }
            return;
        }
        if (meteorCd <= 0 && roll < 32) {
            beginMeteor(pick(fighters));
        } else if (wakeCd <= 0 && roll < 52) {
            beginWake(fighters, 2);
        } else if (cinderCd <= 0 && roll < 66) {
            beginCinderfall(fighters, true);
        } else if (talonCd <= 0 && roll < 86) {
            beginTalon(pick(fighters));
        } else if (volleyCd <= 0) {
            beginVolley(5);
        } else if (wakeCd <= 0) {
            beginWake(fighters, 2);
        }
    }

    private void tickMove(LivingEntity entity, Form form) {
        actionTick++;
        switch (move) {
            case WAKE -> tickWake(entity, form);
            case CINDERFALL -> tickCinderfall(entity);
            case TALON -> tickTalon(entity, form);
            case VOLLEY -> tickVolley(entity);
            case METEOR -> tickMeteor(entity);
            case LANDED -> tickLanded(entity);
            case TAKEOFF -> tickTakeoff(entity);
            case SWEEP -> tickSweep(entity);
            case BREATH -> tickBreath(entity);
            case ERUPTION -> tickEruption(entity);
            case LUNGE -> tickLunge(entity, form);
            case LASH -> tickLash(entity);
            default -> endMove();
        }
    }

    private void start(Move next, Player target) {
        move = next;
        actionTick = 0;
        moveTarget = target == null ? null : target.getUniqueId();
    }

    private void endMove() {
        move = Move.NONE;
        actionTick = 0;
        lashChain = null;
        breath = breathFor(form());
    }

    private void cancelMove() {
        move = Move.NONE;
        actionTick = 0;
        lashChain = null;
        takeoffAfter = false;
        stopWhoosh();
    }

    private Player target() {
        if (moveTarget == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(moveTarget);
        return vulnerable(player) && player.getWorld().equals(center.getWorld()) ? player : null;
    }

    private void teach(Move which, String title, String sub) {
        if (taught.add(which)) {
            titleNear(title, sub);
        } else {
            actionBar(sub);
        }
    }

    private Form form() {
        return formOf(instance.getCurrentPhase());
    }

    /** Named phases first; any other YAML falls back to health bands so an old template still reads right. */
    private static Form formOf(BossPhase phase) {
        if (phase == null) {
            return Form.SOVEREIGN;
        }
        String id = phase.getId() == null ? "" : phase.getId().toLowerCase(Locale.ROOT);
        switch (id) {
            case "unchained" -> {
                return Form.UNCHAINED;
            }
            case "chained" -> {
                return Form.CHAINED;
            }
            case "sovereign" -> {
                return Form.SOVEREIGN;
            }
            default -> {
                double pct = phase.getHealthPercent();
                return pct <= 40.0 ? Form.UNCHAINED : pct <= 70.0 ? Form.CHAINED : Form.SOVEREIGN;
            }
        }
    }

    private void expose(int ticks, double mul) {
        exposedTicks = Math.max(exposedTicks, ticks);
        exposedMul = mul;
        actionBar("&a✦ OPENING &8| &fhis heart is bare — strike now");
        World world = center.getWorld();
        world.playSound(heartPos(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.8f);
        world.playSound(heartPos(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.6f);
    }

    // ------------------------------------------------------------------ idle

    private void idle(LivingEntity entity, Form form) {
        if (form == Form.CHAINED) {
            stalk(entity);
            return;
        }
        if (grounded) {
            holdAt(entity, pos, heading, 0.0);
            return;
        }
        boolean fast = form == Form.UNCHAINED;
        if (clock % 140 == 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            cruiseR = arenaRadius * (0.35 + random.nextDouble() * 0.25);
            cruiseH = fast ? 8.5 + random.nextDouble() * 3.5 : 11.0 + random.nextDouble() * 4.0;
            if (random.nextDouble() < 0.35) {
                cruiseDir = -cruiseDir;
            }
        }
        cruiseAngle += (fast ? 0.032 : 0.022) * cruiseDir;
        Location goal = center.clone().add(
                Math.cos(cruiseAngle) * cruiseR,
                cruiseH + Math.sin(cruiseAngle * 1.7) * 1.4,
                Math.sin(cruiseAngle) * cruiseR);
        fly(entity, goal, fast ? 0.78 : 0.6, 0.12);
    }

    /** Chained: he prowls toward the nearest fighter until the chains stop him. */
    private void stalk(LivingEntity entity) {
        Player near = nearest(pos, fighters());
        if (near == null) {
            holdAt(entity, ground(pos), heading, 0.0);
            return;
        }
        Location goal = near.getLocation();
        Vector to = goal.toVector().subtract(pos.toVector()).setY(0);
        double d = to.length();
        Vector face = steer(heading, to.lengthSquared() > 0.01 ? to.clone().normalize() : heading, 0.08);
        Location next = pos.clone();
        if (d > 6.0) {
            next.add(face.clone().multiply(0.11));
        }
        boolean strained = leash(next);
        next.setY(approach(pos.getY(), groundY(next.getX(), next.getZ()), 0.3));
        moveBody(entity, next, face);
        if (strained && clock % 6 == 0) {
            strainSparks();
        }
        if (d > 6.0 && clock % 18 == 0) {
            World world = center.getWorld();
            world.playSound(pos, Sound.ENTITY_RAVAGER_STEP, 1.2f, 0.5f);
            floorBurst(world, pos, 10, 1.4);
        }
    }

    /** Keeps a chained body inside the chain slack. */
    private boolean leash(Location next) {
        if (!chainsAlive()) {
            return false;
        }
        double dx = next.getX() - center.getX();
        double dz = next.getZ() - center.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double max = leashR();
        if (d <= max) {
            return false;
        }
        next.setX(center.getX() + dx / d * max);
        next.setZ(center.getZ() + dz / d * max);
        return true;
    }

    // ------------------------------------------------------------------ ASHEN WAKE

    private void beginWake(List<Player> fighters, int count) {
        Player target = pick(fighters);
        start(Move.WAKE, target);
        passes = count;
        windup = form() == Form.UNCHAINED ? 26 : 34;
        planWake(target == null ? center : target.getLocation(), null);
        wakeCd = form() == Form.UNCHAINED ? 170 : 220;
        teach(Move.WAKE, "&6&lASHEN WAKE", "&7He burns a lane across the Throne. &fLeave the lane.");
    }

    /** A burning chord of the arena through {@code through}; perpendicular to {@code cross} if given. */
    private void planWake(Location through, Vector cross) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector dir;
        if (cross != null) {
            dir = rotateY(cross, Math.PI / 2 * (random.nextBoolean() ? 1 : -1));
        } else if (horizontal(through, center) > 3.0) {
            dir = rotateY(flatDir(center, through, heading), random.nextDouble(-0.5, 0.5));
        } else {
            dir = rotateY(new Vector(1, 0, 0), random.nextDouble(Math.PI * 2));
        }
        dir.setY(0).normalize();
        double reach = arenaRadius - 3.0;
        Vector rel = through.toVector().subtract(center.toVector()).setY(0);
        double b = rel.dot(dir);
        double c = rel.lengthSquared() - reach * reach;
        double disc = b * b - c;
        Location t = through.clone();
        if (disc < 0) {
            t = center.clone();
            b = 0;
            disc = reach * reach;
        }
        double root = Math.sqrt(disc);
        from = ground(t.clone().add(dir.clone().multiply(-b - root)));
        to = ground(t.clone().add(dir.clone().multiply(-b + root)));
        aim = dir;
        laneLen = Math.max(6.0, horizontal(from, to));
        double speed = form() == Form.UNCHAINED ? 1.25 : 1.05;
        passTicks = (int) Math.ceil(laneLen / speed);
        lastBreathGround = null;
        lastBurnAt = -99;
    }

    private void tickWake(LivingEntity entity, Form form) {
        World world = center.getWorld();
        int t = actionTick;
        if (t <= windup) {
            Location stage = from.clone().add(aim.clone().multiply(-7.0)).add(0, 9.0, 0);
            fly(entity, stage, form == Form.UNCHAINED ? 1.1 : 0.9, 0.3);
            int remaining = windup - t;
            drawLane(from, aim, laneLen, 2.6, t / (double) windup, t, remaining, EMBER);
            fuse(from, remaining);
            if (t == windup - 12) {
                world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 0.8f);
            }
            if (t == windup) {
                startWhoosh(from, 1.0f, 0.9f);
                world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.8f, 0.7f);
                world.playSound(pos, Sound.ITEM_FIRECHARGE_USE, 1.4f, 0.6f);
                cinStart = pos.clone();
            }
            return;
        }
        int p = t - windup;
        if (p <= passTicks) {
            double u = p / (double) passTicks;
            Location lane = lerp(from, to, u).add(0, 4.2, 0);
            Location at = p <= 6 ? lerp(cinStart, lane, p / 6.0) : lane;
            vel = aim.clone().multiply(laneLen / passTicks);
            moveBody(entity, at, aim);
            Location g = ground(lerp(from, to, u));
            Location head = headPos();
            for (int i = 1; i <= 5; i++) {
                Location q = lerp(head, g, i / 5.0);
                world.spawnParticle(Particle.FLAME, q, 2, 0.25, 0.25, 0.25, 0.02, null, true);
            }
            world.spawnParticle(Particle.FLAME, g.clone().add(0, 0.3, 0), 8, 1.2, 0.2, 1.2, 0.04, null, true);
            world.spawnParticle(Particle.LARGE_SMOKE, g.clone().add(0, 0.8, 0), 2, 0.8, 0.4, 0.8, 0.02);
            if (p % 2 == 0) {
                world.spawnParticle(Particle.LAVA, g, 1, 0.8, 0.1, 0.8, 0);
            }
            double traveled = u * laneLen;
            if (traveled - lastBurnAt >= 1.4) {
                lastBurnAt = traveled;
                addBurn(g, 2.0, 70, ((int) (traveled / 1.4)) % 2 == 0, false);
            }
            Location prev = lastBreathGround == null ? g : lastBreathGround;
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                if (segmentDistance(flat(pl), flat(prev), flat(g)) > 2.9 || Math.abs(pl.getY() - g.getY()) > 3.0) {
                    continue;
                }
                if (!gate("wake", player, 20)) {
                    continue;
                }
                BossHits.hurt(player, entity, 34);
                player.setFireTicks(Math.max(player.getFireTicks(), 80));
                player.setVelocity(right(aim).multiply(Math.signum(right(aim).dot(pl.toVector().subtract(g.toVector()))) * 0.5).setY(0.35));
            }
            lastBreathGround = g;
            if (p % 5 == 0) {
                world.playSound(g, Sound.BLOCK_FIRE_AMBIENT, 1.6f, 0.6f);
                world.playSound(g, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
            }
            return;
        }
        int exit = p - passTicks;
        if (exit == 1) {
            stopWhoosh();
        }
        fly(entity, to.clone().add(aim.clone().multiply(10.0)).add(0, 11.0, 0), form == Form.UNCHAINED ? 1.0 : 0.85, 0.2);
        if (exit >= 16) {
            passes--;
            if (passes > 0) {
                Player next = pick(fighters());
                actionTick = 0;
                windup = 22;
                planWake(next == null ? center : next.getLocation(), aim);
                return;
            }
            endMove();
        }
    }

    // ------------------------------------------------------------------ CINDERFALL

    private void beginCinderfall(List<Player> fighters, boolean fierce) {
        start(Move.CINDERFALL, null);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = (int) clamp(3 + fighters.size() * 2 + (fierce ? 2 : 0), 4, fierce ? 12 : 10);
        for (int i = 0; i < count; i++) {
            Location ground;
            if (i % 2 == 0 && !fighters.isEmpty()) {
                Player p = fighters.get((i / 2) % fighters.size());
                ground = ground(p.getLocation().clone().add(random.nextDouble(-2.0, 2.0), 0, random.nextDouble(-2.0, 2.0)));
            } else {
                double a = random.nextDouble(Math.PI * 2);
                double r = random.nextDouble(arenaRadius * 0.85);
                ground = ground(center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            }
            boolean big = fierce && i % 4 == 3;
            embers.add(new Ember(ground, big ? 3.6 : 2.6, big ? 40 : 30, 18 + i * 6, big ? 24 : 18, big ? 0.0 : 4.0, big));
        }
        windup = 18 + count * 6 + 26;
        cinderCd = fierce ? 200 : 260;
        teach(Move.CINDERFALL, "&6&lCINDERFALL", "&7He spits the Throne's ash. &fLeave the ember rings.");
        World world = center.getWorld();
        world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 0.65f);
    }

    private void tickCinderfall(LivingEntity entity) {
        Location perch = center.clone().add(0, clamp(skyRise * 0.55, 10.0, 16.0), 0);
        if (horizontal(pos, perch) > 2.0 || Math.abs(pos.getY() - perch.getY()) > 1.5) {
            fly(entity, perch, 0.8, 0.25);
        } else {
            Player near = nearest(pos, fighters());
            holdAt(entity, perch, near == null ? heading : steer(heading, flatDir(pos, near.getLocation(), heading), 0.05), 0.35);
        }
        if (actionTick % 12 == 0) {
            World world = center.getWorld();
            world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f, 0.7f);
            world.spawnParticle(Particle.WHITE_ASH, pos.clone().add(0, -1, 0), 30, 4.0, 1.0, 4.0, 0.02, null, true);
        }
        if (actionTick >= windup) {
            endMove();
        }
    }

    // ------------------------------------------------------------------ TALON DIVE

    private void beginTalon(Player target) {
        start(Move.TALON, target);
        mark = target == null ? center.clone() : ground(target.getLocation());
        windup = form() == Form.UNCHAINED ? 24 : 30;
        talonCd = form() == Form.UNCHAINED ? 150 : 200;
        teach(Move.TALON, "&c&lTALON DIVE", "&7You are marked. &fKeep moving, then leave the ring.");
        World world = center.getWorld();
        world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f, 1.1f);
        if (target != null) {
            target.playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 1.4f);
        }
    }

    private void tickTalon(LivingEntity entity, Form form) {
        World world = center.getWorld();
        int t = actionTick;
        int strobe = 8;
        int dive = 12;
        Player target = target();
        if (t <= windup) {
            if (target != null) {
                mark = ground(target.getLocation());
            }
            Vector back = flatDir(mark, center, heading).multiply(-1);
            Location perch = mark.clone().add(back.multiply(-10.0)).add(0, 14.0, 0);
            fly(entity, perch, 1.0, 0.3);
            if (t % 2 == 0) {
                ring(world, mark.clone().add(0, 0.12, 0), 3.8, CRIMSON, 1.4f);
                ring(world, mark.clone().add(0, 0.12, 0), 3.8 * (1.0 - t / (double) windup) + 0.3, CRIMSON, 1.0f);
            }
            if (t % 4 == 0 && target != null) {
                target.sendActionBar(TextUtil.component("&c✖ MARKED &8| &fthe dragon is coming for you"));
            }
            return;
        }
        if (t <= windup + strobe) {
            int remaining = windup + strobe - t;
            Color c = remaining % 2 == 0 ? HOT : CRIMSON;
            ring(world, mark.clone().add(0, 0.12, 0), 4.5, c, 1.6f);
            ring(world, mark.clone().add(0, 0.12, 0), 2.2, c, 1.2f);
            fuse(mark, remaining);
            holdAt(entity, pos.clone().add(0, 0.12, 0), flatDir(pos, mark, heading), 0.0);
            if (t == windup + 1) {
                cinStart = pos.clone();
                startWhoosh(mark, 1.2f, 0.8f);
            }
            return;
        }
        if (t <= windup + strobe + dive) {
            double u = (t - windup - strobe) / (double) dive;
            Location at = lerp(cinStart, mark, u * u * u);
            Location before = pos.clone();
            moveBody(entity, at, flatDir(cinStart, mark, heading));
            streak(world, before.clone().add(0, 1.5, 0), at.clone().add(0, 1.5, 0), form == Form.UNCHAINED ? EMBER : ROYAL, true);
            world.spawnParticle(Particle.FLAME, at.clone().add(0, 1.5, 0), 6, 1.2, 0.6, 1.2, 0.03, null, true);
            if (t == windup + strobe + dive) {
                talonImpact(entity, form);
                start(Move.LANDED, null);
                landedFor = form == Form.UNCHAINED ? 44 : 60;
                expose(landedFor - 6, 1.3);
            }
        }
    }

    private void talonImpact(LivingEntity entity, Form form) {
        stopWhoosh();
        World world = center.getWorld();
        Location mid = mark.clone().add(0, 0.5, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, mid, 1, 0, 0, 0, 0, null, true);
        floorBurst(world, mark, 70, 1.8);
        world.spawnParticle(Particle.LAVA, mid, 24, 1.6, 0.3, 1.6, 0, null, true);
        burst(world, mid, Particle.FLAME, 40, 0.35);
        world.playSound(mark, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.55f);
        world.playSound(mark, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
        world.playSound(mark, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 0.7f);
        for (Player player : fighters()) {
            Location pl = player.getLocation();
            if (horizontal(pl, mark) > 4.8 || Math.abs(pl.getY() - mark.getY()) > 4.0) {
                continue;
            }
            BossHits.hurt(player, entity, 52);
            player.setVelocity(away(pl, mark).multiply(1.0).setY(0.6));
        }
        waves.add(new Shockwave(mark, 1.5, 0.55, form == Form.UNCHAINED ? 17.0 : 14.0, form == Form.UNCHAINED ? EMBER : ROYAL, 24.0, 2));
        teachWave();
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + 0.6;
            addBurn(mark.clone().add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6), 1.5, 70, true, false);
        }
        grounded = true;
    }

    // ------------------------------------------------------------------ LANDED / TAKEOFF

    private void tickLanded(LivingEntity entity) {
        grounded = true;
        Location stand = ground(pos);
        Player near = nearest(pos, fighters());
        if (near != null) {
            float now = yawOf(heading);
            float wanted = yawOf(near.getLocation().toVector().subtract(pos.toVector()).setY(0));
            moveBody(entity, stand, dirOf(now + clampF(wrapDeg(wanted - now), -3.0f, 3.0f)));
        } else {
            holdAt(entity, stand, heading, 0.0);
        }
        World world = center.getWorld();
        if (actionTick % 4 == 0) {
            world.spawnParticle(Particle.SMOKE, headPos(), 3, 0.2, 0.2, 0.2, 0.02);
        }
        if (actionTick == 14 && sweepCd <= 0) {
            for (Player player : fighters()) {
                if (horizontal(player.getLocation(), pos) <= 9.0 && angleFrom(heading, pos, player.getLocation()) > 110.0) {
                    beginSweep(true);
                    return;
                }
            }
        }
        if (actionTick >= landedFor) {
            beginTakeoff();
        }
    }

    private void beginTakeoff() {
        start(Move.TAKEOFF, null);
        cinStart = pos.clone();
        World world = center.getWorld();
        world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.6f);
        world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.8f);
        world.playSound(pos, Sound.ITEM_ELYTRA_FLYING, 0.5f, 1.4f);
        ringForce(world, ground(pos).add(0, 0.2, 0), 6.5, ASH, 1.6f);
        floorBurst(world, pos, 50, 3.0);
        world.spawnParticle(Particle.WHITE_ASH, pos, 60, 4, 0.6, 4, 0.05, null, true);
        for (Player player : fighters()) {
            Location pl = player.getLocation();
            if (horizontal(pl, pos) <= 7.0) {
                player.setVelocity(away(pl, pos).multiply(0.8).setY(0.4));
            }
        }
    }

    private void tickTakeoff(LivingEntity entity) {
        double u = easeOut(actionTick / 16.0);
        Location at = cinStart.clone().add(heading.clone().multiply(3.0 * u)).add(0, 9.0 * u, 0);
        moveBody(entity, at, heading);
        if (actionTick >= 16) {
            grounded = false;
            vel = heading.clone().multiply(0.3);
            endMove();
        }
    }

    // ------------------------------------------------------------------ SKYFIRE VOLLEY

    private void beginVolley(int count) {
        start(Move.VOLLEY, null);
        volleyLeft = count;
        volleyCd = count > 3 ? 150 : 190;
        double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        mark = center.clone().add(Math.cos(a) * arenaRadius * 0.55, 8.0, Math.sin(a) * arenaRadius * 0.55);
        teach(Move.VOLLEY, "&6&lSKYFIRE", "&7Orbs land where the ember rings bloom. &fSidestep.");
    }

    private void tickVolley(LivingEntity entity) {
        List<Player> fighters = fighters();
        Player near = nearest(pos, fighters);
        if (horizontal(pos, mark) > 2.0) {
            fly(entity, mark, 0.9, 0.25);
        } else {
            holdAt(entity, mark, near == null ? heading : steer(heading, flatDir(pos, near.getLocation(), heading), 0.12), 0.3);
        }
        if (actionTick >= 16 && (actionTick - 16) % 12 == 0 && volleyLeft > 0 && !fighters.isEmpty()) {
            Player target = fighters.get(volleyLeft % fighters.size());
            Location predicted = target.getLocation().clone().add(target.getVelocity().clone().setY(0).multiply(10.0));
            clampToArena(predicted, 1.0);
            Location ground = ground(predicted);
            double dist = headPos().distance(ground);
            embers.add(new Ember(ground, 3.0, 32, 10, (int) clamp(dist / 0.95, 10, 30), 1.5, false));
            volleyLeft--;
            center.getWorld().playSound(pos, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.4f, 1.0f);
        }
        if (volleyLeft <= 0 && actionTick >= 16 + 12 * 5 + 20) {
            endMove();
        } else if (volleyLeft <= 0 && embers.isEmpty()) {
            endMove();
        }
    }

    // ------------------------------------------------------------------ SOVEREIGN'S FALL (meteor)

    private void beginMeteor(Player target) {
        start(Move.METEOR, target);
        mark = target == null ? center.clone() : ground(target.getLocation());
        meteorCd = 360;
        sign = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        aim = rotateY(new Vector(1, 0, 0), ThreadLocalRandom.current().nextDouble(Math.PI * 2));
        teach(Move.METEOR, "&4&lSOVEREIGN'S FALL", "&7He falls like a star. &fLeave the circle — then stand between the spokes.");
        World world = center.getWorld();
        world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        world.playSound(pos, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 0.5f);
    }

    private void tickMeteor(LivingEntity entity) {
        World world = center.getWorld();
        int t = actionTick;
        Player target = target();
        double high = clamp(skyRise + 8.0, 18.0, 36.0);
        if (t <= 28) {
            if (target != null) {
                mark = ground(target.getLocation());
            }
            fly(entity, mark.clone().add(0, high, 0), 1.3, 0.3);
            world.spawnParticle(Particle.FLAME, pos.clone().add(0, 1, 0), 8, 1.4, 0.8, 1.4, 0.04, null, true);
            if (t % 2 == 0) {
                ring(world, mark.clone().add(0, 0.12, 0), 6.5, CRIMSON, 1.5f);
                ring(world, mark.clone().add(0, 0.12, 0), 6.5 * (1.0 - t / 28.0) + 0.3, GOLD, 1.1f);
            }
            if (t >= 16 && t % 3 == 0) {
                spokeTell(world, 0.35);
            }
            if (t % 4 == 0 && target != null) {
                target.sendActionBar(TextUtil.component("&4☄ MARKED &8| &fa star is falling on you"));
            }
            return;
        }
        if (t <= 40) {
            int remaining = 40 - t;
            Color c = remaining % 2 == 0 ? HOT : CRIMSON;
            ringForce(world, mark.clone().add(0, 0.12, 0), 6.5, c, 1.8f);
            spokeTell(world, 1.0);
            fuse(mark, remaining);
            holdAt(entity, mark.clone().add(0, high, 0), heading, 0.0);
            if (t == 29) {
                cinStart = pos.clone();
                world.playSound(mark, Sound.ENTITY_WITHER_SHOOT, 1.6f, 0.5f);
            }
            if (t == 34) {
                startWhoosh(mark, 1.4f, 0.6f);
            }
            return;
        }
        if (t <= 50) {
            double u = (t - 40) / 10.0;
            Location before = pos.clone();
            Location at = lerp(cinStart, mark, u * u * u);
            moveBody(entity, at, heading);
            streak(world, before.clone().add(0, 1.5, 0), at.clone().add(0, 1.5, 0), EMBER, true);
            world.spawnParticle(Particle.FLAME, at.clone().add(0, 1.5, 0), 20, 2.2, 1.2, 2.2, 0.06, null, true);
            world.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 3, 0), 6, 1.5, 1.5, 1.5, 0.02, null, true);
            if (t == 50) {
                meteorImpact(entity);
                start(Move.LANDED, null);
                landedFor = 50;
                expose(44, 1.3);
            }
        }
    }

    private void spokeTell(World world, double strength) {
        Particle.DustOptions dust = new Particle.DustOptions(EMBER, (float) (0.7 + 0.5 * strength));
        for (int i = 0; i < 6; i++) {
            Vector dir = rotateY(aim, i * Math.PI / 3);
            for (double d = 2.0; d <= 14.0; d += strength >= 1.0 ? 0.8 : 1.6) {
                world.spawnParticle(Particle.DUST, mark.clone().add(dir.clone().multiply(d)).add(0, 0.14, 0), 1, 0, 0, 0, 0, dust);
            }
        }
    }

    private void meteorImpact(LivingEntity entity) {
        stopWhoosh();
        World world = center.getWorld();
        Location mid = mark.clone().add(0, 0.6, 0);
        world.spawnParticle(Particle.FLASH, mid, 2, 0.6, 0.3, 0.6, 0, null, true);
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3;
            world.spawnParticle(Particle.EXPLOSION_EMITTER, mid.clone().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2), 1, 0, 0, 0, 0, null, true);
        }
        floorBurst(world, mark, 100, 3.0);
        world.spawnParticle(Particle.LAVA, mid, 40, 3.0, 0.4, 3.0, 0, null, true);
        burst(world, mid, Particle.FLAME, 70, 0.55);
        world.playSound(mark, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.55f);
        world.playSound(mark, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.5f);
        world.playSound(mark, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.7f);
        world.playSound(mark, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.6f);
        for (Player player : fighters()) {
            Location pl = player.getLocation();
            if (horizontal(pl, mark) > 6.8 || Math.abs(pl.getY() - mark.getY()) > 5.0) {
                player.setVelocity(player.getVelocity().add(new Vector(0, 0.25, 0)));
                continue;
            }
            BossHits.hurt(player, entity, 60);
            player.setFireTicks(Math.max(player.getFireTicks(), 100));
            player.setVelocity(away(pl, mark).multiply(1.1).setY(0.7));
        }
        waves.add(new Shockwave(mark, 2.0, 0.55, 18.0, EMBER, 24.0, 4));
        for (int i = 0; i < 6; i++) {
            Vector dir = rotateY(aim, i * Math.PI / 3);
            lanes.add(new Lane(mark.clone().add(dir.clone().multiply(2.0)), dir, 12.0, 1.3, 34, 12, EMBER, true, false));
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Math.PI / 6;
            addBurn(mark.clone().add(Math.cos(a) * 3.4, 0, Math.sin(a) * 3.4), 1.6, 110, true, false);
        }
        grounded = true;
    }

    // ------------------------------------------------------------------ TAIL SWEEP

    private void beginSweep(boolean thenTakeoff) {
        start(Move.SWEEP, null);
        takeoffAfter = thenTakeoff;
        sweepFrom = heading.clone();
        sign = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        windup = form() == Form.UNCHAINED ? 12 : 16;
        sweepCd = form() == Form.CHAINED ? 90 : 120;
        teach(Move.SWEEP, "&c&lTAIL SWEEP", "&7The rear arc is about to be swept. &fGet in front of him — or far away.");
        center.getWorld().playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 1.4f);
    }

    private void tickSweep(LivingEntity entity) {
        World world = center.getWorld();
        int t = actionTick;
        Location feet = ground(pos);
        if (t <= windup) {
            int remaining = windup - t;
            Vector rear = sweepFrom.clone().multiply(-1);
            Color c = remaining <= 6 && remaining % 2 == 0 ? HOT : CRIMSON;
            arcTell(feet, rear, 9.5, 110.0, t / (double) windup, c);
            fuse(feet, remaining);
            holdAt(entity, feet, heading, 0.0);
            return;
        }
        if (t <= windup + 8) {
            double step = Math.toRadians(200.0 / 8.0) * sign;
            moveBody(entity, feet, rotateY(heading, step));
            if (t == windup + 1) {
                world.playSound(feet, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2.0f, 0.5f);
                world.playSound(feet, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.8f, 0.6f);
            }
            Vector tail = heading.clone().multiply(-1);
            for (double d = 3.0; d <= 9.0; d += 1.5) {
                Location p = feet.clone().add(tail.clone().multiply(d)).add(0, 0.8, 0);
                world.spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.CRIT, p, 2, 0.2, 0.2, 0.2, 0.1);
            }
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                if (horizontal(pl, feet) > 9.8 || Math.abs(pl.getY() - feet.getY()) > 3.0) {
                    continue;
                }
                if (angleFrom(sweepFrom, feet, pl) <= 70.0 || !gate("sweep", player, 20)) {
                    continue;
                }
                BossHits.hurt(player, entity, 44);
                player.setVelocity(away(pl, feet).multiply(1.15).setY(0.5));
            }
            return;
        }
        holdAt(entity, feet, heading, 0.0);
        if (t >= windup + 14) {
            boolean lift = takeoffAfter;
            takeoffAfter = false;
            endMove();
            if (lift) {
                beginTakeoff();
            }
        }
    }

    // ------------------------------------------------------------------ ASHFIRE BREATH

    private void beginBreath(Player target) {
        start(Move.BREATH, target);
        aim = target == null ? heading.clone() : flatDir(pos, target.getLocation(), heading);
        sign = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        windup = 26;
        breathCd = 170;
        lastBurnAt = -99;
        teach(Move.BREATH, "&6&lASHFIRE BREATH", "&7It sweeps the way the arrows point. &fGet behind him.");
        World world = center.getWorld();
        world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 0.9f);
        world.playSound(pos, Sound.BLOCK_FIRE_AMBIENT, 1.6f, 0.5f);
    }

    private void tickBreath(LivingEntity entity) {
        World world = center.getWorld();
        int t = actionTick;
        int duration = 40;
        double half = Math.toRadians(60);
        Vector startDir = rotateY(aim, -half * sign);
        Location feet = ground(pos);
        if (t <= windup) {
            float now = yawOf(heading);
            float wanted = yawOf(startDir);
            moveBody(entity, feet, dirOf(now + clampF(wrapDeg(wanted - now), -6.0f, 6.0f)));
            Location head = headPos();
            converge(world, head, 1.8, Particle.FLAME, 4);
            int remaining = windup - t;
            Color c = remaining <= 8 && remaining % 2 == 0 ? HOT : EMBER;
            if (t % 2 == 0 || remaining <= 8) {
                coneTell(feet, aim, 15.0, 60.0, t / (double) windup, c);
                chevrons(world, feet, aim, sign, 10.0);
            }
            fuse(feet, remaining);
            if (t == windup) {
                world.playSound(head, Sound.ENTITY_ENDER_DRAGON_SHOOT, 2.0f, 0.6f);
                world.playSound(head, Sound.ITEM_FIRECHARGE_USE, 1.6f, 0.5f);
            }
            return;
        }
        int p = t - windup;
        if (p <= duration) {
            double u = p / (double) duration;
            Vector dir = rotateY(aim, -half * sign + 2 * half * sign * u);
            moveBody(entity, feet, dir);
            Location head = headPos();
            Location far = ground(feet.clone().add(dir.clone().multiply(15.0)));
            for (int i = 1; i <= 8; i++) {
                Location q = lerp(head, far, i / 8.0);
                world.spawnParticle(Particle.FLAME, q, 3, 0.35 + i * 0.08, 0.3, 0.35 + i * 0.08, 0.03, null, true);
                if (i % 3 == 0) {
                    world.spawnParticle(Particle.LARGE_SMOKE, q.clone().add(0, 0.6, 0), 1, 0.3, 0.3, 0.3, 0.01);
                }
            }
            world.spawnParticle(Particle.LAVA, far, 2, 1.0, 0.1, 1.0, 0);
            if (p % 6 == 0) {
                double d = ThreadLocalRandom.current().nextDouble(6.0, 13.0);
                addBurn(ground(feet.clone().add(dir.clone().multiply(d))), 1.8, 60, p % 12 == 0, false);
            }
            if (p % 5 == 0) {
                world.playSound(head, Sound.BLOCK_FIRE_AMBIENT, 1.8f, 0.6f);
                world.playSound(head, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.5f);
            }
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                if (!inCone(feet, dir, pl, 15.5, 16.0) || Math.abs(pl.getY() - feet.getY()) > 4.0) {
                    continue;
                }
                if (!gate("breath", player, 10)) {
                    continue;
                }
                BossHits.hurt(player, entity, 20);
                player.setFireTicks(Math.max(player.getFireTicks(), 80));
            }
            if (p == duration) {
                expose(36, 1.3);
                world.playSound(heartPos(), Sound.BLOCK_FIRE_EXTINGUISH, 1.4f, 0.5f);
            }
            return;
        }
        holdAt(entity, feet, heading, 0.0);
        world.spawnParticle(Particle.SMOKE, headPos(), 3, 0.2, 0.2, 0.2, 0.02);
        if (p >= duration + 6) {
            endMove();
        }
    }

    // ------------------------------------------------------------------ THRONE ERUPTION

    private void beginEruption() {
        start(Move.ERUPTION, null);
        eruptCd = 260;
        cinStart = ground(pos);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double base = random.nextDouble(Math.PI * 2);
        double[] radii = {5.0, 9.0, 13.0};
        for (int k = 0; k < radii.length; k++) {
            double r = Math.min(radii[k], arenaRadius - 1.0);
            double[] gaps = new double[3];
            for (int g = 0; g < 3; g++) {
                gaps[g] = base + k * 0.7 + g * Math.PI * 2 / 3;
            }
            int delay = 18 + k * 10;
            ringBursts.add(new RingBurst(cinStart.clone(), r, gaps, Math.toRadians(22), delay, 36));
        }
        teach(Move.ERUPTION, "&6&lTHRONE ERUPTION", "&7Three rings of fire. &fStand in the blue gaps.");
        center.getWorld().playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);
    }

    private void tickEruption(LivingEntity entity) {
        World world = center.getWorld();
        int t = actionTick;
        if (t <= 14) {
            holdAt(entity, cinStart.clone().add(0, 2.8 * easeOut(t / 14.0), 0), heading, 0.0);
            if (t % 3 == 0) {
                world.spawnParticle(Particle.FLAME, heartPos(), 6, 0.3, 0.3, 0.3, 0.03);
            }
            return;
        }
        if (t == 15) {
            moveBody(entity, cinStart, heading);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, cinStart.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0, null, true);
            floorBurst(world, cinStart, 60, 2.0);
            world.playSound(cinStart, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.5f);
            world.playSound(cinStart, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                if (horizontal(pl, cinStart) <= 3.5 && Math.abs(pl.getY() - cinStart.getY()) < 3.0) {
                    BossHits.hurt(player, entity, 40);
                    player.setVelocity(away(pl, cinStart).multiply(0.9).setY(0.5));
                }
            }
            return;
        }
        holdAt(entity, cinStart, heading, 0.0);
        if (t == 48) {
            expose(40, 1.35);
        }
        if (t >= 52) {
            endMove();
        }
    }

    // ------------------------------------------------------------------ LUNGE

    private void beginLunge(Player target) {
        start(Move.LUNGE, target);
        cinStart = ground(pos);
        aim = target == null ? heading.clone() : flatDir(pos, target.getLocation(), heading);
        double dist = target == null ? 10.0 : horizontal(pos, target.getLocation());
        laneLen = clamp(dist - 2.0, 6.0, 16.0);
        to = ground(cinStart.clone().add(aim.clone().multiply(laneLen)));
        windup = 18;
        lungeCd = 150;
        teach(Move.LUNGE, "&c&lLUNGE", "&7He hurls himself down the lane. &fSidestep.");
        center.getWorld().playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 1.0f);
    }

    private void tickLunge(LivingEntity entity, Form form) {
        World world = center.getWorld();
        int t = actionTick;
        if (t <= windup) {
            int remaining = windup - t;
            moveBody(entity, cinStart.clone().add(0, -0.3 * (t / (double) windup), 0), steer(heading, aim, 0.3));
            drawLane(cinStart, aim, laneLen + 2.0, 2.4, t / (double) windup, t, remaining, CRIMSON);
            fuse(cinStart, remaining);
            return;
        }
        if (t <= windup + 8) {
            double u = (t - windup) / 8.0;
            Location before = pos.clone();
            Location at = lerp(cinStart, to, u * u);
            moveBody(entity, at, aim);
            floorBurst(world, at, 8, 1.2);
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                if (segmentDistance(flat(pl), flat(before), flat(at)) > 2.6 || Math.abs(pl.getY() - at.getY()) > 3.0) {
                    continue;
                }
                if (!gate("lunge", player, 20)) {
                    continue;
                }
                BossHits.hurt(player, entity, 40);
                player.setVelocity(right(aim).multiply(Math.signum(right(aim).dot(pl.toVector().subtract(at.toVector()))) * 0.9).setY(0.4));
            }
            if (t == windup + 8) {
                Location jaw = to.clone().add(aim.clone().multiply(2.0));
                world.playSound(jaw, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.6f, 0.6f);
                world.playSound(jaw, Sound.ENTITY_ENDER_DRAGON_HURT, 1.2f, 0.6f);
                world.spawnParticle(Particle.SWEEP_ATTACK, jaw.clone().add(0, 1, 0), 3, 1.0, 0.3, 1.0, 0);
                for (Player player : fighters()) {
                    Location pl = player.getLocation();
                    if (inCone(to, aim, pl, 5.5, 55.0) && Math.abs(pl.getY() - to.getY()) < 3.0 && gate("bite", player, 20)) {
                        BossHits.hurt(player, entity, 48);
                        player.setVelocity(aim.clone().multiply(0.9).setY(0.45));
                    }
                }
                if (form == Form.CHAINED && horizontal(to, center) > leashR()) {
                    world.playSound(pos, Sound.BLOCK_CHAIN_BREAK, 1.2f, 0.6f);
                    strainSparks();
                }
            }
            return;
        }
        Location settle = pos.clone();
        if (form == Form.CHAINED && leash(settle)) {
            settle = lerp(pos, settle, 0.35);
        }
        settle.setY(groundY(settle.getX(), settle.getZ()));
        holdAt(entity, settle, heading, 0.0);
        if (t >= windup + 18) {
            endMove();
        }
    }

    // ------------------------------------------------------------------ CHAIN LASH

    private void beginLash() {
        List<Chain> live = new ArrayList<>();
        for (Chain chain : chains) {
            if (!chain.broken && !chain.links.isEmpty()) {
                live.add(chain);
            }
        }
        if (live.isEmpty()) {
            return;
        }
        start(Move.LASH, null);
        lashChain = live.get(ThreadLocalRandom.current().nextInt(live.size()));
        windup = 22;
        lashCd = 200;
        Location a = lashChain.anchor.clone();
        Location b = ground(pos);
        Vector dir = flatDir(a, b, heading);
        double len = Math.max(3.0, horizontal(a, b));
        lanes.add(new Lane(a, dir, len, 1.8, 34, windup, ROYAL, false, true));
        teach(Move.LASH, "&5&lCHAIN LASH", "&7He thrashes a chain. &fGet off its glowing line.");
        center.getWorld().playSound(a, Sound.BLOCK_CHAIN_STEP, 1.8f, 0.5f);
    }

    private void tickLash(LivingEntity entity) {
        World world = center.getWorld();
        holdAt(entity, ground(pos), heading, 0.0);
        if (lashChain == null) {
            endMove();
            return;
        }
        if (actionTick < windup) {
            lashChain.shake = 0.14;
            if (actionTick % 4 == 0) {
                world.playSound(lashChain.anchor, Sound.BLOCK_CHAIN_HIT, 1.4f, 0.6f);
            }
        } else if (actionTick == windup) {
            lashChain.shake = 0.0;
            lashChain.slam = 1.0;
            world.playSound(lashChain.anchor, Sound.BLOCK_CHAIN_BREAK, 1.6f, 0.5f);
            world.playSound(lashChain.anchor, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.5f);
            world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f, 1.2f);
        }
        if (actionTick >= windup + 14) {
            endMove();
        }
    }

    // ------------------------------------------------------------------ THE THRONE CLAIMS HIM (Sovereign -> Chained)

    /*
     * 0-12 he hangs in the air, shaking · 12 three pylons tear out of the floor · 30/38/46 a chain fires
     * from each and latches · 56-96 he is dragged down, fighting it · 96 he hits the floor · 100 name card.
     */
    private void tickThrone(LivingEntity entity, int t) {
        World world = center.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location hover = cinStart.clone();
        hover.setY(Math.max(hover.getY(), groundY(hover.getX(), hover.getZ()) + 6.0));
        Location down = center.clone();
        if (t <= 56) {
            double jitter = t >= 30 ? 0.25 : 0.1;
            Location at = hover.clone().add(random.nextDouble(-jitter, jitter), 1.5 * easeOut(Math.min(1.0, t / 12.0)), random.nextDouble(-jitter, jitter));
            Player near = nearest(at, fighters());
            moveBody(entity, at, near == null ? heading : steer(heading, flatDir(at, near.getLocation(), heading), 0.05));
        } else if (t <= 96) {
            double u = (t - 56) / 40.0;
            Location top = hover.clone().add(0, 1.5, 0);
            Location at = lerp(top, down, u * u).add(random.nextDouble(-0.3, 0.3), 0, random.nextDouble(-0.3, 0.3));
            moveBody(entity, at, steer(heading, flatDir(top, down, heading), 0.04));
            if (t % 14 == 0) {
                world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 0.8f + random.nextFloat() * 0.2f);
                world.playSound(at, Sound.ENTITY_ENDER_DRAGON_HURT, 1.4f, 0.7f);
            }
            if (t % 3 == 0) {
                strainSparks();
            }
        } else {
            Player near = nearest(down, fighters());
            Location stand = down.clone().add(0, t < 104 ? 0.0 : 0.0, 0);
            moveBody(entity, stand, near == null ? heading : steer(heading, flatDir(stand, near.getLocation(), heading), 0.1));
        }
        if (beat(t, 6)) {
            world.playSound(center, Sound.ENTITY_WARDEN_EMERGE, 1.6f, 0.6f);
            world.playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 1.6f, 0.5f);
        }
        if (t >= 6 && t < 12 && t % 2 == 0) {
            for (int i = 0; i < CHAINS; i++) {
                ring(world, anchor(i).add(0, 0.14, 0), 2.4, ROYAL, 1.4f);
            }
        }
        if (beat(t, 12)) {
            spawnChains(false);
            for (int i = 0; i < CHAINS; i++) {
                Location a = anchor(i);
                floorBurst(world, a, 50, 1.2);
                world.spawnParticle(Particle.REVERSE_PORTAL, a.clone().add(0, 1.2, 0), 40, 0.6, 1.2, 0.6, 0.05, null, true);
                world.playSound(a, Sound.BLOCK_DEEPSLATE_BREAK, 1.6f, 0.5f);
                world.playSound(a, Sound.ENTITY_WARDEN_DIG, 1.4f, 0.6f);
                for (Player player : fighters()) {
                    if (horizontal(player.getLocation(), a) < 2.8) {
                        player.setVelocity(away(player.getLocation(), a).multiply(0.7).setY(0.4));
                    }
                }
            }
        }
        for (int i = 0; i < chains.size(); i++) {
            int fire = 30 + i * 8;
            Chain chain = chains.get(i);
            if (t >= fire && chain.reach < 1.0) {
                chain.reach = Math.min(1.0, (t - fire) / 8.0);
                if (beat(t, fire)) {
                    world.playSound(chain.anchor, Sound.ITEM_TRIDENT_THROW, 1.6f, 0.5f);
                    world.playSound(chain.anchor, Sound.BLOCK_CHAIN_PLACE, 1.6f, 0.6f);
                }
                if (chain.reach >= 1.0) {
                    world.playSound(pos, Sound.ITEM_TRIDENT_HIT, 1.8f, 0.5f);
                    world.playSound(pos, Sound.BLOCK_ANVIL_PLACE, 0.9f, 0.6f);
                    world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_HURT, 1.6f, 0.6f);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, attachPoint(chain), 20, 0.3, 0.3, 0.3, 0.2, null, true);
                }
            }
        }
        if (beat(t, 96)) {
            Location land = down.clone();
            world.spawnParticle(Particle.EXPLOSION_EMITTER, land.clone().add(0, 0.5, 0), 2, 1.0, 0, 1.0, 0, null, true);
            floorBurst(world, land, 120, 3.0);
            world.playSound(land, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.5f);
            world.playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
            world.playSound(land, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f);
            waves.add(new Shockwave(land, 1.5, 0.6, arenaRadius, ROYAL, 24.0, 0));
            teachWave();
            grounded = true;
            heartBoost = 0.7;
        }
        if (beat(t, 100)) {
            titleNear("&5&lTHE THRONE CLAIMS HIM", "&7Chained. Furious. &fWithin reach of your blades.");
            shout("&8&oThe Throne&7: &fKneel, Sovereign.");
        }
        if (beat(t, 108)) {
            world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);
            burst(world, headPos(), Particle.FLAME, 40, 0.3);
        }
    }

    private void tickHold(LivingEntity entity, int tick) {
        holdAt(entity, pos, heading, grounded ? 0.0 : 0.3);
        if (tick % 20 == 0) {
            center.getWorld().playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.9f);
        }
    }

    // ------------------------------------------------------------------ THE LAST SKY (Chained -> Unchained)

    /*
     * 0-44 he strains; the chains snap at 20 / 30 / 40 · 44-76 he rockets up and the sky goes black ·
     * 84 he kindles a sun · 92 the broken pylons become soul wards · 152 he dives into the sun and it falls ·
     * 206 impact, a wall of fire sweeps the Throne · 220 he rises from the crater, crowned · 236 name card.
     */
    private void tickLastSky(LivingEntity entity, int t) {
        World world = center.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double sunH = clamp(skyRise - 2.0, 14.0, 30.0);
        Location sunHome = center.clone().add(0, sunH, 0);
        Location base = cinStart.clone();
        base.setY(groundY(base.getX(), base.getZ()));

        if (beat(t, 1) && !chainsAlive()) {
            spawnChains(true);
        }

        // STRAIN
        if (t <= 44) {
            double lift = 3.0 * easeInOut(t / 44.0);
            Location at = base.clone().add(random.nextDouble(-0.2, 0.2), lift, random.nextDouble(-0.2, 0.2));
            moveBody(entity, at, heading);
            if (t % 2 == 0) {
                for (Chain chain : chains) {
                    if (chain.broken) {
                        continue;
                    }
                    Location mid = lerp(chain.anchor.clone().add(0, 2.4, 0), attachPoint(chain), random.nextDouble());
                    world.spawnParticle(Particle.ELECTRIC_SPARK, mid, 3, 0.2, 0.2, 0.2, 0.08);
                    world.spawnParticle(Particle.FLAME, mid, 2, 0.2, 0.2, 0.2, 0.02);
                }
            }
            if (t % 8 == 0) {
                world.playSound(at, Sound.BLOCK_CHAIN_STEP, 1.8f, 0.5f);
                world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f, 0.9f);
            }
            for (int i = 0; i < chains.size(); i++) {
                if (beat(t, 20 + i * 10)) {
                    snapChain(chains.get(i));
                    moveBody(entity, pos.clone().add(0, 0.8, 0), heading);
                }
            }
        }

        // ASCENT
        if (t > 44 && t <= 76) {
            double u = (t - 44) / 32.0;
            Location top = sunHome.clone().add(0, 6.0, 0);
            Location low = base.clone().add(0, 3.0, 0);
            moveBody(entity, lerp(low, top, u * u), heading);
            world.spawnParticle(Particle.FLAME, pos.clone().add(0, -1, 0), 14, 1.4, 1.4, 1.4, 0.05, null, true);
            world.spawnParticle(Particle.LARGE_SMOKE, pos.clone().add(0, -2, 0), 4, 1.0, 1.0, 1.0, 0.02, null, true);
            if (t % 5 == 0) {
                world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.9f);
            }
        }
        if (beat(t, 46)) {
            startWhoosh(center, 1.2f, 0.5f);
            world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        }
        if (beat(t, 58)) {
            stopWhoosh();
            darkenSky();
            world.playSound(center, Sound.BLOCK_BELL_USE, 1.8f, 0.5f);
            world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.8f, 0.5f);
            shout("&8…the sky goes out.");
        }

        // KINDLE: he circles the forming sun
        if (t > 76 && t < SKY_FALL) {
            double a = (t - 76) * 0.12;
            Location at = sunHome.clone().add(Math.cos(a) * 10.0, Math.sin(t * 0.08) * 1.2, Math.sin(a) * 10.0);
            moveBody(entity, at, new Vector(-Math.sin(a), 0, Math.cos(a)));
        }
        if (beat(t, SKY_SUN)) {
            spawnSun(sunHome);
            world.playSound(sunHome, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.0f, 0.5f);
            world.playSound(sunHome, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
            world.spawnParticle(Particle.FLASH, sunHome, 2, 0.5, 0.5, 0.5, 0, null, true);
        }
        if (t >= SKY_SUN && t < SKY_FALL) {
            double grow = easeOut(Math.min(1.0, (t - SKY_SUN) / 36.0));
            sunPos = sunHome.clone();
            poseSun(1.0 * grow + 0.02, 1);
            if (t % 10 == 0) {
                world.playSound(sunHome, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.8f, 0.5f + (t - SKY_SUN) * 0.008f);
                world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.6f);
            }
            world.spawnParticle(Particle.FALLING_LAVA, sunHome, 4, 2.5, 1.5, 2.5, 0, null, true);
            converge(world, sunHome, 7.0, Particle.FLAME, 6);
        }
        if (beat(t, SKY_WARDS)) {
            raiseWards();
            titleNear("&4&l☀ THE LAST SKY", "&bShelter in the soul wards — &fnow.");
        }
        if (t >= SKY_WARDS && t < SKY_IMPACT) {
            tickWards(world, t, false);
            int remaining = SKY_IMPACT - t;
            if (t % 4 == 0) {
                actionBar(remaining <= 12
                        ? "&f&l⚠ INTO THE WARDS!"
                        : "&4☀ THE LAST SKY &8| &bstand in a soul ward &8| " + meter(remaining, SKY_IMPACT - SKY_WARDS, "&4"));
            }
            fuse(center, remaining);
        }

        // FALL: he dives into the sun and rides it down
        if (beat(t, SKY_FALL)) {
            world.spawnParticle(Particle.FLASH, sunHome, 3, 1.0, 1.0, 1.0, 0, null, true);
            world.playSound(sunHome, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
            world.playSound(sunHome, Sound.ITEM_FIRECHARGE_USE, 2.0f, 0.5f);
            startWhoosh(center, 1.4f, 0.5f);
        }
        if (t >= SKY_FALL && t < SKY_IMPACT) {
            double u = (t - SKY_FALL) / (double) (SKY_IMPACT - SKY_FALL);
            double eased = u * u * u;
            sunPos = sunHome.clone().add(0, -(sunH - 2.5) * eased, 0);
            poseSun(1.0 + 0.6 * eased, 1);
            moveBody(entity, sunPos.clone().add(0, -1.5, 0), heading);
            world.spawnParticle(Particle.WHITE_ASH, center.clone().add(0, 6, 0), 30, arenaRadius * 0.6, 5, arenaRadius * 0.6, 0.02, null, true);
            world.spawnParticle(Particle.FLAME, sunPos, 12, 3.5, 3.5, 3.5, 0.08, null, true);
            if (t % 3 == 0) {
                double a = random.nextDouble(Math.PI * 2);
                double r = random.nextDouble(arenaRadius * 0.8);
                world.spawnParticle(Particle.LAVA, center.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 2, 0.2, 0, 0.2, 0);
            }
            if (t % 6 == 0) {
                world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 1.8f, 0.5f + (float) u * 0.4f);
            }
        }
        if (beat(t, SKY_IMPACT)) {
            lastSkyImpact(entity);
        }

        // EMERGENCE
        if (t > SKY_IMPACT) {
            tickFireWall(world, t - SKY_IMPACT);
            Location crater = center.clone();
            if (t < 220) {
                moveBody(entity, crater, heading);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, crater.clone().add(0, 1, 0), 4, 2.0, 0.5, 2.0, 0.03, null, true);
            } else {
                double u = easeOut(Math.min(1.0, (t - 220) / 20.0));
                Player near = nearest(crater, fighters());
                moveBody(entity, crater.clone().add(0, 4.0 * u, 0), near == null ? heading : steer(heading, flatDir(crater, near.getLocation(), heading), 0.08));
            }
            if (t >= 222 && t < 222 + 5 * 3) {
                int shown = (t - 222) / 3 + 1;
                if (shown != crownShown) {
                    crownShown = shown;
                    world.playSound(headPos(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.6f + shown * 0.12f);
                    world.spawnParticle(Particle.FLAME, headPos().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.04);
                }
            }
            if (beat(t, 236)) {
                world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);
                burst(world, headPos(), Particle.FLAME, 60, 0.45);
                titleNear("&6&lAETHERION UNCHAINED", "&7The last sky burns inside him now.");
                shout("&6&lAetherion&7: &fI am the last sky. &cBurn beneath it.");
                heartBoost = 1.0;
            }
            if (t >= 240) {
                tickWards(world, t, true);
            }
            if (beat(t, 244)) {
                restoreSky();
            }
            if (beat(t, 246)) {
                sinkPylons();
            }
        }
    }

    private void lastSkyImpact(LivingEntity entity) {
        stopWhoosh();
        World world = center.getWorld();
        Location mid = center.clone().add(0, 1.0, 0);
        clearSun();
        world.spawnParticle(Particle.FLASH, mid, 4, 2.0, 1.0, 2.0, 0, null, true);
        world.spawnParticle(Particle.SONIC_BOOM, mid, 1, 0, 0, 0, 0, null, true);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            world.spawnParticle(Particle.EXPLOSION_EMITTER, mid.clone().add(Math.cos(a) * 4.0, 0, Math.sin(a) * 4.0), 1, 0, 0, 0, 0, null, true);
        }
        floorBurst(world, center, 160, 4.0);
        world.spawnParticle(Particle.LAVA, mid, 80, 5.0, 0.6, 5.0, 0, null, true);
        burst(world, mid, Particle.FLAME, 120, 0.9);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 0.6f);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 0.5f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 1.2f, 0.5f);
        spawnFireWall();
        for (Player player : fighters()) {
            Location pl = player.getLocation();
            if (inWard(pl)) {
                player.showTitle(Title.title(TextUtil.component("&bSHELTERED"),
                        TextUtil.component("&7The Throne kept you."), titleTimes()));
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, pl.clone().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.05);
                continue;
            }
            BossHits.crush(player, entity, 115);
            player.setFireTicks(Math.max(player.getFireTicks(), 100));
            player.setVelocity(away(pl, center).multiply(1.0).setY(0.8));
            player.showTitle(Title.title(TextUtil.component("&4BURNED"),
                    TextUtil.component("&7The Last Sky: shelter in a soul ward."), titleTimes()));
        }
        for (Location ward : wards) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, ward.clone().add(0, 1.5, 0), 60, WARD_R * 0.5, 1.5, WARD_R * 0.5, 0.05, null, true);
            world.playSound(ward, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.4f, 0.8f);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 14; i++) {
            double a = random.nextDouble(Math.PI * 2);
            double r = 3.0 + random.nextDouble(arenaRadius * 0.8);
            Location at = ground(center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            if (!inWard(at)) {
                addBurn(at, 1.8, 150 + random.nextInt(60), true, false);
            }
        }
    }

    // ------------------------------------------------------------------ death: ash to the Throne

    /*
     * 0-46 he falters upward, the heart flickering · 46 the heart cracks · 47-88 he spirals down ·
     * 88 the crash and the skid · 100-134 four chains of the Throne bind him, the heartbeats slow ·
     * 140-184 the heart drifts back to the Throne · 184 it goes out · 204 the body is given to the light.
     */
    private boolean tickDeath() {
        deathTick++;
        int t = deathTick;
        World world = center.getWorld();
        LivingEntity entity = instance.getEntity();
        boolean body = entity != null && entity.isValid() && !entity.isDead();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (body) {
            readParts(entity);
        }

        if (t <= 46) {
            if (body) {
                Location goal = deathSky.clone().add(random.nextDouble(-0.8, 0.8), random.nextDouble(-0.5, 0.5), random.nextDouble(-0.8, 0.8));
                fly(entity, goal, 0.45, 0.2);
            }
            heartCoreMat = null;
            setBlock(heartCore, (t / 3) % 2 == 0 ? Material.SHROOMLIGHT : Material.BLACKSTONE);
            if (t % 3 == 0) {
                world.spawnParticle(Particle.LAVA, partPos(6), 2, 0.8, 0.3, 0.8, 0);
                world.spawnParticle(Particle.LAVA, partPos(7), 2, 0.8, 0.3, 0.8, 0);
                world.spawnParticle(Particle.LARGE_SMOKE, partPos(2).add(0, 1, 0), 4, 1.2, 0.6, 1.2, 0.02, null, true);
            }
            if (t % 12 == 0) {
                world.playSound(pos, Sound.ENTITY_WARDEN_HEARTBEAT, 1.8f, 0.6f);
                world.playSound(pos, Sound.ENTITY_ENDER_DRAGON_HURT, 1.4f, 0.5f);
            }
        }
        if (t == 46) {
            Location heart = heartPos();
            world.spawnParticle(Particle.FLASH, heart, 2, 0.2, 0.2, 0.2, 0, null, true);
            burst(world, heart, Particle.FLAME, 60, 0.4);
            burst(world, heart, Particle.END_ROD, 30, 0.3);
            world.playSound(heart, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f);
            world.playSound(heart, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.4f, 0.6f);
            world.playSound(heart, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.45f);
            for (int i = 0; i < 8; i++) {
                spawnDebris(heart, Material.ORANGE_STAINED_GLASS, randomUnit().multiply(0.35).add(new Vector(0, 0.2, 0)), 0.3f, 30);
            }
            discardRig(heartShell);
            heartShell = null;
            setBlock(heartCore, Material.PEARLESCENT_FROGLIGHT);
            cinStart = pos.clone();
            startWhoosh(restAt, 1.2f, 0.5f);
        }
        if (t > 46 && t <= 88 && body) {
            double u = (t - 46) / 42.0;
            double eased = u * u;
            double swirl = (1.0 - u) * 6.0;
            double a = u * Math.PI * 3.0;
            Location path = lerp(cinStart, restAt, eased).add(Math.cos(a) * swirl, 0, Math.sin(a) * swirl);
            Vector face = new Vector(-Math.sin(a), 0, Math.cos(a));
            if (u > 0.75 && restAt != null) {
                face = steer(heading, flatDir(path, restAt, heading), 0.25);
            }
            Location before = pos.clone();
            moveBody(entity, path, face);
            streak(world, before.clone().add(0, 1.2, 0), path.clone().add(0, 1.2, 0), EMBER, true);
            world.spawnParticle(Particle.FLAME, path.clone().add(0, 1, 0), 10, 1.5, 0.8, 1.5, 0.04, null, true);
            world.spawnParticle(Particle.LARGE_SMOKE, path.clone().add(0, 2, 0), 3, 1.0, 1.0, 1.0, 0.02, null, true);
        }
        if (t == 88) {
            stopWhoosh();
            Location land = restAt != null ? restAt.clone() : ground(center);
            if (body) {
                snapBody(entity, land, heading);
            }
            world.spawnParticle(Particle.EXPLOSION_EMITTER, land.clone().add(0, 0.5, 0), 3, 2.0, 0.2, 2.0, 0, null, true);
            floorBurst(world, land, 160, 4.0);
            world.spawnParticle(Particle.LAVA, land.clone().add(0, 0.5, 0), 40, 3.0, 0.3, 3.0, 0, null, true);
            world.playSound(land, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.45f);
            world.playSound(land, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
            world.playSound(land, Sound.BLOCK_ANVIL_LAND, 1.4f, 0.45f);
            world.playSound(land, Sound.ENTITY_ENDER_DRAGON_HURT, 2.0f, 0.5f);
            waves.add(new Shockwave(land, 2.0, 0.8, arenaRadius, EMBER, 0.0, 0));
            cinStart = land.clone();
        }
        if (t > 88 && t <= 100 && body) {
            // Settle on the anchor — no skid that drifts the finale off the Throne.
            holdAt(entity, restAt, heading, 0.0);
            if (t % 3 == 0) {
                floorBurst(world, restAt, 10, 1.4);
            }
        }
        if (t == 100) {
            restAt = ground(center);
            if (body) {
                snapBody(entity, restAt, heading);
            }
            chains.clear();
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(45 + i * 90);
                Location anchor = ground(restAt.clone().add(Math.cos(a) * 6.5, 0, Math.sin(a) * 6.5));
                Chain chain = new Chain(anchor);
                chain.pylonless = true;
                chains.add(chain);
                floorBurst(world, anchor, 30, 0.8);
                world.spawnParticle(Particle.REVERSE_PORTAL, anchor.clone().add(0, 0.5, 0), 20, 0.4, 0.4, 0.4, 0.05);
            }
            world.playSound(restAt, Sound.ENTITY_WARDEN_EMERGE, 1.6f, 0.6f);
            shout("&8&oThe Throne&7: &fStay.");
        }
        for (int i = 0; i < chains.size() && t >= 100 && t < 184; i++) {
            Chain chain = chains.get(i);
            int fire = 104 + i * 6;
            if (t >= fire && chain.reach < 1.0) {
                chain.reach = Math.min(1.0, (t - fire) / 6.0);
                if (chain.reach >= 1.0) {
                    world.playSound(restAt, Sound.ITEM_TRIDENT_HIT, 1.6f, 0.6f);
                    world.playSound(restAt, Sound.BLOCK_CHAIN_PLACE, 1.6f, 0.5f);
                    if (body) {
                        moveBody(entity, restAt.clone().add(random.nextDouble(-0.25, 0.25), 0.3, random.nextDouble(-0.25, 0.25)), heading);
                    }
                }
            }
        }
        if (t > 100 && t < 184 && body && t % 4 == 0) {
            moveBody(entity, restAt, heading);
        }
        if (t == 104 || t == 118 || t == 134) {
            float volume = t == 134 ? 0.9f : 1.6f;
            world.playSound(restAt, Sound.ENTITY_WARDEN_HEARTBEAT, volume, 0.5f);
            heartBoost = t == 134 ? 0.25 : 0.5;
        }
        if (t >= 100 && t < 184) {
            if (t % 3 == 0) {
                world.spawnParticle(Particle.WHITE_ASH, restAt.clone().add(0, 2, 0), 16, 4.0, 1.5, 4.0, 0.01, null, true);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, restAt.clone().add(random.nextDouble(-3, 3), 1.5, random.nextDouble(-3, 3)), 1, 0.2, 0.2, 0.2, 0.02);
            }
        }
        if (t >= 140 && t < 184) {
            double u = easeInOut((t - 140) / 44.0);
            Location start = heartPos();
            Location throne = center.clone().add(0, 3.0, 0);
            Location rise = start.clone().add(0, 3.0, 0);
            heartFloat = u < 0.35 ? lerp(start, rise, u / 0.35) : lerp(rise, throne, (u - 0.35) / 0.65);
            world.spawnParticle(Particle.END_ROD, heartFloat, 2, 0.1, 0.1, 0.1, 0.01, null, true);
            world.spawnParticle(Particle.FLAME, heartFloat, 1, 0.05, 0.05, 0.05, 0.01);
            if (t % 6 == 0) {
                world.playSound(heartFloat, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 0.5f + (float) u * 0.8f);
            }
        }
        if (t == 184) {
            Location end = heartFloat != null ? heartFloat : center.clone().add(0, 3, 0);
            world.spawnParticle(Particle.FLASH, end, 2, 0.1, 0.1, 0.1, 0, null, true);
            burst(world, end, Particle.END_ROD, 60, 0.35);
            converge(world, end, 3.0, Particle.FLAME, 30);
            world.playSound(end, Sound.BLOCK_BEACON_DEACTIVATE, 1.8f, 0.6f);
            world.playSound(end, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.6f, 0.5f);
            world.playSound(end, Sound.BLOCK_BELL_RESONATE, 1.2f, 0.6f);
            shout("&8The Throne keeps what it burns.");
            // Keep a floating heart ember for the eclipse; body/chains go.
            setBlock(heartCore, Material.PEARLESCENT_FROGLIGHT);
            heartFloat = end.clone();
            for (Chain chain : chains) {
                crumbleChain(chain);
            }
            chains.clear();
            if (body) {
                entity.setInvisible(true);
                entity.setCustomNameVisible(false);
            }
            discardRig(heartShell);
            heartShell = null;
            darkenSky();
            titleNear("&5&lTHRONE ECLIPSE", "&8The last ash remembers the sky");
        }
        if (t >= ECLIPSE_HUSH) {
            tickEclipse(world, t);
        }
        if (t == 150) {
            shout("&5&lAetherion&7: &fFine. Take a piece of me. &8Not the sky.");
        }
        tickWaves(entity);
        tickDebris();
        tickBurns();
        tickChains();
        if (t < 184) {
            syncRig();
        }
        if (t >= DEATH_TICKS) {
            placeDeathChest();
            clearEclipse();
            clearCombatFx();
            clearRig();
            clearChains();
            stopWhoosh();
            restoreSky();
            heartFloat = null;
            return true;
        }
        return false;
    }

    /** Floor perch under the Sovereign Anchor (same XZ as spawn; Y on solid ground). */
    private Location introPerch() {
        return ground(center);
    }

    /** Force the body onto {@code at} — soft HOVER seeking alone can stall a few blocks high. */
    private void snapBody(LivingEntity entity, Location at, Vector face) {
        if (entity == null || !entity.isValid() || at == null) {
            return;
        }
        moveBody(entity, at, face);
        Location dest = at.clone();
        dest.setYaw(wrapDeg(yawOf(heading) + 180.0f));
        dest.setPitch(0.0f);
        pos = dest.clone();
        instance.runInternalTeleport(() -> {
            if (entity.isValid()) {
                entity.teleport(dest);
                entity.setVelocity(new Vector());
            }
        });
        if (entity instanceof EnderDragon dragon) {
            dragon.setPhase(EnderDragon.Phase.CIRCLING);
            dragon.setPhase(EnderDragon.Phase.HOVER);
            dragon.setVelocity(new Vector());
            dragon.setRotation(dest.getYaw(), 0.0f);
        }
    }

    /**
     * Soft-place the Floor-3 Sovereign Reliquary on the live spawn anchor once the Throne Eclipse ends.
     * Reflective so BossEngine stays free of a hard Dungeons compile dependency.
     * Displays/hitbox must stay non-persistent (see DungeonLootFx) so nothing serializes into
     * ashen_void/entities/*.mca across restarts.
     */
    private void placeDeathChest() {
        if (deathChestPlaced || center == null || center.getWorld() == null) {
            return;
        }
        deathChestPlaced = true;
        Location spawn = instance.getSpawnLocation();
        World world = spawn != null && spawn.getWorld() != null ? spawn.getWorld() : center.getWorld();
        if (world == null) {
            return;
        }
        Location pad = ground(spawn != null ? spawn : center);
        int x = pad.getBlockX();
        int y = pad.getBlockY();
        int z = pad.getBlockZ();
        clearArenaVictoryChest();
        org.bukkit.plugin.Plugin dungeons = Bukkit.getPluginManager().getPlugin("AetherionDungeons");
        if (dungeons == null || !dungeons.isEnabled()) {
            return;
        }
        try {
            Class<?> fx = Class.forName("de.aetherion.dungeons.instance.DungeonLootFx");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<? extends Enum> tierClass = (Class<? extends Enum>) Class.forName(
                    "de.aetherion.dungeons.instance.DungeonLootFx$ChestTier");
            Object legendary = Enum.valueOf(tierClass, "LEGENDARY");
            Method place = fx.getMethod(
                    "placeVictoryAt",
                    org.bukkit.plugin.Plugin.class,
                    World.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    tierClass);
            world.getChunkAt(x >> 4, z >> 4).load(true);
            place.invoke(null, dungeons, world, x, y, z, 3, legendary);
        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException ignored) {
            // Arena still plays without Dungeons present.
        }
    }

    /** Drop any leftover Ashen Void Reliquary on the spawn pad before a new one lands / fight starts. */
    private void clearArenaVictoryChest() {
        Location spawn = instance.getSpawnLocation();
        Location origin = spawn != null ? spawn : center;
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        Location pad = ground(origin);
        if (pad == null || pad.getWorld() == null) {
            return;
        }
        World world = pad.getWorld();
        int x = pad.getBlockX();
        int y = pad.getBlockY();
        int z = pad.getBlockZ();
        for (int dy = -2; dy <= 3; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    clearArenaVictoryChestAt(world, x + dx, y + dy, z + dz);
                }
            }
        }
    }

    private void clearArenaVictoryChestAt(World world, int x, int y, int z) {
        if (world == null) {
            return;
        }
        try {
            Class<?> fx = Class.forName("de.aetherion.dungeons.instance.DungeonLootFx");
            Method clear = fx.getMethod("clearVictoryAt", World.class, int.class, int.class, int.class);
            world.getChunkAt(x >> 4, z >> 4).load(true);
            clear.invoke(null, world, x, y, z);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR, false);
            }
        }
    }

    /** Flat decorative ring for the eclipse corona (visual only). */
    private void tiltedAshRing(World world, Location focus, double radius, double spin) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(90, 20, 140), 1.2f);
        for (int i = 0; i < 36; i++) {
            double a = spin + i * Math.PI * 2 / 36;
            Location p = focus.clone().add(Math.cos(a) * radius, Math.sin(a * 2) * 0.35, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust, true);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.FLAME, p, 1, 0, 0, 0, 0, null, true);
            }
        }
    }

    // ------------------------------------------------------------------ death: Throne Eclipse (visual only)

    /*
     * Where the Hollow Sun goes outward and upward, the Throne goes inward and down: it takes the sky
     * back instead of giving one away.
     *
     * 185-203 hush, and a violet tear splits the black sky over the arena · 204-223 eight chains of the
     * Throne climb out of the floor, hook the tear and haul it shut, grinding louder as it narrows ·
     * 224-235 twelve spears of ash come off the rim and drive into the heart, one after another ·
     * 236-239 true silence, one black pinpoint, the whole rebound staged unseen ·
     * 240 ASH NOVA: the collapse rebounds, the Throne's seal burns itself into the arena floor and an
     * eclipse opens overhead · 241-281 the seal turns, the corona burns, ash falls over everything ·
     * 282-309 the seal contracts in three grinding slams and the eclipse closes ·
     * 310-329 ash settles, dawn comes back · 330 everything is cleared · 336 the body is released.
     *
     * Nothing in here damages, pushes, or otherwise touches a player. Every shell that gets large is
     * anchored in the sky, so no display can ever close around a player's camera.
     */
    private void tickEclipse(World world, int t) {
        if (t >= ECLIPSE_CLEAR) {
            if (t == ECLIPSE_CLEAR) {
                clearEclipse();
                world.playSound(center, Sound.BLOCK_BELL_RESONATE, 0.7f, 0.5f);
            }
            return;
        }
        Location focus = heartFloat != null ? heartFloat : center.clone().add(0, 3, 0);
        if (eclipseSky == null) {
            eclipseSky = center.clone().add(0, Math.min(20.0, skyRise * 0.72 + 4.0), 0);
        }
        if (t < ECLIPSE_HAUL) {
            hushEclipse(world, focus, t - ECLIPSE_HUSH);
        } else if (t < ECLIPSE_COLLAPSE) {
            haulSkyShut(world, focus, t - ECLIPSE_HAUL);
        } else if (t < ECLIPSE_SILENCE) {
            collapseAsh(world, focus, t - ECLIPSE_COLLAPSE);
        } else if (t < ECLIPSE_NOVA) {
            sealSilence(world, focus, t - ECLIPSE_SILENCE);
        } else if (t < ECLIPSE_SHUT) {
            if (t == ECLIPSE_NOVA) {
                ashNova(world, focus);
            }
            tickAshNova(world, focus, t - ECLIPSE_NOVA);
        } else if (t < ECLIPSE_SETTLE) {
            shutSeal(world, t - ECLIPSE_SHUT);
        } else {
            settleAsh(world, t - ECLIPSE_SETTLE);
        }
    }

    /** 185-203: every sound cut, ash pulled in, and a tear of violet light opens in the black sky. */
    private void hushEclipse(World world, Location focus, int u) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (u == 0) {
            for (Player player : fighters()) {
                player.stopAllSounds();
            }
            world.playSound(focus, Sound.BLOCK_CONDUIT_DEACTIVATE, 1.4f, 0.4f);
            world.playSound(focus, Sound.ENTITY_WARDEN_HEARTBEAT, 1.8f, 0.38f);
            tearYaw = random.nextDouble(Math.PI);
            tearGash = spawnBlock(eclipseSky, Material.PURPLE_STAINED_GLASS.createBlockData(), 0, 4.0f);
            tearCore = spawnBlock(eclipseSky, Material.WHITE_STAINED_GLASS.createBlockData(), 0, 4.0f);
        }
        if (u == 3) {
            world.playSound(eclipseSky, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, 0.4f);
            world.playSound(eclipseSky, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 0.6f);
        }
        if (u == 9 || u == 15) {
            world.playSound(focus, Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.34f);
        }
        tearLen = 30.0 * easeOut(u / 17.0);
        poseTear(tearLen, 0.9f, 0.22f);
        for (int i = 0; i < 4 + u / 2; i++) {
            Vector dir = randomUnit();
            double r = 8.0 + random.nextDouble(12.0);
            Location from = focus.clone().add(dir.clone().multiply(r));
            world.spawnParticle(Particle.END_ROD, from, 0, -dir.getX(), -dir.getY(), -dir.getZ(), r * 0.08, null, true);
            world.spawnParticle(Particle.WHITE_ASH, from, 0,
                    -dir.getX() * 0.15, -dir.getY() * 0.15, -dir.getZ() * 0.15, 0.2, null, true);
        }
        if (u % 3 == 0) {
            ringForce(world, focus, 6.0 - u * 0.25, SOUL, 0.85f);
            tearDust(world, tearLen, 0.55f);
        }
        if (heartCore != null && heartCore.isValid()) {
            put(heartCore, focus, centered(new Quaternionf().rotateY(u * 0.35f), Math.max(0.14f, 0.55f - u * 0.02f)), 1);
        }
    }

    /** 204-223: chains climb out of the floor, hook the tear and drag it closed. */
    private void haulSkyShut(World world, Location focus, int u) {
        if (u == 0) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + 0.2;
                Location anchor = ground(center.clone().add(Math.cos(a) * (arenaRadius * 0.62), 0, Math.sin(a) * (arenaRadius * 0.62)));
                Vector dir = eclipseSky.toVector().subtract(anchor.toVector()).normalize();
                skyChains.add(spawnBlock(anchor, Material.CHAIN.createBlockData(), 0, 4.0f));
                skyChainFoot.add(anchor);
                skyChainDir.add(dir);
                floorBurst(world, anchor, 26, 0.7);
                world.spawnParticle(Particle.REVERSE_PORTAL, anchor.clone().add(0, 0.6, 0), 18, 0.4, 0.4, 0.4, 0.05);
            }
            world.playSound(center, Sound.ENTITY_WARDEN_EMERGE, 1.8f, 0.5f);
            world.playSound(center, Sound.BLOCK_CHAIN_PLACE, 2.0f, 0.4f);
            shout("&8&oThe Throne&7: &fGive it back.");
        }
        double grow = easeOut(u / 9.0);
        for (int i = 0; i < skyChains.size(); i++) {
            Location anchor = skyChainFoot.get(i);
            double span = anchor.distance(eclipseSky) * grow;
            Vector dir = skyChainDir.get(i);
            put(skyChains.get(i), anchor,
                    rod(new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()), 0f, (float) span, 0.34f), 2);
        }
        double p = Math.max(0.0, (u - 8) / 11.0);
        tearLen = 30.0 * (1.0 - easeInOut(p));
        poseTear(tearLen, 0.9f - (float) p * 0.6f, 0.22f - (float) p * 0.16f);
        if (u >= 8 && u % 2 == 0) {
            world.playSound(center, Sound.BLOCK_GRINDSTONE_USE, 1.1f, 0.35f + (float) p * 0.5f);
            world.playSound(center, Sound.BLOCK_CHAIN_STEP, 1.4f, 0.4f + (float) p * 0.4f);
        }
        if (u % 3 == 0) {
            tearDust(world, tearLen, 0.7f);
            world.spawnParticle(Particle.WHITE_ASH, focus.clone().add(0, 2, 0), 18, 5.0, 2.0, 5.0, 0.01, null, true);
        }
        if (u == 19) {
            world.spawnParticle(Particle.FLASH, eclipseSky, 2, 0.3, 0.1, 0.3, 0, null, true);
            world.playSound(center, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.4f);
            world.playSound(center, Sound.ITEM_TRIDENT_HIT, 1.8f, 0.45f);
            titleNear("&8&lTHE LAST SKY", "&7…is shut.");
        }
    }

    /** 224-235: twelve spears of ash come off the rim and drive into the heart, one after another. */
    private void collapseAsh(World world, Location focus, int u) {
        if (u == 0) {
            for (int i = 0; i < ASH_SPEARS; i++) {
                double a = i * Math.PI * 2 / ASH_SPEARS + 0.4;
                Location from = center.clone().add(
                        Math.cos(a) * arenaRadius * 0.85, 7.0 + (i % 3) * 2.0, Math.sin(a) * arenaRadius * 0.85);
                ashIn.add(spawnBlock(from, ASH_IN[i % ASH_IN.length].createBlockData(), 0, 4.0f));
                ashInFrom.add(from);
            }
            for (int i = 0; i < skyChains.size(); i++) {
                Vector dir = skyChainDir.get(i);
                put(skyChains.get(i), skyChainFoot.get(i),
                        rod(new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()), 0f, 0.001f, 0.34f), 11);
            }
            world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.8f, 0.4f);
            world.playSound(focus, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.35f);
        }
        double p = u / 11.0;
        for (int i = 0; i < ashIn.size(); i++) {
            BlockDisplay spear = ashIn.get(i);
            if (spear == null || !spear.isValid()) {
                continue;
            }
            Location from = ashInFrom.get(i);
            Vector dir = focus.toVector().subtract(from.toVector());
            double dist = dir.length();
            if (dist < 0.5) {
                continue;
            }
            dir.multiply(1.0 / dist);
            double q = easeOut(Math.max(0.0, Math.min(1.0, (u - i * 0.35) / 9.0)));
            float travel = (float) (q * Math.max(0.5, dist - 2.2));
            put(spear, from, rod(new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()),
                    travel, (float) (2.2 * (1.0 - q * 0.55)), 0.5f), 2);
            if (u == (int) Math.ceil(i * 0.35) + 9) {
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, focus, 8, 0.5, 0.5, 0.5, 0.03, null, true);
                world.playSound(focus, Sound.BLOCK_GRAVEL_BREAK, 1.3f, 0.4f + i * 0.04f);
            }
        }
        ringForce(world, focus, 9.0 * (1.0 - easeInOut(p)) + 0.6, ASH, 1.4f);
        if (u % 2 == 0) {
            tiltedAshRing(world, focus, 6.0 * (1.0 - p) + 0.8, u * 0.3);
        }
        if (u % 3 == 0) {
            world.spawnParticle(Particle.WHITE_ASH, focus, 20, 4.0, 2.5, 4.0, 0.01, null, true);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, focus, 6, 3.0, 2.0, 3.0, 0.01, null, true);
        }
        if (heartCore != null && heartCore.isValid()) {
            put(heartCore, focus, centered(new Quaternionf().rotateY(u * 0.6f).rotateX(0.4f), 0.16f), 1);
        }
    }

    /** 236-239: the shells are gone, one black pinpoint remains, and the rebound is staged unseen. */
    private void sealSilence(World world, Location focus, int u) {
        if (u == 0) {
            for (Player player : fighters()) {
                player.stopAllSounds();
            }
            for (BlockDisplay spear : ashIn) {
                discard(spear);
            }
            ashIn.clear();
            ashInFrom.clear();
            for (BlockDisplay link : skyChains) {
                discard(link);
            }
            skyChains.clear();
            skyChainFoot.clear();
            skyChainDir.clear();
            discard(tearGash);
            discard(tearCore);
            tearGash = null;
            tearCore = null;
            clearRig();
            heartFloat = focus.clone();
            eclipseSeed = spawnBlock(focus, Material.BLACK_CONCRETE.createBlockData(), 1, 4.0f);
            stageAshNova();
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = focus.clone();
        if (u >= 2) {
            at.add(random.nextDouble(-0.07, 0.07), random.nextDouble(-0.07, 0.07), random.nextDouble(-0.07, 0.07));
        }
        put(eclipseSeed, at, centered(new Quaternionf().rotateY(u * 0.8f).rotateX(0.6f), u < 2 ? 0.3f : 0.08f), 1);
        if (u == 2) {
            world.spawnParticle(Particle.DUST, focus, 30, 2.0, 2.0, 2.0, 0, new Particle.DustOptions(ASH, 0.8f), true);
        }
    }

    /** Spawned invisible during the silence so every piece interpolates from the first frame of the nova. */
    private void stageAshNova() {
        // The rebound blooms in the sky where the tear was, so no shell can ever swallow the camera.
        for (int layer = 0; layer < ASH_OUT.length; layer++) {
            for (int k = 0; k < 2; k++) {
                ashOut.add(spawnBlock(eclipseSky, ASH_OUT[layer].createBlockData(), 0, 4.0f));
            }
        }
        // The Throne's seal: two turning rings and eight spokes burned into the arena floor.
        Location floor = center.clone().add(0, 0.14, 0);
        int outer = 22;
        for (int i = 0; i < outer; i++) {
            double a = i * Math.PI * 2 / outer;
            seal.add(new SealPiece(spawnBlock(floor, Material.GOLD_BLOCK.createBlockData(), 0, 4.0f),
                    a, arenaRadius * 0.62, 0, 2 * (float) Math.PI * (float) (arenaRadius * 0.62) / outer * 1.06f));
        }
        int inner = 16;
        for (int i = 0; i < inner; i++) {
            double a = i * Math.PI * 2 / inner;
            seal.add(new SealPiece(spawnBlock(floor, Material.PURPLE_STAINED_GLASS.createBlockData(), 0, 4.0f),
                    a, arenaRadius * 0.34, 1, 2 * (float) Math.PI * (float) (arenaRadius * 0.34) / inner * 1.06f));
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            seal.add(new SealPiece(spawnBlock(floor, Material.CRYING_OBSIDIAN.createBlockData(), 0, 4.0f),
                    a, arenaRadius * 0.7, 2, 0.55f));
        }
        // The eclipse overhead: four crossed slabs read as a dark disc, ringed by a burning corona.
        for (int i = 0; i < eclipseDisc.length; i++) {
            eclipseDisc[i] = spawnBlock(eclipseSky, Material.BLACK_CONCRETE.createBlockData(), 0, 4.0f);
        }
        int ring = 20;
        for (int i = 0; i < ring; i++) {
            double a = i * Math.PI * 2 / ring;
            corona.add(new SealPiece(spawnBlock(eclipseSky,
                    (i % 2 == 0 ? Material.GOLD_BLOCK : Material.MAGMA_BLOCK).createBlockData(), 0, 4.0f),
                    a, 7.2, 0, 2 * (float) Math.PI * 7.2f / ring * 1.08f));
        }
    }

    /** 240: the collapse rebounds. The loudest frame of the fight, and not one point of damage in it. */
    private void ashNova(World world, Location focus) {
        discard(eclipseSeed);
        eclipseSeed = null;
        world.spawnParticle(Particle.FLASH, focus, 8, 1.4, 1.4, 1.4, 0, null, true);
        world.spawnParticle(Particle.FLASH, eclipseSky, 4, 2.0, 1.0, 2.0, 0, null, true);
        burst(world, eclipseSky, Particle.WHITE_ASH, 90, 2.2);
        burst(world, eclipseSky, Particle.END_ROD, 70, 1.8);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, focus, 4, 1.0, 1.0, 1.0, 0, null, true);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            world.spawnParticle(Particle.SONIC_BOOM, focus.clone().add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6), 1, 0, 0, 0, 0, null, true);
        }
        burst(world, focus, Particle.WHITE_ASH, 160, 1.6);
        burst(world, focus, Particle.SOUL_FIRE_FLAME, 90, 0.9);
        burst(world, focus, Particle.END_ROD, 110, 1.3);
        burst(world, focus, Particle.FLAME, 80, 1.0);
        burst(world, focus, Particle.CAMPFIRE_COSY_SMOKE, 50, 0.7);
        world.spawnParticle(Particle.REVERSE_PORTAL, focus, 120, 1.4, 1.4, 1.4, 0.5, null, true);
        world.spawnParticle(Particle.DUST, focus, 80, 2.2, 2.2, 2.2, 0, new Particle.DustOptions(ASH, 2.4f), true);
        world.spawnParticle(Particle.DUST, focus, 60, 3.2, 3.2, 3.2, 0, new Particle.DustOptions(ROYAL, 2.0f), true);
        world.spawnParticle(Particle.DUST, focus, 50, 4.2, 4.2, 4.2, 0, new Particle.DustOptions(EMBER, 1.8f), true);
        for (int i = 0; i < 5; i++) {
            ringForce(world, center.clone().add(0, 0.2, 0), 3.0 + i * 4.6, i % 2 == 0 ? EMBER : ROYAL, 1.3f);
        }
        for (Player player : fighters()) {
            Location eye = player.getEyeLocation();
            player.spawnParticle(Particle.FLASH, eye.add(eye.getDirection().multiply(1.2)), 2, 0, 0, 0, 0);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI / 10;
            Material rock = switch (i % 4) {
                case 0 -> Material.CRYING_OBSIDIAN;
                case 1 -> Material.MAGMA_BLOCK;
                case 2 -> Material.GOLD_BLOCK;
                default -> Material.BASALT;
            };
            spawnDebris(focus, rock,
                    new Vector(Math.cos(a) * random.nextDouble(0.3, 0.55), 0.2 + random.nextDouble(0.3), Math.sin(a) * random.nextDouble(0.3, 0.55)),
                    (float) random.nextDouble(0.3, 0.55), 40 + random.nextInt(20));
        }
        world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.4f);
        world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.7f);
        world.playSound(focus, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.45f);
        world.playSound(focus, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.8f, 0.45f);
        world.playSound(focus, Sound.ITEM_TRIDENT_THUNDER, 1.6f, 0.45f);
        world.playSound(focus, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.5f, 0.4f);
        world.playSound(focus, Sound.BLOCK_BELL_USE, 1.8f, 0.35f);
        world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.6f, 0.4f);
        world.playSound(focus, Sound.BLOCK_END_PORTAL_SPAWN, 0.9f, 0.7f);
        world.playSound(focus, Sound.ITEM_TOTEM_USE, 0.9f, 0.45f);
        world.playSound(focus, Sound.BLOCK_BEACON_ACTIVATE, 1.8f, 0.4f);
    }

    /** 241-281: the seal turns, the eclipse burns overhead, ash buries the Throne. */
    private void tickAshNova(World world, Location focus, int age) {
        int span = ECLIPSE_SHUT - ECLIPSE_NOVA;
        double p = age / (double) span;
        double reach = Math.max(10.0, arenaRadius * 0.8);
        for (int layer = 0; layer < ASH_OUT.length; layer++) {
            double max = reach * ASH_OUT_REACH[layer];
            int ticks = ASH_OUT_TICKS[layer];
            for (int k = 0; k < 2; k++) {
                int index = layer * 2 + k;
                if (index >= ashOut.size()) {
                    continue;
                }
                BlockDisplay shell = ashOut.get(index);
                if (shell == null || !shell.isValid()) {
                    continue;
                }
                if (age > ticks + 4) {
                    discard(shell);
                    ashOut.set(index, null);
                    continue;
                }
                double grown = max * easeOut(Math.min(1.0, age / (double) ticks));
                float size = (float) (age > ticks ? grown * (1.0 - (age - ticks) / 5.0) : grown);
                Quaternionf rot = k == 0
                        ? new Quaternionf().rotateY(age * 0.16f + layer)
                        : new Quaternionf().rotateY(0.785f - age * 0.16f).rotateX(0.615f).rotateZ(0.785f);
                put(shell, eclipseSky, centered(rot, Math.max(0.001f, size)), 2);
            }
        }
        // Seal: rings draw in the first eight ticks, then turn slowly against each other.
        double draw = Math.min(1.0, age / 8.0);
        poseSeal(easeOut(draw), age * 0.012);
        poseEclipse(world, Math.min(1.0, age / 12.0), age * 0.02, age);
        if (age % 2 == 0) {
            world.spawnParticle(Particle.WHITE_ASH, center.clone().add(0, 6, 0), 40, arenaRadius * 0.6, 4.0, arenaRadius * 0.6, 0.02, null, true);
        }
        if (age % 3 == 0) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0, 1.5, 0), 4, arenaRadius * 0.5, 1.0, arenaRadius * 0.5, 0.01, null, true);
        }
        // Twin sweeps: one royal, one ember, tracing the seal out to its rim.
        double sweep = age * 0.19;
        for (int beam = 0; beam < 2; beam++) {
            double a = sweep + beam * Math.PI;
            Color tint = beam == 0 ? ROYAL : EMBER;
            for (int s = 2; s <= 16; s++) {
                Location at = center.clone().add(Math.cos(a) * s * 0.9, 0.35 + Math.sin(s * 0.3) * 0.25, Math.sin(a) * s * 0.9);
                world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(tint, 1.1f), true);
                if (s % 3 == 0) {
                    world.spawnParticle(Particle.END_ROD, at, 1, 0, 0, 0, 0, null, true);
                }
            }
        }
        if (age == 2) {
            world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.8f, 0.4f);
            world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.6f, 0.4f);
        }
        if (age == 4) {
            titleNear("&8&l✦ ASH NOVA ✦", "&5The Throne takes the sky back.");
        }
        if (age == 6) {
            shout("&5&lAetherion &7fell. &8The Throne kept the sky, as it always does.");
        }
        if (age == 14 || age == 28) {
            world.playSound(center, Sound.BLOCK_BELL_RESONATE, 1.2f, 0.4f);
            world.playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 0.5f);
        }
        if (age == 22) {
            world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.1f, 0.4f);
        }
        if (age % 8 == 0) {
            ringForce(world, center.clone().add(0, 0.2, 0), arenaRadius * 0.62, GOLD, 1.0f);
        }
        if (age % 6 == 3) {
            ringForce(world, center.clone().add(0, 0.2, 0), arenaRadius * 0.34, ROYAL, 0.9f);
        }
        if (p > 0.8 && age % 4 == 0) {
            world.playSound(center, Sound.BLOCK_GRINDSTONE_USE, 0.8f, 0.3f);
        }
    }

    /** 282-309: the seal contracts in three grinding slams, and the eclipse closes over it. */
    private void shutSeal(World world, int u) {
        int span = ECLIPSE_SETTLE - ECLIPSE_SHUT;
        double p = easeInOut(u / (double) (span - 1));
        poseSeal(1.0 - p * 0.94, (ECLIPSE_SHUT - ECLIPSE_NOVA) * 0.012 + u * 0.05);
        poseEclipse(world, 1.0 - p, (ECLIPSE_SHUT - ECLIPSE_NOVA) * 0.02 + u * 0.06, ECLIPSE_SHUT + u);
        if (u == 4 || u == 13 || u == 22) {
            double r = arenaRadius * (u == 4 ? 0.62 : u == 13 ? 0.4 : 0.18);
            world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.6f, 0.4f);
            world.playSound(center, Sound.BLOCK_ANVIL_LAND, 1.4f, 0.38f);
            world.playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 1.4f, 0.4f);
            ringForce(world, center.clone().add(0, 0.2, 0), r, GOLD, 1.6f);
            floorBurst(world, center.clone().add(Math.cos(u) * r, 0, Math.sin(u) * r), 40, 2.0);
            world.spawnParticle(Particle.WHITE_ASH, center.clone().add(0, 1.5, 0), 60, r, 1.2, r, 0.02, null, true);
        }
        if (u % 3 == 0) {
            world.spawnParticle(Particle.WHITE_ASH, center.clone().add(0, 5, 0), (int) (30 * (1.0 - p)), arenaRadius * 0.5, 3.0, arenaRadius * 0.5, 0.02, null, true);
        }
        if (u == span - 2) {
            world.spawnParticle(Particle.FLASH, center.clone().add(0, 0.6, 0), 2, 0.3, 0.1, 0.3, 0, null, true);
            converge(world, center.clone().add(0, 0.8, 0), 6.0, Particle.SOUL_FIRE_FLAME, 40);
            world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.6f, 0.4f);
            world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.35f);
            for (SealPiece piece : seal) {
                discard(piece.display);
            }
            seal.clear();
            for (SealPiece piece : corona) {
                discard(piece.display);
            }
            corona.clear();
            for (BlockDisplay slab : eclipseDisc) {
                discard(slab);
            }
            java.util.Arrays.fill(eclipseDisc, null);
        }
    }

    /** 310-329: the ash thins out, dawn comes back over the Throne. */
    private void settleAsh(World world, int u) {
        int span = ECLIPSE_CLEAR - ECLIPSE_SETTLE;
        double fade = u / (double) span;
        if (u % 3 == 0) {
            world.spawnParticle(Particle.WHITE_ASH, center.clone().add(0, 4, 0),
                    (int) (26 * (1.0 - fade)), arenaRadius * 0.55, 3.0, arenaRadius * 0.55, 0.01, null, true);
        }
        if (u % 4 == 0) {
            Location mote = center.clone().add(
                    ThreadLocalRandom.current().nextDouble(-arenaRadius * 0.5, arenaRadius * 0.5), 0.5,
                    ThreadLocalRandom.current().nextDouble(-arenaRadius * 0.5, arenaRadius * 0.5));
            world.spawnParticle(Particle.END_ROD, mote, 1, 0.05, 0.05, 0.05, 0.04, null, true);
        }
        if (u == 8) {
            restoreSky();
            world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.6f);
        }
        if (u == 14) {
            shout("&8…and the ash settles on the Throne.");
            world.playSound(center, Sound.BLOCK_BELL_RESONATE, 0.8f, 0.6f);
        }
    }

    // --- eclipse geometry

    /** The tear is one long thin slab high over the arena, with a white seam inside it. */
    private void poseTear(double length, float height, float coreHeight) {
        float len = (float) Math.max(0.001, length);
        Vector3f axis = new Vector3f((float) Math.cos(tearYaw), 0f, (float) Math.sin(tearYaw));
        put(tearGash, eclipseSky, rod(axis, -len / 2f, len, height), 2);
        put(tearCore, eclipseSky, rod(axis, -len / 2f, len, coreHeight), 2);
    }

    private void tearDust(World world, double length, float size) {
        if (length < 0.5) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(ROYAL, size);
        for (double d = -length / 2; d <= length / 2; d += 1.2) {
            Location at = eclipseSky.clone().add(Math.cos(tearYaw) * d, 0, Math.sin(tearYaw) * d);
            world.spawnParticle(Particle.DUST, at, 1, 0.1, 0.1, 0.1, 0, dust, true);
        }
    }

    /** Both seal rings and the spokes, scaled by {@code shrink} and turned by {@code spin}. */
    private void poseSeal(double shrink, double spin) {
        Location floor = center.clone().add(0, 0.14, 0);
        for (SealPiece piece : seal) {
            if (piece.display == null || !piece.display.isValid()) {
                continue;
            }
            double r = piece.radius * Math.max(0.02, shrink);
            double a = piece.angle + (piece.ring == 1 ? -spin * 1.6 : spin);
            if (piece.ring == 2) {
                Vector3f dir = new Vector3f((float) Math.cos(a), 0f, (float) Math.sin(a));
                put(piece.display, floor, rod(dir, (float) (r * 0.25), (float) (r * 0.78), piece.length), 2);
            } else {
                float len = (float) (piece.length * Math.max(0.02, shrink));
                put(piece.display, floor, segment(a, r, 0.0, len, 0.08f, piece.ring == 0 ? 0.3f : 0.22f), 2);
            }
        }
    }

    /** Four crossed slabs read as a dark disc overhead; the corona burns around its rim. */
    private void poseEclipse(World world, double open, double spin, int age) {
        float size = (float) Math.max(0.001, 12.0 * clamp(open, 0.0, 1.0));
        for (int i = 0; i < eclipseDisc.length; i++) {
            BlockDisplay slab = eclipseDisc[i];
            if (slab == null || !slab.isValid()) {
                continue;
            }
            Quaternionf rot = new Quaternionf().rotateY((float) (spin * 0.4 + i * Math.PI / 4));
            Vector3f scale = new Vector3f(size, 0.5f, size * 0.42f);
            Vector3f offset = rot.transform(new Vector3f(scale).mul(-0.5f));
            put(slab, eclipseSky, new Transformation(offset, rot, scale, new Quaternionf()), 2);
        }
        for (SealPiece piece : corona) {
            if (piece.display == null || !piece.display.isValid()) {
                continue;
            }
            double a = piece.angle + spin;
            double r = piece.radius * Math.max(0.02, open);
            float len = (float) (piece.length * Math.max(0.02, open));
            put(piece.display, eclipseSky, segment(a, r, 0.0, len, 0.55f, 0.55f), 2);
        }
        if (open > 0.2 && age % 2 == 0) {
            double a = spin * 3.0;
            for (int i = 0; i < 20; i++) {
                double ang = a + i * Math.PI * 2 / 20;
                Location at = eclipseSky.clone().add(Math.cos(ang) * 7.2, 0, Math.sin(ang) * 7.2);
                world.spawnParticle(Particle.DUST, at, 1, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(i % 2 == 0 ? GOLD : ROYAL, 1.6f), true);
            }
        }
    }

    private void clearEclipse() {
        for (BlockDisplay spear : ashIn) {
            discard(spear);
        }
        ashIn.clear();
        ashInFrom.clear();
        for (BlockDisplay shell : ashOut) {
            discard(shell);
        }
        ashOut.clear();
        for (BlockDisplay rod : skyChains) {
            discard(rod);
        }
        skyChains.clear();
        skyChainDir.clear();
        for (SealPiece piece : seal) {
            discard(piece.display);
        }
        seal.clear();
        for (SealPiece piece : corona) {
            discard(piece.display);
        }
        corona.clear();
        for (int i = 0; i < eclipseDisc.length; i++) {
            discard(eclipseDisc[i]);
            eclipseDisc[i] = null;
        }
        discard(tearGash);
        discard(tearCore);
        discard(eclipseSeed);
        tearGash = null;
        tearCore = null;
        eclipseSeed = null;
        eclipseSky = null;
        tearLen = 0;
    }

    /** One bar of a turning ring (or one radial spoke) of the Throne's seal. */
    private static final class SealPiece {
        private final BlockDisplay display;
        private final double angle;
        private final double radius;
        private final int ring;
        private final float length;

        private SealPiece(BlockDisplay display, double angle, double radius, int ring, float length) {
            this.display = display;
            this.angle = angle;
            this.radius = radius;
            this.ring = ring;
            this.length = length;
        }
    }

    // ------------------------------------------------------------------ chains

    private void spawnChains(boolean instant) {
        clearChains();
        World world = center.getWorld();
        for (int i = 0; i < CHAINS; i++) {
            Location a = anchor(i);
            Chain chain = new Chain(a);
            chain.pylon = spawnBlock(a, Material.OBSIDIAN.createBlockData(), 0, 3.0f);
            chain.cap = spawnBlock(a, Material.SOUL_LANTERN.createBlockData(), 0, 3.0f);
            chain.rune = spawnBlock(a, Material.CRYING_OBSIDIAN.createBlockData(), 0, 3.0f);
            Transformation pylon = new Transformation(new Vector3f(-0.6f, 0f, -0.6f), new Quaternionf(), new Vector3f(1.2f, 2.3f, 1.2f), new Quaternionf());
            Transformation rune = new Transformation(new Vector3f(-0.66f, 0.9f, -0.66f), new Quaternionf(), new Vector3f(1.32f, 0.4f, 1.32f), new Quaternionf());
            Transformation cap = new Transformation(new Vector3f(-0.5f, 2.3f, -0.5f), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf());
            if (instant) {
                ease(chain.pylon, pylon, 1);
                ease(chain.rune, rune, 1);
                ease(chain.cap, cap, 1);
                chain.reach = 1.0;
            } else {
                chain.pylon.setTransformation(sunk(pylon));
                chain.rune.setTransformation(sunk(rune));
                chain.cap.setTransformation(sunk(cap));
                Chain c = chain;
                Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
                    ease(c.pylon, pylon, 14);
                    ease(c.rune, rune, 14);
                    ease(c.cap, cap, 14);
                }, 2L);
            }
            chains.add(chain);
        }
        if (instant && world != null) {
            world.playSound(center, Sound.BLOCK_CHAIN_PLACE, 1.4f, 0.6f);
        }
    }

    private static Transformation sunk(Transformation t) {
        Vector3f tr = new Vector3f(t.getTranslation()).add(0f, -3.4f, 0f);
        return new Transformation(tr, t.getLeftRotation(), t.getScale(), t.getRightRotation());
    }

    private boolean chainsAlive() {
        for (Chain chain : chains) {
            if (!chain.broken && chain.pylon != null && chain.pylon.isValid()) {
                return true;
            }
        }
        return false;
    }

    private Location attachPoint(Chain chain) {
        Location body = partPos(2).add(0, 1.2, 0);
        Vector toward = flatDir(body, chain.anchor, heading);
        return body.add(toward.multiply(1.4));
    }

    /** Lays links along anchor → dragon. Links alternate a quarter turn so they read as a chain. */
    private void tickChains() {
        if (chains.isEmpty()) {
            return;
        }
        boolean refresh = clock % 2 == 0 || isDying();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Chain chain : chains) {
            if (chain.broken) {
                continue;
            }
            chain.slam *= 0.86;
            if (chain.slam < 0.02) {
                chain.slam = 0.0;
            }
            if (!refresh || chain.reach <= 0.0) {
                continue;
            }
            Location a = chain.anchor.clone().add(0, chain.pylonless ? 0.2 : 2.6, 0);
            Location b = lerp(a, attachPoint(chain), chain.reach);
            Vector span = b.toVector().subtract(a.toVector());
            double len = span.length();
            if (len < 0.3) {
                continue;
            }
            int n = (int) Math.min(22, Math.max(1, Math.ceil(len / LINK)));
            double seg = len / n;
            while (chain.links.size() < n) {
                BlockDisplay link = spawnBlock(a, Material.CHAIN.createBlockData(), 2, 3.0f);
                chain.links.add(link);
            }
            while (chain.links.size() > n) {
                discard(chain.links.remove(chain.links.size() - 1));
            }
            Vector3f axis = new Vector3f((float) span.getX(), (float) span.getY(), (float) span.getZ()).normalize();
            Quaternionf along = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis);
            double sag = 0.35 + chain.slam * 3.5;
            for (int i = 0; i < n; i++) {
                double u = (i + 0.5) / n;
                Location at = lerp(a, b, u).add(0, -sag * 4 * u * (1 - u), 0);
                if (chain.shake > 0) {
                    at.add(random.nextDouble(-chain.shake, chain.shake), random.nextDouble(-chain.shake, chain.shake), random.nextDouble(-chain.shake, chain.shake));
                }
                Quaternionf q = new Quaternionf(along).rotateY(i % 2 == 0 ? 0f : (float) (Math.PI / 2));
                put(chain.links.get(i), at, centered(q, (float) (seg * 1.05)), 2);
            }
        }
    }

    private void strainSparks() {
        World world = center.getWorld();
        for (Chain chain : chains) {
            if (chain.broken || chain.reach < 1.0) {
                continue;
            }
            Location at = attachPoint(chain);
            world.spawnParticle(Particle.ELECTRIC_SPARK, at, 6, 0.2, 0.2, 0.2, 0.15);
            world.spawnParticle(Particle.CRIT, at, 3, 0.2, 0.2, 0.2, 0.2);
        }
        world.playSound(pos, Sound.BLOCK_CHAIN_STEP, 1.0f, 0.6f);
    }

    private void snapChain(Chain chain) {
        if (chain == null || chain.broken) {
            return;
        }
        World world = center.getWorld();
        Location at = attachPoint(chain);
        world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, null, true);
        world.spawnParticle(Particle.EXPLOSION, at, 2, 0.4, 0.4, 0.4, 0, null, true);
        burst(world, at, Particle.ELECTRIC_SPARK, 30, 0.4);
        world.playSound(at, Sound.ITEM_SHIELD_BREAK, 2.0f, 0.5f);
        world.playSound(at, Sound.BLOCK_CHAIN_BREAK, 2.0f, 0.5f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.2f);
        crumbleChain(chain);
        if (chain.cap != null && chain.cap.isValid()) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, chain.anchor.clone().add(0, 3.0, 0), 30, 0.3, 0.6, 0.3, 0.05, null, true);
        }
    }

    /** Links fly apart as physical debris; the pylon (if any) stays behind. */
    private void crumbleChain(Chain chain) {
        chain.broken = true;
        Location a = chain.anchor.clone();
        for (BlockDisplay link : chain.links) {
            if (link == null || !link.isValid()) {
                fx.remove(link);
                continue;
            }
            Location p = link.getLocation();
            Vector out = p.toVector().subtract(a.toVector()).setY(0);
            if (out.lengthSquared() < 0.01) {
                out = randomUnit().setY(0);
            }
            Vector v = out.normalize().multiply(0.2 + ThreadLocalRandom.current().nextDouble(0.25)).setY(0.25 + ThreadLocalRandom.current().nextDouble(0.3));
            debris.add(new Debris(link, p.clone(), v, 26 + ThreadLocalRandom.current().nextInt(12), 1.2f));
        }
        chain.links.clear();
    }

    private void sinkPylons() {
        for (Chain chain : chains) {
            for (BlockDisplay d : new BlockDisplay[]{chain.pylon, chain.rune, chain.cap}) {
                if (d != null && d.isValid()) {
                    ease(d, sunk(d.getTransformation()), 12);
                }
            }
        }
        center.getWorld().playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 1.4f, 0.6f);
    }

    private void clearChains() {
        for (Chain chain : chains) {
            for (BlockDisplay link : chain.links) {
                discard(link);
            }
            chain.links.clear();
            discard(chain.pylon);
            discard(chain.cap);
            discard(chain.rune);
        }
        chains.clear();
    }

    // ------------------------------------------------------------------ the last sky: sun, wards, fire wall

    private void spawnSun(Location at) {
        clearSun();
        sunPos = at.clone();
        sunCore = spawnBlock(at, Material.SHROOMLIGHT.createBlockData(), 1, 6.0f);
        sunShell = spawnBlock(at, Material.ORANGE_STAINED_GLASS.createBlockData(), 1, 6.0f);
        sunHalo = spawnBlock(at, Material.RED_STAINED_GLASS.createBlockData(), 1, 6.0f);
        for (int i = 0; i < sunRays.length; i++) {
            sunRays[i] = spawnBlock(at, (i % 2 == 0 ? Material.MAGMA_BLOCK : Material.SHROOMLIGHT).createBlockData(), 1, 6.0f);
        }
    }

    private void poseSun(double scale, int ticks) {
        if (sunPos == null) {
            return;
        }
        sunSpin += 0.05f;
        float core = (float) (3.6 * scale);
        float shell = (float) (5.0 * scale);
        float halo = (float) (6.4 * scale);
        put(sunCore, sunPos, centered(new Quaternionf().rotateY(sunSpin).rotateX(sunSpin * 0.7f), core), ticks);
        put(sunShell, sunPos, centered(new Quaternionf().rotateY(-sunSpin * 0.8f).rotateZ(sunSpin * 0.5f), shell), ticks);
        put(sunHalo, sunPos, centered(new Quaternionf().rotateX(sunSpin * 0.4f).rotateY(sunSpin * 0.3f), halo), ticks);
        for (int i = 0; i < sunRays.length; i++) {
            double a = sunSpin * 0.6 + i * Math.PI * 2 / sunRays.length;
            Vector3f dir = new Vector3f((float) Math.cos(a), (float) Math.sin(i * 1.3 + sunSpin) * 0.4f, (float) Math.sin(a)).normalize();
            put(sunRays[i], sunPos, rod(dir, halo * 0.35f, (float) (4.5 * scale), (float) (0.45 * scale)), ticks);
        }
    }

    private void clearSun() {
        discard(sunCore);
        discard(sunShell);
        discard(sunHalo);
        sunCore = null;
        sunShell = null;
        sunHalo = null;
        for (int i = 0; i < sunRays.length; i++) {
            discard(sunRays[i]);
            sunRays[i] = null;
        }
    }

    private void raiseWards() {
        clearWards();
        World world = center.getWorld();
        for (int i = 0; i < CHAINS; i++) {
            Location ward = i < chains.size() ? chains.get(i).anchor.clone() : anchor(i);
            wards.add(ward);
            int segs = 16;
            for (int s = 0; s < segs; s++) {
                double a = s * Math.PI * 2 / segs;
                BlockDisplay seg = spawnBlock(ward, Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), 0, 4.0f);
                seg.setTransformation(segment(a, 0.2, 0.05, 0.01f, 0.01f, 0.01f));
                ease(seg, segment(a, WARD_R, 0.04, (float) (2 * Math.PI * WARD_R / segs * 1.05), 0.12f, 0.18f), 10);
                wardRings.add(seg);
            }
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, ward.clone().add(0, 1, 0), 40, WARD_R * 0.4, 0.6, WARD_R * 0.4, 0.04, null, true);
            world.playSound(ward, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.6f, 0.7f);
            world.playSound(ward, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 1.2f);
        }
    }

    private void tickWards(World world, int t, boolean fading) {
        if (fading) {
            if (!wardRings.isEmpty() && beat(t, 240)) {
                for (BlockDisplay seg : wardRings) {
                    if (seg != null && seg.isValid()) {
                        ease(seg, new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()), 10);
                    }
                }
            }
            if (beat(t, 252)) {
                clearWards();
            }
            return;
        }
        if (t % 3 != 0) {
            return;
        }
        for (Location ward : wards) {
            ringForce(world, ward.clone().add(0, 0.2, 0), WARD_R, SOUL, 1.3f);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, ward.clone().add(0, 0.4, 0), 4, WARD_R * 0.45, 0.2, WARD_R * 0.45, 0.02);
            world.spawnParticle(Particle.SOUL, ward.clone().add(0, 2.5, 0), 1, 0.3, 0.6, 0.3, 0.01);
        }
    }

    private boolean inWard(Location at) {
        for (Location ward : wards) {
            if (horizontal(at, ward) <= WARD_R + 0.35 && Math.abs(at.getY() - ward.getY()) < 4.0) {
                return true;
            }
        }
        return false;
    }

    private void clearWards() {
        for (BlockDisplay seg : wardRings) {
            discard(seg);
        }
        wardRings.clear();
        wards.clear();
    }

    private void spawnFireWall() {
        clearFireWall();
        int segs = 36;
        for (int s = 0; s < segs; s++) {
            double a = s * Math.PI * 2 / segs;
            BlockDisplay seg = spawnBlock(center, (s % 3 == 0 ? Material.SHROOMLIGHT : Material.MAGMA_BLOCK).createBlockData(), 0, 5.0f);
            seg.setTransformation(segment(a, 1.0, 0.0, 0.3f, 0.2f, 0.3f));
            fireWall.add(seg);
        }
        wallRadius = 1.0;
    }

    /** The wall of fire races to the edge in 16 ticks, then burns down and is gone. */
    private void tickFireWall(World world, int age) {
        if (fireWall.isEmpty()) {
            return;
        }
        int segs = fireWall.size();
        double reach = arenaRadius + 2.0;
        double r = 1.0 + (reach - 1.0) * easeOut(Math.min(1.0, age / 16.0));
        float height = age <= 16 ? (float) (1.0 + 2.2 * Math.min(1.0, age / 6.0)) : (float) Math.max(0.02, 3.2 * (1.0 - (age - 16) / 14.0));
        float len = (float) (2 * Math.PI * r / segs * 1.08);
        for (int s = 0; s < segs; s++) {
            double a = s * Math.PI * 2 / segs;
            BlockDisplay seg = fireWall.get(s);
            if (seg != null && seg.isValid()) {
                ease(seg, segment(a, r, 0.0, len, height, 0.35f), 1);
            }
            if (age <= 16 && s % 2 == 0) {
                Location p = center.clone().add(Math.cos(a) * r, 0.6, Math.sin(a) * r);
                world.spawnParticle(Particle.FLAME, p, 2, 0.3, 0.6, 0.3, 0.02, null, true);
            }
        }
        if (age >= 30) {
            clearFireWall();
        }
    }

    private void clearFireWall() {
        for (BlockDisplay seg : fireWall) {
            discard(seg);
        }
        fireWall.clear();
        wallRadius = -1;
    }

    private void clearSky() {
        clearSun();
        clearWards();
        clearFireWall();
        clearChains();
    }

    private void darkenSky() {
        for (Player player : center.getWorld().getPlayers()) {
            if (horizontal(player.getLocation(), center) > arenaRadius + 40.0) {
                continue;
            }
            player.setPlayerTime(18000L, false);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
            darkened.add(player.getUniqueId());
        }
    }

    private void restoreSky() {
        for (UUID id : darkened) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.resetPlayerTime();
            }
        }
        darkened.clear();
    }

    // ------------------------------------------------------------------ hazards

    private void tickHazards(LivingEntity entity) {
        tickEmbers(entity);
        tickWaves(entity);
        tickLanes(entity);
        tickRingBursts(entity);
        tickBurns();
        tickDebris();
    }

    private void tickEmbers(LivingEntity entity) {
        Iterator<Ember> it = embers.iterator();
        while (it.hasNext()) {
            Ember e = it.next();
            World world = e.ground.getWorld();
            if (world == null) {
                discard(e.rock);
                discard(e.glow);
                it.remove();
                continue;
            }
            int remaining = e.delay + (e.flight < 0 ? e.fall : Math.max(0, e.fall - e.flight));
            if (clock % 2 == 0 || remaining <= 8) {
                Color c = remaining <= 8 && remaining % 2 == 0 ? HOT : (e.big ? CRIMSON : EMBER);
                Location disc = e.ground.clone().add(0, 0.12, 0);
                ring(world, disc, e.radius, c, e.big ? 1.5f : 1.2f);
                double fill = 1.0 - remaining / (double) Math.max(1, e.warn);
                ring(world, disc, Math.max(0.3, e.radius * fill), c, 0.9f);
            }
            if (e.big) {
                fuse(e.ground, remaining);
            }
            if (e.delay > 0) {
                e.delay--;
                continue;
            }
            if (e.flight < 0) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                e.from = e.big
                        ? e.ground.clone().add(random.nextDouble(-4, 4), 26.0, random.nextDouble(-4, 4))
                        : headPos();
                e.rock = spawnBlock(e.from, Material.MAGMA_BLOCK.createBlockData(), 1, 3.0f);
                e.glow = spawnBlock(e.from, Material.ORANGE_STAINED_GLASS.createBlockData(), 1, 3.0f);
                e.prev = e.from.clone();
                e.flight = 0;
                world.playSound(e.from, e.big ? Sound.ENTITY_WITHER_SHOOT : Sound.ENTITY_BLAZE_SHOOT, 1.0f, e.big ? 0.6f : 0.8f);
            }
            e.flight++;
            double u = Math.min(1.0, e.flight / (double) e.fall);
            Location at = lerp(e.from, e.ground, e.big ? u * u : u).add(0, Math.sin(Math.PI * u) * e.arc, 0);
            float size = e.big ? 1.3f : 0.8f;
            put(e.rock, at, centered(new Quaternionf().rotateY(e.flight * 0.4f).rotateX(e.flight * 0.3f), size), 1);
            put(e.glow, at, centered(new Quaternionf().rotateY(-e.flight * 0.3f).rotateZ(e.flight * 0.2f), size * 1.35f), 1);
            streak(world, e.prev, at, EMBER, true);
            world.spawnParticle(Particle.FLAME, at, 2, 0.2, 0.2, 0.2, 0.01, null, true);
            e.prev = at.clone();
            if (e.flight >= e.fall) {
                emberImpact(entity, e);
                discard(e.rock);
                discard(e.glow);
                it.remove();
            }
        }
    }

    private void emberImpact(LivingEntity entity, Ember e) {
        World world = e.ground.getWorld();
        Location mid = e.ground.clone().add(0, 0.4, 0);
        world.spawnParticle(e.big ? Particle.EXPLOSION_EMITTER : Particle.EXPLOSION, mid, 1, 0.3, 0.1, 0.3, 0, null, true);
        world.spawnParticle(Particle.LAVA, mid, e.big ? 18 : 8, e.radius * 0.4, 0.2, e.radius * 0.4, 0);
        world.spawnParticle(Particle.FLAME, mid, 24, e.radius * 0.35, 0.2, e.radius * 0.35, 0.06);
        floorBurst(world, e.ground, e.big ? 50 : 24, e.radius * 0.4);
        world.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, e.big ? 1.4f : 0.9f, e.big ? 0.7f : 1.15f);
        world.playSound(mid, Sound.BLOCK_LAVA_EXTINGUISH, 0.7f, 0.7f);
        for (Player player : fighters()) {
            Location pl = player.getLocation();
            if (horizontal(pl, e.ground) > e.radius + 0.4 || Math.abs(pl.getY() - e.ground.getY()) > 3.0) {
                continue;
            }
            BossHits.hurt(player, entity, e.power);
            player.setFireTicks(Math.max(player.getFireTicks(), 50));
            player.setVelocity(away(pl, e.ground).multiply(0.55).setY(0.42));
        }
        addBurn(e.ground, e.radius * 0.75, e.big ? 90 : 55, true, false);
    }

    private void tickWaves(LivingEntity entity) {
        Iterator<Shockwave> it = waves.iterator();
        while (it.hasNext()) {
            Shockwave wave = it.next();
            if (wave.delay > 0) {
                wave.delay--;
                continue;
            }
            World world = wave.center.getWorld();
            if (world == null) {
                it.remove();
                continue;
            }
            wave.radius += wave.speed;
            Location base = wave.center.clone().add(0, 0.25, 0);
            ring(world, base, wave.radius, wave.color, 1.4f);
            if (wave.power > 0) {
                ring(world, base.clone().add(0, 0.3, 0), wave.radius, HOT, 0.7f);
                BlockData floor = floorData(wave.center);
                int points = Math.max(8, (int) (wave.radius * 2.0));
                for (int i = 0; i < points; i += 3) {
                    double a = Math.PI * 2 * i / points;
                    world.spawnParticle(Particle.BLOCK, base.clone().add(Math.cos(a) * wave.radius, 0, Math.sin(a) * wave.radius), 1, 0.1, 0.05, 0.1, 0, floor);
                }
                for (Player player : fighters()) {
                    if (wave.struck.contains(player.getUniqueId())) {
                        continue;
                    }
                    Location pl = player.getLocation();
                    double r = horizontal(pl, wave.center);
                    if (Math.abs(r - wave.radius) > 0.75 || pl.getY() - wave.center.getY() > 0.6
                            || wave.center.getY() - pl.getY() > 2.0) {
                        continue;
                    }
                    wave.struck.add(player.getUniqueId());
                    if (entity != null && entity.isValid()) {
                        BossHits.hurt(player, entity, wave.power);
                    }
                    player.setVelocity(away(pl, wave.center).multiply(0.6).setY(0.45));
                }
            }
            if (wave.radius >= wave.maxRadius) {
                it.remove();
            }
        }
    }

    private void teachWave() {
        if (taught.add(Move.NONE)) {
            actionBar("&6◯ SHOCKWAVE &8| &fJUMP the ring as it passes");
        }
    }

    private void tickLanes(LivingEntity entity) {
        Iterator<Lane> it = lanes.iterator();
        while (it.hasNext()) {
            Lane lane = it.next();
            World world = lane.from.getWorld();
            if (world == null) {
                it.remove();
                continue;
            }
            if (lane.delay > 0) {
                drawLane(lane.from, lane.dir, lane.length, lane.half, 1.0 - lane.delay / (double) Math.max(1, lane.warn), (int) clock, lane.delay, lane.color);
                fuse(lane.from, lane.delay);
                lane.delay--;
                continue;
            }
            Location end = lane.from.clone().add(lane.dir.clone().multiply(lane.length));
            for (double d = 0.0; d <= lane.length; d += 1.0) {
                Location p = lane.from.clone().add(lane.dir.clone().multiply(d));
                if (lane.slam) {
                    floorBurst(world, p, 6, 0.6);
                    world.spawnParticle(Particle.CRIT, p.clone().add(0, 0.4, 0), 2, 0.3, 0.2, 0.3, 0.2);
                    if (((int) d) % 3 == 0) {
                        world.spawnParticle(Particle.REVERSE_PORTAL, p.clone().add(0, 0.6, 0), 4, 0.3, 0.2, 0.3, 0.05);
                    }
                } else {
                    world.spawnParticle(Particle.FLAME, p.clone().add(0, 1.0, 0), 4, 0.2, 0.9, 0.2, 0.02, null, true);
                    if (((int) d) % 2 == 0) {
                        world.spawnParticle(Particle.LAVA, p, 1, 0.2, 0.1, 0.2, 0);
                    }
                }
                if (lane.burn && ((int) d) % 3 == 0) {
                    addBurn(p, lane.half, 50, false, false);
                }
            }
            Location mid = lerp(lane.from, end, 0.5);
            if (lane.slam) {
                world.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
            } else {
                world.playSound(mid, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
                world.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
            }
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                if (segmentDistance(flat(pl), flat(lane.from), flat(end)) > lane.half + 0.3
                        || Math.abs(pl.getY() - lane.from.getY()) > 2.5) {
                    continue;
                }
                if (entity != null && entity.isValid()) {
                    BossHits.hurt(player, entity, lane.power);
                }
                if (lane.slam) {
                    Vector side = right(lane.dir);
                    double s = Math.signum(side.dot(pl.toVector().subtract(lane.from.toVector())));
                    player.setVelocity(side.multiply((s == 0 ? 1 : s) * 0.9).setY(0.4));
                } else {
                    player.setVelocity(player.getVelocity().setY(0.75));
                    player.setFireTicks(Math.max(player.getFireTicks(), 60));
                }
            }
            it.remove();
        }
    }

    private void tickRingBursts(LivingEntity entity) {
        Iterator<RingBurst> it = ringBursts.iterator();
        while (it.hasNext()) {
            RingBurst rb = it.next();
            World world = rb.center.getWorld();
            if (world == null) {
                it.remove();
                continue;
            }
            int points = Math.max(16, (int) Math.ceil(Math.PI * 2 * rb.radius / 0.55));
            if (rb.delay > 0) {
                boolean hot = rb.delay <= 6;
                if (clock % 2 == 0 || hot) {
                    Particle.DustOptions fire = new Particle.DustOptions(hot && rb.delay % 2 == 0 ? HOT : EMBER, hot ? 1.4f : 1.1f);
                    Particle.DustOptions safe = new Particle.DustOptions(SOUL, 1.2f);
                    for (int i = 0; i < points; i++) {
                        double a = Math.PI * 2 * i / points;
                        Location p = rb.center.clone().add(Math.cos(a) * rb.radius, 0.14, Math.sin(a) * rb.radius);
                        world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, rb.inGap(a) ? safe : fire);
                    }
                }
                fuse(rb.center, rb.delay);
                rb.delay--;
                continue;
            }
            for (int i = 0; i < points; i += 2) {
                double a = Math.PI * 2 * i / points;
                if (rb.inGap(a)) {
                    continue;
                }
                Location p = rb.center.clone().add(Math.cos(a) * rb.radius, 0, Math.sin(a) * rb.radius);
                world.spawnParticle(Particle.FLAME, p.clone().add(0, 1.2, 0), 3, 0.15, 1.0, 0.15, 0.02, null, true);
                if (i % 6 == 0) {
                    world.spawnParticle(Particle.LAVA, p, 1, 0.1, 0.1, 0.1, 0);
                    floorBurst(world, p, 4, 0.4);
                }
            }
            world.playSound(rb.center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
            world.playSound(rb.center, Sound.ITEM_FIRECHARGE_USE, 1.4f, 0.6f);
            world.playSound(rb.center, Sound.BLOCK_BASALT_BREAK, 1.4f, 0.5f);
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                double r = horizontal(pl, rb.center);
                if (Math.abs(r - rb.radius) > 1.35 || Math.abs(pl.getY() - rb.center.getY()) > 2.5) {
                    continue;
                }
                double a = Math.atan2(pl.getZ() - rb.center.getZ(), pl.getX() - rb.center.getX());
                if (rb.inGap(a)) {
                    continue;
                }
                if (entity != null && entity.isValid()) {
                    BossHits.hurt(player, entity, rb.power);
                }
                player.setVelocity(player.getVelocity().setY(0.8));
                player.setFireTicks(Math.max(player.getFireTicks(), 60));
            }
            it.remove();
        }
    }

    private void addBurn(Location at, double radius, int life, boolean decal, boolean silent) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        while (burns.size() >= MAX_BURNS) {
            Burn old = burns.remove(0);
            discard(old.decal);
        }
        Burn burn = new Burn(at.clone(), radius, life);
        if (decal) {
            int decals = 0;
            for (Burn b : burns) {
                if (b.decal != null) {
                    decals++;
                }
            }
            if (decals < MAX_DECALS) {
                float size = (float) (radius * 1.5);
                burn.decal = spawnBlock(at.clone().add(0, 0.02, 0), Material.MAGMA_BLOCK.createBlockData(), 0, 2.0f);
                burn.size = size;
                burn.yaw = (float) ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                ease(burn.decal, flatDecal(burn.yaw, size), 3);
            }
        }
        burns.add(burn);
        if (!silent && ThreadLocalRandom.current().nextInt(4) == 0) {
            at.getWorld().playSound(at, Sound.BLOCK_FIRE_AMBIENT, 0.8f, 0.8f);
        }
    }

    private static Transformation flatDecal(float yaw, float size) {
        Quaternionf q = new Quaternionf().rotateY(yaw);
        Vector3f offset = q.transform(new Vector3f(-size / 2f, 0f, -size / 2f));
        return new Transformation(offset, q, new Vector3f(size, 0.04f, size), new Quaternionf());
    }

    private void tickBurns() {
        Iterator<Burn> it = burns.iterator();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (it.hasNext()) {
            Burn burn = it.next();
            burn.life--;
            World world = burn.at.getWorld();
            if (world == null || burn.life <= 0) {
                discard(burn.decal);
                it.remove();
                continue;
            }
            if ((clock + burn.life) % 3 == 0) {
                double a = random.nextDouble(Math.PI * 2);
                double r = random.nextDouble(burn.radius);
                Location p = burn.at.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r);
                world.spawnParticle(Particle.SMALL_FLAME, p, 1, 0.05, 0.02, 0.05, 0.004);
                if (random.nextInt(3) == 0) {
                    world.spawnParticle(Particle.SMOKE, p, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
            if (burn.decal != null && burn.life <= 16) {
                ease(burn.decal, flatDecal(burn.yaw, Math.max(0.01f, burn.size * burn.life / 16f)), 1);
            }
            if (clock % 10 != 0) {
                continue;
            }
            for (Player player : fighters()) {
                Location pl = player.getLocation();
                if (horizontal(pl, burn.at) > burn.radius || Math.abs(pl.getY() - burn.at.getY()) > 1.6) {
                    continue;
                }
                if (!gate("burn", player, 10)) {
                    continue;
                }
                scorch(player, 0.05);
                player.setFireTicks(Math.max(player.getFireTicks(), 30));
            }
        }
    }

    private void tickDebris() {
        Iterator<Debris> it = debris.iterator();
        while (it.hasNext()) {
            Debris d = it.next();
            d.life--;
            if (d.life <= 0 || d.display == null || !d.display.isValid()) {
                discard(d.display);
                it.remove();
                continue;
            }
            d.pos.add(d.vel);
            d.vel.setY(d.vel.getY() - 0.04);
            d.vel.multiply(0.97);
            double floor = groundY(d.pos.getX(), d.pos.getZ());
            if (d.pos.getY() < floor + 0.1) {
                d.pos.setY(floor + 0.1);
                d.vel.setY(Math.abs(d.vel.getY()) * 0.3);
                d.vel.multiply(0.6);
            }
            d.spin += 0.35f;
            float size = d.size * Math.min(1.0f, d.life / 10.0f);
            put(d.display, d.pos, centered(new Quaternionf().rotateY(d.spin).rotateX(d.spin * 0.8f), size), 1);
        }
    }

    private void spawnDebris(Location at, Material material, Vector vel, float size, int life) {
        BlockDisplay display = spawnBlock(at, material.createBlockData(), 1, 2.0f);
        debris.add(new Debris(display, at.clone(), vel, life, size));
    }

    /** Transition interrupts: pending threats fizzle into smoke instead of landing mid-cinematic. */
    private void fizzleHazards() {
        for (Ember e : embers) {
            if (e.ground.getWorld() != null) {
                e.ground.getWorld().spawnParticle(Particle.LARGE_SMOKE, e.ground.clone().add(0, 0.3, 0), 6, 0.6, 0.2, 0.6, 0.02);
            }
            discard(e.rock);
            discard(e.glow);
        }
        embers.clear();
        lanes.clear();
        ringBursts.clear();
        waves.clear();
        for (Burn burn : burns) {
            discard(burn.decal);
        }
        burns.clear();
    }

    // ------------------------------------------------------------------ rig

    private void spawnRig() {
        clearRig();
        if (pos == null || pos.getWorld() == null) {
            return;
        }
        heartCore = spawnRigBlock(pos, Material.MAGMA_BLOCK.createBlockData());
        heartShell = spawnRigBlock(pos, Material.PURPLE_STAINED_GLASS.createBlockData());
        heartCoreMat = Material.MAGMA_BLOCK;
        heartShellMat = Material.PURPLE_STAINED_GLASS;
        for (int i = 0; i < 5; i++) {
            crown.add(spawnRigBlock(pos, (i % 2 == 0 ? Material.SHROOMLIGHT : Material.MAGMA_BLOCK).createBlockData()));
        }
        crownShown = form() == Form.UNCHAINED ? 5 : 0;
    }

    private void syncRig() {
        if (heartCore == null || pos == null) {
            return;
        }
        Form form = isDying() ? Form.UNCHAINED : form();
        if (!isDying()) {
            Material core = switch (form) {
                case SOVEREIGN -> Material.MAGMA_BLOCK;
                case CHAINED -> Material.SHROOMLIGHT;
                case UNCHAINED -> Material.PEARLESCENT_FROGLIGHT;
            };
            Material shell = exposedTicks > 0 ? Material.LIME_STAINED_GLASS : switch (form) {
                case SOVEREIGN -> Material.PURPLE_STAINED_GLASS;
                case CHAINED -> Material.RED_STAINED_GLASS;
                case UNCHAINED -> Material.ORANGE_STAINED_GLASS;
            };
            if (core != heartCoreMat) {
                heartCoreMat = core;
                setBlock(heartCore, core);
            }
            if (shell != heartShellMat) {
                heartShellMat = shell;
                setBlock(heartShell, shell);
            }
        }
        Location heart = heartFloat != null ? heartFloat : heartPos();
        double beat = form == Form.SOVEREIGN ? 0.0 : Math.max(0.0, Math.sin(clock * 0.21)) * 0.06;
        double pulse = exposedTicks > 0 ? 0.08 * Math.sin(clock * 0.6) : 0.0;
        float spin = clock * 0.08f;
        float core = (float) Math.max(0.05, 0.5 + heartBoost * 0.4 + heartKick + beat + pulse);
        float shell = (float) Math.max(0.08, 0.82 + heartBoost * 0.6 + heartKick * 1.5 + beat * 1.5 + pulse);
        if (isDying() && deathTick >= 140) {
            core *= (float) Math.max(0.35, 1.0 - (deathTick - 140) / 60.0);
        }
        put(heartCore, heart, centered(new Quaternionf().rotateY(spin).rotateX(spin * 0.6f), core), 1);
        put(heartShell, heart, centered(new Quaternionf().rotateY(-spin * 0.7f).rotateZ(spin * 0.4f), shell), 1);
        Location head = partPos(0).add(0, 1.4, 0);
        Vector side = right(heading);
        for (int i = 0; i < crown.size(); i++) {
            BlockDisplay shard = crown.get(i);
            boolean shown = i < crownShown && !isDying();
            double fan = (i - 2) * 0.45;
            Vector3f dir = new Vector3f(
                    (float) (side.getX() * Math.sin(fan) - heading.getX() * 0.25),
                    (float) Math.cos(fan),
                    (float) (side.getZ() * Math.sin(fan) - heading.getZ() * 0.25)).normalize();
            float length = shown ? (float) (0.8 + (i == 2 ? 0.35 : 0.0) + Math.sin(clock * 0.15 + i) * 0.06) : 0.001f;
            put(shard, head, rod(dir, 0.1f, length, shown ? 0.16f : 0.001f), 1);
        }
    }

    private void clearRig() {
        discardRig(heartCore);
        discardRig(heartShell);
        heartCore = null;
        heartShell = null;
        for (BlockDisplay shard : crown) {
            discardRig(shard);
        }
        crown.clear();
        for (BlockDisplay d : rig) {
            discardRig(d);
        }
        rig.clear();
        heartCoreMat = null;
        heartShellMat = null;
    }

    private void ambient(Form form) {
        World world = center.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (form) {
            case SOVEREIGN -> {
                if (clock % 4 == 0) {
                    world.spawnParticle(Particle.WHITE_ASH, partPos(6 + (int) (clock / 4 % 2)), 3, 1.2, 0.3, 1.2, 0.01);
                }
                if (clock % 10 == 0) {
                    world.spawnParticle(Particle.REVERSE_PORTAL, partPos(2).add(0, 1, 0), 4, 1.0, 0.6, 1.0, 0.02);
                }
                if (!grounded && clock % 6 == 0) {
                    world.spawnParticle(Particle.ASH, pos.clone().add(0, -2, 0), 12, 2.5, 1.5, 2.5, 0.01);
                }
            }
            case CHAINED -> {
                if (clock % 3 == 0) {
                    world.spawnParticle(Particle.SMOKE, headPos(), 2, 0.2, 0.2, 0.2, 0.02);
                }
                if (clock % 8 == 0) {
                    world.spawnParticle(Particle.FLAME, heartPos(), 2, 0.2, 0.2, 0.2, 0.01);
                }
                if (clock % 30 == 0) {
                    world.playSound(heartPos(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 0.6f);
                }
            }
            case UNCHAINED -> {
                int part = 3 + random.nextInt(5);
                world.spawnParticle(Particle.FLAME, partPos(part), 2, 0.6, 0.3, 0.6, 0.01);
                if (clock % 5 == 0) {
                    world.spawnParticle(Particle.LAVA, partPos(2).add(0, 1, 0), 1, 0.8, 0.4, 0.8, 0);
                }
                if (clock % 3 == 0) {
                    world.spawnParticle(Particle.FLAME, partPos(0).add(0, 2.2, 0), 1, 0.15, 0.1, 0.15, 0.01);
                }
                if (clock % 26 == 0) {
                    world.playSound(heartPos(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.7f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ tells & fx

    private void drawLane(Location from, Vector dir, double length, double halfWidth, double fill, int tick, int remaining, Color color) {
        World world = from.getWorld();
        Vector unit = dir.clone().setY(0);
        if (world == null || unit.lengthSquared() < 0.01) {
            return;
        }
        unit.normalize();
        boolean hot = remaining <= 8;
        if (!hot && tick % 2 != 0) {
            return;
        }
        Vector side = new Vector(-unit.getZ(), 0, unit.getX());
        Location base = from.clone().add(0, 0.12, 0);
        Particle.DustOptions edge = new Particle.DustOptions(hot && remaining % 2 == 0 ? HOT : color, hot ? 1.25f : 1.0f);
        Particle.DustOptions spine = new Particle.DustOptions(GOLD, 0.8f);
        for (double d = 0.0; d <= length; d += 0.7) {
            Location on = base.clone().add(unit.clone().multiply(d));
            world.spawnParticle(Particle.DUST, on.clone().add(side.clone().multiply(halfWidth)), 1, 0, 0, 0, 0, edge);
            world.spawnParticle(Particle.DUST, on.clone().subtract(side.clone().multiply(halfWidth)), 1, 0, 0, 0, 0, edge);
        }
        Location cap = base.clone().add(unit.clone().multiply(length));
        for (double s = -halfWidth; s <= halfWidth + 1.0E-4; s += 0.4) {
            world.spawnParticle(Particle.DUST, cap.clone().add(side.clone().multiply(s)), 1, 0, 0, 0, 0, edge);
            world.spawnParticle(Particle.DUST, base.clone().add(side.clone().multiply(s)), 1, 0, 0, 0, 0, edge);
        }
        double reach = Math.max(0.0, Math.min(1.0, fill)) * length;
        for (double d = 0.0; d <= reach; d += 0.5) {
            world.spawnParticle(Particle.DUST, base.clone().add(unit.clone().multiply(d)), 1, 0, 0, 0, 0, spine);
        }
    }

    private void coneTell(Location feet, Vector f, double radius, double halfDeg, double fill, Color color) {
        World world = feet.getWorld();
        Location base = feet.clone().add(0, 0.12, 0);
        Particle.DustOptions edge = new Particle.DustOptions(color, 1.1f);
        double half = Math.toRadians(halfDeg);
        for (int s = -1; s <= 1; s += 2) {
            Vector ray = rotateY(f, half * s);
            for (double d = 1.5; d <= radius; d += 0.7) {
                world.spawnParticle(Particle.DUST, base.clone().add(ray.clone().multiply(d)), 1, 0, 0, 0, 0, edge);
            }
        }
        arcDust(world, base, f, radius, half, edge, 0.6);
        arcDust(world, base, f, Math.max(1.5, radius * fill), half, new Particle.DustOptions(GOLD, 0.8f), 0.7);
    }

    private void arcTell(Location feet, Vector f, double radius, double halfDeg, double fill, Color color) {
        World world = feet.getWorld();
        Location base = feet.clone().add(0, 0.12, 0);
        Particle.DustOptions edge = new Particle.DustOptions(color, 1.1f);
        double half = Math.toRadians(halfDeg);
        arcDust(world, base, f, radius, half, edge, 0.5);
        arcDust(world, base, f, Math.max(1.0, radius * fill), half, new Particle.DustOptions(GOLD, 0.8f), 0.7);
        for (int s = -1; s <= 1; s += 2) {
            Vector ray = rotateY(f, half * s);
            for (double d = 1.5; d <= radius; d += 0.7) {
                world.spawnParticle(Particle.DUST, base.clone().add(ray.clone().multiply(d)), 1, 0, 0, 0, 0, edge);
            }
        }
    }

    private static void arcDust(World world, Location base, Vector f, double radius, double half, Particle.DustOptions dust, double spacing) {
        double step = spacing / Math.max(0.5, radius);
        for (double a = -half; a <= half + 1.0E-4; a += step) {
            Vector ray = rotateY(f, a);
            world.spawnParticle(Particle.DUST, base.clone().add(ray.multiply(radius)), 1, 0, 0, 0, 0, dust);
        }
    }

    /** Arrow heads along the arc pointing the way the breath will travel. */
    private static void chevrons(World world, Location feet, Vector aim, int sign, double radius) {
        Particle.DustOptions dust = new Particle.DustOptions(HOT, 1.0f);
        Location base = feet.clone().add(0, 0.16, 0);
        for (int k = -1; k <= 1; k++) {
            double a = Math.toRadians(35.0 * k);
            Vector radial = rotateY(aim, a);
            Vector tangent = rotateY(radial, Math.PI / 2 * sign);
            Location p = base.clone().add(radial.clone().multiply(radius));
            Location tip = p.clone().add(tangent.clone().multiply(0.7));
            for (int w = -1; w <= 1; w += 2) {
                Location wing = p.clone().add(tangent.clone().multiply(-0.4)).add(radial.clone().multiply(0.5 * w));
                for (double s = 0; s <= 1.0; s += 0.25) {
                    world.spawnParticle(Particle.DUST, lerp(wing, tip, s), 1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    private void fuse(Location at, int remaining) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        if (remaining == 12 || remaining == 8 || remaining == 5 || remaining == 3 || remaining == 1) {
            at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.9f, remaining <= 3 ? 1.9f : 1.4f);
        }
    }

    private static void ring(World world, Location center, double radius, Color color, float size) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int points = Math.max(10, (int) Math.ceil(Math.PI * 2 * radius / 0.5));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, 0, 0, 0, 0, dust);
        }
    }

    private static void ringForce(World world, Location center, double radius, Color color, float size) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int points = Math.max(10, (int) Math.ceil(Math.PI * 2 * radius / 0.45));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, 0, 0, 0, 0, dust, true);
        }
    }

    /** White-hot core with a colored sheath: the look of anything falling at dragon speed. */
    private static void streak(World world, Location a, Location b, Color color, boolean force) {
        if (a == null || b == null || a.getWorld() != b.getWorld()) {
            return;
        }
        Particle.DustOptions core = new Particle.DustOptions(HOT, 0.8f);
        Particle.DustOptions sheath = new Particle.DustOptions(color, 1.4f);
        double len = a.distance(b);
        int index = 0;
        for (double d = 0.0; d <= len; d += 0.35, index++) {
            Location p = lerp(a, b, len < 0.01 ? 0 : d / len);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, core, force);
            if (index % 2 == 0) {
                world.spawnParticle(Particle.DUST, p, 1, 0.1, 0.1, 0.1, 0, sheath, force);
            }
        }
    }

    private static void burst(World world, Location at, Particle particle, int count, double speed) {
        for (int i = 0; i < count; i++) {
            Vector dir = randomUnit();
            world.spawnParticle(particle, at, 0, dir.getX(), dir.getY(), dir.getZ(), speed, null, true);
        }
    }

    private static void converge(World world, Location at, double radius, Particle particle, int count) {
        for (int i = 0; i < count; i++) {
            Vector dir = randomUnit();
            Location from = at.clone().add(dir.clone().multiply(radius));
            world.spawnParticle(particle, from, 0, -dir.getX(), -dir.getY(), -dir.getZ(), radius * 0.09);
        }
    }

    private static void floorBurst(World world, Location at, int count, double spread) {
        BlockData data = floorData(at);
        world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.3, 0), count, spread, 0.3, spread, 0.2, data);
    }

    private static BlockData floorData(Location at) {
        Block below = at.clone().subtract(0, 0.5, 0).getBlock();
        Material type = below.getType();
        return type.isSolid() ? below.getBlockData() : Material.BLACKSTONE.createBlockData();
    }

    private void startWhoosh(Location at, float volume, float pitch) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= 110 * 110) {
                player.playSound(at, Sound.ITEM_ELYTRA_FLYING, volume, pitch);
                whooshHeard.add(player.getUniqueId());
            }
        }
    }

    private void stopWhoosh() {
        for (UUID id : whooshHeard) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.stopSound(Sound.ITEM_ELYTRA_FLYING);
            }
        }
        whooshHeard.clear();
    }

    private void scorch(Player player, double fraction) {
        double max = AttributeUtil.getValue(player, AttributeUtil.maxHealth(), 20.0);
        double amount = Math.max(1.0, max * fraction);
        player.getPersistentDataContainer().set(AetherKeys.TRUE_DAMAGE, PersistentDataType.BYTE, (byte) 1);
        try {
            player.damage(amount);
        } finally {
            player.getPersistentDataContainer().remove(AetherKeys.TRUE_DAMAGE);
        }
    }

    // ------------------------------------------------------------------ displays

    private BlockDisplay spawnBlock(Location at, BlockData data, int teleportTicks, float viewRange) {
        BlockDisplay display = at.getWorld().spawn(flat(at), BlockDisplay.class, spawned -> {
            spawned.setBlock(data);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTeleportDuration(teleportTicks);
            spawned.setInterpolationDuration(2);
            spawned.setTransformation(centered(new Quaternionf(), 0.001f));
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setViewRange(viewRange);
            spawned.setShadowRadius(0.0f);
            spawned.setPersistent(false);
            spawned.setGravity(false);
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
        });
        fx.add(display);
        return display;
    }

    private BlockDisplay spawnRigBlock(Location at, BlockData data) {
        BlockDisplay display = at.getWorld().spawn(flat(at), BlockDisplay.class, spawned -> {
            spawned.setBlock(data);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTeleportDuration(2);
            spawned.setInterpolationDuration(2);
            spawned.setTransformation(centered(new Quaternionf(), 0.001f));
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setViewRange(3.0f);
            spawned.setShadowRadius(0.0f);
            spawned.setPersistent(false);
            spawned.setGravity(false);
            instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
        });
        rig.add(display);
        return display;
    }

    private static void setBlock(BlockDisplay display, Material material) {
        if (display != null && display.isValid()) {
            display.setBlock(material.createBlockData());
        }
    }

    private static void put(BlockDisplay display, Location at, Transformation transformation, int ticks) {
        if (display == null || !display.isValid() || at == null) {
            return;
        }
        display.teleport(flat(at));
        ease(display, transformation, ticks);
    }

    private static void ease(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private void discard(Display display) {
        if (display == null) {
            return;
        }
        if (display.isValid()) {
            display.remove();
        }
        fx.remove(display);
    }

    private static void discardRig(Display display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    private void clearCombatFx() {
        cancelMove();
        // Before the sweep below: clearEclipse() edits fx as it discards, so it cannot run mid-iteration.
        clearEclipse();
        for (Display display : fx) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        fx.clear();
        embers.clear();
        waves.clear();
        lanes.clear();
        ringBursts.clear();
        burns.clear();
        debris.clear();
        for (Chain chain : chains) {
            chain.links.clear();
        }
        chains.clear();
        wardRings.clear();
        wards.clear();
        fireWall.clear();
        sunCore = null;
        sunShell = null;
        sunHalo = null;
        for (int i = 0; i < sunRays.length; i++) {
            sunRays[i] = null;
        }
        sunPos = null;
        wallRadius = -1;
        hitGate.clear();
    }

    private static Transformation centered(Quaternionf rotation, float size) {
        Vector3f offset = rotation.transform(new Vector3f(-size / 2f, -size / 2f, -size / 2f));
        return new Transformation(offset, new Quaternionf(rotation), new Vector3f(size, size, size), new Quaternionf());
    }

    /** A block stretched along {@code dir}, starting {@code start} blocks from the anchor. */
    private static Transformation rod(Vector3f dir, float start, float length, float width) {
        Vector3f axis = new Vector3f(dir);
        if (axis.lengthSquared() < 1.0E-6f) {
            axis.set(0, 1, 0);
        }
        axis.normalize();
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis);
        Vector3f offset = rotation.transform(new Vector3f(-width / 2f, start, -width / 2f));
        return new Transformation(offset, rotation, new Vector3f(width, length, width), new Quaternionf());
    }

    /** A flat box lying tangent to a circle of radius {@code r} at angle {@code a} around the display. */
    private static Transformation segment(double a, double r, double y, float len, float height, float thick) {
        Quaternionf q = new Quaternionf().rotateY((float) -(a + Math.PI / 2));
        Vector3f offset = new Vector3f((float) (Math.cos(a) * r), (float) y, (float) (Math.sin(a) * r))
                .add(q.transform(new Vector3f(-len / 2f, 0f, -thick / 2f)));
        return new Transformation(offset, q, new Vector3f(len, height, thick), new Quaternionf());
    }

    // ------------------------------------------------------------------ helpers

    private boolean beat(int t, int at) {
        if (t < at || at < beatFloor) {
            return false;
        }
        return beats.add(cinematic.name() + at);
    }

    private static int scaled(int tick, int duration, int base) {
        return (int) Math.round(tick * (double) base / Math.max(1, duration));
    }

    private boolean gate(String kind, Player player, int ticks) {
        String key = kind + player.getUniqueId();
        Long next = hitGate.get(key);
        if (next != null && next > clock) {
            return false;
        }
        hitGate.put(key, clock + ticks);
        return true;
    }

    private List<Player> fighters() {
        List<Player> out = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            return out;
        }
        double r = arenaRadius + 18.0;
        double r2 = r * r;
        for (Player player : center.getWorld().getPlayers()) {
            if (!vulnerable(player)) {
                continue;
            }
            Location at = player.getLocation();
            if (Math.abs(at.getY() - floorY) > 45.0 || horizontalSq(at, center) > r2) {
                continue;
            }
            out.add(player);
        }
        return out;
    }

    private static Player pick(List<Player> players) {
        if (players.isEmpty()) {
            return null;
        }
        return players.get(ThreadLocalRandom.current().nextInt(players.size()));
    }

    private static Player nearest(Location from, List<Player> players) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player player : players) {
            double d = horizontalSq(player.getLocation(), from);
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

    private void shout(String message) {
        Location origin = center != null ? center : instance.getSpawnLocation();
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) < 110 * 110) {
                player.sendMessage(TextUtil.component(message));
            }
        }
    }

    private void titleNear(String main, String sub) {
        Location origin = center != null ? center : instance.getSpawnLocation();
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        Title title = Title.title(TextUtil.component(main), TextUtil.component(sub), titleTimes());
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) <= 90 * 90) {
                player.showTitle(title);
            }
        }
    }

    private void actionBar(String message) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        double r = arenaRadius + 20.0;
        for (Player player : center.getWorld().getPlayers()) {
            if (horizontalSq(player.getLocation(), center) <= r * r) {
                player.sendActionBar(TextUtil.component(message));
            }
        }
    }

    private static Title.Times titleTimes() {
        return Title.Times.times(Duration.ofMillis(120), Duration.ofMillis(1400), Duration.ofMillis(400));
    }

    private static String meter(int left, int total, String color) {
        int cells = 10;
        int lit = (int) Math.ceil(cells * Math.max(0, left) / (double) Math.max(1, total));
        StringBuilder out = new StringBuilder(color);
        for (int i = 0; i < cells; i++) {
            if (i == lit) {
                out.append("&8");
            }
            out.append('■');
        }
        return out.toString();
    }

    private static Method moveMethod;
    private static boolean moveResolved;

    private static void nmsMoveTo(Entity entity, Location dest) {
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            if (!moveResolved) {
                moveResolved = true;
                moveMethod = findMove(handle.getClass());
            }
            if (moveMethod != null) {
                moveMethod.invoke(handle, dest.getX(), dest.getY(), dest.getZ(), dest.getYaw(), dest.getPitch());
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method findMove(Class<?> type) {
        for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
            for (String name : new String[]{"absMoveTo", "absSnapTo", "moveTo", "snapTo"}) {
                try {
                    Method method = t.getDeclaredMethod(name, double.class, double.class, double.class, float.class, float.class);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    private static Vector right(Vector f) {
        return new Vector(-f.getZ(), 0, f.getX());
    }

    private static Vector away(Location from, Location origin) {
        Vector out = from.toVector().subtract(origin.toVector()).setY(0);
        if (out.lengthSquared() < 0.01) {
            out = new Vector(0.01, 0, 0);
        }
        return out.normalize();
    }

    private static Vector flatDir(Location from, Location to, Vector fallback) {
        Vector dir = to.toVector().subtract(from.toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) {
            Vector f = fallback.clone().setY(0);
            return f.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : f.normalize();
        }
        return dir.normalize();
    }

    private static Vector steer(Vector current, Vector wanted, double rate) {
        Vector out = current.clone().multiply(1.0 - rate).add(wanted.clone().multiply(rate));
        if (out.lengthSquared() < 1.0E-4) {
            return wanted.clone();
        }
        return out.normalize();
    }

    private static Vector rotateY(Vector v, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(v.getX() * cos - v.getZ() * sin, v.getY(), v.getX() * sin + v.getZ() * cos);
    }

    private static Vector dirOf(float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad));
    }

    private static Vector randomUnit() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double z = random.nextDouble(-1.0, 1.0);
        double a = random.nextDouble(Math.PI * 2);
        double r = Math.sqrt(1.0 - z * z);
        return new Vector(r * Math.cos(a), z, r * Math.sin(a));
    }

    /** Degrees between {@code facing} and the flat direction from {@code origin} to {@code point}. */
    private static double angleFrom(Vector facing, Location origin, Location point) {
        Vector to = point.toVector().subtract(origin.toVector()).setY(0);
        if (to.lengthSquared() < 0.01) {
            return 0.0;
        }
        double dot = to.normalize().dot(facing.clone().setY(0).normalize());
        return Math.toDegrees(Math.acos(clamp(dot, -1.0, 1.0)));
    }

    private static boolean inCone(Location origin, Vector dir, Location point, double radius, double halfDeg) {
        Vector to = point.toVector().subtract(origin.toVector()).setY(0);
        double len = to.length();
        if (len > radius) {
            return false;
        }
        if (len < 1.5) {
            return true;
        }
        return to.multiply(1.0 / len).dot(dir) >= Math.cos(Math.toRadians(halfDeg));
    }

    private static float wrapDeg(float deg) {
        float d = deg % 360.0f;
        if (d > 180.0f) {
            d -= 360.0f;
        } else if (d <= -180.0f) {
            d += 360.0f;
        }
        return d;
    }

    private static float yawOf(Vector dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
    }

    private static double segmentDistance(Location p, Location a, Location b) {
        Vector ab = b.toVector().subtract(a.toVector());
        Vector ap = p.toVector().subtract(a.toVector());
        double len2 = ab.lengthSquared();
        double t = len2 < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, ap.dot(ab) / len2));
        return a.toVector().add(ab.multiply(t)).distance(p.toVector());
    }

    /** Horizontal clamp; a negative margin lets flight overshoot the floor edge a little. */
    private void clampToArena(Location loc, double margin) {
        if (center == null) {
            return;
        }
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double max = Math.max(1.0, arenaRadius - margin);
        if (d > max && d > 0.01) {
            loc.setX(center.getX() + dx / d * max);
            loc.setZ(center.getZ() + dz / d * max);
        }
    }

    private double groundY(double x, double z) {
        Double floor = floorAt(center.getWorld(), x, z, floorY + 4.0);
        return floor == null ? floorY : floor;
    }

    private Location ground(Location loc) {
        Location at = loc.clone();
        at.setY(groundY(at.getX(), at.getZ()));
        at.setPitch(0);
        return at;
    }

    /** Standing surface near {@code nearY}, or null over lava, void, or when the column is walled. */
    private static Double floorAt(World world, double x, double z, double nearY) {
        if (world == null) {
            return null;
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int start = (int) Math.floor(nearY) + 2;
        int min = Math.max(world.getMinHeight() + 1, start - 10);
        for (int y = start; y >= min; y--) {
            Block feet = world.getBlockAt(bx, y, bz);
            if (feet.isLiquid()) {
                continue;
            }
            Block below = world.getBlockAt(bx, y - 1, bz);
            if (!below.getType().isSolid() || below.isLiquid()) {
                continue;
            }
            if (!feet.isPassable() || !world.getBlockAt(bx, y + 1, bz).isPassable()) {
                continue;
            }
            double top = below.getBoundingBox().getMaxY();
            return top > y - 1 && top <= y ? top : (double) y;
        }
        return null;
    }

    private static Location flat(Location at) {
        Location out = at.clone();
        out.setYaw(0.0f);
        out.setPitch(0.0f);
        return out;
    }

    private static Location lerp(Location a, Location b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return a.clone().add((b.getX() - a.getX()) * t, (b.getY() - a.getY()) * t, (b.getZ() - a.getZ()) * t);
    }

    private static double horizontal(Location a, Location b) {
        return Math.sqrt(horizontalSq(a, b));
    }

    private static double horizontalSq(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static double approach(double from, double to, double step) {
        if (Math.abs(to - from) <= step) {
            return to;
        }
        return from + Math.signum(to - from) * step;
    }

    private static double easeInOut(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double easeOut(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return 1.0 - (1.0 - t) * (1.0 - t);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clampF(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ hazard records

    private static final class Ember {
        private final Location ground;
        private final double radius;
        private final double power;
        private final int fall;
        private final double arc;
        private final boolean big;
        private final int warn;
        private int delay;
        private int flight = -1;
        private Location from;
        private Location prev;
        private BlockDisplay rock;
        private BlockDisplay glow;

        private Ember(Location ground, double radius, double power, int delay, int fall, double arc, boolean big) {
            this.ground = ground.clone();
            this.radius = radius;
            this.power = power;
            this.delay = delay;
            this.fall = Math.max(1, fall);
            this.arc = arc;
            this.big = big;
            this.warn = delay + this.fall;
        }
    }

    private static final class Shockwave {
        private final Location center;
        private final double speed;
        private final double maxRadius;
        private final Color color;
        private final double power;
        private final Set<UUID> struck = new HashSet<>();
        private double radius;
        private int delay;

        private Shockwave(Location center, double radius, double speed, double maxRadius, Color color, double power, int delay) {
            this.center = center.clone();
            this.radius = radius;
            this.speed = speed;
            this.maxRadius = maxRadius;
            this.color = color;
            this.power = power;
            this.delay = delay;
        }
    }

    private static final class Lane {
        private final Location from;
        private final Vector dir;
        private final double length;
        private final double half;
        private final double power;
        private final int warn;
        private final Color color;
        private final boolean burn;
        private final boolean slam;
        private int delay;

        private Lane(Location from, Vector dir, double length, double half, double power, int delay, Color color, boolean burn, boolean slam) {
            this.from = from.clone();
            this.dir = dir.clone().setY(0).normalize();
            this.length = length;
            this.half = half;
            this.power = power;
            this.delay = delay;
            this.warn = Math.max(1, delay);
            this.color = color;
            this.burn = burn;
            this.slam = slam;
        }
    }

    private static final class RingBurst {
        private final Location center;
        private final double radius;
        private final double[] gaps;
        private final double gapHalf;
        private final double power;
        private int delay;

        private RingBurst(Location center, double radius, double[] gaps, double gapHalf, int delay, double power) {
            this.center = center;
            this.radius = radius;
            this.gaps = gaps;
            this.gapHalf = gapHalf;
            this.delay = delay;
            this.power = power;
        }

        private boolean inGap(double angle) {
            for (double gap : gaps) {
                double d = (angle - gap) % (Math.PI * 2);
                if (d > Math.PI) {
                    d -= Math.PI * 2;
                } else if (d < -Math.PI) {
                    d += Math.PI * 2;
                }
                if (Math.abs(d) <= gapHalf) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Burn {
        private final Location at;
        private final double radius;
        private int life;
        private BlockDisplay decal;
        private float size;
        private float yaw;

        private Burn(Location at, double radius, int life) {
            this.at = at;
            this.radius = radius;
            this.life = life;
        }
    }

    private static final class Debris {
        private final BlockDisplay display;
        private final Location pos;
        private final Vector vel;
        private final float size;
        private int life;
        private float spin;

        private Debris(BlockDisplay display, Location pos, Vector vel, int life, float size) {
            this.display = display;
            this.pos = pos;
            this.vel = vel;
            this.life = life;
            this.size = size;
        }
    }

    private static final class Chain {
        private final Location anchor;
        private final List<BlockDisplay> links = new ArrayList<>();
        private BlockDisplay pylon;
        private BlockDisplay rune;
        private BlockDisplay cap;
        private double reach;
        private double shake;
        private double slam;
        private boolean broken;
        private boolean pylonless;

        private Chain(Location anchor) {
            this.anchor = anchor.clone();
        }
    }
}
