package de.aetherion.core.network;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decides whether a transfer snapshot may replace live or shared progression.
 * A snapshot saved before skills loaded (vanilla level 1, no skills section,
 * empty inventory) must not clobber a richer profile.
 */
public final class TransferProgressGuard {

    private TransferProgressGuard() {
    }

    /**
     * @return true when the snapshot's vanilla level may be written onto the player.
     *         False means leave the bar alone, or sync it from skills instead.
     */
    public static boolean applySnapshotLevel(
            int snapshotLevel,
            int liveLevel,
            boolean skillsImported,
            boolean sharedProfileIsRich
    ) {
        if (skillsImported) {
            return false;
        }
        if (snapshotLevel < liveLevel) {
            return false;
        }
        if (snapshotLevel <= 1 && sharedProfileIsRich) {
            return false;
        }
        return true;
    }

    /**
     * Empty or undecodable snapshot sections must not replace a live inventory
     * that still holds items. A failed slot decode is a partial transfer.
     */
    public static boolean applyItemSection(int snapshotOccupied, int liveOccupied, boolean decodeIntact) {
        if (!decodeIntact) {
            return false;
        }
        return snapshotOccupied > 0 || liveOccupied <= 0;
    }

    public static boolean isFreshProfile(long bonusXp, int claimedShardLevel, int bonusSlots, boolean anySkillProgress) {
        return bonusXp <= 0L && claimedShardLevel <= 0 && bonusSlots <= 0 && !anySkillProgress;
    }

    /** Fresh in-memory profile must not replace a shared skills section that already has progress. */
    public static boolean refuseFreshSkillOverwrite(boolean memoryFresh, boolean diskHasProgress) {
        return memoryFresh && diskHasProgress;
    }

    @SuppressWarnings("unchecked")
    public static long richness(Object section) {
        Map<String, Object> map = asMap(section);
        if (map.isEmpty()) {
            return 0L;
        }
        long score = longVal(map.get("bonusXp"));
        score += (long) intVal(map.get("claimedShardLevel")) * 1_000L;
        score += (long) intVal(map.get("bonusSlots")) * 100L;
        Object progress = map.get("progress");
        if (progress instanceof Map<?, ?> skills) {
            for (Object value : skills.values()) {
                Map<String, Object> skill = asMap(value);
                int level = intVal(skill.get("level"));
                int xp = intVal(skill.get("xp"));
                if (level > 1) {
                    score += (long) (level - 1) * 10_000L;
                }
                score += Math.max(0, xp);
            }
        }
        return score;
    }

    /**
     * Drop incoming player sections that are a fresh profile when disk already
     * has progress for that same path. Real (non-fresh) sections still write.
     */
    public static Map<String, Object> filterSkillWrites(Map<String, Object> incoming, Map<String, Object> existingByPath) {
        Map<String, Object> kept = new LinkedHashMap<>();
        if (incoming == null || incoming.isEmpty()) {
            return kept;
        }
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            long incomingScore = richness(entry.getValue());
            long diskScore = existingByPath == null ? 0L : richness(existingByPath.get(entry.getKey()));
            if (incomingScore <= 0L && diskScore > 0L) {
                continue;
            }
            kept.put(entry.getKey(), entry.getValue());
        }
        return kept;
    }

    /**
     * Player sections inside an exported skills blob.
     * New snapshots use path keys ({@code players.<uuid>}). Snapshots that went
     * through Bukkit's dotted paths come back nested ({@code players -> uuid}).
     */
    public static Map<String, Object> playerSections(Object skillsNode) {
        Map<String, Object> root = asMap(unwrapYaml(skillsNode));
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            if (entry.getKey().startsWith("players.")) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        Object players = root.get("players");
        if (players != null) {
            for (Map.Entry<String, Object> entry : asMap(players).entrySet()) {
                String path = "players." + entry.getKey();
                out.putIfAbsent(path, entry.getValue());
            }
        }
        return out;
    }

    /** A raw YAML string, a map, or the {@code yml} wrapper from a split key. */
    public static Object unwrapYaml(Object node) {
        if (node instanceof String raw) {
            return raw;
        }
        Map<String, Object> map = asMap(node);
        if (map.size() == 1 && map.containsKey("yml")) {
            return map.get("yml");
        }
        return node;
    }

    public static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    out.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return out;
        }
        return Map.of();
    }

    private static long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static int intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
