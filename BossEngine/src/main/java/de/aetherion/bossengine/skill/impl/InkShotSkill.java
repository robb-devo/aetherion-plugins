package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class InkShotSkill extends AbstractBossSkill {

    private final double speed;
    private final double spread;
    private final double damage;
    private final int count;
    private final int blindTicks;
    private final int slowTicks;
    private final int poisonTicks;

    public InkShotSkill(ConfigurationSection section) {
        super("INK_SHOT", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.speed = section == null ? 1.15 : Math.max(0.45, section.getDouble("speed", 1.15));
        this.spread = section == null ? 0.08 : Math.max(0, section.getDouble("spread", 0.08));
        this.damage = section == null ? 18 : Math.max(1.0, section.getDouble("damage", 18));
        this.count = section == null ? 1 : Math.max(1, section.getInt("count", 1));
        this.blindTicks = section == null ? 50 : Math.max(0, section.getInt("blind-ticks", 50));
        this.slowTicks = section == null ? 60 : Math.max(0, section.getInt("slow-ticks", 60));
        this.poisonTicks = section == null ? 0 : Math.max(0, section.getInt("poison-ticks", 0));
    }

    @Override
    public void execute(SkillContext context) {
        BossInstance instance = context.getInstance();
        LivingEntity boss = context.getEntity();
        LivingEntity target = resolveTarget(boss);
        if (instance == null || target == null) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Vector direction = target.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector());
            if (direction.lengthSquared() < 0.01) {
                continue;
            }
            direction.normalize();
            double extra = i == 0 ? spread : spread + 0.12;
            direction.add(new Vector(
                    random.nextDouble(-extra, extra),
                    random.nextDouble(-extra * 0.45, extra * 0.45),
                    random.nextDouble(-extra, extra)
            )).normalize();
            instance.launchInk(direction, speed, damage, blindTicks, slowTicks, poisonTicks);
        }

        Location look = boss.getLocation();
        look.setDirection(target.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector()));
        boss.setRotation(look.getYaw(), look.getPitch());
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
