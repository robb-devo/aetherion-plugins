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

    /**
     * Doors from the exile island into bosses that keep their own world. Helios runs its normal party entry
     * ({@code /helios enter}); the Last Seed takes the player to the site's arrival point. Nothing is created
     * here: a missing world answers {@code not-built} so staff see it.
     */
    @Override
    public String enterArena(Player player, String arena) {
        if (player == null || arena == null) {
            return "unsupported";
        }
        switch (arena.toLowerCase(java.util.Locale.ROOT)) {
            case "helios", "helios_requiem" -> {
                de.aetherion.bossengine.helios.HeliosModule helios = plugin.helios();
                return helios == null ? "disabled" : helios.enter(player);
            }
            case "world_eater", "nihil", "unbroken", "last_seed" -> {
                de.aetherion.bossengine.instance.worldeater.WorldEaterSite site =
                        de.aetherion.bossengine.instance.worldeater.WorldEaterSite.get();
                if (site == null) {
                    return "disabled";
                }
                if (site.world() == null || !site.built()) {
                    return "not-built";
                }
                Location arrival = site.arrival();
                if (arrival == null) {
                    return "not-built";
                }
                player.teleport(arrival);
                return "ok";
            }
            default -> {
                return "unsupported";
            }
        }
    }

    @Override
    public boolean alive(String templateId) {
        BossEngineAPI api = BossEngine.getAPI();
        if (api == null || templateId == null) {
            return false;
        }
        for (BossInstance instance : api.getActiveBosses()) {
            if (instance != null && instance.getTemplate() != null
                    && templateId.equalsIgnoreCase(instance.getTemplate().getId())
                    && instance.getState() == de.aetherion.bossengine.instance.BossState.ALIVE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tagCompactHearths(Entity entity) {
        if (entity == null || plugin.getKeys() == null) {
            return;
        }
        plugin.getKeys().tagCompactHearths(entity);
    }
}
