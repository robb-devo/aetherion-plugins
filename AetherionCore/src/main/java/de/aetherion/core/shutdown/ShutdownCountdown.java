package de.aetherion.core.shutdown;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Always warn players before the JVM stops.
 * Countdown: 10 → 8 → 6 → 4 → 2 (every 2 seconds) in chat, then shutdown.
 * Locked: keep unless Robbi explicitly removes it.
 */
public final class ShutdownCountdown implements Listener {

    private static final int START = 10;
    private static final long PERIOD_TICKS = 40L; // 2 seconds

    private final JavaPlugin plugin;
    private boolean running;

    public ShutdownCountdown(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (intercept(event.getCommand(), event.getSender())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRemoteCommand(RemoteServerCommandEvent event) {
        if (intercept(event.getCommand(), event.getSender())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2 || raw.charAt(0) != '/') {
            return;
        }
        if (intercept(raw.substring(1), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * @return true if the command was a stop/restart and we took over
     */
    private boolean intercept(String commandLine, CommandSender sender) {
        if (commandLine == null) {
            return false;
        }
        String trimmed = commandLine.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String[] parts = trimmed.split("\\s+");
        String label = parts[0].toLowerCase();
        // strip namespace plugin:stop
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (!label.equals("stop") && !label.equals("restart")) {
            return false;
        }

        // Emergency bypass: stop now / stop force
        for (int i = 1; i < parts.length; i++) {
            String a = parts[i].toLowerCase();
            if (a.equals("now") || a.equals("force") || a.equals("-f")) {
                return false;
            }
        }

        if (running) {
            sender.sendMessage("§cShutdown countdown läuft bereits.");
            return true;
        }

        beginCountdown(label.equals("restart"));
        return true;
    }

    private void beginCountdown(boolean restartAfter) {
        running = true;
        plugin.getLogger().warning("Shutdown countdown started (10s, every 2s). restartAfter=" + restartAfter);

        Bukkit.broadcast(Component.text("Server fährt gleich runter — bitte bereit machen.", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        new BukkitRunnable() {
            int left = START;

            @Override
            public void run() {
                if (left > 0) {
                    Bukkit.broadcast(Component.text("Server stoppt in ", NamedTextColor.RED)
                            .append(Component.text(String.valueOf(left), NamedTextColor.WHITE)
                                    .decorate(TextDecoration.BOLD))
                            .append(Component.text("…", NamedTextColor.RED)));
                    left -= 2;
                    return;
                }
                cancel();
                finish(restartAfter);
            }
        }.runTaskTimer(plugin, 0L, PERIOD_TICKS);
    }

    private void finish(boolean restartAfter) {
        Bukkit.broadcast(Component.text("Server stoppt jetzt.", NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD));
        plugin.getLogger().warning("Shutdown countdown finished — stopping.");
        if (restartAfter) {
            try {
                Bukkit.spigot().restart();
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("restart() failed, falling back to shutdown: " + t.getMessage());
            }
        }
        Bukkit.shutdown();
    }
}
