package de.aetherion.farming.isle;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Inventory counting for orders and the bakehouse. "Plain" crops are vanilla stacks only —
 * a custom item that happens to be a carrot (prize, compressed, quest item) is never eaten.
 */
final class OrderItems {

    private OrderItems() {
    }

    static boolean isPlain(ItemStack stack, Material material) {
        if (stack == null || stack.getType() != material) {
            return false;
        }
        if (!stack.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasCustomModelData() && meta.getPersistentDataContainer().isEmpty();
    }

    static int countPlain(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isPlain(stack, material)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    static void takePlain(PlayerInventory inventory, Material material, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!isPlain(stack, material)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            stack.setAmount(stack.getAmount() - take);
            contents[slot] = stack.getAmount() <= 0 ? null : stack;
        }
        inventory.setStorageContents(contents);
    }

    static String itemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
    }

    static int countById(PlayerInventory inventory, String id) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (id != null && id.equals(itemId(stack))) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    static void takeById(PlayerInventory inventory, String id, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !id.equals(itemId(stack))) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            stack.setAmount(stack.getAmount() - take);
            contents[slot] = stack.getAmount() <= 0 ? null : stack;
        }
        inventory.setStorageContents(contents);
    }

    /** First storage slot holding a prize (of {@code crop}, or any when null); -1 when none. */
    static int firstPrize(PlayerInventory inventory, PrizeCrops prizes, IsleCrop crop) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            IsleCrop found = prizes.cropOf(contents[slot]);
            if (found != null && (crop == null || found == crop)) {
                return slot;
            }
        }
        return -1;
    }

    static int countPrizes(PlayerInventory inventory, PrizeCrops prizes, IsleCrop crop) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            IsleCrop found = prizes.cropOf(stack);
            if (found != null && (crop == null || found == crop)) {
                total += stack.getAmount();
            }
        }
        return total;
    }
}
