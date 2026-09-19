package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class LeapSkill extends AbstractBossSkill {

    private final double upward;
    private final double horizontal;

    public LeapSkill(ConfigurationSection section) {
        super("LEAP", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.upward = section == null ? 0.7 : section.getDouble("upward", 0.7);
        this.horizontal = section == null ? 0.55 : section.getDouble("horizontal", 0.55);
    }

    @Override
    public void execute(SkillContext context) {
        LivingEntity entity = context.getEntity();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector velocity = new Vector(
                random.nextDouble(-horizontal, horizontal),
                Math.max(0.35, upward),
                random.nextDouble(-horizontal, horizontal)
        );
        entity.setVelocity(velocity);
        entity.setFallDistance(0);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1.1f, 0.7f);
        entity.getWorld().spawnParticle(
                Particle.CLOUD,
                entity.getLocation().add(0, 0.2, 0),
                12,
                0.35,
                0.1,
                0.35,
                0.02
        );
    }
}
