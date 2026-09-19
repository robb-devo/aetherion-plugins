package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class ActionBarSkill extends AbstractBossSkill {

    private final String message;
    private final double radius;

    public ActionBarSkill(ConfigurationSection section) {
        super("ACTION_BAR", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.message = section == null ? "" : section.getString("message", "");
        this.radius = section == null ? 32 : section.getDouble("radius", 32);
    }

    @Override
    public void execute(SkillContext context) {
        if (message.isBlank()) {
            return;
        }

        String rendered = context.getInstance().replacePlaceholders(message);

        if (context.getRelatedPlayer() != null) {
            context.getRelatedPlayer().sendActionBar(TextUtil.component(rendered));
            return;
        }

        for (Player player : context.getEntity().getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(context.getEntity().getLocation()) <= radius * radius) {
                player.sendActionBar(TextUtil.component(rendered));
            }
        }
    }
}
