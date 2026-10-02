package de.aetherion.quests.talk;

import de.aetherion.core.api.TalkAccess;

import org.bukkit.Location;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPCs other plugins own (e.g. the island guide in AetherionGuilds) that borrow the talk bubble. Only the
 * anchor, name colour and voice come from here; the bubble itself is the normal {@link TalkUx} one.
 */
public final class GuestSpeakers {

    private static final Map<String, TalkAccess.Speaker> SPEAKERS = new ConcurrentHashMap<>();

    private GuestSpeakers() {
    }

    private static String key(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).trim();
    }

    public static void register(TalkAccess.Speaker speaker) {
        if (speaker != null && speaker.id() != null && !speaker.id().isBlank()) {
            SPEAKERS.put(key(speaker.id()), speaker);
        }
    }

    public static void unregister(String id) {
        if (id != null) {
            SPEAKERS.remove(key(id));
        }
    }

    public static void clear() {
        SPEAKERS.clear();
    }

    public static boolean has(String id) {
        return id != null && SPEAKERS.containsKey(key(id));
    }

    public static TalkAccess.Speaker get(String id) {
        return id == null ? null : SPEAKERS.get(key(id));
    }

    /** Feet position of a guest NPC, or null (unknown id / not spawned). */
    public static Location locate(String id) {
        TalkAccess.Speaker speaker = get(id);
        if (speaker == null || speaker.anchor() == null) {
            return null;
        }
        try {
            Location at = speaker.anchor().get();
            return at == null || at.getWorld() == null ? null : at.clone();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static String nameCode(String id, String fallback) {
        TalkAccess.Speaker speaker = get(id);
        return speaker == null || speaker.nameColor() == null || speaker.nameColor().isBlank()
                ? fallback : speaker.nameColor();
    }

    public static String displayName(String id) {
        TalkAccess.Speaker speaker = get(id);
        return speaker == null || speaker.displayName() == null ? id : speaker.displayName();
    }

    /** Voice id to use for this NPC's blips (guests borrow a named voice), else the id itself. */
    public static String voiceOf(String id) {
        TalkAccess.Speaker speaker = get(id);
        return speaker == null || speaker.voice() == null || speaker.voice().isBlank() ? id : speaker.voice();
    }
}
