package de.aetherion.core.api;

import org.bukkit.inventory.ItemStack;

/**
 * Item factory access for soft dependents (dungeon loot, etc.).
 */
public interface ItemFactoryAccess {

    ItemStack create(String itemId);
}
