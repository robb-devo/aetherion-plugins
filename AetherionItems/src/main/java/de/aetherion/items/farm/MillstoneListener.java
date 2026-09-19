package de.aetherion.items.farm;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class MillstoneListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MillstoneGUI gui)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        if (raw >= MillstoneGUI.SIZE) {
            return;
        }
        if (raw == MillstoneGUI.INPUT_SLOT) {
            return;
        }
        event.setCancelled(true);
        if (raw == MillstoneGUI.BACK_SLOT) {
            returnSlot(player, event.getView().getTopInventory().getItem(MillstoneGUI.INPUT_SLOT));
            event.getView().getTopInventory().setItem(MillstoneGUI.INPUT_SLOT, null);
            gui.markConsumed();
            RootCellarHubGUI.open(player);
            return;
        }
        if (raw != MillstoneGUI.MILL_SLOT) {
            return;
        }
        ItemStack input = event.getView().getTopInventory().getItem(MillstoneGUI.INPUT_SLOT);
        AetherionItems items = AetherionItems.getInstance();
        ItemManager manager = items == null ? null : items.getItemManager();
        String id = manager == null ? null : manager.getItemId(input);
        CompressedResource crop = CompressedResource.byItemId(id);
        if (crop == null || !crop.canRefine() || id == null || !id.equalsIgnoreCase(crop.compactedId())) {
            player.sendMessage("§cNeed one §bCompacted§c farm crop (Wheat / Carrot / Potato).");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        if (gui.ritual().isBusy(gui.stationId()) || gui.ritual().isPlayerBusy(player.getUniqueId())) {
            player.sendMessage("§eMillstone is busy.");
            return;
        }
        ItemStack used = input.clone();
        used.setAmount(input.getAmount() - 1);
        event.getView().getTopInventory().setItem(MillstoneGUI.INPUT_SLOT, used.getAmount() <= 0 ? null : used);
        gui.markConsumed();
        if (!gui.ritual().start(player, gui.stationId(), gui.stationAt(), crop)) {
            returnSlot(player, crop.compacted());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MillstoneGUI)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < MillstoneGUI.SIZE && slot != MillstoneGUI.INPUT_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MillstoneGUI gui)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (gui.consumed()) {
            event.getInventory().setItem(MillstoneGUI.INPUT_SLOT, null);
            return;
        }
        returnSlot(player, event.getInventory().getItem(MillstoneGUI.INPUT_SLOT));
        event.getInventory().setItem(MillstoneGUI.INPUT_SLOT, null);
    }

    private static void returnSlot(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
