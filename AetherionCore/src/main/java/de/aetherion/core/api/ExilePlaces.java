package de.aetherion.core.api;

import org.bukkit.Location;

/**
 * Where the exile ferry lives, for plugins that point players at it (Quests' compass, the Ferryman).
 * Core fills it: the Veil Quay gangway on the main world once {@code /exile setquay} placed the islet.
 */
public final class ExilePlaces {

    /** The exile island world on mmo-c (Runde 7 map). */
    public static final String EXILE_WORLD = "aetherion_exile";

    private static volatile Location quayBoard;
    private static volatile Location quayFerryman;

    private ExilePlaces() {
    }

    /** Gangway of the Veil Quay (main world), or null while the islet is not placed. */
    public static Location quayBoard() {
        Location at = quayBoard;
        return at == null ? null : at.clone();
    }

    public static void setQuayBoard(Location at) {
        quayBoard = at == null ? null : at.clone();
    }

    /** Where the Ferryman stands on the Veil Quay (Quests places him there), or null. */
    public static Location quayFerryman() {
        Location at = quayFerryman;
        return at == null ? null : at.clone();
    }

    public static void setQuayFerryman(Location at) {
        quayFerryman = at == null ? null : at.clone();
    }
}
