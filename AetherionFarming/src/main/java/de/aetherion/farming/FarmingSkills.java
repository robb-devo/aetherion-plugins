package de.aetherion.farming;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillProgression;
import de.aetherion.items.skill.SkillService;

import org.bukkit.entity.Player;

/**
 * Thin bridge to the skill spine in AetherionItems. Safe when Items is missing or
 * mid-reload — the event just stops crediting skills.
 */
final class FarmingSkills {

    private FarmingSkills() {
    }

    /** Bonus Farming XP for helping clear a field. */
    static void bonus(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        try {
            SkillService skills = skills();
            if (skills != null) {
                skills.grantGatherBonus(player, AetherSkill.Category.FARMING, amount);
            }
        } catch (NoClassDefFoundError ignored) {
        }
    }

    /** Focus Farming skill + level bar, or {@code null} with none equipped. */
    static String credit(Player player) {
        try {
            SkillService skills = skills();
            return skills == null ? null : skills.loopCredit(player, AetherSkill.Category.FARMING);
        } catch (NoClassDefFoundError ignored) {
            return null;
        }
    }

    /** 0–5: one step per rarity tier (every 20 levels) of the best Farming skill. */
    static int boostTier(Player player) {
        try {
            SkillService skills = skills();
            if (skills == null) {
                return 0;
            }
            return SkillProgression.rarityTier(skills.highestLevel(player, AetherSkill.Category.FARMING));
        } catch (NoClassDefFoundError ignored) {
            return 0;
        }
    }

    private static SkillService skills() {
        AetherionItems items = AetherionItems.getInstance();
        return items == null ? null : items.getSkills();
    }
}
