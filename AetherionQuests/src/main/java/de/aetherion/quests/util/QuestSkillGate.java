package de.aetherion.quests.util;


import de.aetherion.items.AetherionItems;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillService;
import de.aetherion.quests.model.Quest;

import org.bukkit.entity.Player;


/**
 * Skill / account level gates for quest accept.
 */
public final class QuestSkillGate {


    private QuestSkillGate() {
    }


    public static boolean meets(Player player, Quest quest) {
        return failReason(player, quest) == null;
    }


    /**
     * @return null if ok, otherwise a short player-facing deny line
     */
    public static String failReason(Player player, Quest quest) {
        if (player == null || quest == null || !quest.hasSkillRequirement()) {
            return null;
        }

        SkillService skills = skills();
        if (skills == null) {
            return "§cSkill system offline. Try again later.";
        }

        if (quest.getRequiredAccountLevel() > 0) {
            int have = skills.accountLevel(player);
            int need = quest.getRequiredAccountLevel();
            if (have < need) {
                return "§cNeed Aetherion Level §f" + need
                        + "§c. Yours: §f" + have + "§c.";
            }
        }

        if (quest.getRequiredCategory() != null && quest.getRequiredCategoryLevel() > 0) {
            AetherSkill.Category cat = quest.getRequiredCategory();
            int have = skills.highestLevel(player, cat);
            int need = quest.getRequiredCategoryLevel();
            if (have < need) {
                return "§cNeed " + plainCategory(cat) + " skill level §f" + need
                        + "§c. Yours: §f" + have + "§c.";
            }
        }

        if (quest.getRequiredSkillId() != null && !quest.getRequiredSkillId().isBlank()
                && quest.getRequiredSkillLevel() > 0) {
            AetherSkill skill = AetherSkill.byId(quest.getRequiredSkillId());
            if (skill == null) {
                return "§cUnknown skill gate. Tell staff.";
            }
            int have = skills.level(player, skill);
            int need = quest.getRequiredSkillLevel();
            if (have < need) {
                return "§cNeed §f" + skill.displayName() + " §clevel §f" + need
                        + "§c. Yours: §f" + have + "§c.";
            }
        }

        return null;
    }


    public static String requirementHint(Quest quest) {
        if (quest == null || !quest.hasSkillRequirement()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("§8Requires: ");
        boolean first = true;
        if (quest.getRequiredAccountLevel() > 0) {
            sb.append("§7Aetherion Lv §f").append(quest.getRequiredAccountLevel());
            first = false;
        }
        if (quest.getRequiredCategory() != null && quest.getRequiredCategoryLevel() > 0) {
            if (!first) {
                sb.append("§8 · ");
            }
            sb.append("§7")
                    .append(plainCategory(quest.getRequiredCategory()))
                    .append(" Lv §f")
                    .append(quest.getRequiredCategoryLevel());
            first = false;
        }
        if (quest.getRequiredSkillId() != null && !quest.getRequiredSkillId().isBlank()
                && quest.getRequiredSkillLevel() > 0) {
            if (!first) {
                sb.append("§8 · ");
            }
            AetherSkill skill = AetherSkill.byId(quest.getRequiredSkillId());
            String name = skill != null ? skill.displayName() : quest.getRequiredSkillId();
            sb.append("§7").append(name).append(" Lv §f").append(quest.getRequiredSkillLevel());
        }
        return sb.toString();
    }


    private static String plainCategory(AetherSkill.Category category) {
        if (category == null) {
            return "Skill";
        }
        String raw = category.name();
        if (raw.isEmpty()) {
            return "Skill";
        }
        return raw.charAt(0) + raw.substring(1).toLowerCase(java.util.Locale.ROOT);
    }


    private static SkillService skills() {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null) {
            return null;
        }
        return items.getSkills();
    }

}
