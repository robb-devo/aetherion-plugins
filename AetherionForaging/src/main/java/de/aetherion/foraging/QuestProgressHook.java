package de.aetherion.foraging;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Reflection bridge to AetherionQuests when tree collapse skips BlockBreakEvent.
 */
final class QuestProgressHook {

    private QuestProgressHook() {
    }

    static void noteBroken(Player player, Material material, int amount) {
        if (player == null || material == null || amount <= 0) {
            return;
        }
        try {
            Class<?> type = Class.forName("de.aetherion.quests.bridge.QuestProgressBridge");
            type.getMethod("noteBroken", Player.class, Material.class, int.class)
                    .invoke(null, player, material, amount);
        } catch (Throwable ignored) {
            // Quests missing or older JAR
        }
    }

    /** False until Forager finishes his chop lesson (harbour onboarding). */
    static boolean canChopTrees(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Object ok = Class.forName("de.aetherion.quests.bridge.QuestProgressBridge")
                    .getMethod("canChopTrees", Player.class)
                    .invoke(null, player);
            return !(ok instanceof Boolean b) || b;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
