package de.aetherion.fishing;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Where a place (Fishing Eldervale) plugs into the cast. The controller owns the minigame; the
 * hooks only reshape it — wait, lure count, the bite, the reward line, a forgiven miss. With no
 * hooks set every cast plays exactly like the harbour.
 */
public interface CastHooks {

    /** The bobber just settled in water. Returns a factor for the owned wait (1 = unchanged). */
    double settle(Player player, CastSession session, Location hook);

    /** Extra decorative lure fish for this cast (0 = none). */
    int extraLures(Player player, CastSession session, Location hook);

    /** Short HUD suffix while waiting (water, shoal, heat) or {@code null}. Called twice a second. */
    String waitTag(Player player, CastSession session, Location hook, int streak);

    /**
     * The strike window just opened. May reshape the bar through {@code session}; returns the
     * bite title to show, or {@code null} for the default "Bite!".
     */
    BiteCue bite(Player player, CastSession session, Location hook, int streak);

    /**
     * The fish is landed. Runs one tick after AetherionItems paid the catch.
     *
     * @return an action-bar segment for the reward line, or {@code null}
     */
    String landed(Player player, CastSession session, Location hook, boolean perfect, int streak);

    /** The fish got away. Return {@code true} to keep the streak (a forgiven miss). */
    boolean missed(Player player, CastSession session, Location hook, boolean timeout, int streak);

    /** Player left or the plugin is going down. */
    default void forget(UUID playerId) {
    }

    /** Title + subtitle (legacy colour strings) and a sting for the bite. */
    record BiteCue(String title, String subtitle, Sound sound, float pitch) {
    }
}
