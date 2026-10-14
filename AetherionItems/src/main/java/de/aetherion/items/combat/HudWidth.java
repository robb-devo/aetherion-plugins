package de.aetherion.items.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Pixel widths for the shared ActionBar (vanilla default font, GUI scale 1).
 *
 * <p>The ActionBar is centred, so anything appended on the right drags the whole line left by half
 * its width. {@link #balance(int)} returns blank padding of the same width for the left edge: the
 * centre stays put and HP / defense never move when a cooldown comes or goes.
 *
 * <p>ASCII widths are exact (ascii.png advances). Symbols from the pack's fallback pages are best
 * estimates — if a cooldown still nudges the bar by a pixel or two, tune {@link #BAR_CELL} /
 * {@link #CHECK} first (they appear 8× and 1× in the cooldown fragment).
 */
public final class HudWidth {

    /** Plain space advance. */
    public static final int SPACE = 4;
    /** {@code ▮ / ▯} cooldown cells (nonlatin page). Eight per cooldown → biggest lever when tuning. */
    static final int BAR_CELL = 5;
    /** {@code ✔} ready mark. */
    static final int CHECK = 8;
    /** {@code ❤}. */
    static final int HEART = 8;

    private HudWidth() {
    }

    /** Width of a component tree, honouring inherited bold. */
    public static int of(Component component) {
        return of(component, false);
    }

    private static int of(Component component, boolean inheritedBold) {
        if (component == null) {
            return 0;
        }
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.NOT_SET ? inheritedBold : state == TextDecoration.State.TRUE;
        int width = 0;
        if (component instanceof TextComponent text) {
            width += of(text.content(), bold);
        }
        for (Component child : component.children()) {
            width += of(child, bold);
        }
        return width;
    }

    public static int of(String text, boolean bold) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int advance = advance(cp);
            width += advance + (bold && cp != ' ' && advance > 0 ? 1 : 0);
        }
        return width;
    }

    /** Blank text {@code px} wide (nearest whole space; residual ≤ 2px → ≤ 1px drift on screen). */
    public static Component balance(int px) {
        if (px <= 0) {
            return Component.empty();
        }
        int spaces = Math.max(1, Math.round(px / (float) SPACE));
        return Component.text(" ".repeat(spaces));
    }

    /** Spaces that pad {@code actualPx} up to {@code targetPx} (empty when already wide enough). */
    public static String padTo(int actualPx, int targetPx) {
        int missing = targetPx - actualPx;
        if (missing < SPACE / 2) {
            return "";
        }
        return " ".repeat(Math.max(1, Math.round(missing / (float) SPACE)));
    }

    private static int advance(int cp) {
        switch (cp) {
            case ' ':
                return SPACE;
            case '!': case '\'': case ',': case '.': case ':': case ';': case '|': case 'i':
                return 2;
            case '`': case 'l':
                return 3;
            case '"': case '(': case ')': case '*': case 'I': case '[': case ']': case 't': case '{': case '}':
                return 4;
            case '<': case '>': case 'f': case 'k':
                return 5;
            case '@': case '~':
                return 7;
            case 0x00B7: // ·
                return 2;
            case 0x25AE: case 0x25AF: // ▮ ▯
                return BAR_CELL;
            case 0x2714: // ✔
                return CHECK;
            case 0x2764: // ❤
                return HEART;
            default:
                return cp < 0x20 ? 0 : 6;
        }
    }
}
