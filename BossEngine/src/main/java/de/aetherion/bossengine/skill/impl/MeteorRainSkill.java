package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.t2.T2Mechanics;

import org.bukkit.configuration.ConfigurationSection;

public class MeteorRainSkill extends AbstractBossSkill {

    private final int amount;
    private final double damage;
    private final double scatter;
    private final String style;

    public MeteorRainSkill(ConfigurationSection section) {
        super("METEOR_RAIN", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.amount = section == null ? 7 : section.getInt("amount", 7);
        this.damage = section == null ? 62.0 : section.getDouble("damage", 62.0);
        this.scatter = section == null ? 9.0 : section.getDouble("scatter", 9.0);
        this.style = section == null ? "BURST" : section.getString("style", "BURST");
    }

    @Override
    public void execute(SkillContext context) {
        T2Mechanics.meteorRain(context.getInstance(), amount, damage, scatter, style);
    }
}
