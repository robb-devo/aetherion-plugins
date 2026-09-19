package de.aetherion.guilds.command;

import de.aetherion.guilds.menu.BiomeSelectMenu;
import de.aetherion.guilds.menu.IslandMenu;
import de.aetherion.guilds.model.PersonalIsland;
import de.aetherion.guilds.service.PersonalIslandService;
import de.aetherion.guilds.util.AetherionItemsAccess;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class IslandCommand implements CommandExecutor, TabCompleter {

    private final PersonalIslandService islands;
    private final IslandMenu menu;
    private final BiomeSelectMenu biomes;

    public IslandCommand(PersonalIslandService islands, IslandMenu menu, BiomeSelectMenu biomes) {
        this.islands = islands;
        this.menu = menu;
        this.biomes = biomes;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            player.sendMessage(AetherionItemsAccess.islandHint());
            return true;
        }
        if (args.length == 0) {
            menu.open(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create", "claim" -> {
                PersonalIsland existing = islands.byOwner(player.getUniqueId());
                if (existing != null) {
                    player.sendMessage("§cYou already have an island. Use §f/island home§c.");
                    return true;
                }
                biomes.open(player);
            }
            case "home", "tp", "go" -> islands.goHome(player);
            case "menu" -> menu.open(player);
            case "upgrade" -> islands.upgradeIsland(player);
            default -> player.sendMessage("§7/island §8| §7create §8| §7home §8| §7upgrade");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("create", "home", "menu", "upgrade")
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
