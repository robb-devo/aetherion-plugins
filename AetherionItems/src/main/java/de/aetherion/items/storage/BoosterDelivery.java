package de.aetherion.items.storage;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.BoosterType;
import de.aetherion.items.util.InventoryDrops;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Quest / boss / treasure rewards that are boosters go into the Booster Sack
 * when the sack is in the player's inventory. Crafted boosters stay in inv.
 */
public final class BoosterDelivery {

    private BoosterDelivery() {
    }

    /**
     * @return true if the entire stack was stored in the sack
     */
    public static boolean giveReward(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return true;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || plugin.getSackInventory() == null) {
            InventoryDrops.give(player, stack);
            return false;
        }
        ItemManager items = plugin.getItemManager();
        BoosterType type = items == null ? null : items.getBoosterType(stack);
        if (type == null || !BoosterSackMenu.hasBoosterSack(player)) {
            InventoryDrops.give(player, stack);
            return false;
        }

        int amount = Math.max(1, stack.getAmount());
        int leftover = plugin.getSackInventory().boosters().tryDeposit(player, type, amount);
        int stored = amount - leftover;
        if (stored > 0) {
            player.sendActionBar("§d+" + stored + " booster" + (stored == 1 ? "" : "s") + " §7→ Booster Sack");
        }
        if (leftover <= 0) {
            return true;
        }
        ItemStack rest = stack.clone();
        rest.setAmount(leftover);
        InventoryDrops.give(player, rest);
        return false;
    }

    /**
     * Same as {@link #giveReward} but does not use InventoryDrops level-gate
     * (quest rewards always land in inventory if sack is full/missing).
     */
    public static void giveQuestReward(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        ItemManager items = plugin == null ? null : plugin.getItemManager();
        BoosterType type = items == null ? null : items.getBoosterType(stack);
        if (type != null && plugin != null && plugin.getSackInventory() != null
                && BoosterSackMenu.hasBoosterSack(player)) {
            int amount = Math.max(1, stack.getAmount());
            int leftover = plugin.getSackInventory().boosters().tryDeposit(player, type, amount);
            int stored = amount - leftover;
            if (stored > 0) {
                player.sendActionBar("§d+" + stored + " booster" + (stored == 1 ? "" : "s") + " §7→ Booster Sack");
            }
            if (leftover <= 0) {
                return;
            }
            ItemStack rest = stack.clone();
            rest.setAmount(leftover);
            spillToInventory(player, rest);
            return;
        }
        spillToInventory(player, stack);
    }

    private static void spillToInventory(Player player, ItemStack stack) {
        var leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(left ->
                player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
