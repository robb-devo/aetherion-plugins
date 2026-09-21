package de.aetherion.core.command;

import de.aetherion.core.restart.RestartCountdown;
import de.aetherion.core.restart.RestartNotice;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class RestartCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "aetherion.restart";

    private final JavaPlugin plugin;
    private final RestartCountdown countdown;

    public RestartCommand(JavaPlugin plugin, RestartCountdown countdown) {
        this.plugin = plugin;
        this.countdown = countdown;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cYou cannot use this command.");
            return true;
        }
        if (countdown.running()) {
            sender.sendMessage("§cA restart countdown is already running.");
            return true;
        }
        RestartNotice.Plan plan = RestartNotice.parse(
                args,
                plugin.getConfig().getInt("restart.countdown-seconds", RestartNotice.DEFAULT_COUNTDOWN),
                plugin.getConfig().getInt("restart.eta-seconds", RestartNotice.DEFAULT_ETA)
        );
        if (!countdown.start(plan.countdownSeconds(), plan.etaSeconds())) {
            sender.sendMessage("§cA restart countdown is already running.");
            return true;
        }
        sender.sendMessage("§aRestart in §f" + plan.countdownSeconds()
                + "s §a· back in " + RestartNotice.etaLabel(plan.etaSeconds()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("5", "10"), args[0]);
        }
        if (args.length == 2) {
            return filter(List.of("45", "60"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
