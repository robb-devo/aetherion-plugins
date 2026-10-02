package de.aetherion.stressbots;

import de.aetherion.stressbots.role.BotFocusService;
import de.aetherion.stressbots.role.BotRole;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Internal warp used by Mineflayer general bots. Players cannot use it.
 */
public final class BotFocusCommand implements CommandExecutor, TabCompleter {

    private final AetherionStressBots plugin;

    public BotFocusCommand(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (!plugin.getProvisioner().isStressBot(player) && !AetherionStressBots.canControl(sender)) {
            sender.sendMessage("§cBots only.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/botfocus <mine|forage|catch|roam|combat|fish|trade|quest|pad>");
            return true;
        }
        BotRole activity = BotFocusService.parseActivity(args[0]);
        if (activity == null) {
            sender.sendMessage("§cUnknown activity.");
            return true;
        }
        if (plugin.getFocus().setFocus(player, activity)) {
            return true;
        }
        sender.sendMessage("§cNo pad for " + activity.id() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String lower = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (BotRole role : BotRole.values()) {
            if (role.startable() && role != BotRole.GENERAL && role.id().startsWith(lower)) {
                out.add(role.id());
            }
        }
        return out;
    }
}
