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
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (veins.isVeins(player.getWorld())) {
                veins.leave(player);
            } else {
                veins.enter(player);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("leave")) {
            if (sender instanceof Player player) {
                veins.leave(player);
            }
            return true;
        }
        if (sub.equals("time")) {
            sender.sendMessage("§7The Amethyst Mine (The Veins) resets in §f" + untilReset() + "§7.");
            return true;
        }
        if (sub.equals("npc")) {
            if (!sender.hasPermission("aetherion.mines.admin") || !(sender instanceof Player player)) {
                sender.sendMessage("§cAdmin only.");
                return true;
            }
            player.getInventory().addItem(VeinsNpcs.anchor());
            player.sendMessage("§7Place the lantern. Sneak-click to remove.");
            return true;
        }
        if (sub.equals("reset")) {
            if (!sender.hasPermission("aetherion.mines.admin")) {
                sender.sendMessage("§cAdmin only.");
                return true;
            }
            veins.reset("Amethyst Area closed. Dig zones restored from snapshot seed.");
            sender.sendMessage("§7Dig-zone reset started (hub untouched).");
            return true;
        }
        if (sub.equals("zones")) {
            if (!sender.hasPermission("aetherion.mines.admin")) {
                sender.sendMessage("§cAdmin only.");
                return true;
            }
            boolean force = args.length >= 2 && args[1].equalsIgnoreCase("force");
            veins.ensureLoaded();
            int started = veins.ensureDigZones(sender, force || !veins.isDigZonesReady());
            if (started == 0 && veins.isDigZonesReady() && !force) {
                sender.sendMessage("§7Dig volume already painted. Use §f/deepmines zones force §7to re-paint flush.");
            } else if (started > 0) {
                sender.sendMessage("§7Solid dig paint started (schematic footprint skipped — no air-gap clearance).");
            }
            return true;
        }
        if (sub.equals("softlight")) {
            if (!sender.hasPermission("aetherion.mines.admin")) {
                sender.sendMessage("§cAdmin only.");
                return true;
            }
            org.bukkit.World world = veins.ensureLoaded();
            org.bukkit.Location spawn = veins.hubSpawn();
            if (world == null || spawn == null) {
                sender.sendMessage("§cAmethyst Area world / spawn unavailable.");
                return true;
            }
            int radius = veins.digOuterRadius() + 16;
            if (args.length >= 2) {
                try {
                    radius = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                    sender.sendMessage("§cUsage: /deepmines softlight [radius]");
                    return true;
                }
            }
            veins.runSoftLight(sender, world, spawn.getBlockX(), spawn.getBlockZ(), radius);
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
        sender.sendMessage("§7/amethyst §8· §7/deepmines leave|time");
        if (sender.hasPermission("aetherion.mines.admin")) {
            sender.sendMessage("§8Admin: /deepmines npc|reset|zones|softlight|eldervale");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("leave", "time"));
            if (sender.hasPermission("aetherion.mines.admin")) {
                out.addAll(List.of("npc", "reset", "zones", "softlight", "eldervale"));
            }
            return out;
        }
        if (args.length == 2
                && args[0].equalsIgnoreCase("zones")
                && sender.hasPermission("aetherion.mines.admin")) {
            return List.of("force");
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
