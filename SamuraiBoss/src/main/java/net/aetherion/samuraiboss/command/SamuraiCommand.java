package net.aetherion.samuraiboss.command;

import net.aetherion.samuraiboss.SamuraiBossPlugin;
import net.aetherion.samuraiboss.arena.Arena;
import net.aetherion.samuraiboss.arena.BlockSnapshot;
import net.aetherion.samuraiboss.arena.BuildJob;
import net.aetherion.samuraiboss.arena.Job;
import net.aetherion.samuraiboss.arena.RestoreJob;
import net.aetherion.samuraiboss.arena.dojo.BuildPlan;
import net.aetherion.samuraiboss.audio.Sfx;
import net.aetherion.samuraiboss.boss.Fight;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /samuraiboss (Aliase: /kensei, /samurai)
 *
 * <pre>
 * build [name]            Dojo an deiner Position bauen (fragt nach Bestätigung)
 * build confirm [name]    … bestätigen
 * spawn [name]            Kampf starten (Teilnehmer = alle Spieler im Dojo)
 * stop [name]             Kampf abbrechen – das Dojo baut sich wieder auf
 * reset [name]            Dojo SOFORT exakt in den Ausgangszustand versetzen
 * remove [name] confirm   Dojo abreißen und Gelände wie vorher herstellen
 * list | info [name] | tp [name] | reload
 * debug phase 2|3|ult | ability &lt;id&gt; | hp &lt;prozent&gt; | kill
 * </pre>
 * Ohne Namen gilt das Dojo, in dem du stehst (oder das nächste).
 */
public final class SamuraiCommand implements TabExecutor {

    private final SamuraiBossPlugin plugin;
    private final Map<UUID, Long> buildConfirm = new HashMap<>();
    private final Map<UUID, Location> buildAt = new HashMap<>();

    private static final List<String> SUBS = List.of("build", "spawn", "stop", "reset", "remove", "list", "info", "tp", "reload", "debug", "help");
    private static final List<String> ABILITIES = List.of("issen", "kesagiri", "tsubame", "kazekiri", "utsusemi", "triple-issen",
            "star", "thousand-blades", "tornado", "shadow-clones", "blade-rain", "afterimage");

    public SamuraiCommand(SamuraiBossPlugin plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender s, String mm) {
        s.sendMessage(plugin.prefixed(mm));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("samuraiboss.admin")) {
            sender.sendMessage(plugin.msg("no-permission", "<red>No permission.</red>"));
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "build" -> build(sender, args);
                case "spawn", "start" -> spawn(sender, args);
                case "stop" -> stop(sender, args);
                case "reset" -> reset(sender, args);
                case "remove" -> remove(sender, args);
                case "list" -> list(sender);
                case "info" -> info(sender, args);
                case "tp" -> tp(sender, args);
                case "reload" -> {
                    plugin.reloadSettings();
                    send(sender, "<gray>Config reloaded. Running fights keep their current values.</gray>");
                }
                case "debug" -> debug(sender, args);
                default -> help(sender, label);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error on /" + label + " " + String.join(" ", args), ex);
            send(sender, "<red>Internal error — see console.</red>");
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        String c = "/" + label;
        s.sendMessage(plugin.msgPlain("<gradient:#ffd1dc:#ff6f91><b>Sakuragi Jinsai</b></gradient> <gray>– commands</gray>"));
        String[][] lines = {
                {"build [name]", "Build the dojo at your feet (courtyard center)"},
                {"spawn [name]", "Start the fight — every player in the dojo joins"},
                {"stop [name]", "Abort the fight; the dojo rebuilds"},
                {"reset [name]", "Restore the dojo instantly"},
                {"remove [name] confirm", "Tear down the dojo; restore original terrain"},
                {"list | info [name] | tp [name]", "Overview / info / teleport"},
                {"reload", "Reload config.yml"},
                {"debug phase 2|3|ult", "Force a phase / ultimate"},
                {"debug ability <id>", "Trigger an ability"},
                {"debug hp <percent> | debug kill", "Set HP / kill"},
        };
        for (String[] l : lines) {
            s.sendMessage(plugin.msgPlain(" <#ff8fab>" + c + " " + l[0] + "</#ff8fab> <dark_gray>–</dark_gray> <gray>" + l[1] + "</gray>"));
        }
    }

    // =================================================================== Dojo finden

    private Arena resolve(CommandSender s, String[] args, int idx) {
        if (args.length > idx && !args[idx].equalsIgnoreCase("confirm")) {
            Arena a = plugin.arenas().get(args[idx]);
            if (a == null) send(s, "<red>No dojo named '" + args[idx] + "'.</red>");
            return a;
        }
        if (s instanceof Player p) {
            Arena a = plugin.arenas().at(p.getLocation());
            if (a == null) a = plugin.arenas().nearest(p.getLocation());
            if (a == null) send(s, "<red>No dojo here. Build one with /samuraiboss build.</red>");
            return a;
        }
        if (plugin.arenas().all().size() == 1) return plugin.arenas().all().iterator().next();
        send(s, "<red>Please specify the dojo name.</red>");
        return null;
    }

    // =================================================================== build

    private void build(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            send(s, "<red>Players only (dojo builds at your position).</red>");
            return;
        }
        boolean confirm = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        String name = null;
        for (int i = 1; i < args.length; i++) if (!args[i].equalsIgnoreCase("confirm")) name = args[i].toLowerCase(Locale.ROOT);
        BuildPlan plan = plugin.arenas().plan();
        Location origin = p.getLocation().getBlock().getLocation();
        World w = origin.getWorld();
        int sx = plan.maxX - plan.minX + 1, sy = plan.maxY - plan.minY + 1, sz = plan.maxZ - plan.minZ + 1;
        if (origin.getBlockY() + plan.minY < w.getMinHeight() || origin.getBlockY() + plan.maxY > w.getMaxHeight() - 1) {
            send(s, "<red>Zu nah an der Welthöhen-Grenze. Benötigt: " + (-plan.minY) + " Blöcke darunter, " + plan.maxY + " darüber.</red>");
            return;
        }
        Arena overlap = plugin.arenas().overlapping(origin);
        if (overlap != null) {
            send(s, "<red>Would overlap dojo '" + overlap.id + "' überschneiden.</red>");
            return;
        }
        if (!confirm) {
            buildConfirm.put(p.getUniqueId(), System.currentTimeMillis());
            buildAt.put(p.getUniqueId(), origin);
            send(s, "<gray>Dojo <white>千年桜道場</white> wird <white>" + sx + "×" + sy + "×" + sz
                    + "</white> Blöcke groß. Du stehst in der Mitte des Hofes (Laufhöhe = deine Füße), die Haupthalle liegt im <white>Norden</white>, das Tor im <white>Süden</white>.</gray>");
            send(s, "<gray>Alles im Bereich über dem Boden wird ersetzt – das Gelände wird vorher gesichert und kann mit "
                    + "<white>/samuraiboss remove</white> zurückgeholt werden.</gray>");
            send(s, "<white>/samuraiboss build confirm" + (name != null ? " " + name : "") + "</white> <gray>zum Bauen (30 s).</gray>");
            return;
        }
        Long at = buildConfirm.remove(p.getUniqueId());
        Location loc = buildAt.remove(p.getUniqueId());
        if (at == null || System.currentTimeMillis() - at > 30_000 || loc == null) {
            send(s, "<red>Bitte zuerst /samuraiboss build ausführen.</red>");
            return;
        }
        if (name == null) {
            int n = 1;
            while (plugin.arenas().get("dojo" + (n == 1 ? "" : n)) != null) n++;
            name = "dojo" + (n == 1 ? "" : n);
        }
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            send(s, "<red>Name nur aus a-z, 0-9, _ und - (max. 32 Zeichen).</red>");
            return;
        }
        if (plugin.arenas().get(name) != null) {
            send(s, "<red>A dojo named '" + name + "' gibt es schon.</red>");
            return;
        }
        Arena a = plugin.arenas().create(name, loc);
        // Gelände sichern, damit "remove" alles wiederherstellen kann
        try {
            BlockSnapshot pre = BlockSnapshot.capture(w, a.minX(), a.minY(), a.minZ(), a.maxX(), a.maxY(), a.maxZ());
            pre.save(a.prebuildFile());
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Gelände nicht sichern", ex);
            plugin.arenas().forget(a);
            send(s, "<red>Gelände konnte nicht gesichert werden – Bau abgebrochen.</red>");
            return;
        }
        a.state = Arena.State.BUILDING;
        a.built = false;
        plugin.arenas().save(a);
        plugin.arenas().addTickets(a);
        final String id = name;
        Sfx sfx = new Sfx(plugin.settings());
        BuildJob job = new BuildJob(a, plugin.settings().buildAnimated, 20000, plugin.settings().buildPerTick, 22,
                l -> {
                    if (Rand.chance(0.2)) sfx.play(l, Sfx.Id.REBUILD, 0.6f, 0.8f + Rand.f() * 0.6f);
                    if (plugin.settings().particles && Rand.chance(0.3)) l.getWorld().spawnParticle(Particle.CHERRY_LEAVES, l, 2, 0.4, 0.3, 0.4, 0);
                },
                () -> {
                    a.built = true;
                    a.state = Arena.State.IDLE;
                    plugin.arenas().save(a);
                    plugin.arenas().removeTickets(a);
                    a.deco.ensure();
                    sfx.play(a.point("bell"), Sfx.Id.BELL, 1f, 1f);
                    if (p.isOnline()) send(p, "<white>Dojo '" + id + "' is ready.</white> <gray>Start with</gray> <white>/samuraiboss spawn</white><gray>.</gray>");
                });
        plugin.arenas().runJob(a, job);
        send(s, "<gray>Building dojo <white>" + name + "</white>" + (plugin.settings().buildAnimated ? " – watch it rise…" : "…") + "</gray>");
    }

    // =================================================================== Kampf

    private void spawn(CommandSender s, String[] args) {
        Arena a = resolve(s, args, 1);
        if (a == null) return;
        String err = plugin.fights().start(a);
        if (err != null) send(s, "<red>" + err + "</red>");
        else send(s, "<gray>Der Kampf in <white>" + a.id + "</white> beginnt.</gray>");
    }

    private void stop(CommandSender s, String[] args) {
        Arena a = resolve(s, args, 1);
        if (a == null) return;
        Fight f = plugin.fights().get(a);
        if (f == null) {
            send(s, "<gray>In '" + a.id + "' läuft kein Kampf.</gray>");
            return;
        }
        f.abort(false);
        send(s, "<gray>Fight aborted — the dojo is rebuilding.</gray>");
    }

    private void reset(CommandSender s, String[] args) {
        Arena a = resolve(s, args, 1);
        if (a == null) return;
        Fight f = plugin.fights().get(a);
        if (f != null) {
            f.abort(true);
            send(s, "<gray>Fight ended; dojo fully restored.</gray>");
            return;
        }
        if (plugin.arenas().job(a) != null) {
            plugin.arenas().job(a).finishNow();
            send(s, "<gray>Laufende Aufgabe sofort abgeschlossen.</gray>");
            return;
        }
        World w = a.world();
        if (w == null) {
            send(s, "<red>Welt nicht geladen.</red>");
            return;
        }
        // nach Bauplan neu setzen (repariert alles, was Spieler verändert haben)
        a.deco.despawnAll();
        BuildJob job = new BuildJob(a, false, 20000, 1, 20, null, () -> {
            a.state = Arena.State.IDLE;
            a.built = true;
            plugin.arenas().save(a);
            a.deco.showAll();
        });
        a.state = Arena.State.BUILDING;
        job.finishNow();
        send(s, "<gray>Dojo '" + a.id + "' reset to blueprint.</gray>");
    }

    private void remove(CommandSender s, String[] args) {
        Arena a = resolve(s, args, 1);
        if (a == null) return;
        boolean confirm = false;
        for (String x : args) if (x.equalsIgnoreCase("confirm")) confirm = true;
        if (!confirm) {
            send(s, "<gray>Dojo <white>" + a.id + "</white> will be torn down and the terrain restored. "
                    + "Bestätige mit <white>/samuraiboss remove " + a.id + " confirm</white>.</gray>");
            return;
        }
        Fight f = plugin.fights().get(a);
        if (f != null) f.abort(true);
        Job running = plugin.arenas().job(a);
        if (running != null) running.finishNow();
        World w = a.world();
        a.deco.despawnAll();
        a.deco.setEnabled(false);
        if (w == null || !a.prebuildFile().exists()) {
            plugin.arenas().forget(a);
            send(s, "<gray>Dojo forgotten (no terrain snapshot — blocks stay).</gray>");
            return;
        }
        try {
            BlockSnapshot pre = BlockSnapshot.load(a.prebuildFile());
            a.state = Arena.State.REMOVING;
            plugin.arenas().addTickets(a);
            plugin.arenas().runJob(a, new RestoreJob(w, pre, plugin.settings().restorePerTick, () -> {
                plugin.arenas().removeTickets(a);
                plugin.arenas().forget(a);
                send(s, "<gray>Dojo '" + a.id + "' removed — terrain restored.</gray>");
            }));
            send(s, "<gray>Tearing down dojo…</gray>");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "prebuild.snap von " + a.id + " nicht lesbar", ex);
            send(s, "<red>Gesichertes Gelände nicht lesbar – siehe Konsole.</red>");
        }
    }

    // =================================================================== Info

    private void list(CommandSender s) {
        if (plugin.arenas().all().isEmpty()) {
            send(s, "<gray>No dojo yet. Build with <white>/samuraiboss build</white>.</gray>");
            return;
        }
        for (Arena a : plugin.arenas().all()) {
            Fight f = plugin.fights().get(a);
            s.sendMessage(plugin.msgPlain(" <#ff8fab>" + a.id + "</#ff8fab> <dark_gray>·</dark_gray> <gray>" + a.worldName + " "
                    + a.ox + " " + a.oy + " " + a.oz + " · " + a.state + (f != null ? " · " + f.state() : "") + "</gray>"));
        }
    }

    private void info(CommandSender s, String[] args) {
        Arena a = resolve(s, args, 1);
        if (a == null) return;
        s.sendMessage(plugin.msgPlain("<#ff8fab><b>" + a.id + "</b></#ff8fab> <gray>" + a + "</gray>"));
        s.sendMessage(plugin.msgPlain("<gray>Gebaut: " + a.built + " · Deko-Entities: " + a.deco.liveCount() + "</gray>"));
        Job j = plugin.arenas().job(a);
        if (j != null) s.sendMessage(plugin.msgPlain("<gray>Aufgabe: " + j.describe() + "</gray>"));
        Fight f = plugin.fights().get(a);
        if (f != null) s.sendMessage(plugin.msgPlain("<gray>Kampf: " + f.describe() + "</gray>"));
    }

    private void tp(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        Arena a = resolve(s, args, 1);
        if (a == null) return;
        Location l = a.point("gate").add(0, 0.1, -3);
        l.setYaw(180f);
        l.setPitch(-5f);
        p.teleport(l);
    }

    // =================================================================== Debug

    private void debug(CommandSender s, String[] args) {
        if (args.length < 2) {
            send(s, "<gray>debug phase 2|3|ult · debug ability &lt;id&gt; · debug hp &lt;prozent&gt; · debug kill</gray>");
            return;
        }
        Arena a = resolve(s, args, 99);
        if (a == null) return;
        Fight f = plugin.fights().get(a);
        if (f == null) {
            send(s, "<red>Kein laufender Kampf.</red>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "phase" -> {
                String v = args.length > 2 ? args[2] : "2";
                int n = v.startsWith("ult") ? 4 : v.equals("3") ? 3 : 2;
                f.debugPhase(n);
                send(s, "<gray>Phasenwechsel ausgelöst.</gray>");
            }
            case "ability" -> {
                if (args.length < 3) {
                    send(s, "<gray>IDs: " + String.join(", ", ABILITIES) + "</gray>");
                    return;
                }
                boolean ok = f.debugAbility(args[2].toLowerCase(Locale.ROOT));
                send(s, ok ? "<gray>Fähigkeit gestartet.</gray>" : "<red>Nicht möglich (falscher Zustand, kein Ziel oder unbekannte ID).</red>");
            }
            case "hp" -> {
                double pct = args.length > 2 ? parse(args[2], 50) : 50;
                f.debugSetHp(pct / 100.0);
                send(s, "<gray>HP gesetzt (Phasengrenzen gelten weiterhin).</gray>");
            }
            case "kill" -> {
                f.debugKill();
                send(s, "<gray>Er fällt…</gray>");
            }
            default -> send(s, "<red>Unbekannt.</red>");
        }
    }

    private static double parse(String s, double def) {
        try {
            return Double.parseDouble(s.replace("%", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // =================================================================== Tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("samuraiboss.admin")) return out;
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "spawn", "stop", "reset", "remove", "info", "tp" -> {
                    for (Arena a : plugin.arenas().all()) if (a.id.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(a.id);
                }
                case "build" -> out.add("confirm");
                case "debug" -> out.addAll(List.of("phase", "ability", "hp", "kill"));
                default -> {
                }
            }
            return out;
        }
        if (args.length == 3) {
            if (sub.equals("debug") && args[1].equalsIgnoreCase("ability")) {
                for (String id : ABILITIES) if (id.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(id);
            } else if (sub.equals("debug") && args[1].equalsIgnoreCase("phase")) {
                out.addAll(List.of("2", "3", "ult"));
            } else if (sub.equals("remove")) {
                out.add("confirm");
            }
        }
        return out;
    }
}
