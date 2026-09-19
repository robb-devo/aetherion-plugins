package de.aetherion.fishing;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decorative lure "fish" as ItemDisplays — real Cod/Salmon with AI off
 * do not reliably move on Paper (velocity/teleport get corrected).
 */
final class LureSchool {

    enum Swim {
        WANDER,
        APPROACH,
        NIBBLE
    }

    private static final float SCALE = 0.62f;

    private final NamespacedKey key;
    private final List<Lure> lures = new ArrayList<>();
    private int age;

    LureSchool(NamespacedKey key) {
        this.key = key;
    }

    boolean isEmpty() {
        return lures.isEmpty();
    }

    void spawn(Location hook, int count) {
        if (!lures.isEmpty()) {
            return;
        }
        if (hook == null || hook.getWorld() == null) {
            return;
        }
        World world = hook.getWorld();
        int n = Math.max(1, Math.min(3, count));
        for (int i = 0; i < n; i++) {
            Location at = waterNear(hook, i);
            if (at == null) {
                continue;
            }
            ItemStack head = LureHead.random();
            ItemDisplay display = world.spawn(at, ItemDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.setGravity(false);
                spawned.setSilent(true);
                spawned.setItemStack(head);
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                spawned.setBillboard(Display.Billboard.FIXED);
                spawned.setInterpolationDuration(3);
                spawned.setTeleportDuration(3);
                spawned.setViewRange(0.7f);
                spawned.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
                applyFacing(spawned, ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0d);
            });
            lures.add(new Lure(display.getUniqueId(), i * 2.1d, at, hook));
        }
    }

    void chooseBiter(Location hook) {
        Lure current = biter();
        if (current != null && alive(hook, current) != null) {
            return;
        }
        if (current != null) {
            current.biter = false;
        }
        Lure best = null;
        double bestDist = Double.MAX_VALUE;
        for (Lure lure : lures) {
            ItemDisplay fish = alive(hook, lure);
            if (fish == null || hook == null) {
                continue;
            }
            double dist = fish.getLocation().distanceSquared(hook);
            if (dist < bestDist) {
                best = lure;
                bestDist = dist;
            }
        }
        if (best != null) {
            best.biter = true;
        }
    }

    boolean tick(Location hook, Swim swim) {
        age++;
        if (hook == null || hook.getWorld() == null) {
            clear();
            return false;
        }
        boolean arrived = false;
        Iterator<Lure> it = lures.iterator();
        while (it.hasNext()) {
            Lure lure = it.next();
            ItemDisplay fish = alive(hook, lure);
            if (fish == null) {
                it.remove();
                continue;
            }
            if (swim == Swim.APPROACH && lure.biter) {
                arrived |= swimToBite(fish, hook, lure);
            } else {
                wander(fish, hook, lure, swim == Swim.NIBBLE && lure.biter);
            }
        }
        return arrived;
    }

    void scatter(Location from) {
        for (Lure lure : lures) {
            if (from == null || from.getWorld() == null) {
                continue;
            }
            Entity entity = from.getWorld().getEntity(lure.id);
            if (!(entity instanceof ItemDisplay fish) || !fish.isValid()) {
                continue;
            }
            Vector away = fish.getLocation().toVector().subtract(from.toVector());
            if (away.lengthSquared() < 0.01d) {
                away = new Vector(ThreadLocalRandom.current().nextGaussian(), 0.1d, ThreadLocalRandom.current().nextGaussian());
            }
            moveBy(fish, away.normalize().multiply(0.28d).setY(0.08d), lure);
        }
    }

    void clear() {
        for (Lure lure : List.copyOf(lures)) {
            remove(lure.id);
        }
        lures.clear();
    }

    private boolean swimToBite(ItemDisplay fish, Location hook, Lure lure) {
        Location mouth = hook.clone().add(0, -0.12d, 0);
        Location loc = fish.getLocation();
        Vector delta = mouth.toVector().subtract(loc.toVector());
        double dist = delta.length();
        if (dist < 0.38d) {
            moveTo(fish, mouth, lure, Math.atan2(delta.getZ(), delta.getX()));
            return true;
        }
        if (dist > 14.0d) {
            Location closer = waterNear(hook, 0);
            if (closer != null) {
                moveTo(fish, closer, lure, Math.atan2(delta.getZ(), delta.getX()));
            }
            return false;
        }
        double step = Math.min(0.22d, Math.max(0.10d, dist * 0.10d));
        Vector vel = delta.normalize().multiply(step);
        moveBy(fish, vel, lure);
        return false;
    }

    private void wander(ItemDisplay fish, Location hook, Lure lure, boolean nibble) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (lure.dartLeft > 0) {
            lure.dartLeft--;
        } else if (age >= lure.nextDart) {
            lure.dartLeft = nibble ? 4 + rng.nextInt(5) : 6 + rng.nextInt(10);
            lure.nextDart = age + (nibble ? 16 + rng.nextInt(20) : 14 + rng.nextInt(28));
            lure.heading += (rng.nextDouble() - 0.5d) * (nibble ? 0.8d : 1.8d);
            lure.speed = nibble ? 0.045d : 0.06d + rng.nextDouble() * 0.05d;
            if (!nibble && rng.nextInt(6) == 0) {
                fish.getWorld().playSound(fish.getLocation(), Sound.ENTITY_FISH_SWIM, 0.18f, 1.15f + rng.nextFloat() * 0.3f);
            }
        }

        Location loc = fish.getLocation();
        Vector toHook = hook.toVector().subtract(loc.toVector());
        double dist = toHook.length();
        double minR = nibble ? 0.28d : 1.1d;
        double maxR = nibble ? 0.70d : 4.6d;
        if (dist > maxR) {
            lure.heading = lerpAngle(lure.heading, Math.atan2(toHook.getZ(), toHook.getX()), nibble ? 0.28d : 0.14d);
        } else if (dist < minR) {
            lure.heading = lerpAngle(lure.heading, Math.atan2(-toHook.getZ(), -toHook.getX()), 0.22d);
        } else {
            lure.heading += lure.turn * (nibble ? 0.10d : 0.055d)
                    + Math.sin(age * 0.06d + lure.phase) * 0.04d;
        }

        double cruise = nibble ? 0.035d : lure.cruise;
        if (lure.dartLeft <= 0) {
            lure.speed += (cruise - lure.speed) * 0.12d;
        }

        double yTarget = hook.getY() + (nibble ? -0.18d : -0.32d)
                + Math.sin(age * 0.10d + lure.phase) * (nibble ? 0.04d : 0.18d);
        double vy = Math.max(-0.06d, Math.min(0.06d, (yTarget - loc.getY()) * 0.18d));
        Vector step = new Vector(
                Math.cos(lure.heading) * lure.speed,
                vy,
                Math.sin(lure.heading) * lure.speed
        );
        moveBy(fish, step, lure);
    }

    private void moveBy(ItemDisplay fish, Vector step, Lure lure) {
        if (step == null || step.lengthSquared() < 1.0e-8d) {
            return;
        }
        Location next = fish.getLocation().clone().add(step);
        if (!next.getBlock().isLiquid()) {
            Location flat = fish.getLocation().clone().add(step.getX(), 0.0d, step.getZ());
            if (flat.getBlock().isLiquid()) {
                next = flat;
            } else {
                Location sink = fish.getLocation().clone().add(0.0d, -0.12d, 0.0d);
                if (sink.getBlock().isLiquid()) {
                    next = sink;
                } else {
                    // Still advance heading visually even if stuck in a pocket.
                    next = fish.getLocation().clone().add(step.getX() * 0.35d, 0.0d, step.getZ() * 0.35d);
                }
            }
        }
        double heading = Math.atan2(step.getZ(), step.getX());
        if (step.lengthSquared() > 0.0004d) {
            lure.heading = heading;
            lure.speed = Math.hypot(step.getX(), step.getZ());
        }
        moveTo(fish, next, lure, lure.heading);
    }

    private void moveTo(ItemDisplay fish, Location to, Lure lure, double heading) {
        if (to == null || to.getWorld() == null) {
            return;
        }
        Location dest = to.clone();
        dest.setYaw((float) Math.toDegrees(-heading));
        dest.setPitch(0f);
        fish.setInterpolationDelay(0);
        fish.setTeleportDuration(3);
        fish.teleport(dest);
        applyFacing(fish, heading);
        lure.heading = heading;
    }

    private static void applyFacing(ItemDisplay fish, double heading) {
        // Player-head fish art faces +Z; yaw so the fish points along swim heading.
        float yaw = (float) heading + (float) (Math.PI * 0.5d);
        fish.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(yaw, 0f, 1f, 0f),
                new Vector3f(SCALE, SCALE, SCALE),
                new AxisAngle4f(0f, 0f, 1f, 0f)
        ));
    }

    private Lure biter() {
        for (Lure lure : lures) {
            if (lure.biter) {
                return lure;
            }
        }
        return null;
    }

    private static ItemDisplay alive(Location hook, Lure lure) {
        if (hook == null || hook.getWorld() == null) {
            return null;
        }
        Entity entity = hook.getWorld().getEntity(lure.id);
        return entity instanceof ItemDisplay display && display.isValid() ? display : null;
    }

    private void remove(UUID id) {
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            Entity entity = world.getEntity(id);
            if (entity != null) {
                entity.remove();
                return;
            }
        }
    }

    static boolean marked(Entity entity, NamespacedKey key) {
        return entity != null && entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static double lerpAngle(double from, double to, double t) {
        double delta = to - from;
        while (delta > Math.PI) {
            delta -= Math.PI * 2.0d;
        }
        while (delta < -Math.PI) {
            delta += Math.PI * 2.0d;
        }
        return from + delta * Math.max(0.0d, Math.min(1.0d, t));
    }

    private static Location waterNear(Location hook, int index) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 14; i++) {
            double angle = (Math.PI * 2.0d * index / 3.0d) + rng.nextDouble() * 0.85d;
            double radius = 2.2d + rng.nextDouble() * 2.4d;
            Location loc = hook.clone().add(
                    Math.cos(angle) * radius,
                    -0.30d - rng.nextDouble() * 0.40d,
                    Math.sin(angle) * radius
            );
            if (loc.getBlock().isLiquid()) {
                return loc;
            }
        }
        Location fallback = hook.clone().add(0, -0.35d, 0);
        return fallback.getBlock().isLiquid() ? fallback : null;
    }

    private static final class Lure {
        private final UUID id;
        private final double phase;
        private boolean biter;
        private double heading;
        private double speed;
        private final double cruise;
        private final double turn;
        private int dartLeft;
        private int nextDart;

        private Lure(UUID id, double phase, Location at, Location hook) {
            this.id = id;
            this.phase = phase;
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            Vector away = at.toVector().subtract(hook.toVector());
            this.heading = away.lengthSquared() < 0.01d
                    ? rng.nextDouble() * Math.PI * 2.0d
                    : Math.atan2(away.getZ(), away.getX());
            this.cruise = 0.045d + rng.nextDouble() * 0.025d;
            this.speed = this.cruise;
            this.turn = rng.nextBoolean() ? 1.0d : -1.0d;
            this.nextDart = 8 + rng.nextInt(18);
        }
    }
}
