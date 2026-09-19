package de.aetherion.bossengine.skill;

import org.bukkit.configuration.ConfigurationSection;

@FunctionalInterface
public interface SkillFactory {

    AbstractBossSkill create(ConfigurationSection section);
}
