package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

public final class MineRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public MineRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.MINE;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.MINE, "QaMine");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.MINE);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(items.createMiningHelmet());
        inv.setChestplate(items.createMiningChestplate());
        inv.setLeggings(items.createMiningLeggings());
        inv.setBoots(items.createMiningBoots());
        inv.setItemInMainHand(items.createMiningPickaxe());
        BotRoleRegistry.giveSpare(inv, items.createMiningPickaxe());
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.MINE));
    }

    @Override
    public String description() {
        return "Path/teleport to the mining isle (Eldervale) and break ores with a starter pick.";
    }
}
