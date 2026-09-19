package de.aetherion.stressbots.role;

import de.aetherion.items.item.CustomItem;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

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
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(items.createCombatHelmet());
        inv.setChestplate(items.createCombatChestplate());
        inv.setLeggings(items.createCombatLeggings());
        inv.setBoots(items.createCombatBoots());
        inv.setItemInMainHand(items.createCombatSword());
    }

    @Override
    public Location destination(Player player) {
        return BotLocations.pickAnchor(player, BotRoleRegistry.roleSection(plugin, BotRole.QUEST));
    }

    @Override
    public String description() {
        return "Walk to known quest NPCs (Maren, Twig, …) and right-click. No dialogue-tree automation.";
    }
}
