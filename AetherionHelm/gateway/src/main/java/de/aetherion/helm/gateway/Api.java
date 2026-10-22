package de.aetherion.helm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** The public listener: REST under /api, the live socket, and the web app's files. */
public final class Api {
    private static final Set<String> OPEN = Set.of("/api/health", "/api/auth/login", "/api/auth/totp", "/api/auth/me");
    private static final Set<String> ENROLL_OK = Set.of("/api/auth/me", "/api/auth/enroll/start", "/api/auth/enroll/finish", "/api/auth/logout");
    private static final Set<String> BLOCKED_COMMANDS = Set.of("stop", "restart", "end", "reload", "rl", "shutdown", "minecraft:stop",
            "minecraft:reload", "bukkit:reload", "bukkit:rl", "velocity:shutdown");

    static final class Fail extends RuntimeException {
        final int status;

        Fail(int status, String msg) {
            super(msg, null, false, false);
            this.status = status;
        }
    }

    private final Helm h;
    private Javalin app;

    public Api(Helm h) {
        this.h = h;
    }

    // ---------------------------------------------------------------- plumbing

    public boolean originAllowed(String origin) {
        if (origin == null) return true;
        return origin.equals(h.cfg.publicOrigin) || origin.startsWith("http://127.0.0.1") || origin.startsWith("http://localhost");
    }

    public String clientIp(String cfIp, String remote) {
        if (h.cfg.trustCloudflare && cfIp != null && !cfIp.isBlank()) return cfIp.trim();
        if (remote == null) return "?";
        String r = remote.replaceFirst("^/", "");
        int c = r.lastIndexOf(':');
        return c > 0 ? r.substring(0, c) : r;
    }

    private String ip(Context ctx) {
        return clientIp(ctx.header("CF-Connecting-IP"), ctx.req().getRemoteAddr());
    }

    private static Auth.Session sess(Context ctx) {
        Auth.Session s = ctx.attribute("session");
        if (s == null) throw new Fail(401, "Bitte melde dich an.");
        return s;
    }

    private static void need(Context ctx, String perm) {
        if (!Roles.has(sess(ctx).user().role(), perm)) throw new Fail(403, "Dafür fehlt deiner Rolle die Berechtigung.");
    }

    private static JsonNode body(Context ctx) {
        JsonNode n = Json.read(ctx.body());
        return n == null ? Json.obj() : n;
    }

    private static String str(JsonNode b, String f, int max, boolean required) {
        String v = Json.text(b, f, null);
        if (v != null) v = v.trim();
        if (required && (v == null || v.isEmpty())) throw new Fail(400, "Feld fehlt: " + f);
        if (v != null && v.length() > max) throw new Fail(400, "Zu lang: " + f + " (max. " + max + " Zeichen)");
        return v == null || v.isEmpty() ? null : v;
    }

    private void audit(Context ctx, String action, String target, String detail) {
        h.audit.log(sess(ctx).user().display(), ip(ctx), action, target, detail);
    }

    private void setCookie(Context ctx, String token, long maxAgeSec) {
        String c = Auth.COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict" + (h.cfg.secureCookies ? "; Secure" : "")
                + (maxAgeSec >= 0 ? "; Max-Age=" + maxAgeSec : "");
        ctx.header("Set-Cookie", c);
    }

    private Network.Srv server(Context ctx) {
        Network.Srv s = h.net.srv(ctx.pathParam("id"));
        if (s == null) throw new Fail(404, "Diesen Server gibt es nicht.");
        return s;
    }

    private static AgentConn agent(Network.Srv s) {
        AgentConn a = s.agent;
        if (a == null) throw new Fail(409, s.def.name() + " ist gerade nicht verbunden.");
        return a;
    }

    private static JsonNode await(java.util.concurrent.CompletableFuture<JsonNode> f) {
        try {
            return f.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof java.util.concurrent.TimeoutException) throw new Fail(504, "Der Server hat nicht rechtzeitig geantwortet.");
            throw new Fail(502, c == null ? "Fehler auf dem Server" : c.getMessage());
        } catch (Exception e) {
            throw new Fail(504, "Der Server hat nicht rechtzeitig geantwortet.");
        }
    }

    private static long range(Context ctx) {
        String r = ctx.queryParam("range");
        if (r == null) return 3600_000;
        return switch (r) {
            case "15m" -> 15 * 60_000L;
            case "1h" -> 3600_000L;
            case "6h" -> 6 * 3600_000L;
            case "24h" -> 24 * 3600_000L;
            case "7d" -> 7 * 24 * 3600_000L;
            case "30d" -> 30L * 24 * 3600_000L;
            default -> 3600_000L;
        };
    }

    private static String uuid(Context ctx) {
        String u = ctx.pathParam("uuid").toLowerCase(Locale.ROOT);
        if (!u.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new Fail(400, "Ungültige UUID");
        return u;
    }

    private String nameOf(String uuid) {
        Map<String, Object> r = h.players.row(uuid);
        if (r != null) return (String) r.get("name");
        ObjectNode p = h.net.findOnline(uuid);
        return p == null ? uuid : p.path("name").asText(uuid);
    }

    // ---------------------------------------------------------------- start

    public void start() {
        app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.startupWatcherEnabled = false;
            cfg.http.maxRequestSize = 256 * 1024;
            cfg.useVirtualThreads = true;
            cfg.jetty.modifyWebSocketServletFactory(f -> f.setIdleTimeout(java.time.Duration.ofMinutes(2)));
        });

        app.before(ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("X-Frame-Options", "DENY");
            ctx.header("Referrer-Policy", "same-origin");
            ctx.header("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            ctx.header("Content-Security-Policy", "default-src 'self'; img-src 'self' data: blob: https://mc-heads.net https://crafatar.com; "
                    + "style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self' wss: ws:; font-src 'self' data:; "
                    + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; manifest-src 'self'; worker-src 'self'");
        });

        app.before("/api/*", ctx -> {
            ctx.header("Cache-Control", "no-store");
            String m = ctx.method().name();
            if (!m.equals("GET") && !m.equals("HEAD")) {
                if (!"1".equals(ctx.header("X-Helm"))) throw new Fail(400, "Fehlender Request-Header");
                if (!originAllowed(ctx.header("Origin"))) throw new Fail(403, "Fremde Herkunft");
            }
            Auth.Session s = h.auth.session(ctx.cookie(Auth.COOKIE), ip(ctx));
            if (s != null) ctx.attribute("session", s);
            String path = ctx.path();
            if (OPEN.contains(path)) return;
            if (s == null) throw new Fail(401, "Bitte melde dich an.");
            if (s.enrolling() && !ENROLL_OK.contains(path)) throw new Fail(403, "Richte zuerst die Zwei-Faktor-Anmeldung ein.");
        });

        app.exception(Fail.class, (e, ctx) -> ctx.status(e.status).json(Map.of("error", e.getMessage())));
        app.exception(IllegalArgumentException.class, (e, ctx) -> ctx.status(400).json(Map.of("error", e.getMessage())));
        app.exception(Exception.class, (e, ctx) -> {
            org.slf4j.LoggerFactory.getLogger("helm.api").warn("{} {} failed", ctx.method(), ctx.path(), e);
            ctx.status(500).json(Map.of("error", "Interner Fehler. Details stehen im Gateway-Log."));
        });

        app.ws("/api/live", h.live::routes);

        authRoutes();
        networkRoutes();
        playerRoutes();
        staffRoutes();

        app.get("/*", this::staticFile);
        app.start(h.cfg.bind, h.cfg.port);
    }

    public void stop() {
        if (app != null) app.stop();
    }

    // ---------------------------------------------------------------- auth

    private void authRoutes() {
        app.get("/api/health", ctx -> ctx.json(Map.of("ok", true, "version", Helm.VERSION)));

        app.post("/api/auth/login", ctx -> {
            JsonNode b = body(ctx);
            String user = str(b, "username", 64, true), pw = Json.text(b, "password", "");
            boolean remember = b.path("remember").asBoolean(true);
            Auth.Login r = h.auth.login(user, pw, remember, ip(ctx), ctx.userAgent());
            if (r.token() != null) setCookie(ctx, r.token(), remember ? Auth.SESSION_TTL / 1000 : -1);
            if ("error".equals(r.status()) || "locked".equals(r.status())) {
                h.audit.log(user, ip(ctx), "login.failed", null, r.status());
                ctx.status("locked".equals(r.status()) ? 429 : 401);
            }
            ObjectNode o = Json.obj();
            o.put("status", r.status());
            if (r.ticket() != null) o.put("ticket", r.ticket());
            if (r.error() != null) o.put("error", r.error());
            ctx.json(o);
        });

        app.post("/api/auth/totp", ctx -> {
            JsonNode b = body(ctx);
            Auth.Login r = h.auth.secondFactor(Json.text(b, "ticket", null), Json.text(b, "code", ""), ip(ctx), ctx.userAgent());
            if (r.token() != null) {
                setCookie(ctx, r.token(), Auth.SESSION_TTL / 1000);
                Auth.Session s = h.auth.session(r.token(), ip(ctx));
                if (s != null) h.audit.log(s.user().display(), ip(ctx), "login", null, ctx.userAgent());
            } else {
                ctx.status("locked".equals(r.status()) ? 429 : 401);
            }
            ObjectNode o = Json.obj();
            o.put("status", r.status());
            if (r.error() != null) o.put("error", r.error());
            if (r.ticket() != null) o.put("ticket", r.ticket());
            ctx.json(o);
        });

        app.get("/api/auth/me", ctx -> {
            Auth.Session s = ctx.attribute("session");
            if (s == null) throw new Fail(401, "Bitte melde dich an.");
            ObjectNode o = Json.obj();
            ObjectNode u = o.putObject("user");
            u.put("id", s.user().id());
            u.put("username", s.user().username());
            u.put("display", s.user().display());
            u.put("role", s.user().role());
            u.put("roleLabel", Roles.of(s.user().role()).label());
            if (s.user().mcName() != null) u.put("mcName", s.user().mcName());
            u.put("totp", s.user().totp());
            o.put("enroll", s.enrolling());
            ArrayNode perms = o.putArray("perms");
            Roles.of(s.user().role()).perms().stream().sorted().forEach(perms::add);
            o.put("recoveryLeft", h.auth.recoveryLeft(s.user().id()));
            o.put("version", Helm.VERSION);
            ArrayNode servers = o.putArray("servers");
            for (Config.ServerDef d : h.cfg.servers) {
                ObjectNode x = servers.addObject();
                x.put("id", d.id());
                x.put("name", d.name());
                x.put("role", d.role());
                x.put("kind", d.kind());
            }
            ctx.json(o);
        });

        app.post("/api/auth/enroll/start", ctx -> {
            Auth.Session s = sess(ctx);
            if (s.user().totp() && !s.enrolling()) throw new Fail(409, "Zwei-Faktor ist schon eingerichtet.");
            String secret = h.auth.enrollStart(s.user().id());
            String label = URLEncoder.encode("Aetherion Helm:" + s.user().username(), StandardCharsets.UTF_8).replace("+", "%20");
            ctx.json(Map.of("secret", secret,
                    "uri", "otpauth://totp/" + label + "?secret=" + secret + "&issuer=Aetherion%20Helm&digits=6&period=30"));
        });

        app.post("/api/auth/enroll/finish", ctx -> {
            Auth.Session s = sess(ctx);
            List<String> codes = h.auth.enrollFinish(s, Json.text(body(ctx), "code", ""));
            if (codes == null) throw new Fail(400, "Der Code stimmt nicht. Prüfe die Uhrzeit auf dem Handy und versuch den nächsten.");
            h.audit.log(s.user().display(), ip(ctx), "2fa.enabled", null, null);
            ctx.json(Map.of("codes", codes));
        });

        app.post("/api/auth/logout", ctx -> {
            Auth.Session s = ctx.attribute("session");
            if (s != null) h.auth.logout(s.id());
            setCookie(ctx, "", 0);
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/auth/password", ctx -> {
            JsonNode b = body(ctx);
            Auth.Session s = sess(ctx);
            if (!h.auth.changePassword(s.user().id(), Json.text(b, "current", ""), Json.text(b, "next", ""))) {
                throw new Fail(400, "Das aktuelle Passwort stimmt nicht.");
            }
            h.auth.revokeOthers(s.user().id(), s.id());
            audit(ctx, "password.changed", null, null);
            ctx.json(Map.of("ok", true));
        });

        app.get("/api/auth/sessions", ctx -> {
            Auth.Session s = sess(ctx);
            List<Map<String, Object>> l = h.auth.sessions(s.user().id());
            for (Map<String, Object> r : l) r.put("current", ((Number) r.get("id")).longValue() == s.id());
            ctx.json(l);
        });

        app.delete("/api/auth/sessions/{sid}", ctx -> {
            Auth.Session s = sess(ctx);
            h.auth.revoke(s.user().id(), Long.parseLong(ctx.pathParam("sid")));
            audit(ctx, "session.revoked", null, ctx.pathParam("sid"));
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/auth/sessions/revoke-others", ctx -> {
            Auth.Session s = sess(ctx);
            h.auth.revokeOthers(s.user().id(), s.id());
            audit(ctx, "session.revoked", null, "alle anderen");
            ctx.json(Map.of("ok", true));
        });
    }

    // ---------------------------------------------------------------- servers and host

    private void networkRoutes() {
        app.get("/api/overview", ctx -> ctx.json(h.snapshot()));

        app.get("/api/network/history", ctx -> ctx.json(h.metrics.players(range(ctx))));

        app.get("/api/servers/{id}", ctx -> {
            Network.Srv s = server(ctx);
            ObjectNode o = h.net.serverJson(s, true);
            JsonNode size = h.ctl.sizes().path("servers").path(s.def.id());
            if (!size.isMissingNode()) o.set("disk", size);
            ArrayNode wd = o.putArray("watchdog");
            String alias = s.def.id();
            for (JsonNode l : h.ctl.watchdog()) if (l.asText().contains(" " + alias + " ")) wd.add(l);
            ctx.json(o);
        });

        app.get("/api/servers/{id}/history", ctx -> ctx.json(h.metrics.server(server(ctx).def.id(), range(ctx))));

        app.get("/api/servers/{id}/console", ctx -> {
            need(ctx, "console.view");
            ctx.json(h.net.console(server(ctx).def.id(), Math.min(3000, ctx.queryParamAsClass("limit", Integer.class).getOrDefault(500))));
        });

        app.post("/api/servers/{id}/command", ctx -> {
            need(ctx, "console.command");
            Network.Srv s = server(ctx);
            String cmd = str(body(ctx), "command", 1000, true).replaceFirst("^/", "");
            String head = cmd.split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (BLOCKED_COMMANDS.contains(head)) {
                throw new Fail(400, "„" + head + "“ bitte über die Schaltflächen oben: dort läuft der Countdown, und der Watchdog weiß Bescheid.");
            }
            ObjectNode a = Json.obj();
            a.put("command", cmd);
            a.put("by", sess(ctx).user().display());
            JsonNode r = await(agent(s).request("command", a, 15000));
            audit(ctx, "console.command", s.def.id(), cmd);
            ctx.json(r);
        });

        app.get("/api/servers/{id}/complete", ctx -> {
            need(ctx, "console.command");
            Network.Srv s = server(ctx);
            ObjectNode a = Json.obj();
            a.put("line", ctx.queryParam("line") == null ? "" : ctx.queryParam("line"));
            if (s.agent == null) { ctx.json(Json.arr()); return; }
            try {
                ctx.json(s.agent.request("complete", a, 3000).get(4, TimeUnit.SECONDS));
            } catch (Exception e) {
                ctx.json(Json.arr());
            }
        });

        app.post("/api/servers/{id}/power", ctx -> {
            need(ctx, "server.power");
            Network.Srv s = server(ctx);
            String action = str(body(ctx), "action", 16, true);
            String err = h.ctl.power(s.def.id(), action, sess(ctx).user().display());
            if (err != null) throw new Fail(409, err);
            audit(ctx, "server." + action, s.def.id(), null);
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/servers/{id}/save", ctx -> {
            need(ctx, "console.command");
            Network.Srv s = server(ctx);
            ObjectNode a = Json.obj();
            a.put("command", s.def.kind().equals("velocity") ? "velocity reload" : "save-all");
            a.put("by", sess(ctx).user().display());
            await(agent(s).request("command", a, 15000));
            audit(ctx, "server.save", s.def.id(), null);
            ctx.json(Map.of("ok", true));
        });

        app.get("/api/servers/{id}/entities", ctx -> {
            Network.Srv s = server(ctx);
            if (s.def.kind().equals("velocity")) throw new Fail(400, "Der Proxy hat keine Welten.");
            ctx.json(await(agent(s).request("entities", Json.obj(), 20000)));
        });

        app.get("/api/host", ctx -> {
            need(ctx, "host.view");
            ObjectNode o = h.host.detail();
            o.set("ctl", h.ctl.info());
            o.set("processes", h.host.processesJson(30));
            o.put("gatewayUptime", System.currentTimeMillis() - h.startedAt);
            ctx.json(o);
        });

        app.get("/api/host/history", ctx -> {
            need(ctx, "host.view");
            ctx.json(h.metrics.host(range(ctx)));
        });
    }

    // ---------------------------------------------------------------- players and moderation

    private void playerRoutes() {
        app.get("/api/players", ctx -> {
            need(ctx, "players.view");
            String q = ctx.queryParam("q");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(60);
            List<Map<String, Object>> rows = h.players.search(q == null ? "" : q, limit);
            Map<String, ObjectNode> online = new java.util.HashMap<>();
            for (ObjectNode p : h.net.onlinePlayers()) online.put(p.path("uuid").asText(), p);
            for (Map<String, Object> r : rows) {
                ObjectNode p = online.get((String) r.get("uuid"));
                r.put("online", p != null);
                if (p != null) r.put("server", p.path("server").asText());
            }
            ObjectNode o = Json.obj();
            o.set("results", Json.M.valueToTree(rows));
            o.put("total", h.players.total());
            if (q == null || q.isBlank()) {
                o.set("newest", Json.M.valueToTree(h.players.newest(8)));
                o.set("top", Json.M.valueToTree(h.players.topPlaytime(8)));
            }
            ctx.json(o);
        });

        app.get("/api/players/{uuid}", ctx -> {
            need(ctx, "players.view");
            String uuid = uuid(ctx);
            ObjectNode o = Json.obj();
            Map<String, Object> row = h.players.row(uuid);
            ObjectNode live = h.net.findOnline(uuid);
            if (row == null && live == null) throw new Fail(404, "Dieser Spieler war noch nie auf dem Netzwerk.");
            o.put("uuid", uuid);
            o.put("name", row != null ? (String) row.get("name") : live.path("name").asText());
            if (row != null) o.set("record", Json.M.valueToTree(row));
            if (live != null) o.set("live", live);
            o.set("punishments", Json.M.valueToTree(h.mod.history(uuid)));
            o.set("notes", Json.M.valueToTree(h.players.notes(uuid)));
            o.set("ips", Json.M.valueToTree(h.players.ips(uuid)));
            o.set("alts", Json.M.valueToTree(h.players.alts(uuid)));
            o.set("sessions", Json.M.valueToTree(h.players.sessions(uuid, 30)));
            o.set("activity", Json.M.valueToTree(h.players.activity(uuid)));
            o.set("events", Json.M.valueToTree(h.events.list(List.of("chat", "command", "death", "join", "quit", "switch", "mod", "report"), null, uuid, null, 120)));
            ctx.json(o);
        });

        app.get("/api/players/{uuid}/inventory", ctx -> {
            need(ctx, "players.inventory");
            String uuid = uuid(ctx);
            ObjectNode p = h.net.findOnline(uuid);
            if (p == null) throw new Fail(409, "Inventare sind live: der Spieler muss online sein.");
            Network.Srv s = h.net.srv(p.path("server").asText());
            if (s == null || s.agent == null) throw new Fail(409, "Der Server des Spielers ist nicht verbunden.");
            ObjectNode a = Json.obj();
            a.put("uuid", uuid);
            JsonNode inv = await(s.agent.request("inventory", a, 8000));
            audit(ctx, "player.inventory", nameOf(uuid), null);
            ctx.json(inv);
        });

        app.post("/api/players/{uuid}/kick", ctx -> {
            need(ctx, "players.kick");
            String uuid = uuid(ctx);
            String reason = str(body(ctx), "reason", 300, false);
            String by = sess(ctx).user().display();
            if (!h.mod.kick(uuid, kickText(reason, by))) throw new Fail(409, "Der Spieler ist nicht online.");
            h.mod.punish("kick", new Moderation.Target(uuid, nameOf(uuid)), reason, null, by);
            audit(ctx, "player.kick", nameOf(uuid), reason);
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/players/{uuid}/ban", ctx -> {
            need(ctx, "players.ban");
            String uuid = uuid(ctx);
            JsonNode b = body(ctx);
            String reason = str(b, "reason", 300, false);
            Long dur = b.path("duration").isNumber() ? b.path("duration").asLong() : null;
            h.mod.punish("ban", new Moderation.Target(uuid, nameOf(uuid)), reason, dur, sess(ctx).user().display());
            audit(ctx, "player.ban", nameOf(uuid), (dur == null ? "dauerhaft" : Fmt.duration(dur)) + (reason == null ? "" : " · " + reason));
            ctx.json(Map.of("ok", true));
        });

        app.delete("/api/players/{uuid}/ban", ctx -> {
            need(ctx, "players.ban");
            String uuid = uuid(ctx);
            if (!h.mod.revoke("ban", uuid, sess(ctx).user().display())) throw new Fail(404, "Kein aktiver Bann.");
            audit(ctx, "player.unban", nameOf(uuid), null);
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/players/{uuid}/mute", ctx -> {
            need(ctx, "players.mute");
            String uuid = uuid(ctx);
            JsonNode b = body(ctx);
            String reason = str(b, "reason", 300, false);
            Long dur = b.path("duration").isNumber() ? b.path("duration").asLong() : null;
            h.mod.punish("mute", new Moderation.Target(uuid, nameOf(uuid)), reason, dur, sess(ctx).user().display());
            audit(ctx, "player.mute", nameOf(uuid), (dur == null ? "dauerhaft" : Fmt.duration(dur)) + (reason == null ? "" : " · " + reason));
            ctx.json(Map.of("ok", true));
        });

        app.delete("/api/players/{uuid}/mute", ctx -> {
            need(ctx, "players.mute");
            String uuid = uuid(ctx);
            if (!h.mod.revoke("mute", uuid, sess(ctx).user().display())) throw new Fail(404, "Kein aktiver Mute.");
            audit(ctx, "player.unmute", nameOf(uuid), null);
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/players/{uuid}/warn", ctx -> {
            need(ctx, "players.kick");
            String uuid = uuid(ctx);
            String reason = str(body(ctx), "reason", 300, true);
            h.mod.punish("warn", new Moderation.Target(uuid, nameOf(uuid)), reason, null, sess(ctx).user().display());
            audit(ctx, "player.warn", nameOf(uuid), reason);
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/players/{uuid}/message", ctx -> {
            need(ctx, "players.message");
            String uuid = uuid(ctx);
            String text = str(body(ctx), "text", 400, true);
            ObjectNode a = Json.obj();
            a.put("uuid", uuid);
            a.put("from", sess(ctx).user().display());
            a.put("text", text);
            if (!h.mod.sendToPlayer(uuid, "message", a)) throw new Fail(409, "Der Spieler ist nicht online.");
            h.events.add("staffmsg", null, uuid, nameOf(uuid), sess(ctx).user().display() + " → " + text);
            audit(ctx, "player.message", nameOf(uuid), text);
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/players/{uuid}/send", ctx -> {
            need(ctx, "players.send");
            String uuid = uuid(ctx);
            String target = str(body(ctx), "server", 32, true);
            if (h.cfg.server(target) == null) throw new Fail(400, "Unbekannter Server");
            AgentConn px = h.net.proxy();
            if (px == null) throw new Fail(409, "Dafür muss das Helm-Plugin auf dem Proxy laufen.");
            ObjectNode a = Json.obj();
            a.put("uuid", uuid);
            a.put("server", target);
            await(px.request("send", a, 10000));
            audit(ctx, "player.send", nameOf(uuid), h.serverName(target));
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/players/{uuid}/notes", ctx -> {
            need(ctx, "notes.write");
            String uuid = uuid(ctx);
            String text = str(body(ctx), "text", 2000, true);
            h.players.addNote(uuid, sess(ctx).user().display(), text);
            audit(ctx, "player.note", nameOf(uuid), text.length() > 80 ? text.substring(0, 80) + "…" : text);
            ctx.json(Map.of("ok", true));
        });

        // ban / mute someone by name who was never here (or is offline): resolve through Mojang once
        app.post("/api/punish", ctx -> {
            JsonNode b = body(ctx);
            String type = str(b, "type", 8, true);
            need(ctx, "ban".equals(type) ? "players.ban" : "players.mute");
            if (!type.equals("ban") && !type.equals("mute")) throw new Fail(400, "Typ muss ban oder mute sein");
            Moderation.Target t = h.mod.resolve(str(b, "target", 40, true));
            if (t == null) throw new Fail(404, "Diesen Minecraft-Namen gibt es nicht.");
            String reason = str(b, "reason", 300, false);
            Long dur = b.path("duration").isNumber() ? b.path("duration").asLong() : null;
            h.mod.punish(type, t, reason, dur, sess(ctx).user().display());
            audit(ctx, "player." + type, t.name(), (dur == null ? "dauerhaft" : Fmt.duration(dur)) + (reason == null ? "" : " · " + reason));
            ctx.json(Map.of("ok", true, "uuid", t.uuid(), "name", t.name()));
        });

        app.get("/api/punishments", ctx -> {
            need(ctx, "players.view");
            ObjectNode o = Json.obj();
            o.set("active", Json.M.valueToTree(h.mod.active(null)));
            o.set("recent", Json.M.valueToTree(h.mod.recent(80)));
            ctx.json(o);
        });

        app.get("/api/activity", ctx -> {
            String kinds = ctx.queryParam("kinds");
            List<String> k = kinds == null || kinds.isBlank() ? null : List.of(kinds.split(","));
            Long before = ctx.queryParam("before") == null ? null : Long.parseLong(ctx.queryParam("before"));
            ctx.json(h.events.list(k, ctx.queryParam("server"), null, before, ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100)));
        });

        app.get("/api/chat/stats", ctx -> ctx.json(h.events.chatRate()));

        app.post("/api/chat", ctx -> {
            need(ctx, "chat.send");
            JsonNode b = body(ctx);
            String text = str(b, "text", 400, true);
            String target = str(b, "server", 32, false);
            ObjectNode a = Json.obj();
            a.put("from", sess(ctx).user().display());
            a.put("text", text);
            boolean sent = false;
            if (target == null && h.net.proxy() != null) {
                h.net.proxy().request("broadcast", a, 5000);
                sent = true;
            } else {
                for (Network.Srv s : h.net.servers.values()) {
                    if (s.agent == null || s.agent.isProxy()) continue;
                    if (target != null && !target.equals(s.def.id())) continue;
                    s.agent.request("broadcast", a, 5000);
                    sent = true;
                }
            }
            if (!sent) throw new Fail(409, "Kein Server verbunden.");
            h.events.add("staffchat", target, null, sess(ctx).user().display(), text);
            audit(ctx, "chat.broadcast", target == null ? "Netzwerk" : h.serverName(target), text);
            ctx.json(Map.of("ok", true));
        });

        app.get("/api/reports", ctx -> ctx.json(h.reports.list(ctx.queryParam("status"), 200)));

        app.post("/api/reports/{rid}/resolve", ctx -> {
            need(ctx, "reports.manage");
            long id = Long.parseLong(ctx.pathParam("rid"));
            String res = str(body(ctx), "resolution", 300, false);
            if (!h.reports.resolve(id, sess(ctx).user().display(), res)) throw new Fail(409, "Der Report ist schon erledigt.");
            audit(ctx, "report.resolved", "#" + id, res);
            ctx.json(Map.of("ok", true));
        });
    }

    private static String kickText(String reason, String by) {
        return "§c§lDu wurdest gekickt\n\n§7" + (reason == null || reason.isBlank() ? "Kein Grund angegeben" : reason) + "\n\n§8von " + by;
    }

    // ---------------------------------------------------------------- team, audit, alerts, settings

    private void staffRoutes() {
        app.get("/api/audit", ctx -> {
            need(ctx, "audit.view");
            Long before = ctx.queryParam("before") == null ? null : Long.parseLong(ctx.queryParam("before"));
            ctx.json(h.audit.list(before, ctx.queryParam("user"), ctx.queryParam("action"), ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100)));
        });

        app.get("/api/alerts", ctx -> {
            ObjectNode o = Json.obj();
            o.set("active", h.alerts.activeJson());
            o.set("history", Json.M.valueToTree(h.alerts.history(100)));
            ctx.json(o);
        });

        app.post("/api/alerts/{key}/ack", ctx -> {
            String key = ctx.pathParam("key");
            if (!h.alerts.ack(key, sess(ctx).user().display())) throw new Fail(404, "Diese Meldung ist schon weg.");
            audit(ctx, "alert.ack", key, null);
            ctx.json(Map.of("ok", true));
        });

        app.get("/api/team", ctx -> {
            ArrayNode users = Json.arr();
            java.util.Set<String> online = h.live.onlineStaff();
            for (Auth.User u : h.auth.users()) {
                ObjectNode x = users.addObject();
                x.put("id", u.id());
                x.put("username", u.username());
                x.put("display", u.display());
                x.put("role", u.role());
                x.put("roleLabel", Roles.of(u.role()).label());
                if (u.mcName() != null) x.put("mcName", u.mcName());
                x.put("totp", u.totp());
                x.put("created", u.created());
                if (u.lastLogin() != null) x.put("lastLogin", u.lastLogin());
                x.put("online", online.contains(u.display()));
            }
            ObjectNode o = Json.obj();
            o.set("users", users);
            ArrayNode roles = o.putArray("roles");
            for (Roles.Role r : Roles.ROLES.values()) {
                ObjectNode x = roles.addObject();
                x.put("id", r.id());
                x.put("label", r.label());
                ArrayNode p = x.putArray("perms");
                Roles.PERMS.stream().filter(r.perms()::contains).forEach(p::add);
            }
            ctx.json(o);
        });

        app.post("/api/team", ctx -> {
            need(ctx, "team.manage");
            JsonNode b = body(ctx);
            String role = str(b, "role", 16, true);
            if ("owner".equals(role)) throw new Fail(400, "Es gibt nur einen Owner.");
            String pw = tempPassword();
            Auth.User u = h.auth.create(str(b, "username", 24, true), str(b, "display", 32, true), role, str(b, "mcName", 16, false), pw);
            audit(ctx, "team.created", u.display(), Roles.of(role).label());
            ctx.json(Map.of("id", u.id(), "password", pw));
        });

        app.patch("/api/team/{uid}", ctx -> {
            need(ctx, "team.manage");
            long id = Long.parseLong(ctx.pathParam("uid"));
            JsonNode b = body(ctx);
            String role = str(b, "role", 16, false);
            if (id == sess(ctx).user().id() && role != null && !role.equals(sess(ctx).user().role())) throw new Fail(400, "Deine eigene Rolle kannst du nicht ändern.");
            if ("owner".equals(role)) throw new Fail(400, "Es gibt nur einen Owner.");
            h.auth.update(id, str(b, "display", 32, false), role, str(b, "mcName", 16, false));
            audit(ctx, "team.updated", String.valueOf(id), role);
            ctx.json(Map.of("ok", true));
        });

        app.delete("/api/team/{uid}", ctx -> {
            need(ctx, "team.manage");
            long id = Long.parseLong(ctx.pathParam("uid"));
            if (id == sess(ctx).user().id()) throw new Fail(400, "Dich selbst kannst du nicht entfernen.");
            Auth.User u = h.auth.userById(id);
            h.auth.disable(id);
            h.live.kickUser(id);
            audit(ctx, "team.removed", u == null ? String.valueOf(id) : u.display(), null);
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/team/{uid}/reset-2fa", ctx -> {
            need(ctx, "team.manage");
            long id = Long.parseLong(ctx.pathParam("uid"));
            Auth.User u = h.auth.userById(id);
            if (u == null) throw new Fail(404, "Unbekanntes Konto");
            h.auth.resetTotp(id);
            h.live.kickUser(id);
            audit(ctx, "team.reset-2fa", u.display(), null);
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/team/{uid}/reset-password", ctx -> {
            need(ctx, "team.manage");
            long id = Long.parseLong(ctx.pathParam("uid"));
            Auth.User u = h.auth.userById(id);
            if (u == null) throw new Fail(404, "Unbekanntes Konto");
            String pw = tempPassword();
            h.auth.setPassword(id, pw);
            h.live.kickUser(id);
            audit(ctx, "team.reset-password", u.display(), null);
            ctx.json(Map.of("password", pw));
        });

        app.get("/api/settings", ctx -> {
            need(ctx, "settings.manage");
            ObjectNode o = Json.obj();
            String hook = h.db.setting("discordWebhook", "");
            o.put("discordWebhook", hook.isEmpty() ? "" : hook.replaceAll("/[^/]{12,}$", "/••••••••"));
            o.put("discordSet", !hook.isEmpty());
            o.set("alerts", h.alerts.thresholds());
            ctx.json(o);
        });

        app.put("/api/settings", ctx -> {
            need(ctx, "settings.manage");
            JsonNode b = body(ctx);
            if (b.has("discordWebhook")) {
                String hook = b.path("discordWebhook").asText("").trim();
                if (!hook.isEmpty() && !hook.matches("https://(discord|discordapp)\\.com/api/webhooks/\\d+/[\\w-]+")) {
                    throw new Fail(400, "Das ist keine Discord-Webhook-Adresse.");
                }
                h.db.putSetting("discordWebhook", hook);
            }
            if (b.has("alerts") && b.get("alerts").isObject()) {
                ObjectNode t = h.alerts.thresholds();
                b.get("alerts").fields().forEachRemaining(e -> { if (t.has(e.getKey()) && e.getValue().isNumber()) t.set(e.getKey(), e.getValue()); });
                h.db.putSetting("alerts", Json.write(t));
            }
            audit(ctx, "settings.changed", null, null);
            ctx.json(Map.of("ok", true));
        });

        app.post("/api/settings/discord-test", ctx -> {
            need(ctx, "settings.manage");
            h.alerts.notify("info", "Testnachricht aus Aetherion Helm", "Gesendet von " + sess(ctx).user().display() + ". Alerts landen ab jetzt hier.");
            ctx.json(Map.of("ok", true));
        });

        app.get("/api/search", ctx -> {
            String q = ctx.queryParam("q");
            ObjectNode o = Json.obj();
            ArrayNode players = o.putArray("players");
            if (q != null && q.trim().length() >= 2 && Roles.has(sess(ctx).user().role(), "players.view")) {
                Set<String> online = new java.util.HashSet<>();
                for (ObjectNode p : h.net.onlinePlayers()) online.add(p.path("uuid").asText());
                for (Map<String, Object> r : h.players.search(q.trim(), 8)) {
                    ObjectNode x = players.addObject();
                    x.put("uuid", (String) r.get("uuid"));
                    x.put("name", (String) r.get("name"));
                    x.put("online", online.contains((String) r.get("uuid")));
                }
            }
            ctx.json(o);
        });
    }

    private static String tempPassword() {
        String a = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(a.charAt(Crypto.RNG.nextInt(a.length())));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- web app files

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"), Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"), Map.entry("json", "application/json"),
            Map.entry("webmanifest", "application/manifest+json"), Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"), Map.entry("woff2", "font/woff2"), Map.entry("woff", "font/woff"),
            Map.entry("txt", "text/plain; charset=utf-8"), Map.entry("apk", "application/vnd.android.package-archive"));

    private void staticFile(Context ctx) throws Exception {
        String path = ctx.path();
        if (path.startsWith("/api/")) throw new Fail(404, "Unbekannter Endpunkt");
        Path root = Path.of(h.cfg.webDir).toAbsolutePath().normalize();
        Path f = root.resolve(path.substring(1)).normalize();
        if (!f.startsWith(root)) throw new Fail(404, "Nicht gefunden");
        boolean asset = path.startsWith("/assets/");
        if (!Files.isRegularFile(f)) {
            String last = f.getFileName() == null ? "" : f.getFileName().toString();
            if (asset || last.contains(".")) {
                ctx.status(HttpStatus.NOT_FOUND).result("Not found");
                return;
            }
            f = root.resolve("index.html");
            if (!Files.isRegularFile(f)) {
                ctx.status(503).html("<h1>Aetherion Helm</h1><p>Die Web-App ist noch nicht installiert.</p>");
                return;
            }
        }
        String name = f.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        ctx.contentType(MIME.getOrDefault(ext, "application/octet-stream"));
        ctx.header("Cache-Control", asset ? "public, max-age=31536000, immutable" : "no-cache");
        if (ext.equals("apk")) ctx.header("Content-Disposition", "attachment; filename=\"" + name + "\"");
        InputStream in = Files.newInputStream(f);
        ctx.result(in);
    }
}
