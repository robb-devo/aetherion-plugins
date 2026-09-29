package de.aetherion.core.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The Veins world + ore seal/regen. Implemented by AetherionMining.
 * Pickaxe stats and harvest payouts stay in AetherionItems ({@link HarvestAccess}).
 *
 * <p>The Eldervale block below mirrors {@link FarmAccess}: every method has a quiet default so an
 * older Mining jar next to a newer Items jar (or the other way round) never breaks a harvest.
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

    // ------------------------------------------------------------------ Mining Eldervale

    /** True inside the Mining Eldervale footprint (isle-only skills and loops). */
    default boolean onMineIsle(Location at) {
        return false;
    }

    /**
     * Ore-only Fortune from Mining loops (ore mastery, Strike Rhythm, Hearth rations, depth,
     * specimen cabinet). Added by the Items harvest listener to ore / mineral payouts only —
     * never to crops, logs or fish.
     */
    default double oreFortuneBonus(Player player, Material ore, Location at) {
        return 0.0d;
    }

    /**
     * Ore-specific Mining Power from mastery of that very ore (so it can never open a gate for an
     * ore the player has not already mined). Counts for the gate and the break speed of {@code ore}.
     */
    default double orePowerBonus(Player player, Material ore) {
        return 0.0d;
    }

    /** Extra Mining Power used for break speed only (rhythm, rations) — never for gates. */
    default double oreSpeedPower(Player player, Material ore, Location at) {
        return 0.0d;
    }

    /** Whole extra drops for one ore break (Rich Vein, …). 0 if none. */
    default int oreHarvestBonus(Player player, Material ore, Location at) {
        return 0;
    }

    /** Short action-bar tag for {@link #oreHarvestBonus}, e.g. "Rich Vein". Null when none. */
    default String oreHarvestBonusLabel(Player player, Location at) {
        return null;
    }

    /**
     * An ore paid out without a {@code BlockBreakEvent} (Vein Siphon vacuum). Mining counts it for
     * mastery and contracts; rhythm and Crystal Finds stay hand-mined only.
     */
    default void noteOreHarvest(Player player, Material ore, Location at, boolean vacuum) {
    }

    /** Items blueprint forge ritual started at the Eldervale Forgehand (props light up). */
    default void onForgeStart(Player player, int tier, Location anvil) {
    }

    /** Items blueprint forge ritual finished (Forge Reputation, props, isle shout for Tier IV). */
    default void onForged(Player player, int tier, Location anvil) {
    }

    /** DEV menu: run a Mining Island action ({@code group:verb[:arg]}); returns the chat reply. */
    default String devAction(Player player, String action) {
        return null;
    }

    /** DEV menu: give-able items of a group (id → icon), e.g. {@code npcs}, {@code rations}, {@code specimens}. */
    default Map<String, ItemStack> devItems(String group) {
        return Map.of();
    }

    /** DEV menu: status lines for the Mining Island hub button. */
    default List<String> devStatus() {
        return List.of();
    }

    /** DEV menu: named teleport spots on the isle (label → location). */
    default Map<String, Location> devSpots() {
        return Map.of();
    }
}
