package de.aetherion.items.command;

import de.aetherion.items.economy.ShardService;
import de.aetherion.items.shop.ShardShopMenu;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ShardCommand implements CommandExecutor, TabCompleter {

    private final ShardService shards;
    private final ShardShopMenu shop;

    public ShardCommand(ShardService shards, ShardShopMenu shop) {
        this.shards = shards;
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("shardshop") || label.equalsIgnoreCase("gemshop")
                || label.equalsIgnoreCase("bitshop") || label.equalsIgnoreCase("aethershop")) {
            if (sender instanceof Player player) {
                shop.open(player);
            }
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                player.sendMessage("§bAether Crystals: §f" + shards.formatted(player));
                player.sendMessage("§7Shop: §f/shardshop §8· §7Liquidator NPC for buy/sell/liquidate");
            } else {
                sender.sendMessage("Usage: /shards give <player> <amount>");
            }
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("shop") || action.equals("open")) {
            if (sender instanceof Player player) {
                shop.open(player);
            }
            return true;
        }
        if (action.equals("give") || action.equals("take") || action.equals("set")) {
            if (!sender.hasPermission("aetherion.shards.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /shards " + action + " <player> <amount>");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getUniqueId() == null) {
                sender.sendMessage("§cUnknown player.");
                return true;
            }
            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cNot a number.");
                return true;
            }
            if (amount < 0L) {
                sender.sendMessage("§cAmount must be positive.");
                return true;
            }
            if (action.equals("give")) {
                shards.add(target.getUniqueId(), amount);
            } else if (action.equals("take")) {
                if (!shards.take(target.getUniqueId(), amount)) {
                    sender.sendMessage("§cNot enough shards.");
                    return true;
                }
            } else {
                long current = shards.get(target.getUniqueId());
                if (amount > current) {
                    shards.add(target.getUniqueId(), amount - current);
                } else if (amount < current) {
                    shards.take(target.getUniqueId(), current - amount);
                }
            }
            String name = target.getName() == null ? args[1] : target.getName();
            sender.sendMessage("§a" + name + " now has §b" + shards.get(target.getUniqueId()) + " §aAether Crystals.");
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage("§bAether Crystals: §f" + shards.formatted(target.getPlayer()));
            }
            return true;
        }
        if (sender instanceof Player player) {
            player.sendMessage("§bAether Crystals: §f" + shards.formatted(player));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Stream.of("shop", "give", "take", "set")
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && sender.hasPermission("aetherion.shards.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
