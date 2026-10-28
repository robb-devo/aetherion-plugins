package de.aetherion.mining.titan;

import de.aetherion.mining.AetherionMining;
import de.aetherion.mining.veins.VeinsWorld;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** {@code /jawcamp} for everyone (crosses to the mine server elsewhere), {@code /titan} for staff. */
public final class TitanCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());

    private final AetherionMining plugin;
    private final VeinsWorld veins;

    public TitanCommand(AetherionMining plugin, VeinsWorld veins) {
        this.plugin = plugin;
        this.veins = veins;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("jawcamp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            TitanWorld titan = plugin.getTitan();
            if (titan != null && titan.isTitan(player.getWorld())) {
                titan.coughUp(player, true);
                return true;
            }
            veins.enterCamp(player);
            return true;
        }
        TitanWorld titan = plugin.getTitan();
        if (titan == null) {
            sender.sendMessage("§7The Swallowed Isles run on the mine server only.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "churn" -> {
                titan.churn("§5✦ §fOssarath churns §8(staff)§7. You are coughed up into Jawcamp.");
                sender.sendMessage("§7Churn started. The Gullet reopens when the new body is generated.");
            }
            case "tp" -> {
                if (sender instanceof Player player) {
                    titan.swallow(player);
                }
            }
            case "camp" -> {
                if (sender instanceof Player player) {
                    Location spawn = titan.campSpawn();
                    if (spawn != null) {
                        player.teleport(spawn);
                    }
                }
            }
            default -> {
                TitanShape shape = titan.shape();
                sender.sendMessage("§5Swallowed Isles §7· generation §f" + titan.generation() + " §7· "
                        + (titan.open() ? "§aopen" : "§eclosed / generating"));
                sender.sendMessage("§7Next churn: §f" + (titan.nextChurnAt() > 0
                        ? CLOCK.format(Instant.ofEpochMilli(titan.nextChurnAt())) : "?"));
                if (shape != null) {
                    StringBuilder isles = new StringBuilder();
                    for (TitanShape.Isle isle : shape.isles) {
                        isles.append(" §f").append(isle.name()).append("§7@").append(isle.x()).append(',').append(isle.z());
                    }
                    sender.sendMessage("§7Heart §f" + (int) shape.heartX + " " + (int) shape.heartY + " " + (int) shape.heartZ
                            + " §7· isles" + isles);
                }
                sender.sendMessage("§8/titan churn|tp|camp");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("titan") && args.length == 1) {
            return List.of("status", "churn", "tp", "camp");
        }
        return List.of();
    }
}
