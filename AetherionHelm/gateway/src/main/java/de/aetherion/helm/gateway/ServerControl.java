package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The root-side half of server control. The gateway runs as an unprivileged user and may call exactly one
 * script through sudo, helm-ctl, which knows the server folders and wraps the existing ae-start/ae-stop scripts
 * (graceful stop with the LOCKED countdown, watchdog marker, session locks).
 */
public final class ServerControl {
    private static final Logger LOG = LoggerFactory.getLogger("helm.ctl");
    private final Helm h;
    private volatile JsonNode sizes = Json.obj();
    private volatile JsonNode watchdog = Json.arr();

    public ServerControl(Helm h) {
        this.h = h;
    }

    public record Result(int code, String out) {}

    public Result run(long timeoutSec, String... args) {
        List<String> cmd = new ArrayList<>(h.cfg.ctl);
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thread pump = Thread.ofVirtual().start(() -> {
                try (InputStream in = p.getInputStream()) { in.transferTo(out); } catch (Exception ignored) {}
            });
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(124, "Zeitüberschreitung nach " + timeoutSec + " s");
            }
            pump.join(2000);
            return new Result(p.exitValue(), out.toString().trim());
        } catch (Exception e) {
            return new Result(127, e.getMessage());
        }
    }

    /** Every few seconds: which java processes exist, and which servers staff stopped on purpose. */
    public void poll() {
        Result r = run(15, "status");
        JsonNode n = r.code() == 0 ? Json.read(r.out()) : null;
        if (n == null) {
            for (Network.Srv s : h.net.servers.values()) s.ctlKnown = false;
            return;
        }
        for (Network.Srv s : h.net.servers.values()) {
            JsonNode e = n.path("servers").path(s.def.id());
            if (e.isMissingNode()) { s.ctlKnown = false; continue; }
            s.ctlKnown = true;
            s.ctlPid = e.path("pid").isNumber() ? e.path("pid").asLong() : null;
            s.ctlStopped = e.path("stopped").asBoolean();
            s.ctlUptimeS = e.path("uptime").asLong();
            if (s.ctlPid != null) s.lastSeenUp = System.currentTimeMillis();
            // a stop finished: the process is gone
            if (s.powerOp != null && ("stop".equals(s.powerOp) || "kill".equals(s.powerOp)) && s.ctlPid == null) s.powerOp = null;
        }
        pollHost();
    }

    /** Log tail + list ping for servers without a Helm agent (standalone vanilla). */
    public void pollHost() {
        for (Network.Srv s : h.net.servers.values()) {
            if (!s.hostOnly()) continue;
            if (s.def.port() > 0) {
                McPing.Result ping = McPing.ping("127.0.0.1", s.def.port(), 2500);
                h.net.applyPing(s, ping);
            }
            Result lr = run(12, "logs", s.def.id(), "180", String.valueOf(s.logOffset));
            if (lr.code() != 0) continue;
            JsonNode body = Json.read(lr.out());
            if (body == null) continue;
            if (body.path("offset").isNumber()) s.logOffset = body.path("offset").asLong();
            h.net.ingestLogLines(s.def.id(), body.path("lines"));
        }
    }

    public void pollSlow() {
        Result r = run(300, "sizes");
        JsonNode n = r.code() == 0 ? Json.read(r.out()) : null;
        if (n != null) sizes = n;
        Result w = run(15, "watchdog");
        if (w.code() == 0) {
            var arr = Json.arr();
            for (String l : w.out().split("\n")) if (!l.isBlank()) arr.add(l);
            watchdog = arr;
        }
    }

    public JsonNode sizes() {
        return sizes;
    }

    public JsonNode watchdog() {
        return watchdog;
    }

    /** start · stop · restart · kill. Runs in the background; the server tile shows the progress. */
    public String power(String id, String action, String by) {
        Network.Srv s = h.net.srv(id);
        if (s == null) return "Unbekannter Server";
        if (s.hostOnly()) return s.def.name() + " läuft ohne Helm-Agent — Start/Stop nicht über das Panel.";
        if (!List.of("start", "stop", "restart", "kill").contains(action)) return "Unbekannte Aktion";
        if (s.powerOp != null && System.currentTimeMillis() - s.powerSince < 3 * 60_000) return "Auf " + s.def.name() + " läuft schon: " + s.powerOp;
        s.powerOp = action;
        s.powerSince = System.currentTimeMillis();
        s.powerBy = by;
        String label = switch (action) {
            case "start" -> "startet";
            case "stop" -> "wird gestoppt (Countdown)";
            case "restart" -> "startet neu (Countdown)";
            default -> "wird sofort gestoppt";
        };
        h.events.add("server", id, null, by, s.def.name() + " " + label);
        Thread.ofVirtual().name("power-" + id).start(() -> {
            Result r = run(420, action, id);
            LOG.info("{} {} -> exit {}: {}", action, id, r.code(), r.out().replace('\n', ' '));
            if (r.code() != 0) {
                s.powerOp = null;
                h.events.add("server", id, null, by, s.def.name() + ": " + action + " fehlgeschlagen (" + lastLine(r.out()) + ")");
                h.alerts.notify("warning", s.def.name() + ": " + action + " fehlgeschlagen", lastLine(r.out()));
            } else if ("stop".equals(action) || "kill".equals(action)) {
                s.powerOp = null;
                h.events.add("server", id, null, by, s.def.name() + " ist gestoppt");
            }
            // start/restart clear when the agent says hello again (or after five minutes)
            poll();
        });
        return null;
    }

    private static String lastLine(String s) {
        String[] l = s.trim().split("\n");
        return l.length == 0 ? "" : l[l.length - 1];
    }

    public ObjectNode info() {
        ObjectNode o = Json.obj();
        o.set("sizes", sizes);
        o.set("watchdog", watchdog);
        return o;
    }
}
