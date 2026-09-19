package de.aetherion.bossengine.trigger;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.manager.BossManager;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Live spawner wrapping a YAML definition plus its trigger implementation.
 */
public class BossSpawner {

    private final SpawnerDefinition definition;
    private final AbstractSpawnTrigger trigger;
    private final BossManager bossManager;

    public BossSpawner(
            SpawnerDefinition definition,
            AbstractSpawnTrigger trigger,
            BossManager bossManager
    ) {
        this.definition = definition;
        this.trigger = trigger;
        this.bossManager = bossManager;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public SpawnerDefinition getDefinition() {
        return definition;
    }

    public AbstractSpawnTrigger getTrigger() {
        return trigger;
    }

    public void start() {
        if (definition.isEnabled()) {
            trigger.start(this);
        }
    }

    public void stop() {
        trigger.stop();
    }

    public boolean trySpawn(SpawnCause cause, Player initiator) {
        Location location = definition.toLocation();
        if (location == null) {
            return false;
        }
        return bossManager.spawn(
                definition.getBossId(),
                location,
                cause,
                initiator,
                definition.getId(),
                definition.getConditions()
        ).isPresent();
    }
}
