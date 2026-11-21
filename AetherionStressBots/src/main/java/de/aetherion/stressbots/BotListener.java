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
import org.bukkit.event.player.PlayerQuitEvent;
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
        // /testbot is hidden from people: only bots get the permission, so only their command tree shows it.
        player.addAttachment(plugin, TestBotCommand.PERMISSION, true);
        player.updateCommands();
        plugin.getActivity().onJoin(player);
        plugin.getLogger().info("Testbot joined: " + player.getName());
        de.aetherion.stressbots.role.BotPlaystyle.forceEnglish(plugin, player);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                de.aetherion.stressbots.role.BotPlaystyle.forceEnglish(plugin, player);
            }
        }, 12L);
        // Old QaMine01-style logins kit on join (main world only: their spots are MMO-R spots); the runner's own
        // names kit when they say hello.
        if (plugin.getRegistry().byName(player.getName()) != null && onMainWorld()) {
            plugin.getProvisioner().scheduleSetup(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getIdentity().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
        if (handler == null) {
            return;
        }
        if (handler.role().free()) {
            // Dungeons, islands, the exile and the Pit place their own respawns; a travelling bot keeps its kit.
            plugin.getActivity().markRecovering(player, "respawn (server spot)");
            return;
        }
        Location dest = plugin.getSafety() == null ? null : plugin.getSafety().homeOf(player);
        if (dest == null || !BotLocations.isSolidStand(dest)) {
            dest = BotLocations.assignedAnchor(player, roleSection(handler));
        }
        if (dest == null) {
            dest = handler.destination(player);
        }
        if (dest != null) {
            event.setRespawnLocation(dest);
            if (plugin.getSafety() != null) {
                plugin.getSafety().rememberHome(player, dest);
            }
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

    /** True on MMO-R, and on a single-server setup without Core's router. */
    private static boolean onMainWorld() {
        de.aetherion.core.AetherionCore core = de.aetherion.core.AetherionCore.get();
        de.aetherion.core.network.NetworkRouter router = core == null ? null : core.router();
        String here = router == null ? null : router.here();
        return here == null || here.isBlank() || de.aetherion.core.network.ServerNames.isMain(here);
    }
}
