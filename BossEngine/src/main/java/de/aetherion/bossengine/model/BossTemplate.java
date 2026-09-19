package de.aetherion.bossengine.model;

import de.aetherion.bossengine.skill.AbstractBossSkill;

import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Immutable YAML definition of a boss. Never mutate this at runtime —
 * spawn a {@link de.aetherion.bossengine.instance.BossInstance} instead.
 */
public class BossTemplate {

    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final String modelEngineId;
    private final BossAttributes attributes;
    private final BossEquipment equipment;
    private final BossOptions options;
    private final SpawnCondition conditions;
    private final List<BossPhase> phases;
    private final List<AbstractBossSkill> skills;
    private final BossLootTable lootTable;

    public BossTemplate(
            String id,
            String displayName,
            EntityType entityType,
            String modelEngineId,
            BossAttributes attributes,
            BossEquipment equipment,
            BossOptions options,
            SpawnCondition conditions,
            List<BossPhase> phases,
            List<AbstractBossSkill> skills,
            BossLootTable lootTable
    ) {
        this.id = id;
        this.displayName = displayName;
        this.entityType = entityType;
        this.modelEngineId = modelEngineId == null ? "" : modelEngineId;
        this.attributes = attributes == null ? BossAttributes.defaults() : attributes;
        this.equipment = equipment == null ? BossEquipment.empty() : equipment;
        this.options = options == null ? BossOptions.defaults() : options;
        this.conditions = conditions == null ? SpawnCondition.defaults() : conditions;
        this.phases = List.copyOf(sorted(phases));
        this.skills = List.copyOf(skills == null ? List.of() : skills);
        this.lootTable = lootTable == null ? BossLootTable.empty() : lootTable;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getModelEngineId() {
        return modelEngineId;
    }

    public boolean hasModelEngine() {
        return modelEngineId != null && !modelEngineId.isBlank();
    }

    public BossAttributes getAttributes() {
        return attributes;
    }

    public BossEquipment getEquipment() {
        return equipment;
    }

    public BossOptions getOptions() {
        return options;
    }

    public SpawnCondition getConditions() {
        return conditions;
    }

    public List<BossPhase> getPhases() {
        return phases;
    }

    public List<AbstractBossSkill> getSkills() {
        return skills;
    }

    public BossLootTable getLootTable() {
        return lootTable;
    }

    /**
     * Active phase for the current health percentage, or empty while still
     * above every configured threshold.
     */
    public Optional<BossPhase> phaseForHealth(double healthPercent) {
        BossPhase match = null;
        for (BossPhase phase : phases) {
            if (phase.getHealthPercent() >= healthPercent) {
                if (match == null || phase.getHealthPercent() < match.getHealthPercent()) {
                    match = phase;
                }
            }
        }
        return Optional.ofNullable(match);
    }

    public List<AbstractBossSkill> allSkills() {
        List<AbstractBossSkill> all = new ArrayList<>(skills);
        for (BossPhase phase : phases) {
            all.addAll(phase.getSkills());
        }
        return all;
    }

    private static List<BossPhase> sorted(List<BossPhase> phases) {
        if (phases == null || phases.isEmpty()) {
            return List.of();
        }
        List<BossPhase> copy = new ArrayList<>(phases);
        copy.sort(Comparator.comparingDouble(BossPhase::getHealthPercent).reversed());
        return copy;
    }
}
