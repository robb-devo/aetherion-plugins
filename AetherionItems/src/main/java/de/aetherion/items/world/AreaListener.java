package de.aetherion.items.world;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class AreaListener implements Listener {

    private final AreaService areas;

    public AreaListener(AreaService areas) {
        this.areas = areas;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!areas.isTool(player.getInventory().getItemInMainHand())) {
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
        AreaType type = areas.typeOf(player.getInventory().getItemInMainHand());
        if (type == null) {
            player.sendMessage("§cUnknown area on this tool.");
            return;
        }
        if (player.isSneaking()) {
            areas.removeNearest(player.getLocation(), type, player);
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            player.sendMessage("§cClick a block to mark this area.");
            return;
        }
        areas.place(block.getLocation().add(0.5, 0.5, 0.5), type, player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        areas.revealToLater(event.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        areas.revealToLater(event.getPlayer());
    }
}
