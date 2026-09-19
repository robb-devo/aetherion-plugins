package de.aetherion.items.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class HungerListener implements Listener, Runnable {

    public HungerListener(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 20L, 40L);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            fill(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        event.setFoodLevel(20);
        fill(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        fill(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        fill(event.getPlayer());
    }

    private void fill(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getFoodLevel() != 20) {
            player.setFoodLevel(20);
        }
        if (player.getSaturation() < 20f) {
            player.setSaturation(20f);
        }
        player.setExhaustion(0f);
    }
}
