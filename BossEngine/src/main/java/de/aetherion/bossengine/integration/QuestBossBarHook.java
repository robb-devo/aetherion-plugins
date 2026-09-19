package de.aetherion.bossengine.integration;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Hides the AetherionQuests boss bar while a real boss bar is on screen.
 * Reflection so BossEngine still loads if Quests is missing.
 */
public final class QuestBossBarHook {

    private QuestBossBarHook() {
    }

    public static void suppress(Player player) {
        invokePlayer("suppress", player);
    }

    public static void unsuppress(Player player) {
        invokePlayer("unsuppress", player);
    }

    public static void unsuppress(UUID playerId) {
        if (playerId == null) {
            return;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.ui.QuestProgressDisplay");
            type.getMethod("unsuppress", UUID.class).invoke(null, playerId);
        } catch (Throwable ignored) {
            // Quests not installed or older JAR without UUID unsuppress
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
            // Quests not installed or older JAR without suppress API
        }
    }
}
