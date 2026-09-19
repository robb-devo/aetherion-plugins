package de.aetherion.bossengine.manager;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.BossPhase;
import de.aetherion.bossengine.skill.AbstractBossSkill;
import de.aetherion.bossengine.skill.SkillContext;
import de.aetherion.bossengine.skill.SkillRegistry;
import de.aetherion.bossengine.skill.SkillTrigger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SkillManager {

    private final SkillRegistry registry;

    public SkillManager(SkillRegistry registry) {
        this.registry = registry;
    }

    public SkillRegistry getRegistry() {
        return registry;
    }

    public void execute(BossInstance instance, SkillTrigger trigger) {
        execute(instance, trigger, null, 0);
    }

    public void execute(BossInstance instance, SkillTrigger trigger, Player player, double damage) {
        if (instance == null) {
            return;
        }
        if (trigger != SkillTrigger.ON_DEATH && !instance.isAlive()) {
            return;
        }
        if (trigger == SkillTrigger.ON_TIMER && instance.isDamageBlocked()) {
            return;
        }

        SkillContext context = new SkillContext(
                instance,
                trigger,
                player,
                instance.getCurrentPhase(),
                damage
        );

        for (AbstractBossSkill skill : relevantSkills(instance, trigger)) {
            if (skill.getTrigger() != trigger) {
                continue;
            }
            if (!instance.canCast(skill) && trigger != SkillTrigger.ON_SPAWN && trigger != SkillTrigger.ON_PHASE) {
                continue;
            }
            if (!skill.canExecute(context) && trigger != SkillTrigger.ON_DEATH) {
                continue;
            }
            skill.execute(context);
            instance.markCast(skill);
        }
    }

    public List<AbstractBossSkill> parseList(List<Map<?, ?>> rawList) {
        List<AbstractBossSkill> skills = new ArrayList<>();
        if (rawList == null) {
            return skills;
        }

        int index = 0;
        for (Map<?, ?> raw : rawList) {
            ConfigurationSection section = toSection(raw, "skill-" + index++);
            AbstractBossSkill skill = registry.create(section);
            if (skill != null) {
                skills.add(skill);
            }
        }
        return skills;
    }

    private List<AbstractBossSkill> relevantSkills(BossInstance instance, SkillTrigger trigger) {
        BossPhase phase = instance.getCurrentPhase();
        if (trigger == SkillTrigger.ON_PHASE) {
            return phase == null ? List.of() : new ArrayList<>(phase.getSkills());
        }

        List<AbstractBossSkill> skills = new ArrayList<>(instance.getTemplate().getSkills());
        if (phase != null) {
            skills.addAll(phase.getSkills());
        }
        return skills;
    }

    @SuppressWarnings("unchecked")
    private ConfigurationSection toSection(Map<?, ?> raw, String name) {
        MemoryConfiguration memory = new MemoryConfiguration();
        ConfigurationSection section = memory.createSection(name);
        copy(section, raw);
        return section;
    }

    private void copy(ConfigurationSection target, Map<?, ?> raw) {
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                copy(target.createSection(key), nested);
            } else {
                target.set(key, value);
            }
        }
    }
}
