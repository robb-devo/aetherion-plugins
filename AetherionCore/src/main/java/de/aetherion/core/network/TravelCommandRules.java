package de.aetherion.core.network;

import java.util.Locale;
import java.util.Map;

/**
 * Which cross-server teleports are real commands, and the result they report.
 * The client paints a missing command, or a result of zero, in the error color
 * even when the player still arrives.
 */
final class TravelCommandRules {

    /** Same value as Brigadier {@code Command.SINGLE_SUCCESS}. Zero is a failed command. */
    static final int SUCCESS = 1;

    private static final Map<String, String> CANONICAL = Map.of("harbor", "harbour", "hafen", "harbour");

    private TravelCommandRules() {
    }

    static String bareLabel(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        int colon = token.lastIndexOf(':');
        String bare = colon >= 0 ? token.substring(colon + 1) : token;
        return bare.toLowerCase(Locale.ROOT);
    }

    /** {@code suffix} keeps the spacing the player typed after the label, or is empty. */
    static String replay(String label, String suffix) {
        return CANONICAL.getOrDefault(label, label) + (suffix == null ? "" : suffix);
    }

    static boolean publish(boolean armed, boolean reachable, boolean steals) {
        return armed && reachable && steals;
    }

    /**
     * True when this backend already takes the label, including labels nobody else registered.
     * A hub warp the lobby answers, and a Dungeons label on the dungeon server, stay put.
     */
    static boolean steals(boolean hasOwner, boolean forced, boolean hub, boolean dungeonKeepsIt,
                          boolean mainRole, boolean foreignOwner) {
        if (!hasOwner || forced) {
            return true;
        }
        if (hub || dungeonKeepsIt) {
            return false;
        }
        return mainRole || foreignOwner;
    }
}
