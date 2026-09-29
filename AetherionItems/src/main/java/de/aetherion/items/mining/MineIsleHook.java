package de.aetherion.items.mining;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.MiningAccess;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Items → AetherionMining calls for the Mining Eldervale loops (isle check, ore Fortune,
 * ore Mining Power, harvest extras). Mirrors {@code FarmIsleHook}: every call is guarded, so an
 * older Core / Mining jar on the server turns the hooks off once instead of throwing from the
 * harvest listener on every swing.
 */
public final class MineIsleHook {

    private static volatile boolean broken;

    private MineIsleHook() {
    }

    private static MiningAccess mining() {
        return broken ? null : AetherServices.mining();
    }

    private static void off(LinkageError error) {
        if (!broken) {
            broken = true;
            java.util.logging.Logger.getLogger("AetherionItems").warning(
                    "Mining Eldervale hooks off — Core/Mining jar predates them (" + error.getClass().getSimpleName() + ").");
        }
    }

    /** Inside the Mining Eldervale footprint. */
    public static boolean onIsle(Player player) {
        MiningAccess mining = mining();
        if (mining == null || player == null) {
            return false;
        }
        try {
            return mining.onMineIsle(player.getLocation());
        } catch (LinkageError error) {
            off(error);
            return false;
        }
    }

    /** Ore-only Fortune from the Mining loops. 0 for anything that is not an ore / mineral block. */
    public static double oreFortune(Player player, Material ore, Location at) {
        MiningAccess mining = mining();
        if (mining == null || player == null || ore == null || !HarvestRules.requiresPickaxe(ore)) {
            return 0.0d;
        }
        try {
            return Math.max(0.0d, mining.oreFortuneBonus(player, ore, at));
        } catch (LinkageError error) {
            off(error);
            return 0.0d;
        }
    }

    /** Ore-specific Mining Power (mastery of that ore) — gate + speed for that ore only. */
    public static double orePower(Player player, Material ore) {
        MiningAccess mining = mining();
        if (mining == null || player == null || ore == null || !HarvestRules.requiresPickaxe(ore)) {
            return 0.0d;
        }
        try {
            return Math.max(0.0d, mining.orePowerBonus(player, ore));
        } catch (LinkageError error) {
            off(error);
            return 0.0d;
        }
    }

    /** Speed-only Mining Power (rhythm, rations). Never used for gates. */
    public static double speedPower(Player player, Material ore, Location at) {
        MiningAccess mining = mining();
        if (mining == null || player == null || ore == null) {
            return 0.0d;
        }
        try {
            return Math.max(0.0d, mining.oreSpeedPower(player, ore, at));
        } catch (LinkageError error) {
            off(error);
            return 0.0d;
        }
    }

    /** Whole extra drops (Rich Vein…). */
    public static int harvestBonus(Player player, Material ore, Location at) {
        MiningAccess mining = mining();
        if (mining == null || player == null || ore == null || !HarvestRules.requiresPickaxe(ore)) {
            return 0;
        }
        try {
            return Math.max(0, mining.oreHarvestBonus(player, ore, at));
        } catch (LinkageError error) {
            off(error);
            return 0;
        }
    }

    public static String harvestBonusLabel(Player player, Location at) {
        MiningAccess mining = mining();
        if (mining == null) {
            return null;
        }
        try {
            return mining.oreHarvestBonusLabel(player, at);
        } catch (LinkageError error) {
            off(error);
            return null;
        }
    }

    /** Blueprint forge ritual started at the Forgehand (Mining lights its forge props). */
    public static void forgeStart(Player player, int tier, Location anvil) {
        MiningAccess mining = mining();
        if (mining == null || player == null) {
            return;
        }
        try {
            mining.onForgeStart(player, tier, anvil);
        } catch (LinkageError error) {
            off(error);
        }
    }

    /** Blueprint forge ritual finished (Forge Reputation, props, the Tier IV shout). */
    public static void forged(Player player, int tier, Location anvil) {
        MiningAccess mining = mining();
        if (mining == null || player == null) {
            return;
        }
        try {
            mining.onForged(player, tier, anvil);
        } catch (LinkageError error) {
            off(error);
        }
    }

    /** Vein Siphon (no BlockBreakEvent): let Mining count it for mastery / contracts. */
    public static void noteVacuum(Player player, Material ore, Location at) {
        MiningAccess mining = mining();
        if (mining == null || player == null || ore == null) {
            return;
        }
        try {
            mining.noteOreHarvest(player, ore, at, true);
        } catch (LinkageError error) {
            off(error);
        }
    }
}
