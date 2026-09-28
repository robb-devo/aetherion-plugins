package de.aetherion.bossengine.listener;

import de.aetherion.bossengine.loot.HollowReliquary;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Claims on boss loot reliquaries.
 *
 * Runs at {@link EventPriority#LOWEST} and cancels, so the beam-fx guards further down the chain
 * (which would otherwise swallow the click) never see it. Punching a reliquary does nothing.
 */
public class ReliquaryListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onClick(PlayerInteractEntityEvent event) {
        if (!HollowReliquary.isProp(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        HollowReliquary.click(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        if (HollowReliquary.isProp(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPunch(EntityDamageByEntityEvent event) {
        if (HollowReliquary.isProp(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
