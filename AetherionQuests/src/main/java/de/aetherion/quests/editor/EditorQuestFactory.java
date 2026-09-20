package de.aetherion.quests.editor;

import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.reward.Reward;

import java.util.Locale;
import java.util.Set;

/**
 * Minimal create-then-link quests for the NPC editor.
 * Talk-to-this-NPC stub; Peter can expand objectives later.
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
        quest.addReward(new Reward("XP", 25));
        quest.addReward(new Reward("Coins", 50));
        return quest;
    }
}
