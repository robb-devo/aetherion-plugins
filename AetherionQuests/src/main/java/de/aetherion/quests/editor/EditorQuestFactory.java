package de.aetherion.quests.editor;

import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Create-then-link quests for the NPC editor.
 * Talk stub by default; Peter can switch to gather + edit rewards.
 */
public final class EditorQuestFactory {

    private EditorQuestFactory() {
    }

    public static String slug(String title) {
        if (title == null || title.isBlank()) {
            return "editor_quest";
        }
        String slug = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (slug.isBlank()) {
            return "editor_quest";
        }
        return slug.length() > 32 ? slug.substring(0, 32) : slug;
    }

    public static String uniqueId(String title, Set<String> taken) {
        String base = slug(title);
        if (taken == null || !taken.contains(base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "_" + i;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return base + "_" + Integer.toHexString(title.hashCode() & 0xffff);
    }

    public static Quest talkQuest(String id, String title, String npcId) {
        String safeId = id == null || id.isBlank() ? slug(title) : id.toLowerCase(Locale.ROOT);
        String safeTitle = title == null || title.isBlank() ? "Untitled Quest" : title.trim();
        String talkTarget = npcId == null || npcId.isBlank() ? safeId : npcId;
        Quest quest = new Quest(
                safeId,
                safeTitle,
                "Talk to this NPC. Created in the NPC Editor."
        );
        quest.setServiceId("editor");
        quest.addObjective(new Objective(ObjectiveType.TALK, talkTarget, 1));
        applyDefaultRewards(quest);
        return quest;
    }

    public static void applyDefaultRewards(Quest quest) {
        if (quest == null) {
            return;
        }
        quest.clearRewards();
        quest.addReward(new Reward("XP", 25));
        quest.addReward(new Reward("Coins", 50));
    }

    public static void setRewards(Quest quest, List<Reward> rewards) {
        if (quest == null) {
            return;
        }
        quest.clearRewards();
        if (rewards == null) {
            return;
        }
        for (Reward reward : rewards) {
            if (reward != null && reward.getName() != null && !reward.getName().isBlank() && reward.getAmount() > 0) {
                quest.addReward(reward);
            }
        }
    }

    public static void setObjective(Quest quest, ObjectiveType type, String target, int amount) {
        if (quest == null) {
            return;
        }
        ObjectiveType safeType = type == null ? ObjectiveType.TALK : type;
        String safeTarget = target == null || target.isBlank() ? quest.getId() : target.trim();
        quest.clearObjectives();
        quest.addObjective(new Objective(safeType, safeTarget, Math.max(1, amount)));
        if (isGatherType(safeType)) {
            quest.setServiceId("editor");
        }
    }

    public static boolean isGatherType(ObjectiveType type) {
        return type == ObjectiveType.COLLECT
                || type == ObjectiveType.MINE
                || type == ObjectiveType.BREAK
                || type == ObjectiveType.HARVEST
                || type == ObjectiveType.DELIVER
                || type == ObjectiveType.FISH;
    }

    public static boolean isCurrencyReward(String name) {
        if (name == null) {
            return false;
        }
        String key = name.trim();
        return key.equalsIgnoreCase("Coins")
                || key.equalsIgnoreCase("Coin")
                || key.equalsIgnoreCase("XP")
                || key.equalsIgnoreCase("Exp")
                || key.equalsIgnoreCase("Experience");
    }

    public static int bumpAmount(String rewardName, int current, int delta) {
        int step = isCurrencyReward(rewardName) ? 10 : 1;
        if (Math.abs(delta) >= 10) {
            step *= 5;
            delta = delta > 0 ? 1 : -1;
        }
        int next = current + (delta * step);
        return Math.max(1, Math.min(1_000_000, next));
    }
}
