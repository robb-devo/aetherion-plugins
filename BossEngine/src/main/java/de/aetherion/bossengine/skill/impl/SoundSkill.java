package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class SoundSkill extends AbstractBossSkill {

    private final Sound sound;
    private final float volume;
    private final float pitch;

    public SoundSkill(ConfigurationSection section) {
        super("SOUND", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.sound = parseSound(section == null ? "ENTITY_WITHER_SPAWN" : section.getString("sound", "ENTITY_WITHER_SPAWN"));
        this.volume = section == null ? 1.0f : (float) section.getDouble("volume", 1.0);
        this.pitch = section == null ? 1.0f : (float) section.getDouble("pitch", 1.0);
    }

    @Override
    public void execute(SkillContext context) {
        LivingEntity entity = context.getEntity();
        entity.getWorld().playSound(entity.getLocation(), sound, volume, pitch);
    }

    private static Sound parseSound(String raw) {
        try {
            return Sound.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return Sound.ENTITY_WITHER_SPAWN;
        }
    }
}
