package de.aetherion.hub.api;

import de.aetherion.core.api.HubAccess;
import de.aetherion.core.api.HubSpawnInfo;
import de.aetherion.hub.AetherionHub;
import de.aetherion.hub.model.HubSpawn;
import de.aetherion.hub.service.HubService;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Typed Hub surface. Delegates to {@link AetherionHubAPI} / {@link HubService}
 * so semantics stay identical.
 */
public final class HubAccessImpl implements HubAccess {

    @Override
    public boolean unlock(UUID uuid, String spawnId) {
        return AetherionHubAPI.unlock(uuid, spawnId);
    }

    @Override
    public boolean unlock(Player player, String spawnId) {
        return AetherionHubAPI.unlock(player, spawnId);
    }

    @Override
    public boolean unlockNew(UUID uuid, String spawnId) {
        HubService hub = hub();
        return hub != null && hub.unlockNew(uuid, spawnId);
    }

    @Override
    public boolean unlockAndAnnounce(Player player, String spawnId) {
        HubService hub = hub();
        return hub != null && hub.unlockAndAnnounce(player, spawnId);
    }

    @Override
    public Location location(String spawnId) {
        return AetherionHubAPI.location(spawnId);
    }

    @Override
    public int unlockAll(UUID uuid) {
        return AetherionHubAPI.unlockAll(uuid);
    }

    @Override
    public int unlockAll(Player player) {
        return AetherionHubAPI.unlockAll(player);
    }

    @Override
    public boolean isUnlocked(UUID uuid, String spawnId) {
        return AetherionHubAPI.isUnlocked(uuid, spawnId);
    }

    @Override
    public boolean isUnlocked(Player player, String spawnId) {
        HubService hub = hub();
        return player != null && hub != null && hub.isUnlocked(player, spawnId);
    }

    @Override
    public boolean hasBonusSpawn(UUID uuid) {
        return AetherionHubAPI.hasBonusSpawn(uuid);
    }

    @Override
    public boolean openMenu(Player player) {
        return AetherionHubAPI.openMenu(player);
    }

    @Override
    public ItemStack createAnchor(String spawnId) {
        return AetherionHubAPI.createAnchor(spawnId);
    }

    @Override
    public ItemStack createUnlockItem(String spawnId) {
        return AetherionHubAPI.createUnlockItem(spawnId);
    }

    @Override
    public boolean giveUnlockItem(Player player, String spawnId) {
        return AetherionHubAPI.giveUnlockItem(player, spawnId);
    }

    @Override
    public void blinkUnlockItem(Player player, int seconds) {
        AetherionHubAPI.blinkUnlockItem(player, seconds);
    }

    @Override
    public boolean sendStarterHint(Player player) {
        return AetherionHubAPI.sendStarterHint(player);
    }

    @Override
    public boolean hasUnlockItem(Player player) {
        return AetherionHubAPI.hasUnlockItem(player);
    }

    @Override
    public List<HubSpawnInfo> spawns() {
        HubService hub = hub();
        List<HubSpawnInfo> out = new ArrayList<>();
        if (hub == null) {
            return out;
        }
        for (HubSpawn spawn : hub.spawns()) {
            if (spawn == null || spawn.id() == null || spawn.id().isBlank()) {
                continue;
            }
            out.add(new HubSpawnInfo(spawn.id(), spawn.displayName(), createAnchor(spawn.id())));
        }
        return out;
    }

    private static HubService hub() {
        AetherionHub plugin = AetherionHub.getInstance();
        return plugin == null ? null : plugin.getHub();
    }
}
