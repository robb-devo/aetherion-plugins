package de.aetherion.foraging.island;

import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.habitat.ForageHabitatService;
import de.aetherion.foraging.isle.ForageDisplays;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks who is on the forage isle. When the isle empties, purge ephemeral FX
 * (finds / critters / windfall). Cast/Canopy plates are owned by their cast classes
 * (Farm-style PDC) — this class no longer scans the whole {@code world}.
 */
public final class ForageDisplayGuard implements Listener {

    private final AetherionForaging plugin;
    private final Set<UUID> onIsle = new HashSet<>();
    private BukkitTask task;
    private boolean emptied;

    public ForageDisplayGuard(AetherionForaging plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 100L, 100L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        ForageDisplays.purgeAll();
    }

    private void refresh() {
        boolean wasEmpty = onIsle.isEmpty();
        onIsle.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ForageHabitatService.inIsleFootprint(player.getLocation())) {
                onIsle.add(player.getUniqueId());
            }
        }
        if (onIsle.isEmpty() && (!wasEmpty || !emptied)) {
            emptied = true;
            ForageDisplays.purgeAll();
        } else if (!onIsle.isEmpty()) {
            emptied = false;
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        track(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        track(event.getPlayer(), event.getFrom(), event.getTo());
    }

    private void track(Player player, Location from, Location to) {
        if (player == null || to == null || from == null) {
            return;
        }
        if (from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        boolean was = ForageHabitatService.inIsleFootprint(from);
        boolean now = ForageHabitatService.inIsleFootprint(to);
        if (was == now) {
            return;
        }
        if (now) {
            onIsle.add(player.getUniqueId());
            emptied = false;
        } else {
            onIsle.remove(player.getUniqueId());
            if (onIsle.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (onIsle.isEmpty()) {
                        emptied = true;
                        ForageDisplays.purgeAll();
                    }
                });
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onIsle.remove(event.getPlayer().getUniqueId());
        if (onIsle.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (onIsle.isEmpty()) {
                    emptied = true;
                    ForageDisplays.purgeAll();
                }
            });
        }
    }
}
