package de.aetherion.foraging;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillService;

import org.bukkit.entity.Player;

/**
 * Thin bridge to the skill spine in AetherionItems. Safe when Items is missing or
 * mid-reload — the loop simply stops crediting skills.
 */
final class ForagingSkills {

    private ForagingSkills() {
    }

    /** Bonus Foraging XP for perfect fells / streaks (per-log XP stays where it was). */
    static void bonus(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        try {
            SkillService skills = skills();
            if (skills != null) {
                skills.grantGatherBonus(player, AetherSkill.Category.FORAGING, amount);
            }
        } catch (NoClassDefFoundError ignored) {
        }
    }

    /** Focus Foraging skill + level bar, or {@code null} with none equipped. */
    static String credit(Player player) {
        try {
            SkillService skills = skills();
            return skills == null ? null : skills.loopCredit(player, AetherSkill.Category.FORAGING);
        } catch (NoClassDefFoundError ignored) {
            return null;
        }
    }

    /** Best Foraging skill level (equipped or not) — the fell window grows with it. */
    static int bestLevel(Player player) {
        try {
            SkillService skills = skills();
            return skills == null ? 0 : skills.highestLevel(player, AetherSkill.Category.FORAGING);
        } catch (NoClassDefFoundError ignored) {
            return 0;
        }
    }

    private static SkillService skills() {
        AetherionItems items = AetherionItems.getInstance();
        return items == null ? null : items.getSkills();
    }
}
