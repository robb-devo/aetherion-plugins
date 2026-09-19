package de.aetherion.stressbots;

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

    private final AetherionStressBots plugin;

    public StressBotsCommand(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        BotProvisioner provisioner = plugin.getProvisioner();
        if (args.length == 0) {
            sender.sendMessage("§e/stressbots <reload|setup|list>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAssist();
                sender.sendMessage("§aStress bot config reloaded.");
            }
            case "setup" -> {
                int count = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    BotRole role = provisioner.roleOf(player);
                    if (role == null) {
                        continue;
                    }
                    provisioner.setup(player, role);
                    count++;
                }
                sender.sendMessage("§aRe-provisioned §f" + count + " §astress bots.");
            }
            case "list" -> {
                List<String> combat = new ArrayList<>();
                List<String> mining = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    BotRole role = provisioner.roleOf(player);
                    if (role == BotRole.COMBAT) {
                        combat.add(player.getName());
                    } else if (role == BotRole.MINING) {
                        mining.add(player.getName());
                    }
                }
                sender.sendMessage("§6Combat (§f" + combat.size() + "§6): §7" + String.join(", ", combat));
                sender.sendMessage("§6Mining (§f" + mining.size() + "§6): §7" + String.join(", ", mining));
                sender.sendMessage("§7Prefixes: combat=§f" + provisioner.combatPrefix()
                        + " §7mining=§f" + provisioner.miningPrefix());
            }
            default -> sender.sendMessage("§e/stressbots <reload|setup|list>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "setup", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
