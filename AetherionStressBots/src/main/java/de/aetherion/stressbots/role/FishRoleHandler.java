package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

public final class FishRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public FishRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.FISH;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.FISH, "QaFish");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.FISH);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(items.fishing().helmet(1));
        inv.setChestplate(items.fishing().chestplate(1));
        inv.setLeggings(items.fishing().leggings(1));
        inv.setBoots(items.fishing().boots(1));
        inv.setItemInMainHand(items.fishing().rod(1));
        BotRoleRegistry.giveSpare(inv, items.fishing().rod(1));
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.FISH));
    }

    @Override
    public String description() {
        return "Cast a Nibble rod at water near a safe pad. Strike minigame is not automated.";
    }
}
