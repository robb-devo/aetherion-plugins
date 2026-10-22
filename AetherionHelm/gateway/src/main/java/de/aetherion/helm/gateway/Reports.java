package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** Player reports from /report in game, handled in the app. */
public final class Reports {
    private final Helm h;

    public Reports(Helm h) {
        this.h = h;
    }

    void add(String server, JsonNode e) {
        String reporter = Json.text(e, "name", "?");
        String target = Json.text(e, "target", "?");
        String reason = Json.text(e, "text", "");
        String targetUuid = Json.text(e, "targetUuid", null);
        if (targetUuid == null) targetUuid = h.players.uuidByName(target);
        h.db.insert("INSERT INTO reports(ts,server,reporter_uuid,reporter_name,target_uuid,target_name,reason) VALUES(?,?,?,?,?,?,?)",
                System.currentTimeMillis(), server, Json.text(e, "uuid", null), reporter, targetUuid, target, reason);
        h.events.add("report", server, Json.text(e, "uuid", null), reporter, target + ": " + reason);
        h.alerts.notify("info", "Neuer Report gegen " + target, reporter + " auf " + h.serverName(server) + ": " + reason);
    }

    public List<Map<String, Object>> list(String status, int limit) {
        return h.db.rows("""
                SELECT id,ts,server,reporter_uuid AS reporterUuid,reporter_name AS reporterName,target_uuid AS targetUuid,
                  target_name AS targetName,reason,status,resolved_by AS resolvedBy,resolved_at AS resolvedAt,resolution
                FROM reports WHERE (? IS NULL OR status=?) ORDER BY (status='open') DESC, id DESC LIMIT ?""",
                status, status, limit);
    }

    public boolean resolve(long id, String by, String resolution) {
        return h.db.exec("UPDATE reports SET status='resolved', resolved_by=?, resolved_at=?, resolution=? WHERE id=? AND status='open'",
                by, System.currentTimeMillis(), resolution, id) > 0;
    }

    public int openCount() {
        Integer n = h.db.one("SELECT COUNT(*) FROM reports WHERE status='open'", rs -> rs.getInt(1));
        return n == null ? 0 : n;
    }
}
