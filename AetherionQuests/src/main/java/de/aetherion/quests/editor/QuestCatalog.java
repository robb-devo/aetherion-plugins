package de.aetherion.quests.editor;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.reward.Reward;
import de.aetherion.quests.util.QuestStoryGate;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Read-only view of the registered quests for the studio (list, search, describe, player stage).
 * Never registers or mutates quests — editor NPCs only link existing quest ids.
 */
public final class QuestCatalog {

    private final AetherionQuests plugin;

    public QuestCatalog(AetherionQuests plugin) {
        this.plugin = plugin;
    }

    private QuestManager manager() {
        return plugin.getQuestManager();
    }

    public boolean available() {
        return manager() != null;
    }

    public Quest get(String id) {
        QuestManager quests = manager();
        if (quests == null || id == null || id.isBlank()) {
            return null;
        }
        Quest quest = quests.getQuest(id);
        return quest != null ? quest : quests.getQuest(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    /** Sorted by readable title. */
    public List<Quest> all() {
        List<Quest> out = new ArrayList<>();
        QuestManager quests = manager();
        if (quests == null) {
            return out;
        }
        for (Quest quest : quests.getQuests()) {
            if (quest != null && quest.getId() != null) {
                out.add(quest);
            }
        }
        out.sort(Comparator.comparing(quest -> title(quest).toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Filtered by a search text (title / id / description) and the tutorial toggle. */
    public List<Quest> search(String needle, boolean includeTutorial) {
        String query = needle == null ? "" : needle.trim().toLowerCase(Locale.ROOT);
        List<Quest> out = new ArrayList<>();
        for (Quest quest : all()) {
            if (!includeTutorial && isTutorial(quest.getId())) {
                continue;
            }
            if (!query.isEmpty()) {
                String haystack = (title(quest) + " " + quest.getId() + " " + strip(quest.getDescription()))
                        .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) {
                    continue;
                }
            }
            out.add(quest);
        }
        return out;
    }

    public int tutorialCount() {
        int count = 0;
        for (Quest quest : all()) {
            if (isTutorial(quest.getId())) {
                count++;
            }
        }
        return count;
    }

    public boolean isTutorial(String questId) {
        return QuestStoryGate.isTutorialQuest(questId);
    }

    public String title(String questId) {
        Quest quest = get(questId);
        return quest == null ? questId : title(quest);
    }

    public static String title(Quest quest) {
        if (quest == null) {
            return "?";
        }
        String title = strip(quest.getTitle());
        return title.isBlank() ? quest.getId() : title;
    }

    /** Where the player stands with a quest; null when the quest doesn't exist. */
    public QuestState stateOf(Player player, String questId) {
        QuestManager quests = manager();
        Quest quest = get(questId);
        if (quests == null || quest == null || player == null) {
            return null;
        }
        return quests.getQuestState(player, quest);
    }

    /** Tooltip lines: description, objectives, rewards, requirements. */
    public List<String> describe(Quest quest, int width) {
        List<String> lore = new ArrayList<>();
        if (quest == null) {
            return lore;
        }
        String description = strip(quest.getDescription());
        if (!description.isBlank()) {
            for (String line : wrap(description, width)) {
                lore.add("§7" + line);
            }
        }
        if (!quest.getObjectives().isEmpty()) {
            lore.add("");
            lore.add("§eObjectives");
            int shown = 0;
            for (Objective objective : quest.getObjectives()) {
                if (objective == null) {
                    continue;
                }
                if (shown++ >= 5) {
                    lore.add("§8  …and more");
                    break;
                }
                String name = strip(objective.getDisplayName() != null ? objective.getDisplayName() : objective.getTarget());
                lore.add("§8• §7" + verb(objective) + " §f" + name + " §8×" + Math.max(1, objective.getAmount()));
            }
        }
        if (!quest.getRewards().isEmpty()) {
            lore.add("");
            lore.add("§6Rewards");
            int shown = 0;
            for (Reward reward : quest.getRewards()) {
                if (reward == null) {
                    continue;
                }
                if (shown++ >= 4) {
                    lore.add("§8  …and more");
                    break;
                }
                lore.add("§8• §f" + pretty(reward.getName()) + (reward.getAmount() > 1 ? " §8×" + reward.getAmount() : ""));
            }
        }
        if (quest.hasSkillRequirement() || quest.getRequiredAccountLevel() > 0) {
            lore.add("");
            lore.add("§cHas level requirements §8(checked on accept)");
        }
        return lore;
    }

    private static String verb(Objective objective) {
        if (objective.getType() == null) {
            return "Do";
        }
        return switch (objective.getType()) {
            case TALK -> "Talk to";
            case MINE -> "Mine";
            case BREAK -> "Break";
            case KILL -> "Defeat";
            case CRAFT -> "Craft";
            case COLLECT -> "Collect";
            case FISH -> "Fish";
            case HARVEST -> "Harvest";
            case DELIVER -> "Deliver";
            case USE -> "Use";
            case CATCH -> "Catch";
            default -> "Do";
        };
    }

    public static String strip(String text) {
        if (text == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(text);
        return stripped == null ? "" : stripped.trim();
    }

    private static String pretty(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Reward";
        }
        String clean = strip(raw).replace('_', ' ').trim();
        if (clean.isEmpty()) {
            return "Reward";
        }
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > width) {
                out.add(line.toString());
                line = new StringBuilder();
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
