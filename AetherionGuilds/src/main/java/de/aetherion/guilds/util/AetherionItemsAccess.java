package de.aetherion.guilds.util;

import de.aetherion.core.AetherKeys;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CoinAccess;
import de.aetherion.core.api.ProgressAccess;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class AetherionItemsAccess {

    private static final NamespacedKey ITEM = AetherKeys.ITEM_ID;

    private AetherionItemsAccess() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("AetherionItems");
    }

    public static boolean islandUnlocked(Player player) {
        if (!available() || player == null) {
            return true;
        }
        ProgressAccess progress = AetherServices.progress();
        return progress != null && progress.island(player);
    }

    public static String islandHint() {
        ProgressAccess progress = AetherServices.progress();
        if (progress == null) {
            return "§7Reach Aetherion Level §f20 §7to claim your island.";
        }
        return progress.islandHint();
    }

    public static boolean guildUnlocked(Player player) {
        if (!available() || player == null) {
            return true;
        }
        ProgressAccess progress = AetherServices.progress();
        return progress != null && progress.guild(player);
    }

    public static String guildHint() {
        ProgressAccess progress = AetherServices.progress();
        if (progress == null) {
            return "§7Reach Aetherion Level §f75 §7to unlock.";
        }
        return progress.guildHint();
    }

    public static long coins(Player player) {
        CoinAccess coins = AetherServices.coins();
        if (coins == null || player == null) {
            return 0L;
        }
        return coins.get(player);
    }

    public static boolean takeCoins(Player player, long amount) {
        CoinAccess coins = AetherServices.coins();
        if (coins == null || player == null || amount <= 0L) {
            return amount <= 0L;
        }
        return coins.take(player, amount);
    }

    public static void addCoins(Player player, long amount) {
        CoinAccess coins = AetherServices.coins();
        if (coins == null || player == null || amount <= 0L) {
            return;
        }
        coins.add(player, amount);
    }

    public static String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(ITEM, PersistentDataType.STRING);
    }

    public static int count(Player player, String itemId) {
        if (player == null || itemId == null || itemId.isBlank()) {
            return 0;
        }
        int have = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (itemId.equals(itemId(item))) {
                have += item.getAmount();
            }
        }
        return have;
    }

    public static void take(Player player, String itemId, int amount) {
        if (player == null || itemId == null || amount <= 0) {
            return;
        }
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!itemId.equals(itemId(item))) {
                continue;
            }
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
            left -= take;
            if (left <= 0) {
                return;
            }
        }
    }
}
