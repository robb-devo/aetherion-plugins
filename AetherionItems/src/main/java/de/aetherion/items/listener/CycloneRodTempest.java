package de.aetherion.items.listener;

import de.aetherion.items.combat.ScriptedHits;

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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cyclone Rod (cyclone_rod) — Eye of the Storm.
 * The rod inhales: a dashed catch ring marks the floor, cloud streaks spiral in along the ground and three feeder
 * vortices start circling the edge. The funnel builds from the floor up as banked wind ribbons, dusty grey at the
 * base and white at the crown, swaying as it widens, and the caster stands calm in the eye. Debris chunks tumble up
 * the wall on a rising spiral, helical streaks wrap the funnel and caught enemies trail cloud as they orbit. The howl
 * climbs in pitch the whole time. Ten ticks out the eye closes: the feeders are swallowed, the funnel cinches in and
 * spins hardest while breeze slides tick up the scale. Then it bursts: ribbons and debris blow outward, a gust front
 * rolls across the floor, and everything caught is flung far out trailing cloud.
 * Spin time, catch radius, orbit physics, damage and fling match the old tornado. Blocks are never touched.
 */
public final class CycloneRodTempest {

    static final int SPIN = 55;
    private static final int CONTRACT = SPIN - 10;
    private static final int AFTER = 20;
    private static final int LAYERS = 6;
    private static final int TILES = 7;
    private static final int DEBRIS = 10;
    private static final double TOP = 4.4;
    private static final double CATCH = 7.5;
    private static final double CATCH_Y = 6.0;
    private static final double FLING = 9.0;
    private static final double FLING_Y = 8.0;
    private static final int MAX_TRAILS = 12;
    private static final double TAU = Math.PI * 2;
    /** Past this many live displays (all casters), new cyclones skip the debris chunks. */
    private static final int MAX_LIVE = 240;

    private static final Color WIND = Color.fromRGB(235, 245, 255);
    private static final Color PALE = Color.fromRGB(200, 222, 240);
    private static final Color GREY = Color.fromRGB(150, 165, 180);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    private static final Display.Brightness DUSTY = new Display.Brightness(10, 10);

    private static final Material[] DEBRIS_MATS = {
            Material.WHITE_WOOL,
            Material.LIGHT_GRAY_WOOL,
            Material.SANDSTONE,
            Material.SMOOTH_SANDSTONE,
            Material.TERRACOTTA
    };

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final Player player;
    private final World world;
    private final double damage;
    private final Runnable done;
    private final List<Band> bands = new ArrayList<>();
    private final List<Chunk> debris = new ArrayList<>();
    private final Set<UUID> caught = new HashSet<>();
    private final List<LivingEntity> orbiting = new ArrayList<>();
    private final List<Flung> flung = new ArrayList<>();
    private Location base;
    private BlockData floorData;
    private double spin;
    private double radius = 2.2;
    private double squeeze = 1.0;
    private double sway;
    private int tick;
    private boolean released;

    /** Plugin disable: removes every wind ribbon and debris chunk still standing. */
    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    /** {@code done} runs once, at the fling (same moment the old tornado freed its busy slot). */
    static void cast(JavaPlugin plugin, Player player, double tipDamage, Runnable done) {
        new CycloneRodTempest(plugin, player, tipDamage, done).start();
    }

    private CycloneRodTempest(JavaPlugin plugin, Player player, double tipDamage, Runnable done) {
        this.plugin = plugin;
        this.player = player;
        this.world = player.getWorld();
        this.damage = tipDamage;
        this.done = done;
    }

    private void start() {
        base = floorUnder(player.getLocation());
        Block below = base.clone().add(0, -0.5, 0).getBlock();
        floorData = below.getType().isSolid() ? below.getBlockData() : Material.SANDSTONE.createBlockData();

        for (int layer = 0; layer < LAYERS; layer++) {
            boolean dusty = layer < 2;
            for (int i = 0; i < TILES; i++) {
                Material material = dusty
                        ? (i % 2 == 0 ? Material.LIGHT_GRAY_STAINED_GLASS : Material.GRAY_STAINED_GLASS)
                        : (i % 2 == 0 ? Material.WHITE_STAINED_GLASS : Material.LIGHT_GRAY_STAINED_GLASS);
                BlockDisplay display = spawn(material.createBlockData(), dusty ? DUSTY : LIT);
                if (display != null) {
                    bands.add(new Band(display, layer, i));
                }
            }
        }
        if (LIVE.size() <= MAX_LIVE) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < DEBRIS; i++) {
                BlockData data = i % 4 == 0 ? floorData
                        : DEBRIS_MATS[random.nextInt(DEBRIS_MATS.length)].createBlockData();
                BlockDisplay display = spawn(data, new Display.Brightness(12, 12));
                if (display != null) {
                    debris.add(new Chunk(display, i, random));
                }
            }
        }

        Location tip = rodTip();
        world.spawnParticle(Particle.GUST, tip, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.CLOUD, tip, 8, 0.15, 0.15, 0.15, 0.06);
        world.playSound(tip, Sound.ENTITY_BREEZE_INHALE, 0.9f, 0.7f);
        world.playSound(tip, Sound.ENTITY_BREEZE_WHIRL, 0.7f, 0.6f);
        world.playSound(tip, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.5f, 0.8f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
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
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean step() {
        tick++;
        if (tick <= SPIN) {
            whirl();
        } else if (tick == SPIN + 1) {
            burst();
        } else {
            aftermath(tick - SPIN - 1);
        }
        tickFlung();
        return tick > SPIN + 1 + AFTER;
    }

    private void release() {
        if (!released) {
            released = true;
            done.run();
        }
    }

    // ------------------------------------------------------------------ spin

    private void whirl() {
        double p = tick / (double) SPIN;
        double k = tick > CONTRACT ? (tick - CONTRACT) / (double) (SPIN - CONTRACT) : 0.0;
        base = floorUnder(player.getLocation());
        radius = 2.2 + p * 4.0;
        squeeze = 1.0 - 0.38 * smooth(k);
        sway = 0.8 * Math.min(1.0, p * 1.5) * (1.0 - 0.6 * k);
        spin += 0.16 + p * 0.42 + k * 0.22;

        poseBands(p, k);
        poseDebris(p);
        suction(p);
        helix();
        feeders(p);
        conductor();
        trails();
        howl(p, k);
        pull(p);
    }

    /** Old orbit physics, unchanged: everything in range is dragged round the caster and lifted. */
    private void pull(double p) {
        Location center = player.getLocation().add(0, 0.2, 0);
        for (Entity entity : world.getNearbyEntities(center, CATCH, CATCH_Y, CATCH)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            if (caught.add(living.getUniqueId())) {
                snatched(living);
            }
            Vector rel = living.getLocation().toVector().subtract(center.toVector());
            double dist = Math.max(0.8, Math.min(radius, rel.clone().setY(0).length()));
            double yaw = Math.atan2(rel.getZ(), rel.getX()) + 0.55;
            double lift = 0.35 + p * 0.55;
            Location want = center.clone().add(Math.cos(yaw) * dist, Math.min(3.2, 0.6 + tick * 0.04), Math.sin(yaw) * dist);
            Vector push = want.toVector().subtract(living.getLocation().toVector());
            if (push.lengthSquared() > 0.01) {
                living.setVelocity(push.multiply(0.28).setY(lift * 0.15));
            }
            living.setFallDistance(0f);
        }
    }

    /** The beat an enemy is grabbed: a gust pops off them and the wind audibly takes them. */
    private void snatched(LivingEntity living) {
        if (orbiting.size() < MAX_TRAILS) {
            orbiting.add(living);
        }
        Location at = living.getLocation().add(0, living.getHeight() * 0.5, 0);
        world.spawnParticle(Particle.GUST, at, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.BLOCK, living.getLocation().add(0, 0.1, 0), 10, 0.3, 0.05, 0.3, 0.1, floorData);
        world.playSound(at, Sound.ENTITY_BREEZE_SLIDE, 0.45f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.3f);
    }

    /** Funnel ribbons: built bottom-up, base spins fastest, crown sways, all cinch in when the eye closes. */
    private void poseBands(double p, double k) {
        for (Band band : bands) {
            BlockDisplay display = band.display;
            if (!display.isValid()) {
                continue;
            }
            double lt = band.layer / (double) (LAYERS - 1);
            double grow = smooth(clamp((tick - 1 - band.layer * 2) / 6.0));
            if (grow <= 0.0) {
                continue;
            }
            double y = 0.25 + lt * (TOP - 0.25);
            double r = funnelAt(y);
            double speed = 1.0 + (LAYERS - 1 - band.layer) * 0.1;
            double a = spin * speed + band.index * TAU / TILES + band.layer * 0.45;
            band.angle = a;
            band.r = r;
            band.y = y;
            band.length = (float) (TAU * r / TILES * (0.5 + 0.14 * p) * grow);
            band.height = (float) ((0.14 + 0.1 * lt + 0.1 * k) * grow);
            display.teleport(base);
            ease(display, ribbon(a, r, y, wobX(y), wobZ(y), band.length, band.height, 0.03f,
                    (float) Math.toRadians(16.0 + 12.0 * lt)), 2);
        }
    }

    /** Debris tumbles up the funnel wall on a rising spiral and wraps back to the floor. */
    private void poseDebris(double p) {
        for (Chunk chunk : debris) {
            BlockDisplay display = chunk.display;
            if (!display.isValid()) {
                continue;
            }
            double in = smooth(clamp((tick - 8 - chunk.index) / 4.0));
            if (in <= 0.0) {
                continue;
            }
            chunk.h += chunk.rise * (0.6 + p);
            if (chunk.h > TOP + 0.4) {
                chunk.h = 0.3;
            }
            double r = funnelAt(chunk.h) * chunk.rf;
            double a = spin * 0.9 + chunk.offset;
            chunk.tumble.rotateAxis((float) (chunk.tumbleRate * (0.6 + p)), chunk.axis);
            float s = (float) (chunk.size * in);
            chunk.at = new Vector3f((float) (Math.cos(a) * r + wobX(chunk.h)), (float) chunk.h,
                    (float) (Math.sin(a) * r + wobZ(chunk.h)));
            chunk.out = new Vector3f((float) Math.cos(a), 0f, (float) Math.sin(a));
            display.teleport(base);
            ease(display, cube(chunk.at, chunk.tumble, s), 2);
        }
    }

    /** Catch ring on the floor, cloud streaks spiraling in from past it, and floor dust skirting the base. */
    private void suction(double p) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int streaks = 3 + (int) (p * 3);
        for (int i = 0; i < streaks; i++) {
            double ang = random.nextDouble(TAU);
            double dist = CATCH + random.nextDouble(-0.5, 1.5);
            Location at = base.clone().add(Math.cos(ang) * dist, random.nextDouble(0.1, 1.2), Math.sin(ang) * dist);
            Vector dir = new Vector(-Math.cos(ang) * 0.75 - Math.sin(ang) * 0.65, 0.08,
                    -Math.sin(ang) * 0.75 + Math.cos(ang) * 0.65).normalize();
            world.spawnParticle(Particle.CLOUD, at, 0, dir.getX(), dir.getY(), dir.getZ(), 0.28 + p * 0.25);
        }
        if (tick % 3 == 0) {
            Particle.DustOptions ring = new Particle.DustOptions(p < 0.8 ? GREY : PALE, 1.1f);
            int points = 40;
            for (int i = 0; i < points; i++) {
                if ((i / 2) % 2 == 1) {
                    continue;
                }
                double ang = TAU * i / points - spin * 0.15;
                world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(ang) * CATCH, 0.12, Math.sin(ang) * CATCH),
                        1, 0, 0, 0, 0, ring);
            }
        }
        if (tick % 2 == 0) {
            double r = funnelAt(0.2) + 0.4;
            for (int i = 0; i < 3; i++) {
                double ang = spin + TAU * i / 3;
                world.spawnParticle(Particle.BLOCK, base.clone().add(Math.cos(ang) * r, 0.15, Math.sin(ang) * r),
                        3, 0.25, 0.08, 0.25, 0.15, floorData);
            }
        }
    }

    /** Three streaks wrapping the funnel wall from floor to crown. */
    private void helix() {
        for (int arm = 0; arm < 3; arm++) {
            for (int k = 0; k < 8; k++) {
                double h = (k + 0.5) / 8.0 * TOP;
                double r = funnelAt(h) * 1.04;
                double a = spin * 1.25 + arm * TAU / 3 + h * 1.25;
                Location at = base.clone().add(Math.cos(a) * r + wobX(h), h, Math.sin(a) * r + wobZ(h));
                world.spawnParticle(k % 2 == 0 ? Particle.CLOUD : Particle.WHITE_SMOKE, at, 1, 0.04, 0.04, 0.04, 0.0);
            }
        }
    }

    /** Wind columns circling the catch edge, drawn inward as the cyclone grows, swallowed when the eye closes. */
    private void feeders(double p) {
        if (tick > CONTRACT + 1) {
            return;
        }
        double reach = CATCH - 0.3 - p * 4.2;
        for (int j = 0; j < 3; j++) {
            double a = spin * 0.35 + j * TAU / 3;
            Location foot = base.clone().add(Math.cos(a) * reach, 0, Math.sin(a) * reach);
            if (tick == CONTRACT + 1) {
                world.spawnParticle(Particle.GUST, foot.clone().add(0, 1.2, 0), 1, 0, 0, 0, 0);
                continue;
            }
            for (int k = 0; k < 6; k++) {
                double h = k * 0.55;
                double rr = 0.25 + h * 0.12;
                double b = tick * 0.8 + k * 1.1;
                world.spawnParticle(k % 2 == 0 ? Particle.WHITE_SMOKE : Particle.CLOUD,
                        foot.clone().add(Math.cos(b) * rr, h, Math.sin(b) * rr), 1, 0, 0, 0, 0);
            }
            if (tick % 3 == j) {
                world.spawnParticle(Particle.BLOCK, foot.clone().add(0, 0.1, 0), 3, 0.2, 0.05, 0.2, 0.1, floorData);
            }
        }
    }

    /** The rod keeps a tight spiral of wind around its tip and feeds puffs up into the funnel. */
    private void conductor() {
        Location tip = rodTip();
        Particle.DustOptions dust = new Particle.DustOptions(WIND, 0.6f);
        for (int i = 0; i < 2; i++) {
            double a = tick * 0.9 + i * Math.PI;
            world.spawnParticle(Particle.DUST, tip.clone().add(Math.cos(a) * 0.22, 0.05 * i, Math.sin(a) * 0.22),
                    1, 0, 0, 0, 0, dust);
        }
        if (tick % 5 == 0) {
            world.spawnParticle(Particle.CLOUD, tip, 0, 0, 1, 0, 0.25);
        }
    }

    private void trails() {
        Iterator<LivingEntity> it = orbiting.iterator();
        while (it.hasNext()) {
            LivingEntity body = it.next();
            if (!body.isValid() || body.isDead()) {
                it.remove();
                continue;
            }
            Location at = body.getLocation().add(0, body.getHeight() * 0.5, 0);
            world.spawnParticle(Particle.CLOUD, at, 1, 0.15, 0.1, 0.15, 0.0);
            if (tick % 3 == 0) {
                world.spawnParticle(Particle.WHITE_SMOKE, at, 1, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    /** Wind bed that climbs in pitch, riptide swells as it grows, then the eye closes with a rising tick. */
    private void howl(double p, double k) {
        Location at = base.clone().add(0, 1.5, 0);
        if (tick == 1) {
            world.playSound(at, Sound.ITEM_ELYTRA_FLYING, 0.45f, 0.55f);
        }
        if (tick == 28) {
            world.playSound(at, Sound.ITEM_ELYTRA_FLYING, 0.5f, 0.85f);
        }
        if (tick % 5 == 0) {
            world.playSound(at, Sound.ENTITY_BREEZE_IDLE_GROUND, 0.3f + (float) p * 0.3f, 0.6f + (float) p * 1.0f);
        }
        if (tick % 12 == 6) {
            world.playSound(at, Sound.ENTITY_BREEZE_WHIRL, 0.5f + (float) p * 0.3f, 0.6f + (float) p * 0.9f);
        }
        if (tick == 14) {
            world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.5f, 0.9f);
        }
        if (tick == 30) {
            world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.55f, 1.0f);
        }
        if (tick == CONTRACT + 1) {
            world.playSound(at, Sound.ENTITY_BREEZE_INHALE, 1.0f, 1.2f);
            world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8f, 1.3f);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§f§l✦ THE EYE CLOSES"));
            for (int i = 0; i < 28; i++) {
                double ang = TAU * i / 28;
                Location from = base.clone().add(Math.cos(ang) * CATCH, 0.4, Math.sin(ang) * CATCH);
                world.spawnParticle(Particle.CLOUD, from, 0, -Math.cos(ang), 0.05, -Math.sin(ang), 0.6);
            }
        }
        if (k > 0.0 && tick % 2 == 0) {
            world.playSound(at, Sound.ENTITY_BREEZE_SLIDE, 0.55f, 1.0f + (float) k);
        }
    }

    // ------------------------------------------------------------------ burst

    /** The fling: old damage and knockback, plus the funnel blowing apart and a gust front across the floor. */
    private void burst() {
        Location center = player.getLocation().add(0, 0.2, 0);
        base = floorUnder(player.getLocation());
        Location core = center.clone().add(0, 1.2, 0);

        world.spawnParticle(Particle.EXPLOSION_EMITTER, core, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.GUST, core, 1, 0, 0, 0, 0);
        for (int i = 0; i < 8; i++) {
            double ang = TAU * i / 8 + spin;
            world.spawnParticle(Particle.GUST, base.clone().add(Math.cos(ang) * 2.6, 1.0, Math.sin(ang) * 2.6), 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 36; i++) {
            double ang = TAU * i / 36;
            world.spawnParticle(Particle.CLOUD, base.clone().add(0, 0.25, 0), 0, Math.cos(ang), 0.03, Math.sin(ang), 0.6);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.SWEEP_ATTACK,
                        base.clone().add(Math.cos(ang) * 2.2, 1.0, Math.sin(ang) * 2.2), 1, 0, 0, 0, 0);
            }
        }
        world.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.2, 0), 50, 2.0, 0.2, 2.0, 0.3, floorData);
        world.spawnParticle(Particle.WHITE_SMOKE, core, 30, 1.2, 1.0, 1.2, 0.12);

        world.playSound(core, Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.6f);
        world.playSound(core, Sound.ENTITY_BREEZE_SHOOT, 0.9f, 0.7f);
        world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.9f);
        world.playSound(core, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 0.9f, 0.8f);
        world.playSound(core, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);

        for (Entity entity : world.getNearbyEntities(center, FLING, FLING_Y, FLING)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            if (!caught.contains(living.getUniqueId())
                    && living.getLocation().distanceSquared(center) > 64) {
                continue;
            }
            ScriptedHits.run(() -> living.damage(damage, player));
            Vector away = living.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() < 0.01) {
                away = new Vector(ThreadLocalRandom.current().nextDouble(-1, 1), 0.5,
                        ThreadLocalRandom.current().nextDouble(-1, 1));
            }
            away.normalize().multiply(2.8).setY(1.35);
            living.setVelocity(away);

            Location at = living.getLocation().add(0, living.getHeight() * 0.5, 0);
            world.spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.DAMAGE_INDICATOR, at, 6, 0.3, 0.3, 0.3, 0);
            if (flung.size() < MAX_TRAILS) {
                flung.add(new Flung(living));
                world.spawnParticle(Particle.GUST, at, 1, 0, 0, 0, 0);
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.6f, 0.8f);
            }
        }
        orbiting.clear();
        player.sendMessage("§f✦ Cyclone §7dispersed.");
        release();

        for (Band band : bands) {
            double lt = band.layer / (double) (LAYERS - 1);
            double r = band.r * 1.8 + 2.5;
            double y = band.y + 1.2 + lt * 1.5;
            ease(band.display, ribbon(band.angle + 0.6, r, y, 0, 0, band.length * 1.7f, band.height * 0.6f, 0.03f,
                    (float) Math.toRadians(30.0)), 5);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Chunk chunk : debris) {
            if (chunk.at == null) {
                continue;
            }
            double reach = random.nextDouble(4.5, 7.0);
            chunk.at = new Vector3f(chunk.at).add(new Vector3f(chunk.out).mul((float) reach)).add(0f, 2.0f, 0f);
            chunk.tumble.rotateAxis(2.5f, chunk.axis);
            ease(chunk.display, cube(chunk.at, chunk.tumble, (float) chunk.size), 5);
        }
    }

    /** {@code t} counts from 1 after the burst. */
    private void aftermath(int t) {
        if (t <= 9) {
            double k = t / 9.0;
            double ease = 1.0 - Math.pow(1.0 - k, 3);
            double r = 1.5 + ease * 9.5;
            Particle.DustOptions front = new Particle.DustOptions(mix(WIND, GREY, k), (float) (1.6 - k * 0.7));
            int points = 40;
            for (int i = 0; i < points; i++) {
                double ang = TAU * i / points;
                world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(ang) * r, 0.15, Math.sin(ang) * r),
                        1, 0, 0, 0, 0, front);
            }
        }
        if (t <= 8) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 4; i++) {
                double h = random.nextDouble(0.5, TOP * 1.4);
                double r = random.nextDouble(0.3, 1.4);
                double a = random.nextDouble(TAU);
                world.spawnParticle(Particle.WHITE_SMOKE, base.clone().add(Math.cos(a) * r, h, Math.sin(a) * r),
                        0, 0, 1, 0, 0.12);
            }
        }
        if (t == 5) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (Chunk chunk : debris) {
                if (chunk.at == null) {
                    continue;
                }
                chunk.at = new Vector3f(chunk.at).add(new Vector3f(chunk.out).mul(1.5f));
                chunk.at.y = 0.1f;
                chunk.tumble.rotateAxis((float) random.nextDouble(1.5, 3.0), chunk.axis);
                ease(chunk.display, cube(chunk.at, chunk.tumble, (float) chunk.size), 7);
            }
        }
        if (t == 6) {
            for (Band band : bands) {
                ease(band.display, ribbon(band.angle + 0.9, band.r * 1.8 + 3.5, band.y + 2.0, 0, 0,
                        0.001f, 0.001f, 0.001f, 0f), 6);
            }
            world.playSound(base, Sound.ENTITY_BREEZE_IDLE_GROUND, 0.3f, 0.5f);
        }
        if (t == 12) {
            for (Chunk chunk : debris) {
                if (chunk.at != null) {
                    ease(chunk.display, cube(chunk.at, chunk.tumble, 0.001f), 4);
                    world.spawnParticle(Particle.BLOCK, base.clone().add(chunk.at.x, 0.2, chunk.at.z), 4,
                            0.15, 0.05, 0.15, 0.05, chunk.display.getBlock());
                }
            }
        }
        if (t == 13) {
            removeBands();
        }
    }

    private void tickFlung() {
        Iterator<Flung> it = flung.iterator();
        while (it.hasNext()) {
            Flung f = it.next();
            f.age++;
            if (f.age > 14 || !f.body.isValid() || f.body.isDead()) {
                it.remove();
                continue;
            }
            Location at = f.body.getLocation().add(0, f.body.getHeight() * 0.5, 0);
            world.spawnParticle(Particle.CLOUD, at, 2, 0.12, 0.12, 0.12, 0.0);
            if (f.age % 2 == 0) {
                world.spawnParticle(Particle.WHITE_SMOKE, at, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    // ------------------------------------------------------------------ geometry

    private double funnelAt(double h) {
        return radius * squeeze * (0.28 + 0.72 * Math.pow(clamp(h / TOP), 1.15));
    }

    private double wobX(double h) {
        double lt = clamp(h / TOP);
        return Math.cos(tick * 0.19 + lt * 2.75) * sway * lt;
    }

    private double wobZ(double h) {
        double lt = clamp(h / TOP);
        return Math.sin(tick * 0.23 + lt * 2.75) * sway * lt;
    }

    /** Where the rod sits in third person: right hand, raised a little forward. */
    private Location rodTip() {
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        look.normalize();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        return player.getLocation().add(0, 1.45, 0).add(right.multiply(0.38)).add(look.multiply(0.45));
    }

    private Location floorUnder(Location at) {
        RayTraceResult hit = world.rayTraceBlocks(at.clone().add(0, 0.5, 0), new Vector(0, -1, 0), 4.0,
                FluidCollisionMode.NEVER, true);
        Location floor = hit != null && hit.getHitPosition() != null
                ? hit.getHitPosition().toLocation(world)
                : at.clone();
        floor.setYaw(0);
        floor.setPitch(0);
        return floor;
    }

    /** A banked wind ribbon on the funnel wall: long side along the orbit, leading edge tilted up. */
    private static Transformation ribbon(double angle, double r, double y, double wx, double wz,
                                         float length, float height, float thick, float bank) {
        Vector3f radial = new Vector3f((float) Math.cos(angle), 0f, (float) Math.sin(angle));
        Vector3f tangent = new Vector3f((float) -Math.sin(angle), 0f, (float) Math.cos(angle));
        Vector3f up = new Vector3f(0f, 1f, 0f);
        Vector3f z = new Vector3f(tangent).cross(up);
        Quaternionf rot = new Quaternionf().setFromNormalized(new Matrix3f(tangent, up, z)).rotateZ(bank);
        float l = Math.max(0.001f, length);
        float h = Math.max(0.001f, height);
        float t = Math.max(0.001f, thick);
        Vector3f half = new Quaternionf(rot).transform(new Vector3f(l / 2f, h / 2f, t / 2f));
        Vector3f center = radial.mul((float) r).add((float) wx, (float) y, (float) wz);
        return new Transformation(center.sub(half), rot, new Vector3f(l, h, t), new Quaternionf());
    }

    /** A tumbling cube centered on {@code at}. */
    private static Transformation cube(Vector3f at, Quaternionf tumble, float size) {
        float s = Math.max(0.001f, size);
        Quaternionf rot = new Quaternionf(tumble);
        Vector3f half = new Quaternionf(rot).transform(new Vector3f(s / 2f, s / 2f, s / 2f));
        return new Transformation(new Vector3f(at).sub(half), rot, new Vector3f(s, s, s), new Quaternionf());
    }

    private static double smooth(double x) {
        return x * x * (3.0 - 2.0 * x);
    }

    private static double clamp(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    private static Color mix(Color a, Color b, double t) {
        t = clamp(t);
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }

    // ------------------------------------------------------------------ displays

    private BlockDisplay spawn(BlockData data, Display.Brightness brightness) {
        try {
            BlockDisplay display = world.spawn(base, BlockDisplay.class, spawned -> {
                spawned.setBlock(data);
                spawned.setPersistent(false);
                spawned.setBrightness(brightness);
                spawned.setTeleportDuration(2);
                spawned.setInterpolationDuration(0);
                spawned.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void ease(BlockDisplay display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    private void removeBands() {
        for (Band band : bands) {
            discard(band.display);
        }
        bands.clear();
    }

    private void clearAll() {
        removeBands();
        for (Chunk chunk : debris) {
            discard(chunk.display);
        }
        debris.clear();
        orbiting.clear();
        flung.clear();
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

    private static final class Band {
        final BlockDisplay display;
        final int layer;
        final int index;
        double angle;
        double r;
        double y;
        float length = 0.001f;
        float height = 0.001f;

        Band(BlockDisplay display, int layer, int index) {
            this.display = display;
            this.layer = layer;
            this.index = index;
        }
    }

    private static final class Chunk {
        final BlockDisplay display;
        final int index;
        final double rf;
        final double rise;
        final double offset;
        final double size;
        final Vector3f axis;
        final float tumbleRate;
        final Quaternionf tumble = new Quaternionf();
        double h;
        Vector3f at;
        Vector3f out;

        Chunk(BlockDisplay display, int index, ThreadLocalRandom random) {
            this.display = display;
            this.index = index;
            this.rf = random.nextDouble(0.6, 1.0);
            this.rise = random.nextDouble(0.03, 0.08);
            this.offset = random.nextDouble(TAU);
            this.size = random.nextDouble(0.25, 0.55);
            this.axis = new Vector3f((float) random.nextDouble(-1, 1), (float) random.nextDouble(-1, 1),
                    (float) random.nextDouble(-1, 1));
            if (axis.lengthSquared() < 1.0E-3f) {
                axis.set(0f, 1f, 0f);
            }
            axis.normalize();
            this.tumbleRate = (float) random.nextDouble(0.15, 0.4);
            this.h = random.nextDouble(0.3, TOP);
        }
    }

    private static final class Flung {
        final LivingEntity body;
        int age;

        Flung(LivingEntity body) {
            this.body = body;
        }
    }
}
