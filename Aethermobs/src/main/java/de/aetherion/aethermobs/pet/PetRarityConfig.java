package de.aetherion.aethermobs.pet;

import de.aetherion.items.model.Rarity;

public class PetRarityConfig {

    private final Rarity rarity;

    private final double coreMin;
    private final double coreMax;

    private final double rarityWeight;

    public PetRarityConfig(
            Rarity rarity,
            double coreMin,
            double coreMax,
            double rarityWeight
    ) {
        this.rarity = rarity;
        this.coreMin = coreMin;
        this.coreMax = coreMax;
        this.rarityWeight = rarityWeight;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public double getCoreMin() {
        return coreMin;
    }

    public double getCoreMax() {
        return coreMax;
    }

    public double getRarityWeight() {
        return rarityWeight;
    }
}