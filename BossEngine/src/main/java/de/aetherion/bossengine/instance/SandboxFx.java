package de.aetherion.bossengine.instance;

import de.aetherion.core.AetherKeys;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Shared over-the-top FX for sandbox / test-arena bosses.
 */
final class SandboxFx {

    private SandboxFx() {
    }

    static double hpFrac(LivingEntity body) {
        if (body == null || body.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) == null) {
            return 1.0;
        }
        double max = body.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        return max <= 0 ? 1.0 : body.getHealth() / max;
    }

    static boolean enraged(LivingEntity body) {
        return hpFrac(body) < 0.40;
    }

    static boolean desperate(LivingEntity body) {
        return hpFrac(body) < 0.20;
    }

    static void later(BossInstance instance, long delay, Runnable run) {
        Bukkit.getScheduler().runTaskLater(instance.getPlugin(), () -> {
            if (instance.isAlive()) {
                run.run();
            }
        }, delay);
    }

    static void ring(World world, Location center, Particle particle, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            Location p = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            world.spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }

    static void dustRing(World world, Location center, Color color, double radius, int points, float size) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            Location p = center.clone().add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
        }
    }

    static void beam(World world, Location from, Location to, Particle particle) {
        Vector delta = to.toVector().subtract(from.toVector());
        double len = delta.length();
        if (len < 0.15) {
            return;
        }
        Vector step = delta.normalize().multiply(0.45);
        Location c = from.clone();
        int n = Math.min(48, (int) (len / 0.45));
        for (int i = 0; i < n; i++) {
            c.add(step);
            world.spawnParticle(particle, c, 1, 0, 0, 0, 0);
        }
    }

    static void dustBeam(World world, Location from, Location to, Color color, float size) {
        Vector delta = to.toVector().subtract(from.toVector());
        double len = delta.length();
        if (len < 0.15) {
            return;
        }
        Vector step = delta.normalize().multiply(0.4);
        Location c = from.clone();
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int n = Math.min(60, (int) (len / 0.4));
        for (int i = 0; i < n; i++) {
            c.add(step);
            world.spawnParticle(Particle.DUST, c, 1, 0, 0, 0, 0, dust);
        }
    }

    static void expandingRings(
            BossInstance instance,
            World world,
            Location at,
            LivingEntity source,
            int waves,
            double stepR,
            double damage,
            Particle particle
    ) {
        for (int wave = 1; wave <= waves; wave++) {
            int w = wave;
            later(instance, w * 4L, () -> {
                double r = w * stepR;
                ring(world, at.clone().add(0, 0.15, 0), particle, r, 42);
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.65f, 0.5f + w * 0.08f);
                for (Player player : players(at, r + 0.9)) {
                    if (player.getLocation().distance(at) < r - 1.5) {
                        continue;
                    }
                    player.damage(damage + w * 2.5, source);
                    Vector away = player.getLocation().toVector().subtract(at.toVector());
                    if (away.lengthSquared() > 0.05) {
                        player.setVelocity(away.normalize().multiply(0.75).setY(0.35));
                    }
                }
            });
        }
    }

    static void markedSlam(
            BossInstance instance,
            World world,
            Location mark,
            LivingEntity source,
            double radius,
            double damage,
            long telegraphTicks,
            Particle telegraph,
            Consumer<Location> onImpact
    ) {
        for (int i = 0; i < 20; i++) {
            double a = (Math.PI * 2 * i) / 20.0;
            world.spawnParticle(telegraph, mark.clone().add(Math.cos(a) * radius, 0.08, Math.sin(a) * radius), 1, 0, 0, 0, 0);
        }
        world.playSound(mark, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.6f);
        later(instance, telegraphTicks, () -> {
            world.spawnParticle(Particle.EXPLOSION, mark.clone().add(0, 0.4, 0), 2, 0.3, 0.2, 0.3, 0);
            world.playSound(mark, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.75f);
            if (onImpact != null) {
                onImpact.accept(mark);
            }
            for (Player player : players(mark, radius)) {
                player.damage(damage, source);
                player.setVelocity(new Vector(0, 0.85, 0));
            }
        });
    }

    static void visualDebris(BossInstance instance, List<Entity> props, World world, Location at, Material mat, int count) {
        for (int i = 0; i < count; i++) {
            FallingBlock fb = world.spawnFallingBlock(at.clone().add(0, 1.0, 0), mat.createBlockData());
            fb.setDropItem(false);
            fb.setHurtEntities(false);
            try {
                fb.setCancelDrop(true);
            } catch (Throwable ignored) {
            }
            fb.setPersistent(false);
            fb.getPersistentDataContainer().set(
                    AetherKeys.namespaced("bossengine", "sandbox_debris"),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            fb.setVelocity(new Vector(
                    ThreadLocalRandom.current().nextDouble(-0.95, 0.95),
                    ThreadLocalRandom.current().nextDouble(0.35, 1.15),
                    ThreadLocalRandom.current().nextDouble(-0.95, 0.95)
            ));
            props.add(fb);
            later(instance, 28L + ThreadLocalRandom.current().nextInt(16), () -> {
                if (fb.isValid()) {
                    fb.remove();
                }
            });
        }
    }

    static void pullIn(Location center, List<Player> players, double strength) {
        for (Player player : players) {
            Vector to = center.toVector().subtract(player.getLocation().toVector());
            if (to.lengthSquared() > 0.2) {
                player.setVelocity(player.getVelocity().multiply(0.35).add(to.normalize().multiply(strength)));
            }
        }
    }

    static void flingOut(Location center, List<Player> players, double strength, double y) {
        for (Player player : players) {
            Vector away = player.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() < 0.1) {
                away = new Vector(ThreadLocalRandom.current().nextDouble(-1, 1), 0,
                        ThreadLocalRandom.current().nextDouble(-1, 1));
            }
            player.setVelocity(away.normalize().multiply(strength).setY(y));
        }
    }

    static List<Player> players(Location at, double radius) {
        List<Player> list = new java.util.ArrayList<>();
        World world = at.getWorld();
        if (world == null) {
            return list;
        }
        double r2 = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(at) <= r2) {
                list.add(player);
            }
        }
        return list;
    }

    static Player nearest(Location at, double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player player : players(at, range)) {
            double d = player.getLocation().distanceSquared(at);
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }
}
