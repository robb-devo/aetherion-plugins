package de.aetherion.bossengine.config;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern PATTERN =
            Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    /**
     * Accepts {@code 30s}, {@code 5m}, {@code 2h}, {@code 1d}, combinations like {@code 1h30m},
     * or a raw tick count as a number.
     */
    public static long toTicks(String raw, long fallbackTicks) {
        if (raw == null || raw.isBlank()) {
            return fallbackTicks;
        }

        String value = raw.trim().toLowerCase(Locale.ROOT);

        if (value.matches("\\d+")) {
            return Long.parseLong(value);
        }

        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches()) {
            return fallbackTicks;
        }

        long days = parseGroup(matcher, 1);
        long hours = parseGroup(matcher, 2);
        long minutes = parseGroup(matcher, 3);
        long seconds = parseGroup(matcher, 4);

        long millis = TimeUnit.DAYS.toMillis(days)
                + TimeUnit.HOURS.toMillis(hours)
                + TimeUnit.MINUTES.toMillis(minutes)
                + TimeUnit.SECONDS.toMillis(seconds);

        if (millis <= 0) {
            return fallbackTicks;
        }

        return Math.max(1L, millis / 50L);
    }

    private static long parseGroup(Matcher matcher, int group) {
        String part = matcher.group(group);
        if (part == null || part.isBlank()) {
            return 0L;
        }
        return Long.parseLong(part);
    }
}
