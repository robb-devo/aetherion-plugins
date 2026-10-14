package de.aetherion.hub.command;

import de.aetherion.hub.prop.PropCatalog;
import de.aetherion.hub.prop.PropWand;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PropWandCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final PropWand wand;

    public PropWandCommand(Plugin plugin, PropWand wand) {
        this.plugin = plugin;
        this.wand = wand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PropWand.PERMISSION)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length >= 1 && "extract".equalsIgnoreCase(args[0])) {
            PropCatalog.ensureExtracted(plugin);
            sender.sendMessage("§aProp schematics extracted to §fplugins/AetherionHub/props/ §a(+ FAWE folder).");
            return true;
        }
        if (args.length >= 1 && "list".equalsIgnoreCase(args[0])) {
            for (PropCatalog.Prop prop : PropCatalog.all()) {
                sender.sendMessage("§8- §f" + prop.id() + " §7(" + prop.zone() + ") §8— §7" + prop.display());
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only (or use §f/propwand extract§c).");
            return true;
        }
        PropCatalog.ensureExtracted(plugin);
        player.getInventory().addItem(wand.create());
        player.sendMessage("§aProp Wand given. §7Look at a block → right-click to paste. Sneak+right = menu.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PropWand.PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            List<String> options = List.of("extract", "list");
            return options.stream().filter(o -> o.startsWith(token)).collect(Collectors.toCollection(ArrayList::new));
        }
        return List.of();
    }
}
