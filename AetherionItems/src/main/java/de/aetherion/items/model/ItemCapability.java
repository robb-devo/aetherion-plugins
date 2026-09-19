package de.aetherion.items.model;

/*
 * Gameplay stats that an item can expose.
 *
 * New stats:
 * 1. Add a value here
 * 2. Add a field in ItemStats
 * 3. Add a key in ItemKeys
 * 4. Assign it on the ItemProfile
 * 5. Read it in the matching listener
 */
public enum ItemCapability {

    MINING_POWER,
    FORTUNE,

    DAMAGE,
    DEFENSE,

    HEALTH,
    SPREAD,
    ATTACK_SPREAD,

    SPEED,
    PET_CATCH_RATE,
    CRIT_CHANCE,
    CRIT_DAMAGE,

    UNDEAD_DAMAGE,
    UNDEAD_RESIST,
    HARVEST_SPREAD,
    FISHING_SPEED,
    FISHING_CATCH;

    public boolean isPetBonusStat() {
        return this == MINING_POWER
                || this == FORTUNE
                || this == DAMAGE
                || this == DEFENSE
                || this == HEALTH
                || this == SPREAD
                || this == ATTACK_SPREAD
                || this == SPEED
                || this == PET_CATCH_RATE
                || this == CRIT_CHANCE
                || this == CRIT_DAMAGE
                || this == UNDEAD_DAMAGE
                || this == UNDEAD_RESIST
                || this == HARVEST_SPREAD
                || this == FISHING_SPEED
                || this == FISHING_CATCH;
    }

    public static ItemCapability[] petBonusStats() {
        return new ItemCapability[] {
                MINING_POWER,
                FORTUNE,
                DAMAGE,
                DEFENSE,
                HEALTH,
                SPREAD,
                ATTACK_SPREAD,
                SPEED,
                PET_CATCH_RATE,
                CRIT_CHANCE,
                CRIT_DAMAGE,
                UNDEAD_DAMAGE,
                UNDEAD_RESIST,
                HARVEST_SPREAD,
                FISHING_SPEED,
                FISHING_CATCH
        };
    }
}