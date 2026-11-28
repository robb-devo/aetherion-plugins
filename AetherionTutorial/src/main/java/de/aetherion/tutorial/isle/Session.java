package de.aetherion.tutorial.isle;

import net.kyori.adventure.bossbar.BossBar;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One player on the isle: where they are in the lesson plan and the few counters the running step needs. */
final class Session {

    final UUID id;
    Step step = Step.ARRIVE;
    /** Counters of the running step (logs felled, stone / coal mined, armour pieces made, hits landed). */
    int a;
    int b;
    /** Server tick the step began, and the tick of the last sign of progress (hints wait on that one). */
    long stepStarted;
    long lastProgress;
    long lastHint;
    /** A scene owns the player (camera flight, boss intro, departure): no bar, no hints, no rescue teleports. */
    boolean scene;
    /** Finished the isle before, ever: may take the short way. */
    boolean returning;
    /** Chose the short way this time. */
    boolean skipping;
    /** Where a fall or a swim too far puts the player back: the spot the running step is about. */
    Location rescue;
    /** The last solid ground under the player's feet (the better place to be put back to). */
    Location ground;
    /** A visit: this player has a character elsewhere and walks the isle with empty pockets. */
    boolean guest;
    /** What Old Clanger had left when the player walked off the Flat (-1 = he is fresh). */
    double bossHp = -1.0;
    BossBar bar;
    long joinedAtMs;
    /** The sun is up for this player (the bell was rung). */
    boolean morning;
    /** Small facts of this visit ("overlook", "skills-card"), so a beat plays once. */
    final Set<String> seen = new HashSet<>();

    /** Answers offered in the chat (classic chat talk has no reply chips), and until when they count (ms). */
    java.util.List<de.aetherion.core.api.TalkAccess.Reply> choices;
    long choicesUntil;

    /** How often each islander was spoken to (they say their next thought on the next click). */
    final java.util.Map<String, Integer> chats = new java.util.HashMap<>();

    Session(UUID id) {
        this.id = id;
    }

    void progress(long tick) {
        lastProgress = tick;
    }
}
