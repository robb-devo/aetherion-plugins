package de.aetherion.aethermobs.pet;

import de.aetherion.items.model.Rarity;

public class CatchSphere {

    private final String id;
    private final String displayName;
    private final long cooldownMillis;
    private final boolean infinite;
    private final Double flatChance;
    private final double[] rarityChances;

    private CatchSphere(
            String id,
            String displayName,
            long cooldownMillis,
            boolean infinite,
            Double flatChance,
            double[] rarityChances
    ) {
        this.id = id;
        this.displayName = displayName;
        this.cooldownMillis = cooldownMillis;
        this.infinite = infinite;
        this.flatChance = flatChance;
        this.rarityChances = rarityChances;
    }

    public static CatchSphere rarityTable(
            String id,
            String displayName,
            long cooldownMillis,
            double common,
            double uncommon,
            double rare,
            double epic,
            double legendary,
            double mythic
    ) {
        return new CatchSphere(
                id,
                displayName,
                cooldownMillis,
                false,
                null,
                new double[] { common, uncommon, rare, epic, legendary, mythic, 0.0 }
        );
    }

    public static CatchSphere flat(
            String id,
            String displayName,
            long cooldownMillis,
            boolean infinite,
            double chance
    ) {
        return new CatchSphere(
                id,
                displayName,
                cooldownMillis,
                infinite,
                chance,
                null
        );
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }

    public boolean isInfinite() {
        return infinite;
    }

    public double getCatchChance() {
        if (flatChance != null) {
            return flatChance;
        }

        return rarityChances[Rarity.COMMON.ordinal()];
    }

    public double getCatchChance(Rarity rarity) {
        if (flatChance != null) {
            return flatChance;
        }

        if (rarity == null) {
            return 0.0;
        }

        int index = rarity.ordinal();

        if (index < 0 || index >= rarityChances.length) {
            return 0.0;
        }

        return rarityChances[index];
    }

    public boolean hasRarityTable() {
        return rarityChances != null;
    }
}
