package de.aetherion.aethermobs.model;

import de.aetherion.items.model.ItemCapability;

import java.util.EnumMap;
import java.util.Map;

public class PetStats {

    private final ItemCapability coreStat;
    private double coreValue;

    private final Map<ItemCapability, Double> bonusStats;

    public PetStats(
            ItemCapability coreStat
    ) {
        this.coreStat = coreStat;
        this.coreValue = 0.0;
        this.bonusStats = new EnumMap<>(ItemCapability.class);
    }

    public ItemCapability getCoreStat() {
        return coreStat;
    }

    public double getCoreValue() {
        return coreValue;
    }

    public void setCoreValue(
            double coreValue
    ) {
        this.coreValue = coreValue;
    }

    public void setBonusStat(
            ItemCapability capability,
            double value
    ) {
        bonusStats.put(
                capability,
                value
        );
    }

    public double getBonusStat(
            ItemCapability capability
    ) {
        return bonusStats.getOrDefault(
                capability,
                0.0
        );
    }

    public Map<ItemCapability, Double> getBonusStats() {
        return bonusStats;
    }

    public double getTotalStat(
            ItemCapability capability
    ) {
        double total = 0.0;

        if (coreStat == capability) {
            total += coreValue;
        }

        total += getBonusStat(capability);

        return total;
    }

    public double getScaledCoreValue(
            int level
    ) {
        return PetBalance.dampen(coreStat, coreValue * PetBalance.levelMultiplier(level));
    }

    public double getScaledBonusStat(
            ItemCapability capability,
            int level
    ) {
        return PetBalance.dampen(capability, getBonusStat(capability) * PetBalance.levelMultiplier(level));
    }

    public double getScaledTotalStat(
            ItemCapability capability,
            int level
    ) {
        double total = 0.0;

        if (coreStat == capability) {
            total += getScaledCoreValue(level);
        }

        total += getScaledBonusStat(
                capability,
                level
        );

        return total;
    }
}