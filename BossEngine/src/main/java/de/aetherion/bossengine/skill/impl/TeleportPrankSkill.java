package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.configuration.ConfigurationSection;

public class TeleportPrankSkill extends AbstractBossSkill {

    private final double range;

    public TeleportPrankSkill(ConfigurationSection section) {
        super("TELEPORT_PRANK", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.range = section == null ? 22.0 : section.getDouble("range", 22.0);
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.teleportPrank(context.getInstance(), range);
    }
}
