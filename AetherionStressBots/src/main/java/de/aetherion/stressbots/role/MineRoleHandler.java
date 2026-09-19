package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;

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
        BotPlaystyle.kitMining(player.getInventory(), items, BotPlaystyle.gearTier(player));
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.MINE));
    }

    @Override
    public String description() {
        return "Teleport to Eldervale interior pads and break ores/stone. Mixed T1–T4 pick, mining skills equipped.";
    }
}
