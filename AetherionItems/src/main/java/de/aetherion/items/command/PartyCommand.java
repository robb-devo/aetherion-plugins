package de.aetherion.items.command;

import de.aetherion.items.social.PartyService;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class PartyCommand implements CommandExecutor, TabCompleter {

    private final PartyService parties;

    public PartyCommand(PartyService parties) {
        this.parties = parties;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("info")) {
            parties.sendList(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§e/party invite <player>");
                    return true;
                }
                Player target = findPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cNobody online matches that.");
                    return true;
                }
                parties.invite(player, target);
            }
            case "accept" -> parties.accept(player);
            case "deny", "decline" -> parties.deny(player);
            case "leave" -> parties.leave(player);
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage("§e/party kick <player>");
                    return true;
                }
                Player target = findPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cNobody online matches that.");
                    return true;
                }
                parties.kick(player, target);
            }
            case "disband" -> parties.disband(player);
            case "help" -> usage(player);
            default -> usage(player);
        }
        return true;
    }

    private void usage(Player player) {
        player.sendMessage("§e/party invite <player> §7- invite, max 4");
        player.sendMessage("§e/party accept §7- take an invite");
        player.sendMessage("§e/party deny §7- skip it");
        player.sendMessage("§e/party leave §7- walk away");
        player.sendMessage("§e/party kick <player> §7- leader only");
        player.sendMessage("§e/party disband §7- leader only");
        player.sendMessage("§e/party list §7- who is in");
        player.sendMessage("§8Dungeon starts pull the whole party.");
    }

    private Player findPlayer(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        Player found = null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = online;
        }
        return found;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("invite", "accept", "deny", "leave", "kick", "disband", "list", "help")
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
