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
}
