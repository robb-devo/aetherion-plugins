package de.aetherion.helm.gateway;

import java.util.List;
import java.util.Map;

/** Everyone who ever joined: names, sessions, play time, addresses. Fed by Network's session reconciling. */
public final class Players {
    private final Helm h;

    public Players(Helm h) {
        this.h = h;
        // Sessions still open from before a gateway restart end where we last saw the player.
        h.db.exec("UPDATE play_sessions SET end=(SELECT COALESCE(last_seen, start) FROM players p WHERE p.uuid=play_sessions.uuid) WHERE end IS NULL");
    }

    void join(String uuid, String name, String ip, String client, String server) {
        long now = System.currentTimeMillis();
        h.db.tx(() -> {
            h.db.exec("""
                    INSERT INTO players(uuid,name,first_seen,last_seen,last_server,last_ip,client,sessions) VALUES(?,?,?,?,?,?,?,1)
                    ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, last_seen=excluded.last_seen,
                      last_server=COALESCE(excluded.last_server,last_server), last_ip=COALESCE(excluded.last_ip,last_ip),
                      client=COALESCE(excluded.client,client), sessions=sessions+1""",
                    uuid, name, now, now, server, ip, client);
            h.db.insert("INSERT INTO play_sessions(uuid,start) VALUES(?,?)", uuid, now);
            if (ip != null) {
                h.db.exec("INSERT INTO player_ips(uuid,ip,first,last) VALUES(?,?,?,?) ON CONFLICT(uuid,ip) DO UPDATE SET last=excluded.last",
                        uuid, ip, now, now);
            }
        });
    }

    void quit(String uuid) {
        long now = System.currentTimeMillis();
        h.db.tx(() -> {
            Long start = h.db.one("SELECT start FROM play_sessions WHERE uuid=? AND end IS NULL ORDER BY start DESC LIMIT 1",
                    rs -> rs.getLong(1), uuid);
            h.db.exec("UPDATE play_sessions SET end=? WHERE uuid=? AND end IS NULL", now, uuid);
            h.db.exec("UPDATE players SET last_seen=?, playtime_ms=playtime_ms+? WHERE uuid=?", now, start == null ? 0 : now - start, uuid);
        });
    }

    void lastServer(String uuid, String server) {
        h.db.exec("UPDATE players SET last_server=? WHERE uuid=?", server, uuid);
    }

    public int uniqueToday() {
        Integer n = h.db.one("SELECT COUNT(DISTINCT uuid) FROM play_sessions WHERE start>=? OR end IS NULL OR end>=?",
                rs -> rs.getInt(1), h.midnight(), h.midnight());
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> search(String q, int limit) {
        String like = "%" + (q == null ? "" : q.replace("%", "").replace("_", "\\_")) + "%";
        return h.db.rows("""
                SELECT uuid,name,first_seen AS firstSeen,last_seen AS lastSeen,last_server AS lastServer,playtime_ms AS playtime,
                  sessions FROM players WHERE name LIKE ? ESCAPE '\\' OR uuid=? ORDER BY last_seen DESC LIMIT ?""",
                like, q, Math.min(Math.max(limit, 1), 200));
    }

    public Map<String, Object> row(String uuid) {
        List<Map<String, Object>> r = h.db.rows("""
                SELECT uuid,name,first_seen AS firstSeen,last_seen AS lastSeen,last_server AS lastServer,last_ip AS lastIp,
                  client,playtime_ms AS playtime,sessions FROM players WHERE uuid=?""", uuid);
        return r.isEmpty() ? null : r.get(0);
    }

    public String uuidByName(String name) {
        return h.db.one("SELECT uuid FROM players WHERE name=? COLLATE NOCASE ORDER BY last_seen DESC LIMIT 1", rs -> rs.getString(1), name);
    }

    public List<Map<String, Object>> ips(String uuid) {
        return h.db.rows("SELECT ip,first,last,(SELECT COUNT(DISTINCT uuid) FROM player_ips b WHERE b.ip=a.ip) AS accounts FROM player_ips a WHERE uuid=? ORDER BY last DESC", uuid);
    }

    /** Other accounts seen from the same addresses: the alt check every moderator wants. */
    public List<Map<String, Object>> alts(String uuid) {
        return h.db.rows("""
                SELECT DISTINCT p.uuid,p.name,p.last_seen AS lastSeen FROM player_ips a JOIN player_ips b ON a.ip=b.ip AND b.uuid<>a.uuid
                JOIN players p ON p.uuid=b.uuid WHERE a.uuid=? ORDER BY p.last_seen DESC LIMIT 20""", uuid);
    }

    public List<Map<String, Object>> sessions(String uuid, int limit) {
        return h.db.rows("SELECT start,end FROM play_sessions WHERE uuid=? ORDER BY start DESC LIMIT ?", uuid, limit);
    }

    /** Hour-of-week heat map of when this player is usually online (7 x 24 minutes). */
    public int[][] activity(String uuid) {
        int[][] grid = new int[7][24];
        java.time.ZoneId zone = h.zone();
        long since = System.currentTimeMillis() - 28L * 24 * 3600 * 1000;
        for (long[] s : h.db.query("SELECT start, COALESCE(end, ?) FROM play_sessions WHERE uuid=? AND start>?",
                rs -> new long[]{rs.getLong(1), rs.getLong(2)}, System.currentTimeMillis(), uuid, since)) {
            for (long t = s[0]; t < s[1]; t += 60_000) {
                var z = java.time.Instant.ofEpochMilli(t).atZone(zone);
                grid[z.getDayOfWeek().getValue() - 1][z.getHour()]++;
            }
        }
        return grid;
    }

    public List<Map<String, Object>> notes(String uuid) {
        return h.db.rows("SELECT id,by_user AS byUser,ts,text FROM notes WHERE uuid=? ORDER BY ts DESC", uuid);
    }

    public void addNote(String uuid, String by, String text) {
        h.db.insert("INSERT INTO notes(uuid,by_user,ts,text) VALUES(?,?,?,?)", uuid, by, System.currentTimeMillis(), text);
    }

    public List<Map<String, Object>> newest(int limit) {
        return h.db.rows("SELECT uuid,name,first_seen AS firstSeen FROM players ORDER BY first_seen DESC LIMIT ?", limit);
    }

    public List<Map<String, Object>> topPlaytime(int limit) {
        return h.db.rows("SELECT uuid,name,playtime_ms AS playtime FROM players ORDER BY playtime_ms DESC LIMIT ?", limit);
    }

    public long total() {
        Long n = h.db.one("SELECT COUNT(*) FROM players", rs -> rs.getLong(1));
        return n == null ? 0 : n;
    }
}
