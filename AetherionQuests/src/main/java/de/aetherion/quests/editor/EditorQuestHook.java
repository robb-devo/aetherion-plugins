package de.aetherion.quests.editor;

import org.bukkit.entity.Player;

/**
 * Quest hooks for moderator-created NPCs.
 * <p>
 * Links an existing quest id, or a quest just created from Link Quest → Create
 * (offer / start / turn-in). A later visual quest author can implement this
 * interface without rewriting the dialogue editor.
 */
public interface EditorQuestHook {

    void offer(Player player, CustomNpc npc, String questId);

    void start(Player player, CustomNpc npc, String questId);

    void turnIn(Player player, CustomNpc npc, String questId);
}
