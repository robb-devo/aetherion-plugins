package de.aetherion.items.listener;

import de.aetherion.items.combat.ScriptedHits;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Deepsong Conch (deepsong_conch) — The Stone Sea. The server's flagship.
 *
 * <p>You blow the conch and the sun falls out of the sky: every onlooker's sky races through sunset
 * into night in a second. The ground forgets it is solid — rings run out from your feet like a stone
 * dropped in a pond, and a vast shadow begins to circle beneath the floor, a wake of terrain heaving
 * over its back. It swings in front of you. The ground domes. Silence.
 *
 * <p>A twenty-five-block star-whale breaches out of solid earth: terrain crown, spray sheets, blue-violet
 * hide with a pale pleated belly, glowing eye, bioluminescent flanks, starlight along the spine, great
 * white pectoral fins trailing aurora ribbons across the night. It rolls as it rises, hangs at the apex in
 * slow motion — turns its head, looks at you, sings — then arcs over and dives back into the ground as if
 * it were water, flukes last.
 *
 * <p>Hush. The aurora hangs in the sky. Its shadow drifts past once more, deeper. Two heartbeats under the
 * floor — and the punchline: the Leviathan exhales. A thirty-block geyser blows out of the stone, throws
 * every enemy into the sky, and the night races into dawn while the spray comes down.
 *
 * <p>No blocks are touched. The "sea" is terrain sampled into displays; the sky is per-player time.
 */
public final class DeepsongLeviathan {

    /* ---------------------------------------------------------------- timeline (ticks) */
    private static final int T_SUNSET = 22;
    private static final int T_SEA = 16;
    private static final int T_VEER = 42;
    private static final int T_DOME = 50;
    private static final int T_SILENCE = 57;
    private static final int T_BREACH = 62;
    private static final int HUSH = 20;
    private static final int THUMP = 8;
    private static final int SPOUT = 36;
    private static final int DAWN_DELAY = 6;
    private static final int DAWN = 28;
    private static final int FLIGHT_CAP = 180;

    /* ---------------------------------------------------------------- staging */
    private static final double BREACH_AHEAD = 7.0;
    private static final double DIVE_X_MAX = 20.0;
    private static final double DIVE_X_MIN = 11.0;
    private static final double SHADOW_R = 11.0;
    private static final int NJ = 25;
    private static final int NSEG = NJ - 1;
    private static final float SP = 1.0f;
    private static final int SUB = 20;
    private static final int SPOTS = 9;
    private static final int STARS = 20;
    private static final int PLEAT_SEGS = 7;
    private static final int RIBBON_K = 14;
    private static final float RIBBON_STEP = 1.6f;
    private static final int GROUND_CAP = 150;
    private static final int MAX_LIVE = 520;
    private static final int DRUMS = 8;
    private static final float DRUM_H = 3.6f;
    private static final int FRONDS = 12;
    private static final long NIGHT = 14500L;

    private static final Color AURORA_A = Color.fromRGB(100, 255, 220);
    private static final Color AURORA_B = Color.fromRGB(175, 95, 255);
    private static final Color SPRAY = Color.fromRGB(236, 250, 255);
    private static final Color BIOLUME = Color.fromRGB(90, 255, 230);
    private static final Color STARLIGHT = Color.fromRGB(235, 245, 255);
    private static final Color EYE = Color.fromRGB(255, 200, 80);
    private static final Color SHADE = Color.fromRGB(4, 8, 22);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    /** forward, side, width, length, angle(deg), alpha — the whale's shadow under the floor. */
    private static final float[][] SHADOW_SPEC = {
            {0f, 0f, 3.6f, 13f, 0f, 68f},
            {2.4f, 0f, 4.8f, 7f, 0f, 46f},
            {7.3f, 0f, 3.3f, 3.2f, 0f, 52f},
            {-6.0f, 0f, 1.6f, 4f, 0f, 50f},
            {3.2f, -3.5f, 1.4f, 6.5f, -55f, 50f},
            {3.2f, 3.5f, 1.4f, 6.5f, 55f, 50f},
            {-8.6f, -1.9f, 1.3f, 4.2f, -68f, 52f},
            {-8.6f, 1.9f, 1.3f, 4.2f, 68f, 52f}
    };

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();
    /** Players whose client sky a conch is currently driving. */
    private static final Set<UUID> SKY_LOCKED = ConcurrentHashMap.newKeySet();

    private enum Phase { PRELUDE, FLIGHT, HUSH, THUMP, SPOUT, DONE }

    private final JavaPlugin plugin;
    private final Player caster;
    private final World world;
    private final double damage;
    private final Runnable done;

    private Location feet;
    /** Breach point on the surface; every whale / shadow / spray display is anchored here. */
    private Location origin;
    private Location diveAt;
    private Vector3f fwd;
    private Vector3f side0;
    private double diveX;
    private float diveDy;
    private Path path;
    private double sB;
    private double sApex;
    private double sD;
    private double headS;
    private Vector3f casterEye;
    private boolean crowded;
    private int groundCap;
    private int t;
    private Phase phase = Phase.PRELUDE;
    private int phaseStart;
    private boolean released;

    /* sky */
    private final List<Player> skyViewers = new ArrayList<>();
    private long skyFrom;
    private long skyNight;
    private long skyDawnEnd;
    private int dawnStart = -1;
    private boolean skyReset;

    /* whale */
    private final BlockDisplay[] core = new BlockDisplay[NSEG];
    private final BlockDisplay[] diamond = new BlockDisplay[NSEG];
    private final BlockDisplay[] belly = new BlockDisplay[NSEG];
    private final BlockDisplay[] pleats = new BlockDisplay[PLEAT_SEGS * 2];
    private final BlockDisplay[] eyes = new BlockDisplay[4];
    private final BlockDisplay[] mouth = new BlockDisplay[2];
    private final BlockDisplay[] knobs = new BlockDisplay[6];
    private final BlockDisplay[] fins = new BlockDisplay[4];
    private final BlockDisplay[] flukes = new BlockDisplay[4];
    private final BlockDisplay[] spots = new BlockDisplay[SPOTS * 2];
    private final BlockDisplay[] stars = new BlockDisplay[STARS];
    private BlockDisplay rostrum;
    private BlockDisplay dorsal;
    private boolean whaleAlive;

    private final Vector3f[] joints = new Vector3f[NJ];
    private final boolean[] above = new boolean[NJ];
    private final Vector3f[] segMid = new Vector3f[NSEG];
    private final Vector3f[] segT = new Vector3f[NSEG];
    private final Vector3f[] segU = new Vector3f[NSEG];
    private final Vector3f[] segX = new Vector3f[NSEG];
    private final Quaternionf[] segQ = new Quaternionf[NSEG];
    private final float[] segLen = new float[NSEG];
    private final boolean[] segVisible = new boolean[NSEG];
    private int flightAge;
    private float lookWeight;
    private boolean glinted;
    private boolean apexHushed;
    private boolean accelCalled;
    private boolean dived;
    private int lastFlap;
    private int lastSplash = -10;
    private final Set<UUID> struck = new HashSet<>();
    private final Set<BlockDisplay> hidden = new HashSet<>();

    /* the stone sea */
    private final Map<Long, GroundTile> ground = new HashMap<>();
    private final Map<Long, Double> want = new HashMap<>();
    private final Map<Long, Integer> wantY = new HashMap<>();
    private final Map<Long, Surface> surfaces = new HashMap<>();
    private final List<Ripple> ripples = new ArrayList<>();

    /* fx */
    private final List<Quad> shadow = new ArrayList<>();
    private final Ribbon[] ribbons = new Ribbon[2];
    private final List<Crown> crowns = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    private final BlockDisplay[] drums = new BlockDisplay[DRUMS];
    private final List<Quad> mist = new ArrayList<>();
    private final List<Quad> fronds = new ArrayList<>();

    // ------------------------------------------------------------------ lifecycle

    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
        for (UUID id : SKY_LOCKED) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.resetPlayerTime();
            }
        }
        SKY_LOCKED.clear();
    }

    static void cast(JavaPlugin plugin, Player player, double damage, Runnable done) {
        new DeepsongLeviathan(plugin, player, damage, done).start();
    }

    private DeepsongLeviathan(JavaPlugin plugin, Player caster, double damage, Runnable done) {
        this.plugin = plugin;
        this.caster = caster;
        this.world = caster.getWorld();
        this.damage = damage;
        this.done = done;
    }

    private void start() {
        feet = floorUnder(caster.getLocation());
        Vector look = caster.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        look.normalize();
        fwd = new Vector3f((float) look.getX(), 0f, (float) look.getZ());
        side0 = new Vector3f(fwd).cross(0f, 1f, 0f).normalize();

        origin = floorNear(feet.clone().add(fwd.x * BREACH_AHEAD, 0, fwd.z * BREACH_AHEAD), feet.getY());
        diveX = DIVE_X_MAX;
        Location probe = origin.clone().add(0, 3.0, 0);
        RayTraceResult wall = world.rayTraceBlocks(probe, new Vector(fwd.x, 0, fwd.z), DIVE_X_MAX + 6.0,
                FluidCollisionMode.NEVER, true);
        if (wall != null && wall.getHitPosition() != null) {
            double reach = wall.getHitPosition().distance(probe.toVector());
            diveX = Math.max(DIVE_X_MIN, Math.min(DIVE_X_MAX, reach - 4.0));
        }
        diveAt = floorNear(origin.clone().add(fwd.x * diveX, 0, fwd.z * diveX), origin.getY());
        diveDy = (float) (diveAt.getY() - origin.getY());
        buildPath();
        casterEye = rel(caster.getEyeLocation());
        crowded = LIVE.size() > MAX_LIVE - 380;
        groundCap = crowded ? 70 : GROUND_CAP;
        stageSky();

        world.playSound(feet, Sound.ITEM_GOAT_HORN_SOUND_6, 2.6f, 0.62f);
        world.playSound(feet, Sound.ITEM_GOAT_HORN_SOUND_7, 1.4f, 0.5f);
        world.playSound(feet, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.2f, 0.5f);
        world.playSound(feet, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 0.6f);
        caster.sendMessage("§3✦ Deepsong Conch §7— you blow the conch.");
        caster.sendActionBar(Component.text("§3§o…the ground forgets it is solid."));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!caster.isOnline() || caster.getWorld() != world) {
                    clearAll();
                    cancel();
                    return;
                }
                boolean finished;
                try {
                    finished = step();
                } catch (Throwable error) {
                    plugin.getLogger().warning("[Deepsong] timeline aborted: " + error);
                    finished = true;
                }
                if (finished) {
                    clearAll();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void release() {
        if (!released) {
            released = true;
            done.run();
        }
    }

    private boolean step() {
        t++;
        tickSky();
        switch (phase) {
            case PRELUDE -> prelude();
            case FLIGHT -> flight();
            case HUSH -> hush();
            case THUMP -> thump();
            case SPOUT -> spout();
            case DONE -> {
                return true;
            }
        }
        tickRipples();
        flushGround();
        tickRibbons();
        tickCrowns();
        tickChunks();
        return phase == Phase.DONE;
    }

    // ------------------------------------------------------------------ act I — the call, the stone sea

    private void prelude() {
        if (t == 2 || t == 10) {
            ripples.add(new Ripple(feet.getX(), feet.getZ(), surfaceY(feet), t, 0.55, t == 2 ? 0.5 : 0.36, 13.0, 1.5));
            world.playSound(feet, Sound.AMBIENT_UNDERWATER_ENTER, 0.9f, t == 2 ? 0.5f : 0.6f);
            world.playSound(feet, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.7f, 0.6f);
        }
        if (t == T_SEA - 1) {
            stageShadow();
        }
        if (t >= T_SEA) {
            tickShadow();
        }
        if (t == T_SEA + 4) {
            caster.sendActionBar(Component.text("§b~ §3The Stone Sea §b~"));
        }
        if (t >= T_DOME) {
            dome();
        }
        if (t == T_SILENCE) {
            for (Player near : world.getPlayers()) {
                if (near.getLocation().distanceSquared(origin) < 96 * 96) {
                    near.stopAllSounds();
                }
            }
            world.playSound(origin, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.8f, 0.5f);
        }
        if (t == T_BREACH - 4 && !crowded) {
            ribbons[0] = new Ribbon();
            ribbons[1] = new Ribbon();
        }
        if (t == T_BREACH - 1) {
            stageWhale();
        }
        if (t == T_BREACH) {
            breach();
        }
    }

    private void stageShadow() {
        for (int i = 0; i < SHADOW_SPEC.length; i++) {
            shadow.add(new Quad(SHADE));
        }
    }

    private void tickShadow() {
        Vector3f heading = new Vector3f();
        Vector3f center = shadowPoint(t, heading);
        double fadeIn = Math.min(1.0, (t - T_SEA) / 8.0);
        double sink = t >= T_DOME ? Math.min(1.0, (t - T_DOME) / (double) (T_BREACH - 2 - T_DOME)) : 0.0;
        float scale = (float) (1.0 - 0.5 * sink);
        double vis = fadeIn * (1.0 - sink);
        poseShadow(center, heading, scale, vis);
        if (vis > 0.05) {
            Vector3f ahead = new Vector3f(center).add(new Vector3f(heading).mul(2.5f));
            wake(abs(ahead), heading, 9.0 * scale, 3.2 * scale, 0.36 * vis);
            if (t % 3 == 0) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                Location at = abs(center).add(0, 0.3, 0);
                world.spawnParticle(Particle.BUBBLE_POP, at, 6, 2.2, 0.1, 2.2, 0.02);
                world.spawnParticle(Particle.DUST, at.clone().add(random.nextDouble(-3, 3), 0, random.nextDouble(-3, 3)),
                        2, 0.3, 0.05, 0.3, 0, new Particle.DustOptions(BIOLUME, 1.0f));
                world.spawnParticle(Particle.GLOW, at, 2, 3.0, 0.1, 3.0, 0.0);
            }
            if (t % 14 == 0) {
                Location at = abs(center);
                world.playSound(at, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 1.2f, 0.5f);
                world.playSound(at, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.8f, 0.5f);
            }
        }
        if (t == T_SEA + 10) {
            world.playSound(abs(center), Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE, 2.2f, 0.6f);
        }
        if (t == T_VEER) {
            world.playSound(abs(center), Sound.ITEM_GOAT_HORN_SOUND_6, 1.2f, 0.5f);
            world.playSound(abs(center), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.0f, 0.55f);
        }
        if (t >= T_BREACH - 1) {
            for (Quad quad : shadow) {
                quad.alpha(0);
            }
        }
    }

    /** Circles the caster from behind, then swoops in to the breach point. O-relative, on the floor. */
    private Vector3f shadowPoint(int tick, Vector3f headingOut) {
        Vector3f center = rel(feet);
        double theta0 = Math.atan2(-fwd.z, -fwd.x);
        double thetaEnd = theta0 + Math.PI * 1.5;
        Vector3f pos;
        if (tick < T_VEER) {
            double k = Math.max(0, (tick - T_SEA) / (double) (T_VEER - T_SEA));
            double th = theta0 + Math.PI * 1.5 * k;
            pos = new Vector3f(center).add((float) (Math.cos(th) * SHADOW_R), 0f, (float) (Math.sin(th) * SHADOW_R));
            headingOut.set((float) -Math.sin(th), 0f, (float) Math.cos(th));
        } else {
            double k = Math.min(1.0, (tick - T_VEER) / (double) (T_BREACH - 2 - T_VEER));
            Vector3f start = new Vector3f(center).add((float) (Math.cos(thetaEnd) * SHADOW_R), 0f,
                    (float) (Math.sin(thetaEnd) * SHADOW_R));
            Vector3f tangent = new Vector3f((float) -Math.sin(thetaEnd), 0f, (float) Math.cos(thetaEnd));
            Vector3f control = new Vector3f(start).add(new Vector3f(tangent).mul(7f));
            Vector3f end = new Vector3f();
            float a = (float) ((1 - k) * (1 - k));
            float b = (float) (2 * (1 - k) * k);
            float c = (float) (k * k);
            pos = new Vector3f(start).mul(a).add(new Vector3f(control).mul(b)).add(new Vector3f(end).mul(c));
            Vector3f d = new Vector3f(control).sub(start).mul((float) (2 * (1 - k)))
                    .add(new Vector3f(end).sub(control).mul((float) (2 * k)));
            if (d.lengthSquared() < 1.0E-4f) {
                d.set(fwd);
            }
            headingOut.set(d.x, 0f, d.z).normalize();
        }
        pos.y = floorRel(pos);
        return pos;
    }

    private void poseShadow(Vector3f center, Vector3f heading, float scale, double vis) {
        Vector3f right = new Vector3f(heading).cross(0f, 1f, 0f).normalize();
        for (int i = 0; i < shadow.size() && i < SHADOW_SPEC.length; i++) {
            float[] spec = SHADOW_SPEC[i];
            Vector3f at = new Vector3f(center)
                    .add(new Vector3f(heading).mul(spec[0] * scale))
                    .add(new Vector3f(right).mul(spec[1] * scale));
            at.y = center.y + 0.07f + i * 0.004f;
            Vector3f along = new Quaternionf().rotateY((float) Math.toRadians(-spec[4])).transform(new Vector3f(heading));
            Quad quad = shadow.get(i);
            quad.pose(at, flat(along), spec[2] * scale, spec[3] * scale, 2);
            quad.alpha((int) (spec[5] * vis));
        }
    }

    private void dome() {
        if (t >= T_BREACH) {
            return;
        }
        double k = (t - T_DOME) / (double) (T_BREACH - T_DOME);
        double amp = 1.25 * k * k * k + 0.1 * Math.max(0, Math.sin(t * 1.3)) * k;
        disk(origin, 3.8, amp);
        if (t % 3 == 0) {
            world.playSound(origin, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.7f + (float) k * 0.5f, 0.5f);
            world.spawnParticle(Particle.BUBBLE_POP, origin.clone().add(0, 0.4, 0), 8, 1.8, 0.1, 1.8, 0.03);
        }
        if (t % 4 == 0 && t < T_SILENCE) {
            world.playSound(origin, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 0.8f, 0.5f + (float) k * 0.3f);
        }
    }

    // ------------------------------------------------------------------ act II — breach

    private void stageWhale() {
        BlockData hide = Material.BLUE_TERRACOTTA.createBlockData();
        BlockData dark = Material.BLACK_CONCRETE.createBlockData();
        BlockData pale = Material.SMOOTH_QUARTZ.createBlockData();
        BlockData fin = Material.CALCITE.createBlockData();
        for (int j = 0; j < NSEG; j++) {
            core[j] = spawnBlock(hide, null, 2.0f);
            diamond[j] = spawnBlock(dark, null, 2.0f);
            if ((j + 0.5) / NSEG <= 0.82) {
                belly[j] = spawnBlock(pale, null, 2.0f);
            }
        }
        for (int i = 0; i < pleats.length; i++) {
            pleats[i] = spawnBlock(hide, null, 2.0f);
        }
        for (int s = 0; s < 2; s++) {
            eyes[s * 2] = spawnBlock(dark, null, 2.0f);
            eyes[s * 2 + 1] = spawnBlock(Material.OCHRE_FROGLIGHT.createBlockData(), EYE, 2.0f);
            mouth[s] = spawnBlock(pale, null, 2.0f);
            fins[s * 2] = spawnBlock(fin, null, 2.0f);
            fins[s * 2 + 1] = spawnBlock(fin, null, 2.0f);
            flukes[s * 2] = spawnBlock(hide, null, 2.0f);
            flukes[s * 2 + 1] = spawnBlock(dark, null, 2.0f);
        }
        for (int i = 0; i < knobs.length; i++) {
            knobs[i] = spawnBlock(fin, null, 2.0f);
        }
        rostrum = spawnBlock(hide, null, 2.0f);
        dorsal = spawnBlock(dark, null, 2.0f);
        for (int i = 0; i < spots.length; i++) {
            spots[i] = spawnBlock(Material.SEA_LANTERN.createBlockData(), BIOLUME, 2.0f);
        }
        for (int i = 0; i < stars.length; i++) {
            stars[i] = spawnBlock(Material.WHITE_CONCRETE.createBlockData(), i % 3 == 0 ? STARLIGHT : null, 2.0f);
        }
        whaleAlive = true;
    }

    private void breach() {
        phase = Phase.FLIGHT;
        phaseStart = t;
        headS = sB;
        for (int j = 0; j < NJ; j++) {
            above[j] = false;
        }
        for (Quad quad : shadow) {
            quad.alpha(0);
        }

        crowns.add(new Crown(new Vector3f(), crowded ? 6 : 10, 2.4f, 6.5f));
        launchTerrain(origin, crowded ? 12 : 22, 0.25, 0.55, 0.6, 1.05);
        ripples.add(new Ripple(origin.getX(), origin.getZ(), surfaceY(origin), t, 0.62, 0.95, 15.0, 1.6));
        ripples.add(new Ripple(origin.getX(), origin.getZ(), surfaceY(origin), t + 7, 0.62, 0.6, 15.0, 1.4));
        ripples.add(new Ripple(origin.getX(), origin.getZ(), surfaceY(origin), t + 14, 0.62, 0.38, 14.0, 1.3));
        splashBurst(origin, 1.0);

        world.playSound(origin, Sound.ENTITY_GENERIC_SPLASH, 2.4f, 0.5f);
        world.playSound(origin, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2.4f, 0.5f);
        world.playSound(origin, Sound.AMBIENT_UNDERWATER_EXIT, 2.0f, 0.6f);
        world.playSound(origin, Sound.ENTITY_DOLPHIN_SPLASH, 1.6f, 0.5f);
        world.playSound(origin, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.8f, 0.5f);
        world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.55f);
        world.playSound(origin, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.6f, 0.5f);
        world.playSound(origin, Sound.ITEM_GOAT_HORN_SOUND_6, 2.4f, 0.5f);
        world.playSound(origin, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.8f, 0.5f);
        caster.playHurtAnimation(0f);
        caster.sendActionBar(Component.text("§3§l✦ LEVIATHAN ✦"));

        for (LivingEntity enemy : enemiesNear(origin, 5.5, 6.0)) {
            if (damage > 0) {
                double amount = damage * 0.7;
                ScriptedHits.run(() -> enemy.damage(amount, caster));
            }
            Vector away = enemy.getLocation().toVector().subtract(origin.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(fwd.x, 0, fwd.z);
            }
            enemy.setVelocity(away.normalize().multiply(0.9).setY(1.6));
            enemy.setFallDistance(0f);
            struck.add(enemy.getUniqueId());
        }
    }

    private void flight() {
        flightAge++;
        headS += speed(headS);
        if (caster.isOnline() && !caster.isDead()) {
            casterEye = rel(caster.getEyeLocation());
        }

        boolean inHang = headS > sApex - 1.4 && headS < sApex + 1.8;
        lookWeight += ((inHang ? 1f : 0f) - lookWeight) * 0.16f;
        solveBody();
        poseBody();
        crossings();
        if (flightAge % 2 == 0) {
            contact();
        }
        feedRibbons();

        if (!apexHushed && headS > sApex - 1.2) {
            apexHushed = true;
            for (Player near : world.getPlayers()) {
                if (near.getLocation().distanceSquared(origin) < 96 * 96) {
                    near.stopAllSounds();
                }
            }
            caster.sendActionBar(Component.text("§f§o…"));
        }
        if (apexHushed && !glinted && lookWeight > 0.8f) {
            glinted = true;
            Location eye = abs(eyeWorld(1));
            world.spawnParticle(Particle.FLASH, eye, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.END_ROD, eye, 8, 0.1, 0.1, 0.1, 0.04);
            world.playSound(eye, Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, 3.0f, 0.85f);
            world.playSound(eye, Sound.ITEM_GOAT_HORN_SOUND_7, 1.6f, 0.5f);
            world.playSound(eye, Sound.BLOCK_CONDUIT_AMBIENT, 1.2f, 0.5f);
            world.playSound(eye, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.5f);
        }
        if (!accelCalled && headS > sApex + 1.4) {
            accelCalled = true;
            Location head = abs(joints[0]);
            world.playSound(head, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.8f, 0.5f);
            world.playSound(head, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, 0.5f);
            world.playSound(head, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.2f, 0.6f);
        }
        if (flightAge == 1 || flightAge == 12) {
            world.playSound(abs(joints[0]), Sound.ITEM_TRIDENT_RIPTIDE_1, 1.4f, 0.5f);
        }
        if (!dived && headS >= sD) {
            dived = true;
            diveImpact();
        }
        double tailS = headS - (NJ - 1) * SP;
        if (tailS >= sD + 2.5 || flightAge > FLIGHT_CAP) {
            endFlight();
        }
    }

    private double speed(double s) {
        double b = smoothstep(sApex + 1.0, sApex + 10.0, s);
        if (b > 0) {
            return 0.16 + (1.3 - 0.16) * b;
        }
        double a = smoothstep(sB + 8.0, sApex - 0.6, s);
        return 1.0 + (0.16 - 1.0) * a;
    }

    private void solveBody() {
        for (int j = 0; j < NJ; j++) {
            joints[j] = path.pos(headS - j * SP);
        }
        applyLook();
        for (int j = 0; j < NSEG; j++) {
            Vector3f a = joints[j];
            Vector3f b = joints[j + 1];
            Vector3f forward = new Vector3f(a).sub(b);
            if (forward.lengthSquared() < 1.0E-6f) {
                forward.set(fwd);
            }
            forward.normalize();
            Vector3f up = new Vector3f(side0).cross(forward);
            if (up.lengthSquared() < 1.0E-6f) {
                up.set(0, 1, 0);
            }
            up.normalize();
            float roll = roll(headS - (j + 0.5) * SP);
            Vector3f across = new Vector3f(up).cross(forward).normalize();
            Vector3f dorsalUp = new Vector3f(up).mul((float) Math.cos(roll))
                    .add(new Vector3f(across).mul((float) Math.sin(roll))).normalize();
            Vector3f x = new Vector3f(dorsalUp).cross(forward).normalize();
            segT[j] = forward;
            segU[j] = dorsalUp;
            segX[j] = x;
            segQ[j] = new Quaternionf().setFromNormalized(new Matrix3f(x, dorsalUp, forward));
            segMid[j] = new Vector3f(a).add(b).mul(0.5f);
            segLen[j] = a.distance(b);
            segVisible[j] = segMid[j].y > floorRelAlong(segMid[j]) - 1.3f;
        }
    }

    /** The breach twist: a slow roll that peaks mid-flight and rights itself for the dive. */
    private float roll(double s) {
        double k = (s - sB) / Math.max(1.0, sD - sB);
        if (k <= 0 || k >= 1) {
            return 0f;
        }
        return (float) (1.05 * Math.sin(Math.PI * k));
    }

    /** At the apex the head bends toward the caster's eye. */
    private void applyLook() {
        if (lookWeight < 0.01f) {
            return;
        }
        Vector3f pivot = new Vector3f(joints[4]);
        Vector3f headDir = new Vector3f(joints[0]).sub(pivot);
        Vector3f toEye = new Vector3f(casterEye).sub(pivot);
        if (headDir.lengthSquared() < 1.0E-4f || toEye.lengthSquared() < 1.0E-4f) {
            return;
        }
        Quaternionf full = new Quaternionf().rotationTo(headDir.normalize(), toEye.normalize());
        float angle = 2f * (float) Math.acos(Math.min(1f, Math.abs(full.w)));
        float limit = (float) Math.toRadians(42);
        float scale = angle > limit ? limit / angle : 1f;
        for (int j = 0; j < 4; j++) {
            float w = lookWeight * scale * (4 - j) / 4f;
            Quaternionf q = new Quaternionf().slerp(full, w);
            Vector3f offset = new Vector3f(joints[j]).sub(pivot);
            q.transform(offset);
            joints[j] = offset.add(pivot);
        }
    }

    private void poseBody() {
        if (!whaleAlive) {
            return;
        }
        for (int j = 0; j < NSEG; j++) {
            double u = (j + 0.5) / NSEG;
            float r = radius(u);
            float w = u < 0.18 ? 1.15f : 1.0f;
            float h = u < 0.18 ? 0.86f : 1.0f;
            boolean vis = segVisible[j];
            float len = segLen[j] * 1.3f + 0.1f;
            Quaternionf q = segQ[j];
            Vector3f mid = segMid[j];
            place(core[j], vis, mid, centered(mid, q, new Vector3f(2 * r * w, 2 * r * h, len)));
            float d = 1.56f * r * (w + h) / 2f;
            place(diamond[j], vis, mid, centered(mid, new Quaternionf(q).rotateZ((float) (Math.PI / 4)),
                    new Vector3f(d, d, len * 0.98f)));
            if (belly[j] != null) {
                /* Pale underside, deep enough to swallow the black keel of the rotated box. */
                float thick = 0.56f * r * h;
                float bottom = bellyBottom(r, w, h);
                Vector3f c = new Vector3f(mid).sub(new Vector3f(segU[j]).mul(bottom - thick / 2f));
                place(belly[j], vis, c, centered(c, q, new Vector3f(1.3f * r * w, thick, len * 1.02f)));
            }
        }

        for (int i = 0; i < PLEAT_SEGS; i++) {
            int j = 1 + i;
            double u = (j + 0.5) / NSEG;
            float r = radius(u);
            float h = u < 0.18 ? 0.86f : 1.0f;
            for (int s = 0; s < 2; s++) {
                float sign = s == 0 ? -1f : 1f;
                float w = u < 0.18 ? 1.15f : 1.0f;
                Vector3f c = new Vector3f(segMid[j])
                        .sub(new Vector3f(segU[j]).mul(bellyBottom(r, w, h) + 0.01f))
                        .add(new Vector3f(segX[j]).mul(sign * 0.28f * r));
                place(pleats[i * 2 + s], segVisible[j], c,
                        centered(c, segQ[j], new Vector3f(0.09f, 0.05f, segLen[j] * 1.05f + 0.05f)));
            }
        }

        /* Head: rostrum chamfer, eyes, mouth line, tubercles. */
        float r0 = radius(0.02);
        Vector3f nose = new Vector3f(joints[0]).add(new Vector3f(segT[0]).mul(0.2f));
        place(rostrum, segVisible[0], nose, centered(nose, new Quaternionf(segQ[0]).rotateX((float) (Math.PI / 4)),
                new Vector3f(2 * r0 * 1.15f * 0.92f, 1.25f, 1.25f)));

        float r3 = radius(3.5 / NSEG);
        float glint = glinted && lookWeight > 0.3f ? 1.35f : 1f;
        for (int s = 0; s < 2; s++) {
            float sign = s == 0 ? -1f : 1f;
            Vector3f socket = eyeWorld(s == 0 ? -1 : 1, 0.02f);
            Vector3f iris = eyeWorld(s == 0 ? -1 : 1, 0.13f);
            place(eyes[s * 2], segVisible[3], socket, centered(socket, segQ[3], new Vector3f(0.22f, 0.52f, 0.66f)));
            place(eyes[s * 2 + 1], segVisible[3], iris,
                    centered(iris, segQ[3], new Vector3f(0.14f, 0.3f * glint, 0.32f * glint)));

            Vector3f a = new Vector3f(joints[0]).add(new Vector3f(segX[0]).mul(sign * 0.92f * r0 * 1.15f))
                    .sub(new Vector3f(segU[0]).mul(0.35f * r0));
            Vector3f b = new Vector3f(segMid[3]).add(new Vector3f(segX[3]).mul(sign * (r3 * 1.15f + 0.01f)))
                    .sub(new Vector3f(segU[3]).mul(0.5f * r3 * 0.86f));
            Vector3f mc = new Vector3f(a).add(b).mul(0.5f);
            place(mouth[s], segVisible[0] && segVisible[3], mc, rod(a, b, segU[2], 0.1f, 0.1f, 0.1f));
        }
        for (int k = 0; k < knobs.length; k++) {
            int j = k / 2;
            float sign = k % 2 == 0 ? -1f : 1f;
            float r = radius((j + 0.5) / NSEG);
            Vector3f c = new Vector3f(segMid[j])
                    .add(new Vector3f(segU[j]).mul(r * 0.86f * 0.98f + 0.08f))
                    .add(new Vector3f(segX[j]).mul(sign * 0.32f * r))
                    .add(new Vector3f(segT[j]).mul((k % 2) * 0.3f - 0.15f));
            place(knobs[k], segVisible[j], c, centered(c, segQ[j], new Vector3f(0.34f, 0.24f, 0.34f)));
        }

        /* Pectoral fins: long, pale, beating slowly with a lagging outer half. */
        int fj = 5;
        float r5 = radius((fj + 0.5) / NSEG);
        double phase = flightAge * 0.16;
        float beat = 0.38f * (float) Math.sin(phase);
        float lag = 0.38f * (float) Math.sin(phase - 0.9);
        int flap = (int) Math.floor((phase + Math.PI / 2) / (Math.PI * 2));
        if (flap != lastFlap && segVisible[fj]) {
            lastFlap = flap;
            world.playSound(abs(segMid[fj]), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.45f);
        }
        for (int s = 0; s < 2; s++) {
            float sign = s == 0 ? -1f : 1f;
            Vector3f root = new Vector3f(segMid[fj]).add(new Vector3f(segX[fj]).mul(sign * 0.7f * r5))
                    .sub(new Vector3f(segU[fj]).mul(0.55f * r5));
            Vector3f base = new Vector3f(segX[fj]).mul(sign).sub(new Vector3f(segT[fj]).mul(0.55f))
                    .sub(new Vector3f(segU[fj]).mul(0.3f)).normalize();
            Vector3f inner = new Quaternionf().rotateAxis(sign * beat, segT[fj]).transform(new Vector3f(base));
            Vector3f outer = new Quaternionf().rotateAxis(sign * (beat + 0.45f * lag), segT[fj]).transform(new Vector3f(base));
            Vector3f knee = new Vector3f(root).add(new Vector3f(inner).mul(3.2f));
            Vector3f tip = new Vector3f(knee).add(new Vector3f(outer).mul(3.6f));
            boolean vis = segVisible[fj] && knee.y > floorRelAlong(knee) - 1.0f;
            Vector3f ci = new Vector3f(root).add(knee).mul(0.5f);
            Vector3f co = new Vector3f(knee).add(tip).mul(0.5f);
            place(fins[s * 2], vis, ci, rod(root, knee, segT[fj], 1.8f, 0.24f, 0.2f));
            place(fins[s * 2 + 1], vis, co, rod(knee, tip, segT[fj], 1.2f, 0.2f, 0.1f));
            finTips[s] = tip;
            finSides[s] = new Vector3f(outer);
        }

        int dj = 15;
        float r15 = radius((dj + 0.5) / NSEG);
        Vector3f dBase = new Vector3f(segMid[dj]).add(new Vector3f(segU[dj]).mul(r15 * 0.98f));
        Vector3f dTip = new Vector3f(dBase).add(new Vector3f(segU[dj]).mul(1.15f)).sub(new Vector3f(segT[dj]).mul(1.0f));
        place(dorsal, segVisible[dj], dBase, rod(dBase, dTip, segT[dj], 1.3f, 0.22f, 0.2f));

        /* Flukes: nine-block span, beating in pitch. */
        int tj = NSEG - 1;
        Vector3f tail = new Vector3f(joints[NJ - 1]).sub(new Vector3f(segT[tj]).mul(0.2f));
        float gamma = 0.3f * (float) Math.sin(flightAge * 0.28);
        float gammaLag = 0.3f * (float) Math.sin(flightAge * 0.28 - 0.8);
        boolean tailVis = tail.y > floorRelAlong(tail) - 1.2f;
        for (int s = 0; s < 2; s++) {
            float sign = s == 0 ? -1f : 1f;
            Vector3f span = new Vector3f(segX[tj]).mul(sign).sub(new Vector3f(segT[tj]).mul(0.5f)).normalize();
            Vector3f inner = new Quaternionf().rotateAxis(gamma, segX[tj]).transform(new Vector3f(span));
            Vector3f outer = new Quaternionf().rotateAxis(gamma + 0.5f * gammaLag, segX[tj]).transform(new Vector3f(span));
            Vector3f knee = new Vector3f(tail).add(new Vector3f(inner).mul(2.4f));
            Vector3f tip = new Vector3f(knee).add(new Vector3f(outer).mul(2.5f));
            place(flukes[s * 2], tailVis, tail, rod(tail, knee, segT[tj], 2.1f, 0.2f, 0.15f));
            place(flukes[s * 2 + 1], tailVis, knee, rod(knee, tip, segT[tj], 1.3f, 0.18f, 0.1f));
        }

        /* Bioluminescent lateral line and starlight along the back. */
        for (int i = 0; i < SPOTS; i++) {
            int j = 4 + i * 2;
            float r = radius((j + 0.5) / NSEG);
            float size = 0.24f + 0.12f * (float) Math.abs(Math.sin(t * 0.3 + i));
            for (int s = 0; s < 2; s++) {
                float sign = s == 0 ? -1f : 1f;
                Vector3f c = new Vector3f(segMid[j]).add(new Vector3f(segX[j]).mul(sign * (r + 0.04f)))
                        .sub(new Vector3f(segU[j]).mul(0.15f * r));
                place(spots[i * 2 + s], segVisible[j], c, centered(c, segQ[j], new Vector3f(size, size, size)));
            }
        }
        for (int i = 0; i < STARS; i++) {
            int j = 1 + i;
            double u = (j + 0.5) / NSEG;
            float r = radius(u);
            float h = u < 0.18 ? 0.86f : 1.0f;
            float xOff = (((i * 7) % 5) - 2) * 0.22f * r;
            float lift = Math.max(r * h, 1.103f * r - Math.abs(xOff)) + 0.05f;
            float size = 0.12f + 0.11f * (float) Math.abs(Math.sin(t * 0.45 + i * 1.7));
            Vector3f c = new Vector3f(segMid[j]).add(new Vector3f(segX[j]).mul(xOff)).add(new Vector3f(segU[j]).mul(lift));
            place(stars[i], segVisible[j], c, centered(c, segQ[j], new Vector3f(size, size, size)));
        }
    }

    private static float bellyBottom(float r, float w, float h) {
        float keel = 1.56f * r * (w + h) / 2f * 0.7071f + 0.03f;
        return Math.max(1.13f * r * h, keel);
    }

    private final Vector3f[] finTips = {new Vector3f(), new Vector3f()};
    private final Vector3f[] finSides = {new Vector3f(1, 0, 0), new Vector3f(1, 0, 0)};

    private Vector3f eyeWorld(int side) {
        return eyeWorld(side, 0.13f);
    }

    private Vector3f eyeWorld(int side, float out) {
        float r3 = radius(3.5 / NSEG);
        return new Vector3f(segMid[3])
                .add(new Vector3f(segX[3]).mul(side * (r3 * 1.15f + out)))
                .add(new Vector3f(segU[3]).mul(0.1f * r3))
                .sub(new Vector3f(segT[3]).mul(0.2f));
    }

    /** Body segments breaking the surface throw spray — the earth behaving like water. */
    private void crossings() {
        for (int j = 0; j < NJ; j++) {
            Vector3f p = joints[j];
            boolean up = p.y > floorRelAlong(p) + 0.1f;
            if (up != above[j]) {
                above[j] = up;
                float r = radius(j / (float) (NJ - 1));
                Location at = abs(new Vector3f(p.x, floorRelAlong(p), p.z)).add(0, 0.2, 0);
                BlockData data = surfaceData(at);
                world.spawnParticle(Particle.SPLASH, at, (int) (10 + r * 8), r * 0.5, 0.2, r * 0.5, 0.2);
                world.spawnParticle(Particle.BLOCK, at, (int) (4 + r * 3), r * 0.5, 0.2, r * 0.5, 0.1, data);
                world.spawnParticle(Particle.FALLING_WATER, at.clone().add(0, 1.0, 0), 4, r * 0.4, 0.5, r * 0.4, 0);
                if (j % 3 == 0) {
                    world.spawnParticle(Particle.CLOUD, at, 2, r * 0.3, 0.1, r * 0.3, 0.02);
                }
                if (t - lastSplash >= 2) {
                    lastSplash = t;
                    world.playSound(at, up ? Sound.ENTITY_PLAYER_SPLASH : Sound.ENTITY_GENERIC_SPLASH,
                            0.9f, 0.55f + ThreadLocalRandom.current().nextFloat() * 0.2f);
                }
                if (j % 5 == 0) {
                    ripples.add(new Ripple(at.getX(), at.getZ(), surfaceY(at), t, 0.7, 0.28, 6.0, 1.2));
                }
            }
        }
    }

    private void contact() {
        if (damage <= 0) {
            return;
        }
        Location mid = abs(joints[NJ / 2]);
        for (Entity entity : world.getNearbyEntities(mid, 18, 18, 18)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity) || struck.contains(entity.getUniqueId())) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            Vector3f p = rel(living.getLocation()).add(0, (float) (living.getHeight() * 0.5), 0);
            for (int j = 0; j < NJ; j += 2) {
                if (!above[j]) {
                    continue;
                }
                float reach = radius(j / (float) (NJ - 1)) + 1.3f;
                if (p.distanceSquared(joints[j]) < reach * reach) {
                    struck.add(living.getUniqueId());
                    double amount = damage * 0.5;
                    ScriptedHits.run(() -> living.damage(amount, caster));
                    Vector3f push = segT[Math.min(j, NSEG - 1)];
                    living.setVelocity(new Vector(push.x * 1.3, push.y * 1.3 + 0.7, push.z * 1.3));
                    living.setFallDistance(0f);
                    world.playSound(living.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.5f);
                    break;
                }
            }
        }
    }

    private void diveImpact() {
        crowns.add(new Crown(rel(diveAt), crowded ? 6 : 12, 3.0f, 8.5f));
        launchTerrain(diveAt, crowded ? 14 : 28, 0.3, 0.65, 0.65, 1.2);
        ripples.add(new Ripple(diveAt.getX(), diveAt.getZ(), surfaceY(diveAt), t, 0.62, 1.0, 16.0, 1.7));
        ripples.add(new Ripple(diveAt.getX(), diveAt.getZ(), surfaceY(diveAt), t + 7, 0.62, 0.7, 16.0, 1.5));
        ripples.add(new Ripple(diveAt.getX(), diveAt.getZ(), surfaceY(diveAt), t + 14, 0.62, 0.45, 15.0, 1.3));
        splashBurst(diveAt, 1.4);

        world.playSound(diveAt, Sound.ENTITY_GENERIC_SPLASH, 2.6f, 0.45f);
        world.playSound(diveAt, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2.6f, 0.45f);
        world.playSound(diveAt, Sound.AMBIENT_UNDERWATER_ENTER, 2.2f, 0.5f);
        world.playSound(diveAt, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.45f);
        world.playSound(diveAt, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.5f);
        world.playSound(diveAt, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.5f);
        world.playSound(diveAt, Sound.BLOCK_MUD_BREAK, 1.6f, 0.5f);
        world.playSound(diveAt, Sound.ENTITY_HOSTILE_SPLASH, 1.8f, 0.5f);

        for (LivingEntity enemy : enemiesNear(diveAt, 8.0, 6.0)) {
            if (damage > 0) {
                double amount = damage * 1.1;
                ScriptedHits.run(() -> enemy.damage(amount, caster));
            }
            Vector away = enemy.getLocation().toVector().subtract(diveAt.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(fwd.x, 0, fwd.z);
            }
            enemy.setVelocity(away.normalize().multiply(1.3).setY(1.0));
            enemy.setFallDistance(0f);
        }
    }

    private void endFlight() {
        removeWhale();
        phase = Phase.HUSH;
        phaseStart = t;
        for (Player near : world.getPlayers()) {
            if (near.getLocation().distanceSquared(origin) < 96 * 96) {
                near.stopAllSounds();
            }
        }
        world.playSound(diveAt, Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS, 1.2f, 0.6f);
        caster.sendActionBar(Component.text("§8…the stone sea is still."));
    }

    private void removeWhale() {
        whaleAlive = false;
        discardAll(core);
        discardAll(diamond);
        discardAll(belly);
        discardAll(pleats);
        discardAll(eyes);
        discardAll(mouth);
        discardAll(knobs);
        discardAll(fins);
        discardAll(flukes);
        discardAll(spots);
        discardAll(stars);
        discard(rostrum);
        discard(dorsal);
        rostrum = null;
        dorsal = null;
    }

    // ------------------------------------------------------------------ act III — hush, heartbeat, the breath

    private void hush() {
        int age = t - phaseStart;
        /* Its shadow drifts past once more — deeper now. */
        if (!shadow.isEmpty()) {
            Vector3f from = rel(diveAt);
            Vector3f to = rel(feet);
            Vector3f heading = new Vector3f(to).sub(from);
            heading.y = 0;
            if (heading.lengthSquared() < 1.0E-4f) {
                heading.set(fwd).negate();
            }
            heading.normalize();
            Vector3f center = new Vector3f(from).add(new Vector3f(heading).mul(age * 0.35f));
            center.y = floorRel(center);
            double vis = age < 6 ? age / 6.0 * 0.6 : Math.max(0.0, 0.6 * (1.0 - (age - 6) / 12.0));
            poseShadow(center, heading, 0.75f, vis);
            if (vis > 0.1) {
                wake(abs(center), heading, 7.0, 2.6, 0.2 * vis);
            }
        }
        if (age == 6) {
            world.playSound(diveAt, Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE, 1.2f, 0.5f);
        }
        if (age >= HUSH) {
            for (Quad quad : shadow) {
                quad.alpha(0);
            }
            phase = Phase.THUMP;
            phaseStart = t;
        }
    }

    private void thump() {
        int age = t - phaseStart;
        if (age == 0 || age == 5) {
            float strength = age == 0 ? 1.0f : 1.4f;
            world.playSound(diveAt, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f * strength, 0.5f);
            world.playSound(diveAt, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f * strength, 0.5f);
            world.playSound(diveAt, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f * strength, 0.5f);
            ripples.add(new Ripple(diveAt.getX(), diveAt.getZ(), surfaceY(diveAt), t, 0.8, 0.35 * strength, 9.0, 1.4));
            world.spawnParticle(Particle.BUBBLE_POP, diveAt.clone().add(0, 0.3, 0), 20, 2.0, 0.1, 2.0, 0.05);
        }
        if (age >= 1 && age <= THUMP) {
            disk(diveAt, 3.2, 0.25 + 0.35 * Math.max(0, Math.sin(age * Math.PI / 5.0)) + (age > 5 ? 0.3 : 0));
        }
        if (age >= THUMP) {
            phase = Phase.SPOUT;
            phaseStart = t;
            dawnStart = t + DAWN_DELAY;
            erupt();
        }
    }

    private void erupt() {
        Vector3f base = rel(diveAt);
        BlockData snow = Material.SNOW_BLOCK.createBlockData();
        for (int k = 0; k < DRUMS; k++) {
            drums[k] = spawnBlock(snow, null, 2.0f);
            if (drums[k] != null) {
                ease(drums[k], tinyAt(base.x, base.y - 2f, base.z), 0);
            }
        }
        for (int k = 0; k < (crowded ? 2 : 4); k++) {
            mist.add(new Quad(SPRAY));
        }
        for (int k = 0; k < (crowded ? 6 : FRONDS); k++) {
            fronds.add(new Quad(SPRAY));
        }
        launchTerrain(diveAt, crowded ? 8 : 16, 0.35, 0.75, 0.4, 0.8);
        ripples.add(new Ripple(diveAt.getX(), diveAt.getZ(), surfaceY(diveAt), t, 0.75, 1.1, 16.0, 1.7));
        splashBurst(diveAt, 1.2);

        world.playSound(diveAt, Sound.ENTITY_BREEZE_WIND_BURST, 2.4f, 0.5f);
        world.playSound(diveAt, Sound.ENTITY_BREEZE_WIND_BURST, 2.0f, 0.7f);
        world.playSound(diveAt, Sound.ITEM_TRIDENT_RIPTIDE_3, 2.2f, 0.5f);
        world.playSound(diveAt, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 2.0f, 0.6f);
        world.playSound(diveAt, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
        world.playSound(diveAt, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2.4f, 0.5f);
        world.playSound(diveAt, Sound.BLOCK_FIRE_EXTINGUISH, 1.6f, 0.5f);
        world.playSound(diveAt, Sound.ITEM_BUCKET_EMPTY, 1.6f, 0.5f);
        caster.playHurtAnimation(0f);
        caster.sendActionBar(Component.text("§b§l✦ IT BREATHES ✦"));
        caster.sendMessage("§3✦ §bThe Leviathan exhales.");

        for (LivingEntity enemy : enemiesNear(diveAt, 10.0, 8.0)) {
            if (damage > 0) {
                double amount = damage * 1.8;
                ScriptedHits.run(() -> enemy.damage(amount, caster));
            }
            Vector away = enemy.getLocation().toVector().subtract(diveAt.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(fwd.x, 0, fwd.z);
            }
            enemy.setVelocity(away.normalize().multiply(0.3).setY(2.7));
            enemy.setFallDistance(0f);
        }
    }

    private void spout() {
        int age = t - phaseStart;
        Vector3f base = rel(diveAt);
        double rise = age < 6 ? easeOutCubic(age / 6.0) : 1.0;
        double sink = age > 16 ? easeInCubic(Math.min(1.0, (age - 16) / 14.0)) : 0.0;
        float full = DRUMS * DRUM_H;
        float height = (float) (full * rise * (1.0 - sink));
        float top = base.y + height;

        for (int k = 0; k < DRUMS; k++) {
            BlockDisplay drum = drums[k];
            if (drum == null) {
                continue;
            }
            float cy = base.y + (k + 0.5f) * DRUM_H - (full - height);
            if (cy < base.y - DRUM_H * 0.6f) {
                ease(drum, tinyAt(base.x, base.y - 2f, base.z), 1);
                continue;
            }
            float width = 2.2f + 0.3f * (float) Math.sin(age * 0.9 + k * 0.8) + k * 0.06f;
            Quaternionf spin = new Quaternionf().rotateY(age * 0.28f + k * 0.39f);
            ease(drum, centered(new Vector3f(base.x, cy, base.z), spin, new Vector3f(width, DRUM_H * 1.04f, width)), 1);
        }

        double mistAlpha = 58 * rise * Math.max(0.0, 1.0 - Math.max(0, age - 18) / 12.0);
        for (int k = 0; k < mist.size(); k++) {
            Quad quad = mist.get(k);
            double a = age * 0.05 + Math.PI * k / mist.size();
            Vector3f across = new Vector3f((float) Math.cos(a), 0f, (float) Math.sin(a));
            float h = Math.max(0.1f, height + 2f);
            quad.pose(new Vector3f(base.x, base.y + h / 2f, base.z), basis(across, new Vector3f(0, 1, 0)), 5.2f, h, 1);
            quad.alpha((int) mistAlpha);
        }

        /* The crown: fronds of spray arcing out and falling like a fountain's canopy. */
        double frondK = Math.max(0.0, Math.min(1.0, (age - 4) / 24.0));
        double tilt = 0.65 - 1.95 * easeInOutSine(frondK);
        double frondAlpha = age < 4 ? 0 : 125 * Math.max(0.0, 1.0 - frondK * frondK);
        float crownY = base.y + (float) (full * rise) - (float) (6.0 * frondK);
        for (int k = 0; k < fronds.size(); k++) {
            Quad quad = fronds.get(k);
            double a = Math.PI * 2 * k / fronds.size() + age * 0.02;
            Vector3f radialDir = new Vector3f((float) Math.cos(a), 0f, (float) Math.sin(a));
            Vector3f tangent = new Vector3f((float) -Math.sin(a), 0f, (float) Math.cos(a));
            Vector3f dir = new Vector3f(radialDir).mul((float) Math.cos(tilt)).add(0f, (float) Math.sin(tilt), 0f);
            float len = 7.5f + (float) frondK * 2.5f;
            Vector3f root = new Vector3f(base.x, crownY, base.z).add(new Vector3f(radialDir).mul(0.9f + (float) frondK * 3f));
            Vector3f center = new Vector3f(root).add(new Vector3f(dir).mul(len / 2f));
            quad.pose(center, basis(tangent, dir), 2.4f, len, 1);
            quad.alpha((int) frondAlpha);
        }

        if (age == 5) {
            launchDroplets(abs(new Vector3f(base.x, top, base.z)), crowded ? 14 : 30);
            world.playSound(abs(new Vector3f(base.x, top, base.z)), Sound.WEATHER_RAIN_ABOVE, 1.6f, 0.8f);
        }
        if (age < 18 && age % 2 == 0) {
            Location crown = abs(new Vector3f(base.x, top, base.z));
            world.spawnParticle(Particle.CLOUD, crown, 12, 2.0, 1.0, 2.0, 0.08);
            world.spawnParticle(Particle.SPLASH, crown, 40, 3.0, 1.5, 3.0, 0.4);
            world.spawnParticle(Particle.CLOUD, abs(base).add(0, 0.5, 0), 6, 1.4, 0.2, 1.4, 0.05);
        }
        if (age > 6 && age < 32 && age % 2 == 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble(Math.PI * 2);
                double r = random.nextDouble(2.0, 9.0);
                Location drop = abs(base).add(Math.cos(a) * r, random.nextDouble(4.0, 20.0), Math.sin(a) * r);
                world.spawnParticle(Particle.FALLING_WATER, drop, 3, 0.5, 0.5, 0.5, 0);
            }
            world.spawnParticle(Particle.SNOWFLAKE, abs(base).add(0, 10, 0), 8, 6.0, 5.0, 6.0, 0.01);
        }
        if (age == 12) {
            world.playSound(diveAt, Sound.ITEM_GOAT_HORN_SOUND_7, 0.8f, 0.6f);
            world.playSound(diveAt, Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, 1.2f, 1.0f);
        }
        if (age == 20) {
            world.playSound(diveAt, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.0f, 0.5f);
        }
        if (age >= SPOUT) {
            discardAll(drums);
            for (Quad quad : mist) {
                quad.remove();
            }
            mist.clear();
            for (Quad quad : fronds) {
                quad.remove();
            }
            fronds.clear();
            caster.sendMessage("§8…and the night passes.");
            phase = Phase.DONE;
        }
    }

    // ------------------------------------------------------------------ the stone sea (terrain displacement)

    private void tickRipples() {
        Iterator<Ripple> it = ripples.iterator();
        while (it.hasNext()) {
            Ripple ripple = it.next();
            int age = t - ripple.born;
            if (age < 0) {
                continue;
            }
            double r = age * ripple.speed;
            if (r > ripple.maxR) {
                it.remove();
                continue;
            }
            double amp = ripple.amp * Math.pow(1.0 - r / ripple.maxR, 1.2);
            ring(ripple.x, ripple.z, ripple.nearY, r, ripple.width, amp);
        }
    }

    private void ring(double cx, double cz, int nearY, double r, double width, double amp) {
        if (amp < 0.06) {
            return;
        }
        double half = width / 2.0;
        int minX = (int) Math.floor(cx - r - half);
        int maxX = (int) Math.floor(cx + r + half);
        int minZ = (int) Math.floor(cz - r - half);
        int maxZ = (int) Math.floor(cz + r + half);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x + 0.5 - cx;
                double dz = z + 0.5 - cz;
                double off = (Math.sqrt(dx * dx + dz * dz) - r) / half;
                if (Math.abs(off) >= 1.0) {
                    continue;
                }
                addWant(x, z, nearY, amp * (1.0 - off * off));
            }
        }
    }

    private void disk(Location center, double radius, double amp) {
        if (amp < 0.06) {
            return;
        }
        int nearY = surfaceY(center);
        int minX = (int) Math.floor(center.getX() - radius);
        int maxX = (int) Math.floor(center.getX() + radius);
        int minZ = (int) Math.floor(center.getZ() - radius);
        int maxZ = (int) Math.floor(center.getZ() + radius);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x + 0.5 - center.getX();
                double dz = z + 0.5 - center.getZ();
                double f = 1.0 - (dx * dx + dz * dz) / (radius * radius);
                if (f <= 0) {
                    continue;
                }
                addWant(x, z, nearY, amp * f);
            }
        }
    }

    /** A travelling hump over something swimming beneath the floor. */
    private void wake(Location center, Vector3f heading, double length, double width, double amp) {
        if (amp < 0.06) {
            return;
        }
        int nearY = surfaceY(center);
        double hx = heading.x;
        double hz = heading.z;
        double reach = length / 2.0 + 1.0;
        int minX = (int) Math.floor(center.getX() - reach);
        int maxX = (int) Math.floor(center.getX() + reach);
        int minZ = (int) Math.floor(center.getZ() - reach);
        int maxZ = (int) Math.floor(center.getZ() + reach);
        double sway = 0.85 + 0.15 * Math.sin(t * 0.5);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x + 0.5 - center.getX();
                double dz = z + 0.5 - center.getZ();
                double along = (dx * hx + dz * hz) / (length / 2.0);
                double across = (-dx * hz + dz * hx) / (width / 2.0);
                double f = 1.0 - along * along - across * across;
                if (f <= 0) {
                    continue;
                }
                addWant(x, z, nearY, amp * f * sway);
            }
        }
    }

    private void addWant(int x, int z, int nearY, double lift) {
        long key = key(x, z);
        want.merge(key, lift, Math::max);
        wantY.putIfAbsent(key, nearY);
    }

    private void flushGround() {
        for (Map.Entry<Long, Double> entry : want.entrySet()) {
            double lift = entry.getValue();
            if (lift < 0.06) {
                continue;
            }
            long key = entry.getKey();
            GroundTile tile = ground.get(key);
            if (tile == null) {
                if (ground.size() >= groundCap) {
                    continue;
                }
                int x = (int) (key >> 32);
                int z = (int) key;
                Surface surface = surface(x, z, wantY.getOrDefault(key, origin.getBlockY() - 1));
                if (surface == null) {
                    continue;
                }
                BlockDisplay display = spawnTile(surface);
                if (display == null) {
                    continue;
                }
                tile = new GroundTile(display);
                ground.put(key, tile);
            }
            tile.target = (float) Math.min(1.6, lift);
            tile.idle = 0;
        }
        Iterator<Map.Entry<Long, GroundTile>> it = ground.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, GroundTile> entry = it.next();
            GroundTile tile = entry.getValue();
            Double lift = want.get(entry.getKey());
            if (lift == null || lift < 0.06) {
                tile.target = 0f;
                tile.idle++;
            }
            if (Math.abs(tile.shown - tile.target) > 0.01f) {
                ease(tile.display, tileAt(tile.target), 2);
                tile.shown = tile.target;
            }
            if (tile.idle >= 3 || tile.display == null || !tile.display.isValid()) {
                discard(tile.display);
                it.remove();
            }
        }
        want.clear();
        wantY.clear();
    }

    private static Transformation tileAt(float lift) {
        return new Transformation(new Vector3f(-0.0015f, lift - 0.0015f, -0.0015f), new Quaternionf(),
                new Vector3f(1.003f, 1.003f, 1.003f), new Quaternionf());
    }

    private BlockDisplay spawnTile(Surface surface) {
        try {
            BlockDisplay display = world.spawn(new Location(world, surface.x, surface.y, surface.z), BlockDisplay.class,
                    spawned -> {
                        spawned.setBlock(surface.data);
                        spawned.setPersistent(false);
                        spawned.setBrightness(surface.light);
                        spawned.setInterpolationDuration(0);
                        spawned.setTransformation(tileAt(0f));
                    });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Top solid block of a column near {@code nearY}; cached. */
    private Surface surface(int x, int z, int nearY) {
        long key = key(x, z);
        Surface cached = surfaces.get(key);
        if (cached != null) {
            return cached.data == null ? null : cached;
        }
        Surface found = null;
        for (int y = nearY + 2; y >= nearY - 4; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isOccluding()) {
                continue;
            }
            Block up = block.getRelative(BlockFace.UP);
            if (up.getType().isOccluding()) {
                break;
            }
            found = new Surface(x, y, z, block.getBlockData(),
                    new Display.Brightness(up.getLightFromBlocks(), up.getLightFromSky()));
            break;
        }
        surfaces.put(key, found != null ? found : new Surface(x, 0, z, null, null));
        return found;
    }

    private int surfaceY(Location at) {
        return (int) Math.floor(at.getY() - 0.5);
    }

    private BlockData surfaceData(Location at) {
        Surface surface = surface(at.getBlockX(), at.getBlockZ(), surfaceY(at));
        return surface != null ? surface.data : Material.DIRT.createBlockData();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    // ------------------------------------------------------------------ fx: crowns, chunks, ribbons, sky

    private void splashBurst(Location at, double scale) {
        Location up = at.clone().add(0, 0.4, 0);
        BlockData data = surfaceData(at);
        world.spawnParticle(Particle.SPLASH, up, (int) (220 * scale), 2.4 * scale, 0.6, 2.4 * scale, 0.5);
        world.spawnParticle(Particle.FALLING_WATER, up.clone().add(0, 3, 0), (int) (50 * scale), 2.5 * scale, 2.0, 2.5 * scale, 0);
        world.spawnParticle(Particle.CLOUD, up, (int) (30 * scale), 2.0 * scale, 0.5, 2.0 * scale, 0.08);
        world.spawnParticle(Particle.BLOCK, up, (int) (70 * scale), 2.4 * scale, 0.5, 2.4 * scale, 0.3, data);
        world.spawnParticle(Particle.BUBBLE_POP, up, (int) (30 * scale), 2.0 * scale, 0.5, 2.0 * scale, 0.1);
    }

    private void launchTerrain(Location center, int count, double hMin, double hMax, double vMin, double vMax) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<BlockData> palette = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Location probe = center.clone().add(random.nextDouble(-3, 3), 0, random.nextDouble(-3, 3));
            Surface surface = surface(probe.getBlockX(), probe.getBlockZ(), surfaceY(center));
            if (surface != null) {
                palette.add(surface.data);
            }
        }
        if (palette.isEmpty()) {
            palette.add(Material.DIRT.createBlockData());
            palette.add(Material.STONE.createBlockData());
        }
        double floor = center.getY();
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + random.nextDouble(0.25);
            Vector vel = new Vector(Math.cos(a), 0, Math.sin(a)).multiply(random.nextDouble(hMin, hMax))
                    .setY(random.nextDouble(vMin, vMax));
            Location at = center.clone().add(Math.cos(a) * 1.2, 0.3, Math.sin(a) * 1.2);
            BlockData data = palette.get(random.nextInt(palette.size()));
            chunks.add(new Chunk(at, vel, data, (float) random.nextDouble(0.35, 0.8), 0.055, floor, 40 + random.nextInt(14)));
        }
    }

    private void launchDroplets(Location top, int count) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Material[] spray = {Material.SNOW_BLOCK, Material.WHITE_CONCRETE_POWDER, Material.LIGHT_BLUE_STAINED_GLASS,
                Material.PACKED_ICE};
        double floor = diveAt.getY();
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble(Math.PI * 2);
            Vector vel = new Vector(Math.cos(a), 0, Math.sin(a)).multiply(random.nextDouble(0.3, 0.75))
                    .setY(random.nextDouble(0.1, 0.55));
            Material mat = spray[random.nextInt(spray.length)];
            chunks.add(new Chunk(top.clone(), vel, mat.createBlockData(), (float) random.nextDouble(0.3, 0.6),
                    0.045, floor, 60));
        }
    }

    private void tickChunks() {
        Iterator<Chunk> it = chunks.iterator();
        while (it.hasNext()) {
            Chunk chunk = it.next();
            if (!chunk.step()) {
                discard(chunk.display);
                it.remove();
            }
        }
    }

    private void tickCrowns() {
        Iterator<Crown> it = crowns.iterator();
        while (it.hasNext()) {
            Crown crown = it.next();
            if (!crown.tick()) {
                crown.remove();
                it.remove();
            }
        }
    }

    private void feedRibbons() {
        for (int s = 0; s < 2; s++) {
            Ribbon ribbon = ribbons[s];
            if (ribbon == null) {
                continue;
            }
            Vector3f tip = finTips[s];
            boolean visible = whaleAlive && tip.y > floorRelAlong(tip) + 0.3f;
            ribbon.feed(tip, finSides[s], visible);
        }
    }

    private void tickRibbons() {
        for (int s = 0; s < 2; s++) {
            Ribbon ribbon = ribbons[s];
            if (ribbon == null) {
                continue;
            }
            if (!whaleAlive && !ribbon.frozen && !ribbon.pts.isEmpty()) {
                ribbon.freeze();
            }
            if (!ribbon.render()) {
                ribbon.remove();
                ribbons[s] = null;
            }
        }
    }

    private void stageSky() {
        long tod = world.getTime();
        skyFrom = tod;
        skyNight = tod <= NIGHT ? NIGHT : NIGHT + 24000L;
        skyDawnEnd = tod + 24000L;
        if (skyDawnEnd <= skyNight) {
            skyDawnEnd = skyNight + 9000L;
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(origin) > 110 * 110) {
                continue;
            }
            if (!player.isPlayerTimeRelative() || player.getPlayerTimeOffset() != 0L) {
                continue;
            }
            if (!SKY_LOCKED.add(player.getUniqueId())) {
                continue;
            }
            skyViewers.add(player);
        }
    }

    /** Sunset in a second, a held night, then the night races into dawn. */
    private void tickSky() {
        if (skyReset || skyViewers.isEmpty()) {
            return;
        }
        long value;
        if (t <= T_SUNSET) {
            value = skyFrom + (long) ((skyNight - skyFrom) * easeInOutSine(t / (double) T_SUNSET));
        } else if (dawnStart < 0 || t < dawnStart) {
            value = skyNight + (t - T_SUNSET) * 3L;
        } else {
            double k = (t - dawnStart) / (double) DAWN;
            if (k >= 1.0) {
                resetSky();
                return;
            }
            long from = skyNight + (dawnStart - T_SUNSET) * 3L;
            value = from + (long) ((skyDawnEnd - from) * easeInOutSine(k));
        }
        Iterator<Player> it = skyViewers.iterator();
        while (it.hasNext()) {
            Player viewer = it.next();
            if (!viewer.isOnline() || viewer.getWorld() != world) {
                viewer.resetPlayerTime();
                SKY_LOCKED.remove(viewer.getUniqueId());
                it.remove();
                continue;
            }
            viewer.setPlayerTime(value, false);
        }
    }

    private void resetSky() {
        if (skyReset) {
            return;
        }
        skyReset = true;
        for (Player viewer : skyViewers) {
            if (viewer.isOnline()) {
                viewer.resetPlayerTime();
            }
            SKY_LOCKED.remove(viewer.getUniqueId());
        }
        skyViewers.clear();
    }

    // ------------------------------------------------------------------ path

    private void buildPath() {
        float s = (float) (diveX / DIVE_X_MAX);
        float ax = 7.0f * Math.max(0.75f, s);
        float dx = (float) diveX;
        float dy = diveDy;
        List<Vector3f> knots = new ArrayList<>();
        knots.add(local(-3.5f, -22f, 0f));
        knots.add(local(-1.4f, -9f, 0f));
        knots.add(local(0f, 0f, 0f));
        knots.add(local(1.0f, 7.5f, 0.4f));
        knots.add(local(3.2f, 14.5f, 1.1f));
        knots.add(local(ax, 19f + Math.max(0f, dy) * 0.5f, 1.5f));
        knots.add(local(ax + (dx - ax) * 0.34f, 17.4f + dy * 0.1f, 1.1f));
        knots.add(local(ax + (dx - ax) * 0.68f, 10.8f + dy * 0.45f, 0.4f));
        knots.add(local(dx, dy, 0f));
        knots.add(local(dx + 1.6f, dy - 8f, 0f));
        knots.add(local(dx + 2.6f, dy - 22f, 0f));
        path = new Path(knots);
        sB = path.knot(2);
        sApex = path.knot(5);
        sD = path.knot(8);
    }

    /** x along the caster's facing, y up, z to the side — relative to the breach point. */
    private Vector3f local(float x, float y, float z) {
        return new Vector3f(fwd).mul(x).add(0f, y, 0f).add(new Vector3f(side0).mul(z));
    }

    private static final class Path {
        private final float[] px;
        private final float[] py;
        private final float[] pz;
        private final float[] cum;
        private final int n;

        Path(List<Vector3f> knots) {
            List<Vector3f> out = new ArrayList<>();
            int m = knots.size();
            for (int i = 0; i < m - 1; i++) {
                Vector3f p1 = knots.get(i);
                Vector3f p2 = knots.get(i + 1);
                Vector3f p0 = i > 0 ? knots.get(i - 1) : new Vector3f(p1).mul(2f).sub(p2);
                Vector3f p3 = i + 2 < m ? knots.get(i + 2) : new Vector3f(p2).mul(2f).sub(p1);
                for (int k = 0; k < SUB; k++) {
                    out.add(catmull(p0, p1, p2, p3, k / (float) SUB));
                }
            }
            out.add(new Vector3f(knots.get(m - 1)));
            n = out.size();
            px = new float[n];
            py = new float[n];
            pz = new float[n];
            cum = new float[n];
            for (int i = 0; i < n; i++) {
                Vector3f p = out.get(i);
                px[i] = p.x;
                py[i] = p.y;
                pz[i] = p.z;
                if (i > 0) {
                    cum[i] = cum[i - 1] + p.distance(out.get(i - 1));
                }
            }
        }

        float knot(int k) {
            return cum[Math.min(n - 1, k * SUB)];
        }

        Vector3f pos(double s) {
            if (s <= 0) {
                Vector3f dir = new Vector3f(px[1] - px[0], py[1] - py[0], pz[1] - pz[0]).normalize();
                return new Vector3f(px[0], py[0], pz[0]).add(dir.mul((float) s));
            }
            float total = cum[n - 1];
            if (s >= total) {
                Vector3f dir = new Vector3f(px[n - 1] - px[n - 2], py[n - 1] - py[n - 2], pz[n - 1] - pz[n - 2]).normalize();
                return new Vector3f(px[n - 1], py[n - 1], pz[n - 1]).add(dir.mul((float) (s - total)));
            }
            int lo = 0;
            int hi = n - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (cum[mid] <= s) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            float seg = cum[hi] - cum[lo];
            float f = seg < 1.0E-6f ? 0f : (float) ((s - cum[lo]) / seg);
            return new Vector3f(px[lo] + (px[hi] - px[lo]) * f, py[lo] + (py[hi] - py[lo]) * f, pz[lo] + (pz[hi] - pz[lo]) * f);
        }

        private static Vector3f catmull(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float u) {
            float u2 = u * u;
            float u3 = u2 * u;
            return new Vector3f(
                    cr(p0.x, p1.x, p2.x, p3.x, u, u2, u3),
                    cr(p0.y, p1.y, p2.y, p3.y, u, u2, u3),
                    cr(p0.z, p1.z, p2.z, p3.z, u, u2, u3));
        }

        private static float cr(float a, float b, float c, float d, float u, float u2, float u3) {
            return 0.5f * (2 * b + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (-a + 3 * b - 3 * c + d) * u3);
        }
    }

    /** Humpback girth profile along the body, snout (0) to tail stock (1). */
    private static float radius(double u) {
        double[][] keys = {
                {0.0, 1.05}, {0.05, 1.6}, {0.12, 2.0}, {0.2, 2.3}, {0.3, 2.4},
                {0.45, 2.2}, {0.6, 1.7}, {0.75, 1.1}, {0.88, 0.65}, {1.0, 0.45}
        };
        double x = Math.max(0, Math.min(1, u));
        for (int i = 1; i < keys.length; i++) {
            if (x <= keys[i][0]) {
                double f = (x - keys[i - 1][0]) / (keys[i][0] - keys[i - 1][0]);
                return (float) (keys[i - 1][1] + (keys[i][1] - keys[i - 1][1]) * f);
            }
        }
        return (float) keys[keys.length - 1][1];
    }

    // ------------------------------------------------------------------ inner pieces

    private static final class Surface {
        final int x;
        final int y;
        final int z;
        final BlockData data;
        final Display.Brightness light;

        Surface(int x, int y, int z, BlockData data, Display.Brightness light) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
            this.light = light;
        }
    }

    private static final class GroundTile {
        final BlockDisplay display;
        float target;
        float shown;
        int idle;

        GroundTile(BlockDisplay display) {
            this.display = display;
        }
    }

    private static final class Ripple {
        final double x;
        final double z;
        final int nearY;
        final int born;
        final double speed;
        final double amp;
        final double maxR;
        final double width;

        Ripple(double x, double z, int nearY, int born, double speed, double amp, double maxR, double width) {
            this.x = x;
            this.z = z;
            this.nearY = nearY;
            this.born = born;
            this.speed = speed;
            this.amp = amp;
            this.maxR = maxR;
            this.width = width;
        }
    }

    /** Ballistic debris / spray. Sinks back into the "sea" when it falls below the floor. */
    private final class Chunk {
        final BlockDisplay display;
        final Location at;
        final Vector vel;
        final float size;
        final double gravity;
        final double floor;
        final int life;
        final Quaternionf rot = new Quaternionf();
        final float spinX;
        final float spinY;
        int age;

        Chunk(Location start, Vector vel, BlockData data, float size, double gravity, double floor, int life) {
            this.at = level(start);
            this.vel = vel;
            this.size = size;
            this.gravity = gravity;
            this.floor = floor;
            this.life = life;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            this.spinX = (float) random.nextDouble(-0.35, 0.35);
            this.spinY = (float) random.nextDouble(-0.3, 0.3);
            this.display = spawnBlockAt(at, data, null, centered(new Vector3f(), rot, new Vector3f(size, size, size)), 1.5f);
            if (display != null) {
                display.setTeleportDuration(1);
            }
        }

        boolean step() {
            age++;
            if (display == null || !display.isValid() || age > life) {
                return false;
            }
            at.add(vel);
            vel.setY(vel.getY() - gravity);
            vel.multiply(0.985);
            if (vel.getY() < 0 && at.getY() < floor - 0.25) {
                world.spawnParticle(Particle.SPLASH, at.clone().add(0, 0.4, 0), 6, 0.2, 0.05, 0.2, 0.1);
                return false;
            }
            rot.rotateXYZ(spinX, spinY, spinX * 0.4f);
            display.teleport(at);
            ease(display, centered(new Vector3f(), rot, new Vector3f(size, size, size)), 1);
            return true;
        }
    }

    /** A ring of spray sheets thrown up where the Leviathan breaks the surface. */
    private final class Crown {
        final Vector3f center;
        final List<Quad> sheets = new ArrayList<>();
        final float radius;
        final float height;
        int age;

        Crown(Vector3f center, int count, float radius, float height) {
            this.center = center;
            this.radius = radius;
            this.height = height;
            for (int i = 0; i < count; i++) {
                sheets.add(new Quad(SPRAY));
            }
        }

        boolean tick() {
            age++;
            if (age > 20) {
                return false;
            }
            double grow = age < 5 ? easeOutCubic(age / 5.0) : Math.max(0.0, 1.0 - (age - 5) / 15.0);
            float h = (float) (height * grow);
            int alpha = (int) (118 * Math.max(0.0, 1.0 - age / 20.0));
            for (int i = 0; i < sheets.size(); i++) {
                double a = Math.PI * 2 * i / sheets.size();
                Vector3f radial = new Vector3f((float) Math.cos(a), 0f, (float) Math.sin(a));
                Vector3f tangent = new Vector3f((float) -Math.sin(a), 0f, (float) Math.cos(a));
                Vector3f lean = new Vector3f(0f, (float) Math.cos(0.32), 0f).add(new Vector3f(radial).mul((float) Math.sin(0.32)));
                Vector3f foot = new Vector3f(center).add(new Vector3f(radial).mul(radius + age * 0.09f));
                Vector3f mid = new Vector3f(foot).add(new Vector3f(lean).mul(h / 2f));
                Quad sheet = sheets.get(i);
                sheet.pose(mid, basis(tangent, lean), (float) (2 * Math.PI * radius / sheets.size() * 1.15), Math.max(0.05f, h), 1);
                sheet.alpha(alpha);
            }
            return true;
        }

        void remove() {
            for (Quad sheet : sheets) {
                sheet.remove();
            }
            sheets.clear();
        }
    }

    /** An aurora ribbon painted by a fin tip through the night sky; it outlives the whale. */
    private final class Ribbon {
        final List<Vector3f> pts = new ArrayList<>();
        final List<Vector3f> sides = new ArrayList<>();
        final Quad[] quads = new Quad[RIBBON_K];
        final Vector3f liveTip = new Vector3f();
        final Vector3f liveSide = new Vector3f(1, 0, 0);
        boolean frozen;
        boolean dirty;
        int frozenAt = -1;

        Ribbon() {
            for (int i = 0; i < RIBBON_K; i++) {
                quads[i] = new Quad(i == 0 ? AURORA_A : mix(AURORA_A, AURORA_B, i / (double) (RIBBON_K - 1)));
            }
        }

        void feed(Vector3f tip, Vector3f side, boolean visible) {
            if (frozen) {
                return;
            }
            if (!visible) {
                if (!pts.isEmpty()) {
                    freeze();
                }
                return;
            }
            liveTip.set(tip);
            liveSide.set(side);
            if (pts.isEmpty() || pts.get(pts.size() - 1).distance(tip) >= RIBBON_STEP) {
                pts.add(new Vector3f(tip));
                sides.add(new Vector3f(side));
                while (pts.size() > RIBBON_K + 1) {
                    pts.remove(0);
                    sides.remove(0);
                }
                dirty = true;
            }
        }

        void freeze() {
            frozen = true;
            frozenAt = t;
            dirty = true;
        }

        /** @return false once fully faded. */
        boolean render() {
            if (pts.isEmpty()) {
                return true;
            }
            double fade = frozen ? Math.max(0.0, 1.0 - (t - frozenAt) / 46.0) : 1.0;
            if (fade <= 0.0) {
                return false;
            }
            int n = pts.size();
            for (int i = 0; i < RIBBON_K; i++) {
                Quad quad = quads[i];
                Vector3f a;
                Vector3f b;
                Vector3f side;
                if (i == 0) {
                    if (frozen) {
                        quad.alpha(0);
                        continue;
                    }
                    a = liveTip;
                    b = pts.get(n - 1);
                    side = liveSide;
                } else {
                    if (n - 1 - i < 0) {
                        quad.alpha(0);
                        continue;
                    }
                    a = pts.get(n - i);
                    b = pts.get(n - 1 - i);
                    side = sides.get(n - i);
                }
                float taper = (float) Math.pow(1.0 - i / (double) RIBBON_K, 0.7);
                if (i == 0 || dirty) {
                    Vector3f dir = new Vector3f(a).sub(b);
                    float len = dir.length();
                    if (len < 0.05f) {
                        quad.alpha(0);
                        continue;
                    }
                    Vector3f center = new Vector3f(a).add(b).mul(0.5f);
                    quad.pose(center, basis(side, dir), 1.25f * taper + 0.12f, len + 0.25f, i == 0 ? 1 : 0);
                }
                double shimmer = 0.85 + 0.15 * Math.sin((t / 2) * 0.8 + i * 0.7);
                quad.alpha((int) (150 * Math.pow(1.0 - i / (double) RIBBON_K, 1.3) * fade * shimmer));
            }
            dirty = false;
            return true;
        }

        void remove() {
            for (Quad quad : quads) {
                if (quad != null) {
                    quad.remove();
                }
            }
        }
    }

    /** Translucent colored plane: two text-display backgrounds back to back. */
    private final class Quad {
        final TextDisplay front;
        final TextDisplay back;
        final Color tint;
        int alpha = -1;

        Quad(Color tint) {
            this.tint = tint;
            this.front = spawnText(origin);
            this.back = spawnText(origin);
        }

        void pose(Vector3f center, Quaternionf rot, float w, float h, int ticks) {
            ease(front, quad(center, rot, w, h, false), ticks);
            ease(back, quad(center, rot, w, h, true), ticks);
        }

        void alpha(int value) {
            int a = Math.max(0, Math.min(255, value));
            if (a == alpha) {
                return;
            }
            alpha = a;
            Color color = a < 6 ? Color.fromARGB(0, 0, 0, 0) : Color.fromARGB(a, tint.getRed(), tint.getGreen(), tint.getBlue());
            if (front != null && front.isValid()) {
                front.setBackgroundColor(color);
            }
            if (back != null && back.isValid()) {
                back.setBackgroundColor(color);
            }
        }

        void remove() {
            discard(front);
            discard(back);
        }
    }

    // ------------------------------------------------------------------ helpers

    private List<LivingEntity> enemiesNear(Location center, double radius, double height) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, radius, height, radius)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            double dx = entity.getLocation().getX() - center.getX();
            double dz = entity.getLocation().getZ() - center.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                out.add((LivingEntity) entity);
            }
        }
        return out;
    }

    /** Surface height (O-relative) under an O-relative point, using the breach / dive floors. */
    private float floorRelAlong(Vector3f p) {
        float along = p.x * fwd.x + p.z * fwd.z;
        return along < diveX * 0.5 ? 0f : diveDy;
    }

    /** Actual sampled floor height (O-relative) at an O-relative point. */
    private float floorRel(Vector3f p) {
        Location at = abs(p);
        Surface surface = surface(at.getBlockX(), at.getBlockZ(), origin.getBlockY() - 1);
        if (surface == null) {
            return floorRelAlong(p);
        }
        return (float) (surface.y + 1 - origin.getY());
    }

    private Vector3f rel(Location at) {
        return new Vector3f((float) (at.getX() - origin.getX()), (float) (at.getY() - origin.getY()),
                (float) (at.getZ() - origin.getZ()));
    }

    private Location abs(Vector3f p) {
        return origin.clone().add(p.x, p.y, p.z);
    }

    private void place(BlockDisplay display, boolean visible, Vector3f center, Transformation shown) {
        if (display == null) {
            return;
        }
        if (visible) {
            hidden.remove(display);
            ease(display, shown, 2);
        } else if (hidden.add(display)) {
            ease(display, tinyAt(center.x, center.y, center.z), 1);
        }
    }

    private static Transformation centered(Vector3f center, Quaternionf rot, Vector3f size) {
        Quaternionf q = new Quaternionf(rot);
        Vector3f s = new Vector3f(Math.max(0.001f, size.x), Math.max(0.001f, size.y), Math.max(0.001f, size.z));
        Vector3f corner = new Vector3f(center).sub(new Quaternionf(q).transform(new Vector3f(s).mul(0.5f)));
        return new Transformation(corner, q, s, new Quaternionf());
    }

    private static Transformation rod(Vector3f a, Vector3f b, Vector3f xHint, float wx, float wz, float overlap) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1.0E-3f) {
            return tinyAt(a.x, a.y, a.z);
        }
        Vector3f center = new Vector3f(a).add(b).mul(0.5f);
        return centered(center, basis(xHint, d), new Vector3f(wx, len + overlap, wz));
    }

    private static Quaternionf basis(Vector3f xAxis, Vector3f yAxis) {
        Vector3f y = new Vector3f(yAxis).normalize();
        Vector3f x = new Vector3f(xAxis).sub(new Vector3f(y).mul(xAxis.dot(y)));
        if (x.lengthSquared() < 1.0E-6f) {
            x = Math.abs(y.y) < 0.9f ? new Vector3f(0, 1, 0).cross(y) : new Vector3f(1, 0, 0).cross(y);
        }
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y);
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    private static Quaternionf flat(Vector3f along) {
        Vector3f y = new Vector3f(along.x, 0f, along.z).normalize();
        Vector3f x = new Vector3f(y).cross(0f, 1f, 0f);
        return basis(x, y);
    }

    private static Transformation quad(Vector3f center, Quaternionf rot, float w, float h, boolean back) {
        Quaternionf q = back ? new Quaternionf(rot).rotateY((float) Math.PI) : new Quaternionf(rot);
        Vector3f shift = new Quaternionf(q).transform(new Vector3f(-0.1f * w, -0.5f * h, 0f));
        Vector3f translation = new Vector3f(center).add(shift);
        if (back) {
            translation.sub(new Quaternionf(rot).transform(new Vector3f(0f, 0f, 0.004f)));
        }
        return new Transformation(translation, q, new Vector3f(8f * Math.max(0.001f, w), 4f * Math.max(0.001f, h), 1f),
                new Quaternionf());
    }

    private static Transformation tinyAt(float x, float y, float z) {
        return new Transformation(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f),
                new Quaternionf());
    }

    private static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static double smoothstep(double e0, double e1, double x) {
        if (e1 <= e0) {
            return x >= e1 ? 1.0 : 0.0;
        }
        double k = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return k * k * (3 - 2 * k);
    }

    private static double easeOutCubic(double x) {
        x = Math.max(0, Math.min(1, x));
        return 1.0 - Math.pow(1.0 - x, 3);
    }

    private static double easeInCubic(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * x;
    }

    private static double easeInOutSine(double x) {
        x = Math.max(0, Math.min(1, x));
        return -(Math.cos(Math.PI * x) - 1) / 2;
    }

    private Location floorUnder(Location at) {
        RayTraceResult hit = world.rayTraceBlocks(at.clone().add(0, 0.6, 0), new Vector(0, -1, 0), 8.0,
                FluidCollisionMode.NEVER, true);
        Location out = hit != null && hit.getHitPosition() != null ? hit.getHitPosition().toLocation(world) : at.clone();
        return level(out);
    }

    private Location floorNear(Location at, double nearY) {
        Location from = at.clone();
        from.setY(nearY + 6.0);
        RayTraceResult hit = world.rayTraceBlocks(from, new Vector(0, -1, 0), 14.0, FluidCollisionMode.NEVER, true);
        Location out = at.clone();
        out.setY(hit != null && hit.getHitPosition() != null ? hit.getHitPosition().getY() : nearY);
        return level(out);
    }

    private static Location level(Location at) {
        Location out = at.clone();
        out.setYaw(0);
        out.setPitch(0);
        return out;
    }

    private static void ease(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private BlockDisplay spawnBlock(BlockData data, Color glow, float viewRange) {
        return spawnBlockAt(origin, data, glow, tinyAt(0, 0, 0), viewRange);
    }

    private BlockDisplay spawnBlockAt(Location at, BlockData data, Color glow, Transformation initial, float viewRange) {
        try {
            BlockDisplay display = world.spawn(level(at), BlockDisplay.class, spawned -> {
                spawned.setBlock(data);
                spawned.setPersistent(false);
                spawned.setBrightness(LIT);
                spawned.setTeleportDuration(2);
                spawned.setInterpolationDuration(0);
                spawned.setViewRange(viewRange);
                if (glow != null) {
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                }
                spawned.setTransformation(initial);
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private TextDisplay spawnText(Location at) {
        try {
            TextDisplay display = world.spawn(level(at), TextDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.text(Component.text(" "));
                spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                spawned.setDefaultBackground(false);
                spawned.setShadowed(false);
                spawned.setSeeThrough(false);
                spawned.setBillboard(Display.Billboard.FIXED);
                spawned.setBrightness(LIT);
                spawned.setLineWidth(200);
                spawned.setInterpolationDuration(0);
                spawned.setViewRange(2.0f);
                spawned.setTransformation(tinyAt(0, 0, 0));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void clearAll() {
        removeWhale();
        for (Quad quad : shadow) {
            quad.remove();
        }
        shadow.clear();
        for (int s = 0; s < ribbons.length; s++) {
            if (ribbons[s] != null) {
                ribbons[s].remove();
                ribbons[s] = null;
            }
        }
        for (Crown crown : crowns) {
            crown.remove();
        }
        crowns.clear();
        for (Chunk chunk : chunks) {
            discard(chunk.display);
        }
        chunks.clear();
        discardAll(drums);
        for (Quad quad : mist) {
            quad.remove();
        }
        mist.clear();
        for (Quad quad : fronds) {
            quad.remove();
        }
        fronds.clear();
        for (GroundTile tile : ground.values()) {
            discard(tile.display);
        }
        ground.clear();
        ripples.clear();
        resetSky();
        release();
    }

    private static void discardAll(BlockDisplay[] displays) {
        for (int i = 0; i < displays.length; i++) {
            discard(displays[i]);
            displays[i] = null;
        }
    }

    private static void discard(Display display) {
        if (display == null) {
            return;
        }
        LIVE.remove(display);
        if (display.isValid()) {
            display.remove();
        }
    }
}
