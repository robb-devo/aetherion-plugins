package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Watches the snapshot for trouble (server down, low TPS, memory, disk, RAID) and keeps a list of active alerts.
 * A condition must hold for a while before it alerts, so one slow tick does not page anybody.
 * Optional: mirrors alerts into a Discord channel via webhook.
 */
public final class Alerts {
    public static final class Alert {
        public String key, level, title, detail, server;
        public long since;
        public String ackedBy;
    }

    private final Helm h;
    private final Map<String, Alert> active = new LinkedHashMap<>();
    private final Map<String, Long> pending = new HashMap<>();
    private final Map<String, Long> discordSent = new HashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public Alerts(Helm h) {
        this.h = h;
    }

    public ObjectNode thresholds() {
        ObjectNode d = Json.obj();
        d.put("tpsWarn", 17.0);
        d.put("tpsCrit", 12.0);
        d.put("msptWarn", 50.0);
        d.put("heapWarn", 0.9);
        d.put("cpuWarn", 0.9);
        d.put("memWarn", 0.92);
        d.put("diskWarn", 0.9);
        JsonNode s = Json.read(h.db.setting("alerts", "{}"));
        if (s != null && s.isObject()) d.setAll((ObjectNode) s);
        return d;
    }

    public synchronized ArrayNode activeJson() {
        ArrayNode a = Json.arr();
        for (Alert x : active.values()) a.add(toJson(x));
        return a;
    }

    private static ObjectNode toJson(Alert x) {
        ObjectNode o = Json.obj();
        o.put("key", x.key);
        o.put("level", x.level);
        o.put("title", x.title);
        o.put("detail", x.detail);
        if (x.server != null) o.put("server", x.server);
        o.put("since", x.since);
        if (x.ackedBy != null) o.put("ackedBy", x.ackedBy);
        return o;
    }

    public synchronized boolean ack(String key, String by) {
        Alert a = active.get(key);
        if (a == null) return false;
        a.ackedBy = by;
        return true;
    }

    /** Runs every five seconds over the live state. */
    public synchronized void evaluate() {
        JsonNode t = thresholds();
        Set<String> seen = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Network.Srv s : h.net.servers.values()) {
            String id = s.def.id(), name = s.def.name();
            String st = s.state();
            if ("offline".equals(st)) {
                cond(seen, "down:" + id, "critical", name + " ist offline", "Der Server läuft nicht und wurde nicht vom Team gestoppt.", id, 15_000);
            }
            if ("degraded".equals(st)) {
                cond(seen, "agent:" + id, "warning", name + ": Helm-Plugin antwortet nicht",
                        "Der Server läuft, aber das Helm-Plugin ist nicht verbunden. Live-Daten fehlen.", id, 60_000);
            }
            if (!s.live() || s.stats == null || s.agent == null || s.agent.isProxy()) continue;
            double tps = s.stats.path("tps").path(0).asDouble(20);
            if (tps < t.path("tpsCrit").asDouble()) {
                cond(seen, "tps:" + id, "critical", name + ": TPS bei " + String.format(java.util.Locale.GERMANY, "%.1f", tps),
                        "Der Server kommt nicht hinterher. Schau in die Analyse (Lag-Radar).", id, 20_000);
            } else if (tps < t.path("tpsWarn").asDouble()) {
                cond(seen, "tps:" + id, "warning", name + ": TPS bei " + String.format(java.util.Locale.GERMANY, "%.1f", tps),
                        "Leichter Lag seit über 30 Sekunden.", id, 30_000);
            }
            long used = s.stats.path("heapUsed").asLong(), max = s.stats.path("heapMax").asLong(1);
            if (max > 0 && (double) used / max > t.path("heapWarn").asDouble()) {
                cond(seen, "heap:" + id, "warning", name + ": Arbeitsspeicher fast voll",
                        Fmt.bytes(used) + " von " + Fmt.bytes(max) + " belegt.", id, 90_000);
            }
        }
        JsonNode host = h.host.summary();
        if (host.has("cpu") && host.path("cpu").asDouble() > t.path("cpuWarn").asDouble()) {
            cond(seen, "host:cpu", "warning", "Hetzner: CPU-Last über " + Math.round(t.path("cpuWarn").asDouble() * 100) + " %",
                    "Die Maschine ist seit über einer Minute fast voll ausgelastet.", null, 60_000);
        }
        long memT = host.path("memTotal").asLong(), memU = host.path("memUsed").asLong();
        if (memT > 0 && (double) memU / memT > t.path("memWarn").asDouble()) {
            cond(seen, "host:mem", "warning", "Hetzner: RAM fast voll", Fmt.bytes(memU) + " von " + Fmt.bytes(memT) + " belegt.", null, 60_000);
        }
        for (JsonNode fs : h.host.detail().path("filesystems")) {
            long tot = fs.path("total").asLong(), u = fs.path("used").asLong();
            if (tot > 0 && (double) u / tot > t.path("diskWarn").asDouble()) {
                cond(seen, "disk:" + fs.path("mount").asText(), "warning", "Speicherplatz knapp auf " + fs.path("mount").asText(),
                        Fmt.bytes(tot - u) + " frei.", null, 0);
            }
        }
        for (JsonNode r : h.host.detail().path("static").path("raid")) {
            if (r.path("degraded").asBoolean()) {
                cond(seen, "raid:" + r.path("name").asText(), "critical", "RAID " + r.path("name").asText() + " ist degradiert",
                        "Eine Platte fehlt im Verbund (" + r.path("map").asText() + "). Hetzner-Support kontaktieren.", null, 0);
            }
        }
        // resolve what is gone
        for (var it = active.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!seen.contains(e.getKey())) {
                Alert a = e.getValue();
                it.remove();
                h.db.exec("UPDATE alerts_log SET resolved=? WHERE key=? AND resolved IS NULL", now, a.key);
                ObjectNode ev = toJson(a);
                ev.put("resolved", now);
                h.live.alert(ev);
                discord(a.key + ":ok", "ok", "Behoben: " + a.title, null);
            }
        }
        pending.keySet().retainAll(seen);
    }

    private void cond(Set<String> seen, String key, String level, String title, String detail, String server, long holdMs) {
        seen.add(key);
        long now = System.currentTimeMillis();
        Alert a = active.get(key);
        if (a != null) {
            boolean escalate = !a.level.equals(level) && "critical".equals(level);
            a.title = title;
            a.detail = detail;
            if (escalate) {
                a.level = level;
                h.live.alert(toJson(a));
                discord(key, level, title, detail);
            }
            return;
        }
        long first = pending.computeIfAbsent(key, k -> now);
        if (now - first < holdMs) return;
        a = new Alert();
        a.key = key;
        a.level = level;
        a.title = title;
        a.detail = detail;
        a.server = server;
        a.since = first;
        active.put(key, a);
        h.db.insert("INSERT INTO alerts_log(key,level,title,detail,server,since) VALUES(?,?,?,?,?,?)", key, level, title, detail, server, first);
        h.live.alert(toJson(a));
        discord(key, level, title, detail);
    }

    /** One-off notices (new report, failed action): shown as a toast and sent to Discord, not kept as active. */
    public void notify(String level, String title, String detail) {
        ObjectNode o = Json.obj();
        o.put("key", "notice:" + System.nanoTime());
        o.put("level", level);
        o.put("title", title);
        o.put("detail", detail);
        o.put("since", System.currentTimeMillis());
        o.put("notice", true);
        h.live.alert(o);
        discord("notice:" + title, level, title, detail);
    }

    public java.util.List<java.util.Map<String, Object>> history(int limit) {
        return h.db.rows("SELECT id,key,level,title,detail,server,since,resolved FROM alerts_log ORDER BY id DESC LIMIT ?", limit);
    }

    private void discord(String key, String level, String title, String detail) {
        String url = h.db.setting("discordWebhook", "");
        if (url.isBlank() || !url.startsWith("https://discord.com/api/webhooks/") && !url.startsWith("https://discordapp.com/api/webhooks/")) return;
        long now = System.currentTimeMillis();
        Long last = discordSent.get(key);
        if (last != null && now - last < 10 * 60_000) return;
        discordSent.put(key, now);
        int color = switch (level) {
            case "critical" -> 0xF43F5E;
            case "warning" -> 0xF59E0B;
            case "ok" -> 0x10B981;
            default -> 0x818CF8;
        };
        ObjectNode body = Json.obj();
        body.put("username", "Aetherion Helm");
        ObjectNode emb = body.putArray("embeds").addObject();
        emb.put("title", title);
        if (detail != null) emb.put("description", detail);
        emb.put("color", color);
        emb.put("url", h.cfg.publicOrigin);
        Thread.ofVirtual().start(() -> {
            try {
                http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // Discord is optional
            }
        });
    }
}
