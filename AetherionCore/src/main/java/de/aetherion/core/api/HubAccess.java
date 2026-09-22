package de.aetherion.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Unlockable spawns / homestead markers. Implemented by AetherionHub.
 */
public interface HubAccess {

    boolean unlock(UUID uuid, String spawnId);

    boolean unlock(Player player, String spawnId);

    boolean unlockNew(UUID uuid, String spawnId);

    Location location(String spawnId);

    /**
     * Teleport on this server, enforcing unlock rules.
     * @return false when the spawn is missing or still locked
     */
    boolean teleport(Player player, String spawnId);

    int unlockAll(UUID uuid);

    int unlockAll(Player player);

    boolean isUnlocked(UUID uuid, String spawnId);

    boolean isUnlocked(Player player, String spawnId);

    boolean hasBonusSpawn(UUID uuid);

    boolean openMenu(Player player);

    ItemStack createAnchor(String spawnId);

    ItemStack createUnlockItem(String spawnId);

    boolean giveUnlockItem(Player player, String spawnId);

    void blinkUnlockItem(Player player, int seconds);

    boolean sendStarterHint(Player player);

    boolean hasUnlockItem(Player player);

    List<HubSpawnInfo> spawns();
}
