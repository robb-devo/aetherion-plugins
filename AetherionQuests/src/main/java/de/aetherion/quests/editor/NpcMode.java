package de.aetherion.quests.editor;

import java.util.Locale;

/**
 * Editor NPC behaviour: talk only, or talk plus a linked quest.
 */
public enum NpcMode {
    DIALOG,
    QUEST;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return this == QUEST ? "Gives a quest" : "Just talks";
    }

    public static NpcMode parse(String raw, boolean hasLinkedQuest) {
        if (raw == null || raw.isBlank()) {
            return hasLinkedQuest ? QUEST : DIALOG;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (key.equals("quest") || key.equals("quest_npc") || key.equals("questnpc")) {
            return QUEST;
        }
        if (key.equals("dialog") || key.equals("dialogue") || key.equals("dialog_only")
                || key.equals("dialogue_only") || key.equals("talk") || key.equals("talk_only")) {
            return DIALOG;
        }
        return hasLinkedQuest ? QUEST : DIALOG;
    }
}
