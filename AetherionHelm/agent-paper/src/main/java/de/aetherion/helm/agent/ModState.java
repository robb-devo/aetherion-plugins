package de.aetherion.helm.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bans and mutes as last pushed by the gateway, kept on disk so they hold while the gateway is away. */
final class ModState {
    record Entry(String name, String reason, String by, long until) {
        boolean active() {
            return until <= 0 || until > System.currentTimeMillis();
        }
    }

    private final Path file;
    private final Map<UUID, Entry> bans = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> mutes = new ConcurrentHashMap<>();

    ModState(Path file) {
        this.file = file;
        try {
            if (Files.exists(file)) apply(JsonParser.parseString(Files.readString(file)).getAsJsonObject(), false);
        } catch (Exception ignored) {
            // start empty
        }
    }

    void apply(JsonObject m, boolean save) {
        fill(bans, m.getAsJsonArray("bans"));
        fill(mutes, m.getAsJsonArray("mutes"));
        if (save) {
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, m.toString(), StandardCharsets.UTF_8);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                // memory copy still holds
            }
        }
    }

    private static void fill(Map<UUID, Entry> map, JsonArray arr) {
        map.clear();
        if (arr == null) return;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            try {
                map.put(UUID.fromString(o.get("uuid").getAsString()), new Entry(
                        str(o, "name"), str(o, "reason"), str(o, "by"), o.has("until") ? o.get("until").getAsLong() : 0));
            } catch (Exception ignored) {
                // skip malformed
            }
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    Entry ban(UUID id) {
        Entry e = bans.get(id);
        return e != null && e.active() ? e : null;
    }

    Entry mute(UUID id) {
        Entry e = mutes.get(id);
        return e != null && e.active() ? e : null;
    }

    static String until(Entry e) {
        if (e.until() <= 0) return "dauerhaft";
        long left = e.until() - System.currentTimeMillis();
        long min = Math.max(1, left / 60_000);
        String rest = min < 60 ? min + " Min." : min < 2880 ? (min / 60) + " Std." : (min / 1440) + " Tage";
        return "bis " + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(e.until())) + " (noch " + rest + ")";
    }
}
