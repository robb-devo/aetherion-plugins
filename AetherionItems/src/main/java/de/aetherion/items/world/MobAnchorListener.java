package de.aetherion.items.world;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class MobAnchorListener implements Listener {

    private final MobZoneService zones;
    private final AreaService areas;

    public MobAnchorListener(MobZoneService zones, AreaService areas) {
        this.zones = zones;
        this.areas = areas;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean borderlands = zones.isBorderlandsAnchor(hand);
        boolean eldervale = zones.isEldervaleAnchor(hand);
        boolean mob = zones.isAnchor(hand);
        if (!borderlands && !eldervale && !mob) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("aetherion.dev")) {
            player.sendMessage("§cDEV only.");
            return;
        }

        Action action = event.getAction();
        if (borderlands && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            int next = zones.cycleBorderlandsRadius(hand);
            player.sendMessage("§6Borderlands radius §f" + next + "m§6.");
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            zones.removeNearest(player.getLocation(), player);
            if (borderlands && areas != null) {
                areas.removeNearest(player.getLocation(), AreaType.BORDERLANDS, player);
            }
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            player.sendMessage(borderlands
                    ? "§cRight-click a block to place Borderlands."
                    : eldervale
                    ? "§cRight-click a block to place Eldervale deep."
                    : "§cRight-click a block to place a mob zone.");
            return;
        }
        var at = block.getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        if (borderlands) {
            int radius = zones.borderlandsRadiusOf(hand);
            if (zones.placeBorderlands(at, player, radius) != null && areas != null) {
                areas.place(at, AreaType.BORDERLANDS, player, radius);
            }
            return;
        }
        if (eldervale) {
            zones.placeEldervale(at, player, MobZoneService.ELDERVALE_DEFAULT_RADIUS);
            return;
        }
        zones.place(at, player);
    }
}
