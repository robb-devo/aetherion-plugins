package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class ForageRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public ForageRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.FORAGE;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.FORAGE, "QaForage");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.FORAGE);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        BotPlaystyle.kitForaging(player.getInventory(), items, BotPlaystyle.gearTier(player));
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.FORAGE));
    }

    @Override
    public String description() {
        return "Teleport to Forage Isle grove/interior and chop logs. Mixed Kindling–Canopy axe, foraging skills equipped.";
    }
}
