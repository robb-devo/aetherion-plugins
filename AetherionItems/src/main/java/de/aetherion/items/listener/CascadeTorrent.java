package de.aetherion.items.listener;

import de.aetherion.items.combat.ScriptedHits;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cascade Shortbow (cascade_shortbow) — Cascade Bolt as a sunlit torrent.
 * The string flings a sheet of spray and a straight jet into the first target; from there the water leaps
 * target to target in pouring arcs that shed a curtain of drips, glinting gold where the light catches the crest.
 * Every landing throws a splash crown at the target's feet — petals flaring out with droplet beads on their tips
 * and a jet rising from the middle — while the target keeps dripping and each drop note falls a step lower,
 * so the chain sounds like water running down a staircase. The last pool plunges into a spray fountain.
 * Targeting, hop timing, hop count and damage match the old bolt.
 */
final class CascadeTorrent {

    static final String ITEM_ID = "cascade_shortbow";
    private static final int HOPS = 10;
    private static final int HOP_TICKS = 3;
    private static final double FIRST_RANGE = 22.0;
    private static final double FIRST_CONE = 0.35;
    private static final double FALLBACK_RANGE = 14.0;
    private static final double NEXT_RANGE = 12.0;
    private static final int CROWN_TICKS = 10;
    private static final int DRENCH_TICKS = 12;
    private static final int MAX_DRENCH = 10;
    /** Past this many live crown displays (all casters), landings fall back to particles only. */
    private static final int MAX_LIVE = 200;

    private static final Color DEEP = Color.fromRGB(25, 85, 175);
    private static final Color AQUA = Color.fromRGB(70, 180, 240);
    private static final Color FOAM = Color.fromRGB(235, 250, 255);
    private static final Color GLINT = Color.fromRGB(255, 212, 115);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private static final List<BlockDisplay> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final Player player;
    private final World world;
    private final double perHit;
    private final Runnable done;
    private final Set<UUID> hit = new HashSet<>();
    private final List<Crown> crowns = new ArrayList<>();
    private final List<Drench> drench = new ArrayList<>();
    private Location cursor;
    private LivingEntity current;
    private int hop;
    private boolean chainOver;

    /** Plugin disable: removes every splash crown still standing. */
    static void shutdown() {
        for (BlockDisplay display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    static boolean isCascade(String itemId) {
        return ITEM_ID.equalsIgnoreCase(itemId == null ? "" : itemId);
    }

    /** {@code done} runs once when the chain stops hopping (same moment the old bolt freed the caster). */
    static void cast(JavaPlugin plugin, Player player, double perHit, Runnable done) {
        new CascadeTorrent(plugin, player, perHit, done).start();
    }

    private CascadeTorrent(JavaPlugin plugin, Player player, double perHit, Runnable done) {
        this.plugin = plugin;
        this.player = player;
        this.world = player.getWorld();
        this.perHit = perHit;
        this.done = done;
        this.cursor = player.getEyeLocation().clone();
    }

    private void start() {
        current = firstInCone();
        launch();

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!chainOver) {
                    if (!player.isOnline()) {
                        endChain();
                    } else if (t % HOP_TICKS == 0) {
                        hopStep();
                    }
                }
                tickCrowns();
                tickDrench();
                t++;
                if (chainOver && crowns.isEmpty() && drench.isEmpty()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private LivingEntity firstInCone() {
        Vector aim = player.getEyeLocation().getDirection().normalize();
        LivingEntity first = null;
        double best = FIRST_RANGE * FIRST_RANGE;
        for (Entity entity : world.getNearbyEntities(player.getLocation(), FIRST_RANGE, 12, FIRST_RANGE)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity)) {
                continue;
            }
            Vector to = ((LivingEntity) entity).getEyeLocation().toVector().subtract(player.getEyeLocation().toVector());
            if (to.lengthSquared() < 0.01) {
                continue;
            }
            double dist = to.lengthSquared();
            if (to.normalize().dot(aim) < FIRST_CONE) {
                continue;
            }
            if (dist < best) {
                best = dist;
                first = (LivingEntity) entity;
            }
        }
        return first;
    }

    private LivingEntity nearest(Location from, double range) {
        LivingEntity best = null;
        double bestDist = range * range;
        for (Entity entity : world.getNearbyEntities(from, range, range, range)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity) || hit.contains(entity.getUniqueId())) {
                continue;
            }
            double d = entity.getLocation().distanceSquared(from);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private void endChain() {
        if (!chainOver) {
            chainOver = true;
            done.run();
        }
    }

    private void hopStep() {
        if (hop >= HOPS) {
            endChain();
            return;
        }
        LivingEntity target = current;
        if (target == null || !target.isValid() || target.isDead()) {
            target = nearest(cursor, FALLBACK_RANGE);
            current = target;
        }
        if (target == null) {
            if (hop == 0) {
                miss();
            } else {
                plunge(cursor);
            }
            endChain();
            return;
        }

        Location dest = target.getEyeLocation();
        hit.add(target.getUniqueId());
        LivingEntity next = nearest(dest, NEXT_RANGE);
        boolean last = next == null || hop + 1 >= HOPS;

        pour(hop == 0 ? muzzlePoint(dest) : cursor, dest, hop);
        land(target, dest, hop, last);
        LivingEntity struck = target;
        ScriptedHits.run(() -> struck.damage(perHit, player));

        cursor = dest.clone();
        hop++;
        current = next;
        if (last) {
            plunge(cursor);
            endChain();
        }
    }

    // ------------------------------------------------------------------ beats

    private Location muzzlePoint(Location toward) {
        Location eye = player.getEyeLocation();
        Vector dir = toward.toVector().subtract(eye.toVector());
        if (dir.lengthSquared() < 1.0E-4) {
            dir = eye.getDirection();
        }
        return eye.clone().add(dir.normalize().multiply(0.7)).add(0, -0.15, 0);
    }

    /** The string flicks a fan of spray off the bow as the first jet leaves. */
    private void launch() {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector right = look.getCrossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        Location tip = eye.clone().add(look.clone().multiply(0.9)).add(right.clone().multiply(0.18)).add(0, -0.18, 0);
        Particle.DustTransition sheet = new Particle.DustTransition(FOAM, AQUA, 0.9f);
        for (int i = -3; i <= 3; i++) {
            Vector d = look.clone().add(right.clone().multiply(i * 0.14)).normalize();
            for (int k = 1; k <= 3; k++) {
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, tip.clone().add(d.clone().multiply(k * 0.3)),
                        1, 0.01, 0.01, 0.01, 0, sheet);
            }
        }
        world.spawnParticle(Particle.SPLASH, tip, 10, 0.15, 0.1, 0.15, 0.1);
        world.spawnParticle(Particle.FALLING_WATER, tip, 3, 0.2, 0.05, 0.2, 0);
        world.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.7f);
        world.playSound(eye, Sound.ITEM_BUCKET_EMPTY, 0.6f, 1.3f);
        world.playSound(eye, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.4f, 1.5f);
    }

    /**
     * The stream: a straight jet on the first hop, then pouring arcs that crest and fall onto the next target,
     * shedding drips below the arc and glinting at the crest. Grows a little heavier every hop.
     */
    private void pour(Location from, Location to, int index) {
        Vector span = to.toVector().subtract(from.toVector());
        double len = span.length();
        if (len < 0.2) {
            return;
        }
        double peak = index == 0 ? 0.06 * len : 0.9 + 0.14 * len;
        float size = 1.15f + Math.min(10, index) * 0.05f;
        int steps = Math.max(6, Math.min(90, (int) (len / 0.28)));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Particle.DustOptions body = new Particle.DustOptions(AQUA, size);
        Particle.DustTransition foam = new Particle.DustTransition(FOAM, AQUA, size * 0.6f);
        Particle.DustOptions deep = new Particle.DustOptions(DEEP, size * 0.7f);
        Particle.DustOptions glint = new Particle.DustOptions(GLINT, 0.65f);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location at = from.clone().add(span.clone().multiply(t)).add(0, 4.0 * peak * t * (1.0 - t), 0);
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, body);
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.07, 0.07, 0.07, 0, foam);
            if (i % 2 == 1) {
                world.spawnParticle(Particle.DUST, at.clone().add(0, -0.08, 0), 1, 0.03, 0.03, 0.03, 0, deep);
            }
            if (i % 3 == 0) {
                world.spawnParticle(Particle.DOLPHIN, at, 1, 0.05, 0.05, 0.05, 0);
            }
            if (i % 4 == 2 && t > 0.15 && t < 0.9) {
                world.spawnParticle(Particle.FALLING_WATER, at, 1, 0.04, 0, 0.04, 0);
            }
            if (index > 0 && i % 2 == 0 && Math.abs(t - 0.5) < 0.12) {
                world.spawnParticle(Particle.DUST, at.clone().add(0, 0.1, 0), 1, 0.05, 0.03, 0.05, 0, glint);
            }
        }
        for (int i = 0; i < 3; i++) {
            double t = random.nextDouble(0.2, 0.95);
            world.spawnParticle(Particle.SPLASH,
                    from.clone().add(span.clone().multiply(t)).add(0, 4.0 * peak * t * (1.0 - t), 0), 2, 0.1, 0.1, 0.1, 0.05);
        }
        if (index > 0) {
            world.playSound(from, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.35f, 0.9f + random.nextFloat() * 0.3f);
        }
    }

    /** A landing: splash at the head, a crown at the feet, and the target left dripping. */
    private void land(LivingEntity target, Location head, int index, boolean last) {
        world.spawnParticle(Particle.SPLASH, head, 16, 0.3, 0.3, 0.3, 0.12);
        world.spawnParticle(Particle.DUST, head, 8, 0.25, 0.25, 0.25, 0, new Particle.DustOptions(FOAM, 1.1f));
        world.spawnParticle(Particle.BUBBLE_POP, head, 6, 0.3, 0.3, 0.3, 0.02);

        float drip = (float) Math.max(0.6, 1.9 - index * 0.12);
        world.playSound(head, Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, 0.9f, drip);
        world.playSound(head, Sound.ENTITY_PLAYER_SPLASH, 0.45f, (float) Math.max(0.8, 1.3 - index * 0.04));
        world.playSound(head, Sound.ITEM_TRIDENT_HIT, 0.55f, 1.35f);

        float scale = (float) (Math.max(0.8, Math.min(2.2, target.getWidth() * 1.35)) * (1.0 + index * 0.03) * (last ? 1.35 : 1.0));
        Location floor = floorUnder(target.getLocation());
        if (LIVE.size() < MAX_LIVE) {
            crowns.add(new Crown(floor, scale, last));
        } else {
            world.spawnParticle(Particle.SPLASH, floor.clone().add(0, 0.2, 0), 20, 0.5, 0.1, 0.5, 0.2);
        }
        if (drench.size() < MAX_DRENCH) {
            drench.add(new Drench(target));
        }
    }

    /** End of the staircase: the last pool bursts into a fountain of spray, drips and sunlit glints. */
    private void plunge(Location at) {
        world.spawnParticle(Particle.SPLASH, at, 40, 0.8, 0.6, 0.8, 0.25);
        world.spawnParticle(Particle.FALLING_WATER, at.clone().add(0, 1.2, 0), 18, 1.2, 0.3, 1.2, 0);
        world.spawnParticle(Particle.DUST, at, 16, 0.6, 0.5, 0.6, 0, new Particle.DustOptions(FOAM, 1.4f));
        world.spawnParticle(Particle.DUST, at.clone().add(0, 0.5, 0), 10, 0.8, 0.6, 0.8, 0,
                new Particle.DustOptions(GLINT, 0.8f));
        world.spawnParticle(Particle.DOLPHIN, at, 20, 0.7, 0.6, 0.7, 0.05);
        world.playSound(at, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.85f, 0.8f);
        world.playSound(at, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 0.6f);
        world.playSound(at, Sound.ITEM_BUCKET_EMPTY, 0.7f, 0.6f);
    }

    /** No target at all: the jet still leaves the bow and splashes where it lands. */
    private void miss() {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult ray = world.rayTraceBlocks(eye, dir, 16.0, FluidCollisionMode.NEVER, true);
        Location end = ray != null && ray.getHitPosition() != null
                ? ray.getHitPosition().toLocation(world)
                : eye.clone().add(dir.clone().multiply(16.0));
        pour(muzzlePoint(end), end, 0);
        world.spawnParticle(Particle.SPLASH, end, 18, 0.35, 0.2, 0.35, 0.15);
        world.playSound(end, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.6f, 1.1f);
        if (ray != null && ray.getHitBlock() != null && LIVE.size() < MAX_LIVE) {
            crowns.add(new Crown(floorUnder(end.clone().add(0, 0.3, 0)), 0.8f, false));
        }
    }

    private Location floorUnder(Location at) {
        RayTraceResult ground = world.rayTraceBlocks(at.clone().add(0, 0.3, 0), new Vector(0, -1, 0), 3.5,
                FluidCollisionMode.NEVER, true);
        Location floor = ground != null && ground.getHitPosition() != null
                ? ground.getHitPosition().toLocation(world)
                : at.clone();
        floor.setYaw(0);
        floor.setPitch(0);
        return floor;
    }

    private void tickCrowns() {
        Iterator<Crown> it = crowns.iterator();
        while (it.hasNext()) {
            if (!it.next().step()) {
                it.remove();
            }
        }
    }

    private void tickDrench() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Iterator<Drench> it = drench.iterator();
        while (it.hasNext()) {
            Drench wet = it.next();
            wet.age++;
            LivingEntity body = wet.body;
            if (!body.isValid() || body.isDead() || wet.age > DRENCH_TICKS) {
                it.remove();
                continue;
            }
            double w = Math.max(0.25, body.getWidth() * 0.5);
            for (int i = 0; i < 2; i++) {
                world.spawnParticle(Particle.FALLING_WATER, body.getLocation().add(
                        random.nextDouble(-w, w), body.getHeight() * random.nextDouble(0.45, 1.0), random.nextDouble(-w, w)),
                        1, 0, 0, 0, 0);
            }
            if (wet.age % 4 == 0) {
                world.spawnParticle(Particle.SPLASH, body.getLocation().add(0, 0.1, 0), 3, w, 0.05, w, 0.05);
            }
        }
    }

    /**
     * Worthington splash crown: glass petals rise and flare outward, a foam bead rides each tip and flies off
     * as the rim breaks, and a jet climbs out of the middle and drops its bead back into the pool.
     */
    private final class Crown {
        final Location base;
        final float s;
        final int count;
        final double turn;
        final List<BlockDisplay> petals = new ArrayList<>();
        final List<BlockDisplay> beads = new ArrayList<>();
        final BlockDisplay jet;
        final BlockDisplay drop;
        int age;

        Crown(Location base, float s, boolean heavy) {
            this.base = base;
            this.s = s;
            this.count = heavy ? 10 : 8;
            this.turn = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            for (int i = 0; i < count; i++) {
                petals.add(spawn(base, i % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WHITE_STAINED_GLASS));
                beads.add(spawn(base, Material.WHITE_CONCRETE));
            }
            jet = spawn(base, Material.LIGHT_BLUE_STAINED_GLASS);
            drop = spawn(base, Material.WHITE_CONCRETE);
            world.spawnParticle(Particle.SPLASH, base.clone().add(0, 0.15, 0), (int) (12 * s), 0.35 * s, 0.05, 0.35 * s, 0.15);
        }

        /** @return false once the crown has fallen back and been removed */
        boolean step() {
            age++;
            if (age > CROWN_TICKS) {
                for (int i = 0; i < beads.size(); i += 2) {
                    world.spawnParticle(Particle.SPLASH, beadAt(i, 1.0), 2, 0.05, 0.02, 0.05, 0.05);
                }
                world.spawnParticle(Particle.SPLASH, base.clone().add(0, 0.1, 0), 6, 0.3 * s, 0.02, 0.3 * s, 0.05);
                removeAll(petals);
                removeAll(beads);
                discard(jet);
                discard(drop);
                return false;
            }
            double p = age / (double) CROWN_TICKS;
            for (int i = 0; i < count; i++) {
                pose(petals.get(i), petal(i, p));
                pose(beads.get(i), cube(beadOffset(i, p), s * 0.1f));
            }
            double q = (p - 0.3) / 0.6;
            if (q > 0 && q < 1) {
                float h = (float) (s * 1.3 * Math.sin(Math.PI * q));
                float w = s * 0.13f;
                pose(jet, new Transformation(new Vector3f(-w / 2f, 0f, -w / 2f), new Quaternionf(),
                        new Vector3f(w, Math.max(0.001f, h), w), new Quaternionf()));
            } else {
                pose(jet, cube(new Vector3f(), 0.001f));
            }
            if (q > 0) {
                double y;
                if (q < 0.6) {
                    y = s * 1.3 * Math.sin(Math.PI * q);
                } else {
                    double f = q - 0.6;
                    y = s * 1.3 * Math.sin(Math.PI * 0.6) + f * s * 0.5 - f * f * s * 6.0;
                }
                pose(drop, cube(new Vector3f(0f, (float) Math.max(0.02, y), 0f), s * 0.16f));
            }
            if (age == 4) {
                for (int i = 1; i < count; i += 2) {
                    world.spawnParticle(Particle.SPLASH, beadAt(i, p), 1, 0.02, 0.02, 0.02, 0.1);
                }
            }
            return true;
        }

        private double phi(int i) {
            return turn + i * Math.PI * 2 / count;
        }

        private Transformation petal(int i, double p) {
            double phi = phi(i);
            double r = s * (0.25 + 0.7 * p);
            float len = (float) Math.max(0.001, s * 0.9 * Math.sin(Math.PI * Math.min(1.0, p * 1.15)));
            double tilt = Math.toRadians(10 + 75 * p);
            float w = (float) (s * 0.14 * (1.0 - 0.5 * p));
            float thick = 0.03f;
            Vector3f tangent = new Vector3f((float) -Math.sin(phi), 0f, (float) Math.cos(phi));
            Vector3f up = new Vector3f((float) (Math.cos(phi) * Math.sin(tilt)), (float) Math.cos(tilt),
                    (float) (Math.sin(phi) * Math.sin(tilt)));
            Vector3f normal = new Vector3f(tangent).cross(up).normalize();
            Quaternionf rot = new Quaternionf().setFromNormalized(new Matrix3f(tangent, up, normal));
            Vector3f half = new Quaternionf(rot).transform(new Vector3f(w / 2f, 0f, thick / 2f));
            Vector3f at = new Vector3f((float) (Math.cos(phi) * r), 0.02f, (float) (Math.sin(phi) * r)).sub(half);
            return new Transformation(at, rot, new Vector3f(w, len, thick), new Quaternionf());
        }

        private Vector3f tip(int i, double p) {
            double phi = phi(i);
            double r = s * (0.25 + 0.7 * p);
            double len = s * 0.9 * Math.sin(Math.PI * Math.min(1.0, p * 1.15));
            double tilt = Math.toRadians(10 + 75 * p);
            return new Vector3f(
                    (float) (Math.cos(phi) * (r + Math.sin(tilt) * len)),
                    (float) (0.02 + Math.cos(tilt) * len),
                    (float) (Math.sin(phi) * (r + Math.sin(tilt) * len)));
        }

        private Vector3f beadOffset(int i, double p) {
            if (p < 0.6) {
                return tip(i, p);
            }
            double f = (p - 0.6) / 0.4;
            double phi = phi(i);
            Vector3f from = tip(i, 0.6);
            return from.add(
                    (float) (Math.cos(phi) * f * s * 0.9),
                    (float) Math.max(-from.y + 0.02, f * s * 0.5 - f * f * s * 1.6),
                    (float) (Math.sin(phi) * f * s * 0.9));
        }

        private Location beadAt(int i, double p) {
            Vector3f off = beadOffset(i, p);
            return base.clone().add(off.x, off.y, off.z);
        }

        private Transformation cube(Vector3f center, float size) {
            float b = Math.max(0.001f, size);
            return new Transformation(new Vector3f(center).sub(b / 2f, b / 2f, b / 2f), new Quaternionf(),
                    new Vector3f(b, b, b), new Quaternionf());
        }
    }

    private static void pose(BlockDisplay display, Transformation transformation) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTransformation(transformation);
    }

    private static BlockDisplay spawn(Location at, Material material) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setPersistent(false);
                spawned.setBrightness(LIT);
                spawned.setInterpolationDuration(1);
                spawned.setTransformation(new Transformation(
                        new Vector3f(), new Quaternionf(), new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
            });
            LIVE.add(display);
            return display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void removeAll(List<BlockDisplay> displays) {
        for (BlockDisplay display : displays) {
            discard(display);
        }
        displays.clear();
    }

    private static void discard(BlockDisplay display) {
        if (display == null) {
            return;
        }
        LIVE.remove(display);
        if (display.isValid()) {
            display.remove();
        }
    }

    private static final class Drench {
        final LivingEntity body;
        int age;

        Drench(LivingEntity body) {
            this.body = body;
        }
    }

    /**
     * Normal Cascade shots (not the ulti): a spray flick at the string, a thin water wake that sheds drips,
     * and a splash where the arrow lands. Driven by {@link ShortbowListener}.
     */
    static final class Shots {

        private static final int TRAIL_MAX_AGE = 40;

        private final Map<UUID, Wake> wakes = new HashMap<>();

        void muzzle(Player player) {
            World world = player.getWorld();
            Location eye = player.getEyeLocation();
            Vector look = eye.getDirection().normalize();
            Location tip = eye.clone().add(look.clone().multiply(0.9)).add(0, -0.15, 0);
            world.spawnParticle(Particle.SPLASH, tip, 4, 0.08, 0.05, 0.08, 0.05);
            world.spawnParticle(Particle.DUST, tip.clone().add(look.clone().multiply(0.3)), 2, 0.03, 0.03, 0.03, 0,
                    new Particle.DustOptions(AQUA, 0.9f));
            world.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 0.85f, 1.45f);
            world.playSound(eye, Sound.ENTITY_FISHING_BOBBER_THROW, 0.35f, 1.7f);
        }

        void track(Arrow arrow) {
            wakes.put(arrow.getUniqueId(), new Wake(arrow, arrow.getLocation().clone()));
        }

        boolean isTracked(Arrow arrow) {
            return arrow != null && wakes.containsKey(arrow.getUniqueId());
        }

        void tick() {
            Iterator<Wake> it = wakes.values().iterator();
            while (it.hasNext()) {
                Wake wake = it.next();
                Arrow arrow = wake.arrow;
                wake.age++;
                if (!arrow.isValid() || arrow.isInBlock() || wake.age > TRAIL_MAX_AGE
                        || arrow.getWorld() != wake.last.getWorld()) {
                    it.remove();
                    continue;
                }
                World world = arrow.getWorld();
                Location now = arrow.getLocation();
                Vector seg = now.toVector().subtract(wake.last.toVector());
                int points = Math.max(1, Math.min(5, (int) Math.ceil(seg.length() / 0.7)));
                Particle.DustTransition water = new Particle.DustTransition(AQUA, DEEP, 0.75f);
                for (int i = 1; i <= points; i++) {
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                            wake.last.clone().add(seg.clone().multiply(i / (double) points)), 1, 0.02, 0.02, 0.02, 0, water);
                }
                if (wake.age % 3 == 0) {
                    world.spawnParticle(Particle.FALLING_WATER, now, 1, 0.03, 0, 0.03, 0);
                }
                wake.last = now;
            }
        }

        void impactEntity(Arrow arrow) {
            if (!isTracked(arrow)) {
                return;
            }
            World world = arrow.getWorld();
            Location at = arrow.getLocation();
            world.spawnParticle(Particle.SPLASH, at, 10, 0.2, 0.2, 0.2, 0.1);
            world.spawnParticle(Particle.DUST, at, 4, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(FOAM, 0.9f));
            world.playSound(at, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.5f, 1.4f);
        }

        void impactBlock(Arrow arrow) {
            if (wakes.remove(arrow.getUniqueId()) == null) {
                return;
            }
            World world = arrow.getWorld();
            Location at = arrow.getLocation();
            world.spawnParticle(Particle.SPLASH, at, 6, 0.12, 0.05, 0.12, 0.08);
            world.playSound(at, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.3f, 1.6f);
        }

        private static final class Wake {
            final Arrow arrow;
            Location last;
            int age;

            Wake(Arrow arrow, Location last) {
                this.arrow = arrow;
                this.last = last;
            }
        }
    }
}
