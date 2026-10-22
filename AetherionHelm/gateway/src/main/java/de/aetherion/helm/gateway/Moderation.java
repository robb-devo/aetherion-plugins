package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Bans, mutes, kicks and warnings. The gateway owns the lists and pushes them to every agent; the agents keep a
 * copy on disk, so a ban still holds while the gateway is down. The proxy enforces bans at login, the backends
 * enforce mutes in chat (and bans too, in case the proxy plugin is missing).
 */
public final class Moderation {
    private final Helm h;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile long nextExpiry = Long.MAX_VALUE;

    public Moderation(Helm h) {
        this.h = h;
    }

    public record Target(String uuid, String name) {}

    /** Accepts a uuid, a known name, or any Mojang name (looked up once). */
    public Target resolve(String key) {
        if (key == null || key.isBlank()) return null;
        key = key.trim();
        if (key.matches("[0-9a-fA-F-]{32,36}")) {
            String uuid = dashed(key.replace("-", "").toLowerCase());
            Map<String, Object> r = h.players.row(uuid);
            return new Target(uuid, r == null ? key : (String) r.get("name"));
        }
        String uuid = h.players.uuidByName(key);
        if (uuid != null) {
            Map<String, Object> r = h.players.row(uuid);
            return new Target(uuid, (String) r.get("name"));
        }
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + key))
                    .timeout(Duration.ofSeconds(6)).build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                JsonNode n = Json.read(res.body());
                if (n != null && n.has("id")) return new Target(dashed(n.get("id").asText()), n.path("name").asText(key));
            }
        } catch (Exception ignored) {
            // offline lookup only
        }
        return null;
    }

    static String dashed(String hex) {
        if (hex.length() != 32) return hex;
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
    }

    // ---------------------------------------------------------------- actions

    public long punish(String type, Target t, String reason, Long durationMs, String by) {
        long now = System.currentTimeMillis();
        Long expires = durationMs == null || durationMs <= 0 ? null : now + durationMs;
        if ("ban".equals(type) || "mute".equals(type)) {
            // one active entry per type: a new one replaces the old
            h.db.exec("UPDATE punishments SET revoked_by=?, revoked_at=? WHERE uuid=? AND type=? AND revoked_at IS NULL AND (expires IS NULL OR expires>?)",
                    by + " (ersetzt)", now, t.uuid(), type, now);
        }
        long id = h.db.insert("INSERT INTO punishments(uuid,name,type,reason,by_user,created,expires) VALUES(?,?,?,?,?,?,?)",
                t.uuid(), t.name(), type, reason, by, now, expires);
        if ("ban".equals(type) || "mute".equals(type)) pushAll();
        String what = switch (type) {
            case "ban" -> expires == null ? "gebannt" : "gebannt (" + Fmt.duration(durationMs) + ")";
            case "mute" -> expires == null ? "gemutet" : "gemutet (" + Fmt.duration(durationMs) + ")";
            case "kick" -> "gekickt";
            default -> "verwarnt";
        };
        h.events.add("mod", null, t.uuid(), t.name(), what + " von " + by + (reason == null || reason.isBlank() ? "" : ": " + reason));
        if ("warn".equals(type)) {
            ObjectNode a = Json.obj();
            a.put("uuid", t.uuid());
            a.put("text", "§c§lVerwarnung§r§7 von " + by + ": §f" + (reason == null ? "" : reason));
            sendToPlayer(t.uuid(), "message", a);
        }
        return id;
    }

    public boolean revoke(String type, String uuid, String by) {
        long now = System.currentTimeMillis();
        int n = h.db.exec("UPDATE punishments SET revoked_by=?, revoked_at=? WHERE uuid=? AND type=? AND revoked_at IS NULL AND (expires IS NULL OR expires>?)",
                by, now, uuid, type, now);
        if (n > 0) {
            pushAll();
            Map<String, Object> r = h.players.row(uuid);
            h.events.add("mod", null, uuid, r == null ? uuid : (String) r.get("name"),
                    ("ban".equals(type) ? "entbannt" : "entmutet") + " von " + by);
        }
        return n > 0;
    }

    /** Kicks off the whole network via the proxy, or off the backend the player is on. */
    public boolean kick(String uuid, String message) {
        ObjectNode a = Json.obj();
        a.put("uuid", uuid);
        a.put("reason", message);
        return sendToPlayer(uuid, "kick", a);
    }

    /** Proxy first (reaches every player), else the backend that reports the player. */
    public boolean sendToPlayer(String uuid, String op, ObjectNode args) {
        AgentConn px = h.net.proxy();
        if (px != null) {
            px.request(op, args, 5000);
            return true;
        }
        ObjectNode p = h.net.findOnline(uuid);
        if (p == null) return false;
        Network.Srv s = h.net.srv(p.path("server").asText());
        if (s == null || s.agent == null) return false;
        s.agent.request(op, args, 5000);
        return true;
    }

    // ---------------------------------------------------------------- lists

    public List<Map<String, Object>> active(String type) {
        return h.db.rows("""
                SELECT id,uuid,name,type,reason,by_user AS byUser,created,expires FROM punishments
                WHERE (? IS NULL OR type=?) AND type IN ('ban','mute') AND revoked_at IS NULL AND (expires IS NULL OR expires>?)
                ORDER BY created DESC""", type, type, System.currentTimeMillis());
    }

    public List<Map<String, Object>> history(String uuid) {
        return h.db.rows("""
                SELECT id,type,reason,by_user AS byUser,created,expires,revoked_by AS revokedBy,revoked_at AS revokedAt
                FROM punishments WHERE uuid=? ORDER BY created DESC""", uuid);
    }

    public List<Map<String, Object>> recent(int limit) {
        return h.db.rows("""
                SELECT id,uuid,name,type,reason,by_user AS byUser,created,expires,revoked_by AS revokedBy,revoked_at AS revokedAt
                FROM punishments ORDER BY id DESC LIMIT ?""", limit);
    }

    private ObjectNode payload() {
        ObjectNode m = Json.obj();
        m.put("t", "mod");
        ArrayNode bans = m.putArray("bans");
        ArrayNode mutes = m.putArray("mutes");
        long next = Long.MAX_VALUE;
        for (Map<String, Object> r : active(null)) {
            ObjectNode e = Json.obj();
            e.put("uuid", (String) r.get("uuid"));
            e.put("name", (String) r.get("name"));
            e.put("reason", (String) r.get("reason"));
            e.put("by", (String) r.get("byUser"));
            Object exp = r.get("expires");
            if (exp != null) {
                long x = ((Number) exp).longValue();
                e.put("until", x);
                next = Math.min(next, x);
            }
            ("ban".equals(r.get("type")) ? bans : mutes).add(e);
        }
        nextExpiry = next;
        return m;
    }

    public void pushTo(AgentConn c) {
        c.send(payload());
    }

    public void pushAll() {
        ObjectNode p = payload();
        for (Network.Srv s : h.net.servers.values()) if (s.agent != null) s.agent.send(p);
    }

    /** Called every few seconds: when a timed ban or mute ran out, everybody gets the new list. */
    public void tick() {
        if (System.currentTimeMillis() >= nextExpiry) pushAll();
    }
}
