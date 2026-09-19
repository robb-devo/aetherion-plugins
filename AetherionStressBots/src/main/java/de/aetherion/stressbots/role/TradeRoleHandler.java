package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class TradeRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public TradeRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.TRADE;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.TRADE, "QaTrade");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.TRADE);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        BotPlaystyle.kitCombat(player.getInventory(), items, BotPlaystyle.gearTier(player), false);
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.TRADE));
    }

    @Override
    public String description() {
        return "Try /ah and /bazaar (TRADER unlocked on provision), else right-click AH/Bazaar NPCs. Listings are not automated.";
    }
}
