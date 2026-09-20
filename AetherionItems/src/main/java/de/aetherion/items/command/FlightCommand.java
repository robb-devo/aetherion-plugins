package de.aetherion.items.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Simple /flight toggle for content helpers. Honors {@code aetherion.flight}
 * or EssentialsX {@code essentials.fly} (live MMO-R has Essentials).
 */
public final class FlightCommand implements CommandExecutor {

    public static boolean allowed(Player player) {
        return player != null && (player.isOp()
                || player.hasPermission("aetherion.flight")
                || player.hasPermission("essentials.fly"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (!allowed(player)) {
            player.sendMessage("§cNo permission for /flight.");
            return true;
        }
        boolean enable = !player.getAllowFlight();
        player.setAllowFlight(enable);
        if (!enable) {
            player.setFlying(false);
        }
        player.sendMessage(enable ? "§aFlight enabled." : "§7Flight disabled.");
        return true;
    }
}
