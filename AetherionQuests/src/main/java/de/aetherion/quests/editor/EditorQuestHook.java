package de.aetherion.quests.editor;

import org.bukkit.entity.Player;

/**
 * Quest hooks for editor-created NPCs.
 * Offer / start / turn-in use the live {@link de.aetherion.quests.manager.QuestManager}.
 * Editor-authored jobs persist in {@code editor-quests.yml} and register into that same manager.
 */
public interface EditorQuestHook {

    void offer(Player player, CustomNpc npc, String questId);

    void start(Player player, CustomNpc npc, String questId);

    void turnIn(Player player, CustomNpc npc, String questId);
}
