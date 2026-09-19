package de.aetherion.hub.command;

import de.aetherion.hub.AetherionHub;
import de.aetherion.hub.item.HomesteadMarker;
import de.aetherion.hub.model.HubSpawn;
import de.aetherion.hub.service.HubService;
import de.aetherion.hub.util.SoftLightPass;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class HubAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "set", "list", "unlock", "lock", "name", "icon", "slot", "default", "hint", "give",
            "softlight", "shabbymine", "reload", "reloadpads"
    );

    private final AetherionHub plugin;
    private final HubService hub;
    private final HomesteadMarker marker;

    public HubAdminCommand(AetherionHub plugin, HubService hub, HomesteadMarker marker) {
        this.plugin = plugin;
        this.hub = hub;
        this.marker = marker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aetherionhub.admin")) {
            sender.sendMessage("§cYou cannot use this command.");
            return true;
        }

        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> set(sender, args);
            case "list" -> list(sender);
            case "unlock" -> unlock(sender, args, true);
            case "lock" -> unlock(sender, args, false);
            case "name" -> name(sender, args);
            case "icon" -> icon(sender, args);
            case "slot" -> slot(sender, args);
            case "default" -> defaultUnlock(sender, args);
            case "hint" -> hint(sender, args);
            case "give" -> give(sender, args);
            case "softlight" -> softlight(sender, args);
            case "shabbymine" -> shabbymine(sender);
            case "reload" -> {
                hub.reload();
                if (plugin.getLaunchPads() != null) {
                    plugin.getLaunchPads().reload();
                }
                sender.sendMessage("§aAetherionHub reloaded.");
            }
            case "reloadpads" -> {
                if (plugin.getLaunchPads() != null) {
                    plugin.getLaunchPads().reload();
                    sender.sendMessage("§aIsland jump pads reloaded: §f" + plugin.getLaunchPads().padCount() + "§a.");
                } else {
                    sender.sendMessage("§cJump pads not loaded — restart the server (new Hub jar).");
                }
            }
            default -> help(sender);
        }

        return true;
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cStand at the spawn and run this in-game.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /hubadmin set <id>");
            return;
        }

        HubSpawn spawn = hub.setLocation(args[1], player.getLocation(), true);
        sender.sendMessage("§aSaved §f" + spawn.displayName() + " §7(" + spawn.id() + ") §aat your location.");
    }

    private void list(CommandSender sender) {
        sender.sendMessage("§6Aetherion spawns:");
        if (hub.spawns().isEmpty()) {
            sender.sendMessage("§7None yet. /hubadmin set spawn");
            return;
        }
        for (HubSpawn spawn : hub.spawns()) {
            sender.sendMessage("§8- §f" + spawn.id()
                    + " §7" + spawn.displayName()
                    + (spawn.unlockedByDefault() ? " §a(default)" : " §8(locked)")
                    + (spawn.hasLocation() ? " §a✔" : " §cno location"));
        }
    }

    private void unlock(CommandSender sender, String[] args, boolean unlock) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /hubadmin " + (unlock ? "unlock" : "lock") + " <player> <id>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return;
        }

        HubSpawn spawn = hub.spawn(args[2]);
        if (spawn == null) {
            sender.sendMessage("§cUnknown spawn §f" + args[2] + "§c.");
            return;
        }

        if (unlock) {
            hub.unlock(target.getUniqueId(), spawn.id());
            sender.sendMessage("§aUnlocked §f" + spawn.displayName() + " §afor §f" + target.getName() + "§a.");
            target.sendMessage("§aYou unlocked a new spawn: §f" + spawn.displayName() + "§a. Open /spawns to select it.");
        } else {
            hub.lock(target.getUniqueId(), spawn.id());
            sender.sendMessage("§eLocked §f" + spawn.displayName() + " §efor §f" + target.getName() + "§e.");
        }
    }

    private void name(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /hubadmin name <id> <display name>");
            return;
        }
        HubSpawn spawn = hub.spawn(args[1]);
        if (spawn == null) {
            sender.sendMessage("§cUnknown spawn.");
            return;
        }
        spawn.setDisplayName(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        hub.saveSpawns();
        sender.sendMessage("§aRenamed to §f" + spawn.displayName() + "§a.");
    }

    private void icon(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /hubadmin icon <id> <material>");
            return;
        }
        HubSpawn spawn = hub.spawn(args[1]);
        if (spawn == null) {
            sender.sendMessage("§cUnknown spawn.");
            return;
        }
        Material material;
        try {
            material = Material.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("§cUnknown material.");
            return;
        }
        spawn.setIcon(material);
        hub.saveSpawns();
        sender.sendMessage("§aIcon set to §f" + material.name() + "§a.");
    }

    private void slot(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /hubadmin slot <id> <0-26>");
            return;
        }
        HubSpawn spawn = hub.spawn(args[1]);
        if (spawn == null) {
            sender.sendMessage("§cUnknown spawn.");
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cSlot must be a number.");
            return;
        }
        if (slot < 0 || slot > 26) {
            sender.sendMessage("§cSlot must be 0-26.");
            return;
        }
        spawn.setSlot(slot);
        hub.saveSpawns();
        sender.sendMessage("§aSlot set to §f" + slot + "§a.");
    }

    private void defaultUnlock(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /hubadmin default <id> <true|false>");
            return;
        }
        HubSpawn spawn = hub.spawn(args[1]);
        if (spawn == null) {
            sender.sendMessage("§cUnknown spawn.");
            return;
        }
        boolean value = Boolean.parseBoolean(args[2]);
        spawn.setUnlockedByDefault(value);
        hub.saveSpawns();
        sender.sendMessage("§aDefault unlock for §f" + spawn.id() + "§a is now §f" + value + "§a.");
    }

    private void hint(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cUsage: /hubadmin hint <player>");
            return;
        }

        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return;
        }

        sendHint(target);
        sender.sendMessage("§aSent the starter hint to §f" + target.getName() + "§a.");
    }

    private void give(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cUsage: /hubadmin give <player>");
            return;
        }
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return;
        }

        String spawnId = args.length >= 3 ? args[2] : HomesteadMarker.DEFAULT_SPAWN;
        target.getInventory().addItem(marker.create(spawnId));
        sender.sendMessage("§aGave a Homestead Marker (" + spawnId + ") to §f" + target.getName() + "§a.");
    }

    public void sendHint(Player player) {
        String title = plugin.getConfig().getString(
                "starter-hint.title",
                "§6§lFIND EGON"
        );
        String subtitle = plugin.getConfig().getString(
                "starter-hint.subtitle",
                "§eGreen glow near spawn · talk to him"
        );
        String actionbar = plugin.getConfig().getString(
                "starter-hint.actionbar",
                "§aLook for the green aura — Egon kits rookies"
        );

        player.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(title == null ? "§6§lFIND EGON" : title),
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(subtitle == null ? "§eGreen glow near spawn" : subtitle),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(300),
                        java.time.Duration.ofSeconds(4),
                        java.time.Duration.ofMillis(700)
                )
        ));
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(actionbar == null ? "§aLook for the green aura — Egon" : actionbar));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.3f);

        // Optional chat flavor — players may ignore it.
        if (plugin.getConfig().getBoolean("starter-hint.also-chat", false)) {
            List<String> messages = plugin.getConfig().getStringList("starter-hint.messages");
            if (messages.isEmpty()) {
                player.sendMessage("§6§lHint: §eTalk to Egon and Quartermaster to start your journey");
            } else {
                for (String line : messages) {
                    player.sendMessage(line);
                }
            }
        }
    }

    /**
     * /hubadmin softlight [radius|mines] [minLight] [step]
     * Default: radius 450, place where light ≤ 7, grid every 5 blocks.
     */
    private void softlight(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cRun in-game from the hub (centers on you).");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("mines")) {
            shabbymine(sender);
            return;
        }
        int radius = 450;
        int minLight = 7;
        int step = 5;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cUsage: /hubadmin softlight [radius|mines] [minLight] [step]");
                return;
            }
        }
        if (args.length >= 3) {
            try {
                minLight = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cUsage: /hubadmin softlight [radius|mines] [minLight] [step]");
                return;
            }
        }
        if (args.length >= 4) {
            try {
                step = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cUsage: /hubadmin softlight [radius|mines] [minLight] [step]");
                return;
            }
        }
        SoftLightPass.run(
                plugin,
                sender,
                player.getWorld(),
                player.getLocation().getBlockX(),
                player.getLocation().getBlockZ(),
                radius,
                minLight,
                step,
                12
        );
    }

    private void shabbymine(CommandSender sender) {
        try {
            Class.forName("de.aetherion.items.world.ShabbyMinePrep")
                    .getMethod("run", CommandSender.class)
                    .invoke(null, sender);
        } catch (ReflectiveOperationException | NoClassDefFoundError e) {
            sender.sendMessage("§cShabby Mine prep needs AetherionItems loaded.");
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6AetherionHub admin");
        sender.sendMessage("§7/hubadmin set <id> §8- save your location as a spawn");
        sender.sendMessage("§7/hubadmin list");
        sender.sendMessage("§7/hubadmin unlock <player> <id>");
        sender.sendMessage("§7/hubadmin lock <player> <id>");
        sender.sendMessage("§7/hubadmin name <id> <name>");
        sender.sendMessage("§7/hubadmin icon <id> <material>");
        sender.sendMessage("§7/hubadmin slot <id> <0-26>");
        sender.sendMessage("§7/hubadmin default <id> true|false");
        sender.sendMessage("§7/hubadmin hint [player]");
        sender.sendMessage("§7/hubadmin give [player] [spawnId]");
        sender.sendMessage("§7/hubadmin softlight [radius|mines] …");
        sender.sendMessage("§7/hubadmin shabbymine §8- dense lights + sponge→ores in Shabby Mine");
        sender.sendMessage("§7/hubadmin reload");
        sender.sendMessage("§7/hubadmin reloadpads §8- reload island jump pads");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aetherionhub.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2 && List.of("set", "name", "icon", "slot", "default").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(hub.spawns().stream().map(HubSpawn::id).toList(), args[1]);
        }
        if (args.length == 2 && List.of("unlock", "lock", "hint", "give").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && List.of("unlock", "lock", "give").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(hub.spawns().stream().map(HubSpawn::id).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
