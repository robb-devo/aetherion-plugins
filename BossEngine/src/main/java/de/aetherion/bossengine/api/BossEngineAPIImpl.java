package de.aetherion.bossengine.api;

import de.aetherion.bossengine.event.BossDespawnEvent;
import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.manager.BossManager;
import de.aetherion.bossengine.manager.SpawnerManager;
import de.aetherion.bossengine.model.BossTemplate;
import de.aetherion.bossengine.trigger.BossSpawner;
import de.aetherion.bossengine.trigger.SpawnerDefinition;
import de.aetherion.bossengine.util.BossKeys;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class BossEngineAPIImpl implements BossEngineAPI {

    private final BossManager bossManager;
    private final BossKeys keys;
    private final SpawnerManager spawnerManager;

    public BossEngineAPIImpl(BossManager bossManager, BossKeys keys, SpawnerManager spawnerManager) {
        this.bossManager = bossManager;
        this.keys = keys;
        this.spawnerManager = spawnerManager;
    }

    @Override
    public Optional<BossTemplate> getTemplate(String id) {
        return bossManager.getTemplates().get(id);
    }

    @Override
    public Collection<BossTemplate> getTemplates() {
        return bossManager.getTemplates().getAll();
    }

    @Override
    public Optional<BossInstance> spawn(String templateId, Location location, SpawnCause cause, Player initiator) {
        return bossManager.spawn(templateId, location, cause, initiator);
    }

    @Override
    public Optional<BossInstance> getInstance(UUID instanceId) {
        return bossManager.getActive().stream()
                .filter(instance -> instance.getInstanceId().equals(instanceId))
                .findFirst();
    }

    @Override
    public Optional<BossInstance> getInstance(Entity entity) {
        return bossManager.getByEntity(entity);
    }

    @Override
    public Collection<BossInstance> getActiveBosses() {
        return bossManager.getActive();
    }

    @Override
    public Location locate(String templateId, Location from) {
        if (templateId == null || templateId.isBlank()) {
            return null;
        }
        Location live = liveLocation(templateId, from);
        return live != null ? live : homeLocation(templateId);
    }

    @Override
    public boolean isKnownBoss(String templateId) {
        return templateId != null && !templateId.isBlank() && getTemplate(templateId).isPresent();
    }

    @Override
    public String displayName(String templateId) {
        return getTemplate(templateId)
                .map(template -> TextUtil.plain(template.getDisplayName()))
                .filter(name -> name != null && !name.isBlank())
                .orElse(templateId);
    }

    @Override
    public boolean isBoss(Entity entity) {
        return keys.isBoss(entity);
    }

    @Override
    public boolean isMinion(Entity entity) {
        return keys.isMinion(entity);
    }

    @Override
    public boolean despawn(UUID instanceId) {
        Optional<BossInstance> instance = getInstance(instanceId);
        instance.ifPresent(value -> bossManager.despawn(value, BossDespawnEvent.Reason.COMMAND, null));
        return instance.isPresent();
    }

    private Location liveLocation(String templateId, Location from) {
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BossInstance instance : bossManager.getActive()) {
            if (instance == null || instance.getTemplate() == null
                    || !templateId.equalsIgnoreCase(instance.getTemplate().getId())) {
                continue;
            }
            Location location = entityLocation(instance);
            if (location == null) {
                continue;
            }
            if (from == null || from.getWorld() == null || location.getWorld() == null) {
                return location;
            }
            boolean sameWorld = from.getWorld().equals(location.getWorld());
            double distance = sameWorld ? from.distanceSquared(location) : Double.MAX_VALUE / 2.0;
            if (!sameWorld && best != null && best.getWorld() != null && from.getWorld().equals(best.getWorld())) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = location;
            }
        }
        return best;
    }

    private static Location entityLocation(BossInstance instance) {
        LivingEntity entity = instance.getEntity();
        if (entity != null && entity.isValid() && !entity.isDead()) {
            return entity.getLocation();
        }
        return instance.getSpawnLocation();
    }

    private Location homeLocation(String templateId) {
        if (spawnerManager == null) {
            return null;
        }
        Location home = null;
        Location any = null;
        String homeId = templateId + "_home";
        for (BossSpawner spawner : spawnerManager.getSpawners()) {
            SpawnerDefinition definition = spawner.getDefinition();
            if (definition == null || !templateId.equalsIgnoreCase(definition.getBossId())) {
                continue;
            }
            Location location = definition.toLocation();
            if (location == null) {
                continue;
            }
            if (homeId.equalsIgnoreCase(definition.getId())) {
                home = location;
            }
            if (any == null) {
                any = location;
            }
        }
        return home != null ? home : any;
    }
}
