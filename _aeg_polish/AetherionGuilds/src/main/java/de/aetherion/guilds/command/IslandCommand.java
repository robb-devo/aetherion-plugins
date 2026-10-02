package de.aetherion.guilds.command;

import de.aetherion.guilds.island.Highlight;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.Res;
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
    private Highlight highlight;

    public IslandCommand(PersonalIslandService islands, IslandMenu menu, BiomeSelectMenu biomes) {
        this.islands = islands;
        this.menu = menu;
        this.biomes = biomes;
    }

    public void attachHighlight(Highlight highlight) {
        this.highlight = highlight;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("admin")) {
            admin(player, args);
            return true;
        }
        if (!AetherionItemsAccess.islandUnlocked(player)) {
            if (highlight != null && (action.isEmpty() || action.equals("create") || action.equals("claim")
                    || action.equals("menu"))) {
                highlight.starterMenu().open(player);
            } else {
                player.sendMessage(AetherionItemsAccess.islandHint());
            }
            return true;
        }
        if (args.length == 0) {
            menu.open(player);
            return true;
        }
        PersonalIsland own = islands.byOwner(player.getUniqueId());
        IslandHost host = own == null ? null : IslandHost.personal(own.ownerId());
        switch (action) {
            case "create", "claim", "starter" -> {
                if (own != null) {
                    player.sendMessage("§cYou already have an island. Use §f/island home§c.");
                    return true;
                }
                if (highlight != null) {
                    highlight.starterMenu().open(player);
                } else {
                    biomes.open(player);
                }
            }
            case "home", "tp", "go" -> islands.goHome(player);
            case "menu" -> menu.open(player);
            case "upgrade", "tier" -> islands.upgradeIsland(player);
            case "build", "blueprints" -> {
                if (needIsland(player, host)) {
                    highlight.buildMenu().open(player, host);
                }
            }
            case "land", "expand" -> {
                if (needIsland(player, host)) {
                    highlight.landMenu().open(player, host);
                }
            }
            case "belts", "rails", "conveyor" -> {
                if (needIsland(player, host)) {
                    highlight.placement().startBelts(player, host);
                }
            }
            case "border" -> {
                if (needIsland(player, host)) {
                    highlight.land().flashBorder(player, host);
                    highlight.land().sendBorderMessage(player);
                }
            }
            case "done", "cancel" -> {
                if (highlight != null) {
                    highlight.placement().cancel(player, "§7Build mode off.");
                }
            }
            case "production" -> {
                if (needIsland(player, host)) {
                    production(player, host);
                }
            }
            default -> player.sendMessage("§7/island §8| §7create §8| §7home §8| §7build §8| §7land §8| §7belts §8| §7tier §8| §7production");
        }
        return true;
    }

    private boolean needIsland(Player player, IslandHost host) {
        if (highlight == null) {
            player.sendMessage("§cIsland building is not loaded.");
            return false;
        }
        if (host == null) {
            player.sendMessage("§7Claim your island first: §f/island create");
            return false;
        }
        return true;
    }

    private void production(Player player, IslandHost host) {
        List<LogisticsService.Route> routes = highlight.logistics().routes(host);
        player.sendMessage("§6=== Production ===");
        if (routes.isEmpty()) {
            player.sendMessage("§7No belts from any chute yet. Quarry → belt → Storage Hut.");
            return;
        }
        for (LogisticsService.Route route : routes) {
            Res res = route.lastRes();
            player.sendMessage("§7" + route.from().label() + " §8→ "
                    + (route.deadEnd() ? "§cdead end" : "§f" + route.to().label())
                    + " §8(" + route.length() + " tiles" + (res != null ? ", " + res.display() : "") + ")");
        }
    }

    private void admin(Player player, String[] args) {
        if (!player.hasPermission("aetherion.guild.admin")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        if (highlight == null) {
            player.sendMessage("§cIsland highlight not loaded.");
            return;
        }
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "fx" -> {
                String which = args.length < 3 ? "unlock" : args[2].toLowerCase(Locale.ROOT);
                switch (which) {
                    case "foreshadow" -> highlight.unlock().foreshadowIsland(player);
                    case "guild" -> highlight.unlock().announceGuild(player);
                    case "guildhint" -> highlight.unlock().foreshadowGuild(player);
                    default -> highlight.unlock().announceIsland(player);
                }
            }
            case "rebuild" -> {
                IslandHost host = highlight.hosts().at(player.getLocation());
                if (host == null) {
                    player.sendMessage("§cStand on an island.");
                    return;
                }
                int tiles = highlight.logistics().rebuild(host);
                player.sendMessage("§aRe-placed " + tiles + " conveyor tiles from island_logistics.yml.");
            }
            case "reload-templates" -> {
                highlight.templates().reload();
                Res.clearCache();
                player.sendMessage("§aTemplates reloaded from plugins/AetherionGuilds/templates/.");
            }
            case "info" -> {
                IslandHost host = highlight.hosts().at(player.getLocation());
                player.sendMessage("§6Island highlight §8| §7paste jobs: §f" + highlight.paste().pending()
                        + " §8| §7cargo: §f" + highlight.logistics().cargoCount());
                if (host != null) {
                    player.sendMessage("§7Host §f" + host.key() + " §8| §7structures §f"
                            + highlight.structures().of(host).size() + " §8| §7belts §f" + highlight.logistics().count(host)
                            + " §8| §7parcels §f" + highlight.hosts().parcels(host).size()
                            + " §8| §7active §f" + highlight.logistics().isActive(host));
                }
            }
            default -> player.sendMessage("§7/island admin fx [unlock|foreshadow|guild|guildhint] §8| §7rebuild §8| §7reload-templates §8| §7info");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("create", "home", "menu", "build", "land", "belts", "tier", "production", "done")
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("aetherion.guild.admin")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("fx", "rebuild", "reload-templates", "info").filter(o -> o.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
