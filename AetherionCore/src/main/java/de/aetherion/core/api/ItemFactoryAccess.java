package de.aetherion.core.api;

import org.bukkit.inventory.ItemStack;

/**
 * Item factory access for soft dependents (dungeon loot, etc.).
 */
public interface ItemFactoryAccess {

    ItemStack create(String itemId);

    /** CompressedResource.valueOf(resourceName).compressed(), or null. */
    default ItemStack compressed(String resourceName) {
        return null;
    }

    /** CompressedResource.valueOf(resourceName).compacted(), or null. */
    default ItemStack compacted(String resourceName) {
        return null;
    }

    /** Random CompressedResource compressed/compacted stack, matching dungeon loot rolls. */
    default ItemStack randomCompressed(boolean compacted) {
        return null;
    }

    /** Persistent item id, or null. */
    default String itemId(ItemStack item) {
        return null;
    }

    /** Floor-scaled dungeon weapon schematic, or null. */
    default ItemStack dungeonWeaponSchematic(int floor) {
        return null;
    }
}
