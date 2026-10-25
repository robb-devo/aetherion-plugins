package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** One connected plugin (a Paper backend or the Velocity proxy) and its open requests. */
public final class AgentConn {
    public final String server;
    public final String kind;
    public final String version;
    public final long pid;
    public final long connectedAt = System.currentTimeMillis();
    private final WsContext ws;
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    public volatile long lastMessage = System.currentTimeMillis();

    public AgentConn(String server, String kind, String version, long pid, WsContext ws) {
        this.server = server;
        this.kind = kind;
        this.version = version;
        this.pid = pid;
        this.ws = ws;
    }

    public boolean isProxy() {
        return "velocity".equals(kind);
    }

    public void send(JsonNode msg) {
        String s = Json.write(msg);
        synchronized (this) {
            try {
                if (ws.session.isOpen()) ws.send(s);
            } catch (Exception ignored) {
                // closed while sending; the close handler cleans up
            }
        }
    }

    /** Sends {t:req} and completes with the agent's data, or fails with its error text. */
    public CompletableFuture<JsonNode> request(String op, ObjectNode args, long timeoutMs) {
        long id = ids.incrementAndGet();
        CompletableFuture<JsonNode> f = new CompletableFuture<>();
        pending.put(id, f);
        ObjectNode m = Json.obj();
        m.put("t", "req");
        m.put("id", id);
        m.put("op", op);
        m.set("args", args == null ? Json.obj() : args);
        send(m);
        return f.orTimeout(timeoutMs, TimeUnit.MILLISECONDS).whenComplete((v, e) -> pending.remove(id));
    }

    public void answer(JsonNode res) {
        CompletableFuture<JsonNode> f = pending.remove(res.path("id").asLong());
        if (f == null) return;
        if (res.path("ok").asBoolean()) f.complete(res.path("data"));
        else f.completeExceptionally(new AgentException(res.path("error").asText("Fehler auf dem Server")));
    }

    public void failAll() {
        pending.values().forEach(f -> f.completeExceptionally(new AgentException("Verbindung zum Server getrennt")));
        pending.clear();
    }

    public void close() {
        try { ws.closeSession(); } catch (Exception ignored) {}
    }

    public static final class AgentException extends RuntimeException {
        public AgentException(String msg) { super(msg, null, false, false); }
    }
}
