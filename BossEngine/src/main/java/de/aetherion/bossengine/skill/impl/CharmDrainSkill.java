package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.configuration.ConfigurationSection;

public class CharmDrainSkill extends AbstractBossSkill {

    private final double range;
    private final int lifetimeTicks;

    public CharmDrainSkill(ConfigurationSection section) {
        super("CHARM_DRAIN", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.range = section == null ? 22.0 : section.getDouble("range", 22.0);
        this.lifetimeTicks = section == null ? 140 : section.getInt("lifetime-ticks", 140);
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.charmDrain(context.getInstance(), range, lifetimeTicks);
    }
}
