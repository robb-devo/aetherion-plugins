package de.aetherion.core.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
}
