package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.configuration.ConfigurationSection;

public class TaxCollectorSkill extends AbstractBossSkill {

    private final double range;
    private final double bonusPower;

    public TaxCollectorSkill(ConfigurationSection section) {
        super("TAX_COLLECTOR", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.range = section == null ? 28.0 : section.getDouble("range", 28.0);
        this.bonusPower = section == null ? 88.0 : section.getDouble("bonus-power", 88.0);
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.tax(context.getInstance(), range, bonusPower);
    }
}
