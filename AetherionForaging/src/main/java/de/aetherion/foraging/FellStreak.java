package de.aetherion.foraging;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clean fells in a row. A miss breaks it; two idle minutes let it cool off quietly.
 * Same rules as the catch streak in AetherionFishing so the loops read as one family.
 */
final class FellStreak {

    static final long IDLE_MS = 120_000L;

    private final Map<UUID, Entry> streaks = new ConcurrentHashMap<>();

    int current(UUID playerId) {
        Entry entry = playerId == null ? null : streaks.get(playerId);
        if (entry == null) {
            return 0;
        }
        if (System.currentTimeMillis() - entry.lastMillis > IDLE_MS) {
            streaks.remove(playerId, entry);
            return 0;
        }
        return entry.count;
    }

    /** Count one success; returns the new streak. */
    int bump(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        int next = current(playerId) + 1;
        streaks.put(playerId, new Entry(next, System.currentTimeMillis()));
        return next;
    }

    /** Break the streak; returns what was lost (0 when there was none). */
    int reset(UUID playerId) {
        int lost = current(playerId);
        if (playerId != null) {
            streaks.remove(playerId);
        }
        return lost;
    }

    void clear(UUID playerId) {
        if (playerId != null) {
            streaks.remove(playerId);
        }
    }

    void clearAll() {
        streaks.clear();
    }

    private record Entry(int count, long lastMillis) {
    }
}
