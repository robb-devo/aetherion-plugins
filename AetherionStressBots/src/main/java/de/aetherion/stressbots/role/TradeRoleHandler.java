package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
        PlayerInventory inv = player.getInventory();
        BotPlaystyle.kitCombat(inv, items, BotPlaystyle.gearTier(player), false);
        BotRoleRegistry.giveSpare(inv, items.createMiningPickaxe());
        inv.addItem(new ItemStack(Material.COAL, 32));
        inv.addItem(new ItemStack(Material.COBBLESTONE, 32));
        inv.addItem(new ItemStack(Material.OAK_LOG, 16));
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.TRADE));
    }

    @Override
    public String description() {
        return "Drive Auction House list/buy and Bazaar sell/buy through the real GUIs (TRADER + starter coins).";
    }
}
