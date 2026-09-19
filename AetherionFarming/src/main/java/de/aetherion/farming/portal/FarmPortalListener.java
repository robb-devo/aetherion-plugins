package de.aetherion.farming.portal;

import de.aetherion.farming.AetherionFarming;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class FarmPortalListener implements Listener {

    private final FarmPortalService portals;

    public FarmPortalListener(FarmPortalService portals) {
        this.portals = portals;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (portals.tryReturnFromIsland(player)) {
            return;
        }
        portals.tryEnterFromHub(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!portals.shouldCancelVanillaPortal(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (!portals.tryReturnFromIsland(player)) {
                portals.tryEnterFromHub(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (portals.shouldCancelVanillaPortal(player)) {
            event.setCancelled(true);
        }
    }
}
