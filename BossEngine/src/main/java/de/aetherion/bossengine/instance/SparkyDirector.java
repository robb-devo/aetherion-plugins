package de.aetherion.bossengine.instance;

import de.aetherion.bossengine.fx.FakeDestruction;
import de.aetherion.bossengine.util.AttributeUtil;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sparky death: black hole that eats fake blocks, then spits them with a grid-core boom.
 */
final class SparkyDirector {

    private static final Material[] SUCK = {
            Material.MAGMA_BLOCK,
            Material.NETHERRACK,
            Material.BLACKSTONE,
            Material.BASALT,
            Material.GLOWSTONE,
            Material.COBBLED_DEEPSLATE
    };

    private final BossInstance instance;
    private final List<FallingBlock> swallowed = new ArrayList<>();
    private int deathTicks = -1;
    private Location focus;
    private double startScale = 3.4;

    SparkyDirector(BossInstance instance) {
        this.instance = instance;
    }

    boolean isSparky() {
        return instance.getTemplate() != null
                && "sparky".equalsIgnoreCase(instance.getTemplate().getId());
    }

    boolean isDying() {
        return deathTicks >= 0;
    }

    boolean beginDeath() {
        if (!isSparky() || isDying()) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        deathTicks = 0;
        focus = entity != null && entity.isValid()
                ? entity.getLocation().clone()
                : instance.getSpawnLocation();
        startScale = entity != null
                ? AttributeUtil.getBase(entity, AttributeUtil.scale(), 3.4)
                : 3.4;
        if (entity != null && entity.isValid()) {
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setVelocity(new Vector(0, 0, 0));
            if (entity instanceof Mob mob) {
                mob.setAI(false);
                mob.setAware(false);
            }
        }
        World world = focus.getWorld();
        if (world != null) {
            world.playSound(focus, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.45f);
            world.playSound(focus, Sound.ENTITY_MAGMA_CUBE_SQUISH, 1.4f, 0.35f);
            world.playSound(focus, Sound.BLOCK_PORTAL_TRIGGER, 1.05f, 0.5f);
        }
        org.bukkit.Bukkit.getOnlinePlayers().forEach(player ->
                player.sendMessage(TextUtil.component("&6&lSparky&7: &cThe grid is eating itself. Do not file a ticket."))
        );
        return true;
    }

    boolean abort() {
        boolean dying = isDying();
        deathTicks = -1;
        clearDebris();
        return dying;
    }

    /**
     * @return true when the cinematic finished and the entity should die
     */
    boolean tick() {
        if (!isDying()) {
            return false;
        }
        LivingEntity entity = instance.getEntity();
        boolean body = entity != null && entity.isValid() && !entity.isDead();
        deathTicks++;
        Location hold = (focus == null
                ? (body ? entity.getLocation() : instance.getSpawnLocation())
                : focus).clone();
        double lift = deathTicks < 90 ? Math.min(2.4, deathTicks * 0.028) : 2.4;
        hold.add(0, lift, 0);
        if (body) {
            entity.teleport(hold);
            entity.setVelocity(new Vector(0, 0, 0));
            entity.setFallDistance(0);
            entity.setFireTicks(0);
        }

        World world = hold.getWorld();
        if (world == null) {
            return deathTicks >= 158;
        }
        Location core = hold.clone().add(0, (body ? entity.getHeight() : 2.2) * 0.45, 0);

        if (deathTicks <= 24) {
            charge(world, core);
        } else if (deathTicks <= 96) {
            suck(world, core, entity);
        } else if (deathTicks <= 112) {
            compress(world, core, entity);
        } else {
            spit(world, core, entity);
        }

        if (deathTicks >= 158) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, core, 4, 1.1, 0.6, 1.1, 0);
            world.spawnParticle(Particle.FLASH, core, 3, 0.4, 0.4, 0.4, 0);
            world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.45f);
            world.playSound(core, Sound.ENTITY_WITHER_DEATH, 0.7f, 0.55f);
            world.playSound(core, Sound.ENTITY_GHAST_DEATH, 1.1f, 0.5f);
            FakeDestruction.spawnDebris(core, instance, 18, 1.15, 1.2, 40);
            clearDebris();
            if (entity instanceof org.bukkit.entity.MagmaCube cube) {
                cube.setSize(1);
            }
            return true;
        }
        return false;
    }

    private void charge(World world, Location core) {
        world.spawnParticle(Particle.LAVA, core, 6, 0.6, 0.5, 0.6, 0);
        world.spawnParticle(Particle.FLAME, core, 10, 0.7, 0.6, 0.7, 0.02);
        if (deathTicks % 8 == 0) {
            world.playSound(core, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 1.1f, 0.6f);
            world.playSound(core, Sound.ENTITY_MAGMA_CUBE_JUMP, 0.7f, 0.4f);
        }
    }

    private void suck(World world, Location core, LivingEntity entity) {
        if (deathTicks == 25 || (deathTicks % 6 == 0 && swallowed.size() < 28)) {
            spawnSwallowed(core);
        }
        double progress = (deathTicks - 24) / 72.0;
        double pull = 0.18 + progress * 0.22;
        Iterator<FallingBlock> iterator = swallowed.iterator();
        while (iterator.hasNext()) {
            FallingBlock falling = iterator.next();
            if (falling == null || !falling.isValid()) {
                iterator.remove();
                continue;
            }
            Vector to = core.toVector().subtract(falling.getLocation().toVector());
            double dist = to.length();
            if (dist < 0.7) {
                falling.remove();
                iterator.remove();
                world.spawnParticle(Particle.SQUID_INK, core, 6, 0.2, 0.2, 0.2, 0.02);
                continue;
            }
            falling.setGravity(false);
            falling.setVelocity(to.normalize().multiply(Math.min(1.15, pull + dist * 0.04)));
        }
        world.spawnParticle(Particle.SQUID_INK, core, 14, 0.8, 0.8, 0.8, 0.04);
        world.spawnParticle(Particle.SMOKE, core, 10, 1.1, 1.0, 1.1, 0.01);
        world.spawnParticle(Particle.REVERSE_PORTAL, core, 12, 0.5, 0.5, 0.5, 0.15);
        world.spawnParticle(Particle.ASH, core, 8, 1.4, 1.0, 1.4, 0);
        drawVoidRing(world, core, 3.2 + (1.0 - progress) * 5.5, progress);
        pullPlayers(core, 0.11);
        if (deathTicks % 10 == 0) {
            world.playSound(core, Sound.BLOCK_PORTAL_AMBIENT, 0.85f, 0.4f + (float) progress * 0.5f);
            world.playSound(core, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.45f, 0.55f);
        }
        double scale = startScale * (1.0 - progress * 0.22);
        AttributeUtil.setBase(entity, AttributeUtil.scale(), Math.max(1.6, scale));
    }

    private void compress(World world, Location core, LivingEntity entity) {
        double t = (deathTicks - 96) / 16.0;
        AttributeUtil.setBase(entity, AttributeUtil.scale(), Math.max(0.55, startScale * (0.78 - t * 0.55)));
        world.spawnParticle(Particle.FLASH, core, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.SQUID_INK, core, 20, 0.25, 0.25, 0.25, 0.08);
        world.spawnParticle(Particle.REVERSE_PORTAL, core, 18, 0.2, 0.2, 0.2, 0.4);
        if (deathTicks == 104) {
            world.playSound(core, Sound.BLOCK_END_PORTAL_SPAWN, 1.15f, 0.55f);
            world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 1.4f);
        }
        pullPlayers(core, 0.22);
        clearDebris();
    }

    private void spit(World world, Location core, LivingEntity entity) {
        if (deathTicks == 113) {
            AttributeUtil.setBase(entity, AttributeUtil.scale(), Math.max(0.4, startScale * 0.35));
            FakeDestruction.spawnDebris(core, instance, 22, 1.35, 1.05, 42);
            world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 1.7f, 0.5f);
            world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.8f);
            world.playSound(core, Sound.ENTITY_GHAST_SCREAM, 0.8f, 0.45f);
            coreBoom(world, core, entity);
        }
        if (deathTicks % 5 == 0) {
            Location burst = core.clone().add(
                    ThreadLocalRandom.current().nextDouble(-2.4, 2.4),
                    ThreadLocalRandom.current().nextDouble(-0.4, 1.6),
                    ThreadLocalRandom.current().nextDouble(-2.4, 2.4)
            );
            world.spawnParticle(Particle.EXPLOSION_EMITTER, burst, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.LAVA, burst, 10, 0.4, 0.3, 0.4, 0);
            world.playSound(burst, Sound.ENTITY_GENERIC_EXPLODE, 0.85f, 0.7f);
            FakeDestruction.blockBurst(world, burst, Material.MAGMA_BLOCK, 16);
        }
        world.spawnParticle(Particle.FLAME, core, 16, 1.2, 0.8, 1.2, 0.06);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, core, 6, 1.0, 0.6, 1.0, 0.01);
        pushPlayers(core, 0.18);
    }

    private void coreBoom(World world, Location core, LivingEntity entity) {
        de.aetherion.bossengine.skill.t2.T2Mechanics.runMeteorBoom(
                () -> world.createExplosion(core, 3.4f, false, false, entity)
        );
        world.spawnParticle(Particle.EXPLOSION_EMITTER, core, 3, 0.8, 0.4, 0.8, 0);
        Location floor = (focus == null ? core : focus).clone();
        FakeDestruction.paintCrater(floor, instance);
    }

    private void spawnSwallowed(Location core) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double yaw = random.nextDouble() * Math.PI * 2;
        double dist = 6.5 + random.nextDouble() * 6.5;
        double y = random.nextDouble(-0.4, 3.6);
        Location at = core.clone().add(Math.cos(yaw) * dist, y, Math.sin(yaw) * dist);
        Material mat = SUCK[random.nextInt(SUCK.length)];
        FallingBlock falling = FakeDestruction.spawnHeldDebris(at, instance, mat, false);
        if (falling != null) {
            swallowed.add(falling);
        }
    }

    private void drawVoidRing(World world, Location core, double radius, double progress) {
        int points = 28;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points + deathTicks * 0.12;
            Location p = core.clone().add(Math.cos(angle) * radius, (0.5 - progress) * 1.4, Math.sin(angle) * radius);
            world.spawnParticle(Particle.SQUID_INK, p, 1, 0, 0, 0, 0);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.REVERSE_PORTAL, p, 1, 0, 0, 0, 0);
            }
        }
    }

    private void pullPlayers(Location core, double strength) {
        World world = core.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (!instance.isCombatTarget(player)) {
                continue;
            }
            Vector to = core.toVector().subtract(player.getLocation().toVector());
            double dist = to.length();
            if (dist < 2.2 || dist > 16.0) {
                continue;
            }
            player.setVelocity(player.getVelocity().add(to.normalize().multiply(strength)));
        }
    }

    private void pushPlayers(Location core, double strength) {
        World world = core.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (!instance.isCombatTarget(player)) {
                continue;
            }
            Vector away = player.getLocation().toVector().subtract(core.toVector());
            double dist = away.length();
            if (dist < 0.4 || dist > 14.0) {
                continue;
            }
            player.setVelocity(player.getVelocity().add(away.normalize().multiply(strength).setY(0.28)));
        }
    }

    private void clearDebris() {
        for (FallingBlock falling : swallowed) {
            if (falling != null && falling.isValid()) {
                falling.remove();
            }
        }
        swallowed.clear();
    }
}
