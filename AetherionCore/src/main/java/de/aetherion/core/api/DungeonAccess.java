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
}
