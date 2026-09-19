package de.aetherion.bossengine.api;

import de.aetherion.bossengine.instance.BossInstance;
import de.aetherion.bossengine.model.BossTemplate;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Public surface for Quest- and Lexikon-plugins.
 */
public interface BossEngineAPI {

    Optional<BossTemplate> getTemplate(String id);

    Collection<BossTemplate> getTemplates();

    Optional<BossInstance> spawn(String templateId, Location location, SpawnCause cause, Player initiator);

    Optional<BossInstance> getInstance(UUID instanceId);

    Optional<BossInstance> getInstance(Entity entity);

    Collection<BossInstance> getActiveBosses();

    /**
     * Live boss location, else the home/altar spawner for this template.
     */
    Location locate(String templateId, Location from);

    boolean isKnownBoss(String templateId);

    String displayName(String templateId);

    boolean isBoss(Entity entity);

    boolean isMinion(Entity entity);

    boolean despawn(UUID instanceId);
}
