package de.aetherion.fishing;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillService;

import org.bukkit.entity.Player;

/**
 * Thin bridge to the skill spine in AetherionItems (softdepend). Every call is safe when
 * Items is missing or mid-reload — the loop just stops crediting skills.
 */
final class FishingSkills {

    private FishingSkills() {
    }

    /** Bonus Fishing XP for timing / streaks (base catch XP stays in Items). */
    static void bonus(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        try {
            SkillService skills = skills();
            if (skills != null) {
                skills.grantGatherBonus(player, AetherSkill.Category.FISHING, amount);
            }
        } catch (NoClassDefFoundError ignored) {
        }
    }

    /** Focus Fishing skill + level bar, or {@code null} with none equipped. */
    static String credit(Player player) {
        try {
            SkillService skills = skills();
            return skills == null ? null : skills.loopCredit(player, AetherSkill.Category.FISHING);
        } catch (NoClassDefFoundError ignored) {
            return null;
        }
    }

    private static SkillService skills() {
        AetherionItems items = AetherionItems.getInstance();
        return items == null ? null : items.getSkills();
    }
}
