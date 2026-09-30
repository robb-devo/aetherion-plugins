package de.aetherion.core.api;

import java.util.UUID;

/**
 * Wipe-safe online time. Implemented by AetherionCore.
 * Totals live outside world folders and are not removed by {@code /wipe beta}.
 * Booster and other soft timers are a different counter.
 */
public interface PlaytimeAccess {

    /**
     * Total online seconds for this id, including an open session on this backend.
     * Unknown players are {@code 0}.
     */
    long seconds(UUID playerId);

    /** Last name seen for this id, or null when there is no record. */
    String name(UUID playerId);

    /**
     * Full reset for one player. Returns the previous total seconds,
     * or {@code -1} when the new total could not be written.
     */
    long reset(UUID playerId);
}
