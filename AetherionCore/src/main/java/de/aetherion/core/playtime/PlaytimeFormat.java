package de.aetherion.core.playtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Human-readable totals. German when the viewer’s client locale is {@code de*},
 * English otherwise (console included).
 */
public final class PlaytimeFormat {

    private PlaytimeFormat() {
    }

    public static String format(long totalSeconds, boolean german) {
        long seconds = Math.max(0L, totalSeconds);
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remain = seconds % 60L;

        List<String> parts = new ArrayList<>(4);
        if (german) {
            add(parts, days, "Tag", "Tage");
            add(parts, hours, "Stunde", "Stunden");
            add(parts, minutes, "Minute", "Minuten");
            add(parts, remain, "Sekunde", "Sekunden");
            if (parts.isEmpty()) {
                return "0 Sekunden";
            }
            return join(parts, " und ");
        }
        add(parts, days, "day", "days");
        add(parts, hours, "hour", "hours");
        add(parts, minutes, "minute", "minutes");
        add(parts, remain, "second", "seconds");
        if (parts.isEmpty()) {
            return "0 seconds";
        }
        return join(parts, " and ");
    }

    private static void add(List<String> parts, long count, String one, String many) {
        if (count <= 0L) {
            return;
        }
        parts.add(count + " " + (count == 1L ? one : many));
    }

    private static String join(List<String> parts, String conjunction) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        String last = parts.get(parts.size() - 1);
        String head = String.join(", ", parts.subList(0, parts.size() - 1));
        return head + conjunction + last;
    }
}
