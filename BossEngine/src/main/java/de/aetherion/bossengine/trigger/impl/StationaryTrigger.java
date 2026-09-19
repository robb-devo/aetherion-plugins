package de.aetherion.bossengine.trigger.impl;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.event.BossDeathEvent;
import de.aetherion.bossengine.event.BossDespawnEvent;
import de.aetherion.bossengine.trigger.AbstractSpawnTrigger;
import de.aetherion.bossengine.trigger.BossSpawner;
import de.aetherion.bossengine.trigger.SpawnType;
import de.aetherion.bossengine.trigger.SpawnerDefinition;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class StationaryTrigger extends AbstractSpawnTrigger implements Listener {

    private final JavaPlugin plugin;
    private BukkitTask task;
    private long nextAllowedMs;

    public StationaryTrigger(JavaPlugin plugin, SpawnerDefinition definition) {
        super(definition);
        this.plugin = plugin;
    }

    @Override
    public SpawnType getType() {
        return SpawnType.STATIONARY;
    }

    @Override
    public SpawnCause getCause() {
        return SpawnCause.TIMER;
    }

    @Override
    public void start(BossSpawner host) {
        stop();
        nextAllowedMs = 0L;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() < nextAllowedMs) {
                return;
            }
            if (host.getBossManager().isSpawnerOccupied(getDefinition().getId())) {
                return;
            }
            host.trySpawn(SpawnCause.TIMER, null);
        }, 20L, 40L);
    }

    @Override
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onDeath(BossDeathEvent event) {
        delayIfOurs(event.getInstance().getSpawnerId());
    }

    @EventHandler
    public void onDespawn(BossDespawnEvent event) {
        if (event.getReason() == BossDespawnEvent.Reason.LEASH
                || event.getReason() == BossDespawnEvent.Reason.COMMAND) {
            delayIfOurs(event.getInstance().getSpawnerId());
        }
    }

    private void delayIfOurs(String spawnerId) {
        if (spawnerId == null || !spawnerId.equalsIgnoreCase(getDefinition().getId())) {
            return;
        }
        long delayMs = Math.max(5L * 60L * 1000L, Math.max(20L, getDefinition().getIntervalTicks()) * 50L);
        nextAllowedMs = System.currentTimeMillis() + delayMs;
    }
}
