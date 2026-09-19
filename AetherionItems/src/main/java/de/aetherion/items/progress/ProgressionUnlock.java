package de.aetherion.items.progress;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.menu.AetherionManagerListener;

import org.bukkit.entity.Player;

/**
 * Reflection-friendly unlocks for Quests (and soft cross-plugin calls).
 */
public final class ProgressionUnlock {

    private ProgressionUnlock() {
    }

    public static boolean unlock(Player player, String flagName, String title, String subtitle) {
        if (player == null || flagName == null || flagName.isBlank()) {
            return false;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.progress() == null) {
            return false;
        }
        ProgressionService.Flag flag;
        try {
            flag = ProgressionService.Flag.valueOf(flagName.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        boolean first = plugin.progress().unlockWithToast(player, flag, title, subtitle);
        AetherionManagerListener.refreshManagerItem(player);
        return first;
    }

    /** Restore Manager unlocks for players who already finished the teaching quests. */
    public static void syncFromCompletedQuests(Player player) {
        if (player == null) {
            return;
        }
        if (completed("lesson_manager", player) || active("lesson_manager", player)) {
            unlockSilent(player, ProgressionService.Flag.SKILLS);
        }
        if (completed("pocket_zoo", player) || active("pocket_zoo", player)) {
            unlockSilent(player, ProgressionService.Flag.PETS);
        }
        if (completed("a_simple_craft", player) || active("a_simple_craft", player)) {
            unlockSilent(player, ProgressionService.Flag.WORKBENCH);
        }
        if (completed("lesson_boost", player) || active("lesson_boost", player)) {
            unlockSilent(player, ProgressionService.Flag.ANVIL);
        }
        AetherionManagerListener.refreshManagerItem(player);
    }

    private static void unlockSilent(Player player, ProgressionService.Flag flag) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.progress() != null) {
            plugin.progress().unlock(player, flag);
        }
    }

    private static boolean completed(String questId, Player player) {
        try {
            Object yes = Class.forName("de.aetherion.quests.bridge.QuestProgressBridge")
                    .getMethod("isQuestCompleted", Player.class, String.class)
                    .invoke(null, player, questId);
            return yes instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean active(String questId, Player player) {
        return de.aetherion.items.util.QuestProgressHook.isQuestActive(player, questId);
    }
}
