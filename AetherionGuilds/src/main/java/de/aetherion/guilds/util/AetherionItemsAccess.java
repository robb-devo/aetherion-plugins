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

    /**
     * Tells Items that the player now holds this good, so the recipes built from it (its quarry) are unlocked for
     * them the way a find in the world would have unlocked them. Nothing happens without Items, or with an Items
     * that keeps its recipe gates elsewhere.
     */
    public static void noteObtained(Player player, ItemStack stack) {
        org.bukkit.plugin.Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled() || player == null || stack == null) {
            return;
        }
        try {
            Object unlocks = items.getClass().getMethod("recipeUnlocks").invoke(items);
            if (unlocks != null) {
                unlocks.getClass().getMethod("noteObtained", Player.class, ItemStack.class).invoke(unlocks, player, stack);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // the recipe then opens the usual way: once the player has picked the good up somewhere
        }
    }

    /**
     * Opens the recipe book on "Ready to craft" (everything the player's bag can make right now); the book's first
     * page when Items has no such page.
     */
    public static void openReadyRecipes(Player player) {
        org.bukkit.plugin.Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled() || player == null) {
            return;
        }
        try {
            Object book = items.getClass().getMethod("getRecipeBookGUI").invoke(items);
            if (book != null) {
                book.getClass().getMethod("openReady", Player.class, int.class).invoke(book, player, 1);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // an Items without that page: the book's front page it is
        }
        player.performCommand("atrecipes");
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
