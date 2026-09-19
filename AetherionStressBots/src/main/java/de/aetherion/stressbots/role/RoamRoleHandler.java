package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class RoamRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public RoamRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.ROAM;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.ROAM, "QaRoam");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.ROAM);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        BotPlaystyle.kitCombat(player.getInventory(), items, BotPlaystyle.gearTier(player), false);
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.ROAM));
    }

    @Override
    public String description() {
        return "Local hops on Origin slime pads with jump/look/swing. Mixed combat kit; plugin pad-hops; flees hostiles.";
    }
}
