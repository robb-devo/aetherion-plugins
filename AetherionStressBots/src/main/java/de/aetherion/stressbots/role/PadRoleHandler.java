package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

public final class PadRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public PadRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.PAD;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.PAD, "QaPad");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.PAD);
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
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.PAD));
    }

    @Override
    public String description() {
        return "Stand on known Hub/island jump pads. Far pads are plugin teleports, not void walks.";
    }
}
