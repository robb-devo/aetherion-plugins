package de.aetherion.dungeons.instance;

import org.bukkit.entity.Player;

public final class QuestHudHook {

    private QuestHudHook() {
    }

    public static void suppress(Player player) {
        invoke("suppress", player);
    }

    public static void unsuppress(Player player) {
        invoke("unsuppress", player);
    }

    private static void invoke(String method, Player player) {
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
