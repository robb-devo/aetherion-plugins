package de.aetherion.items.command;

import de.aetherion.items.world.AshenVoidArena;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /ashenvoid} — teleport to the Ashen Void preview arena (End remap of Bloodstone).
 */
public final class AshenVoidCommand implements CommandExecutor, TabCompleter {

    private final AshenVoidArena arena;

    public AshenVoidCommand(AshenVoidArena arena) {
        this.arena = arena;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("set")) {
            if (!player.isOp() && !player.hasPermission("aetherion.devmenu")) {
                player.sendMessage("§cDEV / OP only.");
                return true;
            }
            if (!AshenVoidArena.WORLD.equalsIgnoreCase(player.getWorld().getName())) {
                player.sendMessage("§cStand in §d" + AshenVoidArena.WORLD + " §cfirst.");
                return true;
            }
            arena.setPlayerSpawn(player.getLocation());
            player.sendMessage("§5Ashen Void §7player spawn §asaved§7.");
            return true;
        }
        if (!arena.teleport(player)) {
            player.sendMessage("§cAshen Void world missing. Import §dashen_void§c first.");
            return true;
        }
        player.sendMessage("§5✦ §dAshen Void");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && (sender.isOp() || sender.hasPermission("aetherion.devmenu"))) {
            return List.of("set").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
