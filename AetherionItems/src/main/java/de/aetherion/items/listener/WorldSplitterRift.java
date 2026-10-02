package de.aetherion.items.listener;

import de.aetherion.items.combat.ScriptedHits;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World Splitter (world_splitter) — Reality Rift.
 * No blocks are broken. A vertical seam of BlockDisplays tears the world-illusion apart:
 * two terrain walls peel open, a void plane breathes between them, debris falls the wrong way,
 * and reality slams shut. Pure spatial composition — not a nova.
 */
public final class WorldSplitterRift {

    private static final int TENSION = 14;
    private static final int FRACTURE = TENSION + 18;
    private static final int PEEL = FRACTURE + 12;
    private static final int RIFT = PEEL + 48;
    private static final int SEAL = RIFT + 22;
    private static final int AFTER = SEAL + 14;
    private static final int TOTAL = AFTER + 10;

    private static final int WALL_W = 7;
    private static final int WALL_H = 6;
    private static final float CELL = 1.05f;
    private static final float MAX_GAP = 5.2f;
    private static final double REACH = 8.0;
    private static final int SEAM_SEGS = 22;
    private static final int VOID_PANELS = 10;
    private static final int DEBRIS_N = 22;
    private static final int SCAR_N = 14;
    private static final int MAX_LIVE = 300;
    private static final double SPLASH_R = 9.0;

    private static final Color VOID_GLOW = Color.fromRGB(90, 20, 140);
    private static final Color EDGE = Color.fromRGB(200, 120, 255);
    private static final Color SEAM = Color.fromRGB(255, 230, 255);
    private static final Color WRONG = Color.fromRGB(40, 255, 200);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    private static final Display.Brightness DIM = new Display.Brightness(4, 8);

    private static final Material[] TERRAIN = {
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.STONE,
            Material.DEEPSLATE, Material.ANDESITE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
            Material.TUFF, Material.CALCITE, Material.ROOTED_DIRT, Material.PODZOL
    };
    private static final Material[] WRONG_WORLD = {
            Material.CRYING_OBSIDIAN, Material.END_STONE, Material.PURPUR_BLOCK,
            Material.BLACKSTONE, Material.OBSIDIAN, Material.BEDROCK, Material.SCULK,
            Material.WARPED_WART_BLOCK, Material.PEARLESCENT_FROGLIGHT
    };

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final Player player;
    private final World world;
    private final double damage;
    private final Runnable done;

    private final List<BlockDisplay> leftWall = new ArrayList<>();
    private final List<BlockDisplay> rightWall = new ArrayList<>();
    private final List<BlockDisplay> seam = new ArrayList<>();
    private final List<BlockDisplay> voidPlane = new ArrayList<>();
    private final List<BlockDisplay> edgeL = new ArrayList<>();
    private final List<BlockDisplay> edgeR = new ArrayList<>();
    private final List<BlockDisplay> scars = new ArrayList<>();
    private final List<Debris> debris = new ArrayList<>();

    private Location focus;
    private Vector right;
    private Vector up;
    private Vector forward;
    private float gap;
    private float targetGap;
    private int wallCols = WALL_W;
    private int wallRows = WALL_H;
    private int tick;
    private int phaseTick;
    private Phase phase = Phase.TENSION;
    private boolean released;
    private boolean hit;

    private enum Phase { TENSION, FRACTURE, PEEL, RIFT, SEAL, AFTER, DONE }

    private final class Debris {
        final BlockDisplay display;
        final Location at;
        final Vector velocity;
        final float size;
        final int life;
        final Quaternionf rot = new Quaternionf();
        final float spinX;
        final float spinY;
        final boolean intoRift;
        int age;

        Debris(BlockDisplay display, Location start, Vector velocity, float size, int life, boolean intoRift) {
            this.display = display;
            this.at = level(start.clone());
            this.velocity = velocity;
            this.size = size;
            this.life = life;
            this.intoRift = intoRift;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            this.spinX = (float) random.nextDouble(-0.35, 0.35);
            this.spinY = (float) random.nextDouble(-0.35, 0.35);
            if (display != null) {
                display.setTeleportDuration(1);
            }
        }

        boolean step() {
            age++;
            if (display == null || !display.isValid() || age > life) {
                return false;
            }
            if (intoRift && focus != null) {
                Vector pull = focus.toVector().add(new Vector(0, at.getY() - focus.getY(), 0))
                        .subtract(at.toVector());
                if (pull.lengthSquared() > 0.01) {
                    velocity.add(pull.normalize().multiply(0.08));
                }
            }
            at.add(velocity);
            if (!intoRift) {
                velocity.setY(velocity.getY() - 0.035);
            } else {
                velocity.multiply(0.96);
            }
            rot.rotateXYZ(spinX, spinY, spinX * 0.3f);
            display.teleport(at);
            float s = size * Math.max(0.15f, 1f - age / (float) life * 0.55f);
            ease(display, centered(s, rot), 1);
            return true;
        }
    }

    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    static void cast(JavaPlugin plugin, Player player, double damage, Runnable done) {
        new WorldSplitterRift(plugin, player, damage, done).start();
    }

    private WorldSplitterRift(JavaPlugin plugin, Player player, double damage, Runnable done) {
        this.plugin = plugin;
        this.player = player;
        this.world = player.getWorld();
        this.damage = damage;
        this.done = done;
    }

    private void start() {
        Location eye = player.getEyeLocation();
        forward = eye.getDirection().clone().setY(0);
        if (forward.lengthSquared() < 0.01) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        up = new Vector(0, 1, 0);

        focus = eye.clone().add(forward.clone().multiply(REACH));
        focus = floorNear(focus).add(0, WALL_H * CELL * 0.45, 0);
        gap = 0.02f;
        targetGap = 0.02f;

        player.sendMessage("§5✦ World Splitter §7— reality holds its breath…");
        player.sendActionBar(net.kyori.adventure.text.Component.text("§5§l∥ REALITY STRAIN"));
        world.playSound(focus, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 0.4f);
        world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 1.0f, 0.5f);
        world.playSound(focus, Sound.ENTITY_WARDEN_HEARTBEAT, 1.1f, 0.55f);

        stageSeam();

        new BukkitRunnable() {
            @Override
            public void run() {
                if ((!player.isOnline() || player.isDead() || player.getWorld() != world)
                        && phase.ordinal() < Phase.RIFT.ordinal()) {
                    release();
                    clearAll();
                    cancel();
                    return;
                }
                if (step()) {
                    clearAll();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean step() {
        tick++;
        return switch (phase) {
            case TENSION -> {
                tension();
                yield false;
            }
            case FRACTURE -> {
                fracture();
                yield false;
            }
            case PEEL -> {
                peel();
                yield false;
            }
            case RIFT -> {
                rift();
                yield false;
            }
            case SEAL -> {
                seal();
                yield false;
            }
            case AFTER -> {
                after();
                yield false;
            }
            case DONE -> true;
        };
    }

    private void release() {
        if (!released) {
            released = true;
            done.run();
        }
    }

    // ------------------------------------------------------------------ tension: hairline seam

    private void stageSeam() {
        for (int i = 0; i < SEAM_SEGS; i++) {
            double t = (i / (double) (SEAM_SEGS - 1)) - 0.5;
            Location at = focus.clone().add(up.clone().multiply(t * WALL_H * CELL));
            seam.add(spawn(at, Material.BLACK_CONCRETE.createBlockData(), LIT, SEAM, tiny()));
        }
    }

    private void tension() {
        phaseTick++;
        double u = phaseTick / (double) TENSION;
        float crack = 0.04f + (float) u * 0.12f;
        poseSeam(crack, (float) (0.15 + u * 0.55), (float) (Math.sin(phaseTick * 0.7) * 0.08));

        if (phaseTick % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double t = ThreadLocalRandom.current().nextDouble(-0.5, 0.5);
                Location at = focus.clone().add(up.clone().multiply(t * WALL_H * CELL * 0.9));
                world.spawnParticle(Particle.DUST, at, 1, 0.02, 0.15, 0.02, 0, new Particle.DustOptions(EDGE, 0.9f));
                world.spawnParticle(Particle.REVERSE_PORTAL, at, 1, 0.02, 0.1, 0.02, 0.01);
            }
        }
        if (phaseTick == 1 || phaseTick == 5 || phaseTick == 10) {
            world.playSound(focus, Sound.BLOCK_GLASS_HIT, 0.55f, 1.6f - (float) u * 0.6f);
            world.playSound(focus, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.4f + (float) u * 0.3f);
        }
        if (phaseTick >= TENSION) {
            phase = Phase.FRACTURE;
            phaseTick = 0;
            stageWalls();
            stageVoid();
            stageEdges();
            stageScars();
            world.playSound(focus, Sound.BLOCK_GLASS_BREAK, 0.9f, 0.55f);
            world.playSound(focus, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.7f, 0.45f);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§5∥ Fracture spreading…"));
        }
    }

    private void poseSeam(float width, float heightScale, float jitter) {
        int n = seam.size();
        for (int i = 0; i < n; i++) {
            BlockDisplay piece = seam.get(i);
            if (piece == null || !piece.isValid()) {
                continue;
            }
            double t = (i / (double) Math.max(1, n - 1)) - 0.5;
            double j = Math.sin(tick * 0.45 + i * 0.7) * jitter;
            Location at = focus.clone()
                    .add(up.clone().multiply(t * WALL_H * CELL * heightScale))
                    .add(right.clone().multiply(j));
            piece.teleport(level(at));
            Quaternionf face = facePlane();
            ease(piece, box(width, CELL * 0.55f * heightScale / Math.max(0.5f, n / 14f), 0.08f, face), 2);
        }
    }

    // ------------------------------------------------------------------ fracture: walls appear compressed on the seam

    private void stageWalls() {
        boolean crowded = LIVE.size() > MAX_LIVE - 120;
        int w = crowded ? WALL_W - 2 : WALL_W;
        int h = crowded ? WALL_H - 1 : WALL_H;
        wallCols = w;
        wallRows = h;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int side = -1; side <= 1; side += 2) {
            List<BlockDisplay> wall = side < 0 ? leftWall : rightWall;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    Material mat = terrainFor(y, h, random);
                    Location at = cellAt(side, x, y, w, h, 0.02f);
                    wall.add(spawn(at, mat.createBlockData(), y == h - 1 ? LIT : DIM, null, tiny()));
                }
            }
        }
    }

    private Material terrainFor(int y, int h, ThreadLocalRandom random) {
        if (y >= h - 1) {
            return Material.GRASS_BLOCK;
        }
        if (y >= h - 2) {
            return random.nextBoolean() ? Material.DIRT : Material.COARSE_DIRT;
        }
        if (y <= 1) {
            return random.nextBoolean() ? Material.DEEPSLATE : Material.STONE;
        }
        return TERRAIN[random.nextInt(TERRAIN.length)];
    }

    private void stageVoid() {
        for (int i = 0; i < VOID_PANELS; i++) {
            double t = (i / (double) (VOID_PANELS - 1)) - 0.5;
            Location at = focus.clone().add(up.clone().multiply(t * WALL_H * CELL * 0.95));
            Material mat = i % 3 == 0 ? Material.BLACK_CONCRETE
                    : i % 3 == 1 ? Material.PURPLE_CONCRETE : Material.CRYING_OBSIDIAN;
            voidPlane.add(spawn(at, mat.createBlockData(), LIT, VOID_GLOW, tiny()));
        }
    }

    private void stageEdges() {
        for (int i = 0; i < WALL_H + 2; i++) {
            double t = (i / (double) (WALL_H + 1)) - 0.5;
            Location at = focus.clone().add(up.clone().multiply(t * WALL_H * CELL));
            edgeL.add(spawn(at, Material.MAGENTA_STAINED_GLASS.createBlockData(), LIT, EDGE, tiny()));
            edgeR.add(spawn(at, Material.PURPLE_STAINED_GLASS.createBlockData(), LIT, EDGE, tiny()));
        }
    }

    private void stageScars() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location floor = floorNear(focus.clone().subtract(0, WALL_H * CELL * 0.4, 0));
        for (int i = 0; i < SCAR_N; i++) {
            double along = (i / (double) (SCAR_N - 1) - 0.5) * WALL_W * CELL * 1.4;
            double side = (random.nextDouble() - 0.5) * 1.2;
            Location at = floor.clone()
                    .add(right.clone().multiply(along))
                    .add(forward.clone().multiply(side * 0.4));
            Material mat = i % 2 == 0 ? Material.BLACK_CONCRETE : Material.CRYING_OBSIDIAN;
            scars.add(spawn(at, mat.createBlockData(), DIM, VOID_GLOW, tiny()));
        }
    }

    private void fracture() {
        phaseTick++;
        int span = FRACTURE - TENSION;
        double u = phaseTick / (double) span;
        poseSeam(0.08f + (float) u * 0.2f, 1.0f, 0.12f + (float) u * 0.15f);
        /* Walls still clamped shut — barely visible as a compressed mass. */
        poseWalls(0.04f, 0.15f + (float) u * 0.55f);
        poseVoid(0.02f, (float) u * 0.35f);
        poseEdges(0.04f);
        poseScars((float) u * 0.7f);

        if (phaseTick % 3 == 0) {
            world.playSound(focus, Sound.BLOCK_DEEPSLATE_BREAK, 0.35f, 0.6f + (float) u);
            world.playSound(focus, Sound.BLOCK_GRASS_BREAK, 0.25f, 0.5f);
        }
        if (phaseTick % 2 == 0) {
            Location floor = floorNear(focus.clone().subtract(0, WALL_H * CELL * 0.4, 0));
            world.spawnParticle(Particle.BLOCK, floor, 8, 1.5, 0.05, 0.2, 0.02, Material.DIRT.createBlockData());
            world.spawnParticle(Particle.DUST, focus, 10, 0.1, 2.0, 0.1, 0, new Particle.DustOptions(EDGE, 1.2f));
        }
        if (phaseTick >= span) {
            phase = Phase.PEEL;
            phaseTick = 0;
            targetGap = MAX_GAP;
            world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 0.55f, 0.35f);
            world.playSound(focus, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.35f);
            world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.4f);
            world.playSound(focus, Sound.ITEM_TRIDENT_THUNDER, 0.9f, 0.45f);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§5§l∥ WORLD SPLIT"));
            player.sendMessage("§5✦ §7The world tears.");
        }
    }

    // ------------------------------------------------------------------ peel open

    private void peel() {
        phaseTick++;
        int span = PEEL - FRACTURE;
        double u = easeOutQuart(phaseTick / (double) span);
        gap = (float) (0.04 + (MAX_GAP - 0.04) * u);
        poseSeam(0.02f, 1.0f, 0.05f);
        poseWalls(gap, 1.0f);
        poseVoid(gap * 0.92f, 1.0f);
        poseEdges(gap);
        poseScars(1.0f);
        suckParticles(0.4 + u * 0.8);

        if (phaseTick == 1 || phaseTick == 4 || phaseTick == 8) {
            world.playSound(focus, Sound.BLOCK_ANVIL_LAND, 0.45f, 0.35f + phaseTick * 0.05f);
            world.playSound(focus, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.35f, 0.55f);
        }
        if (phaseTick >= span) {
            phase = Phase.RIFT;
            phaseTick = 0;
            discardSeam();
            spawnDebrisBurst(true);
            splashLight();
            release();
        }
    }

    // ------------------------------------------------------------------ rift dwell — the "what the fuck" beat

    private void rift() {
        phaseTick++;
        int span = RIFT - PEEL;
        double breathe = 1.0 + 0.045 * Math.sin(phaseTick * 0.22);
        gap = (float) (MAX_GAP * breathe);
        poseWalls(gap, 1.0f);
        poseVoid(gap * 0.95f, 1.0f);
        poseEdges(gap);
        poseScars(1.0f);
        tickDebris();
        suckParticles(1.2);
        wrongSkyHints();

        if (phaseTick % 7 == 0) {
            spawnDebrisBurst(phaseTick % 14 == 0);
        }
        if (phaseTick % 5 == 0) {
            world.playSound(focus, Sound.BLOCK_PORTAL_AMBIENT, 0.7f, 0.45f + (phaseTick % 20) * 0.02f);
            world.playSound(focus, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.25f, 0.7f);
        }
        if (phaseTick == 8 || phaseTick == 24) {
            world.playSound(focus, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.55f, 0.3f);
            world.playSound(focus, Sound.BLOCK_END_PORTAL_SPAWN, 0.4f, 0.55f);
        }
        if (phaseTick == span / 2) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§8…something looks back"));
        }
        if (phaseTick >= span) {
            phase = Phase.SEAL;
            phaseTick = 0;
            targetGap = 0.02f;
            for (Player near : world.getPlayers()) {
                if (near.getWorld() == world && near.getLocation().distanceSquared(focus) < 80 * 80) {
                    near.stopAllSounds();
                }
            }
            world.playSound(focus, Sound.BLOCK_BELL_RESONATE, 1.0f, 0.4f);
            world.playSound(focus, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.1f, 0.55f);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§f∥ sealing…"));
        }
    }

    // ------------------------------------------------------------------ seal shut

    private void seal() {
        phaseTick++;
        int span = SEAL - RIFT;
        double u = easeInQuart(phaseTick / (double) span);
        gap = (float) (MAX_GAP * (1.0 - u) + 0.02 * u);
        poseWalls(gap, 1.0f);
        poseVoid(Math.max(0.02f, gap * 0.9f), (float) (1.0 - u));
        poseEdges(gap);
        poseScars((float) (1.0 - u * 0.85));
        tickDebris();

        if (phaseTick == span - 2) {
            world.spawnParticle(Particle.FLASH, focus, 6, 0.8, 2.0, 0.3, 0);
            world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.4f);
            world.playSound(focus, Sound.BLOCK_ANVIL_LAND, 1.3f, 0.35f);
            world.playSound(focus, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.4f);
            world.playSound(focus, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.4f);
            world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.55f);
        }
        if (phaseTick >= span) {
            clearWallsAndVoid();
            stageSeam();
            phase = Phase.AFTER;
            phaseTick = 0;
            player.sendMessage("§8…Minecraft remembers itself.");
        }
    }

    private void after() {
        phaseTick++;
        int span = TOTAL - AFTER;
        double u = phaseTick / (double) span;
        poseSeam(0.1f * (float) (1.0 - u), (float) (1.0 - u * 0.5), 0.02f);
        tickDebris();
        if (phaseTick % 2 == 0) {
            world.spawnParticle(Particle.DUST, focus, 4, 0.05, 1.5, 0.05, 0,
                    new Particle.DustOptions(mix(EDGE, SEAM, u), 0.8f));
        }
        if (phaseTick == 4) {
            world.playSound(focus, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.6f);
            world.playSound(focus, Sound.BLOCK_BEACON_DEACTIVATE, 0.45f, 0.8f);
        }
        if (phaseTick >= span && debris.isEmpty()) {
            phase = Phase.DONE;
        }
    }

    // ------------------------------------------------------------------ poses

    /** Walls are vertical slabs separated along {@code right}; cells span forward × up. */
    private Location cellAt(int side, int col, int row, int cols, int rows, float gapNow) {
        double alongForward = (col - (cols - 1) * 0.5) * CELL;
        double alongUp = (row - (rows - 1) * 0.5) * CELL;
        double alongRight = side * (gapNow * 0.5 + CELL * 0.5);
        return focus.clone()
                .add(right.clone().multiply(alongRight))
                .add(forward.clone().multiply(alongForward))
                .add(up.clone().multiply(alongUp));
    }

    private void poseWalls(float gapNow, float scale) {
        poseWallSide(leftWall, -1, gapNow, scale);
        poseWallSide(rightWall, 1, gapNow, scale);
    }

    private void poseWallSide(List<BlockDisplay> wall, int side, float gapNow, float scale) {
        if (wall.isEmpty()) {
            return;
        }
        int cols = wallCols;
        int rows = wallRows;
        Quaternionf face = facePlane();
        float s = CELL * Math.max(0.05f, scale);
        for (int i = 0; i < wall.size(); i++) {
            BlockDisplay cell = wall.get(i);
            if (cell == null || !cell.isValid()) {
                continue;
            }
            int col = i % cols;
            int row = i / cols;
            Location at = cellAt(side, col, row, cols, rows, gapNow);
            cell.teleport(level(at));
            if (scale < 0.08f) {
                ease(cell, tiny(), 2);
            } else {
                ease(cell, box(s * 0.98f, s * 0.98f, s * 0.98f, face), 2);
            }
        }
    }

    private void poseVoid(float gapNow, float opacityScale) {
        float width = Math.max(0.04f, gapNow * 0.95f);
        float tall = WALL_H * CELL * 1.05f * Math.max(0.05f, opacityScale);
        Quaternionf face = facePlane();
        int n = voidPlane.size();
        for (int i = 0; i < n; i++) {
            BlockDisplay panel = voidPlane.get(i);
            if (panel == null || !panel.isValid()) {
                continue;
            }
            double t = n <= 1 ? 0 : (i / (double) (n - 1)) - 0.5;
            Location at = focus.clone().add(up.clone().multiply(t * WALL_H * CELL * opacityScale));
            panel.teleport(level(at));
            if (opacityScale < 0.06f) {
                ease(panel, tiny(), 2);
            } else {
                ease(panel, box(width, tall / Math.max(4, n) * 1.15f, 0.12f, face), 2);
            }
        }
    }

    private void poseEdges(float gapNow) {
        poseEdgeSide(edgeL, -1, gapNow);
        poseEdgeSide(edgeR, 1, gapNow);
    }

    private void poseEdgeSide(List<BlockDisplay> edge, int side, float gapNow) {
        int n = edge.size();
        Quaternionf face = facePlane();
        for (int i = 0; i < n; i++) {
            BlockDisplay piece = edge.get(i);
            if (piece == null || !piece.isValid()) {
                continue;
            }
            double t = n <= 1 ? 0 : (i / (double) (n - 1)) - 0.5;
            Location at = focus.clone()
                    .add(right.clone().multiply(side * gapNow * 0.5))
                    .add(up.clone().multiply(t * WALL_H * CELL));
            piece.teleport(level(at));
            ease(piece, box(0.12f, CELL * 0.7f, 0.12f, face), 2);
        }
    }

    private void poseScars(float scale) {
        Location floor = floorNear(focus.clone().subtract(0, WALL_H * CELL * 0.4, 0));
        int n = scars.size();
        for (int i = 0; i < n; i++) {
            BlockDisplay scar = scars.get(i);
            if (scar == null || !scar.isValid()) {
                continue;
            }
            double along = (i / (double) Math.max(1, n - 1) - 0.5) * WALL_W * CELL * 1.4;
            Location at = floor.clone().add(right.clone().multiply(along)).add(0, 0.04, 0);
            scar.teleport(level(at));
            float len = (float) (0.35 + scale * 0.55);
            float wide = 0.08f + scale * 0.12f;
            Quaternionf rot = new Quaternionf().rotateY(yawOf(right)).rotateX((float) Math.toRadians(90));
            if (scale < 0.05f) {
                ease(scar, tiny(), 2);
            } else {
                ease(scar, box(len, wide, 0.06f, rot), 2);
            }
        }
    }

    private Quaternionf facePlane() {
        /* Orient a block so its face looks along forward (toward/away player). */
        float yaw = yawOf(forward);
        return new Quaternionf().rotateY(yaw);
    }

    private static float yawOf(Vector v) {
        return (float) Math.atan2(-v.getX(), v.getZ());
    }

    // ------------------------------------------------------------------ particles / debris / damage

    private void suckParticles(double strength) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            double a = random.nextDouble(Math.PI * 2);
            double r = 1.5 + random.nextDouble(6.0);
            Location from = focus.clone()
                    .add(right.clone().multiply(Math.cos(a) * r))
                    .add(up.clone().multiply((random.nextDouble() - 0.5) * WALL_H * CELL))
                    .add(forward.clone().multiply(Math.sin(a) * r * 0.35));
            Vector to = focus.toVector().subtract(from.toVector());
            if (to.lengthSquared() < 0.01) {
                continue;
            }
            to.normalize().multiply(0.15 * strength);
            world.spawnParticle(Particle.REVERSE_PORTAL, from, 0, to.getX(), to.getY(), to.getZ(), 1.0);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.DUST, from, 1, 0, 0, 0, 0, new Particle.DustOptions(VOID_GLOW, 1.1f));
            }
        }
        world.spawnParticle(Particle.PORTAL, focus, (int) (8 * strength), gap * 0.2, WALL_H * 0.3, 0.15, 0.4);
    }

    private void wrongSkyHints() {
        if (phaseTick % 2 != 0) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            double t = (ThreadLocalRandom.current().nextDouble() - 0.5) * WALL_H * CELL;
            Location at = focus.clone().add(up.clone().multiply(t));
            world.spawnParticle(Particle.DUST, at, 1, gap * 0.15, 0.2, 0.05, 0, new Particle.DustOptions(WRONG, 1.3f));
            world.spawnParticle(Particle.END_ROD, at, 1, 0.05, 0.2, 0.05, 0.01);
        }
    }

    private void spawnDebrisBurst(boolean intoRift) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int n = LIVE.size() > MAX_LIVE - 40 ? 6 : (intoRift ? 10 : 8);
        for (int i = 0; i < n; i++) {
            Material mat = intoRift
                    ? WRONG_WORLD[random.nextInt(WRONG_WORLD.length)]
                    : TERRAIN[random.nextInt(TERRAIN.length)];
            double t = (random.nextDouble() - 0.5) * WALL_H * CELL * 0.8;
            int side = random.nextBoolean() ? -1 : 1;
            Location start = focus.clone()
                    .add(right.clone().multiply(side * gap * 0.45))
                    .add(up.clone().multiply(t))
                    .add(forward.clone().multiply((random.nextDouble() - 0.5) * 2.0));
            BlockDisplay chunk = spawn(start, mat.createBlockData(), LIT,
                    intoRift ? VOID_GLOW : null, centered(0.01f, new Quaternionf()));
            Vector vel;
            if (intoRift) {
                vel = focus.toVector().subtract(start.toVector()).normalize()
                        .multiply(random.nextDouble(0.15, 0.4))
                        .add(new Vector(0, random.nextDouble(-0.1, 0.15), 0));
            } else {
                vel = right.clone().multiply(side * random.nextDouble(0.2, 0.55))
                        .add(up.clone().multiply(random.nextDouble(0.15, 0.45)))
                        .add(forward.clone().multiply((random.nextDouble() - 0.5) * 0.3));
            }
            debris.add(new Debris(chunk, start, vel, (float) random.nextDouble(0.35, 0.75),
                    24 + random.nextInt(16), intoRift));
        }
    }

    private void tickDebris() {
        Iterator<Debris> it = debris.iterator();
        while (it.hasNext()) {
            Debris d = it.next();
            if (!d.step()) {
                discard(d.display);
                it.remove();
            }
        }
    }

    private void splashLight() {
        if (hit || damage <= 0) {
            return;
        }
        hit = true;
        for (Entity entity : world.getNearbyEntities(focus, SPLASH_R, SPLASH_R, SPLASH_R)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            Vector rel = living.getLocation().toVector().subtract(focus.toVector());
            ScriptedHits.run(() -> living.damage(damage, player));
            Vector away = rel.setY(0);
            if (away.lengthSquared() < 0.01) {
                away = right.clone();
            }
            living.setVelocity(away.normalize().multiply(1.1).setY(0.55));
            living.setFallDistance(0f);
        }
    }

    private void discardSeam() {
        for (BlockDisplay piece : seam) {
            discard(piece);
        }
        seam.clear();
    }

    private void clearWallsAndVoid() {
        for (BlockDisplay cell : leftWall) {
            discard(cell);
        }
        leftWall.clear();
        for (BlockDisplay cell : rightWall) {
            discard(cell);
        }
        rightWall.clear();
        for (BlockDisplay panel : voidPlane) {
            discard(panel);
        }
        voidPlane.clear();
        for (BlockDisplay e : edgeL) {
            discard(e);
        }
        edgeL.clear();
        for (BlockDisplay e : edgeR) {
            discard(e);
        }
        edgeR.clear();
        for (BlockDisplay scar : scars) {
            discard(scar);
        }
        scars.clear();
    }

    // ------------------------------------------------------------------ helpers

    private Location floorNear(Location at) {
        RayTraceResult hit = world.rayTraceBlocks(at.clone().add(0, 2.0, 0), new Vector(0, -1, 0), 48.0,
                FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            return level(hit.getHitPosition().toLocation(world));
        }
        Location out = at.clone();
        out.setY(world.getHighestBlockYAt(at) + 0.05);
        return level(out);
    }

    private static Location level(Location at) {
        Location out = at.clone();
        out.setYaw(0);
        out.setPitch(0);
        return out;
    }

    private static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }

    private static double easeOutQuart(double x) {
        return 1.0 - Math.pow(1.0 - Math.max(0, Math.min(1, x)), 4);
    }

    private static double easeInQuart(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * x * x;
    }

    private static Transformation tiny() {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf());
    }

    private static Transformation centered(float size, Quaternionf rot) {
        Quaternionf r = new Quaternionf(rot);
        float s = Math.max(0.001f, size);
        Vector3f half = new Quaternionf(r).transform(new Vector3f(s / 2f, s / 2f, s / 2f));
        return new Transformation(half.negate(), r, new Vector3f(s, s, s), new Quaternionf());
    }

    private static Transformation box(float x, float y, float z, Quaternionf rot) {
        Quaternionf r = new Quaternionf(rot);
        Vector3f half = new Quaternionf(r).transform(new Vector3f(x / 2f, y / 2f, z / 2f));
        return new Transformation(half.negate(), r,
                new Vector3f(Math.max(0.001f, x), Math.max(0.001f, y), Math.max(0.001f, z)), new Quaternionf());
    }

    private static void ease(BlockDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private static BlockDisplay spawn(Location at, BlockData data, Display.Brightness light, Color glow,
                                      Transformation initial) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(level(at.clone()), BlockDisplay.class, spawned -> {
                spawned.setBlock(data);
                spawned.setPersistent(false);
                if (light != null) {
                    spawned.setBrightness(light);
                }
                spawned.setTeleportDuration(2);
                spawned.setInterpolationDuration(0);
                if (glow != null) {
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(glow);
                }
                spawned.setTransformation(initial != null ? initial : tiny());
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void clearAll() {
        discardSeam();
        clearWallsAndVoid();
        for (Debris d : debris) {
            discard(d.display);
        }
        debris.clear();
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
