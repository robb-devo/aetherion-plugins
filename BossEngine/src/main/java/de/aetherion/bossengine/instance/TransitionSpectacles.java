package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.model.PhaseTransition;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lobby Cleaner phase FX only: purple tornado, then four spinning crystal beams.
 * BossInstance never routes the Ashen Sheath here. Its tells are a straight petal draw
 * and a vertical cherry column.
 */
final class TransitionSpectacles {

    private static final double HIGH_Y = 1.58;
    private static final double LOW_Y = 0.52;
    private static final double HIGH_HALF = 0.38;
    private static final double LOW_HALF = 0.42;
    private static final double BEAM_WIDTH = 0.62;
    private static final double INNER = 1.9;
    private static final int HIT_COOLDOWN_TICKS = 16;
    private static final int BEAM_WINDUP_TICKS = 18;

    private final BossInstance instance;
    private final List<EnderCrystal> crystals = new ArrayList<>();
    private final Map<UUID, Long> lastHitTick = new HashMap<>();

    TransitionSpectacles(BossInstance instance) {
        this.instance = instance;
    }

    void tickVoidTornado(PhaseTransition transition, int tick, int duration) {
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid()) {
            return;
        }
        Location focus = instance.hazardFocus(tick <= 1);
        hold(entity, focus);
        World world = focus.getWorld();
        if (world == null) {
            return;
        }
        double progress = tick / (double) Math.max(1, duration);
        double height = 2.2 + progress * 5.4;
        int arms = 4;
        int points = 16;
        for (int arm = 0; arm < arms; arm++) {
            for (int i = 0; i < points; i++) {
                double t = (i / (double) (points - 1)) * height;
                double yaw = tick * 0.42 + arm * (Math.PI * 2 / arms) + t * 0.95;
                double radius = 0.55 + t * 0.38;
                Location p = focus.clone().add(Math.cos(yaw) * radius, t * 0.22, Math.sin(yaw) * radius);
                world.spawnParticle(Particle.PORTAL, p, 2, 0.02, 0.04, 0.02, 0.12);
                if (i % 2 == 0) {
                    world.spawnParticle(Particle.REVERSE_PORTAL, p, 1, 0, 0, 0, 0);
                }
                if (i % 4 == 0) {
                    world.spawnParticle(Particle.WITCH, p, 1, 0.04, 0.08, 0.04, 0);
                }
            }
        }
        world.spawnParticle(Particle.DRAGON_BREATH, focus.clone().add(0, 1.1, 0), 6, 0.45, 0.8, 0.45, 0.01);
        if (tick % 7 == 0) {
            world.playSound(focus, Sound.BLOCK_PORTAL_AMBIENT, 0.55f, 0.7f + (float) progress * 0.45f);
            world.playSound(focus, Sound.ENTITY_ENDERMAN_AMBIENT, 0.35f, 0.45f);
        }
    }

    void tickBeamSpin(PhaseTransition transition, int tick, int duration) {
        LivingEntity entity = instance.getEntity();
        if (entity == null || !entity.isValid()) {
            return;
        }
        Location focus = instance.hazardFocus(tick <= 1);
        hold(entity, focus);
        World world = focus.getWorld();
        if (world == null) {
            return;
        }
        if (crystals.isEmpty()) {
            spawnCrystals(world, focus);
        }
        double length = Math.max(12.0, transition.getStartRadius() > 1.0 ? transition.getStartRadius() : 18.0);
        double yaw = tick * (Math.PI * 2.0 / 72.0);
        for (int i = 0; i < 4; i++) {
            boolean high = i % 2 == 0;
            double beamY = high ? HIGH_Y : LOW_Y;
            double half = high ? HIGH_HALF : LOW_HALF;
            double angle = yaw + i * (Math.PI * 0.5);
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);
            Location crystalAt = focus.clone().add(dirX * 0.28, beamY, dirZ * 0.28);
            Location start = focus.clone().add(dirX * 0.45, beamY, dirZ * 0.45);
            Location end = focus.clone().add(dirX * length, beamY, dirZ * length);
            updateCrystal(i, crystalAt, end);
            drawBeam(world, start, end, high);
            if (tick > BEAM_WINDUP_TICKS) {
                hitBeam(focus, dirX, dirZ, length, beamY, half, transition);
            }
        }
        drawGroundSpiral(world, focus, tick);
        if (tick <= BEAM_WINDUP_TICKS && tick % 5 == 0) {
            world.playSound(focus, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.55f, 0.7f + tick * 0.03f);
        }
        if (tick % 10 == 0) {
            world.playSound(focus, Sound.BLOCK_BEACON_AMBIENT, 0.7f, 0.55f);
            world.playSound(focus, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.35f, 0.45f);
        }
    }

    void finish() {
        Iterator<EnderCrystal> iterator = crystals.iterator();
        while (iterator.hasNext()) {
            EnderCrystal crystal = iterator.next();
            if (crystal != null && crystal.isValid()) {
                crystal.remove();
            }
            iterator.remove();
        }
        lastHitTick.clear();
    }

    private void spawnCrystals(World world, Location focus) {
        for (int i = 0; i < 4; i++) {
            boolean high = i % 2 == 0;
            Location at = focus.clone().add(0.12, high ? HIGH_Y : LOW_Y, 0);
            try {
                EnderCrystal crystal = world.spawn(at, EnderCrystal.class, spawned -> {
                    spawned.setShowingBottom(false);
                    spawned.setInvulnerable(true);
                    spawned.setPersistent(false);
                    spawned.setSilent(true);
                    spawned.setGravity(false);
                    spawned.setVisualFire(false);
                    instance.getKeys().tagBeamFx(spawned, instance.getInstanceId());
                });
                crystals.add(crystal);
            } catch (Throwable ignored) {
                crystals.add(null);
            }
        }
    }

    private void updateCrystal(int index, Location start, Location end) {
        if (index < 0 || index >= crystals.size()) {
            return;
        }
        EnderCrystal crystal = crystals.get(index);
        if (crystal == null || !crystal.isValid()) {
            return;
        }
        crystal.teleport(start);
        crystal.setBeamTarget(end);
    }

    private void drawBeam(World world, Location start, Location end, boolean high) {
        Vector step = end.toVector().subtract(start.toVector());
        double length = step.length();
        if (length < 0.2) {
            return;
        }
        Vector dir = step.multiply(1.0 / length);
        Particle core = high ? Particle.END_ROD : Particle.DRAGON_BREATH;
        for (double d = 0; d <= length; d += 0.7) {
            Location p = start.clone().add(dir.clone().multiply(d));
            world.spawnParticle(core, p, 1, 0.02, 0.02, 0.02, 0);
            if ((int) (d * 10) % 14 == 0) {
                world.spawnParticle(Particle.REVERSE_PORTAL, p, 1, 0.04, 0.04, 0.04, 0);
            }
        }
    }

    private void drawGroundSpiral(World world, Location focus, int tick) {
        double yaw = tick * 0.28;
        for (int i = 0; i < 22; i++) {
            double t = i / 21.0;
            double radius = 1.4 + t * 11.0;
            double angle = yaw + t * Math.PI * 3.2;
            Location p = focus.clone().add(Math.cos(angle) * radius, 0.12, Math.sin(angle) * radius);
            world.spawnParticle(Particle.PORTAL, p, 1, 0, 0, 0, 0);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.SQUID_INK, p, 1, 0.05, 0.02, 0.05, 0);
            }
        }
    }

    private void hitBeam(
            Location origin,
            double dirX,
            double dirZ,
            double length,
            double beamY,
            double half,
            PhaseTransition transition
    ) {
        World world = origin.getWorld();
        LivingEntity boss = instance.getEntity();
        if (world == null || boss == null) {
            return;
        }
        double damage = transition.getExplodeDamage() > 0 ? transition.getExplodeDamage() : 88.0;
        double scatter = transition.getExplodeRadius() > 0 ? transition.getExplodeRadius() : 10.0;
        long now = instance.getTicksAlive();
        for (Player player : world.getPlayers()) {
            if (!instance.isCombatTarget(player)) {
                continue;
            }
            if (player.getWorld() != world) {
                continue;
            }
            if (!intersects(player.getBoundingBox(), origin, dirX, dirZ, length, origin.getY() + beamY, half)) {
                continue;
            }
            Long last = lastHitTick.get(player.getUniqueId());
            if (last != null && now - last < HIT_COOLDOWN_TICKS) {
                continue;
            }
            lastHitTick.put(player.getUniqueId(), now);
            BossHits.hurt(player, boss, instance.scaleDamage(damage));
            scatter(player, scatter);
            world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.15f, 0.7f);
            world.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1.0, 0), 18, 0.3, 0.5, 0.3, 0.12);
            player.sendActionBar(net.kyori.adventure.text.Component.text("§5The bag found you."));
        }
    }

    private static boolean intersects(
            BoundingBox box,
            Location origin,
            double dirX,
            double dirZ,
            double length,
            double beamWorldY,
            double half
    ) {
        if (box.getMaxY() < beamWorldY - half || box.getMinY() > beamWorldY + half) {
            return false;
        }
        double px = (box.getMinX() + box.getMaxX()) * 0.5;
        double pz = (box.getMinZ() + box.getMaxZ()) * 0.5;
        double dx = px - origin.getX();
        double dz = pz - origin.getZ();
        double along = dx * dirX + dz * dirZ;
        if (along < INNER || along > length) {
            return false;
        }
        double perp = Math.abs(dx * dirZ - dz * dirX);
        double playerHalf = Math.max(box.getWidthX(), box.getWidthZ()) * 0.5;
        return perp <= BEAM_WIDTH + playerHalf;
    }

    private static void scatter(Player player, double range) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location from = player.getLocation();
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        double span = Math.max(6.0, range);
        for (int attempt = 0; attempt < 14; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = span * (0.7 + random.nextDouble() * 0.5);
            Location dest = from.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            dest.setY(from.getY());
            dest.setYaw(from.getYaw());
            dest.setPitch(from.getPitch());
            if (!dest.getBlock().isPassable()) {
                dest.add(0, 1.1, 0);
            }
            if (dest.getBlock().isPassable() && dest.clone().add(0, 1, 0).getBlock().isPassable()) {
                player.teleport(dest);
                return;
            }
        }
    }

    private void hold(LivingEntity entity, Location dest) {
        Location hold = dest.clone();
        hold.setYaw(entity.getLocation().getYaw());
        hold.setPitch(entity.getLocation().getPitch());
        instance.runInternalTeleport(() -> entity.teleport(hold));
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0);
    }
}
