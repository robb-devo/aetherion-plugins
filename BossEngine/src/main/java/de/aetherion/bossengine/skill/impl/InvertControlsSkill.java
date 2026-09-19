package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.configuration.ConfigurationSection;

public class InvertControlsSkill extends AbstractBossSkill {

    private final double range;
    private final int durationTicks;

    public InvertControlsSkill(ConfigurationSection section) {
        super("INVERT_CONTROLS", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.range = section == null ? 18.0 : section.getDouble("range", 18.0);
        this.durationTicks = section == null ? 80 : section.getInt("duration-ticks", 80);
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.invert(context.getEntity(), range, durationTicks);
    }
}
