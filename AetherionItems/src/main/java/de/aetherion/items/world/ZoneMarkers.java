package de.aetherion.items.world;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Staff label stands over zone centres. Entities load after their chunk, so "marker id not found" right after a
 * chunk load used to put up a second stand, every restart: Anker Harbour carried 85 of them. Before a new one goes
 * up, the stands already standing there are adopted and the extras removed.
 */
final class ZoneMarkers {

    private ZoneMarkers() {
    }

    /** True once the centre's chunk and its entities are loaded (never loads the chunk itself). */
    static boolean ready(Location centre) {
        World world = centre.getWorld();
        int cx = centre.getBlockX() >> 4;
        int cz = centre.getBlockZ() >> 4;
        return world != null && world.isChunkLoaded(cx, cz) && world.getChunkAt(cx, cz).isEntitiesLoaded();
    }

    /** Keeps one stand for {@code zoneId} at the centre (the {@code preferred} one if it is there), removes the rest. */
    static ArmorStand keepOne(Location centre, NamespacedKey key, String zoneId, UUID preferred) {
        World world = centre.getWorld();
        if (world == null) {
            return null;
        }
        ArmorStand keep = null;
        for (Entity entity : world.getNearbyEntities(centre, 2.0, 3.0, 2.0)) {
            if (!(entity instanceof ArmorStand stand)
                    || !zoneId.equals(stand.getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                continue;
            }
            if (keep == null) {
                keep = stand;
            } else if (stand.getUniqueId().equals(preferred)) {
                keep.remove();
                keep = stand;
            } else {
                stand.remove();
            }
        }
        return keep;
    }
}
