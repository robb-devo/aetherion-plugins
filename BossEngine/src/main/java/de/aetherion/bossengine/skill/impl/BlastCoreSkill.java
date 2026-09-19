package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.configuration.ConfigurationSection;

public class BlastCoreSkill extends AbstractBossSkill {

    private final int amount;
    private final int fuseTicks;
    private final double explodePower;

    public BlastCoreSkill(ConfigurationSection section) {
        super("BLAST_CORES", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.amount = section == null ? 3 : section.getInt("amount", 3);
        this.fuseTicks = section == null ? 100 : section.getInt("fuse-ticks", 100);
        this.explodePower = section == null ? 68.0 : section.getDouble("explode-power", 68.0);
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.blastCores(context.getInstance(), amount, fuseTicks, explodePower);
    }
}
