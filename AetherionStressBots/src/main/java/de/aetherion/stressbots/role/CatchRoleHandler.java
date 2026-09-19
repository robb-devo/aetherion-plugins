package de.aetherion.stressbots.role;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.PetAccess;
import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

public final class CatchRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public CatchRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.CATCH;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.CATCH, "QaCatch");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.CATCH);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(items.catcher().helmet(1));
        inv.setChestplate(items.catcher().chestplate(1));
        inv.setLeggings(items.catcher().leggings(1));
        inv.setBoots(items.catcher().boots(1));
        inv.setItemInMainHand(items.catcher().gaff(1));
        giveSpheres(inv);
    }

    private void giveSpheres(PlayerInventory inv) {
        PetAccess pets = AetherServices.pets();
        ConfigurationSection section = BotRoleRegistry.roleSection(plugin, BotRole.CATCH);
        List<String> ids = section == null
                ? List.of("common")
                : section.getStringList("spheres");
        if (ids.isEmpty()) {
            ids = List.of("common");
        }
        int count = section == null ? 16 : Math.max(1, section.getInt("sphere-count", 16));
        if (pets == null) {
            plugin.getLogger().warning("AetherMobs offline — catch bots join without spheres.");
            return;
        }
        for (String id : ids) {
            ItemStack sphere = pets.catchSphere(id);
            if (sphere == null) {
                continue;
            }
            sphere.setAmount(Math.min(64, count));
            inv.addItem(sphere);
        }
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.CATCH));
    }

    @Override
    public String description() {
        return "Throw catch spheres from a solid habitat pad. No void chase; timing minigame is not automated.";
    }
}
