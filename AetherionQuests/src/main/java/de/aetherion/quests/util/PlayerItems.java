package de.aetherion.quests.util;


import de.aetherion.core.AetherKeys;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;


public final class PlayerItems {


    private PlayerItems() {
    }


    /**
     * Count by vanilla material name, or by Aetherion item id (PDC {@code aetherion:item}).
     */
    public static int count(Player player, String target) {
        if (player == null || target == null || target.isBlank()) {
            return 0;
        }
        Material material = Material.matchMaterial(target);
        if (material != null) {
            return count(player, material);
        }
        return countCustom(player, target);
    }


    public static int count(Player player, Material material) {

        if (player == null || material == null) {
            return 0;
        }

        int amount = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }

        return amount;

    }


    public static void remove(Player player, String target, int amount) {
        if (player == null || target == null || target.isBlank() || amount <= 0) {
            return;
        }
        Material material = Material.matchMaterial(target);
        if (material != null) {
            remove(player, material, amount);
            return;
        }
        removeCustom(player, target, amount);
    }


    public static void remove(Player player, Material material, int amount) {

        if (player == null || material == null || amount <= 0) {
            return;
        }

        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {

            ItemStack item = contents[slot];

            if (item == null || item.getType() != material) {
                continue;
            }

            int remove = Math.min(item.getAmount(), remaining);
            int newAmount = item.getAmount() - remove;

            if (newAmount <= 0) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(newAmount);
            }

            remaining -= remove;

        }

    }


    private static int countCustom(Player player, String itemId) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (matchesCustom(item, itemId)) {
                amount += item.getAmount();
            }
        }
        return amount;
    }


    private static void removeCustom(Player player, String itemId, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!matchesCustom(item, itemId)) {
                continue;
            }
            int take = Math.min(item.getAmount(), remaining);
            int left = item.getAmount() - take;
            if (left <= 0) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(left);
            }
            remaining -= take;
        }
    }


    private static boolean matchesCustom(ItemStack item, String itemId) {
        if (item == null || item.getType().isAir() || itemId == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String id = meta.getPersistentDataContainer().get(AetherKeys.ITEM_ID, PersistentDataType.STRING);
        return id != null && id.equalsIgnoreCase(itemId);
    }

}
