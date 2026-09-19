package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PotionSkill extends AbstractBossSkill {

    private final PotionEffectType effectType;
    private final int amplifier;
    private final int durationTicks;
    private final boolean ambient;
    private final boolean particles;

    public PotionSkill(ConfigurationSection section) {
        super("POTION", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.effectType = parseEffect(section == null ? "INCREASE_DAMAGE" : section.getString("effect", "INCREASE_DAMAGE"));
        this.amplifier = section == null ? 0 : Math.max(0, section.getInt("amplifier", 0));
        this.durationTicks = section == null ? 200 : Math.max(1, section.getInt("duration-ticks", 200));
        this.ambient = section != null && section.getBoolean("ambient", true);
        this.particles = section == null || section.getBoolean("particles", true);
    }

    @Override
    public void execute(SkillContext context) {
        if (effectType == null) {
            return;
        }
        LivingEntity entity = context.getEntity();
        entity.addPotionEffect(new PotionEffect(
                effectType,
                durationTicks,
                amplifier,
                ambient,
                particles,
                true
        ));
    }

    private static PotionEffectType parseEffect(String raw) {
        if (raw == null) {
            return PotionEffectType.STRENGTH;
        }
        String key = raw.toUpperCase();
        if (key.equals("INCREASE_DAMAGE") || key.equals("STRENGTH")) {
            return PotionEffectType.STRENGTH;
        }
        PotionEffectType type = PotionEffectType.getByName(raw);
        if (type != null) {
            return type;
        }
        return PotionEffectType.STRENGTH;
    }
}
