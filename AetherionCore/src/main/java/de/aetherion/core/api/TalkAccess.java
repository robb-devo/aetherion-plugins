package de.aetherion.core.api;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * In-world NPC talk: the speech bubble that types out over an NPC's head, plus reply chips beside it.
 * Implemented by AetherionQuests (its TalkUx layer); {@code null} on {@link AetherServices#talk()} while Quests
 * is off.
 *
 * <p>NPCs that another plugin owns join as <b>guest speakers</b>: register an id, a name and where the NPC
 * stands, then {@link #say} lines and offer {@link #replies}. The bubble look, pacing, voice blips and chip
 * input stay exactly the Quests ones. Every call returns {@code false} when the bubble can't open (the player
 * chose classic chat talk, stands too far away, or talk UI is switched off); callers then fall back to chat.
 */
public interface TalkAccess {

    /**
     * A guest NPC. {@code nameColor} is a legacy colour code such as {@code "§e"}; {@code voice} names one of
     * the Quests NPC voices (e.g. {@code "isle_hand"}), null = the default blip. {@code anchor} returns the
     * NPC's feet position (null while it isn't spawned).
     */
    record Speaker(String id, String displayName, String nameColor, String voice, Supplier<Location> anchor) {
    }

    /** One reply chip. {@code echo} is what the player "says" in chat when picking it (null = nothing). */
    record Reply(String label, NamedTextColor color, Runnable action, String echo) {
        public static Reply of(String label, NamedTextColor color, Runnable action) {
            return new Reply(label, color, action, label);
        }
    }

    void registerSpeaker(Speaker speaker);

    void unregisterSpeaker(String id);

    /** Bubble talk is on for this player (global switch and the player's own classic-chat opt-out). */
    boolean bubbles(Player player);

    /** How close (blocks) a player must stand for the bubble to open and stay. */
    double reach();

    /** One spoken line in the bubble. Starts the conversation if needed. */
    boolean say(Player player, String speakerId, String line);

    /** Reply chips under the current line (1 to 5). */
    boolean replies(Player player, String speakerId, List<Reply> replies);

    boolean talkingWith(Player player, String speakerId);

    boolean hasReplies(Player player, String speakerId);

    /** A reply was just picked: swallow the NPC click that came with the same mouse press. */
    boolean recentlyPicked(Player player);

    /** Nudge the chips when the player clicks the NPC instead of a reply. */
    void pulse(Player player);

    /** Close this player's bubble (animated). */
    void end(Player player);

    /** Ambient one-liner over a guest NPC, for these viewers only. */
    void bark(String speakerId, String line, Collection<? extends Player> viewers, int ticks);
}
