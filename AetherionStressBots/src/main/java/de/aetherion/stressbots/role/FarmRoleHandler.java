package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class FarmRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public FarmRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.FARM;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.FARM, "QaFarm");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.FARM);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        BotPlaystyle.kitFarming(player.getInventory(), items, BotPlaystyle.gearTier(player));
    }

    @Override
    public Location destination(Player player) {
        Location pick = BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.FARM));
        if (pick != null) {
            return pick;
        }
        return BotLocations.fallback(player, -600.5, 90.0, 427.5, 4);
    }

    @Override
    public String description() {
        return "Harvest crops on the Eldervale farm island (-600 90 427). Mixed hoe + farming skills. No dungeon instances.";
    }
}
