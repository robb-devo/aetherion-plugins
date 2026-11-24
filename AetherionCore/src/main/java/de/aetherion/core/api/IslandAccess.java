package de.aetherion.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

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

    /**
     * What the player's own island wants next, as its status bar shows it: the tour step while the tour runs, else
     * the next factory goal ({@code title}, {@code how}, goals {@code done} of {@code total}). Null without one.
     */
    default IslandGoal nextGoal(Player player) {
        return null;
    }

    /** One island goal for the Aether Journal. {@code how} may be null; {@code total} 0 when there is no ladder. */
    record IslandGoal(String title, String how, int done, int total) {
    }
}
