package de.aetherion.items.skill;

import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What this login earned: skill XP per skill, level-ups, and XP per hour. Memory only — it
 * resets when you log out, which is the point ("how was that session?").
 */
public final class SkillSession {

    private static final Map<UUID, Tally> SESSIONS = new ConcurrentHashMap<>();

    private SkillSession() {
    }

    /** A read-only view of one session. */
    public record Snapshot(long started, long xp, int levels, AetherSkill top, long topXp) {

        public long minutes() {
            return Math.max(0L, (System.currentTimeMillis() - started) / 60_000L);
        }

        /** XP per hour, 0 in the first minute (too noisy). */
        public long perHour() {
            long millis = System.currentTimeMillis() - started;
            if (millis < 60_000L) {
                return 0L;
            }
            return Math.round(xp * 3_600_000.0d / millis);
        }
    }

    private static final class Tally {
        private final long started = System.currentTimeMillis();
        private final EnumMap<AetherSkill, Long> xp = new EnumMap<>(AetherSkill.class);
        private long total;
        private int levels;
    }

    static void note(Player player, AetherSkill skill, int amount, int levels) {
        if (player == null || skill == null || amount <= 0) {
            return;
        }
        Tally tally = SESSIONS.computeIfAbsent(player.getUniqueId(), ignored -> new Tally());
        synchronized (tally) {
            tally.xp.merge(skill, (long) amount, Long::sum);
            tally.total += amount;
            tally.levels += Math.max(0, levels);
        }
    }

    static void forget(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    public static Snapshot of(Player player) {
        Tally tally = player == null ? null : SESSIONS.get(player.getUniqueId());
        if (tally == null) {
            return new Snapshot(System.currentTimeMillis(), 0L, 0, null, 0L);
        }
        synchronized (tally) {
            AetherSkill top = null;
            long topXp = 0L;
            for (Map.Entry<AetherSkill, Long> entry : tally.xp.entrySet()) {
                if (entry.getValue() > topXp) {
                    top = entry.getKey();
                    topXp = entry.getValue();
                }
            }
            return new Snapshot(tally.started, tally.total, tally.levels, top, topXp);
        }
    }
}
