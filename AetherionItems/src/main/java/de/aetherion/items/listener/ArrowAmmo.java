package de.aetherion.items.listener;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

final class ArrowAmmo {

    private ArrowAmmo() {
    }

    static boolean consume(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        if (takeFrom(inventory.getItemInOffHand())) {
            return true;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (takeFrom(item)) {
                if (item.getAmount() <= 0) {
                    inventory.setItem(slot, null);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean takeFrom(ItemStack item) {
        if (item == null || !isArrow(item.getType())) {
            return false;
        }
        item.setAmount(item.getAmount() - 1);
        return true;
    }

    private static boolean isArrow(Material material) {
        return material == Material.ARROW
                || material == Material.SPECTRAL_ARROW
                || material == Material.TIPPED_ARROW;
    }
}
