package de.aetherion.items.core;

/**
 * One shared cap for every booster on an item.
 * Core and special uses both count toward this total.
 */
public final class BoosterLimits {

    /** Soft ceiling so bare tier gaps still matter after investment. */
    public static final int MAX_TOTAL = de.aetherion.items.item.BalanceTargets.BOOSTER_MAX_TOTAL;

    private BoosterLimits() {
    }
}
