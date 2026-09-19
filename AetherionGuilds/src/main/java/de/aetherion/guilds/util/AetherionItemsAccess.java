package de.aetherion.guilds.util;

import de.aetherion.core.AetherKeys;

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
        try {
            Object plugin = Class.forName("de.aetherion.items.AetherionItems").getMethod("getInstance").invoke(null);
            if (plugin == null) {
                return false;
            }
            Object progress = plugin.getClass().getMethod("progress").invoke(plugin);
            if (progress == null) {
                return false;
            }
            Object result = progress.getClass().getMethod("island", Player.class).invoke(progress, player);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static String islandHint() {
        if (!available()) {
            return "§7Reach Aetherion Level §f20 §7to claim your island.";
        }
        try {
            Object plugin = Class.forName("de.aetherion.items.AetherionItems").getMethod("getInstance").invoke(null);
            Object progress = plugin == null ? null : plugin.getClass().getMethod("progress").invoke(plugin);
            Object result = progress == null ? null : progress.getClass().getMethod("islandHint").invoke(progress);
            return result instanceof String hint ? hint : "§7Reach Aetherion Level §f20 §7to claim your island.";
        } catch (ReflectiveOperationException ignored) {
            return "§7Reach Aetherion Level §f20 §7to claim your island.";
        }
    }

    public static boolean guildUnlocked(Player player) {
        if (!available() || player == null) {
            return true;
        }
        try {
            Object plugin = Class.forName("de.aetherion.items.AetherionItems").getMethod("getInstance").invoke(null);
            if (plugin == null) {
                return false;
            }
            Object progress = plugin.getClass().getMethod("progress").invoke(plugin);
            if (progress == null) {
                return false;
            }
            Object result = progress.getClass().getMethod("guild", Player.class).invoke(progress, player);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static String guildHint() {
        if (!available()) {
            return "§7Reach Aetherion Level §f75 §7to unlock.";
        }
        try {
            Object plugin = Class.forName("de.aetherion.items.AetherionItems").getMethod("getInstance").invoke(null);
            Object progress = plugin == null ? null : plugin.getClass().getMethod("progress").invoke(plugin);
            Object result = progress == null ? null : progress.getClass().getMethod("guildHint").invoke(progress);
            return result instanceof String hint ? hint : "§7Reach Aetherion Level §f75 §7to unlock.";
        } catch (ReflectiveOperationException ignored) {
            return "§7Reach Aetherion Level §f75 §7to unlock.";
        }
    }

    public static long coins(Player player) {
        Object coins = coinsService();
        if (coins == null || player == null) {
            return 0L;
        }
        try {
            Object value = coins.getClass().getMethod("get", Player.class).invoke(coins, player);
            return value instanceof Number number ? number.longValue() : 0L;
        } catch (ReflectiveOperationException ignored) {
            return 0L;
        }
    }

    public static boolean takeCoins(Player player, long amount) {
        Object coins = coinsService();
        if (coins == null || player == null || amount <= 0L) {
            return amount <= 0L;
        }
        try {
            Object value = coins.getClass().getMethod("take", Player.class, long.class).invoke(coins, player, amount);
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static void addCoins(Player player, long amount) {
        Object coins = coinsService();
        if (coins == null || player == null || amount <= 0L) {
            return;
        }
        try {
            coins.getClass().getMethod("add", Player.class, long.class).invoke(coins, player, amount);
        } catch (ReflectiveOperationException ignored) {
        }
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

    private static Object coinsService() {
        if (!available()) {
            return null;
        }
        try {
            Object plugin = Class.forName("de.aetherion.items.AetherionItems").getMethod("getInstance").invoke(null);
            if (plugin == null) {
                return null;
            }
            return plugin.getClass().getMethod("getCoins").invoke(plugin);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
