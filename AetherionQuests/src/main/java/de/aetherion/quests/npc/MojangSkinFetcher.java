package de.aetherion.quests.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Direct Mojang profile + session texture fetch.
 * FancyNpcs' UUIDFetcher hits a broken {@code api.minecraftservices.com?...&at=} URL (404).
 */
final class MojangSkinFetcher {

    private MojangSkinFetcher() {
    }

    record Textures(String value, String signature) {
    }

    static Textures fetch(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            String id = fetchProfileId(username.trim());
            if (id == null || id.isBlank()) {
                return null;
            }
            return fetchTextures(id);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String fetchProfileId(String username) throws Exception {
        HttpURLConnection conn = open(
                "https://api.mojang.com/users/profiles/minecraft/" + username
        );
        int code = conn.getResponseCode();
        if (code == 204 || code == 404) {
            return null;
        }
        if (code != 200) {
            return null;
        }
        JsonObject json = readJson(conn);
        if (json == null || !json.has("id")) {
            return null;
        }
        return json.get("id").getAsString();
    }

    private static Textures fetchTextures(String undashedUuid) throws Exception {
        HttpURLConnection conn = open(
                "https://sessionserver.mojang.com/session/minecraft/profile/"
                        + undashedUuid + "?unsigned=false"
        );
        if (conn.getResponseCode() != 200) {
            return null;
        }
        JsonObject json = readJson(conn);
        if (json == null || !json.has("properties")) {
            return null;
        }
        JsonArray props = json.getAsJsonArray("properties");
        for (JsonElement el : props) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject prop = el.getAsJsonObject();
            if (!"textures".equalsIgnoreCase(prop.get("name").getAsString())) {
                continue;
            }
            String value = prop.has("value") ? prop.get("value").getAsString() : null;
            String sig = prop.has("signature") ? prop.get("signature").getAsString() : null;
            if (value != null && !value.isBlank() && sig != null && !sig.isBlank()) {
                return new Textures(value, sig);
            }
        }
        return null;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "AetherionQuests/1.0");
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    private static JsonObject readJson(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
        )) {
            JsonElement el = JsonParser.parseReader(reader);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        }
    }

    static UUID parseUndashed(String id) {
        if (id == null) {
            return null;
        }
        String raw = id.toLowerCase(Locale.ROOT).replace("-", "");
        if (raw.length() != 32) {
            return null;
        }
        return UUID.fromString(
                raw.substring(0, 8) + "-"
                        + raw.substring(8, 12) + "-"
                        + raw.substring(12, 16) + "-"
                        + raw.substring(16, 20) + "-"
                        + raw.substring(20, 32)
        );
    }
}
