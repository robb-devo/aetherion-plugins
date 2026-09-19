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

    private static final double PER_LEVEL = 0.01d;
    /** Soft band: big pets still grow, without erasing gear tiers. */
    private static final double SOFT_CAP = 48.0d;
    private static final double OVER_CAP = 0.28d;

    public double getScaledCoreValue(
            int level
    ) {
        return dampen(coreValue * levelMultiplier(level));
    }

    public double getScaledBonusStat(
            ItemCapability capability,
            int level
    ) {
        return dampen(getBonusStat(capability) * levelMultiplier(level));
    }

    private static double levelMultiplier(int level) {
        return 1.0 + (Math.max(0, level - 1) * PER_LEVEL);
    }

    private static double dampen(double value) {
        if (value <= SOFT_CAP) {
            return value;
        }
        return SOFT_CAP + ((value - SOFT_CAP) * OVER_CAP);
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