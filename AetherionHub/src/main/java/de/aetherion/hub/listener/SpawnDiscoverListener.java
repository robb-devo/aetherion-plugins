package de.aetherion.hub.listener;

import de.aetherion.hub.AetherionHub;
import de.aetherion.hub.model.HubSpawn;
import de.aetherion.hub.service.HubService;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Unlocks configured spawns the first time a player walks near them
 * (hub teleport point and/or matching AetherionItems area markers).
 */
public final class SpawnDiscoverListener implements Listener, Runnable {

    private static final Set<String> ORGANIC = Set.of(
            "ore_ridge", "farm", "farm_isle", "capital", "borderlands", "eldervale", "fishing"
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
                if (hub.unlockNew(player.getUniqueId(), spawn.id())) {
                    announce(player, spawn);
                }
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
        if (!hub.unlockNew(player.getUniqueId(), spawn.id())) {
            return;
        }
        announce(player, spawn);
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
        if ("fishing".equalsIgnoreCase(spawnId)) {
            return 36.0;
        }
        return 0;
    }

    private void announce(Player player, HubSpawn spawn) {
        String name = spawn.displayName();
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.15f);
        player.showTitle(Title.title(
                LegacyComponentSerializer.legacySection().deserialize(
                        "§b§lNEW AREA · " + name.toUpperCase(Locale.ROOT)
                ),
                LegacyComponentSerializer.legacySection().deserialize(
                        "§7teleport unlocked · " + name
                ),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2400), Duration.ofMillis(500))
        ));
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                "§b✦ §f" + name + " §7· teleport unlocked"
        ));
        player.sendMessage("§b✦ §eNew area: §f" + name + "§e.");
        player.sendMessage("§7Teleport unlocked — Manager → Teleports.");
        de.aetherion.core.api.ProgressAccess progress = de.aetherion.core.api.AetherServices.progress();
        if (progress != null) {
            progress.unlock(player, "SPAWN_UNLOCKER", "Teleports", "Manager → Teleports");
        }
    }
}
