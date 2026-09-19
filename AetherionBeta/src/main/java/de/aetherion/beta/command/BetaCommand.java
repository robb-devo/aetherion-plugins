package de.aetherion.beta.command;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.BetaLang;
import de.aetherion.beta.Texts;
import de.aetherion.beta.data.BetaPlayerData;
import de.aetherion.beta.menu.AdminBetaMenu;
import de.aetherion.beta.menu.ChecklistMenu;
import de.aetherion.beta.menu.LanguageMenu;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class BetaCommand implements CommandExecutor, TabCompleter {

    private final AetherionBeta plugin;

    public BetaCommand(AetherionBeta plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("aetherion.beta.admin")) {
            player.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            AdminBetaMenu.openRoot(plugin, player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "npc" -> {
                if (args.length < 2) {
                    player.sendMessage("§e/beta npc place §8| §e/beta npc remove");
                    return true;
                }
                if (args[1].equalsIgnoreCase("place")) {
                    plugin.npcs().placeAnchor(player.getLocation());
                    player.sendMessage("§aBeta guide anchor set. Each player sees their own courier here.");
                    return true;
                }
                if (args[1].equalsIgnoreCase("remove")) {
                    plugin.npcs().clearAnchor();
                    player.sendMessage("§eBeta guide removed.");
                    return true;
                }
                player.sendMessage("§e/beta npc place §8| §e/beta npc remove");
            }
            case "checklist" -> ChecklistMenu.open(plugin, player);
            case "lang" -> LanguageMenu.open(plugin, player);
            case "book" -> {
                BetaPlayerData data = plugin.store().get(player.getUniqueId());
                BetaLang lang = data.langOr(BetaLang.EN);
                plugin.npcs().giveBook(player, data, lang);
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.npcs().respawnAll();
                player.sendMessage("§aAetherionBeta reloaded.");
            }
            case "reset" -> {
                if (args.length < 2) {
                    player.sendMessage("§e/beta reset <player>");
                    return true;
                }
                org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
                if (target.getUniqueId() == null) {
                    player.sendMessage("§cUnknown player.");
                    return true;
                }
                plugin.store().reset(target.getUniqueId());
                org.bukkit.entity.Player online = target.getPlayer();
                if (online != null) {
                    plugin.npcs().despawnFor(online);
                    plugin.npcs().ensureFor(online);
                }
                player.sendMessage("§aReset beta data for §f" + (target.getName() != null ? target.getName() : args[1]) + "§a.");
            }
            default -> player.sendMessage("§e/beta §8| §e/beta npc place §8| §e/beta book §8| §e/beta reset <player> §8| §e/beta reload");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("npc", "checklist", "lang", "book", "reload", "reset"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return filter(args[1], Arrays.asList("place", "remove"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            List<String> names = new ArrayList<>();
            for (org.bukkit.entity.Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(args[1], names);
        }
        return List.of();
    }

    private static List<String> filter(String input, List<String> options) {
        String needle = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(needle)) {
                out.add(option);
            }
        }
        return out;
    }
}
