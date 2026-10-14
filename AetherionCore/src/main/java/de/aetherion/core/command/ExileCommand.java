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
        String sub = args.length == 0 ? "go" : args[0].toLowerCase(Locale.ROOT);
        boolean consoleLike = !(sender instanceof Player);
        boolean admin = consoleLike || sender.hasPermission("aetherion.core.admin");
        // Console / RCON / admin: paste full island into aetherion_exile (void world).
        if ("paste".equals(sub) || "pastegesamt".equals(sub) || "map".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            de.aetherion.core.exile.ExileMapPaste.start(plugin, sender);
            return true;
        }
        if ("quayundo".equals(sub) || "undoclear".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            int times = 2;
            if (args.length >= 2) {
                try {
                    times = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                }
            }
            de.aetherion.core.exile.ExileFaweUndo.undo(plugin, sender, "world", null, times);
            return true;
        }
        if ("clearsaint".equals(sub) || "rmseraphine".equals(sub) || "rmsaint".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            String worldName = args.length >= 2 ? args[1] : "world";
            de.aetherion.core.exile.ExileSaintClear.clear(plugin, sender, worldName);
            return true;
        }
        // Walk from the Borderlands cliff to the pier head: stand where it should leave the cliff. "undo" takes it back.
        if ("causeway".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length >= 2 && "undo".equalsIgnoreCase(args[1])) {
                de.aetherion.core.exile.ExileCauseway.undo(plugin, sender);
            } else if (sender instanceof Player p) {
                de.aetherion.core.exile.ExileCauseway.build(plugin, sender, p.getLocation());
            } else {
                sender.sendMessage("§cStand on the Borderlands cliff in-game: §f/exile causeway§c (or §f/exile causeway undo§c).");
            }
            return true;
        }
        // Clear Seraphine arena + paste Veil Quay (main world). Optional: world x y z.
        // No bare "quay" alias: this airs a 150-block box at the target, it must never be a typo away.
        if ("placequay".equals(sub) || "pastequay".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            String worldName = "world";
            int x = de.aetherion.core.exile.ExileQuayPaste.DEFAULT_X;
            int y = de.aetherion.core.exile.ExileQuayPaste.DEFAULT_Y;
            int z = de.aetherion.core.exile.ExileQuayPaste.DEFAULT_Z;
            if (args.length >= 4) {
                try {
                    int i = 1;
                    if (args.length >= 5) {
                        worldName = args[1];
                        i = 2;
                    }
                    x = Integer.parseInt(args[i]);
                    y = Integer.parseInt(args[i + 1]);
                    z = Integer.parseInt(args[i + 2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cUsage: /exile placequay [world] <x> <y> <z>");
                    return true;
                }
            } else if (sender instanceof Player p) {
                worldName = p.getWorld().getName();
                x = p.getLocation().getBlockX();
                y = p.getLocation().getBlockY();
                z = p.getLocation().getBlockZ();
            }
            de.aetherion.core.exile.ExileQuayPaste.start(plugin, sender, worldName, x, y, z);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only (or §f/exile paste§c from console/RCON).");
            return true;
        }
        ExileLink link = plugin.exile();
        ExileHost host = plugin.exileHost();
        if (link == null) {
            player.sendMessage("§cExile link offline.");
            return true;
        }
        switch (sub) {
            case "go", "enter", "ferry" -> {
                if (link.isExileServer()) {
                    player.sendMessage(Say.t(player, "§8Exile §8» §7Already here. §f/exile return §7leaves.",
                            "§8Exil §8» §7Schon da. §f/exile return §7bringt dich zurück."));
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
                                "§8Ferry §8» §7The ferry leaves from the Veil Quay, off the far east edge of the Borderlands, in the fog.",
                                "§8Fähre §8» §7Die Fähre legt am Schleierkai ab, am äußersten Ostrand der Borderlands, im Nebel."));
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
                        player.sendMessage(Say.t(player, "§8Exile §8» §7You're on Aetherion already.",
                                "§8Exil §8» §7Du bist schon auf Aetherion."));
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
                player.sendMessage("§8Exile §8» §7backend §f" + plugin.link().serverName()
                        + " §8· §7exile-server §f" + link.exileServer()
                        + (link.isExileServer() ? " §a(here)" : ""));
                if (plugin.exileQuay() != null) {
                    player.sendMessage(plugin.exileQuay().describe());
                }
            }
            default -> player.sendMessage("§7/exile §8[§7go · return · replay · stop · where"
                    + (player.hasPermission("aetherion.core.admin") ? " · setquay · causeway · paste" : "") + "§8]");
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
        boolean admin = sender.hasPermission("aetherion.core.admin")
                || sender instanceof org.bukkit.command.ConsoleCommandSender;
        for (String s : admin
                ? List.of("go", "return", "replay", "stop", "where", "setquay", "causeway", "paste",
                        "placequay", "quayundo", "clearsaint")
                : List.of("go", "return", "replay", "stop", "where")) {
            if (s.startsWith(prefix)) {
                out.add(s);
            }
        }
        return out;
    }
}
