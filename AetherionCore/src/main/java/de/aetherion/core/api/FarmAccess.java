package de.aetherion.core.api;

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
}
