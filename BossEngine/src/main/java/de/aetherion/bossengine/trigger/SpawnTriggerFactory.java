package de.aetherion.bossengine.trigger;


@FunctionalInterface
public interface SpawnTriggerFactory {

    AbstractSpawnTrigger create(SpawnerDefinition definition);
}
