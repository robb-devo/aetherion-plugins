package de.aetherion.items.listener;

import de.aetherion.items.combat.ScriptedHits;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Vibration;
import org.bukkit.World;
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

/**
 * Resonance Scythe (resonance_scythe) — Resonance Wave.
 * A struck tuning ring at the blade, then one committed wavefront: a ring of glass tiles sized to the real
 * hit width, a trailing echo ring, a standing-wave carrier whose nodes fall on every step, ground ripples,
 * a rising bell arpeggio, struck targets that shiver and send a sculk vibration back to the caster,
 * and an overtone burst where the line ends. Damage, path, hit box and knockback match the old line.
 */
public final class ResonanceScytheWave {

    private static final int TUNE_TICKS = 3;
    private static final int STEPS = 14;
    private static final double STEP = 1.15;
    private static final double HIT_H = 1.85;
    private static final double HIT_V = 1.6;
    private static final int FRONT = 12;
    private static final int ECHO = 8;
    private static final int BURST_TICKS = 5;
    private static final int SHIVER_TICKS = 8;
    private static final int MAX_SHIVERS = 6;
    private static final double WAVELENGTH = STEP * 2.0;
    private static final Color DEEP = Color.fromRGB(18, 92, 112);
    private static final Color TEAL = Color.fromRGB(40, 205, 215);
    private static final Color PALE = Color.fromRGB(200, 250, 255);
    /** F# major, one note per three steps: the line climbs a chord as it travels. */
    private static final float[] ARPEGGIO = {0.5f, 0.63f, 0.75f, 1.0f, 1.26f};

    private static final List<BlockDisplay> LIVE = new CopyOnWriteArrayList<>();

    private final JavaPlugin plugin;
    private final Player player;
    private final World world;
    private final Location origin;
    private final Vector dir;
    private final Vector u;
    private final Vector v;
    private final double damage;
    private final Set<UUID> hit = new HashSet<>();
    private final List<BlockDisplay> front = new ArrayList<>();
    private final List<BlockDisplay> echo = new ArrayList<>();
    private final List<Shiver> shivers = new ArrayList<>();
    private double spin;
    private double frontRadius = 0.3;

    /** Plugin disable: removes every wavefront tile still travelling. */
    public static void shutdown() {
        for (BlockDisplay display : LIVE) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        LIVE.clear();
    }

    static void cast(JavaPlugin plugin, Player player, double weaponDamage) {
        new ResonanceScytheWave(plugin, player, weaponDamage).start();
    }

    private ResonanceScytheWave(JavaPlugin plugin, Player player, double weaponDamage) {
        this.plugin = plugin;
        this.player = player;
        this.world = player.getWorld();
        Location eye = player.getEyeLocation();
        this.dir = eye.getDirection().normalize();
        this.origin = eye.clone();
        this.origin.setYaw(0);
        this.origin.setPitch(0);
        this.damage = Math.max(28.0, weaponDamage * 0.72 + 12.0);
        Vector side = dir.getCrossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1.0E-4) {
            side = new Vector(1, 0, 0);
        }
        this.u = side.normalize();
        this.v = u.getCrossProduct(dir).normalize();
    }

    private void start() {
        Location blade = origin.clone().add(dir.clone().multiply(1.0));
        for (int i = 0; i < FRONT; i++) {
            front.add(tile(blade, i % 2 == 0 ? Material.CYAN_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS));
        }
        for (int i = 0; i < ECHO; i++) {
            echo.add(tile(blade, Material.WHITE_STAINED_GLASS));
        }
        world.playSound(blade, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.45f, 1.9f);
        world.playSound(blade, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 0.75f);

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!player.isOnline() || player.getWorld() != world) {
                    finish();
                    return;
                }
                if (t < TUNE_TICKS) {
                    tune(t);
                } else if (t == TUNE_TICKS) {
                    release();
                    travel(0);
                } else if (t < TUNE_TICKS + STEPS) {
                    travel(t - TUNE_TICKS);
                } else if (t == TUNE_TICKS + STEPS) {
                    overtone();
                }
                tickShivers();
                t++;
                if (t > TUNE_TICKS + STEPS + BURST_TICKS && shivers.isEmpty()) {
                    finish();
                }
            }

            private void finish() {
                removeAll(front);
                removeAll(echo);
                shivers.clear();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Two counter-turning rings close on the blade tip, like a fork being struck. */
    private void tune(int t) {
        Location center = origin.clone().add(dir.clone().multiply(1.0));
        double k = (t + 1) / (double) TUNE_TICKS;
        double r = 1.3 - 1.0 * k;
        Particle.DustTransition outer = new Particle.DustTransition(PALE, TEAL, 0.8f);
        Particle.DustTransition inner = new Particle.DustTransition(TEAL, DEEP, 0.7f);
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2 * i / 20 + t * 0.4;
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, ring(center, a, r), 1, 0, 0, 0, 0, outer);
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, ring(center, -a, r * 0.6), 1, 0, 0, 0, 0, inner);
        }
        world.spawnParticle(Particle.SCULK_CHARGE_POP, center, 2, 0.08, 0.08, 0.08, 0.01);
    }

    private void release() {
        Location muzzle = origin.clone().add(dir.clone().multiply(1.1));
        world.playSound(muzzle, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.4f);
        world.playSound(muzzle, Sound.BLOCK_BELL_USE, 0.75f, 0.6f);
        world.playSound(muzzle, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 0.7f);
        world.spawnParticle(Particle.SONIC_BOOM, muzzle, 1, 0, 0, 0, 0);
        Particle.DustOptions shock = new Particle.DustOptions(PALE, 1.1f);
        for (int i = 0; i < 36; i++) {
            world.spawnParticle(Particle.DUST, ring(muzzle, Math.PI * 2 * i / 36, 1.6), 1, 0, 0, 0, 0, shock);
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text("§3✦ Resonance Wave"));
    }

    private void travel(int step) {
        Location head = headAt(step);
        Location prev = step == 0 ? origin.clone().add(dir.clone().multiply(0.6)) : headAt(step - 1);

        double grow = Math.min(1.0, (step + 1) / 4.0);
        grow = grow * grow * (3.0 - 2.0 * grow);
        frontRadius = 0.7 + (HIT_H - 0.7) * grow + 0.12 * Math.sin(step * 2.6);
        spin += 0.18;
        poseRing(front, head, frontRadius, spin, 0.78f, 0.16f, 0.04f, 1);
        if (step >= 2) {
            double echoRadius = frontRadius * 0.55 + 0.08 * Math.sin(step * 2.6 + Math.PI);
            poseRing(echo, headAt(step - 2), echoRadius, -spin, 0.7f, 0.12f, 0.03f, 1);
        }

        carrier(prev, head);
        if (step % 3 == 1) {
            groundRipple(head);
        }
        if (step % 3 == 0) {
            float note = ARPEGGIO[Math.min(ARPEGGIO.length - 1, step / 3)];
            world.playSound(head, Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, note);
            world.playSound(head, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, note);
        }

        for (Entity entity : world.getNearbyEntities(head, HIT_H, HIT_V, HIT_H)) {
            if (!TestPrototypeAbilities.isCombatTarget(entity) || !hit.add(entity.getUniqueId())) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            ScriptedHits.run(() -> living.damage(damage, player));
            living.setVelocity(dir.clone().multiply(0.55).setY(0.28));
            struck(living);
        }
    }

    /**
     * Two mirrored sine strands in the side plane: they cross at a node on every step head and
     * bulge between, so the line reads as a standing wave rather than a helix or a beam.
     */
    private void carrier(Location from, Location to) {
        Vector span = to.toVector().subtract(from.toVector());
        double len = span.length();
        if (len < 0.05) {
            return;
        }
        Vector along = span.clone().multiply(1.0 / len);
        double startX = from.toVector().subtract(origin.toVector()).dot(dir);
        Particle.DustOptions a = new Particle.DustOptions(TEAL, 0.7f);
        Particle.DustOptions b = new Particle.DustOptions(PALE, 0.6f);
        for (double s = 0.2; s <= len + 1.0E-6; s += 0.2) {
            double x = startX + s;
            double amp = 0.42 * Math.sin(Math.PI * 2 * x / WAVELENGTH);
            Location at = from.clone().add(along.clone().multiply(s));
            world.spawnParticle(Particle.DUST, at.clone().add(u.clone().multiply(amp)), 1, 0, 0, 0, 0, a);
            world.spawnParticle(Particle.DUST, at.clone().add(u.clone().multiply(-amp)), 1, 0, 0, 0, 0, b);
        }
        world.spawnParticle(Particle.DUST, to, 1, 0, 0, 0, 0, new Particle.DustOptions(PALE, 1.1f));
    }

    /** Pond ripple at the exact hit width wherever the wave passes low over ground. */
    private void groundRipple(Location head) {
        RayTraceResult ground = world.rayTraceBlocks(head, new Vector(0, -1, 0), 3.5, FluidCollisionMode.NEVER, true);
        if (ground == null || ground.getHitPosition() == null) {
            return;
        }
        Location floor = ground.getHitPosition().toLocation(world).add(0, 0.08, 0);
        Particle.DustTransition outer = new Particle.DustTransition(TEAL, DEEP, 1.0f);
        Particle.DustOptions inner = new Particle.DustOptions(PALE, 0.8f);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                    floor.clone().add(Math.cos(a) * HIT_H, 0, Math.sin(a) * HIT_H), 1, 0, 0, 0, 0, outer);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.DUST, floor.clone().add(Math.cos(a), 0, Math.sin(a)), 1, 0, 0, 0, 0, inner);
            }
        }
    }

    /** The target rings like struck glass, and the echo travels back to the caster as a vibration. */
    private void struck(LivingEntity living) {
        Location chest = living.getLocation().add(0, living.getHeight() * 0.55, 0);
        world.playSound(chest, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9f, 0.6f);
        world.playSound(chest, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.6f, 1.5f);
        world.spawnParticle(Particle.SCULK_CHARGE_POP, chest, 8, 0.3, 0.35, 0.3, 0.02);
        world.spawnParticle(Particle.CRIT, chest, 6, 0.25, 0.3, 0.25, 0.02);
        try {
            Location home = player.getLocation().add(0, 1.0, 0);
            int arrival = (int) Math.max(6, Math.min(20, chest.distance(home) * 1.2));
            world.spawnParticle(Particle.VIBRATION, chest, 1, 0, 0, 0, 0,
                    new Vibration(new Vibration.Destination.BlockDestination(home), arrival));
        } catch (Throwable ignored) {
        }
        if (shivers.size() < MAX_SHIVERS) {
            shivers.add(new Shiver(living));
        }
    }

    private void tickShivers() {
        Iterator<Shiver> it = shivers.iterator();
        while (it.hasNext()) {
            Shiver shiver = it.next();
            shiver.age++;
            LivingEntity body = shiver.body;
            if (!body.isValid() || body.isDead() || shiver.age > SHIVER_TICKS) {
                it.remove();
                continue;
            }
            Location feet = body.getLocation();
            double width = Math.max(0.45, body.getWidth() * 0.75);
            double r = width * (shiver.age % 2 == 0 ? 0.8 : 1.05) * (1.0 - shiver.age / (double) (SHIVER_TICKS * 2));
            Particle.DustOptions dust = new Particle.DustOptions(shiver.age % 2 == 0 ? TEAL : PALE, 0.6f);
            for (double h : new double[] {0.3, 0.75}) {
                double y = body.getHeight() * h;
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2 * i / 10 + shiver.age * 0.5;
                    world.spawnParticle(Particle.DUST, feet.clone().add(Math.cos(a) * r, y, Math.sin(a) * r),
                            1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    /** End of the line: the front flares wide and thin, the echo collapses, and the chord rings out. */
    private void overtone() {
        Location end = headAt(STEPS - 1);
        poseRing(front, end, 3.2, spin + 0.4, 0.35f, 0.02f, 0.01f, BURST_TICKS - 1);
        poseRing(echo, headAt(STEPS - 3), 0.05, -spin, 0.2f, 0.01f, 0.01f, 3);
        Particle.DustTransition flare = new Particle.DustTransition(PALE, TEAL, 1.2f);
        for (int i = 0; i < 32; i++) {
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, ring(end, Math.PI * 2 * i / 32, 2.6), 1, 0, 0, 0, 0, flare);
        }
        world.spawnParticle(Particle.SCULK_CHARGE_POP, end, 10, 0.6, 0.6, 0.6, 0.02);
        world.playSound(end, Sound.BLOCK_BELL_RESONATE, 0.55f, 1.3f);
        world.playSound(end, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.45f, 1.6f);
    }

    private Location headAt(int step) {
        return origin.clone().add(dir.clone().multiply(STEP * (step + 1)));
    }

    private Location ring(Location center, double angle, double radius) {
        return center.clone()
                .add(u.clone().multiply(Math.cos(angle) * radius))
                .add(v.clone().multiply(Math.sin(angle) * radius));
    }

    /** Moves the ring to {@code center} and lays its tiles around the travel axis; gaps keep it segmented. */
    private void poseRing(List<BlockDisplay> tiles, Location center, double radius, double turn,
                          float fill, float width, float thick, int ticks) {
        int n = tiles.size();
        if (n == 0) {
            return;
        }
        float length = (float) Math.max(0.01, Math.PI * 2 * radius / n * fill);
        for (int i = 0; i < n; i++) {
            BlockDisplay tile = tiles.get(i);
            if (tile == null || !tile.isValid()) {
                continue;
            }
            tile.teleport(center);
            double a = turn + Math.PI * 2 * i / n;
            Vector radial = u.clone().multiply(Math.cos(a)).add(v.clone().multiply(Math.sin(a)));
            Vector tangent = u.clone().multiply(-Math.sin(a)).add(v.clone().multiply(Math.cos(a)));
            Vector3f x = new Vector3f((float) tangent.getX(), (float) tangent.getY(), (float) tangent.getZ());
            Vector3f y = new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ());
            Vector3f z = new Vector3f(x).cross(y);
            Quaternionf rot = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
            Vector3f half = new Quaternionf(rot).transform(new Vector3f(length / 2f, thick / 2f, width / 2f));
            Vector3f at = new Vector3f(
                    (float) (radial.getX() * radius),
                    (float) (radial.getY() * radius),
                    (float) (radial.getZ() * radius));
            tile.setInterpolationDelay(0);
            tile.setInterpolationDuration(ticks);
            tile.setTransformation(new Transformation(at.sub(half), rot, new Vector3f(length, thick, width), new Quaternionf()));
        }
    }

    private static BlockDisplay tile(Location at, Material material) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        try {
            BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
                spawned.setBlock(material.createBlockData());
                spawned.setPersistent(false);
                spawned.setBrightness(new Display.Brightness(15, 15));
                spawned.setTeleportDuration(1);
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
            if (display != null) {
                LIVE.remove(display);
                if (display.isValid()) {
                    display.remove();
                }
            }
        }
        displays.clear();
    }

    private static final class Shiver {
        final LivingEntity body;
        int age;

        Shiver(LivingEntity body) {
            this.body = body;
        }
    }
}
