package de.aetherion.hub.listener;

import de.aetherion.hub.AetherionHub;
import de.aetherion.hub.model.HubSpawn;
import de.aetherion.hub.service.HubService;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Unlocks configured spawns the first time a player walks near them
 * (hub teleport point and/or matching AetherionItems area markers).
 */
public final class SpawnDiscoverListener implements Listener, Runnable {

    private static final Set<String> ORGANIC = Set.of(
            "ore_ridge", "farm", "farm_isle", "capital", "borderlands", "eldervale", "amethyst"
    );

    private final AetherionHub plugin;
    private final HubService hub;

    public SpawnDiscoverListener(AetherionHub plugin, HubService hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 40L, 20L);
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location here = player.getLocation();
            if (here.getWorld() == null) {
                continue;
            }
            for (HubSpawn spawn : hub.spawns()) {
                tryDiscover(player, here, spawn);
            }
        }
    }

    private void tryDiscover(Player player, Location here, HubSpawn spawn) {
        if (spawn == null || !ORGANIC.contains(spawn.id().toLowerCase(Locale.ROOT))) {
            return;
        }
        if (hub.isUnlocked(player, spawn.id())) {
            return;
        }
        // Farm Isle unlocks by standing on the island world (portal entry).
        if ("farm_isle".equalsIgnoreCase(spawn.id())) {
            if (here.getWorld() != null && "aether_farm_island".equalsIgnoreCase(here.getWorld().getName())) {
                hub.unlockAndAnnounce(player, spawn.id());
            }
            return;
        }
        // Amethyst Mines unlocks by visiting The Veins world (Crystal Guide TP or first walk-in).
        if ("amethyst".equalsIgnoreCase(spawn.id())) {
            de.aetherion.core.api.MiningAccess mining = de.aetherion.core.api.AetherServices.mining();
            boolean inVeins = mining != null
                    ? mining.isVeinsWorld(here.getWorld())
                    : (here.getWorld() != null && "aether_veins".equalsIgnoreCase(here.getWorld().getName()));
            if (inVeins) {
                hub.unlockAndAnnounce(player, spawn.id());
            }
            return;
        }
        // Capital must be hub-point only — area markers overlap the mine road and false-trigger.
        boolean near = nearHubPoint(here, spawn);
        if (!near && "capital".equalsIgnoreCase(spawn.id())) {
            return;
        }
        if (!near && !insideAreaMarker(here, spawn.id())) {
            return;
        }
        if (!hub.unlockAndAnnounce(player, spawn.id())) {
            return;
        }
    }

    private boolean nearHubPoint(Location here, HubSpawn spawn) {
        if (!spawn.hasLocation()) {
            return false;
        }
        double radius = discoverRadius(spawn.id());
        if (radius <= 0) {
            return false;
        }
        Location at = hub.resolveLocation(spawn);
        if (at == null || at.getWorld() == null) {
            return false;
        }
        if (!here.getWorld().equals(at.getWorld())) {
            return false;
        }
        return here.distanceSquared(at) <= radius * radius;
    }

    /**
     * Soft hook into AetherionItems area markers — Capital / Borderlands / Farm / Ore Ridge
     * markers are often larger or offset from the planted hub teleport point.
     */
    private boolean insideAreaMarker(Location here, String spawnId) {
        Plugin items = Bukkit.getPluginManager().getPlugin("AetherionItems");
        if (items == null || !items.isEnabled()) {
            return false;
        }
        try {
            Object pluginInstance = items.getClass().getMethod("getInstance").invoke(null);
            if (pluginInstance == null) {
                return false;
            }
            Object areas = pluginInstance.getClass().getMethod("getAreas").invoke(pluginInstance);
            if (areas == null) {
                return false;
            }
            Class<?> areaTypeClass = Class.forName("de.aetherion.items.world.AreaType");
            Object areaType = areaTypeClass.getMethod("fromId", String.class).invoke(null, spawnId);
            if (areaType == null) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Collection<Object> zones = (Collection<Object>) areas.getClass()
                    .getMethod("zonesOf", areaTypeClass)
                    .invoke(areas, areaType);
            if (zones == null || zones.isEmpty()) {
                return false;
            }
            for (Object zone : zones) {
                Object hit = zone.getClass().getMethod("contains", Location.class).invoke(zone, here);
                if (hit instanceof Boolean ok && ok) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
        }
        return false;
    }

    private double discoverRadius(String spawnId) {
        String path = "spawns." + spawnId + ".discover-radius";
        if (plugin.getConfig().contains(path)) {
            return plugin.getConfig().getDouble(path, 0);
        }
        // Organic walk-in unlocks (title popup).
        if ("ore_ridge".equalsIgnoreCase(spawnId)) {
            return 40.0;
        }
        if ("farm".equalsIgnoreCase(spawnId)) {
            return 28.0;
        }
        if ("capital".equalsIgnoreCase(spawnId)) {
            return 36.0;
        }
        if ("borderlands".equalsIgnoreCase(spawnId)) {
            return 36.0;
        }
        if ("eldervale".equalsIgnoreCase(spawnId)) {
            return 36.0;
        }
        return 0;
    }
}
