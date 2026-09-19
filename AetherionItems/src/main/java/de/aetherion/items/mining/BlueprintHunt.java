package de.aetherion.items.mining;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.blueprint.BlueprintUnlockService;

import org.bukkit.entity.Player;

/**
 * Ore-troll / blueprint hunt unlocks after speaking with the Surveyor.
 */
public final class BlueprintHunt {

    public static final String QUEST_ID = "hidden_blueprints";

    private BlueprintHunt() {
    }

    public static boolean unlocked(Player player) {
        if (player == null) {
            return false;
        }
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.blueprintUnlocks() == null) {
            return false;
        }
        return items.blueprintUnlocks().isHuntEnabled(player);
    }

    public static boolean enable(Player player) {
        AetherionItems items = AetherionItems.getInstance();
        if (items == null || items.blueprintUnlocks() == null) {
            return false;
        }
        return items.blueprintUnlocks().enableHunt(player);
    }
}
