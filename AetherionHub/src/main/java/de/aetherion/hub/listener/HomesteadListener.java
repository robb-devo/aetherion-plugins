package de.aetherion.hub.listener;

import de.aetherion.hub.item.HomesteadMarker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class HomesteadListener implements Listener {

    private final HomesteadMarker marker;

    public HomesteadListener(HomesteadMarker marker) {
        this.marker = marker;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!marker.isMarker(item)) {
            return;
        }

        event.setCancelled(true);

        if (marker.use(player, item)) {
            item.setAmount(item.getAmount() - 1);
        }
    }
}
