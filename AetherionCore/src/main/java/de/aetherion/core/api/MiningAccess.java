package de.aetherion.core.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The Veins world + ore seal/regen. Implemented by AetherionMining.
 * Pickaxe stats and harvest payouts stay in AetherionItems ({@link HarvestAccess}).
 */
public interface MiningAccess {

    ItemStack veinsForemanAnchor();

    /** Configured veins world name (default {@code aether_veins}). */
    String veinsWorldName();

    boolean isVeinsWorld(World world);

    /**
     * Mining skill gate for Amethyst Mines / The Veins (default 30).
     * Admins with {@code aetherion.mines.admin} pass. Missing Items plugin
     * is treated as a pass so the world still works in isolation.
     */
    boolean meetsVeinsMiningLevel(Player player);

    /** Configured minimum mining skill (default 30). */
    int veinsMiningLevelRequired();

    /**
     * Teleport into the veins hub. Remembers the overworld exit, loads the
     * world if needed, and unlocks hub spawn {@code amethyst} on first visit.
     */
    boolean teleportToVeinsHub(Player player);

    /** Veins hub stand point after {@link #teleportToVeinsHub}, or null. */
    Location veinsHubLocation();

    /**
     * Seal an ore for vacuum/spread harvest without firing {@code BlockBreakEvent}.
     * Open-mine / veins callers should set AIR themselves instead of calling this.
     */
    void sealForVacuum(Player player, Block block, BlockData original);

    /** Ore/mineral regen delay in seconds. Unknown materials return the stone default (10). */
    long respawnSeconds(Material material);
}
