package de.aetherion.core.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One thing that happened somewhere in Aetherion and is worth telling on every backend: a boss fell, the titan
 * turned, somebody crossed the Veil. Published through {@link WorldAccess}; the same record reaches every server.
 *
 * <p>{@code kind} names the event family ({@code boss.kill}, {@code titan.churn}); {@code subject} is what it
 * happened to (a boss id, a place). {@code actor} is the player it is credited to, or null for something the world
 * did by itself. {@code data} carries small extras as text; never anything a player must not read.
 */
public record WorldNews(String id, long at, String server, String kind, String subject,
                        UUID actor, String actorName, Map<String, String> data) {

    public static final String BOSS_KILL = "boss.kill";
    public static final String TITAN_HEARTBEAT = "titan.heartbeat";
    public static final String TITAN_CHURN_SOON = "titan.churn.soon";
    public static final String TITAN_CHURN = "titan.churn";

    private static final String VERSION = "1";

    public WorldNews {
        id = id == null ? "" : id;
        server = server == null ? "" : server;
        kind = kind == null ? "" : kind;
        subject = subject == null ? "" : subject;
        actorName = actorName == null ? "" : actorName;
        data = data == null || data.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    /** Text extra, or {@code fallback} when it was not sent. */
    public String text(String key, String fallback) {
        String value = data.get(key);
        return value == null ? fallback : value;
    }

    /** Whole-number extra, or {@code fallback} when it is missing or not a number. */
    public int number(String key, int fallback) {
        String value = data.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public boolean flag(String key) {
        return "1".equals(data.get(key)) || "true".equalsIgnoreCase(data.get(key));
    }

    /** One line for the shared wire file: tab-separated, every field escaped, extras as {@code key=value}. */
    public String encode() {
        StringBuilder out = new StringBuilder(160);
        out.append(VERSION).append('\t')
                .append(escape(id)).append('\t')
                .append(at).append('\t')
                .append(escape(server)).append('\t')
                .append(escape(kind)).append('\t')
                .append(escape(subject)).append('\t')
                .append(actor == null ? "" : actor.toString()).append('\t')
                .append(escape(actorName));
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getKey().indexOf('=') >= 0) {
                continue;
            }
            out.append('\t').append(escape(entry.getKey())).append('=')
                    .append(escape(entry.getValue() == null ? "" : entry.getValue()));
        }
        return out.toString();
    }

    /** Reads a wire line back. {@code null} for a line this version does not understand (never throws). */
    public static WorldNews decode(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length < 8 || !VERSION.equals(parts[0])) {
            return null;
        }
        long at;
        try {
            at = Long.parseLong(parts[2]);
        } catch (NumberFormatException ignored) {
            return null;
        }
        UUID actor = null;
        if (!parts[6].isEmpty()) {
            try {
                actor = UUID.fromString(parts[6]);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        Map<String, String> data = new LinkedHashMap<>();
        for (int i = 8; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq <= 0) {
                continue;
            }
            data.put(unescape(parts[i].substring(0, eq)), unescape(parts[i].substring(eq + 1)));
        }
        String kind = unescape(parts[4]);
        if (kind.isEmpty()) {
            return null;
        }
        return new WorldNews(unescape(parts[1]), at, unescape(parts[3]), kind, unescape(parts[5]), actor,
                unescape(parts[7]), data);
    }

    static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = null;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            String swap = switch (c) {
                case '\\' -> "\\\\";
                case '\t' -> "\\t";
                case '\n' -> "\\n";
                case '\r' -> "\\r";
                default -> null;
            };
            if (swap == null) {
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            if (out == null) {
                out = new StringBuilder(raw.length() + 8).append(raw, 0, i);
            }
            out.append(swap);
        }
        return out == null ? raw : out.toString();
    }

    static String unescape(String raw) {
        if (raw == null || raw.indexOf('\\') < 0) {
            return raw == null ? "" : raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                out.append(c);
                continue;
            }
            char next = raw.charAt(++i);
            switch (next) {
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                default -> out.append(next);
            }
        }
        return out.toString();
    }
}
