package de.aetherion.pit.command;

import de.aetherion.pit.AetherionPit;
import de.aetherion.pit.data.PitDataStore;
import de.aetherion.pit.menu.PitShopGUI;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class PitCommand implements CommandExecutor, TabCompleter {

    private final AetherionPit plugin;

    public PitCommand(AetherionPit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            PitDataStore.Stats stats = plugin.data().of(player);
            player.sendMessage("§8§m--------------------------------");
            player.sendMessage("§5Hub Pit §7· Lvl §f" + stats.level()
                    + " §8· §6" + stats.gold() + "g");
            player.sendMessage("§e/pit spawn §7· §e/pit shop §7· §e/pit play §7(= /aetherion)");
            player.sendMessage("§7Safe box: §f" + plugin.safeZone().describe());
            player.sendMessage("§8§m--------------------------------");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "spawn", "hub" -> {
                Location spawn = plugin.safeZone().spawn();
                if (spawn == null) {
                    player.sendMessage("§cSpawn not configured.");
                    return true;
                }
                player.teleport(spawn);
                player.sendMessage("§aBack to safe spawn.");
            }
            case "shop" -> PitShopGUI.open(player);
            case "play", "aetherion", "mmor" -> plugin.transfer().toAetherion(player);
            case "stats" -> {
                PitDataStore.Stats stats = plugin.data().of(player);
                int need = plugin.levels().xpForNext(stats.level());
                player.sendMessage("§5Pit §7Lvl §f" + stats.level()
                        + " §7· XP §f" + stats.xp() + "§7/§f" + need
                        + " §7· §6" + stats.gold() + "g");
            }
            case "setsafebox" -> {
                if (!player.hasPermission("aetherion.pit.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                if (args.length < 5) {
                    player.sendMessage("§c/pit setsafebox <minX> <maxX> <minZ> <maxZ>");
                    return true;
                }
                try {
                    double minX = Double.parseDouble(args[1]);
                    double maxX = Double.parseDouble(args[2]);
                    double minZ = Double.parseDouble(args[3]);
                    double maxZ = Double.parseDouble(args[4]);
                    plugin.getConfig().set("safe-zone.world", player.getWorld().getName());
                    plugin.getConfig().set("safe-zone.min-x", Math.min(minX, maxX));
                    plugin.getConfig().set("safe-zone.max-x", Math.max(minX, maxX));
                    plugin.getConfig().set("safe-zone.min-z", Math.min(minZ, maxZ));
                    plugin.getConfig().set("safe-zone.max-z", Math.max(minZ, maxZ));
                    plugin.saveConfig();
                    plugin.safeZone().reload();
                    player.sendMessage("§aSafe box = §f" + plugin.safeZone().describe());
                } catch (NumberFormatException ex) {
                    player.sendMessage("§cInvalid numbers.");
                }
            }
            case "placenpc" -> {
                if (!player.hasPermission("aetherion.pit.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c/pit placenpc <aetherion|discord|shop>");
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!List.of("aetherion", "discord", "shop").contains(id)) {
                    player.sendMessage("§cUnknown NPC. Use aetherion, discord, or shop.");
                    return true;
                }
                plugin.hubNpcs().place(id, player.getLocation());
                player.sendMessage("§aPlaced hub NPC §f" + id + "§a here.");
            }
            case "pasteschem" -> {
                if (!player.hasPermission("aetherion.pit.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                player.sendMessage("§ePasting hub schem (can lag a bit)…");
                boolean started = de.aetherion.pit.world.HubSchemPaster.pasteNow(plugin, true);
                if (!started) {
                    player.sendMessage("§cPaste did not start (already running or schem missing).");
                }
            }
            default -> player.sendMessage(
                    "§c/pit [spawn|shop|play|stats|setsafebox|placenpc|pasteschem]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            Stream<String> opts = Stream.of("spawn", "shop", "play", "stats");
            if (sender.hasPermission("aetherion.pit.admin")) {
                opts = Stream.concat(opts, Stream.of("setsafebox", "placenpc", "pasteschem"));
            }
            String p = args[0].toLowerCase(Locale.ROOT);
            return opts.filter(o -> o.startsWith(p)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("placenpc")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("aetherion", "discord", "shop").filter(o -> o.startsWith(p)).toList();
        }
        return List.of();
    }
}
