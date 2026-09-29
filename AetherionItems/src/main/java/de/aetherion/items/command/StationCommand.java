package de.aetherion.items.command;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.progress.ProgressionService;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Portable anvil (/av) and crafting table (/ct) after unlocking them in the world.
 */
public final class StationCommand implements CommandExecutor {

    public enum Station {
        ANVIL,
        WORKBENCH
    }

    private final Station station;

    public StationCommand(Station station) {
        this.station = station;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        AetherionItems plugin = AetherionItems.getInstance();
        ProgressionService progress = plugin == null ? null : plugin.progress();
        if (station == Station.ANVIL) {
            if (progress != null && !progress.anvil(player)) {
                player.sendMessage(progress.hint(ProgressionService.Flag.ANVIL));
                return true;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    de.aetherion.items.menu.BoosterSocketMenu.open(player));
            return true;
        }
        if (progress != null && !progress.craftingTable(player)) {
            player.sendMessage(progress.hint(ProgressionService.Flag.WORKBENCH));
            return true;
        }
        Bukkit.getScheduler().runTask(plugin, () -> player.openWorkbench(player.getLocation(), true));
        return true;
    }
}
