package de.aetherion.hub.listener;

import de.aetherion.hub.AetherionHub;
import de.aetherion.hub.command.HubAdminCommand;
import de.aetherion.hub.menu.SpawnMenu;
import de.aetherion.hub.service.HubService;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class HubListener implements Listener {

    private final AetherionHub plugin;
    private final HubService hub;
    private final SpawnMenu menu;
    private final HubAdminCommand adminCommand;

    public HubListener(AetherionHub plugin, HubService hub, SpawnMenu menu, HubAdminCommand adminCommand) {
        this.plugin = plugin;
        this.hub = hub;
        this.menu = menu;
        this.adminCommand = adminCommand;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        hub.data(player.getUniqueId());

        if (plugin.getConfig().getBoolean("join-at-selected-spawn", true)) {
            // 1 tick later so login/chunk plugins settle, then land on selected spawn.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Location location = hub.resolveLocation(hub.selected(player));
                if (location != null) {
                    player.teleport(location);
                }
            }, 1L);
        }

        // FIND EGON waits until the language menu is closed (LangMenu → Hub API).
        // Only fall back here if language was already chosen before this login.
        if (!plugin.getConfig().getBoolean("starter-hint.enabled", false)) {
            return;
        }
        if (player.hasPlayedBefore()) {
            return;
        }
        if (hub.hintShown(player.getUniqueId())) {
            return;
        }
        long delay = Math.max(1L, plugin.getConfig().getLong("starter-hint.delay-ticks", 60L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || hub.hintShown(player.getUniqueId())) {
                return;
            }
            if (!languageAlreadyChosen(player)) {
                return; // LangMenu still open / pending — it fires the hint after pick.
            }
            de.aetherion.hub.api.AetherionHubAPI.sendStarterHint(player);
        }, delay);
    }

    private static boolean languageAlreadyChosen(Player player) {
        try {
            Object yes = Class.forName("de.aetherion.quests.lang.PlayerLang")
                    .getMethod("hasChosen", Player.class)
                    .invoke(null, player);
            return yes instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return true; // Quests offline — show hint from Hub as before.
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        hub.unload(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("respawn-at-selected-spawn", true)) {
            return;
        }
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }

        Location location = hub.resolveLocation(hub.selected(event.getPlayer()));
        if (location != null) {
            event.setRespawnLocation(location);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpawnMenu.Holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        menu.handleClick(player, event.getRawSlot(), event.isRightClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SpawnMenu.Holder) {
            event.setCancelled(true);
        }
    }
}
