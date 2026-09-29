package de.aetherion.items.codex;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Number, bar and wrap helpers so every Codex page reads the same. */
public final class CodexText {

    private CodexText() {
    }

    public static String number(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    /** {@code 12.4k}, {@code 1.3M} — for tight spots like the tier ladder. */
    public static String compact(long value) {
        if (value >= 1_000_000L) {
            return trim(value / 1_000_000.0d) + "M";
        }
        if (value >= 10_000L) {
            return trim(value / 1_000.0d) + "k";
        }
        return number(value);
    }

    private static String trim(double value) {
        String out = String.format(Locale.US, "%.1f", value);
        return out.endsWith(".0") ? out.substring(0, out.length() - 2) : out;
    }

    /** 20-cell progress bar: {@code ▮▮▮▮▮▯▯▯…} with a colored filled part. */
    public static String bar(double fill, String onColor) {
        return bar(fill, onColor, 20);
    }

    public static String bar(double fill, String onColor, int cells) {
        double clamped = Math.max(0.0d, Math.min(1.0d, fill));
        int filled = (int) Math.floor(clamped * cells);
        if (clamped > 0.0d && filled == 0) {
            filled = 1;
        }
        return onColor + "▌".repeat(filled) + "§8" + "▌".repeat(cells - filled);
    }

    public static String percent(double fill) {
        double value = Math.max(0.0d, Math.min(1.0d, fill)) * 100.0d;
        if (value > 0.0d && value < 1.0d) {
            return "<1%";
        }
        return (int) Math.floor(value) + "%";
    }

    /**
     * Tier pips {@code ✔I ✔II ◆III ·IV …}: claimed green, ready gold, reached-but-unclaimed gold,
     * locked dark. Kept to one lore line.
     */
    public static String pips(int reached, int claimed, int max) {
        StringBuilder out = new StringBuilder();
        for (int tier = 1; tier <= max; tier++) {
            if (tier > 1) {
                out.append(' ');
            }
            if (tier <= claimed) {
                out.append("§a").append(CodexTiers.roman(tier));
            } else if (tier <= reached) {
                out.append("§6§l").append(CodexTiers.roman(tier)).append("§r");
            } else if (tier == reached + 1) {
                out.append("§e").append(CodexTiers.roman(tier));
            } else {
                out.append("§8").append(CodexTiers.roman(tier));
            }
        }
        return out.toString();
    }

    /** Word-wraps plain copy to ~34 visible chars per lore line, each line prefixed by {@code color}. */
    public static List<String> wrap(String text, String color) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > 34) {
                lines.add(color + line);
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(color + line);
        }
        return lines;
    }

    /** {@code dark_oak} / {@code DARK_OAK} → {@code Dark Oak}. */
    public static String pretty(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String[] parts = raw.toLowerCase(Locale.ROOT).split("[_\\-: ]+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.length() == 0 ? "Unknown" : out.toString();
    }

    public static String strip(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }
}
