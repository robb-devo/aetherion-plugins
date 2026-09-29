package de.aetherion.foraging.isle;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /grove}. On its own it opens your Grove Journal. {@code tour} walks you through the cast,
 * {@code go <place>} points the wayfinder and {@code stop} clears it, {@code where} says which forest
 * you're in. The boards ({@code board}, {@code bench}, {@code ledger}) open when you're standing by
 * their keeper — or anywhere for admins. Admins also get {@code dev [action]}.
 */
public final class GroveCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "aetherion.forage.admin";
    /** Content devs (DEV menu → WORLDS) may open the DEV hub too. */
    private static final String CONTENT = "aetherion.dev.content";
    private static final double KEEPER_REACH_SQ = 8.0d * 8.0d;

    private final ForageIsle isle;

    GroveCommand(ForageIsle isle) {
        this.isle = isle;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" , "journal" -> isle.menus().openJournal(player);
            case "mastery" -> isle.menus().openMastery(player);
            case "cabinet" -> isle.menus().openCabinet(player, false);
            case "places", "map" -> isle.menus().openPlaces(player);
            case "tour" -> isle.compass().startTour(player, isle.profiles().get(player));
            case "stop" -> {
                isle.compass().stop(player);
                player.sendMessage("§7Wayfinder off.");
            }
            case "go" -> {
                if (args.length < 2) {
                    player.sendMessage("§7/grove go <forest|place|updraft> §8· §7e.g. §f/grove go blossom_pagoda");
                    return true;
                }
                Location at = isle.compass().resolve(args[1], isle.isleWorld());
                if (at == null) {
                    player.sendMessage("§cDon't know §f" + args[1] + "§c. §7Try §f/grove places§7.");
                    return true;
                }
                isle.compass().point(player, ForageText.pretty(args[1]), at);
            }
            case "where" -> {
                Location at = player.getLocation();
                Grove grove = isle.grove(at);
                Landmark place = isle.landmark(at);
                if (!isle.onIsle(at)) {
                    player.sendMessage("§7You're not on Foraging Eldervale. §f/forage §7takes you there.");
                    return true;
                }
                player.sendMessage("§2❦ " + (grove == null ? "§7Between forests" : grove.colored() + " §8· §7" + grove.tagline())
                        + (place == null ? "" : " §8· " + place.colored()) + " §8· §7weather §f"
                        + ForageText.pretty(String.valueOf(isle.weatherKind(player))));
            }
            case "board" -> keeper(player, ForageCast.BOARD_CLERK);
            case "bench" -> keeper(player, ForageCast.WOODWRIGHT);
            case "ledger" -> keeper(player, ForageCast.ARCHIVIST);
            case "dev" -> {
                if (!player.hasPermission(ADMIN) && !player.hasPermission(CONTENT)) {
                    player.sendMessage("§cAdmin only.");
                    return true;
                }
                if (args.length >= 2) {
                    player.sendMessage(isle.dev().run(player, args[1]));
                } else {
                    isle.dev().open(player);
                }
            }
            default -> player.sendMessage("§7/grove §8[§7tour · go <place> · stop · where · places · mastery · cabinet · board · bench · ledger§8]");
        }
        return true;
    }

    private void keeper(Player player, String role) {
        double[] at = isle.config().castAnchor(role);
        boolean near = at != null && player.getWorld() == isle.isleWorld()
                && player.getLocation().distanceSquared(new Location(player.getWorld(), at[0], at[1], at[2])) <= KEEPER_REACH_SQ;
        if (near || player.hasPermission(ADMIN)) {
            isle.cast().talk(player, role);
            return;
        }
        ForageCast.Member member = ForageCast.member(role);
        Location target = isle.compass().resolve(role, isle.isleWorld());
        player.sendMessage("§7That's " + member.name() + "'s job — go and see them. §8(Pointing the way.)");
        if (target != null) {
            isle.compass().point(player, member.name(), target);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("tour", "go", "stop", "where", "places", "mastery", "cabinet", "board", "bench", "ledger")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            if ((sender.hasPermission(ADMIN) || sender.hasPermission(CONTENT)) && "dev".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("dev");
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("go")) {
            List<String> ids = new ArrayList<>();
            for (Grove grove : Grove.values()) {
                ids.add(grove.id());
            }
            ids.addAll(isle.config().landmarks().keySet());
            ids.addAll(isle.config().updrafts().keySet());
            ids.addAll(ForageCast.ROLES);
            for (String id : ids) {
                if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(id);
                }
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("dev") && sender.hasPermission(ADMIN)) {
            for (String s : List.of("where", "status", "reload", "event:", "critter:", "find:", "tonic:", "cast:", "updraft:", "profile:", "tp:")) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
