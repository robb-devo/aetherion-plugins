package de.aetherion.gnomon;

import de.aetherion.gnomon.ai.Decision;
import de.aetherion.gnomon.ai.MoveSpec;
import de.aetherion.gnomon.encounter.Encounter;
import de.aetherion.gnomon.render.Parts;
import de.aetherion.gnomon.world.TempBlocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /gnomon - admin tools to run, tune and read the fight.
 */
final class GnomonCommand implements TabExecutor {

    private static final List<String> SUBS = List.of(
            "start", "stop", "setarena", "debug", "brain", "force", "hp", "skip", "status", "cleanup", "reload");

    private final GnomonPlugin plugin;

    GnomonCommand(GnomonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> start(sender, args);
            case "stop" -> {
                int n = 0;
                for (Encounter e : new ArrayList<>(plugin.encounters())) {
                    plugin.abort(e, "stopped by " + sender.getName());
                    n++;
                }
                sender.sendMessage("§8[§fGnomon§8] §7Stopped " + n + " fight(s).");
            }
            case "setarena" -> setArena(sender, args);
            case "debug" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                boolean on = plugin.toggleDebug(p);
                sender.sendMessage("§8[§fGnomon§8] §7Mind HUD " + (on ? "§aon" : "§coff") + "§7.");
            }
            case "brain" -> {
                Encounter e = first(sender);
                if (e != null) {
                    for (String line : e.dump()) {
                        sender.sendMessage(line);
                    }
                }
            }
            case "force" -> {
                Encounter e = first(sender);
                if (e == null) {
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§7/gnomon force <" + String.join("|", moveIds()) + "|BREATH>");
                    return true;
                }
                if (e.state() != Encounter.State.FIGHT) {
                    sender.sendMessage("§cOnly during the fight.");
                    return true;
                }
                Decision d = e.forced(args[1].toUpperCase(Locale.ROOT));
                if (d == null) {
                    sender.sendMessage("§cUnknown move.");
                    return true;
                }
                if (d.spec() != null && d.spec().requires() != null && d.exploit() == null) {
                    sender.sendMessage("§e" + d.id() + " needs a learned " + d.spec().requires()
                            + " read; running it with an honest aim.");
                }
                e.startDecision(d);
                sender.sendMessage("§8[§fGnomon§8] §7Forced §f" + d.id()
                        + (d.exploit() != null ? " §7spending §c" + d.exploit() : "") + "§7.");
            }
            case "hp" -> {
                Encounter e = first(sender);
                if (e == null || args.length < 2) {
                    sender.sendMessage("§7/gnomon hp <percent>");
                    return true;
                }
                try {
                    double pct = Double.parseDouble(args[1]);
                    sender.sendMessage(e.setHealthPercent(pct) ? "§7HP set to " + pct + "%." : "§cOnly during the fight.");
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cNot a number.");
                }
            }
            case "skip" -> {
                Encounter e = first(sender);
                if (e != null) {
                    e.skipIntro();
                    sender.sendMessage("§7Intro skipped.");
                }
            }
            case "status" -> {
                if (plugin.encounters().isEmpty()) {
                    sender.sendMessage("§8[§fGnomon§8] §7No fight. Arena: "
                            + (plugin.cfg().arenaCenter() == null ? "§cnot set" : "§f" + GnomonPlugin.describe(plugin.cfg().arenaCenter())));
                }
                for (Encounter e : plugin.encounters()) {
                    sender.sendMessage("§8[§fGnomon§8] §7" + e.status());
                }
            }
            case "cleanup" -> {
                if (!plugin.encounters().isEmpty()) {
                    sender.sendMessage("§cA fight is running - /gnomon stop first (that cleans up too).");
                    return true;
                }
                int displays = 0;
                for (World w : Bukkit.getWorlds()) {
                    displays += Parts.sweep(w);
                }
                int blocks = TempBlocks.recover(new File(plugin.getDataFolder(), "journal.yml"), plugin.getLogger());
                sender.sendMessage("§8[§fGnomon§8] §7Removed " + displays + " display(s), restored " + blocks + " block(s).");
            }
            case "reload" -> {
                plugin.reloadGnomonConfig();
                sender.sendMessage("§8[§fGnomon§8] §7Config reloaded (applies to the next fight).");
            }
            default -> help(sender);
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        Location center;
        boolean here = args.length >= 2 && args[1].equalsIgnoreCase("here");
        if (here) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return;
            }
            center = p.getLocation();
        } else {
            center = plugin.cfg().arenaCenter();
            if (center == null) {
                sender.sendMessage("§cNo arena set. Stand in the middle of a flat, open area (radius "
                        + (int) plugin.cfg().arenaRadius() + ") and use §f/gnomon setarena§c, or §f/gnomon start here§c.");
                return;
            }
        }
        String error = plugin.start(sender instanceof Player p ? p : null, center);
        if (error != null) {
            sender.sendMessage("§c" + error);
        } else {
            sender.sendMessage("§8[§fGnomon§8] §7The dial draws itself. Step onto it (survival) to fight.");
        }
    }

    private void setArena(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return;
        }
        Location l = p.getLocation();
        plugin.getConfig().set("arena.world", l.getWorld().getName());
        plugin.getConfig().set("arena.x", l.getBlockX() + 0.5);
        plugin.getConfig().set("arena.y", (double) l.getBlockY());
        plugin.getConfig().set("arena.z", l.getBlockZ() + 0.5);
        if (args.length >= 2) {
            try {
                plugin.getConfig().set("arena.radius", Math.max(12, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                sender.sendMessage("§cRadius must be a number; kept " + (int) plugin.cfg().arenaRadius() + ".");
            }
        }
        plugin.saveConfig();
        plugin.reloadGnomonConfig();
        sender.sendMessage("§8[§fGnomon§8] §7Arena set to §f" + GnomonPlugin.describe(l) + " §7radius §f"
                + (int) plugin.cfg().arenaRadius() + "§7.");
    }

    private Encounter first(CommandSender sender) {
        for (Encounter e : plugin.encounters()) {
            return e;
        }
        sender.sendMessage("§7No fight running.");
        return null;
    }

    private List<String> moveIds() {
        List<String> ids = new ArrayList<>();
        for (MoveSpec s : plugin.cfg().moves()) {
            ids.add(s.id());
        }
        return ids;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§f§lGNOMON §8· §7/gnomon start [here] · stop · setarena [radius] · debug · brain · "
                + "force <move> · hp <pct> · skip · status · cleanup · reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("force")) {
            List<String> ids = moveIds();
            ids.add("BREATH");
            return filter(ids, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return filter(List.of("here"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                out.add(o);
            }
        }
        return out;
    }
}
