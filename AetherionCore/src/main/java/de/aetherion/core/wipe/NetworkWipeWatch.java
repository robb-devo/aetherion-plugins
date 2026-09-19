package de.aetherion.core.wipe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * When {@code /wipe} runs on another backend, {@link BetaWipe#markPending()} drops a flag
 * into this server's {@code plugins/AetherionCore/} (and optionally Crafty peers / shared,
 * see {@code wipe.*} in AetherionCore {@code config.yml}). Poll that file and shut down
 * the same way the initiating server does, so mmo-d / mmo-r both clear data on next boot.
 */
public final class NetworkWipeWatch {

    private final JavaPlugin plugin;
    private final BetaWipe wipe;
    private volatile boolean shuttingDown;
    private BukkitTask task;

    public NetworkWipeWatch(JavaPlugin plugin, BetaWipe wipe) {
        this.plugin = plugin;
        this.wipe = wipe;
    }

    public void start() {
        // Delay past boot so onLoad wipe+clear has finished before we react to a fresh flag.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 60L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Call from the local /wipe countdown so we do not double-broadcast. */
    public void markLocalShutdown() {
        shuttingDown = true;
    }

    private void tick() {
        if (shuttingDown || !wipe.isPending()) {
            return;
        }
        shuttingDown = true;
        plugin.getLogger().warning(
                "Network wipe flag detected — stopping this backend so player data clears on next boot."
        );
        Bukkit.broadcast(Component.text(
                "BETA WIPE — this backend is stopping (flag from another server).",
                NamedTextColor.RED
        ));
        Bukkit.getScheduler().runTaskLater(plugin, this::shutdownNow, 40L);
    }

    private void shutdownNow() {
        Component reason = Component.text(
                "Beta wipe queued — start the server again from the panel.",
                NamedTextColor.RED
        );
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.kick(reason);
        }
        Bukkit.shutdown();
    }
}
