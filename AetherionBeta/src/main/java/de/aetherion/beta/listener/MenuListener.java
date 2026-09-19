package de.aetherion.beta.listener;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.menu.AdminBetaMenu;
import de.aetherion.beta.menu.ChecklistMenu;
import de.aetherion.beta.menu.LanguageMenu;
import de.aetherion.beta.menu.RatingMenu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class MenuListener implements Listener {

    private final AetherionBeta plugin;

    public MenuListener(AetherionBeta plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof LanguageMenu.Holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                LanguageMenu.handle(plugin, player, event.getSlot());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof ChecklistMenu.Holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                ChecklistMenu.handle(plugin, player, event.getSlot());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof RatingMenu.Holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                RatingMenu.handle(plugin, player, event.getSlot());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof AdminBetaMenu.RootHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                AdminBetaMenu.handleRoot(plugin, player, event.getSlot(), event.getCurrentItem());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof AdminBetaMenu.PlayerHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                AdminBetaMenu.handlePlayer(plugin, player, event.getSlot());
            }
        }
    }

    @EventHandler
    public void onBook(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!plugin.book().isBook(item)) {
            return;
        }
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                event.setCancelled(true);
                Player player = event.getPlayer();
                if (plugin.store().get(player.getUniqueId()).lang() == null) {
                    LanguageMenu.open(plugin, player);
                } else {
                    ChecklistMenu.open(plugin, player);
                }
            }
            default -> {
            }
        }
    }
}
