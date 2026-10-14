package de.aetherion.core.api;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Island homes, read by Core on the island backend (mmo-i) to land arrivals directly on their island
 * and to respawn them there. Implemented by AetherionGuilds; null on backends without it.
 */
public interface IslandAccess {

    /** Personal island spawn when claimed and already built, else null (never starts a paste). */
    Location home(UUID owner);

    /** The player's guild island spawn when the guild exists and its island is built, else null. */
    Location guildHome(UUID member);

    boolean hasIsland(UUID owner);

    /** True when the location is inside a personal or guild island world. */
    default boolean isIslandWorld(Location location) {
        return false;
    }
}
