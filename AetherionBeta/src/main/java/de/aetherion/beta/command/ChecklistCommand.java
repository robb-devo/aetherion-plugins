package de.aetherion.beta.command;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.menu.ChecklistMenu;
import de.aetherion.beta.menu.LanguageMenu;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ChecklistCommand implements CommandExecutor {

    private final AetherionBeta plugin;

    public ChecklistCommand(AetherionBeta plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (plugin.store().get(player.getUniqueId()).lang() == null) {
            LanguageMenu.open(plugin, player);
        } else {
            ChecklistMenu.open(plugin, player);
        }
        return true;
    }
}
