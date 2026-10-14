package de.aetherion.core.api;

import org.bukkit.entity.Player;
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

    /**
     * Progression Signatures (gathering finds: {@code crystal_find}, {@code prize_crop},
     * {@code fish_trophy}). Items owns the id: this stamps it onto a find the gather plugin
     * just built, leaving that plugin's own PDC (grade, carat, kg) untouched. Returns the same stack.
     * No-op without AetherionItems.
     */
    default ItemStack stampSignature(ItemStack item, String signatureId) {
        return item;
    }

    /** A player found a Signature by hand: counts toward its Codex collection entry. */
    default void noteSignature(Player player, String signatureId) {
    }
}
