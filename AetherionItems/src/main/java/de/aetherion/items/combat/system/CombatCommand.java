package de.aetherion.items.combat.system;

import de.aetherion.items.AetherionItems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /combat}: bindings for everyone; {@code dummy [clear]} and {@code reload} for aetherion.dev.
 * Registered on the command map at enable (no plugin.yml entry), removed again at disable.
 */
final class CombatCommand extends Command {

    private static final String DEV = "aetherion.dev";
    private static final String FALLBACK = "aetherionitems";
    private static CombatCommand registered;

    private CombatCommand() {
        super("combat", "Aetherion combat kit: bindings, training golem, reload", "/combat [dummy [clear] | reload]",
                List.of("kampf"));
    }

    static void register(AetherionItems plugin, CombatSystem system) {
        if (registered != null) {
            return;
        }
        CombatCommand command = new CombatCommand();
        plugin.getServer().getCommandMap().register(FALLBACK, command);
        registered = command;
        plugin.getServer().getOnlinePlayers().forEach(Player::updateCommands);
    }

    static void unregister(AetherionItems plugin) {
        CombatCommand command = registered;
        registered = null;
        if (command == null) {
            return;
        }
        CommandMap map = plugin.getServer().getCommandMap();
        command.unregister(map);
        Map<String, Command> known = map.getKnownCommands();
        known.values().removeIf(value -> value == command);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        CombatSystem system = CombatSystem.get();
        if (system == null) {
            sender.sendMessage(Component.text("Combat kit is not running.", NamedTextColor.RED));
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        Player player = sender instanceof Player p ? p : null;
        switch (sub) {
            case "dummy", "golem" -> {
                if (!dev(sender)) {
                    deny(sender);
                    return true;
                }
                if (player == null) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!system.config().enabled && !(args.length > 1 && args[1].equalsIgnoreCase("clear"))) {
                    player.sendMessage(Component.text(CombatSystem.t(player, "The kit is currently off.",
                            "Das Kampf-Kit ist gerade aus."), NamedTextColor.RED));
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
                    int removed = system.dummies().clear(player);
                    player.sendMessage(Component.text(CombatSystem.t(player, "Removed " + removed + " training golem(s).",
                            removed + " Übungsgolem(s) entfernt."), NamedTextColor.GRAY));
                    return true;
                }
                if (!system.dummies().spawn(player)) {
                    player.sendMessage(Component.text(CombatSystem.t(player,
                            "No room: face open, flat ground (3 blocks) and try again.",
                            "Kein Platz: schau auf freien, flachen Boden (3 Blöcke) und versuch es nochmal."), NamedTextColor.RED));
                    return true;
                }
                player.sendMessage(Component.text(CombatSystem.t(player,
                        "Training golem up. Yellow ! = parry (swap-hands key) on the hit. Red !! = roll (Sneak + Jump) through it. "
                                + "/combat dummy clear removes it.",
                        "Übungsgolem steht. Gelbes ! = beim Treffer parieren (Hand-tauschen-Taste). Rotes !! = durchrollen (Schleichen + Springen). "
                                + "/combat dummy clear entfernt ihn."), NamedTextColor.GOLD));
                return true;
            }
            case "reload" -> {
                if (!dev(sender)) {
                    deny(sender);
                    return true;
                }
                system.reload();
                sender.sendMessage(Component.text("combat-system.yml reloaded.", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                if (player != null) {
                    system.tutorial().show(player);
                } else {
                    sender.sendMessage("/combat dummy [clear] | reload");
                }
                if (!system.config().enabled) {
                    sender.sendMessage(Component.text(player == null ? "The kit is disabled in combat-system.yml."
                            : CombatSystem.t(player, "The kit is currently off.", "Das Kampf-Kit ist gerade aus."), NamedTextColor.RED));
                }
                return true;
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("help");
            if (dev(sender)) {
                out.add("dummy");
                out.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dummy") && dev(sender)) {
            out.add("clear");
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(option -> !option.startsWith(typed));
        return out;
    }

    private static boolean dev(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOp() || player.hasPermission(DEV);
    }

    private static void deny(CommandSender sender) {
        String text = sender instanceof Player player
                ? CombatSystem.t(player, "No permission.", "Keine Berechtigung.") : "No permission.";
        sender.sendMessage(Component.text(text, NamedTextColor.RED));
    }
}
