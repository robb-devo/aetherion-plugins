package de.aetherion.items.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Reflection bridge so inventory auto-pay still advances COLLECT quests.
 */
public final class QuestProgressHook {

    private QuestProgressHook() {
    }

    public static void noteCollected(Player player, Material material, int amount) {
        if (player == null || material == null || amount <= 0) {
            return;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.bridge.QuestProgressBridge");
            type.getMethod("noteCollected", Player.class, Material.class, int.class)
                    .invoke(null, player, material, amount);
        } catch (Throwable ignored) {
        }
    }

    public static void noteBroken(Player player, Material material, int amount) {
        if (player == null || material == null || amount <= 0) {
            return;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.bridge.QuestProgressBridge");
            type.getMethod("noteBroken", Player.class, Material.class, int.class)
                    .invoke(null, player, material, amount);
        } catch (Throwable ignored) {
        }
    }

    public static void noteUsed(Player player, String target) {
        if (player == null || target == null || target.isBlank()) {
            return;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.bridge.QuestProgressBridge");
            type.getMethod("noteUsed", Player.class, String.class)
                    .invoke(null, player, target);
        } catch (Throwable ignored) {
        }
    }

    public static void noteCrafted(Player player, String target, int amount) {
        if (player == null || target == null || target.isBlank() || amount <= 0) {
            return;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.bridge.QuestProgressBridge");
            type.getMethod("noteCrafted", Player.class, String.class, int.class)
                    .invoke(null, player, target, amount);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isQuestActive(Player player, String questId) {
        if (player == null || questId == null || questId.isBlank()) {
            return false;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.bridge.QuestProgressBridge");
            Object result = type.getMethod("isQuestActive", Player.class, String.class)
                    .invoke(null, player, questId);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean shouldGuidePetsEquip(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.bridge.QuestProgressBridge");
            Object result = type.getMethod("shouldGuidePetsEquip", Player.class).invoke(null, player);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
