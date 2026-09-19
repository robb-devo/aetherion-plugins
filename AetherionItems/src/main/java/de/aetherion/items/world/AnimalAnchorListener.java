package de.aetherion.items.world;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class AnimalAnchorListener implements Listener {

    private final AnimalZoneService zones;

    public AnimalAnchorListener(AnimalZoneService zones) {
        this.zones = zones;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!zones.isAnchor(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("aetherion.dev")) {
            player.sendMessage("§cDEV only.");
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            zones.removeNearest(player.getLocation(), player);
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            player.sendMessage("§cRight-click a block to place an animal zone.");
            return;
        }
        zones.place(block.getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5), player);
    }
}
