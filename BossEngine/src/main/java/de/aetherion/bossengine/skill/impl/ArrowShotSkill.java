package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.util.AttributeUtil;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ArrowShotSkill extends AbstractBossSkill {

    private final double speed;
    private final float spread;
    private final double damageOverride;
    private final boolean flame;
    private final Particle particle;

    public ArrowShotSkill(ConfigurationSection section) {
        super("ARROW_SHOT", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.speed = section == null ? 2.4 : Math.max(0.6, section.getDouble("speed", 2.4));
        this.spread = section == null ? 1.5f : (float) Math.max(0, section.getDouble("spread", 1.5));
        this.damageOverride = section == null ? 0 : section.getDouble("damage", 0);
        this.flame = section == null || section.getBoolean("flame", true);
        this.particle = parseParticle(section == null ? "FLAME" : section.getString("particle", "FLAME"));
    }

    @Override
    public void execute(SkillContext context) {
        LivingEntity boss = context.getEntity();
        LivingEntity target = resolveTarget(boss);
        if (target == null) {
            return;
        }

        Location from = boss.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        direction.normalize();

        Location look = boss.getLocation();
        look.setDirection(direction);
        boss.teleport(look);

        World world = from.getWorld();
        if (world == null) {
            return;
        }

        Arrow arrow = world.spawnArrow(from, direction, (float) speed, spread);
        arrow.setShooter(boss);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        double damage = damageOverride > 0
                ? context.getInstance().scaleDamage(damageOverride)
                : AttributeUtil.getBase(boss, AttributeUtil.attackDamage(), 8);
        arrow.setDamage(Math.max(2.0, damage * 0.4));
        if (flame) {
            arrow.setFireTicks(120);
            arrow.setVisualFire(true);
        }

        world.playSound(from, Sound.ENTITY_SKELETON_SHOOT, 1.15f, 1.15f);
        world.spawnParticle(particle, from, 6, 0.12, 0.12, 0.12, 0.01);
    }

    private LivingEntity resolveTarget(LivingEntity boss) {
        if (boss instanceof Mob mob && mob.getTarget() instanceof LivingEntity living && living.isValid()) {
            return living;
        }

        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : boss.getWorld().getPlayers()) {
            if (!player.isValid() || player.isDead()
                    || player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(boss.getLocation());
            if (distance < best && distance <= 48 * 48) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null && boss instanceof Mob mob) {
            mob.setTarget(nearest);
        }
        return nearest;
    }

    private static Particle parseParticle(String raw) {
        try {
            return Particle.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return Particle.FLAME;
        }
    }
}
