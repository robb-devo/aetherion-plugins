package de.aetherion.hub.api;

import de.aetherion.hub.AetherionHub;
import de.aetherion.hub.item.HomesteadMarker;
import de.aetherion.hub.model.HubSpawn;
import de.aetherion.hub.service.HubService;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Tiny hook for quests/rewards: unlock a spawn or give the homestead marker.
 */
public final class AetherionHubAPI {

    private AetherionHubAPI() {
    }

    public static boolean unlock(UUID uuid, String spawnId) {
        HubService hub = hub();
        if (hub == null || uuid == null || spawnId == null || spawnId.isBlank()) {
            return false;
        }
        hub.ensureId(spawnId);
        return hub.unlock(uuid, spawnId);
    }

    public static boolean unlock(Player player, String spawnId) {
        return player != null && unlock(player.getUniqueId(), spawnId);
    }

    /** Planted spawn location, or null if unset / hub offline. */
    public static Location location(String spawnId) {
        HubService hub = hub();
        if (hub == null || spawnId == null || spawnId.isBlank()) {
            return null;
        }
        HubSpawn spawn = hub.spawn(spawnId);
        return spawn == null ? null : spawn.toLocation();
    }

    public static int unlockAll(UUID uuid) {
        HubService hub = hub();
        if (hub == null || uuid == null) {
            return 0;
        }
        return hub.unlockAll(uuid);
    }

    public static int unlockAll(Player player) {
        return player == null ? 0 : unlockAll(player.getUniqueId());
    }

    public static boolean isUnlocked(UUID uuid, String spawnId) {
        HubService hub = hub();
        if (hub == null) {
            return false;
        }
        HubSpawn spawn = hub.spawn(spawnId);
        return spawn != null && hub.data(uuid).isUnlocked(spawn.id());
    }

    public static boolean hasBonusSpawn(UUID uuid) {
        HubService hub = hub();
        if (hub == null || uuid == null) {
            return false;
        }
        var data = hub.data(uuid);
        for (String id : data.unlocked()) {
            HubSpawn spawn = hub.spawn(id);
            if (spawn != null && !spawn.unlockedByDefault()) {
                return true;
            }
        }
        return false;
    }

    public static boolean openMenu(Player player) {
        AetherionHub plugin = AetherionHub.getInstance();
        if (plugin == null || plugin.getMenu() == null || player == null) {
            return false;
        }
        plugin.getMenu().open(player);
        return true;
    }

    public static ItemStack createAnchor(String spawnId) {
        HomesteadMarker marker = marker();
        return marker == null ? null : marker.createAnchor(spawnId);
    }

    public static ItemStack createUnlockItem(String spawnId) {
        HomesteadMarker marker = marker();
        return marker == null ? null : marker.create(spawnId);
    }

    public static boolean giveUnlockItem(Player player, String spawnId) {
        ItemStack item = createUnlockItem(spawnId);
        if (player == null || item == null) {
            return false;
        }
        player.getInventory().addItem(item);
        return true;
    }

    /** Pulse-enchant Homestead Markers in inventory for {@code seconds}. */
    public static void blinkUnlockItem(Player player, int seconds) {
        HomesteadMarker marker = marker();
        if (marker == null || player == null) {
            return;
        }
        marker.blinkUnlockItem(player, seconds);
    }

    /**
     * FIND EGON starter title — once per player. Safe to call after language pick.
     *
     * @return true if the hint was shown now
     */
    public static boolean sendStarterHint(Player player) {
        AetherionHub plugin = AetherionHub.getInstance();
        if (plugin == null || player == null || !player.isOnline()) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("starter-hint.enabled", false)) {
            return false;
        }
        HubService hub = plugin.getHub();
        if (hub != null && hub.hintShown(player.getUniqueId())) {
            return false;
        }
        if (plugin.getAdminCommand() == null) {
            return false;
        }
        plugin.getAdminCommand().sendHint(player);
        if (hub != null) {
            hub.markHintShown(player.getUniqueId());
        }
        return true;
    }

    /** True if the player is holding an unused Homestead Marker (not a DEV anchor). */
    public static boolean hasUnlockItem(Player player) {
        HomesteadMarker marker = marker();
        if (marker == null || player == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (marker.isMarker(stack) && !marker.isAnchor(stack)) {
                return true;
            }
        }
        return false;
    }

    private static HubService hub() {
        AetherionHub plugin = AetherionHub.getInstance();
        return plugin == null ? null : plugin.getHub();
    }

    private static HomesteadMarker marker() {
        AetherionHub plugin = AetherionHub.getInstance();
        return plugin == null ? null : plugin.getHomesteadMarker();
    }
}
