package de.aetherion.core.api;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The Veins world + ore seal/regen. Implemented by AetherionMining.
 * Pickaxe stats and harvest payouts stay in AetherionItems ({@link HarvestAccess}).
 *
 * <p>Elder Vale / Quests NPC entry into Amethyst Mines (Crystal Hollows try):
 * call {@link #teleportToVeinsHub(Player)} (mining-level gate + hub unlock),
 * or teleport yourself then {@code AetherServices.hub().unlock(player, "amethyst")}.
 * Players can then {@code /amethyst}. Existing {@code /deepmines} also enters veins.
 */
public interface MiningAccess {

    ItemStack veinsForemanAnchor();

    /** Configured veins world name (default {@code aether_veins}). */
    String veinsWorldName();

    boolean isVeinsWorld(World world);

    /**
     * Seal an ore for vacuum/spread harvest without firing {@code BlockBreakEvent}.
     * Open-mine / veins callers should set AIR themselves instead of calling this.
     */
    void sealForVacuum(Player player, Block block, BlockData original);

    /** Ore/mineral regen delay in seconds. Unknown materials return the stone default (10). */
    long respawnSeconds(Material material);

    /**
     * Teleport the player to the Amethyst Mines beacon hub in {@code aether_veins}
     * and unlock the hub spawn {@code amethyst} (announces on first unlock).
     * Does not enforce the mining-level gate — callers should check
     * {@link #meetsVeinsMiningLevel(Player)} first for NPC / quest entry.
     *
     * @return true if teleport succeeded
     */
    boolean teleportToVeinsHub(Player player);

    /**
     * Whether the player meets {@code veins.min-mining-level} (default 30)
     * on their mining skill. Used by the Foreman NPC and the Crystal Guide.
     */
    boolean meetsVeinsMiningLevel(Player player);

    /** Configured minimum mining skill for veins entry (default 30). */
    int veinsMinMiningLevel();
}
