package de.aetherion.hub.command;

import de.aetherion.hub.menu.SpawnMenu;
import de.aetherion.hub.service.HubService;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpawnCommand implements CommandExecutor {

    private final HubService hub;
    private final SpawnMenu menu;

    public SpawnCommand(HubService hub, SpawnMenu menu) {
        this.hub = hub;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("spawns")) {
            menu.open(player);
            return true;
        }

        hub.teleportSelected(player);
        return true;
    }
}
