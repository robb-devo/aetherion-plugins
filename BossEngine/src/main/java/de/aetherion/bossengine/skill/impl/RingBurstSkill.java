package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

public class RingBurstSkill extends AbstractBossSkill {

    private final int waves;
    private final double step;
    private final double damage;
    private final Particle particle;

    public RingBurstSkill(ConfigurationSection section) {
        super("RING_BURST", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.waves = section == null ? 4 : section.getInt("waves", 4);
        this.step = section == null ? 3.2 : section.getDouble("step", 3.2);
        this.damage = section == null ? 48.0 : section.getDouble("damage", 48.0);
        this.particle = parse(section == null ? "SWEEP_ATTACK" : section.getString("particle", "SWEEP_ATTACK"));
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.ringBurst(context.getInstance(), waves, step, damage, particle);
    }

    private static Particle parse(String raw) {
        try {
            return Particle.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return Particle.SWEEP_ATTACK;
        }
    }
}
