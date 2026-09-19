package de.aetherion.farming.api;

import de.aetherion.core.api.FarmAccess;
import de.aetherion.farming.portal.FarmPortalAPI;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class FarmAccessImpl implements FarmAccess {

    @Override
    public boolean available() {
        return FarmPortalAPI.available();
    }

    @Override
    public String ensureIsland(boolean forceRebuild) {
        return FarmPortalAPI.ensureIsland(forceRebuild);
    }

    @Override
    public ItemStack hubPortalTool() {
        return FarmPortalAPI.createHubPortalTool();
    }

    @Override
    public void setIslandExitHere(Player player) {
        FarmPortalAPI.setIslandExitHere(player);
    }

    @Override
    public void teleportToIsland(Player player) {
        FarmPortalAPI.teleportToIsland(player);
    }

    @Override
    public String refreshAmbience() {
        return FarmPortalAPI.refreshAmbience();
    }

    @Override
    public String statusLine() {
        return FarmPortalAPI.statusLine();
    }
}
