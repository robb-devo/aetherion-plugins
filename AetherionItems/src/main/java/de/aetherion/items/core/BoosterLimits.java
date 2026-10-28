package de.aetherion.items.core;

/**
 * One shared cap for every booster on an item.
 * Core and special uses both count toward this total.
 */
public final class BoosterLimits {

    /** Soft ceiling so bare tier gaps still matter after investment. */
    public static final int MAX_TOTAL = de.aetherion.items.item.BalanceTargets.BOOSTER_MAX_TOTAL;
    /** Bonegrafts from the Heartforge (Jawcamp, mine server): each one opens one more socket. */
    public static final int MAX_GRAFTS = 4;
    /** Socket array size: the base cap plus every graft an item can take. */
    public static final int MAX_SLOTS = MAX_TOTAL + MAX_GRAFTS;

    private BoosterLimits() {
    }
}
