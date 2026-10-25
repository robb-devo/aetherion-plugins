package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The activity stream: joins, hops, chat, commands, deaths, staff actions, server power. */
public final class Events {
    private static final long KEEP_MS = 30L * 24 * 3600 * 1000;
    private final Helm h;

    public Events(Helm h) {
        this.h = h;
    }

    public void add(String kind, String server, String uuid, String name, String text) {
        long ts = System.currentTimeMillis();
        long id = h.db.insert("INSERT INTO events(ts,kind,server,uuid,name,text) VALUES(?,?,?,?,?,?)", ts, kind, server, uuid, name, text);
        ObjectNode e = Json.obj();
        e.put("id", id);
        e.put("ts", ts);
        e.put("kind", kind);
        if (server != null) e.put("server", server);
        if (uuid != null) e.put("uuid", uuid);
        if (name != null) e.put("name", name);
        if (text != null) e.put("text", text);
        h.live.event(e);
    }

    public List<Map<String, Object>> list(List<String> kinds, String server, String uuid, Long before, int limit) {
        StringBuilder q = new StringBuilder("SELECT id,ts,kind,server,uuid,name,text FROM events WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (kinds != null && !kinds.isEmpty()) {
            q.append(" AND kind IN (").append("?,".repeat(kinds.size()).replaceAll(",$", "")).append(")");
            args.addAll(kinds);
        }
        if (server != null) { q.append(" AND server=?"); args.add(server); }
        if (uuid != null) { q.append(" AND uuid=?"); args.add(uuid); }
        if (before != null) { q.append(" AND id<?"); args.add(before); }
        q.append(" ORDER BY id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 500));
        return h.db.rows(q.toString(), args.toArray());
    }

    /** Messages per hour over the last day, for the chat page's little activity chart. */
    public List<Map<String, Object>> chatRate() {
        long since = System.currentTimeMillis() - 24L * 3600 * 1000;
        return h.db.rows("SELECT (ts/3600000)*3600000 AS t, COUNT(*) AS n FROM events WHERE kind='chat' AND ts>? GROUP BY t ORDER BY t", since);
    }

    public void prune() {
        h.db.exec("DELETE FROM events WHERE ts<?", System.currentTimeMillis() - KEEP_MS);
    }
}
