package de.aetherion.guilds.command;

import de.aetherion.guilds.menu.FriendMenu;
import de.aetherion.guilds.service.FriendService;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

public final class FriendCommand implements CommandExecutor, TabCompleter {

    private final FriendService friends;
    private final FriendMenu menu;

    public FriendCommand(FriendService friends, FriendMenu menu) {
        this.friends = friends;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("menu")) {
            menu.open(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 2 && (action.equals("add") || action.equals("accept") || action.equals("deny")
                || action.equals("remove") || action.equals("visit"))) {
            player.sendMessage("§cUsage: /friend " + action + " <player>");
            return true;
        }
        switch (action) {
            case "add" -> friends.request(player, Bukkit.getPlayerExact(args[1]));
            case "accept" -> friends.accept(player, args[1]);
            case "deny" -> friends.deny(player, args[1]);
            case "remove" -> friends.remove(player, args[1]);
            case "visit" -> friends.visit(player, args[1]);
            default -> player.sendMessage("§7/friend add|accept|deny|remove|visit|list");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("add", "accept", "deny", "remove", "visit", "list")
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && sender instanceof Player player) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            if (args[0].equalsIgnoreCase("add")) {
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .forEach(names::add);
            } else if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny")) {
                for (UUID id : friends.incomingOf(player.getUniqueId())) {
                    OfflinePlayer other = Bukkit.getOfflinePlayer(id);
                    if (other.getName() != null && other.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        names.add(other.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("visit")) {
                for (UUID id : friends.friendsOf(player.getUniqueId())) {
                    OfflinePlayer other = Bukkit.getOfflinePlayer(id);
                    if (other.getName() != null && other.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        names.add(other.getName());
                    }
                }
            }
            return names;
        }
        return List.of();
    }
}
