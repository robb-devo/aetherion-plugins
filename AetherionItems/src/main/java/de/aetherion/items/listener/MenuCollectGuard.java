package de.aetherion.items.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * A double click in your own inventory collects matching stacks from the open window as well. In a plugin menu
 * those stacks are icons or copies (trade offers, shop items, forge inputs), so taking them duplicates items.
 * Blocked for every plugin menu; real chests, barrels and shulkers behave as in vanilla.
 */
public final class MenuCollectGuard implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCollect(InventoryClickEvent event) {
        if (event.getAction() != InventoryAction.COLLECT_TO_CURSOR) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (pluginMenu(top)) {
            event.setCancelled(true);
        }
    }

    private static boolean pluginMenu(Inventory top) {
        InventoryHolder holder = top.getHolder(false);
        if (holder == null) {
            // Chest-type windows always have a block or entity behind them unless a plugin made them.
            InventoryType type = top.getType();
            return type == InventoryType.CHEST || type == InventoryType.HOPPER || type == InventoryType.DISPENSER
                    || type == InventoryType.DROPPER;
        }
        return holder.getClass().getName().startsWith("de.aetherion.");
    }
}
