package de.aetherion.core.command;

import de.aetherion.core.network.RestartPlan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Countdown then {@link Bukkit#shutdown()}. Crafty stop/SIGTERM cannot wait,
 * so the panel or the host wrapper must send this command before the process is killed.
 */
public final class NetworkRestartCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private BukkitTask task;

    public NetworkRestartCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aetherion.core.admin")) {
            sender.sendMessage("§cYou cannot use this command.");
            return true;
        }
        if (args.length == 0 || !"restart".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§7Usage: §f/" + label + " restart [seconds] [eta]");
            sender.sendMessage("§8Broadcasts a countdown, then shuts this backend down.");
            sender.sendMessage("§8Crafty stop should run this before it kills the process.");
            return true;
        }
        RestartPlan plan = RestartPlan.parse(args);
        if (!plan.ok()) {
            sender.sendMessage("§c" + plan.error());
            return true;
        }
        if (task != null) {
            sender.sendMessage("§cA restart countdown is already running.");
            return true;
        }
        int seconds = plan.seconds();
        String eta = plan.eta();
        plugin.getLogger().warning(sender.getName() + " started a restart countdown of " + seconds
                + "s (back online " + eta + ").");
        sender.sendMessage("§aRestart countdown started. This backend will stop itself.");
        Bukkit.broadcast(Component.text("Network restart in " + seconds + "s. Back online " + eta + ".",
                NamedTextColor.LIGHT_PURPLE));
        int[] left = {seconds};
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            left[0]--;
            if (left[0] > 0) {
                Bukkit.broadcast(Component.text("Network restart in " + left[0] + "s. Back online " + eta + ".",
                        NamedTextColor.LIGHT_PURPLE));
                return;
            }
            BukkitTask running = task;
            task = null;
            if (running != null) {
                running.cancel();
            }
            plugin.getLogger().warning("Restart countdown finished. Shutting down.");
            Bukkit.broadcast(Component.text("Restarting now. Back online " + eta + ".", NamedTextColor.LIGHT_PURPLE));
            Bukkit.shutdown();
        }, 20L, 20L);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aetherion.core.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("restart"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("restart")) {
            return filter(List.of("10", "15", "30"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
