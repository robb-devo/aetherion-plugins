package de.aetherion.core.restart;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Hypixel-style restart warning deploy scripts can fire before Crafty stop:
 * {@code aetherrestart 5 45} → 5s countdown, ~45s estimated downtime.
 */
public final class RestartCountdown {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private BukkitTask task;
    private boolean running;

    public RestartCountdown(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean running() {
        return running;
    }

    public boolean start(int countdownSeconds, int etaSeconds) {
        if (running) {
            return false;
        }
        int countdown = RestartNotice.clampCountdown(countdownSeconds);
        int eta = RestartNotice.clampEta(etaSeconds);
        running = true;
        plugin.getLogger().info("Restart in " + countdown + "s · estimated downtime " + RestartNotice.etaLabel(eta));
        task = new BukkitRunnable() {
            int left = countdown;

            @Override
            public void run() {
                if (left <= 0) {
                    pulse(0, eta);
                    cancel();
                    stopServer(eta);
                    return;
                }
                pulse(left, eta);
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        return true;
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        running = false;
    }

    private void pulse(int left, int eta) {
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            boolean german = RestartNotice.german(player.locale());
            player.sendMessage(RestartNotice.chat(left, eta, german));
            player.sendTitle(
                    RestartNotice.title(german),
                    RestartNotice.subtitle(left, eta, german),
                    0, 30, 8
            );
            player.sendActionBar(LEGACY.deserialize(RestartNotice.subtitle(left, eta, german)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, left <= 1 ? 1.6f : 1.15f);
        }
    }

    private void stopServer(int eta) {
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            boolean german = RestartNotice.german(player.locale());
            player.kick(Component.text(RestartNotice.kick(eta, german)));
        }
        plugin.getLogger().info("Shutting down for content deploy. Estimated back in " + RestartNotice.etaLabel(eta));
        Bukkit.shutdown();
    }
}
