package de.aetherion.core.command;

import de.aetherion.core.AetherionCore;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CharacterTransport;
import de.aetherion.core.network.CharacterHost;
import de.aetherion.core.network.CharacterLedger;
import de.aetherion.core.network.NetworkRouter;
import de.aetherion.core.network.RouteBook;
import de.aetherion.core.network.ServerNames;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /aetransfer [player]} — where this backend thinks a character is (admin, smoke tests).
 * {@code /aetransfer go <server>} — hop yourself with the live pipeline (admin; mmo-r/mmo-d/mmo-c/mmo-i).
 */
public final class TransferCommand implements CommandExecutor, TabCompleter {

    private final AetherionCore plugin;

    public TransferCommand(AetherionCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        NetworkRouter router = plugin.router();
        if (router == null) {
            sender.sendMessage("§cNetwork router offline.");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("go")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            String target = router.serverFor(args[1]);
            if (!ServerNames.isCharacterBackend(target)) {
                sender.sendMessage("§c" + target + " is not a character backend.");
                return true;
            }
            if (!router.travel(player, target, RouteBook.Kind.NONE, "", null, "admin-go")) {
                sender.sendMessage("§cNo transport here (or the hop was refused). See console.");
            }
            return true;
        }
        UUID id;
        String name;
        if (args.length >= 1) {
            Player online = Bukkit.getPlayerExact(args[0]);
            OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[0]);
            if (target == null) {
                sender.sendMessage("§cUnknown player " + args[0]);
                return true;
            }
            id = target.getUniqueId();
            name = target.getName() == null ? args[0] : target.getName();
        } else if (sender instanceof Player player) {
            id = player.getUniqueId();
            name = player.getName();
        } else {
            sender.sendMessage("Usage: /aetransfer <player> | /aetransfer go <server>");
            return true;
        }
        CharacterTransport transport = router.transport();
        CharacterHost host = plugin.characterHost();
        File dir = plugin.snapshots().directory();
        sender.sendMessage("§8[§5Transfer§8] §7here §f" + router.here()
                + " §8· §7transport §f" + (transport == null ? "none (v5 fallback)"
                : (transport == host ? "core v6" : "plugin v6 (" + transport.getClass().getSimpleName() + ")")));
        File ledger = new File(new File(dir, "ledger"), id + ".yml");
        if (ledger.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(ledger);
            String holder = yaml.getString("holder", "");
            sender.sendMessage("§7" + name + " §8· §7holder §f" + (holder.isBlank() ? "-" : holder)
                    + (holder.startsWith(CharacterLedger.SNAPSHOT_PREFIX) ? " §8(packed, in flight)" : "")
                    + " §8· §7last sent §f" + yaml.getString("last-sent-id", "-") + " → " + yaml.getString("last-sent-to", "-")
                    + " §8· §7last applied §f" + yaml.getString("last-applied-id", "-") + " on " + yaml.getString("last-applied-on", "-"));
        } else {
            sender.sendMessage("§7" + name + " §8· §7no ledger yet (never crossed with v6)");
        }
        boolean pending = new File(dir, id + ".char.yml").isFile() || new File(dir, id + ".char.claimed.yml").isFile();
        boolean legacy = new File(dir, id + ".yml").isFile() || new File(dir, id + ".claimed.yml").isFile();
        sender.sendMessage("§7pending character §f" + pending + " §8· §7legacy v5 file §f" + legacy);
        RouteBook.Route route = router.routes().peek(id);
        if (route != null) {
            long age = (System.currentTimeMillis() - route.createdAt()) / 1000L;
            sender.sendMessage("§7route §f" + route.kind() + " '" + route.value() + "' §7→ §f" + route.to()
                    + " §8(" + age + "s old" + (route.fresh(router.routes().ttlMs()) ? "" : ", expired") + ")");
        }
        if (transport != null) {
            sender.sendMessage("§7held §f" + transport.isHeld(id) + " §8· §7arriving §f" + transport.isArriving(id)
                    + (host != null && host.isGuest(id) ? " §c· guest session" : ""));
        }
        if (AetherServices.islands() != null) {
            sender.sendMessage("§7island home §f" + (AetherServices.islands().home(id) != null));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            if ("go".startsWith(prefix)) {
                out.add("go");
            }
            Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).forEach(out::add);
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("go")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return ServerNames.CHARACTER_BACKENDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
