package de.aetherion.items.util;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.QuestProgressAccess;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Typed bridge so inventory auto-pay still advances COLLECT quests.
 * No-op when AetherionQuests is offline.
 */
public final class QuestProgressHook {

    private QuestProgressHook() {
    }

    public static void noteCollected(Player player, Material material, int amount) {
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            quests.noteCollected(player, material, amount);
        }
    }

    public static void noteBroken(Player player, Material material, int amount) {
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            quests.noteBroken(player, material, amount);
        }
    }

    public static void noteUsed(Player player, String target) {
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            quests.noteUsed(player, target);
        }
    }

    public static void noteCrafted(Player player, String target, int amount) {
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            quests.noteCrafted(player, target, amount);
        }
    }

    public static void noteInventoryGain(Player player) {
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            quests.noteInventoryGain(player);
        }
    }

    public static boolean isQuestActive(Player player, String questId) {
        QuestProgressAccess quests = AetherServices.quests();
        return quests != null && quests.isQuestActive(player, questId);
    }

    public static boolean shouldGuidePetsEquip(Player player) {
        QuestProgressAccess quests = AetherServices.quests();
        return quests != null && quests.shouldGuidePetsEquip(player);
    }
}
