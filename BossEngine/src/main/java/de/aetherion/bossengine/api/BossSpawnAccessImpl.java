package de.aetherion.bossengine.api;

import de.aetherion.bossengine.BossEngine;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.LeashAction;
import de.aetherion.bossengine.model.SpawnCondition;
import de.aetherion.core.api.BossSpawnAccess;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers BossEngine on {@link de.aetherion.core.api.AetherServices} for soft dependents.
 */
public final class BossSpawnAccessImpl implements BossSpawnAccess {

    private final BossEngine plugin;

    public BossSpawnAccessImpl(BossEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean spawn(String templateId, Location location, Player initiator) {
        BossEngineAPI api = BossEngine.getAPI();
        if (api == null || location == null) {
            return false;
        }
        String id = templateId == null || templateId.isBlank() ? "dungeon_sentinel" : templateId;
        Optional<BossInstance> result = api.spawn(id, location, SpawnCause.API, initiator);
        return result.isPresent();
    }

    @Override
    public boolean spawnSandbox(String templateId, Location location, Player initiator) {
        if (plugin.getBossManager() == null || location == null || location.getWorld() == null) {
            return false;
        }
        String id = templateId == null || templateId.isBlank() ? "dungeon_sentinel" : templateId;
        // High instance cap so live world bosses don't block arena testing.
        SpawnCondition override = new SpawnCondition(64, 80.0, LeashAction.TELEPORT);
        return plugin.getBossManager()
                .spawn(id, location, SpawnCause.API, initiator, null, override)
                .isPresent();
    }

    @Override
    public void despawnInWorld(World world) {
        BossEngineAPI api = BossEngine.getAPI();
        if (api == null || world == null) {
            return;
        }
        for (BossInstance instance : new ArrayList<>(api.getActiveBosses())) {
            if (instance == null) {
                continue;
            }
            boolean here = false;
            Entity entity = instance.getEntity();
            if (entity != null && world.equals(entity.getWorld())) {
                here = true;
            }
            if (!here) {
                Location spawn = instance.getSpawnLocation();
                if (spawn != null && world.equals(spawn.getWorld())) {
                    here = true;
                }
            }
            if (!here) {
                continue;
            }
            UUID id = instance.getInstanceId();
            if (id != null) {
                api.despawn(id);
            }
        }
    }

    @Override
    public void tagCompactHearths(Entity entity) {
        if (entity == null || plugin.getKeys() == null) {
            return;
        }
        plugin.getKeys().tagCompactHearths(entity);
    }
}
