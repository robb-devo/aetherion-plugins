package de.aetherion.items.listener;

import de.aetherion.core.AetherKeys;
import de.aetherion.items.combat.ScriptedHits;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Terminus (terminus) — Here Ends the World.
 *
 * <p>You set a brass survey benchmark into the ground where you look, and a survey grid lays itself out from it,
 * a hundred blocks on a side. Then every sound stops, and fifty blocks out on all four sides — where nothing
 * should be — the edge of the world is standing in the sky: the real, vanilla world border, faint and blue, the
 * one that lives thirty million blocks away. Chat says an admin just set it. The action bar says how far away
 * the end of the world now is.
 *
 * <p>It turns red and comes. Slowly, then not slowly: four walls as tall as the world sweep in across the grid,
 * shoving every enemy in front of them, and pass straight through you — the edges of your screen go red,
 * because you are now outside the world. The grid compresses with the space it measures. The walls close on a
 * single block and crush everything they herded into one tower standing on the benchmark.
 *
 * <p>The world has to go somewhere. It squeezes out of that one block like paste from a tube: a twenty-nine
 * block core sample of the ground — soil, stone, a fossil, a geode, deepslate, bedrock and, at the very bottom
 * of the world, a command block — rams up the needle with the tower on top. Silence. The needle is one block
 * wide and still tightening.
 *
 * <p>Then the world lets go. The edge snaps back out, green, past everyone and on to the horizon; the core sample
 * drops back into the ground; the grid springs out and dissolves. The tower is still up there, standing on the
 * benchmark, on nothing. It looks at you. It looks down. It falls — and the benchmark sets itself back into the
 * ground where it started.
 *
 * <p>No blocks are touched. The edge is a per-player virtual border ({@link TerminusBorder}: everyone gets their
 * own back), the grid and the core are displays anchored on the benchmark and moved only by interpolated
 * transformations, and the tower rides the benchmark as passengers so it moves as one piece. The world has one
 * edge: only one Terminus runs at a time.
 */
public final class TerminusEdge {

    /* ---------------------------------------------------------------- timeline (ticks from the click) */
    private static final int T_LAND = 4;
    private static final int T_CROSS = 7;
    private static final int T_FAN = 12;
    private static final int T_HORIZON = 28;
    private static final int T_ADVANCE = 50;
    private static final int ADVANCE = 60;
    private static final int T_CRUSH = T_ADVANCE + ADVANCE;
    private static final int CRUSH = 4;
    private static final int T_EXTRUDE = T_CRUSH + CRUSH;
    private static final int EXTRUDE = 16;
    private static final int T_HOLD = T_EXTRUDE + EXTRUDE;
    private static final int HOLD = 16;
    private static final int T_RELEASE = T_HOLD + HOLD;
    private static final int GREEN_MS = 800;
    private static final int T_RESTORE = T_RELEASE + 15;
    private static final int T_LOOK = T_RELEASE + 7;
    private static final int T_LOOK_DOWN = T_RELEASE + 15;
    private static final int T_DROP = T_RELEASE + 23;
    private static final int FALL_CAP = 60;
    private static final int AFTER = 34;
    private static final int HARD_END = T_DROP + FALL_CAP + AFTER + 60;

    /* ---------------------------------------------------------------- staging */
    private static final double REACH = 15.0;
    private static final double MIN_REACH = 9.0;
    private static final double MAX_REACH = 26.0;
    private static final double START_SIZE = 100.0;
    private static final double BOX_SIZE = 7.0;
    private static final double NEEDLE_SIZE = 1.3;
    private static final double HOLD_SIZE = 1.15;
    private static final double GREEN_SIZE = 220.0;
    private static final double AUDIENCE = 72.0;
    private static final double CAPTURE = 36.0;
    private static final int TOTEM_CAP = 10;
    private static final double CRUSH_SHARE = 0.1;
    private static final int LINES = 11;
    private static final int HALF_LINES = LINES / 2;
    private static final float LINE_W = 0.06f;
    private static final float EDGE_W = 0.14f;
    private static final float LINE_Y = 0.02f;
    private static final int CORE = 29;
    /** The benchmark's entity point sits this far above its plate so riders stand on it, not in it. */
    private static final double LIFT = 0.4;
    private static final int MAX_DECALS = 14;

    /** The client's own border colours: BorderStatus STATIONARY, SHRINKING, GROWING. */
    private static final Color EDGE_BLUE = Color.fromRGB(0x20, 0xA0, 0xFF);
    private static final Color EDGE_RED = Color.fromRGB(0xFF, 0x30, 0x30);
    private static final Color EDGE_GREEN = Color.fromRGB(0x40, 0xFF, 0x80);
    private static final Color COMMAND = Color.fromRGB(255, 150, 50);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    /** A core sample of an ordinary world, top to bottom. The top few are replaced by the real ground. */
    private static final Material[] STRATA = {
            Material.GRASS_BLOCK, Material.DIRT, Material.DIRT, Material.STONE, Material.STONE,
            Material.COAL_ORE, Material.STONE, Material.BONE_BLOCK, Material.STONE, Material.IRON_ORE,
            Material.ANDESITE, Material.COPPER_ORE, Material.STONE, Material.TUFF, Material.DEEPSLATE,
            Material.DEEPSLATE_REDSTONE_ORE, Material.SMOOTH_BASALT, Material.CALCITE, Material.AMETHYST_BLOCK,
            Material.CALCITE, Material.SMOOTH_BASALT, Material.DEEPSLATE, Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE, Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE, Material.BEDROCK,
            Material.BEDROCK, Material.COMMAND_BLOCK
    };

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();
    private static volatile TerminusEdge active;

    private final JavaPlugin plugin;
    private final Player caster;
    private final World world;
    private final double damage;
    private final Runnable done;

    private Location anchor;
    private double cx;
    private double cz;
    private BlockData floorData;
    private TerminusBorder border;
    private final List<Player> audience = new ArrayList<>();
    private int t;
    private boolean released;
    private boolean cleared;

    /* the survey grid: lines parallel to X (spread along Z) and parallel to Z (spread along X) */
    private final BlockDisplay[] alongX = new BlockDisplay[LINES];
    private final BlockDisplay[] alongZ = new BlockDisplay[LINES];
    private final double[] fan = new double[LINES];
    private double gridLen;
    private float gridThick = 1f;

    /* the core sample and the benchmark */
    private final BlockDisplay[] core = new BlockDisplay[CORE];
    private final BlockData[] strata = new BlockData[CORE];
    private BlockDisplay plate;
    private double plateRel;
    private double plateVy;
    private int emerged;
    private int beepAt = -1;
    private int landedAt = -1;

    /* the tower */
    private final List<Captive> captives = new ArrayList<>();
    private final List<Captive> chain = new ArrayList<>();
    private final Set<UUID> taken = new HashSet<>();
    private int nextTopple;

    /* sound + screen */
    private int nextBeat = T_HORIZON + 8;
    private int pulsePeak;
    private int pulseAge = -1;

    private final List<Decal> decals = new ArrayList<>();

    // ------------------------------------------------------------------ lifecycle

    static boolean isRunning() {
        return active != null;
    }

    public static void shutdown() {
        TerminusEdge session = active;
        if (session != null) {
            session.clearAll();
        }
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
        active = null;
    }

    static void cast(JavaPlugin plugin, Player player, double damage, Runnable done) {
        if (active != null) {
            player.sendActionBar(Component.text("§7The world already has an edge."));
            done.run();
            return;
        }
        TerminusEdge session = new TerminusEdge(plugin, player, damage, done);
        active = session;
        try {
            session.start();
        } catch (Throwable error) {
            plugin.getLogger().warning("[Terminus] could not set the stone: " + error);
            session.clearAll();
        }
    }

    private TerminusEdge(JavaPlugin plugin, Player caster, double damage, Runnable done) {
        this.plugin = plugin;
        this.caster = caster;
        this.world = caster.getWorld();
        this.damage = damage;
        this.done = done;
    }

    private void start() {
        anchor = pickBenchmark();
        cx = anchor.getX();
        cz = anchor.getZ();
        Block under = world.getBlockAt(anchor.getBlockX(), (int) Math.floor(anchor.getY()) - 1, anchor.getBlockZ());
        floorData = under.getType().isSolid() ? under.getBlockData() : Material.STONE.createBlockData();
        sampleStrata();
        border = new TerminusBorder(world, cx, cz);
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            double dx = at.getX() - cx;
            double dz = at.getZ() - cz;
            if (player.equals(caster) || (dx * dx + dz * dz <= AUDIENCE * AUDIENCE && Math.abs(at.getY() - anchor.getY()) < 64)) {
                audience.add(player);
            }
        }

        caster.swingMainHand();
        caster.sendMessage("§f✦ Terminus §7— here ends the world.");
        world.playSound(caster.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.2f, 0.7f);
        world.playSound(caster.getLocation(), Sound.BLOCK_VAULT_CLOSE_SHUTTER, 0.9f, 0.6f);

        plateRel = LIFT + 2.6;
        plate = spawn(anchor.clone().add(0, plateRel, 0), Material.WAXED_CHISELED_COPPER.createBlockData(),
                plateShape((float) Math.toRadians(45), 1f), null, 3.0f);
        if (plate != null) {
            plate.setTeleportDuration(3);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (cleared) {
                    cancel();
                    return;
                }
                if (!caster.isOnline() || caster.isDead() || caster.getWorld() != world) {
                    clearAll();
                    cancel();
                    return;
                }
                boolean finished;
                try {
                    finished = step();
                } catch (Throwable error) {
                    plugin.getLogger().warning("[Terminus] timeline aborted: " + error);
                    finished = true;
                }
                if (finished) {
                    clearAll();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean step() {
        t++;

        if (t == 1) {
            plateRel = LIFT;
            movePlate();
            ease(plate, plateShape(0f, 1f), 3);
        }
        if (t == T_LAND) {
            setBenchmark();
        }
        if (t == T_CROSS) {
            gridLen = 1.0;
            fan[HALF_LINES] = 1.0;
            for (int i = 0; i < LINES; i++) {
                poseLine(i, START_SIZE / 2.0, 6);
            }
            world.playSound(anchor, Sound.ITEM_SPYGLASS_USE, 2.0f, 0.6f);
            world.playSound(anchor, Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.4f, 1.3f);
        }
        fanOut();

        if (t == T_HORIZON) {
            horizon();
        }
        if (t == T_ADVANCE) {
            advanceBegins();
        }
        if (t > T_ADVANCE && t < T_CRUSH) {
            advance();
        }
        if (t == T_EXTRUDE - 32) {
            score(Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 0.55f);
        }
        if (t == T_CRUSH) {
            crushBegins();
        }
        if (t > T_ADVANCE && t <= T_EXTRUDE) {
            herd();
        }
        if (t >= T_ADVANCE && t < T_RELEASE) {
            double half = sizeAt(t + 2) / 2.0;
            for (int i = 0; i < LINES; i++) {
                poseLine(i, half, 2);
            }
        }
        if (t == T_EXTRUDE) {
            crunch();
        }
        if (t > T_EXTRUDE && t <= T_HOLD) {
            extrude();
        }
        if (beepAt > 0 && t == beepAt + 2) {
            world.playSound(anchor.clone().add(0, 0.5, 0), Sound.BLOCK_NOTE_BLOCK_BIT, 1.6f, 1.5f);
        }
        if (t == T_HOLD) {
            border.lerp(1.0, HOLD * 50L + 150L);
        }
        if (t == T_HOLD + 2) {
            silence();
        }
        if (t == T_RELEASE) {
            letGo();
        }
        if (t == T_RELEASE + 5) {
            discardAll(core);
        }
        if (t == T_RELEASE + 6) {
            gridThick = 0f;
            for (int i = 0; i < LINES; i++) {
                poseLine(i, START_SIZE / 2.0, 10);
            }
        }
        if (t == T_RELEASE + 18) {
            discardAll(alongX);
            discardAll(alongZ);
        }
        if (t == T_RESTORE) {
            border.restore();
        }
        if (t == T_LOOK) {
            look(8f);
        }
        if (t == T_LOOK_DOWN) {
            look(72f);
            if (plate != null) {
                plate.setTeleportDuration(1);
            }
            world.playSound(plateAt(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.5f);
        }
        if (t >= T_DROP && landedAt < 0) {
            fall();
        }
        if (landedAt >= 0) {
            topple();
        }
        if (t >= T_EXTRUDE) {
            tickTower();
        }
        tickLoose();
        tickDecals();
        heartbeat();
        tickPulse();
        if (border.isShown()) {
            border.sweep(this::crossed);
        }
        hud();
        if (landedAt >= 0 && t == landedAt + AFTER - 9) {
            sinkBenchmark();
        }

        if (t >= HARD_END) {
            return true;
        }
        return landedAt >= 0 && t >= landedAt + AFTER && !pending();
    }

    // ------------------------------------------------------------------ act I — the benchmark and the survey

    private void setBenchmark() {
        world.playSound(anchor, Sound.BLOCK_HEAVY_CORE_PLACE, 2.2f, 0.6f);
        world.playSound(anchor, Sound.BLOCK_COPPER_PLACE, 1.6f, 0.5f);
        world.playSound(anchor, Sound.BLOCK_ANVIL_LAND, 0.4f, 1.7f);
        world.spawnParticle(Particle.BLOCK, anchor.clone().add(0, 0.1, 0), 18, 0.45, 0.05, 0.45, 0.0, floorData);
        decal(anchor, 0.55f, 2.2f, 7);
        for (int i = 0; i < LINES; i++) {
            Transformation none = box(0f, LINE_Y, 0f, 0.001f, 0.001f, 0.001f);
            alongX[i] = spawn(anchor, Material.WHITE_CONCRETE.createBlockData(), none, null, 3.0f);
            alongZ[i] = spawn(anchor, Material.WHITE_CONCRETE.createBlockData(), none, null, 3.0f);
        }
    }

    /** The parallels leave the centre cross a pair at a time, each with a tick of the instrument. */
    private void fanOut() {
        if (t < T_FAN || t > T_FAN + 2 * (HALF_LINES - 1) || (t - T_FAN) % 2 != 0) {
            return;
        }
        int m = (t - T_FAN) / 2 + 1;
        fan[HALF_LINES - m] = 1.0;
        fan[HALF_LINES + m] = 1.0;
        poseLine(HALF_LINES - m, START_SIZE / 2.0, 4);
        poseLine(HALF_LINES + m, START_SIZE / 2.0, 4);
        world.playSound(anchor, Sound.BLOCK_NOTE_BLOCK_HAT, 1.3f, 1.0f + m * 0.16f);
        if (m == HALF_LINES) {
            world.playSound(anchor, Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.4f, 0.9f);
        }
    }

    // ------------------------------------------------------------------ act II — the edge appears, and comes

    private void horizon() {
        silence();
        border.show(audience, START_SIZE);
        tintEdge(Material.LIGHT_BLUE_CONCRETE, EDGE_BLUE);
        score(Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
        score(Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 0.9f, 0.55f);
        score(Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.5f);
        admin("commands.worldborder.set.immediate", size(START_SIZE));
    }

    private void advanceBegins() {
        tintEdge(Material.RED_CONCRETE, EDGE_RED);
        score(Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
        score(Sound.ITEM_ELYTRA_FLYING, 0.55f, 0.55f);
        admin("commands.worldborder.set.shrink", size(1.0), Component.text(String.valueOf(ADVANCE / 20)));
        gatherCaptives();
    }

    private void advance() {
        border.lerp(sizeAt(t + 2), 100L);
        double k = (t - T_ADVANCE) / (double) ADVANCE;
        if ((t - T_ADVANCE) % 10 == 0) {
            score(Sound.BLOCK_BEACON_AMBIENT, 0.9f, (float) (0.5 + k * 1.0));
        }
        if (t % 2 == 0) {
            plough(border.size() / 2.0);
        }
    }

    /** Dust kicked up where the four walls cut across the floor. */
    private void plough(double half) {
        if (half < 1.5) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Particle.DustOptions red = new Particle.DustOptions(EDGE_RED, 1.3f);
        for (int wall = 0; wall < 4; wall++) {
            for (int n = 0; n < 5; n++) {
                double along = random.nextDouble(-half, half);
                double x = wall < 2 ? along : (wall == 2 ? half : -half);
                double z = wall < 2 ? (wall == 0 ? half : -half) : along;
                Location at = anchor.clone().add(x, 0.12, z);
                world.spawnParticle(Particle.BLOCK, at, 2, 0.12, 0.04, 0.12, 0.0, floorData);
                world.spawnParticle(Particle.DUST, at, 1, 0.05, 0.1, 0.05, 0.0, red);
            }
        }
    }

    private void gatherCaptives() {
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(anchor, CAPTURE, 16.0, CAPTURE)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity) || entity.isInsideVehicle()) {
                continue;
            }
            if (entity.getPersistentDataContainer().has(AetherKeys.BOSS_ID, PersistentDataType.STRING)) {
                continue;
            }
            boolean carrying = false;
            for (Entity passenger : entity.getPassengers()) {
                if (!(passenger instanceof Display)) {
                    carrying = true;
                    break;
                }
            }
            if (!carrying) {
                found.add((LivingEntity) entity);
            }
        }
        found.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(anchor)));
        for (LivingEntity entity : found) {
            if (captives.size() >= TOTEM_CAP) {
                break;
            }
            captives.add(new Captive(entity));
            taken.add(entity.getUniqueId());
        }
    }

    /** The walls shove whatever is in front of them; at the very end they simply put it where it belongs. */
    private void herd() {
        double half = border.size() / 2.0;
        double speed = Math.max(0.0, (sizeAt(t) - sizeAt(t + 1)) / 2.0);
        for (Captive captive : captives) {
            if (captive.escaped || captive.mounted) {
                continue;
            }
            LivingEntity entity = captive.entity;
            if (!entity.isValid() || entity.isDead()) {
                captive.escaped = true;
                continue;
            }
            Location at = entity.getLocation();
            double dx = at.getX() - cx;
            double dz = at.getZ() - cz;
            double limit = Math.max(0.0, half - Math.max(0.3, entity.getWidth() / 2.0) - 0.15);
            if (half < 4.5) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) > 10.0 || Math.abs(at.getY() - anchor.getY()) > 8.0) {
                    captive.escaped = true;
                    continue;
                }
                if (Math.abs(dx) > limit || Math.abs(dz) > limit) {
                    Location to = at.clone();
                    to.setX(cx + clamp(dx, -limit, limit));
                    to.setZ(cz + clamp(dz, -limit, limit));
                    entity.teleport(to, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                    entity.setFallDistance(0f);
                }
                continue;
            }
            if (Math.max(Math.abs(dx), Math.abs(dz)) > half + 4.0) {
                captive.escaped = true;
                continue;
            }
            Vector velocity = entity.getVelocity();
            double shove = Math.min(3.0, speed + 0.3);
            boolean pushed = false;
            if (Math.abs(dx) > limit) {
                velocity.setX(-Math.signum(dx) * shove);
                pushed = true;
            }
            if (Math.abs(dz) > limit) {
                velocity.setZ(-Math.signum(dz) * shove);
                pushed = true;
            }
            if (pushed) {
                velocity.setY(Math.max(velocity.getY(), 0.06));
                entity.setVelocity(velocity);
                entity.setFallDistance(0f);
            }
        }
    }

    // ------------------------------------------------------------------ act III — one block wide

    private void crushBegins() {
        border.lerp(NEEDLE_SIZE, CRUSH * 50L);
        world.playSound(anchor, Sound.BLOCK_PISTON_CONTRACT, 3.0f, 0.5f);
        for (int i = 0; i < CORE; i++) {
            core[i] = spawn(anchor, strata[i], coreSegment(i, -0.01), null, 2.5f);
        }
    }

    /** The walls meet: the herd is crushed into one tower on the benchmark, and the world starts to squeeze out. */
    private void crunch() {
        border.lerp(HOLD_SIZE, EXTRUDE * 50L + 100L);
        world.playSound(anchor, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 3.0f, 0.5f);
        world.playSound(anchor, Sound.BLOCK_ANVIL_LAND, 2.2f, 0.5f);
        world.playSound(anchor, Sound.BLOCK_HEAVY_CORE_PLACE, 3.0f, 0.5f);
        world.playSound(anchor, Sound.BLOCK_PISTON_EXTEND, 3.0f, 0.5f);
        world.playSound(anchor, Sound.BLOCK_GRINDSTONE_USE, 2.0f, 0.5f);
        world.spawnParticle(Particle.BLOCK, anchor.clone().add(0, 0.3, 0), 40, 0.5, 0.2, 0.5, 0.0, floorData);
        shake(40.0);

        List<Captive> crushed = new ArrayList<>();
        for (Captive captive : captives) {
            LivingEntity entity = captive.entity;
            if (captive.escaped || !entity.isValid() || entity.isDead()) {
                continue;
            }
            Location at = entity.getLocation();
            if (Math.max(Math.abs(at.getX() - cx), Math.abs(at.getZ() - cz)) > 2.5
                    || Math.abs(at.getY() - anchor.getY()) > 6.0) {
                continue;
            }
            hit(entity, damage * CRUSH_SHARE);
            if (entity.isValid() && !entity.isDead()) {
                crushed.add(captive);
            }
        }
        crushed.sort(Comparator.comparingDouble((Captive c) -> c.entity.getHeight()).reversed());
        Entity vehicle = plate;
        for (Captive captive : crushed) {
            LivingEntity entity = captive.entity;
            entity.setVelocity(new Vector());
            entity.setFallDistance(0f);
            captive.hush();
            boolean mounted = vehicle != null && vehicle.isValid() && vehicle.addPassenger(entity);
            if (mounted) {
                captive.mounted = true;
                chain.add(captive);
                vehicle = entity;
            } else {
                captive.wake();
                captive.loose = true;
                captive.looseAge = 0;
                entity.setVelocity(new Vector(0, 1.5, 0));
            }
        }
        if (plate != null) {
            plate.setTeleportDuration(2);
        }
    }

    /** A core sample of the world rams up the needle, stratum by stratum, the tower riding the benchmark on top. */
    private void extrude() {
        double k = (t + 1 - T_EXTRUDE) / (double) EXTRUDE;
        double h = CORE * outBack(k, 1.15);
        for (int i = 0; i < CORE; i++) {
            ease(core[i], coreSegment(i, h), 2);
        }
        plateRel = h + LIFT;
        movePlate();

        int out = (int) Math.max(0, Math.min(CORE, Math.floor(h + 1.0E-3)));
        if (out > emerged) {
            emerged = out;
            int i = out - 1;
            Sound place = strata[i].getSoundGroup().getPlaceSound();
            world.playSound(anchor, place, 2.0f, 0.65f + 0.6f * i / (float) CORE);
            if (i == CORE - 1) {
                beepAt = t;
                glow(core[i], COMMAND);
                world.playSound(anchor.clone().add(0, 0.5, 0), Sound.BLOCK_NOTE_BLOCK_BIT, 1.6f, 1.0f);
            }
        }
        if (k < 0.7) {
            world.spawnParticle(Particle.BLOCK, anchor.clone().add(0, 0.2, 0), 6, 0.35, 0.1, 0.35, 0.0, floorData);
        }
    }

    // ------------------------------------------------------------------ act IV — the world lets go

    private void letGo() {
        border.lerp(GREEN_SIZE, GREEN_MS);
        tintEdge(Material.LIME_CONCRETE, EDGE_GREEN);
        admin("commands.worldborder.set.grow", size(world.getWorldBorder().getSize()), Component.text("1"));
        score(Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.7f);
        score(Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.6f);
        score(Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.6f);
        score(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
        world.playSound(anchor, Sound.BLOCK_PISTON_CONTRACT, 3.0f, 0.5f);
        world.spawnParticle(Particle.BLOCK, anchor.clone().add(0, 0.3, 0), 30, 0.4, 0.2, 0.4, 0.0, floorData);
        shake(AUDIENCE);

        for (int i = 0; i < CORE; i++) {
            ease(core[i], coreSegment(i, -0.01), 3);
        }
        for (int i = 0; i < LINES; i++) {
            poseLine(i, START_SIZE / 2.0, 5);
        }

        for (Entity entity : world.getNearbyEntities(anchor, 3.5, 10.0, 3.5)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity) || taken.contains(entity.getUniqueId())) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            hit(living, damage);
            Vector away = living.getLocation().toVector().subtract(anchor.toVector()).setY(0);
            if (away.lengthSquared() < 1.0E-4) {
                away = new Vector(1, 0, 0);
            }
            living.setVelocity(away.normalize().multiply(0.9).setY(0.7));
        }
    }

    /** The tower turns to look at whoever did this; then it looks down. */
    private void look(float pitch) {
        Location from = caster.getLocation();
        for (Captive captive : chain) {
            if (!captive.mounted || !captive.entity.isValid()) {
                continue;
            }
            Location at = captive.entity.getLocation();
            double dx = from.getX() - at.getX();
            double dz = from.getZ() - at.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            captive.entity.setRotation(yaw, pitch);
        }
    }

    /** The benchmark surfs the tower down under ordinary gravity while a slide whistle falls with it. */
    private void fall() {
        if (t == T_DROP) {
            plateVy = 0.0;
        }
        int age = t - T_DROP;
        plateVy = (plateVy - 0.08) * 0.98;
        plateRel = Math.max(LIFT, plateRel + plateVy);
        movePlate();
        if (age % 2 == 0) {
            float pitch = (float) Math.pow(2.0, 1.0 - 2.0 * Math.min(1.0, age / 24.0));
            world.playSound(plateAt(), Sound.BLOCK_NOTE_BLOCK_FLUTE, 1.3f, pitch);
        }
        if (plateRel <= LIFT + 1.0E-3 || age >= FALL_CAP) {
            plateRel = LIFT;
            movePlate();
            landedAt = t;
            nextTopple = t + 2;
            world.playSound(anchor, Sound.BLOCK_HEAVY_CORE_PLACE, 3.0f, 0.5f);
            world.playSound(anchor, Sound.ENTITY_GENERIC_BIG_FALL, 2.0f, 0.6f);
            world.playSound(anchor, Sound.BLOCK_ANVIL_LAND, 0.8f, 1.5f);
            world.spawnParticle(Particle.BLOCK, anchor.clone().add(0, 0.2, 0), 30, 0.8, 0.1, 0.8, 0.0, floorData);
            glow(plate, null);
            decal(anchor, 0.7f, 7.5f, 12);
            shake(24.0);
        }
    }

    /** The tower comes apart from the top, one body at a time. */
    private void topple() {
        if (t < nextTopple) {
            return;
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            Captive captive = chain.get(i);
            if (!captive.mounted) {
                continue;
            }
            LivingEntity entity = captive.entity;
            captive.mounted = false;
            if (entity.isValid() && !entity.isDead()) {
                Location was = entity.getLocation();
                entity.leaveVehicle();
                entity.teleport(was, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                captive.wake();
                ThreadLocalRandom random = ThreadLocalRandom.current();
                double angle = random.nextDouble(Math.PI * 2);
                entity.setVelocity(new Vector(Math.cos(angle) * 0.45, 0.42, Math.sin(angle) * 0.45));
                entity.setFallDistance(0f);
                captive.loose = true;
                captive.looseAge = 0;
            }
            nextTopple = t + 2;
            return;
        }
    }

    /** Anyone thrown, dropped or knocked off lands with the full weight of the world. */
    private void tickLoose() {
        for (Captive captive : captives) {
            if (!captive.loose) {
                continue;
            }
            LivingEntity entity = captive.entity;
            if (!entity.isValid() || entity.isDead()) {
                captive.loose = false;
                continue;
            }
            captive.looseAge++;
            if ((captive.looseAge >= 4 && entity.isOnGround()) || captive.looseAge >= 60) {
                captive.loose = false;
                splat(entity);
            }
        }
    }

    private void splat(LivingEntity entity) {
        Location at = entity.getLocation();
        hit(entity, damage);
        float pitch = 0.75f + ThreadLocalRandom.current().nextFloat() * 0.3f;
        world.playSound(at, Sound.ENTITY_GENERIC_BIG_FALL, 1.6f, pitch);
        world.playSound(at, Sound.BLOCK_HEAVY_CORE_STEP, 1.8f, 0.7f);
        Block below = world.getBlockAt(at.getBlockX(), (int) Math.floor(at.getY() - 0.2), at.getBlockZ());
        BlockData dust = below.getType().isSolid() ? below.getBlockData() : floorData;
        world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.15, 0), 16, 0.4, 0.05, 0.4, 0.0, dust);
        decal(at, 0.45f, 3.0f, 9);
    }

    /** A benchmark whose tower fell off it, or whose rider was killed from under it, lets the rest go. */
    private void tickTower() {
        for (Captive captive : chain) {
            if (!captive.mounted) {
                continue;
            }
            LivingEntity entity = captive.entity;
            if (!entity.isValid() || entity.isDead()) {
                captive.mounted = false;
                continue;
            }
            if (entity.getVehicle() == null) {
                captive.mounted = false;
                captive.wake();
                captive.loose = true;
                captive.looseAge = 0;
                continue;
            }
            entity.setFallDistance(0f);
        }
    }

    private void sinkBenchmark() {
        ease(plate, plateShape(0f, 0f), 8);
        world.playSound(anchor, Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.4f, 0.5f);
        world.playSound(anchor, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 1.0f, 0.5f);
    }

    private boolean pending() {
        for (Captive captive : captives) {
            if (captive.loose || captive.mounted) {
                return true;
            }
        }
        return !decals.isEmpty();
    }

    // ------------------------------------------------------------------ the edge, the screen, the sound

    /** Blue border size scripted over the whole show; the grid always lies exactly in the border's footprint. */
    private static double sizeAt(int tick) {
        if (tick <= T_ADVANCE) {
            return START_SIZE;
        }
        if (tick <= T_CRUSH) {
            double k = (tick - T_ADVANCE) / (double) ADVANCE;
            return START_SIZE - (START_SIZE - BOX_SIZE) * (0.15 * k + 0.85 * k * k * k);
        }
        if (tick <= T_EXTRUDE) {
            return BOX_SIZE + (NEEDLE_SIZE - BOX_SIZE) * (tick - T_CRUSH) / (double) CRUSH;
        }
        if (tick <= T_HOLD) {
            return NEEDLE_SIZE + (HOLD_SIZE - NEEDLE_SIZE) * (tick - T_EXTRUDE) / (double) EXTRUDE;
        }
        if (tick <= T_RELEASE) {
            return HOLD_SIZE + (1.0 - HOLD_SIZE) * (tick - T_HOLD) / (double) HOLD;
        }
        return 1.0;
    }

    /** Someone the edge just passed. Going out is a hard wall of air through the body; coming back is a breath. */
    private void crossed(Player player, boolean out) {
        Location at = player.getLocation();
        if (out) {
            player.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.0f, 0.7f);
            player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.5f);
            player.sendHurtAnimation(0f);
        } else {
            player.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.3f);
        }
    }

    /** Heartbeat under the whole approach; each beat pulses the red at the edges of every screen still inside. */
    private void heartbeat() {
        if (!border.isShown() || t < nextBeat || t >= T_CRUSH) {
            return;
        }
        double k = t < T_ADVANCE ? 0.0 : (t - T_ADVANCE) / (double) ADVANCE;
        int interval = t < T_ADVANCE ? 20 : (int) Math.round(16 - 11 * k);
        nextBeat = t + Math.max(4, interval);
        score(Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, (float) (0.6 + 0.45 * k));
        pulsePeak = t < T_ADVANCE ? 64 : (int) Math.round(border.size() / 2.0 * 1.7 + 6.0);
        pulseAge = 0;
    }

    private void tickPulse() {
        if (pulseAge < 0) {
            return;
        }
        double k = pulseAge / 6.0;
        if (k >= 1.0 || t >= T_CRUSH) {
            border.warn(0);
            pulseAge = -1;
            return;
        }
        border.warn((int) Math.round(pulsePeak * (1.0 - k) * (1.0 - k)));
        pulseAge++;
    }

    /** How far away the end of the world is, per onlooker — and, for a while, how far outside it they are. */
    private void hud() {
        if (t < T_LAND || t % 2 != 0) {
            return;
        }
        if (landedAt >= 0 && t > landedAt + 18) {
            return;
        }
        boolean virtual = border.isShown() && t < T_RELEASE;
        for (Player player : audience) {
            if (!player.isOnline() || player.getWorld() != world) {
                continue;
            }
            double distance = virtual && border.sees(player)
                    ? border.signed(player.getLocation())
                    : TerminusBorder.realDistance(player);
            player.sendActionBar(Component.text(edgeLine(distance)));
        }
    }

    private static String edgeLine(double distance) {
        if (distance >= 0) {
            long n = (long) Math.floor(distance);
            return "§7edge of the world §8· §f" + String.format(Locale.ROOT, "%,d", n) + (n == 1 ? " §7block" : " §7blocks");
        }
        long n = (long) Math.ceil(-distance);
        return "§cyou are §l" + String.format(Locale.ROOT, "%,d", n) + (n == 1 ? "§c block" : "§c blocks")
                + " outside the world";
    }

    /** Exactly the line an operator sees when someone runs /worldborder. */
    private void admin(String key, Component... args) {
        Component line = Component.translatable("chat.type.admin",
                        Component.text(caster.getName()), Component.translatable(key, args))
                .color(NamedTextColor.GRAY)
                .decorate(TextDecoration.ITALIC);
        for (Player player : audience) {
            if (player.isOnline()) {
                player.sendMessage(line);
            }
        }
    }

    private static Component size(double blocks) {
        return Component.text(String.format(Locale.ROOT, "%.1f", blocks));
    }

    /** Non-positional: heard the same by everyone watching. */
    private void score(Sound sound, float volume, float pitch) {
        for (Player player : audience) {
            if (player.isOnline() && player.getWorld() == world) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private void silence() {
        for (Player player : audience) {
            if (player.isOnline()) {
                player.stopAllSounds();
            }
        }
    }

    private void shake(double radius) {
        for (Player player : audience) {
            if (player.isOnline() && player.getWorld() == world
                    && player.getLocation().distanceSquared(anchor) <= radius * radius) {
                player.sendHurtAnimation(0f);
            }
        }
    }

    // ------------------------------------------------------------------ geometry

    private void poseLine(int i, double half, int ticks) {
        int k = i - HALF_LINES;
        boolean outer = Math.abs(k) == HALF_LINES;
        float w = Math.max(0.001f, (outer ? EDGE_W : LINE_W) * gridThick);
        float th = Math.max(0.001f, (outer ? 0.03f : 0.02f) * gridThick);
        float off = (float) (k / (double) HALF_LINES * half * fan[i]);
        float len = (float) Math.max(0.001, 2.0 * half * gridLen);
        ease(alongX[i], box(-len / 2f, LINE_Y, off - w / 2f, len, th, w), ticks);
        ease(alongZ[i], box(off - w / 2f, LINE_Y, -len / 2f, w, th, len), ticks);
    }

    /** The outer pair of each axis is the border's own footprint on the floor, and wears its colour. */
    private void tintEdge(Material material, Color color) {
        BlockData data = material.createBlockData();
        for (BlockDisplay display : new BlockDisplay[]{alongX[0], alongX[LINES - 1], alongZ[0], alongZ[LINES - 1]}) {
            if (display != null && display.isValid()) {
                display.setBlock(data);
                glow(display, color);
            }
        }
        glow(plate, color);
    }

    private static Transformation coreSegment(int i, double h) {
        return box(-0.499f, (float) (h - (i + 1)) + 0.001f, -0.499f, 0.998f, 0.998f, 0.998f);
    }

    /** A square brass plate; its top face sits a hair above the entity point minus the rider lift. */
    private static Transformation plateShape(float yaw, float scale) {
        float s = Math.max(0.001f, scale);
        Quaternionf rot = new Quaternionf().rotateY(yaw);
        Vector3f size = new Vector3f(1.1f * s, 0.1f * s, 1.1f * s);
        Vector3f center = new Vector3f(0f, (float) (-LIFT - 0.02) - (1f - s) * 0.05f, 0f);
        Vector3f corner = new Vector3f(center).sub(new Quaternionf(rot).transform(new Vector3f(size).mul(0.5f)));
        return new Transformation(corner, rot, size, new Quaternionf());
    }

    private static Transformation box(float x, float y, float z, float sx, float sy, float sz) {
        return new Transformation(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf());
    }

    private void movePlate() {
        if (plate != null && plate.isValid()) {
            plate.teleport(plateAt(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
    }

    private Location plateAt() {
        return anchor.clone().add(0, plateRel, 0);
    }

    private void sampleStrata() {
        for (int i = 0; i < CORE; i++) {
            strata[i] = STRATA[i].createBlockData();
        }
        int y = (int) Math.floor(anchor.getY()) - 1;
        for (int i = 0; i < 3; i++) {
            Block block = world.getBlockAt(anchor.getBlockX(), y - i, anchor.getBlockZ());
            Material type = block.getType();
            if (type.isSolid() && type.isOccluding()) {
                strata[i] = block.getBlockData();
            }
        }
    }

    /** Where you look, at least a few steps out, snapped to the centre of a block and set on its floor. */
    private Location pickBenchmark() {
        Location eye = caster.getEyeLocation();
        Location feet = caster.getLocation();
        Vector dir = eye.getDirection().normalize();
        Vector flat = dir.clone().setY(0);
        if (flat.lengthSquared() < 1.0E-4) {
            flat = new Vector(0, 0, 1);
        }
        flat.normalize();
        RayTraceResult hit = world.rayTraceBlocks(eye, dir, MAX_REACH, FluidCollisionMode.NEVER, true);
        Location aim;
        double nearY;
        if (hit != null && hit.getHitPosition() != null) {
            aim = hit.getHitPosition().toLocation(world);
            nearY = aim.getY();
        } else {
            aim = feet.clone().add(flat.clone().multiply(REACH));
            nearY = feet.getY();
        }
        double out = Math.hypot(aim.getX() - feet.getX(), aim.getZ() - feet.getZ());
        if (out < MIN_REACH) {
            aim = feet.clone().add(flat.clone().multiply(MIN_REACH));
            nearY = feet.getY();
        }
        Location probe = new Location(world, aim.getBlockX() + 0.5, nearY + 6.0, aim.getBlockZ() + 0.5);
        RayTraceResult floor = world.rayTraceBlocks(probe, new Vector(0, -1, 0), 18.0, FluidCollisionMode.NEVER, true);
        double y = floor != null && floor.getHitPosition() != null ? floor.getHitPosition().getY() : Math.floor(feet.getY());
        return new Location(world, aim.getBlockX() + 0.5, y, aim.getBlockZ() + 0.5, 0f, 0f);
    }

    // ------------------------------------------------------------------ decals

    private void decal(Location at, float from, float to, int life) {
        if (decals.size() >= MAX_DECALS) {
            return;
        }
        decals.add(new Decal(at, from, to, life));
    }

    private void tickDecals() {
        for (int i = decals.size() - 1; i >= 0; i--) {
            Decal decal = decals.get(i);
            if (!decal.step()) {
                decal.remove();
                decals.remove(i);
            }
        }
    }

    /** A square shock ring on the floor: in a world with square edges, everything lands square. */
    private final class Decal {
        final BlockDisplay[] sides = new BlockDisplay[4];
        final float to;
        final int life;
        int age;

        Decal(Location at, float from, float to, int life) {
            this.to = to;
            this.life = life;
            Location floor = new Location(world, at.getX(), at.getY(), at.getZ(), 0f, 0f);
            Transformation[] ring = ring(from, 0.16f);
            for (int s = 0; s < 4; s++) {
                sides[s] = spawn(floor, Material.WHITE_CONCRETE.createBlockData(), ring[s], null, 1.5f);
            }
        }

        boolean step() {
            age++;
            if (age == 1) {
                pose(ring(to * 0.7f, 0.12f), 3);
            } else if (age == 4) {
                pose(ring(to, 0.001f), Math.max(1, life - 4));
            }
            return age <= life;
        }

        private void pose(Transformation[] ring, int ticks) {
            for (int s = 0; s < 4; s++) {
                ease(sides[s], ring[s], ticks);
            }
        }

        private Transformation[] ring(float r, float w) {
            float th = 0.03f;
            return new Transformation[]{
                    box(-r, LINE_Y, r - w / 2f, 2f * r, th, w),
                    box(-r, LINE_Y, -r - w / 2f, 2f * r, th, w),
                    box(r - w / 2f, LINE_Y, -r, w, th, 2f * r),
                    box(-r - w / 2f, LINE_Y, -r, w, th, 2f * r)
            };
        }

        void remove() {
            discardAll(sides);
        }
    }

    // ------------------------------------------------------------------ the tower

    private static final class Captive {
        final LivingEntity entity;
        Boolean hadAi;
        Boolean wasSilent;
        boolean escaped;
        boolean mounted;
        boolean loose;
        int looseAge;

        Captive(LivingEntity entity) {
            this.entity = entity;
        }

        /** Held still and quiet while it is part of the tower. */
        void hush() {
            if (entity instanceof Mob mob && hadAi == null) {
                hadAi = mob.hasAI();
                mob.setAI(false);
            }
            if (wasSilent == null) {
                wasSilent = entity.isSilent();
                entity.setSilent(true);
            }
        }

        void wake() {
            if (!entity.isValid()) {
                return;
            }
            if (hadAi != null && entity instanceof Mob mob) {
                mob.setAI(hadAi);
            }
            if (wasSilent != null) {
                entity.setSilent(wasSilent);
            }
            hadAi = null;
            wasSilent = null;
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Marks the hit as scripted so BossEngine does not treat it as spam melee. */
    private void hit(LivingEntity target, double amount) {
        ScriptedHits.run(() -> target.damage(amount, caster));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static double outBack(double x, double overshoot) {
        x = clamp(x, 0.0, 1.0);
        double c3 = overshoot + 1.0;
        double u = x - 1.0;
        return 1.0 + c3 * u * u * u + overshoot * u * u;
    }

    private static void glow(Display display, Color color) {
        if (display == null || !display.isValid()) {
            return;
        }
        if (color == null) {
            display.setGlowing(false);
            return;
        }
        display.setGlowColorOverride(color);
        display.setGlowing(true);
    }

    private static void ease(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, ticks));
        display.setTransformation(transformation);
    }

    private BlockDisplay spawn(Location at, BlockData data, Transformation initial, Color glow, float viewRange) {
        try {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(data);
                spawned.setPersistent(false);
                spawned.setBrightness(LIT);
                spawned.setViewRange(viewRange);
                spawned.setInterpolationDelay(0);
                spawned.setInterpolationDuration(0);
                spawned.setTeleportDuration(0);
                spawned.setTransformation(initial);
                if (glow != null) {
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                }
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
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

    private void release() {
        if (!released) {
            released = true;
            done.run();
        }
    }

    /** Every exit path: borders back, bodies down and awake, every display gone, the world's edge freed. */
    private void clearAll() {
        if (cleared) {
            return;
        }
        cleared = true;
        if (border != null) {
            border.restore();
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            LivingEntity entity = chain.get(i).entity;
            if (entity.isValid() && entity.isInsideVehicle()) {
                entity.leaveVehicle();
            }
        }
        if (plate != null && plate.isValid()) {
            plate.eject();
        }
        for (Captive captive : captives) {
            LivingEntity entity = captive.entity;
            if (!entity.isValid()) {
                continue;
            }
            if (chain.contains(captive) && anchor != null && entity.getLocation().getY() - anchor.getY() > 3.0) {
                entity.teleport(anchor, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
            captive.mounted = false;
            captive.loose = false;
            captive.wake();
            entity.setFallDistance(0f);
        }
        discardAll(alongX);
        discardAll(alongZ);
        discardAll(core);
        for (Decal decal : decals) {
            decal.remove();
        }
        decals.clear();
        discard(plate);
        plate = null;
        if (active == this) {
            active = null;
        }
        release();
    }
}
