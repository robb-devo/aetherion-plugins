package de.aetherion.core.api;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Quest progress notes, queries, and boss-bar suppress.
 * Implemented by AetherionQuests; no-op when unregistered.
 */
public interface QuestProgressAccess {

    void noteBroken(Player player, Material material, int amount);

    void noteCollected(Player player, Material material, int amount);

    void noteInventoryGain(Player player);

    void noteUsed(Player player, String target);

    void noteCrafted(Player player, String target, int amount);

    boolean isQuestActive(Player player, String questId);

    boolean isQuestCompleted(Player player, String questId);

    boolean shouldGuidePetsEquip(Player player);

    boolean canChopTrees(Player player);

    void unlockForagerChop(Player player);

    /**
     * Speak as a living NPC (bubble + chat when Quests/TalkUx is up). Default falls back to plain chat.
     */
    default void npcLine(Player player, String npcId, String displayName, String line) {
        if (player == null || line == null || line.isBlank()) {
            return;
        }
        String name = displayName == null || displayName.isBlank() ? "NPC" : displayName;
        player.sendMessage("§6" + name + ": §f" + line);
    }

    void suppress(Player player);

    void unsuppress(Player player);

    void unsuppress(UUID playerId);

    /**
     * Persist this player's quest YAML before a network snapshot. Default no-op
     * when Quests is not loaded.
     */
    default void flushPlayer(Player player) {
    }

    List<QuestNpcInfo> npcs();

    ItemStack npcAnchor(String npcId);

    ItemStack merchantChest();

    ItemStack exploreChest(String kind);

    String despawnNpc(Entity entity);
}
