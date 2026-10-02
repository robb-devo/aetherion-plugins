package de.aetherion.stressbots.role;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.PetAccess;
import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class GeneralRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public GeneralRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.GENERAL;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.GENERAL, "QaGeneral");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.GENERAL);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        PlayerInventory inv = player.getInventory();
        int tier = BotPlaystyle.gearTier(player);
        BotPlaystyle.kitCombat(inv, items, Math.min(3, tier), false);
        BotRoleRegistry.giveSpare(inv, items.createMiningPickaxe());
        BotRoleRegistry.giveSpare(inv, items.foraging().axe(Math.max(1, Math.min(3, tier))));
        BotRoleRegistry.giveSpare(inv, items.fishing().rod(Math.max(1, Math.min(3, tier))));
        inv.addItem(new ItemStack(Material.COAL, 24));
        inv.addItem(new ItemStack(Material.COBBLESTONE, 24));
        inv.addItem(new ItemStack(Material.OAK_LOG, 12));
        giveSpheres(inv);
    }

    private void giveSpheres(PlayerInventory inv) {
        PetAccess pets = AetherServices.pets();
        if (pets == null) {
            return;
        }
        ItemStack sphere = pets.catchSphere("common");
        if (sphere == null) {
            return;
        }
        sphere.setAmount(16);
        inv.addItem(sphere);
    }

    @Override
    public Location destination(Player player) {
        if (plugin.getFocus() != null) {
            return plugin.getFocus().destination(player);
        }
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.ROAM));
    }

    @Override
    public String description() {
        return "General player: mixed kit, switches between mine/forage/combat/fish/trade/quest/roam/catch using the real pads.";
    }
}
