package de.aetherion.items.recipe;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.blueprint.BlueprintUnlockService;

import org.bukkit.entity.Player;

/**
 * Recipe gated behind a Surveyor blueprint unlock.
 */
public final class BlueprintUnlockRequirement implements UnlockRequirement {

    private final String blueprintId;
    private final String display;

    public BlueprintUnlockRequirement(String blueprintId) {
        this(blueprintId, "Turn in the blueprint at the Surveyor");
    }

    public BlueprintUnlockRequirement(String blueprintId, String display) {
        this.blueprintId = blueprintId == null ? "" : blueprintId;
        this.display = display == null ? "" : display;
    }

    @Override
    public boolean isUnlocked(Player player) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.blueprintUnlocks() == null || blueprintId.isBlank()) {
            return false;
        }
        return plugin.blueprintUnlocks().has(player, blueprintId);
    }

    @Override
    public String getDisplayText() {
        return display;
    }
}
