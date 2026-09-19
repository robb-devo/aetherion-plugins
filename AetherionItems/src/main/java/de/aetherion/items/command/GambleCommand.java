package de.aetherion.items.command;

import de.aetherion.items.casino.CasinoService;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class GambleCommand implements CommandExecutor, TabCompleter {

    private static final List<String> TABLES = List.of("slots", "roulette");

    private final CasinoService casino;

    public GambleCommand(CasinoService casino) {
        this.casino = casino;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (casino == null) {
            sender.sendMessage("§cCasino is closed.");
            return true;
        }
        String table = args.length > 0 ? args[0] : fromLabel(label);
        casino.open(player, table);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return TABLES.stream().filter(table -> table.startsWith(prefix)).toList();
    }

    private static String fromLabel(String label) {
        if (label == null) {
            return null;
        }
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "slots", "slot" -> "slots";
            case "roulette" -> "roulette";
            default -> null;
        };
    }
}
