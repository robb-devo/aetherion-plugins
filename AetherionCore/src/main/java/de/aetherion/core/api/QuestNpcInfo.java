package de.aetherion.core.api;

import org.bukkit.inventory.ItemStack;

/**
 * Snapshot of a quest NPC for DEV catalogs.
 */
public record QuestNpcInfo(
        String id,
        String name,
        String questId,
        String type,
        String entityId,
        ItemStack icon
) {
}
