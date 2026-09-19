package de.aetherion.items.blueprint;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BlueprintForgeListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlueprintForgeGUI gui)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        // Allow placing into tool/stone slots from player inv.
        if (raw >= BlueprintForgeGUI.SIZE) {
            return;
        }
        if (raw == BlueprintForgeGUI.TOOL_SLOT || raw == BlueprintForgeGUI.STONE_SLOT) {
            return;
        }
        event.setCancelled(true);
        if (raw != BlueprintForgeGUI.APPLY_SLOT) {
            return;
        }
        ItemManager items = gui.itemManager();
        ItemStack tool = top.getItem(BlueprintForgeGUI.TOOL_SLOT);
        ItemStack stone = top.getItem(BlueprintForgeGUI.STONE_SLOT);
        ItemStack result = BlueprintUpgrade.apply(items, tool, stone);
        if (result == null) {
            player.sendMessage("§cNeed a Blueprint tool + matching Upgrade Stone (next tier).");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }

        AetherionItems plugin = AetherionItems.getInstance();
        BlueprintForgeRitual ritual = plugin == null ? null : plugin.getBlueprintForgeRitual();
        if (ritual == null) {
            // Fallback: instant upgrade if ritual service missing.
            top.setItem(BlueprintForgeGUI.TOOL_SLOT, result);
            ItemStack used = stone.clone();
            used.setAmount(stone.getAmount() - 1);
            top.setItem(BlueprintForgeGUI.STONE_SLOT, used.getAmount() <= 0 ? null : used);
            player.sendMessage("§aBlueprint upgraded to Tier §f"
                    + BlueprintUpgrade.roman(BlueprintUpgrade.tier(result)) + "§a.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
            return;
        }

        // Consume inputs now; ritual holds the upgraded result.
        ItemStack used = stone.clone();
        used.setAmount(stone.getAmount() - 1);
        top.setItem(BlueprintForgeGUI.TOOL_SLOT, null);
        top.setItem(BlueprintForgeGUI.STONE_SLOT, used.getAmount() <= 0 ? null : used);
        gui.markConsumed();

        // Return leftover stones immediately; tool goes into the frame.
        if (used.getAmount() > 0) {
            returnSlot(player, used);
            top.setItem(BlueprintForgeGUI.STONE_SLOT, null);
        }

        if (!ritual.start(player, result)) {
            // Ritual refused — give result back so nothing is lost.
            returnSlot(player, result);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BlueprintForgeGUI) {
            for (int slot : event.getRawSlots()) {
                if (slot < BlueprintForgeGUI.SIZE
                        && slot != BlueprintForgeGUI.TOOL_SLOT
                        && slot != BlueprintForgeGUI.STONE_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlueprintForgeGUI gui)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getInventory();
        if (gui.skipReturn()) {
            top.setItem(BlueprintForgeGUI.TOOL_SLOT, null);
            top.setItem(BlueprintForgeGUI.STONE_SLOT, null);
            return;
        }
        returnSlot(player, top.getItem(BlueprintForgeGUI.TOOL_SLOT));
        returnSlot(player, top.getItem(BlueprintForgeGUI.STONE_SLOT));
        top.setItem(BlueprintForgeGUI.TOOL_SLOT, null);
        top.setItem(BlueprintForgeGUI.STONE_SLOT, null);
    }

    private static void returnSlot(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        var leftover = player.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }
}
