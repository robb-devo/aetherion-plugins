package de.aetherion.quests.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Paper slip from Tackle — turn in at the Fishmonger to open the dock shop.
 */
public final class DockShopPass {

    public static final String TARGET = "DOCK_SHOP_PASS";

    private DockShopPass() {
    }

    public static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "dock_shop_pass");
    }

    public static ItemStack create(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bDock Shop Pass");
            meta.setLore(List.of(
                    "§7Signed by Tackle. Barely.",
                    "",
                    "§eGive this to the Fishmonger",
                    "§eto open the dock stall.",
                    "§8Or go fish first — your call."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            try {
                meta.getPersistentDataContainer().set(
                        de.aetherion.core.AetherKeys.ITEM_ID,
                        PersistentDataType.STRING,
                        "dock_shop_pass"
                );
            } catch (NoClassDefFoundError ignored) {
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isPass(JavaPlugin plugin, ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    public static int count(Player player, JavaPlugin plugin) {
        if (player == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isPass(plugin, stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public static boolean takeOne(Player player, JavaPlugin plugin) {
        if (player == null) {
            return false;
        }
        var inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isPass(plugin, stack)) {
                continue;
            }
            int amount = stack.getAmount();
            if (amount <= 1) {
                inv.setItem(slot, null);
            } else {
                stack.setAmount(amount - 1);
            }
            return true;
        }
        return false;
    }
}
