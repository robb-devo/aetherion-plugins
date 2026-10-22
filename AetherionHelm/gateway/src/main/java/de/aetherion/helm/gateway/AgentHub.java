package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Second listener, bound to loopback on its own port: the plugins dial in here. The tunnel never routes to it,
 * and every connection must still present the agent token in its first message.
 */
public final class AgentHub {
    private static final Logger LOG = LoggerFactory.getLogger("helm.agents");
    private final Helm h;
    private final Map<String, AgentConn> bySession = new ConcurrentHashMap<>();
    private Javalin app;

    public AgentHub(Helm h) {
        this.h = h;
    }

    public void start() {
        app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.startupWatcherEnabled = false;
            cfg.jetty.modifyWebSocketServletFactory(f -> {
                f.setMaxTextMessageSize(16 * 1024 * 1024);
                f.setIdleTimeout(java.time.Duration.ofMinutes(2));
            });
        });
        app.ws("/agent", ws -> {
            ws.onConnect(ctx -> {
                if (!(ctx.session.getRemoteAddress() instanceof InetSocketAddress a) || !a.getAddress().isLoopbackAddress()) {
                    ctx.closeSession(4003, "loopback only");
                    return;
                }
                ctx.enableAutomaticPings();
            });
            ws.onMessage(ctx -> onMessage(ctx, ctx.message()));
            ws.onClose(ctx -> onClose(ctx));
            ws.onError(ctx -> onClose(ctx));
        });
        app.start("127.0.0.1", h.cfg.agentPort);
    }

    public void stop() {
        if (app != null) app.stop();
    }

    private void onMessage(WsContext ctx, String raw) {
        JsonNode m = Json.read(raw);
        if (m == null) return;
        AgentConn c = bySession.get(ctx.sessionId());
        String t = m.path("t").asText();
        if (c == null) {
            if (!"hello".equals(t)) {
                ctx.closeSession(4001, "hello first");
                return;
            }
            String token = m.path("token").asText();
            if (!MessageDigest.isEqual(token.getBytes(), h.cfg.agentToken.getBytes())) {
                LOG.warn("agent with a wrong token refused");
                ctx.closeSession(4001, "bad token");
                return;
            }
            String server = m.path("server").asText();
            if (h.cfg.server(server) == null) {
                LOG.warn("agent for unknown server '{}' refused", server);
                ctx.closeSession(4004, "unknown server");
                return;
            }
            c = new AgentConn(server, m.path("kind").asText("paper"), m.path("version").asText("?"), m.path("pid").asLong(), ctx);
            bySession.put(ctx.sessionId(), c);
            h.net.agentUp(c, m);
            LOG.info("agent connected: {} ({} {}, pid {})", server, c.kind, c.version, c.pid);
            return;
        }
        c.lastMessage = System.currentTimeMillis();
        try {
            switch (t) {
                case "res" -> c.answer(m);
                case "stats" -> h.net.stats(c, m.path("data"));
                case "players" -> h.net.players(c, m.path("data"));
                case "info" -> h.net.info(c, m.path("data"));
                case "log" -> h.net.log(c, m.path("lines"));
                case "event" -> h.net.event(c, m.path("data"));
                default -> { }
            }
        } catch (Exception e) {
            LOG.warn("agent {}: message '{}' failed: {}", c.server, t, e.toString());
        }
    }

    private void onClose(WsContext ctx) {
        AgentConn c = bySession.remove(ctx.sessionId());
        if (c == null) return;
        c.failAll();
        h.net.agentDown(c);
        LOG.info("agent disconnected: {}", c.server);
    }
}
