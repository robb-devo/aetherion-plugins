package de.aetherion.bossengine.trigger;

import de.aetherion.bossengine.api.SpawnCause;

/**
 * New spawn types: extend this class and register a factory in {@link de.aetherion.bossengine.manager.SpawnerManager}.
 */
public abstract class AbstractSpawnTrigger {

    private final SpawnerDefinition definition;

    protected AbstractSpawnTrigger(SpawnerDefinition definition) {
        this.definition = definition;
    }

    public SpawnerDefinition getDefinition() {
        return definition;
    }

    public abstract SpawnType getType();

    public abstract SpawnCause getCause();

    public abstract void start(BossSpawner host);

    public abstract void stop();
}
