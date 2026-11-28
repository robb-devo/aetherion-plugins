package de.aetherion.tutorial;

import de.aetherion.tutorial.isle.Isle;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /tutorial} is the players' command (visit the island from the main world, leave a visit on the island);
 * {@code /morningbell} is the staff tool for looking at and steering a lesson.
 */
final class TutorialCommand implements TabExecutor {

    private static final List<String> STAFF = List.of("status", "step", "next", "reset", "skip", "tp", "marks", "boss", "visit", "poke");

    private final TutorialPlugin plugin;

    TutorialCommand(TutorialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tutorial")) {
            return players(sender, args);
        }
        if (!sender.hasPermission(TutorialPlugin.ADMIN)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        return staff(sender, args);
    }

    // ------------------------------------------------------------------ /tutorial

    private boolean players(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only. Staff: /morningbell");
            return true;
        }
        if (plugin.arrival() != null) {
            plugin.arrival().visit(player);
            return true;
        }
        Isle isle = plugin.isle();
        if (isle == null) {
            player.sendMessage(Txt.t(player, "§7The island is reached from the lobby or the mainland: §f/tutorial §7there.",
                    "§7Zur Insel geht es von der Lobby oder vom Festland: dort §f/tutorial§7."));
            return true;
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("pick")) {
            // the click on an answer that was offered in the chat (classic chat talk)
            try {
                isle.director().pick(player, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                // not a number: nothing was picked
            }
            return true;
        }
        boolean leave = args.length > 0 && (args[0].equalsIgnoreCase("leave") || args[0].equalsIgnoreCase("verlassen")
                || args[0].equalsIgnoreCase("ende"));
        if (!isle.director().visiting(player)) {
            player.sendMessage(Txt.t(player, "§e✦ §7This is your first morning: §fthe bar at the top §7shows your next step.",
                    "§e✦ §7Das ist dein erster Morgen: §fDie Leiste oben §7zeigt deinen nächsten Schritt."));
            return true;
        }
        if (!leave) {
            player.sendMessage(Txt.t(player, "§e✦ §7You are visiting. §f/tutorial leave §7takes you back to the mainland at any time.",
                    "§e✦ §7Du bist zu Besuch. §f/tutorial verlassen §7bringt dich jederzeit zurück aufs Festland."));
            return true;
        }
        isle.director().leave(player);
        return true;
    }

    // ------------------------------------------------------------------ /morningbell

    private boolean staff(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        Isle isle = plugin.isle();
        if (sub.equals("visit")) {
            return ticket(sender, args);
        }
        if (isle == null) {
            sender.sendMessage("§eMorningbell: role §f" + plugin.role() + "§e on §f" + plugin.here()
                    + "§e, store §f" + plugin.store().directory().getAbsolutePath());
            if (args.length > 1 && sub.equals("status")) {
                describe(sender, args[1]);
            }
            return true;
        }
        switch (sub) {
            case "status" -> {
                if (args.length > 1) {
                    describe(sender, args[1]);
                    return true;
                }
                sender.sendMessage("§eMorningbell §7(isle) on §f" + plugin.here() + "§7, "
                        + isle.marks().keys().size() + " spots, in a lesson now:");
                boolean any = false;
                for (Player online : isle.world().getPlayers()) {
                    String line = isle.director().describe(online);
                    if (line != null) {
                        sender.sendMessage("§7 - §f" + online.getName() + "§7: " + line);
                        any = true;
                    }
                }
                if (!any) {
                    sender.sendMessage("§7 - nobody");
                }
            }
            case "marks" -> sender.sendMessage("§eSpots: §7" + String.join(", ", new java.util.TreeSet<>(isle.marks().keys())));
            case "tp" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    sender.sendMessage("§c/morningbell tp <spot>");
                    return true;
                }
                Location at = isle.marks().at(isle.world(), args[1]);
                if (at == null) {
                    sender.sendMessage("§cNo spot '" + args[1] + "'. /morningbell marks");
                    return true;
                }
                player.teleport(at);
            }
            case "step", "next", "reset", "skip", "boss" -> {
                Player target = target(sender, args, sub.equals("step") ? 2 : 1);
                if (target == null) {
                    sender.sendMessage("§cWho? /morningbell " + sub + (sub.equals("step") ? " <STEP>" : "") + " [player]");
                    return true;
                }
                String wanted = switch (sub) {
                    case "step" -> args.length > 1 ? args[1] : "";
                    case "reset" -> "ARRIVE";
                    case "boss" -> "GO_FLAT";
                    case "skip" -> "TALK_WIM";
                    default -> "+";
                };
                String result = isle.director().steer(target, wanted);
                sender.sendMessage("§e" + target.getName() + "§7: " + result);
            }
            case "poke" -> {
                // /morningbell poke <bell|iron|oda|burr|...> [player]: what the player's own click would do
                Player target = target(sender, args, 2);
                if (target == null || args.length < 2) {
                    sender.sendMessage("§c/morningbell poke <bell|iron|oda|burr|grett|ansel|hilde|wim|tolly> [player]");
                    return true;
                }
                sender.sendMessage(isle.director().poke(target, args[1]) ? "§7poked §f" + args[1] + " §7for §e" + target.getName()
                        : "§cNothing to poke: no lesson for that player, or unknown name '" + args[1] + "'.");
            }
            default -> sender.sendMessage("§e/morningbell §7<" + String.join("|", STAFF) + ">");
        }
        return true;
    }

    /** {@code /morningbell visit <player>}: hand a player a visit ticket (console or staff), on any backend. */
    private boolean ticket(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c/morningbell visit <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cNot online here: " + args[1]);
            return true;
        }
        if (plugin.arrival() != null) {
            plugin.arrival().visit(target);
            sender.sendMessage("§e" + target.getName() + " §7is on the way to the island as a visitor.");
        } else {
            sender.sendMessage("§7Visits start on the main world or in the lobby (/tutorial).");
        }
        return true;
    }

    private void describe(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        UUID id = online != null ? online.getUniqueId() : null;
        if (id == null) {
            try {
                id = UUID.fromString(name);
            } catch (IllegalArgumentException ex) {
                id = Bukkit.getOfflinePlayer(name).getUniqueId();
            }
        }
        TutorialStore.Entry entry = plugin.store().load(id);
        sender.sendMessage("§e" + name + "§7: state §f" + entry.state.name().toLowerCase(Locale.ROOT)
                + "§7, step §f" + (entry.step.isBlank() ? "-" : entry.step)
                + "§7, " + (entry.seconds / 60L) + " min on the isle, stumbles " + entry.deaths
                + (entry.guest ? "§7, §fvisit" : "") + (entry.completedOnce ? "§7, §ffinished once" : "")
                + (entry.skipped ? "§7, short way" : ""));
        if (online == null) {
            plugin.store().forget(id);
        }
    }

    private static Player target(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            return Bukkit.getPlayerExact(args[index]);
        }
        return sender instanceof Player player ? player : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("tutorial")) {
            if (args.length == 1 && plugin.isle() != null) {
                out.add("leave");
            }
            return out;
        }
        if (!sender.hasPermission(TutorialPlugin.ADMIN)) {
            return out;
        }
        if (args.length == 1) {
            for (String sub : STAFF) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2 && plugin.isle() != null) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (args[0].equalsIgnoreCase("tp")) {
                for (String key : plugin.isle().marks().keys()) {
                    if (key.startsWith(prefix)) {
                        out.add(key);
                    }
                }
            } else if (args[0].equalsIgnoreCase("step")) {
                for (String step : plugin.isle().director().stepNames()) {
                    if (step.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(step);
                    }
                }
            }
        }
        return out;
    }
}
