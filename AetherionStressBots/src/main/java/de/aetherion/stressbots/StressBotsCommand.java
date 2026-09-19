package de.aetherion.stressbots;

import de.aetherion.stressbots.role.BotRole;
import de.aetherion.stressbots.role.BotRoleHandler;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StressBotsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "reload", "setup", "list", "start", "stop", "stopall", "report"
    );

    private final AetherionStressBots plugin;

    public StressBotsCommand(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!AetherionStressBots.canControl(sender)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAssist();
                sender.sendMessage("§aStress/testbot config reloaded. enabled=" + plugin.getController().enabled());
            }
            case "setup" -> {
                int count = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    BotRoleHandler handler = plugin.getRegistry().byPlayer(player);
                    if (handler == null) {
                        continue;
                    }
                    plugin.getProvisioner().setup(player, handler);
                    count++;
                }
                sender.sendMessage("§aRe-provisioned §f" + count + " §abots.");
            }
            case "list" -> sender.sendMessage(plugin.getController().reportText().split("\n"));
            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e/stressbots start <mine|forage|catch|roam|combat|fish|trade|quest|pad> [count]");
                    return true;
                }
                int count = args.length >= 3 ? parseInt(args[2], 1) : Math.max(1, plugin.getController().desired(args[1]));
                sender.sendMessage(plugin.getController().start(args[1], count));
            }
            case "stop" -> {
                if (args.length < 2 || "all".equalsIgnoreCase(args[1])) {
                    sender.sendMessage(plugin.getController().stopAll());
                    return true;
                }
                sender.sendMessage(plugin.getController().stop(args[1]));
            }
            case "stopall" -> sender.sendMessage(plugin.getController().stopAll());
            case "report" -> sender.sendMessage(plugin.getController().reportText().split("\n"));
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§e/stressbots <reload|setup|list|start|stop|stopall|report>");
        sender.sendMessage("§7Wave 1: §f/stressbots start mine 3");
        sender.sendMessage("§7Wave 2: §f/stressbots start trade 2 §7· fish · quest · pad · combat");
        sender.sendMessage("§7Also: §f/botreport");
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2 && ("start".equalsIgnoreCase(args[0]) || "stop".equalsIgnoreCase(args[0]))) {
            List<String> roles = new ArrayList<>(plugin.getController().startableRoles());
            if ("stop".equalsIgnoreCase(args[0])) {
                roles.add("all");
                roles.add(BotRole.MINING.id());
            }
            return filter(roles, args[1]);
        }
        if (args.length == 3 && "start".equalsIgnoreCase(args[0])) {
            return filter(List.of("1", "3", "5", "10"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(lower)).toList();
    }
}
