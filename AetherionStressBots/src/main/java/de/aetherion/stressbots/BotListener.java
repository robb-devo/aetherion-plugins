package de.aetherion.stressbots;

import de.aetherion.stressbots.role.BotLocations;
import de.aetherion.stressbots.role.BotRoleHandler;
import de.aetherion.stressbots.role.BotRoleRegistry;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
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
        BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
        if (handler != null) {
            de.aetherion.stressbots.role.BotPlaystyle.forceEnglish(plugin, player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    de.aetherion.stressbots.role.BotPlaystyle.forceEnglish(plugin, player);
                }
            }, 12L);
        }
        plugin.getProvisioner().scheduleSetup(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
        if (handler == null) {
            return;
        }
        Location dest = BotLocations.assignedAnchor(player, roleSection(handler));
        if (dest == null) {
            dest = handler.destination(player);
        }
        if (dest != null) {
            event.setRespawnLocation(dest);
            plugin.getActivity().markRecovering(player, "respawn " + BotLocations.format(dest));
        }
        int delay = Math.max(1, plugin.getConfig().getInt("testbots.safety.respawn-setup-ticks", 5));
        plugin.getProvisioner().scheduleSetup(player, delay);
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

    private ConfigurationSection roleSection(BotRoleHandler handler) {
        ConfigurationSection qa = BotRoleRegistry.roleSection(plugin, handler.role());
        if (qa != null) {
            return qa;
        }
        return plugin.getConfig().getConfigurationSection(handler.role().id());
    }
}
