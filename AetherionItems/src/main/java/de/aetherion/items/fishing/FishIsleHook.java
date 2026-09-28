package de.aetherion.items.fishing;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.FishAccess;

import org.bukkit.entity.Player;

/**
 * Items → AetherionFishing call for the Fishing Eldervale loops (isle check for Lake Sense).
 * Guarded: with an older Core / Fishing jar on the server the hook goes quiet (once) instead of
 * throwing from a stat lookup.
 */
public final class FishIsleHook {

    private static volatile boolean broken;

    private FishIsleHook() {
    }

    /** Inside the Fishing Eldervale footprint. */
    public static boolean onIsle(Player player) {
        if (broken || player == null) {
            return false;
        }
        try {
            FishAccess fishing = AetherServices.fishing();
            return fishing != null && fishing.onFishIsle(player.getLocation());
        } catch (LinkageError error) {
            broken = true;
            return false;
        }
    }
}
