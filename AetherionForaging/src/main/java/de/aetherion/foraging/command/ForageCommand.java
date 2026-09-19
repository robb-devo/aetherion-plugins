package de.aetherion.foraging.command;

import de.aetherion.foraging.AetherionForaging;
import de.aetherion.foraging.habitat.ForageHabitat;
import de.aetherion.foraging.island.ForageIslePaste;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ForageCommand implements CommandExecutor, TabCompleter {

    private final AetherionForaging plugin;

    public ForageCommand(AetherionForaging plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7/forageadmin isle … §8· §7/forageadmin habitat … §8· §7/forageadmin weather|grove|guide");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("habitat") || sub.equals("habitats") || sub.equals("zone") || sub.equals("zones")) {
            return handleHabitat(sender, args);
        }
        if (sub.equals("weather") || sub.equals("wx")) {
            return handleWeather(sender);
        }
        if (sub.equals("grove") || sub.equals("ritual") || sub.equals("rituals")) {
            return handleGrove(sender, args);
        }
        if (sub.equals("guide") || sub.equals("npc") || sub.equals("canopy")) {
            return handleGuide(sender, args);
        }
        if (!sub.equals("isle") && !sub.equals("island")) {
            sender.sendMessage("§7/forageadmin isle … §8| §7/forageadmin habitat … §8| §7/forageadmin weather|grove|guide");
            return true;
        }
        if (!sender.hasPermission("aetherion.forage.admin")) {
            sender.sendMessage("§cAdmin only.");
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("clear") || action.equals("undo") || action.equals("remove")) {
            ForageIslePaste.clear(plugin, sender);
            return true;
        }
        if (action.equals("scan")) {
            de.aetherion.foraging.island.ForageIsleScan.run(plugin, sender);
            return true;
        }
        if (action.equals("light") || action.equals("lights")) {
            de.aetherion.foraging.island.ForageIsleLight.run(plugin, sender);
            return true;
        }
        if (action.equals("pads") || action.equals("syncpads")) {
            ForageIslePaste.syncPadsOnly(plugin, sender);
            return true;
        }
        if (action.equals("ensurearea") || action.equals("area")) {
            plugin.habitats().ensureParentIsleArea();
            sender.sendMessage("§aFORAGE_ISLE area ensure requested.");
            return true;
        }
        if (action.equals("paste")) {
            boolean here = args.length >= 3 && args[2].equalsIgnoreCase("here");
            if (here) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c/forageadmin isle paste here needs a player.");
                    return true;
                }
                ForageIslePaste.paste(plugin, sender, player.getLocation());
            } else {
                ForageIslePaste.paste(plugin, sender);
            }
            return true;
        }
        sender.sendMessage("§7/forageadmin isle clear|paste|light|pads|scan|ensurearea");
        return true;
    }

    private boolean handleHabitat(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aetherion.forage.admin")) {
            sender.sendMessage("§cAdmin only.");
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "list", "ls" -> {
                sender.sendMessage("§8§m--------------------------------");
                sender.sendMessage("§aHabitat sense §7(forage isle only, r="
                        + plugin.getConfig().getInt("habitat-sense.radius", 12) + ")");
                sender.sendMessage("§8spruce/snow→Snow · dark oak/mushrooms→Dark Thicket · acacia→Savanna");
                sender.sendMessage("§8oak/birch→Plains · cherry→Flower · mangrove→Swamp …");
                if (!plugin.habitats().all().isEmpty()) {
                    sender.sendMessage("§7AABB fallbacks §8(" + plugin.habitats().all().size() + ")");
                    for (ForageHabitat h : plugin.habitats().all()) {
                        sender.sendMessage("§e#" + h.priority() + " §f" + h.describe());
                    }
                }
                sender.sendMessage("§8§m--------------------------------");
            }
            case "here", "at" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }
                var sensed = plugin.habitats().sense().detect(player.getLocation());
                ForageHabitat box = plugin.habitats().at(player.getLocation());
                if (sensed != null) {
                    player.sendMessage("§aSense: §f" + sensed.display() + " §8(" + sensed.id() + ")");
                } else {
                    boolean onIsle = de.aetherion.foraging.habitat.ForageHabitatService
                            .inIsleFootprint(player.getLocation());
                    player.sendMessage(onIsle
                            ? "§7No material habitat nearby §8(on isle)."
                            : "§cNot on forage isle footprint.");
                }
                if (box != null) {
                    player.sendMessage("§7AABB fallback: §f" + box.display() + " §8(" + box.id() + ")");
                }
            }
            case "pos1", "p1" -> {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                plugin.habitats().setPos1(player, player.getLocation());
                player.sendMessage("§aHabitat pos1 §7= §f"
                        + fmt(player.getLocation()));
            }
            case "pos2", "p2" -> {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                plugin.habitats().setPos2(player, player.getLocation());
                player.sendMessage("§aHabitat pos2 §7= §f"
                        + fmt(player.getLocation()));
            }
            case "save", "create", "set" -> {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("§c/forageadmin habitat save <id> [display…]");
                    return true;
                }
                String id = args[2];
                String display = args.length >= 4
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                        : null;
                int priority = 20;
                plugin.habitats().saveFromSelection(player, id, display, priority);
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.habitats().reload();
                if (plugin.weather() != null) {
                    plugin.weather().reload();
                }
                if (plugin.grove() != null) {
                    plugin.grove().reload();
                }
                if (plugin.guide() != null) {
                    plugin.guide().reload();
                }
                sender.sendMessage("§aHabitats + weather + grove + guide reloaded.");
            }
            default -> sender.sendMessage(
                    "§7/forageadmin habitat list|here|pos1|pos2|save <id>|reload");
        }
        return true;
    }

    private boolean handleWeather(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (plugin.weather() == null) {
            player.sendMessage("§cWeather service offline.");
            return true;
        }
        var state = plugin.weather().current(player);
        player.sendMessage("§8§m--------------------------------");
        player.sendMessage("§aIsle weather §8· §f" + state.kind()
                + " §8(" + state.source() + ")");
        player.sendMessage("§7Habitat §f" + state.habitat()
                + " §8· §7Phase §f" + state.phase());
        player.sendMessage("§8§m--------------------------------");
        return true;
    }

    private boolean handleGrove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aetherion.forage.admin")) {
            sender.sendMessage("§cAdmin only.");
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "here";
        if (action.equals("set") || action.equals("here")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            player.getInventory().addItem(de.aetherion.foraging.ritual.GroveRitualService.createAnchor());
            player.sendMessage("§aGrove table anchor §7given — place via right-click (DEV).");
            return true;
        }
        if (action.equals("table") || action.equals("ensure") || action.equals("pack")) {
            if (!(sender instanceof Player player)) {
                return true;
            }
            if (action.equals("pack") && plugin.grove() != null) {
                plugin.grove().packUp(player);
                return true;
            }
            player.getInventory().addItem(de.aetherion.foraging.ritual.GroveRitualService.createAnchor());
            player.sendMessage("§7Use the DEV anchor to place — no auto table.");
            return true;
        }
        if (plugin.grove() == null || plugin.grove().grove() == null || !plugin.grove().isPlaced()) {
            sender.sendMessage("§cGrove not placed. §7DEV → Services → Grove Enchanting Table");
            return true;
        }
        sender.sendMessage("§aGrove §7at §f" + fmt(plugin.grove().grove())
                + (plugin.getConfig().getBoolean("rituals.enabled", true) ? " §a(on)" : " §c(off)"));
        return true;
    }

    private boolean handleGuide(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aetherion.forage.admin")) {
            sender.sendMessage("§cAdmin only.");
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "here";
        if (action.equals("set") || action.equals("here") || action.equals("give")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            player.getInventory().addItem(de.aetherion.foraging.npc.IsleGuideNpc.createAnchor());
            player.sendMessage("§aMiss Canopy anchor §7given — place via right-click (DEV).");
            return true;
        }
        if (action.equals("talk") || action.equals("test")) {
            if (!(sender instanceof Player player)) {
                return true;
            }
            if (plugin.guide() != null) {
                plugin.guide().talk(player);
            }
            return true;
        }
        if (action.equals("despawn") || action.equals("remove")) {
            if (!(sender instanceof Player player)) {
                return true;
            }
            if (plugin.guide() != null) {
                plugin.guide().despawn(player);
            }
            return true;
        }
        sender.sendMessage("§7/forageadmin guide give|talk|despawn §8· place via DEV menu");
        return true;
    }

    private static String fmt(org.bukkit.Location loc) {
        return String.format("%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aetherion.forage.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("isle", "habitat", "weather", "grove", "guide"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("isle")) {
            return filter(List.of("clear", "paste", "light", "pads", "scan", "ensurearea"), args[1]);
        }
        if (args.length == 2 && startsHabitat(args[0])) {
            return filter(List.of("list", "here", "pos1", "pos2", "save", "reload"), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("grove") || args[0].equalsIgnoreCase("ritual"))) {
            return filter(List.of("here", "set", "info", "table"), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("guide") || args[0].equalsIgnoreCase("npc"))) {
            return filter(List.of("here", "set", "talk", "ensure"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("isle") && args[1].equalsIgnoreCase("paste")) {
            return filter(List.of("here"), args[2]);
        }
        if (args.length == 3 && startsHabitat(args[0]) && args[1].equalsIgnoreCase("save")) {
            return filter(plugin.habitats().all().stream().map(ForageHabitat::id).collect(Collectors.toList()),
                    args[2]);
        }
        return List.of();
    }

    private static boolean startsHabitat(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        return s.equals("habitat") || s.equals("habitats") || s.equals("zone") || s.equals("zones");
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(t)) {
                out.add(option);
            }
        }
        return out;
    }
}
