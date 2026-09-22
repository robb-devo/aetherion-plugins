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
 * {@code /amethyst} is also a first-time entry (mining gate) into the Amethyst Area.
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
            Map.entry("amethyst", "amethyst"),
            Map.entry("amethystmines", "amethyst")
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

        de.aetherion.core.api.DungeonAccess dungeons = de.aetherion.core.api.AetherServices.dungeons();
        if (dungeons != null && dungeons.needsMainWorld(player)) {
            dungeons.transferToMainSpawn(player, spawnId);
            return true;
        }

        if ("amethyst".equals(spawnId)) {
            return teleportAmethyst(player);
        }

        HubSpawn spawn = hub.spawn(spawnId);
        if (spawn == null) {
            player.sendMessage("§cThat teleport is not set up yet.");
            return true;
        }

        hub.teleport(player, spawn);
        return true;
    }

    /**
     * Player entry into the Amethyst Area: mining gate on first visit, then Hub spawn
     * planted by the Amethyst Mines spawn anchor (or {@code /hubadmin set amethyst}).
     */
    private boolean teleportAmethyst(Player player) {
        de.aetherion.core.api.MiningAccess mining = de.aetherion.core.api.AetherServices.mining();
        boolean unlocked = hub.isUnlocked(player, "amethyst");
        if (!unlocked) {
            if (mining != null && !mining.meetsVeinsMiningLevel(player)) {
                int need = mining.veinsMinMiningLevel();
                player.sendMessage("§cNeed Mining Skill " + need + " for Amethyst Mines.");
                player.sendMessage("§7Or talk to the Crystal Guide on Elder Vale when ready.");
                return true;
            }
        }
        if (mining != null) {
            mining.teleportToVeinsHub(player);
            return true;
        }
        HubSpawn spawn = hub.spawn("amethyst");
        if (spawn == null) {
            player.sendMessage("§cAmethyst Mines is not set up yet.");
            return true;
        }
        if (!unlocked) {
            hub.unlock(player.getUniqueId(), "amethyst");
        }
        hub.teleport(player, spawn);
        return true;
    }
}
