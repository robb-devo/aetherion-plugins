package de.aetherion.core.command;

import de.aetherion.core.playtime.PlaytimeFormat;
import de.aetherion.core.playtime.PlaytimeService;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /playtime} and {@code /fullplaytimereset}.
 */
public final class PlaytimeCommand implements CommandExecutor, TabCompleter {

    public static final String OTHERS = "aetherion.playtime.others";
    public static final String RESET_SELF = "aetherion.playtime.reset.self";
    public static final String RESET_OTHERS = "aetherion.playtime.reset.others";

    private final PlaytimeService playtime;

    public PlaytimeCommand(PlaytimeService playtime) {
        this.playtime = playtime;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (playtime == null) {
            sender.sendMessage("§cPlaytime storage is not available.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("fullplaytimereset")) {
            return reset(sender, label, args);
        }
        return show(sender, label, args);
    }

    private boolean show(CommandSender sender, String label, String[] args) {
        boolean german = german(sender);
        if (args.length > 1) {
            sender.sendMessage(usage(german, label));
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§7Usage: §f/" + label + " <player>");
                return true;
            }
            sendTotal(sender, german, player.getUniqueId(), null, true);
            return true;
        }
        UUID target = resolve(args[0]);
        if (target == null) {
            sender.sendMessage(german ? "§cSpieler nicht gefunden." : "§cUnknown player.");
            return true;
        }
        boolean self = sender instanceof Player player && player.getUniqueId().equals(target);
        if (!self && !sender.hasPermission(OTHERS)) {
            deny(sender, german);
            return true;
        }
        sendTotal(sender, german, target, args[0], self);
        return true;
    }

    private boolean reset(CommandSender sender, String label, String[] args) {
        boolean german = german(sender);
        if (args.length > 1) {
            sender.sendMessage(usage(german, label));
            return true;
        }
        UUID target;
        String token;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§7Usage: §f/" + label + " <player>");
                return true;
            }
            if (!canReset(sender, true)) {
                deny(sender, german);
                return true;
            }
            target = player.getUniqueId();
            token = player.getName();
        } else {
            target = resolve(args[0]);
            if (target == null) {
                sender.sendMessage(german ? "§cSpieler nicht gefunden." : "§cUnknown player.");
                return true;
            }
            boolean self = sender instanceof Player player && player.getUniqueId().equals(target);
            if (!canReset(sender, self)) {
                deny(sender, german);
                return true;
            }
            token = args[0];
        }
        long previous = playtime.reset(target);
        if (previous < 0L) {
            sender.sendMessage(german
                    ? "§cSpielzeit konnte nicht gespeichert werden."
                    : "§cCould not save playtime.");
            return true;
        }
        String formatted = PlaytimeFormat.format(previous, german);
        boolean self = sender instanceof Player player && player.getUniqueId().equals(target);
        if (self) {
            sender.sendMessage(german
                    ? "§aDeine Spielzeit wurde zurückgesetzt. §8(vorher §f" + formatted + "§8)"
                    : "§aYour playtime was reset. §8(was §f" + formatted + "§8)");
        } else {
            String name = display(target, token);
            sender.sendMessage(german
                    ? "§aSpielzeit von §f" + name + " §azurückgesetzt. §8(vorher §f" + formatted + "§8)"
                    : "§aReset playtime for §f" + name + "§a. §8(was §f" + formatted + "§8)");
            Player online = Bukkit.getPlayer(target);
            if (online != null) {
                boolean targetGerman = german(online);
                online.sendMessage(targetGerman
                        ? "§cDeine Spielzeit wurde zurückgesetzt."
                        : "§cYour playtime was reset.");
            }
        }
        return true;
    }

    private void sendTotal(CommandSender sender, boolean german, UUID target, String token, boolean self) {
        String formatted = PlaytimeFormat.format(playtime.seconds(target), german);
        if (self) {
            sender.sendMessage(german ? "§7Spielzeit: §f" + formatted : "§7Playtime: §f" + formatted);
            return;
        }
        String name = display(target, token);
        sender.sendMessage(german
                ? "§7Spielzeit von §f" + name + "§7: §f" + formatted
                : "§7Playtime of §f" + name + "§7: §f" + formatted);
    }

    private String display(UUID target, String token) {
        String known = playtime.name(target);
        if (known != null && !known.isBlank()) {
            return known;
        }
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            return online.getName();
        }
        return token == null ? target.toString() : token;
    }

    private UUID resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(token);
        if (exact != null) {
            return exact.getUniqueId();
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(token)) {
                return online.getUniqueId();
            }
        }
        UUID stored = playtime.findByName(token);
        if (stored != null) {
            return stored;
        }
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException ignored) {
            // Token is a player name. Fall through to the usercache.
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(token);
        return cached == null ? null : cached.getUniqueId();
    }

    private static boolean canReset(CommandSender sender, boolean self) {
        if (self && sender.hasPermission(RESET_SELF)) {
            return true;
        }
        return sender.hasPermission(RESET_OTHERS);
    }

    private static void deny(CommandSender sender, boolean german) {
        sender.sendMessage(german ? "§cDazu fehlt dir die Berechtigung." : "§cYou cannot use this command.");
    }

    private static String usage(boolean german, String label) {
        if (german) {
            return "§7Nutze §f/" + label + " §7oder §f/" + label + " <spieler>§7.";
        }
        return "§7Usage: §f/" + label + " [player]";
    }

    private static boolean german(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }
        Locale locale = player.locale();
        return locale != null && "de".equalsIgnoreCase(locale.getLanguage());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (playtime == null || args.length != 1) {
            return List.of();
        }
        boolean reset = command.getName().equalsIgnoreCase("fullplaytimereset");
        if (reset) {
            if (!sender.hasPermission(RESET_OTHERS)) {
                return List.of();
            }
        } else if (!sender.hasPermission(OTHERS)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>(playtime.knownNames());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!names.contains(player.getName())) {
                names.add(player.getName());
            }
        }
        names.removeIf(name -> name == null || !name.toLowerCase(Locale.ROOT).startsWith(prefix));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (names.size() > 40) {
            return List.copyOf(names.subList(0, 40));
        }
        return names;
    }
}
