package de.aetherion.stressbots;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class BotListener implements Listener {

    private final AetherionStressBots plugin;

    public BotListener(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getProvisioner().isStressBot(player)) {
            return;
        }
        plugin.getActivity().onJoin(player);
        plugin.getLogger().info("Testbot joined: " + player.getName());
        plugin.getProvisioner().scheduleSetup(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getProvisioner().scheduleSetup(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("auto-feed", true)) {
            return;
        }
        if (!plugin.getProvisioner().isStressBot(player)) {
            return;
        }
        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }
}
