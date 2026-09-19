package de.aetherion.core.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Minimal boss spawn surface for dungeons and other soft dependents.
 * Implemented by BossEngine; prefer this over reflection bridges.
 */
public interface BossSpawnAccess {

    boolean spawn(String templateId, Location location, Player initiator);

    /**
     * Sandbox / test spawn: ignores global max-instance caps from live world bosses.
     */
    default boolean spawnSandbox(String templateId, Location location, Player initiator) {
        return spawn(templateId, location, initiator);
    }

    void despawnInWorld(World world);

    void tagCompactHearths(Entity entity);
}
