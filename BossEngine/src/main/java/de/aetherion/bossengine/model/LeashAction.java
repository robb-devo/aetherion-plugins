package de.aetherion.bossengine.model;

public enum LeashAction {

    /**
     * Keep the boss inside its arena (soft clamp to the leash ring).
     */
    TELEPORT,

    /**
     * Despawn and fully reset the encounter.
     */
    RESET
}
