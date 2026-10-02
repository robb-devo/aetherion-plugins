package de.aetherion.items.farming;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.FarmAccess;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Items → AetherionFarming calls for the Eldervale loops (isle check, crop Fortune, harvest bonus).
 * Every call is guarded: with an older Core / Farming jar on the server the hooks go quiet
 * (once, not per harvest) instead of throwing from the crop listener.
 */
public final class FarmIsleHook {

    private static volatile boolean broken;

    private FarmIsleHook() {
    }

    private static FarmAccess farm() {
        return broken ? null : AetherServices.farming();
    }

    /** Inside the Eldervale Farm Isle footprint. */
    public static boolean onIsle(Player player) {
        FarmAccess farm = farm();
        if (farm == null || player == null) {
            return false;
        }
        try {
            return farm.onFarmIsle(player.getLocation());
        } catch (LinkageError error) {
            broken = true;
            return false;
        }
    }

    /** Crop-only Fortune: mastery, Harvest Rhythm, bakehouse food. */
    public static double cropFortune(Player player, Material crop) {
        FarmAccess farm = farm();
        if (farm == null || player == null || crop == null) {
            return 0.0d;
        }
        try {
            return Math.max(0.0d, farm.cropFortuneBonus(player, crop));
        } catch (LinkageError error) {
            broken = true;
            return 0.0d;
        }
    }

    /** Extra whole crops for one harvest (featured crop, Bee Bloom). Falls back to the featured-only call. */
    public static int harvestBonus(Player player, Material yield, Location at) {
        FarmAccess farm = AetherServices.farming();
        if (farm == null || yield == null) {
            return 0;
        }
        if (!broken) {
            try {
                return Math.max(0, farm.harvestBonus(player, yield, at));
            } catch (LinkageError error) {
                broken = true;
            }
        }
        try {
            return Math.max(0, farm.featuredCropBonus(yield));
        } catch (LinkageError error) {
            return 0;
        }
    }

    public static String harvestBonusLabel(Player player, Location at) {
        FarmAccess farm = AetherServices.farming();
        if (farm == null) {
            return null;
        }
        if (!broken) {
            try {
                return farm.harvestBonusLabel(player, at);
            } catch (LinkageError error) {
                broken = true;
            }
        }
        try {
            String name = farm.featuredCropName();
            return name == null ? null : "Featured " + name;
        } catch (LinkageError error) {
            return null;
        }
    }
}
