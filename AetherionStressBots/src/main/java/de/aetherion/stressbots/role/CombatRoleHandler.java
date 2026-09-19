package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.concurrent.ThreadLocalRandom;

/** Wave 2 QA combat bots ({@code QaCombat*}). Legacy {@code StressC*} still match. */
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
        return BotRoleRegistry.prefixOf(plugin, BotRole.COMBAT, "QaCombat");
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
        ConfigurationSection qa = BotRoleRegistry.roleSection(plugin, BotRole.COMBAT);
        if (qa != null && (qa.getList("anchors") != null || qa.isSet("x"))) {
            return BotLocations.pickAnchor(player, qa);
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("combat");
        if (section == null) {
            return null;
        }
        return BotLocations.scatter(BotLocations.readPoint(section), section.getDouble("scatter-radius", 18), ThreadLocalRandom.current());
    }

    @Override
    public String description() {
        return "Fight nearby hostiles on the combat pad. Deaths here are expected.";
    }
}
