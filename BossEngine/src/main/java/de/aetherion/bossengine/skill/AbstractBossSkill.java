package de.aetherion.bossengine.skill;

import org.bukkit.configuration.ConfigurationSection;

/**
 * New skill types: extend this class, then register a factory in
 * {@link SkillRegistry#registerDefaults()}.
 */
public abstract class AbstractBossSkill {

    private final String type;
    private final SkillTrigger trigger;
    private final int cooldownTicks;
    private final int intervalTicks;

    protected AbstractBossSkill(
            String type,
            SkillTrigger trigger,
            int cooldownTicks,
            int intervalTicks
    ) {
        this.type = type;
        this.trigger = trigger == null ? SkillTrigger.ON_TIMER : trigger;
        this.cooldownTicks = Math.max(0, cooldownTicks);
        this.intervalTicks = Math.max(1, intervalTicks);
    }

    public String getType() {
        return type;
    }

    public SkillTrigger getTrigger() {
        return trigger;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public int getIntervalTicks() {
        return intervalTicks;
    }

    public boolean canExecute(SkillContext context) {
        return context != null && context.getEntity() != null && context.getEntity().isValid();
    }

    public abstract void execute(SkillContext context);

    protected static SkillTrigger triggerOf(ConfigurationSection section) {
        if (section == null) {
            return SkillTrigger.ON_TIMER;
        }
        try {
            return SkillTrigger.valueOf(section.getString("trigger", "ON_TIMER").toUpperCase());
        } catch (IllegalArgumentException exception) {
            return SkillTrigger.ON_TIMER;
        }
    }

    protected static int cooldownOf(ConfigurationSection section) {
        return section == null ? 0 : section.getInt("cooldown-ticks", 0);
    }

    protected static int intervalOf(ConfigurationSection section) {
        return section == null ? 20 : section.getInt("interval-ticks", 20);
    }
}
