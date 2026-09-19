package de.aetherion.dungeons.bridge;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;

/** Periodic TPS / player count log for hub vs mmo-d comparison. */
public final class DungeonTpsProbe {

    private final Plugin plugin;
    private BukkitTask task;

    public DungeonTpsProbe(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("tps-log.enabled", true)) {
            return;
        }
        long interval = Math.max(100L, plugin.getConfig().getLong("tps-log.interval-ticks", 1200L));
        String prefix = plugin.getConfig().getString("tps-log.prefix", "[DungeonTPS]");
        String role = plugin.getConfig().getString("role", "hub");
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double[] tps = Bukkit.getServer().getTPS();
            double t1 = tps.length > 0 ? tps[0] : -1;
            double t5 = tps.length > 1 ? tps[1] : -1;
            double t15 = tps.length > 2 ? tps[2] : -1;
            int players = Bukkit.getOnlinePlayers().size();
            int worlds = Bukkit.getWorlds().size();
            plugin.getLogger().info(String.format(
                    "%s role=%s players=%d worlds=%d tps=%.2f/%.2f/%.2f",
                    prefix, role, players, worlds, t1, t5, t15
            ));
        }, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
