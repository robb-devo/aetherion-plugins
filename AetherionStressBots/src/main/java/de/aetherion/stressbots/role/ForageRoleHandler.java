package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

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
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(items.foraging().helmet(1));
        inv.setChestplate(items.foraging().chestplate(1));
        inv.setLeggings(items.foraging().leggings(1));
        inv.setBoots(items.foraging().boots(1));
        inv.setItemInMainHand(items.foraging().axe(1));
        BotRoleRegistry.giveSpare(inv, items.foraging().axe(1));
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.FORAGE));
    }

    @Override
    public String description() {
        return "Teleport to Forage Isle and chop logs (Kindling I axe).";
    }
}
