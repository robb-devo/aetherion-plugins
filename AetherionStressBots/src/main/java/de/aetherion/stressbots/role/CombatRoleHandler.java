package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.concurrent.ThreadLocalRandom;

/** Phase 1 Borderlands combat bots ({@code StressC*}). */
public final class CombatRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public CombatRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.COMBAT;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.COMBAT, "StressC");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.COMBAT);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(items.createCombatHelmet());
        inv.setChestplate(items.createCombatChestplate());
        inv.setLeggings(items.createCombatLeggings());
        inv.setBoots(items.createCombatBoots());
        inv.setItemInMainHand(items.createCombatSword());
        BotRoleRegistry.giveSpare(inv, items.createCombatSword());
    }

    @Override
    public Location destination(Player player) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("combat");
        if (section == null) {
            return null;
        }
        return BotLocations.scatter(BotLocations.readPoint(section), section.getDouble("scatter-radius", 18), ThreadLocalRandom.current());
    }

    @Override
    public String description() {
        return "Legacy stress: Borderlands combat wander/attack loop.";
    }
}
