package de.aetherion.bossengine.listener;

import de.aetherion.bossengine.loot.SeraphineMusicBox;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Right-clicking Seraphine's music box claims your share of her loot.
 */
public class SeraphineMusicBoxListener implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (SeraphineMusicBox.click(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
