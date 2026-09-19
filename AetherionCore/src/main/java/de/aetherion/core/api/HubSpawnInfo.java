package de.aetherion.core.api;

import org.bukkit.inventory.ItemStack;

/**
 * Spawn id + display name + DEV anchor icon.
 */
public record HubSpawnInfo(String id, String displayName, ItemStack anchor) {
}
