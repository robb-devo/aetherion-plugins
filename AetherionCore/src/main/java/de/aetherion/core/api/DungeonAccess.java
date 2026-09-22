package de.aetherion.core.api;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Dungeon DEV start / keeper NPC. Implemented by AetherionDungeons.
 */
public interface DungeonAccess {

    ItemStack dungeonKeeperAnchor();

    boolean startTest(Player player, boolean bossOnly, int floor);

    boolean despawnKeeper(Entity entity);

    /**
     * True on the dungeon backend (mmo-d). Hub warps must proxy to the main world
     * before teleporting — local coordinates there are not Capital.
     */
    default boolean needsMainWorld(Player player) {
        return false;
    }

    /**
     * Snapshot inventory, level, and progression (never clears them), Velocity-connect
     * to the main server, and teleport to {@code spawnId} after the snapshot applies.
     *
     * @return true when this backend handled the request so the caller must not teleport locally
     */
    default boolean transferToMainSpawn(Player player, String spawnId) {
        return false;
    }
}
