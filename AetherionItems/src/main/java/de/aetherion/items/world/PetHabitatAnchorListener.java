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

public final class PetHabitatAnchorListener implements Listener {

    private final PetHabitatZoneService zones;

    public PetHabitatAnchorListener(PetHabitatZoneService zones) {
        this.zones = zones;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!zones.isAnchor(hand)) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("aetherion.dev")) {
            player.sendMessage("§cDEV only.");
            return;
        }

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            int next = zones.cycleRadius(hand);
            PetHabitatKind habitat = zones.habitatOf(hand);
            String name = habitat == null ? "Pet habitat" : habitat.coloredName();
            player.sendMessage(name + " §7radius §f" + next + "m§7.");
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            zones.removeNearest(player.getLocation(), player);
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            player.sendMessage("§cRight-click a block to place a pet habitat.");
            return;
        }
        PetHabitatKind habitat = zones.habitatOf(hand);
        if (habitat == null) {
            player.sendMessage("§cUnknown pet habitat stick.");
            return;
        }
        zones.place(
                block.getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5),
                habitat,
                player,
                zones.radiusOf(hand)
        );
    }
}
