package de.aetherion.items.menu.dev;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class DevMenuListener implements Listener {

    private final DevMenu menu;

    public DevMenuListener(DevMenu menu) {
        this.menu = menu;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DevMenu.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (holder.page() == DevMenu.Page.BOOSTER_LAB) {
            menu.handleBoosterLab(player, event);
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        menu.handle(player, event.getCurrentItem(), event.getRawSlot(), event.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DevMenu.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DevMenu.Holder holder)) {
            return;
        }
        if (holder.page() != DevMenu.Page.BOOSTER_LAB) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        menu.returnLabItem(player, event.getInventory());
    }
}
