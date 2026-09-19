package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;

import org.bukkit.configuration.ConfigurationSection;

public class BoilingWaterSkill extends AbstractBossSkill {

    private final int amount;
    private final double radius;
    private final double spread;
    private final double percentPerSecond;

    public BoilingWaterSkill(ConfigurationSection section) {
        super("BOILING_WATER", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.amount = section == null ? 5 : Math.max(1, section.getInt("amount", 5));
        this.radius = section == null ? 2.4 : Math.max(1.2, section.getDouble("radius", 2.4));
        this.spread = section == null ? 10.0 : Math.max(4.0, section.getDouble("spread", 10.0));
        this.percentPerSecond = section == null ? 4.0 : Math.max(1.0, section.getDouble("percent-per-second", 4.0));
    }

    @Override
    public void execute(SkillContext context) {
        BossInstance instance = context.getInstance();
        if (instance == null) {
            return;
        }
        instance.spawnBoilingClusters(amount, radius, spread, percentPerSecond);
    }
}
