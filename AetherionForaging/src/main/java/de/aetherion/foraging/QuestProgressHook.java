package de.aetherion.foraging;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.QuestProgressAccess;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Typed bridge to AetherionQuests when tree collapse skips BlockBreakEvent.
 */
final class QuestProgressHook {

    private QuestProgressHook() {
    }

    static void noteBroken(Player player, Material material, int amount) {
        QuestProgressAccess quests = AetherServices.quests();
        if (quests != null) {
            quests.noteBroken(player, material, amount);
        }
    }

    /** False until Forager finishes his chop lesson (harbour onboarding). */
    static boolean canChopTrees(Player player) {
        if (player == null) {
            return false;
        }
        QuestProgressAccess quests = AetherServices.quests();
        if (quests == null) {
            return true;
        }
        return quests.canChopTrees(player);
    }
}
