package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;
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
        inv.setHelmet(items.createCombatHelmet());
        inv.setChestplate(items.createCombatChestplate());
        inv.setLeggings(items.createCombatLeggings());
        inv.setBoots(items.createCombatBoots());
        inv.setItemInMainHand(items.createCombatSword());
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.TRADE));
    }

    @Override
    public String description() {
        return "Try /ah and /bazaar, else right-click AH/Bazaar NPCs. Listings and TRADER unlock are not automated.";
    }
}
