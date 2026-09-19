package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.util.AttributeUtil;
import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Egg;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class EggShotSkill extends AbstractBossSkill {

    private final double speed;
    private final double spread;
    private final double damageOverride;
    private final double knockback;

    public EggShotSkill(ConfigurationSection section) {
        super("EGG_SHOT", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.speed = section == null ? 1.6 : Math.max(0.5, section.getDouble("speed", 1.6));
        this.spread = section == null ? 0.08 : Math.max(0, section.getDouble("spread", 0.08));
        this.damageOverride = section == null ? 0 : section.getDouble("damage", 0);
        this.knockback = section == null ? 0.28 : Math.max(0.0, section.getDouble("knockback", 0.28));
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
        ThreadLocalRandom random = ThreadLocalRandom.current();
        direction.add(new Vector(
                random.nextDouble(-spread, spread),
                random.nextDouble(-spread * 0.4, spread * 0.4),
                random.nextDouble(-spread, spread)
        )).normalize();

        Location look = boss.getLocation();
        look.setDirection(direction);
        boss.teleport(look);

        Egg egg = boss.launchProjectile(Egg.class, direction.multiply(speed));
        egg.setShooter(boss);
        double damage = damageOverride > 0
                ? context.getInstance().scaleDamage(damageOverride)
                : AttributeUtil.getBase(boss, AttributeUtil.attackDamage(), 8);
        BossKeys keys = context.getInstance().getKeys();
        egg.getPersistentDataContainer().set(keys.eggDamageKey(), PersistentDataType.DOUBLE, damage);
        egg.getPersistentDataContainer().set(keys.eggKnockbackKey(), PersistentDataType.DOUBLE, knockback);

        from.getWorld().playSound(from, Sound.ENTITY_EGG_THROW, 1.1f, 0.85f);
        from.getWorld().spawnParticle(Particle.CLOUD, from, 4, 0.1, 0.1, 0.1, 0.01);
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
            if (distance < best && distance <= 40 * 40) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null && boss instanceof Mob mob) {
            mob.setTarget(nearest);
        }
        return nearest;
    }
}
