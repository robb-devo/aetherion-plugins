package de.aetherion.core.playtime;

import de.aetherion.core.api.PlaytimeAccess;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Counts online time from join to quit and flushes the open session on a timer.
 * Offline totals stay on disk.
 */
public final class PlaytimeService implements PlaytimeAccess, Listener {

    private final JavaPlugin plugin;
    private final PlaytimeStore store;
    private BukkitTask flushTask;

    public PlaytimeService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new PlaytimeStore(PlaytimePaths.resolve(plugin), plugin.getLogger());
    }

    public void start() throws IOException {
        store.open();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            store.join(player.getUniqueId(), player.getName());
        }
        long flushSeconds = plugin.getConfig().getLong("playtime.flush-seconds", 60L);
        if (flushSeconds < 5L) {
            flushSeconds = 5L;
        }
        long ticks = flushSeconds * 20L;
        flushTask = plugin.getServer().getScheduler().runTaskTimer(plugin, store::flushOnline, ticks, ticks);
        plugin.getLogger().info("Playtime store (kept across wipes): " + store.directory().getAbsolutePath());
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        store.flushOnline();
    }

    public UUID findByName(String name) {
        return store.findByName(name);
    }

    public List<String> knownNames() {
        return store.knownNames();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        store.join(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        store.quit(event.getPlayer().getUniqueId());
    }

    @Override
    public long seconds(UUID playerId) {
        return store.seconds(playerId);
    }

    @Override
    public String name(UUID playerId) {
        return store.name(playerId);
    }

    @Override
    public long reset(UUID playerId) {
        return store.reset(playerId);
    }
}
