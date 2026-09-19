package de.aetherion.bossengine.skill;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.BossPhase;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Runtime context passed into every skill execution.
 */
public class SkillContext {

    private final BossInstance instance;
    private final SkillTrigger trigger;
    private final Player relatedPlayer;
    private final BossPhase phase;
    private final double damage;

    public SkillContext(
            BossInstance instance,
            SkillTrigger trigger,
            Player relatedPlayer,
            BossPhase phase,
            double damage
    ) {
        this.instance = instance;
        this.trigger = trigger;
        this.relatedPlayer = relatedPlayer;
        this.phase = phase;
        this.damage = damage;
    }

    public static SkillContext of(BossInstance instance, SkillTrigger trigger) {
        return new SkillContext(instance, trigger, null, instance.getCurrentPhase(), 0);
    }

    public BossInstance getInstance() {
        return instance;
    }

    public SkillTrigger getTrigger() {
        return trigger;
    }

    public Player getRelatedPlayer() {
        return relatedPlayer;
    }

    public BossPhase getPhase() {
        return phase;
    }

    public double getDamage() {
        return damage;
    }

    public LivingEntity getEntity() {
        return instance.getEntity();
    }
}
