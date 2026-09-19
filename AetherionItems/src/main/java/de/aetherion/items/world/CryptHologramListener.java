package de.aetherion.items.world;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class CryptHologramListener implements Listener {

    private final CryptHologramService holograms;

    public CryptHologramListener(CryptHologramService holograms) {
        this.holograms = holograms;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!holograms.isTool(player.getInventory().getItemInMainHand())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("aetherion.dev")) {
            player.sendMessage("§cDEV only.");
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            holograms.removeNearest(player.getLocation(), player);
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            player.sendMessage("§cClick a block to place the Crypt hologram.");
            return;
        }
        holograms.place(block.getLocation(), player);
    }
}
