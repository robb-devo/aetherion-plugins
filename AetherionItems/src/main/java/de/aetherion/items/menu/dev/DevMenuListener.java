package de.aetherion.items.menu.dev;

import de.aetherion.items.AetherionItems;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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

    /** DEV search prompt: the next chat line after "Search" is the query, never broadcast. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSearchChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!menu.takeSearchPrompt(player)) {
            return;
        }
        event.setCancelled(true);
        String query = event.getMessage();
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                menu.openSearch(player, query);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menu.clearSearchPrompt(event.getPlayer().getUniqueId());
    }
}
