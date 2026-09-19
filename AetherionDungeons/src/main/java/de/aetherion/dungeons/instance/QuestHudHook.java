package de.aetherion.dungeons.instance;

import de.aetherion.core.api.QuestBars;

import org.bukkit.entity.Player;

/**
 * Thin adapter — quest-bar suppress goes through {@link QuestBars}.
 */
public final class QuestHudHook {

    private QuestHudHook() {
    }

    public static void suppress(Player player) {
        QuestBars.suppress(player);
    }

    public static void unsuppress(Player player) {
        QuestBars.unsuppress(player);
    }
}
