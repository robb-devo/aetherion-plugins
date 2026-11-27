package de.aetherion.items.skill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Level-system v2 (2026-11-27): talents open with the level of their skill. The first three of every skill are there
 * from the start; the rest spread from level 5 to 100 on a gentle curve, so almost every tenth level brings something
 * new. Skills with four talents or fewer (Dungeon) have them all at once. Utility talents follow the best skill.
 * Order is the enum order inside each category, which already runs from basic to isle-specific.
 */
public final class TalentUnlocks {

    private static final int FREE = 3;
    private static final Map<AetherSkill, Integer> LEVEL = new EnumMap<>(AetherSkill.class);

    static {
        for (AetherSkill.Category category : AetherSkill.Category.values()) {
            List<AetherSkill> talents = of(category);
            int n = talents.size();
            int k = n - FREE;
            for (int i = 0; i < n; i++) {
                int level;
                if (n <= 4 || i < FREE) {
                    level = 1;
                } else if (k == 1) {
                    level = 20;
                } else {
                    double t = (i - FREE) / (double) (k - 1);
                    level = (int) (Math.round((5.0d + 95.0d * Math.pow(t, 1.6d)) / 5.0d) * 5L);
                    level = Math.max(5, Math.min(100, level));
                }
                LEVEL.put(talents.get(i), level);
            }
        }
    }

    private TalentUnlocks() {
    }

    /** Skill level this talent opens at (1 = from the start). */
    public static int level(AetherSkill talent) {
        return talent == null ? 1 : LEVEL.getOrDefault(talent, 1);
    }

    public static List<AetherSkill> of(AetherSkill.Category category) {
        List<AetherSkill> out = new ArrayList<>();
        for (AetherSkill talent : AetherSkill.values()) {
            if (talent.category() == category) {
                out.add(talent);
            }
        }
        return out;
    }

    /** Talents of {@code category} that open on the way from {@code from} (exclusive) to {@code to} (inclusive). */
    public static List<AetherSkill> openedBetween(AetherSkill.Category category, int from, int to) {
        List<AetherSkill> out = new ArrayList<>();
        if (to <= from) {
            return out;
        }
        for (AetherSkill talent : of(category)) {
            int at = level(talent);
            if (at > from && at <= to) {
                out.add(talent);
            }
        }
        return out;
    }

    /** The next talent of {@code category} still closed at {@code level}, or {@code null}. */
    public static AetherSkill next(AetherSkill.Category category, int level) {
        AetherSkill best = null;
        for (AetherSkill talent : of(category)) {
            int at = level(talent);
            if (at > level && (best == null || at < level(best))) {
                best = talent;
            }
        }
        return best;
    }
}
