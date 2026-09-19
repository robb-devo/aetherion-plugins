package de.aetherion.bossengine.api;

/**
 * Why a boss was created. Quest- and Lexikon-plugins can filter on this.
 */
public enum SpawnCause {

    TIMER,
    COMMAND,
    ITEM,
    API,
    UNKNOWN
}
