package de.aetherion.core.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Hides the quest boss bar while another plugin's bar is on screen.
 * No-op when AetherionQuests is offline.
 */
public final class QuestBars {

    private QuestBars() {
    }

    public static void suppress(Player player) {
        QuestProgressAccess access = AetherServices.quests();
        if (access != null) {
            access.suppress(player);
        }
    }

    public static void unsuppress(Player player) {
        QuestProgressAccess access = AetherServices.quests();
        if (access != null) {
            access.unsuppress(player);
        }
    }

    public static void unsuppress(UUID playerId) {
        QuestProgressAccess access = AetherServices.quests();
        if (access != null) {
            access.unsuppress(playerId);
        }
    }
}
