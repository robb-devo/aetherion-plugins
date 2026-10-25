package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Live picture of the network, fed by the agents and the host control script. Everything the dashboard shows
 * is assembled here, every two seconds, into one snapshot.
 */
public final class Network {
    private static final int CONSOLE_KEEP = 3000;
    private static final long STALE_MS = 10_000;

    public static final class Srv {
        public final Config.ServerDef def;
        public volatile AgentConn agent;
        public volatile JsonNode stats;
        public volatile long statsAt;
        public volatile JsonNode info;
        public volatile ArrayNode players = Json.arr();
        public final Deque<JsonNode> console = new ArrayDeque<>();
        // from helm-ctl (root side): is the java process there, did staff stop it on purpose
        public volatile Long ctlPid;
        public volatile boolean ctlStopped;
        public volatile long ctlUptimeS;
        public volatile boolean ctlKnown;
        // a start/stop/restart in flight
        public volatile String powerOp;
        public volatile long powerSince;
        public volatile String powerBy;
        public volatile long lastSeenUp;
        /** Host-only servers (no Helm agent): list ping + log tail from helm-ctl. */
        public volatile McPing.Result ping;
        public volatile long pingAt;
        public volatile long logOffset;

        Srv(Config.ServerDef def) {
            this.def = def;
        }

        public boolean hostOnly() {
            return "vanilla".equals(def.kind());
        }

        public boolean live() {
            return agent != null && System.currentTimeMillis() - statsAt < STALE_MS;
        }

        public long pid() {
            if (agent != null && agent.pid > 0) return agent.pid;
            return ctlPid == null ? 0 : ctlPid;
        }

        /** online · starting · stopping · restarting · degraded · stopped · offline · unknown */
        public String state() {
            long now = System.currentTimeMillis();
            if (powerOp != null && now - powerSince < 5 * 60_000) {
                if (!"start".equals(powerOp) || !live()) return switch (powerOp) {
                    case "start" -> "starting";
                    case "restart" -> "restarting";
                    default -> "stopping";
                };
            }
            if (live()) return "online";
            if (ctlKnown && ctlPid != null) {
                if (hostOnly()) return "online";
                // process is there but the plugin is not (yet) talking: booting, or Helm plugin missing
                return ctlUptimeS < 240 ? "starting" : "degraded";
            }
            if (ctlKnown) return ctlStopped ? "stopped" : "offline";
            return agent != null ? "online" : "unknown";
        }
    }

    private final Helm h;
    public final Map<String, Srv> servers = new LinkedHashMap<>();
    private final Set<String> online = new HashSet<>();   // uuids with an open play session
    private volatile int peakToday;
    private volatile String peakDay = "";

    public Network(Helm h) {
        this.h = h;
        for (Config.ServerDef d : h.cfg.servers) servers.put(d.id(), new Srv(d));
        peakDay = h.today();
        peakToday = Integer.parseInt(h.db.setting("peak:" + peakDay, "0"));
    }

    public Srv srv(String id) {
        return servers.get(id);
    }

    public AgentConn proxy() {
        for (Srv s : servers.values()) if (s.agent != null && s.agent.isProxy()) return s.agent;
        return null;
    }

    // ---------------------------------------------------------------- agent feed

    void agentUp(AgentConn c, JsonNode hello) {
        Srv s = srv(c.server);
        AgentConn old = s.agent;
        s.agent = c;
        if (old != null && old != c) old.close();
        if ("start".equals(s.powerOp) || "restart".equals(s.powerOp)) {
            h.events.add("server", c.server, null, null, s.def.name() + " ist wieder online");
            s.powerOp = null;
        }
        h.mod.pushTo(c);
        if (c.isProxy()) {
            // the proxy names its servers itself; it matches them to Helm ids by port
            ObjectNode m = Json.obj();
            m.put("t", "servers");
            ArrayNode list = m.putArray("list");
            for (Config.ServerDef d : h.cfg.servers) {
                ObjectNode x = list.addObject();
                x.put("id", d.id());
                x.put("port", d.port());
                x.put("kind", d.kind());
            }
            c.send(m);
        }
    }

    void agentDown(AgentConn c) {
        Srv s = srv(c.server);
        if (s.agent != c) return;
        s.agent = null;
        s.players = Json.arr();
    }

    void stats(AgentConn c, JsonNode data) {
        Srv s = srv(c.server);
        s.stats = data;
        s.statsAt = System.currentTimeMillis();
        s.lastSeenUp = s.statsAt;
    }

    void info(AgentConn c, JsonNode data) {
        srv(c.server).info = data;
    }

    void players(AgentConn c, JsonNode data) {
        Srv s = srv(c.server);
        s.players = data.isArray() ? (ArrayNode) data : Json.arr();
        if (c.isProxy() || proxy() == null) reconcileSessions();
    }

    void log(AgentConn c, JsonNode lines) {
        ingestLogLines(c.server, lines);
    }

    void ingestLogLines(String id, JsonNode lines) {
        if (!lines.isArray() || lines.isEmpty()) return;
        Srv s = srv(id);
        if (s == null) return;
        synchronized (s.console) {
            for (JsonNode l : lines) {
                s.console.addLast(l);
                if (s.console.size() > CONSOLE_KEEP) s.console.removeFirst();
            }
        }
        h.live.log(id, lines);
    }

    void applyPing(Srv s, McPing.Result r) {
        s.ping = r;
        s.pingAt = System.currentTimeMillis();
    }

    void event(AgentConn c, JsonNode e) {
        String kind = e.path("kind").asText();
        String uuid = Json.text(e, "uuid", null);
        String name = Json.text(e, "name", null);
        String text = Json.text(e, "text", null);
        switch (kind) {
            case "join", "quit", "switch" -> {
                // Network joins come from the proxy; without a proxy agent the backends' joins stand in.
                if (!c.isProxy() && proxy() != null) return;
                if ("switch".equals(kind) && uuid != null) h.players.lastServer(uuid, Json.text(e, "to", c.server));
                h.events.add(kind, c.isProxy() ? Json.text(e, "server", null) : c.server, uuid, name, text);
            }
            case "report" -> h.reports.add(c.server, e);
            default -> h.events.add(kind, c.server, uuid, name, text);
        }
    }

    // ---------------------------------------------------------------- players

    /** Network player list: the proxy's when it is connected, else everyone the backends report. */
    public List<ObjectNode> onlinePlayers() {
        Map<String, ObjectNode> backend = new HashMap<>();
        for (Srv s : servers.values()) {
            if (s.agent == null || s.agent.isProxy()) continue;
            for (JsonNode p : s.players) {
                ObjectNode o = ((ObjectNode) p).deepCopy();
                o.put("server", s.def.id());
                backend.put(p.path("uuid").asText(), o);
            }
        }
        AgentConn px = proxy();
        List<ObjectNode> out = new ArrayList<>();
        if (px != null) {
            for (JsonNode p : srv(px.server).players) {
                String uuid = p.path("uuid").asText();
                ObjectNode o = ((ObjectNode) p).deepCopy();
                ObjectNode b = backend.get(uuid);
                if (b != null) {
                    b.fieldNames().forEachRemaining(f -> { if (!o.has(f) || "ping".equals(f) && o.path("ping").asInt() <= 0) o.set(f, b.get(f)); });
                }
                out.add(o);
            }
        } else {
            out.addAll(backend.values());
        }
        out.sort((a, b) -> a.path("name").asText().compareToIgnoreCase(b.path("name").asText()));
        return out;
    }

    public ObjectNode findOnline(String uuid) {
        for (ObjectNode p : onlinePlayers()) if (p.path("uuid").asText().equals(uuid)) return p;
        return null;
    }

    private synchronized void reconcileSessions() {
        List<ObjectNode> now = onlinePlayers();
        Set<String> seen = new HashSet<>();
        for (ObjectNode p : now) {
            String uuid = p.path("uuid").asText();
            seen.add(uuid);
            if (online.add(uuid)) {
                h.players.join(uuid, p.path("name").asText(), Json.text(p, "ip", null), Json.text(p, "client", null), Json.text(p, "server", null));
            }
        }
        for (String uuid : new ArrayList<>(online)) {
            if (!seen.contains(uuid)) {
                online.remove(uuid);
                h.players.quit(uuid);
            }
        }
        int count = now.size();
        String day = h.today();
        if (!day.equals(peakDay)) { peakDay = day; peakToday = 0; }
        if (count > peakToday) {
            peakToday = count;
            h.db.putSetting("peak:" + day, String.valueOf(count));
        }
    }

    // ---------------------------------------------------------------- snapshot

    public ObjectNode serverJson(Srv s, boolean detail) {
        ObjectNode o = Json.obj();
        Config.ServerDef d = s.def;
        o.put("id", d.id());
        o.put("name", d.name());
        o.put("role", d.role());
        o.put("kind", d.kind());
        o.put("state", s.state());
        o.put("agent", s.agent != null);
        if (s.agent != null) o.put("agentVersion", s.agent.version);
        long pid = s.pid();
        if (pid > 0) o.put("pid", pid);
        if (!d.xmx().isEmpty()) o.put("xmx", d.xmx());
        if (detail && d.port() > 0) o.put("port", d.port());
        if (s.powerOp != null) {
            ObjectNode p = o.putObject("power");
            p.put("op", s.powerOp);
            p.put("since", s.powerSince);
            p.put("by", s.powerBy);
        }
        JsonNode st = s.live() ? s.stats : null;
        if (st != null) {
            o.setAll((ObjectNode) st.deepCopy());
        } else if (s.ctlPid != null) {
            o.put("uptime", s.ctlUptimeS * 1000);
        }
        HostMonitor.Proc pr = pid > 0 ? h.host.proc(pid) : null;
        if (pr != null) {
            o.put("rss", pr.rss());
            o.put("hostCpu", pr.cpu());
        }
        if (s.hostOnly() && s.ping != null && s.ping.ok() && System.currentTimeMillis() - s.pingAt < 30_000) {
            o.put("players", s.ping.online());
            o.put("maxPlayers", s.ping.max());
            if (detail) {
                ObjectNode info = s.info != null ? ((ObjectNode) s.info).deepCopy() : Json.obj();
                if (!info.has("mc") && s.ping.version() != null) info.put("mc", s.ping.version());
                if (!info.has("motd") && s.ping.motd() != null) info.put("motd", s.ping.motd());
                info.put("software", "Vanilla");
                o.set("info", info);
                if (s.ping.sample().size() > 0) o.set("pingPlayers", s.ping.sample());
            }
        } else if (!o.has("players")) {
            o.put("players", s.players.size());
        }
        if (detail && s.info != null && !o.has("info")) o.set("info", s.info);
        return o;
    }

    public ObjectNode snapshot() {
        ObjectNode o = Json.obj();
        o.put("ts", System.currentTimeMillis());
        ArrayNode srv = o.putArray("servers");
        for (Srv s : servers.values()) srv.add(serverJson(s, false));
        List<ObjectNode> players = onlinePlayers();
        ObjectNode net = o.putObject("network");
        net.put("online", players.size());
        net.put("peakToday", Math.max(peakToday, players.size()));
        net.put("uniqueToday", h.players.uniqueToday());
        AgentConn px = proxy();
        if (px != null && srv(px.server).stats != null) net.put("maxPlayers", srv(px.server).stats.path("maxPlayers").asInt());
        ArrayNode pl = o.putArray("players");
        for (ObjectNode p : players) {
            ObjectNode c = Json.obj();
            for (String f : new String[]{"uuid", "name", "server", "ping", "client", "world", "gamemode", "health", "level", "afk"}) {
                if (p.has(f)) c.set(f, p.get(f));
            }
            pl.add(c);
        }
        o.set("host", h.host.summary());
        o.set("alerts", h.alerts.activeJson());
        o.put("openReports", h.reports.openCount());
        return o;
    }

    public List<JsonNode> console(String id, int limit) {
        Srv s = srv(id);
        if (s == null) return List.of();
        synchronized (s.console) {
            List<JsonNode> all = new ArrayList<>(s.console);
            return all.subList(Math.max(0, all.size() - limit), all.size());
        }
    }
}
