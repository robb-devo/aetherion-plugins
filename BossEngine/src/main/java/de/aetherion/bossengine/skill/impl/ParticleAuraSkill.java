package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class ParticleAuraSkill extends AbstractBossSkill {

    private final Particle particle;
    private final int count;
    private final double radius;
    private final double extra;

    public ParticleAuraSkill(ConfigurationSection section) {
        super("PARTICLE_AURA", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.particle = parseParticle(section == null ? "PORTAL" : section.getString("particle", "PORTAL"));
        this.count = section == null ? 12 : Math.max(1, section.getInt("count", 12));
        this.radius = section == null ? 1.5 : section.getDouble("radius", 1.5);
        this.extra = section == null ? 0.05 : section.getDouble("extra", 0.05);
    }

    @Override
    public void execute(SkillContext context) {
        LivingEntity entity = context.getEntity();
        if (de.aetherion.bossengine.instance.rite.RiteKit.owns(context.getInstance())) {
            return; // the rite kits dress their bosses themselves
        }
        // Presence, not fog: a few motes in the boss's colour rising off it.
        if (de.aetherion.core.api.Vfx.motes(entity.getLocation().add(0, entity.getHeight() * 0.35, 0), de.aetherion.bossengine.fx.ShapeFx.tint(particle),
                Math.max(1, Math.min(3, count / 4)), Math.max(0.3, radius * 0.7), new org.bukkit.util.Vector(0, 1.4, 0), 24)) {
            return;
        }
        entity.getWorld().spawnParticle(
                particle,
                entity.getLocation().add(0, entity.getHeight() * 0.5, 0),
                count,
                radius,
                entity.getHeight() * 0.3,
                radius,
                extra
        );
    }

    private static Particle parseParticle(String raw) {
        try {
            return Particle.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return Particle.PORTAL;
        }
    }
}
