package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.model.PhaseTransition;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Overworld Ender Dragon control: grounded intro, circling flight,
 * phase spectacles, and a pepped-up death animation.
 */
final class DragonDirector {

    private static final float GROUND_YAW_SPEED = 2.4f;

    private final BossInstance instance;
    private double cruiseAngle;
    private int deathTicks = -1;
    private int airTicks;
    private Location diveDest;
    private Location glideAt;
    private float bodyYaw;
    private double cruiseRadius = 17.5;
    private double cruiseHeight = 8.4;
    private int cruiseDir = 1;
    private AirMode airMode = AirMode.CRUISE;

    private enum AirMode {
        CRUISE,
        DIVE,
        CLIMB
    }

    DragonDirector(BossInstance instance) {
        this.instance = instance;
    }

    boolean isDragon() {
        return instance.getEntity() instanceof EnderDragon;
    }

    boolean isDying() {
        return deathTicks >= 0;
    }

    void onBind() {
        if (!(instance.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        dragon.setPhase(EnderDragon.Phase.HOVER);
        dragon.setAware(false);
        dragon.setGravity(false);
        Location spawn = instance.getSpawnLocation();
        try {
            dragon.setPodium(spawn);
        } catch (Throwable ignored) {
        }
        dragon.teleport(spawn);
        dragon.setVelocity(new Vector(0, 0, 0));
        dragon.setRotation(spawn.getYaw(), 15f);
    }

    void tick() {
        if (!isDragon() || instance.getEntity() == null || !instance.getEntity().isValid()) {
            return;
        }
        EnderDragon dragon = (EnderDragon) instance.getEntity();
        dragon.setFireTicks(0);
        if (isDying()) {
            dragon.setGravity(false);
            dragon.setPhase(EnderDragon.Phase.HOVER);
            return;
        }
        if (instance.isTransitioning()) {
            return;
        }
        if (instance.getTicksAlive() < introTicks()) {
            dragon.setGravity(false);
            dragon.setPhase(EnderDragon.Phase.HOVER);
            holdGround(dragon);
            return;
        }
        fly(dragon);
    }

    boolean beginDeath() {
        if (!isDragon() || isDying()) {
            return false;
        }
        deathTicks = 0;
        LivingEntity entity = instance.getEntity();
        if (entity != null && entity.isValid()) {
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setVelocity(new Vector(0, 0.18, 0));
            if (entity instanceof EnderDragon dragon) {
                dragon.setPhase(EnderDragon.Phase.HOVER);
            }
        }
        Location at = entity != null && entity.isValid()
                ? entity.getLocation()
                : instance.getSpawnLocation();
        World world = at.getWorld();
        if (world != null) {
            world.playSound(at, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.85f, 0.85f);
            world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.55f);
        }
        BukkitBroadcast.chat("&5&lAetherion&7: &fWhat a fight, its as if im young again... thank you...");
        return true;
    }

    boolean abortDeath() {
        boolean dying = isDying();
        deathTicks = -1;
        return dying;
    }

    /**
     * @return true when the cinematic finished and the entity should die
     */
    boolean tickDeath() {
        if (!isDying()) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        boolean body = entity != null && entity.isValid() && !entity.isDead();
        deathTicks++;
        Location at = (body ? entity.getLocation() : instance.getSpawnLocation()).clone().add(0, 0.18, 0);
        if (body) {
            try {
                entity.teleport(at);
                entity.setVelocity(new Vector(0, 0.16, 0));
            } catch (IllegalArgumentException | IllegalStateException ignored) {
            }
        }
        World world = at.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.PORTAL, at.clone().add(0, 1.5, 0), 18, 1.4, 1.2, 1.4, 0.25);
            world.spawnParticle(Particle.DRAGON_BREATH, at, 8, 1.1, 0.8, 1.1, 0.02);
            if (deathTicks % 12 == 0) {
                world.spawnParticle(Particle.END_ROD, at, 10, 1.0, 1.0, 1.0, 0.05);
            }
            if (deathTicks % 18 == 0) {
                world.spawnParticle(Particle.EXPLOSION, at, 1, 0.4, 0.4, 0.4, 0);
            }
        }
        if (deathTicks >= 165) {
            if (world != null) {
                world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 3, 1.2, 0.6, 1.2, 0);
                world.spawnParticle(Particle.FLASH, at, 2, 0.2, 0.2, 0.2, 0);
                world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.15f, 0.55f);
                world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.4f);
            }
            return true;
        }
        return false;
    }

    void tickFireSpiral(PhaseTransition transition, int tick, int duration) {
        LivingEntity entity = instance.getEntity();
        if (entity == null) {
            return;
        }
        Location focus = instance.hazardFocus(tick <= 1);
        holdHere(entity, focus.clone().add(0, 2.8, 0));
        World world = focus.getWorld();
        if (world == null) {
            return;
        }
        double progress = tick / (double) Math.max(1, duration);
        double height = 1.2 + progress * 8.0;
        int arms = 3;
        int points = 18;
        for (int arm = 0; arm < arms; arm++) {
            for (int i = 0; i < points; i++) {
                double t = (i / (double) points) * height;
                double yaw = tick * 0.28 + arm * (Math.PI * 2 / arms) + t * 0.85;
                double radius = 1.4 + t * 0.45;
                Location p = focus.clone().add(Math.cos(yaw) * radius, t, Math.sin(yaw) * radius);
                world.spawnParticle(Particle.FLAME, p, 1, 0, 0, 0, 0);
                if (i % 3 == 0) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0, 0, 0, 0);
                }
            }
        }
        if (tick % 8 == 0) {
            world.playSound(focus, Sound.BLOCK_BEACON_POWER_SELECT, 0.45f, 0.7f + (float) progress * 0.5f);
            world.playSound(focus, Sound.ENTITY_BLAZE_SHOOT, 0.25f, 0.55f);
        }
        world.spawnParticle(Particle.LAVA, entity.getLocation(), 3, 0.6, 0.4, 0.6, 0);
    }

    void tickLightningCharge(PhaseTransition transition, int tick, int duration) {
        LivingEntity entity = instance.getEntity();
        if (entity == null) {
            return;
        }
        Location focus = instance.hazardFocus(tick <= 1);
        holdHere(entity, focus.clone().add(0, 3.2, 0));
        World world = focus.getWorld();
        if (world == null) {
            return;
        }
        if (tick % 8 == 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location bolt;
            Player prey = nearestPlayer(entity, 32);
            if (prey != null && random.nextBoolean()) {
                bolt = prey.getLocation().clone().add(
                        random.nextDouble(-2.8, 2.8),
                        0.1,
                        random.nextDouble(-2.8, 2.8)
                );
            } else {
                bolt = focus.clone().add(
                        random.nextDouble(-8.0, 8.0),
                        0.2,
                        random.nextDouble(-8.0, 8.0)
                );
            }
            double damage = transition.getExplodeDamage() > 0 ? transition.getExplodeDamage() : 14.0;
            instance.strikeStormBolt(bolt, damage);
        }
        world.spawnParticle(Particle.END_ROD, entity.getLocation(), 4, 1.2, 0.8, 1.2, 0.02);
        if (tick == duration) {
            instance.startLightningStorm(
                    transition.getExplodeRadius() > 0 ? transition.getExplodeRadius() : 22.0,
                    transition.getExplodeDamage() > 0 ? transition.getExplodeDamage() : 8.0
            );
        }
    }

    void tickBlackHole(PhaseTransition transition, int tick, int duration) {
        LivingEntity entity = instance.getEntity();
        if (entity == null) {
            return;
        }
        Location focus = instance.hazardFocus(tick <= 1);
        holdHere(entity, focus.clone().add(0, 3.6, 0));
        World world = focus.getWorld();
        if (world == null) {
            return;
        }
        double progress = tick / (double) Math.max(1, duration);
        double radius = 2.0 + progress * 4.5;
        drawVoidSphere(focus.clone().add(0, 1.2, 0), radius);
        if (tick % 10 == 0) {
            world.playSound(focus, Sound.BLOCK_PORTAL_AMBIENT, 0.35f, 0.45f);
        }
        if (tick == duration) {
            instance.startBlackHole(
                    transition.getExplodeRadius() > 0 ? transition.getExplodeRadius() : 22.0,
                    2.15
            );
        }
    }

    private void holdGround(EnderDragon dragon) {
        Location spawn = instance.getSpawnLocation();
        Location hold = spawn.clone();
        hold.setYaw((dragon.getLocation().getYaw() + GROUND_YAW_SPEED) % 360f);
        hold.setPitch(18f);
        placeDragon(dragon, hold);
        if (instance.getTicksAlive() % 12 == 0) {
            spawn.getWorld().spawnParticle(Particle.PORTAL, hold.clone().add(0, 1, 0), 8, 1.2, 0.4, 1.2, 0.15);
        }
    }

    private void fly(EnderDragon dragon) {
        airTicks++;
        Location spawn = instance.getSpawnLocation();

        if (airMode == AirMode.CRUISE && airTicks > 92) {
            airMode = AirMode.DIVE;
            airTicks = 0;
            diveDest = randomArenaPoint(spawn, 3.5, 7.0, 16.0);
            faceAlongPath(diveDest, -17f);
        } else if (airMode == AirMode.DIVE && (airTicks > 52 || reached(diveDest, 3.2))) {
            airMode = AirMode.CLIMB;
            airTicks = 0;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            diveDest = spawn.clone().add(random.nextDouble(-5.5, 5.5), 12.0, random.nextDouble(-5.5, 5.5));
            faceAlongPath(diveDest, -6f);
        } else if (airMode == AirMode.CLIMB && (airTicks > 44 || reached(diveDest, 2.8))) {
            airMode = AirMode.CRUISE;
            airTicks = 0;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (random.nextDouble() < 0.4) {
                cruiseDir = -cruiseDir;
            }
            cruiseRadius = 15.0 + random.nextDouble() * 6.0;
            cruiseHeight = 7.1 + random.nextDouble() * 3.4;
        }

        if (airMode == AirMode.DIVE) {
            faceAlongPath(diveDest, -17f);
            smoothGlide(dragon, diveDest, 0.22, 0.64);
            return;
        }
        if (airMode == AirMode.CLIMB) {
            faceAlongPath(diveDest, -6f);
            smoothGlide(dragon, diveDest, 0.17, 0.48);
            return;
        }
        cruise(dragon);
    }

    private void cruise(EnderDragon dragon) {
        Location spawn = instance.getSpawnLocation();
        cruiseAngle += 0.0082 * cruiseDir;
        double radius = cruiseRadius + Math.sin(cruiseAngle * 0.65) * 2.2;
        Location dest = spawn.clone().add(
                Math.cos(cruiseAngle) * radius,
                cruiseHeight + Math.sin(cruiseAngle * 0.5) * 1.7,
                Math.sin(cruiseAngle) * radius
        );
        Vector tangent = new Vector(
                -Math.sin(cruiseAngle) * cruiseDir,
                0.05,
                Math.cos(cruiseAngle) * cruiseDir
        );
        dest.setDirection(tangent);
        dest.setPitch(-10f);
        clampToArena(dest, spawn, 24.0);
        smoothGlide(dragon, dest, 0.16, 0.44);
    }

    private void faceAlongPath(Location dest, float pitch) {
        if (dest == null) {
            return;
        }
        if (glideAt != null && glideAt.getWorld() == dest.getWorld()) {
            Vector to = dest.toVector().subtract(glideAt.toVector());
            if (to.lengthSquared() > 0.01) {
                dest.setDirection(to);
            }
        }
        dest.setPitch(pitch);
    }

    private Location randomArenaPoint(Location spawn, double y, double minRadius, double maxRadius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double yaw = random.nextDouble() * Math.PI * 2;
        double radius = minRadius + random.nextDouble() * Math.max(0.1, maxRadius - minRadius);
        Location dest = spawn.clone().add(Math.cos(yaw) * radius, y, Math.sin(yaw) * radius);
        clampToArena(dest, spawn, 24.0);
        return dest;
    }

    private static void clampToArena(Location dest, Location spawn, double max) {
        double dx = dest.getX() - spawn.getX();
        double dz = dest.getZ() - spawn.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist <= max || dist < 0.001) {
            return;
        }
        double scale = max / dist;
        dest.setX(spawn.getX() + dx * scale);
        dest.setZ(spawn.getZ() + dz * scale);
    }

    private void smoothGlide(EnderDragon dragon, Location dest, double lerp, double maxStep) {
        if (dest == null || dest.getWorld() == null) {
            return;
        }
        if (glideAt == null || glideAt.getWorld() != dest.getWorld()) {
            glideAt = dragon.getLocation().clone();
            bodyYaw = dragon.getLocation().getYaw();
        }
        Vector cur = glideAt.toVector();
        Vector delta = dest.toVector().subtract(cur);
        double dist = delta.length();
        if (dist > 0.04) {
            Vector step = delta.clone().multiply(lerp);
            double cap = dist > 16.0 ? Math.min(1.05, dist * 0.12) : maxStep;
            if (step.length() > cap) {
                step.normalize().multiply(cap);
            }
            glideAt = cur.add(step).toLocation(dest.getWorld());
        }
        bodyYaw = turnToward(bodyYaw, dest.getYaw(), 4.8f);
        glideAt.setYaw(bodyYaw);
        glideAt.setPitch(dest.getPitch());
        dragon.setGravity(false);
        dragon.setAware(false);
        try {
            dragon.setPodium(glideAt);
        } catch (Throwable ignored) {
        }
        placeDragon(dragon, glideAt);
        dragon.setPhase(EnderDragon.Phase.HOVER);
    }

    private static float turnToward(float current, float target, float maxStep) {
        float delta = target - current;
        while (delta > 180f) {
            delta -= 360f;
        }
        while (delta < -180f) {
            delta += 360f;
        }
        if (delta > maxStep) {
            delta = maxStep;
        } else if (delta < -maxStep) {
            delta = -maxStep;
        }
        return current + delta;
    }

    private boolean reached(Location dest, double range) {
        return dest != null
                && glideAt != null
                && glideAt.getWorld() == dest.getWorld()
                && glideAt.distanceSquared(dest) <= range * range;
    }

    private Player nearestPlayer(LivingEntity from, double range) {
        Player best = null;
        double bestDist = range * range;
        for (Player player : from.getWorld().getPlayers()) {
            if (!player.isValid() || player.isDead()
                    || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(from.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private void holdHere(LivingEntity entity, Location dest) {
        dest = dest.clone();
        dest.setYaw(entity.getLocation().getYaw());
        dest.setPitch(10f);
        if (entity instanceof EnderDragon dragon) {
            placeDragon(dragon, dest);
            dragon.setPhase(EnderDragon.Phase.HOVER);
        } else {
            entity.teleport(dest);
            entity.setVelocity(new Vector(0, 0, 0));
        }
    }

    private void placeDragon(EnderDragon dragon, Location dest) {
        nmsMoveTo(dragon, dest);
        if (dragon.getLocation().distanceSquared(dest) > 2.2 * 2.2) {
            try {
                dragon.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } catch (Throwable ignored) {
                dragon.teleport(dest);
            }
            nmsMoveTo(dragon, dest);
        }
        dragon.setVelocity(new Vector(0, 0, 0));
        dragon.setRotation(dest.getYaw(), dest.getPitch());
    }

    private static void nmsMoveTo(EnderDragon dragon, Location dest) {
        try {
            Object handle = dragon.getClass().getMethod("getHandle").invoke(dragon);
            try {
                Method method = handle.getClass().getMethod(
                        "absMoveTo",
                        double.class,
                        double.class,
                        double.class,
                        float.class,
                        float.class
                );
                method.invoke(handle, dest.getX(), dest.getY(), dest.getZ(), dest.getYaw(), dest.getPitch());
                return;
            } catch (NoSuchMethodException ignored) {
            }
            Class<?> type = handle.getClass();
            while (type != null && type != Object.class) {
                for (String name : new String[] {"absMoveTo", "moveTo", "snapTo"}) {
                    try {
                        Method method = type.getDeclaredMethod(
                                name,
                                double.class,
                                double.class,
                                double.class,
                                float.class,
                                float.class
                        );
                        method.setAccessible(true);
                        method.invoke(
                                handle,
                                dest.getX(),
                                dest.getY(),
                                dest.getZ(),
                                dest.getYaw(),
                                dest.getPitch()
                        );
                        return;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                try {
                    Method setPos = type.getDeclaredMethod("setPos", double.class, double.class, double.class);
                    setPos.setAccessible(true);
                    setPos.invoke(handle, dest.getX(), dest.getY(), dest.getZ());
                    return;
                } catch (NoSuchMethodException ignored) {
                }
                type = type.getSuperclass();
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void drawVoidSphere(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int points = 28;
        double phi = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < points; i++) {
            double y = 1.0 - (i / (double) (points - 1)) * 2.0;
            double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = phi * i;
            Location p = center.clone().add(
                    Math.cos(theta) * ring * radius,
                    y * radius * 0.65,
                    Math.sin(theta) * ring * radius
            );
            world.spawnParticle(Particle.SQUID_INK, p, 1, 0, 0, 0, 0);
            if (i % 4 == 0) {
                world.spawnParticle(Particle.PORTAL, p, 2, 0.05, 0.05, 0.05, 0.02);
            }
        }
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 10, 0.35, 0.35, 0.35, 0.05);
    }

    static void drawSilentBolt(Location ground) {
        World world = ground.getWorld();
        if (world == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double xJitter = 0;
        double zJitter = 0;
        for (int y = 0; y < 14; y++) {
            xJitter += random.nextDouble(-0.35, 0.35);
            zJitter += random.nextDouble(-0.35, 0.35);
            Location p = ground.clone().add(xJitter, y * 0.65, zJitter);
            world.spawnParticle(Particle.ELECTRIC_SPARK, p, 2, 0.04, 0.18, 0.04, 0.01);
            if (y % 3 == 0) {
                world.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
            }
        }
        world.playSound(ground, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.12f, 1.25f);
    }

    private int introTicks() {
        return Math.max(20, instance.getTemplate().getOptions().getInvulnerableSpawnTicks());
    }

    private static final class BukkitBroadcast {
        private static void chat(String message) {
            org.bukkit.Bukkit.getOnlinePlayers().forEach(player ->
                    player.sendMessage(TextUtil.component(message))
            );
        }
    }
}
