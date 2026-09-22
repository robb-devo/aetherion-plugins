package de.aetherion.core.api;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Amethyst Area dig zone ({@code aether_veins}) + ore seal/regen. Implemented by AetherionMining.
 * Pickaxe stats and harvest payouts stay in AetherionItems ({@link HarvestAccess}).
 *
 * <p>Player entry is {@code /amethyst} or the Crystal Guide NPC — both call
 * {@link #teleportToVeinsHub(Player)} after the mining-level check. That teleports to the
 * Hub spawn {@code amethyst} planted by Robb's spawn anchor when set; otherwise the
 * Mining config {@code veins.spawn-*} default in {@code aether_veins}. Old {@code /deepmines}
 * is retired as a player entry.
 */
public interface MiningAccess {

    ItemStack veinsForemanAnchor();

    /** Configured Amethyst Area world name (default {@code aether_veins}). */
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
     * Teleport the player to the Amethyst Area spawn and unlock Hub spawn {@code amethyst}
     * on first arrival. Prefers a planted Hub location ({@code /hubadmin set amethyst} or
     * Dev Menu spawn anchor); falls back to Mining {@code veins.spawn-*} in {@code aether_veins}.
     * Does not enforce the mining-level gate — callers should check
     * {@link #meetsVeinsMiningLevel(Player)} first for NPC / first {@code /amethyst} entry.
     *
     * @return true if teleport succeeded
     */
    boolean teleportToVeinsHub(Player player);

    /**
     * Whether the player meets {@code veins.min-mining-level} (default 30)
     * on their mining skill. Used by the Crystal Guide and first-time {@code /amethyst}.
     */
    boolean meetsVeinsMiningLevel(Player player);

    /** Configured minimum mining skill for Amethyst Area entry (default 30). */
    int veinsMinMiningLevel();
}
