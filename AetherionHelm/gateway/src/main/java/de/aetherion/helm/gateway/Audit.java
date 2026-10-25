package de.aetherion.helm.gateway;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Append-only record of every staff action. Nothing in the app deletes from it. */
public final class Audit {
    private final Db db;
    private Consumer<Map<String, Object>> listener = x -> {};

    public Audit(Db db) {
        this.db = db;
    }

    public void onEntry(Consumer<Map<String, Object>> l) {
        this.listener = l;
    }

    public void log(String user, String ip, String action, String target, String detail) {
        long ts = System.currentTimeMillis();
        long id = db.insert("INSERT INTO audit(ts,user,ip,action,target,detail) VALUES(?,?,?,?,?,?)", ts, user, ip, action, target, detail);
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", id);
        row.put("ts", ts);
        row.put("user", user);
        row.put("action", action);
        row.put("target", target);
        row.put("detail", detail);
        listener.accept(row);
    }

    public List<Map<String, Object>> list(Long before, String user, String action, int limit) {
        StringBuilder q = new StringBuilder("SELECT id,ts,user,ip,action,target,detail FROM audit WHERE 1=1");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (before != null) { q.append(" AND id<?"); args.add(before); }
        if (user != null && !user.isBlank()) { q.append(" AND user=?"); args.add(user); }
        if (action != null && !action.isBlank()) { q.append(" AND action LIKE ?"); args.add(action + "%"); }
        q.append(" ORDER BY id DESC LIMIT ?");
        args.add(Math.min(Math.max(limit, 1), 500));
        return db.rows(q.toString(), args.toArray());
    }
}
