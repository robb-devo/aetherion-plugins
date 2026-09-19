package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

public class SpectacleSkill extends AbstractBossSkill {

    private final String title;
    private final String subtitle;
    private final int lightning;
    private final Particle particle;
    private final int count;
    private final double radius;
    private final Sound sound;

    public SpectacleSkill(ConfigurationSection section) {
        super("SPECTACLE", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.title = section == null ? "&5T2" : section.getString("title", "&5T2");
        this.subtitle = section == null ? "" : section.getString("subtitle", "");
        this.lightning = section == null ? 6 : section.getInt("lightning", 6);
        this.particle = parseParticle(section == null ? "END_ROD" : section.getString("particle", "END_ROD"));
        this.count = section == null ? 60 : section.getInt("count", 60);
        this.radius = section == null ? 10.0 : section.getDouble("radius", 10.0);
        this.sound = parseSound(section == null ? "ENTITY_WITHER_SPAWN" : section.getString("sound", "ENTITY_WITHER_SPAWN"));
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.spectacle(context.getInstance(), title, subtitle, lightning, particle, count, radius, sound);
    }

    private static Particle parseParticle(String raw) {
        try {
            return Particle.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return Particle.END_ROD;
        }
    }

    private static Sound parseSound(String raw) {
        try {
            return Sound.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return Sound.ENTITY_WITHER_SPAWN;
        }
    }
}
