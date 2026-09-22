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
     * True when this backend is not the main world (mmo-r). Hub warps must proxy
     * before teleporting — local coordinates there are not Capital.
     */
    default boolean needsMainWorld(Player player) {
        return false;
    }

    /**
     * Leave a dungeon instance without touching inventory. No-op when the player
     * is already in the dungeon hub or on the main world.
     */
    default void leaveInstance(Player player) {
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
