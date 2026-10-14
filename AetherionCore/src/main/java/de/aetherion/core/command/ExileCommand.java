package de.aetherion.core.command;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.exile.ExileQuay;
import de.aetherion.core.exile.Say;
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
                ExileQuay quay = plugin.exileQuay();
                if (quay != null && quay.placed()) {
                    if (quay.near(player, 48)) {
                        quay.board(player, "command");
                    } else if (player.hasPermission("aetherion.core.admin")) {
                        link.send(player);
                    } else {
                        player.sendMessage(Say.t(player,
                                "§8Ferry §8» §7The ferry leaves from the Veil Quay, at the end of the King's Road.",
                                "§8Fähre §8» §7Die Fähre legt am Schleierkai ab, am Ende der Königsstraße."));
                    }
                    return true;
                }
                // No islet placed yet: the old direct hop, but the story still decides who may cross.
                String no = quay == null ? null : quay.refusal(player);
                if (no != null) {
                    player.sendMessage(Say.t(player, "§8Ferryman §8» §7", "§8Fährmann §8» §7") + no);
                    return true;
                }
                link.send(player);
            }
            case "landing" -> {
                // Run on arrival back on the main world: step off the ferry at the Veil Quay (or the harbour).
                ExileQuay quay = plugin.exileQuay();
                if (quay != null && quay.placed()) {
                    quay.landHere(player);
                } else {
                    de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
                    org.bukkit.Location harbour = hub == null ? null : hub.location("harbour");
                    if (harbour != null && harbour.getWorld() != null) {
                        player.teleport(harbour);
                    }
                }
            }
            case "setquay" -> {
                if (!player.hasPermission("aetherion.core.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                ExileQuay quay = plugin.exileQuay();
                if (quay == null) {
                    player.sendMessage("§cThe Veil Quay lives on the main world, not on the exile backend.");
                    return true;
                }
                quay.setHere(player);
            }
            case "return", "leave", "back", "main" -> {
                if (link.isExileServer() && host != null && host.doors() != null
                        && !player.getWorld().getName().equalsIgnoreCase(plugin.getConfig().getString("exile.world", "aetherion_exile"))) {
                    org.bukkit.Location back = host.doors().returnPoint(player.getWorld().getName());
                    if (back != null) {
                        // Out of a boss world beside the island: back to its door, not off the island.
                        player.teleport(back);
                        return true;
                    }
                }
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
            case "where" -> {
                player.sendMessage("§8Exil §8» §7backend §f" + plugin.link().serverName()
                        + " §8· §7exile-server §f" + link.exileServer()
                        + (link.isExileServer() ? " §a(here)" : ""));
                if (plugin.exileQuay() != null) {
                    player.sendMessage(plugin.exileQuay().describe());
                }
            }
            default -> player.sendMessage("§7/exile §8[§7go · return · replay · stop · where" 
                    + (player.hasPermission("aetherion.core.admin") ? " · setquay" : "") + "§8]");
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
        for (String s : sender.hasPermission("aetherion.core.admin")
                ? List.of("go", "return", "replay", "stop", "where", "setquay")
                : List.of("go", "return", "replay", "stop", "where")) {
            if (s.startsWith(prefix)) {
                out.add(s);
            }
        }
        return out;
    }
}
