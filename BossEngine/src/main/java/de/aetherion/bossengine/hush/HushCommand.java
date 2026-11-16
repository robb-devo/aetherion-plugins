package de.aetherion.bossengine.hush;

import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /hush enter|leave} for players (entry needs the permission from hush.yml), and the test tools
 * behind {@code hush.admin}: start, abort, status, skip, solve, move, lamps, dread, reload.
 */
final class HushCommand implements CommandExecutor, TabCompleter {

    static final String ADMIN = "hush.admin";

    private final HushModule module;

    HushCommand(HushModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        Player player = sender instanceof Player p ? p : null;
        boolean admin = sender.hasPermission(ADMIN);
        switch (sub) {
            case "enter", "betreten" -> {
                if (player == null) {
                    sender.sendMessage("Players only. Use /hush start <player>.");
                    return true;
                }
                tell(player, module.enter(player));
            }
            case "leave", "verlassen" -> {
                HushEncounter e = player == null ? null : module.encounterOf(player);
                if (e != null) {
                    e.leave(player, true);
                    player.sendMessage(TextUtil.component(HushText.LEFT.of(player)));
                }
            }
            case "start" -> {
                if (!admin) {
                    return deny(sender);
                }
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : player;
                if (target == null) {
                    sender.sendMessage(TextUtil.component("&cPlayer not found."));
                    return true;
                }
                String result = module.enter(target, true);
                sender.sendMessage(TextUtil.component("&8[Hush] &7enter " + target.getName() + ": &f" + result));
                if (!"ok".equals(result) && !target.equals(sender)) {
                    tell(target, result);
                }
            }
            case "abort" -> {
                if (!admin) {
                    return deny(sender);
                }
                HushEncounter e = pick(sender, player, args);
                if (e != null) {
                    e.close(false, "admin");
                    sender.sendMessage(TextUtil.component("&8[Hush] &7Slot closed."));
                }
            }
            case "status" -> {
                if (!admin) {
                    return deny(sender);
                }
                sender.sendMessage(TextUtil.component("&8[Hush] &7world &f" + module.config().worldName()
                        + (module.world() == null ? " &8(not loaded)" : " &a(loaded)")
                        + " &7instances &f" + module.encounters().size() + "&7/&f" + module.config().maxInstances()));
                for (HushEncounter e : module.encounters()) {
                    sender.sendMessage(TextUtil.component("&8 - &7" + e.debug()));
                }
            }
            case "skip", "solve", "move", "lamps", "dread" -> {
                if (!admin) {
                    return deny(sender);
                }
                HushEncounter e = player == null ? null : module.encounterOf(player);
                if (e == null || e.script() == null) {
                    sender.sendMessage(TextUtil.component("&cYou are not in a running fight."));
                    return true;
                }
                test(sender, player, e, sub, args);
            }
            case "reload" -> {
                if (!admin) {
                    return deny(sender);
                }
                module.reload();
                sender.sendMessage(TextUtil.component("&8[Hush] &7hush.yml reloaded. Running fights keep their values."));
            }
            default -> {
                sender.sendMessage(TextUtil.component("&8[Hush] &7/hush enter &8· &7/hush leave"));
                if (admin) {
                    sender.sendMessage(TextUtil.component(
                            "&8[Hush] &7start [player] &8· &7abort [slot] &8· &7status &8· &7skip &8· &7solve &8· &7move <"
                                    + String.join("|", HushScript.moveIds()) + "> &8· &7lamps <on|off> &8· &7dread <0-100> &8· &7reload"));
                }
            }
        }
        return true;
    }

    private void test(CommandSender sender, Player player, HushEncounter e, String sub, String[] args) {
        HushScript script = e.script();
        switch (sub) {
            case "skip" -> {
                script.skip();
                sender.sendMessage(TextUtil.component("&8[Hush] &7Skipped ahead: " + script.debug()));
            }
            case "solve" -> sender.sendMessage(TextUtil.component(script.solve()
                    ? "&8[Hush] &7The song is held." : "&cNo lullaby is running."));
            case "move" -> {
                boolean ok = args.length >= 2 && script.start(args[1]);
                sender.sendMessage(TextUtil.component(ok ? "&8[Hush] &7Move started: " + args[1]
                        : "&cUnknown move, or it is not fighting right now. " + HushScript.moveIds()));
            }
            case "lamps" -> {
                boolean on = args.length < 2 || !"off".equalsIgnoreCase(args[1]);
                for (int i = 0; i < HushArena.LAMPS; i++) {
                    if (on) {
                        e.relight(i, null);
                    } else {
                        e.snuff(i, false);
                    }
                }
                sender.sendMessage(TextUtil.component("&8[Hush] &7Lamps " + (on ? "lit." : "out.")));
            }
            case "dread" -> {
                double value = 100;
                try {
                    value = args.length >= 2 ? Double.parseDouble(args[1]) : 100;
                } catch (NumberFormatException ignored) {
                    // keep 100
                }
                e.addDread(player, -100);
                e.addDread(player, value);
                sender.sendMessage(TextUtil.component("&8[Hush] &7Dread set to " + (int) value + "."));
            }
            default -> {
            }
        }
    }

    private HushEncounter pick(CommandSender sender, Player player, String[] args) {
        if (args.length >= 2) {
            try {
                int slot = Integer.parseInt(args[1]);
                for (HushEncounter e : module.encounters()) {
                    if (e.slot() == slot) {
                        return e;
                    }
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        HushEncounter mine = player == null ? null : module.encounterOf(player);
        if (mine == null && module.encounters().size() == 1) {
            mine = module.encounters().get(0);
        }
        if (mine == null) {
            sender.sendMessage(TextUtil.component("&cNo such slot. /hush status lists them."));
        }
        return mine;
    }

    private static boolean deny(CommandSender sender) {
        sender.sendMessage(TextUtil.component("&cNo permission."));
        return true;
    }

    /** Tells a player how their attempt to enter went. */
    static void tell(Player p, String result) {
        HushText.T line;
        if (result.startsWith("cooldown:")) {
            line = HushText.E_COOLDOWN.with(result.substring(9));
        } else if (result.startsWith("min-players:")) {
            line = HushText.E_MIN.with(result.substring(12));
        } else {
            line = switch (result) {
                case "ok" -> null;
                case "permission" -> HushText.E_PERMISSION;
                case "already" -> HushText.E_ALREADY;
                case "not-leader" -> HushText.E_LEADER;
                case "full" -> HushText.E_FULL;
                default -> HushText.E_DISABLED;
            };
        }
        if (line != null) {
            p.sendMessage(TextUtil.component(line.of(p)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("enter");
            out.add("leave");
            if (sender.hasPermission(ADMIN)) {
                out.addAll(List.of("start", "abort", "status", "skip", "solve", "move", "lamps", "dread", "reload"));
            }
        } else if (args.length == 2 && sender.hasPermission(ADMIN)) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "move" -> out.addAll(HushScript.moveIds());
                case "lamps" -> out.addAll(List.of("on", "off"));
                case "start" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                default -> {
                }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
