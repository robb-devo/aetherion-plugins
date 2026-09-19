package de.aetherion.items.farm;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class RootCellarListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof RootCellarHubGUI) {
            event.setCancelled(true);
            if (event.getRawSlot() == RootCellarHubGUI.CELLAR_SLOT) {
                RootCellarCraftGUI.open(player, RootCellarCrafts.Category.CELLAR);
                return;
            }
            if (event.getRawSlot() == RootCellarHubGUI.PANTRY_SLOT) {
                RootCellarCraftGUI.open(player, RootCellarCrafts.Category.PANTRY);
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof RootCellarCraftGUI gui) {
            event.setCancelled(true);
            if (event.getRawSlot() == RootCellarCraftGUI.BACK_SLOT) {
                RootCellarHubGUI.open(player);
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) {
                return;
            }
            ItemMeta meta = clicked.getItemMeta();
            String action = meta.getPersistentDataContainer().get(ItemKeys.devAction(), PersistentDataType.STRING);
            if (action == null || !action.startsWith("rootcraft:")) {
                return;
            }
            String id = action.substring("rootcraft:".length());
            RootCellarCrafts.Offer offer = RootCellarCrafts.byId(id);
            if (offer == null) {
                return;
            }
            AetherionItems plugin = AetherionItems.getInstance();
            ItemManager manager = plugin == null ? null : plugin.getItemManager();
            if (!RootCellarCrafts.canAfford(player, offer, manager)) {
                player.sendMessage("§cMissing materials.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                RootCellarCraftGUI.open(player, gui.category());
                return;
            }

            CompressedResource refine = RootCellarCrafts.refineCrop(offer);
            if (refine != null) {
                craftCellar(player, offer, refine, manager, plugin);
                return;
            }

            if (!RootCellarCrafts.craft(player, offer, manager)) {
                player.sendMessage("§cCraft failed.");
                return;
            }
            player.sendMessage("§a✦ §fCrafted " + offer.title() + "§a.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.45f, 1.35f);
            RootCellarCraftGUI.open(player, gui.category());
        }
    }

    private static void craftCellar(
            Player player,
            RootCellarCrafts.Offer offer,
            CompressedResource crop,
            ItemManager manager,
            AetherionItems plugin
    ) {
        MillstoneRitual ritual = plugin == null ? null : plugin.getMillstoneRitual();
        var hit = MillstoneCabinet.findNearest(player.getLocation(), 16.0);
        if (ritual != null && hit != null) {
            if (ritual.isBusy(hit.getUniqueId()) || ritual.isPlayerBusy(player.getUniqueId())) {
                player.sendMessage("§eMillstone is busy.");
                return;
            }
            if (!RootCellarCrafts.consumeCosts(player, offer, manager)) {
                player.sendMessage("§cMissing materials.");
                return;
            }
            if (!ritual.start(player, hit.getUniqueId(), hit.getLocation(), crop)) {
                player.getInventory().addItem(crop.compacted()).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
            return;
        }
        // No mill prop nearby — still craft instantly from the shelf.
        if (!RootCellarCrafts.craft(player, offer, manager)) {
            player.sendMessage("§cCraft failed.");
            return;
        }
        player.sendMessage("§a✦ §fCrafted " + offer.title() + "§a.");
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.7f, 1.2f);
        RootCellarCraftGUI.open(player, RootCellarCrafts.Category.CELLAR);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RootCellarHubGUI
                || event.getInventory().getHolder() instanceof RootCellarCraftGUI) {
            event.setCancelled(true);
        }
    }
}
