package de.aetherion.core.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

import java.io.IOException;
import java.io.InputStream;

/**
 * Pixel widths in the vanilla 1.21.1 default font, exactly as the client measures them.
 *
 * <p>The table ({@code font/advances-1.21.1.bin}, written by {@code AetherionItems/tools/gen_font_advances.py})
 * holds one advance per BMP code point: ascii / accented / nonlatin_european pages, then unifont. Bold adds 1
 * to every glyph, spaces included. Anything the client would not draw from those pages (supplementary planes,
 * pack-only private-use glyphs, translatable or non-default-font components) is {@link #UNKNOWN}: callers that
 * need an exact centre skip their trick instead of drawing it in the wrong place.
 */
public final class TextWidth {

    public static final int UNKNOWN = -1;

    private static final byte[] ADVANCES = load();

    private TextWidth() {
    }

    /** Advance of one code point, or {@link #UNKNOWN}. */
    public static int advance(int codePoint) {
        if (codePoint < 0 || codePoint > 0xFFFF || ADVANCES == null) {
            return UNKNOWN;
        }
        int advance = ADVANCES[codePoint] & 0xFF;
        return advance == 0 && codePoint != 0x200C ? UNKNOWN : advance;
    }

    /** Width of a {@code §}-coded legacy string (colour codes reset bold, like the client). */
    public static int legacy(String text) {
        if (text == null) {
            return 0;
        }
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                if (i >= text.length()) {
                    break;
                }
                char code = Character.toLowerCase(text.charAt(i++));
                if (code == 'l') {
                    bold = true;
                } else if (code == 'r' || code == 'x' || Character.digit(code, 16) >= 0) {
                    bold = false;
                }
                continue;
            }
            int advance = advance(cp);
            if (advance == UNKNOWN) {
                return UNKNOWN;
            }
            width += advance + (bold ? 1 : 0);
        }
        return width;
    }

    /** Width of a component tree (plain text in the default font only), or {@link #UNKNOWN}. */
    public static int of(Component component) {
        return of(component, false);
    }

    private static int of(Component component, boolean inheritedBold) {
        if (component == null) {
            return 0;
        }
        if (!(component instanceof TextComponent text) || component.font() != null) {
            return UNKNOWN;
        }
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.NOT_SET ? inheritedBold : state == TextDecoration.State.TRUE;
        int width = 0;
        String content = text.content();
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            i += Character.charCount(cp);
            int advance = advance(cp);
            if (advance == UNKNOWN) {
                return UNKNOWN;
            }
            width += advance + (bold ? 1 : 0);
        }
        for (Component child : component.children()) {
            int part = of(child, bold);
            if (part == UNKNOWN) {
                return UNKNOWN;
            }
            width += part;
        }
        return width;
    }

    private static byte[] load() {
        try (InputStream in = TextWidth.class.getClassLoader().getResourceAsStream("font/advances-1.21.1.bin")) {
            if (in == null) {
                return null;
            }
            byte[] table = in.readAllBytes();
            return table.length == 0x10000 ? table : null;
        } catch (IOException e) {
            return null;
        }
    }
}
