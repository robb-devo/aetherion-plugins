package de.aetherion.guilds.command;

import de.aetherion.guilds.menu.GuildMenu;
import de.aetherion.guilds.service.GuildService;
import de.aetherion.guilds.service.MinionService;
import de.aetherion.guilds.util.AetherionItemsAccess;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class GuildCommand implements CommandExecutor, TabCompleter {

    private final GuildService guilds;
    private final MinionService minions;
    private final GuildMenu menu;

    public GuildCommand(GuildService guilds, MinionService minions, GuildMenu menu) {
        this.guilds = guilds;
        this.minions = minions;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!AetherionItemsAccess.guildUnlocked(player)) {
            player.sendMessage(AetherionItemsAccess.guildHint());
            return true;
        }
        if (args.length == 0) {
            menu.open(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /guild create <name>");
                    return true;
                }
                StringBuilder name = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    if (i > 1) {
                        name.append(' ');
                    }
                    name.append(args[i]);
                }
                guilds.create(player, name.toString());
            }
            case "disband" -> guilds.disband(player);
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /guild invite <player>");
                    return true;
                }
                guilds.invite(player, Bukkit.getPlayerExact(args[1]));
            }
            case "accept" -> guilds.accept(player, args.length >= 2 ? args[1] : null);
            case "deny" -> guilds.deny(player);
            case "leave" -> guilds.leave(player);
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /guild kick <player>");
                    return true;
                }
                guilds.kick(player, Bukkit.getPlayerExact(args[1]));
            }
            case "home", "island" -> guilds.goHome(player);
            case "menu" -> menu.open(player);
            case "quarry" -> {
                player.sendMessage("§7Quarries are crafted, not given.");
                player.sendMessage("§7Put §f8 Compressed §7of a resource around a §dQuarry Core§7.");
                player.sendMessage("§7They can only be placed on your §fprivate or guild island§7.");
            }
            case "rank", "setrank" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /guild rank <player> <leader ranks: vp|mayor|soldier|footman>");
                    return true;
                }
                guilds.setRank(player, Bukkit.getPlayerExact(args[1]), args[2]);
            }
            default -> player.sendMessage("§7/guild create|invite|accept|deny|leave|kick|rank|home|disband|quarry");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("create", "invite", "accept", "deny", "leave", "kick", "rank", "home", "disband", "menu", "quarry")
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick") || args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("setrank"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("setrank"))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Stream.of("vp", "mayor", "soldier", "footman")
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
