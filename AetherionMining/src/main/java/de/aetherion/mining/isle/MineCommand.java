package de.aetherion.mining.isle;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /mineisle}. On its own it opens your Mining Journal. {@code tour} points at the next crew
 * member, {@code go <place>} sets the compass and {@code stop} clears it. Admins also get
 * {@code reload}, {@code where} and {@code dev <action>}, which runs the same actions as the DEV menu.
 */
public final class MineCommand implements TabExecutor {

    private final MineIsle isle;

    public MineCommand(MineIsle isle) {
        this.isle = isle;
    }

    private static boolean admin(CommandSender sender) {
        return sender.hasPermission("aetherion.mines.admin") || sender.isOp();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && admin(sender)) {
            sender.sendMessage(isle.reload());
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§7Players only (admins: /mineisle reload).");
            return true;
        }
        if (args.length == 0) {
            isle.menus().openJournal(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "tour" -> isle.compass().tour(player);
            case "stop" -> isle.compass().stop(player);
            case "map", "wick" -> isle.menus().openWick(player);
            case "go" -> {
                if (args.length < 2) {
                    player.sendMessage("§7/mineisle go <place>");
                    return true;
                }
                String wanted = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
                MineProfiles.Profile profile = isle.profiles().of(player);
                for (MineDistricts.District district : isle.districts().all()) {
                    boolean known = profile.districts().contains(district.id()) || admin(player);
                    if (known && (district.id().equals(wanted.replace(' ', '_')) || district.name().toLowerCase(Locale.ROOT).equals(wanted))) {
                        isle.compass().guide(player, district);
                        return true;
                    }
                }
                for (MineRole role : MineRole.values()) {
                    if (role.id().equals(wanted) || role.display().toLowerCase(Locale.ROOT).contains(wanted)) {
                        isle.compass().guide(player, role);
                        return true;
                    }
                }
                player.sendMessage("§7You don't know a place called §f" + wanted + "§7 yet.");
            }
            case "where" -> {
                if (admin(player)) {
                    player.sendMessage(isle.dev().action(player, "where"));
                }
            }
            case "dev" -> {
                if (!admin(player)) {
                    player.sendMessage("§cDEV only.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§7/mineisle dev <group:verb[:arg]> §8(e.g. event:rich, find:diamond:perfect, critter:stonejaw)");
                    return true;
                }
                String reply = isle.dev().action(player, args[1]);
                if (reply != null && !reply.isBlank()) {
                    for (String line : reply.split("\n")) {
                        player.sendMessage(line);
                    }
                }
            }
            default -> isle.menus().openJournal(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("tour", "go", "stop", "map")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
            if (admin(sender)) {
                for (String option : List.of("reload", "where", "dev")) {
                    if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                        out.add(option);
                    }
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("go") && sender instanceof Player player) {
            MineProfiles.Profile profile = isle.profiles().of(player);
            for (MineDistricts.District district : isle.districts().all()) {
                if ((profile.districts().contains(district.id()) || admin(sender)) && district.id().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(district.id());
                }
            }
            for (MineRole role : MineRole.values()) {
                if (role.id().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(role.id());
                }
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("dev") && admin(sender)) {
            for (String option : List.of("event:rich", "event:ember", "event:tremor", "event:troll", "event:stop", "rhythm:max",
                    "find:diamond:perfect", "find:amethyst:heartstone", "critter:stonejaw", "critter:cinder_mite",
                    "critter:gloam_moth", "critter:shardling", "hazard:cavein", "forge:rep:1000", "forge:max", "forge:reset",
                    "forge:tool:flare", "forge:burst", "mastery:4", "cabinet:fill", "contracts:reset", "contracts:fill",
                    "districts:all", "depth:all", "streak:1", "npc:preset-all", "props:rebuild", "prop:records", "prop:deep_forge",
                    "landing:set", "give:rations", "give:tools", "give:specimens", "profile:reset", "config:reload", "where")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
        }
        return out;
    }
}
