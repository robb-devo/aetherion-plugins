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

    /**
     * Open the in-game FancyNPC / quest editor ({@code /npc}).
     *
     * @return {@code true} if the editor opened
     */
    default boolean openNpcEditor(Player player) {
        return npcEditorAction(player, "open");
    }

    /**
     * Run an NPC editor action ({@code open}, {@code create}, {@code nearby},
     * {@code list}, {@code wand}, {@code help}).
     *
     * @return {@code true} if Quests handled the action
     */
    default boolean npcEditorAction(Player player, String action) {
        return false;
    }
}
