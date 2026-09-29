package de.aetherion.items.command;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.world.BloodstoneArena;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /bloodstonearena} — teleport to the Hollow Sun arena player pad.
 * NPC warp later; this is the interim command.
 */
public final class BloodstoneArenaCommand implements CommandExecutor, TabCompleter {

    private final BloodstoneArena arena;

    public BloodstoneArenaCommand(BloodstoneArena arena) {
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
            if (!"bloodstone".equalsIgnoreCase(player.getWorld().getName())) {
                player.sendMessage("§cStand in §fbloodstone §cfirst.");
                return true;
            }
            arena.setPlayerSpawn(player.getLocation());
            player.sendMessage("§6Bloodstone arena player spawn §asaved§7.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("bossset")) {
            if (!player.isOp() && !player.hasPermission("aetherion.devmenu")) {
                player.sendMessage("§cDEV / OP only.");
                return true;
            }
            if (!"bloodstone".equalsIgnoreCase(player.getWorld().getName())) {
                player.sendMessage("§cStand in §fbloodstone §cfirst.");
                return true;
            }
            arena.setBossSpawn(player.getLocation());
            player.sendMessage("§6Hollow Sun boss spawn §asaved§7 (Items snapshot — keep BossEngine spawner in sync).");
            return true;
        }
        if (!arena.teleport(player)) {
            player.sendMessage("§cBloodstone arena world missing.");
            return true;
        }
        player.sendMessage("§6✦ §eBloodstone Arena");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && (sender.isOp() || sender.hasPermission("aetherion.devmenu"))) {
            return List.of("set", "bossset").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
