package de.aetherion.bossengine.listener;

import de.aetherion.bossengine.loot.WorldEaterBonusChest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Right-clicking the World Eater's Bonus Chest claims your share of its loot.
 */
public class WorldEaterChestListener implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (WorldEaterBonusChest.click(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
