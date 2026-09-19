package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.configuration.ConfigurationSection;

public class VacuumPullSkill extends AbstractBossSkill {

    private final double range;
    private final double aoePower;
    private final int delayTicks;

    public VacuumPullSkill(ConfigurationSection section) {
        super("VACUUM_PULL", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.range = section == null ? 20.0 : section.getDouble("range", 20.0);
        this.aoePower = section == null ? 72.0 : section.getDouble("aoe-power", 72.0);
        this.delayTicks = section == null ? 16 : section.getInt("delay-ticks", 16);
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.vacuum(context.getInstance(), range, aoePower, delayTicks);
    }
}
