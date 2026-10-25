package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import org.eclipse.jetty.websocket.api.WriteCallback;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The app's live channel: snapshots every two seconds, console lines, activity, alerts, audit entries. */
public final class LiveHub {
    private static final class Client {
        final WsContext ws;
        final Auth.Session session;
        volatile String console;

        Client(WsContext ws, Auth.Session session) {
            this.ws = ws;
            this.session = session;
        }

        boolean can(String perm) {
            return Roles.has(session.user().role(), perm);
        }

        void send(String s) {
            try {
                if (ws.session.isOpen()) ws.session.getRemote().sendString(s, WriteCallback.NOOP);
            } catch (Exception ignored) {
                // closed
            }
        }
    }

    private final Helm h;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    public LiveHub(Helm h) {
        this.h = h;
    }

    public void routes(WsConfig ws) {
        ws.onConnect(ctx -> {
            String origin = ctx.header("Origin");
            if (origin != null && !h.api.originAllowed(origin)) {
                ctx.closeSession(4403, "origin");
                return;
            }
            Auth.Session s = h.auth.session(ctx.cookie(Auth.COOKIE), h.api.clientIp(ctx.header("CF-Connecting-IP"), ctx.session.getRemoteAddress().toString()));
            if (s == null || s.enrolling()) {
                ctx.closeSession(4401, "login");
                return;
            }
            ctx.enableAutomaticPings();
            Client c = new Client(ctx, s);
            clients.put(ctx.sessionId(), c);
            ObjectNode hello = Json.obj();
            hello.put("t", "snap");
            hello.set("data", h.snapshot());
            c.send(Json.write(hello));
        });
        ws.onMessage(ctx -> {
            Client c = clients.get(ctx.sessionId());
            JsonNode m = Json.read(ctx.message());
            if (c == null || m == null) return;
            if ("sub".equals(m.path("t").asText())) {
                String srv = Json.text(m, "console", null);
                if (srv != null && (!c.can("console.view") || h.net.srv(srv) == null)) srv = null;
                c.console = srv;
                if (srv != null) {
                    ObjectNode o = Json.obj();
                    o.put("t", "console");
                    o.put("server", srv);
                    o.put("reset", true);
                    ArrayNode lines = o.putArray("lines");
                    h.net.console(srv, 400).forEach(lines::add);
                    c.send(Json.write(o));
                }
            }
        });
        ws.onClose(ctx -> clients.remove(ctx.sessionId()));
        ws.onError(ctx -> clients.remove(ctx.sessionId()));
    }

    public void snapshot(ObjectNode snap) {
        if (clients.isEmpty()) return;
        ObjectNode o = Json.obj();
        o.put("t", "snap");
        o.set("data", snap);
        String s = Json.write(o);
        clients.values().forEach(c -> c.send(s));
    }

    public void log(String server, JsonNode lines) {
        if (clients.isEmpty()) return;
        ObjectNode o = Json.obj();
        o.put("t", "console");
        o.put("server", server);
        o.set("lines", lines);
        String s = Json.write(o);
        clients.values().forEach(c -> { if (server.equals(c.console)) c.send(s); });
    }

    public void event(ObjectNode e) {
        if (clients.isEmpty()) return;
        ObjectNode o = Json.obj();
        o.put("t", "event");
        o.set("data", e);
        String s = Json.write(o);
        clients.values().forEach(c -> c.send(s));
    }

    public void alert(ObjectNode a) {
        if (clients.isEmpty()) return;
        ObjectNode o = Json.obj();
        o.put("t", "alert");
        o.set("data", a);
        String s = Json.write(o);
        clients.values().forEach(c -> c.send(s));
    }

    public void audit(Map<String, Object> row) {
        if (clients.isEmpty()) return;
        ObjectNode o = Json.obj();
        o.put("t", "audit");
        o.set("data", Json.M.valueToTree(row));
        String s = Json.write(o);
        clients.values().forEach(c -> { if (c.can("audit.view")) c.send(s); });
    }

    /** Ends live connections of a user whose sessions were revoked. */
    public void kickUser(long userId) {
        clients.values().forEach(c -> {
            if (c.session.user().id() == userId) c.ws.closeSession();
        });
    }

    public int count() {
        return clients.size();
    }

    public java.util.Set<String> onlineStaff() {
        java.util.Set<String> s = new java.util.TreeSet<>();
        clients.values().forEach(c -> s.add(c.session.user().display()));
        return s;
    }
}
