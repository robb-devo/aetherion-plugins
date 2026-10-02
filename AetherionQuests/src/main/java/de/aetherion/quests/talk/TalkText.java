package de.aetherion.quests.talk;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Legacy-string helpers for speech: {@code {player}} tokens, typewriter-safe truncation
 * and pixel-width wrapping (vanilla default font advances), so a bubble never re-flows
 * while it types out.
 */
public final class TalkText {

    private TalkText() {
    }

    /** Replace speech tokens. Unknown tokens stay as written. */
    public static String fill(Player player, String line) {
        if (line == null) {
            return null;
        }
        if (line.indexOf('{') < 0) {
            return line;
        }
        String name = player == null ? "friend" : player.getName();
        return line
                .replace("{player}", name)
                .replace("{Player}", name)
                .replace("{PLAYER}", name.toUpperCase(Locale.ROOT));
    }

    /** Count of characters a reader actually sees (colour codes stripped). */
    public static int visibleLength(String legacy) {
        if (legacy == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                i++;
                continue;
            }
            if (c != '\n') {
                n++;
            }
        }
        return n;
    }

    /** The first {@code visible} readable characters, colour codes preserved. */
    public static String truncate(String legacy, int visible) {
        if (legacy == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(legacy.length());
        int n = 0;
        for (int i = 0; i < legacy.length(); i++) {
            if (n >= visible) {
                break;
            }
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                out.append(c).append(legacy.charAt(i + 1));
                i++;
                continue;
            }
            if (c == '\n') {
                out.append(c);
                continue;
            }
            out.append(c);
            n++;
        }
        return out.toString();
    }

    /** Plain text after the first {@code visible} readable characters (line breaks kept). */
    public static String rest(String legacy, int visible) {
        if (legacy == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int n = 0;
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                i++;
                continue;
            }
            if (c == '\n') {
                if (n >= visible) {
                    out.append(c);
                }
                continue;
            }
            if (n >= visible) {
                out.append(c);
            }
            n++;
        }
        return out.toString();
    }

    /** Character at visible index (for voice blips); 0 when out of range. */
    public static char visibleCharAt(String legacy, int index) {
        if (legacy == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                i++;
                continue;
            }
            if (c == '\n') {
                continue;
            }
            if (n == index) {
                return c;
            }
            n++;
        }
        return 0;
    }

    public static String strip(String legacy) {
        if (legacy == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(legacy.length());
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Word-wrap to {@code maxPx} using vanilla font advances. Colour state is carried
     * over line breaks so a wrapped highlight keeps its colour.
     */
    public static String wrap(String legacy, int maxPx) {
        if (legacy == null || legacy.isEmpty()) {
            return "";
        }
        List<String> words = splitKeepingCodes(legacy);
        StringBuilder out = new StringBuilder();
        int lineWidth = 0;
        String activeCodes = "";
        boolean bold = false;
        for (String word : words) {
            if (word.equals(" ")) {
                if (lineWidth > 0) {
                    out.append(' ');
                    lineWidth += 4;
                }
                continue;
            }
            int w = 0;
            boolean b = bold;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (c == '§' && i + 1 < word.length()) {
                    char code = Character.toLowerCase(word.charAt(i + 1));
                    if (code == 'l') {
                        b = true;
                    } else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                        b = false;
                    }
                    i++;
                    continue;
                }
                w += advance(c) + (b ? 1 : 0);
            }
            if (lineWidth > 0 && lineWidth + w > maxPx) {
                // drop trailing space, break, re-apply colour
                if (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
                    out.setLength(out.length() - 1);
                }
                out.append('\n').append(activeCodes);
                lineWidth = 0;
            }
            out.append(word);
            lineWidth += w;
            // track colour/format codes seen in this word
            for (int i = 0; i < word.length() - 1; i++) {
                if (word.charAt(i) == '§') {
                    char code = Character.toLowerCase(word.charAt(i + 1));
                    if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                        activeCodes = code == 'r' ? "" : "§" + code;
                        bold = false;
                    } else if ("klmno".indexOf(code) >= 0) {
                        activeCodes = activeCodes + "§" + code;
                        if (code == 'l') {
                            bold = true;
                        }
                    }
                }
            }
        }
        return out.toString();
    }

    private static List<String> splitKeepingCodes(String legacy) {
        List<String> out = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == ' ') {
                if (word.length() > 0) {
                    out.add(word.toString());
                    word.setLength(0);
                }
                out.add(" ");
                continue;
            }
            word.append(c);
        }
        if (word.length() > 0) {
            out.add(word.toString());
        }
        return out;
    }

    /** Vanilla default-font advance in pixels (glyph + 1px spacing). */
    public static int advance(char c) {
        switch (c) {
            case ' ':
                return 4;
            case '!': case ',': case '.': case ':': case ';': case 'i': case '|': case '\'':
                return 2;
            case 'l': case '`':
                return 3;
            case 'I': case 't': case '[': case ']': case '(': case ')': case '{': case '}': case '"': case '*':
                return 4;
            case 'f': case 'k': case '<': case '>':
                return 5;
            case '@': case '~':
                return 7;
            default:
                if (c < 128) {
                    return 6;
                }
                // unifont-ish symbols (—, ♥, ▶ …) are wider
                return c == '—' ? 9 : 8;
        }
    }
}
