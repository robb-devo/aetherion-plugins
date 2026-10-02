package de.aetherion.dungeons.command;

import de.aetherion.dungeons.AetherionDungeons;
import de.aetherion.dungeons.bridge.RemoteServerBridge;
import de.aetherion.dungeons.instance.InstanceManager;
import de.aetherion.dungeons.npc.DungeonGuideService;
import de.aetherion.dungeons.npc.DungeonKeeperService;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private final InstanceManager instances;
    private final DungeonKeeperService keeper;
    private final DungeonGuideService guide;
    private final RemoteServerBridge remote;

    public DungeonCommand(InstanceManager instances, DungeonKeeperService keeper, RemoteServerBridge remote) {
        this(instances, keeper, null, remote);
    }

    public DungeonCommand(InstanceManager instances, DungeonKeeperService keeper,
                          DungeonGuideService guide, RemoteServerBridge remote) {
        this.instances = instances;
        this.keeper = keeper;
        this.guide = guide;
        this.remote = remote;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            usage(player);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "leave" -> {
                if (instances.sessionOf(player) == null && !instances.isDungeonWorld(player.getWorld())) {
                    if (AetherionDungeons.getInstance() != null
                            && AetherionDungeons.getInstance().sendToDungeonHub(player)) {
                        return true;
                    }
                    player.sendMessage("§cYou are not in a dungeon. Use §e/dhub§c for the dungeon hub.");
                    return true;
                }
                instances.leave(player, false);
                player.sendMessage("§7Tip: §e/dhub §7returns to the dungeon hub anytime.");
            }
            case "dhub", "hub" -> {
                if (AetherionDungeons.getInstance() == null
                        || !AetherionDungeons.getInstance().sendToDungeonHub(player)) {
                    player.sendMessage("§cCould not reach the dungeon hub.");
                }
            }
            case "give" -> {
                if (!player.hasPermission("aetherion.dungeon.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                player.getInventory().addItem(DungeonKeeperService.createAnchor());
                player.sendMessage("§aGave the Dungeon Keeper anchor. Right-click a block to place him.");
                player.sendMessage("§7Sneak + right-click despawns him so you can move him.");
            }
            case "guide" -> {
                if (!player.hasPermission("aetherion.dungeon.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                if (guide == null) {
                    player.sendMessage("§cGuide NPC service offline.");
                    return true;
                }
                player.getInventory().addItem(DungeonGuideService.createAnchor());
                player.sendMessage("§aGave the Dungeon Scribe anchor. Right-click a block to place them.");
            }
            case "remove" -> {
                if (!player.hasPermission("aetherion.dungeon.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                keeper.despawnAll();
                player.sendMessage("§eDespawned §f" + DungeonKeeperService.DISPLAY_NAME + "§e.");
            }
            case "enter" -> {
                if (!player.hasPermission("aetherion.dungeon.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                int floor = parseFloor(args);
                if (remote != null && remote.remoteDungeonsEnabled()) {
                    remote.transferToDungeon(player, floor, false);
                    return true;
                }
                instances.enterPrototype(player, false, floor);
            }
            case "boss" -> {
                if (!player.hasPermission("aetherion.dungeon.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                int floor = parseFloor(args);
                if (remote != null && remote.remoteDungeonsEnabled()) {
                    remote.transferToDungeon(player, floor, true);
                    return true;
                }
                instances.enterPrototype(player, true, floor);
            }
            case "transfer", "mmod" -> {
                if (!player.hasPermission("aetherion.dungeon.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                if (remote == null || !remote.transferToDungeon(player)) {
                    player.sendMessage("§cEnable remote-transfer in AetherionDungeons config.");
                }
            }
            case "home", "return" -> {
                if (remote == null) {
                    player.sendMessage("§cNo remote bridge.");
                    return true;
                }
                remote.transferHome(player);
            }
            case "portal" -> {
                if (!player.hasPermission("aetherion.dungeon.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                if (AetherionDungeons.getInstance() == null) {
                    return true;
                }
                var hub = AetherionDungeons.getInstance().getHubService();
                if (hub == null) {
                    player.sendMessage("§cHub service only runs on the dungeon backend (mmo-d).");
                    return true;
                }
                hub.ensureReturnPortal();
                player.sendMessage("§aReturn portal rebuilt at configured coords.");
            }
            case "cleanup" -> {
                if (!player.hasPermission("aetherion.dungeon.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                var hub = AetherionDungeons.getInstance() == null ? null : AetherionDungeons.getInstance().getHubService();
                if (hub == null) {
                    player.sendMessage("§cHub service only runs on the dungeon backend (mmo-d).");
                    return true;
                }
                hub.cleanupClutter();
                player.sendMessage("§aDungeon hub clutter cleaned.");
            }
            default -> usage(player);
        }
        return true;
    }

    private void usage(Player player) {
        player.sendMessage("§5/dungeon leave §7- leave your instance");
        player.sendMessage("§5/dhub §7- teleport to the dungeon hub (anywhere)");
        player.sendMessage("§5/dungeon home §7- Velocity return to capital (mmo-r)");
        if (player.hasPermission("aetherion.dungeon.admin")) {
            player.sendMessage("§5/dungeon give §7- Keeper anchor");
            player.sendMessage("§5/dungeon guide §7- Scribe anchor (briefing menu)");
            player.sendMessage("§5/dungeon remove §7- despawn the Dungeon Keeper");
            player.sendMessage("§5/dungeon enter [1|2|3|ice] §7- enter a floor without the NPC");
            player.sendMessage("§5/dungeon boss [1|2|3|ice] §7- skip to that floor's boss");
            player.sendMessage("§5/dungeon transfer §7- send self to mmo-d (when remote enabled)");
            player.sendMessage("§5/dungeon portal §7- rebuild return portal (mmo-d)");
            player.sendMessage("§5/dungeon cleanup §7- remove capital leftovers (mmo-d)");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            Stream<String> options = Stream.of("leave", "home", "return", "dhub", "hub");
            if (sender.hasPermission("aetherion.dungeon.admin")) {
                options = Stream.concat(options, Stream.of(
                        "give", "guide", "remove", "enter", "boss", "transfer", "mmod", "portal", "cleanup"));
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.filter(option -> option.startsWith(prefix)).toList();
        }
        if (args.length == 2 && sender.hasPermission("aetherion.dungeon.admin")) {
            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("enter") || action.equals("boss")) {
                String prefix = args[1];
                return Stream.of("1", "2", "3", "ice", "test", "5", "endless", "6")
                        .filter(option -> option.startsWith(prefix)).toList();
            }
        }
        return List.of();
    }

    private static int parseFloor(String[] args) {
        if (args.length < 2) {
            return 1;
        }
        String raw = args[1].toLowerCase(Locale.ROOT);
        if (raw.equals("endless") || raw.equals("xl") || raw.equals("6")) {
            return de.aetherion.dungeons.instance.EndlessSchemBuilder.ENTER_CODE;
        }
        if (raw.equals("ice") || raw.equals("test") || raw.equals("test2") || raw.equals("5")) {
            return 2;
        }
        if (raw.equals("lerfing") || raw.equals("4") || raw.equals("prison")) {
            return 1;
        }
        try {
            int floor = Integer.parseInt(raw);
            if (floor == de.aetherion.dungeons.instance.EndlessSchemBuilder.ENTER_CODE) {
                return floor;
            }
            return Math.max(1, Math.min(3, floor));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
