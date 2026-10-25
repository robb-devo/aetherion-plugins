package de.aetherion.helm.gateway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SQLite behind one lock. Traffic is a handful of staff, so one connection is plenty. */
public final class Db {
    public interface Mapper<T> { T map(ResultSet rs) throws SQLException; }

    private final Connection c;

    public Db(Path dir) throws Exception {
        Files.createDirectories(dir);
        c = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("helm.db"));
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA foreign_keys=ON");
        }
        migrate();
    }

    private void migrate() throws SQLException {
        String[] ddl = {
            """
            CREATE TABLE IF NOT EXISTS users(
              id INTEGER PRIMARY KEY, username TEXT UNIQUE NOT NULL COLLATE NOCASE, display TEXT NOT NULL,
              role TEXT NOT NULL, mc_name TEXT, pw_hash TEXT NOT NULL, totp_secret TEXT, totp_last_step INTEGER DEFAULT 0,
              recovery TEXT, created INTEGER NOT NULL, last_login INTEGER, disabled INTEGER DEFAULT 0)""",
            """
            CREATE TABLE IF NOT EXISTS sessions(
              id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
              token_hash TEXT UNIQUE NOT NULL, state TEXT NOT NULL, created INTEGER NOT NULL, last_seen INTEGER NOT NULL,
              expires INTEGER NOT NULL, ip TEXT, ua TEXT)""",
            """
            CREATE TABLE IF NOT EXISTS audit(
              id INTEGER PRIMARY KEY, ts INTEGER NOT NULL, user TEXT, ip TEXT, action TEXT NOT NULL, target TEXT, detail TEXT)""",
            "CREATE INDEX IF NOT EXISTS audit_ts ON audit(ts)",
            """
            CREATE TABLE IF NOT EXISTS players(
              uuid TEXT PRIMARY KEY, name TEXT NOT NULL, first_seen INTEGER, last_seen INTEGER, last_server TEXT,
              last_ip TEXT, client TEXT, playtime_ms INTEGER DEFAULT 0, sessions INTEGER DEFAULT 0)""",
            "CREATE INDEX IF NOT EXISTS players_name ON players(name COLLATE NOCASE)",
            """
            CREATE TABLE IF NOT EXISTS player_ips(uuid TEXT NOT NULL, ip TEXT NOT NULL, first INTEGER, last INTEGER,
              PRIMARY KEY(uuid, ip))""",
            """
            CREATE TABLE IF NOT EXISTS play_sessions(
              id INTEGER PRIMARY KEY, uuid TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER)""",
            "CREATE INDEX IF NOT EXISTS play_sessions_uuid ON play_sessions(uuid, start)",
            "CREATE INDEX IF NOT EXISTS play_sessions_start ON play_sessions(start)",
            """
            CREATE TABLE IF NOT EXISTS punishments(
              id INTEGER PRIMARY KEY, uuid TEXT NOT NULL, name TEXT, type TEXT NOT NULL, reason TEXT, by_user TEXT,
              created INTEGER NOT NULL, expires INTEGER, revoked_by TEXT, revoked_at INTEGER)""",
            "CREATE INDEX IF NOT EXISTS punishments_uuid ON punishments(uuid)",
            """
            CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY, uuid TEXT NOT NULL, by_user TEXT, ts INTEGER, text TEXT)""",
            """
            CREATE TABLE IF NOT EXISTS reports(
              id INTEGER PRIMARY KEY, ts INTEGER NOT NULL, server TEXT, reporter_uuid TEXT, reporter_name TEXT,
              target_uuid TEXT, target_name TEXT, reason TEXT, status TEXT NOT NULL DEFAULT 'open',
              resolved_by TEXT, resolved_at INTEGER, resolution TEXT)""",
            """
            CREATE TABLE IF NOT EXISTS events(
              id INTEGER PRIMARY KEY, ts INTEGER NOT NULL, kind TEXT NOT NULL, server TEXT, uuid TEXT, name TEXT, text TEXT)""",
            "CREATE INDEX IF NOT EXISTS events_ts ON events(ts)",
            "CREATE INDEX IF NOT EXISTS events_uuid ON events(uuid, ts)",
            """
            CREATE TABLE IF NOT EXISTS metrics(
              ts INTEGER NOT NULL, server TEXT NOT NULL, players INTEGER, tps REAL, mspt REAL, heap INTEGER,
              cpu REAL, rss INTEGER, entities INTEGER, chunks INTEGER)""",
            "CREATE INDEX IF NOT EXISTS metrics_ts ON metrics(server, ts)",
            """
            CREATE TABLE IF NOT EXISTS host_metrics(
              ts INTEGER PRIMARY KEY, cpu REAL, mem INTEGER, swap INTEGER, rx REAL, tx REAL, load1 REAL,
              disk_r REAL, disk_w REAL, players INTEGER)""",
            """
            CREATE TABLE IF NOT EXISTS alerts_log(
              id INTEGER PRIMARY KEY, key TEXT, level TEXT, title TEXT, detail TEXT, server TEXT, since INTEGER,
              resolved INTEGER, acked_by TEXT)""",
            "CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT)"
        };
        try (Statement s = c.createStatement()) {
            for (String q : ddl) s.execute(q);
        }
    }

    private static void bind(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
    }

    public synchronized int exec(String sql, Object... args) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql + ": " + e.getMessage(), e);
        }
    }

    public synchronized long insert(String sql, Object... args) {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, args);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql + ": " + e.getMessage(), e);
        }
    }

    public synchronized <T> List<T> query(String sql, Mapper<T> m, Object... args) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(m.map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql + ": " + e.getMessage(), e);
        }
    }

    public <T> T one(String sql, Mapper<T> m, Object... args) {
        List<T> l = query(sql, m, args);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Rows as ordered maps, column name to value: the shape the API hands to the web app. */
    public List<Map<String, Object>> rows(String sql, Object... args) {
        return query(sql, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            int n = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= n; i++) row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
            return row;
        }, args);
    }

    public synchronized void tx(Runnable r) {
        try {
            c.setAutoCommit(false);
            r.run();
            c.commit();
        } catch (Exception e) {
            try { c.rollback(); } catch (SQLException ignored) {}
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public String setting(String key, String def) {
        String v = one("SELECT value FROM settings WHERE key=?", rs -> rs.getString(1), key);
        return v == null ? def : v;
    }

    public void putSetting(String key, String value) {
        exec("INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", key, value);
    }
}
