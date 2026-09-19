package de.aetherion.bossengine.fx;

import de.aetherion.bossengine.combat.BossHits;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.skill.t2.T2Mechanics;
import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fake destruction: boom packets, flying blocks that never place, short visual craters.
 * No world edits.
 */
public final class FakeDestruction {

    private static final Material[] DEBRIS = {
            Material.MAGMA_BLOCK,
            Material.NETHERRACK,
            Material.BLACKSTONE,
            Material.BASALT,
            Material.COBBLED_DEEPSLATE,
            Material.GLOWSTONE
    };

    private FakeDestruction() {
    }

    public static void boom(Location at, BossInstance instance, double damage, double radius, boolean crater) {
        if (at == null || instance == null) {
            return;
        }
        World world = at.getWorld();
        LivingEntity source = instance.getEntity();
        if (world == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.55f, 0.62f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.9f);
        world.playSound(at, Sound.ENTITY_GHAST_SHOOT, 0.55f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.4, 0), 3, 0.35, 0.2, 0.35, 0);
        world.spawnParticle(Particle.LAVA, at, 18, 0.55, 0.2, 0.55, 0);
        world.spawnParticle(Particle.FLAME, at, 28, 0.7, 0.35, 0.7, 0.04);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at.clone().add(0, 0.4, 0), 8, 0.45, 0.2, 0.45, 0.01);
        blockBurst(world, at, Material.MAGMA_BLOCK, 22);
        blockBurst(world, at, Material.NETHERRACK, 12);

        T2Mechanics.runMeteorBoom(() -> world.createExplosion(at, 2.8f, false, false, source));

        double scaled = instance.scaleDamage(damage);
        double radiusSq = radius * radius;
        for (Player player : world.getPlayers()) {
            if (!T2Mechanics.vulnerable(player)) {
                continue;
            }
            if (player.getLocation().distanceSquared(at) > radiusSq) {
                continue;
            }
            if (scaled > 0) {
                BossHits.hurt(player, source, scaled);
            }
            Vector push = player.getLocation().toVector().subtract(at.toVector());
            if (push.lengthSquared() > 0.01) {
                push.normalize().multiply(0.85).setY(Math.max(0.35, push.getY()));
                player.setVelocity(player.getVelocity().add(push));
            }
            player.setFireTicks(Math.max(player.getFireTicks(), 55));
        }

        spawnDebris(at, instance, 8, 0.55, 0.95, 36, DEBRIS);
        if (crater) {
            paintCrater(at, instance);
        }
    }

    public static void visualImpact(Location at, BossInstance instance, int debrisCount, Material... palette) {
        if (at == null || instance == null) {
            return;
        }
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Material[] used = palette == null || palette.length == 0 ? DEBRIS : palette;
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.25f, 0.7f);
        world.playSound(at, Sound.BLOCK_STONE_BREAK, 0.9f, 0.55f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.25, 0), 2, 0.25, 0.1, 0.25, 0);
        world.spawnParticle(Particle.CLOUD, at, 10, 0.6, 0.2, 0.6, 0.03);
        blockBurst(world, at, used[0], 18);
        T2Mechanics.runMeteorBoom(() -> world.createExplosion(at, 2.2f, false, false, instance.getEntity()));
        spawnDebris(at, instance, Math.max(4, debrisCount), 0.48, 0.82, 32, used);
        paintCrater(at, instance);
    }

    public static List<FallingBlock> spawnDebris(
            Location at,
            BossInstance instance,
            int count,
            double speed,
            double lift,
            int lifetimeTicks
    ) {
        return spawnDebris(at, instance, count, speed, lift, lifetimeTicks, DEBRIS);
    }

    public static List<FallingBlock> spawnDebris(
            Location at,
            BossInstance instance,
            int count,
            double speed,
            double lift,
            int lifetimeTicks,
            Material[] palette
    ) {
        List<FallingBlock> spawned = new ArrayList<>();
        World world = at == null ? null : at.getWorld();
        if (world == null || instance == null) {
            return spawned;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Material[] used = palette == null || palette.length == 0 ? DEBRIS : palette;
        int n = Math.max(1, Math.min(36, count));
        for (int i = 0; i < n; i++) {
            Material mat = used[random.nextInt(used.length)];
            Location from = at.clone().add(
                    random.nextDouble(-0.4, 0.4),
                    0.35 + random.nextDouble() * 0.5,
                    random.nextDouble(-0.4, 0.4)
            );
            FallingBlock falling = spawnHeldDebris(from, instance, mat, true);
            if (falling == null) {
                continue;
            }
            falling.setGravity(true);
            double yaw = random.nextDouble() * Math.PI * 2;
            falling.setVelocity(new Vector(
                    Math.cos(yaw) * speed * (0.7 + random.nextDouble() * 0.6),
                    lift * (0.65 + random.nextDouble() * 0.7),
                    Math.sin(yaw) * speed * (0.7 + random.nextDouble() * 0.6)
            ));
            spawned.add(falling);
        }
        JavaPlugin plugin = instance.getPlugin();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (FallingBlock falling : spawned) {
                if (falling != null && falling.isValid()) {
                    falling.remove();
                }
            }
        }, Math.max(12L, lifetimeTicks));
        return spawned;
    }

    public static FallingBlock spawnHeldDebris(Location at, BossInstance instance, Material material, boolean gravity) {
        World world = at == null ? null : at.getWorld();
        if (world == null || instance == null || material == null) {
            return null;
        }
        try {
            FallingBlock falling = world.spawnFallingBlock(at, material.createBlockData());
            falling.setDropItem(false);
            falling.setHurtEntities(false);
            falling.setCancelDrop(true);
            falling.setGravity(gravity);
            falling.setPersistent(false);
            instance.getKeys().tagDebris(falling);
            return falling;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void paintCrater(Location center, BossInstance instance) {
        World world = center == null ? null : center.getWorld();
        if (world == null || instance == null) {
            return;
        }
        JavaPlugin plugin = instance.getPlugin();
        List<Location> painted = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = 2;
        int y = center.getBlockY() - 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                Block block = world.getBlockAt(center.getBlockX() + dx, y, center.getBlockZ() + dz);
                if (!canOverlay(block)) {
                    continue;
                }
                Material fake = dx == 0 && dz == 0
                        ? Material.MAGMA_BLOCK
                        : (random.nextBoolean() ? Material.MAGMA_BLOCK : Material.BLACKSTONE);
                Location loc = block.getLocation();
                painted.add(loc);
                BlockData overlay = fake.createBlockData();
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(center) <= 48 * 48) {
                        player.sendBlockChange(loc, overlay);
                    }
                }
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Location loc : painted) {
                BlockData real = loc.getBlock().getBlockData();
                for (Player player : world.getPlayers()) {
                    player.sendBlockChange(loc, real);
                }
            }
        }, 52L);
    }

    public static void blockBurst(World world, Location at, Material material, int count) {
        if (world == null || at == null || material == null) {
            return;
        }
        BlockData data = material.createBlockData();
        try {
            world.spawnParticle(Particle.BLOCK, at, count, 0.55, 0.25, 0.55, 0.18, data);
        } catch (Throwable ignored) {
            try {
                world.spawnParticle(Particle.FALLING_DUST, at, Math.max(6, count / 2), 0.4, 0.2, 0.4, 0.02, data);
            } catch (Throwable ignoredToo) {
            }
        }
    }

    private static boolean canOverlay(Block block) {
        if (block == null || !block.getType().isSolid() || block.getType().isAir()) {
            return false;
        }
        Material type = block.getType();
        if (type == Material.BEDROCK
                || type == Material.BARRIER
                || type == Material.COMMAND_BLOCK
                || type == Material.CHAIN_COMMAND_BLOCK
                || type == Material.REPEATING_COMMAND_BLOCK
                || type == Material.STRUCTURE_BLOCK
                || type == Material.JIGSAW
                || type == Material.CHEST
                || type == Material.ENDER_CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.SPAWNER) {
            return false;
        }
        return !(block.getState() instanceof Container);
    }
}
