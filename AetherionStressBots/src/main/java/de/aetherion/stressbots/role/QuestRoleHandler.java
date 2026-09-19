package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class QuestRoleHandler implements BotRoleHandler {

    private final AetherionStressBots plugin;

    public QuestRoleHandler(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public BotRole role() {
        return BotRole.QUEST;
    }

    @Override
    public String prefix() {
        return BotRoleRegistry.prefixOf(plugin, BotRole.QUEST, "QaQuest");
    }

    @Override
    public int cap() {
        return BotRoleRegistry.capOf(plugin, BotRole.QUEST);
    }

    @Override
    public void kit(Player player, CustomItem items) {
        BotPlaystyle.kitCombat(player.getInventory(), items, BotPlaystyle.gearTier(player), false);
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.QUEST));
    }

    @Override
    public String description() {
        return "Talk to quest NPCs, click dialogue/accept GUIs, and turn in when possible.";
    }
}
