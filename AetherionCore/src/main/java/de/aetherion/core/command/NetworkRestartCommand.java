package de.aetherion.core.command;

import de.aetherion.core.network.RestartCountdown;
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
 * Countdown then {@link Bukkit#shutdown()}.
 * Registered as {@code aenet} so Crafty stdin can send the bare form
 * {@code aenet restart 10} (no leading slash). In-game {@code /aenet} uses the same executor.
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
            sender.sendMessage("§7Usage: §f" + label + " restart [seconds] [reason]");
            sender.sendMessage("§8Default is 10 seconds. Chat ticks every 2 seconds: 10, 8, 6, 4, 2.");
            sender.sendMessage("§8No reason: one short restart line, then the countdown. A reason names the patch instead.");
            sender.sendMessage("§8Example: §f" + label + " restart 10 Patch Ashen-Katana-Restore");
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
        String reason = plan.reason();
        if (reason == null) {
            plugin.getLogger().warning(sender.getName() + " started a restart countdown of " + seconds + "s.");
        } else {
            plugin.getLogger().warning(sender.getName() + " started a restart countdown of " + seconds
                    + "s (" + reason + ").");
        }
        sender.sendMessage("§aRestart countdown started (" + seconds + "s). This backend will stop itself.");
        Bukkit.broadcast(Component.text(RestartCountdown.openLine(reason), NamedTextColor.YELLOW));
        RestartCountdown countdown = new RestartCountdown(seconds);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (countdown.stopped()) {
                return;
            }
            String line = countdown.tick();
            if (countdown.stopped()) {
                BukkitTask running = task;
                task = null;
                if (running != null) {
                    running.cancel();
                }
                plugin.getLogger().warning("Restart countdown finished. Shutting down.");
                Bukkit.shutdown();
                return;
            }
            if (line != null) {
                Bukkit.broadcast(Component.text(line, NamedTextColor.LIGHT_PURPLE));
            }
        }, 0L, RestartCountdown.CLOCK_PERIOD_TICKS);
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
