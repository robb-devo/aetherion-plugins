package de.aetherion.hub.origin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /origin} — the Origin Journal.
 * <pre>
 * /origin                     open the journal
 * /origin go &lt;place&gt;          point the wayfinder (district, landmark, glowcap, vista, bell, cast:x, camp:x, updraft:x, flight:x)
 * /origin stop                clear the wayfinder
 * /origin tour [stop]         Orla's eight-stop tour
 * /origin settings            ambience / particles
 * /origin wish                wishes made on falling stars
 * /origin dev [action …]      DEV hub (aetherion.origin.dev)
 * </pre>
 */
public final class OriginCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("journal", "go", "stop", "tour", "settings", "wish", "help");

    private final OriginIsle isle;

    OriginCommand(OriginIsle isle) {
        this.isle = isle;
    }

    /** DEV permission: {@code aetherion.origin.dev}, the shared {@code aetherion.dev}, or op. */
    public static boolean dev(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("aetherion.origin.dev") || sender.hasPermission("aetherion.origin.admin")
                || sender.hasPermission("aetherion.dev");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "journal" : args[0].toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player player)) {
            if (sub.equals("dev") && args.length > 1 && args[1].equalsIgnoreCase("status")) {
                sender.sendMessage(isle.running() ? "Origin Isle running." : "Origin Isle stopped.");
            } else {
                sender.sendMessage("§7/origin is a player command.");
            }
            return true;
        }
        switch (sub) {
            case "journal", "j", "open" -> isle.menus().openJournal(player);
            case "go", "find", "where" -> {
                if (args.length < 2) {
                    player.sendMessage("§7/origin go <place> §8· §7e.g. §ffountain_square§7, §fskyreach§7, §fcast:keeper§7, §fcamp:summit");
                    return true;
                }
                String target = String.join("_", Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
                if (!isle.running()) {
                    player.sendMessage("§7Origin is resting right now.");
                } else if (!isle.compass().go(player, target)) {
                    player.sendMessage("§7No place called §f" + target + "§7 on Origin. §8(tab completes)");
                }
            }
            case "stop" -> {
                isle.compass().stop(player);
                player.sendMessage("§7Wayfinder cleared.");
            }
            case "tour" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
                    isle.compass().stopTour(player);
                    player.sendMessage("§7Tour paused. §f/origin tour §7picks it back up.");
                } else if (!isle.running()) {
                    player.sendMessage("§7Origin is resting right now.");
                } else {
                    isle.compass().startTour(player);
                }
            }
            case "settings" -> isle.menus().openPage(player, "settings");
            case "wish", "wishes" -> {
                OriginProfile profile = isle.profiles().get(player);
                boolean falling = isle.events().active() == OriginEvents.Kind.STARFALL;
                player.sendMessage("§d✦ Wishes made on Origin: §f" + profile.wishes + " §8· "
                        + (falling ? "§dThe stars are falling right now — sneak and look up!" : "§7During a Starfall, sneak and look up."));
            }
            case "dev" -> {
                if (!dev(player)) {
                    player.sendMessage("§cDEV only.");
                    return true;
                }
                String action = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
                String result = isle.dev().run(player, action);
                if (result != null) {
                    player.sendMessage(result);
                }
            }
            default -> {
                player.sendMessage("§6Origin §8· §f/origin §7journal §8· §f/origin go <place> §8· §f/origin stop §8· §f/origin tour §8· §f/origin settings §8· §f/origin wish");
                if (dev(player)) {
                    player.sendMessage("§c/origin dev §7[status|reload|event <kind|stop>|toll|anchor <role>|cast <presets|remove|role>|seed|softlight <district|all|here|undo|cancel>|tp <place>|updraft <id>|fly <id>|vista <id>|profile <reset|complete>]");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBS);
            if (dev(sender)) {
                options.add("dev");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("go")) {
            options.addAll(places());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("tour")) {
            options.add("stop");
        } else if (dev(sender) && args[0].equalsIgnoreCase("dev")) {
            if (args.length == 2) {
                options.addAll(OriginDev.ACTIONS);
            } else if (args.length == 3) {
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "event" -> {
                        for (OriginEvents.Kind kind : OriginEvents.Kind.values()) {
                            options.add(kind.id());
                        }
                        options.add("stop");
                    }
                    case "anchor" -> {
                        for (OriginRole role : OriginRole.values()) {
                            options.add(role.id());
                        }
                    }
                    case "cast" -> {
                        options.add("presets");
                        options.add("remove");
                        for (OriginRole role : OriginRole.values()) {
                            options.add(role.id());
                        }
                    }
                    case "softlight" -> {
                        options.addAll(List.of("all", "here", "undo", "cancel"));
                        options.addAll(isle.config().districts().keySet());
                    }
                    case "tp" -> options.addAll(places());
                    case "updraft" -> options.addAll(isle.config().updrafts().keySet());
                    case "fly" -> options.addAll(isle.config().flights().keySet());
                    case "vista" -> options.addAll(isle.config().vistas().keySet());
                    case "profile" -> options.addAll(List.of("reset", "complete", "show"));
                    default -> {
                    }
                }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }

    private List<String> places() {
        OriginConfig c = isle.config();
        List<String> out = new ArrayList<>();
        out.addAll(c.districts().keySet());
        out.addAll(c.landmarks().keySet());
        for (String id : c.waystones().keySet()) {
            // Some glowcap ids are also district ids (skyreach, colosseum, eastwood): the prefix always works.
            out.add("waystone:" + id);
        }
        for (String id : c.vistas().keySet()) {
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        out.addAll(c.bells().keySet());
        for (OriginRole role : OriginRole.values()) {
            out.add("cast:" + role.id());
        }
        for (String id : c.updrafts().keySet()) {
            out.add("updraft:" + id);
        }
        for (String id : c.flights().keySet()) {
            out.add("flight:" + id);
        }
        isle.hub().spawns().forEach(spawn -> out.add("camp:" + spawn.id()));
        return out;
    }
}
