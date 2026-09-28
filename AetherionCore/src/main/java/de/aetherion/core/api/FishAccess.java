package de.aetherion.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Fishing Eldervale hooks for AetherionItems (isle-only skill stats, DEV "Fishing Island" hub).
 * Implemented by AetherionFishing; every method has a quiet default so an older Fishing jar
 * never breaks a caller.
 */
public interface FishAccess {

    /** True inside the Fishing Eldervale footprint. */
    default boolean onFishIsle(Location at) {
        return false;
    }

    /** DEV menu: run a Fishing Island action ({@code group:verb[:arg]}); returns the chat reply. */
    default String devAction(Player player, String action) {
        return null;
    }

    /** DEV menu: give-able items of a group (id → icon), e.g. {@code npcs}, {@code trophies}. */
    default Map<String, ItemStack> devItems(String group) {
        return Map.of();
    }

    /** DEV menu: status lines for the Fishing Island hub button. */
    default List<String> devStatus() {
        return List.of();
    }

    /** DEV menu: named teleport spots on the isle (label → location). */
    default Map<String, Location> devSpots() {
        return Map.of();
    }
}
