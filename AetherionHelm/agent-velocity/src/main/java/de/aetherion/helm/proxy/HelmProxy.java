package de.aetherion.helm.proxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Helm agent for the Velocity proxy: the network-wide player list (who is where, ping, client), joins, quits and
 * server switches, ban enforcement at login, and the network-wide actions (kick, message, broadcast, send).
 */
@Plugin(id = "aetherionhelm", name = "AetherionHelm", version = "1.0.0", authors = {"Aetherion"},
        description = "Aetherion Helm agent for the proxy")
public final class HelmProxy {
    static final String VERSION = "1.0.0";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final ProxyServer proxy;
    private final Logger log;
    private final Path dir;
    private final ExecutorService worker = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "helm-proxy");
        t.setDaemon(true);
        return t;
    });
    /** Velocity server name to Helm server id, matched by port against the list the gateway sends. */
    private final Map<String, String> ids = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> pings = new ConcurrentHashMap<>();
    private final Set<UUID> joined = ConcurrentHashMap.newKeySet();
    private final long startedAt = System.currentTimeMillis();

    private Properties cfg;
    private Link link;
    private LogCapture logs;
    private ModState mod;
    private volatile boolean playersDirty = true;
    private int ticks;

    @Inject
    public HelmProxy(ProxyServer proxy, Logger log, @DataDirectory Path dir) {
        this.proxy = proxy;
        this.log = log;
        this.dir = dir;
    }

    // ---------------------------------------------------------------- lifecycle

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        cfg = loadConfig();
        mod = new ModState(dir.resolve("moderation.json"));
        try {
            logs = new LogCapture();
            logs.attach();
        } catch (Throwable t) {
            logs = null;
            log.warn("console mirror unavailable: {}", t.toString());
        }
        String token = cfg.getProperty("token", "").trim();
        String server = cfg.getProperty("server", "").trim();
        if (token.isEmpty() || server.isEmpty()) {
            log.warn("Helm is not configured yet: set token and server in {}", dir.resolve("config.properties"));
            return;
        }
        link = new Link(cfg.getProperty("gateway", "ws://127.0.0.1:8788/agent").trim(), () -> {
            JsonObject o = new JsonObject();
            o.addProperty("t", "hello");
            o.addProperty("token", token);
            o.addProperty("server", server);
            o.addProperty("kind", "velocity");
            o.addProperty("version", VERSION);
            o.addProperty("pid", ProcessHandle.current().pid());
            return o;
        }, this::onMessage, this::onConnected, log);
        link.start();

        proxy.getScheduler().buildTask(this, this::tick).delay(2, TimeUnit.SECONDS).repeat(2, TimeUnit.SECONDS).schedule();
        proxy.getScheduler().buildTask(this, this::flushLogs).repeat(250, TimeUnit.MILLISECONDS).schedule();
        proxy.getScheduler().buildTask(this, this::pingBackends).delay(1, TimeUnit.SECONDS).repeat(10, TimeUnit.SECONDS).schedule();
        proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("helm").plugin(this).build(), new StatusCommand());
    }

    @Subscribe(order = PostOrder.LAST)
    public void onShutdown(ProxyShutdownEvent e) {
        if (link != null) link.close();
        if (logs != null) logs.detach();
        worker.shutdownNow();
    }

    private Properties loadConfig() {
        Properties p = new Properties();
        Path f = dir.resolve("config.properties");
        try {
            Files.createDirectories(dir);
            if (!Files.exists(f)) {
                try (InputStream in = getClass().getResourceAsStream("/config.properties"); OutputStream out = Files.newOutputStream(f)) {
                    if (in != null) in.transferTo(out);
                }
            }
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            }
        } catch (Exception ex) {
            log.warn("could not read {}: {}", f, ex.toString());
        }
        return p;
    }

    // ---------------------------------------------------------------- gateway

    private void onConnected() {
        if (logs != null) link.send(msg("log", "lines", logs.backlog()));
        link.send(msg("info", "data", info()));
        link.send(msg("stats", "data", stats()));
        link.send(msg("players", "data", players()));
    }

    private void onMessage(JsonObject m) {
        String t = m.has("t") ? m.get("t").getAsString() : "";
        switch (t) {
            case "req" -> handle(m);
            case "mod" -> {
                mod.apply(m, true);
                for (Player p : proxy.getAllPlayers()) {
                    ModState.Entry b = mod.ban(p.getUniqueId());
                    if (b != null) p.disconnect(banScreen(b));
                }
            }
            case "servers" -> {
                ids.clear();
                Map<Integer, String> byPort = new ConcurrentHashMap<>();
                for (JsonElement el : m.getAsJsonArray("list")) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("port") && o.get("port").getAsInt() > 0) byPort.put(o.get("port").getAsInt(), o.get("id").getAsString());
                }
                for (RegisteredServer rs : proxy.getAllServers()) {
                    String id = byPort.get(rs.getServerInfo().getAddress().getPort());
                    if (id != null) ids.put(rs.getServerInfo().getName(), id);
                }
                playersDirty = true;
                link.send(msg("info", "data", info()));
            }
            default -> { }
        }
    }

    private void tick() {
        if (link == null || !link.connected()) return;
        link.send(msg("stats", "data", stats()));
        // ping and server changes need no event to show up, so the list is simply resent every few seconds
        if (playersDirty || ticks % 3 == 0) {
            playersDirty = false;
            link.send(msg("players", "data", players()));
        }
        if (++ticks % 30 == 0) link.send(msg("info", "data", info()));
    }

    private void flushLogs() {
        if (logs == null || link == null || !link.connected()) return;
        JsonArray lines = logs.drain();
        if (!lines.isEmpty()) link.send(msg("log", "lines", lines));
    }

    private void event(JsonObject e) {
        if (link != null) link.sendReliable(msg("event", "data", e));
    }

    private static JsonObject msg(String t, String key, JsonElement data) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        o.add(key, data);
        return o;
    }

    /** Helm id for a Velocity server name (falls back to the name itself). */
    private String id(String velocityName) {
        return ids.getOrDefault(velocityName, velocityName);
    }

    private RegisteredServer byId(String id) {
        for (RegisteredServer rs : proxy.getAllServers()) {
            if (id(rs.getServerInfo().getName()).equals(id) || rs.getServerInfo().getName().equalsIgnoreCase(id)) return rs;
        }
        return null;
    }

    // ---------------------------------------------------------------- snapshots

    private JsonObject stats() {
        JsonObject o = new JsonObject();
        o.addProperty("players", proxy.getPlayerCount());
        o.addProperty("maxPlayers", proxy.getConfiguration().getShowMaxPlayers());
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        o.addProperty("heapUsed", heap.getUsed());
        o.addProperty("heapCommitted", heap.getCommitted());
        o.addProperty("heapMax", heap.getMax());
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            o.addProperty("cpu", Math.round(Math.max(0, os.getProcessCpuLoad()) * 10000) / 10000.0);
        }
        o.addProperty("threads", ManagementFactory.getThreadMXBean().getThreadCount());
        o.addProperty("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        long gcCount = 0, gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcTime += Math.max(0, gc.getCollectionTime());
        }
        o.addProperty("gcCount", gcCount);
        o.addProperty("gcTime", gcTime);
        JsonArray backends = new JsonArray();
        for (RegisteredServer rs : proxy.getAllServers()) {
            String name = rs.getServerInfo().getName();
            JsonObject b = new JsonObject();
            b.addProperty("name", name);
            b.addProperty("id", id(name));
            b.addProperty("connected", rs.getPlayersConnected().size());
            JsonObject ping = pings.get(name);
            if (ping != null) b.asMap().putAll(ping.asMap());
            backends.add(b);
        }
        o.add("backends", backends);
        return o;
    }

    private JsonArray players() {
        JsonArray a = new JsonArray();
        for (Player p : proxy.getAllPlayers()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", p.getUniqueId().toString());
            o.addProperty("name", p.getUsername());
            p.getCurrentServer().ifPresent(s -> o.addProperty("server", id(s.getServerInfo().getName())));
            o.addProperty("ping", p.getPing());
            o.addProperty("client", p.getProtocolVersion().getMostRecentSupportedVersion());
            o.addProperty("protocol", p.getProtocolVersion().getProtocol());
            if (p.getClientBrand() != null) o.addProperty("brand", p.getClientBrand());
            InetSocketAddress addr = p.getRemoteAddress();
            if (addr != null && addr.getAddress() != null) o.addProperty("ip", addr.getAddress().getHostAddress());
            p.getVirtualHost().ifPresent(v -> o.addProperty("host", v.getHostString()));
            o.addProperty("locale", p.getEffectiveLocale() == null ? "" : p.getEffectiveLocale().toString());
            o.addProperty("onlineMode", p.isOnlineMode());
            p.getModInfo().ifPresent(mi -> o.addProperty("mods", mi.getMods().size()));
            a.add(o);
        }
        return a;
    }

    private JsonObject info() {
        JsonObject o = new JsonObject();
        o.addProperty("software", proxy.getVersion().getName() + " " + proxy.getVersion().getVersion());
        o.addProperty("java", System.getProperty("java.version"));
        o.addProperty("jvm", System.getProperty("java.vm.name"));
        o.addProperty("cpus", Runtime.getRuntime().availableProcessors());
        JsonArray args = new JsonArray();
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(s -> !s.startsWith("-Djavax.net.ssl") && !s.toLowerCase().contains("password")).forEach(args::add);
        o.add("jvmArgs", args);
        o.addProperty("onlineMode", proxy.getConfiguration().isOnlineMode());
        o.addProperty("motd", PlainTextComponentSerializer.plainText().serialize(proxy.getConfiguration().getMotd()));
        o.addProperty("startedAt", startedAt);
        o.addProperty("compressionThreshold", proxy.getConfiguration().getCompressionThreshold());
        JsonArray plugins = new JsonArray();
        for (PluginContainer pc : proxy.getPluginManager().getPlugins()) {
            JsonObject x = new JsonObject();
            x.addProperty("name", pc.getDescription().getName().orElse(pc.getDescription().getId()));
            x.addProperty("version", pc.getDescription().getVersion().orElse("?"));
            x.addProperty("enabled", true);
            plugins.add(x);
        }
        o.add("plugins", plugins);
        JsonArray backends = new JsonArray();
        for (RegisteredServer rs : proxy.getAllServers()) {
            JsonObject b = new JsonObject();
            b.addProperty("name", rs.getServerInfo().getName());
            b.addProperty("id", id(rs.getServerInfo().getName()));
            backends.add(b);
        }
        o.add("backends", backends);
        JsonArray order = new JsonArray();
        proxy.getConfiguration().getAttemptConnectionOrder().forEach(order::add);
        o.add("tryOrder", order);
        return o;
    }

    /** Status-ping every backend: reachability, latency, the version it reports, its own player count. */
    private void pingBackends() {
        for (RegisteredServer rs : proxy.getAllServers()) {
            String name = rs.getServerInfo().getName();
            long t0 = System.nanoTime();
            rs.ping().orTimeout(4, TimeUnit.SECONDS).whenComplete((ServerPing sp, Throwable err) -> {
                JsonObject r = new JsonObject();
                r.addProperty("checked", System.currentTimeMillis());
                if (err != null || sp == null) {
                    r.addProperty("reachable", false);
                } else {
                    r.addProperty("reachable", true);
                    r.addProperty("latency", Math.round((System.nanoTime() - t0) / 100_000.0) / 10.0);
                    r.addProperty("version", sp.getVersion().getName());
                    sp.getPlayers().ifPresent(pl -> {
                        r.addProperty("online", pl.getOnline());
                        r.addProperty("max", pl.getMax());
                    });
                }
                pings.put(name, r);
            });
        }
    }

    // ---------------------------------------------------------------- player events

    @Subscribe(order = PostOrder.EARLY)
    public void onLogin(LoginEvent e) {
        if (mod == null || !e.getResult().isAllowed()) return;
        ModState.Entry b = mod.ban(e.getPlayer().getUniqueId());
        if (b != null) e.setResult(ResultedEvent.ComponentResult.denied(banScreen(b)));
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent e) {
        Player p = e.getPlayer();
        joined.add(p.getUniqueId());
        JsonObject o = base("join", p);
        o.addProperty("text", p.getUsername() + " hat das Netzwerk betreten");
        o.addProperty("client", p.getProtocolVersion().getMostRecentSupportedVersion());
        event(o);
        playersDirty = true;
    }

    @Subscribe
    public void onConnected(ServerConnectedEvent e) {
        Player p = e.getPlayer();
        String to = id(e.getServer().getServerInfo().getName());
        String from = e.getPreviousServer().map(s -> id(s.getServerInfo().getName())).orElse(null);
        JsonObject o = base("switch", p);
        o.addProperty("to", to);
        o.addProperty("server", to);
        if (from != null) o.addProperty("from", from);
        o.addProperty("text", from == null ? p.getUsername() + " → " + to : p.getUsername() + " · " + from + " → " + to);
        event(o);
        playersDirty = true;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent e) {
        Player p = e.getPlayer();
        if (!joined.remove(p.getUniqueId())) return;
        JsonObject o = base("quit", p);
        p.getCurrentServer().ifPresent(s -> o.addProperty("server", id(s.getServerInfo().getName())));
        o.addProperty("text", p.getUsername() + " hat das Netzwerk verlassen");
        event(o);
        playersDirty = true;
    }

    @Subscribe
    public void onBrand(PlayerClientBrandEvent e) {
        playersDirty = true;
    }

    private static JsonObject base(String kind, Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("uuid", p.getUniqueId().toString());
        o.addProperty("name", p.getUsername());
        return o;
    }

    static Component banScreen(ModState.Entry e) {
        return LEGACY.deserialize("§c§lDu bist vom Aetherion-Netzwerk gebannt\n\n§7Grund: §f" + (e.reason() == null ? "Kein Grund angegeben" : e.reason())
                + "\n§7Dauer: §f" + ModState.until(e) + "\n\n§8Einspruch? Melde dich im Discord.");
    }

    // ---------------------------------------------------------------- requests

    private void handle(JsonObject req) {
        long id = req.get("id").getAsLong();
        String op = req.get("op").getAsString();
        JsonObject args = req.has("args") ? req.getAsJsonObject("args") : new JsonObject();
        worker.execute(() -> {
            JsonObject res = new JsonObject();
            res.addProperty("t", "res");
            res.addProperty("id", id);
            try {
                JsonElement data = switch (op) {
                    case "kick" -> kick(args);
                    case "message" -> message(args);
                    case "broadcast" -> broadcast(args);
                    case "send" -> send(args);
                    case "command" -> command(args);
                    case "complete" -> complete(args);
                    case "inventory" -> throw new IllegalStateException("Das Inventar liefert der Spielserver, nicht der Proxy.");
                    case "entities" -> throw new IllegalStateException("Auf dem Proxy gibt es keine Welten.");
                    default -> throw new IllegalArgumentException("unknown op " + op);
                };
                res.addProperty("ok", true);
                res.add("data", data == null ? new JsonObject() : data);
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                res.addProperty("ok", false);
                res.addProperty("error", c.getMessage() == null ? c.toString() : c.getMessage());
            }
            link.send(res);
        });
    }

    private Player player(JsonObject args) {
        return proxy.getPlayer(UUID.fromString(args.get("uuid").getAsString()))
                .orElseThrow(() -> new IllegalStateException("Der Spieler ist nicht (mehr) online."));
    }

    private JsonElement kick(JsonObject args) {
        player(args).disconnect(LEGACY.deserialize(args.has("reason") ? args.get("reason").getAsString() : "§cDu wurdest gekickt."));
        return null;
    }

    private JsonElement message(JsonObject args) {
        Player p = player(args);
        String text = args.get("text").getAsString();
        if (args.has("from")) {
            p.sendMessage(Component.text()
                    .append(Component.text("✉ ", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text("Team · " + args.get("from").getAsString(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(text, NamedTextColor.WHITE))
                    .build());
        } else {
            p.sendMessage(LEGACY.deserialize(text));
        }
        return null;
    }

    private JsonElement broadcast(JsonObject args) {
        proxy.sendMessage(Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("Team", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(args.get("from").getAsString(), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text(args.get("text").getAsString(), NamedTextColor.WHITE))
                .build());
        return null;
    }

    private JsonElement send(JsonObject args) throws Exception {
        Player p = player(args);
        RegisteredServer rs = byId(args.get("server").getAsString());
        if (rs == null) throw new IllegalStateException("Der Proxy kennt diesen Server nicht.");
        var result = p.createConnectionRequest(rs).connect().get(10, TimeUnit.SECONDS);
        JsonObject o = new JsonObject();
        o.addProperty("status", result.getStatus().name().toLowerCase());
        if (!result.isSuccessful()) {
            String why = result.getReasonComponent().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).orElse(null);
            throw new IllegalStateException(switch (result.getStatus()) {
                case ALREADY_CONNECTED -> "Der Spieler ist schon auf diesem Server.";
                case CONNECTION_IN_PROGRESS -> "Der Spieler wechselt gerade schon den Server.";
                default -> "Verbinden fehlgeschlagen" + (why == null || why.isBlank() ? "." : ": " + why);
            });
        }
        return o;
    }

    private JsonElement command(JsonObject args) throws Exception {
        String cmd = args.get("command").getAsString();
        String by = args.has("by") ? args.get("by").getAsString() : "Helm";
        log.info("{} (Helm) ran: {}", by, cmd);
        boolean ok = proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), cmd).get(15, TimeUnit.SECONDS);
        JsonObject o = new JsonObject();
        o.addProperty("dispatched", ok);
        return o;
    }

    private JsonElement complete(JsonObject args) throws Exception {
        String line = args.has("line") ? args.get("line").getAsString() : "";
        JsonArray a = new JsonArray();
        proxy.getCommandManager().offerSuggestions(proxy.getConsoleCommandSource(), line).get(3, TimeUnit.SECONDS)
                .stream().limit(40).forEach(a::add);
        return a;
    }

    /** /helm on the proxy console: is the link up. */
    private final class StatusCommand implements SimpleCommand {
        @Override
        public void execute(Invocation inv) {
            boolean up = link != null && link.connected();
            inv.source().sendMessage(Component.text("Aetherion Helm " + VERSION + " · Gateway ", NamedTextColor.GRAY)
                    .append(Component.text(link == null ? "nicht konfiguriert" : up ? "verbunden" : "nicht erreichbar",
                            up ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }

        @Override
        public boolean hasPermission(Invocation inv) {
            return inv.source().hasPermission("aetherion.helm.admin");
        }
    }
}
