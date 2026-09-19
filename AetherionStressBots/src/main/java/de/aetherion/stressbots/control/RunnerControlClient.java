package de.aetherion.stressbots.control;

import de.aetherion.stressbots.AetherionStressBots;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Talks to the Mineflayer runner HTTP control plane (localhost).
 * Does not touch Velocity secrets — those stay in runner/config.json.
 */
public final class RunnerControlClient {

    private final AetherionStressBots plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public RunnerControlClient(AetherionStressBots plugin) {
        this.plugin = plugin;
    }

    public boolean reachable() {
        Response health = get("/health");
        return health != null && health.ok();
    }

    public String detail() {
        Response health = get("/health");
        if (health == null) {
            return "no response from " + baseUrl();
        }
        if (!health.ok()) {
            return "http " + health.status() + " " + trim(health.body());
        }
        String message = extract(health.body(), "message");
        return message.isBlank() ? "ok " + baseUrl() : message;
    }

    public Response setDesired(String role, int count) {
        String json = "{\"role\":\"" + escape(role) + "\",\"count\":" + count + "}";
        return post("/desired", json);
    }

    public Response stop(String role) {
        String json = "{\"role\":\"" + escape(role) + "\"}";
        return post("/stop", json);
    }

    public Response stopAll() {
        return post("/stop-all", "{}");
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
