package de.aetherion.core.command;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.network.ExileHost;
import de.aetherion.core.network.ExileLink;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /exile} — go / return / replay cutscene / stop.
 * Open for go+return; replay/stop need admin (or being on exile).
 */
public final class ExileCommand implements CommandExecutor, TabCompleter {

    private final AetherionCore plugin;

    public ExileCommand(AetherionCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        ExileLink link = plugin.exile();
        ExileHost host = plugin.exileHost();
        if (link == null) {
            player.sendMessage("§cExile link offline.");
            return true;
        }
        String sub = args.length == 0 ? "go" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "go", "enter", "ferry" -> {
                if (link.isExileServer()) {
                    player.sendMessage("§8Exil §8» §7Already here. §f/exile return §7leaves.");
                    return true;
                }
                link.send(player);
            }
            case "return", "leave", "back", "main" -> {
                if (!link.isExileServer()) {
                    // From any non-exile backend just refuse; from exile hop home.
                    if (plugin.link() != null && plugin.link().isMainWorld()) {
                        player.sendMessage("§8Exil §8» §7You're on Aetherion already.");
                    } else {
                        link.returnToMain(player);
                    }
                    return true;
                }
                link.returnToMain(player);
            }
            case "replay", "cutscene" -> {
                if (host == null || host.arrival() == null) {
                    player.sendMessage("§cExile host offline (this isn't mmo-c).");
                    return true;
                }
                if (!link.isExileServer() && !player.hasPermission("aetherion.core.admin")) {
                    player.sendMessage("§cReplay only on the exile server.");
                    return true;
                }
                host.arrival().replay(player);
            }
            case "stop" -> {
                if (host != null && host.arrival() != null) {
                    host.arrival().stop(player);
                    player.sendMessage("§7Cutscene stopped.");
                }
            }
            case "where" -> player.sendMessage("§8Exil §8» §7backend §f" + plugin.link().serverName()
                    + " §8· §7exile-server §f" + link.exileServer()
                    + (link.isExileServer() ? " §a(here)" : ""));
            default -> player.sendMessage("§7/exile §8[§7go · return · replay · stop · where§8]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : List.of("go", "return", "replay", "stop", "where")) {
            if (s.startsWith(prefix)) {
                out.add(s);
            }
        }
        return out;
    }
}
