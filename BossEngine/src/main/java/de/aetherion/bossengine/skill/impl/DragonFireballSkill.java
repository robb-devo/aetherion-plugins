package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.util.BossKeys;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class DragonFireballSkill extends AbstractBossSkill {

    private final double speed;
    private final double damage;
    private final double spread;

    public DragonFireballSkill(ConfigurationSection section) {
        super("DRAGON_FIREBALL", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.speed = section == null ? 1.15 : Math.max(0.5, section.getDouble("speed", 1.15));
        this.damage = section == null ? 28 : Math.max(4.0, section.getDouble("damage", 28));
        this.spread = section == null ? 0.04 : Math.max(0, section.getDouble("spread", 0.04));
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
        if (spread > 0) {
            direction.add(new Vector(
                    (Math.random() - 0.5) * spread,
                    (Math.random() - 0.5) * spread * 0.4,
                    (Math.random() - 0.5) * spread
            )).normalize();
        }

        World world = from.getWorld();
        if (world == null) {
            return;
        }
        DragonFireball ball = world.spawn(from.add(direction.clone().multiply(2.2)), DragonFireball.class);
        ball.setShooter(boss);
        ball.setDirection(direction);
        ball.setVelocity(direction.multiply(speed));
        ball.setYield(0f);
        ball.setIsIncendiary(false);
        BossKeys keys = context.getInstance().getKeys();
        ball.getPersistentDataContainer().set(keys.eggDamageKey(), PersistentDataType.DOUBLE, context.getInstance().scaleDamage(damage));

        world.playSound(from, Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.85f, 0.85f);
        world.spawnParticle(Particle.DRAGON_BREATH, from, 8, 0.25, 0.25, 0.25, 0.02);
    }

    private LivingEntity resolveTarget(LivingEntity boss) {
        if (boss instanceof Mob mob && mob.getTarget() instanceof LivingEntity living && living.isValid()) {
            return living;
        }
        Player nearest = null;
        double best = 48 * 48;
        for (Player player : boss.getWorld().getPlayers()) {
            if (!player.isValid() || player.isDead()
                    || player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(boss.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }
}
