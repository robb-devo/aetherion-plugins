package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class SlamLeapSkill extends AbstractBossSkill {

    private final double upward;
    private final double range;
    private final double slamRadius;
    private final double slamDamage;

    public SlamLeapSkill(ConfigurationSection section) {
        super("SLAM_LEAP", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.upward = section == null ? 1.05 : Math.max(0.55, section.getDouble("upward", 1.05));
        this.range = section == null ? 22.0 : Math.max(6.0, section.getDouble("range", 22.0));
        this.slamRadius = section == null ? 4.5 : Math.max(1.5, section.getDouble("slam-radius", 4.5));
        this.slamDamage = section == null ? 8.0 : Math.max(1.0, section.getDouble("slam-damage", 8.0));
    }

    @Override
    public void execute(SkillContext context) {
        BossInstance instance = context.getInstance();
        LivingEntity entity = context.getEntity();
        if (instance == null || entity == null || instance.isSlamPending() || instance.isTransitioning()) {
            return;
        }

        Player target = resolveTarget(entity);
        Vector velocity;
        if (target == null) {
            velocity = new Vector(0, upward, 0);
        } else {
            Location from = entity.getLocation();
            Location to = target.getLocation();
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            Vector horizontal = dist < 0.05
                    ? new Vector(0, 0, 0)
                    : new Vector(dx / dist, 0, dz / dist).multiply(Math.min(2.15, 0.42 + dist * 0.14));
            velocity = horizontal;
            velocity.setY(upward + Math.min(0.4, dist * 0.015));
        }

        entity.setVelocity(velocity);
        entity.setFallDistance(0);
        instance.armSlam(slamRadius, slamDamage);

        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_HOGLIN_ATTACK, 1.05f, 0.55f);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 0.5f);
        entity.getWorld().spawnParticle(
                Particle.CLOUD,
                entity.getLocation().add(0, 0.15, 0),
                14,
                0.4,
                0.08,
                0.4,
                0.03
        );
    }

    private Player resolveTarget(LivingEntity boss) {
        if (boss instanceof Mob mob && mob.getTarget() instanceof Player player && isVulnerable(player)) {
            if (player.getLocation().distanceSquared(boss.getLocation()) <= range * range) {
                return player;
            }
        }

        Player nearest = null;
        double best = range * range;
        for (Player player : boss.getWorld().getPlayers()) {
            if (!isVulnerable(player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(boss.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null && boss instanceof Mob mob) {
            mob.setTarget(nearest);
        }
        return nearest;
    }

    private boolean isVulnerable(Player player) {
        return player != null
                && player.isValid()
                && !player.isDead()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
    }
}
