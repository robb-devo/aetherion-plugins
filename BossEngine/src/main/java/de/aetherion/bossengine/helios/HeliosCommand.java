package de.aetherion.bossengine.helios;

import de.aetherion.bossengine.helios.core.Lang;
import de.aetherion.bossengine.helios.encounter.ActScript;
import de.aetherion.bossengine.helios.encounter.HeliosEncounter;
import de.aetherion.bossengine.helios.world.ArenaSlots;
import de.aetherion.bossengine.util.TextUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * <pre>
 * /helios enter                 start an instance for you (and your party, if you lead one)
 * /helios leave                 leave your instance (counts as giving up)
 *
 * admin (helios.admin):
 * /helios start &lt;player...&gt;     start an instance for exactly these players
 * /helios list                  slots and running instances
 * /helios debug [watch]         load report; "watch" toggles a live action bar
 * /helios skip                  Herald → Act II (plays his death)
 * /helios hp &lt;percent&gt;          set the current boss's HP (phase testing)
 * /helios attack &lt;id&gt;           force a move ("/helios attack" lists them)
 * /helios abort [slot|all]      end instances now, everyone home, slot cleared
 * /helios restore &lt;slot&gt;        clear a slot box (repair after manual edits)
 * /helios tp &lt;slot&gt;             look at a slot
 * /helios reload                re-read helios.yml (new instances use it)
 * </pre>
 */
public final class HeliosCommand implements TabExecutor {

    private static final String ADMIN = "helios.admin";

    private final HeliosModule module;
    private final Map<UUID, BukkitTask> watchers = new HashMap<>();

    public HeliosCommand(HeliosModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "enter" -> enter(sender);
            case "leave" -> leave(sender);
            case "start" -> admin(sender, () -> start(sender, args));
            case "list" -> admin(sender, () -> list(sender));
            case "debug" -> admin(sender, () -> debug(sender, args));
            case "skip" -> admin(sender, () -> skip(sender, args));
            case "hp" -> admin(sender, () -> hp(sender, args));
            case "attack" -> admin(sender, () -> attack(sender, args));
            case "abort" -> admin(sender, () -> abort(sender, args));
            case "restore" -> admin(sender, () -> restore(sender, args));
            case "tp" -> admin(sender, () -> tp(sender, args));
            case "reload" -> admin(sender, () -> {
                module.reload();
                msg(sender, "&aHelios config reloaded (new instances use it).");
            });
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        msg(s, "&6✦ Helios Requiem &8— &7/helios enter | leave");
        if (s.hasPermission(ADMIN)) {
            msg(s, "&8admin: &7start, list, debug [watch], skip, hp <pct>, attack <id>, abort [slot|all], restore <slot>, tp <slot>, reload");
        }
    }

    private void admin(CommandSender s, Runnable r) {
        if (!s.hasPermission(ADMIN)) {
            msg(s, "&cNo permission.");
            return;
        }
        r.run();
    }

    /* ------------------------------------------------------------------ players */

    private void enter(CommandSender s) {
        if (!(s instanceof Player p)) {
            msg(s, "Players only.");
            return;
        }
        String result = module.enter(p);
        String text = switch (result.split(":")[0]) {
            case "ok" -> null;
            case "disabled" -> Lang.pick(p, "&cHelios ist gerade nicht verfügbar.", "&cHelios is not available right now.");
            case "permission" -> Lang.pick(p, "&cDu kannst den sterbenden Stern noch nicht betreten.", "&cYou cannot enter the dying star yet.");
            case "already" -> Lang.pick(p, "&cDu bist bereits im Kampf.", "&cYou are already in the fight.");
            case "not-leader" -> Lang.pick(p, "&cNur die Gruppenleitung kann den Kampf beginnen.", "&cOnly the party leader can start the fight.");
            case "min-players" -> Lang.pick(p, "&cIhr seid zu wenige.", "&cNot enough players.");
            case "full" -> Lang.pick(p, "&cAlle Sternenbühnen sind belegt. Versuch es gleich noch einmal.", "&cEvery star stage is in use. Try again shortly.");
            case "cooldown" -> Lang.pick(p, "&cDer Stern ruht noch &f" + result.split(":")[1] + "s&c.", "&cThe star rests for another &f" + result.split(":")[1] + "s&c.");
            default -> "&c" + result;
        };
        if (text != null) {
            msg(p, text);
        }
    }

    private void leave(CommandSender s) {
        if (!(s instanceof Player p)) {
            return;
        }
        HeliosEncounter e = module.encounterOf(p);
        if (e == null) {
            msg(p, Lang.pick(p, "&7Du bist in keinem Kampf.", "&7You are not in a fight."));
            return;
        }
        e.leave(p, true);
    }

    /* ------------------------------------------------------------------ admin */

    private void start(CommandSender s, String[] args) {
        List<Player> group = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Player p = Bukkit.getPlayerExact(args[i]);
            if (p != null && module.encounterOf(p) == null) {
                group.add(p);
            }
        }
        if (group.isEmpty() && s instanceof Player p) {
            group.add(p);
        }
        if (group.isEmpty()) {
            msg(s, "&cNo eligible players.");
            return;
        }
        var opened = HeliosEncounter.open(module, group);
        if (opened.isEmpty()) {
            msg(s, "&cNo free slot.");
            return;
        }
        module.encounters().add(opened.get());
        msg(s, "&aSlot " + opened.get().slot() + " opened for " + group.size() + " player(s).");
    }

    private void list(CommandSender s) {
        msg(s, "&6Helios slots:");
        for (ArenaSlots.Slot slot : module.slots().all()) {
            msg(s, " &7#" + slot.index() + " &f" + slot.state() + " &8(" + slot.players().size() + " players)");
        }
        for (HeliosEncounter e : module.encounters()) {
            msg(s, " &e» " + e.debug().split("\n")[0]);
        }
        msg(s, "&8builder queue: " + module.builder().queued());
    }

    private HeliosEncounter target(CommandSender s, String[] args, int idx) {
        if (args.length > idx) {
            try {
                int slot = Integer.parseInt(args[idx]);
                for (HeliosEncounter e : module.encounters()) {
                    if (e.slot() == slot) {
                        return e;
                    }
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (s instanceof Player p) {
            HeliosEncounter mine = module.encounterOf(p);
            if (mine != null) {
                return mine;
            }
            HeliosEncounter here = module.encounterAt(p.getLocation());
            if (here != null) {
                return here;
            }
        }
        return module.encounters().isEmpty() ? null : module.encounters().get(0);
    }

    private void debug(CommandSender s, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("watch") && s instanceof Player p) {
            BukkitTask old = watchers.remove(p.getUniqueId());
            if (old != null) {
                old.cancel();
                msg(p, "&7Debug watch off.");
                return;
            }
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(module.plugin(), () -> {
                HeliosEncounter e = target(p, new String[0], 5);
                if (!p.isOnline()) {
                    BukkitTask t = watchers.remove(p.getUniqueId());
                    if (t != null) {
                        t.cancel();
                    }
                    return;
                }
                String line = e == null ? "&8no instance" : e.debug().split("\n").length > 1 ? e.debug().split("\n")[1] : e.debug();
                p.sendActionBar(TextUtil.component("&7" + line + " &8| mspt " + String.format(Locale.ROOT, "%.1f", Bukkit.getAverageTickTime())));
            }, 1L, 10L);
            watchers.put(p.getUniqueId(), task);
            msg(p, "&7Debug watch on.");
            return;
        }
        HeliosEncounter e = target(s, args, 1);
        if (e == null) {
            msg(s, "&7No running instance.");
            return;
        }
        for (String line : e.debug().split("\n")) {
            msg(s, line);
        }
        msg(s, String.format(Locale.ROOT, "&8server mspt %.1f | tps %.1f", Bukkit.getAverageTickTime(), Bukkit.getTPS()[0]));
    }

    private void skip(CommandSender s, String[] args) {
        HeliosEncounter e = target(s, args, 1);
        if (e == null || !e.skipToHelios()) {
            msg(s, "&cNothing to skip (only works during Act I's fight).");
            return;
        }
        msg(s, "&aThe Herald falls.");
    }

    private void hp(CommandSender s, String[] args) {
        HeliosEncounter e = target(s, args, 2);
        if (e == null || args.length < 2) {
            msg(s, "&c/helios hp <percent> [slot]");
            return;
        }
        try {
            e.adminHealth(Double.parseDouble(args[1]));
            msg(s, "&aHP set.");
        } catch (NumberFormatException ex) {
            msg(s, "&cNot a number.");
        }
    }

    private void attack(CommandSender s, String[] args) {
        HeliosEncounter e = target(s, args, 2);
        ActScript script = e == null ? null : e.currentScript();
        if (script == null) {
            msg(s, "&cNo running act.");
            return;
        }
        if (args.length < 2) {
            msg(s, "&7Moves: &f" + String.join(", ", script.attackIds()));
            return;
        }
        msg(s, script.forceAttack(args[1]) ? "&aForced " + args[1] + "." : "&cUnknown move or not fighting right now.");
    }

    private void abort(CommandSender s, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            for (HeliosEncounter e : new ArrayList<>(module.encounters())) {
                e.close(false, "admin");
            }
            msg(s, "&aAll instances closed.");
            return;
        }
        HeliosEncounter e = target(s, args, 1);
        if (e == null) {
            msg(s, "&7No running instance.");
            return;
        }
        e.close(false, "admin");
        msg(s, "&aSlot " + e.slot() + " closed.");
    }

    private void restore(CommandSender s, String[] args) {
        if (args.length < 2) {
            msg(s, "&c/helios restore <slot>");
            return;
        }
        try {
            int slot = Integer.parseInt(args[1]);
            for (HeliosEncounter e : module.encounters()) {
                if (e.slot() == slot) {
                    msg(s, "&cSlot is in use; /helios abort " + slot + " first.");
                    return;
                }
            }
            int cx = slot * module.config().slotSpacing();
            module.slots().set(slot, ArenaSlots.State.RESTORING);
            module.builder().clear(module.world(), cx, module.config().arenaY(), 0, () -> {
                module.slots().set(slot, ArenaSlots.State.FREE);
                msg(s, "&aSlot " + slot + " cleared.");
            });
        } catch (NumberFormatException ex) {
            msg(s, "&cNot a number.");
        }
    }

    private void tp(CommandSender s, String[] args) {
        if (!(s instanceof Player p) || args.length < 2 || module.world() == null) {
            msg(s, "&c/helios tp <slot>");
            return;
        }
        try {
            int slot = Integer.parseInt(args[1]);
            var at = new org.bukkit.Location(module.world(), slot * module.config().slotSpacing() + 0.5,
                    module.config().arenaY() + 20, 40.5, 180f, 25f);
            module.teleport(p, at);
        } catch (NumberFormatException ex) {
            msg(s, "&cNot a number.");
        }
    }

    private static void msg(CommandSender s, String text) {
        s.sendMessage(TextUtil.component(text));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("enter", "leave"));
            if (sender.hasPermission(ADMIN)) {
                subs.addAll(List.of("start", "list", "debug", "skip", "hp", "attack", "abort", "restore", "tp", "reload"));
            }
            for (String sub : subs) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("attack") && sender.hasPermission(ADMIN)) {
            HeliosEncounter e = target(sender, new String[0], 5);
            if (e != null && e.currentScript() != null) {
                for (String id : e.currentScript().attackIds()) {
                    if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(id);
                    }
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            out.add("watch");
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("start")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        }
        return out;
    }
}
