package de.aetherion.core.api;

import org.bukkit.inventory.ItemStack;

/**
 * Pet id + display name + DEV icon.
 */
public record PetCatalogItem(String id, String displayName, ItemStack icon) {
}
