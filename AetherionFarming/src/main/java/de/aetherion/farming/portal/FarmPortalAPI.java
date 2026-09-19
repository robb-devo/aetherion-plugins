package de.aetherion.farming.portal;

import de.aetherion.farming.AetherionFarming;
import de.aetherion.farming.island.FarmIslandAmbience;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Public hooks for Dev Menu / other plugins.
 */
public final class FarmPortalAPI {

    private FarmPortalAPI() {
    }

    public static boolean available() {
        AetherionFarming plugin = AetherionFarming.getInstance();
        return plugin != null && plugin.portals() != null;
    }

    public static String ensureIsland(boolean forceRebuild) {
        if (!available()) {
            return "§cAetherionFarming is not loaded.";
        }
        return AetherionFarming.getInstance().portals().island().ensureIsland(forceRebuild);
    }

    public static ItemStack createHubPortalTool() {
        if (!available()) {
            return null;
        }
        return AetherionFarming.getInstance().portals().createHubPortalTool();
    }

    public static void setIslandExitHere(Player player) {
        if (!available() || player == null) {
            return;
        }
        AetherionFarming.getInstance().portals().setIslandExit(player.getLocation());
        player.sendMessage("§aIsland exit set to your feet.");
    }

    public static void teleportToIsland(Player player) {
        if (!available() || player == null) {
            return;
        }
        AetherionFarming.getInstance().portals().teleportToIsland(player);
    }

    public static String refreshAmbience() {
        if (!available()) {
            return "§cAetherionFarming is not loaded.";
        }
        FarmIslandAmbience ambience = AetherionFarming.getInstance().ambience();
        if (ambience == null) {
            return "§cAmbience service missing.";
        }
        return ambience.refreshNearExit();
    }

    public static String statusLine() {
        if (!available()) {
            return "§cFarming offline";
        }
        FarmPortalService portals = AetherionFarming.getInstance().portals();
        boolean pasted = portals.island().isPasted();
        boolean hub = portals.hubPortal() != null;
        boolean exit = portals.islandExit() != null;
        return "§7Island §f" + (pasted ? "ready" : "not pasted")
                + " §8· §7Hub portal §f" + (hub ? "set" : "missing")
                + " §8· §7Exit §f" + (exit ? "set" : "missing")
                + " §8· §7Need Farming §f" + portals.requiredFarmingLevel();
    }
}
