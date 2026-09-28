package de.aetherion.bossengine.instance.worldeater;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.BossPhase;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static de.aetherion.bossengine.instance.worldeater.WeMath.PI;
import static de.aetherion.bossengine.instance.worldeater.WeMath.TAU;
import static de.aetherion.bossengine.instance.worldeater.WeMath.inCubic;
import static de.aetherion.bossengine.instance.worldeater.WeMath.lerp;
import static de.aetherion.bossengine.instance.worldeater.WeMath.smooth;
import static de.aetherion.bossengine.instance.worldeater.WeMath.window;

/**
 * NIHIL, THE WORLD EATER.
 *
 * <p>It has eaten sixteen worlds. You can see them: its body is a spine of chunk-slabs, each a
 * different world, the way the game cuts them. The Last Seed is the seventeenth. It does not only
 * eat blocks. It eats the things every player takes for granted, one at a time: whole chunks,
 * then the stars, then the rules, and at the end it becomes the edge of the world and eats that.
 *
 * <pre>
 *   ARRIVAL    silence; the sky tears open behind the island and the sun is dragged down into
 *              night; it pours out of the tear and circles the island once.
 *   HUNGER     Lunge (it looks at one of you, then bites), Sweep (its body mows a lane),
 *              Devour (the F3+G chunk border appears around your chunk: the chunk is eaten)
 *   STARLESS   it rears into the sky and drinks the stars. From then on it is lit only by its
 *              eyes. + Inhale (crouch to brace), Void Breath (a trench across the island)
 *   UNMADE     "Can't keep up!" - it rewinds, you rubber-band, textures go missing.
 *              + Spit (a world it ate comes up its body and splashes across the island,
 *              with that world's rules), Unmake (a patch of floor with no rules left)
 *   OUROBOROS  it lies down around the island and becomes the edge of the world; the world
 *              border is where it is. It eats its own tail and the world shrinks. Hurt it and
 *              it brings the tail back up.
 *   DEATH      it finishes its tail, then its neck, then itself. Nothing is left: one black
 *              point. "Generating world". The island comes back chunk by chunk; the stars come
 *              back; the border turns green and flies away; dawn. "Saving the game". A Bonus
 *              Chest.
 * </pre>
 *
 * <p>Only at the site does it edit the world, and only inside the site boxes, which the site
 * rebuilds from the layout. Anywhere else the same fight plays out as displays and client paint.
 */
public final class WorldEaterDirector {

    public static final String ID = WorldEaterSite.EATER_ID;

    private enum Act { NONE, ARRIVING, FIGHT, TRANSITION, DYING, DONE }

    private enum Move { IDLE, LUNGE, SWEEP, DEVOUR, INHALE, BREATH, SPIT, UNMAKE, SNAP }

    /** Authored transition lengths, by the phase being entered. */
    private static final int[] AUTHORED = {0, 0, 200, 200, 220};
    /** Failsafe: the death hands over as soon as the world is saved, long before this. */
    private static final int DEATH_TICKS = 1100;
    private static final float ORBIT_R = 35f;
    private static final float ORBIT_Y = -8f;
    private static final int WORLDS = Serpent.WORLDS.length;
    /** Island chunks, regenerated in this order: the heart first, then around. */
    private static final int[][] REGEN = {{1, 1}, {1, 0}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {0, 1}, {0, 0}};

    private final BossInstance instance;

    private WeFx fx;
    private WeProps props;
    private FakeBlocks paint;
    private Serpent serpent;
    private SkyRift rift;
    private Senses ownSenses;
    private boolean site;
    private float edge = 24f;
    private int floorY;

    private Act act = Act.NONE;
    private Move move = Move.IDLE;
    private Move lastMove = Move.IDLE;
    private int actTick;
    private int step;
    private int stepTick;
    private int cooldown = 40;
    private int phase = 1;
    private int transitionPhase;
    private int transitionLength = 200;
    private int clock;
    private int freeze;
    private boolean stutter;
    private boolean deathFinished;
    private Location chestSpot;

    /* swimming */
    private SplinePath leg;
    private float legAt;
    private float legSpeed;
    private int orbitDir = 1;
    private boolean orbiting;

    /* arrival */
    private SplinePath arrival;
    private float arrivalAt;
    private int roarAt = -1;

    /* combat state */
    private int exposeTicks;
    private double lastHealth;
    private int lastHitReact;
    private UUID target;
    private final Map<String, Integer> gate = new HashMap<>();
    private final Set<String> taught = new HashSet<>();
    private final boolean[] eaten = new boolean[9];
    private int eatenCount;
    private final List<Spill> spills = new ArrayList<>();
    private final List<Lob> lobs = new ArrayList<>();
    private final List<Serpent.World> cameUp = new ArrayList<>();
    private final Map<UUID, Location[]> history = new HashMap<>();

    /* ouroboros */
    private boolean inRing;
    private int ringTick;
    private int tailEvery = 70;
    private double ringDamage;
    private int snapReady;

    /* death */
    private BlockDisplay point;
    private WeProps.ChunkMark regenMark;
    private int regenLayer = -1;
    private int generationAt = -1;
    private boolean worldSavedShown;

    /* move scratch */
    private final Vector3f mA = new Vector3f();
    private final Vector3f mB = new Vector3f();
    private final Vector3f mC = new Vector3f();
    private final Vector3f mD = new Vector3f();
    private float mF;
    private int mI;
    private int mJ;
    private WeProps.ChunkMark mark;
    private BlockDisplay beamCore;
    private BlockDisplay beamShell;
    private Serpent.World spitting;
    private int eatLayer = -1;

    public WorldEaterDirector(BossInstance instance) {
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

    /** Untouchable only while it arrives, changes phase, or dies. Head is always hittable in FIGHT. */
    public boolean blocksDamage() {
        if (!isMine()) {
            return false;
        }
        return act == Act.ARRIVING || act == Act.TRANSITION || act == Act.DYING;
    }

    /** Full damage always — no hide tax. Bows and blades hit the same. */
    public double scaleIncoming(double amount) {
        return amount;
    }

    public void onDamaged(double amount) {
        if (isMine() && phase >= 4 && act == Act.FIGHT) {
            ringDamage += amount;
        }
    }

    /** Where the Bonus Chest goes once it is gone (null before its death started). */
    public Location chestSpot() {
        return chestSpot == null ? null : chestSpot.clone();
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
            placeHitbox();
            return;
        }
        WorldEaterSite s = WorldEaterSite.get();
        Location spawn = instance.getSpawnLocation();
        site = s != null && s.atSite(spawn, true);
        Location anchor = site ? SiteLayout.islandAnchor(spawn.getWorld()) : spawn.clone();
        edge = site ? 24f : (float) Math.max(12.0, instance.getConditions().getLeashRadius() * 0.75);
        floorY = anchor.getBlockY() - 1;
        fx = new WeFx(anchor, instance.getKeys(), instance.getInstanceId())
                .audience(site ? 110.0 : edge + 70.0)
                .combat(site ? 46.0 : edge + 16.0, 34.0, 60.0);
        props = new WeProps();
        paint = new FakeBlocks(fx.world(), fx::audience);
        if (!site) {
            ownSenses = new Senses(fx.world(), fx::audience, fx::audience);
        }
        serpent = new Serpent(fx, WORLDS);
        orbitDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        lastHealth = instance.getCombatHealth();
        phase = phaseFromTemplate();
        if (site) {
            s.adoptEater(instance);
        }
        if (phase > 1) {
            // A new body for a fight already in progress (restart, reload): skip the arrival.
            Vector3f at = orbitPoint(0f);
            serpent.spawn(at, orbitTangent(0f), true);
            applyPhaseState(phase);
            act = Act.FIGHT;
            cooldown = 40;
        } else {
            act = Act.ARRIVING;
            actTick = 0;
        }
        placeHitbox();
    }

    public boolean beginDeath() {
        if (!isMine() || act == Act.DYING || act == Act.DONE || fx == null) {
            return false;
        }
        endMove();
        act = Act.DYING;
        actTick = 0;
        chestSpot = site ? new Location(fx.world(), SiteLayout.CENTER_X, SiteLayout.FLOOR + 1.0, SiteLayout.CENTER_Z) : fx.anchor();
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

    /** Removes everything this fight spawned or painted; the site puts the island back itself. */
    public void clear() {
        if (fx == null) {
            return;
        }
        endMove();
        clearSpills();
        props.clear();
        paint.clear();
        if (serpent != null) {
            serpent.remove();
        }
        if (rift != null) {
            rift.remove();
            rift = null;
        }
        fx.kill(point);
        point = null;
        Senses s = senses();
        if (s != null) {
            s.borderOff();
            if (site && !deathFinished) {
                // It never died: give the island its daylight back for the next attempt.
                s.weather(WeatherType.CLEAR);
                s.skyNow(1000f);
            }
        }
        if (ownSenses != null) {
            ownSenses.close();
            ownSenses = null;
        }
        history.clear();
        fx.clear();
        fx = null;
        serpent = null;
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
        recordHistory();
        boolean finished = false;
        switch (act) {
            case ARRIVING -> tickArrive();
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
        if (act == Act.DONE || finished) {
            deathFinished = true;
            act = Act.DONE;
            return true;
        }
        render();
        props.tick();
        paint.tick();
        tickSpills();
        if (ownSenses != null) {
            ownSenses.tick();
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
        return current == null ? 1 : phaseFor(current.getHealthPercent());
    }

    private static int phaseFor(double percent) {
        if (percent > 85.0) {
            return 1;
        }
        if (percent > 55.0) {
            return 2;
        }
        if (percent > 27.0) {
            return 3;
        }
        return 4;
    }

    /** The hitbox rides the skull. */
    private void placeHitbox() {
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid() || fx == null || serpent == null || !serpent.spawned()) {
            return;
        }
        Vector3f skull = serpent.skullCenter();
        float feet = skull.y - (float) entity.getHeight() * 0.5f;
        Location at = fx.at(skull.x, feet, skull.z);
        at.setYaw((float) Math.toDegrees(-WeMath.yawToward(serpent.fwd.x, serpent.fwd.z)));
        at.setPitch(0f);
        Location current = entity.getLocation();
        if (current.getWorld() != at.getWorld() || current.distanceSquared(at) > 0.0004) {
            instance.runInternalTeleport(() -> entity.teleport(at));
        }
        entity.setVelocity(new Vector());
    }

    private void repairIfUnloaded() {
        if (clock % 20 != 0 || serpent == null || !serpent.spawned()) {
            return;
        }
        if (!serpent.intact()) {
            serpent.repair();
        }
    }

    private void render() {
        if (serpent == null || !serpent.spawned()) {
            return;
        }
        List<Serpent.World> up = serpent.tickRising();
        if (!up.isEmpty()) {
            cameUp.addAll(up);
        }
        if (freeze > 0) {
            freeze--;
            return;
        }
        if (stutter) {
            // Server lag: the picture only updates in jumps.
            if (clock % 8 == 0) {
                serpent.render(0);
            }
            return;
        }
        serpent.render(2);
    }

    private Senses senses() {
        if (!site) {
            return ownSenses;
        }
        WorldEaterSite s = WorldEaterSite.get();
        return s == null ? null : s.senses();
    }

    private SiteTerrain terrain() {
        if (!site) {
            return null;
        }
        WorldEaterSite s = WorldEaterSite.get();
        return s == null ? null : s.terrain();
    }

    /* ================================================================== swimming */

    private void swimTo(Vector3f to, Vector3f endDir, float speed, float bend) {
        Vector3f from = new Vector3f(serpent.pos);
        if (from.distance(to) < 0.3f) {
            leg = null;
            return;
        }
        leg = SplinePath.hermite(from, new Vector3f(serpent.fwd), new Vector3f(to), new Vector3f(endDir), bend, 64);
        legAt = 0f;
        legSpeed = speed;
        orbiting = false;
    }

    private void swimPath(SplinePath path, float speed) {
        leg = path;
        legAt = 0f;
        legSpeed = speed;
        orbiting = false;
    }

    /** Advances the head along the current leg. @return true when there is no leg left */
    private boolean swim() {
        if (leg == null) {
            return true;
        }
        legAt = Math.min(leg.length(), legAt + legSpeed);
        serpent.moveTo(leg.at(legAt, new Vector3f()));
        if (legAt >= leg.length() - 1e-3f) {
            leg = null;
            return true;
        }
        return false;
    }

    private float orbitRadius() {
        return site ? ORBIT_R : edge + 11f;
    }

    private Vector3f orbitPoint(float angle) {
        float r = orbitRadius();
        return new Vector3f((float) Math.sin(angle) * r, ORBIT_Y, (float) Math.cos(angle) * r);
    }

    private Vector3f orbitTangent(float angle) {
        return new Vector3f((float) Math.cos(angle) * orbitDir, 0f, -(float) Math.sin(angle) * orbitDir);
    }

    /** Circles the island in the void, now and then breaking the surface to look at you. */
    private void orbit() {
        if (leg == null) {
            float a = (float) Math.atan2(serpent.pos.x, serpent.pos.z);
            if (!orbiting) {
                float join = a + orbitDir * 0.7f;
                swimTo(orbitPoint(join), orbitTangent(join), 0.95f, 0.6f);
                orbiting = true;
            } else {
                float r = orbitRadius();
                leg = SplinePath.circle(0f, ORBIT_Y, 0f, r, a, orbitDir, 6.5f, 2f, 240);
                legAt = 0f;
                legSpeed = 0.9f;
            }
        }
        swim();
        if (serpent.pos.y > -5f) {
            Player p = fx.nearestTarget(serpent.pos);
            if (p != null) {
                serpent.look(stage(p).add(0f, 1.2f, 0f), 0.55f, 0.12f);
            }
        } else {
            serpent.lookForward();
        }
        if (clock % 70 == 0) {
            fx.sound(serpent.pos, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 2.5f, 0.5f);
        }
    }

    /* ================================================================== geometry */

    private Vector3f stage(Player p) {
        return fx.stage(p.getLocation());
    }

    /** Horizontal direction from the island center (never zero). */
    private static Vector3f flat(Vector3f p) {
        Vector3f d = new Vector3f(p.x, 0f, p.z);
        if (d.lengthSquared() < 1e-4f) {
            d.set(0f, 0f, 1f);
        }
        return d.normalize();
    }

    /** A point {@code out} blocks beyond the island's edge in direction {@code dir}, at height {@code y}. */
    private Vector3f rim(Vector3f dir, float out, float y) {
        Vector3f d = flat(dir);
        float reach;
        if (site) {
            reach = edge / Math.max(Math.abs(d.x), Math.abs(d.z)) + out;
        } else {
            reach = edge + out;
        }
        return new Vector3f(d.x * reach, y, d.z * reach);
    }

    private boolean onIsland(Vector3f p, float pad) {
        if (site) {
            return Math.abs(p.x) < edge + pad && Math.abs(p.z) < edge + pad;
        }
        return p.x * p.x + p.z * p.z < (edge + pad) * (edge + pad);
    }

    /** Distance along {@code dir} from {@code from} until the island is left (plus {@code extra}). */
    private float exitDistance(Vector3f from, Vector3f dir, float extra) {
        Vector3f p = new Vector3f(from);
        float d = 0f;
        boolean entered = false;
        while (d < 90f) {
            p.set(from).fma(d, dir);
            boolean in = onIsland(p, 0f);
            if (in) {
                entered = true;
            } else if (entered) {
                break;
            }
            d += 0.5f;
        }
        // A line that never crosses the island (someone standing off it) stays short.
        return Math.min(60f, (entered ? d : 30f) + extra);
    }

    private int cornerNearest(Vector3f p) {
        if (p.x >= 0f) {
            return p.z < 0f ? 0 : 3;
        }
        return p.z < 0f ? 1 : 2;
    }

    /* ================================================================== arrival */

    private void tickArrive() {
        int t = actTick++;
        Senses senses = senses();
        if (t == 0) {
            fx.silence();
            Vector3f at = site ? fx.stage(new Location(fx.world(), 24.5, 140.5, 58.5)) : new Vector3f(0f, 30f, edge + 8f);
            rift = new SkyRift(fx, terrain(), at, 26, 3.2f);
            fx.score(Sound.AMBIENT_CAVE, 1f, 0.5f);
            fx.score(Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.5f);
            if (senses != null && !site) {
                senses.skyNow(1000f);
            }
        }
        Vector3f riftAt = rift.center();
        if (t >= 10 && t < 92) {
            Location look = fx.at(riftAt);
            for (Player p : fx.audience()) {
                Senses.pan(p, look, 0.045f);
            }
        }
        if (t < 64 && t % 7 == 0) {
            // The island trembles.
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 4; i++) {
                Vector3f at = new Vector3f((float) r.nextDouble(-edge, edge), 0.2f, (float) r.nextDouble(-edge, edge));
                fx.blockDust(at, Material.DIRT, 6, 0.8);
            }
            fx.sound(new Vector3f(0f, -3f, 0f), Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.5f);
        }
        if (t >= 30 && t <= 72) {
            rift.open(smooth(window(t, 30, 72)));
        }
        if (t == 30) {
            fx.scoreFrom(riftAt, Sound.BLOCK_END_PORTAL_SPAWN, 1.6f, 0.5f);
            fx.scoreFrom(riftAt, Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
        }
        if (t == 44 || t == 58) {
            fx.scoreFrom(riftAt, Sound.BLOCK_GLASS_BREAK, 2f, t == 44 ? 0.6f : 0.45f);
        }
        if (t >= 30 && t % 2 == 0) {
            fx.particle(Particle.REVERSE_PORTAL, rift.somePoint(ThreadLocalRandom.current()), 6, 0.4, 0.02);
        }
        if (t == 74 && senses != null) {
            // The sun is dragged down into the tear: night in two seconds.
            senses.sky(18000f, 360f);
            fx.score(Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 0.5f);
        }
        if (t == 96) {
            Vector3f start = new Vector3f(riftAt).add(0f, 0f, -1.5f);
            serpent.spawn(start, new Vector3f(0f, -0.25f, -1f), false);
            serpent.brow(true);
            arrival = arrivalPath(start);
            arrivalAt = 0f;
            fx.scoreFrom(start, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.5f);
            fx.scoreFrom(start, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 2f, 0.5f);
        }
        if (t > 96 && roarAt < 0) {
            arrivalAt += 1.7f;
            serpent.moveTo(arrival.at(arrivalAt, new Vector3f()));
            float left = arrival.length() - arrivalAt;
            serpent.jaw(left < 30f ? 0.2f : 0.35f + 0.15f * (float) Math.sin(t * 0.15f));
            Player p = fx.nearestTarget(serpent.pos);
            if (p != null) {
                serpent.look(stage(p).add(0f, 1.2f, 0f), 0.45f, 0.1f);
            }
            if (t % 20 == 0) {
                fx.scoreFrom(serpent.pos, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.8f, 0.5f);
            }
            if (t % 30 == 5) {
                fx.sound(serpent.pos, Sound.ITEM_ELYTRA_FLYING, 2.5f, 0.6f);
            }
            if (left <= 0.01f) {
                roarAt = t;
            }
            if (t > 96 + 400 && roarAt < 0) {
                roarAt = t;
            }
        }
        if (roarAt >= 0) {
            int r = t - roarAt;
            Player p = fx.nearestTarget(serpent.pos);
            if (p != null) {
                serpent.look(stage(p).add(0f, 1.5f, 0f), 0.9f, 0.15f);
            }
            serpent.jaw(r < 6 ? 0.3f : r < 44 ? 1.25f : 0.2f);
            if (r == 6) {
                fx.score(Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.45f);
                fx.score(Sound.ENTITY_WARDEN_ROAR, 1f, 0.5f);
                fx.score(Sound.ENTITY_RAVAGER_ROAR, 0.8f, 0.5f);
                fx.title("&5&lNIHIL", "&7the World Eater", 8, 60, 20);
                for (Player pl : fx.targets()) {
                    Vector v = pl.getVelocity();
                    pl.setVelocity(new Vector(v.getX(), Math.max(v.getY(), 0.3), v.getZ()));
                }
            }
            if (r >= 6 && r < 40 && r % 3 == 0) {
                fx.particle(Particle.SONIC_BOOM, serpent.mouth(), 1, 0.2, 0);
            }
            if (r == 70) {
                fx.actionBar("&7It has eaten &f" + WORLDS + " &7worlds. &8This one is the last.");
            }
            if (r >= 78) {
                serpent.brow(false);
                act = Act.FIGHT;
                actTick = 0;
                cooldown = 30;
                lastHealth = instance.getCombatHealth();
            }
        }
    }

    /** Out of the tear, once around the island and over your heads, ending face to face. */
    private SplinePath arrivalPath(Vector3f start) {
        float k = site ? 1f : edge / 24f;
        float[][] c = {
                {6, 30, 26}, {26, 19, 16}, {31, 9, -8}, {12, 6, -29}, {-14, 4, -29},
                {-31, 0, -8}, {-30, -6, 18}, {-10, -7, 34}, {6, -2, 34}, {2, 5, 24}, {0, 7, 12}};
        List<Vector3f> controls = new ArrayList<>();
        controls.add(new Vector3f(start));
        for (float[] p : c) {
            controls.add(new Vector3f(p[0] * k, p[1], p[2] * k));
        }
        return SplinePath.through(controls, 24);
    }

    /* ================================================================== the fight */

    private void tickFight() {
        actTick++;
        int want = phaseFromTemplate();
        if (want > phase) {
            // The template moved on without a staged transition (disabled in config): catch up.
            phase = want;
            applyPhaseState(phase);
        }
        hitReactions();
        tickExpose();
        if (phase >= 4) {
            tickOuroboros();
        } else {
            bodyContact();
        }
        if (move == Move.IDLE) {
            if (phase >= 4) {
                ringIdle();
            } else {
                orbit();
            }
            if (--cooldown <= 0) {
                chooseMove();
            }
            return;
        }
        switch (move) {
            case LUNGE -> lunge();
            case SWEEP -> sweep();
            case DEVOUR -> devour();
            case INHALE -> inhale();
            case BREATH -> breath();
            case SPIT -> spit();
            case UNMAKE -> unmake();
            case SNAP -> snap();
            default -> finish(20);
        }
        stepTick++;
    }

    private void chooseMove() {
        Player p = currentTarget();
        if (p == null) {
            cooldown = 20;
            return;
        }
        if (phase >= 4) {
            Vector3f tp = stage(p);
            if (snapReady <= 0 && tp.distance(serpent.pos) < 17f) {
                begin(Move.SNAP);
            } else {
                cooldown = 10;
            }
            return;
        }
        List<Move> bag = new ArrayList<>();
        List<Float> weight = new ArrayList<>();
        option(bag, weight, Move.LUNGE, phase == 1 ? 3f : 2.2f);
        option(bag, weight, Move.SWEEP, phase == 3 ? 0.8f : 1.8f);
        option(bag, weight, Move.DEVOUR, canDevour() ? (phase == 1 ? 2.2f : 1.4f) : 0f);
        if (phase >= 2) {
            option(bag, weight, Move.INHALE, phase == 2 ? 2f : 1.2f);
            option(bag, weight, Move.BREATH, 1.8f);
        }
        if (phase >= 3) {
            option(bag, weight, Move.SPIT, serpent.worldsLeft() > 4 ? 3f : 0f);
            option(bag, weight, Move.UNMAKE, 1.5f);
        }
        float total = 0f;
        for (int i = 0; i < bag.size(); i++) {
            if (bag.get(i) == lastMove) {
                weight.set(i, weight.get(i) * 0.25f);
            }
            total += weight.get(i);
        }
        float roll = (float) ThreadLocalRandom.current().nextDouble(Math.max(0.01f, total));
        for (int i = 0; i < bag.size(); i++) {
            roll -= weight.get(i);
            if (roll <= 0f) {
                begin(bag.get(i));
                return;
            }
        }
        begin(Move.LUNGE);
    }

    private static void option(List<Move> bag, List<Float> weight, Move m, float w) {
        if (w > 0f) {
            bag.add(m);
            weight.add(w);
        }
    }

    private void begin(Move m) {
        move = m;
        step = 0;
        stepTick = 0;
        mI = 0;
        mJ = 0;
        mF = 0f;
        Player p = currentTarget();
        target = p == null ? null : p.getUniqueId();
    }

    private void next() {
        step++;
        stepTick = -1;
    }

    private void finish(int cool) {
        endMove();
        lastMove = move;
        move = Move.IDLE;
        int base = phase == 1 ? cool + 14 : phase == 2 ? cool + 6 : cool;
        cooldown = Math.max(8, base);
        serpent.brow(false);
        serpent.lookForward();
        serpent.jaw(0f);
    }

    private void endMove() {
        if (serpent != null) {
            serpent.eyes(WeProps.VOID);
        }
        if (mark != null) {
            mark.expire(4);
            mark = null;
        }
        if (fx != null) {
            fx.kill(beamCore);
            fx.kill(beamShell);
        }
        beamCore = null;
        beamShell = null;
        finishEating();
        spitting = null;
    }

    /* ================================================================== move: LUNGE */

    /**
     * It rises at the edge and stares at one of you (the brow eye opens: it is looking at you),
     * a ring locks where you stand, and it bites the island there. Its jaws stick in the ground:
     * that is the opening.
     */
    private void lunge() {
        int aim = phase == 1 ? 30 : phase == 2 ? 24 : 18;
        switch (step) {
            case 0 -> {
                if (stepTick == 0) {
                    Player p = currentTarget();
                    Vector3f tp = p == null ? new Vector3f() : stage(p);
                    mA.set(rim(serpent.pos, 3.5f, 8f));
                    Vector3f in = new Vector3f(tp).sub(mA);
                    in.y = 0f;
                    in = in.lengthSquared() < 0.01f ? flat(mA).negate() : in.normalize();
                    swimTo(mA, new Vector3f(in.x, 0.5f, in.z), phase >= 3 ? 1.5f : 1.25f, 0.5f);
                    fx.sound(mA, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 3f, 0.55f);
                }
                if (swim()) {
                    next();
                }
            }
            case 1 -> {
                Player p = currentTarget();
                Vector3f tp = p == null ? new Vector3f(serpent.pos).fma(8f, serpent.fwd) : stage(p);
                if (stepTick == 0) {
                    serpent.brow(true);
                    fx.sound(serpent.browPoint(), Sound.ENTITY_WARDEN_SNIFF, 2.5f, 0.5f);
                    fx.sound(serpent.browPoint(), Sound.ENTITY_ENDERMAN_STARE, 1.4f, 0.5f);
                }
                float w = smooth(window(stepTick, 0, aim));
                serpent.faceTo(new Vector3f(tp).sub(serpent.pos), 0.09f);
                serpent.look(new Vector3f(tp).add(0f, 1f, 0f), 0.7f, 0.2f);
                serpent.jaw(0.25f + 0.85f * w);
                if (stepTick % 2 == 0 && p != null) {
                    gaze(serpent.browPoint(), new Vector3f(tp).add(0f, 1.4f, 0f));
                }
                if (stepTick == aim - 8) {
                    mB.set(tp.x, 0f, tp.z);
                    props.add(new WeProps.Circle(fx, mB, 3.6f, 22, Material.PURPLE_STAINED_GLASS, WeProps.VOID));
                    fx.sound(mB, Sound.BLOCK_BEACON_POWER_SELECT, 1.6f, 0.6f);
                }
                if (stepTick >= aim) {
                    next();
                }
            }
            case 2 -> {
                if (stepTick == 0) {
                    Vector3f dir = new Vector3f(mB).sub(serpent.pos);
                    dir.y = 0f;
                    dir = dir.lengthSquared() < 0.01f ? new Vector3f(serpent.fwd.x, 0f, serpent.fwd.z).normalize() : dir.normalize();
                    mC.set(mB).fma(4.2f, dir);
                    mC.y = 0.5f;
                    mD.set(dir);
                    swimTo(mC, new Vector3f(dir.x, -0.35f, dir.z), phase >= 3 ? 2.9f : 2.6f, 0.3f);
                    fx.sound(serpent.pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2.5f, 0.4f);
                    fx.sound(serpent.pos, Sound.ITEM_ELYTRA_FLYING, 2f, 1.2f);
                    serpent.lookForward();
                    if (taught.add("lunge")) {
                        fx.actionBar("&5When the eye opens, it has chosen. &7Leave the ring.");
                    }
                }
                boolean done = swim();
                if (mI == 0 && (WeMath.horizontal(serpent.mouth(), mB) < 2.4f || done)) {
                    mI = 1;
                    serpent.snap();
                    bite(mB, 3.6f, 140, "lunge");
                    freeze = 3;
                }
                if (done) {
                    next();
                }
            }
            case 3 -> {
                int stuck = phase == 1 ? 46 : phase == 2 ? 38 : 32;
                if (stepTick == 0) {
                    expose(stuck + 6);
                    serpent.jaw(0.1f);
                    serpent.brow(false);
                    if (taught.add("stuck")) {
                        fx.actionBar("&dIts jaws are stuck in the island. &fNow.");
                    }
                }
                if (stepTick % 9 == 0) {
                    serpent.skull.kick(0.08f, (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.3f, 0f);
                    fx.blockDust(serpent.mouth(), Material.DIRT, 14, 0.8);
                    fx.sound(serpent.mouth(), Sound.BLOCK_ROOTED_DIRT_BREAK, 1.6f, 0.5f);
                    fx.sound(serpent.skullCenter(), Sound.ENTITY_WARDEN_ANGRY, 1f, 0.5f);
                }
                if (stepTick >= stuck) {
                    next();
                }
            }
            default -> {
                if (stepTick == 0) {
                    Vector3f out = flat(serpent.pos);
                    swimTo(rim(out, 10f, -14f), new Vector3f(out.x, -0.6f, out.z), 1.1f, 0.6f);
                    fx.sound(serpent.mouth(), Sound.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.4f);
                    fx.blockDust(serpent.mouth(), Material.DIRT, 30, 1.2);
                }
                if (swim()) {
                    finish(30);
                }
            }
        }
    }

    /** A thin violet sightline from its brow eye to the one it chose. */
    private void gaze(Vector3f from, Vector3f to) {
        Vector3f d = new Vector3f(to).sub(from);
        float len = d.length();
        if (len < 0.5f) {
            return;
        }
        int n = Math.min(24, (int) (len / 1.2f));
        for (int i = 1; i <= n; i++) {
            fx.dust(new Vector3f(from).fma(i / (float) n, d), WeProps.VOID, 0.8f, 1, 0.02);
        }
    }

    /* ================================================================== move: SWEEP */

    /** It surfaces at the edge and crosses the island low: its whole body mows a lane. */
    private void sweep() {
        int tele = phase == 1 ? 28 : phase == 2 ? 22 : 18;
        switch (step) {
            case 0 -> {
                if (stepTick == 0) {
                    Player p = currentTarget();
                    Vector3f tp = p == null ? new Vector3f() : stage(p);
                    mA.set(rim(serpent.pos, 2f, 1.6f));
                    Vector3f v = new Vector3f(tp).sub(mA);
                    v.y = 0f;
                    v = v.lengthSquared() < 1f ? flat(mA).negate() : v.normalize();
                    mD.set(v);
                    mB.set(mA).fma(exitDistance(mA, v, 8f), v);
                    mB.y = 1.4f;
                    Vector3f start = new Vector3f(mA).fma(-8f, v);
                    start.y = -7f;
                    swimTo(start, new Vector3f(v.x, 0.6f, v.z), 1.35f, 0.6f);
                }
                if (swim()) {
                    next();
                }
            }
            case 1 -> {
                if (stepTick == 0) {
                    Vector3f a = new Vector3f(mA.x, 0f, mA.z);
                    Vector3f b = new Vector3f(mB.x, 0f, mB.z);
                    props.add(new WeProps.Lane(fx, a, b, 5.4f, 8, tele - 3, tele + 30, Material.PURPLE_STAINED_GLASS, WeProps.VOID));
                    swimTo(new Vector3f(mA.x, 1.6f, mA.z), new Vector3f(mD), 0.9f, 0.4f);
                    fx.sound(mA, Sound.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.5f);
                    fx.sound(mA, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 2.5f, 0.6f);
                    if (taught.add("sweep")) {
                        fx.actionBar("&5It is going to cross. &7Get out of the lane: you cannot jump it.");
                    }
                }
                swim();
                serpent.jaw(0.6f);
                if (stepTick >= tele) {
                    next();
                }
            }
            case 2 -> {
                if (stepTick == 0) {
                    swimTo(new Vector3f(mB), new Vector3f(mD), phase >= 3 ? 1.95f : 1.75f, 0.2f);
                    fx.sound(serpent.pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2.5f, 0.5f);
                    fx.sound(serpent.pos, Sound.ITEM_ELYTRA_FLYING, 2.5f, 0.8f);
                }
                boolean done = swim();
                Vector3f side = new Vector3f(-mD.z, 0f, mD.x);
                for (Player pl : fx.targets()) {
                    Vector3f pp = stage(pl);
                    if (pp.y > 4.5f || pp.y < -2f) {
                        continue;
                    }
                    boolean hit = pp.distance(serpent.skullCenter()) < 3.1f || touchesBody(pp, 0.7f);
                    if (hit) {
                        float s = Math.signum(new Vector3f(pp).sub(serpent.pos).dot(side));
                        Vector3f from = new Vector3f(pp).fma(-(s == 0f ? 1f : s), side);
                        strike(pl, 110, from, 1.2f, 0.7f, "sweep", 40);
                    }
                }
                if (stepTick % 2 == 0) {
                    mow(serpent.pos, 1.8f);
                }
                if (done) {
                    next();
                }
            }
            default -> {
                if (stepTick == 0) {
                    swimTo(rim(mD, 12f, -14f), new Vector3f(mD.x, -0.7f, mD.z), 1.3f, 0.5f);
                }
                if (swim()) {
                    finish(26);
                }
            }
        }
    }

    /** Flowers and grass along its path are torn out and swallowed. */
    private void mow(Vector3f at, float r) {
        SiteTerrain t = terrain();
        if (t == null) {
            return;
        }
        Location c = fx.at(at);
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                for (int y = SiteLayout.FLOOR + 1; y <= SiteLayout.FLOOR + 2; y++) {
                    Block b = fx.world().getBlockAt(c.getBlockX() + dx, y, c.getBlockZ() + dz);
                    Material m = b.getType();
                    if (!m.isAir() && !m.isSolid() && m != Material.LANTERN && m != Material.WATER) {
                        t.clear(b.getX(), y, b.getZ());
                    }
                }
            }
        }
    }

    /* ================================================================== move: DEVOUR */

    private boolean canDevour() {
        int max = phase == 1 ? 2 : phase == 2 ? 3 : 4;
        return eatenCount < max && pickChunk() >= 0;
    }

    /**
     * The outer chunk with the most people on it. Never the heart of the island, and never the
     * chunk the bridge lands on (there must always be a way in).
     */
    private int pickChunk() {
        int[] score = new int[9];
        for (Player p : fx.targets()) {
            int idx = chunkOf(stage(p));
            if (idx >= 0) {
                score[idx] += 3;
            }
        }
        int best = -1;
        float bestW = -1f;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 9; i++) {
            if (i == 4 || i == 3 || eaten[i]) {
                continue;
            }
            float w = score[i] + r.nextFloat();
            if (w > bestW) {
                bestW = w;
                best = i;
            }
        }
        return best;
    }

    /** Island chunk index (cx * 3 + cz) of a stage point, or -1. */
    private int chunkOf(Vector3f p) {
        int cx = (int) Math.floor((p.x + 24f) / 16f);
        int cz = (int) Math.floor((p.z + 24f) / 16f);
        if (cx < 0 || cx > 2 || cz < 0 || cz > 2) {
            return -1;
        }
        return cx * 3 + cz;
    }

    private Vector3f chunkCenter(int idx) {
        int cx = idx / 3;
        int cz = idx % 3;
        return new Vector3f(cx * 16f - 16f, 0f, cz * 16f - 16f);
    }

    /**
     * It is hungry for one chunk: the F3+G chunk border appears around it, the ground trembles,
     * the border flashes white, and the whole chunk is gone, top to bedrock, as it bursts up
     * through the hole with the ground in its mouth. It swallows (and you can see the chunk join
     * its body), then rests its head on the edge of the hole to digest. That is the opening.
     */
    private void devour() {
        int warn = phase == 1 ? 72 : phase == 2 ? 62 : 52;
        switch (step) {
            case 0 -> {
                if (stepTick == 0) {
                    mI = pickChunk();
                    if (mI < 0) {
                        finish(10);
                        return;
                    }
                    mA.set(chunkCenter(mI));
                    float x0 = mA.x - 8f;
                    float z0 = mA.z - 8f;
                    mark = props.add(new WeProps.ChunkMark(fx, x0, z0, 0f, warn + 90));
                    Vector3f below = new Vector3f(mA.x, -36f, mA.z);
                    List<Vector3f> controls = new ArrayList<>();
                    controls.add(new Vector3f(serpent.pos));
                    controls.add(rim(serpent.pos, 7f, -26f));
                    controls.add(rim(mA, 2f, -40f));
                    controls.add(new Vector3f(mA.x, -42f, mA.z));
                    controls.add(below);
                    swimPath(SplinePath.through(controls, 16), 1.6f);
                    fx.sound(mA, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
                    if (taught.add("devour")) {
                        fx.actionBar("&eThat is a chunk border. &7It is hungry for the whole chunk. &fGet off it.");
                    }
                }
                swim();
                if (stepTick % 6 == 0) {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    for (int i = 0; i < 3; i++) {
                        Vector3f at = new Vector3f(mA.x + (float) r.nextDouble(-7.5, 7.5), 0.2f, mA.z + (float) r.nextDouble(-7.5, 7.5));
                        fx.blockDust(at, Material.DIRT, 8, 0.6);
                    }
                    fx.sound(new Vector3f(mA.x, -2f, mA.z), Sound.BLOCK_ROOTED_DIRT_BREAK, 1.8f, 0.4f + stepTick * 0.004f);
                }
                if (stepTick % 20 == 0) {
                    fx.sound(new Vector3f(mA.x, -6f, mA.z), Sound.ENTITY_WARDEN_HEARTBEAT, 2.5f, 0.6f);
                }
                if (stepTick == warn - 12 && mark != null) {
                    mark.flash();
                    fx.sound(mA, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2f, 0.5f);
                }
                if (stepTick >= warn) {
                    next();
                }
            }
            case 1 -> {
                if (stepTick == 0) {
                    eatChunk(mI);
                    serpent.jaw(1.2f);
                    serpent.lookForward();
                    swimTo(new Vector3f(mA.x, 9f, mA.z), new Vector3f(0f, 1f, 0.2f), 3.0f, 0.2f);
                    fx.score(Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.5f);
                    fx.sound(mA, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.6f);
                    fx.sound(mA, Sound.BLOCK_ROOTED_DIRT_BREAK, 3f, 0.5f);
                    fx.sound(mA, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
                    if (mark != null) {
                        mark.expire(20);
                        mark = null;
                    }
                }
                eatStep();
                boolean done = swim();
                if (done && eatLayer < 0) {
                    next();
                }
            }
            case 2 -> {
                if (stepTick == 0) {
                    serpent.snap();
                    fx.sound(serpent.mouth(), Sound.ENTITY_GENERIC_EAT, 3f, 0.5f);
                    fx.sound(serpent.mouth(), Sound.ENTITY_GENERIC_EAT, 3f, 0.6f);
                }
                if (stepTick == 8) {
                    serpent.grow(chunkWorld(mI), 2.7f);
                    fx.sound(serpent.throat(), Sound.ENTITY_GENERIC_DRINK, 2.5f, 0.5f);
                }
                if (stepTick == 10) {
                    // Rest the head on the rim of the hole, toward the heart of the island.
                    Vector3f in = flat(mA).negate();
                    mC.set(mA).fma(site ? 6.5f : 4f, in);
                    mC.y = 1.2f;
                    swimTo(mC, new Vector3f(in.x, -0.15f, in.z), 0.7f, 0.4f);
                }
                if (stepTick > 10) {
                    swim();
                }
                if (stepTick == 30) {
                    fx.sound(serpent.mouth(), Sound.ENTITY_PLAYER_BURP, 3f, 0.5f);
                    fx.blockDust(serpent.mouth(), Material.GRASS_BLOCK, 30, 0.8);
                    expose(46);
                    if (taught.add("swallow")) {
                        fx.actionBar("&7It is swallowing. &fHit it while it digests.");
                    }
                }
                if (stepTick >= 74) {
                    next();
                }
            }
            default -> {
                if (stepTick == 0) {
                    swimTo(new Vector3f(mA.x, -40f, mA.z), new Vector3f(0f, -1f, 0.1f), 1.5f, 0.3f);
                }
                if (swim()) {
                    finish(28);
                }
            }
        }
    }

    /** What an eaten chunk of the Last Seed looks like on its body. */
    private Serpent.World chunkWorld(int idx) {
        Color green = Color.fromRGB(110, 200, 80);
        return switch (idx) {
            case 7 -> new Serpent.World("the last seed", Material.SAND, Material.DIRT, Material.SUGAR_CANE, 0.4f, 0.8f, false, Color.fromRGB(80, 160, 230));
            case 0 -> new Serpent.World("the last seed", Material.GRASS_BLOCK, Material.DIRT, Material.OAK_LEAVES, 0.6f, 0.55f, false, green);
            case 1, 8 -> new Serpent.World("the last seed", Material.GRASS_BLOCK, Material.DIRT, Material.BIRCH_LEAVES, 0.55f, 0.5f, false, green);
            default -> new Serpent.World("the last seed", Material.GRASS_BLOCK, Material.DIRT, Material.POPPY, 0.3f, 0.4f, false, green);
        };
    }

    private void eatChunk(int idx) {
        eaten[idx] = true;
        eatenCount++;
        Vector3f c = chunkCenter(idx);
        for (Player p : fx.targets()) {
            Vector3f pp = stage(p);
            if (chunkOf(pp) == idx && pp.y > -3f && pp.y < 5f) {
                strike(p, 120, new Vector3f(pp.x, -4f, pp.z), 0f, 0.35f, "devour", 60);
            }
        }
        // The ground flies into its mouth.
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector3f mouthAt = new Vector3f(c.x, 8f, c.z);
        SiteLayout layout = SiteLayout.get();
        Location w0 = fx.at(new Vector3f(c.x - 8f, 0f, c.z - 8f));
        for (int i = 0; i < 26; i++) {
            float x = c.x + (float) r.nextDouble(-7.5, 7.5);
            float z = c.z + (float) r.nextDouble(-7.5, 7.5);
            Vector3f from = new Vector3f(x, -0.5f, z);
            Material m = Material.GRASS_BLOCK;
            if (site) {
                BlockData d = layout.expected(w0.getBlockX() + (int) (x - c.x + 8f), SiteLayout.FLOOR, w0.getBlockZ() + (int) (z - c.z + 8f));
                m = d == null ? Material.DIRT : d.getMaterial();
            }
            if (!m.isBlock() || m == Material.WATER) {
                m = Material.DIRT;
            }
            Vector3f peak = new Vector3f(x, 3f + r.nextFloat() * 4f, z).lerp(mouthAt, 0.4f);
            props.add(new WeProps.Debris(fx, m, from, peak, new Vector3f(mouthAt).add(0f, r.nextFloat(), 0f), 8, 10, 0, 0.9f));
        }
        if (site) {
            eatLayer = SiteLayout.ISLAND.y1();
            eatStep();
        } else {
            // Nothing real is eaten away from the site: the chunk is shown as void for a while.
            BlockData black = Material.BLACK_CONCRETE.createBlockData();
            Location corner = fx.at(new Vector3f(c.x - 8f, 0f, c.z - 8f));
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    paint.show(corner.getBlockX() + dx, floorY, corner.getBlockZ() + dz, black, 400);
                }
            }
        }
    }

    /** Eats the marked chunk top-down, a dozen layers a tick. */
    private void eatStep() {
        if (eatLayer < 0) {
            return;
        }
        SiteTerrain t = terrain();
        if (t == null) {
            eatLayer = -1;
            return;
        }
        int cx = mI / 3;
        int cz = mI % 3;
        for (int k = 0; k < 12 && eatLayer >= SiteLayout.ISLAND.y0(); k++) {
            t.eatSlice(cx, cz, eatLayer--);
        }
        if (eatLayer < SiteLayout.ISLAND.y0()) {
            eatLayer = -1;
        }
    }

    private void finishEating() {
        while (eatLayer >= 0) {
            eatStep();
        }
    }

    /* ================================================================== move: INHALE */

    /**
     * It lays its jaw on the edge of the island and breathes in. Everyone slides toward the
     * mouth; flowers and grass fly into it. Crouch to brace. It is wide open while it inhales,
     * but whoever reaches the mouth is bitten.
     */
    private void inhale() {
        int dur = phase == 2 ? 100 : 86;
        switch (step) {
            case 0 -> {
                if (stepTick == 0) {
                    mD.set(flat(serpent.pos));
                    mA.set(rim(mD, 2.2f, 1.1f));
                    swimTo(mA, new Vector3f(-mD.x, 0.05f, -mD.z), 1.3f, 0.5f);
                }
                if (swim()) {
                    next();
                }
            }
            case 1 -> {
                Vector3f mouth = serpent.mouth();
                if (stepTick == 0) {
                    serpent.jaw(1.2f);
                    expose(dur);
                    fx.sound(mouth, Sound.ITEM_ELYTRA_FLYING, 3f, 0.6f);
                    if (taught.add("inhale")) {
                        fx.actionBar("&bIt is breathing in. &fCrouch to brace.");
                    }
                }
                if (stepTick % 12 == 0) {
                    fx.sound(mouth, Sound.ENTITY_BREEZE_INHALE, 3f, 0.5f);
                    fx.sound(mouth, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 2f, 0.5f);
                }
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 3; i++) {
                    float a = (float) r.nextDouble(TAU);
                    float d = (float) r.nextDouble(4.0, 16.0);
                    Vector3f from = new Vector3f(mouth.x + (float) Math.sin(a) * d, 0.3f + r.nextFloat() * 2.5f, mouth.z + (float) Math.cos(a) * d);
                    fx.stream(Particle.CLOUD, from, mouth, 0.35);
                }
                for (Player pl : fx.targets()) {
                    Vector3f pp = stage(pl);
                    Vector3f in = new Vector3f(mouth).sub(pp);
                    in.y = 0f;
                    float dist = in.length();
                    if (dist < 3.3f && pp.y < mouth.y + 2.5f && pp.y > mouth.y - 3f) {
                        serpent.snap();
                        strike(pl, 150, mouth, 1.7f, 0.7f, "inhale", 40);
                        fx.sound(mouth, Sound.ENTITY_EVOKER_FANGS_ATTACK, 3f, 0.5f);
                        next();
                        return;
                    }
                    if (dist > 40f || dist < 0.01f) {
                        continue;
                    }
                    float strength = (pl.isSneaking() ? 0.02f : 0.075f) * WeMath.clamp(1.2f - dist / 40f, 0.25f, 1f);
                    in.normalize(strength);
                    Vector v = pl.getVelocity();
                    double vx = v.getX() + in.x;
                    double vz = v.getZ() + in.z;
                    double toward = (vx * in.x + vz * in.z) / Math.max(1e-6, strength);
                    if (toward > 0.55) {
                        double k = 0.55 / toward;
                        vx *= k;
                        vz *= k;
                    }
                    pl.setVelocity(new Vector(vx, v.getY(), vz));
                }
                if (stepTick % 3 == 0) {
                    ripToward(mouth);
                }
                if (stepTick >= dur) {
                    next();
                }
            }
            default -> {
                if (stepTick == 0) {
                    serpent.snap();
                    fx.sound(serpent.mouth(), Sound.ENTITY_GENERIC_EAT, 3f, 0.5f);
                    swimTo(rim(mD, 12f, -14f), new Vector3f(mD.x, -0.7f, mD.z), 1.1f, 0.6f);
                }
                if (swim()) {
                    finish(26);
                }
            }
        }
    }

    /** Tears a plant (or a clod near the jaw) out of the island and pulls it into the mouth. */
    private void ripToward(Vector3f mouth) {
        SiteTerrain t = terrain();
        if (t == null) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location m = fx.at(mouth);
        boolean clod = r.nextFloat() < 0.25f;
        float reach = clod ? 5f : 13f;
        int x = m.getBlockX() + (int) Math.round(r.nextDouble(-reach, reach));
        int z = m.getBlockZ() + (int) Math.round(r.nextDouble(-reach, reach));
        int y = clod ? SiteLayout.FLOOR : SiteLayout.FLOOR + 1;
        Block b = fx.world().getBlockAt(x, y, z);
        Material was = b.getType();
        if (was.isAir() || was == Material.WATER || was == Material.LANTERN || was == Material.OAK_FENCE
                || !clod && was.isSolid() || clod && !was.isSolid()) {
            return;
        }
        if (clod && fx.world().getBlockAt(x, y + 1, z).getType().isSolid()) {
            return;
        }
        if (t.clear(x, y, z) == null) {
            return;
        }
        if (clod) {
            // Whatever stood on the clod goes too.
            t.clear(x, y + 1, z);
        }
        Vector3f from = fx.stage(new Location(fx.world(), x + 0.5, y + 0.5, z + 0.5));
        if (was.isItem()) {
            props.add(new WeProps.FlyingItem(fx, new ItemStack(was), from, new Vector3f(from).add(0f, 0.8f, 0f),
                    serpent::mouth, 3, 10, clod ? 0.8f : 0.55f));
        }
    }

    /* ================================================================== move: VOID BREATH */

    /**
     * It rears over the edge and charges (a lane of void marks the line), then breathes the void
     * across the island: the beam carves a trench, and the trench stays.
     */
    private void breath() {
        int charge = phase == 2 ? 30 : 24;
        switch (step) {
            case 0 -> {
                if (stepTick == 0) {
                    Player p = currentTarget();
                    Vector3f tp = p == null ? new Vector3f() : stage(p);
                    mA.set(rim(serpent.pos, 3f, 9f));
                    Vector3f v = new Vector3f(tp).sub(mA);
                    v.y = 0f;
                    v = v.lengthSquared() < 1f ? flat(mA).negate() : v.normalize();
                    mD.set(v);
                    swimTo(mA, new Vector3f(v.x, -0.35f, v.z), 1.3f, 0.5f);
                }
                if (swim()) {
                    next();
                }
            }
            case 1 -> {
                if (stepTick == 0) {
                    Vector3f a = rim(mA, -1f, 0f);
                    mB.set(a);
                    mC.set(a).fma(exitDistance(a, mD, 2f), mD);
                    mC.y = 0f;
                    props.add(new WeProps.Lane(fx, mB, mC, 3.6f, 8, charge - 2, charge + 28, Material.BLACK_CONCRETE, WeProps.VOID));
                    fx.sound(serpent.mouth(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 3f, 0.6f);
                    if (taught.add("breath")) {
                        fx.actionBar("&5It breathes the void. &7The line it marks will be gone.");
                    }
                }
                serpent.faceTo(new Vector3f(mD.x, -0.35f, mD.z), 0.08f);
                serpent.jaw(smooth(window(stepTick, 0, charge)) * 1.05f);
                fx.particle(Particle.REVERSE_PORTAL, serpent.mouth(), 10, 0.8, 0.05);
                if (stepTick >= charge) {
                    next();
                }
            }
            case 2 -> {
                int dur = 22;
                if (stepTick == 0) {
                    beamCore = fx.block(Material.BLACK_CONCRETE, null, 15);
                    beamShell = fx.block(Material.PURPLE_STAINED_GLASS, WeProps.VOID, 15);
                    fx.sound(serpent.mouth(), Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.5f);
                    fx.score(Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 0.5f);
                    mF = 0f;
                }
                float u = smooth(window(stepTick, 0, 16));
                Vector3f end = WeMath.lerp(mB, mC, u, new Vector3f());
                Vector3f mouth = serpent.mouth();
                WeFx.push(beamCore, WeFx.beam(mouth, end, 1.1f), 1);
                WeFx.push(beamShell, WeFx.beam(mouth, end, 2.0f), 1);
                float dist = mB.distance(end);
                if (dist > mF + 0.5f || stepTick == 0) {
                    Vector3f from = new Vector3f(mB).fma(mF, mD);
                    carve(from, end);
                    mF = dist;
                }
                for (Player pl : fx.targets()) {
                    Vector3f pp = stage(pl).add(0f, 0.9f, 0f);
                    if (WeMath.segmentDistance(pp, mouth, end) < 1.9f) {
                        strike(pl, 150, end, 1.1f, 0.5f, "breath", 40);
                    }
                }
                if (stepTick % 4 == 0) {
                    fx.sound(end, Sound.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.5f);
                    fx.particle(Particle.SQUID_INK, end, 10, 0.8, 0.05);
                }
                if (stepTick >= dur) {
                    next();
                }
            }
            default -> {
                if (stepTick == 0) {
                    Vector3f mouth = serpent.mouth();
                    Vector3f end = new Vector3f(mC);
                    WeFx.push(beamCore, WeFx.beam(mouth, end, 0.01f), 6);
                    WeFx.push(beamShell, WeFx.beam(mouth, end, 0.02f), 6);
                    serpent.jaw(0.1f);
                }
                if (stepTick == 7) {
                    fx.kill(beamCore);
                    fx.kill(beamShell);
                    beamCore = null;
                    beamShell = null;
                    swimTo(rim(mA, 12f, -14f), new Vector3f(-mD.x, -0.7f, -mD.z), 1.1f, 0.6f);
                }
                if (stepTick > 7 && swim()) {
                    finish(26);
                }
            }
        }
    }

    /** The void takes a trench out of the island from {@code a} to {@code b}. */
    private void carve(Vector3f a, Vector3f b) {
        SiteTerrain t = terrain();
        if (t == null) {
            BlockData black = Material.BLACK_CONCRETE.createBlockData();
            Vector3f d = new Vector3f(b).sub(a);
            float len = d.length();
            for (float s = 0f; s <= len; s += 0.8f) {
                Location at = fx.at(new Vector3f(a).fma(len < 1e-3f ? 0f : s / len, d));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        paint.show(at.getBlockX() + dx, floorY, at.getBlockZ() + dz, black, 500);
                    }
                }
            }
            return;
        }
        Location la = fx.at(a);
        Location lb = fx.at(b);
        List<int[]> surface = t.eatLane(la.getX(), la.getZ(), lb.getX(), lb.getZ(), 3.2, 4);
        int n = 0;
        for (int[] c : surface) {
            if (n++ > 5) {
                break;
            }
            Material m = SiteTerrain.material(c);
            if (!m.isBlock()) {
                continue;
            }
            Vector3f from = fx.stage(new Location(fx.world(), c[0] + 0.5, c[1] + 0.5, c[2] + 0.5));
            props.add(WeProps.Debris.toss(fx, m, from, new Vector3f(0f, 1f, 0f), -30f, 0.6f));
        }
    }

    /* ================================================================== move: SPIT */

    /**
     * One of the worlds in its body comes up its throat (you can watch the bulge travel to the
     * mouth). It spits the world onto the island, and for a while that ground has that world's
     * rules. The world is gone from its body for good.
     */
    private void spit() {
        switch (step) {
            case 0 -> {
                if (stepTick == 0) {
                    int first = serpent.firstWorld();
                    if (first < 0 || serpent.worldsLeft() <= 3) {
                        finish(8);
                        return;
                    }
                    int idx = first + ThreadLocalRandom.current().nextInt(Math.max(1, serpent.vertebrae() - first - 2));
                    cameUp.clear();
                    spitting = serpent.bringUp(idx);
                    if (spitting == null) {
                        finish(8);
                        return;
                    }
                    mD.set(flat(serpent.pos));
                    mA.set(rim(mD, 2.5f, 5f));
                    swimTo(mA, new Vector3f(-mD.x, 0.2f, -mD.z), 1.3f, 0.5f);
                    fx.actionBar("&7It is bringing up &f" + spitting.name() + "&7...");
                }
                swim();
                if (stepTick % 7 == 0) {
                    fx.sound(serpent.throat(), Sound.ENTITY_GENERIC_DRINK, 2.5f, 0.5f);
                }
                if (leg == null) {
                    Player p = currentTarget();
                    if (p != null) {
                        serpent.faceTo(stage(p).sub(serpent.pos), 0.06f);
                    }
                }
                if (!cameUp.isEmpty() && leg == null || stepTick > 140) {
                    cameUp.clear();
                    next();
                }
            }
            case 1 -> {
                Player p = currentTarget();
                Vector3f tp = p == null ? new Vector3f() : stage(p);
                if (stepTick == 0) {
                    mB.set(tp.x, 0f, tp.z);
                    props.add(new WeProps.Circle(fx, mB, 5.5f, 30, spitting == null ? Material.PURPLE_STAINED_GLASS : spitting.top(),
                            spitting == null ? WeProps.VOID : spitting.tint()));
                    serpent.jaw(1.1f);
                    fx.sound(serpent.mouth(), Sound.ENTITY_WARDEN_HEARTBEAT, 2.5f, 0.5f);
                }
                serpent.faceTo(new Vector3f(mB).sub(serpent.pos), 0.08f);
                if (stepTick >= 10) {
                    Material m = spitting == null ? Material.DIRT : spitting.top();
                    Serpent.World w = spitting;
                    Vector3f at = new Vector3f(mB);
                    lobs.add(new Lob(m, serpent.mouth(), at, 16, 7f, 2.6f, () -> land(w, at)));
                    fx.sound(serpent.mouth(), Sound.ENTITY_LLAMA_SPIT, 3f, 0.5f);
                    fx.sound(serpent.mouth(), Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.5f);
                    next();
                }
            }
            case 2 -> {
                if (stepTick == 0) {
                    serpent.jaw(0.3f);
                    Vector3f in = flat(mA).negate();
                    swimTo(rim(mD, 1.4f, 0.9f), new Vector3f(in.x, -0.1f, in.z), 0.5f, 0.3f);
                }
                swim();
                if (stepTick == 18) {
                    expose(40);
                    if (taught.add("cough")) {
                        fx.actionBar("&7It is coughing up what is left. &fHit it.");
                    }
                }
                if (stepTick > 18 && stepTick % 8 == 0) {
                    serpent.skull.kick(0.1f, 0f, 0f);
                    fx.particle(Particle.LARGE_SMOKE, serpent.mouth(), 6, 0.4, 0.02);
                    fx.sound(serpent.mouth(), Sound.ENTITY_WARDEN_HURT, 1.2f, 0.5f);
                }
                if (stepTick >= 58) {
                    next();
                }
            }
            default -> {
                if (stepTick == 0) {
                    swimTo(rim(mD, 12f, -14f), new Vector3f(mD.x, -0.7f, mD.z), 1.1f, 0.6f);
                }
                if (swim()) {
                    finish(24);
                }
            }
        }
    }

    /** A spat world lands: a blow where it hits, then its ground and its rules. */
    private void land(Serpent.World w, Vector3f at) {
        if (fx == null) {
            return;
        }
        for (Player pl : fx.targets()) {
            Vector3f pp = stage(pl);
            if (WeMath.horizontal(pp, at) < 5.5f && pp.y < 4f && pp.y > -2f) {
                strike(pl, 90, at, 0.9f, 0.6f, "spit", 30);
            }
        }
        Material top = w == null ? Material.DIRT : w.top();
        fx.sound(at, top.createBlockData().getSoundGroup().getBreakSound(), 3f, 0.5f);
        fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        fx.blockDust(new Vector3f(at).add(0f, 0.4f, 0f), top, 60, 2.2);
        props.add(new WeProps.Shockwave(fx, at, 0.8f, 7f, 0.8f, 12, top, null));
        if (w != null) {
            addSpill(new Spill(fx, paint, w, Spill.kindOf(w), at, 6.5f, 260, floorY, "spill" + clock));
            fx.title("", "&7It spat out &f" + w.name() + "&7.", 4, 40, 10);
        }
    }

    /* ================================================================== move: UNMAKE */

    /** Its brow eye stares at a patch of the island until that patch has no rules left. */
    private void unmake() {
        switch (step) {
            case 0 -> {
                if (stepTick == 0) {
                    mD.set(flat(serpent.pos));
                    mA.set(rim(mD, 3f, 6f));
                    swimTo(mA, new Vector3f(-mD.x, -0.2f, -mD.z), 1.4f, 0.5f);
                }
                if (swim()) {
                    next();
                }
            }
            case 1 -> {
                int stare = 34;
                if (stepTick == 0) {
                    Player p = currentTarget();
                    Vector3f tp = p == null ? new Vector3f() : stage(p);
                    mB.set(tp.x, 0f, tp.z);
                    serpent.brow(true);
                    serpent.eyes(Color.fromRGB(248, 0, 248));
                    fx.sound(mB, Sound.ENTITY_ENDERMAN_STARE, 2f, 0.5f);
                }
                serpent.faceTo(new Vector3f(mB).sub(serpent.pos), 0.08f);
                serpent.look(mB, 0.8f, 0.2f);
                if (stepTick % 4 == 0) {
                    flickerPatch(mB, 6f, 2);
                }
                if (stepTick % 2 == 0) {
                    fx.sound(mB, Sound.UI_BUTTON_CLICK, 1.2f, 0.5f + ThreadLocalRandom.current().nextFloat());
                    gaze(serpent.browPoint(), new Vector3f(mB).add(0f, 0.3f, 0f));
                }
                if (stepTick >= stare) {
                    addSpill(new Spill(fx, paint, null, Spill.Kind.UNMADE, mB, 6f, 300, floorY, "unmade" + clock));
                    fx.sound(mB, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 3f, 0.5f);
                    fx.sound(mB, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.5f);
                    if (taught.add("unmade")) {
                        fx.actionBar("&dThat ground has no rules left. &7Stay off it.");
                    }
                    serpent.eyes(WeProps.VOID);
                    next();
                }
            }
            default -> {
                if (stepTick == 0) {
                    serpent.brow(false);
                    swimTo(rim(mD, 12f, -14f), new Vector3f(mD.x, -0.7f, mD.z), 1.2f, 0.6f);
                }
                if (swim()) {
                    finish(20);
                }
            }
        }
    }

    /** The missing-texture checkerboard flashes over a disc for a moment. */
    private void flickerPatch(Vector3f center, float radius, int ticks) {
        BlockData magenta = Material.MAGENTA_CONCRETE.createBlockData();
        BlockData black = Material.BLACK_CONCRETE.createBlockData();
        Location c = fx.at(center);
        int r = (int) Math.ceil(radius);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius || rnd.nextFloat() < 0.4f) {
                    continue;
                }
                int x = c.getBlockX() + dx;
                int z = c.getBlockZ() + dz;
                paint.show(x, floorY, z, ((x + z) & 1) == 0 ? magenta : black, ticks);
            }
        }
    }

    /* ================================================================== spills and lobs */

    private void addSpill(Spill s) {
        spills.add(s);
        int unmade = 0;
        for (int i = spills.size() - 1; i >= 0; i--) {
            if (spills.get(i).kind == Spill.Kind.UNMADE && ++unmade > 3) {
                spills.remove(i).remove();
            }
        }
        while (spills.size() > 6) {
            spills.remove(0).remove();
        }
    }

    private void tickSpills() {
        List<Player> targets = fx.targets();
        for (Iterator<Spill> it = spills.iterator(); it.hasNext(); ) {
            Spill s = it.next();
            if (!s.tick(targets, this::strike)) {
                s.remove();
                it.remove();
            }
        }
        for (int i = lobs.size() - 1; i >= 0; i--) {
            if (!lobs.get(i).tick()) {
                lobs.remove(i);
            }
        }
    }

    private void clearSpills() {
        for (Spill s : spills) {
            s.remove();
        }
        spills.clear();
        for (Lob l : lobs) {
            l.remove();
        }
        lobs.clear();
    }

    /** A block thrown in an arc that runs something where it lands. */
    private final class Lob {
        private final BlockDisplay d;
        private final Vector3f from;
        private final Vector3f to;
        private final int flight;
        private final float height;
        private final float size;
        private final Runnable onLand;
        private int age;

        Lob(Material m, Vector3f from, Vector3f to, int flight, float height, float size, Runnable onLand) {
            this.d = fx.block(m, null, 15);
            this.from = new Vector3f(from);
            this.to = new Vector3f(to.x, to.y + size * 0.4f, to.z);
            this.flight = Math.max(1, flight);
            this.height = height;
            this.size = size;
            this.onLand = onLand;
            WeFx.push(d, WeFx.cube(from, size * 0.4f, new Quaternionf()), 0);
        }

        boolean tick() {
            age++;
            float u = Math.min(1f, age / (float) flight);
            Vector3f at = WeMath.lerp(from, to, u, new Vector3f());
            at.y += WeMath.arc(u) * height;
            WeFx.push(d, WeFx.cube(at, size * lerp(0.4f, 1f, Math.min(1f, u * 2f)), new Quaternionf().rotateXYZ(u * 4f, u * 3f, 0f)), 1);
            if (age >= flight) {
                remove();
                if (onLand != null) {
                    onLand.run();
                }
                return false;
            }
            return true;
        }

        void remove() {
            fx.kill(d);
        }
    }

    /* ================================================================== transitions */

    private void beginTransition() {
        endMove();
        props.clear();
        move = Move.IDLE;
        act = Act.TRANSITION;
        actTick = 0;
        BossPhase pending = instance.getPendingPhase();
        transitionPhase = pending == null ? Math.min(4, phase + 1) : phaseFor(pending.getHealthPercent());
        if (transitionPhase <= phase) {
            transitionPhase = Math.min(4, phase + 1);
        }
        int configured = pending != null && pending.getTransition() != null ? pending.getTransition().getDurationTicks() : 0;
        int authored = AUTHORED[transitionPhase];
        transitionLength = configured > 0 ? configured : authored;
        leg = null;
    }

    private int authored() {
        return Math.max(1, AUTHORED[transitionPhase]);
    }

    /** Real ticks for an authored span (the configured transition may be shorter or longer). */
    private float realScale() {
        return transitionLength / (float) authored();
    }

    private void tickTransition() {
        if (!instance.isTransitioning()) {
            finishTransition();
            return;
        }
        int a = authored();
        int tau = Math.round(actTick * a / (float) Math.max(1, transitionLength));
        int prev = actTick == 0 ? -1 : Math.round((actTick - 1) * a / (float) Math.max(1, transitionLength));
        actTick++;
        for (int k = prev + 1; k <= tau; k++) {
            switch (transitionPhase) {
                case 2 -> starless(k);
                case 3 -> unmadeTransition(k);
                case 4 -> ouroborosTransition(k);
                default -> {
                }
            }
        }
        if (transitionPhase == 4 && inRing) {
            ringIdle();
        } else {
            swim();
        }
        if (transitionPhase == 3 && tau >= 40 && tau < 90) {
            serpent.rewind(1.4f / Math.max(0.2f, realScale()));
        }
    }

    private void finishTransition() {
        phase = Math.max(phase, transitionPhase);
        stutter = false;
        applyPhaseState(phase);
        act = Act.FIGHT;
        actTick = 0;
        cooldown = 24;
        move = Move.IDLE;
        leg = null;
        orbiting = false;
        lastHealth = instance.getCombatHealth();
        for (int i = 0; i < serpent.vertebrae(); i++) {
            serpent.paintTop(i, null);
        }
    }

    /** Idempotent: what must be true of the world in each phase, however it was reached. */
    private void applyPhaseState(int p) {
        Senses s = senses();
        if (p >= 2) {
            if (s != null) {
                s.weather(WeatherType.DOWNFALL);
                s.sky(18000f, 200f);
            }
            serpent.brightness(4);
        }
        if (p >= 4 && !inRing) {
            enterRing();
        }
    }

    /**
     * STARLESS. It climbs into the sky above the island, unhinges its jaw at the heavens and
     * drinks: the stars stream into its mouth and go out, the moon with them. Then silence, and
     * from now on only its eyes are lit.
     */
    private void starless(int k) {
        float scale = realScale();
        if (k == 0) {
            List<Vector3f> controls = new ArrayList<>();
            controls.add(new Vector3f(serpent.pos));
            Vector3f d = flat(serpent.pos);
            Vector3f side = new Vector3f(-d.z, 0f, d.x);
            controls.add(new Vector3f(d).mul(30f).add(side.x * 8f, 0f, side.z * 8f));
            controls.add(new Vector3f(side).mul(20f).add(0f, 10f, 0f));
            controls.add(new Vector3f(d).mul(-12f).add(0f, 22f, 0f));
            controls.add(new Vector3f(0f, 34f, 0.5f));
            SplinePath path = SplinePath.through(controls, 20);
            swimPath(path, path.length() / Math.max(1f, 60f * scale));
            fx.sound(serpent.pos, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 3f, 0.5f);
        }
        if (k >= 60 && k < 150) {
            serpent.faceTo(new Vector3f(0f, 1f, 0.05f), 0.06f);
            serpent.lookForward();
            serpent.jaw(Math.min(1.25f, (k - 60) / 18f));
        }
        if (k == 70) {
            fx.score(Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);
        }
        if (k == 80) {
            Senses s = senses();
            if (s != null) {
                s.weather(WeatherType.DOWNFALL);
                s.sky(18000f, 200f);
            }
            fx.score(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
        }
        if (k >= 80 && k < 150) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vector3f mouth = serpent.mouth();
            for (int i = 0; i < 5; i++) {
                float a = (float) r.nextDouble(TAU);
                float rr = (float) r.nextDouble(20.0, 60.0);
                Vector3f from = new Vector3f((float) Math.sin(a) * rr, (float) r.nextDouble(55.0, 90.0), (float) Math.cos(a) * rr);
                fx.stream(Particle.END_ROD, from, mouth, 0.9);
            }
            if (k % 10 == 0) {
                fx.score(Sound.ENTITY_BREEZE_INHALE, 0.8f, 0.5f + (k - 80) * 0.004f);
                fx.sound(mouth, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 3f, 0.5f + (k - 80) * 0.01f);
            }
        }
        if (k == 150) {
            serpent.snap();
            freeze = 6;
            fx.silence();
        }
        if (k == 156) {
            serpent.brightness(4);
            fx.title("", "&8It ate the stars.", 10, 50, 20);
        }
        if (k == 166) {
            Vector3f out = flat(serpent.pos.lengthSquared() < 1f ? new Vector3f(1f, 0f, 0f) : serpent.pos);
            List<Vector3f> controls = new ArrayList<>();
            controls.add(new Vector3f(serpent.pos));
            controls.add(new Vector3f(out).mul(14f).add(0f, 20f, 0f));
            controls.add(new Vector3f(out).mul(30f).add(0f, 0f, 0f));
            controls.add(orbitPoint((float) Math.atan2(out.x, out.z) + orbitDir * 0.5f));
            SplinePath path = SplinePath.through(controls, 20);
            swimPath(path, path.length() / Math.max(1f, 32f * scale));
        }
    }

    /**
     * UNMADE. The world stutters like a server that cannot keep up; everyone rubber-bands back to
     * where they stood three seconds ago; it rewinds through its own path; a wave of missing
     * textures sweeps the island and its body; then the picture snaps back.
     */
    private void unmadeTransition(int k) {
        if (k == 0) {
            stutter = true;
            fx.silence();
            if (leg == null) {
                float a = (float) Math.atan2(serpent.pos.x, serpent.pos.z);
                leg = SplinePath.circle(0f, ORBIT_Y + 4f, 0f, orbitRadius(), a, orbitDir * 0.5f, 3f, 1f, 120);
                legAt = 0f;
                legSpeed = 0.9f;
            }
        }
        if (k == 40) {
            stutter = false;
            rubberBand(60);
            fx.title("", "&8Can't keep up! Is the server overloaded?", 0, 40, 10);
            leg = null;
        }
        if (k >= 40 && k < 90 && k % 5 == 0) {
            fx.sound(serpent.pos, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 2.5f, 1.2f - (k - 40) * 0.012f);
        }
        if (k >= 90 && k < 160) {
            float rw = lerp(0f, site ? 36f : edge + 8f, (k - 90) / 70f);
            if (k % 3 == 0) {
                paintBand(new Vector3f(), Math.max(0f, rw - 2.5f), rw, 10);
            }
            if (k % 3 == 1) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                int n = serpent.vertebrae();
                for (int i = 0; i < 3 && n > 0; i++) {
                    int idx = r.nextInt(n);
                    serpent.paintTop(idx, r.nextBoolean() ? Material.MAGENTA_CONCRETE : Material.BLACK_CONCRETE);
                }
            }
            if (k % 6 == 0) {
                fx.score(Sound.UI_BUTTON_CLICK, 0.6f, 0.5f + ThreadLocalRandom.current().nextFloat() * 0.6f);
            }
        }
        if (k == 160) {
            for (int i = 0; i < serpent.vertebrae(); i++) {
                serpent.paintTop(i, null);
            }
            fx.score(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 0.5f);
            fx.title("", "&dIt is eating the rules.", 6, 40, 16);
        }
        if (k == 164) {
            Vector3f side = flat(serpent.pos);
            swimTo(rim(side, 3f, 6f), new Vector3f(-side.x, 0f, -side.z), 1.2f, 0.5f);
            serpent.brow(true);
        }
        if (k == 196) {
            serpent.brow(false);
        }
    }

    /** Paints a ring band [r0, r1] around a stage point in missing textures. */
    private void paintBand(Vector3f center, float r0, float r1, int ticks) {
        BlockData magenta = Material.MAGENTA_CONCRETE.createBlockData();
        BlockData black = Material.BLACK_CONCRETE.createBlockData();
        Location c = fx.at(center);
        int r = (int) Math.ceil(r1);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d < r0 || d > r1) {
                    continue;
                }
                int x = c.getBlockX() + dx;
                int z = c.getBlockZ() + dz;
                paint.show(x, floorY, z, ((x + z) & 1) == 0 ? magenta : black, ticks);
            }
        }
    }

    /** Everyone snaps back to where they stood {@code ticksAgo} ticks ago, if that spot is still ground. */
    private void rubberBand(int ticksAgo) {
        for (Player p : fx.targets()) {
            Location[] ring = history.get(p.getUniqueId());
            if (ring == null) {
                continue;
            }
            Location then = ring[Math.floorMod(clock - ticksAgo, ring.length)];
            if (then == null || then.getWorld() != p.getWorld() || !safeSpot(then)) {
                continue;
            }
            Location to = then.clone();
            to.setYaw(p.getLocation().getYaw());
            to.setPitch(p.getLocation().getPitch());
            p.teleport(to);
            p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
        }
    }

    private static boolean safeSpot(Location l) {
        Block feet = l.getBlock();
        return feet.getRelative(0, -1, 0).getType().isSolid() && !feet.getType().isSolid()
                && !feet.getRelative(0, 1, 0).getType().isSolid();
    }

    private void recordHistory() {
        if (fx == null || act != Act.FIGHT && act != Act.TRANSITION) {
            return;
        }
        for (Player p : fx.targets()) {
            Location[] ring = history.computeIfAbsent(p.getUniqueId(), id -> new Location[80]);
            if (p.isOnGround()) {
                ring[Math.floorMod(clock, ring.length)] = p.getLocation();
            } else {
                Location prev = ring[Math.floorMod(clock - 1, ring.length)];
                ring[Math.floorMod(clock, ring.length)] = prev;
            }
        }
        if (clock % 200 == 0) {
            history.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        }
    }

    /**
     * OUROBOROS. It goes down, comes up everywhere at once, and lies around the island: every
     * world it still holds, stretched into one wall. The edge of the world appears exactly where
     * it lies. Then it takes its own tail in its mouth.
     */
    private void ouroborosTransition(int k) {
        if (k == 0) {
            fx.score(Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.4f);
            Vector3f out = flat(serpent.pos);
            swimTo(rim(out, 10f, -20f), new Vector3f(out.x, -0.8f, out.z), 1.6f, 0.5f);
        }
        if (k == 30) {
            enterRing();
            fx.score(Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 0.5f);
        }
        if (k >= 30 && k < 90 && k % 6 == 0) {
            fx.score(Sound.BLOCK_ROOTED_DIRT_BREAK, 0.9f, 0.4f + (k - 30) * 0.005f);
        }
        if (k == 110) {
            fx.title("", "&5It has become the edge of the world.", 8, 60, 20);
        }
        if (k == 160) {
            swallowOne(false);
            fx.sound(serpent.mouth(), Sound.ENTITY_EVOKER_FANGS_ATTACK, 3f, 0.5f);
        }
        if (k == 200) {
            fx.score(Sound.ENTITY_WARDEN_ROAR, 1f, 0.5f);
            if (taught.add("ouroboros")) {
                fx.actionBar("&5It is eating its own tail, and the world with it. &fHurt it: it brings the tail back up.");
            }
        }
    }

    /* ================================================================== ouroboros */

    private void enterRing() {
        if (inRing || serpent == null) {
            return;
        }
        inRing = true;
        float side = site ? 46f : Math.max(26f, edge * 1.9f);
        // Every world it ever ate comes back up to make the wall: enough of them that each slab
        // stays the size of a small world, not a mountain.
        float per = new RingPath().set(0f, 0f, side, 0f, 0).perimeter();
        for (int k = 0; k < 16 && (per - Serpent.NECK_GAP) / Math.max(1, serpent.visibleCount()) > 6.4f; k++) {
            serpent.grow(Serpent.WORLDS[k % Serpent.WORLDS.length], 2.4f);
        }
        serpent.ringStart(new Vector3f(0f, 0f, 0f), side, -0.7f, cornerNearest(serpent.pos));
        Vector3f rp = new Vector3f();
        Vector3f rf = new Vector3f();
        serpent.ringHead(rp, rf);
        rp.y += 1.5f;
        swimTo(rp, rf, 1.3f, 0.5f);
        Senses s = senses();
        if (s != null) {
            Location c = fx.at(new Vector3f());
            s.border(c.getX(), c.getZ(), side + 40.0);
            s.borderWarning(3);
            s.borderTo(innerSize(side), 4);
        }
        tailEvery = 70;
        ringTick = 0;
        ringDamage = 0;
    }

    /** Border size: the inner face of the ring. */
    private double innerSize(float side) {
        return Math.max(6.0, side - serpent.ringSlot() * 1.06f - 0.4f);
    }

    /** The head rests at its corner, chewing on its own tail. */
    private void ringIdle() {
        if (!inRing) {
            if (leg == null) {
                orbit();
            } else {
                swim();
            }
            return;
        }
        if (leg != null) {
            swim();
            return;
        }
        Vector3f rp = new Vector3f();
        Vector3f rf = new Vector3f();
        serpent.ringHead(rp, rf);
        rp.y += 1.5f;
        Vector3f d = new Vector3f(rp).sub(serpent.pos);
        float len = d.length();
        if (len > 0.03f) {
            serpent.moveTo(new Vector3f(serpent.pos).fma(Math.min(1f, 0.35f / len), d));
        }
        serpent.faceTo(new Vector3f(rf.x, -0.25f, rf.z), 0.05f);
        serpent.jaw(0.35f + 0.3f * (float) Math.sin(clock * 0.22f));
        Player p = fx.nearestTarget(serpent.pos);
        if (p != null && stage(p).distance(serpent.pos) < 22f) {
            serpent.look(stage(p).add(0f, 1.2f, 0f), 0.45f, 0.1f);
        } else {
            serpent.lookForward();
        }
    }

    private void tickOuroboros() {
        if (!inRing) {
            enterRing();
        }
        ringTick++;
        if (snapReady > 0) {
            snapReady--;
        }
        Senses s = senses();
        if (s != null && ringTick % 5 == 0) {
            for (Player p : fx.targets()) {
                double out = s.outside(p.getLocation());
                if (out > 0.25) {
                    Vector3f pp = stage(p);
                    strike(p, 35, new Vector3f(pp).mul(1.2f), 0.5f, 0.2f, "edge", 20);
                    Vector3f in = flat(pp).negate().mul(0.35f);
                    Vector v = p.getVelocity();
                    p.setVelocity(new Vector(v.getX() + in.x, v.getY(), v.getZ() + in.z));
                }
            }
        }
        if (ringTick % tailEvery == 0 && move == Move.IDLE) {
            swallowOne(true);
        }
        double stepHp = instance.getCombatMaxHealth() * 0.02;
        while (stepHp > 0 && ringDamage >= stepHp) {
            ringDamage -= stepHp;
            regurgitate();
        }
        if (ringTick % 170 == 80) {
            spillFromRing();
        }
    }

    /** It swallows one more world of its tail: the ring, and the world border, close in. */
    private void swallowOne(boolean enrage) {
        int worlds = serpent.visibleCount() - 3;
        if (worlds > 2) {
            if (serpent.swallowTail()) {
                float side = serpent.ringFit();
                Senses s = senses();
                if (s != null) {
                    s.borderTo(innerSize(side), 2);
                }
                fx.sound(serpent.mouth(), Sound.ENTITY_GENERIC_EAT, 3f, 0.5f);
                fx.sound(serpent.mouth(), Sound.ENTITY_GENERIC_EAT, 3f, 0.7f);
                fx.score(Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 0.6f);
                tailEvery = Math.max(34, tailEvery - 3);
            }
            return;
        }
        if (!enrage) {
            return;
        }
        // Nothing left to swallow but the world itself.
        fx.title("", "&4It ate the world.", 4, 30, 10);
        fx.score(Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
        for (Player p : fx.targets()) {
            strike(p, 200, new Vector3f(), 0f, 0.6f, "worldend", 80);
        }
        for (int i = 0; i < 4; i++) {
            regurgitate();
        }
    }

    /** Hurt, it brings its tail back up: the ring and the border grow. */
    private void regurgitate() {
        if (!serpent.regurgitateTail()) {
            return;
        }
        float side = serpent.ringFit();
        Senses s = senses();
        if (s != null) {
            s.borderTo(innerSize(side), 1);
        }
        fx.sound(serpent.mouth(), Sound.ENTITY_WARDEN_HURT, 3f, 0.5f);
        fx.sound(serpent.mouth(), Sound.ENTITY_LLAMA_SPIT, 3f, 0.4f);
        serpent.skull.kick(-0.2f, 0f, 0f);
        tailEvery = Math.min(70, tailEvery + 2);
    }

    /** One of the worlds in the ring spills over onto whoever stands nearest it. */
    private void spillFromRing() {
        List<Player> targets = fx.targets();
        if (targets.isEmpty()) {
            return;
        }
        Player p = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        Vector3f tp = stage(p);
        Serpent.Vertebra best = null;
        float bestD = Float.MAX_VALUE;
        for (Serpent.Vertebra v : serpent.body) {
            if (v.hidden || v.world == Serpent.VOID_NECK) {
                continue;
            }
            float d = v.center.distanceSquared(tp);
            if (d < bestD) {
                bestD = d;
                best = v;
            }
        }
        if (best == null) {
            return;
        }
        Serpent.World w = best.world;
        Vector3f at = new Vector3f(tp.x, 0f, tp.z);
        props.add(new WeProps.Circle(fx, at, 5.5f, 30, w.top(), w.tint()));
        lobs.add(new Lob(w.top(), new Vector3f(best.center), at, 30, 9f, 2.4f, () -> land(w, at)));
        fx.sound(best.center, Sound.ENTITY_LLAMA_SPIT, 3f, 0.5f);
    }

    /* ================================================================== move: SNAP */

    /** From its corner it snaps at whoever came too close, then settles back on its tail. */
    private void snap() {
        switch (step) {
            case 0 -> {
                if (stepTick == 0) {
                    Player p = fx.nearestTarget(serpent.pos);
                    if (p == null) {
                        finish(20);
                        return;
                    }
                    Vector3f tp = stage(p);
                    mB.set(tp.x, 0.8f, tp.z);
                    Vector3f dir = new Vector3f(mB).sub(serpent.pos);
                    dir.y = 0f;
                    dir = dir.lengthSquared() < 0.01f ? new Vector3f(0f, 0f, 1f) : dir.normalize();
                    mC.set(mB).fma(1.5f, dir);
                    serpent.jaw(1.1f);
                    swimTo(mC, dir, 2.1f, 0.3f);
                    fx.sound(serpent.pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.7f);
                }
                boolean done = swim();
                if (mI == 0 && (WeMath.horizontal(serpent.mouth(), mB) < 2.2f || done)) {
                    mI = 1;
                    serpent.snap();
                    bite(mB, 3.2f, 130, "snap");
                    freeze = 3;
                }
                if (done) {
                    next();
                }
            }
            case 1 -> {
                if (stepTick == 0) {
                    expose(24);
                }
                if (stepTick >= 16) {
                    next();
                }
            }
            default -> {
                if (stepTick == 0) {
                    Vector3f rp = new Vector3f();
                    Vector3f rf = new Vector3f();
                    serpent.ringHead(rp, rf);
                    rp.y += 1.5f;
                    swimTo(rp, rf, 1.0f, 0.4f);
                }
                if (swim()) {
                    snapReady = 70;
                    finish(20);
                }
            }
        }
    }

    /* ================================================================== death: generating world */

    private boolean tickDeath() {
        int t = actTick++;
        if (t >= DEATH_TICKS || worldSavedShown) {
            finishDeath();
            return true;
        }
        Senses s = senses();
        if (t == 0) {
            clearSpills();
            props.clear();
            freeze = 12;
            leg = null;
            fx.score(Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 0.5f);
            fx.score(Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
            if (!inRing) {
                enterRing();
            }
            mJ = -1;
        }
        if (t == 2) {
            fx.silence();
        }
        if (t >= 14 && t < 160 && mJ < 0) {
            // It keeps eating: its tail, faster and faster, until there is no tail.
            ringIdle();
            serpent.jaw(0.8f + 0.4f * (float) Math.sin(t * 0.6f));
            int every = t < 60 ? 5 : t < 100 ? 3 : 2;
            if (t % every == 0) {
                if (serpent.swallowTail()) {
                    float side = serpent.ringFit();
                    if (s != null) {
                        s.borderTo(Math.max(10.0, innerSize(side)), 1);
                    }
                    fx.sound(serpent.mouth(), Sound.ENTITY_GENERIC_EAT, 3f, 0.5f + Math.min(1f, (t - 14) / 120f));
                } else {
                    mJ = t;
                }
            }
            if (t == 159 && mJ < 0) {
                mJ = t;
            }
        }
        if (mJ >= 0) {
            int u = t - mJ;
            if (u == 0) {
                // Only the head is left. It looks at you.
                serpent.hideBody(true);
                serpent.brow(true);
                serpent.eyes(WeProps.WHITE);
                fx.score(Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.5f);
            }
            if (u < 40) {
                Player p = fx.nearestTarget(serpent.pos);
                if (p != null) {
                    serpent.faceTo(stage(p).add(0f, 1f, 0f).sub(serpent.pos), 0.05f);
                }
                serpent.jaw(0.15f);
            }
            if (u == 20) {
                fx.title("", "&8It is still hungry.", 10, 40, 10);
            }
            if (u >= 40 && u < 90) {
                // It eats itself.
                float f = inCubic(window(u, 40, 88));
                serpent.headScale(1f - f);
                serpent.jaw(1.25f);
                Vector3f spin = new Vector3f((float) Math.sin(u * 0.35f), -0.1f, (float) Math.cos(u * 0.35f));
                serpent.faceTo(spin, 0.4f);
                fx.particle(Particle.REVERSE_PORTAL, serpent.skullCenter(), 20, 1.5, 0.1);
                if (u == 40) {
                    fx.score(Sound.BLOCK_PORTAL_TRIGGER, 1f, 0.5f);
                }
            }
            if (u == 90) {
                serpent.hideHead(true);
                serpent.headScale(0.001f);
                point = fx.block(Material.BLACK_CONCRETE, WeProps.VOID, 15);
                mD.set(serpent.skullCenter());
                WeFx.push(point, WeFx.cube(mD, 0.3f, new Quaternionf()), 0);
                fx.silence();
            }
            if (u > 90 && u < 120 && point != null && u % 6 == 0) {
                WeFx.push(point, WeFx.cube(mD, u % 12 == 0 ? 0.42f : 0.26f, new Quaternionf().rotateY(u * 0.3f)), 5);
            }
            if (u == 120) {
                fx.kill(point);
                point = null;
                generationAt = t;
                generationBegins();
            }
        }
        if (generationAt >= 0) {
            generating(t - generationAt);
        }
        return false;
    }

    /** Nothing is left. The world starts over. */
    private void generationBegins() {
        fx.score(Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
        fx.score(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.5f);
        fx.title("", "&7Generating world", 4, 60, 10);
        Senses s = senses();
        if (s != null) {
            s.weather(WeatherType.CLEAR);
            s.sky(23600f, 26f);
            s.borderWarning(0);
            s.borderTo(900.0, 10);
        }
        regenLayer = -1;
    }

    /** "Preparing spawn area": the island comes back one chunk at a time, bottom to top. */
    private void generating(int g) {
        if (g < 0) {
            return;
        }
        int per = 24;
        int idx = g / per;
        int local = g % per;
        if (rift != null) {
            rift.open(1f - smooth(window(g, 0, 90)));
            if (g >= 90) {
                rift.remove();
                rift = null;
            }
        }
        if (idx < REGEN.length) {
            int cx = REGEN[idx][0];
            int cz = REGEN[idx][1];
            Vector3f c = new Vector3f(cx * 16f - 16f, 0f, cz * 16f - 16f);
            if (local == 0) {
                regenMark = props.add(new WeProps.ChunkMark(fx, c.x - 8f, c.z - 8f, 0f, 26));
                regenLayer = SiteLayout.ISLAND.y0();
                fx.cinematicBar("&7Preparing spawn area: &f" + (idx * 100 / REGEN.length) + "%");
            }
            SiteTerrain t = terrain();
            if (t != null && regenLayer >= 0) {
                for (int k = 0; k < 9 && regenLayer <= SiteLayout.ISLAND.y1(); k++) {
                    t.restoreChunk(cx, cz, regenLayer, regenLayer);
                    regenLayer++;
                }
                if (regenLayer > SiteLayout.ISLAND.y1()) {
                    regenLayer = -1;
                    liftOutOfGround(cx, cz);
                }
            }
            if (local == 14) {
                if (regenMark != null) {
                    regenMark.flash();
                    regenMark.expire(8);
                }
                fx.score(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, WeMath.semitone(idx * 2 - 6));
                fx.score(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, WeMath.semitone(idx * 2 - 6));
                fx.blockDust(new Vector3f(c.x, 0.5f, c.z), Material.GRASS_BLOCK, 40, 5.0);
            }
            return;
        }
        int after = g - REGEN.length * per;
        if (after == 0) {
            fx.cinematicBar("&7Preparing spawn area: &f100%");
            java.util.Arrays.fill(eaten, false);
            eatenCount = 0;
        }
        if (after == 16) {
            fx.say("&7&o[Server: Saving the game (this may take a moment!)]");
        }
        if (after == 50) {
            fx.say("&7&o[Server: Saved the game]");
            Senses s = senses();
            if (s != null) {
                s.borderOff();
            }
        }
        if (after == 58) {
            fx.title("&f&lWORLD SAVED", "&7Nihil ate itself. &8The Last Seed grows back.", 10, 70, 30);
            fx.score(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (after == 100) {
            fx.cinematicBar("&8A Bonus Chest was generated.");
        }
        if (after >= 124) {
            // The world is saved: hand over to the Bonus Chest.
            worldSavedShown = true;
        }
    }

    /** Anyone a regenerated chunk grew around is lifted onto its surface. */
    private void liftOutOfGround(int cx, int cz) {
        for (Player p : fx.world().getPlayers()) {
            Location l = p.getLocation();
            if (SiteLayout.islandChunk(l.getBlockX()) != cx || SiteLayout.islandChunk(l.getBlockZ()) != cz) {
                continue;
            }
            if (l.getY() > SiteLayout.FLOOR + 1.5 || l.getY() < SiteLayout.ISLAND_BOTTOM) {
                continue;
            }
            Block feet = l.getBlock();
            if (!feet.getType().isSolid() && !feet.getRelative(0, 1, 0).getType().isSolid()) {
                continue;
            }
            int y = SiteLayout.FLOOR + 1;
            while (y < SiteLayout.FLOOR + 14 && (fx.world().getBlockAt(l.getBlockX(), y, l.getBlockZ()).getType().isSolid()
                    || fx.world().getBlockAt(l.getBlockX(), y + 1, l.getBlockZ()).getType().isSolid())) {
                y++;
            }
            Location to = l.clone();
            to.setY(y);
            p.teleport(to);
        }
    }

    private void finishDeath() {
        deathFinished = true;
        if (site) {
            WorldEaterSite s = WorldEaterSite.get();
            if (s != null) {
                s.worldSaved();
            }
        }
        LivingEntity entity = instance.getEntity();
        clear();
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    /* ================================================================== reactions */

    private void expose(int ticks) {
        boolean was = exposeTicks > 0;
        exposeTicks = Math.max(exposeTicks, ticks);
        if (!was && serpent != null) {
            serpent.eyes(WeProps.WHITE);
            fx.sound(serpent.skullCenter(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2f, 0.5f);
        }
    }

    private void tickExpose() {
        if (exposeTicks > 0 && --exposeTicks == 0 && serpent != null) {
            serpent.eyes(WeProps.VOID);
        }
    }

    private void hitReactions() {
        double hp = instance.getCombatHealth();
        if (hp < lastHealth - 0.01 && clock - lastHitReact >= 4) {
            lastHitReact = clock;
            Vector3f skull = serpent.skullCenter();
            fx.sound(skull, Sound.ENTITY_ENDER_DRAGON_HURT, 1.4f, 0.6f + ThreadLocalRandom.current().nextFloat() * 0.2f);
            fx.sound(skull, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.5f);
            fx.dust(skull, WeProps.VOID, 1.4f, 10, 0.8);
            serpent.skull.kick(0.06f, 0f, 0f);
        }
        lastHealth = hp;
    }

    /** The body is solid where it crosses the island. */
    private void bodyContact() {
        if (serpent == null || !serpent.spawned()) {
            return;
        }
        for (Player p : fx.targets()) {
            Vector3f pp = stage(p).add(0f, 0.9f, 0f);
            if (pp.distance(serpent.skullCenter()) < 2.6f || touchesBody(pp, 0.5f)) {
                Vector3f from = serpent.nearestBody(pp);
                strike(p, 45, from == null ? serpent.pos : from, 1.0f, 0.5f, "body", 30);
            }
        }
    }

    private boolean touchesBody(Vector3f p, float pad) {
        for (Serpent.Vertebra v : serpent.body) {
            if (v.hidden || v.center.y < -3.5f || v.center.y > 6f) {
                continue;
            }
            float r = v.size * v.ringMul * 0.55f + pad;
            if (v.center.distanceSquared(p) < r * r) {
                return true;
            }
        }
        return false;
    }

    /* ================================================================== combat helpers */

    private Player currentTarget() {
        if (target != null) {
            Player p = Bukkit.getPlayer(target);
            if (p != null && fx.targets().contains(p)) {
                return p;
            }
        }
        List<Player> all = fx.targets();
        if (all.isEmpty()) {
            target = null;
            return null;
        }
        Player p = all.get(ThreadLocalRandom.current().nextInt(all.size()));
        target = p.getUniqueId();
        return p;
    }

    private boolean strike(Player p, double power, Vector3f from, String key, int gateTicks) {
        return strike(p, power, from, 0.4f, 0.25f, key, gateTicks);
    }

    private boolean strike(Player p, double power, Vector3f from, float knock, float lift, String key, int gateTicks) {
        String k = p.getUniqueId() + "|" + key;
        Integer last = gate.get(k);
        if (last != null && clock - last < gateTicks) {
            return false;
        }
        gate.put(k, clock);
        if (gate.size() > 256) {
            gate.entrySet().removeIf(e -> clock - e.getValue() > 400);
        }
        if (power > 0) {
            BossHits.hurt(p, instance.getEntity(), power);
        }
        if (knock > 0f || lift > 0f) {
            Vector3f pp = stage(p);
            Vector3f dir = new Vector3f(pp.x - from.x, 0f, pp.z - from.z);
            if (dir.lengthSquared() < 1e-3f) {
                dir.set(serpent == null ? new Vector3f(0f, 0f, 1f) : serpent.fwd);
                dir.y = 0f;
            }
            if (dir.lengthSquared() > 1e-6f) {
                dir.normalize(knock);
            }
            p.setVelocity(new Vector(dir.x, lift, dir.z));
        }
        return true;
    }

    /** A bite into the island: a hole where it closed its jaws, and whoever was there. */
    private void bite(Vector3f at, float radius, double power, String key) {
        Vector3f ground = new Vector3f(at.x, 0f, at.z);
        for (Player p : fx.targets()) {
            Vector3f pp = stage(p);
            if (WeMath.horizontal(pp, ground) <= radius + 0.3f && pp.y < 4.5f && pp.y > -2.5f) {
                strike(p, power, ground, 1.3f, 0.7f, key, 20);
            }
        }
        fx.sound(ground, Sound.ENTITY_EVOKER_FANGS_ATTACK, 3f, 0.5f);
        fx.sound(ground, Sound.ENTITY_GENERIC_EAT, 3f, 0.4f);
        fx.sound(ground, Sound.BLOCK_ROOTED_DIRT_BREAK, 3f, 0.5f);
        fx.sound(ground, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.6f);
        fx.blockDust(new Vector3f(ground).add(0f, 0.4f, 0f), Material.DIRT, 50, radius * 0.5);
        props.add(new WeProps.Shockwave(fx, ground, 0.6f, radius * 2.2f, 0.8f, 12, Material.DIRT, null));
        SiteTerrain t = terrain();
        if (t != null) {
            Location w = fx.at(ground);
            List<int[]> surface = t.eatSphere(w.getX(), SiteLayout.FLOOR + 0.5, w.getZ(), 2.8);
            int n = 0;
            for (int[] c : surface) {
                if (n++ >= 8) {
                    break;
                }
                Material m = SiteTerrain.material(c);
                if (!m.isBlock() || m == Material.WATER) {
                    continue;
                }
                Vector3f from = fx.stage(new Location(fx.world(), c[0] + 0.5, c[1] + 0.5, c[2] + 0.5));
                ThreadLocalRandom r = ThreadLocalRandom.current();
                props.add(WeProps.Debris.toss(fx, m, from, new Vector3f((float) r.nextDouble(-3, 3), 1.5f, (float) r.nextDouble(-3, 3)), -0.2f, 0.55f));
            }
        } else {
            BlockData black = Material.BLACK_CONCRETE.createBlockData();
            Location c = fx.at(ground);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz <= 6) {
                        paint.show(c.getBlockX() + dx, floorY, c.getBlockZ() + dz, black, 300);
                    }
                }
            }
        }
    }
}
