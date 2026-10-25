package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accounts, sessions, login with mandatory TOTP, recovery codes and brute-force throttling.
 * Session tokens leave the server once, in an HttpOnly cookie; the database keeps only their hash.
 */
public final class Auth {
    public static final String COOKIE = "helm_sid";
    public static final long SESSION_TTL = 30L * 24 * 3600 * 1000;
    private static final long TICKET_TTL = 5 * 60 * 1000;
    private static final int MAX_FAILS = 6;
    private static final long FAIL_WINDOW = 15 * 60 * 1000;

    public record User(long id, String username, String display, String role, String mcName, boolean totp,
                       long created, Long lastLogin) {}

    public record Session(long id, User user, String state) {
        public boolean enrolling() { return "enroll".equals(state); }
    }

    public record Login(String status, String ticket, String token, String error) {}

    private record Ticket(long userId, boolean remember, long expires) {}

    private final Db db;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> fails = new ConcurrentHashMap<>();
    private final Map<String, String> pendingSecrets = new ConcurrentHashMap<>();

    public Auth(Db db) {
        this.db = db;
    }

    private User user(java.sql.ResultSet rs) throws java.sql.SQLException {
        long ll = rs.getLong("last_login");
        return new User(rs.getLong("id"), rs.getString("username"), rs.getString("display"), rs.getString("role"),
                rs.getString("mc_name"), rs.getString("totp_secret") != null, rs.getLong("created"),
                rs.wasNull() ? null : ll);
    }

    public User userById(long id) {
        return db.one("SELECT * FROM users WHERE id=? AND disabled=0", this::user, id);
    }

    public List<User> users() {
        return db.query("SELECT * FROM users WHERE disabled=0 ORDER BY created", this::user);
    }

    public User create(String username, String display, String role, String mcName, String password) {
        if (!username.matches("[A-Za-z0-9_.-]{2,24}")) throw new IllegalArgumentException("Benutzername: 2-24 Zeichen, Buchstaben, Ziffern, _ . -");
        if (!Roles.ROLES.containsKey(role)) throw new IllegalArgumentException("Unbekannte Rolle");
        checkStrength(password);
        long id = db.insert("INSERT INTO users(username,display,role,mc_name,pw_hash,created) VALUES(?,?,?,?,?,?)",
                username, display, role, mcName, Crypto.hashPassword(password), System.currentTimeMillis());
        return userById(id);
    }

    public static void checkStrength(String pw) {
        if (pw == null || pw.length() < 10) throw new IllegalArgumentException("Das Passwort braucht mindestens 10 Zeichen.");
    }

    // ---------------------------------------------------------------- throttling

    private boolean throttled(String key) {
        List<Long> l = fails.get(key);
        if (l == null) return false;
        long cut = System.currentTimeMillis() - FAIL_WINDOW;
        synchronized (l) {
            l.removeIf(t -> t < cut);
            return l.size() >= MAX_FAILS;
        }
    }

    private void fail(String key) {
        List<Long> l = fails.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (l) { l.add(System.currentTimeMillis()); }
    }

    // ---------------------------------------------------------------- login

    public Login login(String username, String password, boolean remember, String ip, String ua) {
        String uKey = "u:" + (username == null ? "" : username.toLowerCase());
        String iKey = "i:" + ip;
        if (throttled(uKey) || throttled(iKey)) {
            return new Login("locked", null, null, "Zu viele Fehlversuche. Bitte warte 15 Minuten.");
        }
        record Row(long id, String hash, String totp) {}
        Row row = db.one("SELECT id,pw_hash,totp_secret FROM users WHERE username=? AND disabled=0",
                rs -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3)), username);
        if (row == null) {
            Crypto.dummyCheck();
            fail(uKey); fail(iKey);
            return new Login("error", null, null, "Benutzername oder Passwort stimmt nicht.");
        }
        if (!Crypto.checkPassword(password, row.hash())) {
            fail(uKey); fail(iKey);
            return new Login("error", null, null, "Benutzername oder Passwort stimmt nicht.");
        }
        if (row.totp() == null) {
            // First login: the session can only set up the authenticator until that is done.
            String token = newSession(row.id(), "enroll", remember, ip, ua);
            return new Login("enroll", null, token, null);
        }
        String ticket = Crypto.token(24);
        tickets.put(ticket, new Ticket(row.id(), remember, System.currentTimeMillis() + TICKET_TTL));
        return new Login("totp", ticket, null, null);
    }

    public Login secondFactor(String ticket, String code, String ip, String ua) {
        Ticket t = ticket == null ? null : tickets.get(ticket);
        if (t == null || t.expires() < System.currentTimeMillis()) {
            if (ticket != null) tickets.remove(ticket);
            return new Login("expired", null, null, "Die Anmeldung ist abgelaufen. Bitte melde dich neu an.");
        }
        String uKey = "t:" + t.userId();
        if (throttled(uKey) || throttled("i:" + ip)) {
            return new Login("locked", null, null, "Zu viele Fehlversuche. Bitte warte 15 Minuten.");
        }
        record Row(String secret, long last, String recovery) {}
        Row row = db.one("SELECT totp_secret,totp_last_step,recovery FROM users WHERE id=?",
                rs -> new Row(rs.getString(1), rs.getLong(2), rs.getString(3)), t.userId());
        if (row == null) return new Login("expired", null, null, "Konto nicht gefunden.");
        long step = Crypto.verifyTotp(row.secret(), code, row.last());
        if (step > 0) {
            db.exec("UPDATE users SET totp_last_step=? WHERE id=?", step, t.userId());
        } else if (!useRecovery(t.userId(), row.recovery(), code)) {
            fail(uKey); fail("i:" + ip);
            return new Login("error", ticket, null, "Der Code stimmt nicht.");
        }
        tickets.remove(ticket);
        return new Login("ok", null, newSession(t.userId(), "ok", t.remember(), ip, ua), null);
    }

    private boolean useRecovery(long userId, String recoveryJson, String code) {
        if (recoveryJson == null || code == null) return false;
        String norm = code.trim().toUpperCase().replace(" ", "");
        if (!norm.matches("[A-Z0-9]{4}-?[A-Z0-9]{4}")) return false;
        if (norm.length() == 8) norm = norm.substring(0, 4) + "-" + norm.substring(4);
        String h = Crypto.sha256(norm);
        var arr = (ArrayNode) Json.read(recoveryJson);
        if (arr == null) return false;
        for (int i = 0; i < arr.size(); i++) {
            if (arr.get(i).asText().equals(h)) {
                arr.remove(i);
                db.exec("UPDATE users SET recovery=? WHERE id=?", Json.write(arr), userId);
                return true;
            }
        }
        return false;
    }

    private String newSession(long userId, String state, boolean remember, String ip, String ua) {
        String token = Crypto.token(32);
        long now = System.currentTimeMillis();
        long ttl = remember ? SESSION_TTL : 12L * 3600 * 1000;
        db.insert("INSERT INTO sessions(user_id,token_hash,state,created,last_seen,expires,ip,ua) VALUES(?,?,?,?,?,?,?,?)",
                userId, Crypto.sha256(token), state, now, now, now + ttl, ip, ua == null ? null : ua.substring(0, Math.min(ua.length(), 300)));
        if ("ok".equals(state)) db.exec("UPDATE users SET last_login=? WHERE id=?", now, userId);
        return token;
    }

    public Session session(String token, String ip) {
        if (token == null || token.length() < 20) return null;
        record Row(long id, long userId, String state, long expires, long lastSeen) {}
        Row r = db.one("SELECT id,user_id,state,expires,last_seen FROM sessions WHERE token_hash=?",
                rs -> new Row(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getLong(5)), Crypto.sha256(token));
        long now = System.currentTimeMillis();
        if (r == null || r.expires() < now) return null;
        User u = userById(r.userId());
        if (u == null) return null;
        if (now - r.lastSeen() > 60_000) {
            // Sliding expiry for remembered sessions; short ones keep their end.
            long ttl = r.expires() - r.lastSeen() > 13L * 3600 * 1000 ? SESSION_TTL : 0;
            db.exec("UPDATE sessions SET last_seen=?, ip=?, expires=MAX(expires, ?) WHERE id=?", now, ip, ttl > 0 ? now + ttl : 0, r.id());
        }
        return new Session(r.id(), u, r.state());
    }

    public void logout(long sessionId) {
        db.exec("DELETE FROM sessions WHERE id=?", sessionId);
    }

    public List<Map<String, Object>> sessions(long userId) {
        return db.rows("SELECT id,created,last_seen AS lastSeen,ip,ua,state FROM sessions WHERE user_id=? AND expires>? ORDER BY last_seen DESC",
                userId, System.currentTimeMillis());
    }

    public void revoke(long userId, long sessionId) {
        db.exec("DELETE FROM sessions WHERE id=? AND user_id=?", sessionId, userId);
    }

    public void revokeOthers(long userId, long keepSessionId) {
        db.exec("DELETE FROM sessions WHERE user_id=? AND id<>?", userId, keepSessionId);
    }

    public void revokeAll(long userId) {
        db.exec("DELETE FROM sessions WHERE user_id=?", userId);
    }

    // ---------------------------------------------------------------- authenticator enrolment

    public String enrollStart(long userId) {
        String secret = Crypto.newTotpSecret();
        pendingSecrets.put(String.valueOf(userId), secret);
        return secret;
    }

    /** Returns fresh recovery codes, or null when the code does not match. */
    public List<String> enrollFinish(Session s, String code) {
        String secret = pendingSecrets.get(String.valueOf(s.user().id()));
        if (secret == null) return null;
        long step = Crypto.verifyTotp(secret, code, 0);
        if (step < 0) return null;
        pendingSecrets.remove(String.valueOf(s.user().id()));
        List<String> codes = new ArrayList<>();
        ArrayNode hashes = Json.arr();
        for (int i = 0; i < 8; i++) {
            String c = Crypto.recoveryCode();
            codes.add(c);
            hashes.add(Crypto.sha256(c));
        }
        db.exec("UPDATE users SET totp_secret=?, totp_last_step=?, recovery=? WHERE id=?", secret, step, Json.write(hashes), s.user().id());
        db.exec("UPDATE sessions SET state='ok' WHERE id=?", s.id());
        db.exec("UPDATE users SET last_login=? WHERE id=?", System.currentTimeMillis(), s.user().id());
        return codes;
    }

    public int recoveryLeft(long userId) {
        String j = db.one("SELECT recovery FROM users WHERE id=?", rs -> rs.getString(1), userId);
        var n = j == null ? null : Json.read(j);
        return n == null ? 0 : n.size();
    }

    public void resetTotp(long userId) {
        db.exec("UPDATE users SET totp_secret=NULL, totp_last_step=0, recovery=NULL WHERE id=?", userId);
        revokeAll(userId);
    }

    public boolean changePassword(long userId, String current, String next) {
        String hash = db.one("SELECT pw_hash FROM users WHERE id=?", rs -> rs.getString(1), userId);
        if (!Crypto.checkPassword(current, hash)) return false;
        checkStrength(next);
        db.exec("UPDATE users SET pw_hash=? WHERE id=?", Crypto.hashPassword(next), userId);
        return true;
    }

    public void setPassword(long userId, String next) {
        checkStrength(next);
        db.exec("UPDATE users SET pw_hash=? WHERE id=?", Crypto.hashPassword(next), userId);
        revokeAll(userId);
    }

    public void update(long userId, String display, String role, String mcName) {
        if (role != null && !Roles.ROLES.containsKey(role)) throw new IllegalArgumentException("Unbekannte Rolle");
        db.exec("UPDATE users SET display=COALESCE(?,display), role=COALESCE(?,role), mc_name=COALESCE(?,mc_name) WHERE id=?",
                display, role, mcName, userId);
    }

    public void disable(long userId) {
        db.exec("UPDATE users SET disabled=1 WHERE id=?", userId);
        revokeAll(userId);
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        tickets.values().removeIf(t -> t.expires() < now);
        db.exec("DELETE FROM sessions WHERE expires<?", now);
    }
}
