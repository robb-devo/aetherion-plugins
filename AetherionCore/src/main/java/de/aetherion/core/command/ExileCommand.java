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
        // Borderlands terrain the 10-14/15 quay clears aired, back from FAWE history; arena leftovers out.
        if ("restoremap".equals(sub) || "cleararena".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            try {
                if ("restoremap".equals(sub)) {
                    de.aetherion.core.exile.ExileMapRestore.restore(plugin, sender, args);
                } else {
                    de.aetherion.core.exile.ExileMapRestore.clearArena(plugin, sender, args);
                }
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cBad number: §f" + ex.getMessage());
            }
            return true;
        }
        // Mooring chain (and the causeway) back where the Seraphine clears cut them. Dry run unless "apply".
        if ("repairquay".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            de.aetherion.core.exile.ExileQuayRepair.run(plugin, sender, args.length >= 2 && "apply".equalsIgnoreCase(args[1]));
            return true;
        }
        // Turn the Veil Quay 180° so the ferry casts off east, away from the main island. Dry run unless "apply".
        if ("turnquay".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            de.aetherion.core.exile.ExileQuayPaste.turn(plugin, sender, args.length >= 2 && "apply".equalsIgnoreCase(args[1]));
            return true;
        }
        // Exile host: old block sword out of the crater, water curtain out of the void. Dry run unless "apply".
        if ("tidy".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            ExileHost host = plugin.exileHost();
            de.aetherion.core.exile.ExileTidy.run(plugin, sender, host == null ? null : host.world(),
                    args.length >= 2 && "apply".equalsIgnoreCase(args[1]));
            return true;
        }
        // Exile host: the Little Theatre becomes Seraphine's arena (hall emptied, her stage built in). Dry run unless "apply".
        if ("theatre".equals(sub) || "theater".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            ExileHost host = plugin.exileHost();
            de.aetherion.core.exile.ExileTidy.theatre(plugin, sender, host == null ? null : host.world(),
                    args.length >= 2 && "apply".equalsIgnoreCase(args[1]));
            return true;
        }
        // Staff: the boss vials the exile altars take (how players earn them comes later).
        // /exile vial <boss|all> [player] [amount]
        if ("vial".equals(sub) || "phiole".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            giveVials(sender, args);
            return true;
        }
        // Exile host: dig the Iron Ring's approach out of the crater debris (runs once on its own). "undo" puts it back.
        if ("gatecut".equals(sub)) {
            if (!admin) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            ExileHost host = plugin.exileHost();
            org.bukkit.World exile = host == null ? null : host.world();
            if (args.length >= 2 && "undo".equalsIgnoreCase(args[1])) {
                de.aetherion.core.exile.ExileGateCut.undo(plugin, sender);
            } else {
                de.aetherion.core.exile.ExileGateCut.run(plugin, sender, exile);
            }
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
                de.aetherion.core.network.NetworkRouter router = plugin.router();
                if (router != null && de.aetherion.core.network.ServerNames.isHub(router.here())) {
                    // The lobby holds no character and no story: the road runs through the main world, which decides.
                    if (!router.travel(player, router.serverFor("main"), de.aetherion.core.network.RouteBook.Kind.COMMAND,
                            "exile go", null, "command:exile")) {
                        player.sendMessage("§7That road is closed right now — try again in a moment.");
                    }
                    return true;
                }
                ExileQuay quay = plugin.exileQuay();
                String no = quay == null ? null : quay.refusal(player);
                if (quay != null && quay.placed()) {
                    if (quay.near(player, 48)) {
                        quay.board(player, "command");
                    } else if (no == null) {
                        // Unlocked: straight across from anywhere, like every other road.
                        link.send(player);
                    } else {
                        player.sendMessage(Say.t(player, "§8Ferryman §8» §7", "§8Fährmann §8» §7") + no);
                        player.sendMessage(Say.t(player,
                                "§8Ferry §8» §7The ferry leaves from the Veil Quay, off the far east edge of the Borderlands, in the fog.",
                                "§8Fähre §8» §7Die Fähre legt am Schleierkai ab, am äußersten Ostrand der Borderlands, im Nebel."));
                    }
                    return true;
                }
                // No islet on this backend (isles, dungeons): a direct hop, but the story still decides who may cross.
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

    private void giveVials(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§7/exile vial <" + String.join("|", vialIds()) + "|all> [player] [amount]");
            return;
        }
        List<de.aetherion.core.exile.ExileVials.Vial> which = new ArrayList<>();
        if ("all".equalsIgnoreCase(args[1])) {
            which.addAll(de.aetherion.core.exile.ExileVials.ALL);
        } else {
            de.aetherion.core.exile.ExileVials.Vial v = de.aetherion.core.exile.ExileVials.byBoss(args[1]);
            if (v == null) {
                sender.sendMessage("§cUnknown boss §f" + args[1] + "§c: " + String.join(", ", vialIds()));
                return;
            }
            which.add(v);
        }
        Player target = args.length >= 3 ? org.bukkit.Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage("§cPlayer not online here.");
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(16, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        boolean german = Say.german(target);
        for (de.aetherion.core.exile.ExileVials.Vial v : which) {
            for (org.bukkit.inventory.ItemStack rest : target.getInventory()
                    .addItem(de.aetherion.core.exile.ExileVials.create(v, amount, german)).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), rest);
            }
        }
        sender.sendMessage("§a" + which.size() + " vial kind(s) x" + amount + " → §f" + target.getName());
    }

    private static List<String> vialIds() {
        List<String> ids = new ArrayList<>();
        for (de.aetherion.core.exile.ExileVials.Vial v : de.aetherion.core.exile.ExileVials.ALL) {
            ids.add(v.boss());
        }
        return ids;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2 && ("vial".equalsIgnoreCase(args[0]) || "phiole".equalsIgnoreCase(args[0]))
                && sender.hasPermission("aetherion.core.admin")) {
            List<String> out = new ArrayList<>();
            for (String id : vialIds()) {
                if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(id);
                }
            }
            if ("all".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                out.add("all");
            }
            return out;
        }
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("aetherion.core.admin")
                || sender instanceof org.bukkit.command.ConsoleCommandSender;
        for (String s : admin
                ? List.of("go", "return", "replay", "stop", "where", "setquay", "causeway", "paste",
                        "placequay", "quayundo", "clearsaint", "restoremap", "cleararena", "repairquay", "turnquay",
                        "gatecut", "tidy", "theatre", "vial")
                : List.of("go", "return", "replay", "stop", "where")) {
            if (s.startsWith(prefix)) {
                out.add(s);
            }
        }
        return out;
    }
}
