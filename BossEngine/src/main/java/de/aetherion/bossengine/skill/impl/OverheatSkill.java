package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.configuration.ConfigurationSection;

public class OverheatSkill extends AbstractBossSkill {

    private final int durationTicks;

    public OverheatSkill(ConfigurationSection section) {
        super("OVERHEAT", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.durationTicks = section == null ? 120 : section.getInt("duration-ticks", 120);
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.overheat(context.getInstance(), durationTicks);
    }
}
