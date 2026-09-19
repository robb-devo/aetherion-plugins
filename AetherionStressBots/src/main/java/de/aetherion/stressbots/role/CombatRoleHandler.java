package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wave 2 combat ({@code QaCombat*}). Legacy {@code StressC*} still matches as an alias.
 */
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
    public List<String> prefixes() {
        List<String> out = new ArrayList<>();
        out.add(prefix());
        String legacy = plugin.getConfig().getString("prefixes.combat", "StressC");
        if (legacy != null && !legacy.isBlank() && !legacy.equalsIgnoreCase(prefix())) {
            out.add(legacy.toLowerCase(java.util.Locale.ROOT));
        }
        return out;
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
        ConfigurationSection wave = BotRoleRegistry.roleSection(plugin, BotRole.COMBAT);
        if (wave != null && !BotLocations.readAnchors(wave).isEmpty()) {
            return BotLocations.pickAnchor(player, wave);
        }
        ConfigurationSection legacy = plugin.getConfig().getConfigurationSection("combat");
        if (legacy == null) {
            return null;
        }
        return BotLocations.scatter(BotLocations.readPoint(legacy), legacy.getDouble("scatter-radius", 8), ThreadLocalRandom.current());
    }

    @Override
    public String description() {
        return "Wave 2: leash + attack hostiles near the combat pad (QaCombat). StressC alias still kits.";
    }
}
