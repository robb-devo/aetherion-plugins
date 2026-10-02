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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Meteor Mace (meteor_mace) — Starfall.
 * The caster pounds the mace into the floor in an accelerating heartbeat while a molten fissure crawls out to the
 * aimed spot and the ground there wells up inside a crimson blast ring. The upward smash erupts the fissure: hostiles
 * in the zone are launched and held in the updraft, and a flare shoots off the mace head into the sky. The sky tears
 * where the flare lands and a crusted, glowing meteor tumbles out, trailing fire and a lingering smoke contrail, its
 * shadow swelling on the floor. It crashes into the zone: flash, fireball, a white-hot shockwave, a crater whose
 * cracks cool from white-hot through magma to blackstone, lifted rim slabs, flying ejecta and a smoke column.
 * The molten pool keeps burning whoever stands in it.
 */
public final class MeteorMaceCrash {

    private static final int CHARGE = 24;
    private static final int SMASH = CHARGE + 1;
    private static final int OPEN = SMASH + 5;
    private static final int FALL = 22;
    private static final int IMPACT = OPEN + FALL;
    private static final int AFTER = 56;
    private static final int HOLD_FROM = SMASH + 6;
    private static final int[] THUMPS = {1, 8, 14, 18, 21, 23};
    private static final int[] BURNS = {10, 20, 30};
    private static final double MAX_AIM = 24.0;
    private static final double LAUNCH_RADIUS = 5.0;
    private static final double BLAST_RADIUS = 7.0;
    private static final double CORE_RADIUS = 3.5;
    private static final double POOL_RADIUS = 3.2;
    private static final double SKY_HEIGHT = 30.0;
    private static final double HOVER = 3.4;
    private static final int MAX_JUGGLED = 16;
    /** Past this many live displays (all casters), impacts skip rim slabs and most ejecta. */
    private static final int MAX_LIVE = 240;

    private static final Color EMBER = Color.fromRGB(255, 130, 30);
    private static final Color MOLTEN = Color.fromRGB(255, 205, 90);
    private static final Color WHITE_HOT = Color.fromRGB(255, 245, 220);
    private static final Color CRIMSON = Color.fromRGB(190, 30, 15);
    private static final Color SOOT = Color.fromRGB(35, 25, 25);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);
    private static final Display.Brightness CRUST_LIGHT = new Display.Brightness(6, 6);

    private static final Material[] CRUST = {
            Material.BLACKSTONE, Material.BASALT, Material.GILDED_BLACKSTONE, Material.BLACKSTONE
    };
    /** Crust chunks around the magma core: offset x/y/z, size, rotation x/y/z in degrees. */
    private static final float[][] CRUST_SPEC = {
            {0.55f, 0.35f, 0.1f, 1.45f, 20f, 35f, 10f},
            {-0.5f, 0.2f, 0.45f, 1.5f, -15f, 60f, 25f},
            {0.1f, -0.5f, -0.5f, 1.35f, 40f, -20f, -30f},
            {-0.2f, 0.55f, -0.45f, 1.25f, -35f, 10f, 45f}
    };

    private static final List<Display> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final Player player;
    private final World world;
    private final double launchDamage;
    private final double impactDamage;
    private final double burnDamage;
    private final Runnable done;
    private final List<Strip> fissure = new ArrayList<>();
    private final List<Strip> feet = new ArrayList<>();
    private final List<Chunk> meteor = new ArrayList<>();
    private final List<Strip> cracks = new ArrayList<>();
    private final List<BlockDisplay> pool = new ArrayList<>();
    private final List<Slab> rim = new ArrayList<>();
    private final List<Ejecta> ejecta = new ArrayList<>();
    private final List<LivingEntity> juggled = new ArrayList<>();
    private Quaternionf tumble = new Quaternionf();
    private Vector3f tumbleAxis;
    private double tumbleAngle;
    private Location origin;
    private Location zone;
    private Location sky;
    private Vector flat;
    private BlockData floorData;
    private BlockDisplay flare;
    private Location flareBase;
    private Vector flareSpan;
    private Location meteorAt;
    private int tick;
    private boolean released;

    /** Plugin disable: removes every fissure, meteor, crater and ejecta display still standing. */
    public static void shutdown() {
        for (Display display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    /** {@code done} runs once, at impact (or when the cast is aborted before it). */
    static void cast(JavaPlugin plugin, Player player, double launchDamage, double impactDamage, double burnDamage,
                     Runnable done) {
        new MeteorMaceCrash(plugin, player, launchDamage, impactDamage, burnDamage, done).start();
    }

    private MeteorMaceCrash(JavaPlugin plugin, Player player, double launchDamage, double impactDamage,
                            double burnDamage, Runnable done) {
        this.plugin = plugin;
        this.player = player;
        this.world = player.getWorld();
        this.launchDamage = launchDamage;
        this.impactDamage = impactDamage;
        this.burnDamage = burnDamage;
        this.done = done;
    }

    private void start() {
        Location feetAt = player.getLocation();
        flat = feetAt.getDirection().setY(0);
        if (flat.lengthSquared() < 1.0E-4) {
            flat = new Vector(0, 0, 1);
        }
        flat.normalize();
        origin = floorAt(feetAt, 0.6, 3.0);
        if (origin == null) {
            origin = level(feetAt.clone());
        }
        zone = aimZone();
        Vector toZone = zone.toVector().subtract(origin.toVector()).setY(0);
        if (toZone.lengthSquared() > 0.25) {
            flat = toZone.normalize();
        }
        floorData = floorBlockData(zone);
        sky = skyPoint();
        Vector fallDir = zone.toVector().subtract(sky.toVector());
        Vector axis = fallDir.getCrossProduct(new Vector(0, 1, 0));
        if (axis.lengthSquared() < 1.0E-4) {
            axis = new Vector(1, 0, 0);
        }
        axis.normalize();
        tumbleAxis = new Vector3f((float) axis.getX(), (float) axis.getY() + 0.25f, (float) axis.getZ()).normalize();
        buildFissure();
        buildFeetCracks();

        world.playSound(origin, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 0.8f, 0.7f);
        world.playSound(origin, Sound.ITEM_FIRECHARGE_USE, 0.7f, 0.55f);
        world.playSound(zone, Sound.BLOCK_LAVA_AMBIENT, 0.8f, 0.6f);
        world.spawnParticle(Particle.BLOCK, origin.clone().add(0, 0.1, 0), 18, 0.5, 0.05, 0.5, 0.1,
                floorBlockData(origin));

        new BukkitRunnable() {
            @Override
            public void run() {
                boolean casterGone = !player.isOnline() || player.isDead() || player.getWorld() != world;
                if (tick < IMPACT && casterGone) {
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

    /** @return true once the crater and every ejecta chunk are gone */
    private boolean step() {
        tick++;
        if (tick <= CHARGE) {
            charge();
        }
        if (tick == SMASH) {
            smash();
        }
        if (tick > SMASH && tick < OPEN) {
            flareRise();
        }
        if (tick == OPEN) {
            openSky();
        }
        if (tick == OPEN + 3) {
            discard(flare);
            flare = null;
        }
        if (tick > OPEN && tick < IMPACT) {
            fall();
        }
        if (tick >= HOLD_FROM && tick < IMPACT) {
            hold();
        }
        if (tick == IMPACT) {
            impact();
        }
        if (tick > IMPACT) {
            aftermath(tick - IMPACT);
        }
        tickFissure();
        tickEjecta();
        return tick >= IMPACT + AFTER && ejecta.isEmpty();
    }

    private void release() {
        if (!released) {
            released = true;
            done.run();
        }
    }

    // ------------------------------------------------------------------ aim + ground

    private Location aimZone() {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult hit = world.rayTraceBlocks(eye, dir, MAX_AIM, FluidCollisionMode.NEVER, true);
        Location probe;
        if (hit != null && hit.getHitPosition() != null) {
            probe = hit.getHitPosition().toLocation(world);
            if (hit.getHitBlockFace() != null) {
                probe.add(hit.getHitBlockFace().getDirection().multiply(0.4));
            }
        } else {
            probe = eye.clone().add(dir.multiply(MAX_AIM));
        }
        Location floor = floorAt(probe, 1.0, 40.0);
        if (floor == null) {
            floor = floorAt(origin.clone().add(flat.clone().multiply(8.0)), 3.0, 12.0);
        }
        return floor != null ? floor : origin.clone();
    }

    /** Where the flare lands and the meteor tears out: high over the zone, a little beyond it. */
    private Location skyPoint() {
        RayTraceResult roof = world.rayTraceBlocks(zone.clone().add(0, 1.0, 0), new Vector(0, 1, 0), SKY_HEIGHT,
                FluidCollisionMode.NEVER, true);
        double height = SKY_HEIGHT;
        if (roof != null && roof.getHitPosition() != null) {
            height = Math.max(6.0, roof.getHitPosition().getY() - zone.getY() - 1.5);
        }
        height = Math.min(height, world.getMaxHeight() - 2 - zone.getY());
        height = Math.max(6.0, height);
        double reach = Math.hypot(zone.getX() - origin.getX(), zone.getZ() - origin.getZ());
        double back = Math.max(3.0, Math.min(9.0, 28.0 - reach)) * height / SKY_HEIGHT;
        return level(zone.clone().add(flat.clone().multiply(back)).add(0, height, 0));
    }

    private Location floorAt(Location at, double up, double down) {
        Location from = at.clone().add(0, up, 0);
        RayTraceResult hit = world.rayTraceBlocks(from, new Vector(0, -1, 0), up + down, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitPosition() == null) {
            return null;
        }
        return level(hit.getHitPosition().toLocation(world));
    }

    private static BlockData floorBlockData(Location at) {
        Block block = at.clone().add(0, -0.1, 0).getBlock();
        return block.getType().isSolid() ? block.getBlockData() : Material.BLACKSTONE.createBlockData();
    }

    private static Location level(Location at) {
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    /** The mace head: planted on the floor in front while charging, raised over the shoulder after the smash. */
    private Location maceHead(boolean planted) {
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        look.normalize();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        if (planted) {
            return player.getLocation().add(look.multiply(0.9)).add(right.multiply(0.3)).add(0, 0.25, 0);
        }
        return player.getLocation().add(0, 2.4, 0).add(right.multiply(0.35)).add(look.multiply(0.2));
    }

    // ------------------------------------------------------------------ charge

    private void buildFissure() {
        Vector span = zone.toVector().subtract(origin.toVector());
        double len = Math.hypot(span.getX(), span.getZ());
        if (len < 1.0) {
            return;
        }
        int n = Math.max(2, Math.min(26, (int) Math.ceil(len / 0.9)));
        Vector side = new Vector(-flat.getZ(), 0, flat.getX());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Location> points = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            if (i == 0) {
                points.add(origin.clone());
                continue;
            }
            if (i == n) {
                points.add(zone.clone());
                continue;
            }
            Location p = origin.clone().add(span.clone().multiply(t))
                    .add(side.clone().multiply(random.nextDouble(-0.45, 0.45) * Math.sin(Math.PI * t)));
            Location floor = floorAt(p, 1.2, 3.0);
            points.add(floor != null ? floor : p);
        }
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            float width = (float) (0.24 + 0.22 * t * t);
            int appear = 2 + (int) (i * (CHARGE - 6) / (double) n);
            fissure.add(new Strip(points.get(i), points.get(i + 1), width, appear, Material.SHROOMLIGHT));
        }
    }

    private void buildFeetCracks() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double base = random.nextDouble(Math.PI * 2);
        for (int i = 0; i < 6; i++) {
            double a = base + i * Math.PI / 3 + random.nextDouble(-0.25, 0.25);
            double len = random.nextDouble(1.0, 1.7);
            Location end = origin.clone().add(Math.cos(a) * len, 0, Math.sin(a) * len);
            feet.add(new Strip(origin.clone().add(Math.cos(a) * 0.3, 0, Math.sin(a) * 0.3), end, 0.16f, 1 + i,
                    Material.SHROOMLIGHT));
        }
    }

    private void charge() {
        double k = tick / (double) CHARGE;
        int shown = 0;
        for (Strip strip : fissure) {
            if (tick == strip.appear) {
                strip.show(1f, 1f, 2);
            }
            if (tick >= strip.appear) {
                shown++;
            }
        }
        for (Strip strip : feet) {
            if (tick == strip.appear) {
                strip.show(1f, 1f, 2);
            }
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (shown > 0) {
            for (int i = 0; i < 2; i++) {
                Location at = fissure.get(random.nextInt(shown)).point(random.nextDouble()).add(0, 0.1, 0);
                world.spawnParticle(Particle.FLAME, at, 0, 0, 1, 0, 0.04);
                world.spawnParticle(Particle.SMOKE, at, 1, 0.05, 0.05, 0.05, 0.01);
                if (tick % 3 == i) {
                    world.spawnParticle(Particle.LAVA, at, 1, 0, 0, 0, 0);
                }
            }
            Location head = fissure.get(shown - 1).point(1.0).add(0, 0.1, 0);
            world.spawnParticle(Particle.BLOCK, head, 4, 0.2, 0.05, 0.2, 0.05, floorData);
            if (tick % 3 == 0) {
                world.playSound(head, Sound.BLOCK_BASALT_BREAK, 0.5f, 0.6f);
                world.playSound(head, Sound.BLOCK_LAVA_POP, 0.45f, 0.7f + random.nextFloat() * 0.3f);
            }
        }

        if (tick % 2 == 0) {
            Color ring = mix(CRIMSON, EMBER, k);
            drawRing(zone, BLAST_RADIUS, 36, new Particle.DustOptions(ring, 1.3f), tick * 0.04, true);
        }
        if (tick % 4 == 0) {
            drawRing(zone, LAUNCH_RADIUS * (0.55 + 0.45 * k), 24, new Particle.DustOptions(MOLTEN, 1.0f),
                    -tick * 0.06, false);
        }
        Location well = zone.clone().add(random.nextDouble(-2.5, 2.5), 0.1, random.nextDouble(-2.5, 2.5));
        if (tick % 2 == 0) {
            world.spawnParticle(Particle.LAVA, well, 1, 0, 0, 0, 0);
        }
        world.spawnParticle(Particle.SMOKE, well, 1, 0.1, 0.05, 0.1, 0.02);
        if (k > 0.5) {
            world.spawnParticle(Particle.FLAME, zone.clone().add(random.nextDouble(-1.5, 1.5), 0.15,
                    random.nextDouble(-1.5, 1.5)), 0, 0, 1, 0, 0.12 + k * 0.1);
        }

        Location head = maceHead(true);
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble(Math.PI * 2);
            Location from = player.getLocation().add(Math.cos(a) * 1.7, 0.1, Math.sin(a) * 1.7);
            Vector pull = head.toVector().subtract(from.toVector());
            world.spawnParticle(Particle.FLAME, from, 0, pull.getX(), pull.getY(), pull.getZ(), 0.07);
        }
        if (tick % 2 == 0) {
            world.spawnParticle(Particle.SMALL_FLAME, head, 2, 0.08, 0.08, 0.08, 0.01);
        }

        for (int i = 0; i < THUMPS.length; i++) {
            if (tick == THUMPS[i]) {
                thump(i);
            }
        }
    }

    /** One beat of the ground heartbeat: faster and louder toward the smash. */
    private void thump(int beat) {
        Location at = player.getLocation();
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND, 0.45f + beat * 0.08f, 0.6f + beat * 0.07f);
        world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.1, 0), 10, 0.5, 0.05, 0.5, 0.1, floorBlockData(at));
        drawRing(at.clone().add(0, 0.1, 0), 1.0 + beat * 0.12, 16, new Particle.DustOptions(EMBER, 1.0f), 0, false);
        world.spawnParticle(Particle.DUST_PILLAR, zone.clone().add(0, 0.1, 0), 5 + beat * 2, 1.4, 0.05, 1.4, 0,
                floorData);
        if (beat == THUMPS.length - 1) {
            world.playSound(zone, Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 0.5f);
        }
    }

    // ------------------------------------------------------------------ smash + flare

    private void smash() {
        Location feetAt = player.getLocation();
        Vector look = feetAt.getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = flat.clone();
        }
        look.normalize();
        Location pivot = feetAt.clone().add(0, 1.2, 0);
        Particle.DustOptions arc = new Particle.DustOptions(MOLTEN, 1.4f);
        for (int i = 0; i <= 14; i++) {
            double a = -1.0 + i / 14.0 * 2.6;
            Location p = pivot.clone().add(look.clone().multiply(Math.cos(a) * 1.7)).add(0, Math.sin(a) * 1.7, 0);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, arc);
            world.spawnParticle(Particle.FLAME, p, 0, 0, 1, 0, 0.05);
        }
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            world.spawnParticle(Particle.CLOUD, feetAt.clone().add(0, 0.15, 0), 0, Math.cos(a), 0.02, Math.sin(a), 0.3);
        }
        world.playSound(feetAt, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.75f);
        world.playSound(feetAt, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 0.6f);
        world.playSound(feetAt, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.5f);
        world.playSound(feetAt, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);

        for (Strip strip : fissure) {
            strip.show(1.9f, 1f, 1);
        }
        for (Strip strip : feet) {
            strip.show(1.9f, 1f, 1);
        }

        erupt();

        flareBase = level(maceHead(false));
        flareSpan = sky.toVector().subtract(flareBase.toVector());
        flare = spawn(flareBase, Material.WHITE_CONCRETE.createBlockData(), LIT, EMBER,
                beam(new Vector(), flareSpan.clone().multiply(0.02), 0.26f));
    }

    /** The fissure bursts at the zone: a geyser of fire and floor that launches hostiles into the updraft. */
    private void erupt() {
        Location c = zone.clone().add(0, 0.2, 0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        world.spawnParticle(Particle.DUST_PILLAR, c, 50, 1.6, 0.1, 1.6, 0, floorData);
        world.spawnParticle(Particle.LAVA, c, 16, 1.5, 0.2, 1.5, 0);
        world.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.6, 0), 3, 1.0, 0.3, 1.0, 0);
        for (int i = 0; i < 26; i++) {
            Location at = c.clone().add(random.nextDouble(-2.0, 2.0), 0, random.nextDouble(-2.0, 2.0));
            world.spawnParticle(Particle.FLAME, at, 0, random.nextDouble(-0.1, 0.1), 1, random.nextDouble(-0.1, 0.1),
                    random.nextDouble(0.35, 0.8));
        }
        world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.25f);
        world.playSound(c, Sound.BLOCK_LAVA_EXTINGUISH, 0.9f, 0.6f);
        world.playSound(c, Sound.ITEM_FIRECHARGE_USE, 0.9f, 0.7f);

        for (Entity entity : world.getNearbyEntities(zone, LAUNCH_RADIUS, 3.0, LAUNCH_RADIUS)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            Vector rel = living.getLocation().toVector().subtract(zone.toVector());
            if (Math.hypot(rel.getX(), rel.getZ()) > LAUNCH_RADIUS) {
                continue;
            }
            ScriptedHits.run(() -> living.damage(launchDamage, player));
            Vector in = rel.setY(0).multiply(-0.08);
            living.setVelocity(in.setY(1.05));
            living.setFallDistance(0f);
            if (juggled.size() < MAX_JUGGLED) {
                juggled.add(living);
            }
        }
    }

    private void flareRise() {
        int f = tick - SMASH;
        if (flare != null && f == 1) {
            ease(flare, beam(new Vector(), flareSpan, 0.26f), 4);
        }
        double reach = Math.min(1.0, f / 4.0);
        Location tip = flareBase.clone().add(flareSpan.clone().multiply(reach));
        world.spawnParticle(Particle.FLAME, tip, 4, 0.15, 0.15, 0.15, 0.03);
        world.spawnParticle(Particle.FIREWORK, tip, 2, 0.1, 0.1, 0.1, 0.02);
        world.spawnParticle(Particle.DUST, flareBase.clone().add(flareSpan.clone().multiply(reach * 0.5)), 2,
                0.1, 0.4, 0.1, 0, new Particle.DustOptions(MOLTEN, 1.2f));
    }

    // ------------------------------------------------------------------ meteor

    private void openSky() {
        if (flare != null) {
            ease(flare, beam(new Vector(), flareSpan, 0.001f), 3);
        }
        world.spawnParticle(Particle.FLASH, sky, 1, 0, 0, 0, 0);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            world.spawnParticle(Particle.FLAME, sky, 0, Math.cos(a), 0, Math.sin(a), 0.35);
        }
        drawRing(sky, 3.0, 28, new Particle.DustOptions(CRIMSON, 2.0f), 0, false);
        world.spawnParticle(Particle.LARGE_SMOKE, sky, 14, 2.0, 0.4, 2.0, 0.02);
        Location ear = player.getLocation();
        world.playSound(ear, Sound.ENTITY_GHAST_SHOOT, 1.0f, 0.5f);
        world.playSound(ear, Sound.ENTITY_WITHER_SHOOT, 0.7f, 0.55f);
        world.playSound(ear, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.45f, 1.3f);

        meteorAt = sky.clone();
        addChunk(Material.MAGMA_BLOCK, LIT, new Vector3f(), new Quaternionf(), 1.9f);
        for (int i = 0; i < CRUST_SPEC.length; i++) {
            float[] s = CRUST_SPEC[i];
            Quaternionf base = new Quaternionf().rotateXYZ((float) Math.toRadians(s[4]),
                    (float) Math.toRadians(s[5]), (float) Math.toRadians(s[6]));
            addChunk(CRUST[i], CRUST_LIGHT, new Vector3f(s[0], s[1], s[2]), base, s[3]);
        }
        addChunk(Material.ORANGE_STAINED_GLASS, LIT, new Vector3f(), new Quaternionf().rotateXYZ(0.3f, 0.5f, 0.2f), 2.9f);
        poseMeteor(0.3f);
    }

    private void addChunk(Material material, Display.Brightness light, Vector3f offset, Quaternionf base, float size) {
        BlockDisplay display = spawn(meteorAt, material.createBlockData(), light, null, null);
        if (display != null) {
            display.setTeleportDuration(1);
            meteor.add(new Chunk(display, offset, base, size));
        }
    }

    private void fall() {
        int f = tick - OPEN;
        double t = f / (double) FALL;
        double e = Math.pow(t, 1.8);
        Location prev = meteorAt.clone();
        Location end = zone.clone().add(0, 1.1, 0);
        meteorAt = level(sky.clone().add(end.toVector().subtract(sky.toVector()).multiply(e)));
        tumbleAngle += 0.22;
        tumble = new Quaternionf().rotateAxis((float) tumbleAngle, tumbleAxis);
        poseMeteor((float) Math.min(1.0, 0.3 + f / 5.0 * 0.7));
        trail(prev, meteorAt);
        warning(e);
        if (f % 5 == 0) {
            int step = f / 5;
            world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.35f + step * 0.1f, 0.5f + step * 0.08f);
        }
        if (f == FALL - 7) {
            world.playSound(zone, Sound.ENTITY_PHANTOM_SWOOP, 1.3f, 0.5f);
            world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 0.6f, 0.6f);
        }
    }

    private void poseMeteor(float grow) {
        for (Chunk chunk : meteor) {
            if (!chunk.display.isValid()) {
                continue;
            }
            chunk.display.teleport(meteorAt);
            Quaternionf rot = new Quaternionf(tumble).mul(chunk.base);
            float s = chunk.size * grow;
            Vector3f center = new Quaternionf(tumble).transform(new Vector3f(chunk.offset).mul(grow));
            Vector3f half = new Quaternionf(rot).transform(new Vector3f(s / 2f, s / 2f, s / 2f));
            ease(chunk.display, new Transformation(center.sub(half), rot, new Vector3f(s, s, s), new Quaternionf()), 1);
        }
    }

    /** Fire, smoke and a lingering contrail along the stretch the meteor covered this tick. */
    private void trail(Location from, Location to) {
        Vector span = to.toVector().subtract(from.toVector());
        Particle.DustOptions ember = new Particle.DustOptions(EMBER, 2.2f);
        for (int s = 0; s < 3; s++) {
            Location p = from.clone().add(span.clone().multiply(s / 3.0));
            world.spawnParticle(Particle.FLAME, p, 2, 0.45, 0.45, 0.45, 0.02);
            world.spawnParticle(Particle.LARGE_SMOKE, p, 1, 0.5, 0.5, 0.5, 0.01);
            world.spawnParticle(Particle.DUST, p, 1, 0.4, 0.4, 0.4, 0, ember);
        }
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, from, 1, 0.3, 0.3, 0.3, 0.01);
        if (tick % 2 == 0) {
            world.spawnParticle(Particle.LAVA, from, 1, 0.3, 0.3, 0.3, 0);
        }
    }

    /** The blast ring and the meteor's shadow swelling on the floor as it closes in. */
    private void warning(double closing) {
        if (tick % 2 == 0) {
            drawRing(zone, BLAST_RADIUS, 36, new Particle.DustOptions(CRIMSON, 1.4f), tick * 0.05, true);
        }
        if (tick % 3 == 0) {
            drawRing(zone, LAUNCH_RADIUS, 24, new Particle.DustOptions(EMBER, 1.0f), -tick * 0.07, false);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double r = 0.8 + 3.4 * closing;
        Particle.DustOptions shadow = new Particle.DustOptions(SOOT, 2.0f);
        for (int i = 0; i < 10; i++) {
            double a = random.nextDouble(Math.PI * 2);
            double d = Math.sqrt(random.nextDouble()) * r;
            world.spawnParticle(Particle.DUST, zone.clone().add(Math.cos(a) * d, 0.1, Math.sin(a) * d), 1, 0, 0, 0, 0,
                    shadow);
        }
    }

    /** Launched hostiles hang in the updraft over the zone until the meteor arrives. */
    private void hold() {
        juggled.removeIf(living -> !living.isValid() || living.isDead() || living.getWorld() != world);
        for (LivingEntity living : juggled) {
            Location at = living.getLocation();
            double dy = zone.getY() + HOVER - at.getY();
            Vector pull = zone.toVector().subtract(at.toVector()).setY(0).multiply(0.05);
            if (pull.lengthSquared() > 0.04) {
                pull.normalize().multiply(0.2);
            }
            living.setVelocity(pull.setY(Math.max(-0.3, Math.min(0.4, dy * 0.2))));
            living.setFallDistance(0f);
            if (tick % 3 == 0) {
                world.spawnParticle(Particle.FLAME, at, 0, 0, 1, 0, 0.06);
                world.spawnParticle(Particle.SMALL_FLAME, at, 2, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }

    // ------------------------------------------------------------------ impact

    private void impact() {
        Location end = zone.clone().add(0, 1.1, 0);
        if (meteorAt != null) {
            trail(meteorAt, end);
        }
        for (Chunk chunk : meteor) {
            discard(chunk.display);
        }
        meteor.clear();

        Location c = zone.clone().add(0, 0.3, 0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        world.spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLASH, c.clone().add(0, 2.0, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, c.clone().add(0, 0.6, 0), 2, 0.8, 0.3, 0.8, 0);
        world.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.8, 0), 8, 2.5, 0.6, 2.5, 0);
        world.spawnParticle(Particle.LAVA, c, 40, 2.0, 0.4, 2.0, 0);
        world.spawnParticle(Particle.BLOCK, c, 80, 2.5, 0.3, 2.5, 0.3, floorData);
        world.spawnParticle(Particle.DUST_PILLAR, c, 50, 2.0, 0.1, 2.0, 0, floorData);
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, c, 14, 1.5, 0.4, 1.5, 0.02);
        world.spawnParticle(Particle.LARGE_SMOKE, c.clone().add(0, 0.8, 0), 30, 2.0, 0.6, 2.0, 0.04);
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40 + random.nextDouble(-0.05, 0.05);
            world.spawnParticle(Particle.FLAME, c, 0, Math.cos(a), 0.05, Math.sin(a), random.nextDouble(0.35, 0.55));
        }

        world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        world.playSound(c, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.55f);
        world.playSound(c, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.6f, 0.6f);
        world.playSound(c, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.2f, 0.5f);
        world.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.6f);
        if (player.isOnline() && player.getLocation().distanceSquared(c) > 144.0) {
            world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.45f);
        }

        for (Entity entity : world.getNearbyEntities(zone, BLAST_RADIUS, 5.0, BLAST_RADIUS)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            Vector rel = living.getLocation().toVector().subtract(zone.toVector());
            double d = Math.hypot(rel.getX(), rel.getZ());
            boolean held = juggled.contains(living);
            if (d > BLAST_RADIUS && !held) {
                continue;
            }
            double falloff = held || d <= CORE_RADIUS
                    ? 1.0
                    : 1.0 - 0.4 * (d - CORE_RADIUS) / (BLAST_RADIUS - CORE_RADIUS);
            double amount = impactDamage * falloff;
            ScriptedHits.run(() -> living.damage(amount, player));
            living.setFireTicks(Math.max(living.getFireTicks(), 60));
            living.setFallDistance(0f);
            if (held) {
                living.setVelocity(new Vector(0, -1.6, 0));
            } else {
                Vector away = rel.setY(0);
                if (away.lengthSquared() < 0.01) {
                    away = new Vector(random.nextDouble(-1, 1), 0, random.nextDouble(-1, 1));
                }
                living.setVelocity(away.normalize().multiply(1.7).setY(0.8));
            }
            world.spawnParticle(Particle.FLAME, living.getLocation().add(0, living.getHeight() * 0.5, 0), 6,
                    0.25, 0.3, 0.25, 0.03);
        }

        crater();
        release();
    }

    private void crater() {
        boolean crowded = LIVE.size() > MAX_LIVE;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 2; i++) {
            BlockDisplay disc = spawn(zone, Material.SHROOMLIGHT.createBlockData(), LIT, null,
                    poolTile(i * 45f, 0.01f));
            if (disc != null) {
                pool.add(disc);
            }
        }
        int count = crowded ? 5 : 10;
        double base = random.nextDouble(Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double heading = base + Math.PI * 2 * i / count + random.nextDouble(-0.2, 0.2);
            Location a = zone.clone().add(Math.cos(heading) * 1.0, 0, Math.sin(heading) * 1.0);
            double len1 = random.nextDouble(1.6, 2.4);
            Location b = crackPoint(a, heading, len1);
            heading += random.nextDouble(-0.4, 0.4);
            Location c = crackPoint(b, heading, random.nextDouble(1.2, 2.2));
            cracks.add(new Strip(a, b, 0.32f, 1, Material.SHROOMLIGHT));
            cracks.add(new Strip(b, c, 0.18f, 3, Material.SHROOMLIGHT));
        }
        if (!crowded) {
            double turn = random.nextDouble(Math.PI * 2);
            for (int i = 0; i < 8; i++) {
                Slab slab = new Slab(turn + Math.PI * 2 * i / 8 + random.nextDouble(-0.12, 0.12),
                        random.nextDouble(3.2, 3.8), (float) random.nextDouble(25, 42));
                if (slab.display != null) {
                    rim.add(slab);
                }
            }
        }
        int chunks = crowded ? 4 : 12;
        for (int i = 0; i < chunks; i++) {
            double a = random.nextDouble(Math.PI * 2);
            double speed = random.nextDouble(0.22, 0.5);
            Vector vel = new Vector(Math.cos(a) * speed, random.nextDouble(0.45, 0.85), Math.sin(a) * speed);
            int kind = random.nextInt(3);
            BlockData data = kind == 0 ? Material.MAGMA_BLOCK.createBlockData()
                    : kind == 1 ? floorData : Material.BLACKSTONE.createBlockData();
            Ejecta chunk = new Ejecta(zone.clone().add(random.nextDouble(-0.6, 0.6), 0.7, random.nextDouble(-0.6, 0.6)),
                    vel, data, kind == 0, (float) random.nextDouble(0.3, 0.65));
            if (chunk.display != null) {
                ejecta.add(chunk);
            }
        }
    }

    private Location crackPoint(Location from, double heading, double len) {
        Location raw = from.clone().add(Math.cos(heading) * len, 0, Math.sin(heading) * len);
        Location floor = floorAt(raw, 1.2, 2.5);
        return floor != null ? floor : raw;
    }

    private void aftermath(int a) {
        if (a == 1) {
            for (int i = 0; i < pool.size(); i++) {
                ease(pool.get(i), poolTile(i * 45f, 2.8f), 2);
            }
            for (Slab slab : rim) {
                slab.pose(1f, 3);
            }
        }
        for (Strip crack : cracks) {
            if (a == crack.appear) {
                crack.show(1f, 1f, 2);
            }
        }
        if (a == 3) {
            for (LivingEntity living : juggled) {
                if (!living.isValid() || living.isDead()) {
                    continue;
                }
                Vector away = living.getLocation().toVector().subtract(zone.toVector()).setY(0);
                if (away.lengthSquared() < 0.01) {
                    away = new Vector(ThreadLocalRandom.current().nextDouble(-1, 1), 0,
                            ThreadLocalRandom.current().nextDouble(-1, 1));
                }
                living.setVelocity(away.normalize().multiply(1.2).setY(0.55));
                living.setFallDistance(0f);
            }
            juggled.clear();
        }

        if (a <= 10) {
            double k = a / 10.0;
            double ease = 1.0 - Math.pow(1.0 - k, 3);
            double r = 1.0 + ease * (BLAST_RADIUS + 2.0);
            Particle.DustOptions wave = new Particle.DustOptions(mix(WHITE_HOT, EMBER, k), (float) (1.8 - k * 0.8));
            int points = 44;
            for (int i = 0; i < points; i++) {
                double ang = Math.PI * 2 * i / points;
                Location at = zone.clone().add(Math.cos(ang) * r, 0.15, Math.sin(ang) * r);
                world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, wave);
                if (i % 4 == 0) {
                    world.spawnParticle(Particle.FLAME, at, 0, Math.cos(ang), 0.1, Math.sin(ang), 0.1);
                }
                if (a <= 6 && i % 3 == 0) {
                    world.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.4, 0), 1, 0.05, 0.1, 0.05, 0.01);
                }
            }
        }
        if (a <= 24 && a % 2 == 0) {
            double h = 1.0 + a * 0.3;
            double spread = 0.4 + a * 0.04;
            world.spawnParticle(Particle.FLAME, zone.clone().add(0, h, 0), 4, spread, 0.3, spread, 0.02);
            world.spawnParticle(Particle.LARGE_SMOKE, zone.clone().add(0, h + 0.4, 0), 3, spread, 0.3, spread, 0.02);
            if (a % 6 == 0) {
                world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, zone.clone().add(0, 0.5, 0), 3, 0.8, 0.2, 0.8, 0.01);
            }
        }
        if (a == 6) {
            world.playSound(zone, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.4f);
            world.playSound(zone, Sound.BLOCK_LAVA_EXTINGUISH, 0.9f, 0.5f);
        }
        for (int burn : BURNS) {
            if (a == burn) {
                burn();
            }
        }

        if (a == 14) {
            recolor(cracks, Material.MAGMA_BLOCK);
        } else if (a == 30) {
            recolor(cracks, Material.BLACKSTONE);
        }
        if (a == 18) {
            for (BlockDisplay disc : pool) {
                disc.setBlock(Material.MAGMA_BLOCK.createBlockData());
            }
        } else if (a == BURNS[BURNS.length - 1] + 4) {
            for (BlockDisplay disc : pool) {
                disc.setBlock(Material.BLACKSTONE.createBlockData());
                disc.setBrightness(null);
            }
        }
        if (a <= 40 && a % 2 == 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double ang = random.nextDouble(Math.PI * 2);
            double d = Math.sqrt(random.nextDouble()) * POOL_RADIUS * 0.8;
            Location at = zone.clone().add(Math.cos(ang) * d, 0.1, Math.sin(ang) * d);
            world.spawnParticle(a < 30 ? Particle.LAVA : Particle.SMOKE, at, 1, 0, 0, 0, 0.01);
            if (!cracks.isEmpty()) {
                Location crack = cracks.get(random.nextInt(cracks.size())).point(random.nextDouble()).add(0, 0.1, 0);
                world.spawnParticle(Particle.SMOKE, crack, 1, 0.05, 0.05, 0.05, 0.01);
            }
            if (a % 8 == 0) {
                world.playSound(at, Sound.BLOCK_LAVA_POP, 0.4f, 0.8f);
            }
        }

        if (a == AFTER - 8) {
            for (Strip crack : cracks) {
                crack.show(0.02f, 1f, 8);
            }
            for (int i = 0; i < pool.size(); i++) {
                ease(pool.get(i), poolTile(i * 45f, 0.01f), 8);
            }
            for (Slab slab : rim) {
                slab.pose(0f, 8);
            }
        }
        if (a == AFTER) {
            for (Strip crack : cracks) {
                discard(crack.display);
            }
            cracks.clear();
            removeAll(pool);
            for (Slab slab : rim) {
                discard(slab.display);
            }
            rim.clear();
        }
    }

    /** The molten pool bites whoever is still standing in it. */
    private void burn() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2 * i / 20;
            world.spawnParticle(Particle.FLAME, zone.clone().add(Math.cos(a) * POOL_RADIUS, 0.1, Math.sin(a) * POOL_RADIUS),
                    0, 0, 1, 0, random.nextDouble(0.08, 0.16));
        }
        world.spawnParticle(Particle.LAVA, zone.clone().add(0, 0.2, 0), 6, 1.5, 0.1, 1.5, 0);
        world.playSound(zone, Sound.BLOCK_LAVA_POP, 0.8f, 0.7f);
        world.playSound(zone, Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.4f);
        if (!player.isOnline()) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(zone, POOL_RADIUS, 2.5, POOL_RADIUS)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            Location at = living.getLocation();
            if (Math.hypot(at.getX() - zone.getX(), at.getZ() - zone.getZ()) > POOL_RADIUS) {
                continue;
            }
            ScriptedHits.run(() -> living.damage(burnDamage, player));
            living.setFireTicks(Math.max(living.getFireTicks(), 40));
        }
    }

    private void recolor(List<Strip> strips, Material material) {
        BlockData data = material.createBlockData();
        for (Strip strip : strips) {
            if (strip.display != null && strip.display.isValid()) {
                strip.display.setBlock(data);
                if (material == Material.BLACKSTONE) {
                    strip.display.setBrightness(null);
                }
            }
        }
    }

    /** Fissure and feet cracks flare on the smash, then drain away. */
    private void tickFissure() {
        if (tick <= SMASH || (fissure.isEmpty() && feet.isEmpty())) {
            return;
        }
        int f = tick - SMASH;
        if (f == 2) {
            for (Strip strip : fissure) {
                strip.show(0.02f, 1f, 10);
            }
            for (Strip strip : feet) {
                strip.show(0.02f, 1f, 8);
            }
        } else if (f >= 13) {
            for (Strip strip : fissure) {
                discard(strip.display);
            }
            for (Strip strip : feet) {
                discard(strip.display);
            }
            fissure.clear();
            feet.clear();
        } else if (f <= 6 && !fissure.isEmpty()) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location at = fissure.get(random.nextInt(fissure.size())).point(random.nextDouble()).add(0, 0.1, 0);
            world.spawnParticle(Particle.FLAME, at, 0, 0, 1, 0, 0.2);
            world.spawnParticle(Particle.SMOKE, at, 2, 0.1, 0.05, 0.1, 0.02);
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

    // ------------------------------------------------------------------ pieces

    /** A flat glowing strip lying along the floor from one point to another. */
    private final class Strip {
        final BlockDisplay display;
        final Location from;
        final Vector3f span;
        final float width;
        final int appear;

        Strip(Location from, Location to, float width, int appear, Material material) {
            this.from = level(from.clone().add(0, 0.02, 0));
            this.span = new Vector3f((float) (to.getX() - from.getX()), (float) (to.getY() - from.getY()),
                    (float) (to.getZ() - from.getZ()));
            this.width = width;
            this.appear = appear;
            this.display = spawn(this.from, material.createBlockData(), LIT, null, strip(span, 0.001f, 0.03f, 0f));
        }

        void show(float widthScale, float reach, int ticks) {
            ease(display, strip(span, width * widthScale, 0.03f, reach), ticks);
        }

        Location point(double t) {
            return from.clone().add(span.x * t, span.y * t, span.z * t);
        }
    }

    /** A slab of the floor torn up at the crater lip, tilted outward. */
    private final class Slab {
        final BlockDisplay display;
        final double angle;
        final double radius;
        final float tilt;

        Slab(double angle, double radius, float tilt) {
            this.angle = angle;
            this.radius = radius;
            this.tilt = tilt;
            this.display = spawn(zone, floorData, null, null, transform(0f));
        }

        void pose(float rise, int ticks) {
            ease(display, transform(rise), ticks);
        }

        private Transformation transform(float rise) {
            float w = 1.3f;
            float h = 0.3f;
            float d = 0.85f;
            Quaternionf rot = new Quaternionf()
                    .rotateY((float) Math.atan2(Math.cos(angle), Math.sin(angle)))
                    .rotateX((float) -Math.toRadians(tilt * rise));
            Vector3f center = new Vector3f((float) (Math.cos(angle) * radius), -0.45f + 0.5f * rise,
                    (float) (Math.sin(angle) * radius));
            float s = Math.max(0.001f, rise);
            Vector3f half = new Quaternionf(rot).transform(new Vector3f(w * s / 2f, h / 2f, d * s / 2f));
            return new Transformation(center.sub(half), rot, new Vector3f(w * s, h, d * s), new Quaternionf());
        }
    }

    /** A chunk of rock thrown out of the crater on a ballistic arc. */
    private final class Ejecta {
        final BlockDisplay display;
        final Location at;
        final Vector velocity;
        final boolean molten;
        final float size;
        final Quaternionf rot = new Quaternionf();
        final float spinX;
        final float spinY;
        int age;

        Ejecta(Location start, Vector velocity, BlockData data, boolean molten, float size) {
            this.at = level(start);
            this.velocity = velocity;
            this.molten = molten;
            this.size = size;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            this.spinX = (float) random.nextDouble(-0.35, 0.35);
            this.spinY = (float) random.nextDouble(-0.3, 0.3);
            this.display = spawn(at, data, molten ? LIT : null, null, centered(size, rot));
            if (display != null) {
                display.setTeleportDuration(1);
            }
        }

        /** @return false once the chunk has landed or timed out */
        boolean step() {
            age++;
            if (display == null || !display.isValid()) {
                return false;
            }
            at.add(velocity);
            velocity.setY(velocity.getY() - 0.075);
            velocity.setX(velocity.getX() * 0.98);
            velocity.setZ(velocity.getZ() * 0.98);
            if ((velocity.getY() < 0 && at.getY() <= zone.getY() + 0.15) || age > 34) {
                world.spawnParticle(Particle.BLOCK, at, 6, 0.15, 0.05, 0.15, 0.05, display.getBlock());
                world.spawnParticle(Particle.SMOKE, at, 2, 0.1, 0.05, 0.1, 0.01);
                return false;
            }
            rot.rotateXYZ(spinX, spinY, spinX * 0.5f);
            display.teleport(at);
            ease(display, centered(size, rot), 1);
            if (molten) {
                world.spawnParticle(Particle.FLAME, at, 1, 0.05, 0.05, 0.05, 0.005);
            }
            if (age % 2 == 0) {
                world.spawnParticle(Particle.SMOKE, at, 1, 0.05, 0.05, 0.05, 0.005);
            }
            return true;
        }
    }

    private static final class Chunk {
        final BlockDisplay display;
        final Vector3f offset;
        final Quaternionf base;
        final float size;

        Chunk(BlockDisplay display, Vector3f offset, Quaternionf base, float size) {
            this.display = display;
            this.offset = offset;
            this.base = base;
            this.size = size;
        }
    }

    // ------------------------------------------------------------------ geometry + displays

    private static Transformation strip(Vector3f d, float width, float thick, float reach) {
        float len = d.length();
        if (len < 1.0E-3f) {
            return tiny();
        }
        float yaw = (float) Math.atan2(d.x, d.z);
        float pitch = (float) -Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z));
        Quaternionf rot = new Quaternionf().rotateY(yaw).rotateX(pitch);
        float w = Math.max(0.001f, width);
        float l = Math.max(0.001f, (len + w * 0.6f) * reach);
        Vector3f off = new Quaternionf(rot).transform(new Vector3f(-w / 2f, 0f, -w * 0.3f));
        return new Transformation(off, rot, new Vector3f(w, thick, l), new Quaternionf());
    }

    private static Transformation poolTile(float yawDeg, float size) {
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(yawDeg));
        float s = Math.max(0.001f, size);
        Vector3f half = new Quaternionf(rot).transform(new Vector3f(s / 2f, 0f, s / 2f));
        return new Transformation(new Vector3f(0f, 0.03f, 0f).sub(half), rot, new Vector3f(s, 0.04f, s),
                new Quaternionf());
    }

    private static Transformation centered(float size, Quaternionf rot) {
        Quaternionf r = new Quaternionf(rot);
        Vector3f half = new Quaternionf(r).transform(new Vector3f(size / 2f, size / 2f, size / 2f));
        return new Transformation(half.negate(), r, new Vector3f(size, size, size), new Quaternionf());
    }

    /** A square-section rod from {@code a} to {@code b}. */
    private static Transformation beam(Vector a, Vector b, float width) {
        Vector3f d = new Vector3f((float) (b.getX() - a.getX()), (float) (b.getY() - a.getY()), (float) (b.getZ() - a.getZ()));
        float len = d.length();
        float w = Math.max(0.001f, width);
        if (len < 1.0E-3f) {
            return tiny();
        }
        d.div(len);
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), d);
        Vector3f start = new Vector3f((float) a.getX(), (float) a.getY(), (float) a.getZ());
        Vector3f off = new Quaternionf(rot).transform(new Vector3f(w / 2f, 0f, w / 2f));
        return new Transformation(start.sub(off), rot, new Vector3f(w, len, w), new Quaternionf());
    }

    private static Transformation tiny() {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f),
                new Quaternionf());
    }

    private void drawRing(Location center, double radius, int points, Particle.DustOptions dust, double phase,
                          boolean dashed) {
        for (int i = 0; i < points; i++) {
            if (dashed && i % 3 == 2) {
                continue;
            }
            double a = phase + Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0.12, Math.sin(a) * radius),
                    1, 0, 0, 0, 0, dust);
        }
    }

    private static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
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
        for (Strip strip : fissure) {
            discard(strip.display);
        }
        fissure.clear();
        for (Strip strip : feet) {
            discard(strip.display);
        }
        feet.clear();
        for (Chunk chunk : meteor) {
            discard(chunk.display);
        }
        meteor.clear();
        for (Strip crack : cracks) {
            discard(crack.display);
        }
        cracks.clear();
        removeAll(pool);
        for (Slab slab : rim) {
            discard(slab.display);
        }
        rim.clear();
        for (Ejecta chunk : ejecta) {
            discard(chunk.display);
        }
        ejecta.clear();
        discard(flare);
        flare = null;
        juggled.clear();
    }

    private static void removeAll(List<BlockDisplay> displays) {
        for (BlockDisplay display : displays) {
            discard(display);
        }
        displays.clear();
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
