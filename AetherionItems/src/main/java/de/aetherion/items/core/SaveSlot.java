package de.aetherion.items.core;

/**
 * When each shared save file gets its minute tick.
 *
 * <p>Every one of these files holds all players and is written on the main thread. They all used to start their
 * one-minute timer in the same tick of the plugin's start, so every minute eight whole-server files were written
 * back to back in a single tick. Each service keeps its minute; the first run is spread out so two never share
 * a tick, and none waits longer for its first save than before.
 */
public enum SaveSlot {
    COINS,
    SKILLS,
    PROGRESS,
    CODEX,
    RECIPES,
    BLUEPRINTS,
    SHARDS,
    XP_BOOSTS;

    /** One minute. */
    public static final long PERIOD_TICKS = 20L * 60;

    private static final long STEP_TICKS = 20L * 7;

    /** Delay before this file's first save. */
    public long firstRunTicks() {
        return PERIOD_TICKS - ordinal() * STEP_TICKS;
    }
}
