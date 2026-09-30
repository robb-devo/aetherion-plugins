package de.aetherion.stressbots;

import de.aetherion.stressbots.control.DashboardLinks;
import de.aetherion.stressbots.control.RunnerControlClient;
import de.aetherion.stressbots.role.BotRole;
import de.aetherion.stressbots.role.BotRoleHandler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

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
            "reload", "setup", "list", "start", "stop", "stopall", "report", "dashboard", "link"
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
            case "dashboard", "link" -> sendDashboard(sender);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§e/stressbots <reload|setup|list|start|stop|stopall|report|dashboard>");
        sender.sendMessage("§7Wave 1: §f/stressbots start mine 3");
        sender.sendMessage("§7Wave 2: §f/stressbots start trade 2 §7· fish · quest · pad · combat");
        sender.sendMessage("§7Also: §f/botreport §7· §f/stressbots dashboard");
    }

    private void sendDashboard(CommandSender sender) {
        String base = plugin.getConfig().getString("testbots.dashboard.public-url", "");
        String urlBase = DashboardLinks.join(base, "");
        if (urlBase.isEmpty()) {
            sender.sendMessage("§eSet §ftestbots.dashboard.public-url §eto a URL you can open, then §f/stressbots reload");
            sender.sendMessage("§7Example: §fhttp://YOUR_HOST:18765");
            sender.sendMessage("§7The runner still listens on 127.0.0.1 until §fcontrol.bind §7is changed or a proxy forwards that URL.");
            sender.sendMessage("§7On the host only: §fhttp://127.0.0.1:"
                    + plugin.getConfig().getInt("testbots.runner.port", 18765) + "/");
            return;
        }
        int hours = Math.max(1, plugin.getConfig().getInt("testbots.dashboard.session-hours", 12));
        sender.sendMessage("§7Minting a dashboard link…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RunnerControlClient.Response minted = plugin.getController().runner().mintSession(hours);
            String token = minted != null && minted.ok()
                    ? RunnerControlClient.extract(minted.body(), "token")
                    : "";
            String url = DashboardLinks.join(base, token);
            Bukkit.getScheduler().runTask(plugin, () -> deliverLink(sender, url, token.isBlank()));
        });
    }

    private void deliverLink(CommandSender sender, String url, boolean missingSession) {
        if (url == null || url.isBlank()) {
            sender.sendMessage("§cDashboard URL is not a valid http(s) address.");
            return;
        }
        Component link = Component.text(url, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("Open the stress-bot dashboard")));
        Component line = Component.text("Stress bots dashboard: ", NamedTextColor.YELLOW).append(link);
        sender.sendMessage(line);
        if (missingSession) {
            sender.sendMessage("§7Runner did not mint a session token. If the page asks for a token, check the runner is up and §ftestbots.runner.token §7matches.");
        } else {
            sender.sendMessage("§7This link token is new. Run §f/stressbots dashboard §7again to rotate it. Old links keep working until they expire.");
        }
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
