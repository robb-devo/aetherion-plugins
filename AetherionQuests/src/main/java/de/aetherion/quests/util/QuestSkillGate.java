package de.aetherion.quests.util;


import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.ItemFactoryAccess;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillService;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;


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
        if (player == null || quest == null || !quest.hasRequirement()) {
            return null;
        }

        if (quest.hasPriorQuestRequirement()) {
            String priorFail = priorQuestFail(player, quest);
            if (priorFail != null) {
                return priorFail;
            }
        }

        if (quest.hasItemRequirement()) {
            int have = countOwned(player, quest.getRequiredItemId());
            int need = quest.getRequiredItemAmount();
            if (have < need) {
                return "§cNeed §f" + need + "× " + pretty(quest.getRequiredItemId())
                        + "§c. Yours: §f" + have + "§c.";
            }
        }

        if (!quest.hasSkillRequirement()) {
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
        if (quest == null || !quest.hasRequirement()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("§8Requires: ");
        boolean first = true;
        if (quest.hasPriorQuestRequirement()) {
            sb.append("§7Quest §f").append(quest.getRequiredPriorQuestId());
            first = false;
        }
        if (quest.hasItemRequirement()) {
            if (!first) {
                sb.append("§8 · ");
            }
            sb.append("§7")
                    .append(quest.getRequiredItemAmount())
                    .append("× ")
                    .append(pretty(quest.getRequiredItemId()));
            first = false;
        }
        if (quest.getRequiredAccountLevel() > 0) {
            if (!first) {
                sb.append("§8 · ");
            }
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


    private static String priorQuestFail(Player player, Quest quest) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        QuestManager manager = plugin == null ? null : plugin.getQuestManager();
        if (manager == null) {
            return null;
        }
        String priorId = quest.getRequiredPriorQuestId();
        Quest prior = manager.getQuest(priorId);
        if (prior == null) {
            return "§cUnknown prior quest. Tell staff.";
        }
        if (manager.getQuestState(player, prior) != QuestState.COMPLETED) {
            return "§cFinish §f" + prior.getTitle() + " §cfirst.";
        }
        return null;
    }


    static int countOwned(Player player, String itemId) {
        if (player == null || itemId == null || itemId.isBlank()) {
            return 0;
        }
        int have = 0;
        ItemFactoryAccess items = AetherServices.items();
        Material material = Material.matchMaterial(itemId.replace(' ', '_'));
        if (material == null) {
            material = Material.matchMaterial(itemId);
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (items != null) {
                String custom = items.itemId(stack);
                if (custom != null && custom.equalsIgnoreCase(itemId)) {
                    have += stack.getAmount();
                    continue;
                }
            }
            if (material != null && stack.getType() == material) {
                have += stack.getAmount();
            }
        }
        return have;
    }


    static String pretty(String id) {
        if (id == null || id.isBlank()) {
            return "item";
        }
        String[] parts = id.toLowerCase(java.util.Locale.ROOT).replace(':', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.isEmpty() ? id : out.toString();
    }


    private static SkillService skills() {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null) {
            return null;
        }
        return items.getSkills();
    }

}
