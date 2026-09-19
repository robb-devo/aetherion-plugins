package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.configuration.ConfigurationSection;

public class BurrowStrikeSkill extends AbstractBossSkill {

    private final int hideTicks;
    private final double launchPower;

    public BurrowStrikeSkill(ConfigurationSection section) {
        super("BURROW_STRIKE", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.hideTicks = section == null ? 100 : section.getInt("hide-ticks", 100);
        this.launchPower = section == null ? 70.0 : section.getDouble("launch-power", 70.0);
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.burrow(context.getInstance(), hideTicks, launchPower);
    }
}
