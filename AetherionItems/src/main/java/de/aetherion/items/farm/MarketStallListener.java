package de.aetherion.items.farm;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class MarketStallListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MarketStallGUI)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.devAction(), PersistentDataType.STRING);
        if (action == null || !action.startsWith("seedstall:")) {
            return;
        }
        String id = action.substring("seedstall:".length());
        if (MarketStallGUI.HOE_XP_ACTION.equals(id)) {
            String fail = MarketStallGUI.buyHoeXp(player);
            if (fail != null) {
                player.sendMessage(fail);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                return;
            }
            player.sendMessage("§a✦ §f+" + MarketStallGUI.HOE_XP_PER_TRADE + " Hoe XP§a.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.6f, 1.2f);
            MarketStallGUI.open(player);
            return;
        }
        MarketStallGUI.Offer offer = MarketStallGUI.Offer.byId(id);
        if (offer == null) {
            return;
        }
        if (!MarketStallGUI.buy(player, offer)) {
            player.sendMessage("§cYou need one Compacted " + offer.cost().prettyName() + "§c.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        player.sendMessage("§a✦ §fBought " + offer.title() + "§a.");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.6f, 1.2f);
        MarketStallGUI.open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MarketStallGUI) {
            event.setCancelled(true);
        }
    }
}
