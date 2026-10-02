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
 * Cataclysm Rod (cataclysm_rod) — Absolute Nova.
 * Right-click launches a molten star-core bolt. On impact (or max range) the sky
 * winds up, hush-silences, collapses into a black seed, then detonates a multi-shell
 * supernova meant to outshine Hollow Sun and Ashen Eclipse. Visual showcase only;
 * light optional splash damage.
 */
public final class CataclysmRodNova {

    private static final int FLIGHT_MAX = 28;
    private static final double FLIGHT_SPEED = 1.55;
    private static final double MAX_RANGE = 42.0;

    private static final int WINDUP = 18;
    private static final int HUSH = WINDUP + 8;
    private static final int BLAST = HUSH + 4;
    private static final int REMNANT = BLAST + 36;
    private static final int SETTLE = REMNANT + 28;
    private static final int TOTAL = SETTLE + 16;

    private static final double BLAST_RADIUS = 11.0;
    private static final double CORE_RADIUS = 4.5;
    private static final float SHELL_REACH = 14f;
    private static final int MAX_LIVE = 280;
    private static final int RING_SEGS = 18;
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

    private Location boltAt;
    private Vector boltVel;
    private Location focus;
    private Location skyFocus;
    private BlockDisplay boltCore;
    private BlockDisplay boltShell;
    private BlockDisplay seed;
    private Quaternionf tilt = new Quaternionf();
    private float spin;
    private int tick;
    private int phaseTick;
    private Phase phase = Phase.FLIGHT;
    private boolean released;
    private boolean damaged;

    private enum Phase { FLIGHT, WINDUP, HUSH, BLAST, REMNANT, SETTLE, DONE }

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
            case REMNANT -> {
                remnant();
                yield false;
            }
            case SETTLE -> {
                settle();
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
        tickShells(phaseTick);
        tickRings(phaseTick);
        tickRays(phaseTick);
        tickPolar(phaseTick);
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
        if (phaseTick == 8 || phaseTick == 16 || phaseTick == 24) {
            seismicWave(phaseTick / 28.0);
        }
        if (phaseTick >= (REMNANT - BLAST)) {
            phase = Phase.REMNANT;
            phaseTick = 0;
            for (BlockDisplay shell : shells) {
                discard(shell);
            }
            shells.clear();
            for (BlockDisplay ray : rays) {
                discard(ray);
            }
            rays.clear();
            rayReach.clear();
        }
    }

    private void tickShells(int age) {
        for (int i = 0; i < 2 && i < shells.size(); i++) {
            BlockDisplay flash = shells.get(i);
            if (flash == null || !flash.isValid()) {
                continue;
            }
            if (age >= 6) {
                discard(flash);
                continue;
            }
            float size = age < 2 ? 7.5f : 12.5f;
            Quaternionf rot = i == 0
                    ? new Quaternionf().rotateY(age * 0.22f)
                    : new Quaternionf().rotateY(0.785f - age * 0.22f).rotateX(0.615f).rotateZ(0.785f);
            ease(flash, centered(size, rot), 2);
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
                if (age > ticks) {
                    discard(shell);
                    continue;
                }
                float r = max * (float) easeOutQuart((age + 1) / (double) ticks);
                float turn = age * 0.035f * (layer + 1);
                Quaternionf rot = k == 0
                        ? new Quaternionf().rotateY(turn).rotateX(0.615f).rotateZ(0.785f)
                        : new Quaternionf().rotateY(-turn + 0.9f).rotateX(-0.4f).rotateZ(-0.5f);
                shell.teleport(level(skyFocus));
                ease(shell, centered(Math.max(0.001f, r), rot), 2);
            }
        }
    }

    private void tickRings(int age) {
        if (rings.isEmpty()) {
            return;
        }
        int segs = rings.size() / 3;
        if (segs <= 0) {
            return;
        }
        double grow = easeOutQuart(Math.min(1.0, age / 16.0));
        double[] scales = {0.55, 0.85, 1.15};
        double[] lifts = {0.0, 0.22, -0.22};
        for (int ring = 0; ring < 3; ring++) {
            double radius = SHELL_REACH * scales[ring] * grow;
            double lift = SHELL_REACH * lifts[ring] * grow;
            float len = (float) (Math.PI * 2 * radius / segs * 1.08);
            float wide = (float) (0.35 + grow * 0.45);
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
                ease(tile, ringSeg(ang, radius, lift, len, wide, 0.08f), 2);
                if (age > 22 && age == 23 + ring * 2) {
                    ease(tile, tiny(), 6);
                }
            }
        }
        if (age >= 30) {
            for (BlockDisplay tile : rings) {
                discard(tile);
            }
            rings.clear();
        }
    }

    private void tickRays(int age) {
        float reach = (float) easeOutQuart(Math.min(1.0, age / 10.0));
        float width = age < 4 ? 0.35f : Math.max(0.04f, 0.35f - (age - 4) * 0.02f);
        for (int i = 0; i < rays.size() && i < rayReach.size(); i++) {
            BlockDisplay ray = rays.get(i);
            if (ray == null || !ray.isValid()) {
                continue;
            }
            Vector3f tip = new Vector3f(rayReach.get(i)).mul(reach);
            ray.teleport(level(skyFocus));
            ease(ray, rod(new Vector3f(), tip, width), 2);
            if (age > 18) {
                discard(ray);
            }
        }
        if (age > 18) {
            rays.clear();
            rayReach.clear();
        }
    }

    private void tickPolar(int age) {
        float len = (float) (SHELL_REACH * 1.6 * easeOutQuart(Math.min(1.0, age / 8.0)));
        float w = age < 6 ? 0.55f : Math.max(0.04f, 0.55f - (age - 6) * 0.04f);
        Vector3f axis = tilt.transform(new Vector3f(0, 1, 0));
        for (int i = 0; i < 2; i++) {
            BlockDisplay beam = polar[i];
            if (beam == null || !beam.isValid()) {
                continue;
            }
            float sign = i == 0 ? 1f : -1f;
            Vector3f tip = new Vector3f(axis).mul(sign * len);
            beam.teleport(level(skyFocus));
            ease(beam, rod(new Vector3f(), tip, w), 2);
            if (age > 16) {
                discard(beam);
                polar[i] = null;
            }
        }
    }

    private void seismicWave(double progress) {
        Location floor = floorNear(focus);
        double r = 2.0 + progress * (BLAST_RADIUS + 6.0);
        Particle.DustOptions wave = new Particle.DustOptions(mix(HOT, CRIMSON, progress), 1.8f);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            Location at = floor.clone().add(Math.cos(a) * r, 0.12, Math.sin(a) * r);
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, wave);
            if (i % 4 == 0) {
                world.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.3, 0), 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        world.playSound(floor, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 0.7f, 0.45f);
        world.playSound(floor, Sound.ENTITY_GENERIC_EXPLODE, 0.55f, 0.35f);
    }

    // ------------------------------------------------------------------ remnant / settle

    private void remnant() {
        phaseTick++;
        tickEjecta();
        for (BlockDisplay tile : rings) {
            if (tile != null && tile.isValid() && phaseTick == 1) {
                ease(tile, tiny(), 8);
            }
        }
        if (phaseTick == 4) {
            for (BlockDisplay tile : rings) {
                discard(tile);
            }
            rings.clear();
        }

        double sweep = phaseTick * 0.22;
        for (int beam = 0; beam < 2; beam++) {
            double a = sweep + beam * Math.PI;
            Color tint = beam == 0 ? SOLAR : VIOLET;
            for (int s = 2; s <= 18; s++) {
                Location at = focus.clone().add(Math.cos(a) * s * 0.7, 0.4 + Math.sin(s * 0.35) * 0.3, Math.sin(a) * s * 0.7);
                world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(tint, 1.15f));
                if (s % 3 == 0) {
                    world.spawnParticle(Particle.END_ROD, at, 1, 0, 0, 0, 0);
                }
            }
        }
        if (phaseTick % 2 == 0) {
            world.spawnParticle(Particle.FIREWORK, skyFocus, 12, 3.5, 2.0, 3.5, 0.02);
            world.spawnParticle(Particle.END_ROD, skyFocus, 8, 2.5, 1.5, 2.5, 0.02);
        }
        if (phaseTick % 4 == 0) {
            drawRing(floorNear(focus), BLAST_RADIUS * 0.85, 36, new Particle.DustOptions(EMBER, 1.2f), phaseTick * 0.05);
        }
        if (phaseTick == 6 || phaseTick == 18) {
            world.playSound(focus, Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 0.55f);
            world.playSound(focus, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.5f);
        }
        if (phaseTick == 12) {
            world.playSound(focus, Sound.BLOCK_BELL_RESONATE, 1.0f, 0.55f);
        }
        if (phaseTick >= (SETTLE - REMNANT)) {
            phase = Phase.SETTLE;
            phaseTick = 0;
            for (int i = 0; i < polar.length; i++) {
                discard(polar[i]);
                polar[i] = null;
            }
        }
    }

    private void settle() {
        phaseTick++;
        tickEjecta();
        double k = phaseTick / (double) (TOTAL - SETTLE);
        if (phaseTick % 2 == 0) {
            int n = Math.max(1, 10 - phaseTick);
            world.spawnParticle(Particle.END_ROD, skyFocus, n, 1.2, 1.0, 1.2, 0.01);
            world.spawnParticle(Particle.FIREWORK, focus, n / 2, 1.0, 0.6, 1.0, 0.01);
            world.spawnParticle(Particle.DUST, focus, 6, 2.0, 1.0, 2.0, 0,
                    new Particle.DustOptions(mix(VIOLET, VOID, k), 1.0f));
        }
        if (phaseTick == 2) {
            world.playSound(focus, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.3f);
            world.playSound(focus, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.45f);
        }
        if (phaseTick == 8) {
            world.playSound(floorNear(focus), Sound.BLOCK_BELL_RESONATE, 0.7f, 1.2f);
            player.sendMessage("§8…the sky is quiet again.");
        }
        if (phaseTick >= (TOTAL - SETTLE) && ejecta.isEmpty()) {
            phase = Phase.DONE;
        }
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
