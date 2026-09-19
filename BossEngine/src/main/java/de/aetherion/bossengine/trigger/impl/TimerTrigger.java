package de.aetherion.bossengine.trigger.impl;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.trigger.AbstractSpawnTrigger;
import de.aetherion.bossengine.trigger.BossSpawner;
import de.aetherion.bossengine.trigger.SpawnType;
import de.aetherion.bossengine.trigger.SpawnerDefinition;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class TimerTrigger extends AbstractSpawnTrigger {

    private final JavaPlugin plugin;
    private BukkitTask task;

    public TimerTrigger(JavaPlugin plugin, SpawnerDefinition definition) {
        super(definition);
        this.plugin = plugin;
    }

    @Override
    public SpawnType getType() {
        return SpawnType.TIMER;
    }

    @Override
    public SpawnCause getCause() {
        return SpawnCause.TIMER;
    }

    @Override
    public void start(BossSpawner host) {
        stop();
        long interval = Math.max(20L, getDefinition().getIntervalTicks());
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> host.trySpawn(SpawnCause.TIMER, null),
                interval,
                interval
        );
    }

    @Override
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
