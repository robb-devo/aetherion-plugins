package de.aetherion.stressbots.control;

import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Talks to the Mineflayer runner HTTP control plane (localhost).
 * Does not touch Velocity secrets — those stay in runner/config.json.
 */
public final class RunnerControlClient {

    private final AetherionStressBots plugin;
    private static final long HEALTH_TTL_MS = 5_000L;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Response lastHealth;
    private volatile long healthAt;

    public RunnerControlClient(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    /**
     * Runner health from a cache refreshed off the main thread (at most every 5 s). The Dev menu and
     * /botreport draw on the main thread, and a blocking HTTP call there froze the server for up to 3 s
     * per draw whenever the runner was down.
     */
    public boolean reachable() {
        Response health = health();
        return health != null && health.ok();
    }

    public String detail() {
        Response health = health();
        if (health == null) {
            return healthAt == 0L ? "checking " + baseUrl() + "…" : "no response from " + baseUrl();
        }
        if (!health.ok()) {
            return "http " + health.status() + " " + trim(health.body());
        }
        String message = extract(health.body(), "message");
        return message.isBlank() ? "ok " + baseUrl() : message;
    }

    private Response health() {
        long now = System.currentTimeMillis();
        if (now - healthAt > HEALTH_TTL_MS && refreshing.compareAndSet(false, true)) {
            Runnable refresh = () -> {
                try {
                    lastHealth = get("/health");
                    healthAt = System.currentTimeMillis();
                } finally {
                    refreshing.set(false);
                }
            };
            if (Bukkit.isPrimaryThread() && plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, refresh);
            } else {
                refresh.run();
            }
        }
        return lastHealth;
    }

    private void sawRunner(Response response) {
        if (response != null) {
            // A POST answered: the runner is up. Refresh the detail line on the next read.
            healthAt = Math.min(healthAt, System.currentTimeMillis() - HEALTH_TTL_MS);
            if (lastHealth == null) {
                lastHealth = new Response(200, "{\"ok\":true}");
            }
        }
    }

    private final AtomicBoolean networkRefreshing = new AtomicBoolean();
    private volatile List<String> network = List.of();
    private volatile long networkAt;

    /**
     * The runner's own view of the whole network (bots per server, roads taken, menu timings, problems), as ready
     * lines from {@code GET /report.txt}. Cached and refreshed off the main thread like the health line.
     */
    public List<String> networkLines() {
        long now = System.currentTimeMillis();
        if (now - networkAt > HEALTH_TTL_MS && networkRefreshing.compareAndSet(false, true)) {
            Runnable refresh = () -> {
                try {
                    Response response = get("/report.txt");
                    network = response == null || response.status() != 200 || response.body() == null
                            ? List.of()
                            : List.of(response.body().split("\\R"));
                    networkAt = System.currentTimeMillis();
                } finally {
                    networkRefreshing.set(false);
                }
            };
            if (Bukkit.isPrimaryThread() && plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, refresh);
            } else {
                refresh.run();
            }
        }
        return network;
    }

    public Response setDesired(String role, int count) {
        String json = "{\"role\":\"" + escape(role) + "\",\"count\":" + count + "}";
        Response response = post("/desired", json);
        sawRunner(response);
        return response;
    }

    public Response stop(String role) {
        String json = "{\"role\":\"" + escape(role) + "\"}";
        Response response = post("/stop", json);
        sawRunner(response);
        return response;
    }

    public Response stopAll() {
        Response response = post("/stop-all", "{}");
        sawRunner(response);
        return response;
    }

    public CompletableFuture<Response> setDesiredAsync(String role, int count) {
        return CompletableFuture.supplyAsync(() -> setDesired(role, count));
    }

    private Response get(String path) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(3))
                    .GET();
            applyToken(builder);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.FINE, "runner GET " + path + " failed", exception);
            return null;
        }
    }

    private Response post(String path, String json) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            applyToken(builder);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (Exception exception) {
            plugin.getLogger().warning("runner POST " + path + " failed: " + exception.getMessage());
            return null;
        }
    }

    private void applyToken(HttpRequest.Builder builder) {
        String token = plugin.getConfig().getString("testbots.runner.token", "");
        if (token != null && !token.isBlank()) {
            builder.header("X-Testbots-Token", token);
        }
    }

    public String baseUrl() {
        String host = plugin.getConfig().getString("testbots.runner.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("testbots.runner.port", 18765);
        return "http://" + host + ":" + port;
    }

    public static String extract(String json, String key) {
        if (json == null || json.isBlank() || key == null) {
            return "";
        }
        String needle = "\"" + key + "\"";
        int at = json.indexOf(needle);
        if (at < 0) {
            return "";
        }
        int colon = json.indexOf(':', at + needle.length());
        if (colon < 0) {
            return "";
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return "";
        }
        if (json.charAt(i) == '"') {
            int end = json.indexOf('"', i + 1);
            return end < 0 ? json.substring(i + 1) : json.substring(i + 1, end);
        }
        int end = i;
        while (end < json.length() && ",}]".indexOf(json.charAt(end)) < 0) {
            end++;
        }
        return json.substring(i, end).trim();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String trim(String body) {
        if (body == null) {
            return "";
        }
        String flat = body.replace('\n', ' ').trim();
        return flat.length() > 120 ? flat.substring(0, 117) + "..." : flat;
    }

    public record Response(int status, String body) {
        public boolean ok() {
            if (status < 200 || status >= 300) {
                return false;
            }
            String flag = extract(body, "ok");
            return flag.isBlank() || "true".equalsIgnoreCase(flag);
        }

        public String message() {
            String message = extract(body, "message");
            return message.isBlank() ? (body == null ? "" : body) : message;
        }
    }
}
