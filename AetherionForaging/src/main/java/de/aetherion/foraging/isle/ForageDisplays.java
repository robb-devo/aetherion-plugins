package de.aetherion.foraging.isle;

import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.ForageKeys;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owned forage-isle FX displays (finds, critters, windfall, widowmaker).
 * Every ephemeral entity gets a PDC mark; {@link #purgeAll()} clears leftovers
 * when the isle empties or the plugin disables — no whole-world janitor needed.
 */
public final class ForageDisplays {

    private static final ConcurrentHashMap<UUID, Boolean> LIVE = new ConcurrentHashMap<>();

    private ForageDisplays() {
    }

    public static void mark(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(ForageKeys.fx(), PersistentDataType.BYTE, (byte) 1);
        LIVE.put(entity.getUniqueId(), Boolean.TRUE);
    }

    public static void forget(Entity entity) {
        if (entity == null) {
            return;
        }
        LIVE.remove(entity.getUniqueId());
    }

    public static void forget(UUID id) {
        if (id != null) {
            LIVE.remove(id);
        }
    }

    /**
     * Remove every tracked FX entity. Falls back to one isle-world PDC sweep for
     * leftovers from crashes (never a whole-network entity scan).
     */
    public static void purgeAll() {
        for (UUID id : LIVE.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        LIVE.clear();
        World world = null;
        ForageIsle isle = ForageIsle.get();
        if (isle != null) {
            world = isle.isleWorld();
        }
        if (world == null) {
            AetherionForaging plugin = AetherionForaging.getInstance();
            if (plugin != null) {
                String name = plugin.getConfig().getString("forage-isle.world", "world");
                world = Bukkit.getWorld(name);
            }
        }
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(ForageKeys.fx(), PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }

    public static int liveCount() {
        return LIVE.size();
    }
}
