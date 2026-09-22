package de.aetherion.mining.veins;

import de.aetherion.mining.AetherionMining;
import de.aetherion.mining.island.EldervalePaste;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Admin / legacy tools for the Amethyst Area dig world ({@code aether_veins}).
 * Player entry is {@code /amethyst} or the Crystal Guide — not this command.
 */
public final class VeinsCommand implements CommandExecutor, TabCompleter {

    private final AetherionMining plugin;
    private final VeinsWorld veins;
    private final VeinsNpcs npcs;

    public VeinsCommand(AetherionMining plugin, VeinsWorld veins, VeinsNpcs npcs) {
        this.plugin = plugin;
        this.veins = veins;
        this.npcs = npcs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            redirectPlayers(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("leave")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (!player.hasPermission("aetherion.mines.admin")) {
                redirectPlayers(sender);
                return true;
            }
            veins.leave(player);
            return true;
        }
        if (sub.equals("enter")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (!player.hasPermission("aetherion.mines.admin")) {
                redirectPlayers(sender);
                return true;
            }
            veins.enter(player);
            sender.sendMessage("§7Admin enter — players use §f/amethyst §7or the Crystal Guide.");
            return true;
        }
        if (sub.equals("time")) {
            sender.sendMessage("§7Amethyst Area reset in §f" + untilReset() + "§7.");
            return true;
        }
        if (sub.equals("npc")) {
            if (!sender.hasPermission("aetherion.mines.admin") || !(sender instanceof Player player)) {
                sender.sendMessage("§cAdmin only.");
                return true;
            }
            player.getInventory().addItem(VeinsNpcs.anchor());
            player.sendMessage("§7Legacy Foreman lantern (admin). Players use the Crystal Guide.");
            return true;
        }
        if (sub.equals("reset")) {
            if (!sender.hasPermission("aetherion.mines.admin")) {
                sender.sendMessage("§cAdmin only.");
                return true;
            }
            veins.reset("The Amethyst Area closed. Stone is new again.");
            sender.sendMessage("§7Reset started.");
            return true;
        }
        if (sub.equals("eldervale")) {
            if (!sender.hasPermission("aetherion.mines.admin")) {
                sender.sendMessage("§cAdmin only.");
                return true;
            }
            String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "paste";
            if (action.equals("paste")) {
                EldervalePaste.paste(plugin, sender);
                return true;
            }
            sender.sendMessage("§7/deepmines eldervale paste");
            return true;
        }
        redirectPlayers(sender);
        return true;
    }

    private static void redirectPlayers(CommandSender sender) {
        sender.sendMessage("§eDeep Mines player entry is retired.");
        sender.sendMessage("§7Use §f/amethyst §7or talk to the §dCrystal Guide §7on Elder Vale.");
        if (sender.hasPermission("aetherion.mines.admin")) {
            sender.sendMessage("§8Admin: /deepmines enter|leave|time|npc|reset|eldervale");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            if (sender.hasPermission("aetherion.mines.admin")) {
                out.addAll(List.of("enter", "leave", "time", "npc", "reset", "eldervale"));
            } else {
                out.add("time");
            }
            return out;
        }
        if (args.length == 2
                && args[0].equalsIgnoreCase("eldervale")
                && sender.hasPermission("aetherion.mines.admin")) {
            return List.of("paste");
        }
        return List.of();
    }

    private String untilReset() {
        long left = Math.max(0L, veins.nextResetAt() - System.currentTimeMillis());
        long hours = TimeUnit.MILLISECONDS.toHours(left);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(left) % 60L;
        if (hours <= 0L && minutes <= 0L) {
            return "moments";
        }
        if (hours <= 0L) {
            return minutes + "m";
        }
        return hours + "h " + minutes + "m";
    }
}
