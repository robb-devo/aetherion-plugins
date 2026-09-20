package de.aetherion.hub.command;

import de.aetherion.hub.model.HubSpawn;
import de.aetherion.hub.service.HubService;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * Direct teleport shortcuts for unlocked spawns, e.g. {@code /harbour}, {@code /mines}.
 */
public final class SpawnGotoCommand implements CommandExecutor {

    /** Command label (lowercase) → spawn id. */
    public static final Map<String, String> COMMAND_TO_SPAWN = Map.ofEntries(
            Map.entry("harbour", "harbour"),
            Map.entry("oreridge", "ore_ridge"),
            Map.entry("ore_ridge", "ore_ridge"),
            Map.entry("mines", "mines"),
            Map.entry("capital", "capital"),
            Map.entry("forage", "forage_isle"),
            Map.entry("forageisle", "forage_isle"),
            Map.entry("farm", "farm"),
            Map.entry("farmisle", "farm_isle"),
            Map.entry("borderlands", "borderlands"),
            Map.entry("colosseum", "colosseum"),
            Map.entry("eldervale", "eldervale"),
            Map.entry("amethyst", "amethyst")
    );

    private final HubService hub;

    public SpawnGotoCommand(HubService hub) {
        this.hub = hub;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this.");
            return true;
        }

        String spawnId = COMMAND_TO_SPAWN.get(label.toLowerCase(Locale.ROOT));
        if (spawnId == null) {
            spawnId = COMMAND_TO_SPAWN.get(command.getName().toLowerCase(Locale.ROOT));
        }
        if (spawnId == null) {
            player.sendMessage("§cUnknown teleport.");
            return true;
        }

        HubSpawn spawn = hub.spawn(spawnId);
        if (spawn == null) {
            player.sendMessage("§cThat teleport is not set up yet.");
            return true;
        }

        hub.teleport(player, spawn);
        return true;
    }

    /** Shortest /command label for a spawn id, or null if none is registered. */
    public static String shortcutCommand(String spawnId) {
        if (spawnId == null || spawnId.isBlank()) {
            return null;
        }
        String want = spawnId.toLowerCase(Locale.ROOT);
        String best = null;
        for (Map.Entry<String, String> entry : COMMAND_TO_SPAWN.entrySet()) {
            if (!want.equals(entry.getValue())) {
                continue;
            }
            if (best == null || entry.getKey().length() < best.length()) {
                best = entry.getKey();
            }
        }
        return best;
    }
}
