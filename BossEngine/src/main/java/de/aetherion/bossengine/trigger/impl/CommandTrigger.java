package de.aetherion.bossengine.trigger.impl;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.trigger.AbstractSpawnTrigger;
import de.aetherion.bossengine.trigger.BossSpawner;
import de.aetherion.bossengine.trigger.SpawnType;
import de.aetherion.bossengine.trigger.SpawnerDefinition;

/**
 * Manual spawn via {@code /boss spawn} or the API. No scheduled work.
 */
public class CommandTrigger extends AbstractSpawnTrigger {

    public CommandTrigger(SpawnerDefinition definition) {
        super(definition);
    }

    @Override
    public SpawnType getType() {
        return SpawnType.COMMAND;
    }

    @Override
    public SpawnCause getCause() {
        return SpawnCause.COMMAND;
    }

    @Override
    public void start(BossSpawner host) {
        // no-op – command / API driven
    }

    @Override
    public void stop() {
        // no-op
    }
}
