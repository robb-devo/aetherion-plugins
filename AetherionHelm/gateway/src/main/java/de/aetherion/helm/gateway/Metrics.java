package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** Every 30 s one row per server and one for the host; charts read buckets of these. Kept for 30 days. */
public final class Metrics {
    private static final long KEEP_MS = 30L * 24 * 3600 * 1000;
    private final Helm h;

    public Metrics(Helm h) {
        this.h = h;
    }

    public void sample() {
        long ts = System.currentTimeMillis();
        int netPlayers = h.net.onlinePlayers().size();
        h.db.tx(() -> {
            for (Network.Srv s : h.net.servers.values()) {
                JsonNode st = s.live() ? s.stats : null;
                HostMonitor.Proc p = s.pid() > 0 ? h.host.proc(s.pid()) : null;
                if (st == null && p == null) continue;
                h.db.exec("INSERT INTO metrics(ts,server,players,tps,mspt,heap,cpu,rss,entities,chunks) VALUES(?,?,?,?,?,?,?,?,?,?)",
                        ts, s.def.id(),
                        st == null ? null : st.path("players").asInt(),
                        st == null || !st.has("tps") ? null : st.path("tps").path(0).asDouble(),
                        st == null || !st.has("mspt") ? null : st.path("mspt").asDouble(),
                        st == null ? null : st.path("heapUsed").asLong(),
                        p == null ? null : p.cpu(),
                        p == null ? null : p.rss(),
                        st == null || !st.has("entities") ? null : st.path("entities").asInt(),
                        st == null || !st.has("chunks") ? null : st.path("chunks").asInt());
            }
            JsonNode host = h.host.summary();
            if (host.has("cpu")) {
                h.db.exec("INSERT OR REPLACE INTO host_metrics(ts,cpu,mem,swap,rx,tx,load1,disk_r,disk_w,players) VALUES(?,?,?,?,?,?,?,?,?,?)",
                        ts, host.path("cpu").asDouble(), host.path("memUsed").asLong(), host.path("swapUsed").asLong(),
                        host.path("rx").asDouble(), host.path("tx").asDouble(), host.path("load").path(0).asDouble(),
                        host.path("diskRead").asDouble(), host.path("diskWrite").asDouble(), netPlayers);
            }
        });
    }

    static long bucketFor(long rangeMs) {
        // about 180 points per chart, never finer than the 30 s sampling
        return Math.max(30_000, rangeMs / 180 / 30_000 * 30_000);
    }

    public List<Map<String, Object>> server(String id, long rangeMs) {
        long b = bucketFor(rangeMs);
        return h.db.rows("""
                SELECT (ts/?)*? AS t, ROUND(AVG(players),2) AS players, MAX(players) AS playersMax, ROUND(AVG(tps),2) AS tps,
                  ROUND(MIN(tps),2) AS tpsMin, ROUND(AVG(mspt),2) AS mspt, ROUND(MAX(mspt),2) AS msptMax, CAST(AVG(heap) AS INTEGER) AS heap,
                  ROUND(AVG(cpu),4) AS cpu, CAST(AVG(rss) AS INTEGER) AS rss, CAST(AVG(entities) AS INTEGER) AS entities,
                  CAST(AVG(chunks) AS INTEGER) AS chunks
                FROM metrics WHERE server=? AND ts>? GROUP BY t ORDER BY t""", b, b, id, System.currentTimeMillis() - rangeMs);
    }

    public List<Map<String, Object>> host(long rangeMs) {
        long b = bucketFor(rangeMs);
        return h.db.rows("""
                SELECT (ts/?)*? AS t, ROUND(AVG(cpu),4) AS cpu, CAST(AVG(mem) AS INTEGER) AS mem, CAST(AVG(swap) AS INTEGER) AS swap,
                  ROUND(AVG(rx)) AS rx, ROUND(AVG(tx)) AS tx, ROUND(AVG(load1),2) AS load1, ROUND(AVG(disk_r)) AS diskRead,
                  ROUND(AVG(disk_w)) AS diskWrite, ROUND(AVG(players),2) AS players, MAX(players) AS playersMax
                FROM host_metrics WHERE ts>? GROUP BY t ORDER BY t""", b, b, System.currentTimeMillis() - rangeMs);
    }

    /** Network players over time, with the same bucketing. */
    public List<Map<String, Object>> players(long rangeMs) {
        long b = bucketFor(rangeMs);
        return h.db.rows("SELECT (ts/?)*? AS t, ROUND(AVG(players),2) AS players, MAX(players) AS playersMax FROM host_metrics WHERE ts>? GROUP BY t ORDER BY t",
                b, b, System.currentTimeMillis() - rangeMs);
    }

    public void prune() {
        long cut = System.currentTimeMillis() - KEEP_MS;
        h.db.exec("DELETE FROM metrics WHERE ts<?", cut);
        h.db.exec("DELETE FROM host_metrics WHERE ts<?", cut);
    }
}
