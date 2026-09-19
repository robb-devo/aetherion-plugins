package de.aetherion.items.world;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class WorldMapListener implements Listener {

    private final WorldMapService maps;

    public WorldMapListener(WorldMapService maps) {
        this.maps = maps;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!maps.isTool(player.getInventory().getItemInMainHand())) {
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
            maps.removeNearest(player.getLocation(), player);
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            player.sendMessage("§cClick a block to hang the map.");
            return;
        }
        maps.place(block, event.getBlockFace(), player);
    }
}
