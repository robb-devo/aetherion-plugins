package de.aetherion.items.social;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class PlayerSocialListener implements Listener {

    private final ItemManager items;
    private final PlayerCardMenu cards;
    private final TradeMenu trades;

    public PlayerSocialListener(AetherionItems plugin) {
        this.items = plugin.getItemManager();
        this.cards = new PlayerCardMenu(plugin);
        this.trades = new TradeMenu(plugin);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClickPlayer(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        if (target.hasMetadata("NPC")) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        String itemId = items.getItemId(player.getInventory().getItemInMainHand());
        if (itemId != null && itemId.toLowerCase().contains("void_stick")) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            trades.request(player, target);
        } else {
            cards.open(player, target);
        }
    }

    @EventHandler
    public void onMenu(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof PlayerCardMenu.Holder) {
            event.setCancelled(true);
            cards.handle(player, event.getRawSlot());
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof TradeMenu.Holder) {
            trades.handle(player, event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PlayerCardMenu.Holder
                || event.getView().getTopInventory().getHolder() instanceof TradeMenu.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getView().getTopInventory().getHolder() instanceof TradeMenu.Holder) {
            trades.onClose(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (trades.isTrading(event.getPlayer())) {
            trades.onClose(event.getPlayer());
        }
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.party() != null) {
            plugin.party().onQuit(event.getPlayer());
        }
    }
}
