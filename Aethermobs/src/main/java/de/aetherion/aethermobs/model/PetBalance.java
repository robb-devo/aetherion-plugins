package de.aetherion.aethermobs.model;

import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.Rarity;

/**
 * Wave 3: pet contribution is 12% of the comfortable full-stack band.
 * Core rolls at L1 sit near {@link #levelOneTarget}; L100 (~×1.99) then
 * {@link #dampen} so a good pet fills the slice instead of a second set.
 */
public final class PetBalance {

    /** Growth past the 12% slice — whales still tick up, they do not double. */
    public static final double OVER_CAP = 0.18d;

    /** Matches {@link de.aetherion.aethermobs.model.PetStats} per-level. */
    public static final double PER_LEVEL = 0.01d;

    public static final double SHINY_MULTIPLIER = 1.25d;

    private PetBalance() {
    }

    /**
     * L100 soft cap = 12% of Wave 2 comfortable bands (BalanceTargets).
     */
    public static double softCap(ItemCapability capability) {
        if (capability == null) {
            return 38.0;
        }
        return switch (capability) {
            case FORTUNE -> 132.0;
            case MINING_POWER -> 58.0;
            case HARVEST_SPREAD -> 144.0;
            case FISHING_CATCH -> 168.0;
            case FISHING_SPEED -> 31.0;
            case DAMAGE -> 38.0;
            case DEFENSE -> 50.0;
            case HEALTH -> 67.0;
            case SPREAD -> 10.0;
            case ATTACK_SPREAD -> 22.0;
            case SPEED -> 12.0;
            case PET_CATCH_RATE -> 18.0;
            case CRIT_CHANCE -> 9.0;
            case CRIT_DAMAGE -> 59.0;
            case UNDEAD_DAMAGE -> 38.0;
            case UNDEAD_RESIST -> 20.0;
        };
    }

    /** L1 core that reaches {@link #softCap} at L100 before dampen. */
    public static double levelOneTarget(ItemCapability capability) {
        return softCap(capability) / levelMultiplier(100);
    }

    public static double levelMultiplier(int level) {
        return 1.0 + (Math.max(0, level - 1) * PER_LEVEL);
    }

    public static double dampen(ItemCapability capability, double value) {
        double cap = softCap(capability);
        if (value <= cap) {
            return value;
        }
        return cap + ((value - cap) * OVER_CAP);
    }

    /**
     * Fractions of {@link #levelOneTarget}: Common is a real starter pad,
     * Legendary fills the slice at L100, Mythic sits on the cap with a little extra.
     */
    public static double[] rarityFractions(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> new double[] {0.18, 0.32};
            case UNCOMMON -> new double[] {0.32, 0.50};
            case RARE -> new double[] {0.50, 0.70};
            case EPIC -> new double[] {0.70, 0.90};
            case LEGENDARY -> new double[] {0.90, 1.10};
            case MYTHIC, AETHERED -> new double[] {1.10, 1.35};
        };
    }

    public static double[] coreBand(String petId, ItemCapability core, boolean randomCore, Rarity rarity) {
        ItemCapability cap = randomCore || core == null ? ItemCapability.DAMAGE : core;
        double target = levelOneTarget(cap);
        double sig = signatureMultiplier(petId);
        double[] frac = rarityFractions(rarity);
        return new double[] {round1(target * frac[0] * sig), round1(target * frac[1] * sig)};
    }

    public static double signatureMultiplier(String petId) {
        if (petId == null) {
            return 1.0;
        }
        String id = petId.toLowerCase();
        if ("aetherion".equals(id)) {
            return 1.45;
        }
        if (id.endsWith("_dragon")) {
            return 1.20;
        }
        if ("hacker".equals(id)) {
            return 1.10;
        }
        return 1.0;
    }

    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
