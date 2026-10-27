package de.aetherion.core.api;

import java.util.UUID;

/**
 * Per-player state a gameplay plugin keeps in its own files (outside the Items files the transfer already knows),
 * carried with the character between backends (transfer v6). Mining's isle profile is the first one: mastery,
 * ledger, forge rep and the Amethyst Mine tallies must follow the player onto the mine server and back.
 *
 * <p>A backend that does not run the plugin carries the text along untouched, so a detour (mine server → isles →
 * main world) loses nothing.
 */
public interface PlayerDataPart {

    /** Stable snapshot key: lowercase letters, digits and underscores ({@code mining}). */
    String id();

    /** Main thread. This player's state as YAML text, or {@code null} when there is none. */
    String export(UUID player);

    /** Main thread, on arrival, before the plugin reads the player again. Replaces what this backend held. */
    void importData(UUID player, String yaml);
}
