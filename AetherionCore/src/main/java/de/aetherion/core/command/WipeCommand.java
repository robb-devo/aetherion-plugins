package de.aetherion.core.command;

import de.aetherion.core.wipe.BetaWipe;
import de.aetherion.core.wipe.NetworkWipeWatch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class WipeCommand implements CommandExecutor, TabCompleter {

    private static final int COUNTDOWN = 5;

    private final JavaPlugin plugin;
    private final BetaWipe wipe;
    private final NetworkWipeWatch networkWatch;
    private boolean running;

    public WipeCommand(JavaPlugin plugin, BetaWipe wipe, NetworkWipeWatch networkWatch) {
        this.plugin = plugin;
        this.wipe = wipe;
        this.networkWatch = networkWatch;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aetherion.wipe")) {
            sender.sendMessage("§cYou cannot use this command.");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("beta")) {
            sender.sendMessage("§7Usage: §f/" + label + " beta confirm");
            sender.sendMessage("§8Queues a full player wipe. All backends stop; start them again from the panel.");
            return true;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage("§cThis wipes inventories, skills, coins, shards, pets, quests,");
            sender.sendMessage("§cstorage, islands and vanilla player files on §fALL backends§c (mmo-r + mmo-d).");
            sender.sendMessage("§cBoth backends shut down. Worlds/NPCs stay. Type §f/" + label + " beta confirm§c.");
            return true;
        }

        if (running) {
            sender.sendMessage("§cA beta wipe is already in progress.");
            return true;
        }

        running = true;
        if (networkWatch != null) {
            networkWatch.markLocalShutdown();
        }
        wipe.markPending();
        plugin.getLogger().warning(sender.getName() + " queued a beta wipe (flags written to all backends).");
        sender.sendMessage("§aWipe queued. mmo-r and mmo-d will both stop — start them again from the panel.");
        Bukkit.broadcast(Component.text("BETA WIPE")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(" — all backends stopping. Data resets on next boot.", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, false)));

        new BukkitRunnable() {
            int left = COUNTDOWN;

            @Override
            public void run() {
                if (left > 0) {
                    Bukkit.broadcast(Component.text(
                            "Stopping in " + left + "… other backends stop via wipe flag too.",
                            NamedTextColor.RED
                    ));
                    left--;
                    return;
                }
                cancel();
                stopForWipe();
            }
        }.runTaskTimer(plugin, 20L, 20L);
        return true;
    }

    private void stopForWipe() {
        Component reason = Component.text("Beta wipe queued — start the server again from the panel.", NamedTextColor.RED);
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.kick(reason);
        }
        plugin.getLogger().warning("Stopping for beta wipe. Player data is cleared on the next boot. RAM is unchanged.");
        Bukkit.shutdown();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aetherion.wipe")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("beta"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("beta")) {
            return filter(List.of("confirm"), args[1]);
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
