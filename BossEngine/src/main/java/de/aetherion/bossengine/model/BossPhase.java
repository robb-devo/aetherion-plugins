package de.aetherion.bossengine.model;

import de.aetherion.bossengine.skill.AbstractBossSkill;

import java.util.List;

public class BossPhase {

    private final String id;
    private final double healthPercent;
    private final String displayName;
    private final BossAttributes attributes;
    private final List<AbstractBossSkill> skills;
    private final PhaseTransition transition;

    public BossPhase(
            String id,
            double healthPercent,
            String displayName,
            BossAttributes attributes,
            List<AbstractBossSkill> skills,
            PhaseTransition transition
    ) {
        this.id = id;
        this.healthPercent = healthPercent;
        this.displayName = displayName;
        this.attributes = attributes;
        this.skills = List.copyOf(skills == null ? List.of() : skills);
        this.transition = transition;
    }

    public PhaseTransition getTransition() {
        return transition;
    }

    public String getId() {
        return id;
    }

    public double getHealthPercent() {
        return healthPercent;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BossAttributes getAttributes() {
        return attributes;
    }

    public List<AbstractBossSkill> getSkills() {
        return skills;
    }
}
