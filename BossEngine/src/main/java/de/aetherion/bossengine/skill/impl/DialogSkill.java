package de.aetherion.bossengine.skill.impl;

import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class DialogSkill extends AbstractBossSkill {

    private final String message;
    private final boolean broadcast;
    private final double radius;
    private final int delayTicks;

    public DialogSkill(ConfigurationSection section) {
        super("DIALOG", triggerOf(section), cooldownOf(section), intervalOf(section));
        this.message = section == null ? "" : section.getString("message", "");
        this.broadcast = section != null && section.getBoolean("broadcast", true);
        this.radius = section == null ? 48 : section.getDouble("radius", 48);
        this.delayTicks = section == null ? 0 : Math.max(0, section.getInt("delay-ticks", 0));
    }

    @Override
    public void execute(SkillContext context) {
        if (message.isBlank()) {
            return;
        }
        if (getTrigger() == de.aetherion.bossengine.skill.SkillTrigger.ON_SPAWN
                && context.getInstance().getTicksAlive() > 10) {
            return;
        }
        if (getTrigger() == de.aetherion.bossengine.skill.SkillTrigger.ON_SPAWN
                && !context.getInstance().tryGlobalSpawnAnnounce()) {
            return;
        }
        if (!context.getInstance().tryAnnounce(getTrigger() + ":" + message)) {
            return;
        }
        if (delayTicks <= 0) {
            send(context);
            return;
        }
        Plugin plugin = context.getInstance().getPlugin();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (context.getInstance().isAlive() || context.getTrigger() == de.aetherion.bossengine.skill.SkillTrigger.ON_DEATH) {
                send(context);
            }
        }, delayTicks);
    }

    private void send(SkillContext context) {
        String rendered = context.getInstance().replacePlaceholders(message);
        boolean worldwide = broadcast
                && getTrigger() != de.aetherion.bossengine.skill.SkillTrigger.ON_SPAWN;
        if (worldwide) {
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(TextUtil.component(rendered)));
            return;
        }
        if (context.getEntity() == null || context.getEntity().getWorld() == null) {
            return;
        }
        double reach = Math.max(32.0, radius);
        for (Player player : context.getEntity().getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(context.getEntity().getLocation()) <= reach * reach) {
                player.sendMessage(TextUtil.component(rendered));
            }
        }
    }
}
