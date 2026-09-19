package de.aetherion.bossengine.trigger.impl;

import de.aetherion.bossengine.api.SpawnCause;
import de.aetherion.bossengine.trigger.AbstractSpawnTrigger;
import de.aetherion.bossengine.trigger.BossSpawner;
import de.aetherion.bossengine.trigger.SpawnType;
import de.aetherion.bossengine.trigger.SpawnerDefinition;

/**
 * Item-at-altar trigger. Actual use is handled by {@link de.aetherion.bossengine.listener.BossItemUseListener}.
 */
public class ItemTrigger extends AbstractSpawnTrigger {

    public ItemTrigger(SpawnerDefinition definition) {
        super(definition);
    }

    @Override
    public SpawnType getType() {
        return SpawnType.ITEM;
    }

    @Override
    public SpawnCause getCause() {
        return SpawnCause.ITEM;
    }

    @Override
    public void start(BossSpawner host) {
        // no-op – listener driven
    }

    @Override
    public void stop() {
        // no-op
    }
}
