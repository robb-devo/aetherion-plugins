package de.aetherion.helm.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Aetherion Helm agent for a Paper backend: streams stats, players, console and activity to the gateway and
 * answers its requests. Reads only; the one thing it adds for players is /report.
 */
public final class HelmAgent extends JavaPlugin {
    private Link link;
    private LogCapture logs;
    private ModState mod;
    private Ops ops;
    private String server;
    private List<String> muteBlocked;
    private final ExecutorService worker = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "helm-ops");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, Long> reportCooldown = new HashMap<>();
    private volatile boolean playersDirty;
    private int tick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        server = getConfig().getString("server", "");
        String token = getConfig().getString("token", "");
        muteBlocked = getConfig().getStringList("mute-blocked-commands").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        mod = new ModState(getDataFolder().toPath().resolve("moderation.json"));
        ops = new Ops(this);
        getServer().getPluginManager().registerEvents(new Activity(this), this);
        if (server.isEmpty() || token.isEmpty()) {
            getLogger().warning("[Helm] server/token missing in config.yml: running offline (bans and mutes from disk still apply)");
            return;
        }
        logs = new LogCapture();
        logs.attach();
        link = new Link(getConfig().getString("gateway", "ws://127.0.0.1:8788/agent"), this::hello, this::onMessage, this::onConnected, getLogger());
        link.start();
        // stats every 2 s, players every 2 s (sooner after joins), info every minute
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (link == null || !link.connected()) return;
            tick++;
            JsonObject stats = Snapshot.stats();
            JsonArray players = Snapshot.players();
            JsonObject info = tick % 30 == 1 ? Snapshot.info() : null;
            async(() -> {
                link.send(msg("stats", "data", stats));
                link.send(msg("players", "data", players));
                if (info != null) link.send(msg("info", "data", info));
            });
        }, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (playersDirty && link != null && link.connected()) {
                playersDirty = false;
                JsonArray players = Snapshot.players();
                async(() -> link.send(msg("players", "data", players)));
            }
        }, 10L, 10L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (link == null || !link.connected()) return;
            JsonArray lines = logs.drain();
            if (!lines.isEmpty()) {
                JsonObject o = new JsonObject();
                o.addProperty("t", "log");
                o.add("lines", lines);
                link.send(o);
            }
        }, 5L, 5L);
    }

    @Override
    public void onDisable() {
        if (logs != null) logs.detach();
        if (link != null) link.close();
        worker.shutdownNow();
    }

    private JsonObject hello() {
        JsonObject o = new JsonObject();
        o.addProperty("t", "hello");
        o.addProperty("server", server);
        o.addProperty("kind", "paper");
        o.addProperty("token", getConfig().getString("token", ""));
        o.addProperty("version", getPluginMeta().getVersion());
        o.addProperty("pid", ProcessHandle.current().pid());
        return o;
    }

    private void onConnected() {
        JsonObject o = new JsonObject();
        o.addProperty("t", "log");
        o.add("lines", logs.backlog());
        link.send(o);
        Bukkit.getScheduler().runTask(this, () -> {
            JsonObject info = Snapshot.info();
            JsonObject stats = Snapshot.stats();
            JsonArray players = Snapshot.players();
            async(() -> {
                link.send(msg("info", "data", info));
                link.send(msg("stats", "data", stats));
                link.send(msg("players", "data", players));
            });
        });
    }

    private void onMessage(JsonObject m) {
        String t = m.has("t") ? m.get("t").getAsString() : "";
        switch (t) {
            case "req" -> ops.handle(m);
            case "mod" -> {
                mod.apply(m, true);
                // anyone banned while online leaves now
                Bukkit.getScheduler().runTask(this, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        ModState.Entry b = mod.ban(p.getUniqueId());
                        if (b != null) p.kick(Activity.banScreen(b));
                    }
                });
            }
            default -> { }
        }
    }

    private static JsonObject msg(String t, String key, com.google.gson.JsonElement data) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        o.add(key, data);
        return o;
    }

    void event(JsonObject e) {
        if (link == null) return;
        link.sendReliable(msg("event", "data", e));
    }

    void pushPlayersSoon() {
        playersDirty = true;
    }

    void async(Runnable r) {
        worker.execute(r);
    }

    Link link() {
        return link;
    }

    ModState mod() {
        return mod;
    }

    List<String> muteBlocked() {
        return muteBlocked;
    }

    // ---------------------------------------------------------------- commands

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equals("helm")) {
            boolean up = link != null && link.connected();
            sender.sendMessage(Component.text("Aetherion Helm · " + server + " · ", NamedTextColor.GRAY)
                    .append(Component.text(up ? "verbunden" : "nicht verbunden", up ? NamedTextColor.GREEN : NamedTextColor.RED)));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur für Spieler.");
            return true;
        }
        if (args.length < 2) {
            p.sendMessage(Component.text("So geht's: ", NamedTextColor.GRAY).append(Component.text("/report <Spieler> <Grund>", NamedTextColor.WHITE)));
            return true;
        }
        long now = System.currentTimeMillis();
        long cd = getConfig().getLong("report-cooldown-seconds", 60) * 1000;
        Long last = reportCooldown.get(p.getUniqueId());
        if (last != null && now - last < cd) {
            p.sendMessage(Component.text("Bitte warte noch " + ((cd - (now - last)) / 1000 + 1) + " Sekunden bis zum nächsten Report.", NamedTextColor.RED));
            return true;
        }
        String target = args[0];
        if (target.equalsIgnoreCase(p.getName())) {
            p.sendMessage(Component.text("Dich selbst kannst du nicht melden.", NamedTextColor.RED));
            return true;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (reason.length() > 300) reason = reason.substring(0, 300);
        reportCooldown.put(p.getUniqueId(), now);
        JsonObject e = Activity.base("report", p);
        e.addProperty("target", target);
        Player tp = Bukkit.getPlayerExact(target);
        if (tp != null) {
            e.addProperty("target", tp.getName());
            e.addProperty("targetUuid", tp.getUniqueId().toString());
        }
        e.addProperty("text", reason);
        event(e);
        p.sendMessage(Component.text("✔ Danke! Dein Report gegen ", NamedTextColor.GREEN)
                .append(Component.text(tp != null ? tp.getName() : target, NamedTextColor.WHITE))
                .append(Component.text(" ist beim Team angekommen.", NamedTextColor.GREEN)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equals("report") || args.length != 1) return List.of();
        String pre = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pre)).sorted().toList();
    }
}
