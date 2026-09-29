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
 * Cataclysm Rod (cataclysm_rod) — Absolute Nova → Seismic Charge.
 * Right-click launches a molten star-core bolt. On impact the sky winds up, hushes,
 * collapses, and detonates a multi-shell Absolute Nova. After the sky settles, a
 * Star-Wars-style seismic charge expands across the floor in silence, then the
 * delayed ground catastrophe hits — the punchline of the showcase.
 */
public final class CataclysmRodNova {

    private static final int FLIGHT_MAX = 28;
    private static final double FLIGHT_SPEED = 1.55;
    private static final double MAX_RANGE = 42.0;

    /* Absolute Nova (sky) — grow, slight overshoot, then reverse-collapse into silence. */
    private static final int WINDUP = 18;
    private static final int HUSH = WINDUP + 8;
    private static final int BLAST = HUSH + 4;
    private static final int NOVA_PEAK = BLAST + 30;   /* grow finishes / overshoot starts */
    private static final int NOVA_HOLD = NOVA_PEAK + 4; /* tiny extra swell */
    private static final int NOVA_COLLAPSE = NOVA_HOLD + 28; /* mirror shrink back to nothing */
    /* Seismic Charge (floor) — punchline after the sky has fully retracted. */
    private static final int SEISMIC_ARM = NOVA_COLLAPSE + 6;
    private static final int SEISMIC_EXPAND = SEISMIC_ARM + 34;
    private static final int SEISMIC_BOOM = SEISMIC_EXPAND + 6;
    private static final int SEISMIC_AFTER = SEISMIC_BOOM + 40;
    private static final int TOTAL = SEISMIC_AFTER + 22;

    private static final double BLAST_RADIUS = 11.0;
    private static final double CORE_RADIUS = 4.5;
    private static final double SEISMIC_RADIUS = 22.0;
    private static final float SHELL_REACH = 14f;
    private static final int MAX_LIVE = 320;
    private static final int RING_SEGS = 18;
    private static final int SEAL_OUTER = 28;
    private static final int SEAL_MID = 22;
    private static final int SEAL_INNER = 16;
    private static final int SEAL_SPOKES = 12;
    private static final int SPIKES = 14;
    private static final int EJECTA_N = 18;

    private static final Color HOT = Color.fromRGB(255, 250, 230);
    private static final Color SOLAR = Color.fromRGB(255, 210, 70);
    private static final Color EMBER = Color.fromRGB(255, 110, 30);
    private static final Color CRIMSON = Color.fromRGB(200, 20, 40);
    private static final Color VIOLET = Color.fromRGB(160, 60, 255);
    private static final Color PHOTON = Color.fromRGB(230, 245, 255);
    private static final Color VOID = Color.fromRGB(20, 5, 40);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private static final Material[] SHELL_MATS = {
            Material.YELLOW_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS,
            Material.RED_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS
    };
    private static final float[] SHELL_SCALE = {0.55f, 0.75f, 1.0f, 1.25f, 1.55f};
    private static final int[] SHELL_TICKS = {10, 14, 18, 24, 30};
    private static final Color[] SHELL_TINT = {HOT, SOLAR, EMBER, CRIMSON, VIOLET};

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final Player player;
    private final World world;
    private final double damage;
    private final Runnable done;

    private final List<BlockDisplay> shells = new ArrayList<>();
    private final List<BlockDisplay> rings = new ArrayList<>();
    private final List<BlockDisplay> rays = new ArrayList<>();
    private final List<Vector3f> rayReach = new ArrayList<>();
    private final List<Ejecta> ejecta = new ArrayList<>();
    private final BlockDisplay[] polar = new BlockDisplay[2];
    private final List<SealTile> seal = new ArrayList<>();
    private final BlockDisplay[] seismicPillar = new BlockDisplay[3];
    private final List<Ejecta> seismicEjecta = new ArrayList<>();

    private Location boltAt;
    private Vector boltVel;
    private Location focus;
    private Location skyFocus;
    private Location floorFocus;
    private BlockDisplay boltCore;
    private BlockDisplay boltShell;
    private BlockDisplay seed;
    private BlockDisplay floorSeed;
    private Quaternionf tilt = new Quaternionf();
    private float spin;
    private float sealSpin;
    private int tick;
    private int phaseTick;
    private Phase phase = Phase.FLIGHT;
    private boolean released;
    private boolean damaged;
    private boolean seismicDamaged;

    private enum Phase {
        FLIGHT, WINDUP, HUSH, BLAST, NOVA_PEAK, NOVA_COLLAPSE,
        SEISMIC_ARM, SEISMIC_EXPAND, SEISMIC_BOOM, SEISMIC_AFTER, DONE
    }

    private static final class SealTile {
        final BlockDisplay display;
        final int ring; /* 0 outer, 1 mid, 2 inner, 3 spoke */
        final double baseAngle;
        final float length;

        SealTile(BlockDisplay display, int ring, double baseAngle, float length) {
            this.display = display;
            this.ring = ring;
            this.baseAngle = baseAngle;
            this.length = length;
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
        new CataclysmRodNova(plugin, player, damage, done).start();
    }

    private CataclysmRodNova(JavaPlugin plugin, Player player, double damage, Runnable done) {
        this.plugin = plugin;
        this.player = player;
        this.world = player.getWorld();
        this.damage = damage;
        this.done = done;
    }

    private void start() {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        boltAt = eye.clone().add(dir.clone().multiply(1.2));
        boltVel = dir.multiply(FLIGHT_SPEED);
        boltCore = spawn(boltAt, Material.PEARLESCENT_FROGLIGHT.createBlockData(), LIT, HOT, centered(0.45f, new Quaternionf()));
        boltShell = spawn(boltAt, Material.YELLOW_STAINED_GLASS.createBlockData(), LIT, SOLAR, centered(0.85f, new Quaternionf()));
        if (boltCore != null) {
            boltCore.setTeleportDuration(1);
        }
        if (boltShell != null) {
            boltShell.setTeleportDuration(1);
        }

        world.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.55f);
        world.playSound(eye, Sound.ITEM_FIRECHARGE_USE, 0.9f, 0.45f);
        world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 0.5f);
        player.sendMessage("§c✦ Cataclysm §7— star-core loosed…");

        new BukkitRunnable() {
            @Override
            public void run() {
                boolean gone = !player.isOnline() || player.isDead() || player.getWorld() != world;
                if (gone && phase.ordinal() < Phase.BLAST.ordinal()) {
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
            case FLIGHT -> {
                flight();
                yield false;
            }
            case WINDUP -> {
                windup();
                yield false;
            }
            case HUSH -> {
                hush();
                yield false;
            }
            case BLAST -> {
                blast();
                yield false;
            }
            case NOVA_PEAK -> {
                novaPeak();
                yield false;
            }
            case NOVA_COLLAPSE -> {
                novaCollapse();
                yield false;
            }
            case SEISMIC_ARM -> {
                seismicArm();
                yield false;
            }
            case SEISMIC_EXPAND -> {
                seismicExpand();
                yield false;
            }
            case SEISMIC_BOOM -> {
                seismicBoom();
                yield false;
            }
            case SEISMIC_AFTER -> {
                seismicAfter();
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

    // ------------------------------------------------------------------ flight

    private void flight() {
        Location prev = boltAt.clone();
        boltAt.add(boltVel);
        RayTraceResult hit = world.rayTraceBlocks(prev, boltVel.clone().normalize(),
                Math.max(0.2, boltVel.length()), FluidCollisionMode.NEVER, true);
        boolean impact = false;
        if (hit != null && hit.getHitPosition() != null) {
            boltAt = hit.getHitPosition().toLocation(world);
            if (hit.getHitBlockFace() != null) {
                boltAt.add(hit.getHitBlockFace().getDirection().multiply(0.35));
            }
            impact = true;
        }
        if (!impact) {
            for (Entity entity : world.getNearbyEntities(boltAt, 1.1, 1.1, 1.1)) {
                if (TestPrototypeAbilities.isCombatTarget(entity)) {
                    impact = true;
                    break;
                }
            }
        }
        double traveled = boltAt.distance(player.getEyeLocation());
        if (impact || tick >= FLIGHT_MAX || traveled >= MAX_RANGE) {
            beginWindup();
            return;
        }

        Quaternionf spinQ = new Quaternionf().rotateY(tick * 0.45f).rotateX(tick * 0.28f);
        if (boltCore != null && boltCore.isValid()) {
            boltCore.teleport(level(boltAt));
            ease(boltCore, centered(0.4f + 0.08f * (float) Math.sin(tick * 0.6), spinQ), 1);
        }
        if (boltShell != null && boltShell.isValid()) {
            boltShell.teleport(level(boltAt));
            ease(boltShell, centered(0.75f + 0.12f * (float) Math.sin(tick * 0.5 + 1), spinQ), 1);
        }
        world.spawnParticle(Particle.FLAME, boltAt, 3, 0.12, 0.12, 0.12, 0.02);
        world.spawnParticle(Particle.END_ROD, boltAt, 2, 0.08, 0.08, 0.08, 0.01);
        world.spawnParticle(Particle.DUST, boltAt, 2, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(SOLAR, 1.4f));
        if (tick % 3 == 0) {
            world.playSound(boltAt, Sound.BLOCK_FIRE_AMBIENT, 0.35f, 1.6f);
        }
    }

    private void beginWindup() {
        discard(boltCore);
        discard(boltShell);
        boltCore = null;
        boltShell = null;
        focus = level(boltAt.clone().add(0, 1.8, 0));
        double roof = 16.0;
        RayTraceResult up = world.rayTraceBlocks(focus, new Vector(0, 1, 0), roof, FluidCollisionMode.NEVER, true);
        if (up != null && up.getHitPosition() != null) {
            roof = Math.max(6.0, up.getHitPosition().getY() - focus.getY() - 1.0);
        }
        skyFocus = level(focus.clone().add(0, Math.min(14.0, roof * 0.7 + 4.0), 0));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        tilt = new Quaternionf().rotateY((float) random.nextDouble(Math.PI * 2)).rotateX(0.32f);
        phase = Phase.WINDUP;
        phaseTick = 0;

        world.spawnParticle(Particle.FLASH, focus, 2, 0.2, 0.2, 0.2, 0);
        world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 0.55f, 1.4f);
        world.playSound(focus, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        world.playSound(focus, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.5f);
    }

    // ------------------------------------------------------------------ windup

    private void windup() {
        phaseTick++;
        double f = phaseTick / (double) WINDUP;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int inhale = 3 + phaseTick / 2;
        for (int i = 0; i < inhale; i++) {
            Vector dir = randomUnit(random);
            double r = 5.0 + random.nextDouble(6.0) * (1.0 - f);
            Location from = focus.clone().add(dir.clone().multiply(r));
            world.spawnParticle(Particle.END_ROD, from, 0, -dir.getX(), -dir.getY(), -dir.getZ(), r * 0.09);
            world.spawnParticle(Particle.DUST, from, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(mix(SOLAR, CRIMSON, f), 1.2f));
        }
        if (phaseTick % 2 == 0) {
            drawRing(focus, 0.6 + 4.5 * (1.0 - f), 40, new Particle.DustOptions(mix(HOT, EMBER, f), 1.5f), phaseTick * 0.08);
            drawRing(skyFocus, 2.0 + 3.0 * f, 28, new Particle.DustOptions(VIOLET, 1.1f), -phaseTick * 0.06);
        }
        if (phaseTick == 1 || phaseTick == 6 || phaseTick == 12 || phaseTick == 17) {
            float step = phaseTick / 18f;
            world.playSound(focus, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.1f - step * 0.5f, 1.9f - step * 1.2f);
            world.playSound(focus, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.55f - step * 0.15f);
        }
        if (phaseTick == 8) {
            world.playSound(focus, Sound.BLOCK_BEACON_DEACTIVATE, 1.1f, 0.55f);
            world.playSound(focus, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.9f, 0.5f);
        }
        if (phaseTick >= WINDUP) {
            phase = Phase.HUSH;
            phaseTick = 0;
            for (Player near : world.getPlayers()) {
                if (near.getWorld() == world && near.getLocation().distanceSquared(focus) < 90 * 90) {
                    near.stopAllSounds();
                }
            }
            seed = spawn(focus, Material.BLACK_CONCRETE.createBlockData(), LIT, VOID, centered(0.28f, new Quaternionf()));
            stageNova();
            world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.4f, 0.35f);
        }
    }

    // ------------------------------------------------------------------ hush

    private void hush() {
        phaseTick++;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = focus.clone();
        if (phaseTick >= 2) {
            at.add(random.nextDouble(-0.06, 0.06), random.nextDouble(-0.06, 0.06), random.nextDouble(-0.06, 0.06));
        }
        float size = phaseTick < 3 ? 0.32f : 0.06f;
        if (seed != null && seed.isValid()) {
            seed.teleport(level(at));
            ease(seed, centered(size, new Quaternionf().rotateY(phaseTick * 0.95f).rotateX(0.7f)), 1);
        }
        if (phaseTick == 3) {
            drawTiltedRing(focus, tilt, 4.0, 0.0, 44, new Particle.DustOptions(PHOTON, 0.8f));
        }
        if (phaseTick == 5) {
            drawTiltedRing(focus, tilt, 1.4, 0.0, 24, new Particle.DustOptions(HOT, 1.0f));
        }
        if (phaseTick >= (HUSH - WINDUP)) {
            phase = Phase.BLAST;
            phaseTick = 0;
            detonate();
        }
    }

    private void stageNova() {
        boolean crowded = LIVE.size() > MAX_LIVE;
        shells.add(spawn(skyFocus, Material.PEARLESCENT_FROGLIGHT.createBlockData(), LIT, HOT, tiny()));
        shells.add(spawn(skyFocus, Material.WHITE_STAINED_GLASS.createBlockData(), LIT, PHOTON, tiny()));
        for (Material mat : SHELL_MATS) {
            shells.add(spawn(skyFocus, mat.createBlockData(), LIT, null, tiny()));
            shells.add(spawn(skyFocus, mat.createBlockData(), LIT, null, tiny()));
        }

        int segs = crowded ? RING_SEGS / 2 : RING_SEGS;
        for (int ring = 0; ring < 3; ring++) {
            Material mat = ring == 0 ? Material.PEARLESCENT_FROGLIGHT
                    : ring == 1 ? Material.YELLOW_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
            Color glow = ring == 0 ? SOLAR : ring == 1 ? EMBER : VIOLET;
            for (int s = 0; s < segs; s++) {
                rings.add(spawn(skyFocus, mat.createBlockData(), LIT, glow, tiny()));
            }
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int spikes = crowded ? SPIKES / 2 : SPIKES;
        for (int i = 0; i < spikes; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / spikes;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double a = i * 2.39996 + random.nextDouble(0.4);
            float len = SHELL_REACH * (float) random.nextDouble(0.5, 0.9);
            rayReach.add(new Vector3f((float) (Math.cos(a) * r), (float) y, (float) (Math.sin(a) * r)).mul(len));
            rays.add(spawn(skyFocus, Material.PEARLESCENT_FROGLIGHT.createBlockData(), LIT, HOT, tiny()));
        }
        Vector3f up = tilt.transform(new Vector3f(0, 1, 0)).mul(SHELL_REACH * 1.5f);
        Vector3f down = tilt.transform(new Vector3f(0, -1, 0)).mul(SHELL_REACH * 0.7f);
        rayReach.add(up);
        rays.add(spawn(skyFocus, Material.WHITE_STAINED_GLASS.createBlockData(), LIT, PHOTON, tiny()));
        rayReach.add(down);
        rays.add(spawn(skyFocus, Material.WHITE_STAINED_GLASS.createBlockData(), LIT, PHOTON, tiny()));

        polar[0] = spawn(skyFocus, Material.WHITE_STAINED_GLASS.createBlockData(), LIT, PHOTON, tiny());
        polar[1] = spawn(skyFocus, Material.WHITE_STAINED_GLASS.createBlockData(), LIT, PHOTON, tiny());

        Material[] rock = {
                Material.MAGMA_BLOCK, Material.SHROOMLIGHT, Material.OCHRE_FROGLIGHT,
                Material.CRYING_OBSIDIAN, Material.PEARLESCENT_FROGLIGHT, Material.GOLD_BLOCK
        };
        Color[] heat = {EMBER, SOLAR, HOT, VIOLET, PHOTON, SOLAR};
        int n = crowded ? EJECTA_N / 2 : EJECTA_N;
        for (int i = 0; i < n; i++) {
            int kind = i % rock.length;
            BlockDisplay chunk = spawn(focus, rock[kind].createBlockData(), LIT, heat[kind], centered(0.01f, new Quaternionf()));
            Vector vel = randomUnit(random).multiply(random.nextDouble(0.55, 1.15));
            vel.setY(Math.abs(vel.getY()) * 0.85 + 0.35);
            ejecta.add(new Ejecta(chunk, focus.clone(), vel, heat[kind],
                    (float) random.nextDouble(0.35, 0.85), 18 + random.nextInt(14)));
        }
    }

    // ------------------------------------------------------------------ blast

    private void detonate() {
        discard(seed);
        seed = null;
        world.spawnParticle(Particle.FLASH, focus, 10, 1.6, 1.6, 1.6, 0);
        world.spawnParticle(Particle.FLASH, skyFocus, 6, 2.0, 1.2, 2.0, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, focus, 5, 1.2, 1.0, 1.2, 0);
        for (int i = 0; i < 14; i++) {
            double a = Math.PI * 2 * i / 14;
            Vector3f p = tilt.transform(new Vector3f((float) (Math.cos(a) * 2.8), 0, (float) (Math.sin(a) * 2.8)));
            world.spawnParticle(Particle.SONIC_BOOM, focus.clone().add(p.x, p.y, p.z), 1, 0, 0, 0, 0);
        }
        burst(Particle.END_ROD, 160, 1.8);
        burst(Particle.FIREWORK, 100, 1.4);
        burst(Particle.FLAME, 80, 1.1);
        burst(Particle.SOUL_FIRE_FLAME, 50, 0.9);
        burst(Particle.ELECTRIC_SPARK, 50, 1.3);
        world.spawnParticle(Particle.DUST, focus, 90, 2.2, 2.2, 2.2, 0, new Particle.DustOptions(HOT, 2.5f));
        world.spawnParticle(Particle.DUST, focus, 70, 3.2, 3.2, 3.2, 0, new Particle.DustOptions(SOLAR, 2.1f));
        world.spawnParticle(Particle.DUST, focus, 60, 4.2, 4.2, 4.2, 0, new Particle.DustOptions(VIOLET, 1.9f));
        world.spawnParticle(Particle.DUST, focus, 50, 5.0, 5.0, 5.0, 0, new Particle.DustOptions(CRIMSON, 1.7f));
        drawTiltedRing(focus, tilt, 4.5, 0.0, 52, new Particle.DustOptions(HOT, 2.1f));

        for (Player near : world.getPlayers()) {
            if (near.getWorld() != world || near.getLocation().distanceSquared(focus) > 64 * 64) {
                continue;
            }
            Location eye = near.getEyeLocation();
            near.spawnParticle(Particle.FLASH, eye.clone().add(eye.getDirection().multiply(1.2)), 2, 0, 0, 0, 0);
        }

        world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.45f);
        world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 1.7f, 0.75f);
        world.playSound(focus, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.45f);
        world.playSound(focus, Sound.ITEM_TRIDENT_THUNDER, 1.7f, 0.5f);
        world.playSound(focus, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.5f, 0.45f);
        world.playSound(focus, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.55f);
        world.playSound(focus, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST_FAR, 1.7f, 0.55f);
        world.playSound(focus, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.7f);
        world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.6f, 0.45f);
        world.playSound(focus, Sound.BLOCK_END_PORTAL_SPAWN, 0.9f, 1.1f);
        world.playSound(focus, Sound.ITEM_TOTEM_USE, 1.0f, 0.55f);
        world.playSound(focus, Sound.ENTITY_WITHER_SPAWN, 0.55f, 0.7f);

        if (player.isOnline() && player.getLocation().distanceSquared(focus) > 196.0) {
            world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.4f);
            world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 0.5f);
        }

        splashDamage();
        release();
    }

    private void splashDamage() {
        if (damaged || damage <= 0) {
            return;
        }
        damaged = true;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Entity entity : world.getNearbyEntities(focus, BLAST_RADIUS, BLAST_RADIUS, BLAST_RADIUS)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            Vector rel = living.getLocation().toVector().subtract(focus.toVector());
            double d = Math.hypot(rel.getX(), rel.getZ());
            if (d > BLAST_RADIUS) {
                continue;
            }
            double falloff = d <= CORE_RADIUS ? 1.0
                    : 1.0 - 0.55 * (d - CORE_RADIUS) / (BLAST_RADIUS - CORE_RADIUS);
            double amount = damage * falloff;
            ScriptedHits.run(() -> living.damage(amount, player));
            Vector away = rel.setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(random.nextDouble(-1, 1), 0, random.nextDouble(-1, 1));
            }
            living.setVelocity(away.normalize().multiply(1.4).setY(0.75));
            living.setFallDistance(0f);
        }
    }

    private void blast() {
        phaseTick++;
        spin += 0.18f;
        tickShells(phaseTick, 1.0f);
        tickRings(phaseTick, 1.0f);
        tickRays(phaseTick, 1.0f);
        tickPolar(phaseTick, 1.0f);
        tickEjecta();

        if (phaseTick % 2 == 0) {
            double r = SHELL_REACH * 1.15 * easeOutQuart(Math.min(1.0, phaseTick / 28.0));
            drawTiltedRing(skyFocus, tilt, r, 0.0, 56, new Particle.DustOptions(phaseTick % 4 == 0 ? HOT : SOLAR, 1.7f));
            drawTiltedRing(skyFocus, tilt, r * 0.72, r * 0.18, 36, new Particle.DustOptions(VIOLET, 1.3f));
            drawTiltedRing(skyFocus, tilt, r * 0.72, -r * 0.18, 36, new Particle.DustOptions(CRIMSON, 1.2f));
        }
        if (phaseTick == 2) {
            world.spawnParticle(Particle.FLASH, skyFocus, 5, 2.5, 2.5, 2.5, 0);
            world.playSound(focus, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE_FAR, 1.6f, 0.65f);
            world.playSound(focus, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 0.45f);
        }
        if (phaseTick == 4) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("§c§l✦ ABSOLUTE NOVA ✦"));
            world.playSound(focus, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.5f);
        }
        if (phaseTick == 10 || phaseTick == 20) {
            world.playSound(focus, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.4f);
        }
        if (phaseTick >= (NOVA_PEAK - BLAST)) {
            phase = Phase.NOVA_PEAK;
            phaseTick = 0;
        }
    }

    /** Brief overshoot — shells swell a hair past peak before the reverse collapse. */
    private void novaPeak() {
        phaseTick++;
        spin += 0.12f;
        float swell = 1.0f + 0.08f * (float) easeOutQuart(phaseTick / (double) Math.max(1, NOVA_HOLD - NOVA_PEAK));
        int growAge = NOVA_PEAK - BLAST;
        tickShells(growAge, swell);
        tickRings(growAge, swell);
        tickRays(growAge, swell);
        tickPolar(growAge, swell);
        tickEjecta();

        if (phaseTick == 1) {
            world.playSound(skyFocus, Sound.BLOCK_BEACON_AMBIENT, 0.7f, 1.4f);
            world.playSound(skyFocus, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f);
        }
        if (phaseTick >= (NOVA_HOLD - NOVA_PEAK)) {
            phase = Phase.NOVA_COLLAPSE;
            phaseTick = 0;
            world.playSound(skyFocus, Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 0.75f);
            world.playSound(skyFocus, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.55f, 1.3f);
        }
    }

    /** Mirror of the Absolute Nova grow — same shells, retracting the way they were born. */
    private void novaCollapse() {
        phaseTick++;
        spin += 0.08f;
        int span = NOVA_COLLAPSE - NOVA_HOLD;
        double retract = 1.0 - easeInQuart(phaseTick / (double) span);
        /* Start from the slight overshoot so the first frame still feels continuous. */
        float scale = (float) (1.08 * Math.max(0.0, retract));
        int growAge = NOVA_PEAK - BLAST;
        tickShells(growAge, scale);
        tickRings(growAge, scale);
        tickRays(growAge, scale);
        tickPolar(growAge, scale);
        tickEjecta();

        if (phaseTick == span / 2) {
            world.playSound(skyFocus, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 0.55f);
        }
        if (phaseTick >= span) {
            /* Sky nova fully gone — clean tear-down, then the floor answers. */
            for (BlockDisplay shell : shells) {
                discard(shell);
            }
            shells.clear();
            for (BlockDisplay tile : rings) {
                discard(tile);
            }
            rings.clear();
            for (BlockDisplay ray : rays) {
                discard(ray);
            }
            rays.clear();
            rayReach.clear();
            for (int i = 0; i < polar.length; i++) {
                discard(polar[i]);
                polar[i] = null;
            }
            for (Ejecta chunk : ejecta) {
                discard(chunk.display);
            }
            ejecta.clear();
            player.sendMessage("§8…the sky is quiet again.");
            player.sendActionBar(net.kyori.adventure.text.Component.text("§8…something answers from below"));
            beginSeismic();
        }
    }

    private void tickShells(int age, float scaleMul) {
        float mul = Math.max(0.001f, scaleMul);
        for (int i = 0; i < 2 && i < shells.size(); i++) {
            BlockDisplay flash = shells.get(i);
            if (flash == null || !flash.isValid()) {
                continue;
            }
            if (age >= 6 && mul >= 0.99f) {
                discard(flash);
                continue;
            }
            if (mul < 0.02f) {
                discard(flash);
                continue;
            }
            float size = (age < 2 ? 7.5f : 12.5f) * mul;
            Quaternionf rot = i == 0
                    ? new Quaternionf().rotateY(age * 0.22f + spin * 0.15f)
                    : new Quaternionf().rotateY(0.785f - age * 0.22f + spin * 0.1f).rotateX(0.615f).rotateZ(0.785f);
            ease(flash, centered(Math.max(0.001f, size), rot), 2);
            if (flash.getWorld() != null) {
                flash.teleport(level(skyFocus));
            }
        }
        for (int layer = 0; layer < SHELL_MATS.length; layer++) {
            int ticks = SHELL_TICKS[layer];
            float max = SHELL_REACH * SHELL_SCALE[layer];
            for (int k = 0; k < 2; k++) {
                int index = 2 + layer * 2 + k;
                if (index >= shells.size()) {
                    continue;
                }
                BlockDisplay shell = shells.get(index);
                if (shell == null || !shell.isValid()) {
                    continue;
                }
                float grow = age >= ticks ? 1.0f : (float) easeOutQuart((age + 1) / (double) ticks);
                float r = max * grow * mul;
                if (r < 0.04f) {
                    ease(shell, tiny(), 2);
                    continue;
                }
                float turn = age * 0.035f * (layer + 1) + spin * 0.02f * (layer + 1);
                Quaternionf rot = k == 0
                        ? new Quaternionf().rotateY(turn).rotateX(0.615f).rotateZ(0.785f)
                        : new Quaternionf().rotateY(-turn + 0.9f).rotateX(-0.4f).rotateZ(-0.5f);
                shell.teleport(level(skyFocus));
                ease(shell, centered(Math.max(0.001f, r), rot), 2);
            }
        }
    }

    private void tickRings(int age, float scaleMul) {
        if (rings.isEmpty()) {
            return;
        }
        int segs = rings.size() / 3;
        if (segs <= 0) {
            return;
        }
        float mul = Math.max(0.0f, scaleMul);
        double grow = easeOutQuart(Math.min(1.0, age / 16.0)) * mul;
        double[] scales = {0.55, 0.85, 1.15};
        double[] lifts = {0.0, 0.22, -0.22};
        for (int ring = 0; ring < 3; ring++) {
            double radius = SHELL_REACH * scales[ring] * grow;
            double lift = SHELL_REACH * lifts[ring] * grow;
            float len = (float) (Math.PI * 2 * Math.max(0.2, radius) / segs * 1.08);
            float wide = (float) Math.max(0.04, (0.35 + Math.min(1.0, age / 16.0) * 0.45) * mul);
            for (int s = 0; s < segs; s++) {
                int idx = ring * segs + s;
                if (idx >= rings.size()) {
                    continue;
                }
                BlockDisplay tile = rings.get(idx);
                if (tile == null || !tile.isValid()) {
                    continue;
                }
                double ang = spin * (ring == 1 ? -1 : 1) + Math.PI * 2 * s / segs;
                tile.teleport(level(skyFocus));
                if (mul < 0.03f || radius < 0.15) {
                    ease(tile, tiny(), 2);
                } else {
                    ease(tile, ringSeg(ang, radius, lift, len, wide, 0.08f), 2);
                }
            }
        }
    }

    private void tickRays(int age, float scaleMul) {
        float mul = Math.max(0.0f, scaleMul);
        float reach = (float) easeOutQuart(Math.min(1.0, age / 10.0)) * mul;
        float width = (age < 4 ? 0.35f : Math.max(0.04f, 0.35f - (age - 4) * 0.02f)) * Math.max(0.15f, mul);
        for (int i = 0; i < rays.size() && i < rayReach.size(); i++) {
            BlockDisplay ray = rays.get(i);
            if (ray == null || !ray.isValid()) {
                continue;
            }
            Vector3f tip = new Vector3f(rayReach.get(i)).mul(reach);
            ray.teleport(level(skyFocus));
            if (mul < 0.04f) {
                ease(ray, tiny(), 2);
            } else {
                ease(ray, rod(new Vector3f(), tip, width), 2);
            }
        }
    }

    private void tickPolar(int age, float scaleMul) {
        float mul = Math.max(0.0f, scaleMul);
        float len = (float) (SHELL_REACH * 1.6 * easeOutQuart(Math.min(1.0, age / 8.0)) * mul);
        float w = (age < 6 ? 0.55f : Math.max(0.04f, 0.55f - (age - 6) * 0.04f)) * Math.max(0.15f, mul);
        Vector3f axis = tilt.transform(new Vector3f(0, 1, 0));
        for (int i = 0; i < 2; i++) {
            BlockDisplay beam = polar[i];
            if (beam == null || !beam.isValid()) {
                continue;
            }
            float sign = i == 0 ? 1f : -1f;
            Vector3f tip = new Vector3f(axis).mul(sign * len);
            beam.teleport(level(skyFocus));
            if (mul < 0.04f) {
                ease(beam, tiny(), 2);
            } else {
                ease(beam, rod(new Vector3f(), tip, w), 2);
            }
        }
    }

    // ------------------------------------------------------------------ seismic charge (floor punchline)

    private void beginSeismic() {
        floorFocus = floorNear(focus).add(0, 0.14, 0);
        phase = Phase.SEISMIC_ARM;
        phaseTick = 0;
        sealSpin = 0f;

        for (Player near : world.getPlayers()) {
            if (near.getWorld() == world && near.getLocation().distanceSquared(floorFocus) < 96 * 96) {
                near.stopAllSounds();
            }
        }

        floorSeed = spawn(floorFocus, Material.BLACK_CONCRETE.createBlockData(), LIT, VOID,
                centered(0.22f, new Quaternionf()));
        stageSeismicSeal();

        world.playSound(floorFocus, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 0.28f);
        world.playSound(floorFocus, Sound.BLOCK_CONDUIT_AMBIENT, 0.7f, 0.4f);
        world.playSound(floorFocus, Sound.ENTITY_WARDEN_HEARTBEAT, 1.35f, 0.45f);
        player.sendMessage("§c✦ §7Seismic Charge — §fhold still.");
    }

    private void stageSeismicSeal() {
        boolean crowded = LIVE.size() > MAX_LIVE - 90;
        int outer = crowded ? SEAL_OUTER / 2 : SEAL_OUTER;
        int mid = crowded ? SEAL_MID / 2 : SEAL_MID;
        int inner = crowded ? SEAL_INNER / 2 : SEAL_INNER;
        int spokes = crowded ? SEAL_SPOKES / 2 : SEAL_SPOKES;

        for (int i = 0; i < outer; i++) {
            double a = Math.PI * 2 * i / outer;
            float len = (float) (Math.PI * 2 * SEISMIC_RADIUS / outer * 1.08);
            BlockDisplay tile = spawn(floorFocus, Material.YELLOW_STAINED_GLASS.createBlockData(), LIT, SOLAR, tiny());
            seal.add(new SealTile(tile, 0, a, len));
        }
        for (int i = 0; i < mid; i++) {
            double a = Math.PI * 2 * i / mid;
            float len = (float) (Math.PI * 2 * (SEISMIC_RADIUS * 0.68) / mid * 1.08);
            BlockDisplay tile = spawn(floorFocus, Material.ORANGE_STAINED_GLASS.createBlockData(), LIT, EMBER, tiny());
            seal.add(new SealTile(tile, 1, a, len));
        }
        for (int i = 0; i < inner; i++) {
            double a = Math.PI * 2 * i / inner;
            float len = (float) (Math.PI * 2 * (SEISMIC_RADIUS * 0.38) / inner * 1.08);
            BlockDisplay tile = spawn(floorFocus, Material.RED_STAINED_GLASS.createBlockData(), LIT, CRIMSON, tiny());
            seal.add(new SealTile(tile, 2, a, len));
        }
        for (int i = 0; i < spokes; i++) {
            double a = Math.PI * 2 * i / spokes;
            BlockDisplay tile = spawn(floorFocus, Material.PEARLESCENT_FROGLIGHT.createBlockData(), LIT, HOT, tiny());
            seal.add(new SealTile(tile, 3, a, 0.55f));
        }

        seismicPillar[0] = spawn(floorFocus, Material.WHITE_STAINED_GLASS.createBlockData(), LIT, PHOTON, tiny());
        seismicPillar[1] = spawn(floorFocus, Material.YELLOW_STAINED_GLASS.createBlockData(), LIT, SOLAR, tiny());
        seismicPillar[2] = spawn(floorFocus, Material.PURPLE_STAINED_GLASS.createBlockData(), LIT, VIOLET, tiny());
    }

    private void seismicArm() {
        phaseTick++;
        Location floor = floorFocus;
        if (floorSeed != null && floorSeed.isValid()) {
            float size = phaseTick < 4 ? 0.35f : 0.08f + 0.04f * (float) Math.sin(phaseTick * 0.9);
            floorSeed.teleport(level(floor.clone().add(
                    ThreadLocalRandom.current().nextDouble(-0.04, 0.04),
                    0,
                    ThreadLocalRandom.current().nextDouble(-0.04, 0.04))));
            ease(floorSeed, centered(size, new Quaternionf().rotateY(phaseTick * 1.1f).rotateX(0.55f)), 1);
        }
        if (phaseTick == 3) {
            drawRing(floor, 1.6, 40, new Particle.DustOptions(PHOTON, 0.9f), 0);
            world.playSound(floor, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 0.4f);
        }
        if (phaseTick == 7) {
            drawRing(floor, 0.7, 28, new Particle.DustOptions(HOT, 1.1f), 0);
            world.playSound(floor, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 0.35f);
        }
        if (phaseTick % 4 == 0) {
            world.playSound(floor, Sound.ENTITY_WARDEN_HEARTBEAT, 0.85f, 0.4f);
        }
        /* Pre-grow seal to a hairline so expand interpolates from something real. */
        putSeal(0.04 + 0.02 * (phaseTick / (double) Math.max(1, SEISMIC_EXPAND - SEISMIC_ARM)), 0.08f, 0f);
        if (phaseTick >= (SEISMIC_EXPAND - SEISMIC_ARM)) {
            phase = Phase.SEISMIC_EXPAND;
            phaseTick = 0;
            discard(floorSeed);
            floorSeed = null;
            player.sendActionBar(net.kyori.adventure.text.Component.text("§c§l◯ SEISMIC CHARGE"));
            world.playSound(floor, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.5f, 0.55f);
        }
    }

    private void seismicExpand() {
        phaseTick++;
        int span = SEISMIC_BOOM - SEISMIC_EXPAND;
        double u = phaseTick / (double) span;
        double grow = easeOutQuart(Math.min(1.0, u));
        sealSpin += 0.045f;
        putSeal(grow, 0.12f + (float) grow * 0.55f, sealSpin);

        Location floor = floorFocus;
        double rOuter = SEISMIC_RADIUS * grow;
        if (phaseTick % 2 == 0) {
            drawRing(floor, rOuter, 64, new Particle.DustOptions(mix(HOT, CRIMSON, u), 1.9f), sealSpin);
            drawRing(floor.clone().add(0, 0.25, 0), rOuter * 0.68, 48,
                    new Particle.DustOptions(mix(SOLAR, VIOLET, u), 1.4f), -sealSpin * 1.3);
            BlockData dust = Material.MAGMA_BLOCK.createBlockData();
            int points = Math.max(12, (int) (rOuter * 2.5));
            for (int i = 0; i < points; i += 2) {
                double a = sealSpin + Math.PI * 2 * i / points;
                Location at = floor.clone().add(Math.cos(a) * rOuter, 0.05, Math.sin(a) * rOuter);
                world.spawnParticle(Particle.BLOCK, at, 1, 0.08, 0.02, 0.08, 0, dust);
                if (i % 4 == 0) {
                    world.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.2, 0), 1, 0.04, 0.02, 0.04, 0.01);
                }
            }
        }

        /* Classic SW: the ring climbs in near-silence — only a rising pressure tone. */
        if (phaseTick == 1 || phaseTick == 8 || phaseTick == 16 || phaseTick == 24 || phaseTick == 30) {
            float pitch = 0.35f + (float) u * 1.1f;
            world.playSound(floor, Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, pitch);
            world.playSound(floor, Sound.BLOCK_BEACON_AMBIENT, 0.4f, 0.5f + (float) u * 0.8f);
        }
        if (phaseTick == span - 4) {
            world.playSound(floor, Sound.BLOCK_BELL_RESONATE, 1.1f, 0.4f);
            world.playSound(floor, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.4f, 0.55f);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§f§l— IMPACT —"));
        }
        if (phaseTick >= span) {
            phase = Phase.SEISMIC_BOOM;
            phaseTick = 0;
            detonateSeismic();
        }
    }

    private void detonateSeismic() {
        Location floor = floorFocus;
        discard(floorSeed);
        floorSeed = null;

        world.spawnParticle(Particle.FLASH, floor, 16, 2.8, 0.4, 2.8, 0);
        world.spawnParticle(Particle.FLASH, floor.clone().add(0, 2.5, 0), 8, 1.5, 2.0, 1.5, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, floor, 10, 2.5, 0.6, 2.5, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, floor.clone().add(0, 1.2, 0), 4, 1.2, 1.0, 1.2, 0);

        for (int ring = 0; ring < 5; ring++) {
            double r = 3.0 + ring * (SEISMIC_RADIUS / 4.5);
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16;
                Location at = floor.clone().add(Math.cos(a) * r, 0.35, Math.sin(a) * r);
                world.spawnParticle(Particle.SONIC_BOOM, at, 1, 0, 0, 0, 0);
            }
        }

        burstAt(floor, Particle.END_ROD, 220, 1.9);
        burstAt(floor, Particle.FIREWORK, 140, 1.5);
        burstAt(floor, Particle.FLAME, 120, 1.2);
        burstAt(floor, Particle.SOUL_FIRE_FLAME, 80, 1.0);
        burstAt(floor, Particle.ELECTRIC_SPARK, 90, 1.4);
        burstAt(floor, Particle.CAMPFIRE_COSY_SMOKE, 70, 0.85);
        world.spawnParticle(Particle.DUST, floor, 120, 4.0, 1.2, 4.0, 0, new Particle.DustOptions(HOT, 2.8f));
        world.spawnParticle(Particle.DUST, floor, 100, 5.5, 1.5, 5.5, 0, new Particle.DustOptions(SOLAR, 2.3f));
        world.spawnParticle(Particle.DUST, floor, 90, 7.0, 1.8, 7.0, 0, new Particle.DustOptions(CRIMSON, 2.0f));
        world.spawnParticle(Particle.DUST, floor, 80, 9.0, 2.0, 9.0, 0, new Particle.DustOptions(VIOLET, 1.8f));
        world.spawnParticle(Particle.DUST, floor, 70, 11.0, 2.2, 11.0, 0, new Particle.DustOptions(VOID, 1.6f));

        for (int i = 0; i < 6; i++) {
            drawRing(floor.clone().add(0, 0.1 + i * 0.15, 0), 4.0 + i * 3.2, 56,
                    new Particle.DustOptions(i % 2 == 0 ? HOT : CRIMSON, 2.0f), i * 0.2);
        }

        for (Player near : world.getPlayers()) {
            if (near.getWorld() != world || near.getLocation().distanceSquared(floor) > 80 * 80) {
                continue;
            }
            Location eye = near.getEyeLocation();
            near.spawnParticle(Particle.FLASH, eye.clone().add(eye.getDirection().multiply(1.15)), 3, 0, 0, 0, 0);
            near.playSound(near.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.35f);
            near.playSound(near.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.4f);
        }

        /* The delayed boom — louder and lower than Absolute Nova. */
        world.playSound(floor, Sound.ENTITY_GENERIC_EXPLODE, 2.4f, 0.32f);
        world.playSound(floor, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.55f);
        world.playSound(floor, Sound.ENTITY_GENERIC_EXPLODE, 1.7f, 0.85f);
        world.playSound(floor, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.4f, 0.35f);
        world.playSound(floor, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.2f, 0.4f);
        world.playSound(floor, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.4f);
        world.playSound(floor, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.8f, 0.35f);
        world.playSound(floor, Sound.ITEM_TRIDENT_THUNDER, 1.9f, 0.4f);
        world.playSound(floor, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.8f, 0.45f);
        world.playSound(floor, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST_FAR, 2.0f, 0.4f);
        world.playSound(floor, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.9f, 0.35f);
        world.playSound(floor, Sound.BLOCK_END_PORTAL_SPAWN, 1.2f, 0.7f);
        world.playSound(floor, Sound.ITEM_TOTEM_USE, 1.2f, 0.45f);
        world.playSound(floor, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.5f, 0.4f);
        world.playSound(floor, Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.55f);
        world.playSound(floor, Sound.BLOCK_ANVIL_LAND, 1.3f, 0.35f);
        world.playSound(floor, Sound.BLOCK_BELL_USE, 1.6f, 0.3f);

        spawnSeismicEjecta();
        seismicSplash();
        player.sendActionBar(net.kyori.adventure.text.Component.text("§c§l✦ SEISMIC DETONATION ✦"));
        player.sendMessage("§c✦ Absolute Nova §8→ §c§lSeismic Charge");
    }

    private void spawnSeismicEjecta() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Material[] rock = {
                Material.MAGMA_BLOCK, Material.SHROOMLIGHT, Material.GOLD_BLOCK,
                Material.CRYING_OBSIDIAN, Material.PEARLESCENT_FROGLIGHT, Material.OCHRE_FROGLIGHT,
                Material.ORANGE_STAINED_GLASS, Material.RED_STAINED_GLASS
        };
        Color[] heat = {EMBER, SOLAR, HOT, VIOLET, PHOTON, SOLAR, EMBER, CRIMSON};
        int n = LIVE.size() > MAX_LIVE - 40 ? 14 : 28;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + random.nextDouble(0.2);
            int kind = i % rock.length;
            BlockDisplay chunk = spawn(floorFocus, rock[kind].createBlockData(), LIT, heat[kind],
                    centered(0.01f, new Quaternionf()));
            Vector vel = new Vector(Math.cos(a), 0, Math.sin(a))
                    .multiply(random.nextDouble(0.75, 1.45))
                    .setY(0.55 + random.nextDouble(0.55));
            seismicEjecta.add(new Ejecta(chunk, floorFocus.clone().add(0, 0.4, 0), vel, heat[kind],
                    (float) random.nextDouble(0.4, 0.95), 28 + random.nextInt(18)));
        }
    }

    private void seismicSplash() {
        if (seismicDamaged || damage <= 0) {
            return;
        }
        seismicDamaged = true;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double radius = SEISMIC_RADIUS + 2.0;
        for (Entity entity : world.getNearbyEntities(floorFocus, radius, 6.0, radius)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            Vector rel = living.getLocation().toVector().subtract(floorFocus.toVector());
            double d = Math.hypot(rel.getX(), rel.getZ());
            if (d > radius) {
                continue;
            }
            double falloff = d <= 6.0 ? 1.15 : 1.0 - 0.65 * (d - 6.0) / (radius - 6.0);
            ScriptedHits.run(() -> living.damage(damage * 1.35 * falloff, player));
            Vector away = rel.setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(random.nextDouble(-1, 1), 0, random.nextDouble(-1, 1));
            }
            living.setVelocity(away.normalize().multiply(1.85).setY(1.15));
            living.setFallDistance(0f);
        }
    }

    private void seismicBoom() {
        phaseTick++;
        sealSpin += 0.12f;
        double punch = easeOutQuart(Math.min(1.0, phaseTick / 5.0));
        putSeal(1.0 + punch * 0.18, 0.7f - (float) punch * 0.25f, sealSpin);
        tickSeismicPillar(phaseTick);
        tickSeismicEjecta();

        Location floor = floorFocus;
        if (phaseTick <= 8) {
            double r = SEISMIC_RADIUS * (0.85 + phaseTick * 0.04);
            drawRing(floor, r, 72, new Particle.DustOptions(HOT, 2.2f), sealSpin);
            drawRing(floor.clone().add(0, 0.4, 0), r * 0.92, 56, new Particle.DustOptions(CRIMSON, 1.8f), -sealSpin);
        }
        if (phaseTick == 2 || phaseTick == 5) {
            world.spawnParticle(Particle.FLASH, floor.clone().add(0, 1.5, 0), 4, 2.0, 1.5, 2.0, 0);
            world.playSound(floor, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 0.45f);
            world.playSound(floor, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.5f);
        }
        if (phaseTick >= (SEISMIC_AFTER - SEISMIC_BOOM)) {
            phase = Phase.SEISMIC_AFTER;
            phaseTick = 0;
        }
    }

    private void seismicAfter() {
        phaseTick++;
        tickSeismicEjecta();
        int span = TOTAL - SEISMIC_AFTER;
        double k = phaseTick / (double) span;
        sealSpin += 0.03f;
        float shrink = (float) (1.0 - easeOutQuart(k) * 0.92);
        putSeal(Math.max(0.02, shrink), Math.max(0.02f, 0.45f * (1f - (float) k)), sealSpin);

        Location floor = floorFocus;
        if (phaseTick % 2 == 0) {
            drawRing(floor, SEISMIC_RADIUS * (1.0 - k * 0.55), 48,
                    new Particle.DustOptions(mix(EMBER, VOID, k), 1.3f), sealSpin);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, floor.clone().add(0, 0.4, 0),
                    Math.max(2, 14 - phaseTick / 2), 4.5, 0.3, 4.5, 0.01);
            world.spawnParticle(Particle.DUST, floor, 10, 5.0, 0.4, 5.0, 0,
                    new Particle.DustOptions(mix(CRIMSON, VOID, k), 1.2f));
        }
        tickSeismicPillarFade(phaseTick, span);

        if (phaseTick == 4) {
            world.playSound(floor, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.55f);
            world.playSound(floor, Sound.BLOCK_BELL_RESONATE, 0.9f, 0.7f);
        }
        if (phaseTick == 12) {
            world.playSound(floor, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.4f);
            player.sendMessage("§8…the ground forgets.");
        }
        if (phaseTick >= span && seismicEjecta.isEmpty()) {
            clearSeismic();
            phase = Phase.DONE;
        }
    }

    private void putSeal(double grow, float width, float spin) {
        if (floorFocus == null) {
            return;
        }
        double[] scales = {1.0, 0.68, 0.38};
        for (SealTile tile : seal) {
            if (tile.display == null || !tile.display.isValid()) {
                continue;
            }
            tile.display.teleport(level(floorFocus));
            if (tile.ring == 3) {
                double a = tile.baseAngle + spin * 0.6;
                double reach = SEISMIC_RADIUS * 0.92 * grow;
                Vector3f tip = new Vector3f((float) (Math.cos(a) * reach), 0.06f, (float) (Math.sin(a) * reach));
                ease(tile.display, rod(new Vector3f(0, 0.04f, 0), tip, Math.max(0.04f, width * 0.45f)), 2);
                continue;
            }
            double radius = SEISMIC_RADIUS * scales[tile.ring] * grow;
            double ang = tile.baseAngle + (tile.ring == 1 ? -spin : spin * (tile.ring == 0 ? 1f : 0.7f));
            float len = (float) (Math.PI * 2 * Math.max(0.4, radius) / Math.max(8, segsFor(tile.ring)) * 1.1);
            ease(tile.display, ringSeg(ang, radius, 0.02 + tile.ring * 0.03, len,
                    Math.max(0.06f, width), 0.1f), 2);
        }
    }

    private int segsFor(int ring) {
        return switch (ring) {
            case 0 -> SEAL_OUTER;
            case 1 -> SEAL_MID;
            case 2 -> SEAL_INNER;
            default -> SEAL_SPOKES;
        };
    }

    private void tickSeismicPillar(int age) {
        float h = (float) (18.0 * easeOutQuart(Math.min(1.0, age / 4.0)));
        float[] w = {1.1f, 0.7f, 0.35f};
        for (int i = 0; i < seismicPillar.length; i++) {
            BlockDisplay beam = seismicPillar[i];
            if (beam == null || !beam.isValid()) {
                continue;
            }
            beam.teleport(level(floorFocus));
            float width = age < 6 ? w[i] : Math.max(0.05f, w[i] - (age - 6) * 0.06f);
            ease(beam, rod(new Vector3f(0, 0, 0), new Vector3f(0, h, 0), width), 2);
        }
    }

    private void tickSeismicPillarFade(int age, int span) {
        float h = (float) (18.0 * (1.0 - age / (double) span));
        for (int i = 0; i < seismicPillar.length; i++) {
            BlockDisplay beam = seismicPillar[i];
            if (beam == null || !beam.isValid()) {
                continue;
            }
            if (h < 0.4f) {
                discard(beam);
                seismicPillar[i] = null;
                continue;
            }
            beam.teleport(level(floorFocus));
            ease(beam, rod(new Vector3f(0, 0, 0), new Vector3f(0, h, 0), Math.max(0.04f, 0.5f - age * 0.02f)), 3);
        }
    }

    private void tickSeismicEjecta() {
        Iterator<Ejecta> it = seismicEjecta.iterator();
        while (it.hasNext()) {
            Ejecta chunk = it.next();
            if (!chunk.step()) {
                discard(chunk.display);
                it.remove();
            }
        }
    }

    private void burstAt(Location at, Particle particle, int count, double speed) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Vector dir = randomUnit(random);
            world.spawnParticle(particle, at, 0, dir.getX(), dir.getY(), dir.getZ(), speed);
        }
    }

    private void clearSeismic() {
        discard(floorSeed);
        floorSeed = null;
        for (SealTile tile : seal) {
            discard(tile.display);
        }
        seal.clear();
        for (int i = 0; i < seismicPillar.length; i++) {
            discard(seismicPillar[i]);
            seismicPillar[i] = null;
        }
        for (Ejecta chunk : seismicEjecta) {
            discard(chunk.display);
        }
        seismicEjecta.clear();
    }

    private void tickEjecta() {
        Iterator<Ejecta> it = ejecta.iterator();
        while (it.hasNext()) {
            Ejecta chunk = it.next();
            if (!chunk.step()) {
                discard(chunk.display);
                it.remove();
            }
        }
    }

    private void burst(Particle particle, int count, double speed) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Vector dir = randomUnit(random);
            world.spawnParticle(particle, focus, 0, dir.getX(), dir.getY(), dir.getZ(), speed);
        }
    }

    // ------------------------------------------------------------------ pieces

    private final class Ejecta {
        final BlockDisplay display;
        final Location at;
        final Vector velocity;
        final Color glow;
        final float size;
        final int life;
        final Quaternionf rot = new Quaternionf();
        final float spinX;
        final float spinY;
        int age;

        Ejecta(BlockDisplay display, Location start, Vector velocity, Color glow, float size, int life) {
            this.display = display;
            this.at = level(start.clone());
            this.velocity = velocity;
            this.glow = glow;
            this.size = size;
            this.life = life;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            this.spinX = (float) random.nextDouble(-0.4, 0.4);
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
            at.add(velocity);
            velocity.setY(velocity.getY() - 0.06);
            velocity.multiply(0.985);
            if (velocity.getY() < 0 && at.getY() <= focus.getY() - 0.5) {
                world.spawnParticle(Particle.SMOKE, at, 4, 0.15, 0.05, 0.15, 0.01);
                return false;
            }
            rot.rotateXYZ(spinX, spinY, spinX * 0.4f);
            display.teleport(at);
            ease(display, centered(size, rot), 1);
            if (age % 2 == 0) {
                world.spawnParticle(Particle.DUST, at, 1, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(glow, 1.0f));
                world.spawnParticle(Particle.FLAME, at, 1, 0.04, 0.04, 0.04, 0.005);
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ geometry + helpers

    private Location floorNear(Location at) {
        RayTraceResult hit = world.rayTraceBlocks(at.clone().add(0, 1.0, 0), new Vector(0, -1, 0), 40.0,
                FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            return level(hit.getHitPosition().toLocation(world));
        }
        return level(at.clone());
    }

    private static Location level(Location at) {
        Location out = at.clone();
        out.setYaw(0);
        out.setPitch(0);
        return out;
    }

    private static Vector randomUnit(ThreadLocalRandom random) {
        double u = random.nextDouble();
        double v = random.nextDouble();
        double theta = 2 * Math.PI * u;
        double phi = Math.acos(2 * v - 1);
        return new Vector(Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta));
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

    private void drawRing(Location center, double radius, int points, Particle.DustOptions dust, double phase) {
        for (int i = 0; i < points; i++) {
            double a = phase + Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0.12, Math.sin(a) * radius),
                    1, 0, 0, 0, 0, dust);
        }
    }

    private void drawTiltedRing(Location center, Quaternionf orient, double radius, double lift, int points,
                                Particle.DustOptions dust) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            Vector3f p = orient.transform(new Vector3f((float) (Math.cos(a) * radius), (float) lift, (float) (Math.sin(a) * radius)));
            world.spawnParticle(Particle.DUST, center.clone().add(p.x, p.y, p.z), 1, 0, 0, 0, 0, dust);
        }
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

    private static Transformation rod(Vector3f a, Vector3f b, float width) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        float w = Math.max(0.001f, width);
        if (len < 1.0E-3f) {
            return tiny();
        }
        d.div(len);
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), d);
        Vector3f start = new Vector3f(a).sub(new Vector3f(d).mul(w * 0.35f));
        Vector3f off = new Quaternionf(rot).transform(new Vector3f(w / 2f, 0f, w / 2f));
        return new Transformation(start.sub(off), rot, new Vector3f(w, len + w * 0.7f, w), new Quaternionf());
    }

    private static Transformation ringSeg(double angle, double radius, double lift, float length, float width, float thick) {
        Quaternionf yaw = new Quaternionf().rotateY((float) angle);
        Vector3f radial = new Vector3f((float) Math.sin(angle), 0, (float) Math.cos(angle));
        Vector3f center = new Vector3f(radial).mul((float) radius).add(0, (float) lift, 0);
        Quaternionf rot = new Quaternionf(yaw).rotateX((float) Math.toRadians(90));
        Vector3f half = new Quaternionf(rot).transform(new Vector3f(length / 2f, thick / 2f, width / 2f));
        return new Transformation(center.sub(half), rot, new Vector3f(length, thick, width), new Quaternionf());
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
        discard(boltCore);
        discard(boltShell);
        discard(seed);
        boltCore = null;
        boltShell = null;
        seed = null;
        for (BlockDisplay shell : shells) {
            discard(shell);
        }
        shells.clear();
        for (BlockDisplay tile : rings) {
            discard(tile);
        }
        rings.clear();
        for (BlockDisplay ray : rays) {
            discard(ray);
        }
        rays.clear();
        rayReach.clear();
        for (int i = 0; i < polar.length; i++) {
            discard(polar[i]);
            polar[i] = null;
        }
        for (Ejecta chunk : ejecta) {
            discard(chunk.display);
        }
        ejecta.clear();
        clearSeismic();
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
