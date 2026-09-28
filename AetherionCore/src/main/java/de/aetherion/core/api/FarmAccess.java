package de.aetherion.core.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Farm island portal hooks used by the DEV menu.
 * Implemented by AetherionFarming {@code FarmPortalAPI}.
 */
public interface FarmAccess {

    boolean available();

    String ensureIsland(boolean forceRebuild);

    ItemStack hubPortalTool();

    void setIslandExitHere(Player player);

    void teleportToIsland(Player player);

    String refreshAmbience();

    String statusLine();

    /** DEV: sugar-cane strip placer. */
    default ItemStack canePatchTool() {
        return null;
    }

    /** DEV: district oak-sign marker. */
    default ItemStack districtMarker(String districtId) {
        return null;
    }

    /** DEV: scarecrow prop for the Farm Isle bird minigame. */
    default ItemStack scarecrowTool() {
        return null;
    }

    /** DEV: hay wagon ambience prop. */
    default ItemStack hayWagonTool() {
        return null;
    }

    /**
     * Seed sugar cane banks + empty farmland inside the Farm Isle footprint.
     * Idempotent — existing cane / crops are left alone.
     *
     * @param force re-run even when the footprint was already seeded once
     */
    default String seedFarmIsle(boolean force) {
        return null;
    }

    /**
     * Extra crop items when harvesting the hourly featured crop (0 if none).
     */
    default int featuredCropBonus(Material yield) {
        return 0;
    }

    default String featuredCropName() {
        return null;
    }

    // ------------------------------------------------------------------ Eldervale expansion

    /** True inside the Eldervale Farm Isle footprint (isle-only skills and loops). */
    default boolean onFarmIsle(Location at) {
        return false;
    }

    /**
     * Crop-only Fortune from Farming loops (crop mastery, Harvest Rhythm, bakehouse food).
     * Added on top of gear Fortune by the Items crop harvest listener — never touches ore.
     */
    default double cropFortuneBonus(Player player, Material crop) {
        return 0.0d;
    }

    /**
     * Whole extra crop items for one harvest: featured crop, Bee Bloom, … (0 if none).
     * Supersedes {@link #featuredCropBonus(Material)} for callers that know the player.
     */
    default int harvestBonus(Player player, Material yield, Location at) {
        return featuredCropBonus(yield);
    }

    /** Short gold action-bar tag for {@link #harvestBonus}, e.g. "Bee Bloom". Null when none. */
    default String harvestBonusLabel(Player player, Location at) {
        String featured = featuredCropName();
        return featured == null ? null : "Featured " + featured;
    }

    /** DEV menu: run a Farming Island action ({@code group:verb[:arg]}); returns the chat reply. */
    default String devAction(Player player, String action) {
        return null;
    }

    /** DEV menu: give-able items of a group (id → icon), e.g. {@code npcs}, {@code foods}, {@code prizes}. */
    default Map<String, ItemStack> devItems(String group) {
        return Map.of();
    }

    /** DEV menu: status lines for the Farming Island hub button. */
    default List<String> devStatus() {
        return List.of();
    }

    /** DEV menu: named teleport spots on the isle (label → location). */
    default Map<String, Location> devSpots() {
        return Map.of();
    }
}
