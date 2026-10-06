package de.aetherion.core.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * Read side of the main story ("Das Schlaflied"). AetherionQuests owns the quest chain and
 * computes everything here; other plugins only read it (bells, Origin townsfolk, Chronicle).
 * Core holds no quest ids and no stage math.
 */
public interface StoryAccess {

    /**
     * Coarse story stage for this player. 0 = nothing heard yet (or tutorial not done),
     * 1 = legend heard … 7 = the King's Road is open. Quests documents the exact mapping.
     */
    int stage(Player player);

    /** Server-wide story flag, e.g. {@code first_lullaby_finale}. */
    boolean flag(String key);

    void setFlag(String key, boolean value);

    /** Per-player story flag, e.g. {@code veil_quay_unlocked}. */
    default boolean playerFlag(Player player, String key) {
        return false;
    }

    /** Chronicle page ids this player has unlocked (lore finds, books, letters). */
    default Set<String> chroniclePages(Player player) {
        return Set.of();
    }

    /**
     * Story-aware ambient line for another plugin's NPC (e.g. the Hub's Sister Aurel), in the
     * player's language. {@code null} = no story line, use your own bark.
     */
    default String bark(Player player, String speakerId) {
        return null;
    }

    /**
     * The player talked to another plugin's NPC. Returns the lines that NPC should say when a
     * story step happens there (the step is already recorded); empty = nothing story-related.
     */
    default List<String> talk(Player player, String speakerId) {
        return List.of();
    }
}
