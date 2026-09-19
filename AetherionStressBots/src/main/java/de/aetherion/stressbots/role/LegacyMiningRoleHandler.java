package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

/** Phase 1 Shabby Mine bots ({@code StressM*}). Same mining loop as Wave 1 {@code mine}. */
public final class LegacyMiningRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public LegacyMiningRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.MINING;
    }

    @Override
    public String prefix() {
        return plugin.getConfig().getString("prefixes.mining", "StressM").toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.MINING);
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
        return BotLocations.pickAnchor(player, plugin.getConfig().getConfigurationSection("mining"));
    }

    @Override
    public String description() {
        return "Legacy stress: Shabby Mine ore/stone dig loop.";
    }
}
