package de.aetherion.farming;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Hides the AetherionQuests boss bar while the bird scare bar is on screen.
 * Reflection so farming still loads if Quests is missing.
 */
final class QuestBossBarHook {

    private QuestBossBarHook() {
    }

    static void suppress(Player player) {
        invokePlayer("suppress", player);
    }

    static void unsuppress(Player player) {
        invokePlayer("unsuppress", player);
    }

    static void unsuppress(UUID playerId) {
        if (playerId == null) {
            return;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.ui.QuestProgressDisplay");
            type.getMethod("unsuppress", UUID.class).invoke(null, playerId);
        } catch (Throwable ignored) {
        }
    }

    private static void invokePlayer(String method, Player player) {
        if (player == null) {
            return;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.ui.QuestProgressDisplay");
            type.getMethod(method, Player.class).invoke(null, player);
        } catch (Throwable ignored) {
        }
    }
}
