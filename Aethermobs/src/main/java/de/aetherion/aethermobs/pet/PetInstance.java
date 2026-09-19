package de.aetherion.aethermobs.pet;

import de.aetherion.aethermobs.model.PetStats;
import de.aetherion.aethermobs.model.PetVariant;
import de.aetherion.items.model.Rarity;

import java.util.UUID;

public class PetInstance {

    public static final int MAX_LEVEL = 100;
    public static final int AETHERED_MAX_LEVEL = 200;

    private final UUID uniqueId;
    private final PetDefinition definition;

    private Rarity rarity;
    private PetVariant variant;

    private int level;
    private long experience;

    private PetStats stats;

    public PetInstance(
            PetDefinition definition,
            Rarity rarity,
            PetVariant variant
    ) {
        this.uniqueId = UUID.randomUUID();
        this.definition = definition;
        this.rarity = rarity;
        this.variant = variant;
        this.level = 1;
        this.experience = 0;
        this.stats = new PetStats(
                definition.getCoreStat()
        );
    }

    public PetInstance(
            UUID uniqueId,
            PetDefinition definition,
            Rarity rarity,
            PetVariant variant,
            int level,
            long experience,
            PetStats stats
    ) {
        this.uniqueId = uniqueId;
        this.definition = definition;
        this.rarity = rarity;
        this.variant = variant;
        this.level = level;
        this.experience = experience;
        this.stats = stats;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public PetDefinition getDefinition() {
        return definition;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public void setRarity(
            Rarity rarity
    ) {
        this.rarity = rarity;
    }

    public int getMaxLevel() {
        return rarity == Rarity.AETHERED ? AETHERED_MAX_LEVEL : MAX_LEVEL;
    }

    public boolean isDragon() {
        String id = definition == null ? null : definition.getId();
        if (id == null || id.isBlank()) {
            return false;
        }
        String lower = id.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith("_dragon") || lower.equals("aetherion");
    }

    public boolean ascendToAethered() {
        if (!isDragon() || rarity != Rarity.MYTHIC) {
            return false;
        }
        rarity = Rarity.AETHERED;
        if (stats != null) {
            stats.setCoreValue(stats.getCoreValue() * 1.25d);
            for (var entry : new java.util.ArrayList<>(stats.getBonusStats().entrySet())) {
                stats.setBonusStat(entry.getKey(), entry.getValue() * 1.20d);
            }
        }
        return true;
    }

    public PetVariant getVariant() {
        return variant;
    }

    public void setVariant(
            PetVariant variant
    ) {
        this.variant = variant;
    }

    public int getLevel() {
        return level;
    }

    public long getExperience() {
        return experience;
    }

    public PetStats getStats() {
        return stats;
    }

    public void setStats(
            PetStats stats
    ) {
        this.stats = stats;
    }

    public void addExperience(
            long amount
    ) {
        if (amount <= 0) {
            return;
        }

        experience += amount;

        while (
                level < getMaxLevel() &&
                        experience >= getRequiredExperience(level)
        ) {
            experience -= getRequiredExperience(level);
            level++;
        }
    }

    public long getRequiredExperience(
            int level
    ) {
        if (level >= getMaxLevel()) {
            return Long.MAX_VALUE;
        }
        int current = Math.max(1, level);
        return 36L + (16L * current) + ((long) current * current / 2L);
    }

    public double getExperienceProgress() {
        if (level >= getMaxLevel()) {
            return 1.0;
        }

        long required =
                getRequiredExperience(level);

        if (required <= 0 || required == Long.MAX_VALUE) {
            return 1.0;
        }

        return Math.min(
                (double) experience / required,
                1.0
        );
    }
}